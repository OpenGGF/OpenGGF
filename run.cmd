@echo off
REM This is a normal, non-certifying local launcher. Keep the distributable in
REM target\ so each worktree keeps its own Maven output.
call mvn -Dmse=off -DskipTests package -q
if %ERRORLEVEL% neq 0 exit /b %ERRORLEVEL%

set "JAR="
for /f "delims=" %%f in ('dir /b target\*-jar-with-dependencies.jar 2^>nul') do set "JAR=target\%%f"

if not defined JAR (
    echo No jar file found
    pause
    exit /b 1
)

java --add-exports java.base/java.lang=ALL-UNNAMED --add-exports java.desktop/sun.awt=ALL-UNNAMED --add-exports java.desktop/sun.java2d=ALL-UNNAMED -XX:+UseG1GC -XX:MaxGCPauseMillis=5 -jar "%JAR%"
