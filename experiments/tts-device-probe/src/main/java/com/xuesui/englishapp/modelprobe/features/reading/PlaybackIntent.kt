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
    fun ready(available: Set<Int>): Boolean = desired && paragraph in available
}
