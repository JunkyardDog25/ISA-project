<script setup>
import { ref, onMounted } from 'vue';
import { useRouter, RouterLink } from 'vue-router';
import { useAuth } from '@/composables/useAuth.js';
import { useToast } from '@/composables/useToast.js';
import {
  createWatchPartyRoom,
  getPublicWatchParties,
  getMyWatchParties,
  joinWatchParty
} from '@/services/WatchPartyService.js';

const router = useRouter();
const { isLoggedIn, user } = useAuth();
const { showSuccess, showError } = useToast();

// ----- State -----

const publicRooms = ref([]);
const myRooms = ref([]);
const publicRoomsLoading = ref(true);
const myRoomsLoading = ref(false);

// Create room form
const showCreateModal = ref(false);
const newRoom = ref({
  name: '',
  description: '',
  isPublic: false
});
const creating = ref(false);

// Join room form
const joinCode = ref('');
const joining = ref(false);

// ----- Fetch Rooms -----

async function fetchPublicRooms() {
  publicRoomsLoading.value = true;
  try {
    const response = await getPublicWatchParties();
    publicRooms.value = response.data;
  } catch (e) {
    console.error('Failed to fetch public rooms:', e);
  } finally {
    publicRoomsLoading.value = false;
  }
}

async function fetchMyRooms() {
  if (!isLoggedIn.value) return;

  myRoomsLoading.value = true;
  try {
    const response = await getMyWatchParties();
    myRooms.value = response.data;
  } catch (e) {
    console.error('Failed to fetch my rooms:', e);
  } finally {
    myRoomsLoading.value = false;
  }
}

// ----- Create Room -----

async function handleCreateRoom() {
  if (!newRoom.value.name.trim()) {
    showError('Please enter a room name');
    return;
  }

  creating.value = true;

  try {
    const response = await createWatchPartyRoom(
      newRoom.value.name,
      newRoom.value.description,
      newRoom.value.isPublic
    );

    showSuccess('Watch Party created!');
    showCreateModal.value = false;

    router.push(`/watch-party/${response.data.roomCode}`);
  } catch (e) {
    console.error('Failed to create room:', e);
    showError(e?.response?.data?.error || 'Failed to create Watch Party');
  } finally {
    creating.value = false;
  }
}

function openCreateModal() {
  if (!isLoggedIn.value) {
    showError('You must be logged in to create a Watch Party');
    router.push('/login');
    return;
  }

  newRoom.value = { name: '', description: '', isPublic: false };
  showCreateModal.value = true;
}

// ----- Join Room -----

async function handleJoinRoom() {
  if (!joinCode.value.trim()) {
    showError('Please enter a room code');
    return;
  }

  joining.value = true;

  try {
    const response = await joinWatchParty(joinCode.value.trim().toUpperCase());

    if (response.data) {
      router.push(`/watch-party/${response.data.roomCode}`);
    }
  } catch (e) {
    if (e.response?.status === 404) {
      showError('Room not found. Please check the code and try again.');
    } else {
      showError(e?.response?.data?.error || 'Failed to join room');
    }
  } finally {
    joining.value = false;
  }
}

// ----- Lifecycle -----

onMounted(() => {
  fetchPublicRooms();
  if (isLoggedIn.value) {
    fetchMyRooms();
  }
});
</script>

<template>
  <div class="watch-party-page">
    <!-- Hero Section -->
    <div class="hero-section">
      <div class="hero-content">
        <div class="hero-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
            <circle cx="9" cy="7" r="4"/>
            <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
            <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
          </svg>
        </div>
        <h1>Watch Party</h1>
        <p>Watch videos together with friends in real-time, no matter where they are.</p>

        <div class="hero-actions">
          <button class="btn-create" @click="openCreateModal">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="12" y1="5" x2="12" y2="19"/>
              <line x1="5" y1="12" x2="19" y2="12"/>
            </svg>
            Create Party
          </button>
        </div>
      </div>
    </div>

    <!-- Join by Code Section -->
    <div class="container">
      <div class="join-section">
        <div class="join-card">
          <div class="join-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4"/>
              <polyline points="10 17 15 12 10 7"/>
              <line x1="15" y1="12" x2="3" y2="12"/>
            </svg>
          </div>
          <div class="join-content">
            <h3>Join a Party</h3>
            <p>Have a code? Enter it below to join your friends</p>
          </div>
          <div class="join-form">
            <input
              v-model="joinCode"
              type="text"
              placeholder="Enter code"
              @keyup.enter="handleJoinRoom"
              maxlength="8"
            >
            <button
              class="btn-join"
              @click="handleJoinRoom"
              :disabled="joining || !joinCode.trim()"
            >
              <span v-if="joining" class="loader-small"></span>
              <span v-else>Join</span>
            </button>
          </div>
        </div>
      </div>

      <!-- My Rooms (if logged in) -->
      <section v-if="isLoggedIn && myRooms.length > 0" class="rooms-section">
        <div class="section-header">
          <h2>
            <svg viewBox="0 0 24 24" fill="currentColor" width="24" height="24">
              <path d="M12 2L15.09 8.26L22 9.27L17 14.14L18.18 21.02L12 17.77L5.82 21.02L7 14.14L2 9.27L8.91 8.26L12 2Z"/>
            </svg>
            Your Parties
          </h2>
        </div>

        <div v-if="myRoomsLoading" class="loading-state">
          <div class="loader"></div>
        </div>

        <div v-else class="rooms-grid">
          <RouterLink
            v-for="room in myRooms"
            :key="room.id"
            :to="`/watch-party/${room.roomCode}`"
            class="room-card owner"
          >
            <div class="room-card-header">
              <div class="room-badges">
                <span class="badge owner-badge">
                  <svg viewBox="0 0 24 24" fill="currentColor" width="12" height="12">
                    <path d="M12 2L15.09 8.26L22 9.27L17 14.14L18.18 21.02L12 17.77L5.82 21.02L7 14.14L2 9.27L8.91 8.26L12 2Z"/>
                  </svg>
                  Your Room
                </span>
                <span v-if="room.isPublic" class="badge public-badge">Public</span>
              </div>
              <span class="room-code">{{ room.roomCode }}</span>
            </div>
            <h3>{{ room.name }}</h3>
            <p v-if="room.description" class="room-description">{{ room.description }}</p>
            <div class="room-footer">
              <span class="member-count">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
                  <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
                  <circle cx="9" cy="7" r="4"/>
                </svg>
                {{ room.memberCount }} watching
              </span>
              <span class="join-arrow">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
                  <line x1="5" y1="12" x2="19" y2="12"/>
                  <polyline points="12 5 19 12 12 19"/>
                </svg>
              </span>
            </div>
          </RouterLink>
        </div>
      </section>

      <!-- Public Rooms -->
      <section class="rooms-section">
        <div class="section-header">
          <h2>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="24" height="24">
              <circle cx="12" cy="12" r="10"/>
              <line x1="2" y1="12" x2="22" y2="12"/>
              <path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/>
            </svg>
            Public Watch Parties
          </h2>
        </div>

        <div v-if="publicRoomsLoading" class="loading-state">
          <div class="loader"></div>
        </div>

        <div v-else-if="publicRooms.length === 0" class="empty-state">
          <div class="empty-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
              <rect x="2" y="7" width="20" height="15" rx="2" ry="2"/>
              <polyline points="17 2 12 7 7 2"/>
            </svg>
          </div>
          <h3>No public parties yet</h3>
          <p>Be the first to create a public Watch Party!</p>
          <button class="btn-create-empty" @click="openCreateModal">
            Create Party
          </button>
        </div>

        <div v-else class="rooms-grid">
          <RouterLink
            v-for="room in publicRooms"
            :key="room.id"
            :to="`/watch-party/${room.roomCode}`"
            class="room-card"
          >
            <div class="room-card-header">
              <div class="room-badges">
                <span class="badge public-badge">Public</span>
              </div>
              <span class="member-count-badge">
                <span class="live-dot"></span>
                {{ room.memberCount }} watching
              </span>
            </div>
            <h3>{{ room.name }}</h3>
            <p v-if="room.description" class="room-description">{{ room.description }}</p>
            <div class="room-footer">
              <span class="host-info">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
                  <path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2"/>
                  <circle cx="12" cy="7" r="4"/>
                </svg>
                {{ room.ownerUsername }}
              </span>
              <span v-if="room.currentVideoTitle" class="now-playing">
                <svg viewBox="0 0 24 24" fill="currentColor" width="12" height="12">
                  <polygon points="5 3 19 12 5 21 5 3"/>
                </svg>
                {{ room.currentVideoTitle }}
              </span>
            </div>
          </RouterLink>
        </div>
      </section>
    </div>

    <!-- Create Room Modal -->
    <Teleport to="body">
      <div v-if="showCreateModal" class="modal-overlay" @click.self="showCreateModal = false">
        <div class="create-modal">
          <div class="modal-header">
            <h2>Create Watch Party</h2>
            <button class="close-btn" @click="showCreateModal = false">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"/>
                <line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
            </button>
          </div>

          <div class="modal-body">
            <div class="form-group">
              <label>Party Name</label>
              <input
                v-model="newRoom.name"
                type="text"
                placeholder="e.g., Movie Night with Friends"
                maxlength="100"
              >
            </div>

            <div class="form-group">
              <label>Description <span class="optional">(optional)</span></label>
              <textarea
                v-model="newRoom.description"
                rows="3"
                placeholder="What are you watching today?"
                maxlength="500"
              ></textarea>
            </div>

            <div class="form-group toggle-group">
              <div class="toggle-info">
                <label>Make it public</label>
                <span class="toggle-desc">Anyone can discover and join your party</span>
              </div>
              <label class="toggle">
                <input v-model="newRoom.isPublic" type="checkbox">
                <span class="toggle-slider"></span>
              </label>
            </div>
          </div>

          <div class="modal-footer">
            <button class="btn-cancel" @click="showCreateModal = false">Cancel</button>
            <button
              class="btn-submit"
              @click="handleCreateRoom"
              :disabled="creating || !newRoom.name.trim()"
            >
              <span v-if="creating" class="loader-small"></span>
              <span v-else>Create Party</span>
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.watch-party-page {
  min-height: calc(100vh - 70px);
  background: #0f0f0f;
  color: #fff;
}

/* Hero Section */
.hero-section {
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f0f0f 100%);
  padding: 80px 24px;
  text-align: center;
}

.hero-content {
  max-width: 600px;
  margin: 0 auto;
}

.hero-icon {
  width: 80px;
  height: 80px;
  margin: 0 auto 24px;
  background: rgba(255,0,0,0.15);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ff0000;
}

.hero-icon svg {
  width: 40px;
  height: 40px;
}

.hero-section h1 {
  font-size: 2.5rem;
  font-weight: 700;
  margin: 0 0 16px;
  background: linear-gradient(90deg, #fff 0%, #aaa 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.hero-section p {
  font-size: 1.1rem;
  color: #aaa;
  margin: 0 0 32px;
  line-height: 1.6;
}

.hero-actions {
  display: flex;
  justify-content: center;
  gap: 16px;
}

.btn-create {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 14px 32px;
  background: #ff0000;
  color: #fff;
  border: none;
  border-radius: 24px;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-create:hover {
  background: #cc0000;
  transform: translateY(-2px);
}

.btn-create svg {
  width: 20px;
  height: 20px;
}

/* Container */
.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 24px 48px;
}

/* Join Section */
.join-section {
  margin-top: -40px;
  margin-bottom: 48px;
}

.join-card {
  background: #181818;
  border: 1px solid #303030;
  border-radius: 16px;
  padding: 24px 32px;
  display: flex;
  align-items: center;
  gap: 24px;
  flex-wrap: wrap;
}

.join-icon {
  width: 56px;
  height: 56px;
  background: rgba(62,166,255,0.1);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #3ea6ff;
}

.join-icon svg {
  width: 28px;
  height: 28px;
}

.join-content {
  flex: 1;
  min-width: 200px;
}

.join-content h3 {
  margin: 0 0 4px;
  font-size: 1.1rem;
}

.join-content p {
  margin: 0;
  color: #888;
  font-size: 0.9rem;
}

.join-form {
  display: flex;
  gap: 12px;
}

.join-form input {
  width: 160px;
  padding: 12px 16px;
  background: #0f0f0f;
  border: 1px solid #303030;
  border-radius: 8px;
  color: #fff;
  font-size: 1rem;
  text-transform: uppercase;
  letter-spacing: 2px;
  text-align: center;
}

.join-form input:focus {
  outline: none;
  border-color: #3ea6ff;
}

.join-form input::placeholder {
  text-transform: none;
  letter-spacing: normal;
}

.btn-join {
  padding: 12px 24px;
  background: #3ea6ff;
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 0.95rem;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.2s;
}

.btn-join:hover:not(:disabled) {
  background: #1a8cff;
}

.btn-join:disabled {
  background: #333;
  cursor: not-allowed;
}

/* Sections */
.rooms-section {
  margin-bottom: 48px;
}

.section-header {
  margin-bottom: 24px;
}

.section-header h2 {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 1.3rem;
  font-weight: 600;
  margin: 0;
}

.section-header h2 svg {
  color: #ff0000;
}

/* Loading */
.loading-state {
  display: flex;
  justify-content: center;
  padding: 48px;
}

.loader {
  width: 40px;
  height: 40px;
  border: 3px solid rgba(255,255,255,0.1);
  border-left-color: #ff0000;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

.loader-small {
  width: 18px;
  height: 18px;
  border: 2px solid rgba(255,255,255,0.3);
  border-left-color: #fff;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  display: inline-block;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* Empty State */
.empty-state {
  text-align: center;
  padding: 64px 24px;
  background: #181818;
  border-radius: 16px;
}

.empty-icon {
  width: 80px;
  height: 80px;
  margin: 0 auto 24px;
  color: #444;
}

.empty-icon svg {
  width: 100%;
  height: 100%;
}

.empty-state h3 {
  font-size: 1.3rem;
  margin: 0 0 8px;
}

.empty-state p {
  color: #888;
  margin: 0 0 24px;
}

.btn-create-empty {
  padding: 12px 28px;
  background: #ff0000;
  color: #fff;
  border: none;
  border-radius: 20px;
  font-size: 0.95rem;
  font-weight: 500;
  cursor: pointer;
}

.btn-create-empty:hover {
  background: #cc0000;
}

/* Rooms Grid */
.rooms-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 20px;
}

.room-card {
  background: #181818;
  border: 1px solid #303030;
  border-radius: 12px;
  padding: 20px;
  text-decoration: none;
  color: inherit;
  transition: all 0.2s;
  display: flex;
  flex-direction: column;
}

.room-card:hover {
  border-color: #ff0000;
  transform: translateY(-3px);
  text-decoration: none;
}

.room-card.owner {
  border-color: rgba(255,215,0,0.3);
  background: linear-gradient(135deg, #181818 0%, rgba(255,215,0,0.05) 100%);
}

.room-card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}

.room-badges {
  display: flex;
  gap: 8px;
}

.badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 0.75rem;
  font-weight: 500;
}

.owner-badge {
  background: rgba(255,215,0,0.15);
  color: #ffd700;
}

.public-badge {
  background: rgba(62,166,255,0.15);
  color: #3ea6ff;
}

.room-code {
  font-family: monospace;
  font-size: 0.8rem;
  color: #666;
  letter-spacing: 1px;
}

.member-count-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.8rem;
  color: #0f0;
}

.live-dot {
  width: 8px;
  height: 8px;
  background: #0f0;
  border-radius: 50%;
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.room-card h3 {
  font-size: 1.1rem;
  font-weight: 600;
  margin: 0 0 8px;
  line-height: 1.3;
}

.room-description {
  color: #888;
  font-size: 0.9rem;
  margin: 0 0 16px;
  flex: 1;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.room-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 12px;
  border-top: 1px solid #303030;
}

.member-count, .host-info {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.85rem;
  color: #888;
}

.now-playing {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.8rem;
  color: #ff6b6b;
  max-width: 150px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.join-arrow {
  color: #ff0000;
  opacity: 0;
  transition: opacity 0.2s;
}

.room-card:hover .join-arrow {
  opacity: 1;
}

/* Modal */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.85);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 20px;
}

.create-modal {
  background: #181818;
  border-radius: 16px;
  width: 100%;
  max-width: 480px;
  overflow: hidden;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px;
  border-bottom: 1px solid #303030;
}

.modal-header h2 {
  margin: 0;
  font-size: 1.3rem;
  color: #fff;
}

.close-btn {
  width: 36px;
  height: 36px;
  background: rgba(255,255,255,0.1);
  border: none;
  border-radius: 50%;
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.close-btn:hover {
  background: rgba(255,255,255,0.2);
}

.close-btn svg {
  width: 20px;
  height: 20px;
}

.modal-body {
  padding: 24px;
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  margin-bottom: 8px;
  font-weight: 500;
  font-size: 0.95rem;
  color: #fff;
}

.form-group .optional {
  color: #888;
  font-weight: 400;
}

.form-group input,
.form-group textarea {
  width: 100%;
  padding: 12px 16px;
  background: #0f0f0f;
  border: 1px solid #303030;
  border-radius: 8px;
  color: #fff;
  font-size: 1rem;
  resize: none;
}

.form-group input:focus,
.form-group textarea:focus {
  outline: none;
  border-color: #ff0000;
}

.form-group input::placeholder,
.form-group textarea::placeholder {
  color: #666;
}

.toggle-group {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  background: #0f0f0f;
  border-radius: 8px;
}

.toggle-info label {
  margin-bottom: 4px;
  color: #fff;
}

.toggle-desc {
  display: block;
  font-size: 0.85rem;
  color: #aaa;
}

.toggle {
  position: relative;
  width: 48px;
  height: 26px;
}

.toggle input {
  opacity: 0;
  width: 0;
  height: 0;
}

.toggle-slider {
  position: absolute;
  cursor: pointer;
  inset: 0;
  background: #303030;
  border-radius: 13px;
  transition: background 0.2s;
}

.toggle-slider::before {
  content: '';
  position: absolute;
  height: 20px;
  width: 20px;
  left: 3px;
  bottom: 3px;
  background: #fff;
  border-radius: 50%;
  transition: transform 0.2s;
}

.toggle input:checked + .toggle-slider {
  background: #ff0000;
}

.toggle input:checked + .toggle-slider::before {
  transform: translateX(22px);
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 20px 24px;
  border-top: 1px solid #303030;
}

.btn-cancel {
  padding: 12px 24px;
  background: transparent;
  border: 1px solid #303030;
  color: #fff;
  border-radius: 8px;
  font-size: 0.95rem;
  cursor: pointer;
}

.btn-cancel:hover {
  background: rgba(255,255,255,0.05);
}

.btn-submit {
  padding: 12px 28px;
  background: #ff0000;
  border: none;
  color: #fff;
  border-radius: 8px;
  font-size: 0.95rem;
  font-weight: 500;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 140px;
}

.btn-submit:hover:not(:disabled) {
  background: #cc0000;
}

.btn-submit:disabled {
  background: #333;
  cursor: not-allowed;
}

@media (max-width: 768px) {
  .hero-section {
    padding: 48px 20px;
  }

  .hero-section h1 {
    font-size: 1.8rem;
  }

  .join-card {
    flex-direction: column;
    text-align: center;
  }

  .join-form {
    width: 100%;
    flex-direction: column;
  }

  .join-form input {
    width: 100%;
  }

  .rooms-grid {
    grid-template-columns: 1fr;
  }
}
</style>




