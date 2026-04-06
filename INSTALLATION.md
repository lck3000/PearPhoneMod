# Installation & Setup Guide - PearPhone Mod

## Quick Start

### 1. Install yt-dlp (Required for YouTube Support)

#### Windows
```bash
# Using pip (Python package manager)
pip install yt-dlp

# Or using Chocolatey
choco install yt-dlp

# Or using Scoop
scoop install yt-dlp
```

#### macOS
```bash
# Using Homebrew
brew install yt-dlp

# Using pip
pip install yt-dlp
```

#### Linux (Ubuntu/Debian)
```bash
# Using apt
sudo apt install yt-dlp

# Or using pip
pip install yt-dlp

# Or using snap
sudo snap install yt-dlp
```

**Verify Installation:**
```bash
yt-dlp --version
```

### 2. Build the Mod

#### Prerequisites
- Java 21 JDK
- Gradle 8.8+ (or use included wrapper)

#### Build Steps
```bash
cd PearPhoneMod

# Generate textures
./gradlew generateTextures

# Build the mod
./gradlew build

# Output: build/libs/pearphone-1.0.0.jar
```

### 3. Install to Minecraft

#### Find Your Mods Folder
- **Windows**: `%APPDATA%\.minecraft\mods\`
- **macOS**: `~/Library/Application Support/minecraft/mods/`
- **Linux**: `~/.minecraft/mods/`

#### Copy the JAR
```bash
# From PearPhoneMod directory
cp build/libs/pearphone-1.0.0.jar ~/.minecraft/mods/
```

#### Alternative: Symbolic Link (for development)
```bash
# Linux/macOS
ln -s "$(pwd)/build/libs/pearphone-1.0.0.jar" ~/.minecraft/mods/

# Windows (PowerShell as Admin)
New-Item -ItemType SymbolicLink -Path "$env:APPDATA\.minecraft\mods\pearphone-1.0.0.jar" -Target "$(pwd)\build\libs\pearphone-1.0.0.jar"
```

### 4. Launch Minecraft

1. Start Minecraft 1.21.1 with NeoForge
2. Select a world or create a new one
3. The mod will be loaded automatically

**Check if mod loaded:**
- Look for "PearPhone Mod loaded successfully!" in the latest.log
- Path: `.minecraft/logs/latest.log`

## Configuration

### Locate Config File
After first launch, find the config file at:
- `~/.minecraft/config/pearphone-common.toml`

### Common Configuration Options

```toml
[audio_player]
    # Maximum distance players can hear the audio player (in blocks, 0-256)
    proximityRange = 64
    
    # Maximum volume level (0-100)
    maxVolume = 100
    
    # Default volume when audio player is first placed (0-100)
    defaultVolume = 50
    
    # Enable proximity-based sound attenuation
    enableProximitySound = true
    
    # Enable YouTube URL support (requires yt-dlp)
    enableYoutubeSupport = true
    
    # Volume decay factor per block distance (0.0-1.0)
    # 1.0 = no decay, 0.95 = 5% reduction per calculation cycle
    volumeDecayFactor = 0.95
```

### Client Configuration

```toml
[client]
    # Show particle effects when adjusting volume
    showVolumeParticles = true
    
    # Enable GUI animations and transitions
    enableGuiAnimations = true
    
    # GUI scale percentage (50-200)
    guiScalePercent = 100
```

### Server Configuration

```toml
[server]
    # Require yt-dlp to be installed for YouTube support
    requireYtDlp = false
    
    # Audio stream timeout in seconds
    audioStreamTimeout = 30
    
    # Cache audio streams for performance
    cacheAudioStreams = true
    
    # Maximum cache size in MB (0 = unlimited)
    maxCacheSize = 500
```

## Troubleshooting

### "yt-dlp is not installed"
**Solution:** Install yt-dlp using pip:
```bash
pip install yt-dlp --upgrade
```

### "Invalid YouTube URL"
**Supported formats:**
- `https://www.youtube.com/watch?v=VIDEO_ID`
- `https://youtu.be/VIDEO_ID`
- `https://www.youtube.com/embed/VIDEO_ID`

**Not supported:**
- Playlist URLs
- Shorts (unless converted to standard URL)
- Private/Age-restricted videos

### No Sound Output
1. Check Minecraft volume settings
2. Verify system audio is enabled
3. Ensure audio drivers are up to date
4. Check proximity range setting (default: 64 blocks)

### Mod Not Loading
1. Verify NeoForge is installed for 1.21.1
2. Check `latest.log` for errors:
   ```bash
   cat ~/.minecraft/logs/latest.log | grep "pearphone"
   ```
3. Ensure mod JAR is in mods folder
4. Verify Java 21+ is being used:
   ```bash
   java -version
   ```

### Audio Playback Issues
1. Check yt-dlp version:
   ```bash
   yt-dlp --version
   ```
2. Update yt-dlp:
   ```bash
   pip install yt-dlp --upgrade
   ```
3. Test YouTube extraction:
   ```bash
   yt-dlp -f 251 -g "https://www.youtube.com/watch?v=EXAMPLE"
   ```

## Development Setup

### IDE Setup (IntelliJ IDEA)
```bash
./gradlew idea
# Open the generated .idea project
```

### IDE Setup (Eclipse)
```bash
./gradlew eclipse
```

### Run Development Server
```bash
./gradlew runServer
```

### Run Development Client
```bash
./gradlew runClient
```

## Performance Tips

1. **Limit Proximity Range**: Smaller ranges = better performance
2. **Disable Unused Features**: Turn off proximity sound if not needed
3. **Cache Streams**: Enable caching for frequently played videos
4. **Monitor CPU Usage**: Audio extraction uses system resources

## Advanced: Custom yt-dlp Path

If yt-dlp is not in your PATH, you can specify the location:

Edit `~/.minecraft/config/pearphone-common.toml` and add:
```toml
# Custom path to yt-dlp executable
ytdlpPath = "/usr/local/bin/yt-dlp"
```

## Uninstallation

1. Remove mod JAR from mods folder
2. Delete config file: `~/.minecraft/config/pearphone-*`
3. Restart Minecraft

## Support & Issues

- **Bug Reports**: Create an issue on the GitHub repository
- **Logs**: Always include `latest.log` when reporting issues
- **yt-dlp Issues**: See https://github.com/yt-dlp/yt-dlp/issues

## License

MIT License - Free to use and modify

---

**Still having issues?** Check the main README.md for more detailed information!
