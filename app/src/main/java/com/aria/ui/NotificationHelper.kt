package com.aria.ui

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.aria.AriaApp
import com.aria.train.TrainState

object NotificationHelper {

    fun showStatus(context: Context, state: TrainState, modelLabel: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val status = if (state.running) "training" else "idle"
        val text = if (state.epoch > 0) {
            "epoch=${state.epoch}  loss=%.4f  best=%.4f".format(state.loss, state.bestLoss)
        } else {
            "no training yet"
        }

        val notif: Notification = NotificationCompat.Builder(context, AriaApp.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Aria · $modelLabel · $status")
            .setContentText(text)
            .setOngoing(false)
            .setSilent(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        try { nm.notify(AriaApp.NOTIF_ID, notif) } catch (_: Exception) {}
    }

    fun clear(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try { nm.cancel(AriaApp.NOTIF_ID) } catch (_: Exception) {}
    }
}