package com.aleb.aletracr.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Client for Open Food Facts (https://world.openfoodfacts.org), a free, open
 * database with >3 million products, very good coverage for Germany/Europe.
 * No API key needed; OFF only asks for a descriptive User-Agent.
 */
class OpenFoodFactsClient(
    private val http: OkHttpClient = defaultHttp,
    private val baseUrl: String = "https://world.openfoodfacts.org",
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val cache = object : LinkedHashMap<String, Pair<Long, List<FoodProduct>>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, List<FoodProduct>>>) = size > 50
    }

    /**
     * Full-text search. Uses the fast "search-a-licious" API first and falls back
     * to the classic (heavily rate-limited) search; both retry on 503/429/timeouts.
     * Results are cached for 30 minutes.
     */
    suspend fun search(query: String, pageSize: Int = 30): List<FoodProduct> = withContext(Dispatchers.IO) {
        val key = query.trim().lowercase()
        synchronized(cache) {
            cache[key]?.let { (time, list) -> if (System.currentTimeMillis() - time < 30 * 60_000) return@withContext list }
        }
        val result = try {
            searchFast(query, pageSize).ifEmpty { searchClassic(query, pageSize) }
        } catch (first: IOException) {
            try {
                searchClassic(query, pageSize)
            } catch (_: IOException) {
                throw first
            }
        }
        synchronized(cache) { cache[key] = System.currentTimeMillis() to result }
        result
    }

    /** search.openfoodfacts.org – Elasticsearch based, much faster and less overloaded. */
    private suspend fun searchFast(query: String, pageSize: Int): List<FoodProduct> {
        val url = SEARCH_URL.toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("langs", "de,en")
            .addQueryParameter("page_size", pageSize.toString())
            .addQueryParameter("fields", FIELDS)
            .build()
        val root = getWithRetry(url.toString()) ?: return emptyList()
        val hits = root["hits"] as? JsonArray ?: return emptyList()
        return hits.mapNotNull { (it as? JsonObject)?.let(::parseProduct) }
    }

    /** Classic world.openfoodfacts.org search (fallback). */
    private suspend fun searchClassic(query: String, pageSize: Int): List<FoodProduct> {
        val url = "$baseUrl/cgi/search.pl".toHttpUrl().newBuilder()
            .addQueryParameter("search_terms", query)
            .addQueryParameter("search_simple", "1")
            .addQueryParameter("action", "process")
            .addQueryParameter("json", "1")
            .addQueryParameter("lc", "de")
            .addQueryParameter("cc", "de")
            .addQueryParameter("page_size", pageSize.toString())
            .addQueryParameter("sort_by", "unique_scans_n")
            .addQueryParameter("fields", FIELDS)
            .build()
        val root = getWithRetry(url.toString()) ?: return emptyList()
        val products = root["products"] as? JsonArray ?: return emptyList()
        return products.mapNotNull { (it as? JsonObject)?.let(::parseProduct) }
    }

    /** Lookup by EAN/UPC barcode. Returns null if the product is unknown. */
    suspend fun byBarcode(barcode: String): FoodProduct? = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/v2/product/$barcode".toHttpUrl().newBuilder()
            .addQueryParameter("fields", FIELDS)
            .addQueryParameter("lc", "de")
            .build()
        val root = getWithRetry(url.toString()) ?: return@withContext null
        if ((root["status"] as? JsonPrimitive)?.intOrNull != 1) return@withContext null
        val product = root["product"] as? JsonObject ?: return@withContext null
        parseProduct(product, fallbackBarcode = barcode)
    }

    /** GET with up to 3 attempts on overload (503/502/504/429) and network errors. */
    private suspend fun getWithRetry(url: String): JsonObject? {
        var last: IOException? = null
        for (attempt in 0 until 3) {
            if (attempt > 0) delay(if (attempt == 1) 700L else 1800L)
            try {
                return get(url)
            } catch (e: RetryableException) {
                last = e
            } catch (e: java.io.InterruptedIOException) { // timeout
                last = e
            } catch (e: IOException) {
                if (e is OffHttpException) throw e // e.g. 400 – retrying will not help
                last = e
            }
        }
        throw last ?: IOException("Open Food Facts nicht erreichbar")
    }

    private fun get(url: String): JsonObject? {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        http.newCall(request).execute().use { response ->
            if (response.code == 404) return null
            if (response.code in RETRY_CODES) throw RetryableException(response.code)
            if (!response.isSuccessful) throw OffHttpException(response.code)
            val body = response.body?.string() ?: return null
            return json.parseToJsonElement(body).jsonObject
        }
    }

    class RetryableException(val code: Int) : IOException("Open Food Facts ist gerade überlastet (HTTP $code)")
    class OffHttpException(val code: Int) : IOException("Open Food Facts: HTTP $code")

    internal fun parseProduct(p: JsonObject, fallbackBarcode: String? = null): FoodProduct? {
        val name = listOf("product_name_de", "product_name", "generic_name_de", "generic_name")
            .firstNotNullOfOrNull { p.str(it)?.takeIf(String::isNotBlank) } ?: return null
        val n = p["nutriments"] as? JsonObject ?: return null
        val kcal = n.num("energy-kcal_100g")
            ?: n.num("energy_100g")?.let { it / 4.184 } // kJ -> kcal
            ?: return null
        return FoodProduct(
            name = name.trim(),
            brand = ((p["brands"] as? JsonArray)?.firstOrNull()?.let { (it as? JsonPrimitive)?.contentOrNull }
                ?: p.str("brands")?.split(',')?.firstOrNull())?.trim()?.takeIf(String::isNotBlank),
            barcode = p.str("code") ?: fallbackBarcode,
            kcalPer100 = kcal,
            proteinPer100 = n.num("proteins_100g") ?: 0.0,
            carbsPer100 = n.num("carbohydrates_100g") ?: 0.0,
            fatPer100 = n.num("fat_100g") ?: 0.0,
            sugarPer100 = n.num("sugars_100g"),
            fiberPer100 = n.num("fiber_100g"),
            saltPer100 = n.num("salt_100g"),
            servingGrams = p.num("serving_quantity")?.takeIf { it > 0 },
            servingLabel = p.str("serving_size")?.takeIf(String::isNotBlank),
            imageUrl = p.str("image_small_url") ?: p.str("image_front_small_url"),
        )
    }

    companion object {
        const val USER_AGENT = "AleTracr/0.4 (Android; https://github.com/AleB-Roni/AleTracr)"
        private const val SEARCH_URL = "https://search.openfoodfacts.org/search"
        private val RETRY_CODES = setOf(429, 502, 503, 504)
        private const val FIELDS =
            "code,product_name,product_name_de,generic_name,generic_name_de,brands,nutriments," +
                "serving_quantity,serving_size,image_small_url,image_front_small_url"

        private val defaultHttp by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
        }
    }
}

/** OFF returns numbers sometimes as strings ("12.5"), so read both. */
private fun JsonObject.num(key: String): Double? {
    val prim = this[key] as? JsonPrimitive ?: return null
    return prim.doubleOrNull ?: prim.content.replace(',', '.').toDoubleOrNull()
}

private fun JsonObject.str(key: String): String? {
    val el: JsonElement = this[key] ?: return null
    return (el as? JsonPrimitive)?.takeIf { it.isString }?.content
}
