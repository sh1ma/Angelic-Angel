package dev.angelic.angel.stream

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import dev.angelic.angel.R
import dev.angelic.angel.data.AngelApplication
import dev.angelic.angel.ui.MainActivity

class PushService : Service() {
    private var stream: AutoPushStream? = null
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("stream", "通知の受信", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, PushService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, "stream").setSmallIcon(R.drawable.ic_angel)
            .setContentTitle("Angelic Angel").setContentText("通知を受信しています。接続状況はアプリで確認できます")
            .setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "停止", stop).build()).build()
        startForeground(1, notification)
        if (stream == null) stream = AutoPushStream(application as AngelApplication)
        stream?.start()
        return START_STICKY
    }
    override fun onDestroy() { stream?.stop(); stream = null; super.onDestroy() }
}
