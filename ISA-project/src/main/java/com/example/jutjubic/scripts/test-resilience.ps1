# PowerShell skripta za testiranje otpornosti klastera

Write-Host "=== TESTIRANJE OTPORNOSTI KLASTERA ===" -ForegroundColor Cyan
Write-Host ""

# Funkcija za testiranje API-ja
function Test-API {
    param($endpoint, $description)
    Write-Host "Test: $description" -ForegroundColor Yellow
    Write-Host "  Endpoint: $endpoint" -ForegroundColor Gray
    try {
        $response = Invoke-WebRequest -Uri $endpoint -UseBasicParsing -ErrorAction Stop
        Write-Host "  Status: $($response.StatusCode) - OK" -ForegroundColor Green
        return $true
    } catch {
        Write-Host "  Status: ERROR - $($_.Exception.Message)" -ForegroundColor Red
        return $false
    }
    Write-Host ""
}

# Funkcija za proveru statusa replika
function Show-ReplicaStatus {
    Write-Host "Status replika:" -ForegroundColor Yellow
    $app1 = docker ps --filter "name=isa-app-1" --format "{{.Status}}"
    $app2 = docker ps --filter "name=isa-app-2" --format "{{.Status}}"
    Write-Host "  isa-app-1: $(if ($app1) { $app1 } else { 'STOPPED' })" -ForegroundColor $(if ($app1) { "Green" } else { "Red" })
    Write-Host "  isa-app-2: $(if ($app2) { $app2 } else { 'STOPPED' })" -ForegroundColor $(if ($app2) { "Green" } else { "Red" })
    Write-Host ""
}

# Funkcija za čekanje
function Wait-ForServices {
    param($seconds = 10)
    Write-Host "Cekanje $seconds sekundi da se servisi stabilizuju..." -ForegroundColor Gray
    Start-Sleep -Seconds $seconds
    Write-Host ""
}

# ============================================
# TEST 1: Osnovno testiranje - sve radi
# ============================================
Write-Host "=== TEST 1: Osnovno testiranje ===" -ForegroundColor Magenta
Write-Host ""

Show-ReplicaStatus

$healthOk = Test-API "http://localhost:8081/actuator/health" "Health check"
$usersOk = Test-API "http://localhost:8081/api/users" "GET /api/users"

if ($healthOk -and $usersOk) {
    Write-Host "[OK] Osnovno testiranje: USPESNO" -ForegroundColor Green
} else {
    Write-Host "[FAIL] Osnovno testiranje: NEUSPESNO" -ForegroundColor Red
}
Write-Host ""
Write-Host "Pritisni Enter za nastavak..." -ForegroundColor Cyan
Read-Host

# ============================================
# TEST 2: Pad jedne replike
# ============================================
Write-Host "=== TEST 2: Pad jedne replike (isa-app-1) ===" -ForegroundColor Magenta
Write-Host ""

Write-Host "Zaustavljanje replike isa-app-1..." -ForegroundColor Yellow
docker stop isa-app-1
Wait-ForServices 5

Show-ReplicaStatus

Write-Host "Testiranje da li API i dalje radi preko preostale replike..." -ForegroundColor Yellow
$healthOk2 = Test-API "http://localhost:8081/actuator/health" "Health check"
$usersOk2 = Test-API "http://localhost:8081/api/users" "GET /api/users"

if ($healthOk2 -and $usersOk2) {
    Write-Host "[OK] Test pada replike: USPESNO - Aplikacija i dalje radi!" -ForegroundColor Green
} else {
    Write-Host "[FAIL] Test pada replike: NEUSPESNO" -ForegroundColor Red
}
Write-Host ""
Write-Host "Pritisni Enter za nastavak..." -ForegroundColor Cyan
Read-Host

# ============================================
# TEST 3: Ponovno podizanje replike
# ============================================
Write-Host "=== TEST 3: Ponovno podizanje replike (isa-app-1) ===" -ForegroundColor Magenta
Write-Host ""

Write-Host "Pokretanje replike isa-app-1..." -ForegroundColor Yellow
docker start isa-app-1
Wait-ForServices 20

Show-ReplicaStatus

Write-Host "Testiranje da li obe replike rade..." -ForegroundColor Yellow
$healthOk3 = Test-API "http://localhost:8081/actuator/health" "Health check"
$usersOk3 = Test-API "http://localhost:8081/api/users" "GET /api/users"

if ($healthOk3 -and $usersOk3) {
    Write-Host "[OK] Test ponovnog podizanja: USPESNO - Obe replike rade!" -ForegroundColor Green
} else {
    Write-Host "[FAIL] Test ponovnog podizanja: NEUSPESNO" -ForegroundColor Red
}
Write-Host ""
Write-Host "Pritisni Enter za nastavak..." -ForegroundColor Cyan
Read-Host

# ============================================
# TEST 4: Parcijalni gubitak konekcije - RabbitMQ
# ============================================
Write-Host "=== TEST 4: Parcijalni gubitak konekcije - RabbitMQ ===" -ForegroundColor Magenta
Write-Host ""

Write-Host "Zaustavljanje RabbitMQ servisa..." -ForegroundColor Yellow
docker stop isa-rabbitmq
Wait-ForServices 5

Write-Host "Testiranje da li API endpointi koji ne zavise od MQ i dalje rade..." -ForegroundColor Yellow
Write-Host "Napomena: Liveness endpoint proverava samo da aplikacija zivi, ne i dependency-je" -ForegroundColor Gray
$livenessOk4 = Test-API "http://localhost:8081/actuator/health/liveness" "Liveness check (proverava samo da aplikacija zivi)"
$usersOk4 = Test-API "http://localhost:8081/api/users" "GET /api/users"

if ($livenessOk4 -and $usersOk4) {
    Write-Host "[OK] Test gubitka MQ: USPESNO - API i dalje radi za operacije koje ne zavise od MQ!" -ForegroundColor Green
    Write-Host "  Liveness: OK - Aplikacija zivi i odgovara na zahteve" -ForegroundColor Green
    Write-Host "  API endpointi koji ne zavise od MQ: OK" -ForegroundColor Green
} else {
    Write-Host "[FAIL] Test gubitka MQ: NEUSPESNO" -ForegroundColor Red
}

Write-Host ""
Write-Host "Ponovno pokretanje RabbitMQ..." -ForegroundColor Yellow
docker start isa-rabbitmq
Wait-ForServices 10
Write-Host ""
Write-Host "Pritisni Enter za nastavak..." -ForegroundColor Cyan
Read-Host

# ============================================
# TEST 5: Parcijalni gubitak konekcije - MySQL
# ============================================
Write-Host "=== TEST 5: Parcijalni gubitak konekcije - MySQL ===" -ForegroundColor Magenta
Write-Host ""

Write-Host "Zaustavljanje MySQL servisa..." -ForegroundColor Yellow
docker stop isa-mysql
Wait-ForServices 5

Write-Host "Testiranje da li aplikacija i dalje odgovara (mada ce DB operacije pasti)..." -ForegroundColor Yellow
Write-Host "Napomena: Liveness endpoint proverava samo da aplikacija zivi, ne i dependency-je" -ForegroundColor Gray
$livenessOk5 = Test-API "http://localhost:8081/actuator/health/liveness" "Liveness check (proverava samo da aplikacija zivi)"
$usersOk5 = Test-API "http://localhost:8081/api/users" "GET /api/users"

if ($livenessOk5) {
    Write-Host "[OK] Liveness check i dalje radi - aplikacija zivi!" -ForegroundColor Green
} else {
    Write-Host "[FAIL] Liveness check ne radi" -ForegroundColor Red
}

if (-not $usersOk5) {
    Write-Host "[OK] Ocekivano: API endpointi koji zahtevaju bazu ne rade (to je normalno)" -ForegroundColor Yellow
    Write-Host "  Aplikacija i dalje odgovara (liveness OK), ali DB operacije ne rade" -ForegroundColor Gray
    Write-Host "  Ovo je primer parcijalnog gubitka konekcije - aplikacija zivi, ali neki servisi ne rade" -ForegroundColor Gray
} else {
    Write-Host "[?] API endpointi i dalje rade (mozda je cache aktivan)" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Ponovno pokretanje MySQL..." -ForegroundColor Yellow
docker start isa-mysql
Wait-ForServices 15
Write-Host ""

# ============================================
# FINALNI TEST: Sve je vraćeno
# ============================================
Write-Host "=== FINALNI TEST: Provera da sve radi ===" -ForegroundColor Magenta
Write-Host ""

Show-ReplicaStatus

$healthFinal = Test-API "http://localhost:8081/actuator/health" "Health check"
$usersFinal = Test-API "http://localhost:8081/api/users" "GET /api/users"

if ($healthFinal -and $usersFinal) {
    Write-Host "[OK] FINALNI TEST: USPESNO - Sve radi normalno!" -ForegroundColor Green
} else {
    Write-Host "[FAIL] FINALNI TEST: NEUSPESNO" -ForegroundColor Red
}

Write-Host ""
Write-Host "=== TESTIRANJE ZAVRSENO ===" -ForegroundColor Cyan
Write-Host ""
Write-Host 'HAProxy statistika: http://localhost:8404/stats' -ForegroundColor Cyan
Write-Host ""
