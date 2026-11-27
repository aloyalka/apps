@echo off
rem ============================================================================
rem  Fire TV speed-up for TV Home (run once, from a Windows laptop)
rem  Turns OFF (does not delete) Amazon's ads, tracking and background extras.
rem  Nothing you use is touched: Prime Video, YouTube, Internet (Silk), Alexa,
rem  the Appstore, Settings, the remote and Amazon's launcher all stay on.
rem  Undo any time with undo-speed-up.bat. A factory reset also undoes it.
rem
rem  Package list adapted from FireFrame's "safe" group (MIT licence,
rem  Copyright (c) 2026 CJ Dyas, https://github.com/dyascj/FireFrame).
rem
rem   com.amazon.kso.blackbird                   ads service
rem   com.amazon.ftvads.deeplinking              opens ads when clicked
rem   com.amazon.tv.acr                          tracks what is on screen
rem   com.amazon.media.recommendations           recommendation rows on Amazon's home
rem   com.amazon.sneakpeek                       autoplaying previews on Amazon's home
rem   com.amazon.shoptv.client                   Amazon shopping on TV
rem   com.amazon.shoptv.firetv.client            Amazon shopping on TV
rem   com.amazon.tigris                          subscription upsells
rem   com.amazon.tv.easyupgrade                  upgrade-your-device promos
rem   com.amazon.hedwig                          News
rem   com.amazon.minitv.android.app              Amazon miniTV
rem   com.amazon.device.crashmanager             crash reports
rem   com.amazon.dp.logger                       device logging
rem   com.amazon.logan                           log upload
rem   com.amazon.device.logmanager               log manager
rem   com.amazon.client.metrics                  usage metrics
rem   com.amazon.tv.fw.metrics                   framework metrics
rem   com.amazon.wirelessmetrics.service         Wi-Fi metrics
rem   com.amazon.perfc                           performance data collection
rem   com.amazon.perfcollection                  performance data collection
rem   com.amazon.wifilocker                      saves Wi-Fi passwords to Amazon
rem   com.amazon.d3                              store demo mode
rem   com.amazon.gamehub                         game hub
rem   com.amazon.spiderpork                      Amazon Luna
rem   com.amazon.tahoe                           Amazon Kids
rem   com.amazon.tmm.tutorial                    tutorials
rem   com.amazon.storm.lightning.tutorial        tutorials
rem   com.amazon.tv.releasenotes                 release notes
rem   com.amazon.tv.csapp                        help videos
rem   com.amazon.ods.kindleconnect               support screen sharing
rem   com.amazon.spotify.mediabrowserservice     Alexa's Spotify bridge (Spotify app still works)
rem   com.amazon.brightdata.coralreef            screensaver with a proxy SDK
rem   com.amazon.brightdata.jellyfish            screensaver with a proxy SDK
rem ============================================================================
setlocal
cd /d "%~dp0"
if not exist adb.exe (
  echo Put this file in your platform-tools folder, next to adb.exe, and run it again.
  pause
  exit /b 1
)
echo.
echo The laptop must be on the same network as the TV (your phone hotspot worked before).
set /p IP=Type the TV's IP address and press Enter: 
adb connect %IP%:5555
adb -s %IP%:5555 get-state >nul 2>&1
if errorlevel 1 (
  echo.
  echo Couldn't reach the TV. If it asks "Allow USB debugging?", press OK, then run this again.
  pause
  exit /b 1
)
adb -s %IP%:5555 shell pm list packages -d > disabled-before.txt
echo.
echo Turning off Amazon's ads, tracking and background extras...
for %%p in (
com.amazon.kso.blackbird
com.amazon.ftvads.deeplinking
com.amazon.tv.acr
com.amazon.media.recommendations
com.amazon.sneakpeek
com.amazon.shoptv.client
com.amazon.shoptv.firetv.client
com.amazon.tigris
com.amazon.tv.easyupgrade
com.amazon.hedwig
com.amazon.minitv.android.app
com.amazon.device.crashmanager
com.amazon.dp.logger
com.amazon.logan
com.amazon.device.logmanager
com.amazon.client.metrics
com.amazon.tv.fw.metrics
com.amazon.wirelessmetrics.service
com.amazon.perfc
com.amazon.perfcollection
com.amazon.wifilocker
com.amazon.d3
com.amazon.gamehub
com.amazon.spiderpork
com.amazon.tahoe
com.amazon.tmm.tutorial
com.amazon.storm.lightning.tutorial
com.amazon.tv.releasenotes
com.amazon.tv.csapp
com.amazon.ods.kindleconnect
com.amazon.spotify.mediabrowserservice
com.amazon.brightdata.coralreef
com.amazon.brightdata.jellyfish
) do (
  findstr /x /c:"package:%%p" disabled-before.txt >nul
  if errorlevel 1 (
    adb -s %IP%:5555 shell pm disable-user --user 0 %%p 2>nul | findstr /c:"disabled-user" >nul
    if not errorlevel 1 (
      echo   off: %%p
      echo %%p>>turned-off-by-tvhome.txt
    )
  )
)
echo.
echo Done. The list of what was turned off is in turned-off-by-tvhome.txt (keep it for undo).
echo Restarting the TV so the memory is freed...
adb -s %IP%:5555 reboot
adb disconnect >nul 2>&1
pause
