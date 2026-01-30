<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FeedItem } from '../api'
import { useAuthStore } from '../store'

const auth = useAuthStore()
const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'published', feed: FeedItem): void
}>()

const title = ref('')
const content = ref('')
const imageUrlsJson = ref('')
const selectedTags = ref<string[]>([])
const visibility = ref<'public' | 'friends' | 'private'>('public')
const submitting = ref(false)
const titleMaxLen = 80
const contentMaxLen = 2000

const TAG_OPTIONS = ['游记', '攻略', '经验分享']
const VISIBILITY_OPTIONS = [
  { value: 'public', label: '公开' },
  { value: 'friends', label: '好友' },
  { value: 'private', label: '私密' },
]

watch(
  () => props.visible,
  (v) => {
    if (!v) return
    title.value = ''
    content.value = ''
    imageUrlsJson.value = ''
    selectedTags.value = []
    visibility.value = 'public'
  }
)

function toggleTag(tag: string) {
  const i = selectedTags.value.indexOf(tag)
  if (i === -1) selectedTags.value = [...selectedTags.value, tag]
  else selectedTags.value = selectedTags.value.filter((t) => t !== tag)
}

const canSubmit = () => {
  if (!content.value.trim()) return false
  if (content.value.length > contentMaxLen) return false
  return true
}

async function submit() {
  if (!canSubmit()) {
    ElMessage.warning('请输入内容，且不超过 ' + contentMaxLen + ' 字')
    return
  }
  submitting.value = true
  try {
    const { feedsApi } = await import('../api')
    const id = await feedsApi.create({
      content: content.value.trim(),
      imageUrlsJson: imageUrlsJson.value.trim() || undefined,
    })
    const feed: FeedItem = {
      id,
      content: content.value.trim(),
      imageUrlsJson: imageUrlsJson.value.trim() || null,
      authorName: auth.nickname || '我',
      createdAt: new Date().toISOString(),
    }
    emit('published', feed)
    emit('update:visible', false)
    ElMessage.success('发布成功')
  } catch (e: any) {
    ElMessage.error(e.message || '发布失败')
  } finally {
    submitting.value = false
  }
}

function close() {
  emit('update:visible', false)
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    title="发布动态"
    width="520px"
    class="publish-dialog"
    @update:model-value="close"
  >
    <div class="form-block">
      <label class="label">内容 <span class="required">*</span></label>
      <el-input
        v-model="content"
        type="textarea"
        :rows="5"
        placeholder="分享你的旅途见闻、攻略或经验..."
        maxlength="contentMaxLen"
        show-word-limit
      />
    </div>
    <div class="form-block">
      <label class="label">图片 URL（可选，多个用英文逗号或 JSON 数组）</label>
      <el-input
        v-model="imageUrlsJson"
        type="textarea"
        :rows="2"
        placeholder='例如：["https://example.com/1.jpg","https://example.com/2.jpg"]'
      />
    </div>
    <div class="form-block">
      <label class="label">标签</label>
      <div class="tag-options">
        <el-tag
          v-for="t in TAG_OPTIONS"
          :key="t"
          :type="selectedTags.includes(t) ? 'primary' : 'info'"
          effect="plain"
          class="tag-opt"
          @click="toggleTag(t)"
        >
          {{ t }}
        </el-tag>
      </div>
    </div>
    <div class="form-block">
      <label class="label">公开范围</label>
      <el-radio-group v-model="visibility">
        <el-radio v-for="opt in VISIBILITY_OPTIONS" :key="opt.value" :value="opt.value as string">
          {{ opt.label }}
        </el-radio>
      </el-radio-group>
    </div>
    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" :loading="submitting" :disabled="!canSubmit()" @click="submit">
        发布
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.publish-dialog :deep(.el-dialog__body) {
  padding-top: 8px;
}

.form-block {
  margin-bottom: 18px;
}

.label {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: #334155;
  margin-bottom: 8px;
}

.required {
  color: #dc2626;
}

.tag-options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag-opt {
  cursor: pointer;
}
</style>
