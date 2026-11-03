package app.discipline

import app.discipline.data.food.Barcodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodesTest {
    @Test fun upcEExpands() {
        // Published examples: 04252614 -> 042100005264, 01234565 -> 012345000065
        assertEquals("042100005264", Barcodes.upcEToA("04252614"))
        assertEquals("012345000065", Barcodes.upcEToA("01234565"))
    }

    @Test fun variantsIncludeEanForUpcA() {
        val v = Barcodes.variants("737628064502")
        assertEquals("737628064502", v.first())
        assertTrue("0737628064502" in v)
    }

    @Test fun variantsStripLeadingZero() {
        assertTrue("737628064502" in Barcodes.variants("0737628064502"))
    }

    @Test fun checksum() {
        assertTrue(Barcodes.checksumOk("3017620422003"))
        assertFalse(Barcodes.checksumOk("3017620422004"))
        assertTrue(Barcodes.checksumOk("042100005264"))
    }

    @Test fun rejectsShortInput() {
        assertTrue(Barcodes.variants("123").isEmpty())
    }
}
