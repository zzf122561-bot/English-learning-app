package com.xuesui.englishapp.dictionary.feature

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

enum class DictionaryPresentation {
    FULL,
    QUICK_LOOKUP,
}

@Composable
@Suppress("UNUSED_PARAMETER")
fun DictionaryFeature(
    initialQuery: String? = null,
    presentation: DictionaryPresentation = DictionaryPresentation.FULL,
    onClose: () -> Unit = {},
) {
    Text(
        text = initialQuery
            ?.takeIf(String::isNotBlank)
            ?.let { "词典模块初始化中：$it" }
            ?: "词典模块初始化中",
    )
}
