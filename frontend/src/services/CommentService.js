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
 * Get all comments for a video.
 * @param {string} videoId - Video UUID
 * @returns {Promise} - Axios response promise with comment list
 */
export function getCommentsByVideoId(videoId) {
  return api.get(`/api/comments/video/${videoId}`);
}

/**
 * Create a new comment for a video.
 * @param {string} videoId - Video UUID
 * @param {Object} commentData - Comment data
 * @returns {Promise} - Axios response promise
 */
export function createComment(videoId, commentData) {
  return api.post(`/api/comments/video/${videoId}`, commentData);
}
