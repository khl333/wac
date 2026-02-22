#include <windows.h>
#include <mfapi.h>
#include <mfidl.h>
#include <mfreadwrite.h>
#include <iostream>
#include <vector>
#include <iomanip>
#include "WarmCodec.h"

#pragma comment(lib, "mfplat.lib")
#pragma comment(lib, "mfreadwrite.lib")
#pragma comment(lib, "mfuuid.lib")
#pragma comment(lib, "ole32.lib")

// Note: Ensure you link these libraries: mfplat.lib mfreadwrite.lib mfuuid.lib ole32.lib

bool DecodeAudioFile(const wchar_t* filename, std::vector<int16_t>& pcmData, uint32_t& sampleRate, uint16_t& channels) {
    HRESULT hr = MFStartup(MF_VERSION);
    if (FAILED(hr)) return false;

    IMFSourceReader* pReader = NULL;
    hr = MFCreateSourceReaderFromURL(filename, NULL, &pReader);
    if (FAILED(hr)) { 
        MFShutdown(); 
        return false; 
    }

    IMFMediaType* pPartialType = NULL;
    MFCreateMediaType(&pPartialType);
    pPartialType->SetGUID(MF_MT_MAJOR_TYPE, MFMediaType_Audio);
    pPartialType->SetGUID(MF_MT_SUBTYPE, MFAudioFormat_PCM);
    
    // We will decode anything into uncompressed PCM for our custom codec
    pPartialType->SetUINT32(MF_MT_AUDIO_NUM_CHANNELS, 2);
    pPartialType->SetUINT32(MF_MT_AUDIO_SAMPLES_PER_SECOND, 44100);
    pPartialType->SetUINT32(MF_MT_AUDIO_BITS_PER_SAMPLE, 16);

    hr = pReader->SetCurrentMediaType(MF_SOURCE_READER_FIRST_AUDIO_STREAM, NULL, pPartialType);
    pPartialType->Release();

    if (FAILED(hr)) { 
        std::wcout << L"Failed to set output media type to PCM." << std::endl;
        pReader->Release(); 
        MFShutdown(); 
        return false; 
    }

    // Get the actual sample rate and channel count output by decoder natively
    IMFMediaType* pUncompressedAudioType = NULL;
    hr = pReader->GetCurrentMediaType(MF_SOURCE_READER_FIRST_AUDIO_STREAM, &pUncompressedAudioType);
    if (SUCCEEDED(hr)) {
        UINT32 sr = 0, ch = 0;
        pUncompressedAudioType->GetUINT32(MF_MT_AUDIO_SAMPLES_PER_SECOND, &sr);
        pUncompressedAudioType->GetUINT32(MF_MT_AUDIO_NUM_CHANNELS, &ch);
        sampleRate = sr;
        channels = ch;
        pUncompressedAudioType->Release();
    }

    // Read blocks of decoded PCM samples continuously
    while (true) {
        IMFSample* pSample = NULL;
        DWORD flags = 0;
        hr = pReader->ReadSample(MF_SOURCE_READER_FIRST_AUDIO_STREAM, 0, NULL, &flags, NULL, &pSample);
        
        if (FAILED(hr) || (flags & MF_SOURCE_READERF_ENDOFSTREAM)) {
            break;
        }
        
        if (pSample) {
            IMFMediaBuffer* pBuffer = NULL;
            pSample->ConvertToContiguousBuffer(&pBuffer);
            BYTE* pData = NULL;
            DWORD cbData = 0;
            pBuffer->Lock(&pData, NULL, &cbData);
            
            size_t currentSize = pcmData.size();
            pcmData.resize(currentSize + cbData / 2); // 2 bytes per 16-bit sample
            memcpy(pcmData.data() + currentSize, pData, cbData);
            
            pBuffer->Unlock();
            pBuffer->Release();
            pSample->Release();
        }
    }

    pReader->Release();
    MFShutdown();
    return true;
}

int wmain(int argc, wchar_t* argv[]) {
    // --------------------------------------------------------------------------
    // Custom Codec Transcoder
    // Converts existing formats (MP3, AAC, FLAC, WAV natively via Windows API)
    // to the brand new '.wac' (Warm Audio Codec)
    // --------------------------------------------------------------------------
    if (argc < 3) {
        std::wcout << L"========================================" << std::endl;
        std::wcout << L"  [WARM CODEC TRANSCODER v1.0] " << std::endl;
        std::wcout << L"========================================" << std::endl;
        std::wcout << L"Usage: Transcoder.exe <input.mp3|aac|flac> <output.wac>" << std::endl;
        return 0;
    }
    
    std::vector<int16_t> pcm;
    uint32_t sampleRate = 44100;
    uint16_t channels = 2;
    
    // Windows API requires COM initialization for Media Foundation subsystem
    HRESULT hrInit = CoInitializeEx(NULL, COINIT_APARTMENTTHREADED | COINIT_DISABLE_OLE1DDE);

    std::wcout << L"[*] Decoding input file: " << argv[1] << L"..." << std::endl;
    if (!DecodeAudioFile(argv[1], pcm, sampleRate, channels)) {
        std::wcout << L"[!] Failed to decode file. Check if Windows supports it." << std::endl;
        CoUninitialize();
        return 1;
    }
    
    std::wcout << L"[+] Decoded " << pcm.size() << L" raw audio samples." << std::endl;
    std::wcout << L"    Sample rate: " << sampleRate << L" Hz" << std::endl;
    std::wcout << L"    Channels:    " << channels << std::endl;
    
    std::wcout << L"\n[*] Encoding PCM into deep and warm '.wac' codec format..." << std::endl;
    std::vector<uint8_t> wacData = WarmCodec::Encode(pcm, sampleRate, channels, true);
    
    std::ofstream out(argv[2], std::ios::binary);
    out.write((const char*)wacData.data(), wacData.size());
    out.close();
    
    double inputSizeMB = (pcm.size() * sizeof(int16_t)) / (1024.0 * 1024.0);
    double outputSizeMB = wacData.size() / (1024.0 * 1024.0);
    
    std::wcout << L"----------------------------------------" << std::endl;
    std::wcout << L"[+] Transcoding complete!" << std::endl;
    std::wcout << std::fixed << std::setprecision(2);
    std::wcout << L"    Original Audio Size: " << inputSizeMB << L" MB" << std::endl;
    std::wcout << L"    WAC Compressed Size: " << outputSizeMB << L" MB" << std::endl;
    std::wcout << L"    Output saved to: " << argv[2] << std::endl;
    std::wcout << L"========================================" << std::endl;

    CoUninitialize();
    return 0;
}
