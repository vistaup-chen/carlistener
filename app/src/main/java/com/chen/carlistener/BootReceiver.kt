package com.chen.carlistener

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * 开机自启 — 监听 BOOT_COMPLETED，确保服务就绪
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.d(TAG, "开机完成，准备启动服务")
        startRingtoneService(context)
    }

    private fun startRingtoneService(context: Context) {
        val serviceIntent = Intent(context, RingtoneService::class.java)
        serviceIntent.putExtra("action", "idle")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Log.d(TAG, "服务启动成功")
        } catch (e: Exception) {
            Log.e(TAG, "服务启动失败: ${e.message}")
        }
    }
}
