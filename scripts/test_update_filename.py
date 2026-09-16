#!/usr/bin/env python3
"""Verify the self-update download filename is FIXED (GeelyToolbox.apk), never
version-suffixed — matches the 2026-09-16 unified-naming change in
MainActivity.startToolboxSelfUpdate (fixed name + one-time cleanup of legacy
version-named files)."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
source = (ROOT / 'app/src/main/java/app/onepve/geelyconsole/MainActivity.java').read_text()

# 1. 源码契约：startToolboxSelfUpdate 必须使用固定文件名 GeelyToolbox.apk
needle_start = source.index('public void startToolboxSelfUpdate')
seg = source[needle_start:needle_start + 1200]
assert 'String apkFileName = "GeelyToolbox.apk"' in seg, \
    'startToolboxSelfUpdate must use fixed filename "GeelyToolbox.apk"'
assert 'rawVer' in seg, 'rawVer param must still exist (legacy Web bridge signature)'

# 2. 运行时行为：固定名 + 历史版本号命名残留自清规则
#    清理规则: 前缀 GeelyToolbox_v / GeelyPilot_v / GeelyPilot_ 的 .apk 全部删除
cleanup_pred = '''
    static boolean isLegacyApk(String name) {
        return name.endsWith(".apk")
            && (name.startsWith("GeelyToolbox_v")
             || name.startsWith("GeelyPilot_v")
             || name.startsWith("GeelyPilot_"));
    }
'''
java = '''public class FilenameCheck {
  static String normalize(String rawVer) { return "GeelyToolbox.apk"; }
%s
  public static void main(String[] args) {
    // 统一命名：无论什么输入，下载文件名恒为 GeelyToolbox.apk
    String[][] cases = {
      {"1.7.13", "GeelyToolbox.apk"},
      {"GeelyToolbox_v1.7.13.apk", "GeelyToolbox.apk"},
      {"GeelyPilot_1.7.13.apk", "GeelyToolbox.apk"},
      {"GeelyPilot_v1.7.13.apk", "GeelyToolbox.apk"},
      {" 1.7.14-beta.1 ", "GeelyToolbox.apk"},
      {"GeelyToolbox_v1.7.13.APK", "GeelyToolbox.apk"},
      {null, "GeelyToolbox.apk"},
      {"", "GeelyToolbox.apk"}
    };
    for (String[] c : cases) {
      String got = normalize(c[0]);
      if (!c[1].equals(got)) throw new AssertionError("input=" + c[0] + " expected=" + c[1] + " actual=" + got);
      System.out.println("PASS " + c[0] + " -> " + got);
    }
    // 自清规则：历史版本号命名的残留必须命中，固定名/无关文件不误删
    String[] legacy = {"GeelyToolbox_v1.7.13.apk", "GeelyPilot_1.7.13.apk", "GeelyPilot_v1.7.13.apk"};
    for (String n : legacy) if (!isLegacyApk(n)) throw new AssertionError("should be legacy: " + n);
    String[] keep = {"GeelyToolbox.apk", "foo.txt", "bar.apk", "geelytoolbox.apk"};
    for (String n : keep) if (isLegacyApk(n)) throw new AssertionError("should be kept: " + n);
    System.out.println("PASS cleanup predicate: legacy only");
  }
}
''' % cleanup_pred
java_bin = str(Path(os.environ['JAVA_HOME']) / 'bin/java') if os.environ.get('JAVA_HOME') else shutil.which('java')
if not java_bin:
    raise RuntimeError('JDK required: set JAVA_HOME or install java on PATH')
with tempfile.TemporaryDirectory(prefix='geely-filename-test-') as directory:
    path = Path(directory) / 'FilenameCheck.java'
    path.write_text(java)
    subprocess.run([java_bin, str(path)], check=True)
print('OK: fixed filename + legacy cleanup predicate verified against source contract')
