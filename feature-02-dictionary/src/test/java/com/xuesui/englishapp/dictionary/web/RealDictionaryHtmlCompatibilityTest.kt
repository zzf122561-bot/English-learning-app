package com.xuesui.englishapp.dictionary.web

import com.xuesui.englishapp.dictionary.audio.AudioPayload
import com.xuesui.englishapp.dictionary.audio.EmbeddedAudioPlayer
import com.xuesui.englishapp.dictionary.audio.EmbeddedAudioProvider
import com.xuesui.englishapp.dictionary.audio.HttpsAudioPlayer
import com.xuesui.englishapp.dictionary.audio.PronunciationCoordinator
import com.xuesui.englishapp.dictionary.audio.PronunciationSource
import com.xuesui.englishapp.dictionary.audio.SpeechSynthesizer
import com.xuesui.englishapp.dictionary.engine.MdictSource
import com.xuesui.englishapp.dictionary.engine.PureKotlinMdictEngine
import java.io.File
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class RealDictionaryHtmlCompatibilityTest {
    @Test
    fun realEntryLinksAreRewrittenAndAudioReferencesDriveMddOrDocumentedFallback() = runBlocking {
        authorizedDictionaries().forEach { files ->
            PureKotlinMdictEngine.open(MdictSource(files.mdx, listOf(files.mdd))).use { engine ->
                val internalTypes = linkedSetOf<String>()
                val lookupTargets = linkedSetOf<String>()
                val audioPaths = linkedSetOf<String>()
                val httpsAudio = linkedSetOf<String>()
                candidateHeadwords(engine).forEach { query ->
                    val entry = engine.exactLookup(query) ?: return@forEach
                    val rewritten = DictionaryHtmlRewriter.rewrite(entry.html)
                    internalTypes += rewritten.internalLinkTypes
                    audioPaths += rewritten.embeddedAudioPaths
                    httpsAudio += rewritten.httpsAudioUrls
                    Regex("https://dictionary\\.local/lookup\\?q=[^\\\"'<>\\s]+")
                        .findAll(rewritten.html)
                        .mapNotNull { match ->
                            (DictionaryWebSecurityPolicy.navigation(match.value) as? NavigationDecision.InternalLookup)?.query
                        }
                        .forEach(lookupTargets::add)
                }

                assertTrue("No real internal links were rewritten for ${files.label}", internalTypes.isNotEmpty())
                val exactTargetHit = lookupTargets.asSequence().take(128).any { engine.exactLookup(it) != null }
                assertTrue("No rewritten real internal link resolved for ${files.label}", exactTargetHit)

                val embeddedHit = audioPaths.asSequence().mapNotNull(engine::readResource).firstOrNull()
                if (audioPaths.isNotEmpty()) {
                    assertTrue("Real MDD audio references did not resolve for ${files.label}", embeddedHit != null)
                }
                val mddAudioResources = engine.resourceKeys().count { key ->
                    key.substringAfterLast('.', "").lowercase(Locale.ROOT) in setOf("mp3", "wav", "ogg", "m4a", "aac")
                }
                if (audioPaths.isEmpty()) {
                    assertTrue(
                        "No sampled entry audio link but MDD contains audio resources for ${files.label}",
                        mddAudioResources == 0,
                    )
                }
                val coordinator = PronunciationCoordinator(
                    embeddedProvider = EmbeddedAudioProvider { paths ->
                        paths.asSequence().mapNotNull(engine::readResource).firstOrNull()?.let {
                            AudioPayload(it.bytes, it.mediaType)
                        }
                    },
                    embeddedPlayer = object : EmbeddedAudioPlayer {
                        override suspend fun play(payload: AudioPayload) = true
                        override fun close() = Unit
                    },
                    httpsPlayer = object : HttpsAudioPlayer {
                        override suspend fun play(url: String) = false
                        override fun close() = Unit
                    },
                    speechSynthesizer = object : SpeechSynthesizer {
                        override suspend fun speak(word: String) = true
                        override fun close() = Unit
                    },
                )
                val fallback = coordinator.pronounce("evidence", audioPaths.toList(), httpsAudio.toList())
                coordinator.close()
                if (embeddedHit == null && httpsAudio.isEmpty()) assertTrue(fallback == PronunciationSource.TTS)
                println(
                    "MDICT_HTML dictionary=${files.label} linkTypes=${internalTypes.sorted().joinToString("+")} " +
                        "internalExactHit=$exactTargetHit audioReferences=${audioPaths.size} " +
                        "mddAudioResources=$mddAudioResources " +
                        "resource=${if (embeddedHit == null) "missing" else "hit"} fallback=${fallback?.name ?: "NONE"}",
                )
            }
        }
    }

    private fun candidateHeadwords(engine: PureKotlinMdictEngine): List<String> {
        val common = listOf("abandon", "apple", "dictionary", "good", "run", "test", "word")
        val count = engine.metadata.entryCount.toInt()
        val sampled = (0 until 512).map { sample ->
            val index = ((count - 1).toLong() * sample / 511).toInt()
            engine.headwordAt(index).trim().trim('\uFEFF')
        }
        return (common + sampled).filter(String::isNotBlank).distinctBy { it.lowercase(Locale.ROOT) }
    }

    private fun authorizedDictionaries(): List<DictionaryFiles> {
        val root = locateProjectRoot()
        return listOf(
            DictionaryFiles(
                "collins",
                File(root, "dictionary-users/柯林斯双解学习词典/柯林斯高阶英汉双解学习词典（好看）.mdx"),
                File(root, "dictionary-users/柯林斯双解学习词典/柯林斯高阶英汉双解学习词典（好看）.mdd"),
            ),
            DictionaryFiles(
                "oxford9",
                File(root, "dictionary-users/牛津9英英(推荐)/Oxford ALD_9th_En-En.mdx"),
                File(root, "dictionary-users/牛津9英英(推荐)/Oxford ALD_9th_En-En.mdd"),
            ),
        ).also { dictionaries ->
            assertTrue("Authorized dictionaries are missing", dictionaries.all { it.mdx.isFile && it.mdd.isFile })
        }
    }

    private fun locateProjectRoot(): File {
        val start = File(System.getProperty("user.dir") ?: ".").absoluteFile
        return generateSequence(start) { it.parentFile }.take(3).firstOrNull {
            File(it, "dictionary-users/牛津9英英(推荐)/Oxford ALD_9th_En-En.mdx").isFile
        } ?: start
    }

    private data class DictionaryFiles(val label: String, val mdx: File, val mdd: File)
}
