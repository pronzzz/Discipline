package app.discipline.data.food

import app.discipline.data.db.FoodEntity
import app.discipline.data.db.FoodSource
import app.discipline.domain.Nutrients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Minimal client for the public Open Food Facts APIs (data licensed ODbL).
 * Search uses the search-a-licious service; barcode lookup uses the v2 product API.
 */
class OpenFoodFactsClient(private val userAgent: String) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun search(query: String, pageSize: Int = 25): List<FoodEntity> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query, "UTF-8")
        val body = get("https://search.openfoodfacts.org/search?q=$q&page_size=$pageSize&fields=$FIELDS")
        val hits = json.parseToJsonElement(body).jsonObject["hits"]?.jsonArray ?: return@withContext emptyList()
        hits.mapNotNull { toFood(it.jsonObject) }
    }

    suspend fun product(barcode: String): FoodEntity? = withContext(Dispatchers.IO) {
        val clean = barcode.filter(Char::isDigit)
        if (clean.isEmpty()) return@withContext null
        val body = try {
            get("https://world.openfoodfacts.org/api/v2/product/$clean.json?fields=$FIELDS")
        } catch (e: HttpStatusException) {
            if (e.code == 404) return@withContext null else throw e
        }
        val root = json.parseToJsonElement(body).jsonObject
        val product = root["product"] as? JsonObject ?: return@withContext null
        toFood(product)
    }

    private fun toFood(o: JsonObject): FoodEntity? {
        val code = o.str("code") ?: return null
        val name = o.str("product_name")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val brand = when (val b = o["brands"]) {
            is JsonArray -> b.firstOrNull()?.jsonPrimitive?.content
            is JsonPrimitive -> b.content.split(',').firstOrNull()
            else -> null
        }?.trim()?.takeIf { it.isNotEmpty() }
        val n = o["nutriments"] as? JsonObject ?: return null
        val per100 = nutrientsFrom(n, "_100g") ?: return null

        val servingGrams = o["serving_quantity"]?.let { (it as? JsonPrimitive)?.doubleOrNull ?: (it as? JsonPrimitive)?.content?.toDoubleOrNull() }
            ?.takeIf { it in 1.0..2000.0 }
        val servingSize = o.str("serving_size")?.trim()?.takeIf { it.isNotEmpty() }

        // Prefer a real serving when the product declares one; otherwise use 100 g.
        val (label, grams, nutrients) = if (servingGrams != null) {
            Triple(servingSize ?: "1 serving (${servingGrams.toInt()} g)", servingGrams, per100 * (servingGrams / 100.0))
        } else {
            Triple("100 g", 100.0, per100)
        }
        return FoodEntity(
            name = name,
            brand = brand,
            barcode = code,
            servingLabel = label,
            servingGrams = grams,
            nutrients = nutrients,
            source = FoodSource.OPEN_FOOD_FACTS,
            remoteId = code,
            searchKey = SearchKey.of(name, brand),
        )
    }

    private fun nutrientsFrom(n: JsonObject, suffix: String): Nutrients? {
        fun d(key: String) = (n["$key$suffix"] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.content.toDoubleOrNull() }
        val kcal = d("energy-kcal") ?: d("energy")?.let { it / 4.184 } ?: return null
        return Nutrients(
            calories = kcal,
            protein = d("proteins") ?: 0.0,
            carbs = d("carbohydrates") ?: 0.0,
            fat = d("fat") ?: 0.0,
            fiber = d("fiber"),
            sugar = d("sugars"),
            saturatedFat = d("saturated-fat"),
            // Open Food Facts stores sodium, cholesterol and potassium in grams.
            sodiumMg = d("sodium")?.times(1000),
            cholesterolMg = d("cholesterol")?.times(1000),
            potassiumMg = d("potassium")?.times(1000),
        )
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.content

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("User-Agent", userAgent)
        conn.setRequestProperty("Accept", "application/json")
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw HttpStatusException(code)
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    class HttpStatusException(val code: Int) : Exception("HTTP $code")

    private companion object {
        const val FIELDS = "code,product_name,brands,nutriments,serving_size,serving_quantity"
    }
}
