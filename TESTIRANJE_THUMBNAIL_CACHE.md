# Uputstvo za testiranje Thumbnail Caching mehanizma

## Brzi test

### 1. Provera da li endpoint radi

**Korak 1:** Otvori browser i idi na:
```
http://localhost:8080/api/thumbnails/thumbnails/thumb1.jpg
```

**Očekivano:** Trebalo bi da vidiš thumbnail sliku.

---

### 2. Provera da li se cache koristi (Backend logovi)

**Korak 1:** Restartuj backend aplikaciju (da se cache očisti)

**Korak 2:** Otvori backend konzolu i obrati pažnju na logove

**Korak 3:** Prvi put otvori thumbnail u browser-u:
```
http://localhost:8080/api/thumbnails/thumbnails/thumb1.jpg
```

**Očekivani log:**
```
DEBUG Loading thumbnail from classpath: thumbnails/thumb1.jpg
INFO Thumbnail cached: thumbnails/thumb1.jpg (XXXXX bytes)
```

**Korak 4:** Osveži stranicu (F5) ili ponovo otvori isti URL

**Očekivani log:**
```
DEBUG Thumbnail served from cache: thumbnails/thumb1.jpg
```

**Ako vidiš "served from cache"** → Caching radi! ✅

---

### 3. Provera preko Frontend-a

**Korak 1:** Otvori aplikaciju u browser-u (`http://localhost:5173`)

**Korak 2:** Otvori Developer Tools (F12) → Network tab

**Korak 3:** Idi na Home stranicu (gde se prikazuju video thumbnails)

**Korak 4:** Proveri Network zahtev:
- Trebalo bi da vidiš zahteve ka `/api/thumbnails/thumbnails/thumb1.jpg`
- Status treba da bude `200 OK`
- Response Headers treba da sadrže `Cache-Control: public, max-age=31536000`

**Korak 5:** Osveži stranicu (F5)

**Očekivano:** 
- Zahtevi ka thumbnail-ima bi trebalo da budu brži (servirani iz cache-a)
- U backend logovima treba da vidiš "served from cache"

---

### 4. Provera Cache Statistika

**Korak 1:** Otvori browser i idi na:
```
http://localhost:8080/api/thumbnails/cache/stats
```

**Očekivano:** Trebalo bi da vidiš JSON:
```json
{"cacheSize": X}
```
gde je X broj thumbnail-a u cache-u.

**Korak 2:** Otvori nekoliko različitih thumbnail-a:
- `http://localhost:8080/api/thumbnails/thumbnails/thumb1.jpg`
- `http://localhost:8080/api/thumbnails/thumbnails/thumb2.jpg`
- `http://localhost:8080/api/thumbnails/thumbnails/thumb3.jpg`

**Korak 3:** Ponovo proveri cache stats:
```
http://localhost:8080/api/thumbnails/cache/stats
```

**Očekivano:** `cacheSize` bi trebalo da se poveća (npr. sa 0 na 3).

---

### 5. Test Cache Clearing

**Korak 1:** Proveri cache size:
```
GET http://localhost:8080/api/thumbnails/cache/stats
```

**Korak 2:** Očisti cache:
```
POST http://localhost:8080/api/thumbnails/cache/clear
```

**Korak 3:** Ponovo proveri cache size:
```
GET http://localhost:8080/api/thumbnails/cache/stats
```

**Očekivano:** `cacheSize` bi trebalo da bude `0`.

**Korak 4:** Otvori thumbnail ponovo:
```
http://localhost:8080/api/thumbnails/thumbnails/thumb1.jpg
```

**Očekivano:** U backend logovima treba da vidiš "Loading thumbnail from classpath" (jer je cache očišćen).

---

## Testiranje preko Postman/cURL

### Test 1: Prvi zahtev (učitavanje i caching)
```bash
curl -v http://localhost:8080/api/thumbnails/thumbnails/thumb1.jpg
```

**Proveri:**
- Status: `200 OK`
- Response Headers: `Cache-Control: public, max-age=31536000`
- Backend log: "Loading thumbnail from classpath" i "Thumbnail cached"

### Test 2: Drugi zahtev (iz cache-a)
```bash
curl -v http://localhost:8080/api/thumbnails/thumbnails/thumb1.jpg
```

**Proveri:**
- Backend log: "Thumbnail served from cache"
- Odgovor bi trebalo da bude brži

### Test 3: Cache statistika
```bash
curl http://localhost:8080/api/thumbnails/cache/stats
```

**Očekivano:**
```json
{"cacheSize": 1}
```

### Test 4: Očišćenje cache-a
```bash
curl -X POST http://localhost:8080/api/thumbnails/cache/clear
```

**Očekivano:**
```
Cache cleared successfully
```

---

## Testiranje performansi

### Test brzine učitavanja

**Korak 1:** Otvori Developer Tools (F12) → Network tab

**Korak 2:** Očisti cache u browser-u (Clear browser cache ili Hard Reload: Ctrl+Shift+R)

**Korak 3:** Očisti backend cache:
```bash
POST http://localhost:8080/api/thumbnails/cache/clear
```

**Korak 4:** Otvori Home stranicu i meri vreme učitavanja thumbnail-a

**Korak 5:** Osveži stranicu (F5) i ponovo meri vreme

**Očekivano:** 
- Prvo učitavanje: Sporije (čita sa file sistema)
- Drugo učitavanje: Brže (iz backend cache-a)

---

## Provera u Backend logovima

### Scenario 1: Prvi zahtev
```
DEBUG Loading thumbnail from classpath: thumbnails/thumb1.jpg
INFO Thumbnail cached: thumbnails/thumb1.jpg (12345 bytes)
```

### Scenario 2: Sledeći zahtevi
```
DEBUG Thumbnail served from cache: thumbnails/thumb1.jpg
```

### Scenario 3: Ne postoji thumbnail
```
WARN Thumbnail not found: thumbnails/nonexistent.jpg
ERROR Error serving thumbnail
```

---

## Troubleshooting

### Problem: Ne vidi se "served from cache" u logovima

**Rešenje:**
1. Proveri da li je log level podešen na DEBUG:
   - U `application.properties` dodaj:
   ```properties
   logging.level.com.example.jutjubic.services.ThumbnailService=DEBUG
   ```

2. Proveri da li se zaista poziva isti thumbnail (isti path)

3. Proveri da li je backend restartovan između testova

### Problem: Thumbnail se ne učitava

**Rešenje:**
1. Proveri da li thumbnail postoji u `src/main/resources/static/thumbnails/`
2. Proveri da li je path tačan (npr. `thumbnails/thumb1.jpg`)
3. Proveri backend logove za greške

### Problem: Cache size je uvek 0

**Rešenje:**
1. Proveri da li se thumbnail zaista učitava (proveri logove)
2. Proveri da li se poziva `getThumbnail()` metoda (proveri logove)
3. Proveri da li postoji greška pri učitavanju

---

## Automatski test (opciono)

Možeš kreirati jednostavan test script:

```bash
#!/bin/bash

echo "Test 1: Prvi zahtev (učitavanje)"
curl -s -o /dev/null -w "Time: %{time_total}s\n" http://localhost:8080/api/thumbnails/thumbnails/thumb1.jpg

echo "Test 2: Drugi zahtev (iz cache-a)"
curl -s -o /dev/null -w "Time: %{time_total}s\n" http://localhost:8080/api/thumbnails/thumbnails/thumb1.jpg

echo "Test 3: Cache stats"
curl -s http://localhost:8080/api/thumbnails/cache/stats
```

Drugi zahtev bi trebalo da bude brži od prvog.

---

## Provera da li frontend koristi novi endpoint

**Korak 1:** Otvori Developer Tools (F12) → Network tab

**Korak 2:** Idi na Home stranicu

**Korak 3:** Proveri Network zahteve

**Očekivano:** Trebalo bi da vidiš zahteve ka:
- `/api/thumbnails/thumbnails/thumb1.jpg`
- `/api/thumbnails/thumbnails/thumb2.jpg`
- itd.

**NE bi trebalo** da vidiš direktne zahteve ka:
- `/thumbnails/thumb1.jpg` (stari način)

---

## Rezime

✅ **Caching radi ako:**
1. Prvi zahtev: Backend log pokazuje "Loading thumbnail from classpath" i "Thumbnail cached"
2. Sledeći zahtevi: Backend log pokazuje "Thumbnail served from cache"
3. Cache stats pokazuje povećanje broja kesiranih thumbnail-a
4. Frontend koristi `/api/thumbnails/` endpoint

❌ **Caching ne radi ako:**
1. Uvek vidiš "Loading thumbnail from classpath" (nikad "served from cache")
2. Cache size je uvek 0
3. Frontend i dalje koristi stari endpoint (direktan pristup)
