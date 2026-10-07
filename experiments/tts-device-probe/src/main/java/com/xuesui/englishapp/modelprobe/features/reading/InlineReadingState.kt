package com.xuesui.englishapp.modelprobe.features.reading

import com.xuesui.englishapp.modelprobe.core.storage.SavedRun

internal data class InlineReadingState(
    val target: ReadingTarget? = null,
    val latest: SavedRun? = null,
    val matchesSettings: Boolean = false,
    val switching: Boolean = false,
    val completed: Int = 0,
    val planned: Int = 0,
    val activeItem: String = "",
    val error: String = "",
)
