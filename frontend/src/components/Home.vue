<script setup>
import { ref, computed, onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuth } from '@/composables/useAuth.js';
import { getVideosPaginated } from '@/services/VideoService.js';

const route = useRoute();
const router = useRouter();
const { isLoggedIn, user } = useAuth();

// ----- State -----

const loading = ref(true);
const error = ref(null);

// ----- Pagination (Server-side) -----

const videosPerPage = 16;
const totalPages = ref(0);
const totalElements = ref(0);
const isFirstPage = ref(true);
const isLastPage = ref(false);

// Get current page from URL query (1-based for URL)
const currentPage = computed(() => {
  const page = parseInt(route.query.page) || 1;
  return Math.max(1, page);
});

// ----- Video Data -----

const videos = ref([]);

// ----- Fetch Videos -----

async function fetchVideos() {
  loading.value = true;
  error.value = null;

  try {
    // API uses 0-based pages, URL uses 1-based
    const response = await getVideosPaginated(currentPage.value - 1, videosPerPage);
    const data = response.data;

    // Map API response to display format
    videos.value = data.content.map(video => ({
      id: video.id,
      title: video.title,
      description: video.description,
      thumbnail: `http://localhost:8080/${video.thumbnailPath}`,
      channel: video.creator?.username || 'Unknown',
      views: formatViews(video.viewCount),
      uploadedAt: formatDate(video.createdAt),
      duration: video.duration
    }));

    // Update pagination state from response metadata
    totalPages.value = data.totalPages;
    totalElements.value = data.totalElements;
    isFirstPage.value = data.first;
    isLastPage.value = data.last;

  } catch (e) {
    error.value = e?.response?.data?.message || e?.message || 'Failed to load videos';
    console.error('Error fetching videos:', e);
  } finally {
    loading.value = false;
  }
}

// ----- Watch for URL changes -----

watch(() => route.query.page, () => {
  fetchVideos();
  window.scrollTo({ top: 0, behavior: 'smooth' });
});

// ----- Helper Functions -----

function formatViews(count) {
  if (!count) return '0 views';
  if (count >= 1000000) {
    return `${(count / 1000000).toFixed(1)}M views`;
  }
  if (count >= 1000) {
    return `${(count / 1000).toFixed(0)}K views`;
  }
  return `${count} views`;
}

function formatDate(dateString) {
  if (!dateString) return '';

  const date = new Date(dateString);
  const now = new Date();
  const diffMs = now - date;
  const diffSecs = Math.floor(diffMs / 1000);
  const diffMins = Math.floor(diffSecs / 60);
  const diffHours = Math.floor(diffMins / 60);
  const diffDays = Math.floor(diffHours / 24);
  const diffWeeks = Math.floor(diffDays / 7);
  const diffMonths = Math.floor(diffDays / 30);
  const diffYears = Math.floor(diffDays / 365);

  if (diffYears > 0) return `${diffYears} year${diffYears > 1 ? 's' : ''} ago`;
  if (diffMonths > 0) return `${diffMonths} month${diffMonths > 1 ? 's' : ''} ago`;
  if (diffWeeks > 0) return `${diffWeeks} week${diffWeeks > 1 ? 's' : ''} ago`;
  if (diffDays > 0) return `${diffDays} day${diffDays > 1 ? 's' : ''} ago`;
  if (diffHours > 0) return `${diffHours} hour${diffHours > 1 ? 's' : ''} ago`;
  if (diffMins > 0) return `${diffMins} minute${diffMins > 1 ? 's' : ''} ago`;
  return 'Just now';
}

// ----- Lifecycle -----

onMounted(() => {
  fetchVideos();
});

// ----- Pagination Methods -----

function goToPage(page) {
  if (page >= 1 && page <= totalPages.value) {
    router.push({ query: { ...route.query, page } });
  }
}

function goToVideo(id) {
  router.push({ name: 'video', params: { id } });
}

// Generate page numbers to display
const pageNumbers = computed(() => {
  const pages = [];
  const total = totalPages.value;
  const current = currentPage.value;

  if (total <= 5) {
    for (let i = 1; i <= total; i++) {
      pages.push(i);
    }
  } else {
    pages.push(1);

    if (current > 3) {
      pages.push('...');
    }

    const start = Math.max(2, current - 1);
    const end = Math.min(total - 1, current + 1);

    for (let i = start; i <= end; i++) {
      if (!pages.includes(i)) {
        pages.push(i);
      }
    }

    if (current < total - 2) {
      pages.push('...');
    }

    if (!pages.includes(total)) {
      pages.push(total);
    }
  }

  return pages;
});
</script>

<template>
  <div class="home-container">
    <!-- Header -->
    <header class="home-header">
      <div class="header-content">
        <h1>
          <span v-if="isLoggedIn && user?.username">
            Welcome back, {{ user.username }}!
          </span>
          <span v-else>Discover Videos</span>
        </h1>
        <p class="subtitle">Watch the latest and greatest content</p>
      </div>
    </header>

    <!-- Video Grid -->
    <main class="video-section">
      <div class="section-header">
        <h2>Trending Now</h2>
        <span class="video-count">{{ totalElements }} videos</span>
      </div>

      <!-- Loading State -->
      <div v-if="loading" class="loading-state">
        <div class="spinner"></div>
        <p>Loading videos...</p>
      </div>

      <!-- Error State -->
      <div v-else-if="error" class="error-state">
        <p>{{ error }}</p>
        <button @click="fetchVideos" class="retry-btn">Try Again</button>
      </div>

      <!-- Empty State -->
      <div v-else-if="videos.length === 0" class="empty-state">
        <p>No videos found</p>
      </div>

      <!-- Video Grid -->
      <div v-else class="video-grid">
        <article
          v-for="video in videos"
          :key="video.id"
          class="video-card"
          @click="goToVideo(video.id)"
        >
          <!-- Thumbnail -->
          <div class="thumbnail-wrapper">
            <img
              :src="video.thumbnail"
              :alt="video.title"
              class="thumbnail"
            />
            <div class="play-overlay">
              <svg
                class="play-icon"
                viewBox="0 0 24 24"
                fill="currentColor"
              >
                <path d="M8 5v14l11-7z" />
              </svg>
            </div>
          </div>

          <!-- Video Info -->
          <div class="video-info">
            <h3 class="video-title">{{ video.title }}</h3>
            <div class="video-meta">
              <span class="channel-name">{{ video.channel }}</span>
              <span class="meta-separator">•</span>
              <span class="view-count">{{ video.views }}</span>
              <span class="meta-separator">•</span>
              <span class="upload-time">{{ video.uploadedAt }}</span>
            </div>
          </div>
        </article>
      </div>

      <!-- Pagination -->
      <nav v-if="!loading && !error && totalPages > 1" class="pagination" aria-label="Video pagination">
        <!-- Previous Button -->
        <button
          class="page-btn prev-btn"
          :disabled="isFirstPage"
          @click="goToPage(currentPage - 1)"
          aria-label="Previous page"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M15 18l-6-6 6-6" />
          </svg>
          <span class="btn-text">Previous</span>
        </button>

        <!-- Page Numbers -->
        <div class="page-numbers">
          <template v-for="(page, index) in pageNumbers" :key="index">
            <span v-if="page === '...'" class="page-ellipsis">...</span>
            <button
              v-else
              class="page-number"
              :class="{ active: page === currentPage }"
              @click="goToPage(page)"
              :aria-label="`Page ${page}`"
              :aria-current="page === currentPage ? 'page' : undefined"
            >
              {{ page }}
            </button>
          </template>
        </div>

        <!-- Next Button -->
        <button
          class="page-btn next-btn"
          :disabled="isLastPage"
          @click="goToPage(currentPage + 1)"
          aria-label="Next page"
        >
          <span class="btn-text">Next</span>
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M9 18l6-6-6-6" />
          </svg>
        </button>
      </nav>

      <!-- Page Info -->
      <p v-if="!loading && !error && totalPages > 1" class="page-info">
        Showing {{ (currentPage - 1) * videosPerPage + 1 }}–{{ Math.min(currentPage * videosPerPage, totalElements) }} of {{ totalElements }} videos
      </p>
    </main>
  </div>
</template>

<style scoped>
/* Container */
.home-container {
  min-height: 100vh;
  background: #f8f8f8;
}

/* Header */
.home-header {
  background: linear-gradient(135deg, #ff0000 0%, #cc0000 100%);
  color: #fff;
  padding: 3rem 2rem;
  text-align: center;
}

.header-content h1 {
  margin: 0 0 0.5rem;
  font-size: 2rem;
  font-weight: 700;
}

.subtitle {
  margin: 0;
  font-size: 1.1rem;
  opacity: 0.9;
}

/* Video Section */
.video-section {
  max-width: 1400px;
  margin: 0 auto;
  padding: 2rem;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 1.5rem;
}

.section-header h2 {
  margin: 0;
  font-size: 1.5rem;
  font-weight: 600;
  color: #111;
}

.video-count {
  color: #666;
  font-size: 0.9rem;
}

/* Loading State */
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 4rem 2rem;
  color: #666;
}

.spinner {
  width: 40px;
  height: 40px;
  border: 4px solid #eee;
  border-top-color: #ff0000;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin-bottom: 1rem;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

/* Error State */
.error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 4rem 2rem;
  color: #666;
}

.retry-btn {
  margin-top: 1rem;
  padding: 0.5rem 1.5rem;
  background: #ff0000;
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 0.9rem;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.2s;
}

.retry-btn:hover {
  background: #e60000;
}

/* Empty State */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 4rem 2rem;
  color: #666;
}

/* Video Grid */
.video-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 1.5rem;
}

/* Video Card */
.video-card {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  transition: transform 0.2s, box-shadow 0.2s;
  cursor: pointer;
  text-decoration: none;
  color: inherit;
  display: block;
}

.video-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
}

/* Thumbnail */
.thumbnail-wrapper {
  position: relative;
  aspect-ratio: 16 / 9;
  overflow: hidden;
}

.thumbnail {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.3s;
}

.video-card:hover .thumbnail {
  transform: scale(1.05);
}

.play-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.3);
  opacity: 0;
  transition: opacity 0.2s;
}

.video-card:hover .play-overlay {
  opacity: 1;
}

.play-icon {
  width: 48px;
  height: 48px;
  color: #fff;
  filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.3));
}

/* Video Info */
.video-info {
  padding: 1rem;
}

.video-title {
  margin: 0 0 0.5rem;
  font-size: 0.95rem;
  font-weight: 600;
  color: #111;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.video-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.25rem;
  font-size: 0.8rem;
  color: #666;
}

.channel-name {
  color: #ff0000;
  font-weight: 500;
}

.channel-name:hover {
  text-decoration: underline;
}

.meta-separator {
  color: #999;
}

/* Pagination */
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  margin-top: 2rem;
  padding-top: 2rem;
  border-top: 1px solid #eee;
}

.page-btn {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 1rem;
  background: #fff;
  border: 1px solid #ddd;
  border-radius: 8px;
  font-size: 0.9rem;
  font-weight: 500;
  color: #333;
  cursor: pointer;
  transition: all 0.2s;
}

.page-btn:hover:not(:disabled) {
  border-color: #ff0000;
  color: #ff0000;
}

.page-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.page-btn svg {
  width: 16px;
  height: 16px;
}

.page-numbers {
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.page-number {
  min-width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  border: 1px solid #ddd;
  border-radius: 8px;
  font-size: 0.9rem;
  font-weight: 500;
  color: #333;
  cursor: pointer;
  transition: all 0.2s;
}

.page-number:hover:not(.active) {
  border-color: #ff0000;
  color: #ff0000;
}

.page-number.active {
  background: #ff0000;
  border-color: #ff0000;
  color: #fff;
}

.page-ellipsis {
  padding: 0 0.5rem;
  color: #666;
}

.page-info {
  text-align: center;
  margin-top: 1rem;
  font-size: 0.85rem;
  color: #666;
}

/* Responsive */
@media (max-width: 768px) {
  .home-header {
    padding: 2rem 1rem;
  }

  .header-content h1 {
    font-size: 1.5rem;
  }

  .video-section {
    padding: 1rem;
  }

  .video-grid {
    grid-template-columns: 1fr;
  }

  .pagination {
    flex-wrap: wrap;
    gap: 0.75rem;
  }

  .btn-text {
    display: none;
  }

  .page-btn {
    padding: 0.5rem 0.75rem;
  }

  .page-number {
    min-width: 36px;
    height: 36px;
    font-size: 0.85rem;
  }
}
</style>
