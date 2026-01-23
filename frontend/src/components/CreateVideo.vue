<script setup>
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useAuth } from '@/composables/useAuth.js';
import { useToast } from '@/composables/useToast.js';
import { createVideo } from '@/services/VideoService.js';
import ToastContainer from '@/components/common/ToastContainer.vue';

// Router & Auth
const router = useRouter();
const { isLoggedIn, user } = useAuth();
const { showError, showSuccess } = useToast();

// Form state
const loading = ref(false);
const MAX_VIDEO_SIZE = 200 * 1024 * 1024; // 200MB in bytes

const form = ref({
  title: '',
  description: '',
  tags: ''
});

// File uploads
const videoFile = ref(null);
const thumbnailFile = ref(null);

// Location state
const showLocationModal = ref(false);
const locationStatus = ref('pending'); // 'pending', 'allowed', 'denied'
const userLocation = ref(null); // { lat, lon } or null

// Track which fields have been touched (blurred)
const touched = ref({
  title: false,
  tags: false,
  videoFile: false,
  thumbnailFile: false
});

// ----- Validation -----

const errors = computed(() => ({
  title: !form.value.title ? 'Title is required.' : '',
  tags: !form.value.tags || form.value.tags.trim() === '' ? 'Tags are required.' : '',
  videoFile: !videoFile.value ? 'Video file is required.' :
    (!videoFile.value.name.toLowerCase().endsWith('.mp4') ? 'Video file must be in MP4 format.' :
    (videoFile.value.size > MAX_VIDEO_SIZE ? `Video file size exceeds maximum allowed size of 200MB. Current size: ${formatFileSize(videoFile.value.size)}` : '')),
  thumbnailFile: !thumbnailFile.value ? 'Thumbnail image is required.' :
    (!thumbnailFile.value.type.startsWith('image/') ? 'Thumbnail must be an image file.' : '')
}));

const isFormValid = computed(
  () => !errors.value.title && !errors.value.tags && !errors.value.videoFile && !errors.value.thumbnailFile
);

// Helper function to format file size
function formatFileSize(bytes) {
  if (bytes === 0) return '0 Bytes';
  const k = 1024;
  const sizes = ['Bytes', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i];
}

// ----- Location Handling -----

// Promise wrapper for browser geolocation with timeout
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

async function handleAllowLocation() {
  showLocationModal.value = false;
  const loc = await getBrowserLocation(5000);
  if (loc) {
    userLocation.value = loc;
    locationStatus.value = 'allowed';
    showSuccess('Location enabled for this video');
  } else {
    locationStatus.value = 'denied';
    showError('Could not get your location. Video will be created without location.');
  }
}

function handleDenyLocation() {
  showLocationModal.value = false;
  locationStatus.value = 'denied';
  userLocation.value = null;
}

// Show location modal on mount if user hasn't decided yet
onMounted(() => {
  // Check if user already allowed location during login
  if (user.value?.locationAllowed && user.value?.latitude && user.value?.longitude) {
    // Pre-fill with user's stored location
    userLocation.value = { lat: user.value.latitude, lon: user.value.longitude };
    locationStatus.value = 'allowed';
  } else {
    // Show the modal to ask for location
    showLocationModal.value = true;
  }
});

// ----- Form Submission -----

function markAllTouched() {
  touched.value.title = true;
  touched.value.tags = true;
  touched.value.videoFile = true;
  touched.value.thumbnailFile = true;
}

function handleVideoFileChange(event) {
  const file = event.target.files[0];
  if (file) {
    if (!file.name.toLowerCase().endsWith('.mp4')) {
      showError('Video file must be in MP4 format.');
      event.target.value = '';
      videoFile.value = null;
      return;
    }
    if (file.size > MAX_VIDEO_SIZE) {
      showError(`Video file size exceeds maximum allowed size of 200MB. Current size: ${formatFileSize(file.size)}`);
      event.target.value = '';
      videoFile.value = null;
      return;
    }
    videoFile.value = file;
    touched.value.videoFile = true;
  }
}

function handleThumbnailFileChange(event) {
  const file = event.target.files[0];
  if (file) {
    if (!file.type.startsWith('image/')) {
      showError('Thumbnail must be an image file.');
      event.target.value = '';
      thumbnailFile.value = null;
      return;
    }
    thumbnailFile.value = file;
    touched.value.thumbnailFile = true;
  }
}

async function onSubmit() {
  markAllTouched();
  if (!isFormValid.value) return;

  loading.value = true;

  try {
    // Create FormData for file upload
    const formData = new FormData();
    formData.append('title', form.value.title);
    if (form.value.description) {
      formData.append('description', form.value.description);
    }
    formData.append('tags', form.value.tags ? form.value.tags.split(',').map(tag => tag.trim()).filter(tag => tag.length > 0).join(',') : '');

    // Add location if available
    if (userLocation.value) {
      formData.append('latitude', userLocation.value.lat);
      formData.append('longitude', userLocation.value.lon);
    }

    formData.append('videoFile', videoFile.value);
    formData.append('thumbnailFile', thumbnailFile.value);

    await createVideo(formData);
    showSuccess('Video created successfully!');

    setTimeout(() => {
      router.push('/');
    }, 1500);
  } catch (e) {
    handleCreateError(e);
  } finally {
    loading.value = false;
  }
}

function handleCreateError(e) {
  const serverMessage = e?.response?.data?.message;
  showError(
    serverMessage ||
      e?.response?.statusText ||
      e?.message ||
      'Failed to create video. Please try again.'
  );
}
</script>

<template>
  <div class="create-video-container">
    <div class="card">
      <h2>Upload Video</h2>
      <p class="subtitle">Share your content with the world</p>

      <form @submit.prevent="onSubmit" novalidate>
        <!-- Title -->
        <div class="form-group">
          <label for="title">Title <span class="required">*</span></label>
          <input
            id="title"
            type="text"
            v-model="form.title"
            required
            placeholder="Enter video title"
            :class="{ 'input-error': touched.title && errors.title }"
            @blur="touched.title = true"
          />
          <span v-if="touched.title && errors.title" class="error-text">
            {{ errors.title }}
          </span>
        </div>

        <!-- Description -->
        <div class="form-group">
          <label for="description">Description</label>
          <textarea
            id="description"
            v-model="form.description"
            placeholder="Enter video description"
            rows="4"
          ></textarea>
        </div>

        <!-- Tags -->
        <div class="form-group">
          <label for="tags">Tags <span class="required">*</span></label>
          <input
            id="tags"
            v-model="form.tags"
            type="text"
            required
            placeholder="Enter tags separated by commas (e.g., gaming, tutorial, tech)"
            :class="{ 'input-error': touched.tags && errors.tags }"
            @blur="touched.tags = true"
          />
          <span v-if="touched.tags && errors.tags" class="error-text">
            {{ errors.tags }}
          </span>
          <small class="help-text">Separate multiple tags with commas</small>
        </div>

        <!-- Video File Upload -->
        <div class="form-group">
          <label for="videoFile">Video File (MP4, max 200MB) <span class="required">*</span></label>
          <input
            id="videoFile"
            type="file"
            accept="video/mp4"
            :class="{ 'input-error': touched.videoFile && errors.videoFile }"
            @change="handleVideoFileChange"
          />
          <span v-if="touched.videoFile && errors.videoFile" class="error-text">
            {{ errors.videoFile }}
          </span>
          <span v-if="videoFile && !errors.videoFile" class="file-info">
            Selected: {{ videoFile.name }} ({{ formatFileSize(videoFile.size) }})
          </span>
        </div>

        <!-- Thumbnail File Upload -->
        <div class="form-group">
          <label for="thumbnailFile">Thumbnail Image <span class="required">*</span></label>
          <input
            id="thumbnailFile"
            type="file"
            accept="image/*"
            :class="{ 'input-error': touched.thumbnailFile && errors.thumbnailFile }"
            @change="handleThumbnailFileChange"
          />
          <span v-if="touched.thumbnailFile && errors.thumbnailFile" class="error-text">
            {{ errors.thumbnailFile }}
          </span>
          <span v-if="thumbnailFile && !errors.thumbnailFile" class="file-info">
            Selected: {{ thumbnailFile.name }} ({{ formatFileSize(thumbnailFile.size) }})
          </span>
        </div>

        <!-- Location Status -->
        <div class="form-group location-status">
          <label>Location</label>
          <div class="location-info">
            <span v-if="locationStatus === 'allowed'" class="location-enabled">
              <svg class="location-icon" viewBox="0 0 24 24" fill="currentColor">
                <path d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z"/>
              </svg>
              Location enabled
            </span>
            <span v-else class="location-disabled">
              <svg class="location-icon" viewBox="0 0 24 24" fill="currentColor">
                <path d="M12 6.5c1.38 0 2.5 1.12 2.5 2.5 0 .74-.33 1.39-.83 1.85l3.63 3.63c.98-1.86 1.7-3.8 1.7-5.48 0-3.87-3.13-7-7-7-1.98 0-3.76.83-5.04 2.15l3.19 3.19c.46-.5 1.11-.84 1.85-.84zM3.41 2.86L2 4.27l2.62 2.62C4.23 7.59 4 8.27 4 9c0 5.25 7 13 7 13l2.07-2.42 3.49 3.49 1.41-1.41L3.41 2.86z"/>
              </svg>
              Location disabled
            </span>
            <button type="button" class="change-location-btn" @click="showLocationModal = true">
              Change
            </button>
          </div>
        </div>

        <!-- Submit button -->
        <button type="submit" :disabled="loading" class="submit-btn">
          <span v-if="!loading">Create Video</span>
          <span v-else>Creating…</span>
        </button>

        <!-- Footer -->
        <p class="footer-text">
          <router-link to="/" class="footer-link">← Back to Home</router-link>
        </p>
      </form>
    </div>

    <!-- Toast Container -->
    <ToastContainer />

    <!-- Location Permission Modal -->
    <div v-if="showLocationModal" class="location-modal-overlay">
      <div class="location-modal">
        <div class="modal-icon">
          <svg viewBox="0 0 24 24" fill="currentColor">
            <path d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z"/>
          </svg>
        </div>
        <h3>Add location to your video?</h3>
        <p>Adding your location helps viewers discover videos from their area. Your exact coordinates will be stored with the video.</p>
        <div class="modal-actions">
          <button type="button" @click="handleDenyLocation" class="btn-secondary">No thanks</button>
          <button type="button" @click="handleAllowLocation" class="btn-primary">Allow location</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* Container */
.create-video-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1rem;
  background: #f8f8f8;
}

/* Card */
.card {
  background: #fff;
  border-radius: 12px;
  padding: 2.5rem;
  max-width: 600px;
  width: 100%;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
}

/* Typography */
h2 {
  margin: 0 0 0.5rem;
  font-size: 1.5rem;
  font-weight: 600;
  color: #111;
  text-align: center;
}

.subtitle {
  color: #666;
  margin-bottom: 2rem;
  text-align: center;
  font-size: 0.95rem;
}

.required {
  color: #ff4444;
}

/* Form groups */
.form-group {
  margin-bottom: 1rem;
  text-align: left;
}

.form-group label {
  display: block;
  font-weight: 500;
  color: #333;
  margin-bottom: 0.5rem;
  font-size: 0.9rem;
}

/* Inputs */
input[type="text"],
input[type="number"],
input[type="datetime-local"],
input[type="file"],
textarea {
  width: 100%;
  padding: 0.75rem 1rem;
  border: 1px solid #ddd;
  border-radius: 8px;
  font-size: 0.95rem;
  transition: border-color 0.2s, box-shadow 0.2s;
  box-sizing: border-box;
  background: #fff;
  font-family: inherit;
}

input[type="file"] {
  cursor: pointer;
  padding: 0.5rem;
}

input[type="file"]::file-selector-button {
  padding: 0.5rem 1rem;
  margin-right: 1rem;
  border: 1px solid #ddd;
  border-radius: 6px;
  background: #f5f5f5;
  cursor: pointer;
  font-size: 0.9rem;
  transition: background 0.2s;
}

input[type="file"]::file-selector-button:hover {
  background: #e8e8e8;
}

textarea {
  resize: vertical;
  min-height: 100px;
}

input:focus,
textarea:focus {
  outline: none;
  border-color: #ff0000;
  box-shadow: 0 0 0 3px rgba(255, 0, 0, 0.1);
}

input::placeholder,
textarea::placeholder {
  color: #aaa;
}

/* Error state */
.input-error {
  border-color: #ff4444 !important;
}

.input-error:focus {
  box-shadow: 0 0 0 3px rgba(255, 68, 68, 0.1) !important;
}

.error-text {
  display: block;
  color: #ff4444;
  font-size: 0.8rem;
  margin-top: 0.35rem;
}

.help-text {
  display: block;
  color: #666;
  font-size: 0.8rem;
  margin-top: 0.35rem;
}

.file-info {
  display: block;
  color: #28a745;
  font-size: 0.85rem;
  margin-top: 0.5rem;
  font-weight: 500;
}

/* Grid */
.grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
}

/* Checkbox */
.checkbox-group {
  margin-top: 0.5rem;
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  cursor: pointer;
  font-weight: normal;
}

.checkbox-label input[type="checkbox"] {
  width: auto;
  margin: 0;
  cursor: pointer;
}

.checkbox-label span {
  user-select: none;
}

/* Submit button */
.submit-btn {
  width: 100%;
  padding: 0.875rem 1.5rem;
  margin-top: 0.5rem;
  background: #ff0000;
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s, transform 0.2s;
}

.submit-btn:hover:not(:disabled) {
  background: #e60000;
}

.submit-btn:active:not(:disabled) {
  transform: scale(0.98);
}

.submit-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

/* Footer */
.footer-text {
  text-align: center;
  margin-top: 1.5rem;
  color: #666;
  font-size: 0.9rem;
}

.footer-link {
  color: #ff0000;
  text-decoration: none;
  font-weight: 600;
}

.footer-link:hover {
  text-decoration: underline;
}

/* Responsive */
@media (max-width: 768px) {
  .card {
    padding: 1.5rem;
  }

  .grid-2 {
    grid-template-columns: 1fr;
  }
}

/* Location Status */
.location-status {
  margin-top: 0.5rem;
}

.location-info {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 0.75rem 1rem;
  background: #f8f9fa;
  border-radius: 8px;
  border: 1px solid #e9ecef;
}

.location-enabled {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: #28a745;
  font-weight: 500;
}

.location-disabled {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: #6c757d;
}

.location-icon {
  width: 18px;
  height: 18px;
}

.change-location-btn {
  margin-left: auto;
  padding: 0.4rem 0.8rem;
  background: transparent;
  border: 1px solid #ddd;
  border-radius: 6px;
  font-size: 0.85rem;
  color: #666;
  cursor: pointer;
  transition: all 0.2s;
}

.change-location-btn:hover {
  border-color: #ff0000;
  color: #ff0000;
}

/* Location Modal */
.location-modal-overlay {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1200;
}

.location-modal {
  background: #fff;
  padding: 2rem;
  border-radius: 12px;
  max-width: 420px;
  width: 90%;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.15);
  text-align: center;
}

.modal-icon {
  width: 60px;
  height: 60px;
  margin: 0 auto 1rem;
  background: #fff0f0;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.modal-icon svg {
  width: 32px;
  height: 32px;
  color: #ff0000;
}

.location-modal h3 {
  margin: 0 0 0.75rem;
  font-size: 1.25rem;
  color: #111;
}

.location-modal p {
  color: #666;
  margin-bottom: 1.5rem;
  line-height: 1.5;
  font-size: 0.95rem;
}

.modal-actions {
  display: flex;
  gap: 0.75rem;
  justify-content: center;
}

.btn-primary {
  background: #ff0000;
  color: #fff;
  padding: 0.7rem 1.25rem;
  border-radius: 8px;
  border: none;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.2s;
}

.btn-primary:hover {
  background: #e60000;
}

.btn-secondary {
  background: transparent;
  color: #333;
  padding: 0.7rem 1.25rem;
  border-radius: 8px;
  border: 1px solid #ddd;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-secondary:hover {
  border-color: #999;
}
</style>
