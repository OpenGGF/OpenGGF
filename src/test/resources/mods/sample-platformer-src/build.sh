#!/usr/bin/env sh
set -eu
engine=$1 sdk=$2 out=$3
cp -R "$(dirname "$0")/project" "$out"
mvn -q -f "$out/pom.xml" package "-Dopenggf.engine.jar=$engine" "-Dopenggf.sdk.jar=$sdk"
