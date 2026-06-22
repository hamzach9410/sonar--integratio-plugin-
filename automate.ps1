# ==============================================================================
# SonarQube Bitbucket Plugin - Automation Suite
# usage: .\automate.ps1 -Action Build (or Deploy, or Run)
# ==============================================================================

param (
    [Parameter(Mandatory=$true)]
    [ValidateSet("Build", "Deploy", "Run", "Check", "Test")]
    [string]$Action,

    [string]$BitbucketUrl = "http://localhost:7990/bitbucket",
    [string]$AdminUser = "admin",
    [string]$AdminPass = "admin"
)

$ErrorActionPreference = "Stop"
$ProjectDir = Get-Location
$JarPath = "$ProjectDir\target\sonar-integration-plugin-1.0.0.jar"

function Check-Environment {
    Write-Host "--- Checking Development Environment ---" -ForegroundColor Cyan
    
    # 1. Check Java Version
    $javaVersion = try { & java -version 2>&1 | Out-String } catch { $_.Exception.Message }
    if ($javaVersion -like "*version ""1.8*" -or $javaVersion -like "*version ""11*") {
        Write-Host "[OK] Java version is compatible (8/11)." -ForegroundColor Green
    } else {
        Write-Host "[WARNING] Java version mismatch! Found: $javaVersion" -ForegroundColor Yellow
        Write-Host "Note: Bitbucket 6.10.7 requires Java 8 or 11. Current Java 21 may cause build errors." -ForegroundColor Gray
    }

    # 2. Check Atlassian SDK
    try {
        $atlasVersion = & atlas-version | Out-String
        Write-Host "[OK] Atlassian SDK is installed." -ForegroundColor Green
    } catch {
        Write-Host "[ERROR] Atlassian SDK not found in PATH." -ForegroundColor Red
        Write-Host "Please install it from: https://developer.atlassian.com/server/framework/atlassian-sdk/downloads/" -ForegroundColor Gray
        return $false
    }
    return $true
}

function Build-Plugin {
    if (-not (Check-Environment)) { return }
    
    Write-Host "--- Building Plugin JAR ---" -ForegroundColor Cyan
    & atlas-package
    
    if (Test-Path $JarPath) {
        Write-Host "[SUCCESS] Plugin built: $JarPath" -ForegroundColor Green
    } else {
        Write-Host "[ERROR] Build failed. JAR not found at $JarPath" -ForegroundColor Red
    }
}

function Run-Local {
    if (-not (Check-Environment)) { return }
    
    Write-Host "--- Launching Local Bitbucket Instance (atlas-run) ---" -ForegroundColor Cyan
    Write-Host "Press Ctrl+C to stop when finished." -ForegroundColor Gray
    & atlas-run
}

function Deploy-Plugin {
    if (-not (Test-Path $JarPath)) {
        Write-Host "[ERROR] Plugin JAR not found. Please run -Action Build first." -ForegroundColor Red
        return
    }

    Write-Host "--- Deploying to Bitbucket ($BitbucketUrl) ---" -ForegroundColor Cyan
    
    # Bitbucket UPM (Universal Plugin Manager) REST API
    $auth = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("$($AdminUser):$($AdminPass)"))
    $headers = @{
        "Authorization" = "Basic $auth"
        "Accept"        = "application/json"
    }

    # 1. Get UPM Token
    $upmUrl = "$BitbucketUrl/rest/plugins/1.0/?os_authType=basic"
    $response = Invoke-WebRequest -Uri $upmUrl -Method Get -Headers $headers
    $token = $response.Headers["upm-token"]

    # 2. Upload JAR
    $uploadUrl = "$BitbucketUrl/rest/plugins/1.0/?token=$token"
    $fileStream = [System.IO.File]::OpenRead($JarPath)
    
    try {
        Invoke-RestMethod -Uri $uploadUrl -Method Post -Headers $headers -InFile $JarPath -ContentType "application/java-archive"
        Write-Host "[SUCCESS] Plugin deployed successfully!" -ForegroundColor Green
    } catch {
        Write-Host "[ERROR] Deployment failed. Check your URL, credentials, and Bitbucket logs." -ForegroundColor Red
        Write-Host $_.Exception.Message -ForegroundColor Gray
    } finally {
        $fileStream.Close()
    }
}

function Test-Plugin {
    if (-not (Check-Environment)) { return }
    
    Write-Host "--- Running Unit Tests ---" -ForegroundColor Cyan
    & atlas-unit-test
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "[SUCCESS] All tests passed!" -ForegroundColor Green
    } else {
        Write-Host "[ERROR] Tests failed." -ForegroundColor Red
    }
}

# --- Execution ---
switch ($Action) {
    "Check"  { Check-Environment }
    "Build"  { Build-Plugin }
    "Run"    { Run-Local }
    "Deploy" { Deploy-Plugin }
    "Test"   { Test-Plugin }
}
