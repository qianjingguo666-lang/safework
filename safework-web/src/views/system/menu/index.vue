<template>
  <div class="page-card">
    <div class="toolbar">
      <span class="hint">菜单由平台统一维护，所有企业共用；企业通过“角色管理 → 分配权限”控制可见范围</span>
    </div>
    <el-table v-loading="loading" :data="tree" row-key="id" default-expand-all border>
      <el-table-column prop="menuName" label="名称" min-width="180" />
      <el-table-column label="类型" width="90">
        <template #default="{ row }">
          <el-tag :type="TYPE[row.menuType].tag" size="small">{{ TYPE[row.menuType].label }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="path" label="路由" min-width="140" />
      <el-table-column prop="component" label="组件" min-width="160" />
      <el-table-column prop="perms" label="权限码" min-width="160" />
      <el-table-column prop="sort" label="排序" width="70" />
    </el-table>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { menuTree } from '@/api'

const TYPE = { M: { label: '目录', tag: 'primary' }, C: { label: '页面', tag: 'success' }, F: { label: '按钮', tag: 'info' } }
const loading = ref(false)
const tree = ref([])

onMounted(async () => {
  loading.value = true
  try {
    tree.value = await menuTree()
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.hint { color: #888; font-size: 13px; }
</style>
