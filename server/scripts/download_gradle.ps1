param(
  [string]$DistRoot,
  [string]$Version = "8.5"
)

$zip = Join-Path $env:TEMP "gradle-$Version-bin.zip"
$url = "https://services.gradle.org/distributions/gradle-$Version-bin.zip"
Write-Host "Downloading $url to $zip"
Invoke-WebRequest -Uri $url -OutFile $zip
Write-Host "Extracting $zip to $DistRoot"
if (!(Test-Path $DistRoot)) { New-Item -ItemType Directory -Path $DistRoot -Force | Out-Null }
Expand-Archive -Path $zip -DestinationPath $DistRoot -Force
Write-Host "Extraction complete"
