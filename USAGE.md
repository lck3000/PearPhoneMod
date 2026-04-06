# PearPhone Mod - Complete Usage Guide

## Overview

The PearPhone Mod adds a functional music player to Minecraft that can stream audio from YouTube videos. All players within proximity of the player hear the audio, creating an immersive shared experience.

## Getting Started

### 1. Finding the Audio Player Block

The **Audio Player Block** can be found in the Creative Inventory under:
- Search: "audio"
- Category: "Decoration" or "Tools"

Or craft it (recipe provided in survival):
```
[Resource Block] [Speaker Icon] [Resource Block]
[Speaker Icon]  [Redstone]     [Speaker Icon]
[Resource Block] [Speaker Icon] [Resource Block]
```

### 2. Placing the Audio Player

1. **Select** the Audio Player Block from your inventory
2. **Right-click** in the world to place it
3. The block will appear as a dark gray device with speaker icons
4. The block takes up one block space and is solid (no collision)

### 3. Opening the GUI

**Right-click** on the placed Audio Player Block to open the control panel.

The GUI shows:
- **YouTube URL Input Field** - Paste your video link here
- **Play Button** (▶) - Start playing the audio
- **Stop Button** (⏹) - Stop playback
- **Volume Controls** (-, Volume %, +) - Adjust audio level
- **Status Display** - Shows if playing/stopped
- **Track Name** - Title of the current video

## Using the Audio Player

### Step 1: Get a YouTube URL

Find any YouTube video you want to play:
```
https://www.youtube.com/watch?v=VIDEO_ID
https://youtu.be/VIDEO_ID
https://www.youtube.com/embed/VIDEO_ID
```

**Supported Video Types:**
- Music videos ✓
- Songs ✓
- Podcasts ✓
- Audiobooks ✓
- Any audio content ✓

**NOT Supported:**
- Playlists ✗
- Shorts ✗
- Age-restricted videos ✗
- Private videos ✗

### Step 2: Enter the URL

1. Click in the **URL Input Field**
2. **Paste** (Ctrl+V) your YouTube link
3. Click **Update** button to validate the URL

The block will show a green checkmark if valid, red X if invalid.

### Step 3: Play the Audio

1. Click the **Play** button (▶)
2. Audio starts playing immediately
3. The **Status** display shows "▶ Playing"
4. The **Track Name** updates with video information

### Step 4: Adjust Volume

**Method 1: Volume Buttons**
- Click **-** button to decrease volume (5% per click)
- Click **+** button to increase volume (5% per click)
- Range: 0% (silent) to 100% (maximum)

**Method 2: Volume Slider** (if available)
- Click and drag the slider to set exact volume

**Method 3: Direct Percentage**
- Some versions allow typing a percentage (0-100)

### Step 5: Stop Playback

1. Click the **Stop** button (⏹)
2. Audio stops immediately
3. Status shows "⏹ Stopped"
4. You can resume from the current position

## Proximity Sound System

### How It Works

The audio player creates immersive proximity sound:

- **Within 64 blocks**: All players hear the full audio
- **64-128 blocks**: Audio volume decreases with distance
- **Beyond 128 blocks**: No sound is heard

### Volume Decay

Volume decreases based on:
- **Distance from player** - Further away = quieter
- **Configuration setting** - `volumeDecayFactor` (default: 0.95)
- **Proximity range** - Default 64 blocks (configurable)

### Example

```
Distance:        0 blocks    32 blocks   64 blocks   128 blocks
Volume:          100%        ~95%        ~70%        0%
```

**Note:** The exact decay depends on your configuration!

## Advanced Features

### Multi-Player Support

Multiple players can:
- ✓ Hear the same audio simultaneously
- ✓ Be at different distances
- ✓ Experience volume adjustments based on proximity
- ✓ Control one audio player together

### Persistent Playback

Settings are saved:
- YouTube URL is remembered
- Current volume is saved
- Playback state persists on reload
- Track name is cached

### Audio Formats Supported

The mod automatically converts YouTube videos to:
- **Opus** (highest quality audio-only format)
- **MP3** (fallback)
- Other common audio formats

## Troubleshooting

### No Sound Output

**Possible Causes:**
1. Minecraft volume is muted
   - Check Minecraft settings → Sound
2. System audio is off
   - Check your operating system volume
3. Audio drivers need update
   - Update sound drivers
4. Outside proximity range
   - Move closer to the player
5. URL is invalid
   - Verify the YouTube link is correct

**Solution:**
```
1. Open Game Settings
2. Go to Sound
3. Adjust Master Volume to 100%
4. Check Proximity Range (default: 64 blocks)
5. Verify yt-dlp is installed: yt-dlp --version
```

### Invalid YouTube URL

**Causes:**
- Broken/expired link
- Private video
- Age-restricted content
- Incorrect URL format

**Solution:**
- Use a fresh YouTube link
- Try a different video
- Ensure URL is from youtube.com or youtu.be
- Don't include playlist parameters (?list=...)

### yt-dlp Not Found

**Error Message:**
```
[ERROR] yt-dlp is not available. YouTube audio extraction disabled.
```

**Solution:**
```bash
# Install yt-dlp
pip install yt-dlp

# Or run the included script
bash install_ytdlp.sh        # macOS/Linux
install_ytdlp.bat            # Windows
```

### Audio Quality is Poor

**Causes:**
- Network connection is slow
- yt-dlp is outdated
- YouTube is throttling the connection

**Solution:**
```bash
# Update yt-dlp to latest
pip install yt-dlp --upgrade

# Check connection speed
# Try again at a different time
```

### Playback Stuttering

**Causes:**
- CPU usage is high
- Network bandwidth is low
- Too many players in proximity

**Solution:**
1. Reduce video quality setting
2. Disable proximity sound if not needed
3. Lower proximity range in config
4. Decrease number of concurrent players

## Configuration Tips

### Optimize for Your Server

Edit `~/.minecraft/config/pearphone-common.toml`:

**For Small Servers (1-5 players):**
```toml
proximityRange = 64
volumeDecayFactor = 0.95
cacheAudioStreams = true
maxCacheSize = 500
```

**For Large Servers (5+ players):**
```toml
proximityRange = 32          # Reduce range for performance
volumeDecayFactor = 0.90     # Steeper decay
cacheAudioStreams = true
maxCacheSize = 200           # Reduce cache
```

**For Creative/Testing:**
```toml
proximityRange = 256         # Whole world can hear
volumeDecayFactor = 1.0      # No decay
enableProximitySound = false # Same volume everywhere
```

### Customize Volume Behavior

**Realistic Distance Sound:**
```toml
volumeDecayFactor = 0.85     # Steeper volume drop-off
proximityRange = 48          # Smaller area of effect
```

**Background Music Style:**
```toml
volumeDecayFactor = 0.98     # Gentle volume fade
proximityRange = 128         # Wide area of effect
```

## Performance Considerations

### Optimization Tips

1. **Limit Active Players** - Reduce concurrent streams
2. **Use Mono Audio** - Reduces bandwidth
3. **Lower Bit Rate** - Use format 251 (Opus)
4. **Cache Frequently Played Videos** - Enable caching
5. **Close Unused Streams** - Stop playback when done

### Monitoring Performance

**Check logs for:**
```
latest.log location:
- Windows: %APPDATA%\.minecraft\logs\latest.log
- macOS: ~/Library/Application Support/minecraft/logs/latest.log
- Linux: ~/.minecraft/logs/latest.log
```

Look for:
- ✓ "yt-dlp found - YouTube audio streaming enabled"
- ✓ "Successfully extracted audio stream URL"
- ✗ "Error extracting audio stream"
- ✗ "Connection timeout"

## Creative Uses

### Ambient Music System

Create multiple audio players around a build to create an ambient soundtrack.

### Event Hosting

Stream music during multiplayer events and parties.

### Guided Tours

Play audio narration as players navigate through structures.

### Virtual Cinema

Create a cinema setup where players watch/listen together.

### Meditation Spaces

Stream calming music and nature sounds.

## Limitations & Known Issues

| Issue | Status | Workaround |
|-------|--------|-----------|
| Playlists not supported | Known | Play individual videos |
| Age-restricted videos fail | Known | Use unrestricted videos |
| Very long videos buffer | Known | Keep under 3 hours |
| Multiple simultaneous streams | Limited | Max 2-3 per server |

## Updates & Support

### Getting Help

1. Check `latest.log` for error messages
2. Verify yt-dlp is installed and updated
3. Test with a known-good YouTube URL
4. Check configuration file for typos

### Reporting Issues

Include in your report:
- Minecraft version
- NeoForge version
- Mod version
- Error log excerpt
- YouTube URL (if applicable)

## Advanced Usage

### Command Line Testing

Test if yt-dlp can extract a video:
```bash
yt-dlp -f 251 -g "https://www.youtube.com/watch?v=VIDEO_ID"
```

Expected output: A direct URL to the audio stream (starts with https://...)

### Network Requirements

- **Download Speed**: Minimum 128 kbps (higher for better quality)
- **Latency**: Less than 200ms recommended
- **Stability**: Reliable connection (WiFi may have issues)

### Server-Side Installation

For dedicated servers:
1. Install Java 21 JDK
2. Install Python and yt-dlp
3. Place mod JAR in mods folder
4. Configure pearphone-common.toml
5. Start server

## Best Practices

✓ **DO:**
- Test videos in single-player first
- Use reliable internet connection
- Keep yt-dlp updated
- Monitor server performance
- Document custom configurations

✗ **DON'T:**
- Play copyrighted music on public servers without permission
- Use excessive proximity ranges (causes lag)
- Play multiple videos simultaneously at high volume
- Forget to stop playback when leaving
- Modify mod files directly (use config instead)

---

**Need more help?** Check the INSTALLATION.md file or open an issue on the repository!
