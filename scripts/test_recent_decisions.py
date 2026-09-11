#!/usr/bin/env python3
"""Execute production Java decision methods and JS installation predicate."""
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
from check_recent_regressions import JAVA, clean, region

ROOT = Path(__file__).resolve().parents[1]
def read(path):
    return clean((ROOT/path).read_text())
idle = read(JAVA+'utils/IdleScreensaverManager.java')
voice = read(JAVA+'utils/VehicleVoicePlayer.java')
service = read(JAVA+'services/VehicleAutomationService.java')
constants = '\n'.join(re.findall(r'(?:public|private) static final (?:int|Pattern) [A-Z_]+\s*=.*?;', idle, re.S))
ttl_match = re.search(r'private static final long PENDING_TTL_MS\s*=\s*\d+L;', voice)
ttl_decl = ttl_match.group(0) if ttl_match else 'static final long PENDING_TTL_MS=8000;'
methods = '\n'.join(signature+' {'+region(idle, signature)+'}' for signature in [
    'static long parseIdleMs(String dump, long nowWallMs, long nowUptimeMs)',
    'private static long saneIdle(long idle)', 'private static long parseDurationMs(String value)',
    'public static int clampSeconds(int v)'])
java = '''import java.util.*; import java.util.regex.*; import java.text.*;
public class DecisionTest {
%s
static String lastRawValue="";
static class TextUtils {static boolean isEmpty(String s){return s==null||s.isEmpty();}}
static class Log {static void i(String t,String m){}}
static String TAG="test";
int lastPowerMode=1; float latestBatteryVoltage=0; int currentSpeedKmH=0;
Object tts=new Object(); boolean ttsReady=true; String pendingText,pendingVoiceType;
long pendingTextAt; %s
int spoken; String spokenText;
void speakText(String text,String type){spoken++;spokenText=text;}
%s
boolean isEngineRunning(){%s}
void flushPendingSpeech(){%s}
static void eq(Object want,Object got){if(!want.equals(got))throw new AssertionError(want+" != "+got);}
public static void main(String[] args)throws Exception{
 int[][] clamps={{-1,-1},{0,3},{2,3},{3,3},{30,30},{600,600},{601,600}};
 for(int[] c:clamps)eq(c[1],clampSeconds(c[0]));
 eq(3,MIN_SECONDS);eq(600,MAX_SECONDS);eq(30,DEFAULT_SECONDS);eq(-1,NEVER_SECONDS);
 long now=System.currentTimeMillis(), uptime=1000000L;
 String[][] dumps={{"100000 (1234 ms ago)","1234"},{"900000","100000"},{"+0h 3m 12s 345ms","192345"},{"1970-01-01 00:00:00","-1"},{"garbage","-1"},{"100000 (999999999 ms ago)","-1"},{"100000 (-1 ms ago)","0"}};
 for(String[] d:dumps)eq(Long.parseLong(d[1]),parseIdleMs("mLastUserActivityTime="+d[0],now,uptime));
 for(String fmt:new String[]{"yyyy-MM-dd HH:mm:ss.SSS","MM-dd HH:mm:ss.SSS"})
   eq(5000L,parseIdleMs("mLastUserActivityTime="+new SimpleDateFormat(fmt).format(new Date(now-5000)),now,uptime));
 eq(-1L,parseIdleMs(null,now,uptime));
 DecisionTest t=new DecisionTest();
 t.lastPowerMode=0;t.latestBatteryVoltage=14f;eq(false,t.isEngineRunning());
 t.lastPowerMode=1;t.latestBatteryVoltage=12f;t.currentSpeedKmH=0;eq(true,t.isEngineRunning());
 t.lastPowerMode=-1;t.latestBatteryVoltage=10f;t.currentSpeedKmH=0;eq(false,t.isEngineRunning());
 t.lastPowerMode=-1;t.latestBatteryVoltage=0f;eq(true,t.isEngineRunning());
 t.lastPowerMode=-1;t.latestBatteryVoltage=13.5f;eq(true,t.isEngineRunning());
 t.lastPowerMode=-1;t.latestBatteryVoltage=10f;t.currentSpeedKmH=10;eq(true,t.isEngineRunning());
 t.pendingText="最新语音";t.pendingTextAt=now;t.flushPendingSpeech();eq(1,t.spoken);eq("最新语音",t.spokenText);
 t.flushPendingSpeech();eq(1,t.spoken);
 t.pendingText="过期";t.pendingTextAt=now-9000;t.flushPendingSpeech();eq(1,t.spoken);
 t.ttsReady=false;t.pendingText="未就绪";t.pendingTextAt=now;t.flushPendingSpeech();eq(1,t.spoken);
 System.out.println("PASS production Java: idle formats/clamp, engine decisions, TTS flush/expiry");
}
}
''' % (constants, ttl_decl, methods, region(service,'public boolean isEngineRunning()'),region(voice,'private void flushPendingSpeech()'))
java_bin = str(Path(os.environ['JAVA_HOME'])/'bin/java') if os.environ.get('JAVA_HOME') else shutil.which('java')
if not java_bin:
    raise RuntimeError('JDK required')
with tempfile.TemporaryDirectory(prefix='geely-decisions-') as directory:
    p=Path(directory)/'DecisionTest.java';p.write_text(java)
    subprocess.run([java_bin,str(p)],check=True,timeout=45)
app = read('web/src/components/modals/AppDetailModal.vue')
body = region(app,'const isMapApp = computed')
js = 'const classify = value => {const app={value};'+body+'};\n'
js += '''for(const [value,expected] of [[null,false],[{name:'高德地图',need_theme_install:false},false],[{name:'工具',need_theme_install:true},true],[{filename:'AutoMap_8.5.apk'},true],[{name:'QQ音乐'},false]]) {if(classify(value)!==expected)throw Error(JSON.stringify(value));} console.log('PASS production JS: explicit true/false and legacy fallback');'''
subprocess.run(['node','--input-type=module','-e',js],check=True,timeout=15)
