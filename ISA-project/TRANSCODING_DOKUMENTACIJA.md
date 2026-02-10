# Video Transcoding Feature - Dokumentacija

## Pregled implementacije

Implementirana je funkcionalnost za **transcoding video sadržaja** korišćenjem RabbitMQ message queue sistema sa više potrošača (consumera) i FFmpeg alata za obradu videa.

---

## Arhitektura rešenja

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────────┐
│  VideoService   │────▶│   RabbitMQ       │────▶│  TranscodingConsumer│
│  (Producer)     │     │   Queue          │     │  (2-4 consumera)    │
└─────────────────┘     └──────────────────┘     └─────────────────────┘
                               │                          │
                               │                          ▼
                               │                 ┌─────────────────────┐
                               │                 │      FFmpeg         │
                               │                 │   (Transcoding)     │
                               │                 └─────────────────────┘
                               │                          │
                               ▼                          │ (neuspeh)
                        ┌──────────────────┐              │
                        │  Dead Letter     │◀─────────────┘
                        │  Queue (DLQ)     │
                        └──────────────────┘
                               │ (retry nakon 60s)
                               └──────────▶ Nazad u main queue (max 3 puta)
```

---

## Kreirani/Modifikovani fajlovi

### 1. `application.properties` (Modifikovan)
**Putanja:** `src/main/resources/application.properties`

Dodate konfiguracije za:
- **RabbitMQ konekciju** - host, port, username, password
- **Transcoding queue** - naziv queue-a, exchange-a i routing key-a
- **Broj potrošača** - `concurrency=2-4` (minimalno 2 consumera)
- **Retry mehanizam** - max-retries i retry-delay-ms
- **FFmpeg putanje** - konfigurabilne putanje do FFmpeg i FFprobe
- **Transcoding parametri** - predefinisani parametri (codec, rezolucija, bitrate)

```properties
# RabbitMQ Configuration
spring.rabbitmq.host=${RABBITMQ_HOST:localhost}
spring.rabbitmq.port=${RABBITMQ_PORT:5672}

# Transcoding Configuration
transcoding.consumer.concurrency=2-4
transcoding.max-retries=3
transcoding.retry-delay-ms=60000

# Transcoding Parameters (predefined)
transcoding.params.video-codec=libx264
transcoding.params.resolution=1280x720
transcoding.params.video-bitrate=2500000
```

---

### 2. `RabbitMQConfig.java` (Nov fajl)
**Putanja:** `src/main/java/com/example/jutjubic/config/RabbitMQConfig.java`

Konfiguracija RabbitMQ sistema:
- **Queue** - Durable queue koji preživljava restart brokera
- **Exchange** - Direct exchange za rutiranje poruka
- **Binding** - Povezivanje queue-a sa exchange-om preko routing key-a
- **Manual Acknowledgment** - Sprečava duplikatnu isporuku poruka
- **Prefetch Count = 1** - Fer distribucija poruka među consumerima
- **Dead Letter Queue (DLQ)** - Queue za neuspele poruke
- **Dead Letter Exchange (DLX)** - Exchange za rutiranje neuspelih poruka
- **Automatski retry** - Poruke se vraćaju u main queue nakon TTL

```java
// Main queue sa DLQ konfiguracijom
@Bean
public Queue transcodingQueue() {
    return QueueBuilder.durable(queueName)
            .withArgument("x-dead-letter-exchange", exchangeName + ".dlx")
            .withArgument("x-dead-letter-routing-key", routingKey + ".dlq")
            .build();
}

// Dead Letter Queue sa automatskim retry-om
@Bean
public Queue transcodingDeadLetterQueue() {
    return QueueBuilder.durable(queueName + ".dlq")
            .withArgument("x-dead-letter-exchange", exchangeName)
            .withArgument("x-dead-letter-routing-key", routingKey)
            .withArgument("x-message-ttl", 60000) // retry nakon 60 sekundi
            .build();
}
```

---

### 3. `TranscodingJobMessage.java` (Nov fajl)
**Putanja:** `src/main/java/com/example/jutjubic/dto/TranscodingJobMessage.java`

DTO klasa koja predstavlja poruku za transcoding job:
- `videoId` - ID videa koji se transkodira
- `sourcePath` - Putanja do originalnog video fajla
- `outputPath` - Putanja za transkodovani video
- `videoCodec` - Video kodek (npr. libx264)
- `audioCodec` - Audio kodek (npr. aac)
- `resolution` - Ciljna rezolucija (npr. 1280x720)
- `videoBitrate` - Video bitrate
- `audioBitrate` - Audio bitrate
- `format` - Output format (npr. mp4)

---

### 4. `TranscodingProducerService.java` (Nov fajl)
**Putanja:** `src/main/java/com/example/jutjubic/services/TranscodingProducerService.java`

Producer servis koji šalje transcoding jobove u RabbitMQ queue:
- Koristi `RabbitTemplate` za slanje poruka
- Automatski koristi predefinisane parametre iz `application.properties`
- Generiše output putanju za transkodovani video
- Podržava i custom parametre za transcoding

```java
public void sendTranscodingJob(UUID videoId, String sourcePath) {
    TranscodingJobMessage message = new TranscodingJobMessage(...);
    rabbitTemplate.convertAndSend(exchangeName, routingKey, message);
}
```

---

### 5. `TranscodingConsumerService.java` (Nov fajl)
**Putanja:** `src/main/java/com/example/jutjubic/services/TranscodingConsumerService.java`

Consumer servis koji obrađuje transcoding jobove:

#### Ključne karakteristike:
- **Višestruki consumeri** - `concurrency="2-4"` garantuje minimalno 2 aktivna consumera
- **Manual acknowledgment** - Poruka se potvrđuje tek nakon uspešne obrade
- **FFmpeg integracija** - Koristi `net.bramp.ffmpeg` wrapper biblioteku
- **Automatsko ažuriranje statusa** - Postavlja `transcoded=true` i `transcodedVideoPath` nakon uspešnog transcodinga
- **Retry tracking** - Prati broj pokušaja iz x-death headera
- **Max retries** - Prestaje sa pokušajima nakon 3 neuspela retry-a

```java
@RabbitListener(
    queues = "${transcoding.queue.name}",
    concurrency = "${transcoding.consumer.concurrency}"
)
public void processTranscodingJob(TranscodingJobMessage message, 
                                   Channel channel, 
                                   @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                                   @Header(name = "x-death", required = false) List<Map<String, Object>> xDeath) {
    int retryCount = getRetryCount(xDeath);
    logger.info("Processing video: {} (attempt {}/{})", message.getVideoId(), retryCount + 1, MAX_RETRY_COUNT);
    
    try {
        transcodeVideo(message);
        updateVideoTranscodedStatus(message.getVideoId(), message.getOutputPath());
        channel.basicAck(deliveryTag, false);
    } catch (Exception e) {
        if (retryCount >= MAX_RETRY_COUNT - 1) {
            channel.basicAck(deliveryTag, false); // Give up after max retries
        } else {
            channel.basicNack(deliveryTag, false, false); // Send to DLQ for retry
        }
    }
}
```

#### Sprečavanje duple obrade:
1. **Manual ACK mode** - Poruka ostaje u queue-u dok nije eksplicitno potvrđena
2. **Prefetch = 1** - Svaki consumer dobija samo jednu poruku dok je ne obradi
3. **basicAck/basicNack** - Eksplicitna potvrda ili odbijanje poruke

---

### 6. `VideoService.java` (Modifikovan)
**Putanja:** `src/main/java/com/example/jutjubic/services/VideoService.java`

Dodata integracija sa transcoding sistemom:

1. **Dodat dependency injection** za `TranscodingProducerService`
2. **Slanje transcoding joba** nakon uspešnog kreiranja videa:

```java
// Send transcoding job to message queue
try {
    transcodingProducerService.sendTranscodingJob(savedVideo.getId(), savedVideo.getVideoPath());
    logger.info("Transcoding job sent for video: {}", savedVideo.getId());
} catch (Exception e) {
    logger.error("Failed to send transcoding job for video {}: {}", savedVideo.getId(), e.getMessage());
    // Don't fail the video creation if transcoding job fails to send
}
```

---

### 7. `Video.java` (Modifikovan)
**Putanja:** `src/main/java/com/example/jutjubic/models/Video.java`

Dodato novo polje za putanju transkodovanog videa:

```java
@Column(name = "transcoded_video_path")
private String transcodedVideoPath;
```

---

## Tok izvršavanja

### Uspešan transcoding:
1. **Korisnik kreira video** → `VideoController.createVideo()`
2. **VideoService čuva video** → `videoRepository.save(video)`
3. **Producer šalje job** → `transcodingProducerService.sendTranscodingJob()`
4. **RabbitMQ prima poruku** → Poruka sačuvana u queue-u
5. **Consumer preuzima poruku** → Jedan od 2-4 aktivnih consumera
6. **FFmpeg transkodira** → `ffmpeg -i input.mp4 -c:v libx264 ... output.mp4`
7. **Status se ažurira** → `video.setTranscoded(true)`, `video.setTranscodedVideoPath(...)`
8. **ACK potvrda** → `channel.basicAck()` - poruka se briše iz queue-a

### Neuspešan transcoding (retry flow):
1. **Consumer primi poruku** → Obrada neuspešna
2. **NACK potvrda** → `channel.basicNack()` - poruka ide u DLQ
3. **DLQ čeka TTL** → 60 sekundi
4. **Poruka se vraća** → Nazad u main queue sa povećanim x-death count
5. **Retry** → Ponavlja se do 3 puta
6. **Max retries** → Nakon 3 neuspela pokušaja, poruka se trajno odbacuje

---

## Predefinisani transcoding parametri

| Parametar | Vrednost | Opis |
|-----------|----------|------|
| Video Codec | libx264 | H.264 video kompresija |
| Audio Codec | aac | AAC audio kompresija |
| Resolution | 1280x720 | 720p HD rezolucija |
| Video Bitrate | 2.5 Mbps | Kvalitet video stream-a |
| Audio Bitrate | 128 kbps | Kvalitet audio stream-a |
| Format | mp4 | Kontejner format |
| Max Retries | 3 | Maksimalan broj pokušaja |
| Retry Delay | 60s | Pauza između pokušaja |

---

## Konfiguracija okruženja

### Potrebne environment varijable:
```bash
# RabbitMQ
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USER=guest
RABBITMQ_PASSWORD=guest

# FFmpeg (opciono - default koristi PATH)
FFMPEG_PATH=ffmpeg
FFPROBE_PATH=ffprobe
```

### Pokretanje RabbitMQ (Docker):
```bash
docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3-management
```

---

## Prednosti implementacije

1. **Asinhrona obrada** - Video upload ne čeka na transcoding
2. **Skalabilnost** - Lako povećanje broja consumera
3. **Pouzdanost** - Manual ACK sprečava gubitak poruka
4. **Fer distribucija** - Prefetch=1 obezbeđuje ravnomernu raspodelu
5. **Konfigurabilnost** - Svi parametri u `application.properties`
6. **Graceful degradation** - Neuspeli transcoding ne blokira kreiranje videa
7. **Retry mehanizam** - Automatsko ponovno pokušavanje do 3 puta
8. **Dead Letter Queue** - Neuspele poruke se čuvaju i automatski ponavljaju
9. **Praćenje putanje** - Transkodovani video se čuva u posebnom polju

---

## Napomena

Transkodovani video fajlovi se čuvaju u `media/videos/transcoded/` direktorijumu sa imenima oblika `transcoded_{videoId}.mp4`.

Putanja transkodovanog videa se čuva u bazi u polju `transcoded_video_path` u tabeli `videos`.

---

## Zašto Docker i RabbitMQ?

### Zašto RabbitMQ?

RabbitMQ je **message broker** (posrednik za poruke) koji omogućava asinhronu komunikaciju između različitih delova aplikacije. U kontekstu video transcodinga:

1. **Decoupling** - Video upload i transcoding su razdvojeni procesi
   - Korisnik ne mora da čeka dok se video transkodira
   - Upload se završava odmah, transcoding se dešava u pozadini

2. **Pouzdanost** - Poruke se čuvaju dok ne budu obrađene
   - Ako server padne, poruke ostaju u queue-u
   - Garantovana isporuka (at-least-once delivery)

3. **Skalabilnost** - Lako dodavanje više consumera
   - Više transcoding workera = brža obrada
   - Horizontalno skaliranje bez promene koda

4. **Load balancing** - Automatska raspodela posla
   - RabbitMQ ravnomerno distribuira poruke među consumerima
   - Nijedan consumer nije preopterećen

### Zašto Docker?

Docker omogućava pokretanje RabbitMQ servera u izolovanom kontejneru:

1. **Jednostavna instalacija** - Jedna komanda umesto kompleksne instalacije
2. **Konzistentnost** - Ista verzija i konfiguracija na svim mašinama
3. **Izolacija** - RabbitMQ ne utiče na ostatak sistema
4. **Portabilnost** - Radi isto na Windows, Linux, Mac

---

## Full Testiranje Transcoding Feature-a

### Preduslov: Pokretanje RabbitMQ servera

#### 1. Instalacija Docker-a
- Preuzeti Docker Desktop sa: https://www.docker.com/products/docker-desktop/
- Instalirati i pokrenuti Docker Desktop

#### 2. Pokretanje RabbitMQ kontejnera
```bash
docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3-management
```

**Objašnjenje komande:**
- `-d` - Pokreni u pozadini (detached mode)
- `--name rabbitmq` - Ime kontejnera
- `-p 5672:5672` - AMQP port (za aplikaciju)
- `-p 15672:15672` - Management UI port (za praćenje)
- `rabbitmq:3-management` - Image sa uključenim web UI-om

#### 3. Provera da li RabbitMQ radi
- Otvoriti browser: http://localhost:15672
- Login: `guest` / `guest`
- Trebalo bi da vidite RabbitMQ Management Dashboard

---

### Korak po korak testiranje

#### Test 1: Provera konekcije sa RabbitMQ

1. Pokrenuti Spring Boot aplikaciju
2. U logovima tražiti:
   ```
   Created new connection: rabbitConnectionFactory
   ```
3. U RabbitMQ UI (http://localhost:15672) proveriti:
   - **Connections** tab - Treba da postoji aktivna konekcija
   - **Queues** tab - Treba da postoji `video-transcoding-queue`

#### Test 2: Kreiranje videa i slanje transcoding job-a

1. Ulogovati se u aplikaciju
2. Kreirati novi video (upload video + thumbnail)
3. U Spring Boot logovima tražiti:
   ```
   Transcoding job sent for video: <video-id>
   ```
4. U RabbitMQ UI proveriti:
   - **Queues** → `video-transcoding-queue`
   - **Messages Ready** treba da bude 1 (ako consumer još nije obradio)

#### Test 3: Praćenje consumer obrade

1. U logovima tražiti:
   ```
   [pool-X-thread-Y] Received transcoding job for video: <video-id>
   Starting FFmpeg transcoding for: media/videos/video_xxx.mp4
   Transcoding completed successfully. Output: media/videos/transcoded/transcoded_<video-id>.mp4
   Updated video <video-id> transcoded status to true
   ```

2. Proveriti da li je fajl kreiran:
   ```
   media/videos/transcoded/transcoded_<video-id>.mp4
   ```

3. Proveriti bazu podataka:
   ```sql
   SELECT id, title, is_transcoded, transcoded_video_path FROM videos WHERE id = '<video-id>';
   ```
   - `is_transcoded` treba da bude `1` (true)
   - `transcoded_video_path` treba da ima putanju

#### Test 4: Testiranje višestrukih consumera

1. U RabbitMQ UI → **Queues** → `video-transcoding-queue` → **Consumers**
2. Treba da vidite 2-4 aktivna consumera
3. Kreirati više videa brzo uzastopno
4. U logovima proveriti da različiti thread-ovi obrađuju različite videe:
   ```
   [pool-1-thread-1] Received transcoding job for video: aaa-bbb
   [pool-1-thread-2] Received transcoding job for video: ccc-ddd
   ```

#### Test 5: Testiranje retry mehanizma (simulacija greške)

1. Privremeno preimenovati FFmpeg executable (da izazove grešku)
2. Kreirati novi video
3. U logovima tražiti:
   ```
   Failed to transcode video xxx: FFmpeg is not properly initialized
   Message sent to DLQ for retry. Video: xxx, Attempt: 1/3
   ```
4. U RabbitMQ UI proveriti:
   - `video-transcoding-queue.dlq` ima poruku
5. Sačekati 60 sekundi - poruka se vraća u main queue
6. Nakon 3 neuspela pokušaja:
   ```
   Max retries (3) reached for video: xxx. Giving up.
   ```

#### Test 6: Testiranje Dead Letter Queue

1. U RabbitMQ UI → **Queues**
2. Proveriti postojanje:
   - `video-transcoding-queue` - Glavni queue
   - `video-transcoding-queue.dlq` - Dead Letter Queue
3. Neuspele poruke se automatski šalju u DLQ
4. Nakon TTL (60s) vraćaju se u main queue

---

### Praćenje u RabbitMQ Management UI

#### Queues Tab
| Queue | Opis |
|-------|------|
| `video-transcoding-queue` | Glavni queue za transcoding jobove |
| `video-transcoding-queue.dlq` | Dead Letter Queue za neuspele poruke |

#### Exchanges Tab
| Exchange | Opis |
|----------|------|
| `video-transcoding-exchange` | Glavni exchange |
| `video-transcoding-exchange.dlx` | Dead Letter Exchange |

#### Korisne metrike za praćenje:
- **Messages Ready** - Poruke koje čekaju na obradu
- **Messages Unacked** - Poruke koje se trenutno obrađuju
- **Consumers** - Broj aktivnih consumera
- **Message rates** - Publish/Deliver rate

---

### Troubleshooting

| Problem | Rešenje |
|---------|---------|
| `Connection refused` | Proveriti da li RabbitMQ Docker kontejner radi: `docker ps` |
| `Queue not created` | Restartovati aplikaciju - queue se kreira pri startu |
| `FFmpeg not found` | Instalirati FFmpeg i dodati u PATH, ili podesiti `FFMPEG_PATH` |
| `Message stuck in queue` | Proveriti consumer logove za greške |
| `Consumer not starting` | Proveriti da li je RabbitMQ dostupan pre pokretanja aplikacije |

### Docker komande za RabbitMQ

```bash
# Pokretanje kontejnera
docker start rabbitmq

# Zaustavljanje kontejnera
docker stop rabbitmq

# Pregled logova
docker logs rabbitmq

# Restart kontejnera
docker restart rabbitmq

# Brisanje kontejnera (ako treba ponovo kreirati)
docker rm -f rabbitmq
```



