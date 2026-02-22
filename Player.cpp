#include <windows.h>
#include <mmsystem.h>
#include <iostream>
#include <vector>
#include <fstream>
#include <conio.h>
#include "WarmCodec.h"

#pragma comment(lib, "winmm.lib")

// Note: Link with winmm.lib

int wmain(int argc, wchar_t* argv[]) {
    // --------------------------------------------------------------------------
    // WAC Player implementation (Unique Custom Codec Player)
    // Plays the unique deep and warm lossy codec.
    // --------------------------------------------------------------------------
    if (argc < 2) {
        std::wcout << L"========================================" << std::endl;
        std::wcout << L"  [WARM CODEC PLAYER v1.0] " << std::endl;
        std::wcout << L"========================================" << std::endl;
        std::wcout << L"Usage: Player.exe <input.wac>" << std::endl;
        return 0;
    }

    std::ifstream in(argv[1], std::ios::binary | std::ios::ate);
    if (!in.is_open()) {
        std::wcout << L"[!] Failed to open " << argv[1] << std::endl;
        return 1;
    }
    
    std::streamsize size = in.tellg();
    in.seekg(0, std::ios::beg);
    
    std::vector<uint8_t> wacData(size);
    if (!in.read((char*)wacData.data(), size)) {
        std::wcout << L"[!] Failed to read file." << std::endl;
        return 1;
    }

    uint32_t sampleRate = 0;
    uint16_t channels = 0;
    
    std::wcout << L"[*] Decoding deep '.wac' compressed audio format back into PCM..." << std::endl;
    std::vector<int16_t> pcm = WarmCodec::Decode(wacData, sampleRate, channels);
    
    if (pcm.empty()) {
        std::wcout << L"[!] Invalid, Missing Magic Signature, or Corrupted '.wac' file." << std::endl;
        return 1;
    }

    std::wcout << L"\n[+] Playing High-Quality Audio: " << argv[1] << std::endl;
    std::wcout << L"    Sample rate: " << sampleRate << L" Hz" << std::endl;
    std::wcout << L"    Channels:    " << channels << std::endl;

    // Direct Windows PCM streaming - using standard waveOut write.
    WAVEFORMATEX wfx     = {0};
    wfx.wFormatTag       = WAVE_FORMAT_PCM;
    wfx.nChannels        = channels;
    wfx.nSamplesPerSec   = sampleRate;
    wfx.wBitsPerSample   = 16;
    wfx.nBlockAlign      = wfx.nChannels * (wfx.wBitsPerSample / 8);
    wfx.nAvgBytesPerSec  = wfx.nSamplesPerSec * wfx.nBlockAlign;

    HWAVEOUT hWaveOut;
    // Open system default audio device
    if (waveOutOpen(&hWaveOut, WAVE_MAPPER, &wfx, 0, 0, CALLBACK_NULL) != MMSYSERR_NOERROR) {
        std::wcout << L"[!] Failed to open system audio output device." << std::endl;
        return 1;
    }

    WAVEHDR hdr = {0};
    hdr.lpData = (LPSTR)pcm.data();
    hdr.dwBufferLength = (DWORD)(pcm.size() * sizeof(int16_t));

    // Prepare and enqueue the audio buffers to the system hardware
    waveOutPrepareHeader(hWaveOut, &hdr, sizeof(WAVEHDR));
    waveOutWrite(hWaveOut, &hdr, sizeof(WAVEHDR));

    std::wcout << L"\n[*] --> Press ENTER or 'q' to stop playback and exit..." << std::endl;

    bool isPlaying = true;
    while(isPlaying) {
        if (_kbhit()) {
            int ch = _getch();
            if (ch == '\r' || ch == '\n' || ch == 'q' || ch == 'Q') {
                isPlaying = false;
            }
        }
        
        // Check if playback finished naturally (WHDR_DONE flag)
        if (hdr.dwFlags & WHDR_DONE) {
            isPlaying = false;
        }
        Sleep(50);
    }
    
    std::wcout << L"\n[*] Stopping playback..." << std::endl;

    waveOutReset(hWaveOut);
    waveOutUnprepareHeader(hWaveOut, &hdr, sizeof(WAVEHDR));
    waveOutClose(hWaveOut);

    return 0;
}
