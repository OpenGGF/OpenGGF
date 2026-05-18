@echo off
REM Record a BizHawk trace for any Sonic 2 zone/act.
REM The Lua script auto-detects zone and act from RAM.
REM
REM Usage:  record_s2_trace.bat <rom_path> <bk2_path> [trace_profile]
REM Example: record_s2_trace.bat "Sonic The Hedgehog 2 (W) (REV01) [!].gen" "Movies\s2-ehz1.bk2"
REM Example: record_s2_trace.bat "Sonic The Hedgehog 2 (W) (REV01) [!].gen" "Movies\s2-lvl-select-CPZ.bk2" level_gated_reset_aware
REM
REM Output goes to: <repo>\tools\bizhawk\trace_output\
REM   (BizHawk resolves the script's relative trace_output folder from the
REM    recorder script location)
REM
REM BizHawk path can be overridden with BIZHAWK_EXE env var.
REM To see the emulator window during recording, edit HEADLESS_VISIBLE in
REM s2_trace_recorder.lua (set to true).

setlocal

set "BIZHAWK_EXE=%BIZHAWK_EXE%"
if "%BIZHAWK_EXE%"=="" set "BIZHAWK_EXE=C:\Users\farre\IdeaProjects\sonic-engine\docs\BizHawk-2.11-win-x64\EmuHawk.exe"

set "LUA_SCRIPT=%~dp0s2_trace_recorder.lua"

set "OUTPUT_DIR=%~dp0trace_output"
set "COMPRESS_SCRIPT=%~dp0..\traces\compress-traces.ps1"

if "%~1"=="" (
    echo Usage: %~nx0 ^<rom_path^> ^<bk2_path^> [trace_profile]
    echo.
    echo   rom_path   Path to Sonic 2 REV01 ROM
    echo   bk2_path   Path to BK2 movie file
    echo   trace_profile  Optional. Defaults to gameplay_unlock. Use level_gated_reset_aware for level-select BK2s.
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
for %%I in ("%~2") do set "OGGF_BK2_BASENAME=%%~nxI"
set "TRACE_PROFILE=%~3"
if "%TRACE_PROFILE%"=="" set "TRACE_PROFILE=%OGGF_S2_TRACE_PROFILE%"
if "%TRACE_PROFILE%"=="" set "TRACE_PROFILE=gameplay_unlock"
set "OGGF_S2_TRACE_PROFILE=%TRACE_PROFILE%"

echo === BizHawk Sonic 2 Trace Recorder ===
echo ROM:    %ROM_PATH%
echo Movie:  %BK2_PATH%
echo Profile: %TRACE_PROFILE%
echo Lua:    %LUA_SCRIPT%
echo Output: %OUTPUT_DIR%\
echo.
echo Starting BizHawk in headless mode...

set "POWERSHELL_EXE=%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe"

"%POWERSHELL_EXE%" -NoProfile -ExecutionPolicy Bypass -Command ^
  "Add-Type -AssemblyName System.IO.Compression.FileSystem;" ^
  "$zip = [System.IO.Compression.ZipFile]::OpenRead($env:BK2_PATH);" ^
  "$entry = $zip.Entries | Where-Object { $_.FullName -eq 'Input Log.txt' };" ^
  "if ($entry -ne $null) {" ^
  "  $reader = New-Object System.IO.StreamReader($entry.Open());" ^
  "  $frameCount = 0;" ^
  "  while (($line = $reader.ReadLine()) -ne $null) { if ($line.StartsWith('|')) { $frameCount++ } }" ^
  "  $reader.Dispose();" ^
  "  $env:OGGF_BK2_FRAME_COUNT = [string]$frameCount;" ^
  "}" ^
  "$zip.Dispose();" ^
  "$psi = New-Object System.Diagnostics.ProcessStartInfo;" ^
  "$psi.FileName = $env:BIZHAWK_EXE;" ^
  "$psi.WorkingDirectory = [System.IO.Path]::GetDirectoryName($env:BIZHAWK_EXE);" ^
  "$psi.UseShellExecute = $false;" ^
  "$psi.RedirectStandardOutput = $true;" ^
  "$psi.RedirectStandardError = $true;" ^
  "$psi.Arguments = ('--chromeless --lua \"' + $env:LUA_SCRIPT + '\" --movie \"' + $env:BK2_PATH + '\" \"' + $env:ROM_PATH + '\"');" ^
  "$proc = [System.Diagnostics.Process]::Start($psi);" ^
  "$stdout = $proc.StandardOutput.ReadToEnd();" ^
  "$stderr = $proc.StandardError.ReadToEnd();" ^
  "$proc.WaitForExit();" ^
  "if ($stdout) { [Console]::Out.Write($stdout) }" ^
  "if ($stderr) { [Console]::Error.Write($stderr) }" ^
  "exit $proc.ExitCode"

if %ERRORLEVEL% neq 0 (
    echo BizHawk exited with error code %ERRORLEVEL%
    exit /b %ERRORLEVEL%
)

echo.
echo === Trace recording complete ===
if exist "%OUTPUT_DIR%\metadata.json" (
    "%POWERSHELL_EXE%" -NoProfile -ExecutionPolicy Bypass -File "%COMPRESS_SCRIPT%" "%OUTPUT_DIR%" -ThresholdBytes 0
    if %ERRORLEVEL% neq 0 (
        echo Trace compression failed with error code %ERRORLEVEL%
        exit /b %ERRORLEVEL%
    )
    echo Output files:
    dir /b "%OUTPUT_DIR%\"
    echo.
    REM Show metadata summary
    type "%OUTPUT_DIR%\metadata.json"
) else (
    echo WARNING: No trace output found in %OUTPUT_DIR%\
    echo Check BizHawk Lua console for errors.
)
