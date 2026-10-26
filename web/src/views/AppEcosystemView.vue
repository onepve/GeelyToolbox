<template>
  <div class="flex flex-col space-y-4">
    <!-- 顶部车规二级分段胶囊：一页全景掌控 (商城 + 特权安装 + 应用管理) -->
    <SubTabs :tabs="appTabs" v-model="activeTab" />

    <!-- Tab 1: 专车精选商城 -->
    <div v-if="activeTab === 'store'" class="flex flex-col space-y-4">
      <StoreView />
    </div>

    <!-- Tab 2: 特权安装通道 (原生管理器 + 无线快传) -->
    <div v-if="activeTab === 'channels'" class="flex flex-col space-y-4">
      <InstallView initial-tab="channels" />
    </div>

    <!-- Tab 3: 整车应用管理 (高级应用管理 + 专家卡兔子) -->
    <div v-if="activeTab === 'manage'" class="flex flex-col space-y-4">
      <InstallView initial-tab="manage" />
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import SubTabs from '../components/SubTabs.vue';
import StoreView from './StoreView.vue';
import InstallView from './InstallView.vue';

const activeTab = ref('store');
const appTabs = [
  { id: 'store', name: '专车精选商城', tag: '生态应用' },
  { id: 'channels', name: '特权安装通道', tag: '文件/快传' },
  { id: 'manage', name: '整车应用管理', tag: '卸载/维护' }
];
</script>