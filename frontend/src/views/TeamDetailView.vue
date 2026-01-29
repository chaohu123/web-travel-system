<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../store'
import { api, commentsApi } from '../api'

const auth = useAuthStore()

interface TeamMemberItem {
  userId: number
  userName: string
  role: string
  state: string
}

interface TeamDetail {
  id: number
  name: string
  status: string
  postId: number | null
  destination: string | null
  startDate: string | null
  endDate: string | null
  relatedPlanId: number | null
  members: TeamMemberItem[]
}

const route = useRoute()
const router = useRouter()
const team = ref<TeamDetail | null>(null)
const loading = ref(false)
const errorMsg = ref('')

interface CommentItem {
  id: number
  userName: string
  content: string
  score: number | null
  createdAt: string
}

const comments = ref<CommentItem[]>([])
const loadingComments = ref(false)
const newContent = ref('')
const newScore = ref<number | null>(5)
const postingComment = ref(false)
const commentError = ref('')

const fetchTeam = async () => {
  const id = route.params.id
  if (!id) return
  loading.value = true
  try {
    const resp = await api.get(`/companion/teams/${id}`)
    team.value = resp.data.data
  } catch (e: any) {
    errorMsg.value = e.response?.data?.message || '加载小队信息失败'
  } finally {
    loading.value = false
  }
}

const goPlan = () => {
  if (!team.value?.relatedPlanId) return
  router.push(`/routes/${team.value.relatedPlanId}`)
}

const fetchComments = async () => {
  if (!team.value) return
  loadingComments.value = true
  try {
    comments.value = await commentsApi.list('companion_team', team.value.id)
  } finally {
    loadingComments.value = false
  }
}

const submitComment = async () => {
  if (!team.value) return
  if (!auth.token) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    ElMessage.warning('请先登录后再评价')
    return
  }
  if (!newContent.value.trim()) {
    commentError.value = '请输入评价内容'
    return
  }
  if (!newScore.value) {
    commentError.value = '请选择评分'
    return
  }
  postingComment.value = true
  commentError.value = ''
  try {
    await commentsApi.create({
      targetType: 'companion_team',
      targetId: team.value.id,
      content: newContent.value,
      score: newScore.value,
    })
    newContent.value = ''
    newScore.value = 5
    await fetchComments()
  } catch (e: any) {
    commentError.value = e.response?.data?.message || '提交评价失败'
  } finally {
    postingComment.value = false
  }
}

onMounted(async () => {
  await fetchTeam()
  await fetchComments()
})
</script>

<template>
  <div v-if="team" class="page">
    <section class="card main">
      <header class="header">
        <h2>{{ team.name }}</h2>
        <p class="text-subtle">
          <span>状态：{{ team.status }}</span>
          <span v-if="team.destination">
            · 目的地：{{ team.destination }}
            <span v-if="team.startDate && team.endDate">
              （{{ team.startDate }} ~ {{ team.endDate }}）
            </span>
          </span>
        </p>
      </header>
      <section class="members">
        <h3>成员</h3>
        <ul class="member-list">
          <li v-for="m in team.members" :key="m.userId" class="member-item">
            <div class="avatar">
              {{ (m.userName || 'U').charAt(0).toUpperCase() }}
            </div>
            <div class="info">
              <div class="top-line">
                <span class="name">{{ m.userName || '旅友' }}</span>
                <span v-if="m.role === 'leader'" class="tag leader">队长</span>
                <span v-else class="tag">成员</span>
              </div>
              <div class="state text-subtle">状态：{{ m.state }}</div>
            </div>
          </li>
        </ul>
      </section>
      <section class="comments">
        <h3>队伍评价</h3>
        <div v-if="!loadingComments && comments.length === 0" class="text-subtle small">
          还没有评价，队员完成行程后可以在这里互相打分。
        </div>
        <ul v-else class="comment-list">
          <li v-for="c in comments" :key="c.id" class="comment-item">
            <div class="meta-line">
              <span class="name">{{ c.userName || '旅友' }}</span>
              <span class="time text-subtle">{{ c.createdAt }}</span>
              <span v-if="c.score" class="score text-subtle"> · 评分 {{ c.score }} / 5</span>
            </div>
            <p class="body">{{ c.content }}</p>
          </li>
        </ul>
        <div class="comment-editor">
          <textarea
            v-model="newContent"
            class="form-input"
            rows="3"
            placeholder="对本次队伍的整体体验进行评价（仅示例，不会公开展示联系方式）"
          ></textarea>
          <div class="editor-footer">
            <select v-model.number="newScore" class="form-select score-select">
              <option :value="5">5 分</option>
              <option :value="4">4 分</option>
              <option :value="3">3 分</option>
              <option :value="2">2 分</option>
              <option :value="1">1 分</option>
            </select>
            <div class="actions">
              <button class="btn primary" type="button" @click="submitComment" :disabled="postingComment">
                {{ postingComment ? '提交中...' : '提交评价' }}
              </button>
            </div>
          </div>
          <p v-if="commentError" class="error">{{ commentError }}</p>
        </div>
      </section>
    </section>
    <aside class="side">
      <div class="card side-card" v-if="team.relatedPlanId">
        <h3>关联行程</h3>
        <p class="text-subtle">查看本小队共同的行程计划。</p>
        <button class="btn ghost full" @click="goPlan">查看行程详情</button>
      </div>
    </aside>
  </div>
  <div v-else class="text-subtle">
    {{ loading ? '加载中...' : errorMsg || '未找到小队信息' }}
  </div>
</template>

<style scoped>
.page {
  width: 960px;
  max-width: 100%;
  display: grid;
  grid-template-columns: minmax(0, 3fr) minmax(0, 1fr);
  gap: 16px;
}

.main {
  padding: 18px 18px 20px;
}

.members {
  margin-top: 16px;
  border-top: 1px solid var(--color-border);
  padding-top: 10px;
}

.members h3 {
  margin: 0 0 8px;
  font-size: 16px;
}

.member-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.member-item {
  display: flex;
  gap: 10px;
  align-items: center;
}

.avatar {
  width: 32px;
  height: 32px;
  border-radius: 999px;
  background: var(--color-primary-soft);
  color: var(--color-primary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
}

.info .top-line {
  display: flex;
  align-items: center;
  gap: 6px;
}

.info .name {
  font-size: 14px;
  font-weight: 500;
}

.tag {
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--color-primary-soft);
  color: var(--color-primary);
  font-size: 11px;
}

.tag.leader {
  background: #f973161a;
  color: #ea580c;
}

.side-card {
  padding: 14px 14px 16px;
}

.comments {
  margin-top: 16px;
  border-top: 1px solid var(--color-border);
  padding-top: 10px;
}

.comments h3 {
  margin: 0 0 8px;
  font-size: 16px;
}

.small {
  font-size: 13px;
}

.comment-list {
  list-style: none;
  padding: 0;
  margin: 0 0 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.comment-item .meta-line {
  display: flex;
  gap: 8px;
  align-items: baseline;
  font-size: 13px;
}

.comment-item .name {
  font-weight: 500;
}

.comment-item .body {
  margin: 2px 0 0;
  font-size: 14px;
}

.comment-editor {
  margin-top: 8px;
}

.editor-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
}

.score-select {
  max-width: 120px;
}

.error {
  margin-top: 4px;
  color: #dc2626;
  font-size: 13px;
}

@media (max-width: 768px) {
  .page {
    grid-template-columns: 1fr;
  }
}
</style>

