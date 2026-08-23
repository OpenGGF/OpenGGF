#!/usr/bin/env bash
set -euo pipefail

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)"
exec java --source 21 "$script_dir/TestSessionCoordinator.java" --reuse-stale "$@"
