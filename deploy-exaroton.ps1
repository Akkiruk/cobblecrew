# Deploy CobbleCrew to Exaroton
# Usage: .\deploy-exaroton.ps1

$ErrorActionPreference = "Stop"

# 1. Load configuration from .env
$envFile = Join-Path $PSScriptRoot ".env"
if (-not (Test-Path $envFile)) {
    Write-Error "Error: .env file not found at $envFile. Please create one with EXAROTON_API_TOKEN and EXAROTON_SERVER_ID."
}

Get-Content $envFile | Where-Object { $_ -match '=' -and -not ($_ -match '^#') } | ForEach-Object {
    $key, $value = $_ -split '=', 2
    Set-Variable -Name $key -Value $value -Scope Script
}

if (-not $EXAROTON_API_TOKEN -or -not $EXAROTON_SERVER_ID) {
    Write-Error "Error: EXAROTON_API_TOKEN or EXAROTON_SERVER_ID missing in .env file."
}

# 2. Build the mod
Write-Host "Building CobbleCrew..." -ForegroundColor Cyan
# Run the build command from context: clean :fabric:build :neoforge:build
# Assuming we want to deploy the Fabric version to the server as per most Cobblemon servers, but let's check what's produced.
# If the server is NeoForge, the user should adjust. We'll default to uploading both or asking? 
# The user's prompt implies "deploy updates... to the server files directly". 
# Usually a server is one loader. I'll search for fabric jar first as it's common for Cobblemon.
# Actually, I'll upload the Fabric jar by default as Cobblemon is primarily Fabric, 
# but I'll add a check or comment.

$gradleProcess = Start-Process -FilePath ".\gradlew.bat" -ArgumentList "clean :fabric:build" -Wait -NoNewWindow -PassThru
if ($gradleProcess.ExitCode -ne 0) {
    Write-Error "Build failed with exit code $($gradleProcess.ExitCode)."
}

# 3. Find the built jar
$buildDir = Join-Path $PSScriptRoot "fabric\build\libs"
$jarFile = Get-ChildItem -Path $buildDir -Filter "cobblecrew-fabric-*.jar" | 
    Where-Object { $_.Name -notmatch "-dev" -and $_.Name -notmatch "-sources" } | 
    Sort-Object LastWriteTime -Descending | Select-Object -First 1

if (-not $jarFile) {
    Write-Error "Could not find built JAR file in $buildDir"
}

Write-Host "Found JAR: $($jarFile.Name)" -ForegroundColor Green

# 4. Exaroton API Helper
function Invoke-ExarotonApi {
    param(
        [string]$Method,
        [string]$Uri,
        [string]$ConfigType = "application/json",
        $Body
    )
    
    $headers = @{
        "Authorization" = "Bearer $EXAROTON_API_TOKEN"
    }

    try {
        $params = @{
            Uri = "https://api.exaroton.com/v1/servers/$EXAROTON_SERVER_ID/$Uri"
            Method = $Method
            Headers = $headers
            ContentType = $ConfigType
        }
        if ($Body) { $params.Body = $Body }
        
        return Invoke-RestMethod @params
    } catch {
        Write-Host "API Request Failed." -ForegroundColor Red
        if ($_.Exception.Response) {
             $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
             $responseText = $reader.ReadToEnd()
             Write-Host "Response Body: $responseText" -ForegroundColor Red 
        } else {
             Write-Host "Exception: $_" -ForegroundColor Red
        }
        throw $_
    }
}

# 5. List existing mods to find old version
Write-Host "Checking remote mods folder..." -ForegroundColor Cyan
$modsInfo = Invoke-ExarotonApi -Method GET -Uri "files/info/mods/"

if ($modsInfo.success -eq $true) {
    $existingMod = $modsInfo.data.children | Where-Object { $_.name -match "^cobblecrew-fabric" }
    
    if ($existingMod) {
        Write-Host "Found old version: $($existingMod.name). Deleting..." -ForegroundColor Yellow
        Invoke-ExarotonApi -Method DELETE -Uri "files/data/mods/$($existingMod.name)/" | Out-Null
    }
}

# 6. Upload new jar
Write-Host "Uploading $($jarFile.Name)..." -ForegroundColor Cyan
$fileContent = [System.IO.File]::ReadAllBytes($jarFile.FullName)
Invoke-ExarotonApi -Method PUT -Uri "files/data/mods/$($jarFile.Name)/" -ConfigType "application/octet-stream" -Body $fileContent | Out-Null
Write-Host "Upload complete." -ForegroundColor Green

# 7. Restart or Start Server
Write-Host "Starting/restarting server..." -ForegroundColor Cyan
$serverStatus = Invoke-ExarotonApi -Method GET -Uri ""
if ($serverStatus.data.status -eq 0) {
    # Server is offline — start it
    Invoke-ExarotonApi -Method GET -Uri "start/" | Out-Null
    Write-Host "Server start triggered." -ForegroundColor Green
} else {
    # Server is online — restart it
    Invoke-ExarotonApi -Method GET -Uri "restart/" | Out-Null
    Write-Host "Server restart triggered." -ForegroundColor Green
}
