# CarListener - Android 项目

## 项目结构
- `app/` — 主应用模块（com.chen.carlistener）
- `notifier/` — 通知测试辅助模块（com.chen.notifier），独立 APK
- `settings.gradle` — 多模块配置

## 技术栈
- Kotlin 2.0.21, Gradle 8.13, AGP 8.13.0
- minSdk 23, targetSdk/compileSdk 36
- 无网络权限（纯本地监听）

## 核心功能
- `SmsReceiver` — 短信广播接收器，关键字匹配后触发响铃
- `NotificationMonitorService` — NotificationListenerService，监听通知
- `RingtoneService` — 前台服务，播放铃声+振动，30秒超时
- `DebugSmsReceiver` — 调试用，接收 notifier 的模拟短信广播

## 编码规范
- 不改现有架构，只改需求相关的代码
- 修改后检查 import 完整性、方法签名、重复定义
- 资源文件用 `res/` 下的 XML，不用硬编码
- notifier 是独立 app，通过广播与主 app 通信

## AI 编码准则（Karpathy）

> 这些准则倾向于谨慎而非速度。琐碎任务（拼写错误、明显的一行修改）自行判断，不需要完整流程。

### 1. 编码前思考
- 明确说明假设，不确定就问
- 有歧义时呈现多种解释，不要默默选一个
- 存在更简单的方法要说出来
- 困惑时停下来问，不要猜

### 2. 简洁优先
- 不添加要求之外的功能
- 不为一次性代码创建抽象
- 不为不可能的场景做错误处理
- 200 行能写成 50 行就重写

### 3. 精准修改
- 不"改进"相邻代码、注释或格式
- 不重构没坏的东西
- 匹配现有风格
- 注意到无关死代码就提一下，不要主动删除
- 删除因你的改动而变得无用的导入/变量/函数

### 4. 目标驱动执行
- 定义可验证的成功标准
- 多步骤任务列出简短计划：`1. [步骤] → 验证: [检查]`
