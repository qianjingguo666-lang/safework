<template>
  <div class="flow-page">
    <div class="page-card intro">
      <b>作业票审批流程</b>
      <span>按作业类型配置多级审批：每个节点可以是「或签」（一人同意即通过）或「会签」（所有人都同意才通过），任何节点驳回都退回申请人。修改只影响之后提交的作业票，审批中的票按提交时的流程走完。</span>
    </div>

    <div v-loading="loading" class="cards">
      <div v-for="f in flows" :key="f.ticketType" class="page-card flow-card">
        <div class="card-head">
          <span class="type">{{ f.typeLabel }}</span>
          <el-tag v-if="f.configured" size="small" type="success">已配置 {{ f.nodes.length }} 级审批</el-tag>
          <el-tag v-else size="small" type="info">默认流程</el-tag>
          <div class="spacer" />
          <el-button v-if="f.configured" link type="info" @click="doReset(f)">恢复默认</el-button>
          <el-button type="primary" link icon="Edit" @click="openEdit(f)">配置</el-button>
        </div>

        <div class="steps">
          <template v-for="(n, i) in f.nodes" :key="i">
            <div class="step">
              <div class="idx">{{ i + 1 }}</div>
              <div class="body">
                <div class="name">
                  {{ n.name }}
                  <el-tag size="small" :type="n.mode === 'ALL' ? 'warning' : ''" effect="plain">{{ MODES[n.mode] }}</el-tag>
                </div>
                <div class="desc">{{ describe(n) }}</div>
                <div class="people" :class="{ empty: !f.approvers[i]?.length }">
                  {{ f.approvers[i]?.length ? '现在能审批：' + preview(f.approvers[i]) : '现在没有人能审批，请调整' }}
                </div>
              </div>
            </div>
            <el-icon v-if="i < f.nodes.length - 1" class="arrow"><ArrowRight /></el-icon>
          </template>
        </div>
        <div v-if="f.configured && f.updaterName" class="foot">最后修改：{{ f.updaterName }}</div>
      </div>
    </div>

    <el-dialog v-model="editVisible" :title="`配置审批流程：${editing?.typeLabel || ''}`" width="min(720px, 94vw)">
      <div class="nodes">
        <div v-for="(n, i) in nodes" :key="n.key" class="node">
          <div class="node-head">
            <span class="idx">{{ i + 1 }}</span>
            <el-input v-model="n.name" placeholder="节点名称，如：安全员审核" maxlength="20" style="width: 220px" />
            <div class="spacer" />
            <el-button link icon="Top" :disabled="i === 0" @click="move(i, -1)" />
            <el-button link icon="Bottom" :disabled="i === nodes.length - 1" @click="move(i, 1)" />
            <el-button link type="danger" icon="Delete" :disabled="nodes.length === 1" @click="nodes.splice(i, 1)" />
          </div>
          <div class="node-row">
            <span class="label">审批人</span>
            <el-radio-group v-model="n.approverType" @change="onTypeChange(n)">
              <el-radio-button value="ANY_APPROVER">所有有审批权限的人</el-radio-button>
              <el-radio-button value="ROLE">指定角色</el-radio-button>
              <el-radio-button value="USER">指定人员</el-radio-button>
            </el-radio-group>
          </div>
          <div v-if="n.approverType === 'ROLE'" class="node-row">
            <span class="label" />
            <el-select v-model="n.roleCode" placeholder="选择角色" style="width: 220px">
              <el-option v-for="r in roles" :key="r.roleCode" :label="r.roleName" :value="r.roleCode" />
            </el-select>
          </div>
          <div v-if="n.approverType === 'USER'" class="node-row">
            <span class="label" />
            <el-select v-model="n.userIds" multiple filterable placeholder="选择审批人（有作业票审批权限的员工）" style="flex: 1">
              <el-option v-for="u in candidates" :key="u.id" :label="u.nickname" :value="u.id">
                <span>{{ u.nickname }}</span><span class="opt-dept">{{ u.deptName }}</span>
              </el-option>
            </el-select>
          </div>
          <div class="node-row">
            <span class="label">方式</span>
            <el-radio-group v-model="n.mode">
              <el-radio value="ANY">或签（一人同意即通过）</el-radio>
              <el-radio value="ALL" :disabled="n.approverType === 'ANY_APPROVER'">会签（所有人都同意）</el-radio>
            </el-radio-group>
          </div>
        </div>
      </div>
      <el-button class="add" icon="Plus" :disabled="nodes.length >= 5" @click="addNode">添加审批节点（最多 5 个）</el-button>
      <div class="tip">申请人不能审批自己的票：如果某个节点除了申请人没有别的审批人，这张票会提交失败。</div>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listRoles, listTicketFlows, resetTicketFlow, saveTicketFlow, ticketFlowCandidates } from '@/api'
import { useUserStore } from '@/stores/user'

const MODES = { ANY: '或签', ALL: '会签' }
const store = useUserStore()
const loading = ref(false)
const flows = ref([])
const roles = ref([])
const candidates = ref([])

async function load() {
  loading.value = true
  try {
    const [f, c, r] = await Promise.all([
      listTicketFlows(),
      ticketFlowCandidates(),
      store.hasPerm('system:role:list') ? listRoles() : Promise.resolve([])
    ])
    flows.value = f
    candidates.value = c
    roles.value = r
  } finally {
    loading.value = false
  }
}

function describe(n) {
  if (n.approverType === 'ROLE') {
    return '角色：' + (roles.value.find((r) => r.roleCode === n.roleCode)?.roleName || n.roleCode)
  }
  if (n.approverType === 'USER') {
    return '指定 ' + (n.userIds?.length || 0) + ' 人'
  }
  return '有作业票审批权限的人（申请人除外）'
}
const preview = (names) => (names.length > 4 ? names.slice(0, 4).join('、') + ` 等 ${names.length} 人` : names.join('、'))

// ---------------- 编辑 ----------------
const editVisible = ref(false)
const editing = ref(null)
const nodes = ref([])
const saving = ref(false)
let seq = 0

function openEdit(f) {
  editing.value = f
  nodes.value = f.nodes.map((n) => ({ ...n, userIds: n.userIds ? [...n.userIds] : [], key: ++seq }))
  editVisible.value = true
}
function addNode() {
  nodes.value.push({ key: ++seq, name: '', approverType: 'ROLE', roleCode: '', userIds: [], mode: 'ANY' })
}
function move(i, d) {
  const list = nodes.value
  ;[list[i], list[i + d]] = [list[i + d], list[i]]
}
function onTypeChange(n) {
  if (n.approverType === 'ANY_APPROVER') n.mode = 'ANY'
}

async function doSave() {
  const body = nodes.value.map((n) => ({
    name: n.name,
    approverType: n.approverType,
    roleCode: n.approverType === 'ROLE' ? n.roleCode : null,
    userIds: n.approverType === 'USER' ? n.userIds : null,
    mode: n.mode
  }))
  saving.value = true
  try {
    await saveTicketFlow(editing.value.ticketType, body)
    ElMessage.success('已保存，之后提交的作业票按新流程审批')
    editVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function doReset(f) {
  await ElMessageBox.confirm(`【${f.typeLabel}】恢复为默认流程（有审批权限的人一人审批即可）？审批中的票不受影响。`, '恢复默认', { type: 'warning' })
  await resetTicketFlow(f.ticketType)
  ElMessage.success('已恢复默认流程')
  await load()
}

onMounted(load)
</script>

<style scoped>
.intro { display: flex; flex-direction: column; gap: 6px; color: #666; font-size: 13px; line-height: 1.7; }
.intro b { color: #1f2d3d; font-size: 15px; }
.cards { display: flex; flex-direction: column; gap: 16px; margin-top: 16px; }
.card-head { display: flex; align-items: center; flex-wrap: wrap; gap: 6px 10px; margin-bottom: 14px; }
.card-head .type { font-size: 16px; font-weight: 600; white-space: nowrap; }
.card-head .el-button + .el-button { margin-left: 0; }
.spacer { flex: 1; }
.steps { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; }
.step { display: flex; gap: 10px; padding: 12px 14px; border: 1px solid #e4e8f0; border-radius: 8px; background: #fafbfd; min-width: 220px; max-width: 300px; }
.idx { flex: none; width: 24px; height: 24px; border-radius: 50%; background: #2f6bff; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 600; }
.name { font-weight: 600; display: flex; align-items: center; gap: 6px; }
.desc { color: #666; font-size: 12px; margin-top: 4px; }
.people { color: #888; font-size: 12px; margin-top: 4px; }
.people.empty { color: #f56c6c; }
.arrow { color: #b0b8c6; font-size: 18px; }
.foot { margin-top: 12px; color: #999; font-size: 12px; }
.nodes { display: flex; flex-direction: column; gap: 12px; }
.node { border: 1px solid #e4e8f0; border-radius: 8px; padding: 12px; display: flex; flex-direction: column; gap: 10px; }
.node-head { display: flex; align-items: center; gap: 10px; }
.node-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.node-row .label { width: 48px; color: #666; font-size: 13px; flex: none; }
.opt-dept { float: right; color: #999; font-size: 12px; margin-left: 12px; }
.add { width: 100%; margin-top: 12px; border-style: dashed; }
.tip { margin-top: 10px; color: #999; font-size: 12px; }
@media (max-width: 768px) {
  .step { max-width: none; width: 100%; }
  .arrow { transform: rotate(90deg); margin: 0 auto; }
  .node-row .label { width: auto; }
}
</style>
