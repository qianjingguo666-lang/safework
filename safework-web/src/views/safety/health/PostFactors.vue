<template>
  <div>
    <div class="toolbar">
      <span class="muted">岗位和岗位人员在【劳保用品 → 岗位标准】里维护；这里设置每个岗位接触哪些危害因素</span>
    </div>
    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column prop="postName" label="岗位" width="160" />
      <el-table-column label="人数" width="70"><template #default="{ row }">{{ row.memberCount }}</template></el-table-column>
      <el-table-column label="接触的危害因素" min-width="300">
        <template #default="{ row }">
          <el-select v-if="editing === row.postId" v-model="selected" multiple filterable style="width: 100%">
            <el-option v-for="f in factors" :key="f.id" :label="f.name" :value="f.id" />
          </el-select>
          <template v-else>
            <el-tag v-for="n in row.factorNames" :key="n" size="small" type="warning" effect="plain" class="tag">{{ n }}</el-tag>
            <span v-if="!row.factorNames.length" class="muted">不接触（不需要职业健康体检）</span>
          </template>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <template v-if="editing === row.postId">
            <el-button link type="primary" :loading="saving" @click="save(row)">保存</el-button>
            <el-button link @click="editing = null">取消</el-button>
          </template>
          <el-button v-else link type="primary" @click="edit(row)">设置</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { healthFactors, healthPosts, saveHealthPostFactors } from '@/api'

const emit = defineEmits(['changed'])
const loading = ref(false)
const saving = ref(false)
const list = ref([])
const factors = ref([])
const editing = ref(null)
const selected = ref([])

async function load() {
  loading.value = true
  try {
    list.value = await healthPosts()
  } finally {
    loading.value = false
  }
}
async function edit(row) {
  if (!factors.value.length) factors.value = await healthFactors(true)
  selected.value = [...row.factorIds]
  editing.value = row.postId
}
async function save(row) {
  saving.value = true
  try {
    await saveHealthPostFactors(row.postId, selected.value)
    ElMessage.success('已保存，监护台账已按新的接害情况计算')
    editing.value = null
    load()
    emit('changed')
  } finally {
    saving.value = false
  }
}
onMounted(load)
</script>

<style scoped>
.muted { color: #999; font-size: 12px; }
.tag { margin-right: 4px; }
</style>
