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
 * Get paginated comments for a video.
 * Comments are sorted from newest to oldest.
 * Results are cached on the server.
 *
 * @param {string} videoId - Video UUID
 * @param {number} page - Page number (0-based, default: 0)
 * @param {number} size - Page size (default: 20, max: 100)
 * @returns {Promise} - Axios response promise with paginated comment list
 */
export function getCommentsByVideoId(videoId, page = 0, size = 20) {
  return api.get(`/api/comments/video/${videoId}`, {
    params: { page, size }
  });
}

/**
 * Get all comments for a video (legacy - no pagination).
 * @deprecated Use getCommentsByVideoId with pagination instead
 *
 * @param {string} videoId - Video UUID
 * @returns {Promise} - Axios response promise with comment list
 */
export function getAllCommentsByVideoId(videoId) {
  return api.get(`/api/comments/video/${videoId}/all`);
}

/**
 * Create a new comment for a video.
 * Only registered users can comment.
 * Limit: 60 comments per hour per user.
 *
 * @param {string} videoId - Video UUID
 * @param {Object} commentData - Comment data
 * @param {string} commentData.userId - User UUID
 * @param {string} commentData.content - Comment content
 * @returns {Promise} - Axios response promise
 */
export function createComment(videoId, commentData) {
  return api.post(`/api/comments/video/${videoId}`, commentData);
}

/**
 * Get comment limit status for a user.
 * Returns info about remaining comments allowed.
 *
 * @param {string} userId - User UUID
 * @returns {Promise} - Axios response promise with { limit, used, remaining, resetInMinutes }
 */
export function getCommentLimitStatus(userId) {
  return api.get(`/api/comments/limit/${userId}`);
}

/**
 * Get total comment count for a video.
 *
 * @param {string} videoId - Video UUID
 * @returns {Promise} - Axios response promise with count number
 */
export function getCommentCount(videoId) {
  return api.get(`/api/comments/video/${videoId}/count`);
}


