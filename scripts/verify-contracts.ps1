Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Invoke-GradleOrFail {
    param(
        [string[]]$Arguments
    )

    & .\gradlew @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle command failed: gradlew $($Arguments -join ' ')"
    }
}

Write-Host "==> Validating contract fixtures JSON..."
$fixturesPath = Join-Path $PSScriptRoot "..\app\src\test\resources\contracts"
if (-not (Test-Path $fixturesPath)) {
    throw "Contracts fixtures path not found: $fixturesPath"
}

$fixtureFiles = Get-ChildItem -Path $fixturesPath -Recurse -Filter *.json
if ($fixtureFiles.Count -eq 0) {
    throw "No JSON fixtures found under $fixturesPath"
}

foreach ($file in $fixtureFiles) {
    try {
        Get-Content -Path $file.FullName -Raw | ConvertFrom-Json | Out-Null
    }
    catch {
        throw "Invalid JSON fixture: $($file.FullName)`n$($_.Exception.Message)"
    }
}
Write-Host "Fixtures validated: $($fixtureFiles.Count)"

Write-Host "==> Running contract unit tests..."
Push-Location (Join-Path $PSScriptRoot "..")
try {
    Invoke-GradleOrFail @("testDebugUnitTest", "--tests", "*ContractsTest")
    Write-Host "==> Running assembleDebug..."
    Invoke-GradleOrFail @("assembleDebug")
}
finally {
    Pop-Location
}

Write-Host "Contract verification completed successfully."
