# Switches the running emulator to another screen size or font size for layout testing, and collects crash logs.
# Usage:  .\scripts\screen-check.ps1 small | phone | large | tablet | landscape | bigfont | reset | crashes
param([Parameter(Mandatory = $true)][ValidateSet("small", "phone", "large", "tablet", "landscape", "bigfont", "reset", "crashes")] [string]$Profile)
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { $adb = "adb" }
function Reset-Screen { & $adb shell wm size reset; & $adb shell wm density reset; & $adb shell settings put system font_scale 1.0; & $adb shell settings put system accelerometer_rotation 0; & $adb shell settings put system user_rotation 0 }
switch ($Profile) {
    "small"     { Reset-Screen; & $adb shell wm size 720x1280; & $adb shell wm density 320 }   # 360 x 640 dp: small phone
    "phone"     { Reset-Screen }                                                                # the emulator's own size
    "large"     { Reset-Screen; & $adb shell wm size 1440x3200; & $adb shell wm density 560 }  # 411 x 914 dp: large phone
    "tablet"    { Reset-Screen; & $adb shell wm size 1600x2560; & $adb shell wm density 320 }  # 800 x 1280 dp: 10" tablet
    "landscape" { Reset-Screen; & $adb shell settings put system accelerometer_rotation 0; & $adb shell settings put system user_rotation 1 }
    "bigfont"   { Reset-Screen; & $adb shell wm size 720x1280; & $adb shell wm density 320; & $adb shell settings put system font_scale 1.3 }  # small phone, large text
    "reset"     { Reset-Screen }
    "crashes"   {
        $out = Join-Path (Split-Path -Parent $PSScriptRoot) "crash-log.txt"
        & $adb logcat -b crash -d | Out-File -Encoding utf8 $out
        & $adb logcat -d -s AndroidRuntime:E | Out-File -Encoding utf8 -Append $out
        Write-Host "Crash log written to $out"
    }
}
& $adb shell wm size
& $adb shell wm density
