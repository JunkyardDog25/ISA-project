<script setup>
import { ref, computed, onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuth } from '@/composables/useAuth.js';
import { getUserProfile, getUserVideos } from '@/services/UserService.js';

const route = useRoute();
const router = useRouter();
const { isLoggedIn, user: currentUser } = useAuth();

// ----- State -----

const loading = ref(true);
const error = ref(null);
const profile = ref(null);

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
const videosLoading = ref(false);

// ----- Computed -----

const userId = computed(() => route.params.id);

const isOwnProfile = computed(() => {
  return isLoggedIn.value && currentUser.value?.id === userId.value;
});

// ----- Fetch Profile -----

async function fetchProfile() {
  loading.value = true;
  error.value = null;

  try {
    const response = await getUserProfile(userId.value);
    profile.value = response.data;
    await fetchVideos();
  } catch (e) {
    if (e?.response?.status === 404) {
      error.value = 'Korisnik nije pronađen';
    } else {
      error.value = e?.response?.data?.message || e?.message || 'Greška pri učitavanju profila';
    }
    console.error('Error fetching profile:', e);
  } finally {
    loading.value = false;
  }
}

async function fetchVideos() {
  videosLoading.value = true;

  try {
    // API uses 0-based pages, URL uses 1-based
    const response = await getUserVideos(userId.value, currentPage.value - 1, videosPerPage);
    const data = response.data;

    // Map API response to display format
    videos.value = data.content.map(video => ({
      id: video.id,
      title: video.title,
      description: video.description,
      thumbnail: `http://localhost:8080/${video.thumbnailPath}`,
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
    console.error('Error fetching user videos:', e);
    videos.value = [];
  } finally {
    videosLoading.value = false;
  }
}

// ----- Watch for URL changes -----

watch(() => route.params.id, () => {
  fetchProfile();
});

watch(() => route.query.page, () => {
  fetchVideos();
  window.scrollTo({ top: 0, behavior: 'smooth' });
});

// ----- Helper Functions -----

function formatViews(count) {
  if (!count) return '0 pregleda';
  if (count >= 1000000) {
    return `${(count / 1000000).toFixed(1)}M pregleda`;
  }
  if (count >= 1000) {
    return `${(count / 1000).toFixed(0)}K pregleda`;
  }
  return `${count} pregleda`;
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

  if (diffYears > 0) return `pre ${diffYears} godin${diffYears > 1 ? 'a' : 'u'}`;
  if (diffMonths > 0) return `pre ${diffMonths} mesec${diffMonths > 1 ? 'i' : ''}`;
  if (diffWeeks > 0) return `pre ${diffWeeks} nedelj${diffWeeks > 1 ? 'e' : 'u'}`;
  if (diffDays > 0) return `pre ${diffDays} dan${diffDays > 1 ? 'a' : ''}`;
  if (diffHours > 0) return `pre ${diffHours} sat${diffHours > 1 ? 'i' : ''}`;
  if (diffMins > 0) return `pre ${diffMins} minut${diffMins > 1 ? 'a' : ''}`;
  return 'Upravo sada';
}

function formatMemberSince(dateString) {
  if (!dateString) return '';
  const date = new Date(dateString);
  return date.toLocaleDateString('sr-RS', {
    year: 'numeric',
    month: 'long',
    day: 'numeric'
  });
}

// ----- Lifecycle -----

onMounted(() => {
  fetchProfile();
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

function goBack() {
  router.back();
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
  <div class="profile-page">
    <!-- Loading State -->
    <div v-if="loading" class="loading-state">
      <div class="spinner"></div>
      <p>Učitavanje profila...</p>
    </div>

    <!-- Error State -->
    <div v-else-if="error" class="error-state">
      <p>{{ error }}</p>
      <button @click="fetchProfile" class="retry-btn">Pokušaj ponovo</button>
      <button @click="goBack" class="back-btn">Nazad</button>
    </div>

    <!-- Profile Content -->
    <div v-else-if="profile" class="profile-content">
      <!-- Profile Header -->
      <header class="profile-header">
        <div class="profile-avatar">
          {{ profile.username?.charAt(0)?.toUpperCase() || '?' }}
        </div>
        <div class="profile-info">
          <h1 class="profile-username">{{ profile.username }}</h1>
          <p v-if="profile.firstName || profile.lastName" class="profile-name">
            {{ profile.firstName }} {{ profile.lastName }}
          </p>
          <p class="profile-meta">
            <span>Član od: {{ formatMemberSince(profile.createdAt) }}</span>
            <span class="meta-separator">•</span>
            <span>{{ totalElements }} video{{ totalElements !== 1 ? ' objava' : '' }}</span>
          </p>
          <p v-if="isOwnProfile" class="own-profile-badge">
            Ovo je vaš profil
          </p>
        </div>
      </header>

      <!-- Videos Section -->
      <main class="videos-section">
        <div class="section-header">
          <h2>Video objave</h2>
        </div>

        <!-- Videos Loading -->
        <div v-if="videosLoading" class="loading-state">
          <div class="spinner"></div>
          <p>Učitavanje videa...</p>
        </div>

        <!-- Empty State -->
        <div v-else-if="videos.length === 0" class="empty-state">
          <p>Ovaj korisnik još uvek nema video objava.</p>
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
                <svg class="play-icon" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M8 5v14l11-7z" />
                </svg>
              </div>
            </div>

            <!-- Video Info -->
            <div class="video-info">
              <h3 class="video-title">{{ video.title }}</h3>
              <div class="video-meta">
                <span class="view-count">{{ video.views }}</span>
                <span class="meta-separator">•</span>
                <span class="upload-time">{{ video.uploadedAt }}</span>
              </div>
            </div>
          </article>
        </div>

        <!-- Pagination -->
        <nav v-if="!videosLoading && totalPages > 1" class="pagination" aria-label="Paginacija video objava">
          <button
            class="pagination-btn prev"
            :disabled="isFirstPage"
            @click="goToPage(currentPage - 1)"
          >
            <svg viewBox="0 0 24 24" fill="currentColor">
              <path d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z"/>
            </svg>
            <span>Prethodna</span>
          </button>

          <div class="pagination-pages">
            <button
              v-for="page in pageNumbers"
              :key="page"
              class="page-btn"
              :class="{ active: page === currentPage, ellipsis: page === '...' }"
              :disabled="page === '...'"
              @click="page !== '...' && goToPage(page)"
            >
              {{ page }}
            </button>
          </div>

          <button
            class="pagination-btn next"
            :disabled="isLastPage"
            @click="goToPage(currentPage + 1)"
          >
            <span>Sledeća</span>
            <svg viewBox="0 0 24 24" fill="currentColor">
              <path d="M8.59 16.59L10 18l6-6-6-6-1.41 1.41L13.17 12z"/>
            </svg>
          </button>
        </nav>
      </main>
    </div>
  </div>
</template>

<style scoped>
.profile-page {
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
  min-height: 50vh;
  gap: 1rem;
  padding: 2rem;
}

.spinner {
  width: 48px;
  height: 48px;
  border: 4px solid #e0e0e0;
  border-top-color: #111;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.retry-btn,
.back-btn {
  padding: 0.75rem 1.5rem;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-size: 1rem;
  transition: all 0.2s ease;
}

.retry-btn {
  background: #111;
  color: white;
}

.retry-btn:hover {
  background: #333;
}

.back-btn {
  background: #e0e0e0;
  color: #111;
}

.back-btn:hover {
  background: #d0d0d0;
}

/* Profile Header */
.profile-header {
  display: flex;
  align-items: flex-start;
  gap: 1.5rem;
  padding: 2rem;
  background: white;
  border-bottom: 1px solid #e0e0e0;
}

.profile-avatar {
  width: 100px;
  height: 100px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 2.5rem;
  font-weight: 600;
  flex-shrink: 0;
}

.profile-info {
  flex: 1;
}

.profile-username {
  font-size: 1.75rem;
  font-weight: 700;
  margin: 0 0 0.25rem 0;
  color: #111;
}

.profile-name {
  font-size: 1.1rem;
  color: #555;
  margin: 0 0 0.5rem 0;
}

.profile-meta {
  font-size: 0.9rem;
  color: #666;
  margin: 0;
}

.meta-separator {
  margin: 0 0.5rem;
  color: #999;
}

.own-profile-badge {
  display: inline-block;
  margin-top: 0.75rem;
  padding: 0.25rem 0.75rem;
  background: #e8f5e9;
  color: #2e7d32;
  border-radius: 16px;
  font-size: 0.85rem;
  font-weight: 500;
}

/* Videos Section */
.videos-section {
  padding: 2rem;
}

.section-header {
  margin-bottom: 1.5rem;
}

.section-header h2 {
  font-size: 1.5rem;
  font-weight: 600;
  margin: 0;
  color: #111;
}

/* Empty State */
.empty-state {
  text-align: center;
  padding: 3rem 1rem;
  color: #666;
}

/* Video Grid */
.video-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 1.5rem;
}

.video-card {
  background: white;
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.video-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
}

.thumbnail-wrapper {
  position: relative;
  aspect-ratio: 16 / 9;
  overflow: hidden;
  background: #e0e0e0;
}

.thumbnail {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.3s ease;
}

.video-card:hover .thumbnail {
  transform: scale(1.05);
}

.play-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.video-card:hover .play-overlay {
  opacity: 1;
}

.play-icon {
  width: 64px;
  height: 64px;
  color: white;
  filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.3));
}

.video-info {
  padding: 1rem;
}

.video-title {
  font-size: 1rem;
  font-weight: 600;
  margin: 0 0 0.5rem 0;
  color: #111;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.4;
}

.video-meta {
  font-size: 0.85rem;
  color: #666;
}

/* Pagination */
.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 1rem;
  margin-top: 2rem;
  padding: 1rem 0;
}

.pagination-btn {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 1rem;
  background: white;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  cursor: pointer;
  font-size: 0.9rem;
  color: #111;
  transition: all 0.2s ease;
}

.pagination-btn:hover:not(:disabled) {
  background: #f5f5f5;
  border-color: #111;
}

.pagination-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.pagination-btn svg {
  width: 20px;
  height: 20px;
}

.pagination-pages {
  display: flex;
  gap: 0.5rem;
}

.page-btn {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: white;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  cursor: pointer;
  font-size: 0.9rem;
  color: #111;
  transition: all 0.2s ease;
}

.page-btn:hover:not(:disabled):not(.active) {
  background: #f5f5f5;
  border-color: #111;
}

.page-btn.active {
  background: #111;
  color: white;
  border-color: #111;
}

.page-btn.ellipsis {
  border: none;
  cursor: default;
}

/* Responsive */
@media (max-width: 768px) {
  .profile-header {
    flex-direction: column;
    align-items: center;
    text-align: center;
  }

  .profile-avatar {
    width: 80px;
    height: 80px;
    font-size: 2rem;
  }

  .videos-section {
    padding: 1rem;
  }

  .video-grid {
    grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
    gap: 1rem;
  }

  .pagination {
    flex-wrap: wrap;
  }
}
</style>

