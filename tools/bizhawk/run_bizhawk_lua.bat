@echo off
REM Reusable BizHawk Lua launcher for diagnostics and one-off probes.
REM
REM Usage:
REM   run_bizhawk_lua.bat <lua_path> <bk2_path> <rom_path>
REM
REM Example:
REM   set OGGF_START=16300
REM   set OGGF_STOP=16320
REM   set OGGF_OUT=C:\tmp\htz2_diag.txt
REM   tools\bizhawk\run_bizhawk_lua.bat ^
REM     tools\bizhawk\diag_s2_htz2_obj30.lua ^
REM     src\test\resources\traces\s2\htz2\s2-lvl-select-HTZ.bk2 ^
REM     s2.gen
REM
REM BizHawk path can be overridden with BIZHAWK_EXE. The launcher writes a
REM temporary no-audio diagnostic config by default; set BIZHAWK_USE_DIAG_CONFIG=0
REM to use BizHawk's remembered config instead. It also verifies that the Lua
REM contains the fast-headless template calls; set BIZHAWK_ALLOW_SLOW_LUA=1 only
REM when deliberately running a visible/interactive diagnostic. Additional
REM EmuHawk flags can be supplied with BIZHAWK_EXTRA_ARGS. The launcher
REM intentionally keeps EmuHawk path/movie/lua/ROM quoting simple so positional
REM ROM loading stays reliable.

setlocal

if "%~1"=="" goto :usage
if "%~2"=="" goto :usage
if "%~3"=="" goto :usage

set "BIZHAWK_EXE=%BIZHAWK_EXE%"
if "%BIZHAWK_EXE%"=="" if exist "%~dp0..\..\docs\BizHawk-2.11-win-x64\EmuHawk.exe" set "BIZHAWK_EXE=%~dp0..\..\docs\BizHawk-2.11-win-x64\EmuHawk.exe"
if "%BIZHAWK_EXE%"=="" if exist "C:\Users\farre\Downloads\_Sorted\Emulators\BizHawk-2.11-win-x64\EmuHawk.exe" set "BIZHAWK_EXE=C:\Users\farre\Downloads\_Sorted\Emulators\BizHawk-2.11-win-x64\EmuHawk.exe"
if "%BIZHAWK_EXE%"=="" (
    echo ERROR: BIZHAWK_EXE is not set and no default EmuHawk.exe was found.
    exit /b 1
)

for %%I in ("%BIZHAWK_EXE%") do set "BIZHAWK_EXE=%%~fI"
for %%I in ("%~1") do set "LUA_SCRIPT=%%~fI"
for %%I in ("%~2") do set "BK2_PATH=%%~fI"
for %%I in ("%~3") do set "ROM_PATH=%%~fI"

if "%BIZHAWK_USE_DIAG_CONFIG%"=="" set "BIZHAWK_USE_DIAG_CONFIG=1"
set "BIZHAWK_HAS_DIAG_CONFIG=0"
if not "%BIZHAWK_USE_DIAG_CONFIG%"=="1" goto :after_diag_config

set "BIZHAWK_SOURCE_CONFIG=%BIZHAWK_SOURCE_CONFIG%"
if "%BIZHAWK_SOURCE_CONFIG%"=="" if exist "%~dp0..\..\docs\BizHawk-2.11-win-x64\config.ini" set "BIZHAWK_SOURCE_CONFIG=%~dp0..\..\docs\BizHawk-2.11-win-x64\config.ini"
if "%BIZHAWK_SOURCE_CONFIG%"=="" if exist "C:\Users\farre\Downloads\_Sorted\Emulators\BizHawk-2.11-win-x64\config.ini" set "BIZHAWK_SOURCE_CONFIG=C:\Users\farre\Downloads\_Sorted\Emulators\BizHawk-2.11-win-x64\config.ini"
if "%BIZHAWK_DIAG_CONFIG%"=="" set "BIZHAWK_DIAG_CONFIG=%TEMP%\openggf-bizhawk-diag-config.ini"
if "%BIZHAWK_SOURCE_CONFIG%"=="" (
    echo WARNING: No BizHawk config.ini found; relying on Lua-side sound/render toggles only.
    goto :after_diag_config
)

powershell -NoProfile -ExecutionPolicy Bypass -Command "$src=$env:BIZHAWK_SOURCE_CONFIG; $dst=$env:BIZHAWK_DIAG_CONFIG; $cfg=Get-Content -Raw -LiteralPath $src | ConvertFrom-Json; $cfg.SoundEnabled=$false; $cfg.SoundEnabledNormal=$false; $cfg.SoundEnabledRWFF=$false; $cfg.SoundVolume=0; $cfg.SoundVolumeRWFF=0; $cfg.SoundThrottle=$false; $cfg.RunLuaDuringTurbo=$true; $cfg.StartPaused=$false; $cfg | ConvertTo-Json -Depth 100 | Set-Content -NoNewline -LiteralPath $dst"
if errorlevel 1 (
    echo ERROR: Failed to create BizHawk diagnostic config from %BIZHAWK_SOURCE_CONFIG%
    exit /b 1
)
set "BIZHAWK_HAS_DIAG_CONFIG=1"

:after_diag_config

if not exist "%BIZHAWK_EXE%" (
    echo ERROR: EmuHawk.exe not found: %BIZHAWK_EXE%
    exit /b 1
)
if not exist "%LUA_SCRIPT%" (
    echo ERROR: Lua script not found: %LUA_SCRIPT%
    exit /b 1
)
if not exist "%BK2_PATH%" (
    echo ERROR: BK2 movie not found: %BK2_PATH%
    exit /b 1
)
if not exist "%ROM_PATH%" (
    echo ERROR: ROM not found: %ROM_PATH%
    exit /b 1
)

if not "%BIZHAWK_ALLOW_SLOW_LUA%"=="1" (
    powershell -NoProfile -ExecutionPolicy Bypass -Command "$lua=Get-Content -Raw -LiteralPath $env:LUA_SCRIPT; $missing=@(); if($lua -notmatch 'limitframerate\s*\(\s*false\s*\)'){$missing+='emu.limitframerate(false)'}; if($lua -notmatch 'speedmode\s*\(\s*6400\s*\)'){$missing+='client.speedmode(6400)'}; if($lua -notmatch 'invisibleemulation\s*\(\s*true\s*\)'){$missing+='client.invisibleemulation(true)'}; if($lua -notmatch 'SetSoundOn'){$missing+='client.SetSoundOn(false)'}; if($missing.Count){Write-Host ('ERROR: Lua diagnostic is missing fast-headless template call(s): ' + ($missing -join ', ')); Write-Host 'Copy tools\bizhawk\diag_template_fast.lua or set BIZHAWK_ALLOW_SLOW_LUA=1 for deliberate visible debugging.'; exit 1}"
    if errorlevel 1 (
        exit /b 1
    )
)

echo === BizHawk Lua Launcher ===
echo EmuHawk: %BIZHAWK_EXE%
echo Lua:     %LUA_SCRIPT%
echo Movie:   %BK2_PATH%
echo ROM:     %ROM_PATH%
if not "%BIZHAWK_EXTRA_ARGS%"=="" echo Extra:   %BIZHAWK_EXTRA_ARGS%
if "%BIZHAWK_HAS_DIAG_CONFIG%"=="1" echo Config:  %BIZHAWK_DIAG_CONFIG%
if not "%OGGF_START%"=="" echo OGGF_START=%OGGF_START%
if not "%OGGF_STOP%"=="" echo OGGF_STOP=%OGGF_STOP%
if not "%OGGF_OUT%"=="" echo OGGF_OUT=%OGGF_OUT%
echo.

pushd "%~dp0" >nul
if "%BIZHAWK_HAS_DIAG_CONFIG%"=="1" (
    "%BIZHAWK_EXE%" --config "%BIZHAWK_DIAG_CONFIG%" --chromeless --lua "%LUA_SCRIPT%" --movie "%BK2_PATH%" "%ROM_PATH%" %BIZHAWK_EXTRA_ARGS%
) else (
    "%BIZHAWK_EXE%" --chromeless --lua "%LUA_SCRIPT%" --movie "%BK2_PATH%" "%ROM_PATH%" %BIZHAWK_EXTRA_ARGS%
)
set "EXIT_CODE=%ERRORLEVEL%"
popd >nul

if not "%EXIT_CODE%"=="0" (
    echo BizHawk exited with error code %EXIT_CODE%
)
exit /b %EXIT_CODE%

:usage
echo Usage: %~nx0 ^<lua_path^> ^<bk2_path^> ^<rom_path^>
echo.
echo Set BIZHAWK_EXE to override EmuHawk.exe. Diagnostic scripts may also read
echo OGGF_START, OGGF_STOP, OGGF_OUT, and other script-specific env vars.
echo Set BIZHAWK_EXTRA_ARGS for rare additional EmuHawk flags.
echo Set BIZHAWK_USE_DIAG_CONFIG=0 to skip the generated no-audio config.
echo Set BIZHAWK_ALLOW_SLOW_LUA=1 to skip the fast-headless Lua guard.
exit /b 1
