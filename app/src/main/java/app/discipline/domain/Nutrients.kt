package app.discipline.domain

import kotlinx.serialization.Serializable

/**
 * Nutrient vector. Macros in grams, sodium/cholesterol/potassium in milligrams.
 * Optional micro/sub-nutrients are nullable so "unknown" is distinguishable from zero.
 */
@Serializable
data class Nutrients(
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
    val fiber: Double? = null,
    val sugar: Double? = null,
    val saturatedFat: Double? = null,
    val sodiumMg: Double? = null,
    val cholesterolMg: Double? = null,
    val potassiumMg: Double? = null,
) {
    operator fun times(factor: Double) = Nutrients(
        calories = calories * factor,
        protein = protein * factor,
        carbs = carbs * factor,
        fat = fat * factor,
        fiber = fiber?.times(factor),
        sugar = sugar?.times(factor),
        saturatedFat = saturatedFat?.times(factor),
        sodiumMg = sodiumMg?.times(factor),
        cholesterolMg = cholesterolMg?.times(factor),
        potassiumMg = potassiumMg?.times(factor),
    )

    operator fun plus(o: Nutrients) = Nutrients(
        calories = calories + o.calories,
        protein = protein + o.protein,
        carbs = carbs + o.carbs,
        fat = fat + o.fat,
        fiber = addNullable(fiber, o.fiber),
        sugar = addNullable(sugar, o.sugar),
        saturatedFat = addNullable(saturatedFat, o.saturatedFat),
        sodiumMg = addNullable(sodiumMg, o.sodiumMg),
        cholesterolMg = addNullable(cholesterolMg, o.cholesterolMg),
        potassiumMg = addNullable(potassiumMg, o.potassiumMg),
    )

    /** Share of calories from each macro, using 4/4/9 kcal per gram. Sums to 1 when any macro is present. */
    fun macroSplit(): Triple<Float, Float, Float> {
        val c = carbs * 4; val p = protein * 4; val f = fat * 9
        val total = c + p + f
        if (total <= 0.0) return Triple(0f, 0f, 0f)
        return Triple((c / total).toFloat(), (p / total).toFloat(), (f / total).toFloat())
    }

    companion object {
        val ZERO = Nutrients()
        private fun addNullable(a: Double?, b: Double?): Double? =
            if (a == null && b == null) null else (a ?: 0.0) + (b ?: 0.0)
    }
}

fun Iterable<Nutrients>.sum(): Nutrients = fold(Nutrients.ZERO) { acc, n -> acc + n }
