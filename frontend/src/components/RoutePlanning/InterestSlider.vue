<script setup lang="ts">
import { ref, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    label: string
    modelValue: number
    min?: number
    max?: number
  }>(),
  { min: 0, max: 100 }
)
const emit = defineEmits<{ 'update:modelValue': [value: number] }>()
const isDragging = ref(false)
const local = ref(props.modelValue)

watch(
  () => props.modelValue,
  (v) => {
    local.value = v
  },
  { immediate: true }
)

function onInput(e: Event) {
  const t = e.target as HTMLInputElement
  const v = Number(t.value)
  local.value = v
  emit('update:modelValue', v)
}
function onPointerDown() {
  isDragging.value = true
}
function onPointerUp() {
  isDragging.value = false
}
</script>

<template>
  <div
    class="interest-slider"
    :class="{ 'is-dragging': isDragging }"
    @pointerdown.capture="onPointerDown"
    @pointerup.capture="onPointerUp"
    @pointerleave.capture="onPointerUp"
  >
    <div class="flex items-center justify-between mb-1.5">
      <span class="text-sm font-medium text-slate-700">{{ label }}</span>
      <span
        class="text-sm font-semibold tabular-nums min-w-[2.5rem] text-right transition-colors"
        :class="isDragging ? 'text-teal-600' : 'text-slate-600'"
      >
        {{ local }}%
      </span>
    </div>
    <input
      type="range"
      :min="min"
      :max="max"
      :value="local"
      class="slider-input"
      :style="{ '--percent': local + '%' }"
      @input="onInput"
    />
  </div>
</template>

<style scoped>
.interest-slider {
  --track-height: 8px;
  --thumb-size: 20px;
}
.interest-slider.is-dragging {
  --thumb-scale: 1.15;
}
.slider-input {
  width: 100%;
  height: var(--track-height);
  -webkit-appearance: none;
  appearance: none;
  background: linear-gradient(
    to right,
    rgb(20 184 166) 0%,
    rgb(20 184 166) var(--percent, 0%),
    rgb(226 232 240) var(--percent, 0%),
    rgb(226 232 240) 100%
  );
  border-radius: 9999px;
  outline: none;
}
.slider-input::-webkit-slider-thumb {
  -webkit-appearance: none;
  width: var(--thumb-size);
  height: var(--thumb-size);
  border-radius: 50%;
  background: linear-gradient(135deg, #14b8a6 0%, #0d9488 100%);
  box-shadow: 0 2px 8px rgba(13, 148, 136, 0.4);
  cursor: grab;
  transform: scale(var(--thumb-scale, 1));
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}
.interest-slider.is-dragging .slider-input::-webkit-slider-thumb {
  cursor: grabbing;
  box-shadow: 0 4px 12px rgba(13, 148, 136, 0.5);
}
.slider-input::-moz-range-thumb {
  width: var(--thumb-size);
  height: var(--thumb-size);
  border-radius: 50%;
  border: none;
  background: linear-gradient(135deg, #14b8a6 0%, #0d9488 100%);
  box-shadow: 0 2px 8px rgba(13, 148, 136, 0.4);
  cursor: grab;
  transform: scale(var(--thumb-scale, 1));
}
.interest-slider.is-dragging .slider-input::-moz-range-thumb {
  cursor: grabbing;
}
</style>
