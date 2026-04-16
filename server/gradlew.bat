@echo off
setlocal
set GRADLE_VERSION=8.5
set BASEDIR=%~dp0
set DISTROOT=%BASEDIR%\.gradle\wrapper\dist
set GRADLE_DIR=%DISTROOT%\gradle-%GRADLE_VERSION%

if not exist "%GRADLE_DIR%\bin\gradle.bat" (
  echo Gradle %GRADLE_VERSION% not found — downloading via PowerShell script...
  powershell -NoProfile -ExecutionPolicy Bypass -File "%BASEDIR%scripts\download_gradle.ps1" "%DISTROOT%" "%GRADLE_VERSION%"
)

if not exist "%GRADLE_DIR%\bin\gradle.bat" (
  echo Failed to prepare Gradle wrapper.
  exit /b 1
)

"%GRADLE_DIR%\bin\gradle.bat" %*
