<template>
  <div class="login-page">
    <div class="intro">
      <h1>SafeWork</h1>
      <p class="sub">智能安全作业管理平台</p>
      <ul>
        <li>受限空间、动火、高处作业票在线申请与审批</li>
        <li>多企业 SaaS：按套餐开通功能，数据严格隔离</li>
        <li>AI 安全规程问答与作业票智能预审</li>
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
        <el-form-item v-if="captcha.enabled" prop="captchaCode">
          <div class="captcha-row">
            <el-input v-model="form.captchaCode" placeholder="计算结果" prefix-icon="Key" maxlength="3" />
            <img v-if="captcha.image" :src="captcha.image" class="captcha-img" alt="验证码" title="看不清？点击换一张" @click="refreshCaptcha">
            <div v-else class="captcha-img captcha-empty" @click="refreshCaptcha">点击获取</div>
          </div>
        </el-form-item>
        <el-button type="primary" class="submit" :loading="loading" @click="onSubmit">登 录</el-button>
      </el-form>
      <div class="demo">
        <div class="demo-title">演示账号（密码均为 admin123）</div>
        <div class="demo-list">
          <el-link v-for="a in demoAccounts" :key="a.label" type="primary" @click="fill(a)">{{ a.label }}</el-link>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { getCaptcha } from '@/api'

const LAST_TENANT_KEY = 'safework_last_tenant'
function lastTenant() {
  try { return localStorage.getItem(LAST_TENANT_KEY) || 'demo' } catch { return 'demo' }
}

const store = useUserStore()
const router = useRouter()
const route = useRoute()
const formRef = ref()
const loading = ref(false)
const form = reactive({ tenantCode: lastTenant(), username: '', password: '', captchaKey: '', captchaCode: '' })
const rules = {
  tenantCode: [{ required: true, message: '请输入企业编码', trigger: 'blur' }],
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  captchaCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
}
const demoAccounts = [
  { label: '企业管理员', tenant: 'demo', username: 'admin' },
  { label: '安全员', tenant: 'demo', username: 'zhangsan' },
  { label: '作业负责人', tenant: 'demo', username: 'lisi' },
  { label: '监护人', tenant: 'demo', username: 'zhaoliu' },
  { label: '平台管理员', tenant: 'platform', username: 'admin' }
]

// 验证码：后端关闭验证码时 enabled = false，不显示输入框
const captcha = reactive({ enabled: true, image: '' })
async function refreshCaptcha() {
  form.captchaCode = ''
  try {
    const data = await getCaptcha()
    captcha.enabled = data.enabled
    captcha.image = data.image || ''
    form.captchaKey = data.captchaKey || ''
  } catch {
    captcha.image = ''
  }
}

function fill(a) {
  form.tenantCode = a.tenant
  form.username = a.username
  form.password = 'admin123'
}

async function onSubmit() {
  await formRef.value.validate()
  loading.value = true
  try {
    await store.login(form)
    try { localStorage.setItem(LAST_TENANT_KEY, form.tenantCode) } catch { /* ignore */ }
    ElMessage.success('登录成功')
    router.replace(route.query.redirect || '/')
  } catch {
    // 验证码是一次性的：不管错在哪一步，都要换一张新的
    refreshCaptcha()
  } finally {
    loading.value = false
  }
}

onMounted(refreshCaptcha)
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
.captcha-row { display: flex; gap: 10px; width: 100%; }
.captcha-row .el-input { flex: 1; }
.captcha-img { width: 120px; height: 40px; border-radius: 4px; cursor: pointer; flex-shrink: 0; border: 1px solid #dcdfe6; box-sizing: border-box; }
.captcha-empty { display: flex; align-items: center; justify-content: center; font-size: 12px; color: #999; }
.demo { margin-top: 20px; font-size: 13px; color: #888; }
.demo-list { display: flex; gap: 16px; flex-wrap: wrap; margin-top: 8px; }
@media (max-width: 900px) {
  .intro { display: none; }
}
</style>
