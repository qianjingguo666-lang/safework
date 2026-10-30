<template>
  <span class="risk-badge" :style="{ background: g.color, color: g.text }">{{ short ? g.short : g.label }}</span>
</template>

<script setup>
import { computed } from 'vue'
import { RISK_GRADES } from '@/utils/dict'

const props = defineProps({ level: { type: String, required: true }, short: Boolean })
const g = computed(() => RISK_GRADES[props.level] || RISK_GRADES.LOW)
</script>

<style scoped>
.risk-badge { display: inline-block; padding: 1px 8px; border-radius: 4px; font-size: 12px; font-weight: 600; line-height: 20px; white-space: nowrap; }
</style>
