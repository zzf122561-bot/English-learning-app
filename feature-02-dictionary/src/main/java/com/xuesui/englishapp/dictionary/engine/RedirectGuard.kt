package com.xuesui.englishapp.dictionary.engine

import java.util.Locale

internal class RedirectGuard(private val maxDepth: Int = 16) {
    private val visited = linkedSetOf<String>()

    fun enter(value: String) {
        val key = value.replace('/', '\\').lowercase(Locale.ROOT)
        if (!visited.add(key) || visited.size > maxDepth) {
            throw MdictEngineException("Redirect cycle or depth limit exceeded")
        }
    }
}

