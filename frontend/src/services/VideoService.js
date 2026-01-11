import axios from 'axios';

// ----- Configuration -----

const BASE_URL = 'http://localhost:8080';

// ----- Storage Keys -----

const STORAGE_KEYS = {
  TOKEN: 'authToken'
};

// ----- Helper Functions -----

function getAuthToken() {
  return localStorage.getItem(STORAGE_KEYS.TOKEN) || sessionStorage.getItem(STORAGE_KEYS.TOKEN) || null;
}

// ----- Axios Instance -----

const api = axios.create({
  baseURL: BASE_URL
});

// ----- Request Interceptor -----

/**
 * Automatski dodaje Authorization header ako postoji token.
 */
api.interceptors.request.use(
  (config) => {
    const token = getAuthToken();
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// ----- API Functions -----

/**
 * Get all videos.
 * @returns {Promise} - Axios response promise with video list
 */
export function getAllVideos() {
  return api.get('/api/videos/');
}

/**
 * Get videos with pagination.
 * @param {number} page - Page number (0-based)
 * @param {number} size - Number of items per page
 * @returns {Promise} - Axios response promise with paginated video list
 */
export function getVideosPaginated(page = 0, size = 16) {
  return api.get('/api/videos/', {
    params: { page, size }
  });
}

/**
 * Get a single video by ID.
 * @param {string} id - Video UUID
 * @returns {Promise} - Axios response promise with video data
 */
export function getVideoById(id) {
  return api.get(`/api/videos/${id}`);
}

/**
 * Create a new video with file upload.
 * @param {FormData} formData - FormData containing video data and files
 * @returns {Promise} - Axios response promise
 */
export function createVideo(formData) {
  // Get token from localStorage or sessionStorage
  const token = localStorage.getItem('authToken') || sessionStorage.getItem('authToken');
  
  const config = {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  };
  
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  
  return api.post('/api/videos/create', formData, config);
}

/**
 * Toggle like on a video.
 * @param {string} videoId - Video UUID
 * @param {string} userId - User UUID
 * @returns {Promise} - Axios response promise with { liked: boolean, likeCount: number }
 */
export function toggleLike(videoId, userId) {
  return api.post(`/api/videos/${videoId}/like`, { userId });
}

/**
 * Get like status for a video.
 * @param {string} videoId - Video UUID
 * @param {string} userId - User UUID
 * @returns {Promise} - Axios response promise with { liked: boolean, likeCount: number }
 */
export function getLikeStatus(videoId, userId) {
  return api.get(`/api/videos/${videoId}/like`, {
    params: { userId }
  });
}

/**
 * Get like count for a video.
 * @param {string} videoId - Video UUID
 * @returns {Promise} - Axios response promise with like count
 */
export function getLikeCount(videoId) {
  return api.get(`/api/videos/${videoId}/likes/count`);
}

/**
 * Increment video view count.
 * @param {string} videoId - Video UUID
 * @returns {Promise} - Axios response promise
 */
export function incrementViewCount(videoId) {
  return api.put(`/api/videos/${videoId}/views`);
}
