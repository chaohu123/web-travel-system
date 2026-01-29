<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  useChatStore,
  type ChatMessage,
  type ChatRoutePayload,
  type ChatCompanionPayload,
} from '../store/chat'
import { useAuthStore } from '../store'

const route = useRoute()
const router = useRouter()
const chatStore = useChatStore()
const auth = useAuthStore()

const contactId = computed(() => {
  const raw = route.params.id
  return raw && !Array.isArray(raw) ? String(raw) : '0'
})

const contactName = computed(() => {
  const q = route.query.nickname
  return typeof q === 'string' && q.trim() ? q.trim() : '旅友'
})

const contactRole = computed(() => {
  const q = route.query.role
  return typeof q === 'string' && q.trim() ? q.trim() : '结伴发起人'
})

const companionPayload = computed<ChatCompanionPayload | null>(() => {
  const dest = typeof route.query.destination === 'string' ? route.query.destination : ''
  const start = typeof route.query.startDate === 'string' ? route.query.startDate : ''
  const end = typeof route.query.endDate === 'string' ? route.query.endDate : ''
  const cid = route.query.companionId
  if (!dest || !start || !end || !cid || Array.isArray(cid)) return null
  return { id: Number(cid), destination: dest, startDate: start, endDate: end }
})

const sessionId = computed(() => `user-${contactId.value}`)
const messages = computed<ChatMessage[]>(() => chatStore.getMessages(sessionId.value))

const draft = ref('')
const sending = ref(false)
const previewVisible = ref(false)
const previewImageUrl = ref('')
const guideVisible = ref(false)

const messageListRef = ref<HTMLDivElement | null>(null)

async function ensureLogin() {
  if (auth.token) return true
  try {
    await ElMessageBox.confirm('登录后才可以与旅友私信沟通和发送路线～', '请先登录', {
      confirmButtonText: '去登录',
      cancelButtonText: '取消',
      type: 'warning',
    })
    router.push({ name: 'login', query: { redirect: route.fullPath } })
  } catch {
    // 用户取消
  }
  return false
}

function scrollToBottom(immediate = false) {
  const fn = () => {
    const el = messageListRef.value
    if (!el) return
    el.scrollTop = el.scrollHeight
  }
  if (immediate) fn()
  else nextTick(fn)
}

function formatTimeLabel(iso: string) {
  const d = new Date(iso)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  const oneDay = 24 * 60 * 60 * 1000
  if (diff < oneDay) return d.toTimeString().slice(0, 5)
  if (diff < 2 * oneDay) return `昨天 ${d.toTimeString().slice(0, 5)}`
  return d.toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

function shouldShowTimeDivider(curr: ChatMessage, prev?: ChatMessage) {
  if (!prev) return true
  const currTime = new Date(curr.createdAt).getTime()
  const prevTime = new Date(prev.createdAt).getTime()
  return currTime - prevTime > 10 * 60 * 1000
}

function detectSensitive(text: string) {
  const lowered = text.toLowerCase()
  const patterns = [/wechat|微信/, /qq/, /alipay|支付宝/, /转账|打款/, /银行卡/, /身份证/]
  return patterns.some((re) => re.test(lowered))
}

async function sendText() {
  const content = draft.value.trim()
  if (!content || sending.value) return
  const ok = await ensureLogin()
  if (!ok) return

  if (detectSensitive(content)) {
    await ElMessageBox.alert(
      '为保障资金与人身安全，请优先在平台内沟通并确认行程后，再考虑交换微信、QQ 等第三方联系方式或转账。',
      '安全提醒',
      { confirmButtonText: '我已了解', type: 'warning' },
    )
  }

  sending.value = true
  draft.value = ''
  const msg = chatStore.addTextMessage(sessionId.value, 'me', content)
  scrollToBottom()

  // 准实时占位：模拟对方回复 + 偶发失败
  setTimeout(() => {
    const fail = Math.random() < 0.06
    if (fail) {
      chatStore.markFailed(sessionId.value, msg.id)
      sending.value = false
      return
    }
    chatStore.addTextMessage(sessionId.value, 'other', '收到～我们先对齐预算和每天可用时间，再定具体景点。')
    sending.value = false
    scrollToBottom()
  }, 650)
}

function retrySend(m: ChatMessage) {
  if (m.from !== 'me' || m.type !== 'text' || !m.failed) return
  m.failed = false
  ElMessage.success('已重试发送（示例）')
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    sendText()
  }
}

function pickEmoji() {
  draft.value += draft.value ? ' 😊' : '😊'
}

function openImagePreview(url: string) {
  previewImageUrl.value = url
  previewVisible.value = true
}

async function handleImageChange(file: any) {
  const ok = await ensureLogin()
  if (!ok) return false
  const raw = file.raw as File | undefined
  if (!raw) return false
  const url = URL.createObjectURL(raw)
  chatStore.addImageMessage(sessionId.value, 'me', url)
  scrollToBottom()
  return false
}

async function sendRouteQuick() {
  const ok = await ensureLogin()
  if (!ok) return
  const payload: ChatRoutePayload = {
    id: Number(route.query.routeId || 2),
    title: typeof route.query.routeTitle === 'string' ? route.query.routeTitle : '推荐路线：轻松慢游 6 日',
    days: Number(route.query.routeDays || 6),
    destination: typeof route.query.destination === 'string' ? route.query.destination : '目的地待定',
  }
  chatStore.addRouteCard(sessionId.value, 'me', payload)
  scrollToBottom()
}

async function sendCompanionQuick() {
  const ok = await ensureLogin()
  if (!ok) return
  if (!companionPayload.value) {
    ElMessage.info('当前会话未关联结伴活动')
    return
  }
  chatStore.addCompanionCard(sessionId.value, 'me', companionPayload.value)
  scrollToBottom()
}

function goRouteDetail(payload?: ChatRoutePayload) {
  if (!payload) return
  router.push(`/routes/${payload.id}`)
}

function goCompanionDetail(payload?: ChatCompanionPayload) {
  if (!payload) return
  router.push(`/companion/${payload.id}`)
}

function goCurrentCompanion() {
  if (!companionPayload.value) return
  goCompanionDetail(companionPayload.value)
}

function goUserProfile() {
  if (!contactId.value || contactId.value === '0') return
  router.push({ name: 'user-profile', params: { id: contactId.value } })
}

function handleReport() {
  ElMessageBox.confirm('确认要举报该用户吗？我们会优先处理涉及诈骗、辱骂等行为的举报。', '举报确认', {
    confirmButtonText: '确认举报',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => ElMessage.success('已提交举报，我们会尽快处理。'))
    .catch(() => {})
}

function handleBlock() {
  ElMessageBox.confirm('拉黑后将不再收到对方的新消息，确认继续吗？', '拉黑确认', {
    confirmButtonText: '确认拉黑',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => ElMessage.success('已拉黑该用户，可在账户设置中解除。'))
    .catch(() => {})
}

function handleClear() {
  ElMessageBox.confirm('确定清空当前聊天记录吗？此操作仅影响本设备显示。', '清空聊天记录', {
    confirmButtonText: '清空',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => {
      ;(chatStore.sessions as any)[sessionId.value] = []
    })
    .catch(() => {})
}

onMounted(() => {
  if (messages.value.length === 0) {
    chatStore.addSystemTip(sessionId.value, '提示：平台不展示第三方联系方式，请勿在未确认前添加微信或转账。')
  }
  scrollToBottom(true)
})

onBeforeUnmount(() => {
  if (previewImageUrl.value) URL.revokeObjectURL(previewImageUrl.value)
})
</script>

<template>
  <div class="chat-page">
    <el-header class="chat-header" height="64px">
      <div class="header-inner">
        <el-button link class="back-btn" @click="router.back()">← 返回</el-button>

        <div class="contact" @click="goUserProfile">
          <el-avatar class="contact-avatar" size="small">
            {{ contactName.charAt(0).toUpperCase() }}
          </el-avatar>
          <div class="contact-meta">
            <div class="contact-name">
              {{ contactName }}
              <el-tag size="small" type="warning" effect="plain" class="contact-role">
                {{ contactRole }}
              </el-tag>
            </div>
            <div class="contact-subtitle">结伴沟通 · 私信聊天</div>
          </div>
        </div>

        <el-dropdown>
          <span class="more-trigger">更多</span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="goCurrentCompanion">查看结伴活动</el-dropdown-item>
              <el-dropdown-item divided @click="handleReport">举报</el-dropdown-item>
              <el-dropdown-item @click="handleBlock">拉黑</el-dropdown-item>
              <el-dropdown-item divided @click="handleClear">清空聊天记录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>

    <main class="chat-main">
      <section v-if="companionPayload" class="companion-hint">
        <el-card shadow="hover" class="companion-hint-card">
          <div class="hint-title">当前正在沟通的结伴活动</div>
          <div class="hint-body">
            <div class="hint-left">
              <div class="hint-dest">{{ companionPayload.destination }}</div>
              <div class="hint-date">{{ companionPayload.startDate }} – {{ companionPayload.endDate }}</div>
            </div>
            <el-button type="primary" plain @click="goCurrentCompanion">查看详情</el-button>
          </div>
        </el-card>
      </section>

      <section ref="messageListRef" class="message-list">
        <template v-for="(m, idx) in messages" :key="m.id">
          <div v-if="shouldShowTimeDivider(m, messages[idx - 1])" class="time-divider">
            <span>{{ formatTimeLabel(m.createdAt) }}</span>
          </div>

          <div v-if="m.type === 'system'" class="system-tip">
            {{ m.content }}
          </div>

          <div v-else class="msg-row" :class="m.from === 'me' ? 'is-me' : 'is-other'">
            <!-- 左侧消息：头像在左，气泡在右 -->
            <template v-if="m.from === 'other'">
              <el-avatar class="msg-avatar msg-avatar-left" size="small">
                {{ contactName.charAt(0).toUpperCase() }}
              </el-avatar>
              <div class="bubble-wrap">
                <el-card
                  v-if="m.type === 'route' && m.routePayload"
                  class="bubble-card route"
                  shadow="hover"
                  @click="goRouteDetail(m.routePayload)"
                >
                  <div class="card-title">路线：{{ m.routePayload.title }}</div>
                  <div class="card-meta">{{ m.routePayload.destination }} · {{ m.routePayload.days }} 天</div>
                  <div class="card-link">查看路线详情 →</div>
                </el-card>

                <el-card
                  v-else-if="m.type === 'companion' && m.companionPayload"
                  class="bubble-card companion"
                  shadow="hover"
                  @click="goCompanionDetail(m.companionPayload)"
                >
                  <div class="card-title">结伴：{{ m.companionPayload.destination }}</div>
                  <div class="card-meta">{{ m.companionPayload.startDate }} – {{ m.companionPayload.endDate }}</div>
                  <div class="card-link">查看结伴详情 →</div>
                </el-card>

                <div
                  v-else-if="m.type === 'image' && m.imageUrl"
                  class="bubble bubble-image other"
                  @click="openImagePreview(m.imageUrl)"
                >
                  <img :src="m.imageUrl" alt="图片" class="image-content" />
                </div>

                <div v-else class="bubble bubble-text other">
                  <p>{{ m.content }}</p>
                </div>
              </div>
            </template>

            <!-- 右侧消息：气泡在左，头像在右（仿微信/QQ） -->
            <template v-else>
              <div class="bubble-wrap from-me">
                <el-card
                  v-if="m.type === 'route' && m.routePayload"
                  class="bubble-card route"
                  shadow="hover"
                  @click="goRouteDetail(m.routePayload)"
                >
                  <div class="card-title">路线：{{ m.routePayload.title }}</div>
                  <div class="card-meta">{{ m.routePayload.destination }} · {{ m.routePayload.days }} 天</div>
                  <div class="card-link">查看路线详情 →</div>
                </el-card>

                <el-card
                  v-else-if="m.type === 'companion' && m.companionPayload"
                  class="bubble-card companion"
                  shadow="hover"
                  @click="goCompanionDetail(m.companionPayload)"
                >
                  <div class="card-title">结伴：{{ m.companionPayload.destination }}</div>
                  <div class="card-meta">{{ m.companionPayload.startDate }} – {{ m.companionPayload.endDate }}</div>
                  <div class="card-link">查看结伴详情 →</div>
                </el-card>

                <div
                  v-else-if="m.type === 'image' && m.imageUrl"
                  class="bubble bubble-image me"
                  @click="openImagePreview(m.imageUrl)"
                >
                  <img :src="m.imageUrl" alt="图片" class="image-content" />
                </div>

                <div v-else class="bubble bubble-text me" @click="retrySend(m)">
                  <p>{{ m.content }}</p>
                  <span v-if="m.failed" class="failed-tag">发送失败，点击重试</span>
                </div>
              </div>
              <el-avatar class="msg-avatar msg-avatar-right" size="small">
                {{ (auth.nickname || '我').charAt(0).toUpperCase() }}
              </el-avatar>
            </template>
          </div>
        </template>
      </section>

      <footer class="composer">
        <div class="toolbar">
          <el-button text size="small" @click="pickEmoji">表情</el-button>
          <el-upload :show-file-list="false" accept="image/*" :auto-upload="false" :on-change="handleImageChange">
            <el-button text size="small">图片</el-button>
          </el-upload>
          <el-button text size="small" @click="sendCompanionQuick">发送行程</el-button>
          <el-button text size="small" @click="sendRouteQuick">发送路线</el-button>
        </div>

        <div class="input-row">
          <el-input
            v-model="draft"
            type="textarea"
            :autosize="{ minRows: 1, maxRows: 4 }"
            placeholder="输入消息，Enter 发送（Shift+Enter 换行）"
            @keydown="handleKeydown"
          />
          <el-button type="primary" :loading="sending" @click="sendText">发送</el-button>
        </div>

        <div class="safety">
          为保障安全，请勿在未确认前添加微信或转账
          <a href="javascript:void(0)" @click="guideVisible = true">查看《安全指引》</a>
        </div>
      </footer>
    </main>

    <el-dialog v-model="previewVisible" width="520px">
      <img v-if="previewImageUrl" :src="previewImageUrl" class="preview-img" alt="预览" />
    </el-dialog>

    <el-dialog v-model="guideVisible" title="出行安全指引" width="520px">
      <ul class="guide-list">
        <li>尽量在平台内完成沟通与结伴确认，谨慎线下交易与转账。</li>
        <li>不要轻易提供身份证号、银行卡号等敏感信息。</li>
        <li>如对方频繁催促付款或索要押金，请提高警惕并及时举报。</li>
        <li>线下见面请选择公共场所，提前告知亲友行程。</li>
      </ul>
      <template #footer>
        <el-button type="primary" @click="guideVisible = false">我已了解</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.chat-page {
  min-height: 100vh;
  background: radial-gradient(circle at top left, #e0f2fe 0, transparent 55%),
    radial-gradient(circle at bottom right, #fef3c7 0, transparent 55%),
    #e5e7eb;
  display: flex;
  flex-direction: column;
}

.chat-header {
  background: #020617;
  color: #e5e7eb;
  box-shadow: 0 2px 12px rgba(15, 23, 42, 0.7);
}

.header-inner {
  max-width: 980px;
  margin: 0 auto;
  padding: 0 12px;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.back-btn {
  color: #e5e7eb;
}

.contact {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
}

.contact-avatar {
  background: linear-gradient(135deg, #5eead4, #0d9488);
  color: #fff;
}

.contact-meta {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.contact-name {
  display: flex;
  gap: 6px;
  align-items: center;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.contact-role {
  font-size: 11px;
}

.contact-subtitle {
  font-size: 12px;
  color: #9ca3af;
}

.more-trigger {
  cursor: pointer;
  font-size: 13px;
  color: #e5e7eb;
}

.chat-main {
  flex: 1;
  width: 100%;
  max-width: 980px;
  margin: 12px auto 0;
  padding: 0 12px 12px;
  display: flex;
  flex-direction: column;
}

.companion-hint {
  margin-bottom: 10px;
}

.companion-hint-card {
  border-radius: 16px;
}

.hint-title {
  font-size: 12px;
  color: #64748b;
  margin-bottom: 6px;
}

.hint-body {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.hint-dest {
  font-weight: 700;
  color: #0f172a;
}

.hint-date {
  font-size: 12px;
  color: #64748b;
  margin-top: 2px;
}

.message-list {
  flex: 1;
  background: rgba(248, 250, 252, 0.92);
  border-radius: 16px;
  box-shadow: 0 18px 50px rgba(15, 23, 42, 0.18);
  padding: 12px 12px 8px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.time-divider {
  display: flex;
  justify-content: center;
  margin: 4px 0 6px;
}

.time-divider span {
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 11px;
  background: #e5e7eb;
  color: #6b7280;
}

.system-tip {
  align-self: center;
  max-width: 92%;
  padding: 6px 10px;
  border-radius: 999px;
  background: #fef9c3;
  color: #854d0e;
  font-size: 12px;
}

.msg-row {
  display: flex;
  align-items: flex-end;
  gap: 6px;
}

.msg-row.is-other {
  justify-content: flex-start;
}

.msg-row.is-me {
  justify-content: flex-end;
}

.msg-avatar {
  flex-shrink: 0;
  background: linear-gradient(135deg, #14b8a6, #6366f1);
  color: #fff;
  font-weight: 600;
}

.msg-avatar-left {
  order: 0;
}

.msg-avatar-right {
  order: 1; /* 确保在右侧消息时，头像在气泡右侧 */
}

.bubble-wrap {
  max-width: 72%;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.bubble {
  padding: 8px 10px;
  border-radius: 14px;
  font-size: 14px;
  line-height: 1.5;
  word-break: break-word;
  animation: fadeInUp 0.18s ease-out;
}

.bubble.me {
  background: #4f46e5;
  color: #f9fafb;
  border-bottom-right-radius: 2px;
}

.bubble.other {
  background: #ffffff;
  color: #111827;
  border-bottom-left-radius: 2px;
}

.bubble-image {
  padding: 4px;
  background: transparent;
}

.image-content {
  max-width: 220px;
  border-radius: 12px;
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.25);
  animation: imgFade 0.25s ease-out;
}

.bubble-card {
  border-radius: 12px;
  padding: 8px 10px;
  cursor: pointer;
  animation: fadeInUp 0.18s ease-out;
}

.bubble-card.route {
  border-left: 3px solid #4f46e5;
}

.bubble-card.companion {
  border-left: 3px solid #0d9488;
}

.card-title {
  font-size: 13px;
  font-weight: 700;
  margin-bottom: 2px;
}

.card-meta {
  font-size: 12px;
  color: #64748b;
  margin-bottom: 4px;
}

.card-link {
  font-size: 12px;
  color: #2563eb;
}

.failed-tag {
  display: block;
  margin-top: 2px;
  font-size: 11px;
  color: #fecaca;
}

.composer {
  margin-top: 10px;
  background: rgba(248, 250, 252, 0.96);
  border-radius: 16px;
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.12);
  padding: 10px 10px 8px;
}

.toolbar {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
}

.input-row {
  margin-top: 8px;
  display: flex;
  gap: 8px;
  align-items: flex-end;
}

.input-row :deep(.el-textarea__inner) {
  border-radius: 999px;
}

.safety {
  margin-top: 6px;
  font-size: 11px;
  color: #94a3b8;
}

.safety a {
  color: #2563eb;
}

.preview-img {
  width: 100%;
  border-radius: 12px;
}

.guide-list {
  margin: 0;
  padding-left: 18px;
  color: #475569;
  font-size: 13px;
  line-height: 1.7;
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(4px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes imgFade {
  from {
    opacity: 0;
    transform: scale(0.98);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

@media (max-width: 768px) {
  .chat-main {
    margin-top: 0;
    padding-bottom: 0;
  }

  .message-list {
    border-radius: 0;
  }

  .composer {
    border-radius: 0;
    position: sticky;
    bottom: 0;
  }

  .contact {
    justify-content: flex-start;
  }
}
</style>
