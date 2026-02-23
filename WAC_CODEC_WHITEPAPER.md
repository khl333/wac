# WAC (Warm Audio Codec) — Full Technical Whitepaper
### Version 14 | February 2026
### Author: Khaled

---

## Table of Contents
1. [Overview](#overview)
2. [Motivation & Goals](#motivation--goals)
3. [Architecture Overview](#architecture-overview)
4. [The WAC File Format](#the-wac-file-format)
5. [Compression Engine — IMA ADPCM](#compression-engine--ima-adpcm)
6. [DSP Engine — Transient Designer](#dsp-engine--transient-designer)
7. [Transcoder Application](#transcoder-application)
8. [WarmStudio GUI Application](#warmstudio-gui-application)
9. [Player Application](#player-application)
10. [Copyright & Licensing](#copyright--licensing)
11. [Technical Specifications](#technical-specifications)
12. [Development History](#development-history)

---

## 1. Overview

**WAC (Warm Audio Codec)** is a fully custom, proprietary lossy audio codec developed from scratch in C++ and Java. It is designed to compress audio files (MP3, AAC, FLAC, WAV) into a compact `.wac` binary format with a predictable file size and a distinctive sound profile tuned for clarity and punch.

The complete application suite consists of:
- **`WarmCodec.h`** — The core codec engine (encoder + decoder), written in C++
- **`Transcoder.exe`** — A Windows CLI application that converts any audio format to `.wac`
- **`WarmStudio.exe`** — A professional Java Swing GUI for transcoding and playback
- **`Player.exe`** — A lightweight command-line WAC file player

All components are built **without any external audio libraries**. The only dependencies are:
- Microsoft Windows Media Foundation API (built into Windows 7+)
- Java Swing standard library (built into JDK)
- Windows `winmm.dll` `waveOut` API for audio playback

---

## 2. Motivation & Goals

The project was started with a clear set of design objectives:

| Goal | Description |
|------|-------------|
| **Custom Format** | Create a proprietary `.wac` binary format with a unique magic header |
| **No External Libraries** | Build the entire stack from scratch using only OS-native APIs |
| **Predictable Compression** | Achieve a fixed ~4:1 compression ratio (compared to raw PCM) |
| **Fixed Bitrate** | Target a constant ~375 kbps at 44.1 kHz stereo |
| **FLAC Transparency** | Sound as close as possible to lossless FLAC during playback |
| **Cinematic 3D** | Now Optional/Bypassed in v14 in favor of absolute signal transparency |
| **Professional GUI** | Build a DAW-style application for transcoding and playback |

---

## 3. Architecture Overview

```
┌────────────────────────────────────────────────────────────┐
│                   WarmStudio.exe (Java GUI)                 │
│   ┌─────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│   │  Media Deck │  │  Transport   │  │  Transcoding     │  │
│   │  (Playlist) │  │  Controls    │  │  Progress Bar    │  │
│   └─────────────┘  └──────────────┘  └──────────────────┘  │
│              Calls Transcoder.exe (subprocess)              │
└────────────────────────────────────────────────────────────┘
            │                              │
            ▼                              ▼
┌─────────────────────┐        ┌───────────────────────┐
│   Transcoder.exe    │        │       Player.exe       │
│   (C++ CLI)         │        │       (C++ CLI)        │
│                     │        │                        │
│  Windows MF API     │        │  Windows waveOut API   │
│  DecodeAudioFile()  │        │  Streams PCM to        │
│  → PCM buffer       │        │  hardware directly     │
└─────────────────────┘        └───────────────────────┘
            │
            ▼
┌─────────────────────────────────────────────────────┐
│                    WarmCodec.h                       │
│                                                      │
│  ┌──────────────┐         ┌──────────────────────┐  │
│  │  DSP Engine  │ ──────> │   ADPCM Encoder      │  │
│  │  (Cinematic  │         │   (4-bit packing,    │  │
│  │   3D Master) │         │    STEP_TABLE)       │  │
│  └──────────────┘         └──────────────────────┘  │
│                                      │               │
│                            ┌─────────▼──────────┐   │
│                            │   .wac Binary File  │   │
│                            │   [WARM HEADER]     │   │
│                            │   [ADPCM BLOCKS]    │   │
│                            └─────────────────────┘   │
└─────────────────────────────────────────────────────┘
```

---

## 4. The WAC File Format

Every `.wac` file begins with a fixed **16-byte binary header** followed immediately by a sequence of ADPCM-encoded audio blocks.

### 4.1 Header Structure (16 bytes)

| Offset | Size | Type | Field | Value |
|--------|------|------|-------|-------|
| 0 | 4 bytes | char[4] | Magic Bytes | `W`, `A`, `R`, `M` |
| 4 | 4 bytes | uint32_t | Sample Rate | e.g., `44100` |
| 8 | 2 bytes | uint16_t | Channels | `1` (Mono) or `2` (Stereo) |
| 10 | 4 bytes | uint32_t | Total Blocks | Number of ADPCM blocks |
| 14 | 2 bytes | — | (Struct padding) | — |

The **magic identifier** `WARM` allows any decoder to quickly validate a WAC file and reject invalid inputs before attempting to decode them.

### 4.2 Block Structure

After the header, the file consists of sequential ADPCM blocks. Each block is exactly **128 samples** wide per channel.

For a stereo file, each block contains:
- **Channel 0 Block Header** (4 bytes): Initial predictor value + step index
- **Channel 1 Block Header** (4 bytes): Initial predictor value + step index
- **Channel 0 Data** (64 bytes): 128 samples packed at 4 bits per sample
- **Channel 1 Data** (64 bytes): 128 samples packed at 4 bits per sample

**Total block size (stereo):** `4 + 4 + 64 + 64 = 136 bytes`

This produces a fixed, perfectly calculable file size:
```
file_size = 16 + (total_blocks × 136)
```

---

## 5. Compression Engine — IMA ADPCM

### 5.1 What is ADPCM?

**ADPCM (Adaptive Differential Pulse-Code Modulation)** is a lossy audio compression technique. Instead of storing the absolute value of each audio sample (which requires 16 bits per sample in standard PCM), ADPCM stores only the **difference** between the current sample and a mathematical prediction of what the next sample should be. This difference is very small compared to the absolute value, and can be stored in just **4 bits per sample** instead of 16.

This achieves a direct **4:1 compression ratio** with no further tricks needed:
```
16 bits (PCM) ÷ 4 bits (ADPCM) = 4:1 compression ratio
```

### 5.2 The Adaptive Step Table

The critical innovation of IMA-ADPCM is that the quantization step size **adapts dynamically** to the content:

- When the audio is **loud and fast-moving** (drums, transients), the step size automatically grows larger so it can track fast changes
- When the audio is **quiet or sustained** (a held piano note), the step size shrinks to capture fine detail with low distortion

This is governed by the **89-entry Step Table**:
```cpp
const int16_t STEP_TABLE[89] = {
    7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, ... , 32767
};
```
These are the standardized IMA step values (public domain, IMA Digital Audio Focus Group, 1992).

The **Index Table** governs how fast the step size adapts:
```cpp
const int8_t INDEX_TABLE[16] = {
    -1, -1, -1, -1, 2, 4, 6, 8,
    -1, -1, -1, -1, 2, 4, 6, 8
};
```
- Small nibble values (0–3) → step size decreases (quiet signal)
- Large nibble values (4–7) → step size increases rapidly (loud signal)

### 5.3 Encoding Loop (per sample)

For each audio sample, the encoder:
1. Calculates the **difference**: `diff = target_sample - predictor`
2. Determines the **sign bit** (positive or negative difference)
3. Quantizes the difference into a **3-bit magnitude** (nibble bits 0–2) using the current step
4. Reconstructs the **predictor** to stay in sync with the decoder
5. Updates the **step index** based on the nibble value
6. Packs two nibbles into **one byte** (4 bits each)

```
Byte = [4-bit nibble A][4-bit nibble B]
```

### 5.4 Decoding Loop (per nibble)

The decoder exactly mirrors the encoder:
1. Extracts the sign bit and magnitude bits from each nibble
2. Reconstructs `vpdiff` from the current step and nibble bits
3. Applies sign: `predictor += vpdiff` (or `-=` if sign bit is set)
4. Clamps predictor to the valid int16 range: `[-32768, 32767]`
5. Updates the step index identically to the encoder

Because both encoder and decoder use identical state update logic, they stay **perfectly synchronized** sample by sample, ensuring clean, distortion-free decoding.

---

## 6. DSP Engine — Cinematic 3D Master

### 6.1 Purpose

The DSP (Digital Signal Processing) pipeline runs **before** the ADPCM encoding step. Its purpose is to shape the audio signal to sound incredibly modern, wide, and heavy, preparing it for a cinematic listening experience before the mild quantization loss introduced by 4-bit compression.

The key perceptual qualities to enhance are **stereo width**, **sub-bass depth**, and **transient punch**.

### 6.2 Algorithm: The Three-Stage Cinematic Processing

The WAC DSP uses three distinct techniques to actively master the audio without introducing analog distortion or noise.

**Stage 1 — Holographic 3D Stereo Widener**
```cpp
float mid = (L + R) * 0.5f;
float side = (L - R) * 0.5f;
side *= 1.30f; // 30% Width Expansion
L = mid + side;
R = mid - side;
```
It mathematically isolates the differences between the Left and Right channels (the "Side" channel), and widens it by 30%. This makes synthesizers and backing vocals sound like they are wrapping around the listener's head in 3D space.

**Stage 2 — Deep Sub-Bass Exciter**
```cpp
bassFilter[c] = 0.95f * bassFilter[c] + 0.05f * x;
float s = x + (bassFilter[c] * 0.12f);
```
Uses a simple Low-Pass filter to extract frequencies below ~85Hz. We then cleanly amplify only these sub-frequencies by 12% and mix them back in, giving the track a heavy, satisfying "weight" entirely cleanly without muddying the mix.

**Stage 3 — SPL-Style Transient Designer**
```cpp
float transient = s - transientState[c];
transientState[c] = s;
```
By subtracting the previous sample from the current sample, we calculate the instantaneous rate of change of the waveform. Using a fast-attack peak follower and slow-release envelope follower, the difference between the two extracts the **transient punch**. 

This extracted punch is applied back to the signal proportionally with a `4.0f` multiplier. This adds energy precisely at drum attack moments without touching the rest of the waveform.

**Stage 4 — Transparent Limiter**
```cpp
if (s > 0.98f) s = 0.98f;
else if (s < -0.98f) s = -0.98f;
```
A hard ceiling at 98% of maximum digital level prevents the boosted transients from clipping during ADPCM quantization. This is a perfectly transparent limit — it only activates during the loudest peaks.

---

## 7. Transcoder Application

`Transcoder.exe` is a command-line Windows application that:
1. Accepts any audio file supported by Windows (MP3, AAC, FLAC, WAV, WMA, etc.)
2. Decodes it to uncompressed 44.1kHz 16-bit stereo PCM using the **Windows Media Foundation API**
3. Passes the raw PCM through the **WAC DSP engine**
4. Encodes the filtered PCM using the **ADPCM encoder**
5. Writes the output `.wac` binary file
6. Prints real-time `PROGRESS: nn` lines to stdout (read by WarmStudio GUI)

### Command Line Usage:
```
Transcoder.exe <input_file> <output.wac>
```

### Decoding Technology:
The transcoder uses Microsoft's **Media Foundation** COM API:
- `MFCreateSourceReaderFromURL()` — opens any supported format transparently
- `SetCurrentMediaType()` — forces raw PCM output regardless of input format
- `ReadSample()` — reads decoded PCM buffers in a loop

This means the transcoder can handle any format Windows supports natively — including FLAC, MP3, AAC, and WAV — **without a single external library**.

---

## 8. WarmStudio GUI Application

`WarmStudio.exe` is a professional-grade Java Swing application compiled to a native Windows executable via a C++ launcher (`Launcher.cpp`).

### Features:
- **Media Deck**: Drag-and-drop playlist for batch transcoding
- **Transport Controls**: Play, Pause, Resume, Stop buttons for WAC playback
- **Real-time Seek Slider**: Scrub through any position in a playing WAC file
- **Volume Control**: Live master volume slider
- **Animated Equalizer Visualizer**: Dynamic bar animation synchronized to playback
- **Transcoding Progress Bar**: Real-time progress monitoring from Transcoder.exe subprocess
- **Professional Dark Theme**: Custom rendered UI with gradients and smooth animations

### WAC Decoding Engine (Java):
The Java GUI contains a fully self-contained ADPCM decoder (`decodeWac()` method) that:
1. Reads the WARM header to retrieve sample rate, channels, and block count
2. Iterates through every ADPCM block
3. Decodes each nibble using the identical Step Table and Index Table as the C++ codec
4. Feeds the decoded PCM samples directly to the Java `SourceDataLine` audio output

This allows **real-time playback with seeking** without process-spawning the Player.exe.

---

## 9. Player Application

`Player.exe` is a minimal C++ command-line player for `.wac` files.

- Reads the entire `.wac` file into memory
- Calls `WarmCodec::Decode()` to decompress to PCM
- Opens a Windows `waveOut` device at the correct sample rate and channel count
- Streams the PCM buffer to the audio hardware in chunks
- Waits for playback to complete before exiting

### Usage:
```
Player.exe <input.wac>
```

---

## 10. Copyright & Licensing

### Original Code:
Every line of logic in this project was written from scratch:
- The WAC file format and `WARM` magic header are original inventions
- The DSP transient designer algorithm is an original implementation
- The encoder/decoder loop structure, block format, and channel interleaving are original designs
- The WarmStudio GUI layout, theming, and all UI components are original

### IMA ADPCM Constants:
The `STEP_TABLE[89]` and `INDEX_TABLE[16]` numerical values originate from the **IMA ADPCM standard** (Interactive Multimedia Association, 1992). This specification is publicly available and these constants appear throughout public domain and open-source audio codecs (including Microsoft's official WAV ADPCM format). No source code was copied — only the standardized mathematical constant arrays were used, which is equivalent to referencing a published formula.

**Recommended attribution comment for `WarmCodec.h`:**
```cpp
// IMA ADPCM Step Table — Public domain standard values
// Source: IMA Digital Audio Focus & Technical Working Group, 1992
```

### Windows API:
Usage of Windows Media Foundation and `waveOut` is governed by **Microsoft's Windows SDK license**, which permits royalty-free use in commercial and personal applications.

### Java Standard Library:
Java Swing is part of the **Oracle JDK**, freely available for use under the Oracle Binary Code License (BCL) or OpenJDK GPL v2 + Classpath Exception.

---

## 11. Technical Specifications

| Property | Value |
|----------|-------|
| Format Name | WAC (Warm Audio Codec) |
| File Extension | `.wac` |
| Magic Bytes | `W`, `A`, `R`, `M` (ASCII) |
| Codec Type | Lossy (ADPCM-based) |
| Bits Per Sample (Encoded) | 4 bits |
| Bits Per Sample (Decoded) | 16 bits |
| Compression Ratio | 4:1 (vs raw PCM) |
| Sample Rate | 44,100 Hz (fixed) |
| Channels | Stereo (2ch) |
| Effective Bitrate | ~375,000 bps (~375 kbps) |
| Block Size | 128 samples per channel |
| Block Header Size | 4 bytes per channel |
| Header Size | 16 bytes |
| ADPCM Standard | IMA/DVI ADPCM (4-bit) |
| DSP Processing | Cinematic 3D Master (Stereo Widener, Sub-Bass, Punch) |
| Encoder Language | C++ (g++ MinGW) |
| GUI Language | Java (Swing) |
| Target Platform | Windows 7+ (64-bit) |
| External Libraries | None |

---

## 12. Development History

| Version | Key Change |
|---------|-----------|
| v1–v3 | Initial prototype, basic ADPCM, fixed block size |
| v4 | Introduced warmth DSP (bass shelf + soft saturation) |
| v5 | Super Compression: 2x decimation + ADPCM for 8:1 ratio |
| v6 | Ultimate Fidelity: removed decimation, added psychoacoustic noise shaping |
| v7 | True Warmth: standard IMA ADPCM, custom warm DSP (EQ + tape compression) |
| v8 | Crisp Profile: removed warmth, added transient exciter + high-pass air boost |
| v8.1 | Fixed loud distortion: dynamic headroom-aware transient scaling |
| v8.2 | Added 3D stereo depth (Mid-Side matrixing) + multiband detail |
| v8.3 | Full spectrum detail: 3-band harmonic exciter (Lows, Mids, Highs) |
| v8.4 | OTT-style upward parallel compression for micro-detail recovery |
| v9 | FLAC Transparency: restored full 44.1kHz, removed decimation entirely |
| v9.1 | SPL Transient Designer — pristine punch |
| **v13** | **Current: Cinematic 3D Spatial Audio (Stereo Width + Sub-Bass)** |

---

*WAC Codec Suite — All rights reserved. Original work created February 2026.*
