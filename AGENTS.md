# Agent Guidelines - Geely Cockpit Projects (GeelyToolbox)

## 1. Project Context
- **Description**: 吉利车机辅助悬浮窗、桌面悬浮胶囊、硬件按键与系统级车载服务 (GeelyToolbox).
- **Tech Stack**: Android Java 8 (AOSP / ECarX SDK / OEM Services) + 内置 Web 前端 (Vue 3 / Vite).
- **Docs 真源（单一真源铁律）**: 数据通道、踩坑与实现细节**只写在本地技能** `onepve-geelytoolbox`（`references/data-channels.md` 等），本仓库**不建任何解释性 MD**；`docs/changelog/` 只留当次要发的说明文件（正式版一份 / 在测测试版一份），旧件发完即删。发现仓库与技能说法冲突，一律以技能为准并立刻修仓库代码，绝不维护第二份文档。

## 2. Hard Rules & Guardrails (STRICT)
- **Gatekeeper Lockdown (门禁铁律)**: 各功能模块底层逻辑严格独立门禁锁死。修单一 bug 严禁连带触碰其他模块；若被门禁拦截，必须详细说明改动理由与破坏性影响并经明确确认授权后方可放开修改，严禁擅自做主拆门禁。
- **Vehicle Physical Isolation (车型隔离)**: 以缤越COOL 2022 作为核心基准车型。凡识别非缤越COOL车型即判定为新车型，必须严格执行物理隔离接入，严禁侵入修改基准代码，排错严禁凭空臆测。
- **Build & Verification (编译与交付)**: 严禁本地编译伪造交付。代码提交后必须第一时间 git push 推远程 GitHub Actions 跑 CI 并盯完全程；交付前必须独立回读线上 dl.onepve.com 真实数据（MD5/版本号）核验。
- **Dead Code Cleanup (死代码清理)**: 删任何 Java 方法前，除全库引用外，必须检查 scripts/ 与 .github/ 的 CI 门禁契约；改动前先跑本地 check_recent_regressions.py 与编译检查。
- **Copywriting Standard (文案规范)**: 所有发版更新日志（Changelog）与车机更新弹窗一律采用通俗车规级全中文大白话，严禁出现英文 commit/feat/fix 痕迹与生硬参数。
- **Theme System (主题规范)**: 全局 UI 徽章与微件统一接入助手自身全局动态主题系统（palette.js），严禁脱离主题写死任何具体颜色代码。

## 3. Essential Commands
- **Regression Check**: `python3 scripts/check_recent_regressions.py`
- **Compile Java Check**: `./gradlew compileDebugJavaWithJavac`
- **Build Debug APK**: `./gradlew assembleDebug`
- **Frontend Build**: `cd web && pnpm run build`
