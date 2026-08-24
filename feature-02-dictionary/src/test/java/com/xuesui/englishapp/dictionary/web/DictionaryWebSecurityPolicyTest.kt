package com.xuesui.englishapp.dictionary.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryWebSecurityPolicyTest {
    @Test
    fun acceptsOnlyControlledHttpsResourcePaths() {
        assertEquals(
            "/images/icon.png",
            DictionaryWebSecurityPolicy.resourcePath("https://dictionary.local/images/icon.png"),
        )
        assertNull(DictionaryWebSecurityPolicy.resourcePath("http://dictionary.local/images/icon.png"))
        assertNull(DictionaryWebSecurityPolicy.resourcePath("https://evil.example/images/icon.png"))
        assertNull(DictionaryWebSecurityPolicy.resourcePath("file:///images/icon.png"))
        assertNull(DictionaryWebSecurityPolicy.resourcePath("content://dictionary.local/images/icon.png"))
    }

    @Test
    fun blocksEncodedTraversalBackslashAndOversizedPaths() {
        assertNull(DictionaryWebSecurityPolicy.resourcePath("https://dictionary.local/a/%2e%2e/secret"))
        assertNull(DictionaryWebSecurityPolicy.resourcePath("https://dictionary.local/a%5cb.css"))
        assertNull(
            DictionaryWebSecurityPolicy.resourcePath(
                "https://dictionary.local/" + "a".repeat(2050),
            ),
        )
    }

    @Test
    fun internalLookupIsTheOnlyAllowedNavigation() {
        val internal = DictionaryWebSecurityPolicy.navigation("https://dictionary.local/lookup?q=next%20word")
        assertEquals("next word", (internal as NavigationDecision.InternalLookup).query)
        assertTrue(DictionaryWebSecurityPolicy.navigation("https://dictionary.local/page") is NavigationDecision.Blocked)
        assertTrue(DictionaryWebSecurityPolicy.navigation("https://example.com/") is NavigationDecision.Blocked)
    }

    @Test
    fun generatedDocumentDeclaresRestrictiveCsp() {
        val html = secureHtmlDocument("<p>definition</p>")
        assertTrue(html.contains("default-src 'none'"))
        assertTrue(html.contains("https://dictionary.local"))
        assertTrue(html.contains("<p>definition</p>"))
    }
}

