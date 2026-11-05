<template>
  <el-dialog v-model="visible" title="登记发放" width="min(560px, 94vw)">
    <el-form label-width="80px">
      <el-form-item label="领用人" required>
        <el-select v-model="form.userId" filterable style="width: 100%">
          <el-option v-for="u in users" :key="u.id" :label="u.nickname" :value="u.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="用品" required>
        <div class="items">
          <div v-for="(it, i) in form.items" :key="i" class="row">
            <el-select v-model="it.itemId" filterable placeholder="选择用品" class="name">
              <el-option v-for="x in items" :key="x.id" :label="`${label(x)}（库存 ${x.stock}${x.unit}）`" :value="x.id" :disabled="x.stock <= 0" />
            </el-select>
            <el-input-number v-model="it.quantity" :min="1" :max="20" class="qty" />
            <el-button link icon="Delete" :disabled="form.items.length <= 1" @click="form.items.splice(i, 1)" />
          </div>
          <el-button size="small" icon="Plus" @click="form.items.push({ itemId: null, quantity: 1 })">再加一种</el-button>
        </div>
      </el-form-item>
      <el-form-item label="发放日期">
        <el-date-picker v-model="form.issueDate" type="date" value-format="YYYY-MM-DD" :disabled-date="(d) => d > new Date()" style="width: 100%" />
      </el-form-item>
      <el-form-item label="备注"><el-input v-model="form.remark" maxlength="200" /></el-form-item>
      <div class="hint">发放后扣减库存；这个人之前领的同一种用品自动标记为“已更换”；到期日期按用品的使用期限计算</div>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">确认发放</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ppeIssue, ppeItems, userOptions } from '@/api'

const emit = defineEmits(['issued'])
const visible = ref(false)
const saving = ref(false)
const users = ref([])
const items = ref([])
const form = reactive({})
const label = (x) => x.name + (x.spec ? `（${x.spec}）` : '')

async function open(preset) {
  Object.assign(form, { userId: preset?.userId || null, items: [{ itemId: preset?.itemId || null, quantity: preset?.quantity || 1 }], issueDate: null, remark: '' })
  visible.value = true
  const [u, i] = await Promise.all([users.value.length ? users.value : userOptions(), ppeItems(true)])
  users.value = u
  items.value = i
}
async function save() {
  if (!form.userId) return ElMessage.warning('请选择领用人')
  const list = form.items.filter((x) => x.itemId)
  if (!list.length) return ElMessage.warning('请选择要发放的用品')
  saving.value = true
  try {
    await ppeIssue({ userId: form.userId, items: list, issueDate: form.issueDate || undefined, remark: form.remark || undefined })
    ElMessage.success('已发放，领用人会收到通知')
    visible.value = false
    emit('issued')
  } finally {
    saving.value = false
  }
}
defineExpose({ open })
</script>

<style scoped>
.items { width: 100%; }
.row { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.name { flex: 1; }
.qty { width: 110px; }
.hint { margin: -6px 0 0 80px; color: #999; font-size: 12px; line-height: 1.6; }
</style>
