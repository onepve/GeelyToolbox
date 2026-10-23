<template>
  <div class="flex flex-col space-y-4">
    <!-- 顶部车规二级分段胶囊：一页全景掌控 (专车精选商城 + 原生安装快传) -->
    <SubTabs :tabs="appTabs" v-model="activeTab" />

    <!-- Tab 1: 专车精选商城 (v-if 彻底杜绝幽灵重叠) -->
    <div v-if="activeTab === 'store'" class="flex flex-col space-y-4">
      <StoreView />
    </div>

    <!-- Tab 2: 原生安装快传 (原特权安装，v-if 彻底杜绝幽灵重叠) -->
    <div v-if="activeTab === 'install'" class="flex flex-col space-y-4">
      <InstallView />
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
  { id: 'install', name: '原生安装快传', tag: '本地安装' }
];
</script>