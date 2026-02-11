import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';
import axios from 'axios';

/**
 * Servis za Watch Party funkcionalnost.
 * Koristi REST API za CRUD operacije i WebSocket za real-time sinhronizaciju.
 */

const BASE_URL = 'http://localhost:8080';
const WS_URL = 'http://localhost:8080/ws';

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

// ----- REST API Functions -----

/**
 * Kreira novu Watch Party sobu.
 */
export function createWatchPartyRoom(name, description, isPublic = false) {
  return api.post('/api/watch-party/create', {
    name,
    description,
    isPublic
  });
}

/**
 * Dobija sobu po ID-u.
 */
export function getWatchPartyById(roomId) {
  return api.get(`/api/watch-party/${roomId}`);
}

/**
 * Dobija sobu po kodu za pridruživanje.
 */
export function getWatchPartyByCode(roomCode) {
  return api.get(`/api/watch-party/code/${roomCode}`);
}

/**
 * Dobija sve aktivne javne sobe.
 */
export function getPublicWatchParties() {
  return api.get('/api/watch-party/public');
}

/**
 * Dobija sve sobe koje je kreirao trenutni korisnik.
 */
export function getMyWatchParties() {
  return api.get('/api/watch-party/my-rooms');
}

/**
 * Pridruživanje sobi po kodu.
 */
export function joinWatchParty(roomCode) {
  return api.post(`/api/watch-party/join/${roomCode}`);
}

/**
 * Postavlja trenutni video u sobi.
 */
export function setWatchPartyVideo(roomCode, videoId) {
  return api.post(`/api/watch-party/${roomCode}/video/${videoId}`);
}

/**
 * Zatvara Watch Party sobu.
 */
export function closeWatchParty(roomCode) {
  return api.delete(`/api/watch-party/${roomCode}`);
}

/**
 * Proverava da li je korisnik vlasnik sobe.
 */
export function checkIsOwner(roomCode) {
  return api.get(`/api/watch-party/${roomCode}/is-owner`);
}

/**
 * Dobija istoriju chat poruka za sobu.
 */
export function getChatHistory(roomCode) {
  return api.get(`/api/watch-party/${roomCode}/messages`);
}

// ----- WebSocket Functions -----

let stompClient = null;
let currentSubscription = null;

/**
 * Konektuje se na Watch Party WebSocket sobu.
 * @param {string} roomCode - Kod sobe
 * @param {Object} user - Trenutni korisnik { id, username }
 * @param {Function} onMessage - Callback za primljene poruke
 * @param {Function} onConnect - Callback za uspešnu konekciju
 * @param {Function} onError - Callback za greške
 */
export function connectToWatchParty(roomCode, user, onMessage, onConnect, onError) {
  return new Promise((resolve, reject) => {
    // Ako već postoji konekcija, disconnectuj se prvo
    if (stompClient && stompClient.connected) {
      disconnectFromWatchParty();
    }

    stompClient = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      debug: (str) => {
        // console.log('STOMP WP: ' + str);
      },
    });

    stompClient.onConnect = () => {
      console.log('Connected to Watch Party WebSocket');

      // Pretplati se na Watch Party sobu
      currentSubscription = stompClient.subscribe(
        `/topic/watch-party/${roomCode.toUpperCase()}`,
        (message) => {
          const wpMessage = JSON.parse(message.body);
          if (onMessage) {
            onMessage(wpMessage);
          }
        }
      );

      // Pošalji JOIN poruku
      if (user && user.username) {
        sendJoinMessage(roomCode, user);
      }

      if (onConnect) {
        onConnect();
      }
      resolve();
    };

    stompClient.onStompError = (frame) => {
      console.error('STOMP error:', frame);
      if (onError) {
        onError(frame);
      }
      reject(frame);
    };

    stompClient.onWebSocketError = (event) => {
      console.error('WebSocket error:', event);
      if (onError) {
        onError(event);
      }
    };

    stompClient.onDisconnect = () => {
      console.log('Disconnected from Watch Party WebSocket');
    };

    stompClient.activate();
  });
}

/**
 * Šalje JOIN poruku kada se korisnik pridružuje sobi.
 */
export function sendJoinMessage(roomCode, user) {
  if (!stompClient || !stompClient.connected) {
    return;
  }

  const message = {
    roomCode: roomCode.toUpperCase(),
    senderUsername: user.username,
    senderId: user.id,
    type: 'JOIN'
  };

  stompClient.publish({
    destination: `/app/watch-party/${roomCode.toUpperCase()}/join`,
    body: JSON.stringify(message)
  });
}

/**
 * Šalje LEAVE poruku kada korisnik napušta sobu.
 */
export function sendLeaveMessage(roomCode, user) {
  if (!stompClient || !stompClient.connected) {
    return;
  }

  const message = {
    roomCode: roomCode.toUpperCase(),
    senderUsername: user.username,
    senderId: user.id,
    type: 'LEAVE'
  };

  stompClient.publish({
    destination: `/app/watch-party/${roomCode.toUpperCase()}/leave`,
    body: JSON.stringify(message)
  });
}

/**
 * Vlasnik sobe pokreće video - šalje PLAY_VIDEO poruku svim članovima.
 */
export function sendPlayVideoMessage(roomCode, videoId, videoTitle, videoThumbnail, user) {
  if (!stompClient || !stompClient.connected) {
    console.error('Not connected to Watch Party');
    return;
  }

  const message = {
    roomCode: roomCode.toUpperCase(),
    videoId: videoId,
    videoTitle: videoTitle,
    videoThumbnail: videoThumbnail,
    senderUsername: user.username,
    senderId: user.id,
    type: 'PLAY_VIDEO'
  };

  stompClient.publish({
    destination: `/app/watch-party/${roomCode.toUpperCase()}/play`,
    body: JSON.stringify(message)
  });
}

/**
 * Šalje chat poruku u Watch Party sobu.
 */
export function sendWatchPartyChatMessage(roomCode, content, user) {
  if (!stompClient || !stompClient.connected) {
    console.error('Not connected to Watch Party');
    return;
  }

  const message = {
    roomCode: roomCode.toUpperCase(),
    content: content,
    senderUsername: user.username,
    senderId: user.id,
    type: 'CHAT'
  };

  stompClient.publish({
    destination: `/app/watch-party/${roomCode.toUpperCase()}/chat`,
    body: JSON.stringify(message)
  });
}

/**
 * Vlasnik zatvara sobu.
 */
export function sendCloseRoomMessage(roomCode, user) {
  if (!stompClient || !stompClient.connected) {
    console.error('Not connected to Watch Party');
    return;
  }

  const message = {
    roomCode: roomCode.toUpperCase(),
    senderUsername: user.username,
    senderId: user.id,
    type: 'ROOM_CLOSED'
  };

  stompClient.publish({
    destination: `/app/watch-party/${roomCode.toUpperCase()}/close`,
    body: JSON.stringify(message)
  });
}

/**
 * Prekida vezu sa Watch Party WebSocket-om.
 */
export function disconnectFromWatchParty(roomCode = null, user = null) {
  if (roomCode && user) {
    sendLeaveMessage(roomCode, user);
  }

  if (currentSubscription) {
    currentSubscription.unsubscribe();
    currentSubscription = null;
  }

  if (stompClient) {
    stompClient.deactivate();
    stompClient = null;
  }
}

