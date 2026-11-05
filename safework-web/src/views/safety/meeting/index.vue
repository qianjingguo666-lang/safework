<template>
  <div class="page-card">
    <div v-if="ov" class="stats">
      <div class="stat"><b>{{ ov.heldToday }}/{{ ov.teams }}</b><span>今天已开班前会的班组</span></div>
      <div class="stat" @click="filterUnfit"><b :class="ov.unfitToday ? 'danger' : ''">{{ ov.unfitToday }}</b><span>今天状态不好、不能上岗</span></div>
      <div class="stat"><b :class="ov.unconfirmedToday ? 'warn' : ''">{{ ov.unconfirmedToday }}</b><span>今天还没确认的人</span></div>
      <div class="stat"><b>{{ ov.rate30 == null ? '-' : ov.rate30 + '%' }}</b><span>最近 30 天开展率</span></div>
    </div>

    <el-tabs v-model="tab">
      <el-tab-pane label="班组" name="team">
        <div class="toolbar">
          <span class="muted">班组长每天开工前开班前会；手机上也能开（首页“班前会”）</span>
          <div class="spacer" />
          <el-button v-if="canEdit" type="primary" icon="Plus" @click="editTeam()">新增班组</el-button>
        </div>
        <div class="teams">
          <div v-for="t in teams" :key="t.team.id" class="team" :class="{ off: t.team.status !== 1 }">
            <div class="t-head">
              <b>{{ t.team.name }}</b>
              <el-tag v-if="t.team.status !== 1" size="small" type="info">已停用</el-tag>
              <el-tag v-else :type="t.today ? 'success' : 'warning'" size="small">{{ t.today ? '今天已开' : '今天未开' }}</el-tag>
            </div>
            <div class="muted">班组长 {{ t.team.leaderName }} · {{ t.members.length }} 人</div>
            <div class="rate">
              <el-progress :percentage="t.rate30 || 0" :stroke-width="8" :status="(t.rate30 || 0) >= 90 ? 'success' : (t.rate30 || 0) < 60 ? 'exception' : ''" />
              <span class="muted">最近 30 天开了 {{ t.held30 }} 天<template v-if="t.lastDate">，最近一次 {{ t.lastDate }}</template></span>
            </div>
            <div class="ops">
              <el-button v-if="t.canHold && t.team.status === 1" size="small" type="primary" @click="openHold(t)">开班前会</el-button>
              <el-button size="small" @click="showTeam(t)">记录</el-button>
              <template v-if="canEdit">
                <el-button size="small" link type="primary" @click="editTeam(t)">编辑</el-button>
                <el-button size="small" link :type="t.team.status === 1 ? 'warning' : 'success'" @click="toggle(t.team)">{{ t.team.status === 1 ? '停用' : '启用' }}</el-button>
              </template>
            </div>
          </div>
        </div>
        <el-empty v-if="!teams.length" description="还没有班组" />
      </el-tab-pane>

      <el-tab-pane label="班前会记录" name="record">
        <div class="toolbar">
          <el-select v-model="query.teamId" placeholder="班组" clearable style="width: 150px" @change="search">
            <el-option v-for="t in teams" :key="t.team.id" :label="t.team.name" :value="t.team.id" />
          </el-select>
          <el-checkbox v-model="query.unfit" @change="search">只看有人不能上岗的</el-checkbox>
          <el-button icon="Search" @click="search">查询</el-button>
        </div>
        <el-table v-loading="loading" :data="rows" stripe @row-click="(r) => openDetail(r.id)">
          <el-table-column label="日期" width="140"><template #default="{ row }">{{ row.meetingDate }} {{ MEETING_SHIFTS[row.shift] }}</template></el-table-column>
          <el-table-column prop="teamName" label="班组" width="120" />
          <el-table-column prop="hostName" label="主持" width="140" show-overflow-tooltip />
          <el-table-column label="安全要点" min-width="260" show-overflow-tooltip>
            <template #default="{ row }"><el-tag v-if="row.aiUsed" size="small" effect="plain">AI</el-tag> {{ row.topics }}</template>
          </el-table-column>
          <el-table-column label="出席 / 确认" width="110"><template #default="{ row }">{{ row.presentCount }}/{{ row.memberCount }} · {{ row.confirmedCount }}</template></el-table-column>
          <el-table-column label="不能上岗" width="90">
            <template #default="{ row }"><el-tag v-if="row.unfitCount" size="small" type="danger">{{ row.unfitCount }} 人</el-tag><span v-else class="muted">-</span></template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination v-model:current-page="query.current" :page-size="query.size" :total="total" layout="total, prev, pager, next" @current-change="load" />
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-drawer v-model="holdVisible" :title="`开班前会：${holdTeam?.team.name}`" size="620px" destroy-on-close>
      <HoldForm v-if="holdTeam" :team="holdTeam.team" :members="holdTeam.members" @saved="afterHold" />
    </el-drawer>

    <el-drawer v-model="dVisible" :title="d ? `${d.meeting.teamName} ${d.meeting.meetingDate} ${MEETING_SHIFTS[d.meeting.shift]}` : ''" size="620px" destroy-on-close>
      <template v-if="d">
        <div class="muted">主持 {{ d.meeting.hostName }} · {{ d.meeting.createTime?.slice(11, 16) }}<template v-if="d.meeting.aiUsed"> · 安全要点由 AI 起草</template></div>
        <h4>安全要点</h4>
        <div class="topics">{{ d.meeting.topics }}</div>
        <h4>点名（出席 {{ d.meeting.presentCount }}/{{ d.meeting.memberCount }}，已确认 {{ d.meeting.confirmedCount }}）</h4>
        <el-table :data="d.attendees" size="small" border>
          <el-table-column prop="userName" label="成员" />
          <el-table-column label="出勤" width="80"><template #default="{ row }"><el-tag size="small" :type="MEETING_ATTENDS[row.attend]?.type">{{ MEETING_ATTENDS[row.attend]?.label }}</el-tag></template></el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }"><el-tag v-if="row.bodyState" size="small" :type="MEETING_CONDITIONS[row.bodyState]?.type">{{ MEETING_CONDITIONS[row.bodyState]?.label }}</el-tag></template>
          </el-table-column>
          <el-table-column label="确认" width="130"><template #default="{ row }">{{ row.confirmTime ? row.confirmTime.slice(5, 16) : (row.attend === 'PRESENT' ? '未确认' : '-') }}</template></el-table-column>
          <el-table-column prop="note" label="备注" />
        </el-table>
        <el-button v-if="d.canConfirm" type="primary" class="confirm" @click="doConfirm">我已参加，清楚今天的安全要点</el-button>
        <div class="photos">
          <AttachmentPanel :key="d.meeting.id" biz-type="MEETING" :biz-id="d.meeting.id" tag="PHOTO" title="现场照片" image-only empty-text="主持人可以上传班前会现场照片" />
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="tVisible" :title="tForm.id ? '编辑班组' : '新增班组'" width="520px">
      <el-form label-width="80px">
        <el-form-item label="名称" required><el-input v-model="tForm.name" maxlength="30" /></el-form-item>
        <el-form-item label="班组长" required>
          <el-select v-model="tForm.leaderId" filterable style="width: 100%"><el-option v-for="u in users" :key="u.id" :label="u.nickname" :value="u.id" /></el-select>
        </el-form-item>
        <el-form-item label="成员" required>
          <el-select v-model="tForm.memberIds" multiple filterable style="width: 100%"><el-option v-for="u in users" :key="u.id" :label="u.nickname" :value="u.id" /></el-select>
          <div class="muted">班组长会自动加进成员</div>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="tForm.remark" maxlength="200" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="tVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveTeam">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { confirmMeeting, meetingDetail, meetingOverview, meetingPage, meetingTeams, meetingTeamStatus, saveMeetingTeam, userOptions } from '@/api'
import { MEETING_ATTENDS, MEETING_CONDITIONS, MEETING_SHIFTS } from '@/utils/dict'
import AttachmentPanel from '@/components/AttachmentPanel.vue'
import HoldForm from './HoldForm.vue'

const store = useUserStore()
const canEdit = computed(() => store.hasPerm('meeting:edit'))
const tab = ref('team')
const ov = ref(null)
const teams = ref([])
const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 20, teamId: null, unfit: false })
const holdVisible = ref(false)
const holdTeam = ref(null)
const dVisible = ref(false)
const d = ref(null)
const tVisible = ref(false)
const users = ref([])
const tForm = reactive({ id: null, name: '', leaderId: null, memberIds: [], remark: '' })

async function load() {
  loading.value = true
  try {
    const params = Object.fromEntries(Object.entries(query).filter(([, v]) => v !== '' && v != null && v !== false))
    const page = await meetingPage(params)
    rows.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}
function search() {
  query.current = 1
  load()
}
function reload() {
  meetingOverview().then((v) => { ov.value = v })
  meetingTeams().then((v) => { teams.value = v })
  load()
}
function filterUnfit() {
  tab.value = 'record'
  Object.assign(query, { teamId: null, unfit: true })
  search()
}
function showTeam(t) {
  tab.value = 'record'
  Object.assign(query, { teamId: t.team.id, unfit: false })
  search()
}
function openHold(t) {
  holdTeam.value = t
  holdVisible.value = true
}
function afterHold(id) {
  holdVisible.value = false
  reload()
  openDetail(id)
}
async function openDetail(id) {
  d.value = await meetingDetail(id)
  dVisible.value = true
}
async function doConfirm() {
  await confirmMeeting(d.value.meeting.id)
  ElMessage.success('已确认')
  openDetail(d.value.meeting.id)
  reload()
}
async function editTeam(t) {
  Object.assign(tForm, t ? { id: t.team.id, name: t.team.name, leaderId: t.team.leaderId, memberIds: t.members.map((m) => m.userId), remark: t.team.remark || '' }
    : { id: null, name: '', leaderId: null, memberIds: [], remark: '' })
  if (!users.value.length) users.value = await userOptions()
  tVisible.value = true
}
async function saveTeam() {
  if (!tForm.name.trim() || !tForm.leaderId || !tForm.memberIds.length) return ElMessage.warning('请填写名称、班组长和成员')
  saving.value = true
  try {
    const { id, ...data } = tForm
    await saveMeetingTeam(id, data)
    ElMessage.success('已保存')
    tVisible.value = false
    reload()
  } finally {
    saving.value = false
  }
}
async function toggle(t) {
  const to = t.status === 1 ? 0 : 1
  if (!to) await ElMessageBox.confirm(`停用【${t.name}】？停用后不再提醒开班前会。`, '停用', { type: 'warning' })
  await meetingTeamStatus(t.id, to)
  reload()
}
onMounted(reload)
</script>

<style scoped>
.stats { display: flex; gap: 12px; margin-bottom: 8px; flex-wrap: wrap; }
.stat { flex: 1; min-width: 130px; padding: 12px 16px; border: 1px solid #ebeef5; border-radius: 8px; display: flex; align-items: baseline; gap: 8px; cursor: pointer; }
.stat b { font-size: 24px; }
.stat span { color: #888; font-size: 13px; }
.warn { color: #e6a23c; }
.danger { color: #f56c6c; }
.muted { color: #999; font-size: 12px; }
.teams { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 12px; }
.team { border: 1px solid #ebeef5; border-radius: 8px; padding: 12px 14px; }
.team.off { opacity: 0.6; }
.t-head { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.rate { margin: 10px 0; }
.ops { display: flex; gap: 6px; flex-wrap: wrap; align-items: center; }
h4 { margin: 16px 0 8px; }
.topics { white-space: pre-wrap; line-height: 1.8; background: #f6f8fa; border-radius: 6px; padding: 10px 12px; }
.confirm { width: 100%; margin-top: 12px; }
.photos { margin-top: 16px; }
:deep(.el-table__row) { cursor: pointer; }
</style>
