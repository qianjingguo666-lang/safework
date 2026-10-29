<template>
  <div>
    <div class="page-card">
      <div class="toolbar">
        <span class="hint">任务按 cron 自动执行；多台服务器同时部署时通过 Redis 分布式锁保证同一时刻只有一台执行</span>
        <div class="spacer" />
        <el-button icon="Refresh" @click="loadJobs">刷新</el-button>
      </div>
      <el-table v-loading="jobsLoading" :data="jobs">
        <el-table-column label="任务" min-width="260">
          <template #default="{ row }">
            <div class="name">{{ row.name }} <span class="code">{{ row.code }}</span></div>
            <div class="desc">{{ row.description }}</div>
          </template>
        </el-table-column>
        <el-table-column label="执行时间" width="150">
          <template #default="{ row }"><code>{{ row.cron }}</code></template>
        </el-table-column>
        <el-table-column label="下次执行" width="170">
          <template #default="{ row }">{{ row.nextTime || '已关闭自动执行' }}</template>
        </el-table-column>
        <el-table-column label="上次执行" min-width="260">
          <template #default="{ row }">
            <template v-if="row.lastLog">
              <el-tag size="small" :type="row.lastLog.status === 1 ? 'success' : 'danger'">{{ row.lastLog.status === 1 ? '成功' : '失败' }}</el-tag>
              <span class="muted"> {{ row.lastLog.startTime }} · {{ row.lastLog.costMs }} ms</span>
              <div class="result">{{ row.lastLog.result }}</div>
            </template>
            <span v-else class="muted">还没有执行过</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button v-perm="'platform:job:run'" type="primary" size="small" :loading="running === row.code || row.running"
                       @click="run(row)">立即执行</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="page-card logs">
      <div class="toolbar">
        <b>执行记录</b>
        <el-select v-model="logQuery.code" placeholder="任务" clearable style="width: 180px" @change="searchLogs">
          <el-option v-for="j in jobs" :key="j.code" :label="j.name" :value="j.code" />
        </el-select>
        <el-select v-model="logQuery.status" placeholder="结果" clearable style="width: 100px" @change="searchLogs">
          <el-option label="成功" :value="1" /><el-option label="失败" :value="0" />
        </el-select>
      </div>
      <el-table v-loading="logsLoading" :data="logs" stripe>
        <el-table-column prop="startTime" label="开始时间" width="170" />
        <el-table-column prop="jobName" label="任务" width="150" />
        <el-table-column label="触发方式" width="130">
          <template #default="{ row }">{{ row.triggerType === 'MANUAL' ? `手动（${row.operatorName || '-'}）` : '定时' }}</template>
        </el-table-column>
        <el-table-column label="结果" width="80">
          <template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '成功' : '失败' }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="result" label="说明" min-width="300" show-overflow-tooltip />
        <el-table-column label="耗时" width="90"><template #default="{ row }">{{ row.costMs }} ms</template></el-table-column>
        <el-table-column prop="server" label="服务器" width="160" show-overflow-tooltip />
      </el-table>
      <div class="pager">
        <el-pagination v-model:current-page="logQuery.current" :page-size="logQuery.size" :total="logTotal" layout="total, prev, pager, next" @current-change="loadLogs" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listJobs, pageJobLogs, runJob } from '@/api'

const jobs = ref([])
const jobsLoading = ref(false)
const running = ref('')
const logs = ref([])
const logTotal = ref(0)
const logsLoading = ref(false)
const logQuery = reactive({ current: 1, size: 10, code: '', status: null })

async function loadJobs() {
  jobsLoading.value = true
  try {
    jobs.value = await listJobs()
  } finally {
    jobsLoading.value = false
  }
}
async function loadLogs() {
  logsLoading.value = true
  try {
    const params = Object.fromEntries(Object.entries(logQuery).filter(([, v]) => v !== '' && v != null))
    const page = await pageJobLogs(params)
    logs.value = page.records
    logTotal.value = page.total
  } finally {
    logsLoading.value = false
  }
}
function searchLogs() {
  logQuery.current = 1
  loadLogs()
}
async function run(row) {
  running.value = row.code
  try {
    const log = await runJob(row.code)
    if (log.status === 1) ElMessage.success(`执行成功：${log.result}`)
    else ElMessage.error(log.result)
    loadJobs()
    loadLogs()
  } finally {
    running.value = ''
  }
}
onMounted(() => {
  loadJobs()
  loadLogs()
})
</script>

<style scoped>
.hint { color: #888; font-size: 13px; }
.name { font-weight: 600; }
.code { color: #999; font-weight: normal; font-size: 12px; margin-left: 6px; }
.desc { color: #888; font-size: 12px; margin-top: 4px; line-height: 1.5; }
.muted { color: #999; font-size: 12px; }
.result { color: #555; font-size: 12px; margin-top: 4px; }
.logs { margin-top: 16px; }
</style>
