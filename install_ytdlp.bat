@echo off
REM PearPhone Mod - yt-dlp Installation Script (Windows)

echo.
echo ====================================================================
echo  PearPhone Mod - yt-dlp Installation Script (Windows)
echo  Version 1.0
echo ====================================================================
echo.

REM Check if Python is installed
python --version >nul 2>&1
if errorlevel 1 (
    echo X Python is not installed!
    echo.
    echo Please download Python from: https://www.python.org/
    echo Make sure to check "Add Python to PATH" during installation!
    echo.
    pause
    exit /b 1
)

echo Found Python:
python --version
echo.

REM Check if pip is available
pip --version >nul 2>&1
if errorlevel 1 (
    echo X pip is not available!
    echo.
    echo Please reinstall Python and select "pip" during installation.
    echo.
    pause
    exit /b 1
)

echo Found pip:
pip --version
echo.

REM Install yt-dlp
echo Installing yt-dlp...
echo (This may take a minute)
echo.

pip install yt-dlp --upgrade

echo.
echo Installation complete!
echo.

REM Verify installation
echo Verifying yt-dlp...
yt-dlp --version >nul 2>&1
if errorlevel 1 (
    echo X yt-dlp verification failed!
    echo.
    echo Try restarting your terminal and running this script again.
    echo.
    pause
    exit /b 1
)

echo Found yt-dlp:
yt-dlp --version
echo.

echo ====================================================================
echo  Installation Complete!
echo  You can now use YouTube URLs with the PearPhone Mod
echo ====================================================================
echo.

REM Test YouTube extraction
setlocal enabledelayedexpansion
set /p test_choice="Would you like to test YouTube extraction? (y/n): "

if /i "%test_choice%"=="y" (
    echo.
    echo Testing with a sample YouTube URL...
    echo (This may take a moment)
    echo.
    
    set TEST_URL=https://www.youtube.com/watch?v=jNQXAC9IVRw
    
    yt-dlp -f 251 -g !TEST_URL! >nul 2>&1
    if errorlevel 0 (
        echo o YouTube extraction test passed!
        echo  The PearPhone Mod is ready to use.
    ) else (
        echo X YouTube extraction test failed.
        echo  This might be a temporary network issue. Try again later.
    )
)

echo.
echo Next steps:
echo 1. Open a terminal/command prompt in the PearPhoneMod directory
echo 2. Run: gradlew.bat build
echo 3. Copy build\libs\pearphone-1.0.0.jar to your mods folder
echo 4. Launch Minecraft with NeoForge 1.21.1
echo.

pause
