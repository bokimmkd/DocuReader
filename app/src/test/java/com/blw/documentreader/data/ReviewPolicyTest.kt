package com.blw.documentreader.data
import com.blw.documentreader.reviews.ReviewPolicy
import org.junit.Assert.*
import org.junit.Test
class ReviewPolicyTest {
    private val day = 86_400_000L
    @Test fun requiresAgeAndThreeObservedDates() {
        assertFalse(ReviewPolicy.eligible(8*day, day, 2, 0))
        assertFalse(ReviewPolicy.eligible(7*day, day, 3, 0))
        assertTrue(ReviewPolicy.eligible(8*day, day, 3, 0))
    }
    @Test fun cooldownAndClockRollback() {
        assertFalse(ReviewPolicy.eligible(40*day, day, 3, 11*day))
        assertTrue(ReviewPolicy.eligible(41*day, day, 3, 11*day))
        assertFalse(ReviewPolicy.eligible(40*day, day, 3, 42*day))
        assertFalse(ReviewPolicy.eligible(day, 2*day, 3, 0))
    }
}
