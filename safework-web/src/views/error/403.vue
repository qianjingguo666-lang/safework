<template>
  <el-result icon="warning" title="403" sub-title="没有访问权限，请联系企业管理员分配">
    <template #extra><el-button type="primary" @click="$router.push('/home')">返回首页</el-button></template>
  </el-result>
</template>
