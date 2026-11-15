<template>
  <div class="page-card">
    <div class="toolbar">
      <el-radio-group v-model="active" @change="switchTab">
        <el-radio-button v-for="tab in tabs" :key="tab.label" :label="tab.label">{{ tab.label }}</el-radio-button>
      </el-radio-group>
      <el-button @click="load" :loading="loading">刷新</el-button>
    </div>
    <el-alert v-if="failed" title="加载失败，请点击刷新重试" type="error" :closable="false"/>
    <el-table v-else v-loading="loading" :data="rows" empty-text="当前没有分给你的待办">
      <el-table-column prop="title" label="事项" min-width="220"/>
      <el-table-column prop="info" label="相关信息" min-width="150"/>
      <el-table-column label="操作" width="100"><template #default="{row}">
        <el-button link type="primary" @click="router.push({path:current.path,query:{detail:row.id}})">去处理</el-button>
      </template></el-table-column>
    </el-table>
    <el-pagination v-if="total>20" v-model:current-page="page" :page-size="20" :total="total" layout="total, prev, pager, next" @current-change="load"/>
  </div>
</template>
<script setup>
import { computed, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { pageTickets, workBriefingPage, checkTaskPage } from '@/api'
import request from '@/api/request'

const store = useUserStore(), router = useRouter()
const tabs = [
  {label:'待审批', perm:'ticket:approve', path:'/ticket/list', fetch:pageTickets, filter:{todo:true}, row:r=>({id:r.id,title:r.title,info:r.ticketNo})},
  {label:'待签交底', perm:'briefing:sign', path:'/ticket/briefing', fetch:workBriefingPage, filter:{awaitingMe:true,status:'PENDING'}, row:({briefing:b})=>({id:b.id,title:b.title,info:b.ticketNo})},
  {label:'待完工验收', perm:'ticket:approve', path:'/ticket/list', fetch:pageTickets, filter:{acceptanceTodo:true}, row:r=>({id:r.id,title:r.title,info:r.ticketNo})},
  {label:'待复工审核', perm:'ticket:approve', path:'/ticket/list', fetch:pageTickets, filter:{resumeTodo:true}, row:r=>({id:r.id,title:r.title,info:r.ticketNo})},
  {label:'待接班', perm:'ticket:list', path:'/ticket/handover', fetch:p=>request.get('/ticket/handover/page',{params:p}), filter:{awaitingMe:true}, row:r=>({id:r.id,title:r.title,info:r.senderName+' 交班 · '+r.ticketNo})},
  {label:'待检查', perm:'check:list', path:'/safety/check', fetch:checkTaskPage, filter:{mine:true,state:'PENDING'}, row:r=>({id:r.id,title:r.planName,info:'截止 '+r.dueDate})}
].filter(t=>store.hasPerm(t.perm))
const active = ref(tabs[0]?.label)
const current = computed(()=>tabs.find(t=>t.label===active.value))
const page=ref(1), rows=ref([]), total=ref(0), loading=ref(false), failed=ref(false)
let sequence=0
function switchTab(){page.value=1;load()}
async function load(){
  const tab=current.value
  if(!tab) return
  const id=++sequence
  loading.value=true
  failed.value=false
  rows.value=[]
  total.value=0
  try {
    const data=await tab.fetch({...tab.filter,current:page.value,size:20})
    if(id!==sequence) return
    rows.value=data.records.map(tab.row)
    total.value=Number(data.total)
  } catch { if(id===sequence) failed.value=true }
  finally { if(id===sequence) loading.value=false }
}
onMounted(load)
</script>
