package com.chen.carlistener

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var keywordEditText: EditText
    private lateinit var notificationPackageEditText: EditText
    private lateinit var statusTextView: TextView
    private lateinit var smsPermissionButton: Button
    private lateinit var notificationPermissionButton: Button
    private lateinit var testRingButton: Button

    private var isRinging = false
    
    // 广播接收器，用于监听响铃停止事件
    private val ringtoneStopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.chen.carlistener.RINGTONE_STOPPED") {
                isRinging = false
                updateTestRingButton()
                Toast.makeText(this@MainActivity, "响铃已停止", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        const val PREFS_NAME = "CarListenerPrefs"
        const val KEY_KEYWORDS = "keywords"
        const val KEY_NOTIFICATION_PACKAGE = "notification_package"
        const val DEFAULT_NOTIFICATION_PACKAGE = "com.tmri.app.main" // 交管12123的包名
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 注册广播接收器
        val filter = IntentFilter("com.chen.carlistener.RINGTONE_STOPPED")
        registerReceiver(ringtoneStopReceiver, filter)

        keywordEditText = findViewById(R.id.keywordEditText)
        notificationPackageEditText = findViewById(R.id.notificationPackageEditText)
        statusTextView = findViewById(R.id.statusTextView)
        smsPermissionButton = findViewById(R.id.smsPermissionButton)
        notificationPermissionButton = findViewById(R.id.notificationPermissionButton)

        // 加载保存的配置
        loadPreferences()

        // 短信权限按钮点击事件
        smsPermissionButton.setOnClickListener {
            checkAndRequestSmsPermission()
        }

        // 通知监听权限按钮点击事件
        notificationPermissionButton.setOnClickListener {
            openNotificationListenerSettings()
        }

        // 保存配置按钮
        findViewById<Button>(R.id.saveButton).setOnClickListener {
            savePreferences()
            Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show()
            updateStatus()
        }

        // 测试响铃按钮
        testRingButton = findViewById(R.id.testRingButton)
        testRingButton.setOnClickListener {
            testRingtone()
        }

        // 检查权限状态
        updateStatus()
        updateTestRingButton()
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val keywords = prefs.getString(KEY_KEYWORDS, "")
        val notificationPackage = prefs.getString(KEY_NOTIFICATION_PACKAGE, DEFAULT_NOTIFICATION_PACKAGE)

        keywordEditText.setText(keywords)
        notificationPackageEditText.setText(notificationPackage)
    }

    private fun savePreferences() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putString(KEY_KEYWORDS, keywordEditText.text.toString())
        editor.putString(KEY_NOTIFICATION_PACKAGE, notificationPackageEditText.text.toString())
        editor.apply()
    }

    private fun checkAndRequestSmsPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS),
                1001
            )
        } else {
            Toast.makeText(this, "短信权限已授予", Toast.LENGTH_SHORT).show()
            updateStatus()
        }
    }

    private fun openNotificationListenerSettings() {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开通知监听设置", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateStatus() {
        val hasSmsPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        val hasNotificationPermission = isNotificationListenerEnabled()

        val status = StringBuilder("状态：\n")
        status.append("短信权限：${if (hasSmsPermission) "✓ 已授予" else "✗ 未授予"}\n")
        status.append("通知监听：${if (hasNotificationPermission) "✓ 已启用" else "✗ 未启用"}\n")
        
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val keywords = prefs.getString(KEY_KEYWORDS, "")
        val notificationPackage = prefs.getString(KEY_NOTIFICATION_PACKAGE, DEFAULT_NOTIFICATION_PACKAGE)
        status.append("关键字：${if (keywords.isNullOrEmpty()) "未设置" else keywords}\n")
        status.append("监听应用：${notificationPackage ?: DEFAULT_NOTIFICATION_PACKAGE}")

        statusTextView.text = status.toString()
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val packageName = packageName
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.contains(packageName) == true
    }

    private fun testRingtone() {
        val intent = Intent(this, RingtoneService::class.java)
        if (isRinging) {
            // 正在响铃 → 停止
            intent.putExtra("action", "stop")
            isRinging = false
            startService(intent)
            Toast.makeText(this, "已停止响铃", Toast.LENGTH_SHORT).show()
        } else {
            // 未响铃 → 开始测试
            intent.putExtra("action", "test")
            isRinging = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Toast.makeText(this, "开始测试响铃（30秒后自动停止）", Toast.LENGTH_LONG).show()
        }
        updateTestRingButton()
    }

    private fun updateTestRingButton() {
        testRingButton.text = if (isRinging) "停止响铃" else "测试响铃"
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "短信权限已授予", Toast.LENGTH_SHORT).show()
                updateStatus()
            } else {
                Toast.makeText(this, "需要短信权限才能监听短信", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }
}
