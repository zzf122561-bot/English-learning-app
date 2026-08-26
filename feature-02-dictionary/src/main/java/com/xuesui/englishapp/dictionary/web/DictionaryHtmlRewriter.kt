package com.xuesui.englishapp.dictionary.web

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

internal data class RewrittenDictionaryHtml(
    val html: String,
    val internalLinkTypes: Set<String>,
    val embeddedAudioPaths: List<String>,
    val httpsAudioUrls: List<String>,
)

/** Converts only recognized dictionary references into the controlled WebView origin. */
internal object DictionaryHtmlRewriter {
    private val tagPattern = Regex("<\\s*(a|area|img|link|audio|source|script)\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val attributePattern = Regex("""\b(href|src)\s*=\s*(?:(["'])(.*?)\2|([^\s>]+))""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val audioExtensions = setOf("mp3", "wav", "ogg", "m4a", "aac")
    private val resourceExtensions = audioExtensions + setOf("css", "js", "png", "jpg", "jpeg", "gif", "webp", "svg", "woff", "woff2", "ttf", "otf")

    fun rewrite(rawHtml: String): RewrittenDictionaryHtml {
        val internalTypes = linkedSetOf<String>()
        val embeddedAudio = linkedSetOf<String>()
        val httpsAudio = linkedSetOf<String>()
        val rewritten = tagPattern.replace(rawHtml) { tagMatch ->
            val tag = tagMatch.groupValues[1].lowercase(Locale.ROOT)
            attributePattern.replace(tagMatch.value) { attribute ->
                val name = attribute.groupValues[1].lowercase(Locale.ROOT)
                val quote = attribute.groupValues[2].ifEmpty { "\"" }
                val rawValue = (attribute.groupValues[3].ifEmpty { attribute.groupValues[4] })
                    .replace("&amp;", "&", ignoreCase = true)
                    .trim()
                val replacement = rewriteReference(tag, name, rawValue, internalTypes, embeddedAudio, httpsAudio)
                    ?: return@replace attribute.value
                "$name=$quote$replacement$quote"
            }
        }
        return RewrittenDictionaryHtml(rewritten, internalTypes, embeddedAudio.toList(), httpsAudio.toList())
    }

    private fun rewriteReference(
        tag: String,
        attribute: String,
        value: String,
        internalTypes: MutableSet<String>,
        embeddedAudio: MutableSet<String>,
        httpsAudio: MutableSet<String>,
    ): String? {
        if (value.isBlank()) return null
        if ((tag == "a" || tag == "area") && attribute == "href" && value.startsWith('#')) {
            internalTypes += "anchor"
            return DictionaryWebSecurityPolicy.BASE_URL + value
        }
        val scheme = value.substringBefore(':', "").lowercase(Locale.ROOT)
        if ((tag == "a" || tag == "area") && attribute == "href" && scheme == "javascript") {
            internalTypes += "javascript"
            return null
        }
        val audioReference = tag == "audio" || tag == "source" || extension(value) in audioExtensions ||
            scheme == "sound" || scheme == "audio"
        if (audioReference && scheme == "https" && isStrictHttpsAudioUrl(value)) {
            httpsAudio += value
            return if ((tag == "a" || tag == "area") && attribute == "href") {
                controlledAudioActionUrl(DictionaryAudioSource.HTTPS, value)
            } else {
                null
            }
        }
        if (audioReference) {
            normalizeMddAudioPath(value)?.let { path ->
                embeddedAudio += path
                return if ((tag == "a" || tag == "area") && attribute == "href") {
                    controlledAudioActionUrl(DictionaryAudioSource.MDD, path)
                } else {
                    controlledResourceUrl(path)
                }
            }
        }

        if ((tag == "a" || tag == "area") && attribute == "href") {
            if (scheme == "https" && value.startsWith(DictionaryWebSecurityPolicy.BASE_URL, ignoreCase = true)) {
                internalTypes += "same-origin"
                return null
            }
            internalQuery(value)?.let { (type, query) ->
                internalTypes += type
                return controlledLookupUrl(query)
            }
        }

        if (scheme.isEmpty() && (tag in setOf("img", "link", "script") || attribute == "src" || extension(value) in resourceExtensions)) {
            normalizeMddAudioPath(value)?.let { return controlledResourceUrl(it) }
        }
        return null
    }

    private fun internalQuery(value: String): Pair<String, String>? {
        val scheme = value.substringBefore(':', "").lowercase(Locale.ROOT)
        val encoded = when (scheme) {
            "entry", "bword" -> value.substringAfter(":").removePrefix("//")
            else -> {
                if (scheme in EXTERNAL_OR_LOCAL_SCHEMES || value.startsWith('/') || extension(value) in resourceExtensions) {
                    return null
                }
                if (scheme.isEmpty()) value else value.substringAfter(':').removePrefix("//")
            }
        }.substringBefore('#').substringBefore('?')
        val query = decode(encoded)?.trim().orEmpty()
        if (query.isEmpty() || query.length > 256 || query.any { it == '\u0000' || it == '\r' || it == '\n' }) return null
        return (if (scheme.isEmpty()) "relative" else scheme) to query
    }

    private fun controlledLookupUrl(query: String): String =
        "https://${DictionaryWebSecurityPolicy.HOST}/lookup?q=${encode(query)}"

    private fun controlledResourceUrl(path: String): String =
        "https://${DictionaryWebSecurityPolicy.HOST}" + path.split('/').joinToString("/") { encode(it) }

    private fun controlledAudioActionUrl(source: DictionaryAudioSource, value: String): String =
        "https://${DictionaryWebSecurityPolicy.HOST}${DictionaryWebSecurityPolicy.AUDIO_ACTION_PATH}" +
            "?source=${source.name.lowercase(Locale.ROOT)}&amp;value=${encode(value)}"

    private fun extension(value: String): String = value.substringBefore('#').substringBefore('?')
        .substringAfterLast('.', "").lowercase(Locale.ROOT)

    private fun decode(value: String): String? = try {
        URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name())
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    private val EXTERNAL_OR_LOCAL_SCHEMES = setOf(
        "http", "https", "javascript", "file", "content", "intent", "android-app", "data", "about",
        "sound", "audio",
    )
}

internal fun normalizeMddAudioPath(value: String): String? {
    val scheme = value.substringBefore(':', "").lowercase(Locale.ROOT)
    if (scheme.isNotEmpty() && scheme !in setOf("sound", "audio")) return null
    val withoutScheme = if (scheme.isEmpty()) value else value.substringAfter(':').removePrefix("//")
    val decoded = try {
        URLDecoder.decode(
            withoutScheme.substringBefore('#').substringBefore('?').replace("+", "%2B"),
            StandardCharsets.UTF_8.name(),
        )
    } catch (_: IllegalArgumentException) {
        return null
    }
    if (decoded.isBlank() || decoded.indexOf('\u0000') >= 0 || decoded.length > 2048) return null
    val segments = decoded.replace('\\', '/').split('/').filter(String::isNotEmpty)
    if (segments.isEmpty() || segments.any { it == "." || it == ".." }) return null
    return "/" + segments.joinToString("/")
}

internal fun isStrictHttpsAudioUrl(value: String): Boolean = try {
    val uri = URI(value)
    uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null &&
        (uri.port == -1 || uri.port == 443) && uri.fragment == null
} catch (_: Exception) {
    false
}
