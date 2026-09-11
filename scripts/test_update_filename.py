#!/usr/bin/env python3
"""Run the actual self-update filename normalization using a local JDK."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
source = (ROOT / 'app/src/main/java/app/onepve/geelyconsole/MainActivity.java').read_text()
start = source.index('            String ver = rawVer == null', source.index('public void startToolboxSelfUpdate'))
end = source.index('            mainHandler.post', start)
block = source[start:end]
java = '''public class FilenameCheck {
  static String normalize(String rawVer) {
%s
    return apkFileName;
  }
  public static void main(String[] args) {
    String[][] cases = {
      {"1.7.13", "GeelyPilot_v1.7.13.apk"},
      {"GeelyToolbox_v1.7.13.apk", "GeelyPilot_v1.7.13.apk"},
      {"GeelyPilot_1.7.13.apk", "GeelyPilot_v1.7.13.apk"},
      {"GeelyPilot_v1.7.13.apk", "GeelyPilot_v1.7.13.apk"},
      {" 1.7.14-beta.1 ", "GeelyPilot_v1.7.14-beta.1.apk"},
      {"GeelyToolbox_v1.7.13.APK", "GeelyPilot_v1.7.13.apk"},
      {null, "GeelyPilot_vlatest.apk"},
      {"", "GeelyPilot_vlatest.apk"}
    };
    for (String[] c : cases) {
      String got = normalize(c[0]);
      if (!c[1].equals(got)) throw new AssertionError("input=" + c[0] + " expected=" + c[1] + " actual=" + got);
      System.out.println("PASS " + c[0] + " -> " + got);
    }
  }
}
''' % block
java_bin = str(Path(os.environ['JAVA_HOME']) / 'bin/java') if os.environ.get('JAVA_HOME') else shutil.which('java')
if not java_bin:
    raise RuntimeError('JDK required: set JAVA_HOME or install java on PATH')
with tempfile.TemporaryDirectory(prefix='geely-filename-test-') as directory:
    path = Path(directory) / 'FilenameCheck.java'
    path.write_text(java)
    subprocess.run([java_bin, str(path)], check=True)
