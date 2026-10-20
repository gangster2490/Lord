package com.lord.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** System alarms are wiped on reboot / time changes, so re-arm everything. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmScheduler.rescheduleAll(context)
    }
}
