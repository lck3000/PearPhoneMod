#!/bin/bash

# PearPhone Mod - yt-dlp Installation Script
# This script automates the installation of yt-dlp for YouTube audio support

set -e

echo "╔═══════════════════════════════════════════════════════════════╗"
echo "║  PearPhone Mod - yt-dlp Installation Script             ║"
echo "║  Version 1.0                                                  ║"
echo "╚═══════════════════════════════════════════════════════════════╝"
echo ""

# Detect OS
OS_TYPE="Unknown"
if [[ "$OSTYPE" == "linux-gnu"* ]]; then
    OS_TYPE="Linux"
elif [[ "$OSTYPE" == "darwin"* ]]; then
    OS_TYPE="macOS"
elif [[ "$OSTYPE" == "msys" ]] || [[ "$OSTYPE" == "cygwin" ]]; then
    OS_TYPE="Windows"
fi

echo "✓ Detected OS: $OS_TYPE"
echo ""

# Check if pip is installed
echo "Checking for Python and pip..."
if ! command -v pip &> /dev/null; then
    echo "✗ pip is not installed!"
    echo ""
    echo "Please install Python from: https://www.python.org/"
    echo "Make sure to check 'Add Python to PATH' during installation."
    echo ""
    exit 1
fi

python_version=$(python3 --version)
echo "✓ Found: $python_version"
echo ""

# Install yt-dlp
echo "Installing yt-dlp..."
pip install yt-dlp --upgrade

echo ""
echo "✓ yt-dlp installation complete!"
echo ""

# Verify installation
echo "Verifying yt-dlp installation..."
if command -v yt-dlp &> /dev/null; then
    ytdlp_version=$(yt-dlp --version)
    echo "✓ yt-dlp is ready: $ytdlp_version"
else
    echo "✗ yt-dlp verification failed. Please check your PATH."
    exit 1
fi

echo ""
echo "╔═══════════════════════════════════════════════════════════════╗"
echo "║  Installation Complete!                                       ║"
echo "║  You can now use YouTube URLs with the PearPhone Mod    ║"
echo "╚═══════════════════════════════════════════════════════════════╝"
echo ""

# Test YouTube extraction
read -p "Would you like to test YouTube extraction? (y/n) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo ""
    echo "Testing with a sample YouTube URL..."
    echo "(This may take a moment)"
    echo ""
    
    # Use an example video
    TEST_URL="https://www.youtube.com/watch?v=jNQXAC9IVRw"
    
    if yt-dlp -f 251 -g "$TEST_URL" > /dev/null 2>&1; then
        echo "✓ YouTube extraction test passed!"
        echo "  The PearPhone Mod is ready to use."
    else
        echo "✗ YouTube extraction test failed."
        echo "  This might be a temporary network issue."
        echo "  Try again later."
    fi
fi

echo ""
echo "Next steps:"
echo "1. Build the mod: ./gradlew build"
echo "2. Copy the JAR to your mods folder"
echo "3. Launch Minecraft with NeoForge 1.21.1"
echo ""
