package app.discipline.vision

/**
 * What the user typed next to a food photo, e.g. "chicken curry with rice, about 350 g" or
 * "2 slices, large". Quantities are pulled out; the remaining words describe the food.
 */
data class FoodHints(
    /** Words describing the food, with quantities and filler removed. */
    val words: List<String>,
    val grams: Double? = null,
    val servings: Double? = null,
    /** From size words: small 0.7, medium 1, large 1.4. */
    val sizeFactor: Double = 1.0,
) {
    val text: String get() = words.joinToString(" ")

    /**
     * Servings of a food to log. Uses [grams] when the food has a serving weight, otherwise
     * [servings], scaled by [sizeFactor].
     */
    fun servingsFor(servingGrams: Double?): Double {
        val base = when {
            grams != null && servingGrams != null && servingGrams > 0 -> grams / servingGrams
            servings != null -> servings
            else -> 1.0
        }
        val v = if (grams != null && servingGrams != null) base else base * sizeFactor
        return (Math.round(v * 4) / 4.0).coerceIn(0.25, 50.0)
    }

    /**
     * Orders [guesses] so ones matching what the user typed come first. A guess matches when
     * it shares a word with the hint ("curry" matches "Chicken curry").
     */
    fun rerank(guesses: List<FoodGuess>): List<FoodGuess> {
        if (words.isEmpty()) return guesses
        val hint = words.map(::stem).toSet()
        return guesses.sortedByDescending { g ->
            val overlap = g.label.lowercase().split(' ', '-', ',').map(::stem).count { it in hint }
            overlap * 10f + g.confidence
        }
    }

    /** Text to search the food database with. */
    fun query(top: FoodGuess?): String = when {
        words.isNotEmpty() -> text
        top != null -> top.label
        else -> ""
    }

    companion object {
        /** Words that turn a dish into an ingredient or condiment ("pizza sauce", "curry paste"). */
        private val condiments = setOf(
            "sauce", "seasoning", "mix", "powder", "paste", "spice", "spices", "flavour", "flavor", "flavoured",
            "flavored", "dressing", "base", "dough", "kit", "stock", "marinade", "sugo", "salsa", "topping", "cube",
        )

        /** Searches to run for [query]: the query itself, then its last word ("Neapolitan pizza" → "pizza"). */
        fun searches(query: String): List<String> {
            val q = query.trim()
            val head = q.split(' ').lastOrNull { it.length > 2 }
            return listOfNotNull(q.takeIf { it.isNotEmpty() }, head?.takeIf { !it.equals(q, ignoreCase = true) })
        }

        /**
         * How well a food [name] fits [query]: share of query words it contains, a bonus for the
         * head noun (the last word), and a penalty for condiment words the user didn't ask for.
         */
        fun score(query: String, name: String): Double {
            val q = query.lowercase().split(Regex("""[^\p{L}\p{N}]+""")).filter { it.length > 1 }.map(::stem)
            if (q.isEmpty()) return 0.0
            val n = name.lowercase().split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotEmpty() }.map(::stem)
            val nameSet = n.toSet()
            var s = q.count { it in nameSet }.toDouble() / q.size
            if (q.last() in nameSet) s += 0.5
            if (n.any { it in condiments && it !in q }) s -= 1.0
            s -= (n.size - q.size).coerceAtLeast(0) * 0.03 // prefer plain names
            return s
        }

        private val number = Regex("""(\d+(?:[.,]\d+)?)""")
        private val gramsRx = Regex("""(\d+(?:[.,]\d+)?)\s*(kg|g|gr|grams?|ml)\b""")
        private val ozRx = Regex("""(\d+(?:[.,]\d+)?)\s*(oz|ounces?)\b""")
        private val servingsRx = Regex("""(?:(\d+(?:[.,]\d+)?)\s*(?:x|×|servings?|portions?|pieces?|slices?|bowls?|cups?|plates?)\b)|(?:\b[x×]\s*(\d+(?:[.,]\d+)?))""")
        private val wordNumbers = mapOf(
            "half" to 0.5, "a" to 1.0, "an" to 1.0, "one" to 1.0, "two" to 2.0, "three" to 3.0, "four" to 4.0,
            "five" to 5.0, "six" to 6.0, "couple" to 2.0,
        )
        private val sizes = mapOf("small" to 0.7, "little" to 0.7, "medium" to 1.0, "regular" to 1.0, "large" to 1.4, "big" to 1.4, "huge" to 1.8)
        private val filler = setOf(
            "about", "around", "approx", "approximately", "roughly", "of", "with", "and", "the", "some", "my", "it", "is",
            "this", "that", "plate", "bowl", "serving", "servings", "portion", "portions", "piece", "pieces", "slice",
            "slices", "cup", "cups", "x", "g", "kg", "ml", "oz", "grams", "gram", "homemade", "i", "had", "ate", "for",
        )

        private fun d(s: String) = s.replace(',', '.').toDoubleOrNull()

        fun stem(w: String): String {
            val l = w.lowercase().trim()
            return when {
                l.length > 4 && l.endsWith("ies") -> l.dropLast(3) + "y"
                l.length > 3 && l.endsWith("es") && (l.endsWith("oes") || l.endsWith("shes") || l.endsWith("ches")) -> l.dropLast(2)
                l.length > 3 && l.endsWith('s') && !l.endsWith("ss") -> l.dropLast(1)
                else -> l
            }
        }

        fun parse(input: String): FoodHints {
            var s = " " + input.lowercase().replace('\n', ' ') + " "
            var grams: Double? = null
            var servings: Double? = null

            gramsRx.find(s)?.let { m ->
                val v = d(m.groupValues[1])
                grams = if (m.groupValues[2] == "kg") v?.times(1000) else v
                s = s.replace(m.value, " ")
            }
            if (grams == null) ozRx.find(s)?.let { m ->
                grams = d(m.groupValues[1])?.times(28.35)
                s = s.replace(m.value, " ")
            }
            servingsRx.find(s)?.let { m ->
                servings = d(m.groupValues[1].ifEmpty { m.groupValues[2] })
                s = s.replace(m.value, " ")
            }

            val tokens = s.split(Regex("""[^\p{L}\p{N}'-]+""")).filter { it.isNotBlank() }
            var size = 1.0
            val words = mutableListOf<String>()
            for ((i, t) in tokens.withIndex()) {
                when {
                    t in sizes -> size = sizes.getValue(t)
                    t in wordNumbers && servings == null && i + 1 < tokens.size && tokens[i + 1] !in filler -> {
                        // "two eggs" -> 2 servings of eggs; "a banana" -> 1
                        servings = wordNumbers.getValue(t)
                    }
                    t in wordNumbers -> Unit
                    t in filler -> Unit
                    number.matches(t) -> if (servings == null) servings = d(t)
                    else -> words += t
                }
            }
            return FoodHints(words, grams?.takeIf { it > 0 }, servings?.takeIf { it > 0 }, size)
        }
    }
}
