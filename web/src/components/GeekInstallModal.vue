<template>
  <div v-if="store.modals.geekInstall" class="fixed top-0 left-0 right-0 bottom-0 z-50 flex items-center justify-center p-2 sm:p-4 backdrop-blur-md animate-fade-in" style="background: rgba(0, 0, 0, 0.8);">
    <div class="relative w-[96vw] max-w-6xl bg-car-card border-2 border-car-accent rounded-3xl p-6 shadow-2xl flex flex-col space-y-5 text-car-text max-h-[95vh] overflow-y-auto">
      <!-- 顶部 Header -->
      <div class="flex items-center justify-between pb-4 border-b border-car-border">
        <div class="flex items-center space-x-3">
          <span class="w-3.5 h-3.5 rounded-full bg-car-accent shadow-[0_0_10px_var(--accent-gold)] flex-shrink-0"></span>
          <span class="text-[22px] font-black tracking-wide whitespace-nowrap">缤越 COOL 首次配置与安全向导</span>
          <span class="px-3 py-1 rounded-full text-[12.5px] font-black bg-car-item border border-car-accent text-car-accent whitespace-nowrap flex-shrink-0">
            SX11-A3 / E02 / IHU516G 专属
          </span>
        </div>
        <div class="flex items-center space-x-3 flex-shrink-0">
          <span class="text-[13px] text-car-sub font-bold whitespace-nowrap">开屏安全与特权就绪检测</span>
          <button 
            v-if="canDismiss"
            @click="closeModal" 
            class="w-9 h-9 rounded-xl bg-car-item border border-car-border flex items-center justify-center hover:bg-car-card text-car-sub hover:text-car-text text-xl font-bold cursor-pointer transition-colors flex-shrink-0"
          >
            ×
          </button>
        </div>
      </div>

      <!-- 说明与引导主体：若原厂商店已安全冻结，则自动隐藏第二步，仅呈现第一步车规底线 -->
      <div class="grid gap-5" :class="isStoreFrozen ? 'grid-cols-1 max-w-2xl mx-auto w-full' : 'grid-cols-1 md:grid-cols-2'">
        <!-- Step 1: 缤越实测免责声明 -->
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col justify-between space-y-4">
          <div class="flex flex-col space-y-2">
            <div class="flex items-center justify-between">
              <div class="flex items-center space-x-2">
                <span class="px-2.5 py-0.5 rounded-md bg-car-card border border-car-accent text-car-accent font-black text-[13px] whitespace-nowrap flex-shrink-0">
                  第一步 (Step 1)
                </span>
                <span class="text-[17.5px] font-black text-car-text whitespace-nowrap">车规安全与免责底线</span>
              </div>
              <StatusDot size="sm" :color="disclaimerAgreed ? 'ok' : 'warn'" />
            </div>
            <div class="text-[14px] text-car-sub font-bold leading-relaxed">
              本工具专为吉利缤越 COOL (E02 / IHU516G 双联屏) 深度定制，经实车全功能路试。安装第三方应用需调整车机系统安全防护，请确保在驻车安全状态下操作，严禁在行驶途中调试车机。
            </div>
            <div class="p-3 bg-car-card rounded-xl border border-car-border text-[12.5px] text-car-sub font-mono">
              安全规范：严禁使用 ADB pm install 强行静默安装以免闪退；统一遵循原生特权文件通道。
            </div>
          </div>

          <div class="pt-3 border-t border-car-border flex items-center justify-between">
            <span class="text-[13px] font-bold text-car-sub whitespace-nowrap">
              {{ disclaimerAgreed ? '已同意免责条款' : '须确认安全底线' }}
            </span>
            <button 
              @click="agreeDisclaimer"
              class="h-[52px] px-5 rounded-xl border-2 font-black text-[15px] cursor-pointer transition-all shadow-sm whitespace-nowrap flex-shrink-0"
              :class="disclaimerAgreed ? 'bg-car-card border-car-border text-car-sub' : 'bg-car-item border-car-accent text-car-accent hover:bg-car-card'"
            >
              {{ disclaimerAgreed ? '已同意条款' : '同意并继续' }}
            </button>
          </div>
        </div>

        <!-- Step 2: 商店冻结 / 提权通道就绪（仅在未冻结时出现） -->
        <div v-if="!isStoreFrozen" class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col justify-between space-y-4">
          <div class="flex flex-col space-y-2">
            <div class="flex items-center justify-between">
              <div class="flex items-center space-x-2">
                <span class="px-2.5 py-0.5 rounded-md bg-car-card border border-car-accent text-car-accent font-black text-[13px] whitespace-nowrap flex-shrink-0">
                  第二步 (Step 2)
                </span>
                <span class="text-[17.5px] font-black text-car-text whitespace-nowrap">原厂应用商店防护配置</span>
              </div>
              <span class="px-2 py-0.5 rounded-full text-[11.5px] font-extrabold border whitespace-nowrap flex-shrink-0" :class="isStoreFrozen ? 'bg-emerald-500/15 border-emerald-500/40 text-emerald-400' : 'bg-amber-500/15 border-amber-500/40 text-amber-400'">
                {{ isStoreFrozen ? '已冻结(白名单已锁)' : '未冻结(运行中)' }}
              </span>
            </div>
            <div class="text-[14px] text-car-sub font-bold leading-relaxed">
              原厂应用商店会在后台扫描并拦截第三方安装包或强推更新。推荐一键冻结商店以锁定白名单；也可选择跳过直接使用文件通道。
            </div>
            <div class="grid grid-cols-2 gap-2.5 pt-1">
              <div class="p-2.5 bg-car-card rounded-xl border border-car-border flex flex-col justify-between">
                <span class="text-[12.5px] font-black text-car-text whitespace-nowrap">通道 A (推荐)</span>
                <span class="text-[11.5px] text-car-sub font-bold mt-1">一键冻结原厂商店，阻断拦截</span>
              </div>
              <div class="p-2.5 bg-car-card rounded-xl border border-car-border flex flex-col justify-between">
                <span class="text-[12.5px] font-black text-car-text whitespace-nowrap">通道 B (跳过)</span>
                <span class="text-[11.5px] text-car-sub font-bold mt-1">不冻结商店，走原生文件管理</span>
              </div>
            </div>
          </div>

          <div class="pt-3 border-t border-car-border flex items-center justify-between">
            <span class="text-[13px] font-bold text-car-sub whitespace-nowrap">
              {{ step2Completed ? '通道配置就绪' : '请选择通道' }}
            </span>
            <div class="flex items-center space-x-2 flex-shrink-0">
              <button 
                @click="freezeStore"
                :disabled="!disclaimerAgreed || isStoreFrozen"
                class="h-[52px] px-4 rounded-xl border-2 font-black text-[14.5px] cursor-pointer transition-all shadow-sm whitespace-nowrap flex-shrink-0"
                :class="!disclaimerAgreed ? 'opacity-50 cursor-not-allowed bg-car-card border-car-border text-car-sub' : isStoreFrozen ? 'bg-car-card border-car-border text-car-sub' : 'bg-car-item border-car-accent text-car-accent hover:bg-car-card'"
              >
                {{ isStoreFrozen ? '已安全冻结' : '通道 A: 安全冻结' }}
              </button>
              <button 
                @click="skipStore"
                :disabled="!disclaimerAgreed"
                class="h-[52px] px-3.5 rounded-xl bg-car-card border border-car-border text-car-text hover:border-car-border-light font-black text-[14.5px] cursor-pointer transition-all shadow-sm whitespace-nowrap flex-shrink-0"
                :class="!disclaimerAgreed ? 'opacity-50 cursor-not-allowed' : ''"
              >
                通道 B: 跳过
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 底部完成与进入按钮 -->
      <div class="pt-4 border-t border-car-border flex items-center justify-between">
        <div class="flex items-center space-x-2 text-[13px] text-car-sub font-bold">
          <span>完成初始化向导后，系统将持久化配置并在后续启动时保持静默。</span>
        </div>
        <button 
          @click="finishGuide"
          :disabled="!canFinish"
          class="h-[56px] px-8 rounded-xl font-black text-[16px] cursor-pointer transition-all shadow-lg flex items-center space-x-2"
          :class="canFinish ? 'bg-car-accent text-black hover:opacity-90 active:scale-95' : 'bg-car-item border border-car-border text-car-sub opacity-50 cursor-not-allowed'"
        >
          <span>进入车机工具箱</span>
          <span>→</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import StatusDot from './StatusDot.vue'
import { isAppstoreFrozen, openAppstoreFreezeFlow } from '../utils/appstoreFreeze'
import { store, openModal, closeModal as storeCloseModal } from '../store'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'complete'])

const visible = ref(false)
const disclaimerAgreed = ref(false)
const step2Completed = ref(false)

const isStoreFrozen = computed(() => {
  return isAppstoreFrozen()
})

const canDismiss = computed(() => {
  return localStorage.getItem('geek_install_guide_completed') === 'true'
})

const canFinish = computed(() => {
  return disclaimerAgreed.value && (step2Completed.value || isStoreFrozen.value)
})

const agreeDisclaimer = () => {
  disclaimerAgreed.value = true
  localStorage.setItem('geek_install_disclaimer_agreed', 'true')
}

const freezeStore = () => {
  openAppstoreFreezeFlow(() => {
    step2Completed.value = true
  })
}

const skipStore = () => {
  step2Completed.value = true
}

const finishGuide = () => {
  localStorage.setItem('geek_install_guide_completed', 'true')
  localStorage.setItem('geek_install_disclaimer_agreed', 'true')
  closeModal('geekInstall')
  emit('update:modelValue', false)
  emit('complete')
}

const closeModal = () => {
  storeCloseModal('geekInstall')
  emit('update:modelValue', false)
}

const open = () => {
  disclaimerAgreed.value = localStorage.getItem('geek_install_disclaimer_agreed') === 'true'
  if (isAppstoreFrozen()) {
    step2Completed.value = true
  }
  openModal('geekInstall')
  emit('update:modelValue', true)
}

const handleWelcomeCompleted = () => {
  const completed = localStorage.getItem('geek_install_guide_completed') === 'true'
  if (!completed) {
    // 延迟 200ms 等待上一个弹窗动画淡出，视觉体验更丝滑
    setTimeout(() => {
      open()
    }, 200)
  }
}

onMounted(() => {
  window.addEventListener('welcome-donate-completed', handleWelcomeCompleted)
  window.addEventListener('open-geek-install-guide', open)
  const completed = localStorage.getItem('geek_install_guide_completed') === 'true'
  const welcomeShown = localStorage.getItem('has_shown_welcome_donate') === 'true'
  // 若首次赞赏弹窗尚未展示，向导主动避让，等待赞赏阅读完成事件后再串行拉起
  if (!completed) {
    if (welcomeShown) {
      open()
    }
  } else {
    visible.value = props.modelValue || false
  }
})

onUnmounted(() => {
  window.removeEventListener('welcome-donate-completed', handleWelcomeCompleted)
  window.removeEventListener('open-geek-install-guide', open)
})

defineExpose({
  open,
  close: closeModal
})
</script>
