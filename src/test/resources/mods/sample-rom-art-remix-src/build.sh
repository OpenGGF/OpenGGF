#!/usr/bin/env sh
set -eu
engine=$1 sdk=$2 out=$3
cp -R "$(dirname "$0")/project" "$out"
level_source="$out/src/main/mod/level-source"
while IFS='=' read -r name data; do
  printf '%s' "$data" | base64 -d > "$level_source/$name"
done < "$level_source/binary-assets.properties"
rm "$level_source/binary-assets.properties"
mvn -q -f "$out/pom.xml" package "-Dopenggf.engine.jar=$engine" "-Dopenggf.sdk.jar=$sdk"
