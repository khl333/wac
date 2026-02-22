# WAC — Warm Audio Codec

> A fully custom, proprietary lossy audio codec built from scratch in C++ and Java.  
> No external audio libraries. No dependencies. Pure Windows API + Java Swing.

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
![Platform](https://img.shields.io/badge/Platform-Windows-informational)
![Language](https://img.shields.io/badge/Language-C%2B%2B%20%7C%20Java-orange)
![Version](https://img.shields.io/badge/WAC-v9.1-brightgreen)
[![Arabic README](https://img.shields.io/badge/README-العربية-green)](README.ar.md)


---

<img width="976" height="578" alt="Picture2" src="https://github.com/user-attachments/assets/4bd4eee6-db4f-4349-b67d-f95f7788d4cf" />


## Overview

**WAC (Warm Audio Codec)** compresses MP3, AAC, FLAC, and WAV files into the `.wac` binary format using a custom **4-bit IMA ADPCM** engine with a built-in **SPL-Style Transient Designer** DSP for maximum punch and clarity.

| Property | Value |
|---|---|
| Format | `.wac` (magic: `WARM`) |
| Compression | 4-bit IMA ADPCM → **4:1 ratio** |
| Sample Rate | 44,100 Hz (fixed) |
| Bitrate | ~172 kbps CBR |
| Channels | Stereo / Mono |
| DSP | Transient Designer (SPL-style envelope punch) |
| Input Formats | MP3, AAC, FLAC, WAV, WMA (via Windows MF) |

---

## Application Suite

### `WarmStudio.exe` — Studio GUI
A professional dark-themed audio workstation with:
- 48-bar animated **Spectrum Analyzer** with peak-hold dots and frequency gradients
- Dual **VU Meters** (Left / Right channels)
- **Media Deck** playlist with double-click playback
- **Seek Slider** with gradient track and custom thumb
- Real-time **Transcoding Progress** monitoring
- Transport controls: Previous / Play / Pause / Stop / Next

### `Transcoder.exe` — CLI Converter
Converts any Windows-supported audio format to `.wac`:
```
Transcoder.exe <input.flac> <output.wac>
```
Outputs `PROGRESS: nn` lines for GUI integration.

### `Player.exe` — Lightweight CLI Player
Simple command-line WAC file player:
```
Player.exe <input.wac>
```

---

## Project Structure

```
InternetRadio/
├── WarmCodec.h          # Core codec engine (Encoder + Decoder + DSP)
├── Transcoder.cpp       # CLI transcoder (Windows Media Foundation)
├── Player.cpp           # CLI WAC player (Windows waveOut API)
├── Launcher.cpp         # Native EXE launcher for the Java GUI
├── WarmStudio.java      # Professional Java Swing studio GUI
├── build.bat            # Build script (g++ + javac)
├── WAC_CODEC_WHITEPAPER.md  # Full technical documentation
├── LICENSE              # MIT License
└── .gitignore
```

---

## Building from Source

### Requirements
- **g++** (MinGW-w64, tested with GCC 12+)
- **JDK 11+** (javac + java)
- **Windows 7+** (Media Foundation API required)

### Build
```bat
build.bat
```
Or manually:
```bat
g++ -O2 Transcoder.cpp -o Transcoder.exe -municode -lmfplat -lmfreadwrite -lmfuuid -lole32
g++ -O2 Player.cpp     -o Player.exe     -municode -lwinmm
g++ -O2 Launcher.cpp   -o WarmStudio.exe -mwindows
javac WarmStudio.java
```

---

## How It Works

### Compression (4-bit IMA ADPCM)
Instead of storing every audio sample (16 bits), WAC stores only the **difference** between consecutive samples using an adaptive step size. This achieves a clean 4:1 compression ratio with no psychoacoustic tricks.

### DSP — Transient Designer
Before encoding, a **transient envelope detector** runs on the raw PCM:
1. Computes the instantaneous derivative of the waveform
2. Tracks a fast-attack peak follower vs. a slow-release envelope follower
3. Extracts the difference → **pure transient energy**
4. Applies a `4.0x` punch multiplier precisely at drum and vocal attack moments
5. Limits the result to 98% headroom for clean ADPCM encoding

This gives the WAC output a live, punchy quality that closely matches the source FLAC.

---

## DSP Evolution

| Version | Profile |
|---|---|
| v1–v6 | Warmth, bass shelf, tape saturation |
| v7 | True warmth ADPCM |
| v8–v8.4 | Crispness, 3D stereo depth, multiband harmonics, OTT compression |
| v9 | FLAC transparency (removed all coloration) |
| **v9.1** | **Current: SPL Transient Designer — pristine punch** |

---

## License

MIT License — see [LICENSE](LICENSE)

**Third-party notices:**  
The IMA ADPCM step table constants (`STEP_TABLE[89]`) are derived from the public IMA ADPCM standard (1992) and are in the public domain.

---

*Built with 100% original code. No FFmpeg. No libsndfile. No external audio libraries.*


