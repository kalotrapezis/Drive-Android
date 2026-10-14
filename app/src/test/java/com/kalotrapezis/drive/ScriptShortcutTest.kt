package com.kalotrapezis.drive

import org.junit.Assert.assertEquals
import org.junit.Test

class ScriptShortcutTest {
    private fun shown(w: String) = ScriptShortcut.find(w).let { ScriptShortcut.shown(w, it) + " " + it.map { r -> (if (r.up) "^" else "_") + w.substring(r.from, r.to) } }

    @Test fun `powers and indices`() {
        assertEquals("22 [^2]", shown("2^2"))
        assertEquals("x10 [^10]", shown("x^10"))
        assertEquals("H2O [_2]", shown("H_2O"))
        assertEquals("ei+1 [^i+1]", shown("e^(i+1)"))
        assertEquals("xin [_i, ^n]", shown("x_i^n"))
        assertEquals("m² [^2]".replace("²", "2"), shown("m^2"))
    }

    @Test fun `what is no formula stays as typed`() {
        assertEquals(emptyList<ScriptShortcut.Raise>(), ScriptShortcut.find("first_name"))
        assertEquals(emptyList<ScriptShortcut.Raise>(), ScriptShortcut.find("^2"))
        assertEquals(emptyList<ScriptShortcut.Raise>(), ScriptShortcut.find("a_"))
        assertEquals(emptyList<ScriptShortcut.Raise>(), ScriptShortcut.find("https://x.gr/a_b"))
        assertEquals(emptyList<ScriptShortcut.Raise>(), ScriptShortcut.find("my_file_name_2"))
    }
}
