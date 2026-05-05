package com.chen.carlistener

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var keywordEditText: EditText
    private lateinit var selectedAppTextView: TextView
    private lateinit var selectAppButton: Button
    private lateinit var quick12123Button: Button
    private lateinit var statusTextView: TextView
    private lateinit var smsPermissionButton: Button
    private lateinit var notificationPermissionButton: Button
    private lateinit var testRingButton: Button

    private var isRinging = false

    private val ringtoneStopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == RingtoneService.ACTION_RINGTONE_STOPPED) {
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
        const val DEFAULT_NOTIFICATION_PACKAGE = "com.tmri.app.main"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val filter = IntentFilter(RingtoneService.ACTION_RINGTONE_STOPPED)
        registerReceiver(ringtoneStopReceiver, filter)

        keywordEditText = findViewById(R.id.keywordEditText)
        selectedAppTextView = findViewById(R.id.selectedAppTextView)
        selectAppButton = findViewById(R.id.selectAppButton)
        quick12123Button = findViewById(R.id.quick12123Button)
        statusTextView = findViewById(R.id.statusTextView)
        smsPermissionButton = findViewById(R.id.smsPermissionButton)
        notificationPermissionButton = findViewById(R.id.notificationPermissionButton)

        loadPreferences()

        smsPermissionButton.setOnClickListener {
            checkAndRequestSmsPermission()
        }

        notificationPermissionButton.setOnClickListener {
            openNotificationListenerSettings()
        }

        selectAppButton.setOnClickListener {
            showAppSelectionDialog()
        }

        quick12123Button.setOnClickListener {
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            prefs.edit().putString(KEY_NOTIFICATION_PACKAGE, DEFAULT_NOTIFICATION_PACKAGE).apply()
            updateSelectedAppDisplay(DEFAULT_NOTIFICATION_PACKAGE)
            updateStatus()
            Toast.makeText(this, "已设为监听交管12123", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.saveButton).setOnClickListener {
            savePreferences()
            Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show()
            updateStatus()
        }

        testRingButton = findViewById(R.id.testRingButton)
        testRingButton.setOnClickListener {
            if (isRinging) {
                stopRingtone()
            } else {
                startRingtone("test")
                Toast.makeText(this, "开始测试响铃（30秒后自动停止）", Toast.LENGTH_LONG).show()
            }
        }

        updateStatus()
        updateTestRingButton()
    }

    override fun onResume() {
        super.onResume()
        // 每次回到前台检查服务是否还在运行，同步按钮状态
        isRinging = isServiceRunning()
        updateTestRingButton()
        updateStatus()
    }

    private fun isServiceRunning(): Boolean {
        val am = getSystemService(ACTIVITY_SERVICE) as? android.app.ActivityManager ?: return false
        return am.getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == RingtoneService::class.java.name }
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val keywords = prefs.getString(KEY_KEYWORDS, "")
        val notificationPackage = prefs.getString(KEY_NOTIFICATION_PACKAGE, DEFAULT_NOTIFICATION_PACKAGE)
        keywordEditText.setText(keywords)
        updateSelectedAppDisplay(notificationPackage ?: DEFAULT_NOTIFICATION_PACKAGE)
    }

    private fun updateSelectedAppDisplay(packageName: String) {
        val appLabel = getAppLabel(packageName)
        selectedAppTextView.text = "$appLabel（$packageName）"
    }

    private fun getAppLabel(packageName: String): String {
        return try {
            val pm = packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    private fun savePreferences() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_KEYWORDS, keywordEditText.text.toString())
            .putString(KEY_NOTIFICATION_PACKAGE,
                prefs.getString(KEY_NOTIFICATION_PACKAGE, DEFAULT_NOTIFICATION_PACKAGE))
            .apply()
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

    private fun showAppSelectionDialog() {
        val pm = packageManager
        val installedApps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        }

        val userApps = installedApps
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 || it.packageName == DEFAULT_NOTIFICATION_PACKAGE }
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }

        val items = userApps.map { appInfo ->
            val label = pm.getApplicationLabel(appInfo).toString()
            val pkg = appInfo.packageName
            if (pkg == DEFAULT_NOTIFICATION_PACKAGE) "$label（交管12123）" else label
        }.toTypedArray()

        val selectedPackage = userApps.map { it.packageName }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("选择要监听的应用")
            .setItems(items) { _, which ->
                val chosen = selectedPackage[which]
                val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                prefs.edit().putString(KEY_NOTIFICATION_PACKAGE, chosen).apply()
                updateSelectedAppDisplay(chosen)
                updateStatus()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun updateStatus() {
        val hasSmsPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        val hasNotificationPermission = isNotificationListenerEnabled()

        val status = StringBuilder("状态：\n")
        status.append("短信权限：${if (hasSmsPermission) "✓ 已授予" else "✗ 未授予"}\n")
        status.append("通知监听：${if (hasNotificationPermission) "✓ 已启用" else "✗ 未启用"}\n")

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val keywords = prefs.getString(KEY_KEYWORDS, "")
        val notificationPackage = prefs.getString(KEY_NOTIFICATION_PACKAGE, DEFAULT_NOTIFICATION_PACKAGE) ?: DEFAULT_NOTIFICATION_PACKAGE
        val appLabel = getAppLabel(notificationPackage)
        status.append("关键字：${if (keywords.isNullOrEmpty()) "未设置" else keywords}\n")
        status.append("监听应用：$appLabel（$notificationPackage）")

        statusTextView.text = status.toString()
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.contains(packageName) == true
    }

    private fun startRingtone(action: String) {
        val intent = Intent(this, RingtoneService::class.java)
        intent.putExtra("action", action)
        isRinging = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        updateTestRingButton()
    }

    private fun stopRingtone() {
        // 发广播停止，和通知栏按钮走同一通道
        val intent = Intent(RingtoneService.ACTION_STOP)
        intent.setPackage(packageName)
        sendBroadcast(intent)
        isRinging = false
        updateTestRingButton()
        Toast.makeText(this, "已停止响铃", Toast.LENGTH_SHORT).show()
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

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(ringtoneStopReceiver)
    }
}
