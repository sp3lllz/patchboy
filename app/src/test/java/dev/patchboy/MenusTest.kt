package dev.patchboy

import dev.patchboy.app.QuickRow
import org.junit.Assert.assertEquals
import org.junit.Test

class MenusTest {
    @Test
    fun `every settings row is in exactly one touch section`() {
        val listed = QuickRow.SECTIONS.flatMap { it.second }
        assertEquals(QuickRow.entries.toList(), listed.sortedBy { it.ordinal })
    }
}
