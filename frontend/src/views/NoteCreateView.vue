<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElCard, ElInput, ElSelect, ElOption, ElButton, ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { api, routesApi, notesApi } from '../api'
import { useAuthStore } from '../store'

interface MyPlanOption {
  id: number
  destination: string
  startDate: string
  endDate: string
}

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const noteId = computed(() => {
  const id = route.params.id
  return id ? Number(id) : null
})

const isEditMode = computed(() => noteId.value != null)

const title = ref('')
const content = ref('')
const coverImage = ref('')
const destination = ref('')
const relatedPlanId = ref<number | null>(null)
const myPlans = ref<MyPlanOption[]>([])
const loading = ref(false)
const loadingNote = ref(false)
const errorMsg = ref('')

const canSubmit = computed(() => title.value.trim().length > 0 && content.value.trim().length > 0)

const pageTitle = computed(() => isEditMode.value ? '编辑游记' : '写游记')
const pageSubtitle = computed(() => isEditMode.value ? '修改你的旅行故事' : '分享你的旅行故事，帮助更多旅友')
const submitButtonText = computed(() => loading.value ? '保存中…' : isEditMode.value ? '保存修改' : '发布游记')

const fetchMyPlans = async () => {
  try {
    const list = await routesApi.myPlans()
    myPlans.value = list || []
  } catch {
    myPlans.value = []
  }
}

const loadNote = async () => {
  if (!noteId.value) return
  loadingNote.value = true
  try {
    const note = await notesApi.getOne(noteId.value)
    title.value = note.title || ''
    content.value = note.content || ''
    coverImage.value = note.coverImage || ''
    destination.value = note.destination || ''
    relatedPlanId.value = note.relatedPlanId || null
  } catch (e: any) {
    ElMessage.error(e.message || '加载游记失败')
    router.push('/notes')
  } finally {
    loadingNote.value = false
  }
}

const onSubmit = async () => {
  if (!title.value || !content.value) {
    errorMsg.value = '请填写标题和内容'
    ElMessage.warning('请填写标题和内容')
    return
  }
  loading.value = true
  errorMsg.value = ''
  try {
    if (isEditMode.value && noteId.value) {
      // 编辑模式：更新游记
      await notesApi.update(noteId.value, {
        title: title.value.trim(),
        content: content.value.trim(),
        coverImage: coverImage.value.trim() || undefined,
        relatedPlanId: relatedPlanId.value || undefined,
        destination: destination.value.trim() || undefined,
      })
      ElMessage.success('保存成功')
      router.push(`/notes/${noteId.value}`)
    } else {
      // 创建模式：新建游记
      const resp = await api.post('/notes', {
        title: title.value.trim(),
        content: content.value.trim(),
        coverImage: coverImage.value.trim() || undefined,
        relatedPlanId: relatedPlanId.value || undefined,
        destination: destination.value.trim() || undefined,
      })
      const id = resp.data.data
      ElMessage.success('发布成功')
      router.push(`/notes/${id}`)
    }
  } catch (e: any) {
    errorMsg.value = e.response?.data?.message || (isEditMode.value ? '保存失败' : '发布失败')
    ElMessage.error(errorMsg.value)
  } finally {
    loading.value = false
  }
}

const handleBack = () => {
  if (isEditMode.value && noteId.value) {
    router.push(`/notes/${noteId.value}`)
  } else {
    router.push('/notes')
  }
}

onMounted(async () => {
  await fetchMyPlans()
  if (isEditMode.value) {
    await loadNote()
  }
})

watch(() => route.params.id, async (newId) => {
  if (newId) {
    await loadNote()
  } else {
    // 重置表单
    title.value = ''
    content.value = ''
    coverImage.value = ''
    destination.value = ''
    relatedPlanId.value = null
  }
})
</script>

<template>
  <div class="note-create-page">
    <!-- 返回按钮：与游记详情页同款固定圆形 -->
    <div class="note-back-button">
      <el-button :icon="ArrowLeft" circle @click="handleBack" />
    </div>

    <div class="note-create-container">
      <el-card class="create-card" shadow="never">
        <template #header>
          <div class="card-header">
            <h2 class="card-title">{{ pageTitle }}</h2>
            <p class="card-subtitle">{{ pageSubtitle }}</p>
          </div>
        </template>

        <div v-if="loadingNote" class="loading-wrap">
          <el-skeleton :rows="8" animated />
        </div>

        <form v-else @submit.prevent="onSubmit" class="note-form">
          <!-- 标题 -->
          <div class="form-item">
            <label class="form-label">
              <span class="label-text">标题 <span class="required">*</span></span>
            </label>
            <el-input
              v-model="title"
              placeholder="例如：一场说走就走的日本关西之旅"
              maxlength="80"
              show-word-limit
              class="form-input"
            />
          </div>

          <!-- 目的地 -->
          <div class="form-item">
            <label class="form-label">
              <span class="label-text">目的地（可选）</span>
            </label>
            <el-input
              v-model="destination"
              placeholder="例如：日本·关西、云南·大理"
              maxlength="50"
              class="form-input"
            />
          </div>

          <!-- 关联行程 -->
          <div class="form-item">
            <label class="form-label">
              <span class="label-text">关联行程（可选）</span>
            </label>
            <el-select
              v-model="relatedPlanId"
              placeholder="选择关联的行程"
              clearable
              class="form-select"
            >
              <el-option :value="null" label="不关联行程" />
              <el-option
                v-for="plan in myPlans"
                :key="plan.id"
                :value="plan.id"
                :label="`${plan.destination}（${plan.startDate} ~ ${plan.endDate}）`"
              />
            </el-select>
          </div>

          <!-- 封面图片 -->
          <div class="form-item">
            <label class="form-label">
              <span class="label-text">封面图片 URL（可选）</span>
            </label>
            <el-input
              v-model="coverImage"
              placeholder="粘贴一张网络图片链接"
              class="form-input"
            />
            <p v-if="coverImage" class="form-hint">预览：</p>
            <div v-if="coverImage" class="cover-preview">
              <img :src="coverImage" alt="封面预览" @error="coverImage = ''" />
            </div>
          </div>

          <!-- 正文内容 -->
          <div class="form-item">
            <label class="form-label">
              <span class="label-text">正文内容 <span class="required">*</span></span>
            </label>
            <el-input
              v-model="content"
              type="textarea"
              :rows="12"
              placeholder="可以简单按天记录行程、感受和小贴士，后续可替换为富文本编辑器。"
              maxlength="10000"
              show-word-limit
              class="form-textarea"
            />
          </div>

          <!-- 错误提示 -->
          <p v-if="errorMsg" class="form-error">{{ errorMsg }}</p>

          <!-- 操作按钮 -->
          <div class="form-actions">
            <el-button @click="handleBack">取消</el-button>
            <el-button
              type="primary"
              :disabled="!canSubmit || loading"
              :loading="loading"
              native-type="submit"
            >
              {{ submitButtonText }}
            </el-button>
          </div>
        </form>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.note-create-page {
  min-height: 100vh;
  background: #f5f7fa;
  padding: 24px 20px 48px;
}

.note-create-container {
  max-width: 900px;
  margin: 0 auto;
}

.create-card {
  border-radius: 12px;
  border: none;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.create-card :deep(.el-card__header) {
  padding: 24px 24px 16px;
  border-bottom: 1px solid #f0f2f5;
}

.create-card :deep(.el-card__body) {
  padding: 24px;
}

.card-header {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

/* 返回按钮：与游记详情页同款 */
.note-back-button {
  position: fixed;
  top: 80px;
  left: 20px;
  z-index: 200;
}

.note-back-button :deep(.el-button) {
  width: 44px;
  height: 44px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.1);
  transition: all 0.3s ease;
}

.note-back-button :deep(.el-button:hover) {
  background: #0f766e;
  border-color: #0f766e;
  color: #ffffff;
  transform: translateX(-4px);
  box-shadow: 0 6px 20px rgba(15, 118, 110, 0.3);
}

.card-title {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  color: #303133;
}

.card-subtitle {
  margin: 0;
  font-size: 14px;
  color: #909399;
}

.loading-wrap {
  padding: 20px 0;
}

.note-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-label {
  display: block;
}

.label-text {
  font-size: 14px;
  font-weight: 500;
  color: #606266;
}

.required {
  color: #f56c6c;
}

.form-input,
.form-select {
  width: 100%;
}

.form-textarea :deep(.el-textarea__inner) {
  font-size: 15px;
  line-height: 1.6;
  color: #303133;
  resize: vertical;
}

.form-textarea :deep(.el-textarea__inner)::placeholder {
  color: #c0c4cc;
}

.form-hint {
  margin: 8px 0 0;
  font-size: 13px;
  color: #909399;
}

.cover-preview {
  margin-top: 8px;
  width: 100%;
  max-width: 400px;
  aspect-ratio: 16 / 9;
  border-radius: 8px;
  overflow: hidden;
  background: #f5f7fa;
  border: 1px solid #ebeef5;
}

.cover-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.form-error {
  margin: 0;
  padding: 10px 12px;
  background: #fef0f0;
  border: 1px solid #fde2e2;
  border-radius: 8px;
  font-size: 13px;
  color: #f56c6c;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 8px;
  padding-top: 20px;
  border-top: 1px solid #f0f2f5;
}

.form-actions .el-button {
  border-radius: 8px;
  padding: 10px 20px;
}

@media (max-width: 768px) {
  .note-create-page {
    padding: 16px 12px 32px;
  }

  .note-back-button {
    top: 70px;
    left: 12px;
  }

  .note-back-button :deep(.el-button) {
    width: 40px;
    height: 40px;
  }

  .create-card :deep(.el-card__header),
  .create-card :deep(.el-card__body) {
    padding: 16px;
  }

  .card-title {
    font-size: 20px;
  }

  .note-form {
    gap: 16px;
  }

  .form-actions {
    flex-direction: column-reverse;
  }

  .form-actions .el-button {
    width: 100%;
  }
}
</style>
