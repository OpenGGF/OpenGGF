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
REM BizHawk path can be overridden with BIZHAWK_EXE. Additional EmuHawk flags
REM can be supplied with BIZHAWK_EXTRA_ARGS. The launcher intentionally avoids
REM embedded PowerShell quoting so EmuHawk receives clean path arguments.

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

echo === BizHawk Lua Launcher ===
echo EmuHawk: %BIZHAWK_EXE%
echo Lua:     %LUA_SCRIPT%
echo Movie:   %BK2_PATH%
echo ROM:     %ROM_PATH%
if not "%BIZHAWK_EXTRA_ARGS%"=="" echo Extra:   %BIZHAWK_EXTRA_ARGS%
if not "%OGGF_START%"=="" echo OGGF_START=%OGGF_START%
if not "%OGGF_STOP%"=="" echo OGGF_STOP=%OGGF_STOP%
if not "%OGGF_OUT%"=="" echo OGGF_OUT=%OGGF_OUT%
echo.

pushd "%~dp0" >nul
"%BIZHAWK_EXE%" --chromeless --lua "%LUA_SCRIPT%" --movie "%BK2_PATH%" "%ROM_PATH%" %BIZHAWK_EXTRA_ARGS%
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
exit /b 1
