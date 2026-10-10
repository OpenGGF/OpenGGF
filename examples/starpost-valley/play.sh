#!/bin/sh
# Play Starpost Valley from this checkout: rebuilds the engine when its sources changed,
# packages the mod, and launches OpenGGF with it loaded as a development mod.
#
#   examples/starpost-valley/play.sh            # build what changed, then play
#   examples/starpost-valley/play.sh --fresh    # first delete the mod's save and settings
#   examples/starpost-valley/play.sh --rebuild  # force an engine rebuild
#
# Needs Java 21 and Maven, as for the engine, and your Sonic 3 & Knuckles and Sonic 1 ROMs set
# up for OpenGGF in the repository root (the config.yaml and ROMs the engine normally runs with).
# On the master title choose Sonic 3 & Knuckles: the mod's title screen opens. Holding Escape
# returns to the master title; close the window to quit.
#
# Origin: Starpost Valley example mod, 2026-10-09 (after Slay the Robotnik's play.sh).
set -e
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
cd "$ROOT"

fresh=0
rebuild=0
for arg in "$@"; do
    case "$arg" in
        --fresh) fresh=1 ;;
        --rebuild) rebuild=1 ;;
        -h|--help) sed -n '2,13p' "$0"; exit 0 ;;
        *) echo "Unknown option: $arg (try --help)" >&2; exit 2 ;;
    esac
done

if [ "$fresh" = 1 ]; then
    rm -rf saves/mods/starpost-valley
    echo "Cleared saves/mods/starpost-valley"
fi

marker=target/examples-classpath.txt
if [ "$rebuild" = 0 ] && [ -f "$marker" ] && [ -d target/classes ] \
        && [ -z "$(find src/main pom.xml -newer "$marker" -print 2>/dev/null | head -n 1)" ]; then
    exec python3 examples/build_example.py starpost-valley --run --skip-engine
fi
exec python3 examples/build_example.py starpost-valley --run
