#!/usr/bin/env python3
"""方向盘回正与 EPB 安全状态机纯 Java 回归门禁。

覆盖：方向盘左右转角补码解码、挂 P 位置快照窗口、阈值、一次触发及 EPB 安全静默。
任何断言失败均阻断构建。
"""
import os
import shutil
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
UTILS = ROOT / "app/src/main/java/app/onepve/geelyconsole/utils"
TESTS = ROOT / "app/src/test/java/app/onepve/geelyconsole/utils"
PROD = UTILS / "SafetySensorStateMachine.java"
TEST = TESTS / "SafetyGuardRegressionTest.java"

if not PROD.exists() or not TEST.exists():
    raise RuntimeError("安全状态机生产类或回归测试缺失")

java_home = os.environ.get("JAVA_HOME")
javac = str(Path(java_home) / "bin/javac") if java_home else shutil.which("javac")
java = str(Path(java_home) / "bin/java") if java_home else shutil.which("java")
if not javac or not java:
    raise RuntimeError("JDK required: set JAVA_HOME or install javac/java on PATH")

with tempfile.TemporaryDirectory(prefix="geely-safety-guard-") as directory:
    output = Path(directory) / "out"
    subprocess.run([javac, "-d", str(output), str(PROD), str(TEST)], check=True, timeout=120)
    subprocess.run([java, "-cp", str(output),
                    "app.onepve.geelyconsole.utils.SafetyGuardRegressionTest"],
                   check=True, timeout=60)

print("OK: 安全守护方向盘左右转角 + 挂P快照回归全绿")
