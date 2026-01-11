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

const form = ref({
  title: '',
  description: '',
  videoPath: '',
  thumbnailPath: '',
  thumbnailCompressedPath: '',
  fileSize: '',
  duration: '',
  transcoded: false,
  scheduledAt: '',
  country: ''
});

// Track which fields have been touched (blurred)
const touched = ref({
  title: false,
  videoPath: false
});

// ----- Validation -----

const errors = computed(() => ({
  title: !form.value.title ? 'Title is required.' : '',
  videoPath: !form.value.videoPath ? 'Video path is required.' : ''
}));

const isFormValid = computed(
  () => !errors.value.title && !errors.value.videoPath
);

// ----- Form Submission -----

function markAllTouched() {
  touched.value.title = true;
  touched.value.videoPath = true;
}

async function onSubmit() {
  markAllTouched();
  if (!isFormValid.value) return;

  loading.value = true;

  try {
    const payload = {
      title: form.value.title,
      description: form.value.description || null,
      videoPath: form.value.videoPath,
      thumbnailPath: form.value.thumbnailPath || null,
      thumbnailCompressedPath: form.value.thumbnailCompressedPath || null,
      fileSize: form.value.fileSize ? parseInt(form.value.fileSize) : null,
      duration: form.value.duration || null,
      transcoded: form.value.transcoded || false,
      scheduledAt: form.value.scheduledAt || null,
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

        <!-- Video Path -->
        <div class="form-group">
          <label for="videoPath">Video Path <span class="required">*</span></label>
          <input
            id="videoPath"
            v-model="form.videoPath"
            required
            placeholder="e.g., /static/videos/video1.mp4"
            :class="{ 'input-error': touched.videoPath && errors.videoPath }"
            @blur="touched.videoPath = true"
          />
          <span v-if="touched.videoPath && errors.videoPath" class="error-text">
            {{ errors.videoPath }}
          </span>
        </div>

        <!-- Thumbnail Path -->
        <div class="form-group">
          <label for="thumbnailPath">Thumbnail Path</label>
          <input
            id="thumbnailPath"
            v-model="form.thumbnailPath"
            placeholder="e.g., /static/thumbnails/thumb1.jpg"
          />
        </div>

        <!-- Thumbnail Compressed Path -->
        <div class="form-group">
          <label for="thumbnailCompressedPath">Thumbnail Compressed Path</label>
          <input
            id="thumbnailCompressedPath"
            v-model="form.thumbnailCompressedPath"
            placeholder="e.g., /static/thumbnails/thumb1_compressed.jpg"
          />
        </div>

        <!-- File Size & Duration Grid -->
        <div class="grid-2">
          <div class="form-group">
            <label for="fileSize">File Size (bytes)</label>
            <input
              id="fileSize"
              v-model="form.fileSize"
              type="number"
              placeholder="e.g., 10485760"
            />
          </div>

          <div class="form-group">
            <label for="duration">Duration (HH:MM:SS)</label>
            <input
              id="duration"
              v-model="form.duration"
              placeholder="e.g., 00:05:30"
            />
          </div>
        </div>

        <!-- Country & Scheduled At Grid -->
        <div class="grid-2">
          <div class="form-group">
            <label for="country">Country</label>
            <input
              id="country"
              v-model="form.country"
              placeholder="e.g., Serbia"
            />
          </div>

          <div class="form-group">
            <label for="scheduledAt">Scheduled At</label>
            <input
              id="scheduledAt"
              v-model="form.scheduledAt"
              type="datetime-local"
            />
          </div>
        </div>

        <!-- Transcoded -->
        <div class="form-group checkbox-group">
          <label class="checkbox-label">
            <input
              id="transcoded"
              v-model="form.transcoded"
              type="checkbox"
            />
            <span>Video is transcoded</span>
          </label>
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
