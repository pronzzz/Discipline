package app.discipline.domain

import java.text.DecimalFormat
import kotlin.math.abs
import kotlin.math.roundToInt

enum class UnitSystem { METRIC, IMPERIAL }

object Units {
    const val LB_PER_KG = 2.2046226218
    const val ML_PER_FL_OZ = 29.5735295625
    const val CM_PER_IN = 2.54

    private val oneDecimal = DecimalFormat("#,##0.#")
    private val twoDecimals = DecimalFormat("#,##0.##")
    private val noDecimal = DecimalFormat("#,##0")

    fun weight(kg: Double, system: UnitSystem): String = when (system) {
        UnitSystem.METRIC -> "${oneDecimal.format(kg)} kg"
        UnitSystem.IMPERIAL -> "${oneDecimal.format(kg * LB_PER_KG)} lb"
    }

    fun weightValue(kg: Double, system: UnitSystem) = if (system == UnitSystem.METRIC) kg else kg * LB_PER_KG
    fun weightToKg(value: Double, system: UnitSystem) = if (system == UnitSystem.METRIC) value else value / LB_PER_KG
    fun weightUnit(system: UnitSystem) = if (system == UnitSystem.METRIC) "kg" else "lb"

    fun water(ml: Int, system: UnitSystem): String = when (system) {
        UnitSystem.METRIC -> if (ml >= 1000) "${oneDecimal.format(ml / 1000.0)} L" else "$ml ml"
        UnitSystem.IMPERIAL -> "${(ml / ML_PER_FL_OZ).roundToInt()} fl oz"
    }

    fun height(cm: Double, system: UnitSystem): String = when (system) {
        UnitSystem.METRIC -> "${cm.roundToInt()} cm"
        UnitSystem.IMPERIAL -> {
            val totalIn = (cm / CM_PER_IN).roundToInt()
            "${totalIn / 12}′ ${totalIn % 12}″"
        }
    }

    fun kcal(v: Double) = noDecimal.format(v.roundToInt())
    fun kcal(v: Int) = noDecimal.format(v)
    fun grams(v: Double) = if (abs(v) < 10) "${oneDecimal.format(v)} g" else "${v.roundToInt()} g"
    fun mg(v: Double) = "${noDecimal.format(v.roundToInt())} mg"
    fun number(v: Double) = twoDecimals.format(v)
    fun integer(v: Long) = noDecimal.format(v)
}
