package com.xuesui.englishapp.dictionary.web

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

internal sealed interface NavigationDecision {
    data class InternalLookup(val query: String) : NavigationDecision
    data class SameDocumentAnchor(val fragment: String) : NavigationDecision
    data object AllowControlledInternal : NavigationDecision
    data object AllowInlineScript : NavigationDecision
    data class PlayAudio(val action: DictionaryAudioAction) : NavigationDecision
    data object Blocked : NavigationDecision
}

internal enum class DictionaryAudioSource { MDD, HTTPS }

internal data class DictionaryAudioAction(
    val source: DictionaryAudioSource,
    val value: String,
)

internal sealed interface InterceptDecision {
    data object AllowGeneratedMainDocument : InterceptDecision
    data class ReadControlledResource(val path: String) : InterceptDecision
    data object Blocked : InterceptDecision
}

internal object DictionaryWebSecurityPolicy {
    const val HOST = "dictionary.local"
    const val BASE_URL = "https://$HOST/"
    const val AUDIO_ACTION_PATH = "/action/audio"
    const val MAX_RESOURCE_BYTES = 8 * 1024 * 1024

    fun intercept(url: String, isForMainFrame: Boolean): InterceptDecision {
        if (isForMainFrame && isGeneratedMainDocument(url)) return InterceptDecision.AllowGeneratedMainDocument
        return resourcePath(url)?.let(InterceptDecision::ReadControlledResource)
            ?: InterceptDecision.Blocked
    }

    fun resourcePath(url: String): String? {
        val uri = parseControlled(url) ?: return null
        val rawPath = uri.rawPath.orEmpty().ifEmpty { "/" }
        val decoded = decodeComponent(rawPath) ?: rawPath
        return decoded.let { if (it.startsWith('/')) it else "/$it" }
    }

    fun navigation(url: String): NavigationDecision {
        if (url.startsWith("$BASE_URL#")) {
            return NavigationDecision.SameDocumentAnchor(url.substringAfter('#'))
        }
        val scheme = url.substringBefore(':', "").lowercase(Locale.ROOT)
        if (scheme == "javascript") return NavigationDecision.AllowInlineScript
        if (scheme in BLOCKED_SCHEMES) return NavigationDecision.Blocked
        if (scheme.isNotEmpty() && scheme !in setOf("http", "https")) {
            return internalTarget(url)?.let(NavigationDecision::InternalLookup) ?: NavigationDecision.Blocked
        }
        val uri = parseControlled(url) ?: return NavigationDecision.Blocked
        if (uri.path == AUDIO_ACTION_PATH && uri.rawFragment == null) {
            return parseAudioAction(uri.rawQuery)
        }
        if (uri.path == "/lookup" && uri.rawFragment == null) {
            val query = uri.rawQuery.orEmpty().split('&').mapNotNull { pair ->
                val parts = pair.split('=', limit = 2)
                if (parts.firstOrNull() != "q") null else decodeComponent(parts.getOrElse(1) { "" })
            }.singleOrNull()?.trim().orEmpty()
            if (query.isNotEmpty() && query.length <= 256) return NavigationDecision.InternalLookup(query)
        }
        return if (uri.rawFragment != null && uri.path == "/") {
            NavigationDecision.SameDocumentAnchor(uri.rawFragment.orEmpty())
        } else {
            NavigationDecision.AllowControlledInternal
        }
    }

    private fun internalTarget(url: String): String? {
        val target = url.substringAfter(':').removePrefix("//").substringBefore('#').substringBefore('?')
        return decodeComponent(target)?.trim()?.takeIf { it.isNotEmpty() && it.length <= 256 }
    }

    private fun parseAudioAction(rawQuery: String?): NavigationDecision {
        val pairs = mutableListOf<Pair<String, String>>()
        for (pair in rawQuery.orEmpty().split('&')) {
            val parts = pair.split('=', limit = 2)
            val name = parts.firstOrNull()?.takeIf(String::isNotEmpty) ?: return NavigationDecision.Blocked
            val value = decodeComponent(parts.getOrElse(1) { "" }) ?: return NavigationDecision.Blocked
            pairs += name to value
        }
        if (pairs.size != 2 || pairs.map { it.first }.toSet() != setOf("source", "value")) {
            return NavigationDecision.Blocked
        }
        val source = pairs.single { it.first == "source" }.second
        val value = pairs.single { it.first == "value" }.second
        val action = when (source) {
            "mdd" -> normalizeMddAudioPath(value)?.let { DictionaryAudioAction(DictionaryAudioSource.MDD, it) }
            "https" -> value.takeIf(::isStrictHttpsAudioUrl)?.let {
                DictionaryAudioAction(DictionaryAudioSource.HTTPS, it)
            }
            else -> null
        }
        return action?.let(NavigationDecision::PlayAudio) ?: NavigationDecision.Blocked
    }

    private fun parseControlled(url: String): URI? = try {
        val uri = URI(url)
        uri.takeIf {
            it.scheme.equals("https", ignoreCase = true) &&
                it.host.equals(HOST, ignoreCase = true) &&
                (it.port == -1 || it.port == 443) &&
                it.userInfo == null
        }
    } catch (_: Exception) {
        null
    }

    private fun isGeneratedMainDocument(url: String): Boolean {
        if (url == BASE_URL) return true
        if (!url.startsWith(DATA_HTML_BASE64_PREFIX, ignoreCase = true)) return false
        return url.substring(DATA_HTML_BASE64_PREFIX.length).all { character ->
            character in 'A'..'Z' || character in 'a'..'z' || character in '0'..'9' ||
                character == '+' || character == '/' || character == '='
        }
    }

    private fun decodeComponent(value: String): String? = try {
        URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name())
    } catch (_: IllegalArgumentException) {
        null
    }

    private const val DATA_HTML_BASE64_PREFIX = "data:text/html;charset=utf-8;base64,"
    private val BLOCKED_SCHEMES = setOf(
        "file", "content", "intent", "android-app", "data", "about", "blob",
        "mailto", "tel", "sms", "geo", "market", "ftp", "ws", "wss",
    )
}
