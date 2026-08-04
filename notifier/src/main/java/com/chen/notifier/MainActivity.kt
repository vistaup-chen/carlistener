package com.chen.notifier

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.SystemClock
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private var countDownTimer: CountDownTimer? = null
    private var pending = false
    private lateinit var button: Button
    private lateinit var smsButton: Button

    companion object {
        const val ACTION_SEND_NOTIFICATION = "com.chen.notifier.SEND_NOTIFICATION"
        const val ACTION_SEND_SMS = "com.chen.notifier.SEND_SMS"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 获取状态栏高度，手动设置 padding
        val statusBarHeight = getStatusBarHeight()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(48, 48 + statusBarHeight, 48, 48)
        }

        button = Button(this).apply {
            text = "发送测试通知"
            textSize = 16f
            minHeight = 120
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 32
            }
            setOnClickListener {
                if (pending) return@setOnClickListener
                if (!ensureNotificationPermission()) return@setOnClickListener
                startCountdown("通知")
            }
        }

        smsButton = Button(this).apply {
            text = "发送测试短信"
            textSize = 16f
            minHeight = 120
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 0
            }
            setOnClickListener {
                if (pending) return@setOnClickListener
                startCountdown("短信")
            }
        }

        layout.addView(button)
        layout.addView(smsButton)
        setContentView(layout)
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }

    private fun startCountdown(type: String) {
        pending = true
        val targetButton = if (type == "短信") smsButton else button
        targetButton.isEnabled = false
        Toast.makeText(this, "5秒后发送测试$type", Toast.LENGTH_SHORT).show()

        // 使用 AlarmManager 确保息屏后也能发送（核心修复）
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val sendAction = if (type == "短信") ACTION_SEND_SMS else ACTION_SEND_NOTIFICATION
        val sendIntent = Intent(sendAction).apply {
            `package` = packageName
        }
        val sendPendingIntent = PendingIntent.getBroadcast(
            this, if (type == "短信") 1 else 0, sendIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Android 12+ 需要检查 exact alarm 权限
        val canScheduleExactAlarms = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()

        if (canScheduleExactAlarms) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 5000,
                sendPendingIntent
            )
        } else {
            // 降级方案：使用 allowWhileIdle（不精确但息屏能工作）
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 5000,
                sendPendingIntent
            )
            Toast.makeText(this, "请授予闹钟权限以获得精确延迟", Toast.LENGTH_LONG).show()
        }

        // UI 倒计时 + 兜底直接调用（如果 AlarmManager 失败）
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1000).toInt() + 1
                targetButton.text = "${seconds}秒后发送..."
            }

            override fun onFinish() {
                // 兜底：如果 AlarmManager 没触发，这里也会发送
                if (type == "短信") sendTestSms() else sendTestNotification()
                resetButtonState(targetButton, type)
            }
        }.start()
    }

    private fun sendTestSms() {
        val intent = Intent("com.chen.carlistener.DEBUG_SMS")
        intent.setPackage("com.chen.carlistener")
        intent.putExtra("sender", "12123")
        intent.putExtra("message", "您的小型新能源汽车浙A123456于2026年4月2日18时18分在xxx_xxx未按规定停放已被记录，请立即驶离，未及时驶离的，将依法予以处罚，谢谢配合！")
        sendBroadcast(intent)
    }

    private fun ensureNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    100
                )
                return false
            }
        }
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "权限已授予，请再次点击按钮", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendTestNotification() {
        val nm = getSystemService(NotificationManager::class.java)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(this, NotifierApp.CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(this)
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

    private fun resetButtonState(targetButton: Button, type: String) {
        pending = false
        targetButton.isEnabled = true
        targetButton.text = if (type == "短信") "发送测试短信" else "发送测试通知"
        Toast.makeText(this, "${type}已发送", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
