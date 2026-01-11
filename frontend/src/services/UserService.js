import axios from 'axios';

// ----- Configuration -----

const BASE_URL = 'http://localhost:8080';

// ----- Axios Instance -----

/**
 * Custom axios instance with interceptors for error normalization.
 * All API calls should use this instance.
 */
const api = axios.create({
  baseURL: BASE_URL
});

// ----- Response Interceptors -----

/**
 * Interceptor to normalize common error responses:
 * - 429 (Rate Limit): Adds isRateLimit and retryAfter properties
 * - 401/403 (Auth Errors): Adds isAuthError and userMessage properties
 */
api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error?.response?.status;

    // Handle rate limiting (429)
    if (status === 429) {
      error.isRateLimit = true;
      error.retryAfter = parseRetryAfter(error.response);
    }

    // Handle authentication errors (401/403/500)
    if (status === 401 || status === 403 || status === 500) {
      error.isAuthError = true;
      error.userMessage = 'Please check your password and email and try again.';
    }

    return Promise.reject(error);
  }
);

// ----- Helper Functions -----

/**
 * Parse Retry-After value from response headers or body.
 * @param {Object} response - Axios response object
 * @returns {number|null} - Retry after seconds, or null if not available
 */
function parseRetryAfter(response) {
  const headers = response?.headers || {};

  // Try Retry-After header first
  const retryFromHeader = headers['retry-after'];
  if (retryFromHeader) {
    const parsed = parseInt(retryFromHeader, 10);
    if (!isNaN(parsed)) {
      return parsed;
    }
  }

  // Fall back to response body
  const retryFromBody = response?.data?.retryAfterSeconds;
  if (retryFromBody) {
    const parsed = Number(retryFromBody);
    if (Number.isFinite(parsed)) {
      return parsed;
    }
  }

  return null;
}

// ----- API Functions -----

/**
 * Register a new user.
 * @param {Object} user - User registration data
 * @param {string} user.username - Username
 * @param {string} user.email - Email address
 * @param {string} user.password - Password
 * @param {string} [user.firstName] - First name (optional)
 * @param {string} [user.lastName] - Last name (optional)
 * @param {string} [user.address] - Address (optional)
 * @returns {Promise} - Axios response promise
 */
export function registerUser(user) {
  return api.post('/api/auth/register', user);
}

/**
 * Log in an existing user.
 * @param {Object} user - Login credentials
 * @param {string} user.email - Email address
 * @param {string} user.password - Password
 * @returns {Promise} - Axios response promise with token and user data
 */
export function loginUser(user) {
  return api.post('/api/auth/login', user);
}

/**
 * Verify user email with verification code.
 * @param {Object} verifyData - Verification data
 * @param {string} verifyData.email - User's email
 * @param {string} verifyData.verificationCode - 6-digit verification code
 * @returns {Promise} - Axios response promise
 */
export function verifyUser(verifyData) {
  return api.post('/api/auth/verify', verifyData);
}

/**
 * Resend verification code to user's email.
 * @param {string} email - User's email address
 * @returns {Promise} - Axios response promise
 */
export function resendVerificationCode(email) {
  return api.post('/api/auth/resend', email, {
    headers: { 'Content-Type': 'text/plain' }
  });
}

/**
 * Dobija javni profil korisnika.
 * Dostupno svim korisnicima (autentifikovanim i neautentifikovanim).
 * @param {string} userId - User UUID
 * @returns {Promise} - Axios response promise sa profilom korisnika
 */
export function getUserProfile(userId) {
  return api.get(`/api/users/${userId}/profile`);
}

/**
 * Dobija video objave korisnika sa paginacijom.
 * Dostupno svim korisnicima (autentifikovanim i neautentifikovanim).
 * @param {string} userId - User UUID
 * @param {number} page - Page number (0-based)
 * @param {number} size - Number of items per page
 * @returns {Promise} - Axios response promise sa paginiranom listom video objava
 */
export function getUserVideos(userId, page = 0, size = 16) {
  return api.get(`/api/users/${userId}/videos`, {
    params: { page, size }
  });
}

