package com.subu1979.imagesender.auto

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.subu1979.imagesender.MainActivity
import com.subu1979.imagesender.R

/**
 * Keeps the app unfrozen for the life of one arming.
 *
 * Android caches — and OEM builds freeze — a process whose activity went to the background. Observed
 * on a CPH2637 (ColorOS, Android 16): the accessibility service stopped receiving events roughly
 * twenty seconds after WhatsApp came to the front, while the process itself stayed alive. A gallery
 * attach finished inside that window; taking a photo did not, so nothing was ever sent.
 *
 * A foreground service prevents that freeze. It exists only while a send is armed, and its
 * notification is also the promised way to call the whole thing off.
 */
class ArmingService : Service() {

    private val stopper = Handler(Looper.getMainLooper())
    private val expiryCheck = object : Runnable {
        override fun run() {
            if (AutoSendSession.activeTarget(this@ArmingService) == null) {
                stopSelf()
            } else {
                stopper.postDelayed(this, POLL_MS)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            AutoSendSession.cancel(this, "cancelled from notification")
            stopSelf()
            return START_NOT_STICKY
        }

        val target = AutoSendSession.activeTarget(this)
        if (target == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification(target))
        stopper.removeCallbacks(expiryCheck)
        stopper.postDelayed(expiryCheck, POLL_MS)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopper.removeCallbacks(expiryCheck)
        super.onDestroy()
    }

    private fun buildNotification(target: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.arming_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
        )

        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val cancel = PendingIntent.getService(
            this,
            1,
            Intent(this, ArmingService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.arming_title, "+$target"))
            .setContentText(getString(R.string.arming_text))
            .setSmallIcon(R.drawable.ic_arming)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(
                Notification.Action.Builder(
                    null,
                    getString(R.string.arming_cancel),
                    cancel
                ).build()
            )
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "auto_send_arming"
        private const val NOTIFICATION_ID = 4001
        private const val POLL_MS = 5_000L
        private const val ACTION_CANCEL = "com.subu1979.imagesender.CANCEL_ARMING"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, ArmingService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ArmingService::class.java))
        }
    }
}
