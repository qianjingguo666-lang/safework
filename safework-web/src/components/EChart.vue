<template>
  <div ref="el" class="echart" :style="{ height }" />
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart, LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

// 按需引入：只打包用到的图表类型，比整个 echarts 小一大半
echarts.use([LineChart, BarChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

const props = defineProps({
  option: { type: Object, required: true },
  height: { type: String, default: '280px' }
})
const el = ref()
let chart = null
let observer = null

onMounted(() => {
  chart = echarts.init(el.value)
  chart.setOption(props.option)
  // 侧边栏折叠、窗口变化时图表跟着变宽
  observer = new ResizeObserver(() => chart?.resize())
  observer.observe(el.value)
})
watch(() => props.option, (o) => chart?.setOption(o, true), { deep: true })
onBeforeUnmount(() => {
  observer?.disconnect()
  chart?.dispose()
})
</script>

<style scoped>
.echart { width: 100%; }
</style>
