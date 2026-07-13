#!/bin/sh
set -eu
engine_jar="$1"; sdk_jar="$2"; output="$3"
cp -R "$(dirname "$0")/project" "$output"
base64 -d "$output/src/main/mod/runner.png.base64" > "$output/src/main/mod/runner.png"
rm "$output/src/main/mod/runner.png.base64"
mvn -q -Dmse=off -f "$output/pom.xml" package -Dopenggf.engine.jar="$engine_jar" -Dopenggf.sdk.jar="$sdk_jar"
