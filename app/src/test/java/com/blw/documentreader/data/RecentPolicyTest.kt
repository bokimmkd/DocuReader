package com.blw.documentreader.data

import org.junit.Assert.*
import org.junit.Test

class RecentPolicyTest {
    @Test fun thirtyDayBoundaryAndClearedHistory() {
        val now = 100L * 24 * 60 * 60 * 1000
        val doc = DocumentEntry("content://sample/one", "one.pdf", "PDF", openedAt = now - DocumentEntry.RECENT_WINDOW)
        assertTrue(doc.isRecent(now))
        assertFalse(doc.copy(openedAt = doc.openedAt - 1).isRecent(now))
        assertFalse(doc.copy(openedAt = 0).isRecent(now))
        assertTrue(doc.copy(openedAt = now).isRecent(now))
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
