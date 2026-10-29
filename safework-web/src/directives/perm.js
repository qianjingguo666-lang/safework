import { useUserStore } from '@/stores/user'

/**
 * 按钮级权限：<el-button v-perm="'ticket:add'">新建</el-button>
 * 没有权限时直接移除元素。注意这只是体验优化，真正的权限控制在后端。
 */
export default {
  mounted(el, binding) {
    const store = useUserStore()
    if (!store.hasPerm(binding.value)) {
      el.parentNode && el.parentNode.removeChild(el)
    }
  }
}
