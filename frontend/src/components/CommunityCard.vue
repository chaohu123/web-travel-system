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
    /** 游记 ID，有则整卡点击跳转到游记详情 */
    noteId?: number
  }>(),
  { noteId: undefined }
)

const emit = defineEmits<{ click: [payload: { title: string; likes: number; comments: number }] }>()
const router = useRouter()

function handleClick() {
  emit('click', { title: props.title, likes: props.likes, comments: props.comments })
  if (props.noteId != null) {
    router.push(`/notes/${props.noteId}`)
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
