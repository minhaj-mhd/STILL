package app.still.launcher.data.productivity

import androidx.room.TypeConverter
import app.still.launcher.data.gates.GateItem
import app.still.launcher.data.gates.GateWindow
import app.still.launcher.data.notes.ChecklistItem
import app.still.launcher.schedule.Quota
import app.still.launcher.schedule.Schedule
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate

/**
 * Dates are stored as ISO text ("2026-10-23") so they sort and compare correctly in SQL.
 * Schedules, quotas, note checklists, plan pauses and task gates' items and windows are stored as JSON; unknown keys are ignored so older app versions
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

    /** Hourly totals and the like, as "12,0,340". */
    @TypeConverter
    fun longsToText(values: List<Long>): String = values.joinToString(",")

    @TypeConverter
    fun textToLongs(text: String): List<Long> = if (text.isEmpty()) emptyList() else text.split(',').map { it.toLong() }

    @TypeConverter
    fun scheduleToJson(schedule: Schedule): String = json.encodeToString(Schedule.serializer(), schedule)

    @TypeConverter
    fun jsonToSchedule(text: String): Schedule = json.decodeFromString(Schedule.serializer(), text)

    @TypeConverter
    fun quotaToJson(quota: Quota): String = json.encodeToString(Quota.serializer(), quota)

    @TypeConverter
    fun jsonToQuota(text: String): Quota = json.decodeFromString(Quota.serializer(), text)

    @TypeConverter
    fun stringSetToJson(strings: Set<String>): String = json.encodeToString(stringListSerializer, strings.sorted())

    @TypeConverter
    fun jsonToStringSet(text: String): Set<String> = json.decodeFromString(stringListSerializer, text).toSet()

    @TypeConverter
    fun checklistToJson(items: List<ChecklistItem>): String = json.encodeToString(checklistSerializer, items)

    @TypeConverter
    fun jsonToChecklist(text: String): List<ChecklistItem> = json.decodeFromString(checklistSerializer, text)

    @TypeConverter
    fun gateItemsToJson(items: List<GateItem>): String = json.encodeToString(gateItemsSerializer, items)

    @TypeConverter
    fun jsonToGateItems(text: String): List<GateItem> = json.decodeFromString(gateItemsSerializer, text)

    @TypeConverter
    fun planPausesToJson(pauses: List<PlanPause>): String = json.encodeToString(planPausesSerializer, pauses)

    @TypeConverter
    fun jsonToPlanPauses(text: String): List<PlanPause> = json.decodeFromString(planPausesSerializer, text)

    @TypeConverter
    fun gateWindowToJson(window: GateWindow): String = json.encodeToString(GateWindow.serializer(), window)

    @TypeConverter
    fun jsonToGateWindow(text: String): GateWindow = json.decodeFromString(GateWindow.serializer(), text)

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        // Typed explicitly: Room's code generator can't see the serialization plugin's generated serializer().
        val checklistSerializer: KSerializer<List<ChecklistItem>> = ListSerializer(ChecklistItem.serializer())
        val stringListSerializer: KSerializer<List<String>> = ListSerializer(String.serializer())
        val gateItemsSerializer: KSerializer<List<GateItem>> = ListSerializer(GateItem.serializer())
        val planPausesSerializer: KSerializer<List<PlanPause>> = ListSerializer(PlanPause.serializer())
    }
}
