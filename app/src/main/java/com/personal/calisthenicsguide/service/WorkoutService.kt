package com.personal.calisthenicsguide.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.personal.calisthenics.core.session.EngineSnapshot
import com.personal.calisthenics.core.session.StageKind
import com.personal.calisthenicsguide.CalisthenicsApp
import com.personal.calisthenicsguide.MainActivity
import com.personal.calisthenicsguide.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Foreground service that runs for the length of a workout. It holds a partial wake lock so the CPU keeps ticking the
 * timers (and playing beeps) while the screen is off or the phone is in a pocket, and shows the current timer in an
 * ongoing notification. The timers themselves live in [com.personal.calisthenicsguide.session.SessionController].
 */
class WorkoutService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    private var observer: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        val first = buildNotification("Workout in progress")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, first)
        }
        acquireWakeLock()
        val controller = (application as CalisthenicsApp).sessionController
        observer?.cancel()
        observer = scope.launch {
            controller.ui
                .map { describe(it.snapshot) }
                .distinctUntilChanged()
                .collect { text ->
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(NOTIFICATION_ID, buildNotification(text))
                }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        observer?.cancel()
        scope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "calisthenicsguide:workout").apply {
            setReferenceCounted(false)
            acquire(MAX_WAKE_LOCK_MS)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW)
        channel.description = getString(R.string.notification_channel_description)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_workout)
            .setContentTitle("Calisthenics workout")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setContentIntent(open)
            .build()
    }

    private fun describe(snap: EngineSnapshot?): String {
        if (snap == null) return "Workout in progress"
        val clock = snap.timer?.let { clockText(it.phaseRemainingSeconds) }
        return when (snap.stage) {
            StageKind.FLOW -> "${snap.timer?.phase?.label ?: "Flow"}  $clock"
            StageKind.ACTIVE -> "${snap.item?.title ?: "Set"}: ${snap.timer?.phase?.label ?: ""}  $clock"
            StageKind.REST -> "Rest $clock  -  next: ${snap.nextItem?.title ?: "finish"}"
            StageKind.READY -> "Ready: ${snap.item?.title ?: ""}"
            StageKind.LOGGING -> "Log your set: ${snap.item?.title ?: ""}"
            StageKind.FLOW_READY -> "Next phase is ready"
            StageKind.JOINT_LOG -> "Rate your joints to finish"
            else -> "Workout in progress"
        }
    }

    private fun clockText(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

    private companion object {
        const val CHANNEL_ID = "workout"
        const val NOTIFICATION_ID = 7
        const val MAX_WAKE_LOCK_MS = 4L * 60L * 60L * 1000L
    }
}
