package com.chen.carlistener

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast

/**
 * 厂商自启动设置引导
 * 尝试跳转到对应品牌的自启动管理页面；失败则 Toast 提示手动设置
 */
object AutoStartHelper {

    private const val TAG = "AutoStartHelper"

    /**
     * 尝试打开自启动设置页面，返回 true 表示成功跳转
     */
    fun openAutoStartSetting(context: Context): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        Log.d(TAG, "设备厂商: $manufacturer")

        val intents = buildIntentList(context, manufacturer)

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.d(TAG, "成功跳转: ${intent.component}")
                return true
            } catch (_: Exception) {
                // 该 intent 不可用，尝试下一个
            }
        }

        // 全部失败，提示手动设置
        Toast.makeText(context, "无法自动打开自启动设置，请在系统设置中手动允许自启动", Toast.LENGTH_LONG).show()
        return false
    }

    private fun buildIntentList(context: Context, manufacturer: String): List<Intent> {
        val intents = mutableListOf<Intent>()

        when {
            // 小米 / Redmi / POCO
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> {
                intents.add(componentIntent("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"))
                intents.add(componentIntent("com.miui.securitycenter", "com.miui.powercenter.PowerSettings"))
            }

            // 华为 / 荣耀
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                intents.add(componentIntent("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"))
                intents.add(componentIntent("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"))
                intents.add(componentIntent("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"))
            }

            // OPPO / Realme / OnePlus (ColorOS)
            manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("oneplus") -> {
                intents.add(componentIntent("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"))
                intents.add(componentIntent("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"))
                intents.add(componentIntent("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"))
            }

            // Vivo / iQOO (Funtouch OS / OriginOS)
            manufacturer.contains("vivo") -> {
                intents.add(componentIntent("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"))
                intents.add(componentIntent("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"))
                intents.add(componentIntent("com.vivo.appfilter", "com.vivo.appfilter.AppFilterActivity"))
            }

            // 三星
            manufacturer.contains("samsung") -> {
                intents.add(componentIntent("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"))
                intents.add(componentIntent("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity"))
            }

            // 魅族
            manufacturer.contains("meizu") -> {
                intents.add(componentIntent("com.meizu.safe", "com.meizu.safe.permission.SmartBGActivity"))
            }

            // 联想 / 摩托罗拉
            manufacturer.contains("lenovo") || manufacturer.contains("motorola") -> {
                intents.add(componentIntent("com.lenovo.security", "com.lenovo.security.purebackground.PureBackgroundActivity"))
            }
        }

        // 通用：电池优化设置作为兜底
        intents.add(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))

        return intents
    }

    private fun componentIntent(pkg: String, cls: String): Intent {
        return Intent().setComponent(ComponentName(pkg, cls))
    }
}
