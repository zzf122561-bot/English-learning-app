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
                <a href="relative target">relative</a><img src="images/icon.png">
                <audio src="sound://uk/word.mp3"></audio>""",
        )

        assertEquals(setOf("entry", "bword", "relative"), result.internalLinkTypes)
        assertEquals(listOf("/uk/word.mp3"), result.embeddedAudioPaths)
        assertTrue(result.html.contains("https://dictionary.local/lookup?q=ice%20cream"))
        assertTrue(result.html.contains("https://dictionary.local/uk/word.mp3"))
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
            """<source src="https://audio.example/word.mp3"><source src="http://audio.example/word.mp3">""",
        )

        assertEquals(listOf("https://audio.example/word.mp3"), result.httpsAudioUrls)
        assertTrue(result.embeddedAudioPaths.isEmpty())
    }
}
