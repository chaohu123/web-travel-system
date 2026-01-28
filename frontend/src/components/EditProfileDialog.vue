<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { MeDetail, UpdateProfileRequest } from '../api'
import { userApi } from '../api'

const props = defineProps<{
  visible: boolean
  profile: MeDetail | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'updated', profile: MeDetail): void
}>()

const nickname = ref('')
const avatar = ref('')
const gender = ref('')
const age = ref<number | ''>('')
const city = ref('')
const intro = ref('')
const slogan = ref('')
const preferenceTags = ref<string[]>([])
const submitting = ref(false)

const GENDER_OPTIONS = [
  { value: '', label: '未设置' },
  { value: 'male', label: '男' },
  { value: 'female', label: '女' },
  { value: 'other', label: '其他' },
]

const PREFERENCE_OPTIONS = ['自然风光', '历史文化', '美食体验', '购物娱乐', '休闲放松', '自驾', '穷游', '品质游']

watch(
  () => [props.visible, props.profile] as const,
  ([visible, profile]) => {
    if (!visible) return
    nickname.value = profile?.nickname ?? ''
    avatar.value = profile?.avatar ?? ''
    gender.value = profile?.gender ?? ''
    age.value = profile?.age ?? ''
    city.value = profile?.city ?? ''
    intro.value = profile?.intro ?? ''
    slogan.value = profile?.slogan ?? ''
    preferenceTags.value = []
  },
  { immediate: true }
)

function togglePreference(tag: string) {
  const i = preferenceTags.value.indexOf(tag)
  if (i === -1) preferenceTags.value = [...preferenceTags.value, tag]
  else preferenceTags.value = preferenceTags.value.filter((t) => t !== tag)
}

const canSubmit = () => {
  if (!nickname.value.trim()) return false
  const a = age.value
  if (a !== '' && (Number(a) < 1 || Number(a) > 120)) return false
  return true
}

async function submit() {
  if (!canSubmit()) {
    ElMessage.warning('昵称不能为空，年龄需在 1-120 之间')
    return
  }
  submitting.value = true
  try {
    const body: UpdateProfileRequest = {
      nickname: nickname.value.trim(),
      avatar: avatar.value.trim() || undefined,
      gender: gender.value || undefined,
      age: age.value === '' ? undefined : Number(age.value),
      city: city.value.trim() || undefined,
      intro: intro.value.trim() || undefined,
      slogan: slogan.value.trim() || undefined,
    }
    const updated = await userApi.updateProfile(body)
    emit('updated', updated)
    emit('update:visible', false)
    ElMessage.success('保存成功')
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
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
    title="编辑资料"
    width="560px"
    class="edit-profile-dialog"
    @update:model-value="close"
  >
    <el-form label-width="90px" class="form">
      <el-form-item label="昵称" required>
        <el-input v-model="nickname" placeholder="请输入昵称" maxlength="20" show-word-limit />
      </el-form-item>
      <el-form-item label="头像 URL">
        <el-input v-model="avatar" placeholder="图片链接（可选）" />
      </el-form-item>
      <el-form-item label="性别">
        <el-select v-model="gender" placeholder="请选择" clearable class="w-full">
          <el-option v-for="opt in GENDER_OPTIONS" :key="opt.value || 'empty'" :value="opt.value" :label="opt.label" />
        </el-select>
      </el-form-item>
      <el-form-item label="年龄">
        <el-input-number v-model="age" :min="1" :max="120" placeholder="选填" controls-position="right" />
      </el-form-item>
      <el-form-item label="城市">
        <el-input v-model="city" placeholder="常住城市" />
      </el-form-item>
      <el-form-item label="旅行偏好">
        <div class="tag-group">
          <el-tag
            v-for="t in PREFERENCE_OPTIONS"
            :key="t"
            :type="preferenceTags.includes(t) ? 'primary' : 'info'"
            effect="plain"
            class="tag-opt"
            @click="togglePreference(t)"
          >
            {{ t }}
          </el-tag>
        </div>
      </el-form-item>
      <el-form-item label="个人简介">
        <el-input v-model="intro" type="textarea" :rows="2" placeholder="介绍一下自己" maxlength="200" show-word-limit />
      </el-form-item>
      <el-form-item label="旅行宣言">
        <el-input v-model="slogan" type="textarea" :rows="2" placeholder="你的旅行态度" maxlength="100" show-word-limit />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" :loading="submitting" :disabled="!canSubmit()" @click="submit">
        保存
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.w-full {
  width: 100%;
}

.tag-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag-opt {
  cursor: pointer;
}
</style>
