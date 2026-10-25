package com.prayerwallpaper.app.widgets

import com.prayerwallpaper.app.logic.WidgetHistory
import com.prayerwallpaper.app.logic.WidgetUpdateEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant

@Serializable
private data class StoredWidgetHistory(val version: Int = 1, val lastUpdateEpochMs: Long? = null, val entries: List<StoredUpdate> = emptyList(), val envKey: String? = null)

@Serializable
private data class StoredUpdate(val plannedAtEpochMs: Long, val updatedAtEpochMs: Long, val reason: String)

/** The widget update log as a small JSON file in app-private storage, written to a temp file and renamed. A missing or damaged file reads as empty. */
class WidgetHistoryStore(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    fun read(): WidgetHistory {
        if (!file.exists()) return WidgetHistory()
        return try {
            val stored = json.decodeFromString<StoredWidgetHistory>(file.readText())
            WidgetHistory(
                stored.lastUpdateEpochMs?.let(Instant::ofEpochMilli),
                stored.entries.map { WidgetUpdateEntry(Instant.ofEpochMilli(it.plannedAtEpochMs), Instant.ofEpochMilli(it.updatedAtEpochMs), it.reason) },
                stored.envKey,
            )
        } catch (_: Exception) {
            WidgetHistory()
        }
    }

    @Synchronized
    fun write(history: WidgetHistory) {
        val stored = StoredWidgetHistory(
            lastUpdateEpochMs = history.lastUpdate?.toEpochMilli(),
            entries = history.entries.map { StoredUpdate(it.plannedAt.toEpochMilli(), it.updatedAt.toEpochMilli(), it.reason) },
            envKey = history.envKey,
        )
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(json.encodeToString(stored))
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    @Synchronized
    fun update(change: (WidgetHistory) -> WidgetHistory): WidgetHistory = change(read()).also(::write)

    @Synchronized
    fun clear() {
        file.delete()
    }
}
