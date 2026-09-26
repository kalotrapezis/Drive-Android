package com.kalotrapezis.drive

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class NotesTest {
    private fun store() = NotesStore(Files.createTempDirectory("notes").toFile())

    @Test fun `an edit keeps what a newer app wrote, and history is numbered`() {
        val s = store()
        val n = s.create(checklist = false)
        s.write(s.json(n.id)!!.put("future", "kept"))
        s.setText(n.id, "Shopping", "milk", null)
        assertTrue(s.snapshot(n.id)!!.matches(Regex("Shopping-\\d{4}-\\d\\d-\\d\\d-\\d\\d-\\d\\d-1\\.json")))
        s.setText(n.id, "Shopping", "milk, eggs", null)
        s.snapshot(n.id)
        assertNull("the same text is not kept twice", s.snapshot(n.id))
        val first = s.history(n.id).first { it.content == "milk" }
        s.restoreVersion(n.id, first.name)
        assertEquals("milk", s.get(n.id)!!.content)
        assertEquals("kept", s.json(n.id)!!.getString("future"))
        s.snapshot(n.id)
        s.setText(n.id, "Shopping", "bread", null); s.snapshot(n.id)
        assertEquals(listOf("bread", "milk", "milk, eggs"), s.history(n.id).map { it.content })
    }

    @Test fun `checklist items keep their other fields by id`() {
        val s = store()
        val n = s.create(checklist = true)
        s.write(s.json(n.id)!!.put("checklistItems", JSONArray().put(JSONObject().put("id", "a").put("text", "x").put("isChecked", false).put("order", 0).put("indentationLevel", 2))))
        s.setText(n.id, "", "", listOf(CheckItem("a", "milk", true, 0), CheckItem("b", "eggs", false, 1)))
        val items = s.json(n.id)!!.getJSONArray("checklistItems")
        assertEquals(2, items.getJSONObject(0).getInt("indentationLevel"))
        assertEquals(listOf("eggs", "milk"), s.get(n.id)!!.items.sortedForList().map { it.text })
    }

    @Test fun `the computer's answer - newer wins, a deletion beats an edit and never comes back`() {
        val s = store()
        s.write(JSONObject().put("id", "a").put("title", "mine").put("updatedAt", 20))
        s.write(JSONObject().put("id", "b").put("title", "mine").put("updatedAt", 10))
        s.write(JSONObject().put("id", "c").put("title", "doomed").put("updatedAt", 99))
        s.apply(JSONObject()
            .put("notes", JSONArray().put(JSONObject().put("id", "a").put("title", "older").put("updatedAt", 15))
                .put(JSONObject().put("id", "b").put("title", "newer").put("updatedAt", 30)).put(JSONObject().put("id", "c").put("updatedAt", 1000)))
            .put("deletions", JSONArray().put(JSONObject().put("id", "c").put("deletedAt", 50))))
        assertEquals("mine", s.get("a")!!.title)
        assertEquals("newer", s.get("b")!!.title)
        assertNull(s.get("c"))
        assertEquals(2, s.payload().getJSONArray("notes").length())
    }

    @Test fun `formatting and undo behave as on the computer`() {
        val on = NoteFormat.wrap(NoteFormat.Edit("a word here", 2, 6), "**")
        assertEquals(NoteFormat.Edit("a **word** here", 4, 8), on)
        assertEquals("a word here", NoteFormat.wrap(on, "**").text)
        assertEquals("1. a\n2. b", NoteFormat.prefix(NoteFormat.Edit("a\nb", 0, 3), "1. ").text)
        assertEquals("- [ ] one\n- [ ] two", NoteFormat.prefix(NoteFormat.Edit("one\n- two", 0, 9), "- [ ] ").text)
        val w = NoteUndo("")
        w.push("h", 1000); w.push("hi", 1100); w.push("hi ", 1200, wordDone = NoteUndo.endsWord("hi", "hi ", 3)); w.push("hi t", 1300); w.push("hi th", 1400)
        assertEquals("hi ", w.undo()); assertEquals("", w.undo())
        val u = NoteUndo("", quietMs = 700)
        u.push("h", 1000); u.push("he", 1100); u.step("**he**")
        assertEquals("he", u.undo()); assertEquals("", u.undo()); assertEquals("he", u.redo())
    }
}
