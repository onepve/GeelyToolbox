/**
 * 全国各省市成品油最新最高零售限价与调价周期数据
 * 离线权威兜底 + 支持云端静默同步 (数据来源: 国家发改委 / 东方财富数据中心)
 */

export const DEFAULT_NEXT_ADJUSTMENT = {
  "date": "2026-10-14 24:00",
  "dateLabel": "2026年10月14日 24:00",
  "roundName": "2026年第20轮调价",
  "lastAdjustmentDate": "2026年9月25日 24:00",
  "trend": "up",
  "trendLabel": "预计大幅上调",
  "predictedTonne": "+520 元/吨",
  "predictedLiter": "+0.43 ~ +0.46",
  "tankSaving": "约 20 元",
  "tip": "缤越 COOL 油箱容积 45L，在 10月14日 24:00前加满一箱可省约 20 元，建议提前补油！"
};

export const PROVINCE_LIST = [
  '北京', '天津', '河北', '山西', '内蒙古',
  '辽宁', '吉林', '黑龙江', '上海', '江苏',
  '浙江', '安徽', '福建', '江西', '山东',
  '河南', '湖北', '湖南', '广东', '广西',
  '海南', '重庆', '四川', '贵州', '云南',
  '西藏', '陕西', '甘肃', '青海', '宁夏', '新疆'
];

export const REGIONAL_PRICES = {
  "安徽": {
    "p92": 8.55,
    "p95": 9.14,
    "p98": 10.34,
    "p0": 8.34,
    "diff92": 0.31,
    "diff95": 0.33,
    "diff98": 0.33,
    "diff0": 0.33
  },
  "北京": {
    "p92": 8.61,
    "p95": 9.17,
    "p98": 10.37,
    "p0": 8.36,
    "diff92": 0.32,
    "diff95": 0.34,
    "diff98": 0.34,
    "diff0": 0.34
  },
  "重庆": {
    "p92": 8.67,
    "p95": 9.16,
    "p98": 10.36,
    "p0": 8.36,
    "diff92": 0.32,
    "diff95": 0.34,
    "diff98": 0.34,
    "diff0": 0.33
  },
  "福建": {
    "p92": 8.57,
    "p95": 9.15,
    "p98": 10.35,
    "p0": 8.29,
    "diff92": 0.32,
    "diff95": 0.34,
    "diff98": 0.34,
    "diff0": 0.33
  },
  "广东": {
    "p92": 8.63,
    "p95": 9.35,
    "p98": 10.55,
    "p0": 8.31,
    "diff92": 0.32,
    "diff95": 0.35,
    "diff98": 0.35,
    "diff0": 0.33
  },
  "广西": {
    "p92": 8.67,
    "p95": 9.36,
    "p98": 10.56,
    "p0": 8.36,
    "diff92": 0.32,
    "diff95": 0.34,
    "diff98": 0.34,
    "diff0": 0.33
  },
  "贵州": {
    "p92": 8.74,
    "p95": 9.24,
    "p98": 10.44,
    "p0": 8.4,
    "diff92": 0.32,
    "diff95": 0.34,
    "diff98": 0.34,
    "diff0": 0.33
  },
  "甘肃": {
    "p92": 8.61,
    "p95": 9.2,
    "p98": 10.4,
    "p0": 8.2,
    "diff92": 0.31,
    "diff95": 0.34,
    "diff98": 0.34,
    "diff0": 0.33
  },
  "河北": {
    "p92": 8.61,
    "p95": 9.09,
    "p98": 10.29,
    "p0": 8.31,
    "diff92": 0.32,
    "diff95": 0.33,
    "diff98": 0.33,
    "diff0": 0.33
  },
  "河南": {
    "p92": 8.62,
    "p95": 9.21,
    "p98": 10.41,
    "p0": 8.29,
    "diff92": 0.32,
    "diff95": 0.34,
    "diff98": 0.34,
    "diff0": 0.33
  },
  "海南": {
    "p92": 9.72,
    "p95": 10.33,
    "p98": 11.53,
    "p0": 8.39,
    "diff92": 0.31,
    "diff95": 0.34,
    "diff98": 0.34,
    "diff0": 0.33
  },
  "湖北": {
    "p92": 8.62,
    "p95": 9.23,
    "p98": 10.43,
    "p0": 8.29,
    "diff92": 0.31,
    "diff95": 0.34,
    "diff98": 0.34,
    "diff0": 0.33
  },
  "湖南": {
    "p92": 8.552363091,
    "p95": 9.091018868,
    "p98": 10.29,
    "p0": 8.373597929,
    "diff92": 0.314103526,
    "diff95": 0.333886793,
    "diff98": 0.333886793,
    "diff0": 0.332182916
  },
  "江苏": {
    "p92": 8.58,
    "p95": 9.12,
    "p98": 10.32,
    "p0": 8.26,
    "diff92": 0.32,
    "diff95": 0.33,
    "diff98": 0.33,
    "diff0": 0.33
  },
  "吉林": {
    "p92": 8.57,
    "p95": 9.25,
    "p98": 10.45,
    "p0": 8.21,
    "diff92": 0.31,
    "diff95": 0.34,
    "diff98": 0.34,
    "diff0": 0.33
  },
  "江西": {
    "p92": 8.56,
    "p95": 9.19,
    "p98": 10.39,
    "p0": 8.35,
    "diff92": 0.31,
    "diff95": 0.33,
    "diff98": 0.33,
    "diff0": 0.33
  },
  "内蒙古": {
    "p92": 8.62,
    "p95": 9.15,
    "p98": 10.35,
    "p0": 8.14,
    "diff92": 0.32,
    "diff95": 0.34,
    "diff98": 0.34,
    "diff0": 0.32
  },
  "宁夏": {
    "p92": 8.5,
    "p95": 8.98,
    "p98": 10.18,
    "p0": 8.18,
    "diff92": 0.31,
    "diff95": 0.33,
    "diff98": 0.33,
    "diff0": 0.33
  },
  "青海": {
    "p92": 8.57,
    "p95": 9.19,
    "p98": 10.39,
    "p0": 8.22,
    "diff92": 0.32,
    "diff95": 0.34,
    "diff98": 0.34,
    "diff0": 0.33
  },
  "山西": {
    "p92": 8.55,
    "p95": 9.23,
    "p98": 10.43,
    "p0": 8.38,
    "diff92": 0.31,
    "diff95": 0.34,
    "diff98": 0.34,
    "diff0": 0.34
  },
  "山东": {
    "p92": 8.57,
    "p95": 9.2,
    "p98": 10.4,
    "p0": 8.21,
    "diff92": 0.31,
    "diff95": 0.34,
    "diff98": 0.34,
    "diff0": 0.33
  },
  "上海": {
    "p92": 8.57,
    "p95": 9.12,
    "p98": 10.32,
    "p0": 8.28,
    "diff92": 0.32,
    "diff95": 0.34,
    "diff98": 0.34,
    "diff0": 0.33
  },
  "四川": {
    "p92": 8.7,
    "p95": 9.3,
    "p98": 10.5,
    "p0": 8.34,
    "diff92": 0.31,
    "diff95": 0.34,
    "diff98": 0.34,
    "diff0": 0.33
  },
  "陕西": {
    "p92": 8.49,
    "p95": 8.97,
    "p98": 10.17,
    "p0": 8.19,
    "diff92": 0.32,
    "diff95": 0.33,
    "diff98": 0.33,
    "diff0": 0.33
  },
  "天津": {
    "p92": 8.61,
    "p95": 9.09,
    "p98": 10.29,
    "p0": 8.31,
    "diff92": 0.32,
    "diff95": 0.33,
    "diff98": 0.33,
    "diff0": 0.33
  },
  "西藏": {
    "p92": 9.48,
    "p95": 10.03,
    "p98": 11.23,
    "p0": 8.83,
    "diff92": 0.31,
    "diff95": 0.34,
    "diff98": 0.34,
    "diff0": 0.33
  },
  "云南": {
    "p92": 8.76,
    "p95": 9.4,
    "p98": 10.6,
    "p0": 8.37,
    "diff92": 0.32,
    "diff95": 0.34,
    "diff98": 0.34,
    "diff0": 0.33
  },
  "浙江": {
    "p92": 8.58,
    "p95": 9.12,
    "p98": 10.32,
    "p0": 8.28,
    "diff92": 0.32,
    "diff95": 0.33,
    "diff98": 0.33,
    "diff0": 0.33
  }
};

/**
 * 计算距离调价日期的剩余天数与小时
 */
export function getDaysToAdjustment(targetDateStr) {
  if (!targetDateStr) return { days: 0, hours: 0, isPassed: true };
  try {
    const cleanStr = targetDateStr.replace('24:00', '23:59:59');
    const target = new Date(cleanStr.replace(/-/g, '/')).getTime();
    const now = Date.now();
    const diffMs = target - now;
    if (diffMs <= 0) {
      return { days: 0, hours: 0, isPassed: true };
    }
    const days = Math.floor(diffMs / (24 * 3600 * 1000));
    const hours = Math.floor((diffMs % (24 * 3600 * 1000)) / (3600 * 1000));
    return { days, hours, isPassed: false };
  } catch (e) {
    return { days: 0, hours: 0, isPassed: true };
  }
}

// 默认 4 小时后台静默同步周期（发改委每10个工作日调价，平时每天价格固定，4小时巡检一次最省流量且最敏锐）
export const OIL_SYNC_INTERVAL_MS = 4 * 60 * 60 * 1000;
export const OIL_PRICE_API_URL = 'https://dl.onepve.com/GeelyToolbox/oil-price.json';

/**
 * 获取或同步最新全国油价与调价预测
 * @param {boolean} force 是否强制忽略 4 小时本地缓存限制
 */
export async function syncOilPrices(force = false) {
  try {
    const now = Date.now();
    const lastSync = parseInt(localStorage.getItem('geely_oil_last_sync_time') || '0', 10);
    
    if (!force && (now - lastSync < OIL_SYNC_INTERVAL_MS)) {
      const cached = localStorage.getItem('geely_oil_cached_data');
      if (cached) {
        return { success: true, fromCache: true, data: JSON.parse(cached) };
      }
    }

    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 6000);

    const res = await fetch(`${OIL_PRICE_API_URL}?t=${now}`, {
      signal: controller.signal,
      cache: 'no-store'
    });
    clearTimeout(timeoutId);

    if (res.ok) {
      const json = await res.json();
      if (json && (json.regionalPrices || json.nextAdjustment)) {
        localStorage.setItem('geely_oil_cached_data', JSON.stringify(json));
        localStorage.setItem('geely_oil_last_sync_time', String(now));
        return { success: true, fromCache: false, data: json };
      }
    }
    return { success: false, reason: 'invalid_response' };
  } catch (err) {
    return { success: false, reason: err.message };
  }
}
