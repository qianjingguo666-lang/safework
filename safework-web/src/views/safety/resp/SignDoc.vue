<template>
  <el-dialog v-model="visible" :title="d?.book.title || '安全生产责任书'" width="760px" top="5vh" destroy-on-close>
    <div v-loading="loading" class="doc">
      <template v-if="d">
        <div class="who">
          <span>责任人：<b>{{ d.sign.userName }}</b></span>
          <span>岗位：{{ d.sign.posts || '-' }}</span>
          <span>责任期：{{ d.book.year }} 年度</span>
          <span>签署截止：{{ d.book.deadline }}</span>
        </div>
        <h4>通用条款</h4>
        <div class="content">{{ d.book.content }}</div>
        <h4>岗位安全职责</h4>
        <ol class="duties">
          <li v-for="(t, i) in d.duties" :key="i">{{ t }}</li>
        </ol>

        <h4>责任人签字</h4>
        <div v-if="d.signImage" class="signed">
          <img :src="d.signImage" alt="签名" />
          <div class="meta">
            签署时间 {{ d.sign.signTime }}
            <el-tag v-if="d.sign.late" size="small" type="danger" effect="plain">逾期签署</el-tag>
            <div class="hash" :title="d.sign.signHash">签名指纹 SHA-256：{{ d.sign.signHash?.slice(0, 16) }}…</div>
          </div>
        </div>
        <template v-else-if="d.canSign">
          <el-checkbox v-model="agree">我已认真阅读以上条款和岗位安全职责，承诺认真履行</el-checkbox>
          <SignaturePad ref="padRef" class="pad" placeholder="请在此处手写签名（鼠标或触屏）" />
        </template>
        <div v-else class="muted">{{ d.book.status === 'CLOSED' ? '这份责任书已结束签署，没有签字' : '待本人签署' }}</div>

        <template v-if="d.sign.score != null">
          <h4>年度考核</h4>
          <div class="assess">
            <b>{{ d.sign.score }} 分</b>
            <el-tag :type="RESP_GRADES[d.sign.grade]" effect="dark">{{ d.sign.grade }}</el-tag>
            <span class="muted">考核人 {{ d.sign.assessorName }} · {{ d.sign.assessTime }}</span>
            <div v-if="d.sign.assessComment" class="comment">{{ d.sign.assessComment }}</div>
          </div>
        </template>
      </template>
    </div>
    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
      <el-button v-if="d?.canSign" type="primary" :loading="saving" @click="submit">确认签署</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { respSign, respSignDetail } from '@/api'
import { RESP_GRADES } from '@/utils/dict'
import SignaturePad from '@/components/SignaturePad.vue'

const emit = defineEmits(['signed'])
const visible = ref(false)
const loading = ref(false)
const saving = ref(false)
const d = ref(null)
const agree = ref(false)
const padRef = ref()

async function open(signId) {
  visible.value = true
  d.value = null
  agree.value = false
  loading.value = true
  try {
    d.value = await respSignDetail(signId)
  } finally {
    loading.value = false
  }
}
async function submit() {
  if (!agree.value) {
    ElMessage.warning('请先勾选“我已认真阅读”')
    return
  }
  const signature = padRef.value?.toDataURL()
  if (!signature) {
    ElMessage.warning('请手写签名')
    return
  }
  saving.value = true
  try {
    await respSign(d.value.sign.id, signature)
    ElMessage.success('签署成功')
    visible.value = false
    emit('signed')
  } finally {
    saving.value = false
  }
}
defineExpose({ open })
</script>

<style scoped>
.doc { min-height: 200px; font-size: 14px; line-height: 1.8; }
.who { display: flex; flex-wrap: wrap; gap: 6px 24px; padding: 10px 14px; background: #f6f8fa; border-radius: 6px; }
h4 { margin: 16px 0 6px; }
.content { white-space: pre-wrap; color: #333; }
.duties { margin: 0; padding-left: 22px; }
.signed { display: flex; align-items: center; gap: 20px; }
.signed img { height: 70px; border-bottom: 1px solid #ccc; }
.meta { color: #666; font-size: 13px; }
.hash { color: #999; font-size: 12px; }
.pad { margin-top: 8px; }
.assess b { font-size: 20px; margin-right: 8px; }
.assess .muted { margin-left: 10px; }
.comment { margin-top: 4px; color: #555; }
.muted { color: #999; }
</style>
