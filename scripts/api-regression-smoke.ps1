param(
  [string]$BaseUrl = "http://localhost:8080",
  [string]$RoomCode = "",
  [switch]$VerboseOutput
)

$ErrorActionPreference = "Stop"

function Assert-True {
  param(
    [bool]$Condition,
    [string]$Message
  )
  if (-not $Condition) {
    throw "ASSERT FAILED: $Message"
  }
}

function Invoke-JsonPost {
  param(
    [string]$Url,
    [hashtable]$Payload
  )
  $json = $Payload | ConvertTo-Json -Depth 8
  if ($VerboseOutput) {
    Write-Host "POST $Url"
    Write-Host "BODY $json"
  }
  return Invoke-RestMethod -Method Post -Uri $Url -ContentType "application/json" -Body $json
}

function Invoke-JsonGet {
  param(
    [string]$Url
  )
  if ($VerboseOutput) {
    Write-Host "GET  $Url"
  }
  return Invoke-RestMethod -Method Get -Uri $Url
}

Write-Host "== Splendor API regression smoke test =="
Write-Host "Base URL: $BaseUrl"

# Health-ish check
$null = Invoke-JsonGet "$BaseUrl/api/state?room=Room%20A&name="

$hostName = "SmokeHost"
$guestName = "SmokeGuest"
$ownerReady = $true
$guestReady = $true

# 1) Create room
$createPayload = @{
  room      = $RoomCode
  ownerName = $hostName
  numPlayers = 2
  p1Type    = "human"
  p2Type    = "human"
  p3Type    = "human"
  p4Type    = "human"
}
$createResp = Invoke-JsonPost "$BaseUrl/api/room/create" $createPayload
Assert-True ($createResp.success -eq $true) "room/create success should be true"
Assert-True (-not [string]::IsNullOrWhiteSpace([string]$createResp.room)) "room/create should return room code"
$room = [string]$createResp.room
Write-Host "Created room: $room"

# 2) Join second player
$joinResp = Invoke-JsonPost "$BaseUrl/api/room/join" @{ room = $room; name = $guestName }
Assert-True ($joinResp.success -eq $true) "room/join success should be true"

# 3) Ready state toggles
$readyOwnerResp = Invoke-JsonPost "$BaseUrl/api/room/ready" @{ room = $room; name = $hostName; ready = $ownerReady }
Assert-True ($readyOwnerResp.success -eq $true) "owner ready toggle should succeed"
$readyGuestResp = Invoke-JsonPost "$BaseUrl/api/room/ready" @{ room = $room; name = $guestName; ready = $guestReady }
Assert-True ($readyGuestResp.success -eq $true) "guest ready toggle should succeed"

# 4) Start game (host action)
$startResp = Invoke-JsonPost "$BaseUrl/api/room/start" @{ room = $room; ownerName = $hostName }
Assert-True ($startResp.success -eq $true) "room/start success should be true"

# 5) State payload shape
$stateHost = Invoke-JsonGet "$BaseUrl/api/state?room=$room&name=$hostName"
Assert-True ($null -ne $stateHost.players) "state should include players"
Assert-True ($null -ne $stateHost.levels) "state should include levels"
Assert-True ($null -ne $stateHost.gems) "state should include board gems"
Assert-True ($null -ne $stateHost.recentActions) "state should include recent actions"

# 6) Perform one legal player action (take 3 different)
$takeResp = Invoke-JsonPost "$BaseUrl/api/action" @{
  room = $room
  name = $hostName
  type = "takeGems"
  gems = @("R", "E", "S")
}
Assert-True ($takeResp.success -eq $true) "takeGems action should succeed"

$stateAfter = Invoke-JsonGet "$BaseUrl/api/state?room=$room&name=$hostName"
$actions = @($stateAfter.recentActions)
Assert-True ($actions.Count -gt 0) "recentActions should have entries after action"

Write-Host "PASS: API smoke regression completed successfully."
Write-Host "Checked: create/join/ready/start/state/action/recentActions."
