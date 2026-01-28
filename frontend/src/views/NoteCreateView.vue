<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'

interface MyPlanOption {
  id: number
  destination: string
  startDate: string
  endDate: string
}

const title = ref('')
const content = ref('')
const coverImage = ref('')
const destination = ref('')
const relatedPlanId = ref<number | null>(null)
const myPlans = ref<MyPlanOption[]>([])
const loading = ref(false)
const errorMsg = ref('')

const router = useRouter()

const fetchMyPlans = async () => {
  try {
    const resp = await api.get('/api/routes/my')
    myPlans.value = resp.data.data || []
  } catch {
    myPlans.value = []
  }
}

const onSubmit = async () => {
  if (!title.value || !content.value) {
    errorMsg.value = '请填写标题和内容'
    return
  }
  loading.value = true
  errorMsg.value = ''
  try {
    const resp = await api.post('/api/notes', {
      title: title.value,
      content: content.value,
      coverImage: coverImage.value || undefined,
      relatedPlanId: relatedPlanId.value || undefined,
      destination: destination.value || undefined,
    })
    const id = resp.data.data
    router.push(`/notes/${id}`)
  } catch (e: any) {
    errorMsg.value = e.response?.data?.message || '发布失败'
  } finally {
    loading.value = false
  }
}

onMounted(fetchMyPlans)
</script>

<template>
  <div class="card note-card">
    <h2>写游记</h2>
    <p class="text-subtle">分享你的旅行故事，帮助更多旅友。</p>
    <form @submit.prevent="onSubmit" class="form">
      <label>
        <span class="form-label">标题</span>
        <input v-model="title" class="form-input" placeholder="例如：一场说走就走的日本关西之旅" />
      </label>
      <label>
        <span class="form-label">目的地（可选）</span>
        <input v-model="destination" class="form-input" placeholder="例如：日本·关西、云南·大理" />
      </label>
      <label>
        <span class="form-label">关联行程（可选）</span>
        <select v-model.number="relatedPlanId" class="form-select">
          <option :value="null">不关联行程</option>
          <option v-for="plan in myPlans" :key="plan.id" :value="plan.id">
            {{ plan.destination }}（{{ plan.startDate }} ~ {{ plan.endDate }}）
          </option>
        </select>
      </label>
      <label>
        <span class="form-label">封面图片 URL（可选）</span>
        <input v-model="coverImage" class="form-input" placeholder="粘贴一张网络图片链接" />
      </label>
      <label>
        <span class="form-label">正文内容</span>
        <textarea
          v-model="content"
          class="form-input"
          rows="8"
          placeholder="可以简单按天记录行程、感受和小贴士，后续可替换为富文本编辑器。"
        ></textarea>
      </label>
      <p v-if="errorMsg" class="error">{{ errorMsg }}</p>
      <div class="actions">
        <button class="btn primary" type="submit" :disabled="loading">
          {{ loading ? '发布中...' : '发布游记' }}
        </button>
      </div>
    </form>
  </div>
</template>

<style scoped>
.note-card {
  width: 900px;
  max-width: 100%;
  padding: 28px 24px 24px;
}

.form {
  margin-top: 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

textarea.form-input {
  resize: vertical;
}

.error {
  color: #dc2626;
  font-size: 13px;
}

.actions {
  display: flex;
  justify-content: flex-end;
}
</style>

