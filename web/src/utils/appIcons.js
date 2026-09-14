import iconAmap from '../assets/app_icons/amap.webp';
import iconQq from '../assets/app_icons/qqmusic.webp';
import iconNetease from '../assets/app_icons/netease.webp';
import iconKuwo from '../assets/app_icons/kuwo.webp';
import iconKugou from '../assets/app_icons/kugou.webp';
import iconBili from '../assets/app_icons/bilibili.webp';
import iconCarmedia from '../assets/app_icons/carmedia.webp';
import iconPoweramp from '../assets/app_icons/poweramp.webp';
import iconLyrics from '../assets/app_icons/lyrics.webp';
import iconSettings from '../assets/app_icons/settings.webp';
import iconCamera360 from '../assets/app_icons/camera360.webp';
import iconAdb from '../assets/app_icons/adbhelper.webp';
import iconViper from '../assets/app_icons/viperfx.webp';
import iconDefault from '../assets/app_icons/default.webp';

export const APP_ICON_MAP = {
  'com.autonavi.amapauto': iconAmap,
  'com.tencent.qqmusiccar': iconQq,
  'com.netease.cloudmusic.iot': iconNetease,
  'cn.kuwo.kwmusiccar': iconKuwo,
  'com.kugou.android.auto': iconKugou,
  'com.bilibili.bilithings': iconBili,
  'com.ecarx.carmedia': iconCarmedia,
  'com.maxmpz.equalizer': iconPoweramp,
  'com.ecarx.qq.tool': iconLyrics,
  'com.ecarx.settings': iconSettings,
  'ecarx.settings': iconSettings,
  'com.geely.pvm': iconCamera360,
  'com.ecarx.camera': iconCamera360,
  'com.didjdk.adbhelper': iconAdb,
  'com.pittvandewitt.viperfx': iconViper
};

export function getAppIcon(target) {
  if (!target) return iconDefault;
  const str = (typeof target === 'string' ? target : (target.package_name || target.pkg || target.id || '')).toLowerCase();
  
  if (APP_ICON_MAP[str]) return APP_ICON_MAP[str];
  
  if (str.includes('amap') || str.includes('map') || str.includes('navi')) return iconAmap;
  if (str.includes('qqmusic')) return iconQq;
  if (str.includes('netease') || str.includes('cloudmusic')) return iconNetease;
  if (str.includes('kuwo') || str.includes('kamusic')) return iconKuwo;
  if (str.includes('kugou')) return iconKugou;
  if (str.includes('bili')) return iconBili;
  if (str.includes('carmedia')) return iconCarmedia;
  if (str.includes('poweramp') || str.includes('equalizer')) return iconPoweramp;
  if (str.includes('lyrics')) return iconLyrics;
  if (str.includes('setting')) return iconSettings;
  if (str.includes('camera') || str.includes('360') || str.includes('pvm')) return iconCamera360;
  if (str.includes('adb')) return iconAdb;
  if (str.includes('viper')) return iconViper;
  
  return iconDefault;
}
