@echo off
rem Turns back on everything speed-up.bat turned off (listed in turned-off-by-tvhome.txt).
setlocal
cd /d "%~dp0"
if not exist turned-off-by-tvhome.txt (
  echo turned-off-by-tvhome.txt isn't in this folder, so there is nothing to undo here.
  pause
  exit /b 1
)
set /p IP=Type the TV's IP address and press Enter: 
adb connect %IP%:5555
adb -s %IP%:5555 get-state >nul 2>&1
if errorlevel 1 (
  echo Couldn't reach the TV. If it asks "Allow USB debugging?", press OK, then run this again.
  pause
  exit /b 1
)
for /f "usebackq delims=" %%p in ("turned-off-by-tvhome.txt") do (
  adb -s %IP%:5555 shell pm enable %%p >nul 2>&1
  echo   on: %%p
)
ren turned-off-by-tvhome.txt turned-off-by-tvhome.undone.txt
echo Done. Restarting the TV...
adb -s %IP%:5555 reboot
adb disconnect >nul 2>&1
pause
