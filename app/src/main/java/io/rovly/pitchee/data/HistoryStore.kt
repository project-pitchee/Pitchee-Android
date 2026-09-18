package io.rovly.pitchee.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class AnalysisHistoryEntry(
    val timestampMillis: Long,
    val finalScore: Double,
    val standardScore: Double,
    val naturalnessScore: Double,
    val meanF0Hz: Double?,
)

data class RealtimeF0HistoryEntry(
    val timestampMillis: Long,
    val meanF0Hz: Double,
)

class HistoryStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun analyses(): List<AnalysisHistoryEntry> =
        readArray(KEY_ANALYSES).objects().map { value ->
            AnalysisHistoryEntry(
                timestampMillis = value.optLong("timestamp"),
                finalScore = value.optDouble("final"),
                standardScore = value.optDouble("standard"),
                naturalnessScore = value.optDouble("naturalness"),
                meanF0Hz = value.optNullableDouble("f0"),
            )
        }.sortedByDescending { it.timestampMillis }

    fun realtimeF0(): List<RealtimeF0HistoryEntry> =
        readArray(KEY_REALTIME_F0).objects().map { value ->
            RealtimeF0HistoryEntry(
                timestampMillis = value.optLong("timestamp"),
                meanF0Hz = value.optDouble("mean_f0"),
            )
        }.filter { it.meanF0Hz.isFinite() }.sortedByDescending { it.timestampMillis }

    fun addAnalysis(entry: AnalysisHistoryEntry) {
        val values = analyses().toMutableList()
        values.add(0, entry)
        writeArray(
            KEY_ANALYSES,
            JSONArray().apply {
                values.take(MAX_ENTRIES).forEach { put(it.toJson()) }
            },
        )
    }

    fun removeAnalysis(timestampMillis: Long) {
        writeArray(
            KEY_ANALYSES,
            JSONArray().apply {
                analyses()
                    .filterNot { it.timestampMillis == timestampMillis }
                    .forEach { put(it.toJson()) }
            },
        )
    }

    fun clearRealtimeF0() {
        preferences.edit().remove(KEY_REALTIME_F0).apply()
    }

    fun addRealtimeF0(entry: RealtimeF0HistoryEntry) {
        val values = realtimeF0().toMutableList()
        values.add(0, entry)
        writeArray(
            KEY_REALTIME_F0,
            JSONArray().apply {
                values.take(MAX_ENTRIES).forEach { put(it.toJson()) }
            },
        )
    }

    private fun readArray(key: String): JSONArray =
        runCatching { JSONArray(preferences.getString(key, "[]")) }.getOrElse { JSONArray() }

    private fun writeArray(key: String, values: JSONArray) {
        preferences.edit().putString(key, values.toString()).apply()
    }

    private fun AnalysisHistoryEntry.toJson() = JSONObject().apply {
        put("timestamp", timestampMillis)
        put("final", finalScore)
        put("standard", standardScore)
        put("naturalness", naturalnessScore)
        if (meanF0Hz == null) put("f0", JSONObject.NULL) else put("f0", meanF0Hz)
    }

    private fun RealtimeF0HistoryEntry.toJson() = JSONObject().apply {
        put("timestamp", timestampMillis)
        put("mean_f0", meanF0Hz)
    }

    private fun JSONObject.optNullableDouble(key: String): Double? =
        if (isNull(key)) null else optDouble(key).takeIf { it.isFinite() }

    private fun JSONArray.objects(): List<JSONObject> = buildList {
        for (index in 0 until length()) {
            optJSONObject(index)?.let(::add)
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "history"
        const val KEY_ANALYSES = "analyses"
        const val KEY_REALTIME_F0 = "realtime_f0"
        const val MAX_ENTRIES = 100
    }
}
