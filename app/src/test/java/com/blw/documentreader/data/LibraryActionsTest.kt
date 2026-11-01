package com.blw.documentreader.data

import org.junit.Assert.*
import org.junit.Test

class LibraryActionsTest {
    @Test fun bulkOnlyTouchesSelectedIdentitiesAndKeepsNamesAndFavorites() {
        val a=DocumentEntry("content://a","Original.pdf","PDF",favorite=true,tags="Invoice, home")
        val b=DocumentEntry("content://b","Other.pdf","PDF",category="Work",tags="tax")
        val out=LibraryActions.change(listOf(a,b),setOf(a.uri),category="Finance",tags="invoice, 2026")
        assertEquals("Invoice, home, 2026",out[0].tags);assertTrue(out[0].favorite);assertEquals(a.name,out[0].name)
        assertEquals(b,out[1]);assertEquals("Finance",out[0].category)
    }
    @Test fun removeTagsDoesNotEraseUnrelatedTags() {
        assertEquals("home, 2026",LibraryActions.tags("Invoice, home, 2026","invoice",true))
    }
    @Test fun editingAfterBulkTaggingKeepsEveryAcceptedTag() {
        val tags = LibraryActions.tags("", (1..20).joinToString(", ") { "Tag $it" }, false)
        assertEquals(tags, DocumentNames.tags(tags))
        val longTag = "a".repeat(40)
        assertEquals(longTag, DocumentNames.tags(longTag))
    }
    @Test fun clearingCategoryDoesNotChangeFileIdentity() {
        val d=DocumentEntry("content://a","a.pdf","PDF",category="Work")
        val out=LibraryActions.change(listOf(d),setOf(d.uri),category="Uncategorized").single()
        assertEquals(d.uri,out.uri);assertEquals("Uncategorized",out.category)
    }
    @Test fun adsCountDocumentsRatherThanAdapterRows() {
        assertEquals(listOf(5,15,25,35), (1..36).filter(LibraryActions::adAfter))
    }
}
