<template>
  <div>
    <VoiceFill ref="voiceRef" @fill="onFill" />

    <div class="m-card">
      <h3>现场照片 <span class="m-muted">{{ photos.length }} / {{ MAX_PHOTOS }}</span></h3>
      <div class="photos">
        <div v-for="(p, i) in photos" :key="p.url" class="photo">
          <img :src="p.url" alt="现场照片">
          <el-icon class="del" @click="removePhoto(i)"><CircleCloseFilled /></el-icon>
        </div>
        <label v-if="photos.length < MAX_PHOTOS" class="add">
          <el-icon><Camera /></el-icon>
          <span>拍照 / 相册</span>
          <input type="file" accept="image/*" multiple @change="pick">
        </label>
      </div>
    </div>

    <div class="m-card">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="form">
        <el-form-item label="隐患标题" prop="title" :class="{ ai: ai.has('title') }">
          <el-input v-model="form.title" maxlength="100" placeholder="一句话说清楚是什么问题" />
        </el-form-item>
        <el-form-item label="位置" prop="location" :class="{ ai: ai.has('location') }">
          <el-input v-model="form.location" maxlength="100" placeholder="如：二号储罐区东侧" />
        </el-form-item>
        <el-form-item label="关联受限空间（可选）" :class="{ ai: ai.has('spaceId') }">
          <el-select v-model="form.spaceId" clearable filterable placeholder="不是受限空间的隐患不用选" style="width: 100%" @change="onSpace">
            <el-option v-for="s in spaces" :key="s.id" :label="`${s.spaceCode} ${s.spaceName}`" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="类别" prop="category" :class="{ ai: ai.has('category') }">
          <div class="chips">
            <button v-for="c in HAZARD_CATEGORIES" :key="c" type="button" :class="{ on: form.category === c }" @click="form.category = c">{{ c }}</button>
          </div>
        </el-form-item>
        <el-form-item label="等级" prop="level" :class="{ ai: ai.has('level') }">
          <div class="chips">
            <button v-for="(v, k) in HAZARD_LEVELS" :key="k" type="button" :class="{ on: form.level === k, major: k === 'MAJOR' }" @click="form.level = k">{{ v.label }}</button>
          </div>
        </el-form-item>
        <el-form-item label="详细描述（可选）" :class="{ ai: ai.has('description') }">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="1000" />
        </el-form-item>
      </el-form>
    </div>

    <div class="m-actions">
      <el-button type="danger" :loading="saving" @click="submit">{{ saving ? progress : '上报隐患' }}</el-button>
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { reportHazard, spaceOptions, uploadFile } from '@/api'
import VoiceFill from '@/views/safety/hazard/VoiceFill.vue'
import { HAZARD_CATEGORIES, HAZARD_LEVELS } from '@/utils/dict'
import { compressImage } from '@/utils/mobile'

const MAX_PHOTOS = 6
const route = useRoute()
const router = useRouter()
const formRef = ref()
const voiceRef = ref()
const spaces = ref([])
const form = reactive({ title: '', location: '', spaceId: null, category: '作业环境', level: 'GENERAL', description: '' })
const rules = {
  title: [{ required: true, message: '请填写隐患标题', trigger: 'blur' }],
  location: [{ required: true, message: '请填写位置', trigger: 'blur' }],
  category: [{ required: true, message: '请选择类别' }],
  level: [{ required: true, message: '请选择等级' }]
}

onMounted(async () => {
  spaces.value = await spaceOptions()
  // 从受限空间二维码页点“上报隐患”进来：/m/report?space=CS-001，自动选上这个空间
  const code = route.query.space
  const sp = code && spaces.value.find((s) => s.spaceCode === code)
  if (sp) {
    form.spaceId = sp.id
    form.location = sp.location || sp.spaceName
  }
})

// 一句话上报：AI 提取的字段填进表单，紫色底色提示“请核对”
const ai = ref(new Set())
function onFill(d) {
  const filled = new Set()
  for (const k of ['title', 'category', 'level', 'location', 'description']) {
    if (d[k]) {
      form[k] = d[k]
      filled.add(k)
    }
  }
  if (d.spaceId && spaces.value.some((s) => s.id === d.spaceId)) {
    form.spaceId = d.spaceId
    filled.add('spaceId')
  }
  ai.value = filled
  formRef.value?.clearValidate()
}
function onSpace(id) {
  const sp = spaces.value.find((s) => s.id === id)
  if (sp && !form.location) form.location = sp.location
}

// 照片先存在本地预览，上报成功后再逐张上传（隐患还没创建时没有 ID）
const photos = ref([])
async function pick(e) {
  const files = [...e.target.files].slice(0, MAX_PHOTOS - photos.value.length)
  e.target.value = ''
  for (const f of files) {
    const file = await compressImage(f)
    photos.value.push({ file, url: URL.createObjectURL(file) })
  }
}
function removePhoto(i) {
  URL.revokeObjectURL(photos.value[i].url)
  photos.value.splice(i, 1)
}
onBeforeUnmount(() => photos.value.forEach((p) => URL.revokeObjectURL(p.url)))

const saving = ref(false)
const progress = ref('')
async function submit() {
  try {
    await formRef.value.validate()
  } catch {
    return ElMessage.warning('请把标题、位置填写完整')
  }
  saving.value = true
  progress.value = '正在上报…'
  try {
    const id = await reportHazard(form)
    let failed = 0
    for (let i = 0; i < photos.value.length; i++) {
      progress.value = `上传照片 ${i + 1}/${photos.value.length}…`
      try {
        await uploadFile('HAZARD', id, 'REPORT', photos.value[i].file)
      } catch {
        failed++
      }
    }
    ElMessage.success(failed ? `已上报，${failed} 张照片上传失败，可以在详情里重新上传` : '已上报，等待安全员指派整改')
    voiceRef.value?.cancel()
    router.replace(`/m/hazard/${id}`)
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.photos { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
.photo, .add { position: relative; aspect-ratio: 1; border-radius: 8px; overflow: hidden; }
.photo img { width: 100%; height: 100%; object-fit: cover; }
.del { position: absolute; top: 4px; right: 4px; font-size: 22px; color: #fff; background: rgba(0, 0, 0, .45); border-radius: 50%; }
.add { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4px; border: 1px dashed #c0c4cc; color: #8a8f99; font-size: 12px; background: #fafbfc; }
.add .el-icon { font-size: 28px; }
.add input { position: absolute; inset: 0; opacity: 0; }
.form :deep(.el-form-item__label) { padding-bottom: 4px; }
.chips { display: flex; flex-wrap: wrap; gap: 8px; }
.chips button { border: 1px solid #dcdfe6; background: #fff; color: #555; padding: 6px 14px; border-radius: 16px; font-size: 14px; }
.chips button.on { border-color: #1f6feb; background: #1f6feb; color: #fff; }
.chips button.major.on { border-color: #f5222d; background: #f5222d; }
.ai :deep(.el-input__wrapper), .ai :deep(.el-textarea__inner), .ai :deep(.el-select__wrapper) { background: #f6f3ff; box-shadow: 0 0 0 1px #c9bcff inset; }
.ai .chips button.on { box-shadow: 0 0 0 2px #c9bcff; }
</style>
