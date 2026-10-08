#!/usr/bin/env bash
# Build Sitar Hero and launch it with the ROMs configured in config.yaml.
# Requires Java 21, Maven, Python 3, and at least one supported Sonic ROM.
# Usage: ./launch-sitar-hero.sh [--skip-engine]
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$script_dir"

exec python3 "$script_dir/examples/build_example.py" sitar-hero --run "$@"
