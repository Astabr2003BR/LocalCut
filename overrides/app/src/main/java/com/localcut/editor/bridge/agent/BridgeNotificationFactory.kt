package com.localcut.editor.bridge.agent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.localcut.editor.MainActivity

class BridgeNotificationFactory(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        ensureChannels()
    }

    fun foreground(status: String): Notification = builder(CHANNEL_FOREGROUND)
        .setContentTitle("LocalCut conectado ao ChatGPT")
        .setContentText(status.take(120))
        .setOngoing(true)
        .setContentIntent(openAppIntent())
        .addAction(
            Notification.Action.Builder(
                null,
                "Desconectar",
                serviceActionIntent(BridgeConnectionService.ACTION_DISCONNECT, 101),
            ).build(),
        )
        .build()

    fun reconnectFallback(): Notification = builder(CHANNEL_STATUS)
        .setContentTitle("LocalCut precisa reconectar")
        .setContentText("Toque para reconectar ao ChatGPT")
        .setAutoCancel(true)
        .setContentIntent(serviceActionIntent(BridgeConnectionService.ACTION_RECONNECT, 102))
        .build()

    fun postReconnectFallback() {
        manager.notify(RECONNECT_NOTIFICATION_ID, reconnectFallback())
    }

    fun cancelReconnectFallback() {
        manager.cancel(RECONNECT_NOTIFICATION_ID)
    }

    private fun builder(channelId: String): Notification.Builder {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        return builder
            .setSmallIcon(context.applicationInfo.icon)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        100,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun serviceActionIntent(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        context,
        requestCode,
        Intent(context, BridgeConnectionService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_FOREGROUND,
                "Conexão ChatGPT",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Mantém o LocalCut conectado ao ChatGPT quando você ativar o Bridge."
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_STATUS,
                "Status da conexão ChatGPT",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Avisa quando o LocalCut precisa de uma ação para reconectar."
            },
        )
    }

    companion object {
        const val FOREGROUND_NOTIFICATION_ID = 4101
        const val RECONNECT_NOTIFICATION_ID = 4102
        private const val CHANNEL_FOREGROUND = "localcut_bridge_foreground_v1"
        private const val CHANNEL_STATUS = "localcut_bridge_status_v1"
    }
}
