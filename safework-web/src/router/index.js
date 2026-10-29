import { createRouter, createWebHistory } from 'vue-router'
import Layout from '@/layout/Layout.vue'
import { useUserStore } from '@/stores/user'

/**
 * 路由分两部分：
 * 1. 固定路由：登录、首页、403、404
 * 2. 动态路由：登录后根据后端返回的菜单（component 字段）动态添加，没有权限的页面根本不会注册
 */
const views = import.meta.glob('../views/**/index.vue')

const routes = [
  { path: '/login', component: () => import('@/views/login/index.vue'), meta: { title: '登录' } },
  // 扫码页：现场人员不登录也能看
  { path: '/scan/:token', component: () => import('@/views/scan/index.vue'), meta: { title: '受限空间', public: true } },
  {
    path: '/',
    name: 'Layout',
    component: Layout,
    redirect: '/home',
    children: [
      { path: 'home', component: () => import('@/views/home/index.vue'), meta: { title: '首页' } },
      // 消息中心：所有登录用户都有，不走菜单权限
      { path: 'message', component: () => import('@/views/message/index.vue'), meta: { title: '消息中心' } },
      { path: '403', component: () => import('@/views/error/403.vue'), meta: { title: '无权限' } }
    ]
  }
]

const router = createRouter({ history: createWebHistory(), routes })

function addDynamicRoutes(menus) {
  const walk = (list) => list.forEach((m) => {
    if (m.menuType === 'C' && m.component) {
      const loader = views[`../views/${m.component}.vue`]
      if (loader) {
        router.addRoute('Layout', { path: m.path, component: loader, meta: { title: m.menuName, perm: m.perms } })
      } else {
        console.warn(`菜单【${m.menuName}】的组件不存在：${m.component}`)
      }
    }
    if (m.children?.length) walk(m.children)
  })
  walk(menus)
  // 404 放在布局里面显示；没有权限的页面根本没有注册，也会落到这里
  router.addRoute('Layout', { path: '/:pathMatch(.*)*', component: () => import('@/views/error/404.vue'), meta: { title: '页面不存在' } })
}

router.beforeEach(async (to) => {
  const store = useUserStore()
  if (to.meta.public) {
    return true
  }
  if (to.path === '/login') {
    return store.token ? '/' : true
  }
  if (!store.token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (!store.loaded) {
    try {
      await store.fetchMe()
      addDynamicRoutes(store.menus)
    } catch {
      store.reset()
      return { path: '/login', query: { redirect: to.fullPath } }
    }
    // 动态路由刚加上，需要重新匹配一次
    return to.fullPath
  }
  if (to.meta.perm && !store.hasPerm(to.meta.perm)) {
    return '/403'
  }
  return true
})

router.afterEach((to) => {
  document.title = `${to.meta.title ? to.meta.title + ' - ' : ''}SafeWork`
})

export default router
