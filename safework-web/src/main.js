import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'
import perm from './directives/perm'
import './styles.css'

// 接口错误已经在 request.js 里弹窗提示过了，这里不再当成未处理异常
window.addEventListener('unhandledrejection', (e) => {
  if (e.reason?.handled) e.preventDefault()
})

const app = createApp(App)
app.config.errorHandler = (err) => {
  if (!err?.handled) console.error(err)
}
for (const [name, comp] of Object.entries(ElementPlusIconsVue)) {
  app.component(name, comp)
}
app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.directive('perm', perm)
app.mount('#app')
