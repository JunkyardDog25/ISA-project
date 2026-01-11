<script setup>
import { ref, computed } from 'vue';
import { useRouter } from 'vue-router';
import { useAuth } from '@/composables/useAuth.js';
import { useToast } from '@/composables/useToast.js';
import { createVideo } from '@/services/VideoService.js';
import ToastContainer from '@/components/common/ToastContainer.vue';

// Router & Auth
const router = useRouter();
const { isLoggedIn } = useAuth();
const { showError, showSuccess } = useToast();

// Form state
const loading = ref(false);
const MAX_VIDEO_SIZE = 200 * 1024 * 1024; // 200MB in bytes

const form = ref({
  title: '',
  description: '',
  tags: '',
  country: ''
});

// File uploads
const videoFile = ref(null);
const thumbnailFile = ref(null);

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
    // For now, we'll use file paths. In a real application, you would upload files first
    // and get their paths from the server
    const payload = {
      title: form.value.title,
      description: form.value.description || null,
      tags: form.value.tags ? form.value.tags.split(',').map(tag => tag.trim()).filter(tag => tag.length > 0).join(',') : null,
      videoPath: videoFile.value ? `/static/videos/${videoFile.value.name}` : null,
      thumbnailPath: thumbnailFile.value ? `/static/thumbnails/${thumbnailFile.value.name}` : null,
      fileSize: videoFile.value ? videoFile.value.size : null,
      country: form.value.country || null
    };

    await createVideo(payload);
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

        <!-- Country -->
        <div class="form-group">
          <label for="country">Geographic Location (Optional)</label>
          <input
            id="country"
            v-model="form.country"
            type="text"
            placeholder="e.g., Serbia"
          />
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
</style>
