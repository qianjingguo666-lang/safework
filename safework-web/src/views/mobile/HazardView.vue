<template>
  <div v-loading="loading">
    <template v-if="h">
      <div class="m-card">
        <div class="m-item-head">
          <el-tag effect="dark" :type="HAZARD_LEVELS[h.level]?.type">{{ HAZARD_LEVELS[h.level]?.label }}</el-tag>
          <el-tag :type="HAZARD_STATUS[h.status]?.type">{{ HAZARD_STATUS[h.status]?.label }}</el-tag>
          <el-tag v-if="h.overdue" type="danger" effect="dark">已逾期</el-tag>
        </div>
        <div class="title">{{ h.title }}</div>
        <div class="m-muted">{{ h.hazardNo }} · {{ h.category }}</div>
      </div>

      <div class="m-card">
        <div class="m-row"><span>位置</span><span>{{ h.location }}</span></div>
        <div class="m-row"><span>上报人</span><span>{{ h.reporterName }}　{{ shortTime(h.createTime) }}</span></div>
        <div v-if="h.rectifierName" class="m-row"><span>整改责任人</span><span>{{ h.rectifierName }}</span></div>
        <div v-if="h.deadline" class="m-row"><span>整改期限</span><span :class="{ red: h.overdue }">{{ h.deadline }}</span></div>
        <div v-if="h.description" class="desc">{{ h.description }}</div>
        <div v-if="h.rectifyMeasure" class="desc"><b>整改措施：</b>{{ h.rectifyMeasure }}</div>
      </div>

      <div class="m-card">
        <AttachmentPanel :key="`r-${h.id}-${h.status}`" biz-type="HAZARD" :biz-id="h.id" tag="REPORT" title="现场照片" image-only empty-text="没有现场照片" />
      </div>
      <div v-if="h.rectifierId" class="m-card">
        <AttachmentPanel :key="`f-${h.id}-${h.status}`" biz-type="HAZARD" :biz-id="h.id" tag="RECTIFY" title="整改照片" image-only
                         :empty-text="h.status === 'RECTIFYING' ? '整改完成后拍照上传，再提交整改' : '没有整改照片'" />
      </div>

      <div class="m-card">
        <h3>处理记录</h3>
        <div class="m-timeline">
          <div v-for="l in logs" :key="l.id" class="m-log">
            <b>{{ HAZARD_ACTIONS[l.action] }}</b>　{{ l.operatorName }}
            <div class="m-muted">{{ shortTime(l.createTime) }}</div>
            <div v-if="l.comment" class="comment">{{ l.comment }}</div>
          </div>
        </div>
      </div>

      <div v-if="actions.length" class="m-actions">
        <el-button v-for="a in actions" :key="a" :type="BTN[a] || 'default'" :plain="a === 'REVOKE' || a === 'VERIFY_FAIL'" @click="openAct(a)">
          {{ HAZARD_ACTIONS[a] }}
        </el-button>
      </div>
    </template>

    <el-drawer v-model="sheet" direction="btt" size="auto" :title="HAZARD_ACTIONS[act]" class="m-sheet" append-to-body destroy-on-close>
      <el-form label-position="top">
        <template v-if="act === 'ASSIGN'">
          <el-form-item label="整改责任人" required>
            <el-select v-model="form.rectifierId" filterable placeholder="选择人员" style="width: 100%">
              <el-option v-for="u in users" :key="u.id" :label="u.nickname" :value="u.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="整改期限" required>
            <el-date-picker v-model="form.deadline" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" :editable="false" style="width: 100%" />
          </el-form-item>
        </template>
        <el-alert v-if="act === 'RECTIFY'" type="info" :closable="false" title="提交前请先在上面“整改照片”里上传整改后的照片" style="margin-bottom: 12px" />
        <el-form-item :label="cfg.label" :required="cfg.required">
          <el-input v-model="form.text" type="textarea" :rows="3" maxlength="500" :placeholder="cfg.placeholder" />
        </el-form-item>
        <el-button :type="BTN[act] || 'primary'" class="m-sheet-btn" :loading="busy" @click="submit">确认{{ HAZARD_ACTIONS[act] }}</el-button>
      </el-form>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getHazard, hazardAction, listFiles, userOptions } from '@/api'
import AttachmentPanel from '@/components/AttachmentPanel.vue'
import { HAZARD_ACTIONS, HAZARD_LEVELS, HAZARD_STATUS, nowStr } from '@/utils/dict'
import { shortTime } from '@/utils/mobile'

const emit = defineEmits(['unread'])
const route = useRoute()
const loading = ref(false)
const h = ref(null)
const logs = ref([])
const actions = ref([])
const BTN = { ASSIGN: 'primary', RECTIFY: 'primary', VERIFY_PASS: 'success', VERIFY_FAIL: 'danger', REVOKE: 'info' }
const PATH = { ASSIGN: 'assign', REVOKE: 'revoke', RECTIFY: 'rectify', VERIFY_PASS: 'verify-pass', VERIFY_FAIL: 'verify-fail' }
// 每个动作的文字框：填到哪个字段、是否必填
const CFG = {
  ASSIGN: { key: 'comment', label: '备注（可选）', placeholder: '如：先停用阀门，再更换垫片' },
  RECTIFY: { key: 'rectifyMeasure', label: '整改措施', required: true, placeholder: '写清楚做了哪些整改' },
  VERIFY_PASS: { key: 'comment', label: '验收意见（可选）', placeholder: '如：现场复查已整改到位' },
  VERIFY_FAIL: { key: 'comment', label: '不通过原因', required: true, placeholder: '写明还有哪些问题' },
  REVOKE: { key: 'comment', label: '撤销原因', required: true, placeholder: '如：经核实不属于隐患' }
}
const cfg = computed(() => CFG[act.value] || {})

async function load() {
  loading.value = true
  try {
    const d = await getHazard(route.params.id)
    h.value = d.hazard
    logs.value = d.logs
    actions.value = d.actions
  } finally {
    loading.value = false
  }
}
onMounted(load)

const sheet = ref(false)
const act = ref('')
const busy = ref(false)
const users = ref([])
const form = reactive({})
async function openAct(a) {
  act.value = a
  Object.assign(form, { text: '', rectifierId: null, deadline: a === 'ASSIGN' ? nowStr(h.value.level === 'MAJOR' ? 3 : 7) : '' })
  sheet.value = true
  if (a === 'ASSIGN' && !users.value.length) users.value = await userOptions()
}
async function submit() {
  const a = act.value
  const text = form.text.trim()
  if (cfg.value.required && !text) return ElMessage.warning(`请填写${cfg.value.label}`)
  if (a === 'ASSIGN' && (!form.rectifierId || !form.deadline)) return ElMessage.warning('请选择整改责任人和整改期限')
  if (a === 'RECTIFY') {
    // 没有整改照片也允许提交（有的整改拍不了照），但先提醒一下
    const files = await listFiles({ bizType: 'HAZARD', bizId: h.value.id, tag: 'RECTIFY' })
    if (!files.files.length) {
      try {
        await ElMessageBox.confirm('还没有上传整改照片，确定直接提交吗？', '提示', { confirmButtonText: '直接提交', cancelButtonText: '去拍照' })
      } catch {
        return
      }
    }
  }
  const data = { [cfg.value.key]: text || undefined }
  if (a === 'ASSIGN') Object.assign(data, { rectifierId: form.rectifierId, deadline: form.deadline })
  busy.value = true
  try {
    await hazardAction(h.value.id, PATH[a], data)
    ElMessage.success(`${HAZARD_ACTIONS[a]}成功`)
    sheet.value = false
    emit('unread')
    await load()
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
.title { font-size: 17px; font-weight: 600; margin: 8px 0 4px; }
.desc { margin-top: 8px; padding: 8px 10px; background: #f6f8fb; border-radius: 6px; line-height: 1.7; white-space: pre-wrap; }
.red { color: #f5222d; font-weight: 600; }
</style>
