<script setup lang="ts">
import { ref } from "vue";
import { useRoute } from "vue-router";
import {
  Leaf,
  LayoutDashboard,
  History,
  ChartNoAxesCombined,
  MapPin,
  Database,
  PanelLeftClose,
  ArrowUpRight,
} from "lucide-vue-next";
const route = useRoute();
const open = ref(false);
const nav = [
  {
    path: "overview",
    label: "环境总览",
    icon: LayoutDashboard,
    en: "OVERVIEW",
  },
  { path: "history", label: "历史数据", icon: History, en: "HISTORY" },
  {
    path: "statistics",
    label: "年度统计",
    icon: ChartNoAxesCombined,
    en: "ANALYTICS",
  },
  { path: "stations", label: "监测站点", icon: MapPin, en: "STATIONS" },
  { path: "sources", label: "数据与接入", icon: Database, en: "DATA SOURCES" },
];
</script>
<template>
  <div class="app-shell">
    <button class="mobile-menu" aria-label="展开导航" @click="open = !open">
      <PanelLeftClose :size="22" />
    </button>
    <aside class="sidebar" :class="{ 'is-open': open }">
      <a class="brand" href="#/overview"
        ><span class="brand-mark"><Leaf :size="25" /></span
        ><span
          >青禾<span class="brand-sub">GREENHOUSE OBSERVATORY</span></span
        ></a
      >
      <div class="nav-caption">工作空间 <span>WORKSPACE</span></div>
      <nav aria-label="主导航">
        <RouterLink
          v-for="item in nav"
          :key="item.path"
          :to="`/${item.path}`"
          :class="{ active: route.path === `/${item.path}` }"
          @click="open = false"
          ><component :is="item.icon" :size="19" /><span>{{ item.label }}</span
          ><span class="nav-arrow">›</span></RouterLink
        >
      </nav>
      <div class="sidebar-footer">
        <div class="mini-leaf"><Leaf :size="30" /></div>
        <p>让每一份环境数据<br />都有迹可循。</p>
        <a
          href="https://github.com/SmartGreenHouses/NUTRICONTROL_datasets"
          target="_blank"
          rel="noopener noreferrer"
          >NUTRICONTROL 开放数据 <ArrowUpRight :size="14" /></a
        ><small>SPRING BOOT × VUE 3</small>
      </div>
    </aside>
    <div class="content-shell">
      <header class="topbar">
        <div class="breadcrumbs">智慧农业 <span>/</span> 气象站可视化系统</div>
        <div class="topbar-right">
          <span class="status-dot"></span> 历史数据工作空间
          <span class="avatar">QH</span>
        </div>
      </header>
      <RouterView />
    </div>
  </div>
</template>
