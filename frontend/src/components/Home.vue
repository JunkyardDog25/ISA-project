<script setup>
import { ref, computed, onMounted, watch } from 'vue';
import { useRoute, useRouter, RouterLink } from 'vue-router';
import { useAuth } from '@/composables/useAuth.js';
import { getVideosPaginated, getDailyPopularVideos, getVideosNearby, getNearbyConfig } from '@/services/VideoService.js';

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
const trendingVideos = ref([]);
const otherVideos = ref([]);

// ----- Nearby Search State -----
const showNearby = ref(false);
const nearbyLoading = ref(false);
const nearbyError = ref(null);
const nearbyResults = ref([]);
const nearbyPage = ref(0);
const nearbyTotalPages = ref(0);
const nearbyTotalElements = ref(0);
const nearbyRadius = ref(5); // will be overwritten by server config
const nearbyUnits = ref('km'); // will be overwritten by server config
const nearbyMaxRadius = ref(100); // will be overwritten by server config
const nearbyConfigLoaded = ref(false);
const nearbyLocationStr = ref(null); // remember last used location ("lat,lon" or null for IP-based)

// Load nearby search configuration from server
async function loadNearbyConfig() {
  if (nearbyConfigLoaded.value) return;
  try {
    const resp = await getNearbyConfig();
    if (resp.data) {
      nearbyRadius.value = resp.data.defaultRadius || 5;
      nearbyUnits.value = resp.data.defaultUnits || 'km';
      nearbyMaxRadius.value = resp.data.maxRadius || 100;
      nearbyConfigLoaded.value = true;
    }
  } catch (e) {
    console.warn('Failed to load nearby config, using defaults:', e);
  }
}

// Helper to map API video object to local view model (reuse for both lists)
function mapApiVideo(v) {
  return {
    id: v.id,
    title: v.title,
    description: v.description,
    thumbnail: `http://localhost:8080/${v.thumbnailPath}`,
    channel: v.creator?.username || 'Unknown',
    creatorId: v.creator?.id || null,
    views: formatViews(v.viewCount),
    uploadedAt: formatDate(v.createdAt),
    duration: v.duration,
    tags: v.tags ? v.tags.split(',').map(t => t.trim()).filter(t => t) : []
  };
}

// ----- Fetch Videos -----

async function fetchVideos() {
  loading.value = true;
  error.value = null;

  try {
    // Fetch daily popular videos first, but only for logged-in users
    if (isLoggedIn.value) {
      try {
        const trendResp = await getDailyPopularVideos();
        const trendData = trendResp?.data || [];

        trendingVideos.value = trendData.map(dp => {
          // endpoint returns DailyPopularVideo, which contains a `video` field
          const v = dp?.video || dp;
          return {
            id: v.id,
            title: v.title,
            description: v.description,
            thumbnail: `http://localhost:8080/${v.thumbnailPath}`,
            channel: v.creator?.username || 'Unknown',
            creatorId: v.creator?.id || null,
            views: formatViews(v.viewCount),
            uploadedAt: formatDate(v.createdAt),
            duration: v.duration,
            tags: v.tags ? v.tags.split(',').map(t => t.trim()).filter(t => t) : []
          };
        });
      } catch (e) {
        console.warn('Failed to fetch daily popular videos:', e);
        trendingVideos.value = [];
      }
    } else {
      trendingVideos.value = [];
    }

    // API uses 0-based pages, URL uses 1-based
    const response = await getVideosPaginated(currentPage.value - 1, videosPerPage);
    const data = response.data;

    // Zaštita ako data.content nije definisan
    if (!data || !data.content) {
      console.error('Invalid API response:', data);
      error.value = 'Invalid response from server';
      return;
    }

    // Map API response to display format
    videos.value = data.content.map(video => ({
      id: video.id,
      title: video.title,
      description: video.description,
      thumbnail: `http://localhost:8080/${video.thumbnailPath}`,
      channel: video.creator?.username || 'Unknown',
      creatorId: video.creator?.id || null,
      views: formatViews(video.viewCount),
      uploadedAt: formatDate(video.createdAt),
      duration: video.duration,
      tags: video.tags ? video.tags.split(',').map(t => t.trim()).filter(t => t) : []
    }));

    // Exclude trending videos from the paginated list for the "Other Videos" section
    const trendingIds = new Set(trendingVideos.value.map(t => t.id));
    otherVideos.value = videos.value.filter(v => !trendingIds.has(v.id));

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

// Add browser geolocation helper (promise wrapper)
function getBrowserLocation(timeoutMs = 5000) {
  return new Promise((resolve) => {
    if (!navigator.geolocation) return resolve(null);
    let resolved = false;
    const timer = setTimeout(() => {
      if (!resolved) {
        resolved = true;
        resolve(null);
      }
    }, timeoutMs);

    navigator.geolocation.getCurrentPosition(
      (pos) => {
        if (resolved) return;
        resolved = true;
        clearTimeout(timer);
        resolve({ lat: pos.coords.latitude, lon: pos.coords.longitude });
      },
      () => {
        if (resolved) return;
        resolved = true;
        clearTimeout(timer);
        resolve(null);
      },
      { enableHighAccuracy: false, timeout: timeoutMs }
    );
  });
}

// Perform a nearby search automatically based on user's location preference from login
async function performNearbySearch({ locationStr = undefined, page = 0 } = {}) {
  nearbyLoading.value = true;
  nearbyError.value = null;

  // Use last used locationStr if undefined; explicit null means server will use stored location or IP-based
  if (locationStr === undefined) locationStr = nearbyLocationStr.value;
  // remember chosen location for subsequent pages
  nearbyLocationStr.value = locationStr;

  try {
    const resp = await getVideosNearby({ location: locationStr, radius: nearbyRadius.value, units: nearbyUnits.value, page, size: videosPerPage });
    const data = resp.data;
    if (!data || !data.content) {
      nearbyResults.value = [];
      nearbyTotalPages.value = 0;
      nearbyTotalElements.value = 0;
      return;
    }

    nearbyResults.value = data.content.map(mapApiVideo);
    nearbyTotalPages.value = data.totalPages;
    nearbyTotalElements.value = data.totalElements;
    nearbyPage.value = page;
  } catch (e) {
    nearbyError.value = e?.response?.data?.message || e?.message || 'Failed to load nearby videos';
    nearbyResults.value = [];
    nearbyTotalPages.value = 0;
    nearbyTotalElements.value = 0;
    console.error('Error fetching nearby videos:', e);
  } finally {
    nearbyLoading.value = false;
  }
}

// Initialize nearby search automatically based on user's location preference
async function initNearbySearch() {
  if (!showNearby.value) return;

  // Check if user allowed location sharing during login
  const userData = user.value;

  if (userData?.locationAllowed && userData?.latitude && userData?.longitude) {
    // User allowed location - use stored coordinates
    const locStr = `${userData.latitude},${userData.longitude}`;
    await performNearbySearch({ locationStr: locStr, page: 0 });
  } else if (userData?.locationAllowed) {
    // User allowed but no stored coords - try browser location
    const loc = await getBrowserLocation(4000);
    if (loc) {
      const locStr = `${loc.lat},${loc.lon}`;
      await performNearbySearch({ locationStr: locStr, page: 0 });
    } else {
      // Fallback to server-side (IP approximation or stored location)
      await performNearbySearch({ locationStr: null, page: 0 });
    }
  } else {
    // User didn't allow location - use server-side IP approximation
    await performNearbySearch({ locationStr: null, page: 0 });
  }
}

// Toggle nearby search and auto-fetch
async function toggleNearbySearch() {
  showNearby.value = !showNearby.value;
  if (showNearby.value) {
    // Load config from server on first toggle
    await loadNearbyConfig();
    if (nearbyResults.value.length === 0) {
      initNearbySearch();
    }
  }
}

// Re-search when radius or units change
async function onRadiusChange() {
  if (showNearby.value) {
    await performNearbySearch({ page: 0 });
  }
}

function clearNearby() {
  showNearby.value = false;
  nearbyResults.value = [];
  nearbyError.value = null;
}

// Nearby paging
async function nearbyPrev() {
  if (nearbyPage.value > 0) {
    await performNearbySearch({ page: nearbyPage.value - 1 });
  }
}
async function nearbyNext() {
  if (nearbyPage.value < nearbyTotalPages.value - 1) {
    await performNearbySearch({ page: nearbyPage.value + 1 });
  }
}

// nearby page numbers computed (1-based pages)
const nearbyPageNumbers = computed(() => {
  const pages = [];
  const total = nearbyTotalPages.value;
  const current = nearbyPage.value + 1; // convert to 1-based

  if (total <= 5) {
    for (let i = 1; i <= total; i++) pages.push(i);
  } else {
    pages.push(1);
    if (current > 3) pages.push('...');
    const start = Math.max(2, current - 1);
    const end = Math.min(total - 1, current + 1);
    for (let i = start; i <= end; i++) if (!pages.includes(i)) pages.push(i);
    if (current < total - 2) pages.push('...');
    if (!pages.includes(total)) pages.push(total);
  }
  return pages;
});

// ----- Watch for URL changes -----

watch(() => route.query.page, () => {
  fetchVideos();
  window.scrollTo({ top: 0, behavior: 'smooth' });
});

// Fetch trending when auth state changes (so login without reload works)
watch(() => isLoggedIn.value, (logged) => {
  if (logged) {
    // user just logged in: fetch videos (includes trending)
    fetchVideos();
  } else {
    // user logged out: clear trending list immediately
    trendingVideos.value = [];
    // ensure otherVideos shows all videos
    otherVideos.value = videos.value.slice();
  }
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
        <div class="header-top">
          <div class="header-text">
            <h1>
              <span v-if="isLoggedIn && user?.username">
                Welcome back, {{ user.username }}!
              </span>
              <span v-else>Discover Videos</span>
            </h1>
            <p class="subtitle">Watch the latest and greatest content</p>
          </div>

          <!-- Nearby Search Controls in Header -->
          <div class="header-controls">
            <!-- Admin/Testing Links -->
            <div class="admin-links">
              <RouterLink to="/performance" class="admin-link" title="Performance Metrics [S2]">
                📊 Performance
              </RouterLink>
              <RouterLink to="/simulation" class="admin-link" title="Simulation Testing [S3]">
                🧪 Simulation
              </RouterLink>
            </div>

            <div class="nearby-controls">
              <label class="nearby-toggle">
                <input
                  type="checkbox"
                  :checked="showNearby"
                  @change="toggleNearbySearch"
                />
                <span class="toggle-label">Nearby</span>
              </label>

              <div v-if="showNearby" class="radius-controls-header">
                <input
                  type="number"
                  min="0.1"
                  :max="nearbyMaxRadius"
                  step="0.1"
                  v-model.number="nearbyRadius"
                  @change="onRadiusChange"
                  class="radius-input-sm"
                  :title="`Search radius (max: ${nearbyMaxRadius} ${nearbyUnits})`"
                />
                <select
                  v-model="nearbyUnits"
                  @change="onRadiusChange"
                  class="units-select-sm"
                  title="Distance units"
                >
                  <option value="km">km</option>
                  <option value="m">m</option>
                  <option value="mi">mi</option>
                </select>
              </div>
            </div>
          </div>
        </div>
      </div>
    </header>

    <!-- Trending Now Section -->
    <main class="video-section">
      <!-- Only show trending to logged-in users -->
      <div v-if="isLoggedIn">
        <div class="section-header">
          <h2>Trending Now</h2>
          <div class="section-actions">
            <span class="video-count">{{ trendingVideos.length }} videos</span>
          </div>
        </div>

        <!-- Trending Carousel / Loading / Empty -->
        <div v-if="loading && trendingVideos.length === 0" class="loading-state">
          <div class="spinner"></div>
          <p>Loading trending videos...</p>
        </div>

        <div v-else-if="!loading && trendingVideos.length === 0" class="empty-state">
          <p>No trending videos found</p>
        </div>

        <div v-else class="trending-carousel" role="region" aria-label="Trending videos carousel">
          <div class="trending-carousel-inner">
            <article
              v-for="video in trendingVideos"
              :key="`trending-` + video.id"
              class="trending-card video-card"
              @click="goToVideo(video.id)"
              tabindex="0"
              @keydown.enter="goToVideo(video.id)"
            >
              <div class="thumbnail-wrapper">
                <img :src="video.thumbnail" :alt="video.title" class="thumbnail" />

                <div class="play-overlay">
                  <svg class="play-icon" viewBox="0 0 24 24" fill="currentColor">
                    <path d="M8 5v14l11-7z" />
                  </svg>
                </div>

                <!-- Tags -->
                <div v-if="video.tags && video.tags.length > 0" class="video-tags">
                  <span class="tag" v-for="(tag, idx) in video.tags.slice(0, 2)" :key="idx">{{ tag }}</span>
                  <span v-if="video.tags.length > 2" class="tag tag-more">+{{ video.tags.length - 2 }}</span>
                </div>
              </div>

              <div class="video-info">
                <h3 class="video-title">{{ video.title }}</h3>
                <div class="video-meta">
                  <RouterLink
                    v-if="video.creatorId"
                    :to="{ name: 'user-profile', params: { id: video.creatorId } }"
                    class="channel-name-link"
                    @click.stop
                  >
                    {{ video.channel }}
                  </RouterLink>
                  <span v-else class="channel-name">{{ video.channel }}</span>
                  <span class="meta-separator">•</span>
                  <span class="view-count">{{ video.views }}</span>
                  <span class="meta-separator">•</span>
                  <span class="upload-time">{{ video.uploadedAt }}</span>
                </div>
              </div>
            </article>
          </div>
        </div>
      </div>

      <!-- Other Videos Section -->
      <div class="section-header" style="margin-top: 2.5rem;">
        <h2>Other Videos</h2>
        <div style="display:flex;align-items:center;gap:12px;">
          <span class="video-count">{{ totalElements - trendingVideos.length }} videos</span>
        </div>
      </div>

      <!-- Error State for other videos -->
      <div v-if="error" class="error-state">
        <p>{{ error }}</p>
        <button @click="fetchVideos" class="retry-btn">Try Again</button>
      </div>

      <!-- Empty State for other videos -->
      <div v-else-if="!loading && otherVideos.length === 0" class="empty-state">
        <p>No other videos found</p>
      </div>

      <!-- Other Video Grid -->
      <div v-else class="video-grid">
        <article
          v-for="video in otherVideos"
          :key="video.id"
          class="video-card"
          @click="goToVideo(video.id)"
        >
          <div class="thumbnail-wrapper">
            <img :src="video.thumbnail" :alt="video.title" class="thumbnail" />
            <div class="play-overlay">
              <svg class="play-icon" viewBox="0 0 24 24" fill="currentColor">
                <path d="M8 5v14l11-7z" />
              </svg>
            </div>

            <!-- Tags -->
            <div v-if="video.tags && video.tags.length > 0" class="video-tags">
              <span class="tag" v-for="(tag, idx) in video.tags.slice(0, 2)" :key="idx">{{ tag }}</span>
              <span v-if="video.tags.length > 2" class="tag tag-more">+{{ video.tags.length - 2 }}</span>
            </div>
          </div>

          <div class="video-info">
            <h3 class="video-title">{{ video.title }}</h3>
            <div class="video-meta">
              <RouterLink
                v-if="video.creatorId"
                :to="{ name: 'user-profile', params: { id: video.creatorId } }"
                class="channel-name-link"
                @click.stop
              >
                {{ video.channel }}
              </RouterLink>
              <span v-else class="channel-name">{{ video.channel }}</span>
              <span class="meta-separator">•</span>
              <span class="view-count">{{ video.views }}</span>
              <span class="meta-separator">•</span>
              <span class="upload-time">{{ video.uploadedAt }}</span>
            </div>
          </div>
        </article>
      </div>

      <!-- Nearby Videos Section -->
      <div v-if="showNearby" class="nearby-videos-section">
        <div class="section-header">
          <h2>Nearby Videos</h2>
          <div class="section-actions">
            <span class="video-count">{{ nearbyTotalElements }} videos found</span>
          </div>
        </div>

        <!-- Nearby Video Grid / Loading / Empty -->
        <div v-if="nearbyLoading" class="loading-state">
          <div class="spinner"></div>
          <p>Loading nearby videos...</p>
        </div>

        <div v-else-if="nearbyError" class="error-state">
          <p>{{ nearbyError }}</p>
          <button @click="initNearbySearch" class="retry-btn">Try Again</button>
        </div>

        <div v-else-if="!nearbyLoading && nearbyResults.length === 0" class="empty-state">
          <p>No nearby videos found</p>
        </div>

        <div v-else class="video-grid">
          <article
            v-for="video in nearbyResults"
            :key="video.id"
            class="video-card"
            @click="goToVideo(video.id)"
          >
            <div class="thumbnail-wrapper">
              <img :src="video.thumbnail" :alt="video.title" class="thumbnail" />
              <div class="play-overlay">
                <svg class="play-icon" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M8 5v14l11-7z" />
                </svg>
              </div>

              <!-- Tags -->
              <div v-if="video.tags && video.tags.length > 0" class="video-tags">
                <span class="tag" v-for="(tag, idx) in video.tags.slice(0, 2)" :key="idx">{{ tag }}</span>
                <span v-if="video.tags.length > 2" class="tag tag-more">+{{ video.tags.length - 2 }}</span>
              </div>
            </div>

            <div class="video-info">
              <h3 class="video-title">{{ video.title }}</h3>
              <div class="video-meta">
                <RouterLink
                  v-if="video.creatorId"
                  :to="{ name: 'user-profile', params: { id: video.creatorId } }"
                  class="channel-name-link"
                  @click.stop
                >
                  {{ video.channel }}
                </RouterLink>
                <span v-else class="channel-name">{{ video.channel }}</span>
                <span class="meta-separator">•</span>
                <span class="view-count">{{ video.views }}</span>
                <span class="meta-separator">•</span>
                <span class="upload-time">{{ video.uploadedAt }}</span>
              </div>
            </div>
          </article>
        </div>

        <!-- Nearby Pagination -->
        <nav v-if="!nearbyLoading && nearbyTotalPages > 1" class="pagination" aria-label="Nearby video pagination">
          <!-- Previous Button -->
          <button
            class="page-btn prev-btn"
            :disabled="nearbyPage === 0"
            @click="nearbyPrev"
            aria-label="Previous page"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M15 18l-6-6 6-6" />
            </svg>
            <span class="btn-text">Previous</span>
          </button>

          <!-- Page Numbers -->
          <div class="page-numbers">
            <template v-for="(page, index) in nearbyPageNumbers" :key="index">
              <span v-if="page === '...'" class="page-ellipsis">...</span>
              <button
                v-else
                class="page-number"
                :class="{ active: page === nearbyPage + 1 }"
                @click="performNearbySearch({ page: page - 1 })"
                :aria-label="`Page ${page}`"
                :aria-current="page === nearbyPage + 1 ? 'page' : undefined"
              >
                {{ page }}
              </button>
            </template>
          </div>

          <!-- Next Button -->
          <button
            class="page-btn next-btn"
            :disabled="nearbyPage >= nearbyTotalPages - 1"
            @click="nearbyNext"
            aria-label="Next page"
          >
            <span class="btn-text">Next</span>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M9 18l6-6-6-6" />
            </svg>
          </button>
        </nav>

        <!-- Page Info -->
        <p v-if="!nearbyLoading && nearbyTotalPages > 1" class="page-info">
          Showing {{ nearbyPage * videosPerPage + 1 }}–{{ Math.min((nearbyPage + 1) * videosPerPage, nearbyTotalElements) }} of {{ nearbyTotalElements }} videos
        </p>
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
  padding: 2rem;
}

.header-content {
  max-width: 1400px;
  margin: 0 auto;
}

.header-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 1rem;
}

.header-text {
  flex: 1;
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

/* Header Controls (Nearby Search) */
.header-controls {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.nearby-controls {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  background: rgba(255, 255, 255, 0.15);
  padding: 0.5rem 1rem;
  border-radius: 8px;
  backdrop-filter: blur(4px);
}

.nearby-toggle {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  cursor: pointer;
  font-weight: 500;
  font-size: 0.9rem;
}

.nearby-toggle input[type="checkbox"] {
  width: 18px;
  height: 18px;
  accent-color: #fff;
  cursor: pointer;
}

.toggle-label {
  white-space: nowrap;
}

/* Admin/Testing Links */
.admin-links {
  display: flex;
  gap: 0.5rem;
  margin-right: 1rem;
}

.admin-link {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.4rem 0.8rem;
  background: rgba(255, 255, 255, 0.2);
  color: #fff;
  text-decoration: none;
  border-radius: 6px;
  font-size: 0.85rem;
  font-weight: 500;
  transition: background 0.2s;
  white-space: nowrap;
}

.admin-link:hover {
  background: rgba(255, 255, 255, 0.35);
}

.radius-controls-header {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}

.radius-input-sm {
  width: 60px;
  padding: 0.35rem 0.5rem;
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 6px;
  font-size: 0.85rem;
  background: rgba(255, 255, 255, 0.9);
  color: #333;
}

.radius-input-sm:focus {
  outline: none;
  border-color: #fff;
}

.units-select-sm {
  padding: 0.35rem 0.5rem;
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 6px;
  font-size: 0.85rem;
  background: rgba(255, 255, 255, 0.9);
  color: #333;
  cursor: pointer;
}

.units-select-sm:focus {
  outline: none;
  border-color: #fff;
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

/* Video Tags */
.video-tags {
  position: absolute;
  top: 8px;
  right: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  max-width: 70%;
  justify-content: flex-end;
  z-index: 10;
}

.tag {
  background: rgba(255, 255, 255, 0.95);
  color: #333;
  padding: 3px 8px;
  border-radius: 4px;
  font-size: 0.7rem;
  font-weight: 500;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.15);
  white-space: nowrap;
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.tag-more {
  background: #ff0000;
  color: #fff;
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

.channel-name-link {
  color: #ff0000;
  font-weight: 500;
  text-decoration: none;
  transition: opacity 0.2s ease;
}

.channel-name-link:hover {
  text-decoration: underline;
  opacity: 0.8;
}

.meta-separator {
  color: #999;
}

/* Trending-specific styles */
.trending-carousel {
  margin-bottom: 1.5rem;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  padding: 1rem 0.25rem;
  display: flex; /* make the scroll area itself a flex container so items can be centered */
  justify-content: center;
  scroll-snap-type: x mandatory; /* enable snap so each card centers */
}

.trending-carousel-inner {
  display: flex;
  gap: 1rem;
  align-items: stretch;
  justify-content: center; /* center cards inside the inner container */
  padding: 0 1rem;
}

.trending-card {
  width: clamp(300px, 42vw, 420px);
  background: linear-gradient(180deg, #ffffff, #fffaf8);
  border-radius: 14px;
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.08);
  transition: transform 0.18s ease, box-shadow 0.18s ease;
  flex: 0 0 auto;
  scroll-snap-align: center;
}

.trending-card:hover {
  transform: translateY(-6px) scale(1.01);
  box-shadow: 0 14px 36px rgba(0, 0, 0, 0.12);
}

.trending-card .thumbnail-wrapper {
  aspect-ratio: 16 / 9;
  position: relative;
  overflow: hidden;
}

/* Make play overlay slightly larger on trending cards */
.trending-card .play-icon {
  width: 56px;
  height: 56px;
}

/* Ensure smooth horizontal scrolling visual */
.trending-carousel::-webkit-scrollbar {
  height: 10px;
}
.trending-carousel::-webkit-scrollbar-thumb {
  background: rgba(0,0,0,0.12);
  border-radius: 999px;
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

/* Nearby Videos Section */
.nearby-videos-section {
  margin-top: 2.5rem;
}

/* Responsive */
@media (max-width: 768px) {
  .home-header {
    padding: 1.5rem 1rem;
  }

  .header-top {
    flex-direction: column;
    text-align: center;
  }

  .header-text h1 {
    font-size: 1.5rem;
  }

  .header-controls {
    width: 100%;
    justify-content: center;
  }

  .nearby-controls {
    width: 100%;
    justify-content: center;
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

@media (max-width: 480px) {
  .trending-card {
    width: calc(100% - 2rem);
  }
  .trending-carousel {
    padding: 0.75rem 0.75rem;
  }
  .trending-carousel-inner {
    padding: 0 0.5rem;
    gap: 0.75rem;
  }
}
</style>
