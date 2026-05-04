# CarListener 项目开发总结

## 项目概述

CarListener 是一个 Android 应用，用于监听手机短信和特定应用（默认交管12123）的通知消息。当消息中包含预设的关键字时，应用会以最大音量响铃提醒用户。

## 已完成的功能

### ✅ 核心功能

1. **短信监听**
   - 通过 `BroadcastReceiver` 监听系统短信广播
   - 支持多部分短信合并处理
   - 实时检测短信内容是否包含关键字

2. **通知监听**
   - 通过 `NotificationListenerService` 监听系统通知
   - 可配置监听特定应用（默认：交管12123）
   - 提取通知标题和内容进行关键字匹配

3. **关键字配置**
   - 支持多个关键字，用逗号分隔
   - 不区分大小写匹配
   - 配置持久化存储

4. **最大音量响铃**
   - 自动将铃声音量调至最大
   - 播放系统默认铃声
   - 配合振动提醒
   - 默认响铃时长 30 秒

5. **隐私保护**
   - **不包含任何网络权限**
   - 所有数据本地处理
   - 不会上传或泄露用户信息

### ✅ 用户界面

1. **主界面**
   - 关键字配置输入框
   - 监听应用包名配置
   - 权限授予按钮
   - 配置保存功能
   - 测试响铃功能
   - 实时状态显示

2. **权限管理**
   - 一键申请短信权限
   - 引导启用通知监听
   - 实时显示权限状态

## 技术实现

### 架构设计

```
┌─────────────────────────────────────┐
│         MainActivity                │
│  (配置界面 + 权限管理 + 状态显示)     │
└──────────┬──────────┬───────────────┘
           │          │
    ┌──────┴───┐  ┌──┴──────────────┐
    │SmsReceiver│  │NotificationMonitor│
    │(短信监听) │  │Service(通知监听)  │
    └──────┬───┘  └──┬──────────────┘
           │          │
           └────┬─────┘
                │
         ┌──────┴──────┐
         │RingtoneService│
         │ (响铃服务)    │
         └─────────────┘
```

### 核心组件

1. **MainActivity.kt**
   - 用户配置界面
   - 权限申请和管理
   - 偏好设置存储
   - 状态显示和更新

2. **SmsReceiver.kt**
   - 继承 `BroadcastReceiver`
   - 监听 `SMS_RECEIVED` 广播
   - 解析短信内容
   - 关键字匹配检测

3. **NotificationMonitorService.kt**
   - 继承 `NotificationListenerService`
   - 监听系统通知
   - 过滤目标应用
   - 提取通知内容

4. **RingtoneService.kt**
   - 继承 `Service`
   - 控制音频管理器
   - 播放系统铃声
   - 控制振动模式
   - 定时停止

### 权限配置

```xml
<!-- 必需权限 -->
<uses-permission android:name="android.permission.RECEIVE_SMS" />
<uses-permission android:name="android.permission.READ_SMS" />
<uses-permission android:name="android.permission.VIBRATE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />

<!-- 明确不包含的权限 -->
<!-- 无 INTERNET -->
<!-- 无 ACCESS_NETWORK_STATE -->
<!-- 无 ACCESS_WIFI_STATE -->
```

## 项目结构

```
carlistener/
├── app/
│   ├── src/main/
│   │   ├── java/com/chen/carlistener/
│   │   │   ├── MainActivity.kt              # 主活动
│   │   │   ├── SmsReceiver.kt               # 短信接收器
│   │   │   ├── NotificationMonitorService.kt # 通知监听服务
│   │   │   └── RingtoneService.kt           # 响铃服务
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   └── activity_main.xml        # 主界面布局
│   │   │   ├── values/
│   │   │   │   ├── strings.xml              # 字符串资源
│   │   │   │   ├── colors.xml               # 颜色资源
│   │   │   │   └── themes.xml               # 主题资源
│   │   │   └── xml/
│   │   │       ├── backup_rules.xml         # 备份规则
│   │   │       └── data_extraction_rules.xml # 数据提取规则
│   │   └── AndroidManifest.xml              # 应用清单
│   └── build.gradle                         # 模块构建配置
├── gradle/
│   ├── libs.versions.toml                   # 依赖版本管理
│   └── wrapper/
│       └── gradle-wrapper.properties        # Gradle wrapper 配置
├── build.gradle                             # 项目构建配置
├── settings.gradle                          # 项目设置
├── local.properties                         # 本地配置（SDK路径）
├── README.md                                # 使用说明
├── BUILD.md                                 # 构建说明
├── PROJECT_SUMMARY.md                       # 项目总结（本文件）
├── install.bat                              # Windows 安装脚本
└── install.sh                               # Linux/Mac 安装脚本
```

## 构建信息

- **Gradle 版本**: 8.13
- **Android Gradle Plugin**: 8.13.0
- **Kotlin 版本**: 2.0.21
- **编译 SDK**: API 36 (Android 14)
- **最低 SDK**: API 23 (Android 6.0)
- **目标 SDK**: API 36 (Android 14)

## APK 信息

- **文件名**: app-debug.apk
- **大小**: ~5.7 MB
- **位置**: `app/build/outputs/apk/debug/app-debug.apk`
- **包名**: com.chen.carlistener
- **版本**: 1.0 (versionCode: 1, versionName: "1.0")

## 使用流程

1. **构建项目**
   ```bash
   $env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
   .\gradlew.bat assembleDebug
   ```

2. **安装应用**
   ```bash
   adb install app\build\outputs\apk\debug\app-debug.apk
   ```
   或使用提供的安装脚本：
   ```bash
   .\install.bat  # Windows
   ./install.sh   # Linux/Mac
   ```

3. **配置应用**
   - 打开应用
   - 授予短信权限
   - 启用通知监听
   - 设置关键字
   - 保存配置

4. **开始监听**
   - 应用在后台运行
   - 收到包含关键字的短信或通知时自动响铃

## 注意事项

1. **权限要求**
   - 首次使用必须手动授予短信权限
   - 必须在系统设置中启用通知访问权限
   - 某些手机厂商可能需要额外设置后台运行权限

2. **电池优化**
   - 建议在电池优化设置中将应用设为"不优化"
   - 避免系统杀死后台服务

3. **音量设置**
   - 应用会自动将铃声音量调至最大
   - 请确保手机未处于静音或振动模式

4. **隐私安全**
   - 应用不含任何网络权限
   - 所有数据处理均在本地完成
   - 不会收集或上传用户信息

## 可扩展功能

以下功能可在未来版本中添加：

1. **高级配置**
   - 自定义响铃时长
   - 选择不同铃声
   - 自定义振动模式
   - 设置免打扰时段

2. **多应用监听**
   - 支持同时监听多个应用
   - 为不同应用设置不同关键字

3. **历史记录**
   - 记录触发的短信和通知
   - 查看历史提醒记录

4. **统计功能**
   - 统计触发次数
   - 生成提醒报表

5. **云同步**（需要添加网络权限）
   - 配置云端备份
   - 多设备同步

## 已知限制

1. Android 10+ 对后台启动 Activity 有限制
2. 某些定制 ROM 可能限制通知监听服务
3. 短信广播可能被其他应用拦截
4. 部分手机需要手动允许自启动

## 测试建议

1. **功能测试**
   - 发送包含关键字的测试短信
   - 从交管12123发送测试通知
   - 验证响铃和振动是否正常

2. **权限测试**
   - 测试未授予权限时的提示
   - 测试权限被撤销后的行为

3. **边界测试**
   - 测试空关键字配置
   - 测试特殊字符关键字
   - 测试超长短信内容

4. **兼容性测试**
   - 在不同 Android 版本上测试
   - 在不同品牌手机上测试

## 维护建议

1. 定期检查 Android 系统更新对权限的影响
2. 关注通知监听 API 的变化
3. 适配新的 Android 版本
4. 优化电池使用效率

## 联系方式

如有问题或建议，请联系开发者。

---

**开发完成日期**: 2026-05-04  
**开发者**: 陈亮  
**版本**: 1.0
