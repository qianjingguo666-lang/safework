<template>
  <div>
    <div class="toolbar">
      <span class="muted">岗位决定每个人应该配哪些劳保用品；人员调岗、标准修改后，台账立即按新的计算</span>
      <div class="spacer" />
      <el-button type="primary" icon="Plus" @click="openForm()">新建岗位</el-button>
    </div>
    <el-empty v-if="!loading && !list.length" description="还没有岗位" />
    <div v-loading="loading" class="cards">
      <div v-for="p in list" :key="p.id" class="card" :class="{ off: p.status !== 1 }">
        <div class="card-head">
          <b>{{ p.name }}</b>
          <el-tag v-if="p.status !== 1" size="small" type="info">停用</el-tag>
          <div class="spacer" />
          <el-button link type="primary" @click="openForm(p)">修改</el-button>
          <el-button link @click="toggle(p)">{{ p.status === 1 ? '停用' : '启用' }}</el-button>
        </div>
        <div class="muted">{{ p.members.length }} 人：{{ p.members.map((m) => m.userName).join('、') || '还没有人' }}</div>
        <div class="std">
          <el-tag v-for="s in p.standards" :key="s.itemId" size="small" effect="plain">{{ s.itemName }} × {{ s.quantity }}{{ s.unit }} / {{ s.validMonths }} 个月</el-tag>
        </div>
      </div>
    </div>

    <el-dialog v-model="visible" :title="form.id ? '修改岗位' : '新建岗位'" width="min(620px, 94vw)">
      <el-form label-width="80px">
        <el-form-item label="岗位名称" required><el-input v-model="form.name" maxlength="40" placeholder="如：罐区操作工" /></el-form-item>
        <el-form-item label="人员">
          <el-select v-model="form.userIds" multiple filterable placeholder="选择这个岗位上的人" style="width: 100%">
            <el-option v-for="u in users" :key="u.id" :label="u.nickname" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="发放标准" required>
          <div class="items">
            <div v-for="(s, i) in form.standards" :key="i" class="row">
              <el-select v-model="s.itemId" filterable placeholder="选择用品" class="name">
                <el-option v-for="x in items" :key="x.id" :label="`${x.name}${x.spec ? `（${x.spec}）` : ''} · ${x.validMonths} 个月`" :value="x.id" />
              </el-select>
              <el-input-number v-model="s.quantity" :min="1" :max="20" class="qty" />
              <el-button link icon="Delete" :disabled="form.standards.length <= 1" @click="form.standards.splice(i, 1)" />
            </div>
            <el-button size="small" icon="Plus" @click="form.standards.push({ itemId: null, quantity: 1 })">再加一种</el-button>
          </div>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" maxlength="200" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ppeItems, ppePostStatus, ppePosts, savePpePost, userOptions } from '@/api'

const emit = defineEmits(['changed'])
const loading = ref(false)
const list = ref([])
const users = ref([])
const items = ref([])
const visible = ref(false)
const saving = ref(false)
const form = reactive({})

async function load() {
  loading.value = true
  try {
    list.value = await ppePosts()
  } finally {
    loading.value = false
  }
}
async function openForm(p) {
  Object.assign(form, {
    id: p?.id || null, name: p?.name || '', remark: p?.remark || '',
    userIds: p ? p.members.map((m) => m.userId) : [],
    standards: p?.standards.length ? p.standards.map((s) => ({ itemId: s.itemId, quantity: s.quantity })) : [{ itemId: null, quantity: 1 }]
  })
  visible.value = true
  const [u, i] = await Promise.all([users.value.length ? users.value : userOptions(), ppeItems(true)])
  users.value = u
  items.value = i
}
async function save() {
  if (!form.name.trim()) return ElMessage.warning('请填写岗位名称')
  const standards = form.standards.filter((s) => s.itemId)
  if (!standards.length) return ElMessage.warning('请至少配置一种劳保用品')
  saving.value = true
  try {
    const { id, ...data } = form
    await savePpePost(id, { ...data, standards })
    ElMessage.success('已保存，台账已按新标准计算')
    visible.value = false
    load()
    emit('changed')
  } finally {
    saving.value = false
  }
}
async function toggle(p) {
  await ppePostStatus(p.id, p.status === 1 ? 0 : 1)
  ElMessage.success(p.status === 1 ? '已停用，这个岗位不再计入台账' : '已启用')
  load()
  emit('changed')
}
onMounted(load)
</script>

<style scoped>
.muted { color: #999; font-size: 12px; }
.cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 12px; }
.card { border: 1px solid #ebeef5; border-radius: 8px; padding: 12px 14px; }
.card.off { opacity: .6; }
.card-head { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.card-head b { font-size: 15px; }
.std { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; }
.items { width: 100%; }
.row { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.name { flex: 1; }
.qty { width: 110px; }
</style>
