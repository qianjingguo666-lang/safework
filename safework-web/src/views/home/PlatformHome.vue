<template>
  <div>
    <div class="page-card welcome">
      <div>
        <h2>你好，{{ store.nickname }}</h2>
        <p>SafeWork 运营平台 · 管理入驻企业、套餐和服务期限</p>
      </div>
      <el-button v-perm="'platform:tenant:add'" type="primary" icon="Plus" @click="$router.push('/platform/tenant?create=1')">开通企业</el-button>
    </div>

    <el-row :gutter="16" class="stats">
      <el-col v-for="c in cards" :key="c.label" :xs="12" :sm="6">
        <div class="page-card stat" @click="c.to && $router.push(c.to)">
          <div class="num" :style="{ color: c.color }">{{ c.value }}</div>
          <div class="label">{{ c.label }}</div>
          <div v-if="c.sub" class="sub">{{ c.sub }}</div>
        </div>
      </el-col>
    </el-row>

    <div class="page-card">
      <div class="block-title">
        <span>即将到期 / 已到期的企业</span>
        <el-button link type="primary" @click="$router.push('/platform/tenant?expiring=1')">查看全部</el-button>
      </div>
      <el-table :data="stats?.expiringTenants || []" empty-text="30 天内没有到期的企业">
        <el-table-column prop="tenantName" label="企业" min-width="180" />
        <el-table-column prop="tenantCode" label="企业编码" width="120" />
        <el-table-column prop="packageName" label="套餐" width="100" />
        <el-table-column label="到期时间" width="120">
          <template #default="{ row }">{{ row.expireTime?.slice(0, 10) }}</template>
        </el-table-column>
        <el-table-column label="剩余" width="110">
          <template #default="{ row }">
            <el-tag :type="row.daysLeft <= 0 ? 'danger' : row.daysLeft <= 7 ? 'warning' : 'info'">
              {{ row.daysLeft <= 0 ? '已到期' : `${row.daysLeft} 天` }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="contactName" label="联系人" width="100" />
        <el-table-column prop="contactPhone" label="电话" width="130" />
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useUserStore } from '@/stores/user'
import { platformStats } from '@/api'

const store = useUserStore()
const stats = ref(null)

const cards = computed(() => {
  const s = stats.value || {}
  const v = (x) => (x == null ? '-' : x)
  return [
    { label: '入驻企业', value: v(s.tenantTotal), sub: s.tenantTotal == null ? '' : `正常 ${s.tenantEnabled} 家`, color: '#1f6feb', to: '/platform/tenant' },
    { label: '30 天内到期', value: v(s.tenantExpiringSoon), sub: s.tenantExpired == null ? '' : `已到期 ${s.tenantExpired} 家`, color: '#e6a23c', to: '/platform/tenant?expiring=1' },
    { label: '账号总数 / 在线', value: s.userTotal == null ? '-' : `${s.userTotal} / ${s.onlineTotal}`, color: '#67c23a' },
    { label: '今日登录 成功 / 失败', value: s.loginSuccessToday == null ? '-' : `${s.loginSuccessToday} / ${s.loginFailToday}`, color: s.loginFailToday > 20 ? '#f56c6c' : '#909399', to: '/platform/loginlog' }
  ]
})

onMounted(async () => {
  stats.value = await platformStats()
})
</script>

<style scoped>
.welcome { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px; }
.welcome h2 { margin: 0 0 6px; }
.welcome p { margin: 0; color: #666; }
.stats { margin-top: 16px; }
.stat { text-align: center; cursor: pointer; margin-bottom: 16px; transition: box-shadow .2s; }
.stat:hover { box-shadow: 0 4px 16px rgba(0,0,0,.08); }
.num { font-size: 30px; font-weight: 600; }
.label { color: #666; margin-top: 4px; }
.sub { color: #999; font-size: 12px; margin-top: 2px; }
.block-title { display: flex; justify-content: space-between; align-items: center; font-weight: 600; margin-bottom: 8px; }
</style>
