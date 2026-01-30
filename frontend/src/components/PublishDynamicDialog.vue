<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  ElDialog,
  ElTabs,
  ElTabPane,
  ElInput,
  ElButton,
  ElMessage,
  ElSelect,
  ElOption,
  ElInputNumber,
  ElDatePicker,
} from 'element-plus'
import { api, routesApi, notesApi, feedsApi } from '../api'
import { useAuthStore } from '../store'
import type { FeedItem } from '../api'

export type PublishType = 'feed' | 'note' | 'route' | 'companion'

interface MyPlanOption {
  id: number
  destination: string
  startDate: string
  endDate: string
}

const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'published', type: PublishType): void
}>()

const router = useRouter()
const auth = useAuthStore()

// 当前选择的发布类型
const activeType = ref<PublishType>('feed')

// 打卡动态表单
const feedContent = ref('')
const feedImageUrls = ref('')

// 游记表单
const noteTitle = ref('')
const noteContent = ref('')
const noteDestination = ref('')
const noteCoverImage = ref('')
const noteRelatedPlanId = ref<number | null>(null)

// 路线表单
const routeDestination = ref('')
const routeStartDate = ref('')
const routeEndDate = ref('')
const routeBudget = ref<number | null>(null)
const routePeopleCount = ref(2)
const routePace = ref('normal')

// 结伴表单
const companionDestination = ref('')
const companionStartDate = ref('')
const companionEndDate = ref('')
const companionMinPeople = ref(1)
const companionMaxPeople = ref(4)
const companionBudgetMin = ref<number | null>(null)
const companionBudgetMax = ref<number | null>(null)
const companionExpectedMateDesc = ref('')
const companionRelatedPlanId = ref<number | null>(null)

const submitting = ref(false)
const myPlans = ref<MyPlanOption[]>([])

const paceOptions = [
  { value: 'relaxed', label: '轻松' },
  { value: 'normal', label: '正常' },
  { value: 'intense', label: '紧凑' },
]

// 获取我的路线列表
async function fetchMyPlans() {
  try {
    const list = await routesApi.myPlans()
    myPlans.value = list || []
  } catch {
    myPlans.value = []
  }
}

// 验证表单
const canSubmit = computed(() => {
  switch (activeType.value) {
    case 'feed':
      return feedContent.value.trim().length > 0 && feedContent.value.trim().length <= 2000
    case 'note':
      return noteTitle.value.trim().length > 0 && noteContent.value.trim().length > 0
    case 'route':
      return (
        routeDestination.value.trim().length > 0 &&
        routeStartDate.value &&
        routeEndDate.value &&
        routePeopleCount.value > 0
      )
    case 'companion':
      return (
        companionDestination.value.trim().length > 0 &&
        companionStartDate.value &&
        companionEndDate.value &&
        companionMinPeople.value > 0 &&
        companionMaxPeople.value >= companionMinPeople.value
      )
    default:
      return false
  }
})

// 重置表单
function resetForm() {
  feedContent.value = ''
  feedImageUrls.value = ''
  noteTitle.value = ''
  noteContent.value = ''
  noteDestination.value = ''
  noteCoverImage.value = ''
  noteRelatedPlanId.value = null
  routeDestination.value = ''
  routeStartDate.value = ''
  routeEndDate.value = ''
  routeBudget.value = null
  routePeopleCount.value = 2
  routePace.value = 'normal'
  companionDestination.value = ''
  companionStartDate.value = ''
  companionEndDate.value = ''
  companionMinPeople.value = 1
  companionMaxPeople.value = 4
  companionBudgetMin.value = null
  companionBudgetMax.value = null
  companionExpectedMateDesc.value = ''
  companionRelatedPlanId.value = null
}

// 提交发布
async function submit() {
  if (!canSubmit.value) {
    ElMessage.warning('请填写完整信息')
    return
  }

  submitting.value = true
  try {
    switch (activeType.value) {
      case 'feed': {
        const id = await feedsApi.create({
          content: feedContent.value.trim(),
          imageUrlsJson: feedImageUrls.value.trim() || undefined,
        })
        ElMessage.success('发布成功')
        emit('published', 'feed')
        break
      }
      case 'note': {
        const resp = await api.post('/notes', {
          title: noteTitle.value.trim(),
          content: noteContent.value.trim(),
          coverImage: noteCoverImage.value.trim() || undefined,
          relatedPlanId: noteRelatedPlanId.value || undefined,
          destination: noteDestination.value.trim() || undefined,
        })
        const id = resp.data.data
        ElMessage.success('发布成功')
        emit('published', 'note')
        // 跳转到游记详情页
        router.push(`/notes/${id}`)
        break
      }
      case 'route': {
        const id = await routesApi.create({
          destination: routeDestination.value.trim(),
          startDate: routeStartDate.value,
          endDate: routeEndDate.value,
          budget: routeBudget.value || undefined,
          peopleCount: routePeopleCount.value,
          pace: routePace.value,
        })
        ElMessage.success('发布成功')
        emit('published', 'route')
        // 跳转到路线详情页
        router.push(`/routes/${id}`)
        break
      }
      case 'companion': {
        await api.post('/companion/posts', {
          relatedPlanId: companionRelatedPlanId.value || undefined,
          destination: companionDestination.value.trim(),
          startDate: companionStartDate.value,
          endDate: companionEndDate.value,
          minPeople: companionMinPeople.value,
          maxPeople: companionMaxPeople.value,
          budgetMin: companionBudgetMin.value ?? undefined,
          budgetMax: companionBudgetMax.value ?? undefined,
          expectedMateDesc: companionExpectedMateDesc.value.trim() || undefined,
          visibility: 'public',
        })
        ElMessage.success('发布成功')
        emit('published', 'companion')
        // 跳转到结伴列表页
        router.push('/companion')
        break
      }
    }
    emit('update:visible', false)
    resetForm()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || '发布失败')
  } finally {
    submitting.value = false
  }
}

function close() {
  emit('update:visible', false)
  resetForm()
}

watch(
  () => props.visible,
  (v) => {
    if (v) {
      fetchMyPlans()
      resetForm()
    }
  }
)
</script>

<template>
  <el-dialog
    :model-value="visible"
    title="发布动态内容"
    width="680px"
    class="publish-dynamic-dialog"
    @update:model-value="close"
  >
    <el-tabs v-model="activeType" class="type-tabs">
      <el-tab-pane label="打卡" name="feed">
        <div class="form-section">
          <div class="form-item">
            <label class="form-label">
              内容 <span class="required">*</span>
            </label>
            <el-input
              v-model="feedContent"
              type="textarea"
              :rows="6"
              placeholder="分享你的旅途见闻、攻略或经验..."
              maxlength="2000"
              show-word-limit
            />
          </div>
          <div class="form-item">
            <label class="form-label">图片 URL（可选，多个用英文逗号分隔）</label>
            <el-input
              v-model="feedImageUrls"
              type="textarea"
              :rows="2"
              placeholder='例如：https://example.com/1.jpg,https://example.com/2.jpg'
            />
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="游记" name="note">
        <div class="form-section">
          <div class="form-item">
            <label class="form-label">
              标题 <span class="required">*</span>
            </label>
            <el-input
              v-model="noteTitle"
              placeholder="例如：一场说走就走的日本关西之旅"
              maxlength="80"
              show-word-limit
            />
          </div>
          <div class="form-item">
            <label class="form-label">目的地（可选）</label>
            <el-input
              v-model="noteDestination"
              placeholder="例如：日本·关西、云南·大理"
              maxlength="50"
            />
          </div>
          <div class="form-item">
            <label class="form-label">关联行程（可选）</label>
            <el-select
              v-model="noteRelatedPlanId"
              placeholder="选择关联的行程"
              clearable
              style="width: 100%"
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
            <label class="form-label">封面图片 URL（可选）</label>
            <el-input
              v-model="noteCoverImage"
              placeholder="粘贴一张网络图片链接"
            />
            <div v-if="noteCoverImage" class="cover-preview">
              <img :src="noteCoverImage" alt="封面预览" @error="noteCoverImage = ''" />
            </div>
          </div>
          <div class="form-item">
            <label class="form-label">
              正文内容 <span class="required">*</span>
            </label>
            <el-input
              v-model="noteContent"
              type="textarea"
              :rows="8"
              placeholder="分享你的旅行故事..."
              maxlength="10000"
              show-word-limit
            />
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="路线" name="route">
        <div class="form-section">
          <div class="form-item">
            <label class="form-label">
              目的地 <span class="required">*</span>
            </label>
            <el-input
              v-model="routeDestination"
              placeholder="例如：日本·关西"
              maxlength="50"
            />
          </div>
          <div class="form-row">
            <div class="form-item">
              <label class="form-label">
                出发日期 <span class="required">*</span>
              </label>
              <el-date-picker
                v-model="routeStartDate"
                type="date"
                placeholder="选择日期"
                style="width: 100%"
                format="YYYY-MM-DD"
                value-format="YYYY-MM-DD"
              />
            </div>
            <div class="form-item">
              <label class="form-label">
                结束日期 <span class="required">*</span>
              </label>
              <el-date-picker
                v-model="routeEndDate"
                type="date"
                placeholder="选择日期"
                style="width: 100%"
                format="YYYY-MM-DD"
                value-format="YYYY-MM-DD"
              />
            </div>
          </div>
          <div class="form-row">
            <div class="form-item">
              <label class="form-label">
                人数 <span class="required">*</span>
              </label>
              <el-input-number
                v-model="routePeopleCount"
                :min="1"
                :max="20"
                style="width: 100%"
              />
            </div>
            <div class="form-item">
              <label class="form-label">
                节奏 <span class="required">*</span>
              </label>
              <el-select v-model="routePace" style="width: 100%">
                <el-option
                  v-for="opt in paceOptions"
                  :key="opt.value"
                  :value="opt.value"
                  :label="opt.label"
                />
              </el-select>
            </div>
          </div>
          <div class="form-item">
            <label class="form-label">预算（元，可选）</label>
            <el-input-number
              v-model="routeBudget"
              :min="0"
              :precision="0"
              style="width: 100%"
              placeholder="总预算"
            />
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="结伴" name="companion">
        <div class="form-section">
          <div class="form-item">
            <label class="form-label">关联行程（可选）</label>
            <el-select
              v-model="companionRelatedPlanId"
              placeholder="选择关联的行程"
              clearable
              style="width: 100%"
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
            <label class="form-label">
              目的地 <span class="required">*</span>
            </label>
            <el-input
              v-model="companionDestination"
              placeholder="城市、国家或路线名称"
              maxlength="50"
            />
          </div>
          <div class="form-row">
            <div class="form-item">
              <label class="form-label">
                出发日期 <span class="required">*</span>
              </label>
              <el-date-picker
                v-model="companionStartDate"
                type="date"
                placeholder="选择日期"
                style="width: 100%"
                format="YYYY-MM-DD"
                value-format="YYYY-MM-DD"
              />
            </div>
            <div class="form-item">
              <label class="form-label">
                结束日期 <span class="required">*</span>
              </label>
              <el-date-picker
                v-model="companionEndDate"
                type="date"
                placeholder="选择日期"
                style="width: 100%"
                format="YYYY-MM-DD"
                value-format="YYYY-MM-DD"
              />
            </div>
          </div>
          <div class="form-row">
            <div class="form-item">
              <label class="form-label">
                最少人数 <span class="required">*</span>
              </label>
              <el-input-number
                v-model="companionMinPeople"
                :min="1"
                :max="20"
                style="width: 100%"
              />
            </div>
            <div class="form-item">
              <label class="form-label">
                最多人数 <span class="required">*</span>
              </label>
              <el-input-number
                v-model="companionMaxPeople"
                :min="1"
                :max="20"
                style="width: 100%"
              />
            </div>
          </div>
          <div class="form-row">
            <div class="form-item">
              <label class="form-label">预算下限（元，可选）</label>
              <el-input-number
                v-model="companionBudgetMin"
                :min="0"
                :precision="0"
                style="width: 100%"
                placeholder="最低预算"
              />
            </div>
            <div class="form-item">
              <label class="form-label">预算上限（元，可选）</label>
              <el-input-number
                v-model="companionBudgetMax"
                :min="0"
                :precision="0"
                style="width: 100%"
                placeholder="最高预算"
              />
            </div>
          </div>
          <div class="form-item">
            <label class="form-label">对旅友的期待（可选）</label>
            <el-input
              v-model="companionExpectedMateDesc"
              type="textarea"
              :rows="3"
              placeholder="比如：作息规律、喜欢拍照、会开车、性格安静/活泼等"
              maxlength="500"
            />
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" :loading="submitting" :disabled="!canSubmit" @click="submit">
        发布
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.publish-dynamic-dialog :deep(.el-dialog__body) {
  padding: 20px 24px;
}

.type-tabs :deep(.el-tabs__header) {
  margin-bottom: 20px;
}

.type-tabs :deep(.el-tabs__item) {
  font-size: 15px;
  padding: 0 24px;
}

.type-tabs :deep(.el-tabs__active-bar) {
  background-color: #0d9488;
}

.type-tabs :deep(.el-tabs__item.is-active) {
  color: #0d9488;
}

.form-section {
  display: flex;
  flex-direction: column;
  gap: 18px;
  min-height: 300px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.form-label {
  font-size: 14px;
  font-weight: 500;
  color: #334155;
}

.required {
  color: #dc2626;
}

.cover-preview {
  margin-top: 8px;
  width: 100%;
  max-width: 300px;
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

@media (max-width: 768px) {
  .form-row {
    grid-template-columns: 1fr;
  }
}
</style>
