Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Test-IsWindowsOs {
    if ($PSVersionTable.PSVersion.Major -ge 6) {
        return $IsWindows
    }
    return $env:OS -like "*Windows*"
}

function Invoke-GradleOrFail {
    param(
        [Parameter(Mandatory = $true)]
        [string] $RepoRoot,

        [string[]] $Arguments
    )

    $gradlewBat = Join-Path $RepoRoot "gradlew.bat"
    $gradlewSh = Join-Path $RepoRoot "gradlew"

    if (Test-IsWindowsOs) {
        if (-not (Test-Path -LiteralPath $gradlewBat)) {
            throw "Gradle wrapper not found: $gradlewBat"
        }
        $process = Start-Process -FilePath $gradlewBat -ArgumentList $Arguments -WorkingDirectory $RepoRoot -NoNewWindow -Wait -PassThru
    }
    else {
        if (-not (Test-Path -LiteralPath $gradlewSh)) {
            throw "Gradle wrapper not found: $gradlewSh"
        }
        $bashArgs = @($gradlewSh) + $Arguments
        $process = Start-Process -FilePath "bash" -ArgumentList $bashArgs -WorkingDirectory $RepoRoot -NoNewWindow -Wait -PassThru
    }

    if ($process.ExitCode -ne 0) {
        throw "Gradle command failed (exit $($process.ExitCode)): gradlew $($Arguments -join ' ')"
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
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Invoke-GradleOrFail -RepoRoot $repoRoot -Arguments @("testDebugUnitTest", "--tests", "*ContractsTest")
Write-Host "==> Running assembleDebug..."
Invoke-GradleOrFail -RepoRoot $repoRoot -Arguments @("assembleDebug")

Write-Host "Contract verification completed successfully."
