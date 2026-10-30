<template>
  <div>
    <div class="page-card status">
      <div class="stat">
        <span class="label">对话模型</span>
        <el-tag :type="status?.chatEnabled ? 'success' : 'info'">{{ status?.chatEnabled ? status.chatModel : '未配置' }}</el-tag>
      </div>
      <div class="stat">
        <span class="label">向量模型</span>
        <el-tag :type="status?.embeddingEnabled ? 'success' : 'info'">{{ status?.embeddingEnabled ? status.embeddingModel : '未配置（使用关键词检索）' }}</el-tag>
      </div>
      <div class="stat"><span class="label">文档</span><b>{{ status?.documentCount ?? '-' }}</b></div>
      <div class="stat"><span class="label">片段</span><b>{{ status?.chunkCount ?? '-' }}</b></div>
    </div>

    <div class="page-card">
      <el-tabs v-model="tab">
        <el-tab-pane label="制度文档" name="doc">
          <div class="toolbar">
            <el-input v-model="keyword" placeholder="文档标题" clearable style="width: 220px" @keyup.enter="loadDocs" @clear="loadDocs" />
            <el-button icon="Search" @click="loadDocs">查询</el-button>
            <div class="spacer" />
            <el-upload v-perm="'ai:knowledge:upload'" :show-file-list="false" accept=".pdf,.txt,.md" :http-request="doUpload" :disabled="uploading">
              <el-button type="primary" icon="Upload" :loading="uploading">{{ uploading ? '解析中…' : '上传文档（PDF / TXT / MD）' }}</el-button>
            </el-upload>
          </div>

          <el-table v-loading="loading" :data="docs" stripe>
            <el-table-column prop="title" label="标题" min-width="200" />
            <el-table-column label="类型" width="70"><template #default="{ row }">{{ row.fileType.toUpperCase() }}</template></el-table-column>
            <el-table-column label="大小" width="90"><template #default="{ row }">{{ size(row.fileSize) }}</template></el-table-column>
            <el-table-column prop="charCount" label="字数" width="90" />
            <el-table-column prop="chunkCount" label="片段" width="70" />
            <el-table-column label="向量" width="90">
              <template #default="{ row }">
                <el-tooltip v-if="row.errorMsg" :content="row.errorMsg"><el-tag type="warning">失败</el-tag></el-tooltip>
                <el-tag v-else :type="row.embedded ? 'success' : 'info'">{{ row.embedded ? '已生成' : '未生成' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="uploaderName" label="上传人" width="140" />
            <el-table-column prop="createTime" label="上传时间" width="170" />
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="preview(row)">查看片段</el-button>
                <el-button v-if="!row.embedded && status?.embeddingEnabled" v-perm="'ai:knowledge:upload'" link type="primary"
                           :loading="embedding === row.id" @click="doEmbed(row)">生成向量</el-button>
                <el-button v-perm="'ai:knowledge:remove'" link type="danger" @click="doRemove(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pager">
            <el-pagination v-model:current-page="docQuery.current" :page-size="docQuery.size" :total="docTotal" layout="total, prev, pager, next" @current-change="loadDocs" />
          </div>
        </el-tab-pane>

        <el-tab-pane label="问答记录" name="log">
          <div class="toolbar">
            <el-select v-model="logQuery.bizType" placeholder="类型" clearable style="width: 120px" @change="loadLogs">
              <el-option label="问答" value="CHAT" /><el-option label="智能体" value="AGENT" /><el-option label="作业票预审" value="REVIEW" />
            </el-select>
            <el-select v-model="logQuery.hit" placeholder="是否命中" clearable style="width: 160px" @change="loadLogs">
              <el-option label="命中知识库" :value="1" /><el-option label="未命中（需要补充制度）" :value="0" />
            </el-select>
            <el-input v-model="logQuery.keyword" placeholder="问题关键词" clearable style="width: 200px" @keyup.enter="loadLogs" @clear="loadLogs" />
            <el-button icon="Search" @click="loadLogs">查询</el-button>
          </div>
          <el-table v-loading="logLoading" :data="logs" stripe>
            <el-table-column type="expand">
              <template #default="{ row }">
                <div class="expand">
                  <div><b>回答：</b><span class="pre">{{ row.answer || '-' }}</span></div>
                  <div v-if="row.bizType === 'AGENT'">
                    <b>调用过程：</b>
                    <template v-if="agentTrace(row)">
                      <span>{{ agentTrace(row).rounds }} 轮，{{ agentTrace(row).tokens }} tokens</span>
                      <div v-for="(s, i) in agentTrace(row).steps" :key="i" class="step-line">
                        {{ i + 1 }}. {{ s.tool }}（{{ s.args }}）→ <span :class="{ err: !s.ok }">{{ s.summary }}</span>
                      </div>
                    </template>
                    <span v-else>-</span>
                  </div>
                  <div v-else><b>引用：</b>{{ row.sources }}</div>
                  <div v-if="row.errorMsg" class="err"><b>错误：</b>{{ row.errorMsg }}</div>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="时间" width="170" />
            <el-table-column label="类型" width="100"><template #default="{ row }">{{ BIZ_TYPES[row.bizType] || row.bizType }}</template></el-table-column>
            <el-table-column prop="userName" label="提问人" width="140" />
            <el-table-column prop="question" label="问题" min-width="240" show-overflow-tooltip />
            <el-table-column label="命中" width="80">
              <template #default="{ row }"><el-tag :type="row.hit ? 'success' : 'warning'">{{ row.hit ? '是' : '否' }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="model" label="模型" width="100" />
            <el-table-column label="耗时" width="90"><template #default="{ row }">{{ row.costMs }} ms</template></el-table-column>
          </el-table>
          <div class="pager">
            <el-pagination v-model:current-page="logQuery.current" :page-size="logQuery.size" :total="logTotal" layout="total, prev, pager, next" @current-change="loadLogs" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-drawer v-model="chunkVisible" :title="`片段预览：${chunkDoc?.title || ''}`" size="560px">
      <div v-for="c in chunks" :key="c.id" class="chunk">
        <div class="chunk-head">#{{ c.chunkIndex + 1 }}<span v-if="c.pageNo"> · 第 {{ c.pageNo }} 页</span> · {{ c.content.length }} 字</div>
        <div class="pre">{{ c.content }}</div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { aiStatus, embedKnowledge, knowledgeChunks, pageChatLogs, pageKnowledge, removeKnowledge, uploadKnowledge } from '@/api'

const tab = ref('doc')
const status = ref(null)
const loadStatus = async () => { status.value = await aiStatus() }

const loading = ref(false)
const docs = ref([])
const docTotal = ref(0)
const keyword = ref('')
const docQuery = reactive({ current: 1, size: 10 })
async function loadDocs() {
  loading.value = true
  try {
    const page = await pageKnowledge({ ...docQuery, keyword: keyword.value || undefined })
    docs.value = page.records
    docTotal.value = page.total
  } finally {
    loading.value = false
  }
}
const size = (b) => (b > 1024 * 1024 ? `${(b / 1024 / 1024).toFixed(1)} MB` : `${Math.max(1, Math.round(b / 1024))} KB`)

const uploading = ref(false)
async function doUpload({ file }) {
  uploading.value = true
  try {
    const doc = await uploadKnowledge(file)
    if (doc.errorMsg) ElMessage.warning(`已上传，切分为 ${doc.chunkCount} 个片段；${doc.errorMsg}`)
    else ElMessage.success(`上传成功，切分为 ${doc.chunkCount} 个片段${doc.embedded ? '，已生成向量' : ''}`)
    loadDocs()
    loadStatus()
  } finally {
    uploading.value = false
  }
}
const embedding = ref(null)
async function doEmbed(row) {
  embedding.value = row.id
  try {
    await embedKnowledge(row.id)
    ElMessage.success('向量已生成，检索将使用“向量 + 关键词”混合检索')
    loadDocs()
  } finally {
    embedding.value = null
  }
}
async function doRemove(row) {
  try {
    await ElMessageBox.confirm(`删除后 AI 将不再引用《${row.title}》，确认删除？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  await removeKnowledge(row.id)
  ElMessage.success('已删除')
  loadDocs()
  loadStatus()
}

const chunkVisible = ref(false)
const chunkDoc = ref(null)
const chunks = ref([])
async function preview(row) {
  chunkDoc.value = row
  chunks.value = await knowledgeChunks(row.id)
  chunkVisible.value = true
}

const logLoading = ref(false)
const logs = ref([])
const logTotal = ref(0)
const BIZ_TYPES = { CHAT: '问答', AGENT: '智能体', REVIEW: '作业票预审' }
// 智能体记录的 sources 是 JSON：{ rounds, tokens, steps: [{ tool, args, ok, summary }] }
function agentTrace(row) {
  try { return row.sources ? JSON.parse(row.sources) : null } catch { return null }
}
const logQuery = reactive({ current: 1, size: 20, bizType: '', hit: null, keyword: '' })
async function loadLogs() {
  logLoading.value = true
  try {
    const params = Object.fromEntries(Object.entries(logQuery).filter(([, v]) => v !== '' && v != null))
    const page = await pageChatLogs(params)
    logs.value = page.records
    logTotal.value = page.total
  } finally {
    logLoading.value = false
  }
}
watch(tab, (t) => { if (t === 'log') loadLogs() })

onMounted(() => {
  loadStatus()
  loadDocs()
})
</script>

<style scoped>
.status { display: flex; flex-wrap: wrap; gap: 32px; margin-bottom: 16px; align-items: center; }
.stat { display: flex; align-items: center; gap: 8px; }
.label { color: #888; }
.expand { padding: 4px 48px; line-height: 1.9; font-size: 13px; color: #555; }
.pre { white-space: pre-wrap; }
.err { color: #f56c6c; }
.chunk { border: 1px solid #ebeef5; border-radius: 6px; padding: 10px 12px; margin-bottom: 10px; font-size: 13px; line-height: 1.7; }
.chunk-head { color: #999; font-size: 12px; margin-bottom: 4px; }
.step-line { color: #555; margin-left: 12px; }
</style>
