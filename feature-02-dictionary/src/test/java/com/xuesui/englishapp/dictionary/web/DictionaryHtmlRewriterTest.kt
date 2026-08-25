package com.xuesui.englishapp.dictionary.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryHtmlRewriterTest {
    @Test
    fun rewritesRecognizedInternalAndMddReferencesToControlledOrigin() {
        val result = DictionaryHtmlRewriter.rewrite(
            """<a href="entry://ice%20cream">entry</a><a href='bword://apple'>bword</a>
                <a href="relative target">relative</a><a href="#sense%201">anchor</a><img src="images/icon.png">
                <a href="sound://uk/click.mp3">sound</a><audio src="sound://uk/word.mp3"></audio>""",
        )

        assertEquals(setOf("entry", "bword", "relative", "anchor"), result.internalLinkTypes)
        assertEquals(listOf("/uk/click.mp3", "/uk/word.mp3"), result.embeddedAudioPaths)
        assertTrue(result.html.contains("https://dictionary.local/lookup?q=ice%20cream"))
        assertTrue(result.html.contains("https://dictionary.local/uk/word.mp3"))
        assertTrue(result.html.contains("https://dictionary.local/#sense%201"))
        assertTrue(result.html.contains("/action/audio?source=mdd&amp;value=%2Fuk%2Fclick.mp3"))
    }

    @Test
    fun rejectsTraversalAndLeavesUnsafeSchemesOutsideControlledOrigin() {
        val result = DictionaryHtmlRewriter.rewrite(
            """<a href="javascript:alert(1)">bad</a><img src="../secret.png"><audio src="file:///tmp/a.mp3">""",
        )

        assertTrue(result.internalLinkTypes.isEmpty())
        assertTrue(result.embeddedAudioPaths.isEmpty())
        assertFalse(result.html.contains("dictionary.local"))
    }

    @Test
    fun extractsOnlyStrictHttpsAudioWithoutRewritingItAsMdd() {
        val result = DictionaryHtmlRewriter.rewrite(
            """<a href="https://audio.example/click.mp3">listen</a>
                <source src="https://audio.example/word.mp3"><source src="http://audio.example/word.mp3">""",
        )

        assertEquals(listOf("https://audio.example/click.mp3", "https://audio.example/word.mp3"), result.httpsAudioUrls)
        assertTrue(result.embeddedAudioPaths.isEmpty())
        assertTrue(result.html.contains("/action/audio?source=https&amp;value=https%3A%2F%2Faudio.example%2Fclick.mp3"))
    }

    @Test
    fun emptyOrDangerousAnchorsAndAudioNeverBecomeControlledActions() {
        val result = DictionaryHtmlRewriter.rewrite(
            """<a href="#">empty</a><a href="#%00bad">bad</a>
                <a href="sound://../secret.mp3">traversal</a><a href="http://audio.example/a.mp3">http</a>""",
        )

        assertFalse(result.html.contains("/action/audio"))
        assertFalse(result.html.contains("https://dictionary.local/#"))
        assertTrue(result.embeddedAudioPaths.isEmpty())
    }
}
