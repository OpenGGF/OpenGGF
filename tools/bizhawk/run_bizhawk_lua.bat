@echo off
REM Reusable BizHawk Lua launcher for diagnostics and one-off probes.
REM
REM Usage:
REM   run_bizhawk_lua.bat <lua_path> <bk2_path> <rom_path>
REM
REM Example:
REM   set OGGF_START=16300
REM   set OGGF_STOP=16320
REM   set "AGENT_WSL_SCRATCH_ROOT=/mnt/<drive>/agent-scratch"
REM   set "OGGF_TASK_DIR="
REM   for /f "usebackq delims=" %%I in (`wsl.exe bash -lc "export AGENT_SCRATCH_ROOT=^"%AGENT_WSL_SCRATCH_ROOT%^" ^&^& agent-scratch status ^>/dev/null ^&^& task_dir=$^(agent-scratch new htz2-diag ^| tail -n 1^) ^&^& wslpath -w ^"$task_dir^""`) do set "OGGF_TASK_DIR=%%I"
REM   if not defined OGGF_TASK_DIR (
REM     >&2 echo ERROR: WSL did not provision OGGF_TASK_DIR
REM     exit /b 2
REM   )
REM   if /i not "%OGGF_TASK_DIR:~1,2%"==":\" (
REM     >&2 echo ERROR: OGGF_TASK_DIR is not a Windows drive path
REM     exit /b 2
REM   )
REM   if not exist "%OGGF_TASK_DIR%\NUL" (
REM     >&2 echo ERROR: OGGF_TASK_DIR is not an accessible directory
REM     exit /b 2
REM   )
REM   set "OGGF_OUT=%OGGF_TASK_DIR%\htz2_diag.txt"
REM   tools\bizhawk\run_bizhawk_lua.bat ^
REM     tools\bizhawk\diag_s2_htz2_obj30.lua ^
REM     src\test\resources\traces\s2\htz2\s2-lvl-select-HTZ.bk2 ^
REM     s2.gen
REM
REM AGENT_WSL_SCRATCH_ROOT must name the disk-backed WSL root accessible from Windows. Install
REM the POSIX bootstrap/source with tools/agent-scratch install after helper updates; normal
REM execution uses the installed user-wide agent-scratch command without a checkout dependency.
REM The handoff exports and preflights that root, then converts the task path to a Windows path
REM before assigning OGGF_TASK_DIR; do not use C:\tmp, %%TEMP%%, or %%TMP%% for durable output.
REM BizHawk path can be overridden with BIZHAWK_EXE. The launcher writes a
REM temporary no-audio diagnostic config by default; set BIZHAWK_USE_DIAG_CONFIG=0
REM to use BizHawk's remembered config instead. It also wraps the Lua with the
REM fast-headless template calls and verifies that the diagnostic itself still
REM contains executable fast-headless calls. The default fast path starts
REM EmuHawk with a hidden window and the Lua wrapper disables rendering.
REM Deliberately visible/interactive diagnostics must set both
REM BIZHAWK_ALLOW_SLOW_LUA=1 and BIZHAWK_CONFIRM_VISIBLE_DEBUG=1 so stale shells
REM do not silently bypass the no-audio/no-render path. Additional EmuHawk flags
REM can be supplied with BIZHAWK_EXTRA_ARGS.

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
if "%OGGF_TRACE_SOURCE_BK2%"=="" for %%I in ("%BK2_PATH%") do set "OGGF_TRACE_SOURCE_BK2=%%~nxI"
set "BIZHAWK_EFFECTIVE_LUA=%LUA_SCRIPT%"
set "BIZHAWK_LAUNCH_ID=%RANDOM%-%RANDOM%"
if "%BIZHAWK_FAST_WRAPPER%"=="" set "BIZHAWK_FAST_WRAPPER=%TEMP%\openggf-bizhawk-fast-wrapper-%BIZHAWK_LAUNCH_ID%.lua"

if "%BIZHAWK_ALLOW_SLOW_LUA%"=="1" if not "%BIZHAWK_CONFIRM_VISIBLE_DEBUG%"=="1" (
    echo ERROR: BIZHAWK_ALLOW_SLOW_LUA=1 disables the no-audio/no-render wrapper.
    echo Set BIZHAWK_CONFIRM_VISIBLE_DEBUG=1 as well only for deliberate visible debugging.
    exit /b 1
)

if "%BIZHAWK_USE_DIAG_CONFIG%"=="" set "BIZHAWK_USE_DIAG_CONFIG=1"
set "BIZHAWK_HAS_DIAG_CONFIG=0"
if not "%BIZHAWK_USE_DIAG_CONFIG%"=="1" goto :after_diag_config

set "BIZHAWK_SOURCE_CONFIG=%BIZHAWK_SOURCE_CONFIG%"
if "%BIZHAWK_SOURCE_CONFIG%"=="" if exist "%~dp0..\..\docs\BizHawk-2.11-win-x64\config.ini" set "BIZHAWK_SOURCE_CONFIG=%~dp0..\..\docs\BizHawk-2.11-win-x64\config.ini"
if "%BIZHAWK_SOURCE_CONFIG%"=="" if exist "C:\Users\farre\Downloads\_Sorted\Emulators\BizHawk-2.11-win-x64\config.ini" set "BIZHAWK_SOURCE_CONFIG=C:\Users\farre\Downloads\_Sorted\Emulators\BizHawk-2.11-win-x64\config.ini"
if "%BIZHAWK_DIAG_CONFIG%"=="" set "BIZHAWK_DIAG_CONFIG=%TEMP%\openggf-bizhawk-diag-config-%BIZHAWK_LAUNCH_ID%.ini"
if "%BIZHAWK_SOURCE_CONFIG%"=="" (
    echo WARNING: No BizHawk config.ini found; relying on Lua-side sound/render toggles only.
    goto :after_diag_config
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0create_bizhawk_diag_config.ps1" -SourceConfig "%BIZHAWK_SOURCE_CONFIG%" -DiagConfig "%BIZHAWK_DIAG_CONFIG%"
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

REM Hash the exact movie path handed to EmuHawk. Always overwrite any caller
REM value so identity-pinning probes verify bytes, not a claimed digest.
for /f "usebackq delims=" %%H in (`powershell -NoProfile -Command "(Get-FileHash -Algorithm SHA256 -LiteralPath $env:BK2_PATH).Hash.ToLowerInvariant()"`) do set "OGGF_BIZHAWK_MOVIE_SHA256=%%H"
if "%OGGF_BIZHAWK_MOVIE_SHA256%"=="" (
    echo ERROR: Failed to hash BK2 movie: %BK2_PATH%
    exit /b 1
)

if not "%BIZHAWK_ALLOW_SLOW_LUA%"=="1" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0prepare_bizhawk_fast_lua.ps1" -LuaScript "%LUA_SCRIPT%" -WrapperPath "%BIZHAWK_FAST_WRAPPER%"
    if errorlevel 1 (
        exit /b 1
    )
    for %%I in ("%BIZHAWK_FAST_WRAPPER%") do set "BIZHAWK_EFFECTIVE_LUA=%%~fI"
)

echo === BizHawk Lua Launcher ===
echo EmuHawk: %BIZHAWK_EXE%
echo Lua:     %LUA_SCRIPT%
if not "%BIZHAWK_EFFECTIVE_LUA%"=="%LUA_SCRIPT%" echo Wrapper: %BIZHAWK_EFFECTIVE_LUA%
echo Movie:   %BK2_PATH%
echo MovieSHA: %OGGF_BIZHAWK_MOVIE_SHA256%
echo ROM:     %ROM_PATH%
if "%BIZHAWK_ALLOW_SLOW_LUA%"=="1" (echo Mode:    visible/slow Lua allowed ^(explicitly confirmed^)) else (echo Mode:    no-audio config + fast no-render Lua wrapper)
if not "%BIZHAWK_EXTRA_ARGS%"=="" echo Extra:   %BIZHAWK_EXTRA_ARGS%
if "%BIZHAWK_HAS_DIAG_CONFIG%"=="1" echo Config:  %BIZHAWK_DIAG_CONFIG%
if not "%OGGF_START%"=="" echo OGGF_START=%OGGF_START%
if not "%OGGF_STOP%"=="" echo OGGF_STOP=%OGGF_STOP%
if not "%OGGF_OUT%"=="" echo OGGF_OUT=%OGGF_OUT%
echo.

REM Authoritative shared-lib path for the recorders' oggf_lib_dir() loader
REM (tools/bizhawk/lib/oggf_trace_common.lua). Wins over the debug.getinfo and
REM CWD fallbacks; child EmuHawk inherits it on both the direct and wrapper
REM launch routes. Trailing backslash matches the loader's dir .. "file" concat.
set "OGGF_BIZHAWK_LIB=%~dp0lib\"
set "OGGF_BIZHAWK_PROBE_RUNTIME=%~dp0probes\probe_runtime.lua"

pushd "%~dp0" >nul
if "%BIZHAWK_ALLOW_SLOW_LUA%"=="1" (
    if "%BIZHAWK_HAS_DIAG_CONFIG%"=="1" (
        "%BIZHAWK_EXE%" --audiosync false --config "%BIZHAWK_DIAG_CONFIG%" --chromeless --lua "%BIZHAWK_EFFECTIVE_LUA%" --movie "%BK2_PATH%" "%ROM_PATH%" %BIZHAWK_EXTRA_ARGS%
    ) else (
        "%BIZHAWK_EXE%" --audiosync false --chromeless --lua "%BIZHAWK_EFFECTIVE_LUA%" --movie "%BK2_PATH%" "%ROM_PATH%" %BIZHAWK_EXTRA_ARGS%
    )
) else (
    if "%BIZHAWK_HAS_DIAG_CONFIG%"=="1" (
        powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0run_bizhawk_hidden.ps1" -EmuHawkExe "%BIZHAWK_EXE%" -LuaScript "%BIZHAWK_EFFECTIVE_LUA%" -MoviePath "%BK2_PATH%" -RomPath "%ROM_PATH%" -ConfigPath "%BIZHAWK_DIAG_CONFIG%" -ExtraArgs "%BIZHAWK_EXTRA_ARGS%"
    ) else (
        powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0run_bizhawk_hidden.ps1" -EmuHawkExe "%BIZHAWK_EXE%" -LuaScript "%BIZHAWK_EFFECTIVE_LUA%" -MoviePath "%BK2_PATH%" -RomPath "%ROM_PATH%" -ExtraArgs "%BIZHAWK_EXTRA_ARGS%"
    )
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
echo Set BIZHAWK_ALLOW_SLOW_LUA=1 and BIZHAWK_CONFIRM_VISIBLE_DEBUG=1 to skip the fast-headless Lua guard.
exit /b 1
