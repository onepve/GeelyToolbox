#!/usr/bin/env python3
"""语音播报「单项增益」三通道流映射 + 键名归一化 纯 Java 回归门禁。

覆盖真实缺陷场景（2026-09 IHU516G 反编译固件实测修复）：
  A. 播放侧传带后缀文件名 (gear_r.mp3)，配置端存裸 key (gear_r)，
     未归一化导致 voice_item_offset_* 查找永久失败、增益从未生效。
  B. 历史缺陷：nav/notification 被统一调成 STREAM_NOTIFICATION(5)，
     导航档实际改错对象。真值：nav→原厂私有流 12(STREAM_NAVI)、
     notification→STREAM_SYSTEM(1)、music→STREAM_MUSIC(3)。

把生产类 VoiceGainResolver + 测试类 VoiceGainResolverRegressionTest
编译进临时目录并运行 main；任何断言失败即阻断。
"""
import os
import shutil
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
UTILS = ROOT / 'app/src/main/java/app/onepve/geelyconsole/utils'
TEST_DIR = ROOT / 'app/src/test/java/app/onepve/geelyconsole/utils'

PROD = UTILS / 'VoiceGainResolver.java'
TEST = TEST_DIR / 'VoiceGainResolverRegressionTest.java'

if not PROD.exists():
    raise RuntimeError(f'生产类缺失: {PROD}')
if not TEST.exists():
    raise RuntimeError(f'回归测试缺失: {TEST}')

java_home = os.environ.get('JAVA_HOME')
if java_home:
    javac = str(Path(java_home) / 'bin/javac')
    java = str(Path(java_home) / 'bin/java')
else:
    javac = shutil.which('javac')
    java = shutil.which('java')
if not javac or not java:
    raise RuntimeError('JDK required: set JAVA_HOME or install javac/java on PATH')

with tempfile.TemporaryDirectory(prefix='geely-voice-gain-') as directory:
    d = Path(directory)
    shutil.copy(PROD, d / 'VoiceGainResolver.java')
    shutil.copy(TEST, d / 'VoiceGainResolverRegressionTest.java')
    # 测试类无 package 声明时可同目录直编；这里两个文件都是 app.onepve.geelyconsole.utils 包，
    # 需按包路径摆放以匹配 package 声明。
    src = d / 'app/onepve/geelyconsole/utils'
    src.mkdir(parents=True)
    shutil.copy(PROD, src / 'VoiceGainResolver.java')
    shutil.copy(TEST, src / 'VoiceGainResolverRegressionTest.java')
    compile_cmd = [javac, '-d', str(d / 'out'),
                   str(src / 'VoiceGainResolver.java'),
                   str(src / 'VoiceGainResolverRegressionTest.java')]
    subprocess.run(compile_cmd, check=True, timeout=120)
    run_cmd = [java, '-cp', str(d / 'out'),
               'app.onepve.geelyconsole.utils.VoiceGainResolverRegressionTest']
    subprocess.run(run_cmd, check=True, timeout=60)

print('OK: VoiceGainResolver 键名归一化 + 三通道流映射回归全绿')
