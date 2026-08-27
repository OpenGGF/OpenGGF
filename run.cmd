@echo off
REM This is a normal, non-certifying local launcher. Keep the distributable in
REM target\.
setlocal
cd /d "%~dp0"
if errorlevel 1 goto :failed
call mvn -Dmse=off -DskipTests package -q
if errorlevel 1 goto :failed

set "JAR="
for /f "delims=" %%f in ('dir /b target\*-jar-with-dependencies.jar 2^>nul') do set "JAR=target\%%f"

if not defined JAR (
    echo No jar file found
    pause
    set "EXIT_CODE=1"
    goto :finished
)

java --add-exports java.base/java.lang=ALL-UNNAMED --add-exports java.desktop/sun.awt=ALL-UNNAMED --add-exports java.desktop/sun.java2d=ALL-UNNAMED -XX:+UseG1GC -XX:MaxGCPauseMillis=5 -jar "%JAR%"
set "EXIT_CODE=%ERRORLEVEL%"
goto :finished

:failed
set "EXIT_CODE=%ERRORLEVEL%"

:finished
endlocal & exit /b %EXIT_CODE%
