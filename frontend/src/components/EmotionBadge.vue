<template>
  <span
    class="emotion-pill"
    :style="{ color: meta.color, backgroundColor: meta.bg }"
  >
    <span>{{ meta.icon }}</span>
    <span>{{ meta.label }}</span>
  </span>
</template>

<script setup>
import { computed } from 'vue'
import { getEmotionMeta } from '@/utils/emotions'

const props = defineProps({
  emotion: { type: String, default: 'UNCERTAIN' }
})
const meta = computed(() => getEmotionMeta(props.emotion))
</script>
