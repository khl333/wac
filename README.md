
<img width="300" height="150" alt="LogoGUI" src="https://github.com/user-attachments/assets/318e175c-2f6e-4906-b9f1-cdfa89e96f3c" />


# WAC — Warm Audio Codec

> A fully custom, proprietary lossy audio codec built from scratch in C++ and Java.  
> No external audio libraries. No dependencies. Pure Windows API + Java Swing.

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
![Platform](https://img.shields.io/badge/Platform-Windows-informational)
![Language](https://img.shields.io/badge/Language-C%2B%2B%20%7C%20Java-orange)
![Version](https://img.shields.io/badge/WAC-v14-brightgreen)
[![Arabic README](https://img.shields.io/badge/README-العربية-green)](README.ar.md)


---

## Overview

**WAC (Warm Audio Codec)** compresses MP3, AAC, FLAC, and WAV files into the `.wac` binary format using a custom **4-bit IMA ADPCM** engine with a built-in **Transparent Studio Reference DSP** and high-frequency noise shaping for maximum pristine acoustic transparency.

| Property | Value |
|---|---|
| Format | `.wac` (magic: `WARM`) |
| Compression | 4-bit IMA ADPCM + Noise Shaping → **4:1 ratio** |
| Sample Rate | 44,100 Hz |
| Bitrate | ~375 kbps CBR |
| Channels | Stereo / Mono |
| DSP | Transparent Studio Reference (Bypassed) |
| Input Formats | MP3, AAC, FLAC, WAV, WMA (via Windows MF) |


---

### `WacAnalyzer.exe` — Acoustic Spectrum Analyzer
A dedicated professional acoustic analysis GUI:
- Drag-and-drop support for `.wac`, `.mp3`, `.flac`, `.wav`
- **Time Domain Waveform:** Renders the full 16-bit PCM waveform of the entire track.
- **Frequency Domain FFT:** Calculates a real-time Cooley-Tukey Radix-2 Fast Fourier Transform to display a high-resolution frequency spectrum.
- Automatically handles background transcoding for non-WAC files.

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
├── AnalyzerLauncher.cpp # Native EXE launcher for Analyzer GUI
├── WacAnalyzer.java     # Acoustic Spectrum & FFT Analyzer GUI
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

### DSP — Transparent Studio Reference
Previous versions (like v13) utilized a heavy mastering chain containing side-widening, sub-bass excitement, and transient multiplication. This created a highly cinematic "punchy" audio profile but inherently modified the original master tape data, preventing true mathematical transparency and introducing granular artifacts on extreme transients. 

In **v14**, the DSP pipeline was entirely bypassed in favor of absolute signal integrity. The PCM audio is passed completely unmodified, except for a novel **High-Frequency Noise Shaping Feedback loop**. This takes the ADPCM quantization error and recursively feeds a fraction of it back into the next sample, actively pushing mathematical artifacts out of the audible human hearing range and into the 16kHz+ band.

This gives the WAC output an incredibly dense and pristine texture that is mathematically proven to be almost indistinguishable from uncompressed FLAC files.

---

## DSP Evolution

| Version | Profile |
|---|---|
| v1–v6 | Warmth, bass shelf, tape saturation |
| v7 | True warmth ADPCM |
| v8–v8.4 | Crispness, 3D stereo depth, multiband harmonics, OTT compression |
| v9 | FLAC transparency (removed all coloration) |
| v9.1 | SPL Transient Designer — pristine punch |
| v13 | Cinematic 3D Spatial Audio (Holographic Stereo + Sub-Bass Boost) |
| **v14** | **Current: Transparent Studio Reference (FLAC-identical with Noise Shaping)** |

---

## License

MIT License — see [LICENSE](LICENSE)

**Third-party notices:**  
The IMA ADPCM step table constants (`STEP_TABLE[89]`) are derived from the public IMA ADPCM standard (1992) and are in the public domain.

---

*Built with 100% original code. No FFmpeg. No libsndfile. No external audio libraries.*

