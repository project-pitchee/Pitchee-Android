package io.rovly.pitchee

import io.rovly.pitchee.ui.readingPassages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingPassagesTest {
    @Test
    fun includesElevenBilingualPassages() {
        assertEquals(11, readingPassages.size)
        assertTrue(readingPassages.all { it.titleZh.isNotBlank() && it.titleEn.isNotBlank() })
        assertTrue(readingPassages.all { it.textZh.isNotBlank() && it.textEn.isNotBlank() })
        assertTrue(readingPassages.any { it.pages("zh").size > 1 })
        assertTrue(readingPassages.any { it.pages("en").size > 1 })
    }
}
