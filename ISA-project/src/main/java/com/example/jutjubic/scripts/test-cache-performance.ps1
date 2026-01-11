# ============================================================================
# PowerShell skripta za testiranje performansi keširanja
# ============================================================================

# ----- KONFIGURACIJA -----
$baseUrl = "http://localhost:8080"
$videoId = "e695bd52-d091-4c06-a702-f9cd7b0c2b33"  # Zameni sa stvarnim video ID

# Broj ponavljanja za preciznije merenje
$iterations = 10

# ----- HELPER FUNKCIJE -----

function Write-ColorOutput($message, $color = "White") {
    Write-Host $message -ForegroundColor $color
}

function Measure-ApiCall($endpoint, $description) {
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()

    try {
        $response = Invoke-RestMethod -Uri "$baseUrl$endpoint" -Method Get -ErrorAction Stop
        $stopwatch.Stop()

        return @{
            Success = $true
            Duration = $stopwatch.ElapsedMilliseconds
            Data = $response
        }
    }
    catch {
        $stopwatch.Stop()
        return @{
            Success = $false
            Duration = $stopwatch.ElapsedMilliseconds
            Error = $_.Exception.Message
        }
    }
}

function Clear-Cache() {
    try {
        # Ako imas endpoint za brisanje kesa
        Invoke-RestMethod -Uri "$baseUrl/api/cache/clear" -Method Post -ErrorAction SilentlyContinue
        return $true
    }
    catch {
        return $false
    }
}

# ----- GLAVNA SKRIPTA -----

Write-ColorOutput "============================================================" "Cyan"
Write-ColorOutput "   TEST PERFORMANSI KESIRANJA" "Cyan"
Write-ColorOutput "============================================================" "Cyan"
Write-ColorOutput ""

$endpoint = "/api/comments/video/$videoId"

Write-ColorOutput "Endpoint: $endpoint" "Yellow"
Write-ColorOutput "Broj iteracija: $iterations" "Yellow"
Write-ColorOutput ""

# Pokusaj ocistiti kes
Write-ColorOutput "Pokusaj ciscenja kesa..." "Yellow"
$cleared = Clear-Cache
if ($cleared) {
    Write-ColorOutput "  Kes ociscen." "Green"
} else {
    Write-ColorOutput "  Endpoint za ciscenje kesa nije dostupan (to je OK)." "Gray"
}
Write-ColorOutput ""

# ----- TEST 1: Prvi zahtev (očekuje se cache miss) -----
Write-ColorOutput "============================================================" "Yellow"
Write-ColorOutput "TEST 1: Prvi zahtev (cache MISS - ide na bazu)" "Yellow"
Write-ColorOutput "============================================================" "Yellow"

Start-Sleep -Milliseconds 500  # Kratka pauza

$firstCall = Measure-ApiCall -endpoint $endpoint -description "Prvi poziv"

if ($firstCall.Success) {
    Write-ColorOutput "  Status: USPESNO" "Green"
    Write-ColorOutput "  Vreme: $($firstCall.Duration) ms" "Cyan"
    $dataCount = if ($firstCall.Data -is [Array]) { $firstCall.Data.Count } else { 1 }
    Write-ColorOutput "  Broj vracenih stavki: $dataCount"
} else {
    Write-ColorOutput "  Status: GRESKA - $($firstCall.Error)" "Red"
    exit
}

Write-ColorOutput ""

# ----- TEST 2: Ponovljeni zahtevi (očekuje se cache hit) -----
Write-ColorOutput "============================================================" "Yellow"
Write-ColorOutput "TEST 2: Ponovljeni zahtevi (cache HIT - iz kesa)" "Yellow"
Write-ColorOutput "============================================================" "Yellow"

$cachedTimes = @()

for ($i = 1; $i -le $iterations; $i++) {
    $result = Measure-ApiCall -endpoint $endpoint -description "Iteracija $i"

    if ($result.Success) {
        $cachedTimes += $result.Duration
        $color = if ($result.Duration -lt $firstCall.Duration) { "Green" } else { "Yellow" }
        Write-ColorOutput "  [$i/$iterations] Vreme: $($result.Duration) ms" $color
    } else {
        Write-ColorOutput "  [$i/$iterations] GRESKA: $($result.Error)" "Red"
    }

    Start-Sleep -Milliseconds 100
}

Write-ColorOutput ""

# ----- REZULTATI -----
Write-ColorOutput "============================================================" "Cyan"
Write-ColorOutput "   REZULTATI ANALIZE" "Cyan"
Write-ColorOutput "============================================================" "Cyan"
Write-ColorOutput ""

$avgCached = [math]::Round(($cachedTimes | Measure-Object -Average).Average, 2)
$minCached = ($cachedTimes | Measure-Object -Minimum).Minimum
$maxCached = ($cachedTimes | Measure-Object -Maximum).Maximum

Write-ColorOutput "Performanse:" "Yellow"
Write-ColorOutput "  - Prvi zahtev (bez kesa):    $($firstCall.Duration) ms" "Cyan"
Write-ColorOutput "  - Prosek (sa kesom):         $avgCached ms" "Green"
Write-ColorOutput "  - Min (sa kesom):            $minCached ms" "Green"
Write-ColorOutput "  - Max (sa kesom):            $maxCached ms" "Yellow"
Write-ColorOutput ""

$speedup = if ($avgCached -gt 0) { [math]::Round($firstCall.Duration / $avgCached, 2) } else { "N/A" }
$improvement = [math]::Round((($firstCall.Duration - $avgCached) / $firstCall.Duration) * 100, 1)

Write-ColorOutput "Analiza ubrzanja:" "Yellow"
Write-ColorOutput "  - Faktor ubrzanja:           ${speedup}x" "Cyan"
Write-ColorOutput "  - Poboljsanje:               $improvement %" "Cyan"
Write-ColorOutput ""

# Ocena
Write-ColorOutput "============================================================" "Cyan"
if ($avgCached -lt $firstCall.Duration * 0.5) {
    Write-ColorOutput "ODLICNO: Kesiranje znacajno ubrzava odgovore!" "Green"
} elseif ($avgCached -lt $firstCall.Duration * 0.8) {
    Write-ColorOutput "DOBRO: Kesiranje pokazuje poboljsanje performansi." "Yellow"
} else {
    Write-ColorOutput "PROVERI: Kesiranje možda ne radi kako treba." "Red"
    Write-ColorOutput "  - Proveri da li je @Cacheable pravilno konfigurisan" "Yellow"
    Write-ColorOutput "  - Proveri Redis/Caffeine konfiguraciju" "Yellow"
}
Write-ColorOutput "============================================================" "Cyan"

