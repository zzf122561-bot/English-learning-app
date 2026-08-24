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
    fun generatedDataHtmlMainFrameIsHandledByWebViewButDataSubframesAreBlocked() {
        val main = "data:text/html;charset=utf-8;base64,"
        assertTrue(
            DictionaryWebSecurityPolicy.intercept(main, isForMainFrame = true) is
                InterceptDecision.AllowGeneratedMainDocument,
        )
        assertTrue(
            DictionaryWebSecurityPolicy.intercept(main + "PGRpdj5vazwvZGl2Pg==", isForMainFrame = true) is
                InterceptDecision.AllowGeneratedMainDocument,
        )
        assertTrue(
            DictionaryWebSecurityPolicy.intercept(main, isForMainFrame = false) is InterceptDecision.Blocked,
        )
        assertTrue(
            DictionaryWebSecurityPolicy.intercept("data:text/html,<p>not-base64</p>", true) is InterceptDecision.Blocked,
        )
        assertTrue(
            DictionaryWebSecurityPolicy.intercept(main + "<script>", true) is InterceptDecision.Blocked,
        )
    }

    @Test
    fun mainFrameConditionCannotAllowExternalFileContentOrHttpDocuments() {
        listOf(
            "https://example.com/",
            "https://dictionary.local/page",
            "http://dictionary.local/",
            "file:///dictionary.html",
            "content://dictionary.local/document",
        ).forEach { url ->
            assertTrue(
                "$url must remain blocked",
                DictionaryWebSecurityPolicy.intercept(url, isForMainFrame = true) is InterceptDecision.Blocked,
            )
        }
    }

    @Test
    fun exactControlledBaseIsMainOnlyAndMddResourcesAreSubresourcesOnly() {
        assertTrue(
            DictionaryWebSecurityPolicy.intercept(DictionaryWebSecurityPolicy.BASE_URL, true) is
                InterceptDecision.AllowGeneratedMainDocument,
        )
        assertTrue(
            DictionaryWebSecurityPolicy.intercept(DictionaryWebSecurityPolicy.BASE_URL, false) is
                InterceptDecision.Blocked,
        )
        assertEquals(
            InterceptDecision.ReadControlledResource("/images/icon.png"),
            DictionaryWebSecurityPolicy.intercept(
                "https://dictionary.local/images/icon.png",
                isForMainFrame = false,
            ),
        )
        assertTrue(
            DictionaryWebSecurityPolicy.intercept(
                "https://dictionary.local/images/icon.png",
                isForMainFrame = true,
            ) is InterceptDecision.Blocked,
        )
    }

    @Test
    fun invalidControlledSubresourcesNeverReachResourceReaderClassification() {
        listOf(
            "https://dictionary.local/a/%2e%2e/secret",
            "https://dictionary.local/a%5cb.css",
            "https://dictionary.local/lookup?q=word",
            "https://evil.example/image.png",
            "file:///image.png",
            "content://dictionary.local/image.png",
        ).forEach { url ->
            assertTrue(
                "$url must remain blocked",
                DictionaryWebSecurityPolicy.intercept(url, isForMainFrame = false) is InterceptDecision.Blocked,
            )
        }
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
