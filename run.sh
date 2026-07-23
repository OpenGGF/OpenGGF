#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$script_dir"

mvn -Dmse=off -DskipTests package -q

shopt -s nullglob
jars=(target/*-jar-with-dependencies.jar)
shopt -u nullglob

if (( ${#jars[@]} == 0 )); then
    echo "No jar file found in $script_dir/target" >&2
    exit 1
fi

jar="${jars[${#jars[@]} - 1]}"
exec java \
    --add-exports java.base/java.lang=ALL-UNNAMED \
    --add-exports java.desktop/sun.awt=ALL-UNNAMED \
    --add-exports java.desktop/sun.java2d=ALL-UNNAMED \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=5 \
    -jar "$jar"
