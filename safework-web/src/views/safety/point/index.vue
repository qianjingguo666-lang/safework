<template>
  <div class="page-card">
    <div v-if="ov" class="stats">
      <div class="stat"><b>{{ ov.yearHazards }}</b><span>今年被确认的隐患</span></div>
      <div class="stat"><b>{{ ov.reporters }}</b><span>参与上报人数</span></div>
      <div class="stat"><b>{{ ov.yearPoints }}</b><span>今年发出积分</span></div>
      <div class="stat" @click="tab = 'redeem'"><b :class="{ warn: ov.pending }">{{ ov.pending }}</b><span>兑换待发放</span></div>
      <div class="stat"><b>{{ yuan(ov.issuedAmount) }}</b><span>已发放奖品金额（元，计入安全投入）</span></div>
    </div>
    <div class="rule muted">
      积分规则：上报的隐患被安全员确认（指派整改）后，上报人得分：一般隐患 <b>10</b> 分、重大隐患 <b>50</b> 分；整改责任人按期完成整改得 <b>5</b> 分。
      员工在手机“我的 → 我的积分”里看排名、兑换奖品；发放后奖品金额自动记入安全投入（安全教育培训类：隐患报告奖励）。
    </div>

    <el-tabs v-model="tab">
      <el-tab-pane label="排行榜" name="rank">
        <div class="toolbar">
          <el-radio-group v-model="period" @change="loadRank">
            <el-radio-button value="YEAR">今年</el-radio-button>
            <el-radio-button value="MONTH">本月</el-radio-button>
          </el-radio-group>
          <span v-if="rank" class="muted">{{ rank.label }}：确认隐患 {{ rank.totalHazards }} 条，发出积分 {{ rank.totalPoints }} 分</span>
          <div class="spacer" />
          <el-button v-if="canEdit" icon="Edit" @click="openAdjust()">手工调整积分</el-button>
        </div>
        <el-table v-loading="rLoading" :data="rank?.rows || []" stripe empty-text="这个时间段还没有人得分">
          <el-table-column label="名次" width="80"><template #default="{ row }"><span class="rank">{{ MEDALS[row.rank - 1] || row.rank }}</span></template></el-table-column>
          <el-table-column prop="userName" label="员工" min-width="160" />
          <el-table-column prop="deptName" label="部门" width="140" />
          <el-table-column prop="points" label="得分" width="100"><template #default="{ row }"><b>{{ row.points }}</b></template></el-table-column>
          <el-table-column prop="hazards" label="被确认的隐患" width="120" />
          <el-table-column v-if="canEdit" label="操作" width="110">
            <template #default="{ row }"><el-button link type="primary" @click="openAdjust(row.userId)">调整积分</el-button></template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="积分流水" name="records" lazy>
        <div class="toolbar">
          <el-select v-model="rq.userId" placeholder="员工" clearable filterable style="width: 180px" @change="searchRecords">
            <el-option v-for="u in users" :key="u.id" :label="u.nickname" :value="u.id" />
          </el-select>
          <el-select v-model="rq.sourceType" placeholder="类型" clearable style="width: 130px" @change="searchRecords">
            <el-option v-for="(v, k) in SOURCE_TYPES" :key="k" :label="v.label" :value="k" />
          </el-select>
        </div>
        <el-table v-loading="recLoading" :data="records" stripe>
          <el-table-column label="时间" width="160"><template #default="{ row }">{{ row.createTime?.slice(0, 16) }}</template></el-table-column>
          <el-table-column prop="userName" label="员工" width="150" />
          <el-table-column label="类型" width="100">
            <template #default="{ row }"><el-tag size="small" :type="SOURCE_TYPES[row.sourceType]?.type">{{ SOURCE_TYPES[row.sourceType]?.label }}</el-tag></template>
          </el-table-column>
          <el-table-column label="积分" width="80"><template #default="{ row }"><b :class="row.points > 0 ? 'plus' : 'minus'">{{ row.points > 0 ? '+' : '' }}{{ row.points }}</b></template></el-table-column>
          <el-table-column prop="balanceAfter" label="余额" width="80" />
          <el-table-column prop="reason" label="原因" min-width="260" show-overflow-tooltip />
          <el-table-column prop="operatorName" label="操作人" width="140" show-overflow-tooltip />
        </el-table>
        <div class="pager">
          <el-pagination v-model:current-page="rq.current" :page-size="rq.size" :total="recTotal" layout="total, prev, pager, next" @current-change="loadRecords" />
        </div>
      </el-tab-pane>

      <el-tab-pane name="redeem" lazy>
        <template #label>兑换发放 <el-badge v-if="ov?.pending" :value="ov.pending" class="badge" /></template>
        <el-table :data="redeems" stripe>
          <el-table-column label="申请时间" width="160"><template #default="{ row }">{{ row.createTime?.slice(0, 16) }}</template></el-table-column>
          <el-table-column prop="redeemNo" label="编号" width="160" />
          <el-table-column prop="userName" label="员工" width="150" />
          <el-table-column prop="rewardName" label="奖品" min-width="160" />
          <el-table-column prop="points" label="积分" width="80" />
          <el-table-column label="金额（元）" width="100"><template #default="{ row }">{{ yuan(row.price) }}</template></el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }"><el-tag size="small" :type="REDEEM_STATES[row.status]?.type">{{ REDEEM_STATES[row.status]?.label }}</el-tag></template>
          </el-table-column>
          <el-table-column label="处理" min-width="200">
            <template #default="{ row }">
              <template v-if="row.status === 'PENDING' && canEdit">
                <el-button size="small" type="primary" @click="issue(row)">发放</el-button>
                <el-button size="small" @click="reject(row)">退回</el-button>
              </template>
              <span v-else class="muted">{{ row.handlerName }} {{ row.handleTime?.slice(0, 16) }}{{ row.remark ? '，' + row.remark : '' }}{{ row.costExpenseId ? '，已记入安全投入' : '' }}</span>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="奖品" name="reward" lazy>
        <div class="toolbar"><div class="spacer" /><el-button v-if="canEdit" type="primary" icon="Plus" @click="openReward()">新增奖品</el-button></div>
        <el-table :data="rewards" stripe>
          <el-table-column prop="reward.name" label="奖品" min-width="200" />
          <el-table-column prop="reward.points" label="所需积分" width="100" />
          <el-table-column label="单价（元）" width="100"><template #default="{ row }">{{ yuan(row.reward.price) }}</template></el-table-column>
          <el-table-column prop="reward.stock" label="库存" width="80" />
          <el-table-column prop="reward.remark" label="说明" min-width="160" show-overflow-tooltip />
          <el-table-column label="状态" width="80"><template #default="{ row }"><el-tag size="small" :type="row.reward.status ? 'success' : 'info'">{{ row.reward.status ? '上架' : '下架' }}</el-tag></template></el-table-column>
          <el-table-column v-if="canEdit" label="操作" width="140">
            <template #default="{ row }">
              <el-button link type="primary" @click="openReward(row.reward)">修改</el-button>
              <el-button link @click="toggle(row.reward)">{{ row.reward.status ? '下架' : '上架' }}</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="aVisible" title="手工调整积分" width="460px">
      <el-form label-width="80px">
        <el-form-item label="员工">
          <el-select v-model="adj.userId" filterable style="width: 100%"><el-option v-for="u in users" :key="u.id" :label="`${u.nickname}${u.deptName ? '（' + u.deptName + '）' : ''}`" :value="u.id" /></el-select>
        </el-form-item>
        <el-form-item label="积分"><el-input-number v-model="adj.points" :min="-200" :max="200" /><span class="muted" style="margin-left: 8px">正数加分，负数扣分</span></el-form-item>
        <el-form-item label="原因"><el-input v-model="adj.reason" maxlength="100" placeholder="例如：合理化建议被采纳 / 违章作业扣分" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="aVisible = false">取消</el-button>
        <el-button type="primary" @click="saveAdjust">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="wVisible" :title="wForm.id ? '修改奖品' : '新增奖品'" width="460px">
      <el-form label-width="80px">
        <el-form-item label="名称"><el-input v-model="wForm.name" maxlength="40" /></el-form-item>
        <el-form-item label="所需积分"><el-input-number v-model="wForm.points" :min="1" /></el-form-item>
        <el-form-item label="单价"><el-input-number v-model="wForm.price" :min="0" :precision="2" /><span class="muted" style="margin-left: 8px">元，发放时记入安全投入；不花钱填 0</span></el-form-item>
        <el-form-item label="库存"><el-input-number v-model="wForm.stock" :min="0" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="wForm.remark" maxlength="100" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="wVisible = false">取消</el-button>
        <el-button type="primary" @click="saveReward">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { adjustPoints, allPointRewards, handleRedeem, pointOverview, pointRanking, pointRecords, pointRedeems, pointRewardStatus, savePointReward, userOptions } from '@/api'
import { MEDALS, REDEEM_STATES, SOURCE_TYPES } from './point'

const store = useUserStore()
const canEdit = computed(() => store.hasPerm('point:edit'))
const yuan = (v) => Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const tab = ref('rank')
const ov = ref(null)
const period = ref('YEAR')
const rank = ref(null)
const rLoading = ref(false)
const users = ref([])
const records = ref([])
const recTotal = ref(0)
const recLoading = ref(false)
const rq = reactive({ current: 1, size: 20, userId: null, sourceType: '' })
const redeems = ref([])
const rewards = ref([])
const aVisible = ref(false)
const adj = reactive({ userId: null, points: 10, reason: '' })
const wVisible = ref(false)
const wForm = reactive({ id: null, name: '', points: 100, price: 0, stock: 10, remark: '' })

async function loadOverview() {
  ov.value = await pointOverview()
}
async function loadRank() {
  rLoading.value = true
  try {
    rank.value = await pointRanking({ period: period.value })
  } finally {
    rLoading.value = false
  }
}
async function loadRecords() {
  recLoading.value = true
  try {
    const params = Object.fromEntries(Object.entries(rq).filter(([, v]) => v !== '' && v != null))
    const p = await pointRecords(params)
    records.value = p.records
    recTotal.value = p.total
  } finally {
    recLoading.value = false
  }
}
function searchRecords() {
  rq.current = 1
  loadRecords()
}
async function loadRedeems() {
  redeems.value = await pointRedeems()
}
async function loadRewards() {
  rewards.value = await allPointRewards()
}
function openAdjust(userId = null) {
  Object.assign(adj, { userId, points: 10, reason: '' })
  aVisible.value = true
}
async function saveAdjust() {
  await adjustPoints(adj)
  ElMessage.success('已调整，员工会收到消息')
  aVisible.value = false
  loadRank()
  loadOverview()
  if (tab.value === 'records') loadRecords()
}
async function issue(row) {
  const msg = Number(row.price) > 0 ? `发放后 ${yuan(row.price)} 元会自动记入安全投入（安全教育培训：隐患报告奖励）。` : ''
  const { value } = await ElMessageBox.prompt(`把“${row.rewardName}”发放给 ${row.userName}？${msg}`, '发放奖品', { inputPlaceholder: '说明（可选），例如领取地点', confirmButtonText: '发放' })
  await handleRedeem(row.id, { issue: true, remark: value })
  ElMessage.success('已发放')
  loadRedeems()
  loadOverview()
}
async function reject(row) {
  const { value } = await ElMessageBox.prompt(`退回 ${row.userName} 的兑换“${row.rewardName}”？${row.points} 积分和库存会还回去。`, '退回兑换', {
    inputPlaceholder: '退回原因（必填）', inputValidator: (v) => (v && v.trim() ? true : '请填写退回原因'), confirmButtonText: '退回', type: 'warning'
  })
  await handleRedeem(row.id, { issue: false, remark: value })
  ElMessage.success('已退回')
  loadRedeems()
  loadOverview()
}
function openReward(r = null) {
  Object.assign(wForm, r ? { id: r.id, name: r.name, points: r.points, price: Number(r.price), stock: r.stock, remark: r.remark || '' } : { id: null, name: '', points: 100, price: 0, stock: 10, remark: '' })
  wVisible.value = true
}
async function saveReward() {
  await savePointReward(wForm.id, wForm)
  ElMessage.success('已保存')
  wVisible.value = false
  loadRewards()
}
async function toggle(r) {
  await pointRewardStatus(r.id, r.status ? 0 : 1)
  loadRewards()
}
watch(tab, (t) => {
  if (t === 'records' && !records.value.length) loadRecords()
  if (t === 'redeem') loadRedeems()
  if (t === 'reward') loadRewards()
})
onMounted(async () => {
  loadOverview()
  loadRank()
  users.value = await userOptions()
})
</script>

<style scoped>
.muted { color: #999; font-size: 12px; }
.stats { display: flex; gap: 12px; margin-bottom: 8px; flex-wrap: wrap; }
.stat { flex: 1; min-width: 140px; padding: 12px 16px; border: 1px solid #ebeef5; border-radius: 8px; display: flex; align-items: baseline; gap: 8px; cursor: pointer; }
.stat b { font-size: 22px; }
.stat span { color: #888; font-size: 13px; }
.warn { color: #e6a23c; }
.rule { background: #f6f8fa; border-radius: 6px; padding: 8px 12px; line-height: 1.8; margin-bottom: 4px; }
.rank { font-size: 18px; font-weight: 600; }
.plus { color: #52c41a; }
.minus { color: #f5222d; }
.badge { margin-left: 2px; }
</style>
