package com.chen.carlistener

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import java.util.Timer
import java.util.TimerTask

class RingtoneService : Service() {

    companion object {
        private const val TAG = "RingtoneService"
        private const val RING_DURATION = 5 * 60 * 1000L // 5 分钟，和闹钟一致
        private const val NOTIFICATION_CHANNEL_ID = "ringtone_channel"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.chen.carlistener.STOP_RINGTONE"
        const val ACTION_RINGTONE_STOPPED = "com.chen.carlistener.RINGTONE_STOPPED"

        /** 供外部查询服务是否正在响铃（替代废弃的 getRunningServices） */
        @Volatile
        var isRinging: Boolean = false
            private set
    }

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var timer: Timer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var savedAlarmVolume: Int = -1
    private var savedRingVolume: Int = -1
    private var currentMessage: String = ""

    // 用 BroadcastReceiver 接收停止指令，比 PendingIntent 启动 Service 更可靠
    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_STOP) {
                Log.d(TAG, "BroadcastReceiver 收到停止指令")
                stopRinging()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "RingtoneService 创建")
        createNotificationChannel()
        val filter = IntentFilter(ACTION_STOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stopReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "收到启动命令: ${intent?.getStringExtra("action")}")

        // 第一时间获取 WakeLock，防止启动过程中设备休眠
        acquireWakeLock()

        startForeground(NOTIFICATION_ID, buildForegroundNotification())

        val action = intent?.getStringExtra("action")
        val message = intent?.getStringExtra("message") ?: ""

        when (action) {
            "ring", "test" -> {
                Log.d(TAG, "开始响铃，消息: $message")
                currentMessage = message
                if (isRinging) {
                    // 已在响铃：只重置超时计时器，不重复弹窗
                    Log.d(TAG, "已在响铃中，重置超时计时器")
                    timer?.cancel()
                    timer = Timer().apply {
                        schedule(object : TimerTask() {
                            override fun run() {
                                Log.d(TAG, "5分钟超时，自动停止响铃")
                                stopRinging()
                                showTimeoutNotification()
                            }
                        }, RING_DURATION)
                    }
                    // 刷新 WakeLock，防止重响间隔内过期
                    wakeLock?.let { if (it.isHeld) it.release() }
                    acquireWakeLock()
                } else {
                    startRinging()
                }
            }
            "popup_only" -> {
                // 静默测试：只弹窗，不响铃不振动
                Log.d(TAG, "静默弹窗测试，消息: $message")
                currentMessage = message
                launchAlarmActivity(message)
            }
            "stop" -> {
                Log.d(TAG, "手动停止响铃")
                stopRinging()
            }
            "idle" -> {
                Log.d(TAG, "空闲保活，不响铃")
            }
            else -> {
                // START_STICKY 重建时 intent 为 null，保持前台服务存活即可
                Log.d(TAG, "action 为空或未知: $action，保持服务存活")
            }
        }

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "响铃服务",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "用于保持响铃服务运行"
                setSound(null, null)
                // 振动模式：立即振动 800ms，暂停 400ms，循环
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 800, 400, 800, 400, 800)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        // 停止响铃按钮 —— 发广播，比 PendingIntent.getService 更可靠
        val stopIntent = Intent(ACTION_STOP)
        stopIntent.setPackage(packageName)
        val stopPendingIntent = PendingIntent.getBroadcast(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("车辆监听器")
            .setContentText("正在响铃提醒...")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .addAction(android.R.drawable.ic_media_pause, "停止响铃", stopPendingIntent)
            .setVibrate(longArrayOf(0, 800, 400, 800, 400, 800))
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
    }

    private fun startRinging() {
        try {
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            val prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
            val volumePercent = prefs.getInt(MainActivity.KEY_VOLUME, MainActivity.DEFAULT_VOLUME)

            // 保存原始音量（用于响铃结束后恢复）
            savedAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            savedRingVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING)
            Log.d(TAG, "原始音量: alarm=$savedAlarmVolume, ring=$savedRingVolume, 设置音量=$volumePercent%")

            if (volumePercent > 0) {
                // 按设置比例调整音量
                val maxAlarmVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                val targetVolume = (maxAlarmVolume * volumePercent / 100f).toInt().coerceIn(1, maxAlarmVolume)
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, targetVolume, 0)
                Log.d(TAG, "闹钟音量设为: $targetVolume / $maxAlarmVolume")

                // 请求音频焦点
                requestAudioFocus(audioManager)

                // 获取铃声 URI
                val ringtoneUri =
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                Log.d(TAG, "铃声 URI: $ringtoneUri")

                if (ringtoneUri == null) {
                    Log.e(TAG, "无法获取铃声 URI，尝试兜底方案")
                    playFallbackAlarm()
                } else {
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(applicationContext, ringtoneUri)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_ALARM)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                    .build()
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            setAudioStreamType(AudioManager.STREAM_ALARM)
                        }
                        isLooping = true
                        prepare()
                        start()
                    }

                    if (mediaPlayer?.isPlaying == true) {
                        Log.d(TAG, "MediaPlayer 播放成功 ✓")
                    } else {
                        Log.e(TAG, "MediaPlayer start() 后未在播放，尝试兜底")
                        mediaPlayer?.release()
                        mediaPlayer = null
                        playFallbackAlarm()
                    }
                }
            } else {
                Log.d(TAG, "音量为 0，不播放声音")
            }

            acquireWakeLock()

            startVibration() // 强度由 Spinner 控制，0=关

            isRinging = true

            // 弹出闹钟式全屏界面
            launchAlarmActivity(currentMessage)

            timer = Timer().apply {
                schedule(object : TimerTask() {
                    override fun run() {
                        Log.d(TAG, "5分钟超时，自动停止响铃")
                        stopRinging()
                        showTimeoutNotification()
                    }
                }, RING_DURATION)
            }

        } catch (e: Exception) {
            Log.e(TAG, "响铃失败: ${e.message}", e)
            try {
                playFallbackAlarm()
                acquireWakeLock()
                startVibration()
                isRinging = true
                launchAlarmActivity(currentMessage)
            } catch (e2: Exception) {
                Log.e(TAG, "兜底也失败: ${e2.message}", e2)
                stopSelf()
            }
        }
    }

    /**
     * 兜底方案：用 Ringtone API 直接播放，不依赖 MediaPlayer
     */
    @Suppress("DEPRECATION")
    private fun playFallbackAlarm() {
        Log.d(TAG, "使用 Ringtone 兜底播放")
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        if (uri != null) {
            val ringtone = RingtoneManager.getRingtone(applicationContext, uri)
            ringtone?.play()
            Log.d(TAG, "Ringtone 兜底播放已调用")
        }
    }

    private fun requestAudioFocus(audioManager: AudioManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .build()
            audioFocusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_RING,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        // SCREEN_BRIGHT_WAKE_LOCK + ACQUIRE_CAUSES_WAKEUP：兼容 Android 14+，可靠亮屏
        wakeLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "CarListener::RingtoneWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(RING_DURATION + 10000L)
        }
        Log.d(TAG, "WakeLock 已获取（含亮屏）")
    }

    private fun launchAlarmActivity(message: String) {
        // 用 WindowManager 覆盖层弹出提醒，和测试流程统一
        // Android 12+ BAL 限制下，这是后台/锁屏场景唯一可靠方案
        showOverlayPopup(message)
    }

    /**
     * WindowManager 覆盖层弹窗 — 真实流程和测试共用
     */
    private fun showOverlayPopup(message: String) {
        if (!Settings.canDrawOverlays(this)) {
            Log.e("RingtoneService", "没有悬浮窗权限，降级为 Activity 启动")
            launchAlarmActivityFallback(message)
            return
        }

        val windowManager = getSystemService(WINDOW_SERVICE) as android.view.WindowManager
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        // 全屏透明背景
        val overlay = android.widget.FrameLayout(this).apply {
            setBackgroundColor(0x99000000.toInt())
            isClickable = true
            isFocusable = true
        }

        // 红色卡片
        val card = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setBackgroundColor(0xFFD32F2F.toInt())
            setPadding(dp(32), dp(16), dp(32), dp(32))
            isClickable = true

            // 下拉指示条
            addView(android.view.View(this@RingtoneService).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(dp(40), dp(5)).apply {
                    bottomMargin = dp(16)
                }
                setBackgroundColor(0x80FFFFFF.toInt())
            })

            // 警告图标
            addView(android.widget.TextView(this@RingtoneService).apply {
                text = "⚠️"
                textSize = 48f
                gravity = android.view.Gravity.CENTER
            })

            // 标题
            addView(android.widget.TextView(this@RingtoneService).apply {
                text = "车辆监听提醒"
                textSize = 22f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(0xFFFFFFFF.toInt())
                gravity = android.view.Gravity.CENTER
                setPadding(0, dp(8), 0, dp(8))
            })

            // 消息内容
            addView(android.widget.TextView(this@RingtoneService).apply {
                text = message
                textSize = 16f
                setTextColor(0xFFFFFFFF.toInt())
                gravity = android.view.Gravity.CENTER
                setPadding(0, dp(8), 0, dp(16))
            })

            // 关闭按钮
            addView(android.widget.Button(this@RingtoneService).apply {
                text = "关闭提醒"
                textSize = 18f
                setTextColor(0xFFD32F2F.toInt())
                setBackgroundColor(0xFFFFFFFF.toInt())
                minHeight = dp(56)
                layoutParams = android.widget.LinearLayout.LayoutParams(dp(200), android.widget.LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(8)
                }
                setOnClickListener {
                    stopRinging()
                }
            })

            // 提示文字
            addView(android.widget.TextView(this@RingtoneService).apply {
                text = "点击背景区域可关闭 · 5 分钟后自动停止"
                textSize = 12f
                setTextColor(0xFFFFCDD2.toInt())
                gravity = android.view.Gravity.CENTER
                setPadding(0, dp(12), 0, 0)
            })
        }

        val cardParams = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.BOTTOM
            leftMargin = dp(16)
            rightMargin = dp(16)
            bottomMargin = dp(48)
        }
        overlay.addView(card, cardParams)

        // 点击背景关闭
        overlay.setOnClickListener {
            stopRinging()
        }

        val params = android.view.WindowManager.LayoutParams(
            android.view.WindowManager.LayoutParams.MATCH_PARENT,
            android.view.WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                android.view.WindowManager.LayoutParams.TYPE_PHONE,
            android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        )

        try {
            windowManager.addView(overlay, params)
            overlayView = overlay
            Log.d(TAG, "覆盖层已添加 ✓")
        } catch (e: Exception) {
            Log.e(TAG, "添加覆盖层失败: ${e.message}", e)
            launchAlarmActivityFallback(message)
        }
    }

    private var overlayView: android.view.View? = null

    private fun removeOverlay() {
        overlayView?.let {
            try {
                val windowManager = getSystemService(WINDOW_SERVICE) as android.view.WindowManager
                windowManager.removeView(it)
            } catch (_: Exception) {}
        }
        overlayView = null
    }

    /**
     * 降级方案：直接启动 Activity（有悬浮窗权限时不会走到这里）
     */
    private fun launchAlarmActivityFallback(message: String) {
        try {
            val intent = Intent(this, AlarmActivity::class.java)
            intent.putExtra(AlarmActivity.EXTRA_MESSAGE, message)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "降级启动也失败: ${e.message}")
        }
    }

    private fun showTimeoutNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        builder.setContentTitle("车辆监听器")
            .setContentText("检测到关键字消息，已响铃提醒（5分钟自动停止）")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setAutoCancel(true)
        nm.notify(NOTIFICATION_ID + 1, builder.build())
    }

    private fun startVibration() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(VibratorManager::class.java)
                vibrator = vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator?.hasVibrator() == true) {
                val prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
                val strength = prefs.getInt(MainActivity.KEY_VIBRATION_STRENGTH, MainActivity.DEFAULT_VIBRATION_STRENGTH)
                if (strength == 0) {
                    Log.d(TAG, "振动已关闭")
                    return
                }
                // 振幅：1=弱(120), 2=中(200), 3=强(255)
                val amplitude = when (strength) {
                    1 -> 120
                    2 -> 200
                    else -> 255
                }
                // 振动模式：1000ms振动 + 300ms暂停，循环
                val timings = longArrayOf(0, 1000, 300, 1000, 300, 1000)
                val amplitudes = intArrayOf(0, amplitude, 0, amplitude, 0, amplitude)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createWaveform(timings, amplitudes, 0)
                    if (Build.VERSION.SDK_INT >= 33) {
                        // Android 13+ 必须指定 VibrationAttributes 才能在后台振动
                        val attrs = android.os.VibrationAttributes.Builder()
                            .setUsage(android.os.VibrationAttributes.USAGE_ALARM)
                            .build()
                        vibrator?.vibrate(effect, attrs)
                    } else {
                        vibrator?.vibrate(effect)
                    }
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(timings, 0)
                }
                Log.d(TAG, "振动已启动, 强度=$strength, 振幅=$amplitude")
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动振动失败", e)
        }
    }

    private fun releaseMedia() {
        try {
            timer?.cancel()
            timer = null

            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }

            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
            mediaPlayer = null

            vibrator?.cancel()
            vibrator = null

            wakeLock?.let { if (it.isHeld) it.release() }
            wakeLock = null
        } catch (e: Exception) {
            Log.e(TAG, "释放媒体资源失败", e)
        }
    }

    private fun stopRinging() {
        Log.d(TAG, "停止响铃")
        releaseMedia()
        removeOverlay()
        isRinging = false

        try {
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            if (savedAlarmVolume >= 0) {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, savedAlarmVolume, 0)
                Log.d(TAG, "闹钟音量已恢复: $savedAlarmVolume")
                savedAlarmVolume = -1
            }
            if (savedRingVolume >= 0) {
                audioManager.setStreamVolume(AudioManager.STREAM_RING, savedRingVolume, 0)
                Log.d(TAG, "铃声音量已恢复: $savedRingVolume")
                savedRingVolume = -1
            }
        } catch (e: Exception) {
            Log.e(TAG, "恢复音量失败", e)
        }

        // 广播通知 MainActivity 更新状态
        val stoppedIntent = Intent(ACTION_RINGTONE_STOPPED)
        sendBroadcast(stoppedIntent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "RingtoneService 销毁")
        try {
            unregisterReceiver(stopReceiver)
        } catch (_: Exception) {}
        releaseMedia()
        removeOverlay()
        // 服务被系统杀时 stopRinging 不会走，必须在此重置静态标志
        isRinging = false
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
