<template>
  <div class="login-page">
    <div class="intro">
      <h1>SafeWork</h1>
      <p class="sub">智能安全作业管理平台</p>
      <ul>
        <li>受限空间、动火、高处作业票在线申请与审批</li>
        <li>多企业独立使用，数据严格隔离</li>
        <li>AI 安全规程问答与作业票智能预审（开发中）</li>
      </ul>
    </div>
    <el-card class="box" shadow="always">
      <h2>登录</h2>
      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="onSubmit">
        <el-form-item prop="tenantCode">
          <el-input v-model="form.tenantCode" placeholder="企业编码" prefix-icon="OfficeBuilding" />
        </el-form-item>
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" prefix-icon="Lock" show-password />
        </el-form-item>
        <el-button type="primary" class="submit" :loading="loading" @click="onSubmit">登 录</el-button>
      </el-form>
      <div class="demo">
        <div class="demo-title">演示账号（密码均为 admin123，企业编码 demo）</div>
        <div class="demo-list">
          <el-link v-for="a in demoAccounts" :key="a.username" type="primary" @click="fill(a)">{{ a.label }}</el-link>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const store = useUserStore()
const router = useRouter()
const route = useRoute()
const formRef = ref()
const loading = ref(false)
const form = reactive({ tenantCode: 'demo', username: '', password: '' })
const rules = {
  tenantCode: [{ required: true, message: '请输入企业编码', trigger: 'blur' }],
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}
const demoAccounts = [
  { label: '管理员', username: 'admin' },
  { label: '安全员', username: 'zhangsan' },
  { label: '作业负责人', username: 'lisi' },
  { label: '监护人', username: 'zhaoliu' }
]

function fill(a) {
  form.tenantCode = 'demo'
  form.username = a.username
  form.password = 'admin123'
}

async function onSubmit() {
  await formRef.value.validate()
  loading.value = true
  try {
    await store.login(form)
    ElMessage.success('登录成功')
    router.replace(route.query.redirect || '/')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh; display: flex; align-items: center; justify-content: center; gap: 80px;
  padding: 24px 16px; box-sizing: border-box;
  background: linear-gradient(135deg, #0f1b2d 0%, #1d3557 60%, #1f6feb 100%);
}
.intro { color: #fff; max-width: 420px; }
.intro h1 { font-size: 44px; margin: 0; letter-spacing: 1px; }
.intro .sub { font-size: 20px; opacity: .85; margin: 8px 0 28px; }
.intro ul { padding-left: 18px; line-height: 2; opacity: .8; }
.box { width: 380px; max-width: 100%; border-radius: 12px; }
.box h2 { margin: 4px 0 20px; }
.submit { width: 100%; }
.demo { margin-top: 20px; font-size: 13px; color: #888; }
.demo-list { display: flex; gap: 16px; flex-wrap: wrap; margin-top: 8px; }
@media (max-width: 900px) {
  .intro { display: none; }
}
</style>
