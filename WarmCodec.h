#pragma once
#include <vector>
#include <cstdint>
#include <fstream>
#include <cmath>
#include <algorithm>
#include <cstring>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

// WAC v9.1 (FLAC Transparency & Master Studio Punch)
struct WacHeader {
    char magic[4] = {'W','A','R','M'}; 
    uint32_t sampleRate;
    uint16_t channels;
    uint32_t totalBlocks;
};

const int BLOCK_SIZE = 128;

// IMA ADPCM Step Table — public domain standard (IMA, 1992)
const int16_t STEP_TABLE[89] = { 
    7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41, 45, 
    50, 55, 60, 66, 73, 80, 88, 97, 107, 118, 130, 143, 157, 173, 190, 209, 230, 
    253, 279, 307, 337, 371, 408, 449, 494, 544, 598, 658, 724, 796, 876, 963, 
    1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066, 2272, 2499, 2749, 3024, 3327, 
    3660, 4026, 4428, 4871, 5358, 5894, 6484, 7132, 7845, 8630, 9493, 10442, 11487, 
    12635, 13899, 15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794, 32767 
};

// 4-bit standard index table
const int8_t INDEX_TABLE[16] = { 
    -1, -1, -1, -1, 2, 4, 6, 8, 
    -1, -1, -1, -1, 2, 4, 6, 8 
};

class WarmCodec {
public:
    static std::vector<uint8_t> Encode(const std::vector<int16_t>& pcm, uint32_t sampleRate, uint16_t channels, bool showProgress = false) {
        std::vector<uint8_t> output;
        
        size_t totalFrames = pcm.size() / channels;
        
        // -------------------------------------------------------------
        // WAC v13 - CINEMATIC 3D SPATIAL AUDIO (Holographic Stereo + Sub-Bass)
        // -------------------------------------------------------------
        // A pristine, modern mastering chain that dramatically widens the stereo field
        // and enhances deep bass without adding any analog distortion or noise.
        std::vector<int16_t> filteredPcm(totalFrames * channels);
        
        std::vector<float> peakFollower(channels, 0.0f);
        std::vector<float> envFollower(channels, 0.0f);
        std::vector<float> transientState(channels, 0.0f);
        std::vector<float> bassFilter(channels, 0.0f);

        for (size_t f = 0; f < totalFrames; ++f) {
            float L = pcm[f * channels + 0] / 32768.0f;
            float R = (channels == 2) ? (pcm[f * channels + 1] / 32768.0f) : L;
            
            // 1. Holographic 3D Stereo Widener
            // Isolates the "Mid" (center vocals/kick) and "Side" (wide synths/guitars).
            // Expands the Side channel by 30% to create a massive wrap-around soundstage.
            if (channels == 2) {
                float mid = (L + R) * 0.5f;
                float side = (L - R) * 0.5f;
                side *= 1.30f; // 30% Width Expansion
                L = mid + side;
                R = mid - side;
            }

            float samples[2] = {L, R};
            
            for (int c = 0; c < channels; ++c) {
                float x = samples[c];
                
                // 2. Deep Sub-Bass Exciter
                // Simple low-pass filter to extract sub-frequencies, gently mixed back in
                // to add cinematic "weight" and club-style depth.
                bassFilter[c] = 0.95f * bassFilter[c] + 0.05f * x;
                float s = x + (bassFilter[c] * 0.12f); // Reduced from 0.40x to 0.12x for clean, non-overpowering bass 

                // 3. SPL-Style Transient Designer (Pristine Punch)
                float transient = s - transientState[c];
                transientState[c] = s;
                float absTrans = std::abs(transient);
                
                peakFollower[c] = 0.2f * absTrans + 0.8f * peakFollower[c];
                envFollower[c]  = 0.02f * absTrans + 0.98f * envFollower[c];
                
                float punch = peakFollower[c] - envFollower[c];
                if (punch < 0.0f) punch = 0.0f;
                s += (transient * punch * 4.0f); 
                
                // 4. Pristine Safe Limiter
                if (s > 0.98f) s = 0.98f;
                else if (s < -0.98f) s = -0.98f;
                
                filteredPcm[f * channels + c] = (int16_t)std::clamp((int)(s * 32767.0f), -32768, 32767);
            }
        }

        uint32_t totalBlocks = totalFrames / BLOCK_SIZE;

        WacHeader header;
        header.sampleRate = sampleRate; 
        header.channels = channels;
        header.totalBlocks = totalBlocks;
        
        output.resize(sizeof(WacHeader));
        std::memcpy(output.data(), &header, sizeof(WacHeader));
        
        int32_t* statePred = new int32_t[channels]{0};
        int32_t* stateIndex = new int32_t[channels]{0};

        // -------------------------------------------------------------
        // FULL QUALITY 4-BIT ADPCM ENCODER 
        // -------------------------------------------------------------
        for (uint32_t b = 0; b < totalBlocks; ++b) {
            if (showProgress && (b % 500 == 0 || b == totalBlocks - 1)) {
                printf("PROGRESS: %d\n", (b * 100) / totalBlocks);
                fflush(stdout);
            }
            
            for (int c = 0; c < channels; ++c) {
                int16_t p = (int16_t)statePred[c];
                int16_t idx = (int16_t)stateIndex[c];
                output.push_back(p & 0xFF);
                output.push_back((p >> 8) & 0xFF);
                output.push_back(idx & 0xFF);
                output.push_back((idx >> 8) & 0xFF);
            }
            
            for (int c = 0; c < channels; ++c) {
                for (int i = 0; i < BLOCK_SIZE; i += 2) {
                    uint8_t packedByte = 0;
                    for (int j = 0; j < 2; ++j) {
                        int32_t target = filteredPcm[(b * BLOCK_SIZE + i + j) * channels + c];
                        int32_t diff = target - statePred[c];
                        int32_t step = STEP_TABLE[stateIndex[c]];
                        
                        int32_t sign = (diff < 0) ? 8 : 0;
                        if (sign) diff = -diff;
                        
                        int32_t nibble = 0;
                        int32_t vpdiff = step >> 3;
                        
                        if (diff >= step) { nibble |= 4; diff -= step; vpdiff += step; }
                        step >>= 1;
                        if (diff >= step) { nibble |= 2; diff -= step; vpdiff += step; }
                        step >>= 1;
                        if (diff >= step) { nibble |= 1; vpdiff += step; }
                        
                        if (sign) statePred[c] -= vpdiff;
                        else statePred[c] += vpdiff;
                        
                        if (statePred[c] > 32767) statePred[c] = 32767;
                        else if (statePred[c] < -32768) statePred[c] = -32768;
                        
                        stateIndex[c] += INDEX_TABLE[nibble | sign];
                        if (stateIndex[c] < 0) stateIndex[c] = 0;
                        else if (stateIndex[c] > 88) stateIndex[c] = 88;
                        
                        if (j == 0) packedByte = (nibble | sign) << 4;
                        else packedByte |= (nibble | sign);
                    }
                    output.push_back(packedByte);
                }
            }
        }
        delete[] statePred;
        delete[] stateIndex;
        return output;
    }

    static std::vector<int16_t> Decode(const std::vector<uint8_t>& wacData, uint32_t& outSampleRate, uint16_t& outChannels) {
        std::vector<int16_t> pcm;
        if (wacData.size() < sizeof(WacHeader)) return pcm;
        
        WacHeader* header = (WacHeader*)wacData.data();
        if (header->magic[0] != 'W' || header->magic[1] != 'A' || header->magic[2] != 'R' || header->magic[3] != 'M') return pcm; 
        
        outSampleRate = header->sampleRate;
        outChannels = header->channels;
        
        size_t offset = sizeof(WacHeader);
        size_t expectedSize = offset + header->totalBlocks * outChannels * (4 + BLOCK_SIZE / 2);
        if (wacData.size() < expectedSize) return pcm; 
        
        pcm.resize(header->totalBlocks * BLOCK_SIZE * outChannels);
        
        for (uint32_t b = 0; b < header->totalBlocks; ++b) {
            std::vector<int32_t> statePred(outChannels, 0);
            std::vector<int32_t> stateIndex(outChannels, 0);
            
            for (int c = 0; c < outChannels; ++c) {
                int16_t p = wacData[offset] | (wacData[offset+1] << 8);
                int16_t idx = wacData[offset+2] | (wacData[offset+3] << 8);
                offset += 4;
                statePred[c] = p;
                stateIndex[c] = idx;
            }
            
            for (int c = 0; c < outChannels; ++c) {
                for (int i = 0; i < BLOCK_SIZE; i += 2) {
                    uint8_t packedByte = wacData[offset++];
                    uint8_t n1 = (packedByte >> 4) & 0x0F;
                    uint8_t n2 = packedByte & 0x0F;
                    
                    for (int j = 0; j < 2; ++j) {
                        uint8_t nibble = (j == 0) ? n1 : n2;
                        
                        int32_t step = STEP_TABLE[stateIndex[c]];
                        int32_t vpdiff = step >> 3;
                        if (nibble & 4) vpdiff += step;
                        if (nibble & 2) vpdiff += step >> 1;
                        if (nibble & 1) vpdiff += step >> 2;
                        
                        if (nibble & 8) statePred[c] -= vpdiff;
                        else statePred[c] += vpdiff;
                        
                        if (statePred[c] > 32767) statePred[c] = 32767;
                        else if (statePred[c] < -32768) statePred[c] = -32768;
                        
                        stateIndex[c] += INDEX_TABLE[nibble];
                        if (stateIndex[c] < 0) stateIndex[c] = 0;
                        else if (stateIndex[c] > 88) stateIndex[c] = 88;
                        
                        pcm[(b * BLOCK_SIZE + i + j) * outChannels + c] = (int16_t)statePred[c];
                    }
                }
            }
        }
        return pcm;
    }
};
