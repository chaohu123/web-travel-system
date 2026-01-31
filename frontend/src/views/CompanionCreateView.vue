<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElCard, ElInput, ElButton, ElDatePicker, ElSelect, ElOption, ElInputNumber, ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { companionApi, routesApi } from '../api'

interface MyPlanOption {
  id: number
  destination: string
  startDate: string
  endDate: string
}

const route = useRoute()
const router = useRouter()

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

/** 格式化为 YYYY-MM-DD */
function formatDate(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

/** 根据起止日期计算路线天数（含首尾） */
function getRouteDays(startStr: string, endStr: string): number {
  const start = new Date(startStr)
  const end = new Date(endStr)
  const diff = Math.round((end.getTime() - start.getTime()) / (24 * 60 * 60 * 1000))
  return Math.max(1, diff + 1)
}

/** 将起止日期设为：起始=当前日期，结束=当前日期+路线天数 */
function applyCurrentDateRange(routeDays: number) {
  const today = new Date()
  startDate.value = formatDate(today)
  const end = new Date(today)
  end.setDate(end.getDate() + routeDays)
  endDate.value = formatDate(end)
}

const fetchMyPlans = async () => {
  try {
    const list = await routesApi.myPlans()
    myPlans.value = list || []
  } catch {
    myPlans.value = []
  }
}

const selectedPlan = computed(() =>
  myPlans.value.find((p) => p.id === relatedPlanId.value)
)

const onSubmit = async () => {
  if (!destination.value || !startDate.value || !endDate.value) {
    errorMsg.value = '请填写目的地和时间范围'
    ElMessage.warning('请填写目的地和时间范围')
    return
  }
  loading.value = true
  errorMsg.value = ''
  try {
    const postId = await companionApi.publish({
      relatedPlanId: relatedPlanId.value ?? undefined,
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
    const teamId = await companionApi.createTeam(postId)
    ElMessage.success('发布成功，已自动加入小队')
    router.push(`/teams/${teamId}`)
  } catch (e: any) {
    errorMsg.value = e.response?.data?.message || '发布失败'
    ElMessage.error(errorMsg.value)
  } finally {
    loading.value = false
  }
}

const goBack = () => router.push('/companion')

onMounted(async () => {
  const q = route.query
  const qPlanId = typeof q.planId === 'string' ? Number(q.planId) : null
  if (qPlanId) relatedPlanId.value = qPlanId
  if (typeof q.destination === 'string') destination.value = q.destination

  await fetchMyPlans()

  // 有关联行程时自动填充目的地与起止日期
  if (relatedPlanId.value && myPlans.value.length) {
    const plan = myPlans.value.find((p) => p.id === relatedPlanId.value)
    if (plan) {
      if (plan.destination && !destination.value) destination.value = plan.destination
      if (plan.startDate && plan.endDate) {
        const routeDays = getRouteDays(plan.startDate, plan.endDate)
        applyCurrentDateRange(routeDays)
      }
    }
  }

  // 仅当 URL 带了 startDate/endDate 时用其计算日期
  const qStart = typeof q.startDate === 'string' ? q.startDate : ''
  const qEnd = typeof q.endDate === 'string' ? q.endDate : ''
  if (qStart && qEnd && !relatedPlanId.value) {
    const routeDays = getRouteDays(qStart, qEnd)
    applyCurrentDateRange(routeDays)
  }
})

// 切换关联行程时，自动填充目的地与起止日期
watch(relatedPlanId, (planId) => {
  if (!planId) return
  const plan = myPlans.value.find((p) => p.id === planId)
  if (!plan) return
  if (plan.destination) destination.value = plan.destination
  if (plan.startDate && plan.endDate) {
    const routeDays = getRouteDays(plan.startDate, plan.endDate)
    applyCurrentDateRange(routeDays)
  }
})
</script>

<template>
  <div class="companion-create-page">
    <div class="back-wrap">
      <el-button :icon="ArrowLeft" circle @click="goBack" />
    </div>
    <div class="create-container">
      <el-card class="create-card" shadow="never">
        <template #header>
          <div class="card-header">
            <h2 class="card-title">发布结伴信息</h2>
            <p class="card-subtitle">关联你的行程，寻找志同道合的旅友一起出发。</p>
          </div>
        </template>

        <form @submit.prevent="onSubmit" class="companion-form">
          <div class="form-row">
            <div class="form-item">
              <label class="form-label">关联行程（可选）</label>
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
            <div class="form-item">
              <label class="form-label">目的地 <span class="required">*</span></label>
              <el-input
                v-model="destination"
                placeholder="城市、国家或路线名称"
                maxlength="50"
                show-word-limit
              />
            </div>
          </div>

          <div class="form-row">
            <div class="form-item">
              <label class="form-label">出发日期 <span class="required">*</span></label>
              <el-date-picker
                v-model="startDate"
                type="date"
                placeholder="选择出发日期"
                format="YYYY-MM-DD"
                value-format="YYYY-MM-DD"
                class="form-date"
              />
            </div>
            <div class="form-item">
              <label class="form-label">结束日期 <span class="required">*</span></label>
              <el-date-picker
                v-model="endDate"
                type="date"
                placeholder="选择结束日期"
                format="YYYY-MM-DD"
                value-format="YYYY-MM-DD"
                class="form-date"
              />
            </div>
          </div>

          <div class="form-row">
            <div class="form-item">
              <label class="form-label">最少人数 <span class="required">*</span></label>
              <el-input-number v-model="minPeople" :min="1" :max="20" class="form-number" />
            </div>
            <div class="form-item">
              <label class="form-label">最多人数 <span class="required">*</span></label>
              <el-input-number v-model="maxPeople" :min="1" :max="20" class="form-number" />
            </div>
          </div>

          <div class="form-row">
            <div class="form-item">
              <label class="form-label">预算下限（元，可选）</label>
              <el-input-number
                v-model="budgetMin"
                :min="0"
                :precision="0"
                placeholder="最低预算"
                class="form-number"
              />
            </div>
            <div class="form-item">
              <label class="form-label">预算上限（元，可选）</label>
              <el-input-number
                v-model="budgetMax"
                :min="0"
                :precision="0"
                placeholder="最高预算"
                class="form-number"
              />
            </div>
          </div>

          <div class="form-item full">
            <label class="form-label">对旅友的期待（可选）</label>
            <el-input
              v-model="expectedMateDesc"
              type="textarea"
              :rows="3"
              placeholder="比如：作息规律、喜欢拍照、会开车、性格安静/活泼等"
              maxlength="500"
              show-word-limit
            />
          </div>

          <div class="form-item">
            <label class="form-label">可见性</label>
            <el-select v-model="visibility" class="form-select">
              <el-option value="public" label="公开" />
              <el-option value="friends" label="仅好友（预留）" />
              <el-option value="private" label="私密" />
            </el-select>
          </div>

          <p v-if="errorMsg" class="form-error">{{ errorMsg }}</p>

          <div class="form-actions">
            <el-button @click="goBack">取消</el-button>
            <el-button type="primary" :loading="loading" :disabled="!destination || !startDate || !endDate" native-type="submit">
              {{ loading ? '发布中...' : '发布结伴信息' }}
            </el-button>
          </div>
        </form>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.companion-create-page {
  min-height: 100vh;
  background: linear-gradient(180deg, #f0f9ff 0%, #f8fafc 20%);
  padding: 24px 20px 48px;
}

.back-wrap {
  position: fixed;
  top: 80px;
  left: 20px;
  z-index: 200;
}

.back-wrap :deep(.el-button) {
  width: 44px;
  height: 44px;
  background: #fff;
  border: 1px solid #e2e8f0;
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.08);
  transition: all 0.2s ease;
}

.back-wrap :deep(.el-button:hover) {
  background: #0d9488;
  border-color: #0d9488;
  color: #fff;
  transform: translateX(-4px);
  box-shadow: 0 6px 20px rgba(13, 148, 136, 0.3);
}

.create-container {
  max-width: 720px;
  margin: 0 auto;
}

.create-card {
  border-radius: 16px;
  border: none;
  box-shadow: 0 2px 16px rgba(15, 23, 42, 0.08);
}

.create-card :deep(.el-card__header) {
  padding: 24px 28px 16px;
  border-bottom: 1px solid #f0f2f5;
}

.create-card :deep(.el-card__body) {
  padding: 28px;
}

.card-header {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.card-title {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  color: #1e293b;
}

.card-subtitle {
  margin: 0;
  font-size: 14px;
  color: #64748b;
}

.companion-form {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-item.full {
  grid-column: 1 / -1;
}

.form-label {
  font-size: 14px;
  font-weight: 500;
  color: #334155;
}

.required {
  color: #dc2626;
}

.form-select,
.form-date,
.form-number {
  width: 100%;
}

.form-error {
  margin: 0;
  padding: 12px 16px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 10px;
  font-size: 14px;
  color: #dc2626;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 12px;
  border-top: 1px solid #f0f2f5;
}

.form-actions .el-button {
  border-radius: 10px;
  padding: 10px 24px;
}

.form-actions .el-button--primary {
  background: linear-gradient(135deg, #0d9488, #0f766e);
  border: none;
  box-shadow: 0 2px 10px rgba(13, 148, 136, 0.3);
}

.form-actions .el-button--primary:hover {
  box-shadow: 0 4px 14px rgba(13, 148, 136, 0.4);
}

@media (max-width: 768px) {
  .form-row {
    grid-template-columns: 1fr;
  }

  .back-wrap {
    top: 70px;
    left: 12px;
  }

  .back-wrap :deep(.el-button) {
    width: 40px;
    height: 40px;
  }

  .create-card :deep(.el-card__header),
  .create-card :deep(.el-card__body) {
    padding: 18px;
  }
}
</style>
