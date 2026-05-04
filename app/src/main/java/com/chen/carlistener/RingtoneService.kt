package com.chen.carlistener

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import java.util.Timer
import java.util.TimerTask

class RingtoneService : Service() {

    companion object {
        private const val TAG = "RingtoneService"
        private const val RING_DURATION = 30000L // 响铃30秒
        private const val NOTIFICATION_CHANNEL_ID = "ringtone_channel"
        private const val NOTIFICATION_ID = 1001
    }

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var timer: Timer? = null
    private var savedVolume: Int = -1
    private var savedRingerMode: Int = -1

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "RingtoneService 创建")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "收到启动命令: ${intent?.getStringExtra("action")}")

        // Android 8+ 必须立即调用 startForeground，否则系统会强制停止服务
        startForeground(NOTIFICATION_ID, buildForegroundNotification())

        val action = intent?.getStringExtra("action")
        val message = intent?.getStringExtra("message") ?: ""

        when (action) {
            "ring", "test" -> {
                Log.d(TAG, "开始响铃，消息: $message")
                // 先彻底停止之前的响铃（不调用 stopSelf）
                releaseMedia()
                startRinging()
            }
            "stop" -> {
                Log.d(TAG, "手动停止响铃")
                stopRinging()
            }
            else -> {
                Log.w(TAG, "未知 action: $action，停止服务")
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "响铃服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "用于保持响铃服务运行"
                setSound(null, null) // 通知本身不发声，避免和铃声冲突
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
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
            .build()
    }

    private fun startRinging() {
        try {
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

            // 保存原始状态，响铃结束后恢复
            savedRingerMode = audioManager.ringerMode
            savedVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING)

            // 强制解除静音，使铃声能正常播放
            audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL

            // 设置铃声音量为最大
            val maxRingVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
            audioManager.setStreamVolume(
                AudioManager.STREAM_RING,
                maxRingVolume,
                0
            )
            Log.d(TAG, "铃声音量已设置为最大: $maxRingVolume")

            // 使用默认手机铃声，回退到通知音
            val ringtoneUri =
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            if (ringtoneUri == null) {
                Log.e(TAG, "无法获取铃声 URI，放弃响铃")
                stopSelf()
                return
            }

            Log.d(TAG, "铃声 URI: $ringtoneUri")

            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, ringtoneUri)

                // Android 5+ 使用 AudioAttributes 替代已废弃的 setAudioStreamType
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                } else {
                    @Suppress("DEPRECATION")
                    setAudioStreamType(AudioManager.STREAM_RING)
                }

                isLooping = true
                prepare()
                start()
            }

            Log.d(TAG, "MediaPlayer 开始播放")

            // 启动振动
            startVibration()

            // 30 秒后自动停止
            timer = Timer()
            timer?.schedule(object : TimerTask() {
                override fun run() {
                    Log.d(TAG, "30秒超时，自动停止响铃")
                    stopRinging()
                    // 广播通知 MainActivity 更新状态
                    val stopIntent = Intent("com.chen.carlistener.RINGTONE_STOPPED")
                    sendBroadcast(stopIntent)
                }
            }, RING_DURATION)

        } catch (e: Exception) {
            Log.e(TAG, "响铃失败", e)
            stopSelf()
        }
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

    /** 仅释放媒体资源，不停止服务 */
    private fun releaseMedia() {
        try {
            timer?.cancel()
            timer?.purge()
            timer = null

            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
            mediaPlayer = null

            vibrator?.cancel()
            vibrator = null
        } catch (e: Exception) {
            Log.e(TAG, "释放媒体资源失败", e)
        }
    }

    private fun stopRinging() {
        Log.d(TAG, "停止响铃")
        releaseMedia()

        // 恢复原来的铃声模式和音量
        try {
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            if (savedRingerMode >= 0) {
                audioManager.ringerMode = savedRingerMode
                savedRingerMode = -1
            }
            if (savedVolume >= 0) {
                audioManager.setStreamVolume(AudioManager.STREAM_RING, savedVolume, 0)
                savedVolume = -1
            }
        } catch (e: Exception) {
            Log.e(TAG, "恢复音量失败", e)
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "RingtoneService 销毁")
        releaseMedia()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
