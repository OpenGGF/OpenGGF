#!/usr/bin/env python3
"""Generate the Collision Chaos background-scroll profiles for the ten CCZ stage programs.

Purpose: the ten Collision Chaos scroll routines are one program shape (a 16.16 camera per
parallax layer, an auto-scrolling deformation array, a per-8-line word table built from fill,
deform-row and perspective blocks, and a water pass that writes the per-line hscroll) with
different numbers. This script reads each stage's own scroll routine from scddisasm and writes
the numbers and block order as a TSV with a source citation on every row, so CczScrollHandler
carries no hand-transcribed constants. The output holds code constants (rates, counts, shifts,
repeat tables, section bounds), never disc bytes; the wobble table is read from the stage file
at the offset ccz_cycle_catalogue.py finds.

Sources (docs/scddisasm pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, paths under src/):
  R31A  Level/Collision Chaos/Scroll (Act 1 Present).asm      (R31A Legacy -> R3 Legacy Act 1 Shared:98)
  R31B-D, R32A-D, R33C-D  r3/scroll_{1b,1c,1d,2a,2b,2c,2d,3c,3d}.asm
        (R3 Legacy Act N X Shared.asm:66-67 and R3 Shared.asm:72-76)
The script fails on any instruction sequence it does not recognise, so a changed pin is noticed.
The invariants every Java constant relies on (layer rates, background Y rule, initial layer
positions) are asserted here and cited in CczScrollHandler.

Inputs: a scddisasm checkout (default docs/scddisasm). Output: src/main/resources/soniccd/ccz-scroll-profiles.tsv
(or --check against it). Origin: Collision Chaos presentation lane of the 2026-11-12 Sonic CD re-host.

    python3 -I tools/soniccd/ccz_scroll_profiles.py --scddisasm docs/scddisasm [--check]
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "src/main/resources/soniccd/ccz-scroll-profiles.tsv"
PIN = "1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff"

LEVEL_SCROLL = "Level/Collision Chaos/Scroll (Act 1 Present).asm"
STAGES = {
    "R31A": LEVEL_SCROLL,
    "R31B": "r3/scroll_1b.asm", "R31C": "r3/scroll_1c.asm", "R31D": "r3/scroll_1d.asm",
    "R32A": "r3/scroll_2a.asm", "R32B": "r3/scroll_2b.asm", "R32C": "r3/scroll_2c.asm",
    "R32D": "r3/scroll_2d.asm",
    "R33C": "r3/scroll_3c.asm", "R33D": "r3/scroll_3d.asm",
}
# Both source dialects name the same RAM variables differently.
NAMES = {
    "scroll_fg_x": "FGX", "cameraX": "FGX", "scroll_bg_x": "BGX", "cameraBgX": "BGX",
    "scroll_bg2_x": "BG2X", "cameraBg2X": "BG2X", "scroll_bg3_x": "BG3X", "cameraBg3X": "BG3X",
    "scroll_fg_y": "FGY", "cameraY": "FGY", "scroll_bg_y": "BGY", "cameraBgY": "BGY",
    "bg_scroll_lines": "DEF", "deformBuffer": "DEF", "scroll_lines": "HS", "hscroll": "HS",
    "scroll_x_move": "XD", "scrollXDiff": "XD", "bg_water_deform": "WD", "bgWaterDeform": "WD",
}


def fail(message: str):
    raise SystemExit(f"error: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def number(text: str) -> int:
    text = re.sub(r"\.[wl]$", "", text.strip().lstrip("#"))
    return int(text[1:], 16) if text.startswith("$") else int(text)


class Source:
    """The instruction stream of one scroll file: comments stripped, RAM names canonicalised."""

    def __init__(self, root: Path, relative: str):
        self.relative = relative
        self.rows: list[tuple[int, str]] = []
        self.data: dict[str, list[int]] = {}
        text = (root / "src" / relative).read_text(encoding="utf-8")
        run = None   # label owning the dc.b / dc.w run being read
        for number_, raw in enumerate(text.split("\n"), 1):
            line = raw.split(";")[0].strip()
            if not line:
                continue
            match = re.match(r"^([A-Za-z_.][A-Za-z0-9_.]*):\s*(.*)$", line)
            if match:
                run, line = match.group(1), match.group(2)
                self.data.setdefault(run, [])
                self.rows.append((number_, "LABEL " + run))
                if not line:
                    continue
            for key, value in NAMES.items():
                line = re.sub(r"\b" + key + r"(\.w)?\b", value, line)
            line = re.sub(r"\s+", " ", line)
            self.rows.append((number_, line))
            data = re.match(r"^dc\.([bw]) (.*)$", line)
            if data and run is not None:
                self.data[run].append((1 if data.group(1) == "b" else 2,
                                       [number(v) for v in data.group(2).split(",")]))
            elif not data:
                run = None
        self.i = 0
        self.last = 0

    # -- cursor ----------------------------------------------------------------------------
    def labels_skipped(self):
        while self.i < len(self.rows) and self.rows[self.i][1].startswith("LABEL "):
            self.i += 1

    def peek(self, k: int = 0):
        j, n = self.i, 0
        while j < len(self.rows):
            if not self.rows[j][1].startswith("LABEL "):
                if n == k:
                    return self.rows[j][1]
                n += 1
            j += 1
        return None

    def match(self, pattern: str):
        self.labels_skipped()
        if self.i >= len(self.rows):
            return None
        return re.fullmatch(pattern, self.rows[self.i][1])

    def take(self, pattern: str | None = None):
        self.labels_skipped()
        number_, line = self.rows[self.i]
        match = None
        if pattern is not None:
            match = re.fullmatch(pattern, line)
            require(match is not None, f"{self.relative}:{number_}: expected {pattern!r}, found {line!r}")
        self.i += 1
        self.last = number_
        return number_, match if pattern is not None else line

    def line(self) -> int:
        self.labels_skipped()
        return self.rows[self.i][0]

    def table(self, label: str, size: int) -> list[int]:
        """Values of a dc.b / dc.w run at a label; a dc.b run read as words pairs its bytes."""
        require(self.data.get(label), f"{self.relative}: no data at {label}")
        flat = [v for _, values in self.data[label] for v in values]
        sizes = {s for s, _ in self.data[label]}
        require(len(sizes) == 1, f"{self.relative}: {label} mixes data sizes")
        if size == 2 and sizes == {1}:
            return [flat[k] * 256 + flat[k + 1] for k in range(0, len(flat) - 1, 2)]
        require(sizes == {size}, f"{self.relative}: {label} is not size {size}")
        return flat


def asl_amount(source: Source) -> int:
    """moveq #a,d1 ; asl.l d1,d2   or   asl.l #a,d2."""
    if source.match(r"moveq #(\S+),d1"):
        _, m = source.take(r"moveq #(\S+),d1")
        amount = number(m.group(1))
        source.take(r"asl\.l d1,d2")
        return amount
    _, m = source.take(r"asl\.l #(\S+),d2")
    return number(m.group(1))


def cite(source: Source, first: int) -> str:
    return f"{source.relative}:{first}-{source.last}"


def parse_blocks(source: Source) -> dict:
    """The deformation array, the curve and the ordered row-table blocks, up to `lea HS,a1`."""
    while not (source.i + 1 < len(source.rows) and source.rows[source.i][1] == "lea DEF,a2"
               and source.rows[source.i + 1][1].startswith("addi.l")):
        source.i += 1
        require(source.i < len(source.rows), f"{source.relative}: no deformation rates")
    first, _ = source.take()
    rates: list[int] = []
    while source.match(r"addi\.l #(\S+),\(a2\)\+"):
        _, m = source.take(r"addi\.l #(\S+),\(a2\)\+")
        rates.append(number(m.group(1)))
    result = {"rates": (rates, cite(source, first))}
    first = source.line()
    _, m = source.take(r"moveq #(\S+),d6")
    count = number(m.group(1)) + 1
    source.take(r"moveq #0,d1")
    source.take(r"move\.w d1,d2")
    _, m = source.take(r"mulu\.w #(\S+),d2")
    multiplier = number(m.group(1))
    _, m = source.take(r"addi\.l #(\S+),d2")
    half = number(m.group(1))
    source.take(r"add\.l d2,\(a2\)\+")
    source.take(r"addq\.b #1,d1")
    source.take(r"dbf d6,\S+")
    result["curve"] = ((count, multiplier, half), cite(source, first))
    blocks = []
    while True:
        source.labels_skipped()
        line = source.rows[source.i][1]
        first = source.line()
        if line == "lea HS,a1":
            break
        if (re.fullmatch(r"move\.w FGX,d0", line) and source.peek(1) == "neg.w d0"
                and source.peek(2) == "swap d0"):
            source.take(); source.take(); source.take()
            blocks.append(("fghi", (), cite(source, first)))
            continue
        fill = re.fullmatch(r"move\.w (BGX|BG2X|BG3X),d0", line)
        if fill and source.peek(1) == "neg.w d0" and source.peek(2) == "move.w d0,(a1)+":
            source.take(); source.take(); source.take()
            blocks.append(("fill", (fill.group(1), 1), cite(source, first)))
            continue
        if fill and source.peek(1) == "neg.w d0" and re.fullmatch(r"moveq #\S+,d6", source.peek(2) or ""):
            source.take(); source.take()
            _, m = source.take(r"moveq #(\S+),d6")
            n = number(m.group(1)) + 1
            source.take(r"move\.w d0,\(a1\)\+")
            source.take(r"dbf d6,\S+")
            blocks.append(("fill", (fill.group(1), n), cite(source, first)))
            continue
        if line == "lea DEF,a2":
            source.take()
            _, m = source.take(r"moveq #(\S+),d6")
            n = number(m.group(1)) + 1
            for pattern in (r"move\.l \(a2\)\+,d1", "swap d1", "add\\.w BGX,d1", "neg\\.w d1", "moveq #0,d5"):
                source.take(pattern)
            _, m = source.take(r"lea (\S+),a3")
            label = m.group(1)
            for pattern in (r"move\.b \(a3,d6\.w\),d5", r"move\.w d1,\(a1\)\+", r"dbf d5,\S+", r"dbf d6,\S+"):
                source.take(pattern)
            repeats = source.table(label, 1)
            require(len(repeats) >= n, f"{source.relative}: repeat table {label} shorter than {n}")
            blocks.append(("deform", (n, repeats[:n]), cite(source, first)))
            continue
        if line == "move.w BGX,d0":
            source.take()
            source.take(r"move\.w FGX,d2")
            source.take(r"sub\.w d0,d2")
            source.take(r"ext\.l d2")
            shift1 = asl_amount(source)
            _, m = source.take(r"divs\.w #(\S+),d2")
            divisor = number(m.group(1))
            source.take(r"ext\.l d2")
            shift2 = asl_amount(source)
            n = None
            back = False
            got_d3 = False
            for _ in range(3):
                if source.match(r"moveq #(\S+),d6"):
                    _, m = source.take(r"moveq #(\S+),d6")
                    n = number(m.group(1)) + 1
                elif source.match(r"move\.w BGX,d3"):
                    source.take()
                    got_d3 = True
                elif source.match(r"lea DEF\+(\S+),a1"):
                    source.take()
                    back = True
            require(n is not None and got_d3, f"{source.relative}:{source.line()}: unrecognised perspective loop")
            source.take(r"move\.w d3,d0")
            source.take(r"neg\.w d0")
            repeats = None
            if source.match(r"lea (\S+),a3"):
                _, m = source.take(r"lea (\S+),a3")
                label = m.group(1)
                source.take(r"moveq #0,d5")
                source.take(r"move\.b \(a3,d6\.w\),d5")
                repeats = source.table(label, 1)[:n]
            source.take(r"move\.w d0,-\(a1\)" if back else r"move\.w d0,\(a1\)\+")
            if repeats is not None:
                source.take(r"dbf d5,\S+")
            for pattern in ("swap d3", r"add\.l d2,d3", "swap d3", r"dbf d6,\S+"):
                source.take(pattern)
            if back:
                source.take(r"lea DEF\+(\S+),a1")
            blocks.append(("persp", (divisor, shift1, shift2, n, repeats, back), cite(source, first)))
            continue
        fail(f"{source.relative}:{source.line()}: unrecognised row-table instruction {line!r}")
    result["blocks"] = blocks
    return result


def parse_water(source: Source) -> dict:
    """The hscroll pass: section bounds, row limits, interpolation window and wobble form."""
    first = source.line()
    source.take(r"lea HS,a1")
    source.take(r"lea DEF\+(\S+),a2")
    for pattern in (r"move\.w BGY,d0", r"move\.w d0,d2", r"move\.w d0,d4", r"andi\.w #\$3F8,d0",
                    r"lsr\.w #2,d0", r"move\.w d0,d3", r"lsr\.w #1,d3"):
        source.take(pattern)
    _, m = source.take(r"moveq #(\S+),d1")
    rows = number(m.group(1))
    _, m = source.take(r"moveq #(\S+),d5")
    total = number(m.group(1))
    source.take(r"sub\.w d3,d1")
    source.take(r"bcs\.s \S+")
    _, m = source.take(r"cmpi\.w #(\S+),d1")
    compare = number(m.group(1))
    source.take(r"bcs\.s \S+")
    _, m = source.take(r"moveq #(\S+),d1")
    clamp = number(m.group(1))
    source.take(r"sub\.w d1,d5")
    source.take(r"lea \(a2,d0\.w\),a2")
    _, m = source.take(r"lea (\S+),a3")
    bounds = source.table(m.group(1), 2)
    require(len(bounds) >= 3, f"{source.relative}: section bounds table too short")
    source.take(r"lea WobbleTable,a4")
    source.take(r"addi\.w #\$40,WD")
    source.take(r"bsr\.w \S+")
    source.take(r"bra\.w \S+")
    result = {"rows": rows, "total": total, "compare": compare, "clamp": clamp, "bounds": bounds[:3]}
    while not re.fullmatch(r"move\.w d5,d1", source.peek() or ""):
        source.take()
    source.take(r"move\.w d5,d1")
    source.take(r"lsl\.w #3,d1")
    source.take(r"subq\.w #1,d1")
    result["checksNegative"] = bool(source.match(r"bmi\.s \S+"))
    if result["checksNegative"]:
        source.take()
    source.take(r"lea DEF\+(\S+),a2")
    source.take(r"move\.b WD,d5")
    source.take(r"sub\.w BGY,d4")
    for pattern in (r"move\.l \(a2\)\+,d2", "swap d2", r"add\.w BGX,d2", r"neg\.w d2", r"move\.w d2,d0"):
        source.take(pattern)
    _, m = source.take(r"move\.w #(\S+),d3")
    result["waterLine"] = number(m.group(1))
    source.take(r"sub\.w FGY,d3")
    source.take(r"cmp\.w d3,d4")
    source.take(r"bcs\.s \S+")
    if source.match(r"andi\.w #\$FF,d5"):
        source.take()
        source.take(r"move\.b \(a4,d5\.w\),d3")
        source.take("ext\\.w d3")
        source.take(r"add\.w d3,d3")
        result["wobble"] = "doubled"
    else:
        source.take(r"andi\.w #\$7F,d5")
        source.take(r"move\.w d5,d6")
        source.take(r"add\.w d6,d6")
        source.take(r"move\.b \(a4,d6\.w\),d3")
        source.take("ext\\.w d3")
        result["wobble"] = "pairs"
    source.take(r"add\.w FGX,d3")
    source.take(r"neg\.w d3")
    # The three interpolation windows share one constant (checked below).
    windows = set()
    j = source.i
    while j < len(source.rows) and len(windows) < 2 and j < source.i + 400:
        m = re.fullmatch(r"addi\.w #(\S+),d3", source.rows[j][1])
        if m:
            windows.add(number(m.group(1)))
        j += 1
    require(len(windows) == 1, f"{source.relative}: interpolation windows differ: {windows}")
    result["window"] = windows.pop()
    result["cite"] = f"{source.relative}:{first}-{source.last}"
    return result


def check_invariants(source: Source) -> None:
    """Constants the Java handler hardcodes for every stage; each must read the same here."""
    text = "\n".join(line for _, line in source.rows)
    required = [
        # BG3 / BG2 / BG layer rates (UpdateScroll head) and the background Y rule.
        "move.w XD,d4\next.l d4\nasl.l #4,d4\nmoveq #6,d6\nbsr.w ScrollBg3X"
        if "ScrollBg3X" in text else
        "move.w XD,d4\next.l d4\nasl.l #4,d4\nmoveq #6,d6\nbsr.w SetHorizScrollFlagsBG3",
        "asl.l #3,d4\nmove.l d4,d3\nadd.l d3,d3\nadd.l d3,d4\nmoveq #4,d6",
        "asl.l #2,d4\nmove.l d4,d3\nadd.l d3,d3\nadd.l d3,d4\nmoveq #2,d6",
        "move.w #$218,d0\nmove.w #$520,d1\nsub.w FGY,d1\nbcs.s",
        "lsr.w #1,d1\nsub.w d1,d0\nbpl.s",
    ]
    for chunk in required:
        require(chunk in text, f"{source.relative}: invariant not found:\n{chunk}")


_WATER_BODY: list[str] | None = None


def check_water_body(source: Source) -> None:
    """The 57-instruction section writer (CollisionChaos_..._ScrollWriteWaterRows) is the same code in
    every stage except for its interpolation window; CczScrollHandler.writeWaterSections ports it once."""
    global _WATER_BODY
    ins = [line for _, line in source.rows if not line.startswith("LABEL")]
    start = ins.index("cmp.w (a3),d4")
    end = ins.index("lsr.w #3,d6", start) + 5
    body = [re.sub(r"\$18|\$20", "WINDOW", line) for line in ins[start:end]]
    body = [re.sub(r"^(b\w+(?:\.\w)?|dbf \w+,|jmp) \S+$", r"\1 TARGET", line) for line in body]
    body = [re.sub(r"^(dbf d\d),\S+$", r"\1,TARGET", line) for line in body]
    if _WATER_BODY is None:
        _WATER_BODY = body
    require(body == _WATER_BODY, f"{source.relative}: section writer differs from the others")


def render(root: Path) -> str:
    out = [f"# Generated by tools/soniccd/ccz_scroll_profiles.py from scddisasm {PIN}; code constants only.",
           "stage\tkind\ta\tb\tc\td\te\tf\tcite"]
    for stage, relative in STAGES.items():
        source = Source(root, relative)
        check_invariants(source)
        check_water_body(source)
        # InitLevelScroll has to match the fixed rule too (see CczScrollHandler.initLevelScroll).
        init = "\n".join(line for _, line in source.rows)
        for chunk in ("lsr.w #4,d1\nmove.w d1,BG3X\nlsr.w #1,d1\nmove.w d1,d2\nadd.w d2,d2\nadd.w d1,d2\nmove.w d2,BG2X\n"
                      "lsr.w #1,d1\nmove.w d1,d2\nadd.w d2,d2\nadd.w d1,d2\nmove.w d2,BGX",):
            require(chunk in init, f"{relative}: InitLevelScroll layer rule differs")
        blocks = parse_blocks(source)
        water = parse_water(source)
        rates, rates_cite = blocks["rates"]
        out.append("\t".join([stage, "rates", ",".join(f"{r:X}" for r in rates), "", "", "", "", "", rates_cite]))
        (count, multiplier, half), curve_cite = blocks["curve"]
        out.append("\t".join([stage, "curve", str(count), f"{multiplier:X}", f"{half:X}", "", "", "", curve_cite]))
        for kind, args, block_cite in blocks["blocks"]:
            if kind == "fghi":
                out.append("\t".join([stage, "fghi", "", "", "", "", "", "", block_cite]))
            elif kind == "fill":
                out.append("\t".join([stage, "fill", args[0], str(args[1]), "", "", "", "", block_cite]))
            elif kind == "deform":
                out.append("\t".join([stage, "deform", str(args[0]), ",".join(str(r) for r in args[1]),
                                      "", "", "", "", block_cite]))
            else:
                divisor, shift1, shift2, n, repeats, back = args
                out.append("\t".join([stage, "persp", f"{divisor:X}", str(shift1), str(shift2), str(n),
                                      ",".join(str(r) for r in repeats) if repeats else "-",
                                      "back" if back else "forward", block_cite]))
        bounds = ",".join(f"{b:X}" for b in water["bounds"])
        out.append("\t".join([stage, "water", f"{water['rows']:X}", f"{water['total']:X}",
                              f"{water['compare']:X}", f"{water['clamp']:X}", bounds,
                              f"{water['window']:X}:{water['waterLine']:X}:{water['wobble']}:"
                              f"{'bmi' if water['checksNegative'] else 'nobmi'}", water["cite"]]))
    return "\n".join(out) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--scddisasm", type=Path, default=ROOT / "docs/scddisasm")
    parser.add_argument("--check", action="store_true", help="fail if the committed resource differs")
    args = parser.parse_args()
    text = render(args.scddisasm.expanduser().resolve())
    if args.check:
        require(OUTPUT.read_text() == text, f"{OUTPUT} is stale; regenerate it")
        print(f"OK: {OUTPUT.relative_to(ROOT)} matches the disassembly")
        return
    OUTPUT.write_text(text)
    print(f"Wrote {OUTPUT.relative_to(ROOT)} ({len(text.splitlines()) - 2} rows)")


if __name__ == "__main__":
    main()
