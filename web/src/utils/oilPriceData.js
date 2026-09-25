/**
 * 全国各省市成品油最新最高零售限价与调价周期数据
 * 100% 动态直连云端 R2 (dl.onepve.com/GeelyToolbox/oil-price.json)，严禁本地写死伪造兜底
 */

export const DEFAULT_NEXT_ADJUSTMENT = null;

export const PROVINCE_LIST = [
  '北京', '天津', '河北', '山西', '内蒙古',
  '辽宁', '吉林', '黑龙江', '上海', '江苏',
  '浙江', '安徽', '福建', '江西', '山东',
  '河南', '湖北', '湖南', '广东', '广西',
  '海南', '重庆', '四川', '贵州', '云南',
  '西藏', '陕西', '甘肃', '青海', '宁夏', '新疆'
];

export const REGIONAL_PRICES = {};

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

export const OIL_PRICE_API_URL = 'https://dl.onepve.com/GeelyToolbox/oil-price.json';

/**
 * 获取或同步最新全国油价与调价预测
 * 100% 直连云端 R2，仅断网时允许读取真实历史缓存
 */
export async function syncOilPrices(force = false) {
  try {
    const now = Date.now();
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
        try {
          localStorage.setItem('geely_oil_cached_data', JSON.stringify(json));
          localStorage.setItem('geely_oil_last_sync_time', String(now));
        } catch (e) {}
        return { success: true, fromCache: false, data: json };
      }
    }
  } catch (err) {
    // 仅在网络异常且非强制刷新时，尝试读取上一次成功同步的真实离线缓存
    if (!force) {
      try {
        const cached = localStorage.getItem('geely_oil_cached_data');
        if (cached) {
          return { success: true, fromCache: true, data: JSON.parse(cached) };
        }
      } catch (e) {}
    }
    return { success: false, reason: err.message };
  }
  return { success: false, reason: 'network_failed' };
}
