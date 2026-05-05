package com.chen.notifier

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        button = Button(this).apply {
            text = "发送测试通知"
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 24
            }
            setOnClickListener {
                if (pending) return@setOnClickListener
                if (!ensureNotificationPermission()) return@setOnClickListener
                startCountdown()
            }
        }

        layout.addView(button)
        setContentView(layout)
    }

    private fun startCountdown() {
        pending = true
        button.isEnabled = false
        Toast.makeText(this, "5秒后发送通知", Toast.LENGTH_SHORT).show()

        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1000).toInt() + 1
                button.text = "${seconds}秒后发送..."
            }

            override fun onFinish() {
                sendTestNotification()
                pending = false
                button.isEnabled = true
                button.text = "发送测试通知"
                Toast.makeText(this@MainActivity, "通知已发送", Toast.LENGTH_SHORT).show()
            }
        }.start()
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

        // 点击通知打开本应用
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

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
