<template>
  <div>
    <div class="m-card me">
      <div class="avatar">{{ (store.nickname || '?').slice(-2) }}</div>
      <div>
        <div class="name">{{ store.nickname }}</div>
        <div class="m-muted">{{ store.user?.username }} · {{ roleText }}</div>
        <div class="m-muted">{{ store.tenantName }}</div>
      </div>
    </div>

    <div class="m-card menu">
      <router-link v-if="store.hasPerm('training:my')" to="/training/my" class="m-row"><span>我的考试</span><el-icon><ArrowRight /></el-icon></router-link>
      <router-link to="/m/points" class="m-row"><span>我的积分（隐患随手拍）</span><el-icon><ArrowRight /></el-icon></router-link>
      <router-link to="/m/health" class="m-row"><span>我的职业健康</span><el-icon><ArrowRight /></el-icon></router-link>
      <router-link to="/m/ppe" class="m-row"><span>我的劳保用品</span><el-icon><ArrowRight /></el-icon></router-link>
      <router-link to="/m/resp" class="m-row"><span>我的安全生产责任书</span><el-icon><ArrowRight /></el-icon></router-link>
      <router-link to="/m/accident" class="m-row"><span>我的事故整改措施</span><el-icon><ArrowRight /></el-icon></router-link>
      <router-link to="/m/meeting" class="m-row"><span>班前会</span><el-icon><ArrowRight /></el-icon></router-link>
      <router-link to="/m/chem" class="m-row"><span>危化品出入库</span><el-icon><ArrowRight /></el-icon></router-link>
      <router-link to="/m/checks" class="m-row"><span>我的安全检查</span><el-icon><ArrowRight /></el-icon></router-link>
      <router-link v-if="store.hasPerm('risk:check')" to="/safety/risk-check" class="m-row"><span>风险巡检</span><el-icon><ArrowRight /></el-icon></router-link>
      <router-link v-if="store.hasPerm('ai:chat')" to="/ai/chat" class="m-row"><span>AI 安全助手</span><el-icon><ArrowRight /></el-icon></router-link>
      <a class="m-row" @click="toDesktop"><span>切换到电脑版</span><el-icon><Monitor /></el-icon></a>
    </div>
    <div class="m-muted tip">以上没有手机页面的功能会打开电脑版页面；在电脑版右上角头像菜单里点“手机版”可以回来。</div>

    <el-button class="logout" @click="logout">退出登录</el-button>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { ROLE_NAMES } from '@/utils/dict'
import { setPreferDesktop } from '@/utils/mobile'

const router = useRouter()
const store = useUserStore()
const roleText = computed(() => store.roles.map((r) => ROLE_NAMES[r] || r).join('、') || '员工')

function toDesktop() {
  setPreferDesktop(true)
  router.push('/home')
}
async function logout() {
  try {
    await ElMessageBox.confirm('确定退出登录吗？', '退出登录', { confirmButtonText: '退出', cancelButtonText: '取消' })
  } catch {
    return
  }
  await store.logout()
  router.replace('/login')
}
</script>

<style scoped>
.me { display: flex; align-items: center; gap: 14px; }
.avatar { width: 54px; height: 54px; border-radius: 50%; background: #1f6feb; color: #fff; font-size: 18px; display: flex; align-items: center; justify-content: center; flex: none; }
.name { font-size: 18px; font-weight: 600; margin-bottom: 2px; }
.menu .m-row { align-items: center; padding: 12px 0; color: inherit; text-decoration: none; cursor: pointer; }
.menu .m-row > span:first-child { color: #1f2329; }
.menu .el-icon { color: #c0c4cc; }
.tip { padding: 0 4px 16px; font-size: 12px; }
.logout { width: 100%; height: 44px; font-size: 16px; color: #f5222d; }
</style>
