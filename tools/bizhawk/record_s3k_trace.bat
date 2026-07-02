@echo off
REM Record a BizHawk trace for any Sonic 3&K zone/act.
REM The Lua script auto-detects zone and act from RAM.
REM
REM Usage:  record_s3k_trace.bat <rom_path> <bk2_path> [trace_profile]
REM Example: record_s3k_trace.bat "Sonic and Knuckles & Sonic 3 (W) [!].gen" "Movies\s3k-aiz1.bk2"
REM Example: record_s3k_trace.bat "Sonic and Knuckles & Sonic 3 (W) [!].gen" "src\test\resources\traces\s3k\aiz1_to_hcz_fullrun\s3k-aiz1-aiz2-sonictails.bk2" aiz_end_to_end
REM
REM Output goes to: <repo>\tools\bizhawk\trace_output\
REM   (BizHawk resolves the script's relative trace_output folder from the
REM    recorder script location)
REM
REM BizHawk path can be overridden with BIZHAWK_EXE env var.
REM To see the emulator window during recording, edit HEADLESS_VISIBLE in
REM s3k_trace_recorder.lua (set to true).

setlocal

set "LUA_SCRIPT=%~dp0s3k_trace_recorder.lua"

set "OUTPUT_DIR=%~dp0trace_output"
set "COMPRESS_SCRIPT=%~dp0..\traces\compress-traces.ps1"

if "%~1"=="" (
    echo Usage: %~nx0 ^<rom_path^> ^<bk2_path^> [trace_profile]
    echo.
    echo   rom_path   Path to Sonic 3 ^& Knuckles locked-on ROM
    echo   bk2_path   Path to BK2 movie file
    echo   trace_profile  Optional. Defaults to gameplay_unlock. Use aiz_end_to_end for the AIZ intro through HCZ fixture.
    echo.
    echo The script auto-detects zone and act from the game's RAM.
    echo Output is written to: %OUTPUT_DIR%\
    exit /b 1
)
if "%~2"=="" (
    echo Usage: %~nx0 ^<rom_path^> ^<bk2_path^> [trace_profile]
    exit /b 1
)

for %%I in ("%~1") do set "ROM_PATH=%%~fI"
for %%I in ("%~2") do set "BK2_PATH=%%~fI"
set "TRACE_PROFILE=%~3"
if "%TRACE_PROFILE%"=="" set "TRACE_PROFILE=%OGGF_S3K_TRACE_PROFILE%"
if "%TRACE_PROFILE%"=="" set "TRACE_PROFILE=gameplay_unlock"
set "OGGF_S3K_TRACE_PROFILE=%TRACE_PROFILE%"

echo === BizHawk Sonic 3^&K Trace Recorder ===
echo ROM:    %ROM_PATH%
echo Movie:  %BK2_PATH%
echo Profile: %TRACE_PROFILE%
echo Lua:    %LUA_SCRIPT%
echo Output: %OUTPUT_DIR%\
echo.
echo Starting BizHawk through reusable no-audio/no-render launcher...

set "POWERSHELL_EXE=%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe"

set "OGGF_BK2_FRAME_COUNT="
for /f "usebackq delims=" %%I in (`%POWERSHELL_EXE% -NoProfile -ExecutionPolicy Bypass -File "%~dp0count_bk2_input_frames.ps1" "%BK2_PATH%"`) do set "OGGF_BK2_FRAME_COUNT=%%I"
if "%OGGF_BK2_FRAME_COUNT%"=="" (
    echo ERROR: Failed to count BK2 input frames for %BK2_PATH%
    exit /b 1
)
echo BK2 input frames: %OGGF_BK2_FRAME_COUNT%

call "%~dp0run_bizhawk_lua.bat" "%LUA_SCRIPT%" "%BK2_PATH%" "%ROM_PATH%"

if %ERRORLEVEL% neq 0 (
    echo BizHawk exited with error code %ERRORLEVEL%
    exit /b %ERRORLEVEL%
)

echo.
echo === Trace recording complete ===
if exist "%OUTPUT_DIR%\metadata.json" (
    "%POWERSHELL_EXE%" -NoProfile -ExecutionPolicy Bypass -File "%COMPRESS_SCRIPT%" "%OUTPUT_DIR%"
    if %ERRORLEVEL% neq 0 (
        echo Trace compression failed with error code %ERRORLEVEL%
        exit /b %ERRORLEVEL%
    )
    echo Output files:
    dir /b "%OUTPUT_DIR%\"
    echo.
    type "%OUTPUT_DIR%\metadata.json"
) else (
    echo WARNING: No trace output found in %OUTPUT_DIR%\
    echo Check BizHawk Lua console for errors.
)
