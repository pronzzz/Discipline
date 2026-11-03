package app.discipline.data.food

/** Barcode normalisation for retail product codes (EAN-8, EAN-13, UPC-A, UPC-E). */
object Barcodes {

    /** The scanned code first, then the same product's other common forms. */
    fun variants(raw: String): List<String> {
        val d = raw.filter(Char::isDigit)
        if (d.length < 6) return emptyList()
        val out = linkedSetOf(d)
        when (d.length) {
            8 -> upcEToA(d)?.let { out += it; out += "0$it" }
            12 -> out += "0$d"
            13 -> if (d[0] == '0') out += d.substring(1)
            14 -> if (d[0] == '0') { out += d.substring(1); if (d[1] == '0') out += d.substring(2) }
        }
        return out.toList()
    }

    /** Expands an 8-digit UPC-E (number system 0 or 1) to its 12-digit UPC-A form. */
    fun upcEToA(e: String): String? {
        if (e.length != 8 || !e.all(Char::isDigit) || e[0] !in "01") return null
        val x = e.substring(1, 7)
        val body = when (x[5]) {
            '0', '1', '2' -> "${x[0]}${x[1]}${x[5]}0000${x[2]}${x[3]}${x[4]}"
            '3' -> "${x[0]}${x[1]}${x[2]}00000${x[3]}${x[4]}"
            '4' -> "${x[0]}${x[1]}${x[2]}${x[3]}00000${x[4]}"
            else -> "${x[0]}${x[1]}${x[2]}${x[3]}${x[4]}0000${x[5]}"
        }
        return "${e[0]}$body${e[7]}"
    }

    /** True when the last digit is a valid GTIN check digit. */
    fun checksumOk(code: String): Boolean {
        if (code.length !in setOf(8, 12, 13, 14) || !code.all(Char::isDigit)) return false
        val digits = code.map { it - '0' }
        val sum = digits.dropLast(1).reversed().mapIndexed { i, v -> if (i % 2 == 0) v * 3 else v }.sum()
        return (10 - sum % 10) % 10 == digits.last()
    }
}
