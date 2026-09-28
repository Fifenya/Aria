package com.aria

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class AriaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Aria status",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Показывает прогресс обучения в фоне"
                setShowBadge(false)
            }
            nm.createNotificationChannel(ch)
        }
    }

    companion object {
        const val CHANNEL_ID = "aria_status"
        const val NOTIF_ID = 1001
    }
}