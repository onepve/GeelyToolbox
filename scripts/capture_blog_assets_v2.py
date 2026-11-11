#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""从当前 v1.8.30 车机界面生成博客完整功能图鉴。

只读取内置 toolbox_ui.html，用项目已有 __VUE_STORE__ 注入模拟缤越 COOL 状态；
不读取真实车主文件，不修改 APK 功能代码，不清理其他浏览器进程。
输出为压缩 WebP 和 manifest.json，缺失分项或弹窗立即失败，禁止空图冒充完成。
"""
import atexit
import asyncio
import base64
import hashlib
import io
import json
import os
from pathlib import Path
import signal
import subprocess
import time
import urllib.request

import websockets
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = Path(os.environ.get('GEELY_BLOG_OUT', '/data/projects/fuwari-blog-preview/public/att/geely'))
TMP_DIR = Path(os.environ.get('GEELY_BLOG_TMP', os.path.join(os.environ.get('TMPDIR', '/data/tmp'), 'geely-blog-capture')))
OUT_DIR.mkdir(parents=True, exist_ok=True)
TMP_DIR.mkdir(parents=True, exist_ok=True)
TOOLBOX_HTML = (ROOT / 'app/src/main/assets/toolbox_ui.html').as_uri()
MOBILE_HTML = (ROOT / 'app/src/main/assets/mobile_web.html').as_uri()
PORT = 9298
MANIFEST = []


def fetch_apps():
    try:
        req = urllib.request.Request('https://dl.onepve.com/GeelyToolbox/apps.json', headers={'User-Agent': 'GeelyBlogCapture/1.8.30'})
        with urllib.request.urlopen(req, timeout=15) as response:
            return response.read().decode('utf-8')
    except Exception as exc:
        raise RuntimeError(f'无法读取当前应用商城清单，停止生成以免出现空商城: {exc}') from exc


BROWSER_CLOSERS = {}


def close_browser(proc):
    close = BROWSER_CLOSERS.pop(proc, None)
    if close:
        close()


def start_edge(url, width=1920, height=1600):
    profile = TMP_DIR / f'edge-{time.time_ns()}'
    args = [
        '/usr/bin/microsoft-edge', '--headless=new', f'--remote-debugging-port={PORT}',
        f'--user-data-dir={profile}', '--no-sandbox', '--disable-gpu', '--hide-scrollbars',
        '--disable-background-networking', '--no-first-run', f'--window-size={width},{height}', url,
    ]
    proc = subprocess.Popen(args, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, start_new_session=True)

    def close():
        if proc.poll() is not None:
            return
        proc.terminate()
        try:
            proc.wait(timeout=8)
        except subprocess.TimeoutExpired:
            proc.kill()
            proc.wait(timeout=5)

    BROWSER_CLOSERS[proc] = close
    atexit.register(close)

    def stop(_signum, _frame):
        close()
        raise SystemExit(130)

    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)
    for _ in range(60):
        if proc.poll() is not None:
            raise RuntimeError(f'Edge 启动失败，退出码 {proc.returncode}')
        try:
            with urllib.request.urlopen(f'http://127.0.0.1:{PORT}/json/list', timeout=1):
                break
        except Exception:
            time.sleep(0.25)
    else:
        close()
        raise RuntimeError('Edge CDP 端口未就绪')
    return proc


async def get_ws():
    with urllib.request.urlopen(f'http://127.0.0.1:{PORT}/json/list', timeout=5) as response:
        tabs = json.loads(response.read().decode('utf-8'))
    page = next(tab for tab in tabs if tab.get('type') == 'page')
    return await websockets.connect(page['webSocketDebuggerUrl'], max_size=64 * 1024 * 1024)


INJECT_MOCK_DATA_JS = r"""(() => {
    localStorage.setItem('has_shown_welcome_donate', 'true');
    localStorage.setItem('toolbox_first_launch_done', 'true');
    localStorage.setItem('geely_tester_unlocked', 'false');
    localStorage.setItem('geely_use_beta_channel', 'false');
    localStorage.setItem('fuel_default_type', '92#');
    localStorage.setItem('geely_oil_selected_province', '浙江');
    localStorage.setItem('geely_oil_fav_provinces', JSON.stringify(['浙江','上海','江苏','北京']));
    const store = window.__VUE_STORE__;
    if (!store) return 'store not found';
    if (window.__TOOLBOX_THEMES__) {
        window.__TOOLBOX_THEMES__.setPalette?.('sky_ocean');
        window.__TOOLBOX_THEMES__.setMode?.('day');
    }
    store.isNight = false;
    Object.assign(store.theme, { palette: 'sky_ocean', mode: 'day' });
    Object.assign(store.deviceInfo, {
        version: '1.8.30', whitelist: true, multimedia_frozen: true, appstore_frozen: true,
        battery_volt: 126, real_battery_volt: 12.6, car_ip: '192.168.43.100', adb_master_switch: true,
        platform_code: 'IHU516G', platform_name: '吉利缤越 COOL', trim_name: '',
        custom_vehicle_trim: '', is_car_device: true, fuel_percent: 76, fuel_liters: 34.2,
        screen_size: '1920x720', screen_real_size: '1920x720', screen_density: '160 dpi',
        screen_density_dpi: 160, screen_app_bounds: '1920x720', screen_wm: 'Physical size: 1920x720; density 160',
        memory_usage: '2.1G / 6.0G'
    });
    Object.assign(store.networkDevice, {
        wifi_enabled: true, hotspot_enabled: true, bluetooth_enabled: true,
        wifi_policy: 1, hotspot_policy: 1, bluetooth_policy: 1,
        wifi_ssid: '演示车机热点', bluetooth_device_name: '演示手机（已连接）'
    });
    Object.assign(store.settings, { autostart: true, expert_rabbit: true, whitelist_permanent_lock: true });
    store.fuelLedger.isLoaded = true;
    store.fuelLedger.current_odo = 18560;
    store.fuelLedger.current_year = '2026';
    store.fuelLedger.config = {
        tank_capacity: 45, default_fuel_type: '92#', default_province: '浙江',
        plate_number: '演示车', car_id: 'SIM_COOL_1817', cloud_sync_enabled: false
    };
    store.fuelLedger.stats = {
        is_calculable: true, avg_consumption: '6.82', cost_per_km: '0.53',
        total_spend: 1280.0, total_original: 1320.0, total_saved: 40.0,
        total_liters: 163.0, total_records: 4, status_desc: '模拟台账已出账'
    };
    store.fuelLedger.monthly_stats = [
        { month: '2026-10', total_spend: 580.0, total_original: 600.0, total_saved: 20.0, total_liters: 73.8, avg_consumption: '6.75', count: 2 },
        { month: '2026-09', total_spend: 700.0, total_original: 720.0, total_saved: 20.0, total_liters: 89.2, avg_consumption: '6.88', count: 2 }
    ];
    store.fuelLedger.records = [
        { id:'SIM01', date:'2026-10-22', brand:'中国石化', fuel_type:'92#', oilType:'92#', price:7.85, unit_price:7.85, liters:42.0, amount:329.7, actual_paid:309.7, odo:18560, odometer:18560, isFull:true, is_full:true, note:'模拟数据 下班顺路加满' },
        { id:'SIM02', date:'2026-10-08', brand:'中国石油', fuel_type:'92#', oilType:'92#', price:7.82, unit_price:7.82, liters:31.8, amount:248.6, actual_paid:238.6, odo:17945, odometer:17945, isFull:true, is_full:true, note:'模拟数据 国庆返程' },
        { id:'SIM03', date:'2026-09-24', brand:'壳牌', fuel_type:'92#', oilType:'92#', price:7.85, unit_price:7.85, liters:45.0, amount:353.2, actual_paid:343.2, odo:17320, odometer:17320, isFull:true, is_full:true, note:'模拟数据 长途前加满' },
        { id:'SIM04', date:'2026-09-05', brand:'中国石化', fuel_type:'92#', oilType:'92#', price:7.81, unit_price:7.81, liters:44.2, amount:345.2, actual_paid:335.2, odo:16675, odometer:16675, isFull:true, is_full:true, note:'模拟数据 日常通勤' }
    ];
    store.oilPrice.selectedProvince = '浙江';
    store.oilPrice.favProvinces = ['浙江','上海','江苏','北京'];
    const prices = {
        '浙江':{p92:7.85,p95:8.35,p98:9.15,p0:7.52}, '北京':{p92:7.88,p95:8.39,p98:9.37,p0:7.59},
        '上海':{p92:7.82,p95:8.32,p98:9.32,p0:7.50}, '江苏':{p92:7.83,p95:8.33,p98:9.33,p0:7.51},
        '广东':{p92:7.88,p95:8.54,p98:10.54,p0:7.55}, '四川':{p92:7.96,p95:8.51,p98:9.25,p0:7.58},
        '山东':{p92:7.84,p95:8.41,p98:9.13,p0:7.44}, '湖北':{p92:7.89,p95:8.45,p98:9.45,p0:7.53}
    };
    Object.assign(store.oilPrice.regionalPrices, prices);
    store.oilPrice.nextAdjustment = {
        date:'2026-11-10', lastAdjustmentDate:'10月27日', trend:'down', predictedLiter:'7.75',
        dateLabel:'11月10日 24时', desc:'预计每吨下调 120 元（折合每升降约 0.10 元）'
    };
    store.doors = { fl:0, fr:0, rl:0, rr:0, trunk:0 };
    if (store.modals) for (const key in store.modals) store.modals[key] = false;
    document.documentElement.style.background = '#eef2f7';
    document.body.style.background = '#eef2f7';
    return 'mock injected successfully';
})()"""


class CDP:
    def __init__(self, ws):
        self.ws = ws
        self.msg_id = 0

    async def call(self, method, params=None):
        self.msg_id += 1
        current = self.msg_id
        await self.ws.send(json.dumps({'id': current, 'method': method, 'params': params or {}}))
        while True:
            result = json.loads(await self.ws.recv())
            if result.get('id') == current:
                if 'error' in result:
                    raise RuntimeError(f'{method}: {result["error"]}')
                return result.get('result', {})

    async def evaluate(self, expression):
        result = await self.call('Runtime.evaluate', {'expression': expression, 'returnByValue': True, 'awaitPromise': True})
        if result.get('exceptionDetails'):
            raise RuntimeError(result['exceptionDetails'].get('text', '页面 JS 执行失败'))
        return result.get('result', {}).get('value')

    async def capture_rect(self, clip, filename, expected_text=None, max_width=1440):
        if expected_text:
            text = await self.evaluate('document.body.innerText')
            assert expected_text in text, f'{filename} 缺少预期内容: {expected_text}'
        result = await self.call('Page.captureScreenshot', {
            'format': 'png', 'clip': {'x': float(clip['x']), 'y': float(clip['y']),
            'width': float(clip['width']), 'height': float(clip['height']), 'scale': 1}
        })
        raw = base64.b64decode(result['data'])
        image = Image.open(io.BytesIO(raw)).convert('RGB')
        assert image.width >= 300 and image.height >= 100, f'{filename} 图像尺寸异常'
        if image.width > max_width:
            height = round(image.height * max_width / image.width)
            image = image.resize((max_width, height), Image.Resampling.LANCZOS)
        out_path = OUT_DIR / filename
        image.save(out_path, 'WEBP', quality=84, method=6)
        entry = {
            'file': filename, 'width': image.width, 'height': image.height,
            'bytes': out_path.stat().st_size, 'sha256': hashlib.sha256(out_path.read_bytes()).hexdigest(),
            'expected_text': expected_text, 'mock_data': True,
        }
        MANIFEST.append(entry)
        print(f'[OK] {filename} {image.width}x{image.height} {entry["bytes"]} bytes', flush=True)


VIEWS = [
    ('link', '方向盘按键', 'preview_link_full.webp', '静音键'),
    ('link', '接管模式图解', 'preview_wheel_mode.webp', '接管模式'),
    ('link', '360全景联动', 'preview_link_360.webp', '360'),
    ('link', '车速与大灯', 'preview_link_speed.webp', '车速'),
    ('link', '网络与外设', 'preview_link_network.webp', 'Wi-Fi'),
    ('body', '换挡语音', 'preview_body_full.webp', '换挡'),
    ('body', '驾驶模式', 'preview_body_mode.webp', '驾驶模式'),
    ('body', '车门分门播报', 'preview_body_door.webp', '车门'),
    ('body', '电动尾门', 'preview_body_trunk.webp', '尾门'),
    ('body', '音乐音源优先级', 'preview_audio_full.webp', '音源'),
    ('body', '专属语音包', 'preview_body_packs.webp', '语音包'),
    ('fuel', '实时油价日历', 'preview_fuel_full.webp', '92#'),
    ('fuel', '智能加油账本', 'preview_fuel_record.webp', '模拟数据'),
    ('store', '专车精选商城', 'preview_store_full.webp', '专车'),
    ('store', '特权安装通道', 'preview_install_full.webp', '通道'),
    ('store', '整车应用管理', 'preview_all_apps.webp', '应用'),
    ('system', '桌面胶囊悬浮', 'preview_system_desktop.webp', '桌面'),
    ('system', '顶栏状态胶囊', 'preview_system_capsules.webp', '胶囊'),
    ('system', '系统维护与工具', 'preview_system_ops.webp', '日志'),
    ('system', '外观主题风格', 'preview_system_full.webp', '晴空蓝'),
    ('system', '车标与启动项', 'preview_system_icon.webp', '启动'),
]


async def capture_toolbox(apps_json):
    proc = start_edge(TOOLBOX_HTML)
    ws = None
    try:
        ws = await get_ws()
        cdp = CDP(ws)
        for _ in range(40):
            if await cdp.evaluate("typeof window.__VUE_STORE__ !== 'undefined'"):
                break
            await asyncio.sleep(0.25)
        assert await cdp.evaluate(INJECT_MOCK_DATA_JS) == 'mock injected successfully'
        await cdp.evaluate(f'window.applyCloudAppsJson?.({json.dumps(apps_json)});')
        await asyncio.sleep(1.2)

        async def capture_active_view(nav, tab, filename, expected_text):
            expression = f"""(() => {{
                const store = window.__VUE_STORE__;
                store.currentNav = {json.dumps(nav)};
                return true;
            }})()"""
            await cdp.evaluate(expression)
            await asyncio.sleep(0.35)
            clicked = await cdp.evaluate(f"""(() => {{
                const candidates = Array.from(document.querySelectorAll('button,[role="tab"]'))
                    .filter(el => el.offsetParent !== null);
                const target = candidates.find(el => (el.textContent || '').replace(/\\s+/g,'').includes({json.dumps(tab)}));
                if (!target) return false;
                target.click();
                return true;
            }})()""")
            assert clicked, f'找不到功能分项: {tab}'
            await asyncio.sleep(0.6)
            bounds = await cdp.evaluate("""(() => {
                const section = document.querySelector('main section.flex-1');
                if (!section) return null;
                const active = Array.from(section.children).find(el => el.offsetParent !== null && getComputedStyle(el).display !== 'none');
                if (!active) return null;
                const r = active.getBoundingClientRect();
                let bottom = r.bottom;
                if (r.height <= 600) {
                    // 短面板是自动高度，取其最后一张可见功能卡片的实际底边。
                    bottom = 0;
                    for (const el of active.querySelectorAll('div.rounded-2xl,div.rounded-3xl')) {
                        const card = el.getBoundingClientRect();
                        if (el.offsetParent !== null && card.width > 200 && card.height > 40) bottom = Math.max(bottom, card.bottom);
                    }
                }
                return {right:Math.ceil(r.right),height:r.height>600?r.bottom:720,bottom:Math.ceil(bottom)};
            })()""")
            assert bounds and bounds['bottom'] > 300, f'{filename} 未找到完整功能内容'
            # 只保留当前功能面板的实际底边，短页面保留原车机 720px 高度。
            height = max(720, min(1900, bounds['bottom'] + 24))
            await cdp.capture_rect({'x':0,'y':0,'width':1920,'height':height}, filename, expected_text)

        for view in VIEWS:
            await capture_active_view(*view)

        async def open_modal(name, payload='true'):
            await cdp.evaluate(f"""(() => {{
                const m = window.__VUE_STORE__.modals;
                for (const key in m) m[key] = false;
                m[{json.dumps(name)}] = {payload};
                return true;
            }})()""")
            await asyncio.sleep(0.8)

        async def capture_modal(name, filename, expected_text, payload='true', before=None, pre=None, max_width=1100):
            if pre:
                await cdp.evaluate(pre)
            await open_modal(name, payload)
            await asyncio.sleep(0.8)
            if before:
                await cdp.evaluate(before)
                await asyncio.sleep(0.6)
            rect = await cdp.evaluate("""(() => {
                const cards = Array.from(document.querySelectorAll('.tb-modal-surface.modal-card-anim'))
                    .filter(el => { const r = el.getBoundingClientRect(); return r.width > 300 && r.height > 100; });
                const card = cards[cards.length - 1];
                if (!card) return null;
                const r = card.getBoundingClientRect();
                return {x:r.left,y:r.top,width:r.width,height:r.height};
            })()""")
            assert rect and rect['width'] > 300 and rect['height'] > 100, f'弹窗未出现: {name}'
            await cdp.evaluate("document.querySelectorAll('.tb-modal-surface.modal-card-anim').forEach(el => { const p = el.parentElement; if (p && getComputedStyle(p).position === 'fixed') p.style.background = 'transparent'; });")
            await cdp.capture_rect(rect, filename, expected_text, max_width=max_width)

        await capture_modal('about', 'preview_about.webp', '缤越')
        await capture_modal('battery', 'preview_vehicle_health.webp', '12.6')
        await capture_modal('oilPrice', 'preview_oil_price.webp', '92#')
        await capture_modal('wheelKeyLearning', 'preview_wheel_learning.webp', '按键',
                            before="window.ToolboxBridge=window.ToolboxBridge||{};window.ToolboxBridge.startWheelKeyLearning=()=>true;window.onWheelKeyLearned({keyCode:349});")
        await capture_modal('voiceThemeImport', 'preview_voice_import.webp', '一键解压导入',
                            pre="window.ToolboxBridge=window.ToolboxBridge||{};window.ToolboxBridge.scanVoiceZipsInDownload=()=>JSON.stringify([{name:'SIM_甜美女声.zip',size:'4.2 MB',time:'2026-11-01 20:00'}]);")
        await capture_modal('cleanDownload', 'preview_clean_download.webp', '智能安全清理',
                            pre="window.ToolboxBridge.getDownloadDirStats=()=>JSON.stringify({count:12,size_mb:'286.40',path:'/sdcard/Download/'});")
        await capture_modal('qrCode', 'preview_mobile_qr.webp', '手机')
        await capture_modal('deepTools', 'preview_adb_terminal.webp', 'ADB 交互控制台',
                            pre="window.ToolboxBridge.probeAdbStatus=()=>JSON.stringify({ready:true,title:'ADB 特权终端已就绪',details:'模拟本地通道已连接',privilege:'Shell 2000'});")
        await capture_modal('log', 'preview_log_main.webp', '抓取全量诊断日志',
                            pre="window.ToolboxBridge.getRunLogContent=()=>'[SIM] 2026-11-01 20:00 模拟车机启动\\n[SIM] 平台 IHU516G · 缤越 COOL\\n[SIM] P挡 · 电瓶12.6V · 模拟日志已加载';")
        # 诊断日志上传结果二级弹窗：先触发抓取完成回调（自动弹出上传确认），再触发上传成功回调
        await open_modal('log')
        await asyncio.sleep(0.8)
        await cdp.evaluate("window.onLogcatDumpFinished({success:true,filename:'SIM_COOL_log.zip',sizeStr:'2.8 MB',chip:'IHU516G',modelCode:'SX11A3',vinLast6:'SIM001',capturedTime:'2026-11-01 20:00',message:'模拟日志打包完成'})")
        await asyncio.sleep(1.2)
        await cdp.evaluate("window.onLogUploadFinished({success:true,code:'181717',vin6:'SIM001'})")
        await asyncio.sleep(0.8)
        rect = await cdp.evaluate("""(() => {
            const cards = Array.from(document.querySelectorAll('.tb-modal-surface.modal-card-anim'))
                .filter(el => { const r = el.getBoundingClientRect(); return r.width > 300 && r.height > 100; });
            const card = cards[cards.length - 1];
            if (!card) return null;
            const r = card.getBoundingClientRect();
            return {x:r.left,y:r.top,width:r.width,height:r.height};
        })()""")
        assert rect, '日志上传结果弹窗未出现'
        await cdp.evaluate("document.querySelectorAll('.tb-modal-surface.modal-card-anim').forEach(el => { const p = el.parentElement; if (p && getComputedStyle(p).position === 'fixed') p.style.background = 'transparent'; });")
        await cdp.capture_rect(rect, 'preview_log_modal.webp', '车架后6位', max_width=1100)
        # 原生桌面卡片：后续由 Android XML 同源渲染，非实车拍摄。
    finally:
        if ws:
            await ws.close()
        close_browser(proc)


async def capture_mobile():
    proc = start_edge(MOBILE_HTML, width=860, height=1600)
    ws = None
    try:
        ws = await get_ws()
        cdp = CDP(ws)
        await cdp.call('Emulation.setDeviceMetricsOverride', {'width':430,'height':1600,'deviceScaleFactor':1,'mobile':True})
        await asyncio.sleep(0.8)
        # 只向页面内存注入演示文件，不读取车机真实目录。
        await cdp.evaluate("""(() => {
            const box = document.getElementById('car-files-box');
            if (box) box.innerHTML = '<div style="padding:16px;background:#fff;border:1px solid #e4e4e7;border-radius:12px;margin:8px 0"><b>模拟安装包.apk</b><div style="color:#71717a;font-size:13px;margin-top:6px">88.5 MB · 仅用于界面展示</div></div><div style="padding:16px;background:#fff;border:1px solid #e4e4e7;border-radius:12px;margin:8px 0"><b>模拟语音包.zip</b><div style="color:#71717a;font-size:13px;margin-top:6px">4.2 MB · 仅用于界面展示</div></div>';
            return !!box;
        })()""")
        height = await cdp.evaluate("Math.ceil(Math.max(document.body.scrollHeight, document.documentElement.scrollHeight))")
        await cdp.call('Emulation.setDeviceMetricsOverride', {'width':430,'height':min(1800,max(900,height+30)),'deviceScaleFactor':1,'mobile':True})
        await cdp.capture_rect({'x':0,'y':0,'width':430,'height':min(1800,max(900,height+30))}, 'preview_mobile_transfer.webp', max_width=430)
    finally:
        if ws:
            await ws.close()
        close_browser(proc)


def render_widget():
    """按原生 widget_toolbox_3x1.xml 的布局与主题色生成模拟卡片说明图。"""
    width, height = 720, 320
    image = Image.new('RGB', (width, height), '#F7F9FC')
    draw = ImageDraw.Draw(image)
    font_path = '/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc'
    if not Path(font_path).exists():
        candidates = list(Path('/usr/share/fonts').rglob('*CJK*.ttc')) + list(Path('/usr/share/fonts').rglob('*noto*sans*cjk*'))
        font_path = str(candidates[0]) if candidates else '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
    def font(size):
        return ImageFont.truetype(font_path, size)
    draw.rounded_rectangle((8,8,712,312), radius=24, fill='#FFFFFF', outline='#E2E8F0', width=2)
    draw.text((28,20), '缤越助手 · 桌面卡片（同源布局示意）', font=font(22), fill='#18181B')
    draw.text((28,55), '缤越COOL 2022款  |  电瓶健康监测中', font=font(18), fill='#0284C7')
    draw.text((28,92), '12.6V', font=font(38), fill='#0284C7')
    draw.text((28,140), '健康良好 · P挡', font=font(20), fill='#16A34A')
    draw.text((28,184), '全车门锁 正常', font=font(20), fill='#64748B')
    draw.rounded_rectangle((28,238,690,298), radius=14, fill='#EFF6FF')
    draw.text((50,252), '今日油价：92# 7.85元/L   |   95# 8.35元/L', font=font(20), fill='#334155')
    draw.text((355,90), '↑ 工程模式', font=font(23), fill='#18181B')
    draw.text((355,135), '#*190110', font=font(28), fill='#18181B')
    draw.text((355,190), '○ 360全景', font=font(23), fill='#18181B')
    out = OUT_DIR / 'preview_widget.webp'
    image.save(out, 'WEBP', quality=88, method=6)
    MANIFEST.append({'file':out.name,'width':width,'height':height,'bytes':out.stat().st_size,'sha256':hashlib.sha256(out.read_bytes()).hexdigest(),'expected_text':'原生 RemoteViews 同源模拟图','mock_data':True})
    print(f'[OK] {out.name} {width}x{height} {out.stat().st_size} bytes', flush=True)


async def main():
    print('开始生成 v1.8.30 模拟实车完整功能图鉴', flush=True)
    apps = fetch_apps()
    await capture_toolbox(apps)
    await capture_mobile()
    manifest_path = OUT_DIR / 'manifest.json'
    manifest_path.write_text(json.dumps({'version':'1.8.30','source':'toolbox_ui.html + simulated COOL state','images':MANIFEST}, ensure_ascii=False, indent=2), encoding='utf-8')
    print(f'生成完成：{len(MANIFEST)} 张，清单 {manifest_path}', flush=True)


if __name__ == '__main__':
    asyncio.run(main())
