#!/usr/bin/env python3
"""Splice an engine route's controller rows onto a recorded movie's prefix.

Purpose: a cold OpenGGF route (`src/test/resources/routes/...`) boots straight
into a level, so native BizHawk cannot play it alone. The committed complete-run
movie already reaches that level's first frame. This script keeps that movie's
rows `[0, prefix)` and appends the route's rows, so route row k becomes movie
row `prefix + k`. Capturing the result with TraceChaser's headless harness
(`--trace-profile complete_run`) gives native rows to compare with the engine
playing the same route rows from its cold entry. Comparison only: nothing here
feeds the engine.

Inputs:
  --movie        the recorded BizHawk movie that owns the prefix (its key must
                 be `#Power|Reset|#P1 ...|#P2 ...|`, Genesis pads UDLRABCS)
  --prefix-rows  rows of the movie to keep; the first route row lands here
  --route        route `.bk2` or Input Log in OpenGGF's key (UDLRSABC per pad), or a
                 movie `RouteFiles.exportBk2` derived from a route manifest (BizHawk's
                 Genesis key, kept as is)
  --route-rows   how many route rows to append (default: all)
  --filler-before  comma-separated route row indices; before each listed row k one
                 filler row (a copy of route row k-1) is inserted, once per listing
  --out          output `.bk2`; the name becomes the capture's `source_bk2`

Both pads are re-keyed from the route; no route row is repeated or invented. A
native lag frame consumes a movie row without polling the pad, so without
fillers the two diverge from the first native lag frame on. Each filler stands
in for one observed lag VBlank (a route manifest's native lag fact): the game
never reads it, so the polled presses stay the engine's. Fillers are observed,
never predicted. Bracket the prefix by +/-1 before believing an early
divergence: a one-row offset fakes one hundreds of frames in. Output is
byte-deterministic (fixed zip timestamps).

Example (SSZ1: the S&T complete-run arms SSZ at 396720, route row 0 is the
engine's pre-arm step, so route input k lands on movie row 396719 + k; the
committed native composite used the route movie as it was at fa313dfa3, before
the route became a manifest whose movie is derived with RouteFiles.exportBk2):
  python3 -I tools/traces/compose_route_on_movie_prefix.py \
      --movie src/test/resources/traces/s3k/_movies/s3k-complete-sonic-tails.bk2 \
      --prefix-rows 396719 --route-rows 2000 \
      --route <ssz1-sonic-tails-cold-complete-320.bk2 at fa313dfa3> \
      --out /tmp/ssz1-native-check/s3k-ssz1-cold-route-composite.bk2

Origin: SSZ1 native route check after the ROM-owned solid-contact change
(feature/ai-weakness-pass fa313dfa3, 2026-11-01); promoted on
bugfix/ai-ssz1-native-routes.
"""
import argparse
import sys
import zipfile

BIZHAWK_KEY = ("LogKey:#Power|Reset|#P1 Up|P1 Down|P1 Left|P1 Right|P1 A|P1 B|P1 C|P1 Start|"
               "#P2 Up|P2 Down|P2 Left|P2 Right|P2 A|P2 B|P2 C|P2 Start|")
FIXED_TIME = (1980, 1, 1, 0, 0, 0)


def input_rows(text):
    return [line for line in text.splitlines() if line.startswith("|")]


def rekey(pad):
    """UDLRSABC (OpenGGF route key) -> UDLRABCS (BizHawk Genesis pad)."""
    if len(pad) != 8:
        raise SystemExit(f"unexpected pad field {pad!r}")
    u, d, l, r, s, a, b, c = pad
    return u + d + l + r + a + b + c + s


def read_route(path):
    if zipfile.is_zipfile(path):
        with zipfile.ZipFile(path) as z:
            text = z.read("Input Log.txt").decode("utf-8")
    else:
        text = open(path, encoding="utf-8").read()
    if BIZHAWK_KEY in text.splitlines():
        # A movie derived from a route manifest already carries BizHawk's two-pad key.
        return input_rows(text)
    rows = []
    for line in input_rows(text):
        parts = line.strip("|").split("|")
        if len(parts) != 2:
            raise SystemExit(f"route row is not two OpenGGF pads: {line!r}")
        rows.append("|..|" + rekey(parts[0]) + "|" + rekey(parts[1]) + "|")
    return rows


def entry(name):
    info = zipfile.ZipInfo(name, FIXED_TIME)
    info.external_attr = 0o644 << 16
    return info


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--movie", required=True)
    parser.add_argument("--prefix-rows", type=int, required=True)
    parser.add_argument("--route", required=True)
    parser.add_argument("--route-rows", type=int)
    parser.add_argument("--filler-before", default="")
    parser.add_argument("--out", required=True)
    args = parser.parse_args(argv)

    with zipfile.ZipFile(args.movie) as movie:
        members = {name: movie.read(name) for name in movie.namelist()}
    log = members.pop("Input Log.txt").decode("utf-8").splitlines()
    if BIZHAWK_KEY not in log:
        raise SystemExit("movie key is not the BizHawk Genesis two-pad key")
    movie_rows = input_rows("\n".join(log))
    if not 0 < args.prefix_rows <= len(movie_rows):
        raise SystemExit(f"prefix {args.prefix_rows} outside the movie's {len(movie_rows)} rows")
    route = read_route(args.route)
    count = len(route) if args.route_rows is None else args.route_rows
    if not 0 < count <= len(route):
        raise SystemExit(f"route has {len(route)} rows, asked for {count}")

    fillers = sorted(int(k) for k in args.filler_before.split(",") if k.strip())
    if any(k < 1 or k >= count for k in fillers):
        raise SystemExit(f"a filler goes before a route row in [1, {count}): {fillers}")
    before = {}
    for k in fillers:
        before[k] = before.get(k, 0) + 1
    body = list(movie_rows[:args.prefix_rows])
    for k in range(count):
        body.extend([route[k - 1]] * before.get(k, 0))
        body.append(route[k])
    text = "\n".join(["[Input]", BIZHAWK_KEY] + body + ["[/Input]"]) + "\n"
    with zipfile.ZipFile(args.out, "w", zipfile.ZIP_DEFLATED) as out:
        for name in sorted(members):
            out.writestr(entry(name), members[name], zipfile.ZIP_DEFLATED)
        out.writestr(entry("Input Log.txt"), text, zipfile.ZIP_DEFLATED)
    print(f"wrote {args.out}: {len(body)} rows ({args.prefix_rows} movie + {count} route + {len(fillers)} filler)")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
