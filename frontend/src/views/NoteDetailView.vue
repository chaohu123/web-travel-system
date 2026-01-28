<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api'

interface NoteDetail {
  id: number
  title: string
  content: string
  coverImage: string | null
  relatedPlanId: number | null
  destination: string | null
  authorName: string
  createdAt: string
}

const route = useRoute()
const router = useRouter()
const note = ref<NoteDetail | null>(null)
const loading = ref(false)

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
const newScore = ref<number | null>(null)
const postingComment = ref(false)
const commentError = ref('')

const fetchDetail = async () => {
  const id = route.params.id
  if (!id) return
  loading.value = true
  try {
    const resp = await api.get(`/api/notes/${id}`)
    note.value = resp.data.data
  } finally {
    loading.value = false
  }
}

const fetchComments = async () => {
  if (!note.value) return
  loadingComments.value = true
  try {
    const resp = await api.get('/api/comments', {
      params: { targetType: 'note', targetId: note.value.id },
    })
    comments.value = resp.data.data || []
  } finally {
    loadingComments.value = false
  }
}

const submitComment = async () => {
  if (!note.value) return
  if (!newContent.value.trim()) {
    commentError.value = '请输入评论内容'
    return
  }
  postingComment.value = true
  commentError.value = ''
  try {
    await api.post('/api/comments', {
      targetType: 'note',
      targetId: note.value.id,
      content: newContent.value,
      score: newScore.value || undefined,
    })
    newContent.value = ''
    newScore.value = null
    await fetchComments()
  } catch (e: any) {
    commentError.value = e.response?.data?.message || '发表评论失败'
  } finally {
    postingComment.value = false
  }
}

const goPlan = () => {
  if (!note.value?.relatedPlanId) return
  router.push(`/routes/${note.value.relatedPlanId}`)
}

onMounted(async () => {
  await fetchDetail()
  await fetchComments()
})
</script>

<template>
  <div v-if="note" class="page">
    <article class="card main">
      <header class="header">
        <h1>{{ note.title }}</h1>
        <p class="meta text-subtle">
          <span>作者：{{ note.authorName }}</span>
          <span v-if="note.destination"> · 目的地：{{ note.destination }}</span>
        </p>
      </header>
      <div v-if="note.coverImage" class="cover">
        <img :src="note.coverImage" alt="cover" />
      </div>
      <section class="content">
        <p v-for="(line, idx) in note.content.split(/\n+/)" :key="idx">
          {{ line }}
        </p>
      </section>
      <section class="comments">
        <h3>评论</h3>
        <div v-if="!loadingComments && comments.length === 0" class="text-subtle small">
          还没有评论，快来写下你的看法吧～
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
            placeholder="写下你的感受或补充建议..."
          ></textarea>
          <div class="editor-footer">
            <select v-model.number="newScore" class="form-select score-select">
              <option :value="null">不评分</option>
              <option :value="5">5 分</option>
              <option :value="4">4 分</option>
              <option :value="3">3 分</option>
              <option :value="2">2 分</option>
              <option :value="1">1 分</option>
            </select>
            <div class="actions">
              <button class="btn primary" type="button" @click="submitComment" :disabled="postingComment">
                {{ postingComment ? '发布中...' : '发表评论' }}
              </button>
            </div>
          </div>
          <p v-if="commentError" class="error">{{ commentError }}</p>
        </div>
      </section>
    </article>
    <aside class="side">
      <div class="card side-card" v-if="note.relatedPlanId">
        <h3>关联行程</h3>
        <p class="text-subtle">这篇游记来自某个已规划/完成的行程。</p>
        <button class="btn ghost full" @click="goPlan">查看行程详情</button>
      </div>
    </aside>
  </div>
  <div v-else class="text-subtle">
    {{ loading ? '加载中...' : '未找到游记内容' }}
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

.header h1 {
  margin: 0 0 6px;
  font-size: 22px;
}

.cover {
  margin: 12px 0 14px;
  border-radius: var(--radius-lg);
  overflow: hidden;
  background: #e5e7eb;
  max-height: 260px;
}

.cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.content p {
  margin: 0 0 10px;
  line-height: 1.6;
}

.side-card {
  padding: 14px 14px 16px;
}

.comments {
  margin-top: 20px;
  border-top: 1px solid var(--color-border);
  padding-top: 12px;
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

