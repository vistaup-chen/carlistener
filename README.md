# CarListener - 车辆通知监听器

## 功能说明

CarListener 是一个 Android 应用，用于监听手机短信和特定应用（默认交管12123）的通知消息。当消息中包含预设的关键字时，应用会弹窗 + 响铃 + 振动提醒用户。

**典型场景**：收到违停驶离提醒短信时，自动以最大音量响铃，避免错过挪车通知。

---

## 主要功能

1. **短信监听**：监听所有 incoming SMS 短信
2. **通知监听**：监听指定应用的通知（默认：交管12123，可多选）
3. **关键字匹配**：支持配置多个关键字（用逗号分隔），不区分大小写
4. **响铃提醒**：检测到关键字后，以设置的音量播放铃声（0-100%可调，0=不响）
5. **振动提醒**：四档可调（关/弱/中/强）
6. **弹窗提醒**：通过悬浮窗在锁屏/后台弹出全屏提醒界面
7. **配置持久化**：所有设置即时保存，重启后保留
8. **无网络权限**：应用不包含任何网络权限，保护用户隐私

---

## 权限说明

| 权限 | 用途 | 是否必须 |
|------|------|----------|
| `RECEIVE_SMS` | 接收短信广播 | 必须 |
| `READ_SMS` | 读取短信内容 | 必须 |
| `POST_NOTIFICATIONS` | 前台服务通知（Android 13+） | 必须 |
| `VIBRATE` | 振动提醒 | 必须 |
| `FOREGROUND_SERVICE` | 前台服务（保持响铃服务运行） | 必须 |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Android 14+ 前台服务类型 | 必须 |
| `SYSTEM_ALERT_WINDOW` | 悬浮窗（后台/锁屏弹窗） | 必须 |
| `USE_FULL_SCREEN_INTENT` | 全屏弹窗 | 必须 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 电池优化白名单 | 推荐 |
| `MODIFY_AUDIO_SETTINGS` | 调节音量 | 必须 |
| `WAKE_LOCK` | 响铃时亮屏 | 必须 |
| `RECEIVE_BOOT_COMPLETED` | 开机自启 | 推荐 |
| `QUERY_ALL_PACKAGES` | 应用选择器列表 | 必须 |

**注意**：应用**不包含**任何网络相关权限（如 INTERNET、ACCESS_NETWORK_STATE 等），确保数据不会外传。

---

## 使用方法

### 1. 安装 APK
将 `app-debug.apk` 安装到 Android 设备上。

### 2. 授予权限
打开应用后，按顺序授予以下权限：

| 按钮 | 操作 |
|------|------|
| 授予短信权限 | 弹窗点"允许" |
| 启用通知监听 | 系统设置中找到"车辆监听器"→ 开启开关 |
| 悬浮窗权限 | 系统设置中开启"允许显示在其他应用上层" |
| 自启动设置 | 跳转到厂商自启动管理页面，允许自启动 |
| 电池优化白名单 | 选择"允许"，防止后台被杀 |
| 全屏弹窗设置 | Android 14+ 需要开启，否则闹钟界面无法弹出 |

### 3. 配置关键字
在"关键字匹配"输入框中输入需要监听的关键字，多个关键字用逗号分隔。

**默认值**：`驶离,处罚,交警,违停`

**示例**：`违章,罚款,扣分,年检,驶离`

### 4. 配置短信号码
在"监听的短信号码"输入框中输入号码，多个用逗号分隔。

**说明**：来自这些号码的短信**不管内容**都会触发响铃。号码和关键字是 OR 关系，满足任一条件即触发。

### 5. 配置监听应用
点击"选择应用"按钮，勾选需要监听通知的应用。

- 默认：`com.tmri.app.main`（交管12123）
- 支持多选，交管12123 永远置顶
- 内置搜索和拼音侧边栏

**快捷操作**：点击"一键监听12123"直接设为默认。

### 6. 调整响铃音量和振动
- **音量滑条**（0-100）：拖动即生效，0=不响铃，100=最大音量
- **振动强度**（关/弱/中/强）：选择即生效

### 7. 保存配置
点击"保存配置"按钮保存关键字、号码、应用设置。

**注意**：音量和振动设置是即时生效的，无需点保存。

### 8. 测试功能
- **测试响铃**：5秒延迟后触发完整响铃流程（铃声+振动+弹窗），用于验证功能正常
- **静默弹窗测试**：5秒延迟后只弹窗不响铃，用于在公共场所测试弹窗是否正常

---

## 工作原理

```
短信广播 ──→ SmsReceiver ──┐
                           ├──→ KeywordMatcher ──→ RingtoneService ──→ 弹窗覆盖层
通知事件 ──→ NotificationMonitor ─┘
```

### 核心组件

1. **MainActivity** - 配置界面，权限管理，状态显示
2. **SmsReceiver** - 静态注册 `SMS_RECEIVED` 广播，priority=999
3. **NotificationMonitorService** - NotificationListenerService，5秒去重
4. **RingtoneService** - 前台服务，控制音频+振动+弹窗
5. **AlarmActivity** - 备用弹窗界面（透明主题，底部卡片）
6. **KeywordMatcher** - 关键字匹配工具，短信和通知共用
7. **AppListAdapter** - 应用选择器适配器，支持拼音侧边栏
8. **AutoStartHelper** - 厂商自启动设置引导（小米/华为/OPPO/vivo/三星等）

### 弹窗机制

Android 12+ 对后台启动 Activity 有严格限制（BAL）。本应用使用 `SYSTEM_ALERT_WINDOW`（悬浮窗）实现后台/锁屏弹窗：

- 通过 `WindowManager.addView()` 添加 `TYPE_APPLICATION_OVERLAY` 覆盖层
- 覆盖层包含：半透明黑底 + 红色提醒卡片
- 锁屏显示 + 亮屏 + 保持屏幕常亮
- 点击背景区域或"关闭提醒"按钮可关闭

### 响铃机制

- 使用 `STREAM_ALARM` 音频流（静音模式下也能响）
- `MediaPlayer` 循环播放系统闹钟铃声
- 响铃结束后自动恢复原始音量（静音的保持静音）
- Android 13+ 振动使用 `VibrationAttributes.USAGE_ALARM` 确保后台可用

### 配置存储

所有配置通过 `SharedPreferences` 存储：

| Key | 默认值 | 说明 |
|-----|--------|------|
| `keywords` | `驶离,处罚,交警,违停` | 监听关键字 |
| `sender_numbers` | `12123,121233300` | 监听号码 |
| `notification_package` | `com.tmri.app.main` | 监听应用包名（逗号分隔） |
| `ring_volume` | 100 | 响铃音量 0-100 |
| `ring_vibration_strength` | 2 | 振动强度 0=关 1=弱 2=中 3=强 |

---

## 技术细节

- **最低 Android 版本**：Android 6.0 (API 23)
- **目标 Android 版本**：Android 16 (API 36)
- **开发语言**：Kotlin 2.0.21
- **构建工具**：Gradle 8.13 / AGP 8.13.0
- **第三方库**：pinyin4j（中文转拼音首字母）
- **无网络依赖**

---

## 测试设备

| 设备 | 系统 | 状态 |
|------|------|------|
| 小米 13 | Android 16 / HyperOS 2 | 已验证 |

**已验证功能**：
- 短信监听 + 响铃 + 振动 + 弹窗
- 通知监听（交管12123）
- 后台弹窗（退到桌面）
- 锁屏弹窗（亮屏+解锁后显示）
- 音量恢复（静音→响铃→恢复静音）
- 振动强度三档

---

## 项目结构

```
carlistener/
├── app/                          # 主应用模块
│   ├── src/main/
│   │   ├── java/com/chen/carlistener/
│   │   │   ├── MainActivity.kt              # 主界面 + 权限管理
│   │   │   ├── SmsReceiver.kt               # 短信广播接收器
│   │   │   ├── NotificationMonitorService.kt # 通知监听服务
│   │   │   ├── RingtoneService.kt           # 响铃服务（音频+振动+弹窗）
│   │   │   ├── AlarmActivity.kt             # 闹钟式提醒界面
│   │   │   ├── KeywordMatcher.kt            # 关键字匹配工具
│   │   │   ├── AppListAdapter.kt            # 应用选择器适配器
│   │   │   ├── AutoStartHelper.kt           # 厂商自启动引导
│   │   │   ├── BootReceiver.kt              # 开机自启接收器
│   │   │   └── DebugSmsReceiver.kt          # 调试用模拟短信接收器
│   │   ├── res/                             # 资源文件
│   │   │   ├── layout/                      # 布局文件
│   │   │   ├── values/                      # 字符串/颜色/主题
│   │   │   └── xml/                         # 备份规则
│   │   └── AndroidManifest.xml              # 应用清单
│   └── build.gradle                         # 模块构建配置
├── notifier/                       # 通知测试辅助模块（独立 APK）
│   └── src/main/java/com/chen/notifier/
│       ├── MainActivity.kt                 # 测试界面
│       ├── AlarmReceiver.kt                # 闹钟触发接收器
│       └── NotifierApp.kt                  # Application 类
├── build.gradle                    # 项目构建配置
├── settings.gradle                 # 项目设置（多模块）
└── README.md                       # 本文件
```

---

## 构建方法

```bash
# 设置 Java 环境
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"

# 构建 debug APK
.\gradlew.bat assembleDebug

# APK 输出位置
# app/build/outputs/apk/debug/app-debug.apk
```

---

## 注意事项

1. 首次使用需要在系统设置中手动启用"通知访问"权限
2. 某些手机厂商可能会限制后台服务，建议在电池优化设置中将本应用设为"不优化"
3. 响铃时长默认为 5 分钟，可在代码中修改 `RING_DURATION` 常量
4. 应用不会收集或上传任何用户数据
5. Android 12+ 必须授予悬浮窗权限才能后台弹窗
6. 小米/华为等厂商需要手动允许自启动

## 常见问题

**Q: 为什么收不到通知提醒？**
A: 请确认已在系统设置中启用"通知访问"权限，并且监听的包名正确。

**Q: 为什么铃声不大？**
A: 检查音量滑条设置（0=不响），并确保手机未处于静音模式。

**Q: 为什么后台不弹窗？**
A: 请确认已授予"悬浮窗"权限（设置 → 应用 → 车辆监听器 → 显示在其他应用上层）。

**Q: 为什么锁屏不亮屏？**
A: 响铃时会通过 WakeLock 强制亮屏，请确认应用未被电池优化限制。

**Q: 为什么振动没反应？**
A: 检查振动强度是否设为"关"；Android 13+ 需要 `USAGE_ALARM` 振动属性。

**Q: 响铃结束后音量没恢复？**
A: 应用会自动恢复原始音量，如果之前是静音，响铃后也会恢复静音。

**Q: 如何修改监听的应用？**
A: 点击"选择应用"按钮，勾选目标应用，或多选多个应用。

---

## 开发信息

- 包名：`com.chen.carlistener`
- 版本：1.0
- 签名：debug/release 统一使用 `carlistener.jks`

## 许可证

本项目仅供个人使用。
