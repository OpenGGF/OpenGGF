#!/bin/sh
# Play Typing of the Hedgehog from this checkout: rebuilds the engine when its sources changed,
# packages the mod, and launches OpenGGF with it loaded as a development mod.
#
#   examples/typing-of-the-hedgehog/play.sh            # build what changed, then play
#   examples/typing-of-the-hedgehog/play.sh --fresh    # first delete the mod's records, progress and settings
#   examples/typing-of-the-hedgehog/play.sh --rebuild  # force an engine rebuild
#
# Needs Java 21 and Maven, as for the engine, and at least one of your Sonic 1, Sonic 2 or
# Sonic 3 & Knuckles ROMs set up for OpenGGF in the repository root. The mod's title screen
# opens from the master title; holding Escape returns there; close the window to quit.
#
# Origin: Typing of the Hedgehog example mod, 2026-10-09.
set -e
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
cd "$ROOT"

fresh=0
rebuild=0
for arg in "$@"; do
    case "$arg" in
        --fresh) fresh=1 ;;
        --rebuild) rebuild=1 ;;
        -h|--help) sed -n '2,12p' "$0"; exit 0 ;;
        *) echo "Unknown option: $arg (try --help)" >&2; exit 2 ;;
    esac
done

if [ "$fresh" = 1 ]; then
    rm -rf saves/mods/typing-of-the-hedgehog
    echo "Cleared saves/mods/typing-of-the-hedgehog"
fi

# The build rewrites target/examples-classpath.txt whenever it builds the engine, so anything in
# the engine's sources or POM newer than that file means the engine needs rebuilding.
marker=target/examples-classpath.txt
if [ "$rebuild" = 0 ] && [ -f "$marker" ] && [ -d target/classes ] \
        && [ -z "$(find src/main pom.xml -newer "$marker" -print 2>/dev/null | head -n 1)" ]; then
    exec python3 examples/typing-of-the-hedgehog/build.py --run --skip-engine
fi
exec python3 examples/typing-of-the-hedgehog/build.py --run
