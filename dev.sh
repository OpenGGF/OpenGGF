#!/usr/bin/env bash
#
# Fast dev launcher for rapid iteration. Incrementally compiles changed sources
# and runs directly from target/classes; use run.sh when a distributable jar is
# required.

set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$script_dir"

mvn -q -o -Dmse=off -Pdev-run compile exec:exec
