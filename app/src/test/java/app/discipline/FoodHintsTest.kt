package app.discipline

import app.discipline.vision.FoodGuess
import app.discipline.vision.FoodHints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FoodHintsTest {
    @Test fun gramsAndWords() {
        val h = FoodHints.parse("Chicken curry with rice, about 350 g")
        assertEquals(350.0, h.grams!!, 0.01)
        assertEquals("chicken curry rice", h.text)
    }

    @Test fun kilograms() {
        assertEquals(1200.0, FoodHints.parse("1.2kg roast").grams!!, 0.01)
    }

    @Test fun servingsAndSize() {
        val h = FoodHints.parse("2 slices, large")
        assertEquals(2.0, h.servings!!, 0.01)
        assertEquals(1.4, h.sizeFactor, 0.01)
        assertEquals(2.75, h.servingsFor(null), 0.01) // 2.8 rounded to the nearest quarter
    }

    @Test fun wordNumbers() {
        val h = FoodHints.parse("two eggs")
        assertEquals(2.0, h.servings!!, 0.01)
        assertEquals("eggs", h.text)
    }

    @Test fun gramsUseServingWeight() {
        val h = FoodHints.parse("300g")
        assertEquals(3.0, h.servingsFor(100.0), 0.01)
        assertNull(h.servings)
    }

    @Test fun rerankPrefersTypedWords() {
        val guesses = listOf(FoodGuess("Pad thai", 0.6f), FoodGuess("Chow mein", 0.3f), FoodGuess("Ramen", 0.1f))
        assertEquals("Ramen", FoodHints.parse("ramen with egg").rerank(guesses).first().label)
        assertEquals("Pad thai", FoodHints.parse("").rerank(guesses).first().label)
    }

    @Test fun queryFallsBackToTopGuess() {
        assertEquals("Pizza", FoodHints.parse("large").query(FoodGuess("Pizza", 0.9f)))
        assertEquals("margherita", FoodHints.parse("margherita").query(FoodGuess("Pizza", 0.9f)))
    }

    @Test fun dishesRankAboveSauces() {
        val q = "Neapolitan pizza"
        val names = listOf("Neapolitan pizza sauce", "Neapolitan Pizza Seasoning", "Pizza margherita", "Sacredly authentic neapolitan pizza")
        val ranked = names.sortedByDescending { FoodHints.score(q, it) }
        assertEquals("Sacredly authentic neapolitan pizza", ranked[0])
        assertEquals("Pizza margherita", ranked[1])
    }

    @Test fun searchesAddHeadNoun() {
        assertEquals(listOf("Neapolitan pizza", "pizza"), FoodHints.searches("Neapolitan pizza"))
        assertEquals(listOf("Ramen"), FoodHints.searches("Ramen"))
    }
}
