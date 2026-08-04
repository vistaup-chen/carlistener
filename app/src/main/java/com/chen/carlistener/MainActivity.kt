package com.chen.carlistener

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
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
    private lateinit var senderNumbersEditText: EditText
    private lateinit var testRingButton: Button
    private lateinit var autoStartButton: Button
    private lateinit var batteryOptButton: Button
    private lateinit var notifSettingsButton: Button
    private lateinit var fullScreenSettingsButton: Button

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
        const val KEY_SENDER_NUMBERS = "sender_numbers"
        const val DEFAULT_KEYWORDS = "驶离,处罚,交警,违停"
        const val DEFAULT_SENDER_NUMBERS = "12123,121233300"
        const val DEFAULT_NOTIFICATION_PACKAGE = "com.tmri.app.main"
        /** 测试 app 包名，强制监听但不在列表中展示 */
        const val NOTIFIER_PACKAGE = "com.chen.notifier"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val filter = IntentFilter(RingtoneService.ACTION_RINGTONE_STOPPED)
        ContextCompat.registerReceiver(this, ringtoneStopReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        keywordEditText = findViewById(R.id.keywordEditText)
        senderNumbersEditText = findViewById(R.id.senderNumbersEditText)
        selectedAppTextView = findViewById(R.id.selectedAppTextView)
        selectAppButton = findViewById(R.id.selectAppButton)
        quick12123Button = findViewById(R.id.quick12123Button)
        statusTextView = findViewById(R.id.statusTextView)
        smsPermissionButton = findViewById(R.id.smsPermissionButton)
        notificationPermissionButton = findViewById(R.id.notificationPermissionButton)
        autoStartButton = findViewById(R.id.autoStartButton)
        batteryOptButton = findViewById(R.id.batteryOptButton)
        notifSettingsButton = findViewById(R.id.notifSettingsButton)
        fullScreenSettingsButton = findViewById(R.id.fullScreenSettingsButton)

        loadPreferences()

        smsPermissionButton.setOnClickListener {
            checkAndRequestSmsPermission()
        }

        notificationPermissionButton.setOnClickListener {
            openNotificationListenerSettings()
        }

        autoStartButton.setOnClickListener {
            AutoStartHelper.openAutoStartSetting(this)
        }

        batteryOptButton.setOnClickListener {
            openBatteryOptimizationSettings()
        }

        notifSettingsButton.setOnClickListener {
            openAppNotificationSettings()
        }

        fullScreenSettingsButton.setOnClickListener {
            openFullScreenIntentSettings()
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
            checkAndPromptMissingPermissions()
        }

        testRingButton = findViewById(R.id.testRingButton)
        testRingButton.setOnClickListener {
            if (isRinging) {
                stopRingtone()
            } else {
                // 5 秒延迟，给用户时间锁屏，验证锁屏下能否触发
                testRingButton.isEnabled = false
                object : android.os.CountDownTimer(5000, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        val sec = (millisUntilFinished / 1000).toInt() + 1
                        testRingButton.text = "${sec}秒后触发..."
                    }
                    override fun onFinish() {
                        testRingButton.isEnabled = true
                        startRingtone("test")
                        Toast.makeText(this@MainActivity, "测试响铃已触发（5分钟后自动停止）", Toast.LENGTH_LONG).show()
                    }
                }.start()
            }
        }

        updateStatus()
        updateTestRingButton()

        // 首次启动自动申请权限
        autoRequestPermissions()
    }

    override fun onResume() {
        super.onResume()
        // 静态状态比废弃的 getRunningServices 更可靠
        isRinging = RingtoneService.isRinging
        updateTestRingButton()
        updateStatus()
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val keywords = prefs.getString(KEY_KEYWORDS, DEFAULT_KEYWORDS)
        val senderNumbers = prefs.getString(KEY_SENDER_NUMBERS, DEFAULT_SENDER_NUMBERS)
        val notificationPackage = prefs.getString(KEY_NOTIFICATION_PACKAGE, DEFAULT_NOTIFICATION_PACKAGE)
        keywordEditText.setText(keywords)
        senderNumbersEditText.setText(senderNumbers)
        updateSelectedAppDisplay(notificationPackage ?: DEFAULT_NOTIFICATION_PACKAGE)
    }

    private fun updateSelectedAppDisplay(packageNames: String) {
        val names = packageNames.split(",").map { it.trim() }
            .filter { it.isNotEmpty() && it != NOTIFIER_PACKAGE }
        if (names.size == 1) {
            selectedAppTextView.text = "${getAppLabel(names[0])}（${names[0]}）"
        } else {
            val labels = names.joinToString("\n") { "• ${getAppLabel(it)}（$it）" }
            selectedAppTextView.text = labels
        }
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
        val keywords = keywordEditText.text.toString().trim().ifEmpty { DEFAULT_KEYWORDS }
        val senderNumbers = senderNumbersEditText.text.toString().trim().ifEmpty { DEFAULT_SENDER_NUMBERS }
        prefs.edit()
            .putString(KEY_KEYWORDS, keywords)
            .putString(KEY_SENDER_NUMBERS, senderNumbers)
            .putString(KEY_NOTIFICATION_PACKAGE,
                prefs.getString(KEY_NOTIFICATION_PACKAGE, DEFAULT_NOTIFICATION_PACKAGE))
            .apply()
    }

    /**
     * 启动时自动申请所有必要权限
     */
    private fun autoRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECEIVE_SMS)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.READ_SMS)
        }
        // 双重检查：areNotificationsEnabled + checkSelfPermission（小米兼容）
        val nmCheck = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        val hasNotifPerm = nmCheck.areNotificationsEnabled()
                || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        if (!hasNotifPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), 1002)
        }

        if (!isNotificationListenerEnabled()) {
            Toast.makeText(this, "请在设置中启用「通知监听」权限", Toast.LENGTH_LONG).show()
        }

        // 全屏通知权限（Android 14+，小米等厂商默认关闭）
        if (Build.VERSION.SDK_INT >= 34) {
            val nmFs = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (!nmFs.canUseFullScreenIntent()) {
                Toast.makeText(this, "请开启「全屏弹窗」权限，否则闹钟界面无法弹出", Toast.LENGTH_LONG).show()
            }
        }

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            Toast.makeText(this, "请将本应用加入电池优化白名单", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * 保存配置后检查所有必要权限，缺少的弹窗提示并引导开启
     */
    private fun checkAndPromptMissingPermissions() {
        val missing = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            missing.add("接收短信 (RECEIVE_SMS)")
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            missing.add("读取短信 (READ_SMS)")
        }
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        val hasNotif = nm.areNotificationsEnabled()
                || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        if (!hasNotif) {
            missing.add("通知权限 (POST_NOTIFICATIONS)")
        }
        if (Build.VERSION.SDK_INT >= 34 && !nm.canUseFullScreenIntent()) {
            missing.add("全屏弹窗 (USE_FULL_SCREEN_INTENT)")
        }
        if (!isNotificationListenerEnabled()) {
            missing.add("通知监听 (NotificationListener)")
        }
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            missing.add("电池优化白名单 (BatteryOpt)")
        }

        if (missing.isEmpty()) {
            Toast.makeText(this, "所有权限已就绪 ✓", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("缺少以下权限")
            .setMessage("以下权限未开启，可能影响功能：\n\n${missing.joinToString("\n") { "• $it" }}\n\n点击「去设置」逐个开启。")
            .setPositiveButton("去设置") { _, _ ->
                val nm2 = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                val hasNotif2 = nm2.areNotificationsEnabled()
                        || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                            && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
                when {
                    ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED
                        || ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED -> checkAndRequestSmsPermission()
                    !hasNotif2 -> openAppNotificationSettings()
                    Build.VERSION.SDK_INT >= 34 && !nm2.canUseFullScreenIntent() -> openFullScreenIntentSettings()
                    !isNotificationListenerEnabled() -> openNotificationListenerSettings()
                    !(getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName) -> openBatteryOptimizationSettings()
                }
            }
            .setNegativeButton("稍后", null)
            .show()
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

    /**
     * 打开本应用的通知设置页（用于授予 POST_NOTIFICATIONS 权限）
     */
    private fun openAppNotificationSettings() {
        // 直接用应用详情页，小米/华为/OPPO 都支持，用户从这里进通知设置
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "无法打开应用设置", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 打开全屏通知权限设置（Android 14+ 小米需要手动开启）
     */
    private fun openFullScreenIntentSettings() {
        try {
            // Android 14+ 有专门的全屏通知设置
            val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        } catch (e: Exception) {
            // 降级：打开通知设置
            openAppNotificationSettings()
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

    private fun openBatteryOptimizationSettings() {
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (pm.isIgnoringBatteryOptimizations(packageName)) {
                Toast.makeText(this, "已在电池优化白名单中", Toast.LENGTH_SHORT).show()
                return
            }
            // 跳转到本应用的电池优化设置
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        } catch (e: Exception) {
            // 降级：打开全局电池优化列表
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(this, "无法打开电池优化设置", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showAppSelectionDialog() {
        val pm = packageManager

        val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)

        val appList = resolveInfos
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != NOTIFIER_PACKAGE }
            .sortedWith(compareBy(
                { AppListAdapter.getFirstLetter(pm.getApplicationLabel(it).toString()) },
                { pm.getApplicationLabel(it).toString().lowercase() }
            ))

        val app12123 = try {
            pm.getApplicationInfo(DEFAULT_NOTIFICATION_PACKAGE, 0)
        } catch (_: Exception) {
            null
        }

        if (app12123 == null) {
            Toast.makeText(this, "未安装交管12123，请先安装后再选择", Toast.LENGTH_LONG).show()
        }

        val finalList = if (app12123 != null) {
            val rest = appList.filter { it.packageName != DEFAULT_NOTIFICATION_PACKAGE }
            listOf(app12123) + rest
        } else {
            appList
        }

        if (finalList.isEmpty()) {
            Toast.makeText(this, "未找到已安装应用", Toast.LENGTH_SHORT).show()
            return
        }

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val currentSelected = prefs.getString(KEY_NOTIFICATION_PACKAGE, DEFAULT_NOTIFICATION_PACKAGE) ?: ""
        val selectedSet = currentSelected.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()

        // 加载自定义布局
        val dialogView = layoutInflater.inflate(R.layout.dialog_app_list, null)
        val searchEditText = dialogView.findViewById<EditText>(R.id.searchEditText)
        val listView = dialogView.findViewById<ListView>(R.id.appListView)
        val sidebar = dialogView.findViewById<LinearLayout>(R.id.letterSidebar)

        val adapter = AppListAdapter(this, pm)
        adapter.setData(finalList, selectedSet)
        listView.adapter = adapter

        // 点击切换选中状态
        listView.setOnItemClickListener { _, _, position, _ ->
            val item = adapter.getItem(position) as AppListAdapter.ListItem
            if (item.type == AppListAdapter.TYPE_APP && item.appInfo != null) {
                val pkg = item.appInfo.packageName
                if (pkg in selectedSet) selectedSet.remove(pkg) else selectedSet.add(pkg)
                adapter.setData(finalList, selectedSet)
            }
        }

        // 搜索过滤
        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                adapter.filter(s?.toString() ?: "")
                buildLetterSidebar(sidebar, adapter, listView)
            }
        })

        // 构建 A-Z 侧边栏
        buildLetterSidebar(sidebar, adapter, listView)

        AlertDialog.Builder(this)
            .setTitle("选择要监听的应用（可多选）")
            .setView(dialogView)
            .setPositiveButton("确定") { _, _ ->
                if (selectedSet.isEmpty()) {
                    Toast.makeText(this, "请至少选择一个应用", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val value = selectedSet.joinToString(",")
                prefs.edit().putString(KEY_NOTIFICATION_PACKAGE, value).apply()
                updateSelectedAppDisplay(value)
                updateStatus()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun buildLetterSidebar(sidebar: LinearLayout, adapter: AppListAdapter, listView: ListView) {
        sidebar.removeAllViews()
        val available = adapter.getAvailableLetters()

        // 有 # 分组时加入
        if ('#' in available) {
            sidebar.addView(createLetterView("#", adapter, listView))
        }

        for (c in 'A'..'Z') {
            if (c !in available) continue
            sidebar.addView(createLetterView(c.toString(), adapter, listView))
        }
    }

    private fun createLetterView(letter: String, adapter: AppListAdapter, listView: ListView): TextView {
        return TextView(this).apply {
            text = letter
            textSize = 11f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 4, 0, 4)
            setTextColor(0xFF333333.toInt())
            isClickable = true
            setOnClickListener {
                val pos = adapter.getLetterPosition(letter[0])
                if (pos >= 0) listView.smoothScrollToPosition(pos)
            }
        }
    }

    private fun updateStatus() {
        val hasReceiveSms = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        val hasReadSms = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        // 小米上 areNotificationsEnabled 可能不准确，同时用 checkSelfPermission 兜底
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        val hasPostNotif = nm.areNotificationsEnabled()
                || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        val hasFullScreen = if (Build.VERSION.SDK_INT >= 34) nm.canUseFullScreenIntent() else true
        val hasNotificationPermission = isNotificationListenerEnabled()
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val hasBatteryOpt = pm.isIgnoringBatteryOptimizations(packageName)

        val status = StringBuilder("状态：\n")
        status.append("接收短信：${if (hasReceiveSms) "✓" else "✗ 未授予"}\n")
        status.append("读取短信：${if (hasReadSms) "✓" else "✗ 未授予"}\n")
        status.append("通知权限：${if (hasPostNotif) "✓" else "✗ 点击下方按钮设置"}\n")
        status.append("全屏弹窗：${if (hasFullScreen) "✓" else "✗ 点击下方按钮设置"}\n")
        status.append("通知监听：${if (hasNotificationPermission) "✓ 已启用" else "✗ 未启用"}\n")
        status.append("电池优化：${if (hasBatteryOpt) "✓ 已加入" else "✗ 未加入"}")

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val keywords = prefs.getString(KEY_KEYWORDS, DEFAULT_KEYWORDS)
        val senderNumbers = prefs.getString(KEY_SENDER_NUMBERS, DEFAULT_SENDER_NUMBERS)
        val notificationPackage = prefs.getString(KEY_NOTIFICATION_PACKAGE, DEFAULT_NOTIFICATION_PACKAGE) ?: DEFAULT_NOTIFICATION_PACKAGE
        val appLabels = notificationPackage.split(",")
            .filter { it.trim() != NOTIFIER_PACKAGE }
            .joinToString("、") { "${getAppLabel(it.trim())}（${it.trim()}）" }
        status.append("关键字：${keywords}\n")
        status.append("短信号码：${if (senderNumbers.isNullOrEmpty()) "未设置" else senderNumbers}\n")
        status.append("监听应用：$appLabels")

        statusTextView.text = status.toString()
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.contains(packageName) == true
    }

    private fun startRingtone(action: String) {
        val intent = Intent(this, RingtoneService::class.java)
        intent.putExtra("action", action)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "启动响铃失败: ${e.message}", Toast.LENGTH_SHORT).show()
            return
        }
        isRinging = true
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
        updateStatus()
        when (requestCode) {
            1001 -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    Toast.makeText(this, "短信权限已授予", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "需要短信权限才能监听短信", Toast.LENGTH_LONG).show()
                }
            }
            1002 -> {
                val denied = permissions.filterIndexed { i, _ ->
                    grantResults.getOrNull(i) != PackageManager.PERMISSION_GRANTED
                }.map { it.substringAfterLast(".") }
                if (denied.isEmpty()) {
                    Toast.makeText(this, "所有权限已授予 ✓", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "以下权限未授予：${denied.joinToString("、")}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(ringtoneStopReceiver)
    }
}
