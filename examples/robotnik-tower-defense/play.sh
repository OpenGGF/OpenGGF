#!/usr/bin/env bash
set -euo pipefail
task_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
cd -- "$task_root"
exec python3 examples/build_example.py robotnik-tower-defense --run "$@"
