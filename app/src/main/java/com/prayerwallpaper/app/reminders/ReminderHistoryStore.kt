package com.prayerwallpaper.app.reminders

import com.prayerwallpaper.app.data.Prayer
import com.prayerwallpaper.app.logic.AlarmMethod
import com.prayerwallpaper.app.logic.HistoryEntry
import com.prayerwallpaper.app.logic.ReminderHistory
import com.prayerwallpaper.app.logic.ReminderKind
import com.prayerwallpaper.app.logic.ReminderOutcome
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant

@Serializable
private data class HistoryFile(val version: Int = 1, val entries: List<StoredEntry> = emptyList())

@Serializable
private data class StoredEntry(
    val plannedAtEpochMs: Long, val kind: String, val prayer: String? = null, val method: String,
    val outcome: String, val firedAtEpochMs: Long? = null, val prayerAtEpochMs: Long? = null,
)

/**
 * The reminder log as a small JSON file in app-private storage, written to a temp file and renamed so a crash cannot leave half
 * a file. A missing or damaged file reads as empty. Entries with names this version does not know are skipped, not guessed.
 */
class ReminderHistoryStore(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    fun read(): ReminderHistory {
        if (!file.exists()) return ReminderHistory()
        return try {
            ReminderHistory(json.decodeFromString<HistoryFile>(file.readText()).entries.mapNotNull(::toEntry))
        } catch (_: Exception) {
            ReminderHistory()
        }
    }

    @Synchronized
    fun write(history: ReminderHistory) {
        val stored = HistoryFile(entries = history.entries.map {
            StoredEntry(it.plannedAt.toEpochMilli(), it.kind.name, it.prayer?.name, it.method.name, it.outcome.name, it.firedAt?.toEpochMilli(), it.prayerAtOrNull?.toEpochMilli())
        })
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(json.encodeToString(stored))
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    /** Reads, changes and writes under one lock, so two threads (the alarm receiver and the daily job) cannot lose each other's update. */
    @Synchronized
    fun update(change: (ReminderHistory) -> ReminderHistory): ReminderHistory = change(read()).also(::write)

    private fun toEntry(s: StoredEntry): HistoryEntry? = runCatching {
        HistoryEntry(
            Instant.ofEpochMilli(s.plannedAtEpochMs), ReminderKind.valueOf(s.kind), s.prayer?.let { Prayer.valueOf(it) },
            AlarmMethod.valueOf(s.method), ReminderOutcome.valueOf(s.outcome), s.firedAtEpochMs?.let(Instant::ofEpochMilli),
            s.prayerAtEpochMs?.let(Instant::ofEpochMilli),
        )
    }.getOrNull()
}
