package com.xuesui.englishapp.modelprobe.features.reading

/** No Android/player dependencies: late completions may fill availability but never change
 * the user's intent. Existing-only freezes explicit indexes; fill mode waits at every gap. */
internal class PlaybackIntent(val key: String, val indexes: List<Int>) {
    init { require(indexes.isNotEmpty() && indexes.distinct().size == indexes.size) }
    var cursor = 0; private set
    var desired = true; private set
    val paragraph get() = indexes.getOrNull(cursor)
    fun pause() { desired = false }
    fun resume() { if (paragraph != null) desired = true }
    fun ended() { cursor++; if (paragraph == null) desired = false }
    fun select(index: Int, play: Boolean = desired): Boolean {
        val next = indexes.indexOf(index)
        if (next < 0) return false
        cursor = next; desired = play
        return true
    }
    fun adjacent(direction: Int): Int? = indexes.getOrNull(cursor + direction)
    fun ready(available: Set<Int>): Boolean = desired && paragraph in available
}

/** Unknown duration is not seekable; dragging never changes the play/pause intention. */
internal fun boundedSeek(position: Long, duration: Long): Long? =
    if (duration <= 0) null else position.coerceIn(0, duration)
