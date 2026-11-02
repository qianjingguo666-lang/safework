<template>
  <div>
    <div class="hello">
      <div>
        <div class="name">{{ store.nickname }}，{{ greeting }}</div>
        <div class="tenant">{{ store.tenantName }} · {{ roleText }}</div>
      </div>
      <el-icon class="refresh" :class="{ spin: loading }" @click="load"><Refresh /></el-icon>
    </div>

    <button v-if="store.hasPerm('safety:hazard:report')" class="report" @click="router.push('/m/report')">
      <el-icon><Camera /></el-icon>
      <div><b>随手拍 · 上报隐患</b><span>拍照 + 一句话，AI 帮你填表</span></div>
    </button>

    <div class="m-card">
      <h3>我的待办 <span v-if="total === 0 && h" class="m-muted">　都处理完了 👍</span></h3>
      <div class="grid">
        <router-link v-for="c in cards" :key="c.key" :to="c.to" class="todo" :class="{ hot: c.hot }">
          <b>{{ c.count ?? '-' }}</b>
          <span>{{ c.label }}</span>
          <em v-if="c.sub">{{ c.sub }}</em>
        </router-link>
      </div>
    </div>

    <div class="m-card tips">
      <h3>现场扫码</h3>
      <div class="m-muted">用微信或手机相机扫受限空间门口的二维码，可以看到当前作业票和气体检测数据，并能直接上报这个空间的隐患。</div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { mobileHome } from '@/api'
import { useUserStore } from '@/stores/user'
import { ROLE_NAMES } from '@/utils/dict'

const router = useRouter()
const store = useUserStore()
const h = ref(null)
const loading = ref(false)
const hour = new Date().getHours()
const greeting = hour < 6 ? '夜班辛苦了' : hour < 12 ? '早上好' : hour < 18 ? '下午好' : '晚上好'
const roleText = computed(() => store.roles.map((r) => ROLE_NAMES[r] || r).join('、') || '员工')

// 没有权限的项后端返回 null，不显示
const cards = computed(() => {
  const v = h.value || {}
  return [
    v.approve != null && { key: 'approve', label: '待我审批', count: v.approve, to: '/m/tickets?tab=todo', hot: v.approve > 0 },
    v.working != null && { key: 'working', label: '我的作业', count: v.working, to: '/m/tickets?tab=mine', sub: '待开工 / 作业中' },
    { key: 'rectify', label: '待我整改', count: v.rectify, to: '/m/hazards?tab=rectify', hot: v.rectifyOverdue > 0, sub: v.rectifyOverdue ? `${v.rectifyOverdue} 条已逾期` : '' },
    v.assign != null && { key: 'assign', label: '待指派隐患', count: v.assign, to: '/m/hazards?tab=assign', hot: v.assign > 0 },
    v.verify != null && { key: 'verify', label: '待验收隐患', count: v.verify, to: '/m/hazards?tab=verify', hot: v.verify > 0 },
    v.riskDue != null && { key: 'risk', label: '今日巡检', count: v.riskDue, to: '/safety/risk-check', sub: '到期 / 逾期' }
  ].filter(Boolean)
})
const total = computed(() => cards.value.reduce((s, c) => s + (c.count || 0), 0))

async function load() {
  loading.value = true
  try {
    h.value = await mobileHome()
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>

<style scoped>
.hello { display: flex; align-items: center; justify-content: space-between; margin: 4px 2px 12px; }
.name { font-size: 18px; font-weight: 600; }
.tenant { color: #8a8f99; font-size: 13px; margin-top: 2px; }
.refresh { font-size: 20px; color: #8a8f99; padding: 6px; }
.spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.report { width: 100%; display: flex; align-items: center; gap: 14px; border: 0; border-radius: 12px; padding: 16px; margin-bottom: 12px; color: #fff; text-align: left; background: linear-gradient(135deg, #ff7a45, #f5222d); box-shadow: 0 4px 12px rgba(245, 34, 45, .25); }
.report .el-icon { font-size: 34px; }
.report b { display: block; font-size: 17px; }
.report span { font-size: 13px; opacity: .9; }
.grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
.todo { display: flex; flex-direction: column; align-items: center; padding: 12px 4px; border-radius: 8px; background: #f6f8fb; color: #333; text-decoration: none; }
.todo b { font-size: 24px; color: #1f6feb; }
.todo span { font-size: 13px; margin-top: 2px; }
.todo em { font-style: normal; font-size: 11px; color: #8a8f99; margin-top: 2px; }
.todo.hot b { color: #f5222d; }
.todo.hot em { color: #f5222d; }
.tips h3 { margin-bottom: 6px; }
</style>
