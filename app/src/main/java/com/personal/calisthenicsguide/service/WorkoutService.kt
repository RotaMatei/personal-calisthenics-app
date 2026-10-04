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
import com.personal.calisthenics.core.session.SessionMode
import com.personal.calisthenicsguide.CalisthenicsApp
import com.personal.calisthenicsguide.MainActivity
import com.personal.calisthenicsguide.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the process alive and holds a partial wake lock for the whole workout, so
 * isometric timers and rest countdowns keep running (with sound and vibration) when the phone is locked.
 * The screen itself is kept on by the workout screen via FLAG_KEEP_SCREEN_ON.
 */
class WorkoutService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        val runner = (application as CalisthenicsApp).sessionRunner
        val first = buildNotification("Workout in progress", "Timers keep running with the screen off")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, first)
        }
        acquireWakeLock()

        scope.launch {
            runner.state
                .map { state -> if (state.active) describe(state.snapshot) else null }
                .distinctUntilChanged()
                .collect { text ->
                    if (text == null) {
                        stopSelf()
                    } else {
                        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        manager.notify(NOTIFICATION_ID, buildNotification(text.first, text.second))
                    }
                }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    private fun describe(snapshot: EngineSnapshot?): Pair<String, String> {
        if (snapshot == null) return "Workout in progress" to "Starting..."
        val timer = snapshot.timer
        val seconds = timer?.phaseRemainingSeconds
        val title = snapshot.item?.title ?: snapshot.flowItem?.label ?: "Workout"
        return when (snapshot.mode) {
            SessionMode.FLOW -> "${snapshot.phase?.title ?: "Flow"}: ${snapshot.flowItem?.label ?: ""}" to
                "${seconds ?: 0}s left in this step"
            SessionMode.READY -> "Next: $title" to "Set ${snapshot.item?.setNumber ?: 1}/${snapshot.item?.totalSets ?: 1} - tap to start"
            SessionMode.ACTIVE -> title to "${timer?.phase?.label ?: ""} ${seconds ?: 0}s"
            SessionMode.AWAITING_LOG -> "Log your set: $title" to "Reps and RIR"
            SessionMode.RESTING -> "Rest ${seconds ?: 0}s" to "Next: ${snapshot.item?.title ?: "finish"}"
            else -> "Workout in progress" to ""
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_workout)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = getString(R.string.notification_channel_description) }
        manager.createNotificationChannel(channel)
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "calisthenicsguide:workout").apply {
            setReferenceCounted(false)
            acquire(MAX_SESSION_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private companion object {
        const val CHANNEL_ID = "workout"
        const val NOTIFICATION_ID = 1001
        const val MAX_SESSION_MS = 3L * 60L * 60L * 1000L
    }
}
