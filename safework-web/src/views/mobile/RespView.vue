<template>
  <div v-loading="loading">
    <template v-if="d">
      <div class="m-card">
        <div class="m-item-title big">{{ d.book.title }}</div>
        <div class="m-row"><span>责任人</span><span>{{ d.sign.userName }}</span></div>
        <div class="m-row"><span>岗位</span><span>{{ d.sign.posts || '-' }}</span></div>
        <div class="m-row"><span>签署截止</span><span>{{ d.book.deadline }}</span></div>
      </div>
      <div class="m-card">
        <h3>通用条款</h3>
        <div class="content">{{ d.book.content }}</div>
      </div>
      <div class="m-card">
        <h3>我的岗位安全职责</h3>
        <ol class="duties"><li v-for="(t, i) in d.duties" :key="i">{{ t }}</li></ol>
      </div>
      <div class="m-card">
        <h3>责任人签字</h3>
        <template v-if="d.signImage">
          <img :src="d.signImage" class="sig" alt="签名" />
          <div class="m-muted">{{ d.sign.signTime }} 签署<template v-if="d.sign.late">（逾期）</template></div>
        </template>
        <template v-else-if="d.canSign">
          <label class="agree"><input v-model="agree" type="checkbox" /> 我已认真阅读以上条款和岗位安全职责，承诺认真履行</label>
          <SignaturePad ref="padRef" class="pad" placeholder="请用手指在此处签名" />
          <el-button type="primary" class="m-btn" size="large" :loading="saving" @click="submit">确认签署</el-button>
        </template>
        <div v-else class="m-muted">这份责任书已结束签署</div>
      </div>
      <div v-if="d.sign.score != null" class="m-card">
        <h3>年度考核</h3>
        <div class="score">{{ d.sign.score }} 分 <el-tag :type="RESP_GRADES[d.sign.grade]" effect="dark">{{ d.sign.grade }}</el-tag></div>
        <div v-if="d.sign.assessComment" class="m-muted">{{ d.sign.assessComment }}</div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { respSign, respSignDetail } from '@/api'
import { RESP_GRADES } from '@/utils/dict'
import SignaturePad from '@/components/SignaturePad.vue'

const route = useRoute()
const loading = ref(false)
const saving = ref(false)
const d = ref(null)
const agree = ref(false)
const padRef = ref()

async function load() {
  loading.value = true
  try {
    d.value = await respSignDetail(route.params.id)
  } finally {
    loading.value = false
  }
}
async function submit() {
  if (!agree.value) return ElMessage.warning('请先勾选“我已认真阅读”')
  const signature = padRef.value?.toDataURL()
  if (!signature) return ElMessage.warning('请手写签名')
  saving.value = true
  try {
    await respSign(d.value.sign.id, signature)
    ElMessage.success('签署成功')
    await load()
  } finally {
    saving.value = false
  }
}
onMounted(load)
</script>

<style scoped>
h3 { margin: 0 0 8px; font-size: 15px; }
.big { font-size: 17px; margin-bottom: 6px; }
.content { white-space: pre-wrap; font-size: 14px; line-height: 1.8; color: #333; }
.duties { margin: 0; padding-left: 20px; font-size: 14px; line-height: 1.8; }
.agree { display: flex; gap: 6px; align-items: flex-start; font-size: 14px; margin-bottom: 8px; }
.agree input { margin-top: 4px; }
.pad { margin-bottom: 12px; }
.sig { height: 64px; border-bottom: 1px solid #ccc; display: block; margin-bottom: 4px; }
.score { font-size: 20px; font-weight: 600; margin-bottom: 4px; }
.m-btn { width: 100%; }
</style>
