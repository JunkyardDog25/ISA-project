# ============================================================================
# PowerShell skripta za testiranje ograničenja broja komentara (60 po satu)
# ============================================================================

# ----- KONFIGURACIJA -----
$baseUrl = "http://localhost:8080"

#   - GET /api/users - za userId
#   - GET /api/videos/ - za videoId
$userId = "6fb673ca-7c37-4c4e-a9bd-0b533976e1f8"    # Zameni sa stvarnim user ID
$videoId = "e695bd52-d091-4c06-a702-f9cd7b0c2b33"  # Zameni sa stvarnim video ID

# Broj komentara za slanje (treba biti > 60 da testiramo limit)
$totalComments = 65

# ----- HELPER FUNKCIJE -----

function Write-ColorOutput($message, $color = "White") {
    Write-Host $message -ForegroundColor $color
}

function Send-Comment($commentNumber) {
    $body = @{
        userId = $userId
        content = "Test komentar #$commentNumber - Timestamp: $(Get-Date -Format 'HH:mm:ss.fff')"
    } | ConvertTo-Json

    try {
        $response = Invoke-RestMethod -Uri "$baseUrl/api/comments/video/$videoId" `
            -Method Post `
            -ContentType "application/json" `
            -Body $body `
            -ErrorAction Stop

        return @{
            Success = $true
            StatusCode = 200
            Message = "OK"
        }
    }
    catch {
        $statusCode = 0
        $errorMessage = $_.Exception.Message

        if ($_.Exception.Response) {
            $statusCode = [int]$_.Exception.Response.StatusCode
        }

        if ($_.ErrorDetails.Message) {
            try {
                $errorBody = $_.ErrorDetails.Message | ConvertFrom-Json
                $errorMessage = $errorBody.message
            }
            catch {
                $errorMessage = $_.ErrorDetails.Message
            }
        }

        return @{
            Success = $false
            StatusCode = $statusCode
            Message = $errorMessage
        }
    }
}


function Get-CommentLimitStatus() {
    try {
        $response = Invoke-RestMethod -Uri "$baseUrl/api/comments/limit/$userId" -Method Get
        return $response
    }
    catch {
        return $null
    }
}

# ----- GLAVNA SKRIPTA -----

Write-ColorOutput "============================================================" "Cyan"
Write-ColorOutput "   TEST OGRANICENJA BROJA KOMENTARA (60 po satu)" "Cyan"
Write-ColorOutput "============================================================" "Cyan"
Write-ColorOutput ""
Write-ColorOutput "Konfiguracija:" "Yellow"
Write-ColorOutput "  - User ID:  $userId"
Write-ColorOutput "  - Video ID: $videoId"
Write-ColorOutput "  - Broj komentara za slanje: $totalComments"
Write-ColorOutput ""

# Proveri trenutni status limita
Write-ColorOutput "Provera trenutnog statusa limita..." "Yellow"
$limitStatus = Get-CommentLimitStatus
if ($limitStatus) {
    Write-ColorOutput "  - Limit: $($limitStatus.limit) komentara/sat"
    Write-ColorOutput "  - Iskorisceno: $($limitStatus.used)"
    Write-ColorOutput "  - Preostalo: $($limitStatus.remaining)"
    Write-ColorOutput ""

    if ($limitStatus.remaining -eq 0) {
        Write-ColorOutput "UPOZORENJE: Vec ste iskoristili limit! Sačekajte sat vremena." "Red"
        Write-ColorOutput ""
    }
}

Write-ColorOutput "Započinjem slanje $totalComments komentara..." "Yellow"
Write-ColorOutput ""

# Brojači
$successCount = 0
$failedCount = 0
$limitReachedAt = 0

# Slanje komentara
$startTime = Get-Date

for ($i = 1; $i -le $totalComments; $i++) {
    $result = Send-Comment -commentNumber $i

    if ($result.Success) {
        $successCount++
        Write-ColorOutput "[$i/$totalComments]Komentar poslat uspesno (HTTP $($result.StatusCode))" "Green"
    }
    else {
        $failedCount++
        if ($result.StatusCode -eq 429) {
            if ($limitReachedAt -eq 0) {
                $limitReachedAt = $i
                Write-ColorOutput ""
                Write-ColorOutput "========== LIMIT DOSTIGNUT! ==========" "Red"
            }
            Write-ColorOutput "[$i/$totalComments] LIMIT PREKORACEN (HTTP 429): $($result.Message)" "Red"
        }
        else {
            Write-ColorOutput "[$i/$totalComments] Greska (HTTP $($result.StatusCode)): $($result.Message)" "Red"
        }
    }

    # Kratka pauza između zahteva (opciono)
    # Start-Sleep -Milliseconds 50
}

$endTime = Get-Date
$duration = ($endTime - $startTime).TotalSeconds

# ----- REZULTATI -----

Write-ColorOutput ""
Write-ColorOutput "============================================================" "Cyan"
Write-ColorOutput "   REZULTATI TESTA" "Cyan"
Write-ColorOutput "============================================================" "Cyan"
Write-ColorOutput ""
Write-ColorOutput "Statistika:" "Yellow"
Write-ColorOutput "  - Ukupno poslato zahteva: $totalComments"
Write-ColorOutput "  - Uspesnih komentara: $successCount" "Green"
Write-ColorOutput "  - Neuspesnih (limit): $failedCount" "Red"
Write-ColorOutput "  - Trajanje testa: $([math]::Round($duration, 2)) sekundi"
Write-ColorOutput ""

if ($limitReachedAt -gt 0) {
    Write-ColorOutput "Limit dostignut na: $limitReachedAt. komentaru" "Yellow"
}

# Finalni status limita
Write-ColorOutput ""
Write-ColorOutput "Finalni status limita:" "Yellow"
$finalStatus = Get-CommentLimitStatus
if ($finalStatus) {
    Write-ColorOutput "  - Iskorisceno: $($finalStatus.used)/$($finalStatus.limit)"
    Write-ColorOutput "  - Preostalo: $($finalStatus.remaining)"
}

Write-ColorOutput ""
Write-ColorOutput "============================================================" "Cyan"

# Verifikacija
if ($limitReachedAt -eq 61 -or ($limitReachedAt -gt 0 -and $limitReachedAt -le 61)) {
    Write-ColorOutput "TEST USPESAN: Limit od 60 komentara po satu RADI!" "Green"
}
elseif ($limitReachedAt -eq 0 -and $successCount -eq $totalComments) {
    Write-ColorOutput "Svi komentari su prosli - limit nije dostignut." "Yellow"
    Write-ColorOutput "   Moguće da je user vec imao komentare u poslednjih sat vremena," "Yellow"
    Write-ColorOutput "   ili limit nije pravilno konfigurisan." "Yellow"
}
else {
    Write-ColorOutput "Neocekivano ponašanje - proveri logove backend-a." "Yellow"
}

Write-ColorOutput "============================================================" "Cyan"

