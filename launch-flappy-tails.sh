#!/usr/bin/env bash
# Build Flappy Tails and launch it with the ROMs configured in config.yaml.
# Requires Java 21, Maven, Python 3, and your Sonic 3 & Knuckles ROM.
# Usage: ./launch-flappy-tails.sh [--skip-engine]
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$script_dir"

exec python3 "$script_dir/examples/build_example.py" flappy-tails --run "$@"
