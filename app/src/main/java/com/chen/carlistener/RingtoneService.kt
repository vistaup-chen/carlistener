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

        startForeground(NOTIFICATION_ID, buildForegroundNotification())

        val action = intent?.getStringExtra("action")
        val message = intent?.getStringExtra("message") ?: ""

        when (action) {
            "ring", "test" -> {
                Log.d(TAG, "开始响铃，消息: $message")
                currentMessage = message
                if (isRinging) {
                    // 已在响铃：重置超时计时器，避免重叠播放
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
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
    }

    private fun startRinging() {
        try {
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

            // 用 STREAM_ALARM 比 STREAM_RING 更可靠，静音模式下也能响
            savedAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            savedRingVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING)
            val maxAlarmVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarmVolume, 0)
            Log.d(TAG, "闹钟音量: saved=$savedAlarmVolume, max=$maxAlarmVolume, 铃声saved=$savedRingVolume")

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
                // 先启动播放，再转前台（Android 14+ 要求 media 已在播放）
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

            acquireWakeLock()
            startVibration()
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
            // 最终兜底：用 Ringtone API
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
        // FULL_WAKE_LOCK + ACQUIRE_CAUSES_WAKEUP：响铃时点亮屏幕
        wakeLock = pm.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "CarListener::RingtoneWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(RING_DURATION + 5000L)
        }
        Log.d(TAG, "WakeLock 已获取（含亮屏）")
    }

    private fun launchAlarmActivity(message: String) {
        // 用全屏通知弹出 AlarmActivity，不受后台启动限制
        try {
            val alarmIntent = Intent(this, AlarmActivity::class.java)
            alarmIntent.putExtra(AlarmActivity.EXTRA_MESSAGE, message)
            alarmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pendingIntent = PendingIntent.getActivity(
                this, 1, alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
            }
            val notification = builder
                .setContentTitle("⚠️ 车辆监听提醒")
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setFullScreenIntent(pendingIntent, true)
                .setOngoing(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .build()

            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID + 2, notification)
            Log.d(TAG, "全屏通知已发送，AlarmActivity 应弹出")
        } catch (e: Exception) {
            Log.e(TAG, "全屏通知失败: ${e.message}")
            // 兜底：直接启动
            try {
                val intent = Intent(this, AlarmActivity::class.java)
                intent.putExtra(AlarmActivity.EXTRA_MESSAGE, message)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(intent)
            } catch (e2: Exception) {
                Log.e(TAG, "直接启动也失败: ${e2.message}")
            }
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
                val pattern = longArrayOf(0, 800, 400, 800, 400, 800)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(pattern, 0)
                }
                Log.d(TAG, "振动已启动")
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
        // 服务被系统杀时 stopRinging 不会走，必须在此重置静态标志
        isRinging = false
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
