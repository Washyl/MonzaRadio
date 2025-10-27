
package com.monza.radio.radio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media.session.MediaButtonReceiver
import android.support.v4.media.session.MediaSessionCompat
import android.content.Context

class RadioService : Service() {

    companion object {
        const val CHANNEL_ID = "monza_radio_channel"
        const val NOTIF_ID = 1001
    }

    private lateinit var mediaSession: MediaSessionCompat

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        mediaSession = MediaSessionCompat(this, "MonzaRadioSession")
        val notif = buildNotification()
        startForeground(NOTIF_ID, notif)
        UnisocFmController.getInstance(applicationContext).open()
    }

    override fun onDestroy() {
        UnisocFmController.getInstance(applicationContext).close()
        mediaSession.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): android.app.Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Monza Radio")
            .setContentText("Playing")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle().setMediaSession(mediaSession.sessionToken))
        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Monza Radio", NotificationManager.IMPORTANCE_LOW)
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(ch)
        }
    }
}
