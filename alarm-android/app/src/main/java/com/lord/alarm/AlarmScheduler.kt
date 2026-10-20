package com.lord.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.ZonedDateTime

object AlarmScheduler {
    fun canScheduleExact(context: Context): Boolean {
        val am = context.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }

    /** Schedules (or cancels) the system alarm for [alarm]. Returns the ring time, if any. */
    fun schedule(context: Context, alarm: Alarm): Long? {
        val am = context.getSystemService(AlarmManager::class.java)
        val operation = firePendingIntent(context, alarm.id)
        val at = alarm.nextRingMillis(ZonedDateTime.now())
        if (at == null) {
            am.cancel(operation)
            return null
        }
        val show = PendingIntent.getActivity(
            context, alarm.id,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (canScheduleExact(context)) {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), operation)
        } else {
            // Fallback without the exact-alarm permission: still wakes the device, may drift slightly.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        }
        return at
    }

    fun cancel(context: Context, id: Int) {
        context.getSystemService(AlarmManager::class.java).cancel(firePendingIntent(context, id))
    }

    fun rescheduleAll(context: Context) {
        AlarmStore.load(context).forEach { schedule(context, it) }
    }

    private fun firePendingIntent(context: Context, id: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, id,
            Intent(context, AlarmReceiver::class.java)
                .setAction(AlarmReceiver.ACTION_FIRE)
                .putExtra(AlarmReceiver.EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
