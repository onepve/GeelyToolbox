/**
 * 全国各省市成品油最新最高零售限价与调价周期数据
 * 离线权威兜底 + 支持云端静默同步
 */

export const DEFAULT_NEXT_ADJUSTMENT = {
  date: '2026-09-24 24:00',
  dateLabel: '2026年9月24日 24:00',
  roundName: '2026年第19轮调价',
  lastAdjustmentDate: '2026年9月11日 24:00',
  trend: 'up', // 'up' | 'down' | 'flat'
  trendLabel: '预计大幅上调',
  predictedTonne: '+520 元/吨',
  predictedLiter: '+0.43 ~ +0.46',
  tankSaving: '约 20 元',
  tip: '缤越 COOL 油箱容积 45L，在 24 日晚前加满一箱可省约 20 元，建议提前补油！'
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
  '浙江': { p92: 8.26, p95: 8.79, p98: 10.29, p0: 7.95, diff92: 0.21, diff95: 0.22, diff98: 0.23, diff0: 0.21 },
  '上海': { p92: 8.25, p95: 8.78, p98: 10.78, p0: 7.95, diff92: 0.21, diff95: 0.22, diff98: 0.23, diff0: 0.21 },
  '江苏': { p92: 8.26, p95: 8.79, p98: 10.86, p0: 7.94, diff92: 0.21, diff95: 0.22, diff98: 0.23, diff0: 0.21 },
  '北京': { p92: 8.29, p95: 8.83, p98: 10.33, p0: 8.01, diff92: 0.21, diff95: 0.22, diff98: 0.23, diff0: 0.21 },
  '广东': { p92: 8.31, p95: 9.00, p98: 11.00, p0: 7.97, diff92: 0.21, diff95: 0.22, diff98: 0.23, diff0: 0.21 },
  '山东': { p92: 8.26, p95: 8.86, p98: 9.86, p0: 7.90, diff92: 0.21, diff95: 0.22, diff98: 0.23, diff0: 0.21 },
  '四川': { p92: 8.39, p95: 8.96, p98: 10.23, p0: 8.01, diff92: 0.21, diff95: 0.22, diff98: 0.23, diff0: 0.21 },
  '重庆': { p92: 8.35, p95: 8.82, p98: 10.66, p0: 8.03, diff92: 0.21, diff95: 0.22, diff98: 0.23, diff0: 0.21 },
  '湖北': { p92: 8.31, p95: 8.89, p98: 10.29, p0: 7.97, diff92: 0.21, diff95: 0.22, diff98: 0.23, diff0: 0.21 },
  '湖南': { p92: 8.24, p95: 8.76, p98: 9.96, p0: 8.05, diff92: 0.21, diff95: 0.22, diff98: 0.23, diff0: 0.21 },
  '河南': { p92: 8.30, p95: 8.64, p98: 9.52, p0: 7.96, diff92: 0.21, diff95: 0.22, diff98: 0.23, diff0: 0.21 },
  '河北': { p92: 8.29, p95: 8.76, p98: 9.58, p0: 7.98, diff92: 0.21, diff95: 0.22, diff98: 0.23, diff0: 0.21 },
  '天津': { p92: 8.29, p95: 8.76, p98: 10.26, p0: 7.97, diff92: 0.21, diff95: 0.22, diff98: 0.23, diff0: 0.21 },
  '安徽': { p92: 8.24, p95: 8.81, p98: 10.31, p0: 8.01, diff92: 0.21, diff95: 0.22, diff98: 0.23, diff0: 0.21 },
  '福建': { p92: 8.25, p95: 8.81, p98: 10.31, p0: 7.95, diff92: 0.21, diff95: 0.22, diff98: 0.23, diff0: 0.21 },
  '江西': { p92: 8.25, p95: 8.86, p98: 10.36, p0: 8.02, diff92: 0.21, diff95: 0.22, diff98: 0.23, diff0: 0.21 },
  '陕西': { p92: 8.17, p95: 8.64, p98: 9.87, p0: 7.86, diff92: 0.21, diff95: 0.22, diff98: 0.23, diff0: 0.21 },
  '山西': { p92: 8.24, p95: 8.89, p98: 10.07, p0: 8.04, diff92: 0.21, diff95: 0.22, diff98: 0.23, diff0: 0.21 },
  '辽宁': { p92: 8.36, p95: 8.94, p98: 9.79, p0: 7.98, diff92: 0.21, diff95: 0.22, diff98: 0.23, diff0: 0.21 },
  '吉林': { p92: 8.26, p95: 8.91, p98: 9.71, p0: 7.89, diff92: 0.21, diff95: 0.22, diff98: 0.23, diff0: 0.21 },
  '黑龙江': { p92: 8.26, p95: 8.85, p98: 10.03, p0: 7.76, diff92: 0.21, diff95: 0.22, diff98: 0.23, diff0: 0.21 },
  '内蒙古': { p92: 8.30, p95: 8.81, p98: 9.63, p0: 7.83, diff92: 0.21, diff95: 0.22, diff98: 0.23, diff0: 0.21 },
  '广西': { p92: 8.35, p95: 9.02, p98: 10.30, p0: 8.02, diff92: 0.21, diff95: 0.22, diff98: 0.23, diff0: 0.21 },
  '海南': { p92: 9.41, p95: 9.99, p98: 10.99, p0: 8.06, diff92: 0.21, diff95: 0.22, diff98: 0.23, diff0: 0.21 },
  '贵州': { p92: 8.42, p95: 8.90, p98: 9.90, p0: 8.07, diff92: 0.21, diff95: 0.22, diff98: 0.23, diff0: 0.21 },
  '云南': { p92: 8.44, p95: 9.06, p98: 9.74, p0: 8.04, diff92: 0.21, diff95: 0.22, diff98: 0.23, diff0: 0.21 },
  '西藏': { p92: 9.17, p95: 9.69, p98: 10.81, p0: 8.52, diff92: 0.21, diff95: 0.22, diff98: 0.23, diff0: 0.21 },
  '甘肃': { p92: 8.30, p95: 8.86, p98: 9.43, p0: 7.87, diff92: 0.21, diff95: 0.22, diff98: 0.23, diff0: 0.21 },
  '青海': { p92: 8.25, p95: 8.85, p98: 9.64, p0: 7.89, diff92: 0.21, diff95: 0.22, diff98: 0.23, diff0: 0.21 },
  '宁夏': { p92: 8.19, p95: 8.65, p98: 9.86, p0: 7.86, diff92: 0.21, diff95: 0.22, diff98: 0.23, diff0: 0.21 },
  '新疆': { p92: 8.07, p95: 8.64, p98: 9.65, p0: 7.71, diff92: 0.21, diff95: 0.22, diff98: 0.23, diff0: 0.21 }
};

/**
 * 获取距离下次调价的天数
 */
export function getDaysToAdjustment(targetDateStr = DEFAULT_NEXT_ADJUSTMENT.date) {
  try {
    const target = new Date(targetDateStr.replace(/-/g, '/'));
    const now = new Date();
    const diff = target.getTime() - now.getTime();
    if (diff <= 0) return 0;
    return Math.ceil(diff / (1000 * 60 * 60 * 24));
  } catch (e) {
    return 2;
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
