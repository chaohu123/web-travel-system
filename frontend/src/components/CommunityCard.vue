<script setup lang="ts">
import { useRouter } from 'vue-router'

const props = withDefaults(
  defineProps<{
    cover: string
    title: string
    authorAvatar: string
    authorName: string
    likes: number
    comments: number
    /**
     * 内容类型：游记 / 路线 / 结伴 / 动态
     * 默认游记，便于 SEO 与详情页承接
     */
    type?: 'note' | 'route' | 'companion' | 'feed'
    /** 对应内容的主键 ID */
    targetId?: number
    /**
     * 兼容旧用法：游记 ID，有则整卡点击跳转到游记详情
     * 建议使用 type + targetId
     */
    noteId?: number
  }>(),
  {
    type: 'note',
    targetId: undefined,
    noteId: undefined,
  },
)

const emit = defineEmits<{ click: [payload: { title: string; likes: number; comments: number }] }>()
const router = useRouter()

function handleClick() {
  emit('click', { title: props.title, likes: props.likes, comments: props.comments })

  // 统一根据内容类型跳转到对应详情页
  let finalType = props.type
  let finalId = props.targetId

  // 兼容历史字段：如果没传 targetId，但传了 noteId，则按游记处理
  if (finalId == null && props.noteId != null) {
    finalType = 'note'
    finalId = props.noteId
  }

  if (finalId == null) return

  switch (finalType) {
    case 'route':
      router.push({ name: 'route-detail', params: { id: finalId } })
      break
    case 'companion':
      router.push({ name: 'companion-detail', params: { id: finalId } })
      break
    case 'feed':
      // 目前动态是列表页，没有单独详情，这里统一落到社区/动态页
      router.push({ name: 'feed' })
      break
    case 'note':
    default:
      router.push({ name: 'note-detail', params: { id: finalId } })
      break
  }
}
</script>

<template>
  <article
    class="bg-white rounded-2xl overflow-hidden shadow-lg shadow-slate-200/50 border border-slate-100 hover:shadow-xl hover:border-teal-100 transition-all cursor-pointer"
    @click="handleClick"
  >
    <div class="aspect-[16/10] bg-slate-200 overflow-hidden">
      <img :src="cover" :alt="title" class="w-full h-full object-cover" />
    </div>
    <div class="p-4">
      <h3 class="font-semibold text-slate-800 line-clamp-2 mb-3" :class="{ 'hover:text-teal-600': noteId != null }">{{ title }}</h3>
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-2">
          <span class="w-8 h-8 rounded-full bg-slate-300 flex items-center justify-center text-slate-600 text-xs font-medium overflow-hidden">
            <img v-if="authorAvatar" :src="authorAvatar" :alt="authorName" class="w-full h-full object-cover" />
            <span v-else>{{ authorName.charAt(0) }}</span>
          </span>
          <span class="text-sm text-slate-600">{{ authorName }}</span>
        </div>
        <div class="flex items-center gap-3 text-sm text-slate-500">
          <span>♥ {{ likes }}</span>
          <span>💬 {{ comments }}</span>
        </div>
      </div>
    </div>
  </article>
</template>
