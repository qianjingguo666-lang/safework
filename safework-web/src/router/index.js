import { createRouter, createWebHistory } from 'vue-router'
import Layout from '@/layout/Layout.vue'
import { useUserStore } from '@/stores/user'
import { isMobileDevice, preferDesktop } from '@/utils/mobile'

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
  // 扫纸质作业票上的二维码验真：不登录也能看
  { path: '/verify/ticket/:token', component: () => import('@/views/verify/index.vue'), meta: { title: '作业票验真', public: true } },
  // 手机端：现场人员用。要登录，不走菜单（每个接口自己检查权限），自己的标题栏 + 底部标签栏
  {
    path: '/m',
    component: () => import('@/views/mobile/MobileLayout.vue'),
    children: [
      { path: '', component: () => import('@/views/mobile/HomeView.vue'), meta: { title: 'SafeWork', tab: true } },
      { path: 'tickets', component: () => import('@/views/mobile/TicketsView.vue'), meta: { title: '作业票', tab: true } },
      { path: 'ticket/:id', component: () => import('@/views/mobile/TicketView.vue'), meta: { title: '作业票详情', back: true, backTo: '/m/tickets' } },
      { path: 'hazards', component: () => import('@/views/mobile/HazardsView.vue'), meta: { title: '隐患', tab: true } },
      { path: 'hazard/:id', component: () => import('@/views/mobile/HazardView.vue'), meta: { title: '隐患详情', back: true, backTo: '/m/hazards' } },
      { path: 'report', component: () => import('@/views/mobile/ReportView.vue'), meta: { title: '上报隐患', back: true } },
      { path: 'messages', component: () => import('@/views/mobile/MessagesView.vue'), meta: { title: '消息', tab: true } },
      { path: 'me', component: () => import('@/views/mobile/MeView.vue'), meta: { title: '我的', tab: true } }
    ]
  },
  // 打印页：要登录，但不放在布局里（没有菜单、顶栏，只有 A4 票面）
  { path: '/ticket/print/:id', component: () => import('@/views/ticket/print/index.vue'), meta: { title: '打印作业票' } },
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
      if (loader && m.component.startsWith('screen/')) {
        // 大屏：全屏显示，不放在布局里（没有侧边栏、顶栏）
        router.addRoute({ path: m.path, component: loader, meta: { title: m.menuName, perm: m.perms } })
      } else if (loader) {
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
  // 手机打开首页时自动进手机版（平台管理员没有现场业务，不跳；点过“切换到电脑版”的也不跳）
  if (to.path === '/home' && !store.isPlatform && isMobileDevice() && !preferDesktop()) {
    return '/m'
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
