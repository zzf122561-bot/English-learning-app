package com.xuesui.englishapp.dictionary.web

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

internal sealed interface NavigationDecision {
    data class InternalLookup(val query: String) : NavigationDecision
    data object Blocked : NavigationDecision
}

internal object DictionaryWebSecurityPolicy {
    const val HOST = "dictionary.local"
    const val BASE_URL = "https://$HOST/"
    const val MAX_RESOURCE_BYTES = 8 * 1024 * 1024

    fun resourcePath(url: String): String? {
        val uri = parseControlled(url) ?: return null
        if (uri.rawQuery != null || uri.rawFragment != null) return null
        val decoded = decodeComponent(uri.rawPath ?: return null) ?: return null
        if (decoded.length > 2048 || decoded.indexOf('\u0000') >= 0 || decoded.contains('\\')) return null
        val segments = decoded.split('/').filter(String::isNotEmpty)
        if (segments.isEmpty() || segments.any { it == "." || it == ".." }) return null
        if (segments.first().equals("lookup", ignoreCase = true)) return null
        return "/" + segments.joinToString("/")
    }

    fun navigation(url: String): NavigationDecision {
        val uri = parseControlled(url) ?: return NavigationDecision.Blocked
        if (uri.path != "/lookup" || uri.rawFragment != null) return NavigationDecision.Blocked
        val query = uri.rawQuery.orEmpty().split('&').mapNotNull { pair ->
            val parts = pair.split('=', limit = 2)
            if (parts.firstOrNull() != "q") null else decodeComponent(parts.getOrElse(1) { "" })
        }.singleOrNull()?.trim().orEmpty()
        return if (query.isEmpty() || query.length > 256) {
            NavigationDecision.Blocked
        } else {
            NavigationDecision.InternalLookup(query)
        }
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

    private fun decodeComponent(value: String): String? = try {
        URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name())
    } catch (_: IllegalArgumentException) {
        null
    }
}

