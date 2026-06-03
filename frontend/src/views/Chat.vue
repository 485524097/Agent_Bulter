<template>
  <div class="chat-page">
    <div class="sidebar">
      <div class="logo">Butler AI</div>

      <el-button type="primary" class="new-chat" @click="newChat">
        新会话
      </el-button>

      <div class="session-title">会话列表</div>

      <div
          v-loading="sessionLoading"
          class="session-list"
      >
        <div
            v-if="sessions.length === 0"
            class="empty-session"
        >
          暂无历史会话
        </div>

        <div
            v-for="item in sessions"
            :key="item.sessionId"
            class="session-item"
            :class="{ active: item.sessionId === sessionId }"
            @click="handleSelectSession(item)"
        >
          <div class="session-item-main">
            <div class="session-item-title">
              {{ item.title || '新会话' }}
            </div>
            <div class="session-item-message">
              {{ item.lastMessage || '暂无消息' }}
            </div>
          </div>

          <el-button
              size="small"
              text
              type="danger"
              @click.stop="handleDeleteSession(item)"
          >
            删
          </el-button>
        </div>
      </div>

      <div class="tips">
        <p>你可以试试：</p>
        <p>今天奶茶18</p>
        <p>我这个月花了多少钱</p>
        <p>餐饮预算还剩多少</p>
        <p>我老是买喝的，怎么省一点</p>
      </div>

      <el-button class="logout" @click="handleLogout">
        退出登录
      </el-button>
    </div>

    <div class="main">
      <div class="header">
        <div>
          <h2>和小布聊天</h2>
          <p>自然语言记账、查账、预算分析和财务建议</p>
        </div>

        <div class="user-info" v-if="currentUser">
          <div class="nickname">
            {{ currentUser.nickname || currentUser.username || '用户' }}
          </div>
          <div class="mobile">
            {{ currentUser.mobile }}
          </div>
        </div>
      </div>
      <div class="quick-panel">
        <div
            class="quick-card"
            @click="sendQuickMessage('我这个月花了多少钱')"
        >
          <div class="quick-title">本月支出</div>
          <div class="quick-desc">查询本月总消费</div>
        </div>

        <div
            class="quick-card"
            @click="sendQuickMessage('分析一下我这个月的消费情况')"
        >
          <div class="quick-title">消费分析</div>
          <div class="quick-desc">查看分类占比</div>
        </div>

        <div
            class="quick-card"
            @click="sendQuickMessage('预算查询')"
        >
          <div class="quick-title">预算查询</div>
          <div class="quick-desc">查看预算使用率</div>
        </div>

        <div
            class="quick-card"
            @click="sendQuickMessage('我老是买喝的，怎么省一点？')"
        >
          <div class="quick-title">财务建议</div>
          <div class="quick-desc">RAG 消费建议</div>
        </div>
      </div>

      <div class="messages" ref="messageBoxRef">
        <div
            v-for="(item, index) in messages"
            :key="index"
            class="message-row"
            :class="item.role"
        >
          <div class="bubble">
            <div class="role">
              {{ item.role === 'user' ? '我' : '小布' }}
            </div>
            <div class="content">
              {{ item.content }}
            </div>

            <div
                v-if="item.actions && item.actions.length"
                class="actions"
            >
              <el-tag
                  v-for="(action, idx) in item.actions"
                  :key="idx"
                  size="small"
                  type="success"
              >
                {{ action.name }}
              </el-tag>
            </div>
          </div>
        </div>
      </div>

      <div class="input-area">
        <el-input
            v-model="inputMessage"
            type="textarea"
            :rows="3"
            placeholder="请输入，例如：今天奶茶18"
            @keydown.enter.exact.prevent="handleSend"
        />

        <div class="button-group">
          <el-button
              type="primary"
              class="send-button"
              :loading="sending"
              :disabled="streaming"
              @click="handleSend"
          >
            发送
          </el-button>

          <el-button
              type="success"
              class="send-button"
              :loading="streaming"
              :disabled="sending"
              @click="handleStreamSend"
          >
            流式
          </el-button>
        </div>
      </div>
    </div>
    <div class="notify-panel">
      <div class="notify-header">
        <div>
          <h3>通知中心</h3>
          <p>预算预警与系统通知</p>
        </div>

        <el-badge :value="unreadCount" :hidden="unreadCount === 0">
          <el-button size="small" @click="loadNotifications">
            刷新
          </el-button>
        </el-badge>
      </div>

      <div class="notify-actions">
        <span>未读：{{ unreadCount }}</span>
        <el-button
            size="small"
            type="primary"
            text
            @click="handleMarkAllRead"
        >
          全部已读
        </el-button>
      </div>

      <div
          v-loading="notificationLoading"
          class="notify-list"
      >
        <div
            v-if="notifications.length === 0"
            class="empty-notify"
        >
          暂无通知
        </div>

        <div
            v-for="item in notifications"
            :key="item.id"
            class="notify-item"
            :class="{ unread: item.readStatus === 0 }"
            @click="handleMarkRead(item)"
        >
          <div class="notify-item-title">
            <span>{{ item.title }}</span>
            <el-tag
                v-if="item.readStatus === 0"
                size="small"
                type="danger"
            >
              未读
            </el-tag>
            <el-tag
                v-else
                size="small"
                type="info"
            >
              已读
            </el-tag>
          </div>

          <div class="notify-item-content">
            {{ item.content }}
          </div>

          <div class="notify-item-time">
            {{ item.createdTime }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>

import { nextTick, onMounted, onBeforeUnmount, ref } from 'vue'
import {
  connectNotificationSocket,
  closeNotificationSocket
} from '../utils/notificationSocket'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  chat,
  listSessions,
  getHistory,
  deleteSession
} from '../api/agent'
import { logout, getCurrentUser } from '../api/auth'
import { streamChat } from '../api/agentStream'
import {
  listNotifications,
  countUnreadNotifications,
  markNotificationRead,
  markAllNotificationsRead
} from '../api/notification'

const router = useRouter()

const sessionId = ref('')
const currentSessionTitle = ref('新会话')
const inputMessage = ref('')
const currentUser = ref(null)
const sending = ref(false)
const messageBoxRef = ref(null)
const notifications = ref([])
const unreadCount = ref(0)
const notificationLoading = ref(false)
const sessions = ref([])
const sessionLoading = ref(false)


const messages = ref([
  {
    role: 'assistant',
    content: '你好呀，我是小布，你的 AI 记账管家。你可以直接说：今天奶茶18，或者问我这个月花了多少钱。',
    actions: []

  }
])
onMounted(() => {
  loadCurrentUser()
  loadSessions()
  loadNotifications()

  connectNotificationSocket(() => {
    loadNotifications()
  })
})

onBeforeUnmount(() => {
  closeNotificationSocket()
})

const newChat = () => {
  sessionId.value = ''
  currentSessionTitle.value = '新会话'

  messages.value = [
    {
      role: 'assistant',
      content: '新的会话开始啦，你可以继续让我帮你记账、查账或做消费分析。',
      actions: []
    }
  ]
}

const scrollToBottom = async () => {
  await nextTick()

  if (messageBoxRef.value) {
    messageBoxRef.value.scrollTop = messageBoxRef.value.scrollHeight
  }
}

const handleSend = async () => {
  const text = inputMessage.value.trim()

  if (!text) {
    ElMessage.warning('请输入内容')
    return
  }

  const isNewSession = !sessionId.value

  messages.value.push({
    role: 'user',
    content: text,
    actions: []
  })

  inputMessage.value = ''
  sending.value = true

  await scrollToBottom()

  try {
    const res = await chat({
      sessionId: sessionId.value,
      message: text
    })

    sessionId.value = res.sessionId

    if (isNewSession) {
      currentSessionTitle.value = buildTitle(text)
    }

    messages.value.push({
      role: 'assistant',
      content: res.reply,
      actions: res.actions || []
    })

    await scrollToBottom()
  } finally {
    sending.value = false
    await loadSessions()
  }
}
const loadCurrentUser = async () => {
  try {
    currentUser.value = await getCurrentUser()
  } catch (e) {
    console.log('获取用户信息失败', e)
  }
}
const sendQuickMessage = async (text) => {
  inputMessage.value = text
  await handleSend()
}
const loadNotifications = async () => {
  try {
    notificationLoading.value = true

    const list = await listNotifications()
    const count = await countUnreadNotifications()

    notifications.value = list || []
    unreadCount.value = count || 0
  } finally {
    notificationLoading.value = false
  }
}

const handleMarkRead = async (notification) => {
  if (!notification || notification.readStatus === 1) {
    return
  }

  await markNotificationRead(notification.id)

  ElMessage.success('已标记为已读')

  await loadNotifications()
}

const handleMarkAllRead = async () => {
  if (unreadCount.value <= 0) {
    ElMessage.info('暂无未读通知')
    return
  }

  await markAllNotificationsRead()

  ElMessage.success('已全部标记为已读')

  await loadNotifications()
}
const buildTitle = (text) => {
  if (!text) {
    return '新会话'
  }

  return text.length > 12 ? text.substring(0, 12) + '...' : text
}
const streaming = ref(false)
const handleStreamSend = async () => {
  const text = inputMessage.value.trim()

  if (!text) {
    ElMessage.warning('请输入内容')
    return
  }

  const isNewSession = !sessionId.value

  messages.value.push({
    role: 'user',
    content: text,
    actions: []
  })

  const assistantMessage = {
    role: 'assistant',
    content: '',
    actions: []
  }

  messages.value.push(assistantMessage)

  inputMessage.value = ''
  streaming.value = true

  await scrollToBottom()

  try {
    await streamChat(
        {
          sessionId: sessionId.value,
          message: text
        },
        {
          onSession: (newSessionId) => {
            sessionId.value = newSessionId

            if (isNewSession && typeof buildTitle === 'function') {
              currentSessionTitle.value = buildTitle(text)
            }
          },

          onMessage: async (partial) => {
            assistantMessage.content += partial
            await scrollToBottom()
          },

          onDone: async () => {
            await scrollToBottom()
          },

          onError: (msg) => {
            assistantMessage.content += msg || '流式回复失败'
          }
        }
    )
  } catch (e) {
    assistantMessage.content = e.message || '流式请求失败'
    ElMessage.error(assistantMessage.content)
  } finally {
    streaming.value = false
  }
}
const handleLogout = async () => {
  await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
    type: 'warning'
  })

  try {
    await logout()
  } catch (e) {
    // 即使后端退出失败，也清理本地 token
  }

  closeNotificationSocket()

  localStorage.removeItem('butler_token')
  localStorage.removeItem('butler_user_id')
  localStorage.removeItem('butler_mobile')

  router.push('/login')
}
const loadSessions = async () => {
  try {
    sessionLoading.value = true

    const list = await listSessions()

    sessions.value = list || []
  } finally {
    sessionLoading.value = false
  }
}
const handleSelectSession = async (session) => {
  if (!session || !session.sessionId) {
    return
  }

  sessionId.value = session.sessionId
  currentSessionTitle.value = session.title || '历史会话'

  const historyList = await getHistory(session.sessionId)

  messages.value = (historyList || []).map(item => {
    return {
      role: item.role === 'USER' ? 'user' : 'assistant',
      content: item.content,
      actions: []
    }
  })

  await scrollToBottom()
}
const handleDeleteSession = async (session) => {
  if (!session || !session.sessionId) {
    return
  }

  await ElMessageBox.confirm('确定要删除这个会话吗？', '提示', {
    type: 'warning'
  })

  await deleteSession(session.sessionId)

  ElMessage.success('会话已删除')

  if (sessionId.value === session.sessionId) {
    newChat()
  }

  await loadSessions()
}
</script>

<style scoped>
.chat-page {
  height: 100vh;
  display: flex;
  background: #f5f7fb;
}

.sidebar {
  width: 260px;
  background: #ffffff;
  border-right: 1px solid #e8e8e8;
  padding: 20px;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
}

.logo {
  font-size: 22px;
  font-weight: bold;
  margin-bottom: 20px;
}

.new-chat {
  width: 100%;
  margin-bottom: 24px;
}

.session-title {
  font-size: 14px;
  color: #666;
  margin-bottom: 8px;
}
.session-list {
  flex: 1;
  overflow-y: auto;
  margin-top: 8px;
  margin-bottom: 16px;
}

.empty-session {
  font-size: 13px;
  color: #999;
  padding: 12px;
  text-align: center;
}

.session-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px;
  border-radius: 8px;
  cursor: pointer;
  margin-bottom: 8px;
  background: #f7f8fa;
  transition: all 0.2s;
}

.session-item:hover {
  background: #eef5ff;
}

.session-item.active {
  background: #ecf5ff;
  border: 1px solid #b3d8ff;
}

.session-item-main {
  flex: 1;
  min-width: 0;
}

.session-item-title {
  font-size: 14px;
  color: #333;
  font-weight: bold;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.session-item-message {
  margin-top: 4px;
  font-size: 12px;
  color: #999;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.session-id {
  font-size: 12px;
  color: #999;
  word-break: break-all;
  background: #f5f7fb;
  padding: 10px;
  border-radius: 6px;
}

.tips {
  margin-top: 24px;
  font-size: 13px;
  color: #666;
  line-height: 1.8;
}

.logout {
  margin-top: auto;
}

.main {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.header {
  height: 86px;
  background: #ffffff;
  border-bottom: 1px solid #e8e8e8;
  padding: 16px 28px;
  box-sizing: border-box;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.user-info {
  text-align: right;
  font-size: 13px;
}

.nickname {
  font-weight: bold;
  color: #333;
  margin-bottom: 6px;
}

.mobile {
  color: #888;
}

.header h2 {
  margin: 0 0 8px 0;
}

.header p {
  margin: 0;
  color: #888;
}

.messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}

.message-row {
  display: flex;
  margin-bottom: 18px;
}

.message-row.user {
  justify-content: flex-end;
}

.message-row.assistant {
  justify-content: flex-start;
}

.bubble {
  max-width: 620px;
  background: #ffffff;
  padding: 14px 16px;
  border-radius: 10px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.04);
}

.message-row.user .bubble {
  background: #ecf5ff;
}

.role {
  font-size: 13px;
  color: #888;
  margin-bottom: 6px;
}

.content {
  white-space: pre-wrap;
  line-height: 1.7;
}

.actions {
  margin-top: 10px;
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.input-area {
  background: #ffffff;
  border-top: 1px solid #e8e8e8;
  padding: 18px 24px;
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

.button-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.send-button {
  width: 90px;
  height: 34px;
}
.notify-panel {
  width: 320px;
  background: #ffffff;
  border-left: 1px solid #e8e8e8;
  padding: 18px;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
}

.notify-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 14px;
}

.notify-header h3 {
  margin: 0 0 6px 0;
  font-size: 18px;
}

.notify-header p {
  margin: 0;
  font-size: 13px;
  color: #888;
}

.notify-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: #666;
  font-size: 14px;
  margin-bottom: 12px;
}

.notify-list {
  flex: 1;
  overflow-y: auto;
}

.empty-notify {
  color: #999;
  font-size: 14px;
  text-align: center;
  margin-top: 40px;
}

.notify-item {
  padding: 12px;
  border-radius: 8px;
  background: #f7f8fa;
  margin-bottom: 12px;
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid transparent;
}

.notify-item:hover {
  background: #f0f6ff;
}

.notify-item.unread {
  background: #fff7f7;
  border-color: #ffd6d6;
}

.notify-item-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-weight: bold;
  font-size: 14px;
}

.notify-item-content {
  font-size: 13px;
  color: #555;
  line-height: 1.6;
}

.notify-item-time {
  margin-top: 8px;
  font-size: 12px;
  color: #999;
}
.quick-panel {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  padding: 14px 24px;
  background: #ffffff;
  border-bottom: 1px solid #e8e8e8;
}

.quick-card {
  padding: 12px;
  border-radius: 10px;
  background: #f7f8fa;
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid transparent;
}

.quick-card:hover {
  background: #ecf5ff;
  border-color: #b3d8ff;
  transform: translateY(-1px);
}

.quick-title {
  font-size: 15px;
  font-weight: bold;
  color: #333;
  margin-bottom: 6px;
}

.quick-desc {
  font-size: 12px;
  color: #888;
}
</style>