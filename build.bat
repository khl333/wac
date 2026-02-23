@echo off
echo ===========================================
echo Building Unique Warm Audio Codec (.wac)
echo ===========================================

set "WIN_LIBS=mfplat.lib mfreadwrite.lib mfuuid.lib ole32.lib winmm.lib"

echo [1/4] Compiling Transcoder.cpp...
cl.exe /EHsc /O2 Transcoder.cpp %WIN_LIBS%

echo [2/4] Compiling Player.cpp...
cl.exe /EHsc /O2 Player.cpp %WIN_LIBS%

echo [3/4] Compiling WarmStudio (Java GUI)...
javac WarmStudio.java
cl.exe /EHsc /O2 Launcher.cpp user32.lib shell32.lib /FeWarmStudio.exe

echo [4/4] Compiling WacAnalyzer (Acoustic Analyzer GUI)...
javac WacAnalyzer.java
cl.exe /EHsc /O2 AnalyzerLauncher.cpp user32.lib shell32.lib /FeWacAnalyzer.exe

echo Build finished. 
echo -------------------------------------------
echo Run with: 
echo Transcoder.exe input.mp3 output.wac 
echo Player.exe output.wac
echo WarmStudio.exe
echo WacAnalyzer.exe
echo ===========================================
