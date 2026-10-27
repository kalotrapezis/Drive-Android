package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Test

/** The same cases as the computer's (desktop/test/notes.test.js, "notes: Markdown export"). */
class NoteExportTest {
    private val day = 1_790_000_000_000L // 2026-09-21T14:13:20Z
    private fun note(title: String, content: String = "", labels: List<String> = emptyList(), items: List<CheckItem> = emptyList(),
                     archived: Boolean = false, trashed: Boolean = false, created: Long = day) =
        Note("id-$title-$created", title, content, items.isNotEmpty(), items, labels, null, false,
            if (archived) day else null, if (trashed) day else null, created, created + 60_000)

    @Test fun `a text note is its title as a heading under the front matter, then its Markdown`() {
        val (name, text) = NoteExport.markdown(note("Trip", "**Pack** the tent\n\n", labels = listOf("Holidays", "Say \"hi\"")), "Untitled")
        assertEquals("Trip", name)
        assertEquals("---\ntags: [\"Holidays\", \"Say \\\"hi\\\"\"]\ncreated: 2026-09-21T14:13:20Z\nupdated: 2026-09-21T14:14:20Z\n---\n\n# Trip\n\n**Pack** the tent\n", text)
    }

    @Test fun `a checklist is open items in their order, then the done ones`() {
        val items = listOf(CheckItem("a", "Bread", true, 0), CheckItem("b", "Milk", false, 1), CheckItem("c", "Eggs", false, 0))
        val (_, text) = NoteExport.markdown(note("Shopping", items = items), "Untitled")
        assertEquals("# Shopping\n\n- [ ] Eggs\n- [ ] Milk\n- [x] Bread\n", text.substringAfter("---\n\n"))
    }

    @Test fun `a title a file system refuses is made safe, and an untitled note has no heading`() {
        assertEquals("Ideas - week 3-4", NoteExport.markdown(note("Ideas: week 3/4"), "Untitled").first)
        assertEquals("a-b c", NoteExport.markdown(note("a\\b*c"), "Untitled").first)
        assertEquals("hidden", NoteExport.markdown(note(".hidden"), "Untitled").first)
        val (name, text) = NoteExport.markdown(note("", "just text"), "Χωρίς τίτλο")
        assertEquals("Χωρίς τίτλο", name)
        assertEquals("just text\n", text.substringAfter("---\n\n"))
        assertEquals("# Same\n\nmore\n", NoteExport.markdown(note("Same", "# Same\n\nmore"), "Untitled").second.substringAfter("---\n\n"))
    }

    @Test fun `an export's folder is named by its local time, in English`() {
        assertEquals("Tetra-Notes-export-2026-10-27-2105", NoteExport.folder(java.time.LocalDateTime.of(2026, 10, 27, 21, 5)))
    }

    @Test fun `all notes go in a folder per label, nested by its slashes, untagged at the top, archived and trashed in their own`() {
        val files = NoteExport.files(listOf(
            note("Ideas", "two", labels = listOf("Work", "Home"), created = day + 2),
            note("Ideas", "one", labels = listOf("Work"), created = day + 1),
            note("ideas", "three", labels = listOf("Work"), created = day + 3),
            note("Loose"),
            note("Old", archived = true),
            note("Gone", labels = listOf("Work"), trashed = true),
            note("Bed", labels = listOf("Home/Garden", "/Work//")),
        ), "Untitled")
        assertEquals(listOf("Loose.md", "Archive/Old.md", "Trash/Work/Gone.md", "Home/Garden/Bed.md", "Work/Bed.md",
            "Work/Ideas.md", "Work/Ideas (2).md", "Home/Ideas.md", "Work/ideas (3).md"), files.map { it.path })
        assertEquals("one\n", files.first { it.path == "Work/Ideas.md" }.text.substringAfter("# Ideas\n\n"))
        assertEquals(day + 2 + 60_000, files.first { it.path == "Home/Ideas.md" }.at)
    }
}
