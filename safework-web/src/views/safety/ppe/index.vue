<template>
  <div class="page-card">
    <div v-if="overview" class="stats">
      <div class="stat" @click="filter('EXPIRED')"><b class="danger">{{ overview.expired }}</b><span>已过期</span></div>
      <div class="stat" @click="filter('MISSING')"><b class="danger">{{ overview.missing }}</b><span>未发放</span></div>
      <div class="stat" @click="filter('DUE_SOON')"><b class="warn">{{ overview.dueSoon }}</b><span>30 天内到期</span></div>
      <div class="stat" @click="filter('')"><b>{{ overview.people }}</b><span>{{ canEdit ? '岗位人数' : '我的岗位' }}</span></div>
      <div v-if="canEdit" class="stat" @click="tab = 'item'"><b :class="{ warn: overview.lowStock }">{{ overview.lowStock }}</b><span>库存不足的用品</span></div>
    </div>

    <el-tabs v-model="tab">
      <el-tab-pane :label="canEdit ? '发放台账' : '我的劳保'" name="ledger">
        <div class="toolbar">
          <el-input v-if="canEdit" v-model="query.keyword" placeholder="姓名 / 用品 / 岗位" clearable style="width: 180px" @keyup.enter="search" @clear="search" />
          <el-select v-if="canEdit" v-model="query.postId" placeholder="岗位" clearable style="width: 150px" @change="search">
            <el-option v-for="p in posts" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
          <el-select v-model="query.state" placeholder="状态" clearable style="width: 160px" @change="search">
            <el-option label="需要发放（未发放 + 已过期）" value="NEED" />
            <el-option v-for="(v, k) in PPE_STATES" :key="k" :label="v.label" :value="k" />
          </el-select>
          <el-button icon="Search" @click="search">查询</el-button>
          <div class="spacer" />
          <template v-if="canEdit">
            <el-button icon="Box" :loading="batching" @click="batch">一键补发{{ query.postId ? '本岗位' : '' }}</el-button>
            <el-button type="primary" icon="Plus" @click="issueRef.open()">登记发放</el-button>
          </template>
        </div>
        <el-table v-loading="loading" :data="rows" stripe>
          <el-table-column v-if="canEdit" prop="userName" label="人员" width="150" show-overflow-tooltip />
          <el-table-column prop="posts" label="岗位" width="130" show-overflow-tooltip />
          <el-table-column prop="itemName" label="劳保用品" min-width="200" show-overflow-tooltip />
          <el-table-column label="应配" width="70"><template #default="{ row }">{{ row.quantity }} {{ row.unit }}</template></el-table-column>
          <el-table-column label="上次发放" width="110"><template #default="{ row }">{{ row.issueDate || '-' }}</template></el-table-column>
          <el-table-column label="到期日期" width="110"><template #default="{ row }">{{ row.expireDate || '-' }}</template></el-table-column>
          <el-table-column label="状态" width="170">
            <template #default="{ row }">
              <el-tag size="small" :type="PPE_STATES[row.state]?.type">{{ PPE_STATES[row.state]?.label }}</el-tag>
              <span v-if="row.daysLeft != null && row.state !== 'OK'" class="days">{{ row.daysLeft < 0 ? `过期 ${-row.daysLeft} 天` : `剩 ${row.daysLeft} 天` }}</span>
            </template>
          </el-table-column>
          <el-table-column v-if="canEdit" label="操作" width="80">
            <template #default="{ row }">
              <el-button link type="primary" @click="issueRef.open({ userId: row.userId, itemId: row.itemId, quantity: row.quantity })">发放</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination v-model:current-page="query.current" :page-size="query.size" :total="total" layout="total, prev, pager, next" @current-change="load" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="发放记录" name="issue" lazy>
        <IssueRecords ref="recordRef" :can-edit="canEdit" />
      </el-tab-pane>
      <el-tab-pane v-if="canEdit" label="用品目录" name="item" lazy>
        <ItemList @changed="reload" />
      </el-tab-pane>
      <el-tab-pane v-if="canEdit" label="岗位标准" name="post" lazy>
        <PostList @changed="reloadPosts" />
      </el-tab-pane>
    </el-tabs>

    <IssueDialog ref="issueRef" @issued="afterIssue" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { ppeBatchIssue, ppeLedger, ppeOverview, ppePosts } from '@/api'
import { PPE_STATES } from '@/utils/dict'
import IssueDialog from './IssueDialog.vue'
import IssueRecords from './IssueRecords.vue'
import ItemList from './ItemList.vue'
import PostList from './PostList.vue'

const store = useUserStore()
const canEdit = computed(() => store.hasPerm('ppe:edit'))
const tab = ref('ledger')
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const overview = ref(null)
const posts = ref([])
const issueRef = ref()
const recordRef = ref()
const query = reactive({ current: 1, size: 20, keyword: '', postId: null, state: '' })

async function load() {
  loading.value = true
  try {
    const params = Object.fromEntries(Object.entries(query).filter(([, v]) => v !== '' && v != null))
    const page = await ppeLedger(params)
    rows.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}
function search() {
  query.current = 1
  load()
}
function filter(state) {
  tab.value = 'ledger'
  query.state = state
  search()
}
function reload() {
  load()
  ppeOverview().then((v) => { overview.value = v })
}
function reloadPosts() {
  ppePosts().then((v) => { posts.value = v })
  reload()
}
function afterIssue() {
  reload()
  recordRef.value?.load()
}

const batching = ref(false)
async function batch() {
  const post = posts.value.find((p) => p.id === query.postId)
  try {
    await ElMessageBox.confirm(`给${post ? `【${post.name}】` : '所有岗位'}上“未发放、已过期”的人补发劳保用品？库存不够时一件都不会发。`, '一键补发', { type: 'warning' })
  } catch {
    return
  }
  batching.value = true
  try {
    const r = await ppeBatchIssue(query.postId)
    ElMessageBox.alert(r.lines.join('\n'), `已给 ${r.people} 人发放 ${r.records} 项`, { customClass: 'ppe-batch' })
    afterIssue()
  } finally {
    batching.value = false
  }
}

onMounted(() => {
  reload()
  if (canEdit.value) ppePosts().then((v) => { posts.value = v })
})
</script>

<style scoped>
.stats { display: flex; gap: 12px; margin-bottom: 8px; flex-wrap: wrap; }
.stat { flex: 1; min-width: 110px; padding: 12px 16px; border: 1px solid #ebeef5; border-radius: 8px; display: flex; align-items: baseline; gap: 8px; cursor: pointer; }
.stat b { font-size: 24px; }
.stat span { color: #888; font-size: 13px; }
.warn { color: #e6a23c; }
.danger { color: #f56c6c; }
.days { margin-left: 6px; font-size: 12px; color: #999; }
</style>

<style>
.ppe-batch .el-message-box__message { white-space: pre-wrap; max-height: 50vh; overflow-y: auto; }
</style>
