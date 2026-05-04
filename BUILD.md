# 构建说明

## 环境要求

- Android Studio（包含 JDK）
- Android SDK（API 23-36）
- Gradle 8.13

## 构建步骤

### 方法一：使用 Android Studio

1. 用 Android Studio 打开 `carlistener` 项目
2. 等待 Gradle 同步完成
3. 点击菜单 `Build` -> `Build Bundle(s) / APK(s)` -> `Build APK(s)`
4. APK 生成位置：`app/build/outputs/apk/debug/app-debug.apk`

### 方法二：使用命令行

```bash
# Windows PowerShell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug

# 或使用 CMD
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
gradlew.bat assembleDebug
```

APK 生成位置：`app\build\outputs\apk\debug\app-debug.apk`

## 安装到设备

```bash
adb install app\build\outputs\apk\debug\app-debug.apk
```

## 生成 Release 版本

如需生成签名的 Release 版本：

1. 在 `app/build.gradle` 中配置签名信息
2. 运行：
```bash
.\gradlew.bat assembleRelease
```

## 权限验证

生成的 APK 仅包含以下权限：
- RECEIVE_SMS（接收短信）
- READ_SMS（读取短信）
- VIBRATE（振动）
- FOREGROUND_SERVICE（前台服务）
- REQUEST_IGNORE_BATTERY_OPTIMIZATIONS（忽略电池优化）

**不包含任何网络权限**（INTERNET、ACCESS_NETWORK_STATE 等）

## 代码结构

```
com.chen.carlistener/
├── MainActivity.kt              # 主界面，配置关键字和权限
├── SmsReceiver.kt               # 短信广播接收器
├── NotificationMonitorService.kt # 通知监听服务
└── RingtoneService.kt           # 响铃服务
```

## 修改默认配置

### 修改默认监听应用

编辑 `MainActivity.kt`：
```kotlin
const val DEFAULT_NOTIFICATION_PACKAGE = "com.tmri.app.main" // 修改为其他应用包名
```

### 修改响铃时长

编辑 `RingtoneService.kt`：
```kotlin
private const val RING_DURATION = 30000L // 修改为需要的毫秒数
```

### 修改振动模式

编辑 `RingtoneService.kt`：
```kotlin
val pattern = longArrayOf(0, 1000, 500, 1000, 500, 1000) // 修改振动模式
```

## 故障排除

### 问题：Gradle 构建失败

**解决方案**：
1. 确保 JAVA_HOME 设置正确
2. 清理项目：`.\gradlew.bat clean`
3. 重新构建：`.\gradlew.bat assembleDebug`

### 问题：应用无法监听通知

**解决方案**：
1. 确认已在系统设置中启用"通知访问"权限
2. 检查监听的包名是否正确
3. 查看 Logcat 日志，过滤 `NotificationMonitor` 标签

### 问题：短信无法触发响铃

**解决方案**：
1. 确认已授予短信权限
2. 检查关键字配置是否正确
3. 查看 Logcat 日志，过滤 `SmsReceiver` 标签

## 技术支持

如有问题，请查看 Android Logcat 日志进行调试。
