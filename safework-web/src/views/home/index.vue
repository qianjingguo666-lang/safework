<template>
  <div>
    <div class="page-card welcome">
      <div>
        <h2>你好，{{ store.nickname }}</h2>
        <p>{{ store.tenantName }} · {{ roleText }}</p>
      </div>
      <el-button v-perm="'ticket:add'" type="primary" icon="Plus" @click="$router.push('/ticket/list?create=1')">申请作业票</el-button>
    </div>

    <el-row v-if="canView" :gutter="16" class="stats">
      <el-col v-for="s in stats" :key="s.status" :xs="12" :sm="6">
        <div class="page-card stat" @click="$router.push(`/ticket/list?status=${s.status}`)">
          <div class="num" :style="{ color: s.color }">{{ s.count }}</div>
          <div class="label">{{ s.label }}</div>
        </div>
      </el-col>
    </el-row>

    <el-row v-if="canHazard" :gutter="16">
      <el-col v-for="s in hazardStats" :key="s.label" :xs="12" :sm="6">
        <div class="page-card stat" @click="$router.push('/safety/hazard')">
          <div class="num" :style="{ color: s.color }">{{ s.count }}</div>
          <div class="label">{{ s.label }}</div>
        </div>
      </el-col>
    </el-row>

    <div class="page-card tips">
      <h3>你的工作</h3>
      <ul>
        <li v-for="t in todo" :key="t">{{ t }}</li>
      </ul>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive } from 'vue'
import { useUserStore } from '@/stores/user'
import { pageHazards, pageTickets } from '@/api'
import { ROLE_NAMES } from '@/utils/dict'

const store = useUserStore()
const canView = computed(() => store.hasPerm('ticket:list'))
const roleText = computed(() => store.roles.map((r) => ROLE_NAMES[r] || r).join('、') || '未分配角色')

const stats = reactive([
  { status: 'PENDING', label: '待审批', color: '#e6a23c', count: '-' },
  { status: 'APPROVED', label: '已批准待开工', color: '#1f6feb', count: '-' },
  { status: 'IN_PROGRESS', label: '作业中', color: '#f56c6c', count: '-' },
  { status: 'COMPLETED', label: '已完成', color: '#67c23a', count: '-' }
])

const canHazard = computed(() => store.hasPerm('safety:hazard:list'))
const hazardStats = reactive([
  { params: { status: 'REPORTED' }, label: '隐患待指派', color: '#e6a23c', count: '-' },
  { params: { status: 'RECTIFYING' }, label: '隐患整改中', color: '#1f6feb', count: '-' },
  { params: { status: 'PENDING_VERIFY' }, label: '隐患待验收', color: '#909399', count: '-' },
  { params: { overdue: true }, label: '逾期未整改', color: '#f56c6c', count: '-' }
])

const todo = computed(() => {
  const list = []
  if (store.roles.includes('admin')) list.push('管理用户、角色和权限；可以查看企业全部作业票')
  if (store.hasPerm('safety:hazard:handle')) list.push('指派隐患整改责任人和期限，整改完成后到现场验收')
  if (store.hasPerm('ticket:approve')) list.push('审批“待审批”状态的作业票，检查安全措施和气体检测数据')
  if (store.hasPerm('ticket:add')) list.push('申请作业票：填写作业信息、选择监护人、录入气体检测数据后提交审批')
  if (store.roles.includes('guardian')) list.push('在现场确认安全措施，复测气体后点击“开始作业”，作业结束后点击“作业完成”')
  if (store.hasPerm('ai:chat')) list.push('有安全制度方面的问题，可以在【AI 助手 → 安全问答】里提问，回答会标注出处')
  if (!list.length) list.push('暂无可用功能，请联系企业管理员分配角色')
  return list
})

onMounted(async () => {
  if (canHazard.value) {
    hazardStats.forEach(async (s) => {
      s.count = (await pageHazards({ ...s.params, size: 1 })).total
    })
  }
  if (!canView.value) return
  // 数据权限由后端控制：安全员看到全部，其他人只统计与自己相关的
  await Promise.all(stats.map(async (s) => {
    const page = await pageTickets({ status: s.status, size: 1 })
    s.count = page.total
  }))
})
</script>

<style scoped>
.welcome { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px; }
.welcome h2 { margin: 0 0 6px; }
.welcome p { margin: 0; color: #666; }
.stats { margin-top: 16px; }
.stat { text-align: center; cursor: pointer; margin-bottom: 16px; transition: box-shadow .2s; }
.stat:hover { box-shadow: 0 4px 16px rgba(0,0,0,.08); }
.num { font-size: 32px; font-weight: 600; }
.label { color: #666; margin-top: 4px; }
.tips { margin-top: 0; }
.tips h3 { margin-top: 0; }
.tips li { line-height: 2; color: #444; }
</style>
