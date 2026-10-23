package app.still.launcher.data.productivity

import androidx.room.TypeConverter
import app.still.launcher.data.notes.ChecklistItem
import app.still.launcher.schedule.Quota
import app.still.launcher.schedule.Schedule
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate

/**
 * Dates are stored as ISO text ("2026-10-23") so they sort and compare correctly in SQL.
 * Schedules, quotas and note checklists are stored as JSON; unknown keys are ignored so older app versions
 * can still read rows written by newer ones.
 */
class Converters {

    @TypeConverter
    fun localDateToText(date: LocalDate): String = date.toString()

    @TypeConverter
    fun textToLocalDate(text: String): LocalDate = LocalDate.parse(text)

    @TypeConverter
    fun instantToMillis(instant: Instant): Long = instant.toEpochMilli()

    @TypeConverter
    fun millisToInstant(millis: Long): Instant = Instant.ofEpochMilli(millis)

    @TypeConverter
    fun scheduleToJson(schedule: Schedule): String = json.encodeToString(Schedule.serializer(), schedule)

    @TypeConverter
    fun jsonToSchedule(text: String): Schedule = json.decodeFromString(Schedule.serializer(), text)

    @TypeConverter
    fun quotaToJson(quota: Quota): String = json.encodeToString(Quota.serializer(), quota)

    @TypeConverter
    fun jsonToQuota(text: String): Quota = json.decodeFromString(Quota.serializer(), text)

    @TypeConverter
    fun checklistToJson(items: List<ChecklistItem>): String = json.encodeToString(checklistSerializer, items)

    @TypeConverter
    fun jsonToChecklist(text: String): List<ChecklistItem> = json.decodeFromString(checklistSerializer, text)

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        // Typed explicitly: Room's code generator can't see the serialization plugin's generated serializer().
        val checklistSerializer: KSerializer<List<ChecklistItem>> = ListSerializer(ChecklistItem.serializer())
    }
}
