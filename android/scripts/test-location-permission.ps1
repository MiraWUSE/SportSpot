param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^emulator-\d+$')]
    [string]$DeviceSerial
)

$ErrorActionPreference = 'Stop'
$androidDirectory = Split-Path -Parent $PSScriptRoot
$adbPath = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
$packageName = 'com.mirawuse.sportspot'

function Invoke-TestAdb {
    $output = & $adbPath -s $DeviceSerial @args
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $($args[0])" }
    $output
}

# Permission preparation is restricted to an emulator, never a personal phone.
if ((Invoke-TestAdb shell getprop ro.kernel.qemu) -ne '1') {
    throw 'Select a running Android emulator.'
}

& (Join-Path $androidDirectory 'gradlew.bat') -p $androidDirectory :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Test APK build failed.' }

Invoke-TestAdb install -r --no-incremental (Join-Path $androidDirectory 'app/build/outputs/apk/debug/app-debug.apk')
Invoke-TestAdb install -r --no-incremental (Join-Path $androidDirectory 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')

# Do this before instrumentation: revoking access can terminate the app process.
# Preserve Room and preferences; prepare a retryable denial for either request history.
Invoke-TestAdb shell am force-stop $packageName
foreach ($permission in @('android.permission.ACCESS_FINE_LOCATION', 'android.permission.ACCESS_COARSE_LOCATION')) {
    Invoke-TestAdb shell pm revoke $packageName $permission
    Invoke-TestAdb shell pm clear-permission-flags $packageName $permission user-fixed
    Invoke-TestAdb shell pm set-permission-flags $packageName $permission user-set
}

$result = (Invoke-TestAdb shell am instrument -w -r -e class com.mirawuse.sportspot.LocationPermissionTest com.mirawuse.sportspot.test/androidx.test.runner.AndroidJUnitRunner) -join "`n"
$result
# ADB can return exit code 0 for a failed or skipped JUnit test.
if ($result -notmatch 'OK \(1 test\)' -or $result -match 'FAILURES!!!|INSTRUMENTATION_STATUS_CODE: -\d+|INSTRUMENTATION_FAILED') {
    throw 'Location permission test did not pass without skips.'
}
