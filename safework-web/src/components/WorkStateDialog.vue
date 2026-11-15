<template>
  <el-dialog v-model="visible" :title="TICKET_ACTIONS[action]" width="min(480px,94vw)" append-to-body destroy-on-close>
    <el-form label-position="top" @submit.prevent="submit">
      <el-form-item v-if="selectingReviewer" :label="requesting ? '复工审核人' : '完工验收人'" required>
        <el-select v-model="reviewer" filterable placeholder="选择有审批权限的员工">
          <el-option v-for="p in people" :key="p.id" :label="p.nickname" :value="p.id"/>
        </el-select>
      </el-form-item>
      <el-form-item :label="labels[action]" required>
        <el-input v-model="comment" type="textarea" :maxlength="action === 'COMPLETE' ? 350 : 450" show-word-limit/>
      </el-form-item>
      <el-form-item v-if="action==='ACCEPT_FINISH'" label="现场验收清单" required>
        <el-checkbox-group v-model="checked">
          <el-checkbox v-for="item in checklist" :key="item.key" :label="item.key">{{ item.label }}</el-checkbox>
        </el-checkbox-group>
      </el-form-item>
      <el-form-item v-if="action==='COMPLETE'" label="监护人签名" required><SignaturePad ref="pad"/></el-form-item>
      <template v-if="requesting && TICKET_TYPES[ticket.ticketType]?.gas">
        <el-form-item v-for="(label,key) in gases" :key="key" :label="label" required>
          <el-input-number v-model="values[key]" :min="0" :precision="2"/>
        </el-form-item>
      </template>
    </el-form>
    <template #footer><el-button @click="visible=false">取消</el-button>
      <el-button type="primary" :loading="busy" @click="submit">确认{{ TICKET_ACTIONS[action] }}</el-button>
    </template>
  </el-dialog>
</template>
<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ticketAction } from '@/api'
import SignaturePad from '@/components/SignaturePad.vue'
import request from '@/api/request'
import { TICKET_ACTIONS, TICKET_TYPES } from '@/utils/dict'
const props=defineProps({ticket:Object})
const emit=defineEmits(['changed'])
const visible=ref(false), busy=ref(false), action=ref(''), comment=ref(''), reviewer=ref(), people=ref([]), values=ref({})
const requesting=computed(()=>action.value==='REQUEST_RESUME')
const selectingReviewer = computed(()=>requesting.value || action.value==='COMPLETE')
const checked=ref([]), pad=ref(null)
const checklist=[{key:'personnel',label:'人员已撤离'},{key:'tools',label:'工具已清点'},{key:'site',label:'现场已清理'}]
const gases={gasOxygen:'氧含量 %VOL',gasLel:'可燃气体 %LEL',gasH2s:'硫化氢 mg/m³'}
const labels={COMPLETE:"完工或整改记录",ACCEPT_FINISH:"验收意见",RETURN_FINISH:"退回原因和整改要求",PAUSE:'暂停原因及现场状态',REQUEST_RESUME:'整改情况及复工依据',ALLOW_RESUME:'审核意见',DENY_RESUME:'退回原因'}
async function open(next){
  action.value=next
  comment.value=''
  reviewer.value=undefined
  values.value={}
  checked.value=[]
  if(selectingReviewer.value) people.value=await request.get('/ticket/resume-reviewers')
  visible.value=true
}
async function submit(){
  if(busy.value) return
  if(!comment.value.trim()) return ElMessage.warning('请填写'+labels[action.value])
  const completion = action.value==='COMPLETE'
  const signature = completion ? pad.value?.toDataURL() : undefined
  if(completion && !signature) return ElMessage.warning('请监护人签名')
  if(action.value==='ACCEPT_FINISH' && checked.value.length!==checklist.length) return ElMessage.warning('请逐项核对现场验收清单')
  if(selectingReviewer.value && !reviewer.value) return ElMessage.warning('请选择审核人')
  if(requesting.value && TICKET_TYPES[props.ticket.ticketType]?.gas && Object.keys(gases).some(k=>!Number.isFinite(values.value[k]))) return ElMessage.warning('请填写三项现场复测数据')
  busy.value=true
  try{
    await ticketAction(props.ticket.id,action.value.toLowerCase(),{comment:comment.value.trim(),resumeReviewerId:requesting.value ? reviewer.value : undefined,acceptanceReviewerId:completion ? reviewer.value : undefined,checkedItems:checked.value,signature,...values.value})
    visible.value=false
    ElMessage.success('作业票状态已更新')
    emit('changed')
  }finally{busy.value=false}
}
defineExpose({open})
</script>
