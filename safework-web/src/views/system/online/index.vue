<template>
  <div class="page-card">
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="用户名 / 昵称 / IP" clearable style="width: 220px" @keyup.enter="load" @clear="load" />
      <el-button icon="Search" @click="load">查询</el-button>
      <el-button icon="Refresh" @click="load">刷新</el-button>
      <div class="spacer" />
      <span class="hint">共 {{ list.length }} 个在线会话。同一个人在电脑和手机上同时登录，会显示两条</span>
    </div>

    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column label="用户" min-width="180">
        <template #default="{ row }">
          {{ row.nickname }} <span class="muted">（{{ row.username }}）</span>
          <el-tag v-if="row.current" size="small" type="success">当前会话</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="ip" label="登录 IP" width="140" />
      <el-table-column prop="browser" label="浏览器" width="130" />
      <el-table-column prop="os" label="操作系统" width="130" />
      <el-table-column prop="loginTime" label="登录时间" width="170" />
      <el-table-column label="在线时长" width="110">
        <template #default="{ row }">{{ duration(row.loginTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button v-if="!row.current" v-perm="'system:online:kickout'" link type="danger" @click="kick(row)">强制下线</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { kickoutOnline, listOnlineUsers } from '@/api'

const loading = ref(false)
const list = ref([])
const keyword = ref('')

async function load() {
  loading.value = true
  try {
    list.value = await listOnlineUsers(keyword.value ? { keyword: keyword.value } : {})
  } finally {
    loading.value = false
  }
}

function duration(time) {
  const minutes = Math.max(0, Math.floor((Date.now() - new Date(time.replace(' ', 'T')).getTime()) / 60000))
  if (minutes < 60) return `${minutes} 分钟`
  const h = Math.floor(minutes / 60)
  return h < 24 ? `${h} 小时 ${minutes % 60} 分` : `${Math.floor(h / 24)} 天 ${h % 24} 小时`
}

async function kick(row) {
  try {
    await ElMessageBox.confirm(`确定让【${row.nickname}】（${row.ip}，${row.browser}）下线吗？对方需要重新登录。`, '强制下线', { type: 'warning' })
  } catch {
    return
  }
  await kickoutOnline(row.sessionId)
  ElMessage.success('已强制下线')
  load()
}

onMounted(load)
</script>

<style scoped>
.muted { color: #999; font-size: 12px; }
.hint { color: #999; font-size: 12px; }
</style>
