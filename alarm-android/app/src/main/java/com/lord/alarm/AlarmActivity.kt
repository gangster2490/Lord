package com.lord.alarm

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lord.alarm.ui.Amber
import com.lord.alarm.ui.Bg
import com.lord.alarm.ui.BudilnikTheme
import com.lord.alarm.ui.Muted
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.LocalDate
import java.util.Locale

/** Full-screen ringing screen, shown over the lock screen. */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)

        // Back must not silently kill the alarm.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        val id = intent.getIntExtra(AlarmReceiver.EXTRA_ID, -1)
        setContent {
            BudilnikTheme {
                val ringing by AlarmService.ringing.collectAsStateWithLifecycle()
                // Close once the alarm was handled elsewhere (notification action, timeout).
                var seen by remember { mutableStateOf(false) }
                LaunchedEffect(ringing) {
                    if (ringing != null) seen = true
                    else if (seen) finish()
                    else { delay(3_000); if (AlarmService.ringing.value == null) finish() }
                }
                val alarm = ringing ?: AlarmStore.get(this, id)
                RingingScreen(
                    label = alarm?.label.orEmpty(),
                    snoozeMinutes = AlarmStore.snoozeMinutes(this),
                    onSnooze = {
                        AlarmReceiver.snooze(this, alarm?.id ?: id)
                        finish()
                    },
                    onDismiss = {
                        startService(AlarmService.stopIntent(this))
                        finish()
                    },
                )
            }
        }
    }

    companion object {
        fun intent(context: Context, id: Int) =
            Intent(context, AlarmActivity::class.java)
                .putExtra(AlarmReceiver.EXTRA_ID, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    }
}

@Composable
private fun RingingScreen(
    label: String,
    snoozeMinutes: Int,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(1_000)
        }
    }
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "scale",
    )
    val date = LocalDate.now()
    val ru = Locale("ru")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .systemBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(180.dp)
                    .scale(scale)
                    .background(Amber.copy(alpha = 0.15f), CircleShape),
            )
            Box(Modifier.size(110.dp).background(Amber.copy(alpha = 0.35f), CircleShape))
            Text("⏰", fontSize = 56.sp)
        }
        Spacer(Modifier.height(32.dp))
        Text(
            now.format(DateTimeFormatter.ofPattern("HH:mm")),
            fontSize = 84.sp, fontWeight = FontWeight.Light, color = Color.White,
        )
        Text(
            "${date.dayOfWeek.getDisplayName(TextStyle.FULL, ru).replaceFirstChar { it.titlecase(ru) }}, " +
                "${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.FULL, ru)}",
            color = Muted, fontSize = 18.sp,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            label.ifBlank { "Пора вставать!" },
            fontSize = 24.sp, color = Amber, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onSnooze,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                Text("Отложить на $snoozeMinutes мин", fontSize = 18.sp)
            }
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(72.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Amber),
            ) {
                Text("Выключить", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
