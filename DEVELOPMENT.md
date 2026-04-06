# PearPhone Mod - Development Guide

## Project Structure

```
PearPhoneMod/
├── src/main/java/com/pearphone/mod/
│   ├── PearPhoneMod.java              # Main mod class
│   ├── ModItems.java                      # Item registration
│   ├── ModBlocks.java                     # Block registration
│   ├── ModBlockEntities.java              # Block entity registration
│   ├── AudioPlayerBlock.java              # Block implementation
│   ├── AudioPlayerBlockEntity.java        # Block entity with state
│   ├── AudioPlayerMenu.java               # Container menu
│   ├── YouTubeParser.java                 # URL parsing utility
│   │
│   ├── audio/
│   │   ├── AudioPlayer.java               # Legacy audio player
│   │   ├── EnhancedAudioPlayer.java       # Enhanced player with yt-dlp
│   │   ├── AudioStreamManager.java        # Stream management
│   │   └── YtDlpExtractor.java           # yt-dlp integration
│   │
│   ├── client/
│   │   ├── AudioPlayerScreen.java         # Client-side GUI screen
│   │   └── ClientSetup.java               # Client event handlers
│   │
│   ├── config/
│   │   └── AudioPlayerConfig.java         # Configuration system
│   │
│   ├── event/
│   │   └── ServerEvents.java              # Server event handlers
│   │
│   └── network/
│       └── AudioPlayerSyncPacket.java     # Network synchronization
│
├── src/main/resources/
│   ├── META-INF/
│   │   └── neoforge.mods.toml             # Mod metadata
│   └── assets/pearphone/
│       ├── lang/
│       │   └── en_us.json                 # Translations
│       ├── models/
│       │   ├── block/
│       │   │   └── audio_player_block.json
│       │   └── item/
│       │       └── pearphone.json
│       ├── textures/
│       │   ├── block/
│       │   │   └── audio_player.png
│       │   ├── item/
│       │   │   └── pearphone.png
│       │   └── gui/
│       │       └── audio_player_gui.png
│       └── blockstates/
│           └── audio_player_block.json
│
├── build.gradle.kts                       # Build configuration
├── gradle.properties                      # Gradle settings
├── settings.gradle.kts                    # Gradle root settings
├── README.md                              # Project overview
├── INSTALLATION.md                        # Installation guide
├── USAGE.md                               # User guide
├── DEVELOPMENT.md                         # This file
├── install_ytdlp.sh                      # Linux/macOS setup script
└── install_ytdlp.bat                     # Windows setup script
```

## Setting Up Development Environment

### Prerequisites

- **Java 21 JDK**: Download from [Oracle](https://www.oracle.com/java/technologies/downloads/) or [Temurin](https://adoptium.net/)
- **Gradle**: Included via wrapper (8.8+)
- **Git**: For version control
- **IDE**: IntelliJ IDEA, Eclipse, or VS Code (with Java extensions)

### IntelliJ IDEA Setup

```bash
# Clone and setup
git clone https://github.com/yourusername/PearPhoneMod.git
cd PearPhoneMod

# Generate IDE files
./gradlew idea

# Open the project in IntelliJ
# File → Open → Select the .idea folder
```

### Eclipse Setup

```bash
# Generate IDE files
./gradlew eclipse

# Import in Eclipse
# File → Import → Existing Projects into Workspace
```

### VS Code Setup

```bash
# Generate IDE files
./gradlew build

# Open in VS Code
code .

# Install Extensions:
# - Extension Pack for Java (Microsoft)
# - Gradle for Java (Microsoft)
```

## Building the Mod

### Generate Textures

```bash
./gradlew generateTextures
```

This creates PNG texture files for:
- Block texture (16x16)
- Item texture (16x16)
- GUI background (256x220)

### Build JAR

```bash
./gradlew build
```

Output: `build/libs/pearphone-1.0.0.jar`

### Run Development Client

```bash
./gradlew runClient
```

Starts Minecraft with the mod in development mode.

### Run Development Server

```bash
./gradlew runServer
```

Starts a local multiplayer server.

### Clean Build

```bash
./gradlew clean build
```

## Code Organization & Architecture

### Module: Audio Player Block

**Files:**
- `AudioPlayerBlock.java` - Block definition
- `AudioPlayerBlockEntity.java` - Block entity (state storage)
- `AudioPlayerMenu.java` - Container menu

**Responsibilities:**
- Manage block placement/destruction
- Store player state (URL, volume, playing status)
- Handle player interactions

### Module: Audio Streaming

**Files:**
- `EnhancedAudioPlayer.java` - Main audio playback
- `YtDlpExtractor.java` - YouTube extraction
- `AudioStreamManager.java` - Multi-player proximity management

**Responsibilities:**
- Stream audio from extracted URLs
- Manage volume with proximity decay
- Track nearby players
- Handle playback lifecycle

### Module: Client UI

**Files:**
- `AudioPlayerScreen.java` - GUI screen
- `ClientSetup.java` - Client event registration

**Responsibilities:**
- Render GUI components
- Handle player input
- Update block entity state

### Module: Configuration

**Files:**
- `AudioPlayerConfig.java` - Config definitions

**Responsibilities:**
- Define all mod settings
- Provide type-safe config access
- Generate config files

### Module: Network

**Files:**
- `AudioPlayerSyncPacket.java` - Sync packet

**Responsibilities:**
- Sync state between client and server
- Handle network events

## Common Development Tasks

### Adding a New Configuration Option

1. Edit `AudioPlayerConfig.java`:

```java
public final ModConfigSpec.IntValue newOption = builder
    .comment("Description of this option")
    .defineInRange("optionName", defaultValue, minValue, maxValue);
```

2. Access in code:

```java
AudioPlayerConfig.COMMON.newOption.get()
```

### Adding a New GUI Element

1. Edit `AudioPlayerScreen.java`:

```java
@Override
protected void init() {
    super.init();
    
    // Add button
    this.addRenderableWidget(Button.builder(Component.literal("Label"), button -> handleClick())
        .bounds(x, y, width, height)
        .build());
    
    // Add slider
    this.volumeSlider = new Slider(x, y, width, height, Component.literal("Volume"), 1.0);
    this.addRenderableWidget(this.volumeSlider);
}
```

### Adding Support for New Audio Formats

1. Edit `EnhancedAudioPlayer.java`:

```java
// Add format to supported list
private static final String[] SUPPORTED_FORMATS = {"251", "251", "22", "18"};

// Update format selection logic
String format = selectBestFormat(videoInfo);
```

### Adding Event Listeners

1. Create event handler class:

```java
@EventBusSubscriber(modid = PearPhoneMod.MODID)
public class MyEventHandler {
    @SubscribeEvent
    public static void onEvent(MyEvent event) {
        // Handle event
    }
}
```

### Adding Block Properties

1. Edit `ModBlocks.java`:

```java
public static final DeferredBlock<Block> NEW_BLOCK = BLOCKS.register("new_block",
    () -> new MyBlock(BlockBehaviour.Properties
        .ofFullCopy(Blocks.STONE)
        .strength(2.0f, 10.0f)
        .sound(SoundType.METAL)
    ));
```

## Testing

### Unit Testing

Create test class in `src/test/java/`:

```java
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class YouTubeParserTest {
    @Test
    public void testVideoIdExtraction() {
        String url = "https://www.youtube.com/watch?v=jNQXAC9IVRw";
        String id = YouTubeParser.extractVideoId(url);
        assertEquals("jNQXAC9IVRw", id);
    }
}
```

Run tests:
```bash
./gradlew test
```

### Integration Testing

1. Launch dev client: `./gradlew runClient`
2. Create test world
3. Place audio player block
4. Test functionality

### Performance Testing

Monitor performance in logs:
```bash
tail -f .minecraft/logs/latest.log | grep "AudioPlayer"
```

## Extending the Mod

### Adding Playlist Support

1. Modify `YouTubeParser.java` to detect playlists:

```java
public static List<String> extractPlaylistVideos(String playlistUrl) {
    // Use yt-dlp to extract all videos from playlist
    YtDlpExtractor extractor = YtDlpExtractor.getInstance();
    // Parse JSON output
}
```

2. Update `AudioPlayerBlockEntity.java` to store queue:

```java
private List<String> playlist = new ArrayList<>();
private int currentIndex = 0;
```

3. Add buttons to GUI for next/previous

### Adding Volume Presets

1. Add configuration:

```java
public final ModConfigSpec.IntValue volumePreset1 = builder.defineInRange("preset1", 25, 0, 100);
public final ModConfigSpec.IntValue volumePreset2 = builder.defineInRange("preset2", 50, 0, 100);
public final ModConfigSpec.IntValue volumePreset3 = builder.defineInRange("preset3", 100, 0, 100);
```

2. Add preset buttons to GUI:

```java
for (int i = 1; i <= 3; i++) {
    this.addRenderableWidget(Button.builder(
        Component.literal("P" + i), 
        button -> setVolumePreset(i)
    ).bounds(x, y, 30, 20).build());
}
```

### Adding Audio Filters/Equalizer

1. Create filter package:

```
com.pearphone.mod.audio.filter/
├── AudioFilter.java (interface)
├── EqualizerFilter.java
├── BassBoostFilter.java
└── ReverbFilter.java
```

2. Apply filters in `EnhancedAudioPlayer.java`:

```java
List<AudioFilter> filters = new ArrayList<>();
filters.add(new EqualizerFilter(config));
// Apply filters to audio stream
```

## Debugging

### Enable Debug Logging

Add to `logback.xml` or use command line:

```bash
./gradlew runClient --debug
```

### Common Issues

**Issue:** Block entity data not persisting
- Check `load()` and `saveAdditional()` methods
- Verify NBT key names are correct
- Check JSON model for `noOcclusion`

**Issue:** Client-server synchronization failing
- Check packet registration
- Verify PlayPayloadContext usage
- Check network thread access

**Issue:** yt-dlp not found
- Check PATH environment variable
- Run `yt-dlp --version`
- Update yt-dlp: `pip install yt-dlp --upgrade`

### Adding Debug Output

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

private static final Logger LOGGER = LoggerFactory.getLogger("AudioPlayer");

LOGGER.info("Debug message: {}", variable);
LOGGER.warn("Warning: {}", message);
LOGGER.error("Error: {}", message, exception);
```

## Contributing

### Git Workflow

```bash
# Create feature branch
git checkout -b feature/my-feature

# Make changes and commit
git add .
git commit -m "feat: description of changes"

# Push and create pull request
git push origin feature/my-feature
```

### Code Style

Follow these conventions:
- **Naming**: camelCase for variables, PascalCase for classes
- **Spacing**: 4 spaces (not tabs)
- **Line length**: 120 characters max
- **Comments**: Javadoc for public methods
- **Imports**: Organize and remove unused imports

### Commit Messages

Use conventional commits:
```
feat: add new feature
fix: resolve bug
docs: update documentation
refactor: improve code structure
test: add test case
```

## Performance Optimization

### Profiling

Use Java profiler to identify bottlenecks:

```bash
./gradlew runClient -P jvm_args="-Xprof"
```

### Common Optimizations

1. **Cache extracted video metadata**
   - Reduces API calls
   - Improves response time

2. **Async stream extraction**
   - Use `ExecutorService` for background tasks
   - Don't block main thread

3. **Limit proximity updates**
   - Only recalculate when players move
   - Use spatial indexing for large servers

4. **Stream compression**
   - Use codec with lowest bitrate
   - Format 251 (Opus) is optimal

## Releasing

### Version Numbering

Use semantic versioning: `MAJOR.MINOR.PATCH`

- **MAJOR**: Breaking changes
- **MINOR**: New features
- **PATCH**: Bug fixes

### Creating Release

```bash
# Update version in build.gradle.kts
# git tag v1.0.0
# ./gradlew build
# Upload to CurseForge/Modrinth/GitHub
```

## Resources & Links

- [NeoForge Documentation](https://docs.neoforged.net/)
- [Minecraft Wiki](https://minecraft.wiki/)
- [Java Documentation](https://docs.oracle.com/javase/)
- [Gradle Documentation](https://docs.gradle.org/)
- [yt-dlp GitHub](https://github.com/yt-dlp/yt-dlp)

## Support

For development questions:
1. Check existing GitHub issues
2. Review NeoForge documentation
3. Ask in NeoForge Discord
4. Create detailed issue report

---

Happy developing! 🎵🚀
