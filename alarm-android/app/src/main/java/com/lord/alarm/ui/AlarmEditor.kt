package com.lord.alarm.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.IntentCompat
import com.lord.alarm.Alarm
import com.lord.alarm.AlarmStore
import com.lord.alarm.shortRu
import com.lord.alarm.untilText
import java.time.DayOfWeek
import java.time.ZonedDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditor(
    initial: Alarm,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (Alarm) -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val time = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    var label by remember { mutableStateOf(initial.label) }
    var days by remember { mutableStateOf(initial.days) }
    var vibrate by remember { mutableStateOf(initial.vibrate) }
    var ringtone by remember { mutableStateOf(initial.ringtoneUri) }
    var confirmDelete by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val uri = res.data?.let {
                IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            }
            val default = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ringtone = uri?.takeIf { it != default }?.toString()
        }
    }

    val draft = initial.copy(hour = time.hour, minute = time.minute, label = label.trim(), days = days,
        vibrate = vibrate, ringtoneUri = ringtone, enabled = true, snoozeAt = null)
    val nextAt = draft.nextTrigger(ZonedDateTime.now()).toInstant().toEpochMilli()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.systemBarsPadding().imePadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Закрыть") }
                    Text(
                        if (isNew) "Новый будильник" else "Изменить будильник",
                        fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                    )
                    if (!isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Удалить")
                        }
                    }
                }

                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TimePicker(
                        state = time,
                        colors = TimePickerDefaults.colors(
                            clockDialColor = SurfaceHigh,
                            timeSelectorSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            timeSelectorUnselectedContainerColor = SurfaceHigh,
                        ),
                    )
                    Text(
                        "Зазвонит ${untilText(System.currentTimeMillis(), nextAt)}",
                        color = Amber, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))

                    SectionTitle("Повтор")
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        DayOfWeek.entries.forEach { d ->
                            val selected = d in days
                            FilterChip(
                                selected = selected,
                                onClick = { days = if (selected) days - d else days + d },
                                label = { Text(d.shortRu(), fontSize = 13.sp) },
                                shape = CircleShape,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Amber,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                                modifier = Modifier.size(width = 44.dp, height = 40.dp),
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickDays("Будни", days, DayOfWeek.entries.take(5).toSet()) { days = it }
                        QuickDays("Выходные", days, setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) { days = it }
                        QuickDays("Каждый день", days, DayOfWeek.entries.toSet()) { days = it }
                    }
                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it.take(60) },
                        label = { Text("Название") },
                        placeholder = { Text("Например: Работа") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))

                    SettingRow(
                        title = "Мелодия",
                        subtitle = ringtoneTitle(context, ringtone),
                        onClick = {
                            val existing = ringtone?.let(Uri::parse)
                                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                            picker.launch(
                                Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                                    .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                    .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Мелодия будильника")
                                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                    .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing),
                            )
                        },
                    ) { Icon(Icons.Default.Notifications, contentDescription = null, tint = Muted) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    SettingRow(title = "Вибрация", subtitle = if (vibrate) "Включена" else "Выключена",
                        onClick = { vibrate = !vibrate }) {
                        Switch(checked = vibrate, onCheckedChange = { vibrate = it })
                    }
                    Spacer(Modifier.height(16.dp))
                }

                Button(
                    onClick = { onSave(draft) },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp),
                ) { Text("Сохранить", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить будильник?") },
            text = { Text("Будильник на ${initial.timeText} будет удалён.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun QuickDays(text: String, current: Set<DayOfWeek>, preset: Set<DayOfWeek>, onPick: (Set<DayOfWeek>) -> Unit) {
    TextButton(onClick = { onPick(if (current == preset) emptySet() else preset) }) {
        Text(text, color = if (current == preset) Amber else Muted, fontSize = 13.sp)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text, color = Muted, fontSize = 14.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    )
}

@Composable
fun SettingRow(title: String, subtitle: String?, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp)
            if (subtitle != null) Text(subtitle, color = Muted, fontSize = 13.sp)
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

private fun ringtoneTitle(context: Context, uri: String?): String {
    if (uri == null) return "По умолчанию"
    return runCatching { RingtoneManager.getRingtone(context, Uri.parse(uri))?.getTitle(context) }
        .getOrNull() ?: "Выбранная мелодия"
}

@Composable
fun SettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var snooze by remember { mutableStateOf(AlarmStore.snoozeMinutes(context)) }
    var limit by remember { mutableStateOf(AlarmStore.ringLimitMinutes(context)) }
    var ramp by remember { mutableStateOf(AlarmStore.gradualVolume(context)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Настройки") },
        text = {
            Column {
                SectionTitle("Отложить на (мин)")
                ChoiceRow(listOf(5, 10, 15, 20, 30), snooze) {
                    snooze = it
                    AlarmStore.setSnoozeMinutes(context, it)
                }
                Spacer(Modifier.height(16.dp))
                SectionTitle("Звонить не дольше (мин)")
                ChoiceRow(listOf(1, 5, 10, 15, 30), limit) {
                    limit = it
                    AlarmStore.setRingLimitMinutes(context, it)
                }
                Spacer(Modifier.height(8.dp))
                SettingRow(
                    title = "Плавное нарастание громкости",
                    subtitle = "Громкость растёт ~30 секунд",
                    onClick = { ramp = !ramp; AlarmStore.setGradualVolume(context, ramp) },
                ) {
                    Switch(checked = ramp, onCheckedChange = { ramp = it; AlarmStore.setGradualVolume(context, it) })
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } },
    )
}

@Composable
private fun ChoiceRow(options: List<Int>, selected: Int, onPick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { v ->
            FilterChip(
                selected = v == selected,
                onClick = { onPick(v) },
                label = { Text("$v") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Amber,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}
