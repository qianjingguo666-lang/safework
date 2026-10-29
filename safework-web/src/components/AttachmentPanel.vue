<template>
  <div class="att">
    <div class="att-head">
      <span class="att-title">{{ title }}</span>
      <span class="muted">{{ files.length }}{{ editable ? ` / ${maxCount}` : '' }}</span>
      <div class="spacer" />
      <el-upload v-if="editable && files.length < maxCount" :show-file-list="false" :http-request="doUpload"
                 :accept="imageOnly ? 'image/jpeg,image/png,image/webp' : '.jpg,.jpeg,.png,.webp,.pdf,.doc,.docx,.xls,.xlsx'"
                 :before-upload="beforeUpload" multiple>
        <el-button size="small" icon="Upload" :loading="uploading > 0">{{ imageOnly ? '上传照片' : '上传附件' }}</el-button>
      </el-upload>
    </div>

    <div v-if="loading" class="muted">加载中…</div>
    <div v-else-if="!files.length" class="muted empty">{{ emptyText }}</div>

    <!-- 图片：缩略图，点击放大 -->
    <div v-else-if="imageOnly" class="thumbs">
      <div v-for="(f, i) in files" :key="f.id" class="thumb">
        <el-image :src="urls[f.id]" fit="cover" :preview-src-list="previewList" :initial-index="i" preview-teleported>
          <template #placeholder><div class="ph">…</div></template>
          <template #error><div class="ph">加载失败</div></template>
        </el-image>
        <el-icon v-if="canDelete(f)" class="del" title="删除" @click.stop="remove(f)"><CircleCloseFilled /></el-icon>
      </div>
    </div>

    <!-- 其他文件：列表 -->
    <div v-else class="list">
      <div v-for="f in files" :key="f.id" class="row">
        <el-icon><Document /></el-icon>
        <el-link type="primary" @click="download(f)">{{ f.originalName }}</el-link>
        <span class="muted">{{ size(f.size) }} · {{ f.uploaderName }} · {{ f.createTime }}</span>
        <div class="spacer" />
        <el-button v-if="canDelete(f)" link type="danger" size="small" @click="remove(f)">删除</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listFiles, removeFile, uploadFile } from '@/api'
import { downloadFile, fileObjectUrl } from '@/utils/download'
import { useUserStore } from '@/stores/user'

/**
 * 业务单据上的附件。能不能上传由后端根据业务规则判断（list 接口返回 editable），这里只负责显示。
 * 图片通过带 token 的请求取回再转成 blob 地址显示（<img> 标签自己没法带 Authorization 请求头）
 */
const props = defineProps({
  bizType: { type: String, required: true },
  bizId: { type: [String, Number], required: true },
  tag: { type: String, required: true },
  title: { type: String, default: '附件' },
  imageOnly: { type: Boolean, default: false },
  emptyText: { type: String, default: '暂无附件' }
})
const emit = defineEmits(['changed'])

const store = useUserStore()
const files = ref([])
const editable = ref(false)
const maxCount = ref(9)
const loading = ref(false)
const uploading = ref(0)
const urls = ref({})
const previewList = computed(() => files.value.map((f) => urls.value[f.id]).filter(Boolean))

async function load() {
  loading.value = true
  try {
    const data = await listFiles({ bizType: props.bizType, bizId: props.bizId, tag: props.tag })
    files.value = data.files
    editable.value = data.editable
    maxCount.value = data.maxCount
    if (props.imageOnly) loadThumbs()
  } finally {
    loading.value = false
  }
}

async function loadThumbs() {
  for (const f of files.value) {
    if (!urls.value[f.id]) {
      try {
        urls.value = { ...urls.value, [f.id]: await fileObjectUrl(f.id) }
      } catch { /* 显示“加载失败” */ }
    }
  }
}

function beforeUpload(file) {
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.warning(`${file.name} 超过 10MB`)
    return false
  }
  return true
}

async function doUpload({ file }) {
  uploading.value++
  try {
    await uploadFile(props.bizType, props.bizId, props.tag, file)
    await load()
    emit('changed')
  } finally {
    uploading.value--
  }
}

const canDelete = (f) => editable.value && String(f.uploaderId) === String(store.user?.id)

async function remove(f) {
  try {
    await ElMessageBox.confirm(`删除【${f.originalName}】？`, '删除附件', { type: 'warning' })
  } catch {
    return
  }
  await removeFile(f.id)
  if (urls.value[f.id]) URL.revokeObjectURL(urls.value[f.id])
  await load()
  emit('changed')
}

const download = (f) => downloadFile(`/file/${f.id}`, { download: true }, f.originalName)
const size = (n) => (n > 1024 * 1024 ? `${(n / 1024 / 1024).toFixed(1)} MB` : `${Math.max(1, Math.round(n / 1024))} KB`)

watch(() => [props.bizId, props.tag], load, { immediate: true })
onBeforeUnmount(() => Object.values(urls.value).forEach((u) => URL.revokeObjectURL(u)))
defineExpose({ reload: load })
</script>

<style scoped>
.att { margin-top: 12px; }
.att-head { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.att-title { font-weight: 600; font-size: 14px; }
.spacer { flex: 1; }
.muted { color: #999; font-size: 12px; }
.empty { padding: 6px 0; }
.thumbs { display: flex; flex-wrap: wrap; gap: 8px; }
.thumb { position: relative; width: 88px; height: 88px; border-radius: 6px; overflow: hidden; background: #f3f5f8; }
.thumb .el-image { width: 100%; height: 100%; }
.ph { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; color: #aaa; font-size: 12px; }
.del { position: absolute; top: 2px; right: 2px; color: #f56c6c; background: #fff; border-radius: 50%; cursor: pointer; font-size: 18px; }
.list .row { display: flex; align-items: center; gap: 8px; padding: 4px 0; flex-wrap: wrap; }
</style>
