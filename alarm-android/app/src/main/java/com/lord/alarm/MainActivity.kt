package com.lord.alarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lord.alarm.ui.AlarmApp
import com.lord.alarm.ui.BudilnikTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AlarmStore.load(this)
        AlarmService.ensureChannel(this)
        // Cheap safety net: make sure the system knows about every enabled alarm.
        AlarmScheduler.rescheduleAll(this)
        setContent {
            BudilnikTheme { AlarmApp() }
        }
    }
}
