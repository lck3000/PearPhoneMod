# PearPhone Mod for Minecraft 1.21.1 (NeoForge)

A NeoForge mod that adds a phone that provides several apps as music player, camera, etc. to Minecraft.
## Features

- **Audio Player Block**: Place a block in the world that acts as a portable music player
- **YouTube Integration**: Input YouTube URLs to stream audio directly into Minecraft
- **Proximity Sound**: Audio is only heard by players within 64 blocks of the player
- **Volume Control**: Adjust playback volume from 0-100%
- **Persistent Storage**: Audio player settings are saved and loaded automatically
- **GUI Interface**: User-friendly interface for controlling playback

## Requirements

- Minecraft 1.21.1
- Java 21 or higher
- NeoForge 1.21.1 or later
- Gradle 8.8+

## Installation & Setup

### 1. Prerequisites
Ensure you have Java 21 JDK installed:
```bash
java -version
```

### 2. Clone/Download the Project
```bash
cd PearPhoneMod
```

### 3. Build the Mod
```bash
./gradlew build
```

The compiled JAR will be in `build/libs/pearphone-1.0.0.jar`

### 4. Install to Minecraft
Copy the JAR to your Minecraft mods folder:
- **Windows**: `%APPDATA%\.minecraft\mods\`
- **Mac**: `~/Library/Application Support/minecraft/mods/`
- **Linux**: `~/.minecraft/mods/`

### 5. Launch Minecraft
Start Minecraft 1.21.1 with NeoForge and the mod will be loaded automatically.

## Usage

### Placing the Audio Player
1. Find or craft the **Audio Player Block** (in the creative inventory under "pearphone")
2. Place it anywhere in the world
3. Right-click to open the GUI

### Playing Music
1. **Input YouTube URL**: Paste a YouTube video URL in the text field
2. **Play**: Click the Play button to start streaming
3. **Volume**: Adjust the volume slider (0-100)
4. **Stop**: Click Stop to end playback

### Proximity Sound
- Audio is automatically heard by all players within **64 blocks** of the player
- The sound follows the player as they move
- Players outside the range won't hear the audio

## Project Structure

```
PearPhoneMod/
├── src/main/java/com/pearphone/mod/
│   ├── PearPhoneMod.java          # Main mod class
│   ├── ModItems.java                  # Item registration
│   ├── ModBlocks.java                 # Block registration
│   ├── ModBlockEntities.java          # Block entity registration
│   ├── AudioPlayerBlock.java          # Block implementation
│   ├── AudioPlayerBlockEntity.java    # Block entity with state
│   ├── AudioPlayerMenu.java           # GUI container
│   ├── AudioPlayer.java               # Audio playback handler
│   ├── AudioStreamManager.java        # Stream & proximity management
│   └── YouTubeParser.java             # YouTube URL parsing
├── src/main/resources/
│   ├── META-INF/
│   │   └── neoforge.mods.toml         # Mod metadata
│   └── assets/pearphone/
│       ├── lang/
│       │   └── en_us.json             # English translations
│       ├── models/
│       │   ├── block/
│       │   │   └── audio_player_block.json
│       │   └── item/
│       │       └── pearphone.json
│       ├── textures/
│       │   ├── block/
│       │   │   └── audio_player.png   # (Add texture)
│       │   └── item/
│       │       └── pearphone.png # (Add texture)
│       └── blockstates/
│           └── audio_player_block.json
├── build.gradle.kts
└── gradle.properties
```

## Implementation Details

### YouTube Support
The mod uses a `YouTubeParser` class that:
- Extracts video IDs from various YouTube URL formats
- Validates URLs before playback
- Would require `yt-dlp` or similar tool for actual streaming

### Audio Playback
The `AudioPlayer` class handles:
- Audio stream initialization
- Volume adjustment via FloatControl
- Play/stop controls
- Connection management

### Proximity System
The `AudioStreamManager` tracks:
- All players within 64 blocks
- Real-time distance calculations
- Proximity-based sound distribution
- Server-side player updates

### Block Entity Persistence
State is saved to NBT data including:
- YouTube URL
- Track name
- Playback status
- Volume level

## Important Notes

### YouTube Audio Extraction
The current implementation is a **framework**. To actually stream YouTube audio, you need to:

**Option 1: Use yt-dlp (Server-Side)**
```bash
# Install yt-dlp
pip install yt-dlp
```

Then modify `AudioPlayer.java` to execute:
```java
String command = "yt-dlp -f 251 -o - " + videoUrl;
Process process = Runtime.getRuntime().exec(command);
// Stream from process.getInputStream()
```

**Option 2: Use YouTube Data API**
- Register for API key at: https://console.cloud.google.com/
- Use OAuth 2.0 for authentication
- Query video metadata and streaming URLs

**Option 3: Browser Extension Alternative**
- Create a companion browser extension that handles YouTube extraction
- Communicate via localhost WebSocket

### Audio Format Support
Currently supports:
- WAV
- AIFF
- AU
- SND

Can be extended for MP3 using third-party libraries like Tritonus or SonicAPI.

### Client-Side GUI (Additional Implementation)
You'll need to create a client-side screen class:
```java
// AudioPlayerScreen.java (Client-side)
public class AudioPlayerScreen extends AbstractContainerScreen<AudioPlayerMenu> {
    // Implement GUI rendering and input handling
}
```

Register in your mod event handler:
```java
MenuScreens.register(AudioPlayerMenu::new, AudioPlayerScreen::new);
```

## Troubleshooting

### Mod Not Loading
- Verify NeoForge version matches 1.21.1
- Check Java version is 21+
- Look at latest.log for error messages

### No Sound Output
- Ensure system audio is enabled
- Check volume levels in Minecraft
- Verify audio drivers are up to date

### YouTube URLs Not Working
- Use standard YouTube URLs (youtube.com/watch?v=...)
- Shortened URLs (youtu.be/...) are also supported
- Avoid playlists or special characters

## Building for Distribution

To create a release JAR:
```bash
./gradlew clean build
```

The mod JAR will be created at: `build/libs/pearphone-1.0.0.jar`

You can then upload to:
- **CurseForge**: https://www.curseforge.com/minecraft/mods/create
- **Modrinth**: https://modrinth.com/mods
- **GitHub Releases**: As part of your repository

## Dependencies

- **jsoup** (1.17.2): HTML/XML parsing for metadata extraction
- **oshi-core** (6.4.10): System information (optional, for performance monitoring)
- **NeoForge**: Minecraft modding framework

## License

MIT License - Feel free to modify and distribute

## Credits

Created for Minecraft 1.21.1 NeoForge modding community

## Future Enhancements

- [ ] Playlist support
- [ ] Song queue management
- [ ] Equalization controls
- [ ] Visualization effects
- [ ] Per-player volume adjustment
- [ ] Custom texture support
- [ ] Recording mode (save to disk)
- [ ] Network synchronization
- [ ] Waypoint system with audio triggers

## Support

For issues, questions, or contributions:
1. Check the GitHub Issues page
2. Review the Troubleshooting section
3. Consult NeoForge documentation

---

**Mod ID**: `pearphone`  
**Version**: 1.0.0  
**Minecraft Version**: 1.21.1  
**Modloader**: NeoForge
