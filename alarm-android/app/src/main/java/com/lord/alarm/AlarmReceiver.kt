package com.lord.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, -1)
        when (intent.action) {
            ACTION_FIRE -> {
                val alarm = AlarmStore.get(context, id) ?: return
                // Consume the snooze / one-shot, then arm the next occurrence of a repeating alarm.
                val updated = alarm.copy(
                    snoozeAt = null,
                    enabled = alarm.enabled && alarm.isRepeating,
                )
                AlarmStore.upsert(context, updated)
                AlarmScheduler.schedule(context, updated)
                ContextCompat.startForegroundService(context, AlarmService.startIntent(context, id))
            }
            ACTION_SNOOZE -> snooze(context, id)
            ACTION_DISMISS -> context.startService(AlarmService.stopIntent(context))
        }
    }

    companion object {
        const val ACTION_FIRE = "com.lord.alarm.FIRE"
        const val ACTION_SNOOZE = "com.lord.alarm.SNOOZE"
        const val ACTION_DISMISS = "com.lord.alarm.DISMISS"
        const val EXTRA_ID = "alarm_id"

        fun snooze(context: Context, id: Int) {
            AlarmStore.get(context, id)?.let { alarm ->
                val at = System.currentTimeMillis() + AlarmStore.snoozeMinutes(context) * 60_000L
                val updated = alarm.copy(snoozeAt = at)
                AlarmStore.upsert(context, updated)
                AlarmScheduler.schedule(context, updated)
            }
            context.startService(AlarmService.stopIntent(context))
        }
    }
}
