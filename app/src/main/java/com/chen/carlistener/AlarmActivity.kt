package com.chen.carlistener

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * 闹钟式全屏提醒界面 — 锁屏上弹出，用户点击关闭才停止
 */
class AlarmActivity : AppCompatActivity() {

    private val ringtoneStopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == RingtoneService.ACTION_RINGTONE_STOPPED) {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 锁屏显示 + 亮屏
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        setContentView(R.layout.activity_alarm)

        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "检测到关键字消息"
        findViewById<TextView>(R.id.alarmMessage).text = message

        findViewById<Button>(R.id.dismissButton).setOnClickListener {
            // 发广播停止响铃
            val stopIntent = Intent(RingtoneService.ACTION_STOP)
            stopIntent.setPackage(packageName)
            sendBroadcast(stopIntent)
            finish()
        }

        // 注册广播：如果从通知栏停止，也关闭此界面
        val filter = IntentFilter(RingtoneService.ACTION_RINGTONE_STOPPED)
        ContextCompat.registerReceiver(this, ringtoneStopReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(ringtoneStopReceiver)
        } catch (_: Exception) {}
    }

    companion object {
        const val EXTRA_MESSAGE = "alarm_message"
    }
}
