package com.xuesui.englishapp.dictionary.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryHtmlRewriterTest {
    @Test
    fun knownAndCustomCrossEntryLinksStillReplaceTheCurrentQuery() {
        val result = DictionaryHtmlRewriter.rewrite(
            """<a href="entry://ice%20cream">entry</a><a href='bword://apple'>bword</a>
                <a href="relative target">relative</a><a href="mdict-internal://custom">custom</a>""",
        )

        assertEquals(setOf("entry", "bword", "relative", "mdict-internal"), result.internalLinkTypes)
        assertTrue(result.html.contains("/lookup?q=ice%20cream"))
        assertTrue(result.html.contains("/lookup?q=apple"))
        assertTrue(result.html.contains("/lookup?q=relative%20target"))
        assertTrue(result.html.contains("/lookup?q=custom"))
    }

    @Test
    fun allFragmentShapesAndSameOriginLinksRemainInsideTheControlledDocument() {
        val result = DictionaryHtmlRewriter.rewrite(
            """<a href="#sense">plain</a><a href="#sense%201">encoded</a><a href="#">empty</a>
                <a href="#%00${"x".repeat(2000)}">unusual</a>
                <a href="https://dictionary.local/unknown/path?x=1#part">absolute</a>
                <a href="/absolute/path?mode=quick#part">root</a>""",
        )

        assertTrue(result.html.contains("https://dictionary.local/#sense"))
        assertTrue(result.html.contains("https://dictionary.local/#sense%201"))
        assertTrue(result.html.contains("https://dictionary.local/#\""))
        assertTrue(result.internalLinkTypes.contains("anchor"))
        assertTrue(result.internalLinkTypes.contains("same-origin"))
        assertFalse(result.html.contains("/lookup?q=%2Fabsolute"))
    }

    @Test
    fun inlineEventsJavascriptLinksAndDomGeneratedLinksRemainExecutableLocally() {
        val raw = """<a id="quick" href="javascript:this.className='expanded'"
                onclick="this.className='expanded'">quick</a>
                <script>var a=document.createElement('a');a.href='/generated?x=1#part';</script>"""
        val result = DictionaryHtmlRewriter.rewrite(raw)
        val document = secureHtmlDocument(result.html)

        assertTrue(result.internalLinkTypes.contains("javascript"))
        assertTrue(result.html.contains("onclick="))
        assertTrue(result.html.contains("javascript:this.className"))
        assertTrue(document.contains("document.createElement"))
        assertTrue(document.contains("script-src https://dictionary.local 'unsafe-inline'"))
    }

    @Test
    fun localScriptAndMddResourcesAreRewrittenButExternalScriptsRemainExternalAndBlockedByPolicy() {
        val result = DictionaryHtmlRewriter.rewrite(
            """<script src="scripts/quick.js"></script><img src="images/icon.png">
                <script src="https://example.com/external.js"></script>""",
        )

        assertTrue(result.html.contains("https://dictionary.local/scripts/quick.js"))
        assertTrue(result.html.contains("https://dictionary.local/images/icon.png"))
        assertTrue(result.html.contains("https://example.com/external.js"))
        assertTrue(
            DictionaryWebSecurityPolicy.intercept("https://example.com/external.js", false) is
                InterceptDecision.Blocked,
        )
    }

    @Test
    fun externalAndLocalDeviceSchemesNeverBecomeControlledDictionaryLinks() {
        val result = DictionaryHtmlRewriter.rewrite(
            """<a href="https://example.com/page">web</a><a href="file:///tmp/a">file</a>
                <a href="content://provider/item">content</a><a href="intent://open">intent</a>""",
        )

        assertTrue(result.internalLinkTypes.isEmpty())
        assertFalse(result.html.contains("dictionary.local/lookup"))
        listOf("https://example.com/page", "file:///tmp/a", "content://provider/item", "intent://open")
            .forEach { assertTrue(DictionaryWebSecurityPolicy.navigation(it) is NavigationDecision.Blocked) }
    }

    @Test
    fun audioActionsAndUnsafeMddTraversalKeepTheirExistingBoundaries() {
        val result = DictionaryHtmlRewriter.rewrite(
            """<a href="sound://uk/click.mp3">sound</a><audio src="sound://uk/word.mp3"></audio>
                <a href="sound://../secret.mp3">bad</a>""",
        )

        assertEquals(listOf("/uk/click.mp3", "/uk/word.mp3"), result.embeddedAudioPaths)
        assertTrue(result.html.contains("/action/audio?source=mdd&amp;value=%2Fuk%2Fclick.mp3"))
        assertTrue(result.html.contains("https://dictionary.local/uk/word.mp3"))
        assertFalse(result.html.contains("value=..%2Fsecret.mp3"))
    }
}
