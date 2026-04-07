# PearPhone

A Minecraft mod that adds a phone block with apps — starting with an **Audio Player** that streams YouTube audio to nearby players with proximity-based sound distribution.

## Features

- **Audio Player Block** — place it in the world and stream any YouTube URL
- **Proximity Sound** — audio volume fades naturally based on distance (configurable range, default 64 blocks)
- **yt-dlp Integration** — high-quality audio via format 251 (Opus) streamed directly from YouTube
- **In-game GUI** — URL input, Play/Stop controls, and volume slider

## Requirements

[yt-dlp](https://github.com/yt-dlp/yt-dlp) must be installed and on your system `PATH`:

```bash
pip install yt-dlp
```

## Configuration

All settings are in `config/pearphone-common.toml`:

| Setting | Default | Description |
|---|---|---|
| `proximityRange` | 64 | Max block distance to hear audio |
| `volumeDecayFactor` | 1.0 | How quickly volume fades with distance |
| `allowYoutube` | true | Enable/disable YouTube streaming |
| `streamTimeout` | 30 | yt-dlp stream timeout (seconds) |

## Usage

1. Craft or obtain an **Audio Player Block**
2. Place it in the world
3. Right-click to open the GUI
4. Paste a YouTube URL and press **Play**
5. Players within range will hear the audio

## Source

[GitHub — lck3000/PearPhoneMod](https://github.com/lck3000/PearPhoneMod)
