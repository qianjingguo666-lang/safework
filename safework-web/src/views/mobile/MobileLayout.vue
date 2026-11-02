<template>
  <div class="m-app">
    <header class="m-header">
      <el-icon v-if="route.meta.back" class="m-back" @click="back"><ArrowLeft /></el-icon>
      <span class="m-title">{{ route.meta.title }}</span>
    </header>
    <main class="m-main" :class="{ 'no-tab': !route.meta.tab }">
      <router-view v-slot="{ Component }">
        <component :is="Component" :key="route.fullPath" @unread="loadUnread" />
      </router-view>
    </main>
    <nav v-if="route.meta.tab" class="m-tabbar">
      <router-link v-for="t in tabs" :key="t.path" :to="t.path" class="m-tab" :class="{ on: active(t) }">
        <el-badge :value="t.badge" :hidden="!t.badge" :max="99">
          <el-icon><component :is="t.icon" /></el-icon>
        </el-badge>
        <span>{{ t.label }}</span>
      </router-link>
    </nav>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { unreadMessages } from '@/api'
import { useUserStore } from '@/stores/user'

/** 手机端外壳：顶部标题栏 + 底部 5 个标签。页面本身只负责内容 */
const route = useRoute()
const router = useRouter()
const store = useUserStore()
const unread = ref(0)

const tabs = computed(() => [
  { path: '/m', label: '首页', icon: 'HomeFilled' },
  store.hasPerm('ticket:list') && { path: '/m/tickets', label: '作业', icon: 'Tickets' },
  store.hasPerm('safety:hazard:list') && { path: '/m/hazards', label: '隐患', icon: 'WarningFilled' },
  { path: '/m/messages', label: '消息', icon: 'Bell', badge: unread.value },
  { path: '/m/me', label: '我的', icon: 'User' }
].filter(Boolean))
const active = (t) => (t.path === '/m' ? route.path === '/m' : route.path.startsWith(t.path))

async function loadUnread() {
  try {
    unread.value = (await unreadMessages()).total
  } catch {
    /* 角标拿不到不影响页面 */
  }
}
watch(() => route.path, loadUnread, { immediate: true })

function back() {
  // 从消息、扫码直接打开的详情页没有上一页，返回首页
  if (window.history.state?.back) router.back()
  else router.replace(route.meta.backTo || '/m')
}
</script>

<style>
/* 手机端公共样式（不加 scoped，各页面直接用这些类名） */
.m-app { min-height: 100%; background: #f3f5f8; font-size: 15px; -webkit-tap-highlight-color: transparent; }
.m-header { position: sticky; top: 0; z-index: 10; height: 46px; display: flex; align-items: center; justify-content: center; background: #1f6feb; color: #fff; padding-top: env(safe-area-inset-top); }
.m-title { font-size: 17px; font-weight: 600; }
.m-back { position: absolute; left: 0; width: 46px; height: 46px; font-size: 20px; cursor: pointer; }
.m-main { padding: 12px 12px calc(70px + env(safe-area-inset-bottom)); max-width: 640px; margin: 0 auto; }
.m-main.no-tab { padding-bottom: calc(90px + env(safe-area-inset-bottom)); }
.m-tabbar { position: fixed; left: 0; right: 0; bottom: 0; z-index: 10; display: flex; background: #fff; border-top: 1px solid #e5e7eb; padding-bottom: env(safe-area-inset-bottom); }
.m-tab { flex: 1; height: 54px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 2px; color: #8a8f99; font-size: 11px; text-decoration: none; }
.m-tab .el-icon { font-size: 22px; }
.m-tab.on { color: #1f6feb; }
.m-tab .el-badge__content.is-fixed { top: 4px; }

.m-card { background: #fff; border-radius: 10px; padding: 14px; margin-bottom: 10px; }
.m-card h3 { margin: 0 0 10px; font-size: 15px; }
.m-row { display: flex; justify-content: space-between; gap: 12px; padding: 6px 0; border-bottom: 1px solid #f2f3f5; }
.m-row:last-child { border-bottom: 0; }
.m-row > span:first-child { color: #8a8f99; flex: none; }
.m-row > :last-child { text-align: right; word-break: break-all; }
.m-muted { color: #8a8f99; font-size: 13px; }
.m-item { display: block; background: #fff; border-radius: 10px; padding: 12px 14px; margin-bottom: 10px; color: inherit; text-decoration: none; }
.m-item:active { background: #f7f8fa; }
.m-item-head { display: flex; align-items: center; gap: 6px; margin-bottom: 4px; }
.m-item-title { flex: 1; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.m-tabs { display: flex; gap: 8px; margin-bottom: 12px; overflow-x: auto; }
.m-tabs button { flex: none; border: 0; background: #fff; color: #555; padding: 7px 14px; border-radius: 16px; font-size: 14px; }
.m-tabs button.on { background: #1f6feb; color: #fff; }
.m-tabs button b { margin-left: 4px; }
.m-empty { text-align: center; color: #8a8f99; padding: 40px 0; }
.m-more { display: block; width: 100%; border: 0; background: none; color: #1f6feb; padding: 10px; font-size: 14px; }
/* 详情页底部固定的操作按钮 */
.m-actions { position: fixed; left: 0; right: 0; bottom: 0; z-index: 10; display: flex; gap: 8px; padding: 10px 12px calc(10px + env(safe-area-inset-bottom)); background: #fff; box-shadow: 0 -2px 10px rgba(0, 0, 0, .06); }
.m-actions .el-button { flex: 1; height: 44px; font-size: 16px; margin: 0; }
.m-sheet .el-drawer__body { padding: 0 16px 16px; }
.m-sheet .el-form-item { margin-bottom: 14px; }
.m-sheet-btn { width: 100%; height: 44px; font-size: 16px; }
.m-timeline { padding-left: 2px; }
.m-log { position: relative; padding: 0 0 12px 16px; border-left: 2px solid #e5e7eb; margin-left: 4px; }
.m-log:last-child { border-left-color: transparent; }
.m-log::before { content: ''; position: absolute; left: -6px; top: 3px; width: 10px; height: 10px; border-radius: 50%; background: #1f6feb; }
.m-log .comment { margin-top: 4px; padding: 6px 8px; background: #f6f8fb; border-radius: 4px; color: #555; font-size: 14px; }
.m-log img { display: block; max-width: 140px; max-height: 50px; margin-top: 4px; background: #fff; border: 1px solid #eee; border-radius: 4px; }
</style>
