/**
 * Test skripta za WebSocket chat funkcionalnost.
 * Simulira dva korisnika koji gledaju live video i razmenjuju poruke.
 *
 * Pokretanje: node test/chat-test.js
 * Napomena: Backend server mora biti pokrenut na localhost:8080
 */

import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

// Polyfill za WebSocket u Node.js okruženju
import WebSocket from 'ws';
Object.assign(global, { WebSocket });

const WS_URL = 'http://localhost:8080/ws';
const VIDEO_ID = '0ed03a3a-7915-4458-93af-66f43e67241c'; // Test video ID

// Simulirani korisnici
const user1 = { id: 'user-1', username: 'Marko' };
const user2 = { id: 'user-2', username: 'Ana' };

// Generički odgovori za User2
const genericResponses = [
  'Slažem se! 👍',
  'Super stream!',
  'Odlično!',
  'Baš zanimljivo!',
  'Hvala na informaciji!',
  'Podržavam!',
];

function getRandomResponse() {
  return genericResponses[Math.floor(Math.random() * genericResponses.length)];
}

/**
 * Kreira STOMP klijenta i konektuje se na chat.
 */
function createChatClient(user, onMessage) {
  return new Promise((resolve, reject) => {
    const client = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      reconnectDelay: 5000,
      debug: (str) => {
        // Uncomment for debugging
        // console.log(`[${user.username}] STOMP: ${str}`);
      },
    });

    client.onConnect = () => {
      console.log(`✅ [${user.username}] Konektovan na chat`);

      // Pretplati se na chat sobu
      client.subscribe(`/topic/chat/${VIDEO_ID}`, (message) => {
        const chatMessage = JSON.parse(message.body);
        if (onMessage) {
          onMessage(chatMessage);
        }
      });

      // Pošalji JOIN poruku
      client.publish({
        destination: `/app/chat/${VIDEO_ID}/join`,
        body: JSON.stringify({
          videoId: VIDEO_ID,
          senderUsername: user.username,
          senderId: user.id,
          type: 'JOIN'
        })
      });

      resolve(client);
    };

    client.onStompError = (frame) => {
      console.error(`❌ [${user.username}] STOMP error:`, frame.headers['message']);
      reject(frame);
    };

    client.onWebSocketError = (event) => {
      console.error(`❌ [${user.username}] WebSocket error`);
      reject(event);
    };

    client.activate();
  });
}

/**
 * Šalje chat poruku.
 */
function sendMessage(client, user, content) {
  client.publish({
    destination: `/app/chat/${VIDEO_ID}`,
    body: JSON.stringify({
      videoId: VIDEO_ID,
      content: content,
      senderUsername: user.username,
      senderId: user.id,
      type: 'CHAT',
      timestamp: Date.now()
    })
  });
}

/**
 * Formatira timestamp.
 */
function formatTime(timestamp) {
  return new Date(timestamp).toLocaleTimeString('sr-RS', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
}

/**
 * Glavna test funkcija.
 */
async function runTest() {
  console.log('='.repeat(60));
  console.log('🎬 WebSocket Chat Test - Simulacija dva korisnika');
  console.log('='.repeat(60));
  console.log(`📺 Video ID: ${VIDEO_ID}`);
  console.log(`👤 User 1: ${user1.username}`);
  console.log(`👤 User 2: ${user2.username}`);
  console.log('='.repeat(60));
  console.log('');

  try {
    // Handler za primanje poruka - User 1
    const user1MessageHandler = (msg) => {
      if (msg.type === 'JOIN') {
        console.log(`🔔 [${formatTime(msg.timestamp)}] ${msg.content}`);
      } else if (msg.type === 'LEAVE') {
        console.log(`👋 [${formatTime(msg.timestamp)}] ${msg.content}`);
      } else if (msg.senderId !== user1.id) {
        // Poruka od drugog korisnika
        console.log(`💬 [${formatTime(msg.timestamp)}] ${msg.senderUsername}: ${msg.content}`);
      }
    };

    // Handler za primanje poruka - User 2 (automatski odgovara)
    let client2 = null;
    const user2MessageHandler = (msg) => {
      if (msg.type === 'JOIN') {
        console.log(`🔔 [${formatTime(msg.timestamp)}] ${msg.content}`);
      } else if (msg.type === 'LEAVE') {
        console.log(`👋 [${formatTime(msg.timestamp)}] ${msg.content}`);
      } else if (msg.senderId !== user2.id) {
        // Poruka od drugog korisnika - User 2 automatski odgovara
        console.log(`💬 [${formatTime(msg.timestamp)}] ${msg.senderUsername}: ${msg.content}`);

        // Sačekaj malo pa odgovori
        setTimeout(() => {
          const response = getRandomResponse();
          console.log(`📤 [${user2.username}] šalje automatski odgovor: "${response}"`);
          sendMessage(client2, user2, response);
        }, 1500);
      }
    };

    // Konektuj User 1
    console.log(`⏳ Konektovanje ${user1.username}...`);
    const client1 = await createChatClient(user1, user1MessageHandler);

    // Sačekaj malo pa konektuj User 2
    await new Promise(resolve => setTimeout(resolve, 1000));

    console.log(`⏳ Konektovanje ${user2.username}...`);
    client2 = await createChatClient(user2, user2MessageHandler);

    // Sačekaj malo
    await new Promise(resolve => setTimeout(resolve, 1000));

    console.log('');
    console.log('-'.repeat(60));
    console.log('📨 Započinjemo razmenu poruka...');
    console.log('-'.repeat(60));
    console.log('');

    // User 1 šalje prvu poruku
    const message1 = 'Pozdrav svima! Kako vam se dopada stream?';
    console.log(`📤 [${user1.username}] šalje: "${message1}"`);
    sendMessage(client1, user1, message1);

    // Sačekaj da User 2 odgovori
    await new Promise(resolve => setTimeout(resolve, 3000));

    // User 1 šalje drugu poruku
    const message2 = 'Ovo je baš zanimljiv sadržaj!';
    console.log(`📤 [${user1.username}] šalje: "${message2}"`);
    sendMessage(client1, user1, message2);

    // Sačekaj da User 2 odgovori
    await new Promise(resolve => setTimeout(resolve, 3000));

    // User 1 šalje treću poruku
    const message3 = 'Da li neko zna kada će sledeći stream?';
    console.log(`📤 [${user1.username}] šalje: "${message3}"`);
    sendMessage(client1, user1, message3);

    // Sačekaj odgovor i završi
    await new Promise(resolve => setTimeout(resolve, 3000));

    console.log('');
    console.log('-'.repeat(60));
    console.log('🏁 Test završen - Disconnecting...');
    console.log('-'.repeat(60));

    // Disconnect
    client1.publish({
      destination: `/app/chat/${VIDEO_ID}/leave`,
      body: JSON.stringify({
        videoId: VIDEO_ID,
        senderUsername: user1.username,
        senderId: user1.id,
        type: 'LEAVE'
      })
    });

    await new Promise(resolve => setTimeout(resolve, 500));

    client2.publish({
      destination: `/app/chat/${VIDEO_ID}/leave`,
      body: JSON.stringify({
        videoId: VIDEO_ID,
        senderUsername: user2.username,
        senderId: user2.id,
        type: 'LEAVE'
      })
    });

    await new Promise(resolve => setTimeout(resolve, 1000));

    client1.deactivate();
    client2.deactivate();

    console.log('');
    console.log('='.repeat(60));
    console.log('✅ Test uspešno završen!');
    console.log('='.repeat(60));

    process.exit(0);

  } catch (error) {
    console.error('❌ Test failed:', error);
    process.exit(1);
  }
}

// Pokreni test
runTest();

