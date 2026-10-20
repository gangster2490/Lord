package com.lord.alarm.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lord.alarm.Alarm
import com.lord.alarm.AlarmScheduler
import com.lord.alarm.AlarmStore
import com.lord.alarm.daysSummary
import com.lord.alarm.untilText
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val HHMM = DateTimeFormatter.ofPattern("HH:mm")

fun saveAndSchedule(context: Context, alarm: Alarm, announce: Boolean = true) {
    AlarmStore.upsert(context, alarm)
    val at = AlarmScheduler.schedule(context, alarm)
    if (announce && at != null) {
        Toast.makeText(
            context,
            "Будильник зазвонит ${untilText(System.currentTimeMillis(), at)}",
            Toast.LENGTH_SHORT,
        ).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmApp() {
    val context = LocalContext.current
    val alarms by AlarmStore.alarms.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Alarm?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    // Re-check permissions each time the user comes back from system settings.
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumeTick++
        onPauseOrDispose { }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Будильник", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Настройки")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    val t = ZonedDateTime.now().plusMinutes(1)
                    editing = Alarm(id = AlarmStore.newId(context), hour = t.hour, minute = t.minute)
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Добавить") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ClockHeader(now, alarms) }
            item(key = "perm-$resumeTick") { PermissionBanners() }
            if (alarms.isEmpty()) {
                item {
                    Text(
                        "Будильников пока нет.\nНажмите «Добавить», чтобы создать первый.",
                        color = Muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                    )
                }
            }
            items(alarms, key = { it.id }) { alarm ->
                AlarmCard(
                    alarm = alarm,
                    now = now,
                    onClick = { editing = alarm },
                    onToggle = { on ->
                        saveAndSchedule(context, alarm.copy(enabled = on, snoozeAt = null), announce = on)
                    },
                )
            }
        }
    }

    editing?.let { alarm ->
        AlarmEditor(
            initial = alarm,
            isNew = alarms.none { it.id == alarm.id },
            onDismiss = { editing = null },
            onSave = {
                saveAndSchedule(context, it.copy(enabled = true, snoozeAt = null))
                editing = null
            },
            onDelete = {
                AlarmScheduler.cancel(context, alarm.id)
                AlarmStore.delete(context, alarm.id)
                editing = null
            },
        )
    }
    if (showSettings) SettingsDialog(onDismiss = { showSettings = false })
}

@Composable
private fun ClockHeader(now: Long, alarms: List<Alarm>) {
    val zdt = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
    val ru = Locale("ru")
    val next = alarms.mapNotNull { a -> a.nextRingMillis(zdt)?.let { a to it } }.minByOrNull { it.second }

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            zdt.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
            fontSize = 60.sp, fontWeight = FontWeight.Light,
        )
        Text(
            "${zdt.dayOfWeek.getDisplayName(TextStyle.FULL, ru).replaceFirstChar { it.titlecase(ru) }}, " +
                "${zdt.dayOfMonth} ${zdt.month.getDisplayName(TextStyle.FULL, ru)}",
            color = Muted,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            if (next == null) "Нет активных будильников"
            else {
                val at = Instant.ofEpochMilli(next.second).atZone(ZoneId.systemDefault())
                "Следующий: ${at.format(HHMM)} — ${untilText(now, next.second)}"
            },
            color = if (next == null) Muted else Amber,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun AlarmCard(alarm: Alarm, now: Long, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val active = alarm.enabled || alarm.snoozeAt != null
    val fg = if (active) Color.White else Muted
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(alarm.timeText, fontSize = 44.sp, fontWeight = FontWeight.Light, color = fg)
                val subtitle = listOf(alarm.label, daysSummary(alarm.days)).filter { it.isNotBlank() }
                Text(subtitle.joinToString(" · "), color = Muted)
                val snooze = alarm.snoozeAt
                if (snooze != null && snooze > now) {
                    val at = Instant.ofEpochMilli(snooze).atZone(ZoneId.systemDefault())
                    Text("Отложен до ${at.format(HHMM)}", color = Amber, fontSize = 13.sp)
                }
            }
            Switch(checked = alarm.enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun PermissionBanners() {
    val context = LocalContext.current
    var notifGranted by remember { mutableStateOf(hasNotificationPermission(context)) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifGranted = it
    }
    LaunchedEffect(Unit) {
        if (!notifGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!notifGranted) {
            Banner("Разрешите уведомления — без них будильник не сможет показать экран звонка.") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                context.openSettings(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            }
        }
        if (!AlarmScheduler.canScheduleExact(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Banner("Разрешите точные будильники, чтобы звонок срабатывал минута в минуту.") {
                context.openSettings(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            }
        }
        if (Build.VERSION.SDK_INT >= 34 &&
            !context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        ) {
            Banner("Разрешите полноэкранные уведомления, чтобы будильник открывался на заблокированном экране.") {
                context.openSettings(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
            }
        }
    }
}

private fun hasNotificationPermission(context: Context) =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

private fun Context.openSettings(action: String) {
    val intent = Intent(action)
        .setData(Uri.parse("package:$packageName"))
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    runCatching { startActivity(intent) }.onFailure {
        runCatching {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }
}

@Composable
private fun Banner(text: String, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = Amber)
            Spacer(Modifier.width(12.dp))
            Text(
                text, color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClick) { Text("Разрешить") }
        }
    }
}
