<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { ElDialog, ElTabs, ElTabPane, ElButton, ElMessage, ElEmpty, ElAvatar } from 'element-plus'
import { userApi, messageApi } from '../api'
import type { FollowingItem, FollowerItem } from '../api'

/** 分享内容种类：游记 / 路线 / 结伴 / 动态 */
const TYPE_LABELS: Record<string, string> = {
  note: '游记',
  route: '路线',
  companion: '结伴',
  feed: '动态',
}

const props = defineProps<{
  visible: boolean
  shareUrl: string
  shareTitle: string
  /** 分享内容配图（有则显示在左侧） */
  shareImage?: string
  /** 分享内容种类：note / route / companion / feed */
  shareType?: 'note' | 'route' | 'companion' | 'feed'
}>()

const shareTypeLabel = computed(() =>
  props.shareType ? TYPE_LABELS[props.shareType] || props.shareType : ''
)

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
}>()

const activeTab = ref<'following' | 'followers'>('following')
const followingList = ref<FollowingItem[]>([])
const followersList = ref<FollowerItem[]>([])
const loadingFollowing = ref(false)
const loadingFollowers = ref(false)
/** 当前正在发送私信的用户 ID，用于该行按钮 loading */
const sendingUserId = ref<number | null>(null)

const shareText = computed(() => `${props.shareTitle}\n${props.shareUrl}`)

async function loadFollowing() {
  loadingFollowing.value = true
  try {
    followingList.value = await userApi.myFollowing()
  } catch {
    followingList.value = []
  } finally {
    loadingFollowing.value = false
  }
}

async function loadFollowers() {
  loadingFollowers.value = true
  try {
    followersList.value = await userApi.myFollowers()
  } catch {
    followersList.value = []
  } finally {
    loadingFollowers.value = false
  }
}

watch(
  () => props.visible,
  (v) => {
    if (v) {
      loadFollowing()
      loadFollowers()
    }
  }
)

function close() {
  emit('update:visible', false)
}

/** 以私信方式分享给指定用户 */
async function shareToUser(userId: number) {
  sendingUserId.value = userId
  try {
    await messageApi.sendChatMessage(userId, shareText.value)
    ElMessage.success('已以私信方式分享')
  } catch (e: unknown) {
    ElMessage.error((e as { message?: string })?.message || '发送失败')
  } finally {
    sendingUserId.value = null
  }
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    title="分享给好友"
    width="440px"
    class="share-to-dialog"
    @update:model-value="close"
  >
    <!-- 顶部：分享内容概览（左图右文，不显示链接） -->
    <div class="share-content-block">
      <div v-if="shareImage" class="share-content-img-wrap">
        <img :src="shareImage" alt="" class="share-content-img" />
      </div>
      <div class="share-content-main">
        <div v-if="shareTypeLabel" class="share-content-type">{{ shareTypeLabel }}</div>
        <div class="share-content-title">{{ shareTitle }}</div>
      </div>
    </div>

    <!-- 关注的人 / 粉丝 -->
    <el-tabs v-model="activeTab" class="share-tabs">
      <el-tab-pane label="关注的人" name="following">
        <div v-if="loadingFollowing" class="loading-tip">加载中...</div>
        <div v-else-if="followingList.length === 0" class="empty-tip">
          <el-empty description="暂无关注" :image-size="60" />
        </div>
        <div v-else class="user-list">
          <div v-for="u in followingList" :key="u.userId" class="user-row">
            <el-avatar :size="40" class="user-avatar">
              <img v-if="u.avatar" :src="u.avatar" :alt="u.nickname" />
              <span v-else>{{ (u.nickname || '用').charAt(0) }}</span>
            </el-avatar>
            <span class="user-nickname">{{ u.nickname || '用户' }}</span>
            <el-button
              type="primary"
              size="small"
              class="share-btn"
              :loading="sendingUserId === u.userId"
              @click="shareToUser(u.userId)"
            >
              分享
            </el-button>
          </div>
        </div>
      </el-tab-pane>
      <el-tab-pane label="粉丝" name="followers">
        <div v-if="loadingFollowers" class="loading-tip">加载中...</div>
        <div v-else-if="followersList.length === 0" class="empty-tip">
          <el-empty description="暂无粉丝" :image-size="60" />
        </div>
        <div v-else class="user-list">
          <div v-for="u in followersList" :key="u.userId" class="user-row">
            <el-avatar :size="40" class="user-avatar">
              <img v-if="u.avatar" :src="u.avatar" :alt="u.nickname" />
              <span v-else>{{ (u.nickname || '用').charAt(0) }}</span>
            </el-avatar>
            <span class="user-nickname">{{ u.nickname || '用户' }}</span>
            <el-button
              type="primary"
              size="small"
              class="share-btn"
              :loading="sendingUserId === u.userId"
              @click="shareToUser(u.userId)"
            >
              分享
            </el-button>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <template #footer>
      <el-button @click="close">取消</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.share-content-block {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 16px;
  padding: 14px 16px;
  background: #f5f7fa;
  border-radius: 10px;
  border: 1px solid #ebeef5;
}

.share-content-img-wrap {
  flex-shrink: 0;
  width: 72px;
  height: 72px;
  border-radius: 8px;
  overflow: hidden;
  background: #e4e7ed;
}

.share-content-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.share-content-main {
  flex: 1;
  min-width: 0;
}

.share-content-type {
  font-size: 12px;
  color: #0d9488;
  margin-bottom: 6px;
  font-weight: 500;
}

.share-content-title {
  font-size: 14px;
  color: #303133;
  line-height: 1.6;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
}

.share-tabs {
  min-height: 200px;
}

.loading-tip,
.empty-tip {
  padding: 24px 0;
  text-align: center;
  color: #909399;
  font-size: 14px;
}

.user-list {
  max-height: 280px;
  overflow-y: auto;
}

.user-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid #f0f2f5;
}

.user-row:last-child {
  border-bottom: none;
}

.user-avatar {
  flex-shrink: 0;
  background: linear-gradient(135deg, #5eead4, #0d9488);
  color: #fff;
  font-size: 16px;
  font-weight: 600;
}

.user-nickname {
  flex: 1;
  min-width: 0;
  font-size: 15px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.share-btn {
  flex-shrink: 0;
}

.share-to-dialog :deep(.el-dialog__body) {
  padding-top: 8px;
}
</style>
