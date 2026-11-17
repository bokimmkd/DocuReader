package com.blw.documentreader.data

import org.junit.Assert.*
import org.junit.Test

class RecentPolicyTest {
    @Test fun thirtyDayBoundaryAndClearedHistory() {
        val now = 100L * 24 * 60 * 60 * 1000
        val doc = DocumentEntry("content://sample/one", "one.pdf", "PDF", openedAt = now - 30L * 24 * 60 * 60 * 1000)
        assertTrue(doc.isRecent(now))
        assertFalse(doc.copy(openedAt = doc.openedAt - 1).isRecent(now))
        assertFalse(doc.copy(openedAt = 0).isRecent(now))
        assertTrue(doc.copy(openedAt = now).isRecent(now))
        assertFalse(doc.isRecent(now, days = 7))
        assertTrue(doc.isRecent(now, days = 90))
        val yesterday = doc.copy(openedAt = now - 86_400_000L)
        assertTrue(yesterday.isRecent(now, days = 1))
        assertFalse(yesterday.isRecent(now + 1, days = 1))
        assertFalse(doc.copy(openedAt = 0).isRecent(now, days = 365))
    }
    @Test fun temporaryIsNotSavedAndSavedSurvivesHistoryExpiry() {
        val doc = DocumentEntry("content://sample/one", "one.pdf", "PDF", openedAt = 1, temporaryCopy = true)
        assertFalse(doc.isSaved)
        assertTrue(doc.copy(inLibrary = true).isSaved)
        assertTrue(doc.copy(savedLocally = true).isSaved)
        assertTrue(doc.copy(favorite = true).isSaved)
        assertTrue(doc.copy(addedAt = 1).isSaved)
    }
}
