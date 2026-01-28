<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'

interface NoteSummary {
  id: number
  title: string
  destination: string
  coverImage: string | null
  authorName: string
  createdAt: string
}

const notes = ref<NoteSummary[]>([])
const loading = ref(false)
const router = useRouter()

const fetchNotes = async () => {
  loading.value = true
  try {
    const resp = await api.get('/api/notes')
    notes.value = resp.data.data || []
  } finally {
    loading.value = false
  }
}

const goDetail = (id: number) => {
  router.push(`/notes/${id}`)
}

onMounted(fetchNotes)
</script>

<template>
  <div class="page">
    <div class="header">
      <h2>游记 &amp; 攻略</h2>
      <button class="btn primary" @click="$router.push('/notes/create')">写游记</button>
    </div>

    <div v-if="!loading && notes.length === 0" class="empty text-subtle">
      还没有游记，做第一个分享旅程的人吧～
    </div>

    <div class="list">
      <article v-for="note in notes" :key="note.id" class="card note-card" @click="goDetail(note.id)">
        <div class="cover" v-if="note.coverImage">
          <img :src="note.coverImage" alt="cover" />
        </div>
        <div class="content">
          <h3>{{ note.title }}</h3>
          <p class="meta text-subtle">
            <span v-if="note.destination">目的地：{{ note.destination }}</span>
            <span> · 作者：{{ note.authorName }}</span>
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
  align-items: center;
  margin-bottom: 16px;
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
}

.note-card:hover {
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.1);
}

.cover {
  width: 120px;
  height: 80px;
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

.empty {
  padding: 24px 12px;
  text-align: center;
}
</style>

