<script setup>
import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue';
import { connectToChat, sendChatMessage, disconnectFromChat } from '@/services/ChatService.js';
import { useAuth } from '@/composables/useAuth.js';

const props = defineProps({
  videoId: {
    type: String,
    required: true
  },
  isLive: {
    type: Boolean,
    default: false
  },
  overlayMode: {
    type: Boolean,
    default: false
  }
});

const { isLoggedIn, user } = useAuth();

// State
const messages = ref([]);
const newMessage = ref('');
const isConnectedToChat = ref(false);
const isConnecting = ref(false);
const connectionError = ref(null);
const chatContainer = ref(null);
const isChatExpanded = ref(true);

// Scroll to bottom when new messages arrive
function scrollToBottom() {
  nextTick(() => {
    if (chatContainer.value) {
      chatContainer.value.scrollTop = chatContainer.value.scrollHeight;
    }
  });
}

// Handle incoming messages
function handleMessage(message) {
  messages.value.push(message);

  // Zadrži samo poslednjih 100 poruka (bez istorije)
  if (messages.value.length > 100) {
    messages.value = messages.value.slice(-100);
  }

  scrollToBottom();
}

// Connect to chat
async function connect() {
  if (!props.videoId || isConnecting.value) return;

  isConnecting.value = true;
  connectionError.value = null;

  try {
    await connectToChat(
      props.videoId,
      isLoggedIn.value && user.value ? user.value : { id: 'guest', username: 'Guest' },
      handleMessage,
      () => {
        isConnectedToChat.value = true;
        isConnecting.value = false;
      },
      (error) => {
        connectionError.value = 'Failed to connect to chat';
        isConnecting.value = false;
        console.error('Chat connection error:', error);
      }
    );
  } catch (error) {
    connectionError.value = 'Failed to connect to chat';
    isConnecting.value = false;
    console.error('Chat connection error:', error);
  }
}

// Send message
function handleSendMessage() {
  if (!newMessage.value.trim() || !isConnectedToChat.value) return;

  if (!isLoggedIn.value) {
    return;
  }

  sendChatMessage(props.videoId, newMessage.value.trim(), user.value);
  newMessage.value = '';
}

// Format timestamp
function formatTime(timestamp) {
  const date = new Date(timestamp);
  return date.toLocaleTimeString('sr-RS', { hour: '2-digit', minute: '2-digit' });
}

// Toggle chat visibility
function toggleChat() {
  isChatExpanded.value = !isChatExpanded.value;
}

// Lifecycle
onMounted(() => {
  if (props.isLive) {
    connect();
  }
});

onUnmounted(() => {
  if (isConnectedToChat.value && user.value) {
    disconnectFromChat(props.videoId, user.value);
  } else {
    disconnectFromChat();
  }
});

// Watch for isLive changes
watch(() => props.isLive, (newVal) => {
  if (newVal && !isConnectedToChat.value && !isConnecting.value) {
    connect();
  }
});

// Watch for videoId changes
watch(() => props.videoId, (newVal, oldVal) => {
  if (newVal !== oldVal) {
    // Disconnect from old chat
    if (isConnectedToChat.value) {
      disconnectFromChat(oldVal, user.value);
    }
    messages.value = [];
    isConnectedToChat.value = false;

    // Connect to new chat if live
    if (props.isLive) {
      connect();
    }
  }
});
</script>

<template>
  <div class="live-chat" :class="{ collapsed: !isChatExpanded, 'overlay-mode': props.overlayMode }">
    <!-- Chat Header -->
    <div class="chat-header" @click="toggleChat">
      <div class="header-title">
        <span class="chat-icon">💬</span>
        <span>Live Chat</span>
        <span v-if="isConnectedToChat" class="connection-status connected"></span>
        <span v-else-if="isConnecting" class="connection-status connecting"></span>
      </div>
      <button class="toggle-btn">
        <svg v-if="isChatExpanded" viewBox="0 0 24 24" fill="currentColor">
          <path d="M7.41 8.59L12 13.17l4.59-4.58L18 10l-6 6-6-6 1.41-1.41z"/>
        </svg>
        <svg v-else viewBox="0 0 24 24" fill="currentColor">
          <path d="M7.41 15.41L12 10.83l4.59 4.58L18 14l-6-6-6 6 1.41 1.41z"/>
        </svg>
      </button>
    </div>

    <!-- Chat Content -->
    <div v-show="isChatExpanded" class="chat-content">
      <!-- Connection Status -->
      <div v-if="isConnecting" class="chat-status">
        <div class="spinner-small"></div>
        <span>Povezivanje na chat...</span>
      </div>

      <div v-else-if="connectionError" class="chat-status error">
        <span>{{ connectionError }}</span>
        <button @click="connect" class="retry-btn">Pokušaj ponovo</button>
      </div>

      <div v-else-if="!props.isLive" class="chat-status">
        <span>Chat je dostupan samo tokom live stream-a</span>
      </div>

      <!-- Messages Container -->
      <div v-else ref="chatContainer" class="messages-container">
        <div v-if="messages.length === 0" class="no-messages">
          <span>Budite prvi koji će poslati poruku!</span>
        </div>

        <div
          v-for="(msg, index) in messages"
          :key="index"
          class="message"
          :class="{
            'system-message': msg.type === 'JOIN' || msg.type === 'LEAVE',
            'own-message': msg.senderId === user?.id
          }"
        >
          <!-- System Messages (JOIN/LEAVE) -->
          <div v-if="msg.type === 'JOIN' || msg.type === 'LEAVE'" class="system-content">
            {{ msg.content }}
          </div>

          <!-- Regular Chat Messages -->
          <template v-else>
            <div class="message-header">
              <span class="message-author">{{ msg.senderUsername }}</span>
              <span class="message-time">{{ formatTime(msg.timestamp) }}</span>
            </div>
            <div class="message-content">{{ msg.content }}</div>
          </template>
        </div>
      </div>

      <!-- Message Input -->
      <div v-if="props.isLive && isConnectedToChat" class="message-input-container">
        <div v-if="!isLoggedIn" class="login-prompt-chat">
          Prijavite se da biste slali poruke
        </div>
        <div v-else class="input-wrapper">
          <input
            v-model="newMessage"
            type="text"
            placeholder="Pošaljite poruku..."
            maxlength="200"
            @keydown.enter="handleSendMessage"
          />
          <button
            class="send-btn"
            @click="handleSendMessage"
            :disabled="!newMessage.trim()"
          >
            <svg viewBox="0 0 24 24" fill="currentColor">
              <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/>
            </svg>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.live-chat {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  max-height: 500px;
  transition: max-height 0.3s ease;
}

.live-chat.collapsed {
  max-height: 48px;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.75rem 1rem;
  background: linear-gradient(135deg, #ff0000 0%, #cc0000 100%);
  color: white;
  cursor: pointer;
  user-select: none;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-weight: 600;
  font-size: 0.95rem;
}

.chat-icon {
  font-size: 1.1rem;
}

.connection-status {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-left: 4px;
}

.connection-status.connected {
  background: #4ade80;
  box-shadow: 0 0 6px #4ade80;
}

.connection-status.connecting {
  background: #fbbf24;
  animation: pulse 1s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.toggle-btn {
  background: none;
  border: none;
  color: white;
  cursor: pointer;
  padding: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.toggle-btn svg {
  width: 20px;
  height: 20px;
}

.chat-content {
  display: flex;
  flex-direction: column;
  flex: 1;
  overflow: hidden;
}

.chat-status {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 2rem 1rem;
  color: #666;
  font-size: 0.9rem;
  text-align: center;
}

.chat-status.error {
  color: #ef4444;
}

.retry-btn {
  padding: 0.5rem 1rem;
  background: #ff0000;
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 0.85rem;
}

.retry-btn:hover {
  background: #cc0000;
}

.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 0.75rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  min-height: 200px;
  max-height: 350px;
}

.no-messages {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #999;
  font-size: 0.9rem;
}

.message {
  padding: 0.5rem 0.75rem;
  border-radius: 8px;
  background: #f5f5f5;
  max-width: 85%;
}

.message.own-message {
  background: #e3f2fd;
  align-self: flex-end;
}

.message.system-message {
  background: transparent;
  color: #999;
  font-size: 0.8rem;
  text-align: center;
  max-width: 100%;
  padding: 0.25rem;
}

.system-content {
  font-style: italic;
}

.message-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.25rem;
}

.message-author {
  font-weight: 600;
  font-size: 0.85rem;
  color: #333;
}

.message-time {
  font-size: 0.75rem;
  color: #999;
}

.message-content {
  font-size: 0.9rem;
  color: #333;
  word-break: break-word;
}

.message-input-container {
  padding: 0.75rem;
  border-top: 1px solid #eee;
  background: #fafafa;
}

.login-prompt-chat {
  text-align: center;
  color: #666;
  font-size: 0.85rem;
  padding: 0.5rem;
}

.input-wrapper {
  display: flex;
  gap: 0.5rem;
}

.input-wrapper input {
  flex: 1;
  padding: 0.6rem 0.75rem;
  border: 1px solid #ddd;
  border-radius: 20px;
  font-size: 0.9rem;
  outline: none;
  transition: border-color 0.2s;
}

.input-wrapper input:focus {
  border-color: #ff0000;
}

.send-btn {
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 50%;
  background: #ff0000;
  color: white;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s;
}

.send-btn:hover:not(:disabled) {
  background: #cc0000;
}

.send-btn:disabled {
  background: #ccc;
  cursor: not-allowed;
}

.send-btn svg {
  width: 18px;
  height: 18px;
}

.spinner-small {
  width: 20px;
  height: 20px;
  border: 2px solid #eee;
  border-top-color: #ff0000;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* Overlay Mode Styles */
.live-chat.overlay-mode {
  background: rgba(0, 0, 0, 0.75);
  backdrop-filter: blur(8px);
  border-radius: 0;
  height: 100%;
  max-height: 100%;
  box-shadow: none;
  border-left: 1px solid rgba(255, 255, 255, 0.1);
}

.live-chat.overlay-mode.collapsed {
  max-height: 100%;
  width: 48px;
}

.live-chat.overlay-mode .chat-header {
  background: rgba(204, 0, 0, 0.85);
  padding: 0.6rem 0.75rem;
}

.live-chat.overlay-mode .header-title {
  font-size: 0.85rem;
}

.live-chat.overlay-mode .chat-content {
  background: transparent;
}

.live-chat.overlay-mode .messages-container {
  max-height: none;
  flex: 1;
  min-height: 0;
}

.live-chat.overlay-mode .message {
  background: rgba(255, 255, 255, 0.1);
  color: white;
}

.live-chat.overlay-mode .message.own-message {
  background: rgba(59, 130, 246, 0.3);
}

.live-chat.overlay-mode .message.system-message {
  color: rgba(255, 255, 255, 0.6);
}

.live-chat.overlay-mode .message-author {
  color: #fbbf24;
}

.live-chat.overlay-mode .message-time {
  color: rgba(255, 255, 255, 0.5);
}

.live-chat.overlay-mode .message-content {
  color: rgba(255, 255, 255, 0.9);
}

.live-chat.overlay-mode .no-messages {
  color: rgba(255, 255, 255, 0.5);
}

.live-chat.overlay-mode .chat-status {
  color: rgba(255, 255, 255, 0.7);
}

.live-chat.overlay-mode .message-input-container {
  background: rgba(0, 0, 0, 0.5);
  border-top: 1px solid rgba(255, 255, 255, 0.1);
}

.live-chat.overlay-mode .login-prompt-chat {
  color: rgba(255, 255, 255, 0.6);
}

.live-chat.overlay-mode .input-wrapper input {
  background: rgba(255, 255, 255, 0.1);
  border-color: rgba(255, 255, 255, 0.2);
  color: white;
}

.live-chat.overlay-mode .input-wrapper input::placeholder {
  color: rgba(255, 255, 255, 0.4);
}

.live-chat.overlay-mode .input-wrapper input:focus {
  border-color: rgba(255, 255, 255, 0.4);
  background: rgba(255, 255, 255, 0.15);
}

.live-chat.overlay-mode .send-btn {
  background: rgba(204, 0, 0, 0.8);
}

.live-chat.overlay-mode .send-btn:hover:not(:disabled) {
  background: rgba(204, 0, 0, 1);
}

.live-chat.overlay-mode .send-btn:disabled {
  background: rgba(255, 255, 255, 0.2);
}

/* Responsive */
@media (max-width: 768px) {
  .live-chat {
    max-height: 400px;
    border-radius: 0;
    margin: 0 -1rem;
  }

  .live-chat.collapsed {
    max-height: 44px;
  }

  .messages-container {
    max-height: 250px;
  }
}
</style>


