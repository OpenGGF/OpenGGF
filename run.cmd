@echo off
REM This is a normal, non-certifying local launcher. Keep the distributable in
REM target\ and leave certifying builds to tools\testing\test-session.ps1.
call mvn -Dmse=off -Dopenggf.session.guard.skip=true -DskipTests package -q
if %ERRORLEVEL% neq 0 exit /b %ERRORLEVEL%

set "JAR="
for /f "delims=" %%f in ('dir /b target\*-jar-with-dependencies.jar 2^>nul') do set "JAR=target\%%f"

if not defined JAR (
    echo No jar file found
    pause
    exit /b 1
)

java --add-exports java.base/java.lang=ALL-UNNAMED --add-exports java.desktop/sun.awt=ALL-UNNAMED --add-exports java.desktop/sun.java2d=ALL-UNNAMED -XX:+UseG1GC -XX:MaxGCPauseMillis=5 -jar "%JAR%"
