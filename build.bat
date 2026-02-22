@echo off
echo ===========================================
echo Building Unique Warm Audio Codec (.wac)
echo ===========================================

set "WIN_LIBS=mfplat.lib mfreadwrite.lib mfuuid.lib ole32.lib winmm.lib"

echo [1/2] Compiling Transcoder.cpp...
cl.exe /EHsc /O2 Transcoder.cpp %WIN_LIBS%

echo [2/2] Compiling Player.cpp...
cl.exe /EHsc /O2 Player.cpp %WIN_LIBS%

echo Build finished. 
echo -------------------------------------------
echo Run with: 
echo Transcoder.exe input.mp3 output.wac 
echo Player.exe output.wac
echo ===========================================
