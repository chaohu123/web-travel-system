<script setup lang="ts">
import { useRouter } from 'vue-router'

const props = withDefaults(
  defineProps<{
    avatar: string
    nickname: string
    creditLevel: string
    destination: string
    time: string
    styles: string[]
    /** 用户 ID，用于跳转个人主页 / 私信 */
    userId?: number
    /** 结伴帖 ID，有则「查看」跳转结伴广场并可选高亮 */
    postId?: number
    /** 队伍 ID，有则「查看」跳转队伍详情页 */
    teamId?: number
  }>(),
  { userId: undefined, postId: undefined, teamId: undefined }
)

const router = useRouter()

const handleAvatarClick = () => {
  if (props.userId != null) {
    router.push({
      name: 'user-profile',
      params: { id: String(props.userId) },
    })
  }
}
</script>

<template>
  <article class="bg-white rounded-2xl p-5 shadow-lg shadow-slate-200/50 border border-slate-100 hover:shadow-xl hover:border-indigo-100 transition-all">
    <div class="flex items-start gap-4">
      <div
        class="flex-shrink-0 w-14 h-14 rounded-full bg-gradient-to-br from-indigo-400 to-teal-400 flex items-center justify-center text-white text-lg font-bold overflow-hidden transition-all cursor-pointer hover:scale-110 hover:shadow-lg"
        :class="{ 'hover:ring-2 hover:ring-indigo-300': userId != null }"
        @click.stop="handleAvatarClick"
      >
        <img v-if="avatar" :src="avatar" :alt="nickname" class="w-full h-full object-cover" />
        <span v-else>{{ nickname.charAt(0) }}</span>
      </div>
      <div class="flex-1 min-w-0">
        <div class="flex items-center gap-2 flex-wrap">
          <span class="font-semibold text-slate-800">{{ nickname }}</span>
          <span class="text-xs px-2 py-0.5 rounded-full bg-amber-100 text-amber-700">{{ creditLevel }}</span>
        </div>
        <p class="text-sm text-slate-500 mt-1">{{ destination }} · {{ time }}</p>
        <div class="flex flex-wrap gap-1.5 mt-2">
          <span
            v-for="s in styles"
            :key="s"
            class="text-xs px-2 py-0.5 rounded-lg bg-indigo-50 text-indigo-600"
          >
            {{ s }}
          </span>
        </div>
        <div class="flex gap-2 mt-4">
          <!-- 结伴详情按钮 -->
          <router-link
            v-if="postId != null"
            :to="{
              name: 'companion-detail',
              params: { id: postId },
            }"
            class="flex-1 py-2 rounded-xl text-sm font-medium text-indigo-600 bg-indigo-50 hover:bg-indigo-100 transition-colors text-center"
          >
            查看详情
          </router-link>
          <!-- 个人主页按钮 -->
          <router-link
            v-if="userId != null"
            :to="{
              name: 'user-profile',
              params: { id: String(userId) },
              query: { nickname, creditLevel, tags: styles.join(',') },
            }"
            :class="[
              'flex-1 py-2 rounded-xl text-sm font-medium transition-colors text-center',
              postId != null 
                ? 'text-slate-600 bg-slate-100 hover:bg-slate-200' 
                : 'text-indigo-600 bg-indigo-50 hover:bg-indigo-100'
            ]"
          >
            个人主页
          </router-link>
          <!-- 队伍详情按钮 -->
          <router-link
            v-else-if="teamId != null"
            :to="`/teams/${teamId}`"
            class="flex-1 py-2 rounded-xl text-sm font-medium text-indigo-600 bg-indigo-50 hover:bg-indigo-100 transition-colors text-center"
          >
            查看
          </router-link>
          <!-- 私信按钮 -->
          <router-link
            v-if="userId != null"
            :to="{
              name: 'chat',
              params: { id: String(userId) },
              query: { nickname },
            }"
            class="flex-1 py-2 rounded-xl text-sm font-medium text-slate-600 bg-slate-100 hover:bg-slate-200 transition-colors text-center"
          >
            私信
          </router-link>
          <button
            v-else
            type="button"
            disabled
            class="flex-1 py-2 rounded-xl text-sm font-medium text-slate-400 bg-slate-100 cursor-not-allowed text-center"
          >
            私信
          </button>
        </div>
      </div>
    </div>
  </article>
</template>
