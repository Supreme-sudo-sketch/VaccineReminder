@echo off
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" -d reverse tcp:3000 tcp:3000 >nul
if errorlevel 1 (
  echo Could not link the phone. Check that it is plugged in by USB with USB debugging on.
) else (
  echo Phone linked. The app on the phone can now reach the server at http://127.0.0.1:3000
)
pause
