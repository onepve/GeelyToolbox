# 缤越助手 数据通道总纲（龙骨）

> 真源：本文件。所有模块的「数据从哪来」以本文为准，改代码前先对本文。
> 适用车型：缤越 COOL 2022（SX11 + IHU516G / 亿咖通 E02）为基准，其它车型见文末。

---

## 一、三条线，一条主，两条备

| 线 | 通道 | 本质 | 在本车地位 |
|---|---|---|---|
| **A 线** | HAL 车规属性（`CarPropertyManager`） | CarService 主动把值推给我们，免子进程 | **第一优先**（本车只有 3 个属性可用） |
| **B 线** | logcat 日志（`-b main,system,events,crash`） | 我们自己读日志文字，再正则抠 | **主力承载**（覆盖最广） |
| **C 线** | 车型专属适配（`*Platform.java`） | 同一份数据，不同车型解析规则不同 | 本车以外，暂不涉及 |

**关键认知纠正**：A 线在本车只提供 **方控 / 车门 / 尾门** 三样（外加一份从未验证过的挡位读取）。
挡位、车速、油量、电压、电源、驾驶模式、转向灯——**本车全部走 B 线，HAL 里没有**。
HAL 的挡位/车速/电压/点火属性只有 `BinyueLPlatform`（缤越 L / IHU624G）在用。

---

## 二、A 线：HAL 到底给了我们什么

属性 ID 全部定义在 `hal/AndroidCarClient.java`：

| 属性 | ID | 本车是否使用 | 用途 |
|---|---|---|---|
| `HW_KEY_INPUT` | 678428923 | ✅ 用（常开） | 方控按键直推键码 |
| `DOOR_POS` | 641736873 | ✅ 用（兜底） | 四门开关状态 |
| `TRUNK_POS` | 557850643 | ✅ 用（兜底） | 尾门开关状态 |
| `CURRENT_GEAR` | 289408001 | ❌ 仅缤越 L | 挡位 |
| `IGNITION_STATE` | 289408009 | ❌ 仅缤越 L | 点火 |
| `PERF_VEHICLE_SPEED` | 291504647 | ❌ 仅缤越 L | 车速 |
| `BATTERY_VOLTAGE` | 557875206 | ❌ 仅缤越 L | 电瓶电压 |

---

## 三、B 线：日志管道解析的全部内容

订阅命令：`logcat -v time -b main,system,events,crash -T 200`
白名单预筛：`BASE_FEATURE_WHITELIST`（9 组关键字）
解析入口：`VehicleAutomationService` 的日志行分发（1582 行起）

| 序号 | 功能 | 日志关键字 | 解析点 |
|---|---|---|---|
| 1 | 方向盘方控 | `reportKeyToAdaptApi` | 1582 |
| 2 | 油量百分比 | `mmodelfuel` / `fuel_level` | 1641 |
| 3 | 实时车速 | `CarAudioInfo / Get Speed` | 1659 |
| 4 | 挡位 | `vehicle_gear` | 1737 |
| 5 | CAN 通用数据 | `parsecandata` | 1899 |
| 6 | 电瓶电压 | `mModelBatteryVolt` | 1959 |
| 7 | 电源/点火/熄火 | `vpowerinfo` / `peps_powermode` | 2076 |
| 8 | 车门（主通道） | `91 02 01` | `DoorStateManager` |
| 9 | 转向灯 / 360 | `turnlight=` / `camera_state:` | — |
| 10 | 驾驶模式 | `dm_func_drive_mode` | — |
| 11 | 蓝牙音频 / DSP | `a2dp_player_` | — |

---

## 四、功能 → 通道 全表

### 4.1 完全不需要日志、也不需要 HAL（纯本地）

| 功能 | 实现类 |
|---|---|
| 应用安装/卸载/下载 | `DownloadManager` `AdbClient` `ApkProvider` |
| 应用商城 | `StoreView` + `manage_store_app` |
| 主题切换/配色 | `ThemePatcher` `ThemeColorHelper` `palette.js` |
| 悬浮窗 | `FloatingWindowService` |
| 息屏时钟 | `IdleScreensaverManager` |
| 记油台账（录入/查询/备份） | `FuelManager` |
| 语音播报 | `VehicleVoicePlayer` `VoiceArbiter` |
| 方控按键**学习** | `MainActivity.startWheelKeyLearning` |
| 网络策略 | `NetworkPolicyManager` |
| 车牌/关于/OTA 检测 | 各 Modal + `SystemUtils` |

### 4.2 只走 B 线（日志）

| 功能 | 关键字 |
|---|---|
| 车速（含随速音量、车速自启多媒体） | `CarAudioInfo / Get Speed` |
| 挡位（含挡位安全联动、D 档限制） | `vehicle_gear` |
| 油量百分比（含加油自动记账感知） | `mmodelfuel` |
| 电瓶电压 | `mModelBatteryVolt` |
| 电源/点火/熄火休眠 | `vpowerinfo` |
| 转向灯 / 360 随动 | `turnlight=` |
| 驾驶模式同步 | `dm_func_drive_mode` |
| 蓝牙音频 / DSP 音量 | `a2dp_player_` |
| CAN 通用信号（Tasker） | `parsecandata` |

### 4.3 双通道（A + B 并存）

| 功能 | 主 | 备 | 现在状态 |
|---|---|---|---|
| **方控按键** | A：`HW_KEY_INPUT` | B：logcat `reportKeyToAdaptApi` | **两条都常开**（beta.10），260ms 同键防抖去重 |
| **车门** | B：`91 02 01` | A：`DOOR_POS` | B 静默 15 秒（5 帧）才切 A |
| **尾门** | B：`91 02 01` | A：`TRUNK_POS` | 同上 |

### 4.4 车型专属（C 线，暂不涉及）

`BinruiCoolPlatform` / `BinyueLPlatform` / `EmgrandLPlatform` / `Fe6a3Ihu510gPlatform` / `GenericAospPlatform`

---

## 五、优先级原则（修正版）

1. **能用 HAL 的优先 HAL**——它免子进程、免正则、比 logd 落盘更早（实测早 4ms）。
   但本车 HAL 只有 3 个属性可用，适用范围有限。
2. **HAL 没有的走日志**——不是"退而求其次"，而是本车唯一途径。
3. **双通道功能：主备可切换，不可同时关**。方控是特例——两条都常开，用防抖合并。
4. **互斥铁律**：除方控外，任何兜底通道必须与主通道互斥（skill §6）。方控例外是因为
   主通道会"假健康"（建流成功但读不到 crash 里的按键），必须常开兜底。

---

## 六、模块耦合关系（修 A 会不会坏 B）

```
                    ┌─ 方控 ──── SteeringWheelKeyManager ─┐
                    │                                      ├─→ 媒体控制 / 360 / 语音
   logcat 管道 ─────┼─ 车门 ──── DoorStateManager ─────────┤
   (VehicleAutomation│─ 挡位 ──── GearStateMachine ────────┼─→ 挡位安全联动
    Service 单点)   │─ 车速 ──── 随速音量 / 自启多媒体 ────┤
                    └─ 电源 ──── 熄火休眠 ────────────────┘

   HAL 通道 ────────┴─ HwKeyInputChannel（方控）
                      DoorHalChannel（车门/尾门）
```

**共享单点风险**：
- `VehicleAutomationService` 的日志行分发是**所有 B 线功能的共有入口**。改这里必然影响全部 B 线功能。
- `logcatQueue`（容量 2000）是所有 B 线功能共用的队列。队列满会同时影响全部。
- `KeySignalRecorder` 是纯观察者，只记录不改逻辑，改动零风险。
- `SteeringWheelKeyManager` 只被方控使用，独立。
- `DoorStateManager` / `GearStateMachine` / `FuelManager` 各自独立。

---

## 七、技能拆分建议

| 现有 | 问题 | 建议 |
|---|---|---|
| `onepve-geelytoolbox`（42,823 字） | 什么都装，改方控要先读车门 | 拆成「龙骨 + 分册」 |

拆分方案：
- `onepve-geelytoolbox`（保留）：**龙骨**——架构总纲、通道表、耦合关系、门禁规则、发版流程
- `references/logcat-pipeline.md`：日志管道、白名单、缓冲区、队列
- `references/hal-channels.md`：HAL 属性、探针、兜底触发条件
- `references/wheel-keys.md`：方控全链路、防抖、键码、媒体派发
- `references/doors-gear-fuel.md`：车门/挡位/油量
- `references/ui-theme.md`：主题、门禁 43 项、Chromium 68

---

## 八、本车待验证

- `DOOR_POS` / `TRUNK_POS` 在 IHU516G 上是否真能读到（`DoorHalChannel` 从未实车验证）
- `CURRENT_GEAR` 在本车能否读到（`BinyueCoolPlatform` 未调用，仅 `BinyueLPlatform` 调用）
