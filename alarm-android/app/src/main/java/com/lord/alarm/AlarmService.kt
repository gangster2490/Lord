package com.lord.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Plays the alarm sound + vibration in the foreground and shows the full-screen ringing UI. */
class AlarmService : Service() {
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopRinging()
            return START_NOT_STICKY
        }
        val id = intent?.getIntExtra(AlarmReceiver.EXTRA_ID, -1) ?: -1
        val alarm = AlarmStore.get(this, id)
        if (alarm == null) {
            stopRinging()
            return START_NOT_STICKY
        }

        ensureChannel(this)
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(alarm),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else 0,
        )

        // A new alarm while another is ringing replaces it.
        releaseMedia()
        _ringing.value = alarm

        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "budilnik:ring")
            .apply { acquire(LIMIT_MAX_MS) }

        startSound(alarm)
        if (alarm.vibrate) startVibration()

        // Fall silent by itself if nobody reacts.
        handler.removeCallbacks(autoStop)
        handler.postDelayed(autoStop, AlarmStore.ringLimitMinutes(this) * 60_000L)

        // Show the ringing screen directly when allowed (e.g. app in foreground);
        // otherwise the full-screen intent of the notification takes care of it.
        runCatching { startActivity(AlarmActivity.intent(this, id)) }
        return START_NOT_STICKY
    }

    private val autoStop = Runnable { stopRinging() }

    private fun startSound(alarm: Alarm) {
        val uri = alarm.ringtoneUri?.let(Uri::parse)
            ?: RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val candidates = listOfNotNull(
            uri,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
        ).distinct()

        for (candidate in candidates) {
            val mp = MediaPlayer()
            val ok = runCatching {
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                mp.setDataSource(this, candidate)
                mp.isLooping = true
                mp.prepare()
            }.isSuccess
            if (ok) {
                player = mp
                break
            }
            mp.release()
        }

        val mp = player ?: return
        if (AlarmStore.gradualVolume(this)) {
            var step = 0
            mp.setVolume(0.1f, 0.1f)
            val ramp = object : Runnable {
                override fun run() {
                    val p = player ?: return
                    step++
                    val v = (0.1f + step * 0.03f).coerceAtMost(1f)
                    runCatching { p.setVolume(v, v) }
                    if (v < 1f) handler.postDelayed(this, 1_000)
                }
            }
            handler.postDelayed(ramp, 1_000)
        }
        mp.start()
    }

    private fun startVibration() {
        val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator = vib
        val pattern = longArrayOf(0, 800, 600)
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
        @Suppress("DEPRECATION")
        vib.vibrate(VibrationEffect.createWaveform(pattern, 0), attrs)
    }

    private fun buildNotification(alarm: Alarm): Notification {
        val fullScreen = PendingIntent.getActivity(
            this, alarm.id, AlarmActivity.intent(this, alarm.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        fun action(action: String) = PendingIntent.getBroadcast(
            this, action.hashCode() + alarm.id,
            Intent(this, AlarmReceiver::class.java)
                .setAction(action)
                .putExtra(AlarmReceiver.EXTRA_ID, alarm.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("Будильник ${alarm.timeText}")
            .setContentText(alarm.label.ifBlank { "Пора вставать!" })
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            .addAction(0, "Отложить", action(AlarmReceiver.ACTION_SNOOZE))
            .addAction(0, "Выключить", action(AlarmReceiver.ACTION_DISMISS))
            .build()
    }

    private fun releaseMedia() {
        handler.removeCallbacksAndMessages(null)
        player?.runCatching { stop(); release() }
        player = null
        vibrator?.cancel()
        vibrator = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun stopRinging() {
        releaseMedia()
        _ringing.value = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        releaseMedia()
        _ringing.value = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "ringing"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_STOP = "com.lord.alarm.STOP"
        private const val LIMIT_MAX_MS = 60 * 60_000L

        private val _ringing = MutableStateFlow<Alarm?>(null)
        /** Alarm currently ringing, observed by [AlarmActivity] to close itself. */
        val ringing: StateFlow<Alarm?> = _ringing.asStateFlow()

        fun startIntent(context: Context, id: Int) =
            Intent(context, AlarmService::class.java).putExtra(AlarmReceiver.EXTRA_ID, id)

        fun stopIntent(context: Context) =
            Intent(context, AlarmService::class.java).setAction(ACTION_STOP)

        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Звонок будильника", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Показывается, пока звонит будильник"
                    setSound(null, null) // sound is played by the service itself
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
            )
        }
    }
}
