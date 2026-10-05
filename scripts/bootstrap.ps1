# One command from a fresh clone to a built APK on Windows.
#
#   powershell -ExecutionPolicy Bypass -File scripts/bootstrap.ps1
#
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root

function Info($m) { Write-Host "    $m" }
function Bold($m) { Write-Host "`n==> $m" -ForegroundColor Cyan }
function Die($m)  { Write-Host "`nX $m" -ForegroundColor Red; exit 1 }

Bold "Jarvis bootstrap"

# --- Java 17 ----------------------------------------------------------------
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    $candidates = @(
        "$env:LOCALAPPDATA\Programs\Android Studio\jbr",
        "C:\Program Files\Android\Android Studio\jbr"
    )
    foreach ($c in $candidates) {
        if (Test-Path "$c\bin\java.exe") {
            $env:JAVA_HOME = $c
            $env:PATH = "$c\bin;$env:PATH"
            Info "Using Android Studio's bundled JDK: $c"
            break
        }
    }
}
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Die "Java 17 not found. Install it (see docs\TROUBLESHOOTING.md), then re-run."
}
Info "Java found."

Bold "Fetching native engines (llama.cpp + whisper.cpp)…"
& bash scripts/fetch_native.sh

Bold "Building APK with the native engine…"
& .\gradlew.bat assembleDebug -Pjarvis.buildNative=true

$apk = "app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $apk)) { Die "Build finished but $apk was not produced." }
Bold "Built: $apk"

if ((Get-Command adb -ErrorAction SilentlyContinue) -and ((adb devices) -match "device$")) {
    Bold "Installing to the connected device…"
    adb install -r $apk
    Bold "Done. Open 'Jarvis' on your phone."
} else {
    Bold "No device connected. When your phone is ready:"
    Info "adb install -r $apk"
}
