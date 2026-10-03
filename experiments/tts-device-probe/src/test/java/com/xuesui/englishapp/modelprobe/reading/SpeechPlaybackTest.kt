package com.xuesui.englishapp.modelprobe.reading

import com.xuesui.englishapp.modelprobe.features.speech.*
import com.xuesui.englishapp.modelprobe.stage2.rejected
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class SpeechPlaybackTest {
    private class Session(val events: PlaybackEvents) : AudioSession {
        var plays = 0; var pauses = 0; var releases = 0; var speed = 0f; var position = 0; var fail = false
        override fun prepare() {}
        override fun play(speed: Float) { if (fail) error("Unsupported rate"); this.speed = speed; plays++ }
        override fun pause() { pauses++ }
        override fun release() { releases++ }
    }
    private val sessions = mutableListOf<Session>()
    private val playback = SpeechPlayback(AudioFactory { _, events -> Session(events).also(sessions::add) }) { }
    @Test fun pauseResumeKeepsSameAudioPositionAndReplayCreatesFreshSession() {
        playback.play(File("synthetic.wav"), "run-1")
        val session = sessions.single(); session.events.prepared(); session.position = 48
        playback.pause(); assertEquals(PlaybackPhase.PAUSED, playback.state.phase)
        playback.setSpeed(0.75f); assertEquals(1, session.plays)
        playback.resume(); assertEquals(48, session.position); assertEquals(0.75f, session.speed, 0f)
        assertEquals(2, session.plays); assertEquals(1, sessions.size)
        playback.play(File("synthetic.wav"), "run-1"); assertEquals(2, sessions.size); assertEquals(1, session.releases)
    }
    @Test fun pauseBeforePreparedNeverStartsAudioUntilExplicitResume() {
        playback.play(File("synthetic.wav"), "run-1"); playback.pause()
        val session = sessions.single(); session.events.prepared()
        assertEquals(0, session.plays); assertEquals(PlaybackPhase.PAUSED, playback.state.phase)
        playback.resume(); assertEquals(1, session.plays)
    }
    @Test fun obsoleteCallbacksCannotResumeAfterExitOrOverwriteNewPlayback() {
        playback.play(File("one.wav"), "old"); val old = sessions.single()
        playback.stop(); old.events.prepared(); assertEquals(0, old.plays); assertEquals(PlaybackPhase.IDLE, playback.state.phase)
        playback.play(File("two.wav"), "new"); sessions.last().events.prepared()
        old.events.failed("late"); old.events.completed(); old.events.interrupted()
        assertEquals(PlaybackPhase.PLAYING, playback.state.phase); assertEquals("new", playback.state.runId)
    }
    @Test fun audioFocusLossPausesAndUnsupportedSpeedCannotPretendToPlay() {
        playback.play(File("fixture.wav"), "run"); val session = sessions.single(); session.events.prepared()
        session.events.interrupted(); assertEquals(PlaybackPhase.PAUSED, playback.state.phase)
        session.fail = true; playback.resume()
        assertEquals(PlaybackPhase.FAILED, playback.state.phase); assertEquals(1, session.releases)
    }
    @Test fun speedRangeRejectsInvalidValuesAndCompletionReleasesResources() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, 0.4f, 2.1f).forEach { rejected { playback.setSpeed(it) } }
        playback.setSpeed(2f); playback.play(File("fixture.wav"), "run")
        val session = sessions.single(); session.events.prepared(); assertEquals(2f, session.speed, 0f)
        session.events.completed(); assertEquals(PlaybackPhase.COMPLETED, playback.state.phase); assertEquals(1, session.releases)
    }
}
