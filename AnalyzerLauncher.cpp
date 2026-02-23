#include <windows.h>
#include <string>
#include <stdlib.h>

int APIENTRY WinMain(HINSTANCE hInst, HINSTANCE hPrev, LPSTR lpCmd, int nShow) {
    SetEnvironmentVariable("JAVA_TOOL_OPTIONS", "-Dfile.encoding=UTF8");
    system("java -cp . WacAnalyzer");
    return 0;
}
