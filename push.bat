@echo off
title GitHub Push Tool

echo ========================================================
echo   PREPARING FILES FOR GITHUB
echo ========================================================

:: Check if git exists
where git >nul 2>nul
if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Git is not installed!
    echo Run this command in CMD to install it: winget install Git.Git
    echo.
    pause
    exit /b
)

:: 1. Create settings.gradle.kts
if exist settings.gradle.kts goto HAS_SETTINGS
echo [1/3] Creating settings.gradle.kts...
echo pluginManagement {> settings.gradle.kts
echo     repositories {>> settings.gradle.kts
echo         google()>> settings.gradle.kts
echo         mavenCentral()>> settings.gradle.kts
echo         gradlePluginPortal()>> settings.gradle.kts
echo     }>> settings.gradle.kts
echo }>> settings.gradle.kts
echo dependencyResolutionManagement {>> settings.gradle.kts
echo     repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)>> settings.gradle.kts
echo     repositories {>> settings.gradle.kts
echo         google()>> settings.gradle.kts
echo         mavenCentral()>> settings.gradle.kts
echo     }>> settings.gradle.kts
echo }>> settings.gradle.kts
echo rootProject.name = "SmartwatchLauncher">> settings.gradle.kts
:HAS_SETTINGS

:: 2. Create GitHub Actions workflow
if exist .github\workflows\build.yml goto HAS_WORKFLOW
echo [2/3] Creating .github\workflows\build.yml...
mkdir .github\workflows 2>nul
echo name: Build Smartwatch APK> .github\workflows\build.yml
echo on: [push, workflow_dispatch]>> .github\workflows\build.yml
echo jobs:>> .github\workflows\build.yml
echo   build:>> .github\workflows\build.yml
echo     runs-on: ubuntu-latest>> .github\workflows\build.yml
echo     steps:>> .github\workflows\build.yml
echo       - uses: actions/checkout@v4>> .github\workflows\build.yml
echo       - name: Set up JDK 17>> .github\workflows\build.yml
echo         uses: actions/setup-java@v4>> .github\workflows\build.yml
echo         with:>> .github\workflows\build.yml
echo           java-version: '17'>> .github\workflows\build.yml
echo           distribution: 'temurin'>> .github\workflows\build.yml
echo       - name: Setup Gradle>> .github\workflows\build.yml
echo         uses: gradle/actions/setup-gradle@v3>> .github\workflows\build.yml
echo       - name: Build Debug APK>> .github\workflows\build.yml
echo         run: gradle assembleDebug>> .github\workflows\build.yml
echo       - name: Upload APK>> .github\workflows\build.yml
echo         uses: actions/upload-artifact@v4>> .github\workflows\build.yml
echo         with:>> .github\workflows\build.yml
echo           name: smartwatch-launcher-apk>> .github\workflows\build.yml
echo           path: '**/build/outputs/apk/debug/*.apk'>> .github\workflows\build.yml
:HAS_WORKFLOW

:: 3. Git Push
echo.
echo [3/3] Uploading to GitHub...
git init
git branch -M main
git remote remove origin 2>nul
git remote add origin https://github.com/Duckierpine3108/Bubble-app.git
git add .
git commit -m "Add circular smartwatch launcher with auto APK build"
echo.
echo Pushing... (Log in if prompted in browser)
git push -u origin main --force

echo.
echo ========================================================
echo   DONE! Check https://github.com/Duckierpine3108/Bubble-app
echo ========================================================
pause