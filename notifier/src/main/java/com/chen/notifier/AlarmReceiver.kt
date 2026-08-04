package com.chen.notifier

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            MainActivity.ACTION_SEND_NOTIFICATION -> sendTestNotification(context)
            MainActivity.ACTION_SEND_SMS -> sendTestSms(context)
        }
    }

    private fun sendTestNotification(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)

        // 点击通知打开本应用
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context, 0, openIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(context, NotifierApp.CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(context)
                .setPriority(android.app.Notification.PRIORITY_HIGH)
        }

        builder
            .setContentTitle("交管12123")
            .setContentText("您的小型新能源汽车浙A123456于2026年4月2日18时18分在xxx_xxx未按规定停放已被记录，请立即驶离，未及时驶离的，将依法予以处罚，谢谢配合！")
            .setTicker("违停驶离提醒")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(android.app.Notification.DEFAULT_ALL)

        nm.notify(NotifierApp.NOTIFICATION_ID, builder.build())
    }

    private fun sendTestSms(context: Context) {
        val intent = Intent("com.chen.carlistener.DEBUG_SMS")
        intent.setPackage("com.chen.carlistener")
        intent.putExtra("sender", "12123")
        intent.putExtra("message", "您的小型新能源汽车浙A123456于2026年4月2日18时18分在xxx_xxx未按规定停放已被记录，请立即驶离，未及时驶离的，将依法予以处罚，谢谢配合！")
        context.sendBroadcast(intent)
    }
}