<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, routesApi } from '../api'

interface MyPlanOption {
  id: number
  destination: string
  startDate: string
  endDate: string
}

const relatedPlanId = ref<number | null>(null)
const destination = ref('')
const startDate = ref('')
const endDate = ref('')
const minPeople = ref(1)
const maxPeople = ref(4)
const budgetMin = ref<number | null>(null)
const budgetMax = ref<number | null>(null)
const expectedMateDesc = ref('')
const visibility = ref('public')
const loading = ref(false)
const errorMsg = ref('')
const myPlans = ref<MyPlanOption[]>([])
const route = useRoute()
const router = useRouter()

const fetchMyPlans = async () => {
  try {
    const list = await routesApi.myPlans()
    myPlans.value = list || []
  } catch {
    myPlans.value = []
  }
}

const onSubmit = async () => {
  if (!destination.value || !startDate.value || !endDate.value) {
    errorMsg.value = '请填写目的地和时间范围'
    return
  }
  loading.value = true
  errorMsg.value = ''
  try {
    await api.post('/companion/posts', {
      relatedPlanId: relatedPlanId.value || undefined,
      destination: destination.value,
      startDate: startDate.value,
      endDate: endDate.value,
      minPeople: minPeople.value,
      maxPeople: maxPeople.value,
      budgetMin: budgetMin.value ?? undefined,
      budgetMax: budgetMax.value ?? undefined,
      expectedMateDesc: expectedMateDesc.value || undefined,
      visibility: visibility.value,
    })
    router.push('/companion')
  } catch (e: any) {
    errorMsg.value = e.response?.data?.message || '发布失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  // 支持从「路线详情页」带参跳转，自动预填关联行程和基础字段
  const q = route.query
  const qPlanId = typeof q.planId === 'string' ? Number(q.planId) : null
  if (qPlanId) relatedPlanId.value = qPlanId
  if (typeof q.destination === 'string') destination.value = q.destination
  if (typeof q.startDate === 'string') startDate.value = q.startDate
  if (typeof q.endDate === 'string') endDate.value = q.endDate

  fetchMyPlans()
})
</script>

<template>
  <div class="card publish-card">
    <h2>发布结伴信息</h2>
    <p class="text-subtle">关联你的行程，寻找志同道合的旅友一起出发。</p>
    <form @submit.prevent="onSubmit" class="form-grid">
      <div class="field">
        <span class="form-label">关联行程（可选）</span>
        <select v-model.number="relatedPlanId" class="form-select">
          <option :value="null">不关联行程</option>
          <option v-for="plan in myPlans" :key="plan.id" :value="plan.id">
            {{ plan.destination }}（{{ plan.startDate }} ~ {{ plan.endDate }}）
          </option>
        </select>
      </div>
      <div class="field">
        <span class="form-label">目的地</span>
        <input v-model="destination" class="form-input" placeholder="城市、国家或路线名称" />
      </div>
      <div class="field">
        <span class="form-label">出发日期</span>
        <input v-model="startDate" class="form-input" type="date" />
      </div>
      <div class="field">
        <span class="form-label">结束日期</span>
        <input v-model="endDate" class="form-input" type="date" />
      </div>
      <div class="field">
        <span class="form-label">最少人数</span>
        <input v-model.number="minPeople" class="form-input" type="number" min="1" />
      </div>
      <div class="field">
        <span class="form-label">最多人数</span>
        <input v-model.number="maxPeople" class="form-input" type="number" min="1" />
      </div>
      <div class="field">
        <span class="form-label">预算下限（元，可选）</span>
        <input v-model.number="budgetMin" class="form-input" type="number" min="0" />
      </div>
      <div class="field">
        <span class="form-label">预算上限（元，可选）</span>
        <input v-model.number="budgetMax" class="form-input" type="number" min="0" />
      </div>
      <div class="field full">
        <span class="form-label">对旅友的期待（可选）</span>
        <textarea
          v-model="expectedMateDesc"
          class="form-input"
          rows="3"
          placeholder="比如：作息规律、喜欢拍照、会开车、性格安静/活泼等"
        ></textarea>
      </div>
      <div class="field">
        <span class="form-label">可见性</span>
        <select v-model="visibility" class="form-select">
          <option value="public">公开</option>
          <option value="friends">仅好友（预留）</option>
          <option value="private">私密</option>
        </select>
      </div>
      <p v-if="errorMsg" class="error">{{ errorMsg }}</p>
      <div class="actions">
        <button class="btn primary" type="submit" :disabled="loading">
          {{ loading ? '发布中...' : '发布结伴信息' }}
        </button>
      </div>
    </form>
  </div>
</template>

<style scoped>
.publish-card {
  width: 900px;
  max-width: 100%;
  padding: 28px 24px 24px;
}

h2 {
  margin: 0 0 6px;
  font-size: 22px;
}

.form-grid {
  margin-top: 20px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px 20px;
}

.field.full {
  grid-column: 1 / -1;
}

textarea.form-input {
  resize: vertical;
}

.error {
  grid-column: 1 / -1;
  color: #dc2626;
  font-size: 13px;
}

.actions {
  grid-column: 1 / -1;
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 768px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>

