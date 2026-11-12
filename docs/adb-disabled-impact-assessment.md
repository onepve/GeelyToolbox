# ADB 不启用 / 连不上时，对助手的影响评估

评估日期：2026-11-12　基线：beta-v1.8.33.10　评估人：助手

## 一、一句话结论

**播报和识别一个都不受影响。** 换挡、开门、尾门、引擎盖、模式、方控按键，
全部走车机 logcat / HAL / 原厂广播，跟 ADB 没有半点关系。

**受影响的只有「替别的应用动刀子」这一类操作**：冻结、卸载、清数据、重启、改系统验证开关。
这些活本来是 uid 2000 shell 才准干的，没有 ADB 就干不了 —— 这是权限天花板，不是我们代码写坏了。

## 二、ADB 在代码里的全部使用点（全仓 32 处，逐条核实过）

| # | 功能 | 位置 | 没有 ADB 会怎样 | 兜底 |
|---|------|------|----------------|------|
| 1 | 硬重启车机 | `hardReboot:1980` | 点了没反应，最终报失败 | ❌ 无 |
| 2 | 冻结 / 解冻应用 | `togglePackageFreeze:2080` | 开关打不动 | ❌ 无 |
| 3 | 清除应用数据 | `clearAppData:2166` | 失败 | ❌ 无 |
| 4 | 卸载应用 | `uninstallApp:2206` | 先试 `pm uninstall`，再试 `-k --user 0`，都失败 | ❌ 无 |
| 5 | 应用商城冻结 | `toggleFreezeAppStore:2647` | 直接弹「ADB 总开关已关闭，该功能无法使用」 | ❌ 无 |
| 6 | 安装白名单开关 | `toggleWhitelist:2837` | 开和关都靠 `setprop`，双向失效 | ❌ 无 |
| 7 | 开关车机蓝牙 | `toggleBluetooth:3158` | **不影响**：正常走 `BluetoothAdapter.enable()`，只有拿不到适配器（几乎不发生）才用 ADB | ✅ 完整兜底 |
| 8 | 方控体检读按键 | `startKeyEventCapture:3448` | 体检窗收不到按键事件（`getevent -l` 流建立不起来） | ❌ 无（但接管功能本身正常） |
| 9 | 退出 360 后清原厂多媒体 | `SteeringWheelKeyManager:1055/1057/1072/1107` | 原厂伴听可能在退出 360 后冒头 | ⚠️ 部分：先发 `ecarx.intent.action.STOP` 广播 |
| 10 | 360 卡住的兜底返回键 | `SteeringWheelKeyManager:985` | 360 卡死时无法注入返回键退出 | ❌ 无 |
| 11 | 手机推送 ADB 指令 | `MainActivity:1495` | ADB 失败后降级 `executeShell`，同样无权限 | ⚠️ 名义兜底、实际也失败 |
| 12 | 跳转系统开发者选项 | `MainActivity:1375` | **不影响**：主路径是 Intent 直接跳转，只有抛出异常才用 ADB | ✅ 完整兜底 |

## 三、日常功能影响评估（重点）

| 功能 | 数据来源 | 依赖 ADB | 结论 |
|------|---------|---------|------|
| 换挡播报 D/R/N/P | `VehicleAutomationService` logcat 订阅 | ❌ | **不受影响** |
| 开门 / 关门播报 | logcat 订阅 | ❌ | **不受影响** |
| 尾门 / 引擎盖播报 | logcat 订阅 | ❌ | **不受影响** |
| MODE 模式播报 | logcat + 方控通道 | ❌ | **不受影响** |
| 语音文件解压 / 更新 | APK 内 assets + 本地文件 | ❌ | **不受影响** |
| 方控按键接管（切歌/静音/MODE/自定义/主页） | HAL 常开 + logcat + 原厂广播（三路） | ❌ | **不受影响** |
| 方控体检窗（按一下看有没有信号） | `getevent -l` 流 | ✅ | 收不到按键，功能降级 |
| 抓日志 / 诊断包 | `Runtime.exec(logcat -d)` 直接起进程 | ❌ | **不受影响** |
| 车况显示（门/挡位/温度） | logcat 解析结果 | ❌ | **不受影响** |
| 主题 / 界面 / 设置项 | 本地 SharedPreferences | ❌ | **不受影响** |
| 应用管理（冻结/卸载/清数据） | ADB 特权 | ✅ | **整块不可用** |
| 硬重启车机 | ADB 特权 | ✅ | **不可用** |
| 安装白名单 | ADB 特权 `setprop` | ✅ | **不可用** |

## 四、代码证据

### 播报链路零 ADB
```
GearStateMachine.java      AdbClient=0  executePrivileged=0
VehicleVoicePlayer.java    AdbClient=0  executePrivileged=0
BinyueCoolPlatform.java    AdbClient=0  executePrivileged=0
VehicleAutomationService   仅 import，无实际调用
```

### 日志来源零 ADB
- `SystemUtils:1637` → `logcat -d -v time > raw_all.log`，走 `Runtime.getRuntime().exec`
- 常驻订阅在 `VehicleAutomationService`，走 `ProcessBuilder` 起子进程
- 权限：`AndroidManifest.xml:17` 已声明 `READ_LOGS`
  （⚠️ 但当前代码没有任何地方真正授予它，老版本有 `pm grant`，这是另一条待办）

### `executePrivileged` 的降级链（`SystemUtils:509`）
1. ADB 总开关开 **且** 端口探测通过 → 走 `AdbClient`（uid 2000）
2. 否则降级 `executeShell(cmd)`（普通应用 uid，无特权）
3. `su` 通道已按「零 root」要求彻底移除

### `executeStrictPrivileged`（`SystemUtils:621`）
**不许降级**，拿不到 ADB 直接 `return null`，用于必须成功的场合。

## 五、ADB 总开关的现状

`SystemUtils:92` → `sp.getBoolean(KEY_ADB_MASTER_SWITCH, true)`，**默认开启**。

后果：即使 ADB 实际连不上，代码仍会先尝试、失败后才降级。
用户手感是「点一下没反应，等一会儿提示失败」，因为每次都要等一次端口探测超时。

## 六、TSKER 为什么没 ADB 也能播报

它要的东西和我们一模一样 —— **只要能读到 logcat 就够了**。
开门、换挡、模式的报文本来就写在车机 logcat 里，任何拿到 `READ_LOGS` 的应用都能读。

差别不在「有没有 ADB」，在两件事：
1. 它只「读一行、判断、丢掉」，不常驻订阅、不回放历史
2. 它不需要抓完整诊断包，所以不需要环形缓冲

**推论**：正确方向不是找回 ADB，而是把读日志做回「读完即丢」。
这也是唯一能同时解决「播报延迟」和「不拖垮车机 logd / 不占 ADB 端口」的路子。

## 七、真正需要修的三件事（按优先级）

| 优先级 | 事项 | 原因 |
|--------|------|------|
| P0 | 订阅命令改 `-T 1`，只收实时、不回放历史 | 现在 `-T 200` 每次重连先吞历史，吞不完被踢，形成 15/30/60 秒空窗 |
| P0 | 平时解析完即丢，不保留每行副本 | 环形缓冲常开等于每行都做原子操作，白烧 CPU |
| P1 | 补回 `pm grant READ_LOGS` 的授予动作 | 没这步，`-b all` 和完整日志能力都拿不到；但**需要 ADB 或 root 才能授予，是先有鸡蛋的问题** |

第三条恰恰说明：**不能把播报能力绑在 ADB 上**。
现在的播报已经没绑了（走 logcat），这是对的，要保持。
