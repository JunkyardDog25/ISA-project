<script setup>
import { ref, onMounted, onUnmounted, computed, watch, nextTick } from 'vue';
import { useRoute, useRouter, RouterLink } from 'vue-router';
import { useAuth } from '@/composables/useAuth.js';
import { useToast } from '@/composables/useToast.js';
import { getAllVideos, getVideoById } from '@/services/VideoService.js';
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

// Current video state - for watching within the room
const currentVideoId = ref(null);
const currentVideoTitle = ref(null);
const currentVideoPath = ref(null);
const isWatchingVideo = ref(false);

// Video selection state
const showVideoSelector = ref(false);
const availableVideos = ref([]);
const videosLoading = ref(false);
const videoSearchQuery = ref('');

// Chat state
const messages = ref([]);
const newMessage = ref('');
const chatContainer = ref(null);
const isChatExpanded = ref(true);

// ----- Computed -----

const roomCode = computed(() => route.params.roomCode?.toUpperCase());

const filteredVideos = computed(() => {
  if (!videoSearchQuery.value.trim()) {
    return availableVideos.value;
  }
  const query = videoSearchQuery.value.toLowerCase();
  return availableVideos.value.filter(v =>
    v.title?.toLowerCase().includes(query) ||
    v.creator?.username?.toLowerCase().includes(query)
  );
});

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

    if (response.data.currentVideoId) {
      currentVideoId.value = response.data.currentVideoId;
      currentVideoTitle.value = response.data.currentVideoTitle;
    }

    if (!room.value.active) {
      error.value = 'This Watch Party room is no longer active';
      return;
    }

    if (isLoggedIn.value && user.value) {
      try {
        const ownerResponse = await checkIsOwner(roomCode.value);
        isOwner.value = ownerResponse.data.isOwner;
      } catch (e) {
        isOwner.value = false;
      }
    }

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
      isLoggedIn.value && user.value ? user.value : { id: 'guest-' + Date.now(), username: 'Guest' },
      handleWatchPartyMessage,
      () => {
        isConnected.value = true;
        isConnecting.value = false;
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
      router.push('/watch-party');
      break;
  }
}

function handlePlayVideo(message) {
  if (message.videoId) {
    currentVideoId.value = message.videoId;
    currentVideoTitle.value = message.videoTitle || 'Video';

    if (room.value) {
      room.value.currentVideoId = message.videoId;
      room.value.currentVideoTitle = message.videoTitle;
      room.value.currentVideoThumbnail = message.videoThumbnail;
    }

    if (!isOwner.value) {
      showInfo(`Now playing: ${message.videoTitle || 'Video'}`);
    }

    startWatchingVideo();
  }
}

// ----- Video Watching -----

async function startWatchingVideo() {
  if (currentVideoId.value) {
    try {
      // Fetch video details to get the video path
      const response = await getVideoById(currentVideoId.value);
      const video = response.data;
      currentVideoPath.value = video.videoPath;
      currentVideoTitle.value = video.title;
      isWatchingVideo.value = true;
    } catch (e) {
      console.error('Failed to load video:', e);
      showError('Failed to load video');
    }
  }
}

function stopWatchingVideo() {
  isWatchingVideo.value = false;
}

function goToVideoPage() {
  if (currentVideoId.value) {
    window.open(`/video/${currentVideoId.value}`, '_blank');
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
  videoSearchQuery.value = '';

  if (availableVideos.value.length === 0) {
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
}

async function playSelectedVideo(video) {
  if (!isOwner.value) {
    showError('Only the room owner can play videos');
    return;
  }

  try {
    await setWatchPartyVideo(roomCode.value, video.id);

    currentVideoId.value = video.id;
    currentVideoTitle.value = video.title;
    room.value.currentVideoId = video.id;
    room.value.currentVideoTitle = video.title;
    room.value.currentVideoThumbnail = video.thumbnailPath;

    sendPlayVideoMessage(
      roomCode.value,
      video.id,
      video.title,
      video.thumbnailPath,
      user.value
    );

    showVideoSelector.value = false;
    startWatchingVideo();

    showSuccess('Video started for all party members!');
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
    sendCloseRoomMessage(roomCode.value, user.value);
    await closeWatchParty(roomCode.value);
    showSuccess('Watch Party closed');
    router.push('/watch-party');
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
    showSuccess('Room code copied!');
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

watch(() => route.params.roomCode, (newCode) => {
  if (newCode) {
    isWatchingVideo.value = false;
    currentVideoId.value = null;
    messages.value = [];
    fetchRoom();
  }
});
</script>

<template>
  <div class="watch-party-page">
    <!-- Loading State -->
    <div v-if="loading" class="loading-state">
      <div class="loader"></div>
      <p>Joining Watch Party...</p>
    </div>

    <!-- Error State -->
    <div v-else-if="error" class="error-state">
      <div class="error-content">
        <div class="error-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/>
            <line x1="12" y1="8" x2="12" y2="12"/>
            <line x1="12" y1="16" x2="12.01" y2="16"/>
          </svg>
        </div>
        <h2>{{ error }}</h2>
        <RouterLink to="/watch-party" class="btn-back">Back to Watch Parties</RouterLink>
      </div>
    </div>

    <!-- Main Room Content -->
    <div v-else-if="room" class="room-layout">
      <!-- Video/Main Area -->
      <div class="main-area">
        <!-- Video Player (when watching) -->
        <div v-if="isWatchingVideo && currentVideoId" class="video-container">
          <div class="video-header">
            <h3>{{ currentVideoTitle }}</h3>
            <div class="video-actions">
              <button class="btn-icon" @click="goToVideoPage" title="Open in new tab">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/>
                  <polyline points="15 3 21 3 21 9"/>
                  <line x1="10" y1="14" x2="21" y2="3"/>
                </svg>
              </button>
              <button class="btn-icon" @click="stopWatchingVideo" title="Back to lobby">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="18" y1="6" x2="6" y2="18"/>
                  <line x1="6" y1="6" x2="18" y2="18"/>
                </svg>
              </button>
            </div>
          </div>
          <div class="video-wrapper">
            <video
              v-if="currentVideoPath"
              :src="`http://localhost:8080/${currentVideoPath}`"
              controls
              autoplay
              class="video-player"
            ></video>
          </div>

          <div v-if="isOwner" class="video-controls-bar">
            <button class="change-video-btn" @click="openVideoSelector">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polygon points="5 3 19 12 5 21 5 3"/>
              </svg>
              Change Video
            </button>
          </div>
        </div>

        <!-- Room Info (when not watching) -->
        <div v-else class="room-info-panel">
          <div class="room-banner">
            <div class="party-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
                <circle cx="9" cy="7" r="4"/>
                <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
                <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
              </svg>
            </div>
            <h1>{{ room.name }}</h1>
            <p v-if="room.description" class="room-desc">{{ room.description }}</p>

            <div class="room-stats">
              <div class="stat">
                <span class="stat-value">{{ memberCount }}</span>
                <span class="stat-label">{{ memberCount === 1 ? 'Viewer' : 'Viewers' }}</span>
              </div>
              <div class="stat">
                <span class="stat-value">{{ room.ownerUsername }}</span>
                <span class="stat-label">Host</span>
              </div>
              <div v-if="isOwner" class="stat owner-badge">
                <svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
                  <path d="M12 2L15.09 8.26L22 9.27L17 14.14L18.18 21.02L12 17.77L5.82 21.02L7 14.14L2 9.27L8.91 8.26L12 2Z"/>
                </svg>
                <span class="stat-label">You're the Host</span>
              </div>
            </div>
          </div>

          <div class="room-actions-bar">
            <button class="action-btn" @click="copyRoomCode">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/>
                <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
              </svg>
              <span class="code-text">{{ roomCode }}</span>
            </button>
            <button class="action-btn primary" @click="copyRoomLink">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="18" cy="5" r="3"/>
                <circle cx="6" cy="12" r="3"/>
                <circle cx="18" cy="19" r="3"/>
                <line x1="8.59" y1="13.51" x2="15.42" y2="17.49"/>
                <line x1="15.41" y1="6.51" x2="8.59" y2="10.49"/>
              </svg>
              Share Invite
            </button>
            <button v-if="isOwner" class="action-btn danger" @click="handleCloseRoom">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/>
                <line x1="15" y1="9" x2="9" y2="15"/>
                <line x1="9" y1="9" x2="15" y2="15"/>
              </svg>
              End Party
            </button>
          </div>

          <!-- Current Video Card -->
          <div v-if="currentVideoId" class="current-video-section">
            <h3>Now Playing</h3>
            <div class="current-video-card" @click="startWatchingVideo">
              <div class="video-thumbnail">
                <img
                  v-if="room.currentVideoThumbnail"
                  :src="`http://localhost:8080/${room.currentVideoThumbnail}`"
                  :alt="currentVideoTitle"
                >
                <div class="play-overlay">
                  <svg viewBox="0 0 24 24" fill="currentColor">
                    <polygon points="5 3 19 12 5 21 5 3"/>
                  </svg>
                </div>
              </div>
              <div class="video-details">
                <h4>{{ currentVideoTitle }}</h4>
                <p>Click to watch with the party</p>
              </div>
            </div>
          </div>

          <!-- Owner Video Selection -->
          <div v-if="isOwner" class="owner-section">
            <h3>{{ currentVideoId ? 'Change Video' : 'Start the Party' }}</h3>
            <p>Select a video for everyone to watch together</p>
            <button class="btn-select-video" @click="openVideoSelector">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polygon points="5 3 19 12 5 21 5 3"/>
              </svg>
              {{ currentVideoId ? 'Choose Different Video' : 'Select Video' }}
            </button>
          </div>

          <!-- Waiting State for Members -->
          <div v-else-if="!currentVideoId" class="waiting-section">
            <div class="waiting-animation">
              <div class="dot"></div>
              <div class="dot"></div>
              <div class="dot"></div>
            </div>
            <h3>Waiting for host to start a video</h3>
            <p>{{ room.ownerUsername }} will pick something to watch soon!</p>
          </div>
        </div>
      </div>

      <!-- Chat Sidebar -->
      <div class="chat-sidebar" :class="{ minimized: !isChatExpanded }">
        <div class="chat-header" @click="isChatExpanded = !isChatExpanded">
          <div class="chat-title">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
            </svg>
            <span>Party Chat</span>
            <span class="online-indicator">{{ memberCount }} online</span>
          </div>
          <button class="toggle-btn">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" :class="{ rotated: !isChatExpanded }">
              <polyline points="6 9 12 15 18 9"/>
            </svg>
          </button>
        </div>

        <div v-show="isChatExpanded" class="chat-content">
          <div class="messages-container" ref="chatContainer">
            <div v-if="messages.length === 0" class="empty-chat">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"/>
              </svg>
              <p>No messages yet</p>
              <span>Say hi to your party! 👋</span>
            </div>

            <div
              v-for="(msg, index) in messages"
              :key="index"
              class="message"
              :class="{
                'system': msg.type === 'system',
                'own': msg.senderId === user?.id
              }"
            >
              <template v-if="msg.type === 'system'">
                <span class="system-msg">{{ msg.content }}</span>
              </template>
              <template v-else>
                <div class="msg-header">
                  <span class="sender">{{ msg.senderUsername }}</span>
                  <span class="time">{{ formatTime(msg.timestamp) }}</span>
                </div>
                <p class="msg-text">{{ msg.content }}</p>
              </template>
            </div>
          </div>

          <div class="chat-input-area">
            <div v-if="!isLoggedIn" class="login-prompt">
              <RouterLink to="/login">Sign in</RouterLink> to chat
            </div>
            <div v-else class="input-wrapper">
              <input
                v-model="newMessage"
                type="text"
                placeholder="Send a message..."
                @keyup.enter="sendChatMessage"
                :disabled="!isConnected"
              >
              <button
                @click="sendChatMessage"
                :disabled="!isConnected || !newMessage.trim()"
                class="send-btn"
              >
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="22" y1="2" x2="11" y2="13"/>
                  <polygon points="22 2 15 22 11 13 2 9 22 2"/>
                </svg>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Video Selector Modal -->
    <Teleport to="body">
      <div v-if="showVideoSelector" class="modal-overlay" @click.self="showVideoSelector = false">
        <div class="video-modal">
          <div class="modal-header">
            <h2>Select a Video</h2>
            <button class="close-btn" @click="showVideoSelector = false">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"/>
                <line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
            </button>
          </div>

          <div class="modal-search">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="11" cy="11" r="8"/>
              <line x1="21" y1="21" x2="16.65" y2="16.65"/>
            </svg>
            <input
              v-model="videoSearchQuery"
              type="text"
              placeholder="Search videos..."
            >
          </div>

          <div class="modal-body">
            <div v-if="videosLoading" class="loading-videos">
              <div class="loader small"></div>
              <p>Loading videos...</p>
            </div>

            <div v-else-if="filteredVideos.length === 0" class="no-videos">
              <p>No videos found</p>
            </div>

            <div v-else class="videos-grid">
              <div
                v-for="video in filteredVideos"
                :key="video.id"
                class="video-card"
                @click="playSelectedVideo(video)"
              >
                <div class="thumb-wrapper">
                  <img
                    :src="`http://localhost:8080/${video.thumbnailPath}`"
                    :alt="video.title"
                  >
                </div>
                <div class="video-meta">
                  <h4>{{ video.title }}</h4>
                  <p>{{ video.creator?.username || 'Unknown' }}</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.watch-party-page {
  min-height: calc(100vh - 70px);
  background: #0f0f0f;
  color: #fff;
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  gap: 20px;
}

.loader {
  width: 48px;
  height: 48px;
  border: 4px solid rgba(255,255,255,0.1);
  border-left-color: #ff0000;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

.loader.small {
  width: 32px;
  height: 32px;
  border-width: 3px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.error-state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  padding: 20px;
}

.error-content {
  text-align: center;
  max-width: 400px;
}

.error-icon {
  width: 80px;
  height: 80px;
  margin: 0 auto 24px;
  color: #ff4444;
}

.error-content h2 {
  font-size: 1.5rem;
  margin-bottom: 24px;
  color: #fff;
}

.btn-back {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 12px 24px;
  background: #ff0000;
  color: #fff;
  border-radius: 24px;
  text-decoration: none;
  font-weight: 500;
}

.btn-back:hover {
  background: #cc0000;
  text-decoration: none;
}

.room-layout {
  display: grid;
  grid-template-columns: 1fr 350px;
  min-height: calc(100vh - 70px);
}

@media (max-width: 1024px) {
  .room-layout {
    grid-template-columns: 1fr;
  }

  .chat-sidebar {
    position: fixed;
    bottom: 0;
    left: 0;
    right: 0;
    height: auto;
    max-height: 50vh;
    z-index: 100;
  }
}

.main-area {
  padding: 24px;
  overflow-y: auto;
}

.video-container {
  background: #181818;
  border-radius: 12px;
  overflow: hidden;
}

.video-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  background: #202020;
}

.video-header h3 {
  font-size: 1.1rem;
  font-weight: 500;
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.video-actions {
  display: flex;
  gap: 8px;
}

.btn-icon {
  width: 36px;
  height: 36px;
  border: none;
  background: rgba(255,255,255,0.1);
  border-radius: 50%;
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.btn-icon:hover {
  background: rgba(255,255,255,0.2);
}

.btn-icon svg {
  width: 18px;
  height: 18px;
}

.video-wrapper {
  position: relative;
  padding-top: 56.25%;
  background: #000;
}

.video-player {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}

.video-controls-bar {
  padding: 12px 20px;
  background: #202020;
  display: flex;
  justify-content: center;
}

.change-video-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 24px;
  background: rgba(255,0,0,0.1);
  border: 1px solid #ff0000;
  color: #ff0000;
  border-radius: 20px;
  cursor: pointer;
}

.change-video-btn:hover {
  background: #ff0000;
  color: #fff;
}

.change-video-btn svg {
  width: 16px;
  height: 16px;
}

.room-info-panel {
  max-width: 800px;
  margin: 0 auto;
}

.room-banner {
  text-align: center;
  padding: 48px 24px;
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%);
  border-radius: 16px;
  margin-bottom: 24px;
}

.party-icon {
  width: 80px;
  height: 80px;
  margin: 0 auto 24px;
  background: rgba(255,0,0,0.1);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ff0000;
}

.party-icon svg {
  width: 40px;
  height: 40px;
}

.room-banner h1 {
  font-size: 2rem;
  font-weight: 600;
  margin: 0 0 12px;
}

.room-desc {
  color: #aaa;
  margin: 0 0 24px;
}

.room-stats {
  display: flex;
  justify-content: center;
  gap: 40px;
  flex-wrap: wrap;
}

.stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.stat-value {
  font-size: 1.5rem;
  font-weight: 600;
}

.stat-label {
  font-size: 0.85rem;
  color: #888;
}

.owner-badge {
  flex-direction: row;
  gap: 8px;
  background: rgba(255,215,0,0.1);
  padding: 8px 16px;
  border-radius: 20px;
  color: #ffd700;
}

.owner-badge svg {
  width: 20px;
  height: 20px;
}

.room-actions-bar {
  display: flex;
  justify-content: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 32px;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  border: 1px solid #303030;
  background: #181818;
  color: #fff;
  border-radius: 24px;
  cursor: pointer;
}

.action-btn:hover {
  background: #252525;
}

.action-btn svg {
  width: 18px;
  height: 18px;
}

.action-btn.primary {
  background: #ff0000;
  border-color: #ff0000;
}

.action-btn.primary:hover {
  background: #cc0000;
}

.action-btn.danger {
  color: #ff4444;
  border-color: #ff4444;
}

.action-btn.danger:hover {
  background: rgba(255,68,68,0.1);
}

.code-text {
  font-family: monospace;
  font-size: 1rem;
  letter-spacing: 1px;
}

.current-video-section {
  margin-bottom: 32px;
}

.current-video-section h3 {
  font-size: 1.1rem;
  margin-bottom: 16px;
}

.current-video-card {
  display: flex;
  gap: 16px;
  padding: 16px;
  background: #181818;
  border-radius: 12px;
  cursor: pointer;
}

.current-video-card:hover {
  background: #252525;
}

.video-thumbnail {
  position: relative;
  width: 200px;
  flex-shrink: 0;
  border-radius: 8px;
  overflow: hidden;
}

.video-thumbnail img {
  width: 100%;
  aspect-ratio: 16/9;
  object-fit: cover;
}

.play-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0,0,0,0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.2s;
}

.current-video-card:hover .play-overlay {
  opacity: 1;
}

.play-overlay svg {
  width: 48px;
  height: 48px;
}

.video-details h4 {
  font-size: 1.1rem;
  margin: 0 0 8px;
}

.video-details p {
  color: #888;
  margin: 0;
}

.owner-section {
  text-align: center;
  padding: 32px;
  background: #181818;
  border-radius: 12px;
}

.owner-section h3 {
  margin: 0 0 8px;
}

.owner-section p {
  color: #888;
  margin: 0 0 24px;
}

.btn-select-video {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 14px 32px;
  background: #ff0000;
  color: #fff;
  border: none;
  border-radius: 24px;
  font-size: 1rem;
  font-weight: 500;
  cursor: pointer;
}

.btn-select-video:hover {
  background: #cc0000;
}

.btn-select-video svg {
  width: 20px;
  height: 20px;
}

.waiting-section {
  text-align: center;
  padding: 48px;
}

.waiting-animation {
  display: flex;
  justify-content: center;
  gap: 8px;
  margin-bottom: 24px;
}

.waiting-animation .dot {
  width: 12px;
  height: 12px;
  background: #ff0000;
  border-radius: 50%;
  animation: bounce 1.4s infinite ease-in-out both;
}

.waiting-animation .dot:nth-child(1) { animation-delay: -0.32s; }
.waiting-animation .dot:nth-child(2) { animation-delay: -0.16s; }

@keyframes bounce {
  0%, 80%, 100% { transform: scale(0); }
  40% { transform: scale(1); }
}

.waiting-section h3 {
  margin: 0 0 8px;
  font-size: 1.3rem;
}

.waiting-section p {
  color: #888;
  margin: 0;
}

.chat-sidebar {
  background: #181818;
  border-left: 1px solid #303030;
  display: flex;
  flex-direction: column;
  height: calc(100vh - 70px);
}

.chat-sidebar.minimized {
  height: auto;
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  background: #202020;
  cursor: pointer;
}

.chat-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 500;
}

.chat-title svg {
  width: 20px;
  height: 20px;
  color: #ff0000;
}

.online-indicator {
  font-size: 0.8rem;
  color: #0f0;
  background: rgba(0,255,0,0.1);
  padding: 2px 8px;
  border-radius: 10px;
}

.toggle-btn {
  background: none;
  border: none;
  color: #888;
  cursor: pointer;
}

.toggle-btn svg {
  width: 20px;
  height: 20px;
  transition: transform 0.2s;
}

.toggle-btn svg.rotated {
  transform: rotate(180deg);
}

.chat-content {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}

.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.empty-chat {
  text-align: center;
  padding: 40px 20px;
  color: #666;
}

.empty-chat svg {
  width: 48px;
  height: 48px;
  margin-bottom: 16px;
}

.empty-chat p {
  margin: 0 0 4px;
  color: #888;
}

.message {
  margin-bottom: 16px;
}

.message.system {
  text-align: center;
}

.system-msg {
  font-size: 0.85rem;
  color: #666;
  font-style: italic;
}

.message.own .sender {
  color: #ff6b6b;
}

.msg-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.sender {
  font-weight: 500;
  font-size: 0.9rem;
  color: #3ea6ff;
}

.time {
  font-size: 0.75rem;
  color: #666;
}

.msg-text {
  margin: 0;
  font-size: 0.95rem;
  word-break: break-word;
}

.chat-input-area {
  padding: 16px;
  border-top: 1px solid #303030;
}

.login-prompt {
  text-align: center;
  color: #888;
}

.login-prompt a {
  color: #3ea6ff;
}

.input-wrapper {
  display: flex;
  gap: 8px;
}

.input-wrapper input {
  flex: 1;
  padding: 12px 16px;
  background: #0f0f0f;
  border: 1px solid #303030;
  border-radius: 24px;
  color: #fff;
}

.input-wrapper input:focus {
  outline: none;
  border-color: #3ea6ff;
}

.input-wrapper input::placeholder {
  color: #666;
}

.send-btn {
  width: 44px;
  height: 44px;
  border: none;
  background: #ff0000;
  border-radius: 50%;
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.send-btn:hover:not(:disabled) {
  background: #cc0000;
}

.send-btn:disabled {
  background: #333;
  cursor: not-allowed;
}

.send-btn svg {
  width: 18px;
  height: 18px;
}

.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.85);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 20px;
}

.video-modal {
  background: #181818;
  border-radius: 16px;
  width: 100%;
  max-width: 900px;
  max-height: 85vh;
  display: flex;
  flex-direction: column;
}

.video-modal .modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  border-bottom: 1px solid #303030;
}

.video-modal .modal-header h2 {
  margin: 0;
  font-size: 1.3rem;
}

.close-btn {
  width: 36px;
  height: 36px;
  border: none;
  background: rgba(255,255,255,0.1);
  border-radius: 50%;
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.close-btn:hover {
  background: rgba(255,255,255,0.2);
}

.close-btn svg {
  width: 20px;
  height: 20px;
}

.modal-search {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 24px;
  border-bottom: 1px solid #303030;
}

.modal-search svg {
  width: 20px;
  height: 20px;
  color: #666;
}

.modal-search input {
  flex: 1;
  background: none;
  border: none;
  color: #fff;
  font-size: 1rem;
}

.modal-search input:focus {
  outline: none;
}

.modal-search input::placeholder {
  color: #666;
}

.modal-body {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}

.loading-videos, .no-videos {
  text-align: center;
  padding: 48px;
  color: #888;
}

.videos-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 20px;
}

.video-card {
  cursor: pointer;
  transition: transform 0.2s;
}

.video-card:hover {
  transform: translateY(-4px);
}

.thumb-wrapper {
  border-radius: 8px;
  overflow: hidden;
  margin-bottom: 12px;
}

.thumb-wrapper img {
  width: 100%;
  aspect-ratio: 16/9;
  object-fit: cover;
}

.video-meta h4 {
  margin: 0 0 4px;
  font-size: 0.95rem;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.video-meta p {
  margin: 0;
  font-size: 0.85rem;
  color: #888;
}
</style>






