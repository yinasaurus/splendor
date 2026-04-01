# Downloads development card JPEGs from the hexanome-04/splendor GitHub repo.
# Vendors them into web/media/development-cards/ for local hosting (recommended for production).
#
# Repo path: client/public/images/development-cards
# Verify the repo license and any third-party / commercial-game rights before redistributing images.
#
# Usage (from repo root):
#   powershell -ExecutionPolicy Bypass -File scripts/fetch-hexanome-dev-cards.ps1

$ErrorActionPreference = "Stop"
$commit = "d1797acf5d43c6bc512b57ef3c1d990006a49a5c"
$base = "https://raw.githubusercontent.com/hexanome-04/splendor/$commit/client/public/images/development-cards"
$outDir = Join-Path (Join-Path (Join-Path (Join-Path $PSScriptRoot "..") "web") "media") "development-cards"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

function Get-HexanomeFileName([int]$n) {
  if ($n -lt 10) { return "0$n.jpg" }
  if ($n -lt 100) { return ('0{0:D2}.jpg' -f $n) }
  return "$n.jpg"
}

$ok = 0
$fail = 0
1..100 | ForEach-Object {
  $name = Get-HexanomeFileName $_
  $url = "$base/$name"
  $dest = Join-Path $outDir $name
  try {
    Invoke-WebRequest -Uri $url -OutFile $dest -UseBasicParsing
    $ok++
    Write-Host "OK $name"
  } catch {
    if (Test-Path $dest) { Remove-Item $dest -Force }
    $fail++
    Write-Warning "Skip $name ($($_.Exception.Message))"
  }
}

Write-Host "Done. Saved $ok files to $outDir (failed/skipped: $fail)."
Write-Host "Then set in web/config.js: window.__SPLENDOR_DEV_CARD_ART_BASE__ = 'media/development-cards';"
