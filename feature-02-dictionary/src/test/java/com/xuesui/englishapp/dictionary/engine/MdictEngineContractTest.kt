package com.xuesui.englishapp.dictionary.engine

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MdictEngineContractTest {
    @Test
    fun sourceRequiresMdxExtension() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            MdictSource(File("dictionary.txt"))
        }

        assertEquals(
            "Dictionary source must be an MDX file: dictionary.txt",
            error.message,
        )
    }

    @Test
    fun sourceRequiresMddResourceExtensions() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            MdictSource(
                mdxFile = File("dictionary.mdx"),
                mddFiles = listOf(File("dictionary.zip")),
            )
        }

        assertEquals("Every resource source must be an MDD file", error.message)
    }
}
