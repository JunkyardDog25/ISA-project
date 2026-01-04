import axios from 'axios';

// ----- Configuration -----

const BASE_URL = 'http://localhost:8080';

// ----- Axios Instance -----

const api = axios.create({
  baseURL: BASE_URL
});

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
