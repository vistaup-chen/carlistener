package com.chen.carlistener

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * 闹钟式提醒界面 — 底部弹出，向下滑动可关闭
 */
class AlarmActivity : AppCompatActivity() {

    private val ringtoneStopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == RingtoneService.ACTION_RINGTONE_STOPPED) {
                finish()
            }
        }
    }

    // 下滑关闭手势追踪
    private var startY = 0f
    private var isDragging = false
    private val dragThreshold = 200f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("SilentTest", "AlarmActivity.onCreate() — 弹窗界面已启动")

        // 锁屏显示 + 亮屏 + 保持屏幕常亮
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        // 任何版本都保持屏幕常亮，直到用户关闭提醒
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_alarm)

        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "检测到关键字消息"
        findViewById<TextView>(R.id.alarmMessage).text = message

        findViewById<Button>(R.id.dismissButton).setOnClickListener {
            stopRingtoneAndFinish()
        }

        // 点击半透明背景区域也可关闭
        findViewById<View>(R.id.scrim).setOnClickListener {
            stopRingtoneAndFinish()
        }

        // 下滑关闭手势 — 监听拖动区域
        val dragHandle = findViewById<View>(R.id.dragHandle)
        dragHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    isDragging = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    // 跟随手指向下移动卡片
                    if (isDragging) {
                        val delta = event.rawY - startY
                        if (delta > 0) {
                            findViewById<View>(R.id.alarmCard).translationY = delta
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    val delta = event.rawY - startY
                    if (delta > dragThreshold) {
                        // 滑动超过阈值，关闭
                        stopRingtoneAndFinish()
                    } else {
                        // 回弹
                        findViewById<View>(R.id.alarmCard).animate().translationY(0f).setDuration(200).start()
                    }
                    true
                }
                else -> false
            }
        }

        // 注册广播：如果从通知栏停止，也关闭此界面
        val filter = IntentFilter(RingtoneService.ACTION_RINGTONE_STOPPED)
        ContextCompat.registerReceiver(this, ringtoneStopReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun stopRingtoneAndFinish() {
        val stopIntent = Intent(RingtoneService.ACTION_STOP)
        stopIntent.setPackage(packageName)
        sendBroadcast(stopIntent)
        finish()
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
