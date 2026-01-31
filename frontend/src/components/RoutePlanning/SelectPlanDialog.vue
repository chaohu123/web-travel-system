<script setup lang="ts">
import { ref, watch } from 'vue'
import type { PlanVariant } from '../../store/routePlanning'

const props = defineProps<{
  visible: boolean
  variants: PlanVariant[]
  /** 保存中（如保存路线/保存并进入结伴），确定按钮显示加载态并禁用 */
  loading?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'confirm', variantId: string): void
}>()

const selectedId = ref('')

watch(
  () => props.visible,
  (visible) => {
    if (visible && props.variants.length) {
      selectedId.value = props.variants.some((v) => v.id === selectedId.value)
        ? selectedId.value
        : props.variants[0].id
    }
  },
  { immediate: true }
)

watch(
  () => props.variants,
  (variants) => {
    if (props.visible && variants.length && !variants.some((v) => v.id === selectedId.value)) {
      selectedId.value = variants[0].id
    }
  },
  { deep: true }
)

function handleConfirm() {
  if (selectedId.value) {
    emit('confirm', selectedId.value)
    emit('update:visible', false)
  }
}

function handleCancel() {
  emit('update:visible', false)
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    title="选择路线方案"
    width="480"
    align-center
    destroy-on-close
    class="select-plan-dialog"
    @update:model-value="(v: boolean) => emit('update:visible', v)"
  >
    <p class="dialog-tip">请从以下方案中选择一个路线：</p>
    <div class="variant-list">
      <button
        v-for="v in variants"
        :key="v.id"
        type="button"
        class="variant-card"
        :class="{ active: selectedId === v.id }"
        @click="selectedId = v.id"
      >
        <span class="variant-name">{{ v.name }}</span>
        <span class="variant-days">{{ v.days?.length ?? 0 }} 天 · {{ v.days?.reduce((s, d) => s + (d.items?.length ?? 0), 0) ?? 0 }} 个景点</span>
      </button>
    </div>
    <template #footer>
      <div class="dialog-footer">
        <el-button :disabled="loading" @click="handleCancel">取消</el-button>
        <el-button type="primary" :loading="loading" :disabled="!selectedId || loading" @click="handleConfirm">
          {{ loading ? '保存中…' : '确定' }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.select-plan-dialog :deep(.el-dialog__body) {
  padding-top: 8px;
}
.dialog-tip {
  margin: 0 0 12px;
  font-size: 14px;
  color: var(--el-text-color-regular);
}
.variant-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.variant-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  padding: 14px 16px;
  border: 1px solid var(--el-border-color);
  border-radius: 12px;
  background: var(--el-fill-color-blank);
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s;
  text-align: left;
}
.variant-card:hover {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
}
.variant-card.active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}
.variant-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.variant-days {
  margin-top: 4px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
