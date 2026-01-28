<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'

const destination = ref('')
const startDate = ref('')
const endDate = ref('')
const budget = ref<number | null>(null)
const peopleCount = ref(1)
const pace = ref('normal')
const preferenceWeightsJson = ref('')
const loading = ref(false)
const errorMsg = ref('')

const router = useRouter()

const onSubmit = async () => {
  if (!destination.value || !startDate.value || !endDate.value) {
    errorMsg.value = '请填写目的地和出发/结束日期'
    return
  }
  loading.value = true
  errorMsg.value = ''
  try {
    const resp = await api.post('/api/routes', {
      destination: destination.value,
      startDate: startDate.value,
      endDate: endDate.value,
      budget: budget.value ?? 0,
      peopleCount: peopleCount.value,
      pace: pace.value,
      preferenceWeightsJson: preferenceWeightsJson.value || undefined,
    })
    const id = resp.data.data
    router.push(`/routes/${id}`)
  } catch (e: any) {
    errorMsg.value = e.response?.data?.message || '创建行程失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="route-card card">
    <h2>创建个性化行程</h2>
    <p class="text-subtle">填写你的旅行参数，我们会为你生成基础行程草稿。</p>
    <form @submit.prevent="onSubmit" class="form-grid">
      <div class="field">
        <span class="form-label">目的地</span>
        <input v-model="destination" class="form-input" placeholder="城市、国家或地区" />
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
        <span class="form-label">总预算（元）</span>
        <input v-model.number="budget" class="form-input" type="number" min="0" />
      </div>
      <div class="field">
        <span class="form-label">同行人数</span>
        <input v-model.number="peopleCount" class="form-input" type="number" min="1" />
      </div>
      <div class="field">
        <span class="form-label">行程节奏</span>
        <select v-model="pace" class="form-select">
          <option value="rush">暴走打卡型</option>
          <option value="normal">适中平衡型</option>
          <option value="relax">悠闲度假型</option>
        </select>
      </div>
      <div class="field full">
        <span class="form-label">兴趣偏好 JSON（可选，高级用户）</span>
        <input
          v-model="preferenceWeightsJson"
          class="form-input"
          placeholder='例如：{"nature":0.8,"culture":0.6,"food":0.9}'
        />
      </div>
      <p v-if="errorMsg" class="error">{{ errorMsg }}</p>
      <div class="actions">
        <button class="btn primary" type="submit" :disabled="loading">
          {{ loading ? '生成中...' : '生成行程' }}
        </button>
      </div>
    </form>
  </div>
</template>

<style scoped>
.route-card {
  width: 860px;
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

