#!/usr/bin/env python3
"""Disassemble the Sonic CD backup-RAM manager (BRAMMAIN.MMD) by recursive descent, with its jump tables.

Purpose: the pinned listing (scddisasm 1ecc5f3af7cc, Backup RAM/Main.asm) is a raw machine disassembly whose
L_FFxxxx labels cover about 2,340 instructions; much of the program is left as dc.l data (the V-blank hook at
$FF22F2, the object routines' bodies and their dispatch tables). Following every branch from the entry points
and resolving each `jmp/jsr $T(pc,dN.w)` word table recovers about 3,060 instructions. Lane TA used it to map
the manager (main states at $FF3FF4 by $CD15, objects by ID through the long table $FF2752, V-blank states at
$FF20E2 by $CD02) before deciding the port's scope; the next RAM DATA lane starts here.

Inputs: the user's USA disc (CUE + split data track) or an extracted BRAMMAIN.MMD; the Capstone module
(`python3 -m venv <dir> && <dir>/bin/pip install capstone`, then run with that interpreter and -I).
Output: a text listing at --out (outside the repository; it is regenerable). Origin: lane TA, 2026-11-12.

    <venv>/bin/python -I tools/soniccd/backupram_disassemble.py --media-root ~/Downloads/Sonic-CD/media --out /tmp/brammain.lst
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ORIGIN, HEADER, IMAGE = 0xFF2000, 0x100, 0x7A00          # MMD origin and copied size (BRAMMAIN.MMD header)
ENTRIES = (0xFF2000, 0xFF20A8, 0xFF22F2, 0xFF254C)        # Start, VInterrupt, the V-blank hook, the pad read
LONG_TABLES = ((0xFF2752, 7),)                            # object routines by ID 1-7 (RunObjects, $FF281A)
WORD_TABLES = ((0xFF20E2, 3), (0xFF3FF4, 10))             # V-blank states ($CD02); main states ($CD15)


def load(args) -> bytes:
    if args.file:
        return Path(args.file).read_bytes()
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    from retail_profiles import parse_cue  # noqa: E402
    from stage_inventory import RawIso  # noqa: E402
    cue = sorted((Path(args.media_root).expanduser() / "USA").glob("*.cue"))[0]
    with RawIso((cue.parent / parse_cue(cue.read_text())[0]["file"]).resolve()) as disc:
        return disc.file("BRAMMAIN.MMD;1")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--media-root")
    parser.add_argument("--file", help="an extracted BRAMMAIN.MMD instead of the disc")
    parser.add_argument("--out", required=True)
    args = parser.parse_args()
    import capstone
    data = load(args)
    code = data[HEADER:HEADER + IMAGE]

    def word(a: int, signed: bool = False) -> int:
        v = int.from_bytes(code[a - ORIGIN:a - ORIGIN + 2], "big")
        return v - 0x10000 if signed and v & 0x8000 else v

    md = capstone.Cs(capstone.CS_ARCH_M68K, capstone.CS_MODE_M68K_000)
    entries = set(ENTRIES)
    for base, count in LONG_TABLES:
        entries |= {int.from_bytes(code[base - ORIGIN + 4 * i:base - ORIGIN + 4 * i + 4], "big") for i in range(count)}
    for base, count in WORD_TABLES:
        entries |= {base + word(base + 2 * i, True) for i in range(count)}
    while True:
        seen, labels, work = {}, set(entries), list(entries)
        while work:
            a = work.pop()
            while a not in seen and ORIGIN <= a < ORIGIN + IMAGE:
                ins = next(md.disasm(code[a - ORIGIN:a - ORIGIN + 12], a, 1), None)
                if ins is None:
                    break
                seen[a] = ins
                m, op = ins.mnemonic, ins.op_str
                target = re.search(r"\$([0-9a-f]+)", op)
                if (m.startswith(("b", "db", "jsr", "jmp")) and m not in ("btst", "bset", "bclr", "bchg")
                        and target and "(" not in op):
                    t = int(target.group(1), 16)
                    if 0xF000 <= t < 0x10000:
                        t |= 0xFF0000
                    if ORIGIN <= t < ORIGIN + IMAGE:
                        labels.add(t)
                        work.append(t)
                if m in ("rts", "rte", "bra", "jmp") or m.startswith(("bra", "jmp")):
                    break
                a += ins.size
        # Word tables of `jmp/jsr $T(pc,dN.w)`: offsets from T, read until the first target.
        found = set()
        for ins in seen.values():
            hit = re.search(r"\$([0-9a-f]+)\(pc, d[0-7]\.w\)", ins.op_str)
            if ins.mnemonic in ("jmp", "jsr") and hit:
                base, targets = int(hit.group(1), 16), []
                for i in range(64):
                    if targets and base + 2 * i >= min(targets):
                        break
                    v = word(base + 2 * i, True)
                    if v <= 0 or v & 1 or base + v >= ORIGIN + IMAGE:
                        break
                    targets.append(base + v)
                found |= set(targets)
        if found <= entries:
            break
        entries |= found
    lines = [f"; BRAMMAIN.MMD at ${ORIGIN:X}, {len(seen)} instructions from {len(entries)} entry points"]
    for a in sorted(seen):
        ins = seen[a]
        lines.append(f"{'L_%06X:' % a if a in labels else '':10} {a:06X}  {ins.mnemonic:8} {ins.op_str}")
    Path(args.out).write_text("\n".join(lines) + "\n")
    print(f"Wrote {args.out}: {len(seen)} instructions")


if __name__ == "__main__":
    main()
