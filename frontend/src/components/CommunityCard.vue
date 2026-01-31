<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { CircleCheckFilled, ChatDotRound } from '@element-plus/icons-vue'
import HeartIcon from './HeartIcon.vue'
import { useAuthStore } from '../store'
import { interactionsApi } from '../api'

const props = withDefaults(
  defineProps<{
    cover?: string
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
    /** 当前用户是否已点赞（可选，如果不传则会在组件内部获取） */
    liked?: boolean
  }>(),
  {
    cover: '',
    type: 'note',
    targetId: undefined,
    noteId: undefined,
    liked: undefined,
  },
)

const emit = defineEmits<{ 
  click: [payload: { title: string; likes: number; comments: number }]
  likeChange: [payload: { liked: boolean; likes: number }]
}>()
const router = useRouter()
const auth = useAuthStore()

const liked = ref(props.liked ?? false)
const likeCount = ref(props.likes)

// 监听 props.liked 变化
watch(() => props.liked, (newVal) => {
  if (newVal !== undefined) {
    liked.value = newVal
  }
})

// 监听 props.likes 变化
watch(() => props.likes, (newVal) => {
  likeCount.value = newVal
})

// 如果没有传入 liked prop，尝试从 API 获取
if (props.liked === undefined && auth.token && props.targetId) {
  interactionsApi.summary(props.type, props.targetId).then((summary) => {
    liked.value = summary.likedByCurrentUser ?? false
    likeCount.value = summary.likeCount ?? props.likes
  }).catch(() => {
    // ignore
  })
}

function handleClick() {
  emit('click', { title: props.title, likes: likeCount.value, comments: props.comments })

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
      // 跳转到动态页并定位到该帖
      router.push({ name: 'feed', query: { scrollTo: `feed-${finalId}` } })
      break
    case 'note':
    default:
      router.push({ name: 'note-detail', params: { id: finalId } })
      break
  }
}

function ensureLogin(cb: () => void) {
  if (!auth.token) {
    ElMessage.warning('请先登录后再操作')
    router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
    return
  }
  cb()
}

async function toggleLike(event: Event) {
  event.stopPropagation() // 阻止事件冒泡，避免触发卡片点击
  
  ensureLogin(async () => {
    const finalType = props.type
    const finalId = props.targetId ?? props.noteId
    
    if (!finalId) return
    
    try {
      if (liked.value) {
        await interactionsApi.unlike(finalType, finalId)
        likeCount.value = Math.max(0, likeCount.value - 1)
      } else {
        await interactionsApi.like(finalType, finalId)
        likeCount.value += 1
      }
      liked.value = !liked.value
      emit('likeChange', { liked: liked.value, likes: likeCount.value })
    } catch (e: any) {
      ElMessage.error(e.message || '操作失败')
    }
  })
}
</script>

<template>
  <article
    class="bg-white rounded-2xl overflow-hidden shadow-lg shadow-slate-200/50 border border-slate-100 hover:shadow-xl hover:border-teal-100 transition-all cursor-pointer"
    @click="handleClick"
  >
    <div class="aspect-[16/10] bg-slate-200 overflow-hidden">
      <img :src="cover || 'https://picsum.photos/seed/card/400/250'" :alt="title" class="w-full h-full object-cover" />
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
          <span 
            class="inline-flex items-center gap-1 cursor-pointer hover:text-teal-600 transition-colors"
            @click.stop="toggleLike"
          >
            <HeartIcon :filled="liked" class="align-middle" />
            {{ likeCount }}
          </span>
          <span class="inline-flex items-center gap-1">
            <el-icon class="align-middle"><ChatDotRound /></el-icon>
            {{ comments }}
          </span>
        </div>
      </div>
    </div>
  </article>
</template>
