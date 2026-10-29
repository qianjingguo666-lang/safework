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
        <el-tag effect="plain" class="tenant">{{ store.tenantName }}</el-tag>
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
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { changePassword } from '@/api'
import { ROLE_NAMES } from '@/utils/dict'

const store = useUserStore()
const router = useRouter()
const collapsed = ref(false)

// 后端菜单里的 icon 名称 → Element Plus 图标组件名
const ICONS = {
  setting: 'Setting', user: 'User', team: 'UserFilled', menu: 'Menu', safety: 'Warning', file: 'Document',
  shield: 'FirstAidKit', space: 'Box', hazard: 'WarningFilled', dept: 'OfficeBuilding', log: 'Tickets'
}
const iconOf = (name) => ICONS[name] || 'Document'
const pages = (list = []) => list.filter((c) => c.menuType === 'C')

const roleText = computed(() => store.roles.map((r) => ROLE_NAMES[r] || r).join('、') || '无')

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
.main { padding: 16px; }
@media (max-width: 768px) {
  .tenant, .name { display: none; }
}
</style>
