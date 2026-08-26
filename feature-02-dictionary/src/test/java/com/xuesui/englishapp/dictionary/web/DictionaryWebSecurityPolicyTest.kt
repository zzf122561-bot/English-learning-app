package com.xuesui.englishapp.dictionary.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryWebSecurityPolicyTest {
    @Test
    fun everyControlledOriginResourceShapeReachesOnlyTheCurrentMddReader() {
        val cases = mapOf(
            "https://dictionary.local/images/icon.png" to "/images/icon.png",
            "https://dictionary.local/unknown/path?mode=quick#part" to "/unknown/path",
            "https://dictionary.local/a/%2e%2e/inside.mdd?x=1" to "/a/../inside.mdd",
            "https://dictionary.local/a%5cb.css#x" to "/a\\b.css",
            "https://dictionary.local/" + "a".repeat(3000) to "/" + "a".repeat(3000),
        )
        cases.forEach { (url, path) ->
            assertEquals(path, DictionaryWebSecurityPolicy.resourcePath(url))
            assertEquals(
                InterceptDecision.ReadControlledResource(path),
                DictionaryWebSecurityPolicy.intercept(url, isForMainFrame = false),
            )
        }
        assertNull(DictionaryWebSecurityPolicy.resourcePath("https://dictionary.local.evil/image.png"))
        assertNull(DictionaryWebSecurityPolicy.resourcePath("file:///image.png"))
    }

    @Test
    fun controlledPagesAreNot403EvenForMainFrameUnknownPathsQueriesOrFragments() {
        listOf(
            "https://dictionary.local/page",
            "https://dictionary.local/page?mode=compact",
            "https://dictionary.local/page#part",
            "https://dictionary.local/?dynamic=1#",
        ).forEach { url ->
            assertTrue(
                "$url must reach the current dictionary rather than 403",
                DictionaryWebSecurityPolicy.intercept(url, isForMainFrame = true) is
                    InterceptDecision.ReadControlledResource,
            )
        }
    }

    @Test
    fun generatedDataHtmlMainFrameIsAllowedButDataSubframesStayBlocked() {
        val main = "data:text/html;charset=utf-8;base64,PGRpdj5vazwvZGl2Pg=="
        assertTrue(
            DictionaryWebSecurityPolicy.intercept(main, isForMainFrame = true) is
                InterceptDecision.AllowGeneratedMainDocument,
        )
        assertTrue(DictionaryWebSecurityPolicy.intercept(main, false) is InterceptDecision.Blocked)
        assertTrue(
            DictionaryWebSecurityPolicy.intercept("data:text/html,<p>not-base64</p>", true) is
                InterceptDecision.Blocked,
        )
    }

    @Test
    fun externalAndCrossApplicationRequestsRemainBlockedForEveryFrame() {
        listOf(
            "https://example.com/page",
            "http://dictionary.local/page",
            "file:///dictionary.html",
            "content://dictionary.local/document",
            "intent://lookup#Intent;scheme=test;end",
            "android-app://com.example/page",
            "mailto:user@example.com",
            "tel:10086",
        ).forEach { url ->
            assertTrue(DictionaryWebSecurityPolicy.intercept(url, true) is InterceptDecision.Blocked)
            assertTrue(DictionaryWebSecurityPolicy.intercept(url, false) is InterceptDecision.Blocked)
            assertTrue(DictionaryWebSecurityPolicy.navigation(url) is NavigationDecision.Blocked)
        }
    }

    @Test
    fun ordinaryEncodedAndEmptyFragmentsAreAlwaysNativeSameDocumentNavigation() {
        listOf(
            "https://dictionary.local/#sense",
            "https://dictionary.local/#sense%201",
            "https://dictionary.local/#",
            "https://dictionary.local/#%00${"x".repeat(2000)}",
        ).forEach { url ->
            assertTrue(DictionaryWebSecurityPolicy.navigation(url) is NavigationDecision.SameDocumentAnchor)
        }
    }

    @Test
    fun sameOriginPathsQueriesAndDomGeneratedLinksAreAllowedWithoutEnumeration() {
        listOf(
            "https://dictionary.local/page",
            "https://dictionary.local/unknown/path?x=1",
            "https://dictionary.local/generated/by/dom?mode=quick#target",
        ).forEach { url ->
            assertEquals(NavigationDecision.AllowControlledInternal, DictionaryWebSecurityPolicy.navigation(url))
        }
    }

    @Test
    fun entryBwordAndCustomDictionarySchemesReplaceTheCurrentQuery() {
        assertEquals(
            NavigationDecision.InternalLookup("ice cream"),
            DictionaryWebSecurityPolicy.navigation("entry://ice%20cream"),
        )
        assertEquals(
            NavigationDecision.InternalLookup("apple"),
            DictionaryWebSecurityPolicy.navigation("bword:apple"),
        )
        assertEquals(
            NavigationDecision.InternalLookup("target"),
            DictionaryWebSecurityPolicy.navigation("mdict-internal://target"),
        )
        assertEquals(
            NavigationDecision.InternalLookup("next word"),
            DictionaryWebSecurityPolicy.navigation("https://dictionary.local/lookup?q=next%20word"),
        )
    }

    @Test
    fun inlineDictionaryScriptIsNativeButNeverAHostBridgeOrExternalNavigation() {
        assertEquals(
            NavigationDecision.AllowInlineScript,
            DictionaryWebSecurityPolicy.navigation("javascript:this.className='expanded'"),
        )
        var lookups = 0
        var audio = 0
        var blocked = 0
        assertFalse(
            dispatchDictionaryNavigation(
                NavigationDecision.AllowInlineScript,
                { lookups++ },
                { audio++ },
                { blocked++ },
            ),
        )
        assertEquals(0, lookups)
        assertEquals(0, audio)
        assertEquals(0, blocked)
    }

    @Test
    fun sameDocumentAndSameOriginDispatchNeverMutateQueryOrAddModuleBackStack() {
        var lookups = 0
        var blocked = 0
        listOf(
            NavigationDecision.SameDocumentAnchor(""),
            NavigationDecision.AllowControlledInternal,
        ).forEach { decision ->
            assertFalse(dispatchDictionaryNavigation(decision, { lookups++ }, {}, { blocked++ }))
        }
        assertEquals(0, lookups)
        assertEquals(0, blocked)
    }

    @Test
    fun controlledAudioActionStillUsesTheExistingCoordinatorBoundary() {
        assertEquals(
            NavigationDecision.PlayAudio(DictionaryAudioAction(DictionaryAudioSource.MDD, "/uk/word.mp3")),
            DictionaryWebSecurityPolicy.navigation(
                "https://dictionary.local/action/audio?source=mdd&value=%2Fuk%2Fword.mp3",
            ),
        )
        listOf(
            "https://dictionary.local/action/audio?source=mdd&value=..%2Fsecret.mp3",
            "https://dictionary.local/action/audio?source=https&value=http%3A%2F%2Faudio.example%2Fword.mp3",
        ).forEach { url ->
            assertTrue(DictionaryWebSecurityPolicy.navigation(url) is NavigationDecision.Blocked)
        }
    }

    @Test
    fun generatedDocumentAllowsOnlyLocalAndInlineDictionaryScripts() {
        val html = secureHtmlDocument(
            "<a onclick=\"this.className='expanded'\">quick</a>" +
                "<script>document.body.dataset.ready='1'</script>",
        )
        assertTrue(html.contains("script-src https://dictionary.local 'unsafe-inline'"))
        assertTrue(html.contains("default-src 'none'"))
        assertFalse(html.contains("https://example.com"))
        assertTrue(html.contains("onclick="))
        assertTrue(html.contains("<script>"))
    }
}
