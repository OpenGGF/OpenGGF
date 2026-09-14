#!/usr/bin/env python3
"""Package an InputLogAuthorTool input log as a BizHawk 2.11 BK2 the headless harness accepts.

Purpose: `com.openggf.tools.InputLogAuthorTool` writes OpenGGF's user-recording
log key (`#P1 Up|P1 Down|P1 Left|P1 Right|P1 Start|P1 A|P1 B|P1 C|...`) and, for
`.bk2` output, no `SyncSettings.json`. TraceChaser's headless harness
(`tools/tracechaser/bizhawk-headless`) requires BizHawk's own key with the
`#Power|Reset|` group first and A/B/C before Start, a validated
`SyncSettings.json`, and a power-on `Header.txt`. This script re-keys every
frame and packages it with the `SyncSettings.json` copied from an existing
fixture movie plus a header naming the game and BizHawk's header hash for the
ROM, so an authored route records exactly like a hand-recorded movie.

Inputs: <input log txt> <template bk2> <output bk2> <GameName> <header SHA1>.
The template only supplies SyncSettings.json; use any committed fixture movie
for the same core. GameName and the header hash are the values EmuHawk writes
for the ROM (see the vendored gamedb); they are provenance, not inputs the
harness validates.

Example:
  python3 tools/traces/assemble_bk2_from_input_log.py target/capture/kis2-ehz1.txt \
      src/test/resources/traces/s2/ehz1_fullrun/s2-ehz1.bk2 target/capture/kis2-ehz1.bk2 \
      "Sonic and Knuckles & Sonic 2 (W) [!]" 3E5E4B18D035775B916A06F2B3DC5031

Originating task: the first Knuckles in Sonic 2 trace fixture (2026-09-14).
"""
import sys
import zipfile


def main(argv):
    if len(argv) != 6:
        print(__doc__)
        return 2
    src_log, template_bk2, out_bk2, game_name, header_sha1 = argv[1:6]
    with zipfile.ZipFile(template_bk2) as z:
        sync = z.read("SyncSettings.json")
    frames = []
    in_input = False
    for line in open(src_log, encoding="utf-8").read().splitlines():
        if line == "[Input]":
            in_input = True
            continue
        if line == "[/Input]":
            in_input = False
            continue
        if not in_input or line.startswith("LogKey:"):
            continue
        parts = line.strip("|").split("|")
        if len(parts) != 2 or any(len(p) != 8 for p in parts):
            raise SystemExit(f"unexpected input log line: {line!r}")
        frames.append("|..|" + rekey(parts[0]) + "|" + rekey(parts[1]) + "|")
    log = ("[Input]\n"
           "LogKey:#Power|Reset|#P1 Up|P1 Down|P1 Left|P1 Right|P1 A|P1 B|P1 C|P1 Start|"
           "#P2 Up|P2 Down|P2 Left|P2 Right|P2 A|P2 B|P2 C|P2 Start|\n"
           + "\n".join(frames) + "\n[/Input]\n")
    header = ("MovieVersion BizHawk v2.0.0\n"
              "Author OpenGGF InputLogAuthorTool\n"
              "Core Genplus-gx\n"
              "Platform GEN\n"
              "emuVersion Version 2.11\n"
              "OriginalEmuVersion Version 2.11\n"
              f"GameName {game_name}\n"
              f"SHA1 {header_sha1}\n")
    with zipfile.ZipFile(out_bk2, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("Header.txt", header)
        z.writestr("Comments.txt", "")
        z.writestr("Subtitles.txt", "")
        z.writestr("SyncSettings.json", sync)
        z.writestr("Input Log.txt", log)
    print(f"wrote {out_bk2}: {len(frames)} frames")
    return 0


def rekey(p):
    """UDLRSABC (OpenGGF user recording) -> UDLRABCS (BizHawk Genesis pad)."""
    u, d, l, r, s, a, b, c = p
    return u + d + l + r + a + b + c + s


if __name__ == "__main__":
    sys.exit(main(sys.argv))
