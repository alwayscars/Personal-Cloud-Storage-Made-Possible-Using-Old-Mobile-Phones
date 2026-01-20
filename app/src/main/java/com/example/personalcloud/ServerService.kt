package com.example.personalcloud

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class ServerService : Service() {
    private var httpServer: HttpServer? = null

    companion object {
        var isRunning = false
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "CloudServerChannel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START" -> {
                val username = intent.getStringExtra("username") ?: "admin"
                val password = intent.getStringExtra("password") ?: "password"
                startServer(username, password)
            }
            "STOP" -> {
                stopServer()
            }
        }
        return START_STICKY
    }

    private fun startServer(username: String, password: String) {
        if (isRunning) return

        try {
            httpServer = HttpServer(this, 8080, username, password)
            httpServer?.start()
            isRunning = true

            val notification = createNotification("Server is running on port 8080")
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            e.printStackTrace()
            isRunning = false
        }
    }

    private fun stopServer() {
        httpServer?.stop()
        httpServer = null
        isRunning = false
        stopForeground(true)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Cloud Server",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Personal Cloud Storage Server"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(message: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Personal Cloud Server")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopServer()
    }
}