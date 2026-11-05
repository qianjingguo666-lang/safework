<template>
  <el-dialog v-model="visible" title="登记体检结果" width="min(560px, 94vw)">
    <el-form label-width="90px">
      <el-form-item label="体检人员" required>
        <el-select v-model="form.userId" filterable style="width: 100%">
          <el-option v-for="u in users" :key="u.id" :label="u.nickname" :value="u.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="体检类型" required>
        <el-radio-group v-model="form.examType">
          <el-radio-button v-for="(v, k) in OH_EXAM_TYPES" :key="k" :value="k">{{ v }}</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="体检日期" required>
        <el-date-picker v-model="form.examDate" type="date" value-format="YYYY-MM-DD" :disabled-date="(d) => d > new Date()" style="width: 100%" />
      </el-form-item>
      <el-form-item label="体检结论" required>
        <el-select v-model="form.conclusion" style="width: 100%">
          <el-option v-for="(v, k) in OH_CONCLUSIONS" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="form.conclusion === 'RECHECK'" label="复查日期" required>
        <el-date-picker v-model="form.recheckDate" type="date" value-format="YYYY-MM-DD" placeholder="体检后 90 天内" style="width: 100%" />
      </el-form-item>
      <el-alert v-if="form.conclusion === 'CONTRAINDICATION'" type="error" :closable="false" show-icon class="alert"
                title="有职业禁忌证的人员应调离接触相应危害因素的岗位。台账里会标红，直到调岗或复查正常" />
      <el-form-item label="体检机构"><el-input v-model="form.org" maxlength="60" placeholder="如：合肥市职业病防治院" /></el-form-item>
      <el-form-item label="报告编号"><el-input v-model="form.reportNo" maxlength="40" /></el-form-item>
      <el-form-item label="说明">
        <el-input v-model="form.detail" type="textarea" :rows="2" maxlength="500" :placeholder="abnormal ? '写明异常项目和医生建议' : '可选'" />
      </el-form-item>
      <div class="hint">体检结果属于个人健康信息，只有本人和职业卫生管理员能看到</div>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { saveHealthExam, userOptions } from '@/api'
import { OH_CONCLUSIONS, OH_EXAM_TYPES } from '@/utils/dict'

const emit = defineEmits(['saved'])
const visible = ref(false)
const saving = ref(false)
const users = ref([])
const form = reactive({})
const abnormal = computed(() => ['RECHECK', 'SUSPECT', 'CONTRAINDICATION'].includes(form.conclusion))
const today = () => new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 10)

async function open(userId) {
  Object.assign(form, { userId: userId || null, examType: 'ON', examDate: today(), conclusion: 'NORMAL', recheckDate: null, org: '', reportNo: '', detail: '' })
  visible.value = true
  if (!users.value.length) users.value = await userOptions()
}
async function save() {
  if (!form.userId) return ElMessage.warning('请选择体检人员')
  if (form.conclusion === 'RECHECK' && !form.recheckDate) return ElMessage.warning('需要复查的，请填写复查日期')
  saving.value = true
  try {
    const data = { ...form, recheckDate: form.conclusion === 'RECHECK' ? form.recheckDate : null }
    await saveHealthExam(data)
    ElMessage.success(abnormal.value ? '已登记，异常已进入待处理，本人和管理员会收到通知' : '已登记')
    visible.value = false
    emit('saved')
  } finally {
    saving.value = false
  }
}
defineExpose({ open })
</script>

<style scoped>
.alert { margin: -6px 0 14px 90px; width: auto; }
.hint { margin: -6px 0 0 90px; color: #999; font-size: 12px; }
</style>
