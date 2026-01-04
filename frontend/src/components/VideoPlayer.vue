<script setup>
import { ref, onMounted, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getVideoById, toggleLike, getLikeStatus, incrementViewCount } from '@/services/VideoService.js';
import { useAuth } from '@/composables/useAuth.js';

const route = useRoute();
const router = useRouter();
const { isLoggedIn, user } = useAuth();

// ----- State -----

const loading = ref(true);
const error = ref(null);
const video = ref(null);
const isLiked = ref(false);
const likeCount = ref(0);
const isDescriptionExpanded = ref(false);

// ----- Computed -----

const videoId = computed(() => route.params.id);

const videoUrl = computed(() => {
  if (!video.value?.videoPath) return '';
  return `http://localhost:8080/${video.value.videoPath}`;
});

const thumbnailUrl = computed(() => {
  if (!video.value?.thumbnailPath) return '';
  return `http://localhost:8080/${video.value.thumbnailPath}`;
});

// ----- Fetch Video -----

async function fetchVideo() {
  loading.value = true;
  error.value = null;

  try {
    const response = await getVideoById(videoId.value);
    video.value = response.data;

    // Increment view count
    await incrementViews();

    // Fetch like status if user is logged in
    if (isLoggedIn.value && user.value?.id) {
      await fetchLikeStatus();
    } else {
      // Just get the like count for non-logged-in users
      likeCount.value = video.value.likeCount || 0;
    }
  } catch (e) {
    error.value = e?.response?.data?.message || e?.message || 'Failed to load video';
    console.error('Error fetching video:', e);
  } finally {
    loading.value = false;
  }
}

async function incrementViews() {
  try {
    const response = await incrementViewCount(videoId.value);
    if (response.data?.views !== undefined) {
      video.value.viewCount = response.data.views;
    }
  } catch (e) {
    console.error('Error incrementing view count:', e);
  }
}

async function fetchLikeStatus() {
  try {
    const response = await getLikeStatus(videoId.value, user.value.id);
    isLiked.value = response.data.liked;
    likeCount.value = response.data.likeCount;
  } catch (e) {
    console.error('Error fetching like status:', e);
    likeCount.value = video.value?.likeCount || 0;
  }
}

// ----- Actions -----

async function handleToggleLike() {
  if (!isLoggedIn.value || !user.value?.id) {
    // Optionally redirect to login or show a message
    console.warn('User must be logged in to like videos');
    return;
  }

  try {
    const response = await toggleLike(videoId.value, user.value.id);
    isLiked.value = response.data.liked;
    likeCount.value = response.data.likeCount;
  } catch (e) {
    console.error('Error toggling like:', e);
  }
}

function toggleDescription() {
  isDescriptionExpanded.value = !isDescriptionExpanded.value;
}

// ----- Helper Functions -----

function formatViews(count) {
  if (!count) return '0 views';
  const formatted = count.toString().replace(/\B(?=(\d{3})+(?!\d))/g, '.');
  return `${formatted} views`;
}

function formatLikes(count) {
  if (!count) return '0';
  if (count >= 1000000) {
    return `${(count / 1000000).toFixed(1)}M`;
  }
  if (count >= 1000) {
    return `${Math.floor(count / 1000)}K`;
  }
  return `${count}`;
}

function formatDate(dateString) {
  if (!dateString) return '';
  const date = new Date(dateString);
  return date.toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'long',
    day: 'numeric'
  });
}

function formatRelativeDate(dateString) {
  if (!dateString) return '';
  const date = new Date(dateString);
  const now = new Date();
  const diffMs = now - date;
  const diffMinutes = Math.floor(diffMs / (1000 * 60));
  const diffHours = Math.floor(diffMs / (1000 * 60 * 60));
  const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));

  if (diffMinutes < 1) return 'just now';
  if (diffMinutes === 1) return '1 minute ago';
  if (diffMinutes < 60) return `${diffMinutes} minutes ago`;
  if (diffHours === 1) return '1 hour ago';
  if (diffHours < 24) return `${diffHours} hours ago`;
  if (diffDays === 1) return 'yesterday';
  if (diffDays < 7) return `${diffDays} days ago`;
  if (diffDays < 30) return `${Math.floor(diffDays / 7)} weeks ago`;
  if (diffDays < 365) return `${Math.floor(diffDays / 30)} months ago`;
  return `${Math.floor(diffDays / 365)} years ago`;
}

function goBack() {
  router.back();
}

// ----- Lifecycle -----

onMounted(() => {
  fetchVideo();
});
</script>

<template>
  <div class="video-page">
    <!-- Loading State -->
    <div v-if="loading" class="loading-state">
      <div class="spinner"></div>
      <p>Loading video...</p>
    </div>

    <!-- Error State -->
    <div v-else-if="error" class="error-state">
      <p>{{ error }}</p>
      <button @click="fetchVideo" class="retry-btn">Try Again</button>
      <button @click="goBack" class="back-btn">Go Back</button>
    </div>

    <!-- Video Content -->
    <div v-else-if="video" class="video-content">
      <!-- Video Player -->
      <div class="video-player-container">
        <video
          class="video-player"
          controls
          autoplay
          :poster="thumbnailUrl"
        >
          <source :src="videoUrl" type="video/mp4" />
          Your browser does not support the video tag.
        </video>
      </div>

      <!-- Video Info Section -->
      <div class="video-info">
        <!-- Title -->
        <h1 class="video-title">{{ video.title }}</h1>

        <!-- Channel & Actions Row -->
        <div class="video-actions-row">
          <!-- Channel Info -->
          <div class="channel-section">
            <div class="channel-avatar">
              {{ video.creator?.username?.charAt(0)?.toUpperCase() || '?' }}
            </div>
            <div class="channel-details">
              <span class="channel-name">{{ video.creator?.username || 'Unknown' }}</span>
            </div>
          </div>

          <!-- Action Buttons -->
          <div class="action-buttons">
            <!-- Like Button -->
            <button
              class="action-btn like-btn"
              :class="{ active: isLiked }"
              @click="handleToggleLike"
              :disabled="!isLoggedIn"
              :title="isLoggedIn ? (isLiked ? 'Unlike' : 'Like') : 'Login to like'"
            >
              <svg viewBox="0 0 24 24" fill="currentColor">
                <path d="M1 21h4V9H1v12zm22-11c0-1.1-.9-2-2-2h-6.31l.95-4.57.03-.32c0-.41-.17-.79-.44-1.06L14.17 1 7.59 7.59C7.22 7.95 7 8.45 7 9v10c0 1.1.9 2 2 2h9c.83 0 1.54-.5 1.84-1.22l3.02-7.05c.09-.23.14-.47.14-.73v-2z"/>
              </svg>
              <span>{{ formatLikes(likeCount) }}</span>
            </button>
          </div>
        </div>

        <!-- Description Box -->
        <div class="description-box" :class="{ expanded: isDescriptionExpanded }" @click="toggleDescription">
          <div class="description-header">
            <span class="view-count">{{ formatViews(video.viewCount) }}</span>
            <span class="meta-separator">•</span>
            <span class="upload-date">{{ isDescriptionExpanded ? formatDate(video.createdAt) : formatRelativeDate(video.createdAt) }}</span>
          </div>
          <p class="description-text">{{ video.description || 'No description available.' }}</p>
          <span class="expand-btn">
            {{ isDescriptionExpanded ? 'Show less' : '...more' }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.video-page {
  min-height: 100vh;
  background: #f8f8f8;
  color: #111;
}

/* Loading & Error States */
.loading-state,
.error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  color: #666;
  gap: 1rem;
}

.spinner {
  width: 40px;
  height: 40px;
  border: 4px solid #eee;
  border-top-color: #ff0000;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.retry-btn,
.back-btn {
  padding: 0.5rem 1.5rem;
  border: none;
  border-radius: 8px;
  font-size: 0.9rem;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.2s;
}

.retry-btn {
  background: #ff0000;
  color: #fff;
}

.retry-btn:hover {
  background: #cc0000;
}

.back-btn {
  background: #e5e5e5;
  color: #111;
}

.back-btn:hover {
  background: #d4d4d4;
}

/* Video Content */
.video-content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 1rem;
}

/* Video Player */
.video-player-container {
  background: #000;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
}

.video-player {
  width: 100%;
  aspect-ratio: 16 / 9;
  display: block;
}

/* Video Info */
.video-info {
  margin-top: 1rem;
}

.video-title {
  margin: 0 0 1rem;
  font-size: 1.25rem;
  font-weight: 600;
  color: #111;
  line-height: 1.4;
}

/* Channel & Actions Row */
.video-actions-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 1rem;
  margin-bottom: 1rem;
}

/* Channel Section */
.channel-section {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.channel-avatar {
  width: 40px;
  height: 40px;
  background: linear-gradient(135deg, #ff0000 0%, #cc0000 100%);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 1rem;
  font-weight: 600;
}

.channel-details {
  display: flex;
  flex-direction: column;
}

.channel-name {
  font-size: 1rem;
  font-weight: 600;
  color: #111;
}

/* Action Buttons */
.action-buttons {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  background: #e5e5e5;
  border: none;
  border-radius: 20px;
  padding: 0.5rem 1rem;
  color: #111;
  font-size: 0.9rem;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.2s;
}

.action-btn:hover {
  background: #d4d4d4;
}

.action-btn svg {
  width: 20px;
  height: 20px;
}

.action-btn.active {
  background: #ff0000;
  color: #fff;
}

.action-btn.active:hover {
  background: #cc0000;
}

.action-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.action-btn:disabled:hover {
  background: #e5e5e5;
}

.like-btn {
  border-radius: 20px;
}

/* Description Box */
.description-box {
  background: #e5e5e5;
  border-radius: 12px;
  padding: 1rem;
  cursor: pointer;
  position: relative;
}

.description-box:hover {
  background: #d4d4d4;
}

.description-box:not(.expanded) .description-text {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.description-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.9rem;
  font-weight: 600;
  margin-bottom: 0.5rem;
}

.view-count,
.upload-date {
  color: #111;
}

.meta-separator {
  color: #666;
}

.description-text {
  margin: 0;
  font-size: 0.9rem;
  color: #333;
  line-height: 1.5;
  white-space: pre-wrap;
}

.expand-btn {
  display: inline-block;
  color: #666;
  font-size: 0.9rem;
  font-weight: 600;
  margin-top: 0.5rem;
}

.expand-btn:hover {
  color: #111;
}

/* Responsive */
@media (max-width: 768px) {
  .video-content {
    padding: 0;
  }

  .video-player-container {
    border-radius: 0;
  }

  .video-info {
    padding: 1rem;
  }

  .video-title {
    font-size: 1.1rem;
  }

  .video-actions-row {
    flex-direction: column;
    align-items: flex-start;
  }

  .action-buttons {
    width: 100%;
    overflow-x: auto;
    padding-bottom: 0.5rem;
  }

  .action-btn span {
    display: none;
  }

  .like-btn span,
  .action-btn.active span {
    display: inline;
  }
}
</style>
