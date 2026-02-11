<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue';

// ----- Props -----
const props = defineProps({
  videoRef: {
    type: Object,
    default: null
  },
  isLiveMode: {
    type: Boolean,
    default: false
  }
});

// ----- State -----
const isPlaying = ref(false);
const isMuted = ref(false);
const isFullscreen = ref(false);
const currentTime = ref(0);
const duration = ref(0);
const volume = ref(1);
const showControls = ref(true);
const controlsTimeout = ref(null);
const skipIndicator = ref({ show: false, direction: '', seconds: 0 });
const skipIndicatorTimeout = ref(null);

// ----- Emits -----
const emit = defineEmits(['play', 'pause']);

// ----- Video Controls -----

function togglePlay() {
  if (!props.videoRef) return;
  if (props.videoRef.paused) {
    props.videoRef.play();
  } else {
    props.videoRef.pause();
  }
}

function handlePlay() {
  isPlaying.value = true;
  emit('play');
}

function handlePause() {
  isPlaying.value = false;
  emit('pause');
}

function handleTimeUpdate() {
  if (!props.videoRef) return;
  currentTime.value = props.videoRef.currentTime;
}

function handleLoadedMetadata() {
  if (!props.videoRef) return;
  duration.value = props.videoRef.duration;
}

function handleVolumeChange() {
  if (!props.videoRef) return;
  volume.value = props.videoRef.volume;
  isMuted.value = props.videoRef.muted;
}

function seek(event) {
  // Onemogući seekovanje u live modu
  if (props.isLiveMode) return;

  if (!props.videoRef) return;
  const progressBar = event.currentTarget;
  const rect = progressBar.getBoundingClientRect();
  const percent = (event.clientX - rect.left) / rect.width;
  props.videoRef.currentTime = percent * duration.value;
}

function toggleMute() {
  if (!props.videoRef) return;
  props.videoRef.muted = !props.videoRef.muted;
}

function setVolume(event) {
  if (!props.videoRef) return;
  const volumeBar = event.currentTarget;
  const rect = volumeBar.getBoundingClientRect();
  const percent = Math.max(0, Math.min(1, (event.clientX - rect.left) / rect.width));
  props.videoRef.volume = percent;
  props.videoRef.muted = percent === 0;
}

function toggleFullscreen() {
  const container = document.querySelector('.video-player-container');
  if (!container) return;

  if (!document.fullscreenElement) {
    container.requestFullscreen().then(() => {
      isFullscreen.value = true;
    }).catch(err => console.error(err));
  } else {
    document.exitFullscreen().then(() => {
      isFullscreen.value = false;
    }).catch(err => console.error(err));
  }
}

function handleFullscreenChange() {
  isFullscreen.value = !!document.fullscreenElement;
}

function skip(seconds) {
  // Onemogući skipovanje u live modu
  if (props.isLiveMode) return;

  if (!props.videoRef) return;
  props.videoRef.currentTime = Math.max(0, Math.min(duration.value, props.videoRef.currentTime + seconds));

  // Show skip indicator
  clearTimeout(skipIndicatorTimeout.value);
  skipIndicator.value = {
    show: true,
    direction: seconds > 0 ? 'forward' : 'backward',
    seconds: Math.abs(seconds)
  };
  skipIndicatorTimeout.value = setTimeout(() => {
    skipIndicator.value.show = false;
  }, 800);
}

function formatTime(seconds) {
  if (!seconds || isNaN(seconds)) return '0:00';
  const mins = Math.floor(seconds / 60);
  const secs = Math.floor(seconds % 60);
  return `${mins}:${secs.toString().padStart(2, '0')}`;
}

function handleMouseMove() {
  showControls.value = true;
  clearTimeout(controlsTimeout.value);
  if (isPlaying.value) {
    controlsTimeout.value = setTimeout(() => {
      showControls.value = false;
    }, 3000);
  }
}

function handleMouseLeave() {
  if (isPlaying.value) {
    controlsTimeout.value = setTimeout(() => {
      showControls.value = false;
    }, 1000);
  }
}

function handleKeydown(event) {
  if (event.target.tagName === 'TEXTAREA' || event.target.tagName === 'INPUT') return;

  switch (event.key) {
    case ' ':
    case 'k':
      event.preventDefault();
      togglePlay();
      break;
    case 'ArrowLeft':
      event.preventDefault();
      skip(-5);
      break;
    case 'ArrowRight':
      event.preventDefault();
      skip(5);
      break;
    case 'ArrowUp':
      event.preventDefault();
      if (props.videoRef) props.videoRef.volume = Math.min(1, volume.value + 0.1);
      break;
    case 'ArrowDown':
      event.preventDefault();
      if (props.videoRef) props.videoRef.volume = Math.max(0, volume.value - 0.1);
      break;
    case 'm':
      toggleMute();
      break;
    case 'f':
      toggleFullscreen();
      break;
  }
}

// ----- Setup video event listeners -----
function setupVideoListeners() {
  if (!props.videoRef) return;

  props.videoRef.addEventListener('play', handlePlay);
  props.videoRef.addEventListener('pause', handlePause);
  props.videoRef.addEventListener('timeupdate', handleTimeUpdate);
  props.videoRef.addEventListener('loadedmetadata', handleLoadedMetadata);
  props.videoRef.addEventListener('volumechange', handleVolumeChange);
}

function removeVideoListeners() {
  if (!props.videoRef) return;

  props.videoRef.removeEventListener('play', handlePlay);
  props.videoRef.removeEventListener('pause', handlePause);
  props.videoRef.removeEventListener('timeupdate', handleTimeUpdate);
  props.videoRef.removeEventListener('loadedmetadata', handleLoadedMetadata);
  props.videoRef.removeEventListener('volumechange', handleVolumeChange);
}

// Watch for videoRef changes
watch(() => props.videoRef, (newRef, oldRef) => {
  if (oldRef) removeVideoListeners();
  if (newRef) setupVideoListeners();
}, { immediate: true });

// ----- Lifecycle -----
onMounted(() => {
  document.addEventListener('keydown', handleKeydown);
  document.addEventListener('fullscreenchange', handleFullscreenChange);
});

onUnmounted(() => {
  document.removeEventListener('keydown', handleKeydown);
  document.removeEventListener('fullscreenchange', handleFullscreenChange);
  removeVideoListeners();
  clearTimeout(controlsTimeout.value);
  clearTimeout(skipIndicatorTimeout.value);
});

// ----- Expose methods for parent -----
defineExpose({
  togglePlay,
  isPlaying,
  showControls,
  handleMouseMove,
  handleMouseLeave
});
</script>

<template>
  <!-- Skip Indicator -->
  <Transition name="skip-fade">
    <div v-if="skipIndicator.show" class="skip-indicator" :class="skipIndicator.direction">
      <div class="skip-icon">
        <svg v-if="skipIndicator.direction === 'forward'" viewBox="0 0 24 24" fill="currentColor">
          <path d="M4 18l8.5-6L4 6v12zm9-12v12l8.5-6L13 6z"/>
        </svg>
        <svg v-else viewBox="0 0 24 24" fill="currentColor">
          <path d="M11 18V6l-8.5 6 8.5 6zm.5-6l8.5 6V6l-8.5 6z"/>
        </svg>
      </div>
      <span class="skip-text">{{ skipIndicator.seconds }} seconds</span>
    </div>
  </Transition>

  <!-- Play/Pause Overlay -->
  <div class="play-overlay" v-if="!isPlaying" @click="togglePlay">
    <div class="play-icon">
      <svg viewBox="0 0 24 24" fill="currentColor">
        <path d="M8 5v14l11-7z"/>
      </svg>
    </div>
  </div>

  <!-- Custom Controls -->
  <div class="video-controls" :class="{ visible: showControls || !isPlaying }">
    <!-- Progress Bar -->
    <div
      class="progress-container"
      :class="{ 'live-mode': isLiveMode }"
      @click="seek"
    >
      <div class="progress-bar">
        <div class="progress-filled" :style="{ width: `${(currentTime / duration) * 100}%` }"></div>
        <div v-if="!isLiveMode" class="progress-handle" :style="{ left: `${(currentTime / duration) * 100}%` }"></div>
      </div>
    </div>

    <!-- Controls Row -->
    <div class="controls-row">
      <!-- Left Controls -->
      <div class="controls-left">
        <!-- Play/Pause -->
        <button class="control-btn" @click="togglePlay" :title="isPlaying ? 'Pause (k)' : 'Play (k)'">
          <svg v-if="isPlaying" viewBox="0 0 24 24" fill="currentColor">
            <path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/>
          </svg>
          <svg v-else viewBox="0 0 24 24" fill="currentColor">
            <path d="M8 5v14l11-7z"/>
          </svg>
        </button>

        <!-- Skip Backward (hidden in live mode) -->
        <button v-if="!isLiveMode" class="control-btn" @click="skip(-10)" title="Rewind 10s">
          <svg viewBox="0 0 24 24" fill="currentColor">
            <path d="M11 18V6l-8.5 6 8.5 6zm.5-6l8.5 6V6l-8.5 6z"/>
          </svg>
        </button>

        <!-- Skip Forward (hidden in live mode) -->
        <button v-if="!isLiveMode" class="control-btn" @click="skip(10)" title="Forward 10s">
          <svg viewBox="0 0 24 24" fill="currentColor">
            <path d="M4 18l8.5-6L4 6v12zm9-12v12l8.5-6L13 6z"/>
          </svg>
        </button>

        <!-- Volume -->
        <div class="volume-control">
          <button class="control-btn" @click="toggleMute" :title="isMuted ? 'Unmute (m)' : 'Mute (m)'">
            <svg v-if="isMuted || volume === 0" viewBox="0 0 24 24" fill="currentColor">
              <path d="M16.5 12c0-1.77-1.02-3.29-2.5-4.03v2.21l2.45 2.45c.03-.2.05-.41.05-.63zm2.5 0c0 .94-.2 1.82-.54 2.64l1.51 1.51C20.63 14.91 21 13.5 21 12c0-4.28-2.99-7.86-7-8.77v2.06c2.89.86 5 3.54 5 6.71zM4.27 3L3 4.27 7.73 9H3v6h4l5 5v-6.73l4.25 4.25c-.67.52-1.42.93-2.25 1.18v2.06c1.38-.31 2.63-.95 3.69-1.81L19.73 21 21 19.73l-9-9L4.27 3zM12 4L9.91 6.09 12 8.18V4z"/>
            </svg>
            <svg v-else-if="volume < 0.5" viewBox="0 0 24 24" fill="currentColor">
              <path d="M18.5 12c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM5 9v6h4l5 5V4L9 9H5z"/>
            </svg>
            <svg v-else viewBox="0 0 24 24" fill="currentColor">
              <path d="M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z"/>
            </svg>
          </button>
          <div class="volume-slider-container">
            <div class="volume-slider" @click="setVolume">
              <div class="volume-filled" :style="{ width: `${isMuted ? 0 : volume * 100}%` }"></div>
            </div>
          </div>
        </div>

        <!-- Time -->
        <span class="time-display">
          {{ formatTime(currentTime) }} / {{ formatTime(duration) }}
        </span>
      </div>

      <!-- Right Controls -->
      <div class="controls-right">
        <!-- Fullscreen -->
        <button class="control-btn" @click="toggleFullscreen" :title="isFullscreen ? 'Exit fullscreen (f)' : 'Fullscreen (f)'">
          <svg v-if="isFullscreen" viewBox="0 0 24 24" fill="currentColor">
            <path d="M5 16h3v3h2v-5H5v2zm3-8H5v2h5V5H8v3zm6 11h2v-3h3v-2h-5v5zm2-11V5h-2v5h5V8h-3z"/>
          </svg>
          <svg v-else viewBox="0 0 24 24" fill="currentColor">
            <path d="M7 14H5v5h5v-2H7v-3zm-2-4h2V7h3V5H5v5zm12 7h-3v2h5v-5h-2v3zM14 5v2h3v3h2V5h-5z"/>
          </svg>
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* Skip Indicator */
.skip-indicator {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  padding: 1rem;
  background: rgba(0, 0, 0, 0.7);
  border-radius: 12px;
  pointer-events: none;
  z-index: 10;
}

.skip-indicator.forward {
  right: 15%;
}

.skip-indicator.backward {
  left: 15%;
}

.skip-icon {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.skip-icon svg {
  width: 100%;
  height: 100%;
  color: #fff;
}

.skip-text {
  color: #fff;
  font-size: 0.875rem;
  font-weight: 500;
}

/* Skip Fade Transition */
.skip-fade-enter-active {
  animation: skip-in 0.3s ease-out;
}

.skip-fade-leave-active {
  animation: skip-out 0.3s ease-in;
}

@keyframes skip-in {
  0% {
    opacity: 0;
    transform: translateY(-50%) scale(0.8);
  }
  100% {
    opacity: 1;
    transform: translateY(-50%) scale(1);
  }
}

@keyframes skip-out {
  0% {
    opacity: 1;
    transform: translateY(-50%) scale(1);
  }
  100% {
    opacity: 0;
    transform: translateY(-50%) scale(1.1);
  }
}

/* Play Overlay */
.play-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.3);
  cursor: pointer;
}

.play-icon {
  width: 80px;
  height: 80px;
  background: rgba(0, 0, 0, 0.7);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.2s, background 0.2s;
}

.play-icon svg {
  width: 40px;
  height: 40px;
  color: #fff;
  margin-left: 4px;
}

.play-overlay:hover .play-icon {
  transform: scale(1.1);
  background: rgba(204, 0, 0, 0.9);
}

/* Custom Controls */
.video-controls {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background: linear-gradient(transparent, rgba(0, 0, 0, 0.8));
  padding: 2rem 1rem 0.75rem;
  opacity: 0;
  transition: opacity 0.3s;
}

.video-controls.visible {
  opacity: 1;
}

/* Progress Bar */
.progress-container {
  width: 100%;
  height: 20px;
  cursor: pointer;
  display: flex;
  align-items: center;
  margin-bottom: 0.5rem;
}

.progress-container.live-mode {
  cursor: not-allowed;
}

.progress-bar {
  width: 100%;
  height: 4px;
  background: rgba(255, 255, 255, 0.3);
  border-radius: 2px;
  position: relative;
  transition: height 0.1s;
}

.progress-container:hover .progress-bar {
  height: 6px;
}

.progress-container.live-mode:hover .progress-bar {
  height: 4px;
}

.progress-filled {
  height: 100%;
  background: #cc0000;
  border-radius: 2px;
  position: relative;
}

.progress-container.live-mode .progress-filled {
  background: #cc0000;
}

.progress-handle {
  position: absolute;
  top: 50%;
  width: 14px;
  height: 14px;
  background: #cc0000;
  border-radius: 50%;
  transform: translate(-50%, -50%) scale(0);
  transition: transform 0.1s;
}

.progress-container:hover .progress-handle {
  transform: translate(-50%, -50%) scale(1);
}

/* Controls Row */
.controls-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.controls-left,
.controls-right {
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

/* Control Button */
.control-btn {
  background: transparent;
  border: none;
  color: #fff;
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  border-radius: 4px;
  transition: background 0.2s;
}

.control-btn:hover {
  background: rgba(255, 255, 255, 0.1);
}

.control-btn svg {
  width: 24px;
  height: 24px;
}

/* Volume Control */
.volume-control {
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.volume-slider-container {
  width: 0;
  overflow: hidden;
  transition: width 0.2s;
}

.volume-control:hover .volume-slider-container {
  width: 60px;
}

.volume-slider {
  width: 60px;
  height: 4px;
  background: rgba(255, 255, 255, 0.3);
  border-radius: 2px;
  cursor: pointer;
  position: relative;
}

.volume-filled {
  height: 100%;
  background: #fff;
  border-radius: 2px;
}

/* Time Display */
.time-display {
  color: #fff;
  font-size: 0.8rem;
  font-family: monospace;
  margin-left: 0.5rem;
  white-space: nowrap;
}
</style>

