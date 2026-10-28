package com.blw.documentreader.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

class PageSelectionTest {
    @Test fun parsesRangesAndDeduplicates() = assertEquals(listOf(1, 4, 9, 10, 11), parsePageSelection("2, 5, 10–12, 5", 20))
    @Test(expected = IllegalArgumentException::class) fun rejectsOutOfBounds() { parsePageSelection("1,7", 6) }
    @Test(expected = IllegalArgumentException::class) fun rejectsEmpty() { parsePageSelection("", 6) }
    @Test(expected = IllegalArgumentException::class) fun rejectsReverseRange() { parsePageSelection("5-2", 6) }
}
