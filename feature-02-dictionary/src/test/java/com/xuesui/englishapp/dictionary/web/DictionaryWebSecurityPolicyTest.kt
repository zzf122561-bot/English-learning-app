package com.xuesui.englishapp.dictionary.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            "https://dictionary.local/action/audio",
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
    fun onlySafeSameDocumentAnchorsAreDelegatedToWebView() {
        assertEquals(
            NavigationDecision.SameDocumentAnchor("sense 1"),
            DictionaryWebSecurityPolicy.navigation("https://dictionary.local/#sense%201"),
        )
        assertTrue(DictionaryWebSecurityPolicy.navigation("https://dictionary.local/#") is NavigationDecision.Blocked)
        assertTrue(DictionaryWebSecurityPolicy.navigation("https://example.com/#sense") is NavigationDecision.Blocked)
        assertTrue(DictionaryWebSecurityPolicy.navigation("javascript:#sense") is NavigationDecision.Blocked)
        assertTrue(DictionaryWebSecurityPolicy.navigation("file:///entry.html#sense") is NavigationDecision.Blocked)
        assertTrue(DictionaryWebSecurityPolicy.navigation("content://dictionary.local/#sense") is NavigationDecision.Blocked)
        assertTrue(
            DictionaryWebSecurityPolicy.navigation("https://dictionary.local/page#sense") is NavigationDecision.Blocked,
        )
    }

    @Test
    fun controlledAudioActionsAcceptOnlySafeMddOrStrictHttpsCandidates() {
        assertEquals(
            NavigationDecision.PlayAudio(DictionaryAudioAction(DictionaryAudioSource.MDD, "/uk/word.mp3")),
            DictionaryWebSecurityPolicy.navigation(
                "https://dictionary.local/action/audio?source=mdd&value=%2Fuk%2Fword.mp3",
            ),
        )
        assertEquals(
            NavigationDecision.PlayAudio(
                DictionaryAudioAction(DictionaryAudioSource.HTTPS, "https://audio.example/word.mp3"),
            ),
            DictionaryWebSecurityPolicy.navigation(
                "https://dictionary.local/action/audio?source=https&value=https%3A%2F%2Faudio.example%2Fword.mp3",
            ),
        )
        listOf(
            "https://dictionary.local/action/audio?source=mdd&value=..%2Fsecret.mp3",
            "https://dictionary.local/action/audio?source=https&value=http%3A%2F%2Faudio.example%2Fword.mp3",
            "https://dictionary.local/action/audio?source=https&value=file%3A%2F%2F%2Fword.mp3",
            "https://dictionary.local/action/audio?source=mdd&value=%2Fok.mp3&value=%2Fsecond.mp3",
            "https://dictionary.local/action/audio?source=mdd&value=${"a".repeat(2050)}",
        ).forEach { url ->
            assertTrue("$url must remain blocked", DictionaryWebSecurityPolicy.navigation(url) is NavigationDecision.Blocked)
        }
    }

    @Test
    fun anchorDispatchDoesNotCreateQueryOrBackStackAndAudioNeverNavigates() {
        var lookups = 0
        var audio = 0
        var blocked = 0
        assertFalse(
            dispatchDictionaryNavigation(
                NavigationDecision.SameDocumentAnchor("sense"),
                { lookups++ },
                { audio++ },
                { blocked++ },
            ),
        )
        assertTrue(
            dispatchDictionaryNavigation(
                NavigationDecision.PlayAudio(DictionaryAudioAction(DictionaryAudioSource.MDD, "/word.mp3")),
                { lookups++ },
                { audio++ },
                { blocked++ },
            ),
        )
        assertEquals(0, lookups)
        assertEquals(1, audio)
        assertEquals(0, blocked)
    }

    @Test
    fun generatedDocumentDeclaresRestrictiveCsp() {
        val html = secureHtmlDocument("<p>definition</p>")
        assertTrue(html.contains("default-src 'none'"))
        assertTrue(html.contains("https://dictionary.local"))
        assertTrue(html.contains("<p>definition</p>"))
    }
}
