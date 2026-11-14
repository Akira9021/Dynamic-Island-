package com.akira.island.island

import org.junit.Assert.*
import org.junit.Test

class ShortcutPresentationTest {
    @Test fun initialsHandleWhitespaceNonLatinAndSupplementaryLetters() {
        assertEquals("АН", shortcutInitials("  Анна   Николаева "))
        assertEquals("李小", shortcutInitials("李 小龙"))
        assertEquals("𐐀B", shortcutInitials("𐐨name bob"))
        assertEquals("?", shortcutInitials("   "))
        assertEquals("?", shortcutInitials("💚"))
    }
}
