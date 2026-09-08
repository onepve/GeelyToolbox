# 吉利智驾 (GeelyPilot)

专为搭载 **亿咖通 E02 / IHU516** 芯片的吉利汽车（缤越 COOL、缤瑞 COOL、博越、豪越、帝豪等 Android 9 SWOS 车机系统）量身打造的**新一代全能车载智能座舱控制中枢**。

---

## 📌 项目信息与官方图文教程

- **官方保姆级图文教程**：<a href="https://onepve.com/geely-toolbox-guide/" target="_blank" rel="noopener noreferrer">吉利智驾 (GeelyPilot) 从零初始化与座舱实战全指南</a>
- **GitHub 开源仓库**：<a href="https://github.com/onepve/GeelyToolbox" target="_blank" rel="noopener noreferrer">onepve/GeelyToolbox</a>
- **最新正式版本**：`v1.6.1` (versionCode `7061`)
- **官方直装下载直链**：<a href="https://dl.onepve.com/GeelyToolbox/GeelyToolbox.apk?v=1.6.1" target="_blank" rel="noopener noreferrer">GeelyToolbox.apk (v1.6.1 · 2.5 MB)</a>
- **版本元数据接口**：<a href="https://dl.onepve.com/GeelyToolbox/version.json" target="_blank" rel="noopener noreferrer">version.json</a>
- **配套软件源接口**：<a href="https://dl.onepve.com/GeelyToolbox/apps.json" target="_blank" rel="noopener noreferrer">apps.json</a>
- **全栈设计系统规范**：<a href="https://onepve.com/geely-toolbox-guide/" target="_blank" rel="noopener noreferrer">OnePve Design (PPanel 极简美学规范)</a>
- **车友交流 QQ 群**：`564654011`

---

## 🚀 核心功能一览 (v1.6.1)

1. **全新 OnePve Design 极简美学架构 (Dark Glassmorphism)**：全面基于 Vite + Vue 3 + Tailwind CSS 纯组件化工程重构，采用深邃星空漫反射大底座、PPanel 同款亚克力微透悬浮卡片、日夜双模无缝自适应；
2. **黄金车规大磁贴与暖阳琥珀呼吸光环 (Halo Ring)**：按键高度精准收敛为 **84~88px 黄金触控大磁贴**（主字 21px 特粗、副字 15.5px 粗体紧凑充实），平铺直选，消除上下多余废白，文字保持默认高对比（夜间纯白/白天深墨黑），外圈点亮精致暖阳琥珀金光环；
3. **顶栏 12V 蓄电池物理电压流光微胶囊**：顶栏直出底层 CAN 总线真实物理电压（`>=11.8V 绿色健康/充能`、`11.5~11.8V 橙色低电`、`<11.5V 红色高危`），发电机充放电动态感知，点击秒级呼出整车充能健康大看板；
4. **专属座舱语音资产目录物理隔离**：底层确立 `/sdcard/GeelyPilot/voices/` 专属独立目录并优先加载，与公共系统的 `Download/` 目录彻底物理解耦，无论车主怎么清空系统下载目录，座舱核心语音资产 100% 毫发无损，永不哑巴；
5. **手机无线快传 (8888 端口) 专属语音制作中枢**：快传网页直出【📥 下载官方语音制作模板 (ZIP)】（仅 5.5 KB，内置 16 个车规标称占位音频）与【📖 查看制作说明 (README)】在线弹窗查阅；手机端上传任意语音包 ZIP 后，车机后台全自动识别并穿透解压部署至专属目录；
6. **整套语音主题一键换装与单项自由混搭矩阵**：车载音频专区展示已导入语音包，支持样音试听、一键整套启用与彻底删除；首项锁定【👑 出厂官方原声 (晓晓温婉知性)】随时一键恢复；全车声效设置弹窗右侧新增快捷选配矩阵，支持单项音频自由混搭（左右两栏像素级对齐）；
7. **精选软件中心【🗑️ 清理下载目录】三阶安全中枢**：顶栏右侧常驻清理入口，提供【🔥 彻底全量清空】、【🛡️ 智能安全清理】（推荐，清除旧包缓存并智能避开音频与非空目录）与【📁 仅清理散落普通文件】三大清晰维度，Java 原生多线程安全异步删除与实时 Toast 统计；
8. **全场景车身语音播报体系大一统**：四大车身联动模块全量统称为【xx播报】与【已开启 / 已关闭】家族式质感（D/R/P/N挡播报、智能/舒适/经济/运动播报、四门与尾门开门/关门播报），彻底杜绝生僻词；
9. **智能有人感知与开机静默双态状态机**：内存变量开机天然归零初始化，首条报文作为基准建立无条件执行 `return;` 绝对静音；从 P 挡换出或切出智能模式时激活，挂回 P 挡或切回智能模式时自动复位归零；电子挡把熄火硬件级强制归 P 锁手刹，下一次开机 100% 自动归零静音零误报；
10. **方向盘按键深度接管与 360 秒级唤起**：Mode 键与静音键短按秒切 360 环视或高德导航，软件后台自动补发解静音指令抵消误静音；静音长按 10 秒纯硬件看门狗冷重启救砖 100% 保留；
11. **卡主题专家模式安全硬红线**：做死硬限制——仅限高德地图底包调起卡主题向导，非地图类应用一律杜绝卡主题入口，防范死机风险；
12. **15 大车规级硬核 CI 自动化防御门禁**：涵盖 Java 静态符号完整性、Node.js AST 语法树、Android 9 (Chromium 68) 布局防塌陷（0 裸 gap、0 裸 inset-0）、全量音频资产物理存在、51 项 JSBridge 契约闭环、专属语音模板与三阶清理中枢全生命周期检测。

---

## 📖 完整实操图文全指南

关于从零开启车机 ADB 调试、今日动态工程暗码实时计算器、带红色箭头与步骤编号的保姆级图文实战，请前往官方博客查阅：

👉 <a href="https://onepve.com/geely-toolbox-guide/" target="_blank" rel="noopener noreferrer"><b>吉利智驾 (GeelyPilot) 完整图文实战全指南</b></a>
