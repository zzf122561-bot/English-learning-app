package com.xuesui.englishapp.modelprobe.runtime

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.xuesui.englishapp.modelprobe.features.reading.*
import com.xuesui.englishapp.modelprobe.features.speech.PlaybackPhase
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** UI callback/visibility checks with synthetic text and one real Session/Player, without
 * a model or audible playback. Not a service/notification/lockscreen integration test. */
class PlayerSurfaceDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun narrow320PlayerKeepsSessionAcrossCollapseAndRoutesControls() = checkSurface(320, 640, 1f)
    @Test fun largeFont360ControlsRemainReachable() = checkSurface(360, 640, 2f)
    @Test fun landscapeControlsRemainScrollable() = checkSurface(640, 320, 1.8f)

    private fun checkSurface(width: Int, height: Int, fontScale: Float) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val intent = requireNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
        val target = readingTarget("fixture-document", SpeechReadingRequest("fixture", "A long fixture article title that must wrap without hiding playback controls",
            listOf("First synthetic paragraph.", "Second synthetic paragraph.", "Third synthetic paragraph."), SpeechReadingScope.FULL))
        val available = PlayAvailability(target, mapOf(0 to "one", 1 to "two", 2 to "three"), "fixture-settings")
        val navigation = PlaybackIntent("fixture", listOf(0, 1, 2)).also { it.pause() }
        var presentation by mutableStateOf(GlobalPlayback(targetKey = "fixture", paragraph = 0, desired = false,
            phase = PlaybackPhase.PAUSED, label = "播放已暂停", title = target.plan.input.title, selection = available,
            sequence = navigation.indexes, runId = "one", duration = 2000, position = 400))
        var expanded by mutableStateOf(true)
        lateinit var player: ExoPlayer
        lateinit var session: MediaSession
        ActivityScenario.launch<ComponentActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                player = ExoPlayer.Builder(context).build()
                session = MediaSession.Builder(context, player).build()
                activity.setContent {
                    MaterialTheme {
                        val density = LocalDensity.current.density
                        CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                            Box(Modifier.width(width.dp).height(height.dp)) {
                                if (!expanded) TextButton({ expanded = true }) { Text("展开测试播放器") }
                                else ExpandedPlayerContent(presentation, LedgerState(), { expanded = false },
                                    { navigation.resume(); presentation = presentation.copy(desired = true) },
                                    { position, _ -> presentation = presentation.copy(position = position) },
                                    { value -> player.setPlaybackSpeed(value); presentation = presentation.copy(speed = value) },
                                    { navigation.adjacent(-1)?.let { navigation.select(it); presentation = presentation.copy(paragraph = it) } },
                                    { navigation.adjacent(1)?.let { navigation.select(it); presentation = presentation.copy(paragraph = it, runId = "two", position = 0) } },
                                    { index -> navigation.select(index); presentation = presentation.copy(paragraph = index) },
                                    { player.pause() }, Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }
            try {
                compose.onNodeWithTag("speech-player-scroll").performScrollToNode(hasText("继续播放"))
                compose.onNodeWithText("继续播放").assertIsDisplayed()
                compose.onNodeWithContentDescription("播放进度").performScrollTo()
                    .performSemanticsAction(SemanticsActions.SetProgress) { it(1000f) }
                compose.runOnIdle { assertEquals(1000L, presentation.position); assertFalse(player.playWhenReady) }
                compose.onNodeWithTag("speech-player-scroll").performScrollToNode(hasText("1.5×"))
                compose.onNodeWithText("1.5×").performClick()
                compose.runOnIdle { assertEquals(1.5f, player.playbackParameters.speed, 0f); assertSame(player, session.player) }
                compose.onNodeWithTag("speech-player-scroll").performScrollToNode(hasText("下一段"))
                compose.onNodeWithText("下一段").performClick()
                compose.runOnIdle { assertEquals(1, presentation.paragraph); assertFalse(navigation.desired) }
                compose.onNodeWithText("收起").performClick()
                compose.runOnIdle { assertSame(player, session.player); assertFalse(player.playWhenReady); assertEquals(0L, presentation.position) }
                compose.onNodeWithText("展开测试播放器").performClick()
                compose.onNodeWithText("第2段 / 共3段").assertExists()
                compose.runOnIdle { assertSame(player, session.player); assertEquals(1.5f, presentation.speed, 0f) }
            } finally {
                instrumentation.runOnMainSync { session.release(); player.release() }
            }
        }
    }
}
