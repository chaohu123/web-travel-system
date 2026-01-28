<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../api'

interface FeedItem {
  id: number
  content: string
  imageUrlsJson: string | null
  authorName: string
  createdAt: string
}

const content = ref('')
const imageUrlsJson = ref('')
const posting = ref(false)
const errorMsg = ref('')

const loadingList = ref(false)
const feeds = ref<FeedItem[]>([])

const fetchFeeds = async () => {
  loadingList.value = true
  try {
    const resp = await api.get('/api/feeds')
    feeds.value = resp.data.data || []
  } finally {
    loadingList.value = false
  }
}

const submit = async () => {
  if (!content.value.trim()) {
    errorMsg.value = '请输入动态内容'
    return
  }
  posting.value = true
  errorMsg.value = ''
  try {
    await api.post('/api/feeds', {
      content: content.value,
      imageUrlsJson: imageUrlsJson.value || undefined,
    })
    content.value = ''
    imageUrlsJson.value = ''
    await fetchFeeds()
  } catch (e: any) {
    errorMsg.value = e.response?.data?.message || '发布失败'
  } finally {
    posting.value = false
  }
}

const parseImages = (item: FeedItem): string[] => {
  if (!item.imageUrlsJson) return []
  try {
    const arr = JSON.parse(item.imageUrlsJson)
    return Array.isArray(arr) ? arr : []
  } catch {
    return []
  }
}

onMounted(fetchFeeds)
</script>

<template>
  <div class="page">
    <div class="card editor">
      <h2>发布动态</h2>
      <textarea
        v-model="content"
        class="form-input"
        rows="3"
        placeholder="说点什么，分享此刻旅途的心情..."
      ></textarea>
      <input
        v-model="imageUrlsJson"
        class="form-input"
        placeholder='可选：图片 URL 数组 JSON，如 ["https://...","https://..."]'
      />
      <p v-if="errorMsg" class="error">{{ errorMsg }}</p>
      <div class="actions">
        <button class="btn primary" type="button" :disabled="posting" @click="submit">
          {{ posting ? '发布中...' : '发布' }}
        </button>
      </div>
    </div>

    <div class="list">
      <div v-if="!loadingList && feeds.length === 0" class="empty text-subtle">
        还没有任何动态，来发第一条吧～
      </div>
      <article v-for="item in feeds" :key="item.id" class="card feed-item">
        <div class="header">
          <div class="avatar">
            {{ (item.authorName || 'U').charAt(0).toUpperCase() }}
          </div>
          <div class="info">
            <div class="name">{{ item.authorName || '旅友' }}</div>
            <div class="time text-subtle">{{ item.createdAt }}</div>
          </div>
        </div>
        <p class="content">
          {{ item.content }}
        </p>
        <div v-if="parseImages(item).length" class="images">
          <img v-for="(url, i) in parseImages(item)" :key="i" :src="url" alt="feed image" />
        </div>
      </article>
    </div>
  </div>
</template>

<style scoped>
.page {
  width: 960px;
  max-width: 100%;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.editor {
  padding: 16px 18px 14px;
}

.editor textarea {
  margin-top: 8px;
  resize: vertical;
}

.editor .form-input + .form-input {
  margin-top: 8px;
}

.error {
  margin-top: 6px;
  color: #dc2626;
  font-size: 13px;
}

.actions {
  margin-top: 8px;
  display: flex;
  justify-content: flex-end;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.feed-item {
  padding: 12px 14px 14px;
}

.feed-item .header {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 6px;
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

.info .name {
  font-size: 14px;
  font-weight: 500;
}

.info .time {
  font-size: 12px;
}

.content {
  margin: 4px 0 6px;
}

.images {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.images img {
  width: 80px;
  height: 80px;
  object-fit: cover;
  border-radius: 8px;
  background: #e5e7eb;
}

.empty {
  padding: 20px 12px;
  text-align: center;
}
</style>

