#!/bin/sh
# Play Slay the Robotnik from this checkout: rebuilds the engine when its sources changed,
# packages the mod, and launches OpenGGF with it loaded as a development mod.
#
#   examples/slay-the-robotnik/play.sh            # build what changed, then play
#   examples/slay-the-robotnik/play.sh --fresh    # first delete the mod's saved run, records and settings
#   examples/slay-the-robotnik/play.sh --rebuild  # force an engine rebuild
#
# Needs Java 21 and Maven, as for the engine, and your Sonic 3 & Knuckles ROM set up for
# OpenGGF in the repository root (the config.yaml and ROM the engine normally runs with).
# On the master title choose Sonic 3 & Knuckles: the mod's title screen opens. Holding Escape
# returns to the master title; close the window to quit.
#
# Origin: Slay the Robotnik example mod, 2026-10-06.
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
    rm -rf saves/mods/slay-the-robotnik
    echo "Cleared saves/mods/slay-the-robotnik"
fi

# The build rewrites target/examples-classpath.txt whenever it builds the engine, so anything in
# the engine's sources or POM newer than that file means the engine needs rebuilding.
marker=target/examples-classpath.txt
if [ "$rebuild" = 0 ] && [ -f "$marker" ] && [ -d target/classes ] \
        && [ -z "$(find src/main pom.xml -newer "$marker" -print 2>/dev/null | head -n 1)" ]; then
    exec python3 examples/slay-the-robotnik/build.py --run --skip-engine
fi
exec python3 examples/slay-the-robotnik/build.py --run
