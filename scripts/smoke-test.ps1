param([string]$BaseUrl = 'http://localhost:8080')
$ErrorActionPreference = 'Stop'
function Assert-That($Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}
function Basic-Header([string]$Email, [string]$Password) {
    $encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("${Email}:${Password}"))
    return @{ Authorization = "Basic $encoded" }
}
function Post-Json([string]$Path, $Body, $Headers) {
    Invoke-RestMethod -Uri "$BaseUrl$Path" -Method Post -ContentType 'application/json' -Body ($Body | ConvertTo-Json -Compress) -Headers $Headers
}
$health = Invoke-RestMethod "$BaseUrl/actuator/health"
Assert-That ($health.status -eq 'UP') 'Health check failed'
$suffix = [Guid]::NewGuid().ToString('N').Substring(0, 10)
$password = 'SmokeTest!Password2026'
$email = "smoke-$suffix@example.com"
$account = Post-Json '/api/v1/auth/register' @{ name = 'Smoke Test Learner'; email = $email; password = $password } @{}
$learnerHeaders = Basic-Header $email $password
$adminHeaders = Basic-Header 'admin@learnreserve.local' 'DemoAdmin!2026'
$resource = Post-Json '/api/v1/resources' @{ name = "Smoke Room $suffix"; description = 'Created by the repeatable demo smoke test'; type = 'STUDY_ROOM'; location = 'Demo' } $adminHeaders
$startsAt = [DateTime]::UtcNow.Date.AddDays(2).AddHours(10).ToString("yyyy-MM-ddTHH:mm:ssZ")
$endsAt = [DateTime]::UtcNow.Date.AddDays(2).AddHours(11).ToString("yyyy-MM-ddTHH:mm:ssZ")
$slot = Post-Json "/api/v1/resources/$($resource.id)/availability" @{ startsAt = $startsAt; endsAt = $endsAt } $adminHeaders
$bookingBody = @{ resourceId = $resource.id; startsAt = $startsAt; endsAt = $endsAt }
$booking = Post-Json '/api/v1/bookings' $bookingBody $learnerHeaders
Assert-That ($booking.status -eq 'CONFIRMED') 'Booking was not confirmed'
$conflictSeen = $false
try { Post-Json '/api/v1/bookings' $bookingBody $learnerHeaders | Out-Null }
catch {
    if ([int]$_.Exception.Response.StatusCode -eq 409) { $conflictSeen = $true } else { throw }
}
Assert-That $conflictSeen 'Expected duplicate booking to return HTTP 409'
$cancelled = Post-Json "/api/v1/bookings/$($booking.id)/cancel" @{} $learnerHeaders
Assert-That ($cancelled.status -eq 'CANCELLED') 'Cancellation failed'
$rebooked = Post-Json '/api/v1/bookings' $bookingBody $learnerHeaders
Assert-That ($rebooked.status -eq 'CONFIRMED') 'Cancelled interval was not released'
$history = Invoke-RestMethod "$BaseUrl/api/v1/bookings" -Headers $learnerHeaders
Assert-That ($history.totalElements -eq 2) 'History should retain both reservations'
Post-Json "/api/v1/bookings/$($rebooked.id)/cancel" @{} $learnerHeaders | Out-Null
Post-Json "/api/v1/resources/$($resource.id)/availability/$($slot.id)/close" @{} $adminHeaders | Out-Null
Invoke-RestMethod "$BaseUrl/api/v1/resources/$($resource.id)/active" -Method Patch -Headers $adminHeaders -ContentType 'application/json' -Body '{"active":false}' | Out-Null
Write-Host 'PASS: health, registration, resource creation, availability, booking, conflict, cancellation, rebooking, history and cleanup.'
