<template>
  <div class="page-card">
    <el-tabs v-model="tab">
      <el-tab-pane label="我的责任书" name="mine">
        <el-alert v-if="pending" type="warning" :closable="false" show-icon class="tip"
          :title="`你有 ${pending} 份安全生产责任书待签署，请阅读岗位安全职责后手写签名`" />
        <el-table v-loading="loading" :data="mine" stripe>
          <el-table-column label="年度" width="80"><template #default="{ row }">{{ row.book.year }}</template></el-table-column>
          <el-table-column label="责任书" min-width="200" show-overflow-tooltip><template #default="{ row }">{{ row.book.title }}</template></el-table-column>
          <el-table-column label="签署截止" width="110"><template #default="{ row }">{{ row.book.deadline }}</template></el-table-column>
          <el-table-column label="状态" width="170">
            <template #default="{ row }">
              <el-tag v-if="row.signed" size="small" type="success">已签署</el-tag>
              <el-tag v-else-if="row.book.status === 'PUBLISHED'" size="small" type="warning">待签署</el-tag>
              <el-tag v-else size="small" type="info">未签署（已结束）</el-tag>
              <el-tag v-if="row.late" size="small" type="danger" effect="plain">逾期签署</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="年度考核" width="100">
            <template #default="{ row }">{{ row.assessed ? '已考核' : '-' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button link type="primary" @click="openMine(row)">{{ !row.signed && row.book.status === 'PUBLISHED' ? '阅读并签署' : '查看' }}</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!loading && !mine.length" description="还没有发给你的安全生产责任书" />
      </el-tab-pane>
      <el-tab-pane v-if="canEdit" label="年度责任书" name="book" lazy>
        <BookList />
      </el-tab-pane>
      <el-tab-pane v-if="canEdit" label="岗位安全职责" name="duty" lazy>
        <DutyList />
      </el-tab-pane>
    </el-tabs>

    <SignDoc ref="docRef" @signed="loadMine" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useUserStore } from '@/stores/user'
import { respMine, respMySignId } from '@/api'
import BookList from './BookList.vue'
import DutyList from './DutyList.vue'
import SignDoc from './SignDoc.vue'

const store = useUserStore()
const canEdit = computed(() => store.hasPerm('resp:edit'))
const tab = ref('mine')
const loading = ref(false)
const mine = ref([])
const docRef = ref()
const pending = computed(() => mine.value.filter((r) => !r.signed && r.book.status === 'PUBLISHED').length)

async function loadMine() {
  loading.value = true
  try {
    mine.value = await respMine()
  } finally {
    loading.value = false
  }
}
async function openMine(row) {
  docRef.value.open(await respMySignId(row.book.id))
}
onMounted(async () => {
  await loadMine()
  if (canEdit.value && !pending.value) tab.value = 'book'
})
</script>

<style scoped>
.tip { margin-bottom: 12px; }
.el-tag + .el-tag { margin-left: 4px; }
</style>
