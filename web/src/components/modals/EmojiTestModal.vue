<template>
  <ModalWrapper 
    :show="store.modals.emojiTest" 
    title="🧪 车机 Emoji 兼容性实验室 (实机字体支持实测)" 
    badge="Android 9 字体自检"
    maxWidthClass="max-w-[1520px]"
    @close="closeModal('emojiTest')"
  >
    <div class="flex flex-col space-y-5">
      <!-- 顶部测试指南与拍照说明 -->
      <div class="p-5 rounded-2xl bg-amber-500/15 border-2 border-amber-500/40 flex items-center justify-between">
        <div class="flex items-center space-x-4">
          <span class="text-[32px] leading-none">📸</span>
          <div class="flex flex-col">
            <span class="text-[17px] font-black text-amber-300">
              请拿出手机对准车机屏幕拍摄一张清晰照片
            </span>
            <span class="text-[13.5px] text-amber-200/80 font-bold mt-0.5">
              每个图标均配有独立编号 (#001~#180) 与中文名。凡显示为方块 □ 或缺字黑白轮廓的即为当前车机不支持，AI 将根据照片为你归档可用安全图标库。
            </span>
          </div>
        </div>

        <div class="px-4 py-2 rounded-xl bg-car-card border border-car-border flex items-center space-x-2 shrink-0">
          <span class="text-[13.5px] text-car-sub font-bold">候选总量:</span>
          <span class="text-[18px] font-black text-amber-400 font-mono">{{ emojiList.length }}</span>
        </div>
      </div>

      <!-- 分类筛选大磁贴 (车规 h-[50px]) -->
      <div class="flex items-center space-x-2.5 overflow-x-auto pb-1">
        <button 
          v-for="cat in categories" 
          :key="cat.id"
          @click="currentCategory = cat.id"
          :class="[
            'h-[50px] px-5 rounded-xl border-2 font-black text-[15px] cursor-pointer transition-all shadow-sm shrink-0 flex items-center space-x-2',
            currentCategory === cat.id 
              ? 'bg-amber-500/20 border-amber-500 text-amber-400 ring-2 ring-amber-500/20' 
              : 'bg-car-item border-car-border text-car-sub hover:text-car-text hover:border-car-border-light'
          ]"
        >
          <span>{{ cat.icon }}</span>
          <span>{{ cat.name }}</span>
          <span class="text-[12px] opacity-70 font-mono">({{ getCategoryCount(cat.id) }})</span>
        </button>
      </div>

      <!-- Emoji 网格平铺呈现 (8列舒展布局) -->
      <div class="grid grid-cols-8 gap-3 max-h-[58vh] overflow-y-auto pr-1">
        <div 
          v-for="item in filteredEmojis" 
          :key="item.id"
          class="p-3 rounded-2xl bg-car-item border-2 border-car-border hover:border-car-accent flex flex-col items-center justify-center text-center shadow-md transition-all group select-all"
        >
          <!-- 巨大字号 Emoji (确保拍照清晰可见) -->
          <div class="text-[38px] leading-none my-1 group-hover:scale-110 transition-transform">
            {{ item.emoji }}
          </div>
          <!-- 独立编号 -->
          <div class="text-[11.5px] font-mono text-amber-400 font-black tracking-wider mt-1">
            #{{ item.id }}
          </div>
          <!-- 中文名称 -->
          <div class="text-[13px] font-bold text-car-text truncate max-w-full mt-0.5">
            {{ item.name }}
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="flex items-center justify-between w-full">
        <span class="text-[14px] text-car-sub font-bold">
          提示：实测完毕后，该测试入口与测试代码将在下个版本完整下线。
        </span>
        <button 
          @click="closeModal('emojiTest')"
          class="min-h-[56px] px-8 bg-car-card border-2 border-car-border rounded-xl text-car-sub hover:text-car-text font-black text-[17.5px] cursor-pointer hover:border-car-border-light shadow-sm"
        >
          关闭实验室
        </button>
      </div>
    </template>
  </ModalWrapper>
</template>

<script setup>
import { ref, computed } from 'vue';
import ModalWrapper from './ModalWrapper.vue';
import { store, closeModal } from '../../store';

const currentCategory = ref('all');

const categories = [
  { id: 'all', name: '全部候选', icon: '📋' },
  { id: 'vehicle', name: '车辆交通', icon: '🚗' },
  { id: 'cockpit', name: '座舱硬件', icon: '🚪' },
  { id: 'media', name: '声音媒体', icon: '🎵' },
  { id: 'nav', name: '导航定位', icon: '🧭' },
  { id: 'status', name: '状态指示', icon: '✅' },
  { id: 'gesture', name: '手势方向', icon: '👉' },
  { id: 'tools', name: '界面工具', icon: '📱' },
  { id: 'weather', name: '天气氛围', icon: '☀️' }
];

const emojiList = [
  // 1. 车辆与交通出行 (001 - 024)
  { id: '001', cat: 'vehicle', emoji: '🚗', name: '小汽车' },
  { id: '002', cat: 'vehicle', emoji: '🚘', name: '迎面车' },
  { id: '003', cat: 'vehicle', emoji: '🏎️', name: '激擎赛车' },
  { id: '004', cat: 'vehicle', emoji: '🚙', name: 'SUV越野' },
  { id: '005', cat: 'vehicle', emoji: '🛞', name: '汽车轮胎' },
  { id: '006', cat: 'vehicle', emoji: '⛽', name: '加油站' },
  { id: '007', cat: 'vehicle', emoji: '🔋', name: '蓄电池' },
  { id: '008', cat: 'vehicle', emoji: '🔌', name: '电源插头' },
  { id: '009', cat: 'vehicle', emoji: '⚡', name: '高压闪电' },
  { id: '010', cat: 'vehicle', emoji: '🛣️', name: '高速公路' },
  { id: '011', cat: 'vehicle', emoji: '🏁', name: '终点赛旗' },
  { id: '012', cat: 'vehicle', emoji: '🚦', name: '垂直路灯' },
  { id: '013', cat: 'vehicle', emoji: '🚥', name: '横向路灯' },
  { id: '014', cat: 'vehicle', emoji: '🚨', name: '警报车灯' },
  { id: '015', cat: 'vehicle', emoji: '🅿️', name: 'P挡车位' },
  { id: '016', cat: 'vehicle', emoji: '🛑', name: '停止路牌' },
  { id: '017', cat: 'vehicle', emoji: '🎛️', name: '旋钮开关' },
  { id: '018', cat: 'vehicle', emoji: '🚲', name: '自行车' },
  { id: '019', cat: 'vehicle', emoji: '🛵', name: '轻便摩托' },
  { id: '020', cat: 'vehicle', emoji: '🚌', name: '公共巴士' },
  { id: '021', cat: 'vehicle', emoji: '🚚', name: '货运卡车' },
  { id: '022', cat: 'vehicle', emoji: '🚜', name: '重型农车' },
  { id: '023', cat: 'vehicle', emoji: '🚁', name: '空中直升' },
  { id: '024', cat: 'vehicle', emoji: '🛸', name: '智能飞碟' },

  // 2. 座舱硬件与物理部件 (025 - 048)
  { id: '025', cat: 'cockpit', emoji: '🚪', name: '车身车门' },
  { id: '026', cat: 'cockpit', emoji: '🪟', name: '车窗玻璃' },
  { id: '027', cat: 'cockpit', emoji: '💺', name: '驾驶座椅' },
  { id: '028', cat: 'cockpit', emoji: '🔊', name: '大喇叭声' },
  { id: '029', cat: 'cockpit', emoji: '🔉', name: '中等音量' },
  { id: '030', cat: 'cockpit', emoji: '🔈', name: '低音静音' },
  { id: '031', cat: 'cockpit', emoji: '🔇', name: '完全静音' },
  { id: '032', cat: 'cockpit', emoji: '🔔', name: '提醒铃铛' },
  { id: '033', cat: 'cockpit', emoji: '🔕', name: '免打扰铃' },
  { id: '034', cat: 'cockpit', emoji: '💡', name: '汽车大灯' },
  { id: '035', cat: 'cockpit', emoji: '🔦', name: '应急近光' },
  { id: '036', cat: 'cockpit', emoji: '🔑', name: '智能钥匙' },
  { id: '037', cat: 'cockpit', emoji: '🗝️', name: '备用机械' },
  { id: '038', cat: 'cockpit', emoji: '🧲', name: '磁力吸附' },
  { id: '039', cat: 'cockpit', emoji: '🛡️', name: '安全护盾' },
  { id: '040', cat: 'cockpit', emoji: '⚙️', name: '齿轮设置' },
  { id: '041', cat: 'cockpit', emoji: '🔧', name: '维保扳手' },
  { id: '042', cat: 'cockpit', emoji: '🔨', name: '调试铁锤' },
  { id: '043', cat: 'cockpit', emoji: '🪛', name: '十字螺丝' },
  { id: '044', cat: 'cockpit', emoji: '🧰', name: '五金工具' },
  { id: '045', cat: 'cockpit', emoji: '🔒', name: '安全锁止' },
  { id: '046', cat: 'cockpit', emoji: '🔓', name: '解除锁止' },
  { id: '047', cat: 'cockpit', emoji: '🔐', name: '密码密匙' },
  { id: '048', cat: 'cockpit', emoji: '🔏', name: '签名认证' },

  // 3. 声音与媒体音效 (049 - 072)
  { id: '049', cat: 'media', emoji: '🎵', name: '单音符' },
  { id: '050', cat: 'media', emoji: '🎶', name: '双音音乐' },
  { id: '051', cat: 'media', emoji: '📻', name: '车载电台' },
  { id: '052', cat: 'media', emoji: '🎙️', name: '语音识别' },
  { id: '053', cat: 'media', emoji: '🎤', name: '手持麦克' },
  { id: '054', cat: 'media', emoji: '🎧', name: '高保真耳' },
  { id: '055', cat: 'media', emoji: '💿', name: '无损CD片' },
  { id: '056', cat: 'media', emoji: '📼', name: '模拟磁带' },
  { id: '057', cat: 'media', emoji: '📢', name: '通知喇叭' },
  { id: '058', cat: 'media', emoji: '📣', name: '广播扩音' },
  { id: '059', cat: 'media', emoji: '⏯️', name: '播放暂停' },
  { id: '060', cat: 'media', emoji: '⏸️', name: '纯暂停键' },
  { id: '061', cat: 'media', emoji: '⏹️', name: '完全停止' },
  { id: '062', cat: 'media', emoji: '⏺️', name: '录制声音' },
  { id: '063', cat: 'media', emoji: '⏭️', name: '下一首曲' },
  { id: '064', cat: 'media', emoji: '⏮️', name: '上一首曲' },
  { id: '065', cat: 'media', emoji: '⏩', name: '快速前进' },
  { id: '066', cat: 'media', emoji: '⏪', name: '快速倒退' },
  { id: '067', cat: 'media', emoji: '🔀', name: '随机播放' },
  { id: '068', cat: 'media', emoji: '🔁', name: '列表循环' },
  { id: '069', cat: 'media', emoji: '🔂', name: '单曲循环' },
  { id: '070', cat: 'media', emoji: '🗣️', name: '语音发声' },
  { id: '071', cat: 'media', emoji: '🎼', name: '乐谱总谱' },
  { id: '072', cat: 'media', emoji: '🎻', name: '弦乐管弦' },

  // 4. 导航与地图定位 (073 - 096)
  { id: '073', cat: 'nav', emoji: '🧭', name: '导航罗盘' },
  { id: '074', cat: 'nav', emoji: '🗺️', name: '高德地图' },
  { id: '075', cat: 'nav', emoji: '📍', name: '定位大头' },
  { id: '076', cat: 'nav', emoji: '🚩', name: '终点红旗' },
  { id: '077', cat: 'nav', emoji: '🛰️', name: '北斗GPS' },
  { id: '078', cat: 'nav', emoji: '🚀', name: '极速启程' },
  { id: '079', cat: 'nav', emoji: '🎯', name: '导航目标' },
  { id: '080', cat: 'nav', emoji: '📌', name: '图钉标记' },
  { id: '081', cat: 'nav', emoji: '🏔️', name: '山路自驾' },
  { id: '082', cat: 'nav', emoji: '🏖️', name: '海滨路线' },
  { id: '083', cat: 'nav', emoji: '🏙️', name: '城市道路' },
  { id: '084', cat: 'nav', emoji: '🌉', name: '跨海大桥' },
  { id: '085', cat: 'nav', emoji: '🚇', name: '穿越隧道' },
  { id: '086', cat: 'nav', emoji: '⛳', name: '途经站点' },
  { id: '087', cat: 'nav', emoji: '⚓', name: '停靠锚点' },
  { id: '088', cat: 'nav', emoji: '🛬', name: '到达终点' },
  { id: '089', cat: 'nav', emoji: '🛫', name: '准备出发' },
  { id: '090', cat: 'nav', emoji: '🚏', name: '停靠车站' },
  { id: '091', cat: 'nav', emoji: '🧭', name: '航向偏转' },
  { id: '092', cat: 'nav', emoji: '🗺️', name: '巡航视界' },
  { id: '093', cat: 'nav', emoji: '⛰️', name: '险峻盘山' },
  { id: '094', cat: 'nav', emoji: '🏕️', name: '露营车宿' },
  { id: '095', cat: 'nav', emoji: '🏙️', name: '高楼盲区' },
  { id: '096', cat: 'nav', emoji: '🏎️', name: '超速监控' },

  // 5. 状态与开关指示 (097 - 120)
  { id: '097', cat: 'status', emoji: '✅', name: '运行正常' },
  { id: '098', cat: 'status', emoji: '❌', name: '错误关闭' },
  { id: '099', cat: 'status', emoji: '⭕', name: '空心就绪' },
  { id: '100', cat: 'status', emoji: '⚠️', name: '高危警示' },
  { id: '101', cat: 'status', emoji: '⛔', name: '禁止驶入' },
  { id: '102', cat: 'status', emoji: '🚫', name: '严禁违章' },
  { id: '103', cat: 'status', emoji: '🔴', name: '红色警报' },
  { id: '104', cat: 'status', emoji: '🟢', name: '绿色常通' },
  { id: '105', cat: 'status', emoji: '🟡', name: '黄色待命' },
  { id: '106', cat: 'status', emoji: '🔵', name: '蓝色巡航' },
  { id: '107', cat: 'status', emoji: '🟣', name: '紫色尊贵' },
  { id: '108', cat: 'status', emoji: '⚪', name: '白色纯净' },
  { id: '109', cat: 'status', emoji: '⚫', name: '黑色曜石' },
  { id: '110', cat: 'status', emoji: '🔘', name: '单选光圈' },
  { id: '111', cat: 'status', emoji: '💤', name: '息屏待机' },
  { id: '112', cat: 'status', emoji: '⏰', name: '闹钟定时' },
  { id: '113', cat: 'status', emoji: '⏱️', name: '防抖秒表' },
  { id: '114', cat: 'status', emoji: '⏳', name: '流转沙漏' },
  { id: '115', cat: 'status', emoji: '⌛', name: '就绪沙漏' },
  { id: '116', cat: 'status', emoji: '📊', name: '柱状分析' },
  { id: '117', cat: 'status', emoji: '📈', name: '上升曲线' },
  { id: '118', cat: 'status', emoji: '📉', name: '下降曲线' },
  { id: '119', cat: 'status', emoji: '💡', name: '灵光开关' },
  { id: '120', cat: 'status', emoji: '🔋', name: '电量满格' },

  // 6. 手势、方向与按键 (121 - 144)
  { id: '121', cat: 'gesture', emoji: '👉', name: '食指向右' },
  { id: '122', cat: 'gesture', emoji: '👈', name: '食指向左' },
  { id: '123', cat: 'gesture', emoji: '👆', name: '方向上拨' },
  { id: '124', cat: 'gesture', emoji: '👇', name: '方向下拨' },
  { id: '125', cat: 'gesture', emoji: '☝️', name: '食指按压' },
  { id: '126', cat: 'gesture', emoji: '✌️', name: '胜利双指' },
  { id: '127', cat: 'gesture', emoji: '🖐️', name: '五指张开' },
  { id: '128', cat: 'gesture', emoji: '✋', name: '掌心刹停' },
  { id: '129', cat: 'gesture', emoji: '👊', name: '实体拳击' },
  { id: '130', cat: 'gesture', emoji: '👍', name: '推荐好评' },
  { id: '131', cat: 'gesture', emoji: '👎', name: '差评拉黑' },
  { id: '132', cat: 'gesture', emoji: '👏', name: '鼓掌喝彩' },
  { id: '133', cat: 'gesture', emoji: '🤝', name: '握手联动' },
  { id: '134', cat: 'gesture', emoji: '🔍', name: '放大搜索' },
  { id: '135', cat: 'gesture', emoji: '🔎', name: '精确探查' },
  { id: '136', cat: 'gesture', emoji: '🔄', name: '顺时刷新' },
  { id: '137', cat: 'gesture', emoji: '🔃', name: '逆时同步' },
  { id: '138', cat: 'gesture', emoji: '➡️', name: '向右前进' },
  { id: '139', cat: 'gesture', emoji: '⬅️', name: '向左返回' },
  { id: '140', cat: 'gesture', emoji: '⬆️', name: '向上提升' },
  { id: '141', cat: 'gesture', emoji: '⬇️', name: '向下衰减' },
  { id: '142', cat: 'gesture', emoji: '↗️', name: '斜上指引' },
  { id: '143', cat: 'gesture', emoji: '↔️', name: '水平双向' },
  { id: '144', cat: 'gesture', emoji: '↕️', name: '垂直双向' },

  // 7. 界面、系统与工具 (145 - 168)
  { id: '145', cat: 'tools', emoji: '📱', name: '智能手机' },
  { id: '146', cat: 'tools', emoji: '💻', name: '笔记本ADB' },
  { id: '147', cat: 'tools', emoji: '🖥️', name: '双联大屏' },
  { id: '148', cat: 'tools', emoji: '📺', name: '视频投屏' },
  { id: '149', cat: 'tools', emoji: '📷', name: '360相机' },
  { id: '150', cat: 'tools', emoji: '📹', name: '行车记录' },
  { id: '151', cat: 'tools', emoji: '🗑️', name: '垃圾清理' },
  { id: '152', cat: 'tools', emoji: '🧹', name: '扫帚除尘' },
  { id: '153', cat: 'tools', emoji: '📦', name: '离线APK' },
  { id: '154', cat: 'tools', emoji: '📎', name: '附件参数' },
  { id: '155', cat: 'tools', emoji: '📝', name: '计划备忘' },
  { id: '156', cat: 'tools', emoji: '📋', name: '诊断板' },
  { id: '157', cat: 'tools', emoji: '📁', name: '系统目录' },
  { id: '158', cat: 'tools', emoji: '📂', name: '展开目录' },
  { id: '159', cat: 'tools', emoji: '📄', name: '文本文档' },
  { id: '160', cat: 'tools', emoji: '📃', name: '配置脚本' },
  { id: '161', cat: 'tools', emoji: '🏷️', name: '版本标签' },
  { id: '162', cat: 'tools', emoji: '🔮', name: '水晶探针' },
  { id: '163', cat: 'tools', emoji: '🕹️', name: '遥感手柄' },
  { id: '164', cat: 'tools', emoji: '🪄', name: '魔法指令' },
  { id: '165', cat: 'tools', emoji: '🪟', name: '悬浮胶囊' },
  { id: '166', cat: 'tools', emoji: '🧲', name: '吸附靠边' },
  { id: '167', cat: 'tools', emoji: '📡', name: '无线微波' },
  { id: '168', cat: 'tools', emoji: '💾', name: '备份软盘' },

  // 8. 天气、环境与氛围 (169 - 188)
  { id: '169', cat: 'weather', emoji: '☀️', name: '烈日晴空' },
  { id: '170', cat: 'weather', emoji: '🌤️', name: '多云放晴' },
  { id: '171', cat: 'weather', emoji: '⛅', name: '阴云遮日' },
  { id: '172', cat: 'weather', emoji: '☁️', name: '漫天阴天' },
  { id: '173', cat: 'weather', emoji: '🌧️', name: '细雨连绵' },
  { id: '174', cat: 'weather', emoji: '⛈️', name: '雷暴强雨' },
  { id: '175', cat: 'weather', emoji: '❄️', name: '冰霜冷风' },
  { id: '176', cat: 'weather', emoji: '🌙', name: '夜幕弯月' },
  { id: '177', cat: 'weather', emoji: '🌡️', name: '车外温度' },
  { id: '178', cat: 'weather', emoji: '🔥', name: '激擎火热' },
  { id: '179', cat: 'weather', emoji: '💥', name: '震撼爆发' },
  { id: '180', cat: 'weather', emoji: '✨', name: '流光闪烁' },
  { id: '181', cat: 'weather', emoji: '🌟', name: '金色星芒' },
  { id: '182', cat: 'weather', emoji: '⭐', name: '收藏星标' },
  { id: '183', cat: 'weather', emoji: '👑', name: '尊享特权' },
  { id: '184', cat: 'weather', emoji: '☕', name: '疲劳咖啡' },
  { id: '185', cat: 'weather', emoji: '🥤', name: '清凉冷饮' },
  { id: '186', cat: 'weather', emoji: '🏆', name: '荣誉奖杯' },
  { id: '187', cat: 'weather', emoji: '💖', name: '赞赏爱心' },
  { id: '188', cat: 'weather', emoji: '🎨', name: '座舱主题' }
];

const filteredEmojis = computed(() => {
  if (currentCategory.value === 'all') {
    return emojiList;
  }
  return emojiList.filter(item => item.cat === currentCategory.value);
});

function getCategoryCount(catId) {
  if (catId === 'all') return emojiList.length;
  return emojiList.filter(item => item.cat === catId).length;
}
</script>
