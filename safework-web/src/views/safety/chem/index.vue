<template>
  <div class="page-card">
    <div v-if="ov" class="stats">
      <div class="stat"><b>{{ ov.chemicals }}</b><span>危化品种类</span></div>
      <div class="stat"><b>{{ ov.stores }}</b><span>存储单元</span></div>
      <div class="stat" @click="tab = 'store'">
        <b class="danger">{{ ov.major }}</b>
        <span>重大危险源<template v-if="ov.major">（{{ levelText }}）</template></span>
      </div>
      <div class="stat" @click="tab = 'store'"><b :class="ov.overstock ? 'danger' : ''">{{ ov.overstock }}</b><span>超过最大储量</span></div>
      <div class="stat" @click="tab = 'item'"><b :class="ov.noMsds ? 'warn' : ''">{{ ov.noMsds }}</b><span>缺 MSDS</span></div>
    </div>

    <el-tabs v-model="tab">
      <el-tab-pane label="存储单元与重大危险源" name="store">
        <StoreList @changed="reload" />
      </el-tab-pane>
      <el-tab-pane label="危化品目录" name="item" lazy>
        <ItemList @changed="reload" />
      </el-tab-pane>
      <el-tab-pane label="出入库流水" name="flow" lazy>
        <FlowList />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { chemOverview } from '@/api'
import FlowList from './FlowList.vue'
import ItemList from './ItemList.vue'
import StoreList from './StoreList.vue'

const tab = ref('store')
const ov = ref(null)
const levelText = computed(() => ['一', '二', '三', '四'].map((t, i) => (ov.value[`level${i + 1}`] ? `${t}级 ${ov.value[`level${i + 1}`]}` : '')).filter(Boolean).join('、'))
function reload() {
  chemOverview().then((v) => { ov.value = v })
}
onMounted(reload)
</script>

<style scoped>
.stats { display: flex; gap: 12px; margin-bottom: 8px; flex-wrap: wrap; }
.stat { flex: 1; min-width: 120px; padding: 12px 16px; border: 1px solid #ebeef5; border-radius: 8px; display: flex; align-items: baseline; gap: 8px; cursor: pointer; }
.stat b { font-size: 24px; }
.stat span { color: #888; font-size: 13px; }
.warn { color: #e6a23c; }
.danger { color: #f56c6c; }
</style>
