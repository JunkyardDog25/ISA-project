<script setup>
import { ref, onMounted, onUnmounted, computed, watch, nextTick } from 'vue';
import { useRoute, useRouter, RouterLink } from 'vue-router';
import { useAuth } from '@/composables/useAuth.js';
import { useToast } from '@/composables/useToast.js';
import { getAllVideos } from '@/services/VideoService.js';
import {
  getWatchPartyByCode,
  connectToWatchParty,
  disconnectFromWatchParty,
  sendPlayVideoMessage,
  sendWatchPartyChatMessage,
  setWatchPartyVideo,
  closeWatchParty,
  sendCloseRoomMessage,
  checkIsOwner
} from '@/services/WatchPartyService.js';

const route = useRoute();
const router = useRouter();
const { isLoggedIn, user } = useAuth();
const { showSuccess, showError, showInfo } = useToast();

// ----- State -----

const loading = ref(true);
const error = ref(null);
const room = ref(null);
const isOwner = ref(false);
const memberCount = ref(0);
const isConnected = ref(false);
const isConnecting = ref(false);

// Video selection state
const showVideoSelector = ref(false);
const availableVideos = ref([]);
const videosLoading = ref(false);

// Chat state
const messages = ref([]);
const newMessage = ref('');
const chatContainer = ref(null);
const isChatExpanded = ref(true);

// ----- Computed -----

const roomCode = computed(() => route.params.roomCode?.toUpperCase());

// ----- Room Data Fetching -----

async function fetchRoom() {
  if (!roomCode.value) {
    error.value = 'Invalid room code';
    loading.value = false;
    return;
  }

  loading.value = true;
  error.value = null;

  try {
    const response = await getWatchPartyByCode(roomCode.value);
    room.value = response.data;
    memberCount.value = response.data.memberCount || 0;

    if (!room.value.active) {
      error.value = 'This Watch Party room is no longer active';
      return;
    }

    // Check if current user is owner
    if (isLoggedIn.value && user.value) {
      try {
        const ownerResponse = await checkIsOwner(roomCode.value);
        isOwner.value = ownerResponse.data.isOwner;
      } catch (e) {
        isOwner.value = false;
      }
    }

    // Connect to WebSocket
    await connectToRoom();

  } catch (e) {
    if (e.response?.status === 404) {
      error.value = 'Watch Party room not found';
    } else {
      error.value = e?.response?.data?.message || e?.message || 'Failed to load Watch Party room';
    }
    console.error('Error fetching room:', e);
  } finally {
    loading.value = false;
  }
}

// ----- WebSocket Connection -----

async function connectToRoom() {
  if (isConnecting.value || isConnected.value) return;

  isConnecting.value = true;

  try {
    await connectToWatchParty(
      roomCode.value,
      isLoggedIn.value && user.value ? user.value : { id: 'guest', username: 'Guest' },
      handleWatchPartyMessage,
      () => {
        isConnected.value = true;
        isConnecting.value = false;
        showSuccess('Connected to Watch Party!');
      },
      (err) => {
        isConnecting.value = false;
        console.error('WebSocket connection error:', err);
        showError('Failed to connect to Watch Party');
      }
    );
  } catch (e) {
    isConnecting.value = false;
    console.error('Connection error:', e);
  }
}

// ----- Handle WebSocket Messages -----

function handleWatchPartyMessage(message) {
  console.log('Received WP message:', message);

  switch (message.type) {
    case 'PLAY_VIDEO':
      handlePlayVideo(message);
      break;
    case 'JOIN':
      addChatMessage({
        type: 'system',
        content: message.content || `${message.senderUsername} joined the party`,
        timestamp: message.timestamp
      });
      break;
    case 'LEAVE':
      addChatMessage({
        type: 'system',
        content: message.content || `${message.senderUsername} left the party`,
        timestamp: message.timestamp
      });
      break;
    case 'CHAT':
      addChatMessage({
        type: 'chat',
        senderUsername: message.senderUsername,
        senderId: message.senderId,
        content: message.content,
        timestamp: message.timestamp
      });
      break;
    case 'MEMBER_COUNT_UPDATE':
      memberCount.value = parseInt(message.content) || 0;
      break;
    case 'ROOM_CLOSED':
      showInfo('Watch Party has been closed by the owner');
      router.push('/');
      break;
  }
}

function handlePlayVideo(message) {
  // Owner started a video - navigate to video page
  if (message.videoId) {
    showInfo(`Now playing: ${message.videoTitle || 'Video'}`);
    router.push(`/video/${message.videoId}`);
  }
}

// ----- Chat Functions -----

function addChatMessage(msg) {
  messages.value.push(msg);
  if (messages.value.length > 100) {
    messages.value = messages.value.slice(-100);
  }
  scrollToBottom();
}

function scrollToBottom() {
  nextTick(() => {
    if (chatContainer.value) {
      chatContainer.value.scrollTop = chatContainer.value.scrollHeight;
    }
  });
}

function sendChatMessage() {
  if (!newMessage.value.trim() || !isConnected.value) return;

  if (!isLoggedIn.value) {
    showError('You must be logged in to send messages');
    return;
  }

  sendWatchPartyChatMessage(roomCode.value, newMessage.value.trim(), user.value);
  newMessage.value = '';
}

function formatTime(timestamp) {
  const date = new Date(timestamp);
  return date.toLocaleTimeString('sr-RS', { hour: '2-digit', minute: '2-digit' });
}

// ----- Video Selection (Owner Only) -----

async function openVideoSelector() {
  showVideoSelector.value = true;
  videosLoading.value = true;

  try {
    const response = await getAllVideos();
    availableVideos.value = response.data.content || response.data || [];
  } catch (e) {
    console.error('Failed to load videos:', e);
    showError('Failed to load videos');
  } finally {
    videosLoading.value = false;
  }
}

async function playSelectedVideo(video) {
  if (!isOwner.value) {
    showError('Only the room owner can play videos');
    return;
  }

  try {
    // Update video in backend
    await setWatchPartyVideo(roomCode.value, video.id);

    // Send WebSocket message to all members
    sendPlayVideoMessage(
      roomCode.value,
      video.id,
      video.title,
      video.thumbnailPath,
      user.value
    );

    showVideoSelector.value = false;

    // Owner also navigates to video
    router.push(`/video/${video.id}`);
  } catch (e) {
    console.error('Failed to play video:', e);
    showError(e?.response?.data?.error || 'Failed to start video');
  }
}

// ----- Room Management -----

async function handleCloseRoom() {
  if (!isOwner.value) return;

  if (!confirm('Are you sure you want to close this Watch Party? All members will be disconnected.')) {
    return;
  }

  try {
    // Send close message via WebSocket first
    sendCloseRoomMessage(roomCode.value, user.value);

    // Then close via API
    await closeWatchParty(roomCode.value);

    showSuccess('Watch Party closed');
    router.push('/');
  } catch (e) {
    console.error('Failed to close room:', e);
    showError(e?.response?.data?.error || 'Failed to close Watch Party');
  }
}

function copyRoomLink() {
  const link = `${window.location.origin}/watch-party/${roomCode.value}`;
  navigator.clipboard.writeText(link).then(() => {
    showSuccess('Room link copied to clipboard!');
  }).catch(() => {
    showError('Failed to copy link');
  });
}

function copyRoomCode() {
  navigator.clipboard.writeText(roomCode.value).then(() => {
    showSuccess('Room code copied to clipboard!');
  }).catch(() => {
    showError('Failed to copy code');
  });
}

// ----- Lifecycle -----

onMounted(() => {
  fetchRoom();
});

onUnmounted(() => {
  if (isConnected.value && user.value) {
    disconnectFromWatchParty(roomCode.value, user.value);
  }
});

// Watch for route changes
watch(() => route.params.roomCode, (newCode) => {
  if (newCode) {
    fetchRoom();
  }
});
</script>

<template>
  <div class="watch-party-container">
    <!-- Loading State -->
    <div v-if="loading" class="loading-container">
      <div class="spinner-border text-primary" role="status">
        <span class="visually-hidden">Loading...</span>
      </div>
      <p class="mt-3">Joining Watch Party...</p>
    </div>

    <!-- Error State -->
    <div v-else-if="error" class="error-container">
      <div class="error-card">
        <svg xmlns="http://www.w3.org/2000/svg" width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="error-icon">
          <circle cx="12" cy="12" r="10"/>
          <line x1="12" y1="8" x2="12" y2="12"/>
          <line x1="12" y1="16" x2="12.01" y2="16"/>
        </svg>
        <h2>{{ error }}</h2>
        <RouterLink to="/" class="btn btn-primary mt-3">Back to Home</RouterLink>
      </div>
    </div>

    <!-- Room Content -->
    <div v-else-if="room" class="room-content">
      <!-- Room Header -->
      <div class="room-header">
        <div class="room-info">
          <h1>{{ room.name }}</h1>
          <p v-if="room.description" class="room-description">{{ room.description }}</p>
          <div class="room-meta">
            <span class="badge bg-primary">
              <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="me-1">
                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
                <circle cx="9" cy="7" r="4"/>
                <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
                <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
              </svg>
              {{ memberCount }} {{ memberCount === 1 ? 'member' : 'members' }}
            </span>
            <span class="badge bg-secondary">
              Host: {{ room.ownerUsername }}
            </span>
            <span v-if="isOwner" class="badge bg-success">You are the host</span>
            <span v-if="room.isPublic" class="badge bg-info">Public</span>
          </div>
        </div>

        <div class="room-actions">
          <button class="btn btn-outline-primary btn-sm" @click="copyRoomCode">
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="me-1">
              <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/>
              <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
            </svg>
            Code: {{ roomCode }}
          </button>
          <button class="btn btn-outline-primary btn-sm" @click="copyRoomLink">
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="me-1">
              <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/>
              <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/>
            </svg>
            Share Link
          </button>
          <button v-if="isOwner" class="btn btn-danger btn-sm" @click="handleCloseRoom">
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="me-1">
              <line x1="18" y1="6" x2="6" y2="18"/>
              <line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
            Close Room
          </button>
        </div>
      </div>

      <!-- Main Content Area -->
      <div class="room-main">
        <!-- Video Selection Panel (Owner Only) -->
        <div class="video-panel">
          <div v-if="isOwner" class="owner-controls">
            <h3>Video Selection</h3>
            <p class="text-muted">As the host, select a video to play for everyone in the party.</p>
            <button class="btn btn-primary" @click="openVideoSelector">
              <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="me-2">
                <polygon points="5 3 19 12 5 21 5 3"/>
              </svg>
              Select Video to Play
            </button>

            <!-- Current Video -->
            <div v-if="room.currentVideoId" class="current-video mt-4">
              <h4>Currently Selected:</h4>
              <div class="video-card-mini">
                <img
                  :src="`http://localhost:8080/${room.currentVideoThumbnail}`"
                  :alt="room.currentVideoTitle"
                  class="video-thumb-mini"
                >
                <span>{{ room.currentVideoTitle }}</span>
              </div>
            </div>
          </div>

          <div v-else class="member-waiting">
            <div class="waiting-icon">
              <svg xmlns="http://www.w3.org/2000/svg" width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="12" cy="12" r="10"/>
                <polyline points="12 6 12 12 16 14"/>
              </svg>
            </div>
            <h3>Waiting for the host</h3>
            <p class="text-muted">
              When {{ room.ownerUsername }} starts a video, it will automatically open for you.
            </p>

            <div v-if="room.currentVideoId" class="current-video mt-4">
              <h4>Current Video:</h4>
              <div class="video-card-mini">
                <img
                  :src="`http://localhost:8080/${room.currentVideoThumbnail}`"
                  :alt="room.currentVideoTitle"
                  class="video-thumb-mini"
                >
                <span>{{ room.currentVideoTitle }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- Chat Panel -->
        <div class="chat-panel" :class="{ collapsed: !isChatExpanded }">
          <div class="chat-header" @click="isChatExpanded = !isChatExpanded">
            <h3>
              <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="me-2">
                <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
              </svg>
              Party Chat
            </h3>
            <button class="btn btn-sm btn-link">
              <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" :style="{ transform: isChatExpanded ? 'rotate(180deg)' : '' }">
                <polyline points="6 9 12 15 18 9"/>
              </svg>
            </button>
          </div>

          <div v-show="isChatExpanded" class="chat-body">
            <div class="chat-messages" ref="chatContainer">
              <div
                v-for="(msg, index) in messages"
                :key="index"
                class="chat-message"
                :class="{
                  'system-message': msg.type === 'system',
                  'own-message': msg.senderId === user?.id
                }"
              >
                <template v-if="msg.type === 'system'">
                  <span class="system-text">{{ msg.content }}</span>
                </template>
                <template v-else>
                  <span class="message-sender">{{ msg.senderUsername }}</span>
                  <span class="message-content">{{ msg.content }}</span>
                  <span class="message-time">{{ formatTime(msg.timestamp) }}</span>
                </template>
              </div>

              <div v-if="messages.length === 0" class="no-messages">
                <p>No messages yet. Say hello!</p>
              </div>
            </div>

            <div class="chat-input">
              <input
                v-model="newMessage"
                type="text"
                placeholder="Type a message..."
                @keyup.enter="sendChatMessage"
                :disabled="!isConnected || !isLoggedIn"
              >
              <button
                class="btn btn-primary"
                @click="sendChatMessage"
                :disabled="!isConnected || !isLoggedIn || !newMessage.trim()"
              >
                <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <line x1="22" y1="2" x2="11" y2="13"/>
                  <polygon points="22 2 15 22 11 13 2 9 22 2"/>
                </svg>
              </button>
            </div>

            <p v-if="!isLoggedIn" class="login-hint">
              <RouterLink to="/login">Log in</RouterLink> to send messages
            </p>
          </div>
        </div>
      </div>
    </div>

    <!-- Video Selector Modal -->
    <div v-if="showVideoSelector" class="modal-overlay" @click.self="showVideoSelector = false">
      <div class="video-selector-modal">
        <div class="modal-header">
          <h3>Select a Video</h3>
          <button class="btn-close" @click="showVideoSelector = false"></button>
        </div>

        <div class="modal-body">
          <div v-if="videosLoading" class="text-center p-4">
            <div class="spinner-border" role="status">
              <span class="visually-hidden">Loading...</span>
            </div>
          </div>

          <div v-else-if="availableVideos.length === 0" class="text-center p-4">
            <p class="text-muted">No videos available</p>
          </div>

          <div v-else class="video-grid">
            <div
              v-for="video in availableVideos"
              :key="video.id"
              class="video-select-card"
              @click="playSelectedVideo(video)"
            >
              <img
                :src="`http://localhost:8080/${video.thumbnailPath}`"
                :alt="video.title"
                class="video-thumb"
              >
              <div class="video-info">
                <h4>{{ video.title }}</h4>
                <p>{{ video.creator?.username || 'Unknown' }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.watch-party-container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 20px;
  min-height: calc(100vh - 80px);
}

.loading-container,
.error-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  text-align: center;
}

.error-card {
  background: var(--color-background-soft);
  border-radius: 12px;
  padding: 40px;
  text-align: center;
}

.error-icon {
  color: #dc3545;
  margin-bottom: 20px;
}

.room-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 20px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--color-border);
  margin-bottom: 20px;
}

.room-info h1 {
  margin: 0 0 8px 0;
  font-size: 1.8rem;
}

.room-description {
  color: var(--color-text-muted);
  margin-bottom: 12px;
}

.room-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.room-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.room-main {
  display: grid;
  grid-template-columns: 1fr 350px;
  gap: 20px;
}

@media (max-width: 992px) {
  .room-main {
    grid-template-columns: 1fr;
  }
}

.video-panel {
  background: var(--color-background-soft);
  border-radius: 12px;
  padding: 30px;
  min-height: 400px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
}

.owner-controls,
.member-waiting {
  max-width: 400px;
}

.waiting-icon {
  color: var(--color-text-muted);
  margin-bottom: 20px;
}

.waiting-icon svg {
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.current-video {
  background: var(--color-background);
  border-radius: 8px;
  padding: 15px;
  text-align: left;
}

.current-video h4 {
  font-size: 0.9rem;
  color: var(--color-text-muted);
  margin-bottom: 10px;
}

.video-card-mini {
  display: flex;
  align-items: center;
  gap: 12px;
}

.video-thumb-mini {
  width: 80px;
  height: 45px;
  object-fit: cover;
  border-radius: 4px;
}

/* Chat Panel */
.chat-panel {
  background: var(--color-background-soft);
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  height: 500px;
  overflow: hidden;
}

.chat-panel.collapsed {
  height: auto;
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 15px;
  border-bottom: 1px solid var(--color-border);
  cursor: pointer;
}

.chat-header h3 {
  margin: 0;
  font-size: 1rem;
  display: flex;
  align-items: center;
}

.chat-body {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 15px;
}

.chat-message {
  margin-bottom: 12px;
  padding: 8px 12px;
  background: var(--color-background);
  border-radius: 8px;
}

.chat-message.system-message {
  background: transparent;
  text-align: center;
  font-size: 0.85rem;
  color: var(--color-text-muted);
  font-style: italic;
}

.chat-message.own-message {
  background: var(--color-primary);
  color: white;
  margin-left: 20%;
}

.message-sender {
  font-weight: 600;
  font-size: 0.85rem;
  display: block;
  margin-bottom: 4px;
  color: var(--color-primary);
}

.own-message .message-sender {
  color: rgba(255, 255, 255, 0.8);
}

.message-content {
  display: block;
  word-break: break-word;
}

.message-time {
  font-size: 0.75rem;
  color: var(--color-text-muted);
  display: block;
  text-align: right;
  margin-top: 4px;
}

.own-message .message-time {
  color: rgba(255, 255, 255, 0.6);
}

.no-messages {
  text-align: center;
  color: var(--color-text-muted);
  padding: 40px;
}

.chat-input {
  display: flex;
  gap: 8px;
  padding: 15px;
  border-top: 1px solid var(--color-border);
}

.chat-input input {
  flex: 1;
  padding: 10px 15px;
  border: 1px solid var(--color-border);
  border-radius: 20px;
  background: var(--color-background);
  color: var(--color-text);
}

.chat-input input:focus {
  outline: none;
  border-color: var(--color-primary);
}

.chat-input button {
  border-radius: 50%;
  width: 40px;
  height: 40px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.login-hint {
  font-size: 0.85rem;
  text-align: center;
  padding: 10px;
  color: var(--color-text-muted);
}

/* Video Selector Modal */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 20px;
}

.video-selector-modal {
  background: var(--color-background);
  border-radius: 12px;
  max-width: 900px;
  width: 100%;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
}

.video-selector-modal .modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px;
  border-bottom: 1px solid var(--color-border);
}

.video-selector-modal .modal-header h3 {
  margin: 0;
}

.video-selector-modal .modal-body {
  overflow-y: auto;
  padding: 20px;
}

.video-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 15px;
}

.video-select-card {
  background: var(--color-background-soft);
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}

.video-select-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.video-select-card .video-thumb {
  width: 100%;
  aspect-ratio: 16/9;
  object-fit: cover;
}

.video-select-card .video-info {
  padding: 12px;
}

.video-select-card .video-info h4 {
  margin: 0 0 4px 0;
  font-size: 0.95rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.video-select-card .video-info p {
  margin: 0;
  font-size: 0.85rem;
  color: var(--color-text-muted);
}
</style>


