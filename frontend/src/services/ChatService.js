import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

/**
 * Servis za WebSocket komunikaciju sa chat-om.
 * Koristi SockJS i STOMP protokol za real-time razmenu poruka.
 */

const WS_URL = 'http://localhost:8080/ws';

let stompClient = null;
let currentSubscription = null;

/**
 * Konektuje se na WebSocket server i prijavljuje na chat sobu za određeni video.
 * @param {string} videoId - ID videa (identifikator chat sobe)
 * @param {Object} user - Trenutni korisnik { id, username }
 * @param {Function} onMessage - Callback funkcija koja se poziva kada stigne nova poruka
 * @param {Function} onConnect - Callback funkcija koja se poziva kada se konekcija uspostavi
 * @param {Function} onError - Callback funkcija koja se poziva pri greški
 * @returns {Promise} - Promise koji se resolve-uje kada se konekcija uspostavi
 */
export function connectToChat(videoId, user, onMessage, onConnect, onError) {
  return new Promise((resolve, reject) => {
    // Ako već postoji konekcija, disconnectuj se prvo
    if (stompClient && stompClient.connected) {
      disconnectFromChat();
    }

    // Kreiraj STOMP klijent sa SockJS transportom
    stompClient = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      debug: (str) => {
        // Opciono: logovanje za debugging
        // console.log('STOMP: ' + str);
      },
    });

    stompClient.onConnect = () => {
      console.log('Connected to WebSocket chat');

      // Pretplati se na chat sobu za ovaj video
      currentSubscription = stompClient.subscribe(
        `/topic/chat/${videoId}`,
        (message) => {
          const chatMessage = JSON.parse(message.body);
          if (onMessage) {
            onMessage(chatMessage);
          }
        }
      );

      // Pošalji JOIN poruku
      if (user && user.username) {
        sendJoinMessage(videoId, user);
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
      console.log('Disconnected from WebSocket chat');
    };

    // Aktiviraj konekciju
    stompClient.activate();
  });
}

/**
 * Šalje chat poruku u sobu.
 * @param {string} videoId - ID videa (chat sobe)
 * @param {string} content - Sadržaj poruke
 * @param {Object} user - Korisnik koji šalje { id, username }
 */
export function sendChatMessage(videoId, content, user) {
  if (!stompClient || !stompClient.connected) {
    console.error('Not connected to chat');
    return;
  }

  const message = {
    videoId: videoId,
    content: content,
    senderUsername: user.username,
    senderId: user.id,
    type: 'CHAT',
    timestamp: Date.now()
  };

  stompClient.publish({
    destination: `/app/chat/${videoId}`,
    body: JSON.stringify(message)
  });
}

/**
 * Šalje JOIN poruku kada se korisnik priključi chat-u.
 * @param {string} videoId - ID videa
 * @param {Object} user - Korisnik { id, username }
 */
export function sendJoinMessage(videoId, user) {
  if (!stompClient || !stompClient.connected) {
    return;
  }

  const message = {
    videoId: videoId,
    senderUsername: user.username,
    senderId: user.id,
    type: 'JOIN'
  };

  stompClient.publish({
    destination: `/app/chat/${videoId}/join`,
    body: JSON.stringify(message)
  });
}

/**
 * Šalje LEAVE poruku kada korisnik napušta chat.
 * @param {string} videoId - ID videa
 * @param {Object} user - Korisnik { id, username }
 */
export function sendLeaveMessage(videoId, user) {
  if (!stompClient || !stompClient.connected) {
    return;
  }

  const message = {
    videoId: videoId,
    senderUsername: user.username,
    senderId: user.id,
    type: 'LEAVE'
  };

  stompClient.publish({
    destination: `/app/chat/${videoId}/leave`,
    body: JSON.stringify(message)
  });
}

/**
 * Prekida konekciju sa chat-om.
 * @param {string} videoId - ID videa (opciono, za slanje LEAVE poruke)
 * @param {Object} user - Korisnik (opciono, za slanje LEAVE poruke)
 */
export function disconnectFromChat(videoId = null, user = null) {
  // Pošalji LEAVE poruku ako su prosleđeni parametri
  if (videoId && user && stompClient && stompClient.connected) {
    sendLeaveMessage(videoId, user);
  }

  // Otkaži pretplatu
  if (currentSubscription) {
    currentSubscription.unsubscribe();
    currentSubscription = null;
  }

  // Deaktiviraj klijent
  if (stompClient) {
    stompClient.deactivate();
    stompClient = null;
  }
}
