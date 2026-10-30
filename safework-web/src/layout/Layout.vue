<template>
  <el-container class="layout">
    <el-aside :width="collapsed ? '64px' : '220px'" class="aside">
      <div class="logo" @click="$router.push('/home')">
        <el-icon :size="22"><Lock /></el-icon>
        <span v-show="!collapsed">SafeWork</span>
      </div>
      <el-menu
        :default-active="$route.path"
        :collapse="collapsed"
        router
        background-color="#0f1b2d"
        text-color="#b8c2d3"
        active-text-color="#ffffff"
      >
        <el-menu-item index="/home">
          <el-icon><HomeFilled /></el-icon>
          <template #title>首页</template>
        </el-menu-item>
        <template v-for="m in store.menus" :key="m.id">
          <el-sub-menu v-if="m.menuType === 'M'" :index="m.path">
            <template #title>
              <el-icon><component :is="iconOf(m.icon)" /></el-icon>
              <span>{{ m.menuName }}</span>
            </template>
            <el-menu-item v-for="c in pages(m.children)" :key="c.id" :index="c.path">
              <el-icon><component :is="iconOf(c.icon)" /></el-icon>
              <template #title>{{ c.menuName }}</template>
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else-if="m.menuType === 'C'" :index="m.path">
            <el-icon><component :is="iconOf(m.icon)" /></el-icon>
            <template #title>{{ m.menuName }}</template>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <el-icon class="fold" :size="20" @click="collapsed = !collapsed">
          <component :is="collapsed ? 'Expand' : 'Fold'" />
        </el-icon>
        <span class="title">{{ $route.meta.title }}</span>
        <div class="spacer" />
        <el-popover v-model:visible="bellVisible" placement="bottom-end" :width="360" trigger="click" popper-class="bell-pop">
          <template #reference>
            <el-badge :value="msg.unread" :hidden="!msg.unread" :max="99" class="bell">
              <el-icon :size="20" :title="msg.connected ? '消息实时推送已连接' : '消息'"><Bell /></el-icon>
            </el-badge>
          </template>
          <div class="bell-head">
            <span>未读消息 {{ msg.unread }}</span>
            <el-button link type="primary" :disabled="!msg.unread" @click="readAll">全部已读</el-button>
          </div>
          <div v-if="!msg.latest.length" class="bell-empty">没有未读消息</div>
          <div v-for="m in msg.latest" :key="m.id" class="bell-item" @click="openMsg(m)">
            <div class="bell-title">
              <el-tag size="small" :type="CATEGORY[m.category]?.type" effect="plain">{{ CATEGORY[m.category]?.label }}</el-tag>
              <span>{{ m.title }}</span>
            </div>
            <div class="bell-content">{{ m.content }}</div>
            <div class="bell-time">{{ m.createTime }}<template v-if="m.senderName"> · {{ m.senderName }}</template></div>
          </div>
          <div class="bell-foot"><el-button link type="primary" @click="goMessages">查看全部消息</el-button></div>
        </el-popover>
        <el-tooltip v-if="tenantTip" :content="tenantTip" placement="bottom">
          <el-tag effect="plain" class="tenant" :type="store.isPlatform ? 'danger' : 'primary'">
            {{ store.tenantName }}<template v-if="store.tenant?.packageName"> · {{ store.tenant.packageName }}</template>
          </el-tag>
        </el-tooltip>
        <el-dropdown @command="onCommand">
          <span class="user">
            <el-avatar :size="28">{{ store.nickname.slice(0, 1) }}</el-avatar>
            <span class="name">{{ store.nickname }}</span>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item disabled>角色：{{ roleText }}</el-dropdown-item>
              <el-dropdown-item command="password" divided>修改密码</el-dropdown-item>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main class="main">
        <el-alert v-if="expireWarning" :title="expireWarning" type="warning" show-icon :closable="true" class="expire-alert" />
        <router-view />
      </el-main>
    </el-container>

    <el-dialog v-model="pwdVisible" title="修改密码" width="420px">
      <el-form ref="pwdRef" :model="pwd" :rules="pwdRules" label-width="90px">
        <el-form-item label="原密码" prop="oldPassword"><el-input v-model="pwd.oldPassword" type="password" show-password /></el-form-item>
        <el-form-item label="新密码" prop="newPassword"><el-input v-model="pwd.newPassword" type="password" show-password /></el-form-item>
        <el-form-item label="确认密码" prop="confirm"><el-input v-model="pwd.confirm" type="password" show-password /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="pwdSaving" @click="savePassword">确定</el-button>
      </template>
    </el-dialog>
  </el-container>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { changePassword, readAllMessages } from '@/api'
import { CATEGORY, useMessageStore } from '@/stores/message'
import { ROLE_NAMES } from '@/utils/dict'

const store = useUserStore()
const msg = useMessageStore()
const router = useRouter()

// 站内消息：登录后建立实时推送连接
const bellVisible = ref(false)
onMounted(() => msg.connect())
onBeforeUnmount(() => msg.disconnect())
async function readAll() {
  await readAllMessages()
  msg.unread = 0
  msg.latest = []
}
function openMsg(m) {
  bellVisible.value = false
  msg.open(m)
}
function goMessages() {
  bellVisible.value = false
  router.push('/message')
}
// 手机上默认收起侧边栏，否则内容区只剩一百多像素宽
const collapsed = ref(window.innerWidth < 768)

// 后端菜单里的 icon 名称 → Element Plus 图标组件名
const ICONS = {
  setting: 'Setting', user: 'User', team: 'UserFilled', menu: 'Menu', safety: 'Warning', file: 'Document',
  shield: 'FirstAidKit', space: 'Box', hazard: 'WarningFilled', dept: 'OfficeBuilding', log: 'Tickets',
  ai: 'MagicStick', chat: 'ChatDotRound', book: 'Reading', agent: 'Cpu',
  platform: 'Platform', company: 'OfficeBuilding', package: 'Present', login: 'Key', online: 'Monitor',
  notice: 'Notification', job: 'Timer', chart: 'DataAnalysis',
  training: 'Reading', exam: 'EditPen', question: 'Collection', 'exam-manage': 'Memo',
  risk: 'Odometer', 'risk-check': 'Finished'
}
const iconOf = (name) => ICONS[name] || 'Document'
const pages = (list = []) => list.filter((c) => c.menuType === 'C')

const tenantTip = computed(() => {
  const t = store.tenant
  if (!t) return ''
  if (t.platform) return '运营平台：管理入驻企业、套餐'
  const parts = [`企业编码：${t.tenantCode}`]
  parts.push(t.expireTime ? `服务到期：${t.expireTime.slice(0, 10)}` : '服务期限：不限')
  if (t.accountLimit) parts.push(`账号上限：${t.accountLimit} 个`)
  return parts.join('　')
})
// 30 天内到期提醒续费
const expireWarning = computed(() => {
  const d = store.tenant?.daysLeft
  if (d == null || d > 30) return ''
  return d <= 0 ? '企业服务今天到期，请尽快联系平台续费' : `企业服务还有 ${d} 天到期（${store.tenant.expireTime.slice(0, 10)}），请及时联系平台续费`
})

const roleText = computed(() => store.isPlatform ? '平台管理员' : (store.roles.map((r) => ROLE_NAMES[r] || r).join('、') || '无'))

const pwdVisible = ref(false)
const pwdSaving = ref(false)
const pwdRef = ref()
const pwd = reactive({ oldPassword: '', newPassword: '', confirm: '' })
const pwdRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [{ required: true, min: 6, max: 32, message: '新密码 6-32 位', trigger: 'blur' }],
  confirm: [{ validator: (_, v, cb) => (v === pwd.newPassword ? cb() : cb(new Error('两次输入的密码不一致'))), trigger: 'blur' }]
}
async function savePassword() {
  await pwdRef.value.validate()
  pwdSaving.value = true
  try {
    await changePassword({ oldPassword: pwd.oldPassword, newPassword: pwd.newPassword })
    ElMessage.success('密码已修改')
    pwdVisible.value = false
  } finally {
    pwdSaving.value = false
  }
}

async function onCommand(cmd) {
  if (cmd === 'password') {
    Object.assign(pwd, { oldPassword: '', newPassword: '', confirm: '' })
    pwdVisible.value = true
    return
  }
  if (cmd === 'logout') {
    msg.disconnect()
    await store.logout()
    // 动态路由已注册在 router 上，整页刷新是最简单的清理方式
    window.location.href = '/login'
  }
}
</script>

<style scoped>
.layout { height: 100vh; }
.aside { background: #0f1b2d; transition: width .2s; overflow: hidden; }
.aside .el-menu { border-right: none; }
.logo {
  height: 56px; display: flex; align-items: center; gap: 10px; padding: 0 20px;
  color: #fff; font-size: 18px; font-weight: 600; cursor: pointer; white-space: nowrap;
}
.header {
  display: flex; align-items: center; gap: 12px; background: #fff;
  border-bottom: 1px solid #e5e8ee; height: 56px;
}
.fold { cursor: pointer; color: #555; }
.title { font-size: 16px; font-weight: 500; }
.spacer { flex: 1; }
.user { display: flex; align-items: center; gap: 8px; cursor: pointer; outline: none; }
.main { padding: 16px; display: flex; flex-direction: column; }
/* 内容区是纵向 flex：问答这类要占满剩余高度的页面用 flex: 1，不用再按横幅、标题栏的高度去算 */
.main > :deep(*) { flex-shrink: 0; }
.expire-alert { margin-bottom: 12px; }
.bell { cursor: pointer; color: #555; margin-right: 6px; display: flex; align-items: center; }
.bell-head, .bell-foot { display: flex; justify-content: space-between; align-items: center; font-weight: 600; padding-bottom: 8px; border-bottom: 1px solid #f0f0f0; }
.bell-foot { justify-content: center; border-bottom: none; border-top: 1px solid #f0f0f0; padding: 8px 0 0; }
.bell-empty { color: #999; text-align: center; padding: 24px 0; }
.bell-item { padding: 10px 4px; border-bottom: 1px solid #f5f5f5; cursor: pointer; }
.bell-item:hover { background: #f6f8fb; }
.bell-title { display: flex; gap: 6px; align-items: center; font-size: 14px; color: #1f2329; }
.bell-title span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.bell-content { color: #666; font-size: 12px; margin-top: 4px; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.bell-time { color: #aaa; font-size: 12px; margin-top: 4px; }
@media (max-width: 768px) {
  .tenant, .name { display: none; }
  .main { padding: 10px; }
  .title { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
}
</style>
