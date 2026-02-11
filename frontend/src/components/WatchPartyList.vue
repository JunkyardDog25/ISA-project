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

const loading = ref(false);
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

    // Navigate to the new room
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
  <div class="watch-party-list-container">
    <div class="page-header">
      <div class="header-content">
        <h1>
          <svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="me-2">
            <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
            <circle cx="9" cy="7" r="4"/>
            <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
            <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
          </svg>
          Watch Party
        </h1>
        <p class="subtitle">Watch videos together with friends in real-time!</p>
      </div>

      <div class="header-actions">
        <button class="btn btn-primary btn-lg" @click="openCreateModal">
          <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="me-2">
            <line x1="12" y1="5" x2="12" y2="19"/>
            <line x1="5" y1="12" x2="19" y2="12"/>
          </svg>
          Create Watch Party
        </button>
      </div>
    </div>

    <!-- Join by Code -->
    <div class="join-section">
      <div class="join-card">
        <h3>Join a Watch Party</h3>
        <p class="text-muted">Enter the room code shared by your friend</p>
        <div class="join-form">
          <input
            v-model="joinCode"
            type="text"
            class="form-control"
            placeholder="Enter room code (e.g., ABC12345)"
            @keyup.enter="handleJoinRoom"
            maxlength="8"
          >
          <button
            class="btn btn-primary"
            @click="handleJoinRoom"
            :disabled="joining || !joinCode.trim()"
          >
            <span v-if="joining" class="spinner-border spinner-border-sm me-2"></span>
            Join
          </button>
        </div>
      </div>
    </div>

    <!-- My Rooms (if logged in) -->
    <div v-if="isLoggedIn && myRooms.length > 0" class="rooms-section">
      <h2>My Watch Parties</h2>

      <div v-if="myRoomsLoading" class="text-center p-4">
        <div class="spinner-border" role="status">
          <span class="visually-hidden">Loading...</span>
        </div>
      </div>

      <div v-else class="rooms-grid">
        <RouterLink
          v-for="room in myRooms"
          :key="room.id"
          :to="`/watch-party/${room.roomCode}`"
          class="room-card"
        >
          <div class="room-card-header">
            <span class="badge bg-success">Your Room</span>
            <span v-if="room.isPublic" class="badge bg-info">Public</span>
          </div>
          <h3>{{ room.name }}</h3>
          <p v-if="room.description" class="room-description">{{ room.description }}</p>
          <div class="room-meta">
            <span>
              <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="me-1">
                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
                <circle cx="9" cy="7" r="4"/>
              </svg>
              {{ room.memberCount }} members
            </span>
            <span class="room-code">{{ room.roomCode }}</span>
          </div>
        </RouterLink>
      </div>
    </div>

    <!-- Public Rooms -->
    <div class="rooms-section">
      <h2>Public Watch Parties</h2>

      <div v-if="publicRoomsLoading" class="text-center p-4">
        <div class="spinner-border" role="status">
          <span class="visually-hidden">Loading...</span>
        </div>
      </div>

      <div v-else-if="publicRooms.length === 0" class="empty-state">
        <svg xmlns="http://www.w3.org/2000/svg" width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="empty-icon">
          <rect x="2" y="7" width="20" height="15" rx="2" ry="2"/>
          <polyline points="17 2 12 7 7 2"/>
        </svg>
        <h3>No public Watch Parties</h3>
        <p class="text-muted">Be the first to create a public Watch Party!</p>
        <button class="btn btn-primary mt-3" @click="openCreateModal">
          Create Watch Party
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
            <span class="badge bg-info">Public</span>
          </div>
          <h3>{{ room.name }}</h3>
          <p v-if="room.description" class="room-description">{{ room.description }}</p>
          <div class="room-meta">
            <span>
              <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="me-1">
                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
                <circle cx="9" cy="7" r="4"/>
              </svg>
              {{ room.memberCount }} members
            </span>
            <span>Host: {{ room.ownerUsername }}</span>
          </div>
          <div v-if="room.currentVideoTitle" class="current-video-badge">
            <svg xmlns="http://www.w3.org/2000/svg" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="me-1">
              <polygon points="5 3 19 12 5 21 5 3"/>
            </svg>
            {{ room.currentVideoTitle }}
          </div>
        </RouterLink>
      </div>
    </div>

    <!-- Create Room Modal -->
    <div v-if="showCreateModal" class="modal-overlay" @click.self="showCreateModal = false">
      <div class="create-modal">
        <div class="modal-header">
          <h3>Create Watch Party</h3>
          <button class="btn-close" @click="showCreateModal = false"></button>
        </div>

        <div class="modal-body">
          <div class="mb-3">
            <label class="form-label">Room Name *</label>
            <input
              v-model="newRoom.name"
              type="text"
              class="form-control"
              placeholder="e.g., Movie Night with Friends"
              maxlength="100"
            >
          </div>

          <div class="mb-3">
            <label class="form-label">Description (optional)</label>
            <textarea
              v-model="newRoom.description"
              class="form-control"
              rows="3"
              placeholder="What are you watching today?"
              maxlength="500"
            ></textarea>
          </div>

          <div class="mb-3">
            <div class="form-check form-switch">
              <input
                v-model="newRoom.isPublic"
                class="form-check-input"
                type="checkbox"
                id="isPublicSwitch"
              >
              <label class="form-check-label" for="isPublicSwitch">
                Make this room public
              </label>
            </div>
            <small class="text-muted">
              Public rooms are visible to everyone. Private rooms require a code to join.
            </small>
          </div>
        </div>

        <div class="modal-footer">
          <button class="btn btn-secondary" @click="showCreateModal = false">Cancel</button>
          <button
            class="btn btn-primary"
            @click="handleCreateRoom"
            :disabled="creating || !newRoom.name.trim()"
          >
            <span v-if="creating" class="spinner-border spinner-border-sm me-2"></span>
            Create Room
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.watch-party-list-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 20px;
  margin-bottom: 30px;
}

.header-content h1 {
  display: flex;
  align-items: center;
  margin: 0 0 8px 0;
}

.subtitle {
  color: var(--color-text-muted);
  margin: 0;
}

/* Join Section */
.join-section {
  margin-bottom: 40px;
}

.join-card {
  background: linear-gradient(135deg, var(--color-background-soft) 0%, var(--color-background) 100%);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  padding: 30px;
  text-align: center;
  max-width: 500px;
  margin: 0 auto;
}

.join-card h3 {
  margin: 0 0 8px 0;
}

.join-form {
  display: flex;
  gap: 12px;
  margin-top: 20px;
}

.join-form input {
  flex: 1;
  text-transform: uppercase;
  text-align: center;
  font-size: 1.1rem;
  letter-spacing: 2px;
}

/* Rooms Section */
.rooms-section {
  margin-bottom: 40px;
}

.rooms-section h2 {
  margin-bottom: 20px;
  font-size: 1.4rem;
}

.rooms-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
}

.room-card {
  background: var(--color-background-soft);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  padding: 20px;
  text-decoration: none;
  color: inherit;
  transition: transform 0.2s, box-shadow 0.2s;
  display: block;
}

.room-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.15);
  border-color: var(--color-primary);
}

.room-card-header {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.room-card h3 {
  margin: 0 0 8px 0;
  font-size: 1.1rem;
}

.room-card .room-description {
  color: var(--color-text-muted);
  font-size: 0.9rem;
  margin-bottom: 12px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.room-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 0.85rem;
  color: var(--color-text-muted);
}

.room-code {
  font-family: monospace;
  background: var(--color-background);
  padding: 4px 8px;
  border-radius: 4px;
}

.current-video-badge {
  margin-top: 12px;
  padding: 8px 12px;
  background: var(--color-background);
  border-radius: 6px;
  font-size: 0.85rem;
  display: flex;
  align-items: center;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* Empty State */
.empty-state {
  text-align: center;
  padding: 60px 20px;
  background: var(--color-background-soft);
  border-radius: 12px;
}

.empty-icon {
  color: var(--color-text-muted);
  margin-bottom: 20px;
}

.empty-state h3 {
  margin: 0 0 8px 0;
}

/* Create Modal */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 20px;
}

.create-modal {
  background: var(--color-background);
  border-radius: 12px;
  max-width: 500px;
  width: 100%;
}

.create-modal .modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px;
  border-bottom: 1px solid var(--color-border);
}

.create-modal .modal-header h3 {
  margin: 0;
}

.create-modal .modal-body {
  padding: 20px;
}

.create-modal .modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 20px;
  border-top: 1px solid var(--color-border);
}

@media (max-width: 576px) {
  .join-form {
    flex-direction: column;
  }

  .page-header {
    flex-direction: column;
    text-align: center;
  }

  .header-actions {
    width: 100%;
  }

  .header-actions button {
    width: 100%;
  }
}
</style>

