<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { notesApi, interactionsApi } from '../api'
import type { NoteSummary } from '../api'

const notes = ref<NoteSummary[]>([])
const loading = ref(false)
const router = useRouter()

const fetchNotes = async () => {
  loading.value = true
  try {
    notes.value = await notesApi.list()
  } finally {
    loading.value = false
  }
}

const goDetail = (id: number) => {
  router.push(`/notes/${id}`)
}

// 简单的排序（按点赞数或时间排序）
const sortedNotes = computed(() => {
  return [...notes.value].sort((a, b) => {
    const la = a.likeCount ?? 0
    const lb = b.likeCount ?? 0
    if (lb !== la) return lb - la
    const ta = a.createdAt ? new Date(a.createdAt).getTime() : 0
    const tb = b.createdAt ? new Date(b.createdAt).getTime() : 0
    return tb - ta
  })
})

onMounted(fetchNotes)
</script>

<template>
  <div class="page">
    <div class="header">
      <div>
        <h2>游记 &amp; 攻略</h2>
        <p class="sub">真实旅友分享的线路与故事，可按点赞热度感受大家最爱的玩法。</p>
      </div>
      <button class="btn primary" @click="$router.push('/notes/create')">写游记</button>
    </div>

    <div v-if="!loading && sortedNotes.length === 0" class="empty text-subtle">
      还没有游记，做第一个分享旅程的人吧～
    </div>

    <div class="list">
      <article
        v-for="note in sortedNotes"
        :key="note.id"
        class="card note-card"
        @click="goDetail(note.id)"
      >
        <div class="cover" v-if="note.coverImage">
          <img :src="note.coverImage" alt="cover" loading="lazy" />
        </div>
        <div class="content">
          <h3>{{ note.title }}</h3>
          <p class="meta text-subtle">
            <span v-if="note.destination">目的地：{{ note.destination }}</span>
            <span> · 作者：{{ note.authorName || '旅友' }}</span>
            <span v-if="note.createdAt"> · {{ note.createdAt }}</span>
          </p>
          <p class="stats">
            <span>♥ {{ note.likeCount ?? 0 }}</span>
            <span>💬 {{ note.commentCount ?? 0 }}</span>
          </p>
        </div>
      </article>
    </div>
  </div>
</template>

<style scoped>
.page {
  width: 960px;
  max-width: 100%;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 16px;
  gap: 12px;
}

.sub {
  margin: 4px 0 0;
  font-size: 13px;
  color: #64748b;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.note-card {
  display: flex;
  gap: 12px;
  padding: 10px 12px;
  cursor: pointer;
  align-items: stretch;
}

.note-card:hover {
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.1);
}

.cover {
  width: 150px;
  height: 96px;
  border-radius: 10px;
  overflow: hidden;
  background: #e5e7eb;
  flex-shrink: 0;
}

.cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.content h3 {
  margin: 2px 0 6px;
  font-size: 16px;
}

.meta {
  font-size: 13px;
}

.stats {
  margin: 4px 0 0;
  font-size: 12px;
  color: #9ca3af;
  display: flex;
  gap: 10px;
}

.empty {
  padding: 24px 12px;
  text-align: center;
}
</style>

