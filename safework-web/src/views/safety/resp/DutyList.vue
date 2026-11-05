<template>
  <div>
    <div class="toolbar">
      <span class="muted">岗位和岗位人员在【劳保用品 → 岗位标准】里维护；这里给每个岗位写安全职责。修改职责只影响以后发布的责任书。</span>
      <div class="spacer" />
      <el-button icon="Refresh" @click="load">刷新</el-button>
    </div>
    <el-table v-loading="loading" :data="rows" stripe>
      <el-table-column label="岗位" width="160">
        <template #default="{ row }">
          {{ row.postName }}
          <el-tag v-if="row.status !== 1" size="small" type="info">停用</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="人数" width="70"><template #default="{ row }">{{ row.memberCount }}</template></el-table-column>
      <el-table-column label="安全职责" min-width="320">
        <template #default="{ row }">
          <ol v-if="row.duties.length" class="duties">
            <li v-for="(t, i) in row.duties.slice(0, 3)" :key="i">{{ t }}</li>
            <li v-if="row.duties.length > 3" class="muted more">…… 共 {{ row.duties.length }} 条</li>
          </ol>
          <span v-else class="warn">还没有填写（发布责任书时这个岗位的人不会收到）</span>
        </template>
      </el-table-column>
      <el-table-column label="最后修改" width="130"><template #default="{ row }">{{ row.updaterName || '-' }}</template></el-table-column>
      <el-table-column label="操作" width="80">
        <template #default="{ row }"><el-button link type="primary" @click="edit(row)">编辑</el-button></template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && !rows.length" description="还没有岗位，请先到【劳保用品 → 岗位标准】新建岗位" />

    <el-dialog v-model="visible" :title="`岗位安全职责：${cur?.postName}`" width="640px">
      <div class="muted hint">一行一条，最多 30 条，每条不超过 200 字；行首的编号（如“1.”）会自动去掉</div>
      <el-input v-model="text" type="textarea" :rows="14" placeholder="例如：&#10;严格执行岗位操作规程&#10;正确佩戴劳动防护用品" />
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { respDuties, saveRespDuties } from '@/api'

const loading = ref(false)
const rows = ref([])
const visible = ref(false)
const saving = ref(false)
const cur = ref(null)
const text = ref('')

async function load() {
  loading.value = true
  try {
    rows.value = await respDuties()
  } finally {
    loading.value = false
  }
}
function edit(row) {
  cur.value = row
  text.value = row.duties.join('\n')
  visible.value = true
}
async function save() {
  const duties = text.value.split('\n').map((s) => s.trim()).filter(Boolean)
  saving.value = true
  try {
    await saveRespDuties(cur.value.postId, duties)
    ElMessage.success('已保存')
    visible.value = false
    load()
  } finally {
    saving.value = false
  }
}
onMounted(load)
</script>

<style scoped>
.duties { margin: 0; padding-left: 18px; line-height: 1.7; }
.more { list-style: none; margin-left: -18px; }
.muted { color: #999; font-size: 13px; }
.warn { color: #e6a23c; }
.hint { margin-bottom: 8px; }
.el-tag { margin-left: 4px; }
</style>
