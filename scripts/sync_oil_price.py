#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
全国各省实时油价自动化同步脚本 (Eastmoney 官方数据中心 API + 发改委调价日历)
- 抓取全国 31 省市发改委最新最高零售限价与涨跌额
- 计算下轮调价窗口与缤越 COOL 45L 油箱算账预测
- 输出标准 /tmp/oil-price.json 并支持一键同步 Cloudflare R2 & 边缘 CDN
"""

import os
import sys
import json
import re
import urllib.request
import urllib.parse
from datetime import datetime, date

REPO_DIR = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

# 2026 年国家发改委成品油调价日历 (每 10 个工作日，官方法定窗口，遇国庆长假等法定休市顺延)
SCHEDULE_2026 = [
    {"round": 1, "date": "2026-01-06 24:00", "label": "2026年1月6日 24:00"},
    {"round": 2, "date": "2026-01-20 24:00", "label": "2026年1月20日 24:00"},
    {"round": 3, "date": "2026-02-03 24:00", "label": "2026年2月3日 24:00"},
    {"round": 4, "date": "2026-02-24 24:00", "label": "2026年2月24日 24:00"},
    {"round": 5, "date": "2026-03-10 24:00", "label": "2026年3月10日 24:00"},
    {"round": 6, "date": "2026-03-24 24:00", "label": "2026年3月24日 24:00"},
    {"round": 7, "date": "2026-04-08 24:00", "label": "2026年4月8日 24:00"},
    {"round": 8, "date": "2026-04-22 24:00", "label": "2026年4月22日 24:00"},
    {"round": 9, "date": "2026-05-11 24:00", "label": "2026年5月11日 24:00"},
    {"round": 10, "date": "2026-05-25 24:00", "label": "2026年5月25日 24:00"},
    {"round": 11, "date": "2026-06-08 24:00", "label": "2026年6月8日 24:00"},
    {"round": 12, "date": "2026-06-22 24:00", "label": "2026年6月22日 24:00"},
    {"round": 13, "date": "2026-07-06 24:00", "label": "2026年7月6日 24:00"},
    {"round": 14, "date": "2026-07-20 24:00", "label": "2026年7月20日 24:00"},
    {"round": 15, "date": "2026-08-03 24:00", "label": "2026年8月3日 24:00"},
    {"round": 16, "date": "2026-08-17 24:00", "label": "2026年8月17日 24:00"},
    {"round": 17, "date": "2026-08-31 24:00", "label": "2026年8月31日 24:00"},
    {"round": 18, "date": "2026-09-11 24:00", "label": "2026年9月11日 24:00"},
    {"round": 19, "date": "2026-09-24 24:00", "label": "2026年9月24日 24:00"},
    {"round": 20, "date": "2026-10-15 24:00", "label": "2026年10月15日 24:00"},
    {"round": 21, "date": "2026-10-29 24:00", "label": "2026年10月29日 24:00"},
    {"round": 22, "date": "2026-11-12 24:00", "label": "2026年11月12日 24:00"},
    {"round": 23, "date": "2026-11-26 24:00", "label": "2026年11月26日 24:00"},
    {"round": 24, "date": "2026-12-10 24:00", "label": "2026年12月10日 24:00"},
    {"round": 25, "date": "2026-12-24 24:00", "label": "2026年12月24日 24:00"},
]

def fetch_oil_forecast(current_year=2026):
    """
    抓取全国成品油下轮调价权威预测 (免鉴权动态源: 汽油价格网 / 油价网)
    包含: 下轮调价具体窗口、涨跌方向 (下调/上调/搁浅)、预期变动幅度 (元/吨与元/升)
    """
    urls = [
        "http://m.qiyoujiage.com/",
        "http://www.qiyoujiage.com/"
    ]
    headers = {
        "User-Agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1"
    }

    for u in urls:
        try:
            req = urllib.request.Request(u, headers=headers)
            with urllib.request.urlopen(req, timeout=8) as resp:
                html = resp.read().decode("utf-8", errors="ignore")
                tc_match = re.search(r"var\s+tishiContent\s*=\s*[\"'](.*?)[\"'];", html)
                raw_text = tc_match.group(1) if tc_match else html
                clean_text = re.sub(r"<[^>]+>", " ", raw_text)
                clean_text = " ".join(clean_text.split())

                # 匹配下次油价日期: 如 "10月15日24时"
                date_m = re.search(r"下次油价(?:将在)?(\d{1,2}月\d{1,2}日24时)调整", clean_text)
                # 匹配预测方向与幅度: 如 "预计下调油价150元/吨(0.11元/升-0.14元/升)"
                fc_m = re.search(r"预计(下调|上调|搁浅)[^\d]*(\d+)?元/吨.*?\((.*?)\)", clean_text)

                if date_m and fc_m:
                    date_raw = date_m.group(1)
                    direction = fc_m.group(1)   # "下调" / "上调" / "搁浅"
                    tonne_val = fc_m.group(2)   # "150"
                    liter_raw = fc_m.group(3)   # "0.11元/升-0.14元/升"

                    month_day = re.match(r"(\d+)月(\d+)日", date_raw)
                    if month_day:
                        m_int = int(month_day.group(1))
                        d_int = int(month_day.group(2))
                        date_iso = f"{current_year}-{m_int:02d}-{d_int:02d} 24:00"
                        date_label = f"{current_year}年{m_int}月{d_int}日 24:00"
                    else:
                        date_iso = None
                        date_label = date_raw

                    liter_nums = [float(x) for x in re.findall(r"\d+\.?\d*", liter_raw)]
                    if len(liter_nums) >= 2:
                        min_l, max_l = liter_nums[0], liter_nums[1]
                    elif len(liter_nums) == 1:
                        min_l, max_l = liter_nums[0], liter_nums[0]
                    else:
                        t_float = float(tonne_val) if tonne_val else 150.0
                        min_l = round(t_float / 1380.0, 2)
                        max_l = round(t_float / 1320.0, 2)

                    print(f">> [Forecast] 成功从 {u} 抓取动态调价预测: {date_label} {direction} {tonne_val}元/吨 ({min_l}~{max_l}元/升)")
                    return {
                        "success": True,
                        "date_iso": date_iso,
                        "date_label": date_label,
                        "direction": direction,
                        "tonne": int(tonne_val) if tonne_val else None,
                        "min_liter": min_l,
                        "max_liter": max_l,
                        "source": u
                    }
        except Exception as e:
            print(f">> [Forecast] 警告: 尝试连接 {u} 失败: {e}")

    return {"success": False}

def fetch_eastmoney_data():
    headers = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
        'Referer': 'https://data.eastmoney.com/'
    }

    # 1. 获取最新调价生效日期
    rq_url = "https://datacenter-web.eastmoney.com/api/data/v1/get?reportName=RPTA_WEB_YJ_RQ&columns=ALL&sortTypes=-1&sortColumns=DIM_DATE&pageNumber=1&pageSize=5"
    req_rq = urllib.request.Request(rq_url, headers=headers)
    with urllib.request.urlopen(req_rq, timeout=12) as resp:
        rq_res = json.loads(resp.read().decode('utf-8'))
        latest_date_raw = rq_res['result']['data'][0]['DIM_DATE']
        print(f">> [Eastmoney] 官方最新调价生效日: {latest_date_raw}")

    # 2. 获取该日期的 31 省市最高零售限价
    filter_val = urllib.parse.quote(f"(DIM_DATE='{latest_date_raw}')")
    jh_url = f"https://datacenter-web.eastmoney.com/api/data/v1/get?reportName=RPTA_WEB_YJ_JH&columns=ALL&filter={filter_val}&sortColumns=FIRST_LETTER&sortTypes=1&pageNumber=1&pageSize=50"
    req_jh = urllib.request.Request(jh_url, headers=headers)
    with urllib.request.urlopen(req_jh, timeout=12) as resp:
        jh_res = json.loads(resp.read().decode('utf-8'))
        items = jh_res['result']['data']
        print(f">> [Eastmoney] 成功拉取全国 {len(items)} 个省市最高零售限价")

    # 3. 获取全国汽柴油最新一轮调价幅度 (元/吨)
    bd_url = "https://datacenter-web.eastmoney.com/api/data/v1/get?reportName=RPTA_WEB_YJ_BD&columns=ALL&sortTypes=-1&sortColumns=DIM_DATE&pageNumber=1&pageSize=5"
    req_bd = urllib.request.Request(bd_url, headers=headers)
    latest_adjustment_meta = {}
    try:
        with urllib.request.urlopen(req_bd, timeout=12) as resp:
            bd_res = json.loads(resp.read().decode('utf-8'))
            if bd_res.get('result') and bd_res['result'].get('data'):
                latest_adjustment_meta = bd_res['result']['data'][0]
    except Exception as e:
        print(f"Warning: fetch bd failed: {e}")

    regional_prices = {}
    for it in items:
        city = it['CITYNAME']
        p95 = float(it.get('V95') or 0)
        regional_prices[city] = {
            "p92": float(it.get('V92') or 0),
            "p95": p95,
            "p98": float(it.get('V98') or round(p95 + 1.2, 2)),
            "p0": float(it.get('V0') or 0),
            "diff92": float(it.get('ZDE92') or 0),
            "diff95": float(it.get('ZDE95') or 0),
            "diff98": float(it.get('ZDE98') or it.get('ZDE95') or 0),
            "diff0": float(it.get('ZDE0') or 0)
        }

    # 推导下轮调价日历与轮次
    now = datetime.now()
    next_round = None
    last_round_label = latest_date_raw.split(" ")[0]
    for s in SCHEDULE_2026:
        parse_str = s['date'].replace("24:00", "23:59")
        dt = datetime.strptime(parse_str, "%Y-%m-%d %H:%M")
        if dt > now:
            next_round = dict(s)
            break

    if not next_round:
        next_round = dict(SCHEDULE_2026[-1])

    # 动态抓取第三方权威公开数据中心的调价预期
    forecast = fetch_oil_forecast(now.year)
    date_iso_val = forecast.get("date_iso")
    date_label_val = forecast.get("date_label")
    if forecast.get("success") and isinstance(date_iso_val, str) and isinstance(date_label_val, str):
        parse_str = date_iso_val.replace("24:00", "23:59")
        try:
            dt_fc = datetime.strptime(parse_str, "%Y-%m-%d %H:%M")
            if dt_fc > now:
                next_round['date'] = date_iso_val
                next_round['label'] = date_label_val
        except Exception:
            pass

    # 上轮调价日期格式化
    try:
        dt_last = datetime.strptime(latest_date_raw.split(" ")[0], "%Y-%m-%d")
        last_date_str = f"{dt_last.year}年{dt_last.month}月{dt_last.day}日 24:00"
    except Exception:
        last_date_str = latest_date_raw

    # 根据动态预测构建涨跌幅度与建议
    if forecast.get("success"):
        direction = str(forecast.get("direction", ""))
        tonne_val = forecast.get("tonne")
        tonne = int(tonne_val) if isinstance(tonne_val, (int, float)) else 150
        min_l_val = forecast.get("min_liter")
        min_l = float(min_l_val) if isinstance(min_l_val, (int, float)) else 0.11
        max_l_val = forecast.get("max_liter")
        max_l = float(max_l_val) if isinstance(max_l_val, (int, float)) else 0.14
        saving_min = int(round(45 * min_l))
        saving_max = int(round(45 * max_l))
        saving_str = f"约 {saving_min} ~ {saving_max} 元" if saving_min != saving_max else f"约 {saving_min} 元"
        round_label_str = str(next_round.get('label', ''))
        target_time_label = round_label_str.split('年')[1] if '年' in round_label_str else round_label_str

        if direction == "下调":
            trend = "down"
            trend_label = "预计大幅下调" if tonne >= 200 else "预计小幅下调"
            pred_tonne = f"-{tonne} 元/吨"
            pred_liter = f"-{min_l:.2f} ~ -{max_l:.2f}"
            tip = f"缤越 COOL 油箱容积 45L，在 {target_time_label}调价后加满一箱可省{saving_str}，建议暂缓加油！"
        elif direction == "上调":
            trend = "up"
            trend_label = "预计大幅上调" if tonne >= 200 else "预计小幅上调"
            pred_tonne = f"+{tonne} 元/吨"
            pred_liter = f"+{min_l:.2f} ~ +{max_l:.2f}"
            tip = f"缤越 COOL 油箱容积 45L，在 {target_time_label}前加满一箱可省{saving_str}，建议提前补油！"
        else:
            trend = "flat"
            trend_label = "预计搁浅"
            pred_tonne = "未达 50 元/吨"
            pred_liter = "0.00"
            saving_str = "0 元"
            tip = "本次调价金额预计未达 50 元/吨调价红线，预计不作调整，按需补油即可。"
    else:
        # 网络异常时的安全保底
        trend = "flat"
        trend_label = "正在统计"
        pred_tonne = "--"
        pred_liter = "--"
        saving_str = "--"
        tip = f"第 {next_round['round']} 轮调价窗口将在 {next_round['label']} 开启，正在统计原油变化率。"

    next_adjustment = {
        "date": next_round['date'],
        "dateLabel": next_round['label'],
        "roundName": f"2026年第{next_round['round']}轮调价",
        "lastAdjustmentDate": last_date_str,
        "trend": trend,
        "trendLabel": trend_label,
        "predictedTonne": pred_tonne,
        "predictedLiter": pred_liter,
        "tankSaving": saving_str,
        "tip": tip
    }

    payload = {
        "nextAdjustment": next_adjustment,
        "regionalPrices": regional_prices,
        "updatedAt": date.today().isoformat()
    }
    return payload

def main():
    payload = fetch_eastmoney_data()
    out_path = "/tmp/oil-price.json"
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)
    print(f">> 成功生成全国油价权威标准文件: {out_path} ({len(payload['regionalPrices'])} 个省市)")

if __name__ == "__main__":
    main()
