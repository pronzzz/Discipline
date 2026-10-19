package app.discipline.data.food

import android.content.Context
import app.discipline.data.db.FoodDao
import app.discipline.data.db.FoodEntity
import app.discipline.data.db.FoodSource
import app.discipline.domain.Nutrients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.Normalizer

object SearchKey {
    private val marks = "\\p{Mn}+".toRegex()
    fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(marks, "").replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ").trim()

    fun of(name: String, brand: String?) = normalize(listOfNotNull(name, brand).joinToString(" "))
}

sealed interface OnlineResult {
    data class Ok(val foods: List<FoodEntity>) : OnlineResult
    data class Failed(val message: String) : OnlineResult
}

class FoodRepository(
    private val context: Context,
    private val dao: FoodDao,
    private val off: OpenFoodFactsClient,
) {
    val recent: Flow<List<FoodEntity>> = dao.recent()
    val frequent: Flow<List<FoodEntity>> = dao.frequent()
    val userFoods: Flow<List<FoodEntity>> = dao.userFoods()
    val favorites: Flow<List<FoodEntity>> = dao.favorites()

    suspend fun byId(id: Long) = dao.byId(id)

    /** Loads the bundled generic food list on first launch. */
    suspend fun seedIfNeeded() = withContext(Dispatchers.IO) {
        if (dao.genericCount() > 0) return@withContext
        val text = context.assets.open("generic_foods.json").bufferedReader().use { it.readText() }
        val rows = Json.parseToJsonElement(text).jsonObject["foods"]!!.jsonArray
        val foods = rows.map { row ->
            val a = row.jsonArray
            fun d(i: Int) = (a[i] as JsonPrimitive).doubleOrNull ?: 0.0
            val name = a[1].jsonPrimitive.content
            FoodEntity(
                name = name,
                servingLabel = a[2].jsonPrimitive.content,
                servingGrams = d(3),
                nutrients = Nutrients(
                    calories = d(4), protein = d(5), carbs = d(6), fat = d(7),
                    fiber = d(8), sugar = d(9), saturatedFat = d(10), sodiumMg = d(11),
                ),
                source = FoodSource.GENERIC,
                remoteId = a[0].jsonPrimitive.content,
                searchKey = SearchKey.of(name, null),
            )
        }
        dao.insertAll(foods)
    }

    suspend fun searchLocal(query: String): List<FoodEntity> {
        // Drop a plural "s" so "oats" also finds "oat milk" and "eggs" finds "egg".
        val tokens = SearchKey.normalize(query).split(' ').filter { it.isNotBlank() }
            .map { if (it.length > 3 && it.endsWith('s') && !it.endsWith("ss")) it.dropLast(1) else it }
        if (tokens.isEmpty()) return emptyList()
        return dao.search(tokens[0], tokens.getOrElse(1) { "" }, tokens.getOrElse(2) { "" })
    }

    suspend fun searchOnline(query: String): OnlineResult = try {
        OnlineResult.Ok(off.search(query))
    } catch (e: Exception) {
        OnlineResult.Failed(e.message ?: "Network error")
    }

    sealed interface BarcodeResult {
        data class Found(val food: FoodEntity) : BarcodeResult
        data object NotFound : BarcodeResult
        data class Failed(val message: String) : BarcodeResult
    }

    suspend fun lookupBarcode(code: String): BarcodeResult {
        dao.byBarcode(code)?.let { return BarcodeResult.Found(it) }
        return try {
            val remote = off.product(code) ?: return BarcodeResult.NotFound
            BarcodeResult.Found(persist(remote))
        } catch (e: Exception) {
            BarcodeResult.Failed(e.message ?: "Network error")
        }
    }

    /** Stores an online result locally so it can be logged, favourited and found offline. */
    suspend fun persist(food: FoodEntity): FoodEntity {
        if (food.id != 0L) return food
        val existing = food.remoteId?.let { dao.byRemote(food.source, it) }
        if (existing != null) return existing
        val id = dao.insert(food)
        return food.copy(id = id)
    }

    suspend fun saveUserFood(food: FoodEntity): Long =
        dao.upsert(food.copy(source = FoodSource.USER, searchKey = SearchKey.of(food.name, food.brand)))

    suspend fun deleteUserFood(id: Long) = dao.deleteUserFood(id)
    suspend fun setFavorite(id: Long, fav: Boolean) = dao.setFavorite(id, fav)
    suspend fun lastServings(foodId: Long) = dao.lastServings(foodId)
}
