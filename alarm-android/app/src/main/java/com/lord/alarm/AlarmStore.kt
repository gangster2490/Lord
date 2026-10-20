package com.lord.alarm

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/** Alarms persisted as JSON in SharedPreferences, shared by UI, receivers and the service. */
object AlarmStore {
    private const val PREFS = "alarms"
    private const val KEY_ALARMS = "alarms"
    private const val KEY_SNOOZE = "snooze_minutes"
    private const val KEY_RAMP = "gradual_volume"
    private const val KEY_RING_LIMIT = "ring_limit_minutes"

    private val _alarms = MutableStateFlow<List<Alarm>>(emptyList())
    val alarms: StateFlow<List<Alarm>> = _alarms.asStateFlow()

    @Volatile private var loaded = false

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun load(context: Context): List<Alarm> {
        if (!loaded) {
            val raw = prefs(context).getString(KEY_ALARMS, null)
            _alarms.value = raw?.let { json ->
                runCatching {
                    val arr = JSONArray(json)
                    (0 until arr.length()).map { Alarm.fromJson(arr.getJSONObject(it)) }
                }.getOrDefault(emptyList())
            } ?: emptyList()
            loaded = true
        }
        return _alarms.value
    }

    fun get(context: Context, id: Int): Alarm? = load(context).firstOrNull { it.id == id }

    @Synchronized
    fun upsert(context: Context, alarm: Alarm) {
        val list = load(context).filterNot { it.id == alarm.id } + alarm
        save(context, list.sortedWith(compareBy({ it.hour }, { it.minute }, { it.id })))
    }

    @Synchronized
    fun delete(context: Context, id: Int) {
        save(context, load(context).filterNot { it.id == id })
    }

    @Synchronized
    fun newId(context: Context): Int = (load(context).maxOfOrNull { it.id } ?: 0) + 1

    private fun save(context: Context, list: List<Alarm>) {
        val arr = JSONArray().apply { list.forEach { put(it.toJson()) } }
        prefs(context).edit().putString(KEY_ALARMS, arr.toString()).apply()
        _alarms.value = list
    }

    fun snoozeMinutes(context: Context): Int = prefs(context).getInt(KEY_SNOOZE, 10)
    fun setSnoozeMinutes(context: Context, v: Int) = prefs(context).edit().putInt(KEY_SNOOZE, v).apply()

    fun gradualVolume(context: Context): Boolean = prefs(context).getBoolean(KEY_RAMP, true)
    fun setGradualVolume(context: Context, v: Boolean) = prefs(context).edit().putBoolean(KEY_RAMP, v).apply()

    fun ringLimitMinutes(context: Context): Int = prefs(context).getInt(KEY_RING_LIMIT, 10)
    fun setRingLimitMinutes(context: Context, v: Int) = prefs(context).edit().putInt(KEY_RING_LIMIT, v).apply()
}
