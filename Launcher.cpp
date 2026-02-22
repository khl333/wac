#include <windows.h>
#include <string>

int WINAPI WinMain(HINSTANCE hInstance, HINSTANCE hPrevInstance, LPSTR lpCmdLine, int nCmdShow) {
    // Launch the warm studio java jar natively without popping a console window 
    // Uses javaw so there is no terminal shown
    ShellExecuteA(NULL, "open", "javaw", "-Xms64m -Xmx256m WarmStudio", NULL, SW_HIDE);
    return 0;
}
