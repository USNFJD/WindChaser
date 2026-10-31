param([string]$SdkPath, [string]$JavaHome)
$ErrorActionPreference = 'Stop'
if (-not $SdkPath) { $SdkPath = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
if (-not (Test-Path -LiteralPath (Join-Path $SdkPath 'platforms\android-35\android.jar'))) { throw '需要 Android SDK Platform 35。' }
if ($JavaHome) { $env:JAVA_HOME=$JavaHome; $env:PATH=(Join-Path $JavaHome 'bin')+';'+$env:PATH }
Push-Location -LiteralPath $PSScriptRoot
try {
    $sdkProperty=$SdkPath.Replace('\','/').Replace(':','\:')
    Set-Content -LiteralPath 'local.properties' -Encoding ascii -Value ('sdk.dir='+$sdkProperty)
    & '.\gradlew.bat' ':host:assembleDebug' '--console' 'plain'
    if ($LASTEXITCODE -ne 0) { throw '实验工程构建失败。' }
    Write-Host 'APK: host/build/outputs/apk/debug/host-debug.apk'
} finally { Pop-Location }
