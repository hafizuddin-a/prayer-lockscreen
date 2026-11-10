package com.prayerwallpaper.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.prayerwallpaper.app.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

fun interface HttpGet {
    fun get(url: String): String
}

/** Non-200 response. [code] 429 means we are being rate limited. */
class HttpStatusException(val code: Int, val retryAfterSeconds: Long?) : IOException("HTTP $code")

/**
 * HTTPS only (a debug build also reaches the computer the emulator runs on, to try updates). The API key is only ever set in debug builds.
 * [headers] replace the defaults, such as the `Accept` header.
 */
class UrlConnectionHttp(private val apiKey: String = "", private val headers: Map<String, String> = emptyMap()) : HttpGet {
    override fun get(url: String): String {
        require(url.startsWith("https://") || (BuildConfig.DEBUG && url.startsWith("http://10.0.2.2:"))) { "HTTPS only" }
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Accept", "application/json")
            if (apiKey.isNotEmpty()) connection.setRequestProperty("x-api-key", apiKey)
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            val code = connection.responseCode
            if (code != 200) {
                throw HttpStatusException(code, connection.getHeaderField("Retry-After")?.toLongOrNull())
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

/** What one dataset download produced. [schema] is the field list as reported by the API. */
class DatasetFetch(val datasetId: String, val schema: String, val days: List<PrayerDay>, val rejectedRows: Int)

class PrayerApi(private val http: HttpGet) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Ids of every dataset in the MUIS collection. A new year shows up here first. */
    fun collectionDatasetIds(): List<String> {
        val body = json.parseToJsonElement(http.get(COLLECTION_URL)).jsonObject
        val children = body["data"]!!.jsonObject["collectionMetadata"]!!.jsonObject["childDatasets"]!!
        return children.jsonArray.map { it.jsonPrimitive.content }
    }

    /**
     * Downloads every row. The API always reports a `next` link, even on the last page, so paging
     * stops on `total` or an empty page, not on the link.
     */
    fun fetchDataset(datasetId: String): DatasetFetch {
        val days = mutableListOf<PrayerDay>()
        var schema = ""
        var rejected = 0
        var offset = 0
        var total = Int.MAX_VALUE
        var pages = 0
        while (offset < total && pages++ < MAX_PAGES) {
            val url = "$SEARCH_URL?resource_id=$datasetId&limit=$PAGE_SIZE&offset=$offset"
            val result = json.parseToJsonElement(http.get(url)).jsonObject["result"]!!.jsonObject
            total = result["total"]!!.jsonPrimitive.int
            if (offset == 0) schema = describeFields(result["fields"] as? JsonArray)
            val records = result["records"]!!.jsonArray
            if (records.isEmpty()) break
            for (record in records) {
                val day = DatasetParser.parseRecord(record as JsonObject)
                if (day != null) days += day else rejected++
            }
            offset += records.size
        }
        return DatasetFetch(datasetId, schema, days, rejected)
    }

    private fun describeFields(fields: JsonArray?): String =
        fields.orEmpty().joinToString { "${it.jsonObject["id"]?.jsonPrimitive?.content}:${it.jsonObject["type"]?.jsonPrimitive?.content}" }

    private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())

    companion object {
        const val SEARCH_URL = "https://data.gov.sg/api/action/datastore_search"
        const val COLLECTION_URL = "https://api-production.data.gov.sg/v2/public/api/collections/2312/metadata"
        const val PAGE_SIZE = 400
        private const val MAX_PAGES = 10
    }
}
