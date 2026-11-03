package app.discipline

import app.discipline.update.Updater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {
    @Test fun versionOrdering() {
        assertTrue(Updater.isNewer("1.1.0", "1.0.0"))
        assertTrue(Updater.isNewer("v1.10.0", "1.9.2"))
        assertTrue(Updater.isNewer("1.1", "1.0.9"))
        assertFalse(Updater.isNewer("1.0.0", "1.0.0"))
        assertFalse(Updater.isNewer("1.0.0", "1.1.0"))
        assertTrue(Updater.isNewer("1.1.0", "1.1.0-beta1"))
        assertFalse(Updater.isNewer("1.1.0-beta1", "1.1.0"))
    }

    @Test fun notesAreCleaned() {
        val md = "## What's new\n- **Barcode** scanning with `Open Food Facts`\n- See [docs](https://x.y)\n\n\n\n**Full Changelog**: a...b"
        assertEquals("What's new\n• Barcode scanning with Open Food Facts\n• See docs", Updater.cleanNotes(md))
    }
}
