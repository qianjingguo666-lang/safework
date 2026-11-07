<template>
  <el-drawer :model-value="modelValue" title="入厂预约详情" size="560px" @update:model-value="emit('update:modelValue', $event)" @open="load">
    <div v-loading="loading">
      <template v-if="d">
        <div class="head">
          <el-tag :type="VISIT_TYPES[p.visitType]?.type">{{ VISIT_TYPES[p.visitType]?.label }}</el-tag>
          <el-tag :type="VISIT_STATUS[p.status]?.type" effect="dark">{{ VISIT_STATUS[p.status]?.label }}</el-tag>
          <el-tag v-if="d.overstay" type="danger">超时未离厂</el-tag>
          <span class="muted">{{ p.passNo }}</span>
        </div>
        <el-alert v-if="d.problems.length" type="error" :closable="false" show-icon title="不符合入厂条件" class="mb">
          <div v-for="x in d.problems" :key="x">· {{ x }}</div>
        </el-alert>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item :label="p.visitType === 'VISITOR' ? '来访人' : '驾驶员'">{{ p.visitorName }}（{{ p.peopleCount }} 人）</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ p.phone }}</el-descriptions-item>
          <el-descriptions-item label="身份证">{{ p.idMasked }}</el-descriptions-item>
          <el-descriptions-item label="车牌">{{ p.plateNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="单位" :span="2">{{ p.company }}</el-descriptions-item>
          <el-descriptions-item label="事由" :span="2">{{ p.purpose }}</el-descriptions-item>
          <el-descriptions-item label="来访日期">{{ p.planDate }}</el-descriptions-item>
          <el-descriptions-item label="接待人">{{ p.hostName }}</el-descriptions-item>
          <template v-if="p.visitType === 'HAZMAT'">
            <el-descriptions-item label="货物" :span="2">{{ p.cargoName }} {{ p.cargoUnNo }} · {{ p.cargoQty }} 吨 → {{ p.destination || '-' }}</el-descriptions-item>
            <el-descriptions-item label="驾驶员资格证" :span="2">{{ p.driverCertNo || '-' }}（有效期至 {{ p.driverCertExpire || '-' }}）</el-descriptions-item>
            <el-descriptions-item label="押运员" :span="2">{{ p.escortName || '-' }} {{ p.escortCertNo || '' }}（有效期至 {{ p.escortCertExpire || '-' }}）</el-descriptions-item>
            <el-descriptions-item label="道路运输证" :span="2">{{ p.transportPermitNo || '-' }}（有效期至 {{ p.transportPermitExpire || '-' }}）</el-descriptions-item>
            <el-descriptions-item label="审批" :span="2">{{ p.approverName ? `${p.approverName} ${p.approveTime?.slice(0, 16)}` : '待审批' }}{{ p.rejectReason ? '，驳回：' + p.rejectReason : '' }}</el-descriptions-item>
          </template>
          <el-descriptions-item label="登记人">{{ p.creatorName }}</el-descriptions-item>
          <el-descriptions-item label="备注">{{ p.remark || '-' }}</el-descriptions-item>
          <el-descriptions-item v-if="p.enterTime" label="入厂">{{ p.enterTime?.slice(0, 16) }}（{{ p.enterGuard }}）</el-descriptions-item>
          <el-descriptions-item v-if="p.leaveTime" label="离厂">{{ p.leaveTime?.slice(0, 16) }}（{{ p.leaveGuard }}）</el-descriptions-item>
        </el-descriptions>
        <div v-if="p.signImage" class="sign">
          <div class="muted">已阅读《入厂安全须知》并签名（{{ p.signTime?.slice(0, 16) }}）</div>
          <img :src="p.signImage" alt="签名" />
        </div>

        <template v-if="d.canGate && p.status === 'APPROVED' && !d.problems.length">
          <el-divider content-position="left">入厂登记：请来访人阅读后签名</el-divider>
          <ol class="notice"><li v-for="n in d.notice" :key="n">{{ n }}</li></ol>
          <SignaturePad ref="padRef" class="pad" placeholder="来访人在此手写签名" />
          <el-button type="primary" class="wide" :loading="busy" @click="enter">核对证件无误，登记入厂</el-button>
        </template>
        <div class="actions">
          <el-button v-if="d.canGate && p.status === 'ENTERED'" type="success" :loading="busy" @click="leave">登记离厂</el-button>
          <el-button v-if="d.canApprove" type="primary" :loading="busy" @click="approve">审批通过</el-button>
          <el-button v-if="d.canApprove" type="danger" plain @click="reject">驳回</el-button>
          <el-button v-if="d.canCancel" @click="cancel">取消预约</el-button>
        </div>
      </template>
    </div>
  </el-drawer>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { approveVisit, cancelVisit, rejectVisit, visitDetail, visitEnter, visitLeave } from '@/api'
import SignaturePad from '@/components/SignaturePad.vue'
import { VISIT_STATUS, VISIT_TYPES } from './visit'

const props = defineProps({ modelValue: Boolean, id: { type: [String, Number], default: null } })
const emit = defineEmits(['update:modelValue', 'changed'])
const d = ref(null)
const loading = ref(false)
const busy = ref(false)
const padRef = ref(null)
const p = computed(() => d.value?.pass || {})

async function load() {
  if (!props.id) return
  loading.value = true
  try {
    d.value = await visitDetail(props.id)
  } finally {
    loading.value = false
  }
}
async function act(fn, msg) {
  busy.value = true
  try {
    const res = await fn()
    ElMessage.success(msg + (res?.tip ? '。' + res.tip : ''))
    emit('changed')
    load()
  } finally {
    busy.value = false
  }
}
function enter() {
  if (padRef.value.isEmpty()) return ElMessage.warning('请来访人先手写签名')
  act(() => visitEnter(props.id, padRef.value.toDataURL()), '已登记入厂')
}
const leave = () => act(() => visitLeave(props.id), '已登记离厂')
const approve = () => act(() => approveVisit(props.id), '已审批通过')
async function reject() {
  const { value } = await ElMessageBox.prompt('驳回原因', '驳回', { inputValidator: (v) => (v && v.trim() ? true : '请填写原因') })
  act(() => rejectVisit(props.id, value), '已驳回')
}
async function cancel() {
  await ElMessageBox.confirm('取消这次预约？', '取消预约', { type: 'warning' })
  act(() => cancelVisit(props.id), '已取消')
}
</script>

<style scoped>
.head { display: flex; gap: 8px; align-items: center; margin-bottom: 12px; }
.muted { color: #999; font-size: 12px; }
.mb { margin-bottom: 12px; }
.sign { margin-top: 12px; }
.sign img { display: block; max-width: 260px; border: 1px solid #eee; border-radius: 4px; margin-top: 4px; background: #fff; }
.notice { padding-left: 18px; margin: 0 0 10px; font-size: 13px; line-height: 1.8; color: #444; }
.pad { margin-bottom: 10px; }
.wide { width: 100%; }
.actions { margin-top: 16px; display: flex; gap: 8px; flex-wrap: wrap; }
</style>
