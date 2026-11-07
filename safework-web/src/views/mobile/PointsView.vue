<template>
  <div v-loading="loading">
    <div v-if="me" class="m-card hero">
      <div class="bal"><b>{{ me.balance }}</b><span>可用积分</span></div>
      <div class="sub">
        <div><b>{{ me.monthPoints }}</b><span>本月得分</span></div>
        <div><b>{{ me.monthRank ?? '-' }}</b><span>本月名次</span></div>
        <div><b>{{ me.yearRank ?? '-' }}</b><span>今年名次</span></div>
        <div><b>{{ me.earned }}</b><span>累计得分</span></div>
      </div>
      <div class="rule">上报的隐患被确认：一般 +10、重大 +50；按期完成整改 +5</div>
      <el-button class="go" type="warning" round @click="$router.push('/m/report')">随手拍，上报隐患</el-button>
    </div>

    <div class="m-tabs">
      <button :class="{ on: tab === 'reward' }" @click="tab = 'reward'">兑换奖品</button>
      <button :class="{ on: tab === 'rank' }" @click="tab = 'rank'">排行榜</button>
      <button :class="{ on: tab === 'record' }" @click="tab = 'record'">积分明细</button>
      <button :class="{ on: tab === 'redeem' }" @click="tab = 'redeem'">我的兑换</button>
    </div>

    <template v-if="tab === 'reward'">
      <div v-for="r in rewards" :key="r.reward.id" class="m-item">
        <div class="m-item-head">
          <span class="m-item-title">{{ r.reward.name }}</span>
          <b class="cost">{{ r.reward.points }} 分</b>
        </div>
        <div class="row">
          <span class="m-muted">库存 {{ r.reward.stock }}{{ r.reward.remark ? ' · ' + r.reward.remark : '' }}</span>
          <el-button size="small" type="primary" :disabled="!r.affordable" @click="redeem(r.reward)">{{ r.reward.stock ? (r.affordable ? '兑换' : '积分不够') : '已兑完' }}</el-button>
        </div>
      </div>
      <div v-if="!rewards.length" class="m-empty">还没有可兑换的奖品</div>
    </template>

    <template v-if="tab === 'rank'">
      <div class="m-tabs small">
        <button :class="{ on: period === 'YEAR' }" @click="period = 'YEAR'">今年</button>
        <button :class="{ on: period === 'MONTH' }" @click="period = 'MONTH'">本月</button>
      </div>
      <div class="m-card">
        <div v-for="x in rank?.rows || []" :key="x.userId" class="m-row" :class="{ mine: x.userId === myId }">
          <span><span class="medal">{{ MEDALS[x.rank - 1] || x.rank }}</span> {{ x.userName }}</span>
          <span><b>{{ x.points }}</b> 分 <span class="m-muted">· 隐患 {{ x.hazards }}</span></span>
        </div>
        <div v-if="rank && !rank.rows.length" class="m-empty">{{ rank.label }}还没有人得分，快去随手拍吧</div>
      </div>
    </template>

    <template v-if="tab === 'record'">
      <div class="m-card">
        <div v-for="r in me?.records || []" :key="r.id" class="rec">
          <div class="rec-head">
            <span>{{ SOURCE_TYPES[r.sourceType]?.label }}</span>
            <b :class="r.points > 0 ? 'plus' : 'minus'">{{ r.points > 0 ? '+' : '' }}{{ r.points }}</b>
          </div>
          <div class="m-muted">{{ r.reason }}</div>
          <div class="m-muted">{{ r.createTime?.slice(0, 16) }} · 余额 {{ r.balanceAfter }}</div>
        </div>
        <div v-if="me && !me.records.length" class="m-empty">还没有积分记录</div>
      </div>
    </template>

    <template v-if="tab === 'redeem'">
      <div v-for="r in me?.redeems || []" :key="r.id" class="m-item">
        <div class="m-item-head">
          <span class="m-item-title">{{ r.rewardName }}</span>
          <el-tag size="small" :type="REDEEM_STATES[r.status]?.type">{{ REDEEM_STATES[r.status]?.label }}</el-tag>
        </div>
        <div class="m-muted">{{ r.redeemNo }} · {{ r.points }} 分 · {{ r.createTime?.slice(0, 16) }}</div>
        <div v-if="r.remark" class="m-muted">{{ r.remark }}</div>
      </div>
      <div v-if="me && !me.redeems.length" class="m-empty">还没有兑换过</div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { pointMine, pointRanking, pointRewards, redeemReward } from '@/api'
import { MEDALS, REDEEM_STATES, SOURCE_TYPES } from '@/views/safety/point/point'

const store = useUserStore()
const myId = computed(() => String(store.user?.id ?? ''))
const loading = ref(false)
const me = ref(null)
const rewards = ref([])
const rank = ref(null)
const tab = ref('reward')
const period = ref('YEAR')

async function load() {
  loading.value = true
  try {
    ;[me.value, rewards.value] = await Promise.all([pointMine(), pointRewards()])
  } finally {
    loading.value = false
  }
}
async function loadRank() {
  rank.value = await pointRanking({ period: period.value })
}
async function redeem(r) {
  await ElMessageBox.confirm(`用 ${r.points} 积分兑换“${r.name}”？兑换后等安全环保部发放。`, '兑换', { confirmButtonText: '兑换' })
  await redeemReward(r.id)
  ElMessage.success('兑换成功，等待发放')
  load()
}
watch(tab, (t) => { if (t === 'rank' && !rank.value) loadRank() })
watch(period, loadRank)
onMounted(load)
</script>

<style scoped>
.hero { background: linear-gradient(135deg, #1f6feb, #4f8ff7); color: #fff; }
.bal { display: flex; align-items: baseline; gap: 8px; }
.bal b { font-size: 36px; }
.sub { display: flex; justify-content: space-between; margin: 10px 0 8px; text-align: center; }
.sub div { display: flex; flex-direction: column; }
.sub b { font-size: 18px; }
.sub span, .bal span { font-size: 12px; opacity: 0.85; }
.rule { font-size: 12px; opacity: 0.9; }
.go { margin-top: 10px; width: 100%; }
.small button { padding: 5px 12px; font-size: 13px; }
.cost { color: #fa8c16; }
.row { display: flex; justify-content: space-between; align-items: center; }
.medal { display: inline-block; width: 26px; text-align: center; color: #1f2329; font-weight: 600; }
.mine { background: #f0f6ff; }
.rec { padding: 8px 0; border-bottom: 1px solid #f2f3f5; }
.rec:last-child { border-bottom: 0; }
.rec-head { display: flex; justify-content: space-between; }
.plus { color: #52c41a; }
.minus { color: #f5222d; }
</style>
