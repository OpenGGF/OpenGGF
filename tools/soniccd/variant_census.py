#!/usr/bin/env python3
"""Census of how Sonic CD's 70 stage programs differ from one another.

Purpose: each stage program (R11A ... R83D) is assembled from its own copy of
the level engine.  This tool resolves, per stage, the transitive include graph
of the scddisasm sources, honouring the conditional assembly symbols the build
defines (REGION, DEMO, STAGE_*, Rn_VARIANT, ...), then reports which files and
routines are identical across stages and which genuinely differ.

Inputs: a scddisasm checkout (read-only) and an output directory.
Origin: 2026-11 Sonic CD re-host variant-census lane.  Stage roots and build
symbols come from make.sh `assemble` lines; the stage list is cross-checked
against the FID_Rxxx table in src/_Include/System.inc.

Limits: this is a text census, not an assembler.  Conditional expressions are
evaluated with a small integer evaluator; macro bodies are not expanded and
conditionals inside them are ignored.  Comparison is textual after dropping
comments and whitespace, canonicalising ROM-address labels (loc_202BC6 ->
loc_#1, numbered by first appearance) and zone-name label prefixes.  Two
routines that differ only in a branch target between two address labels that
appear in the same order are therefore reported identical.  Standard library
only; run with `python3 -I`.
"""
from __future__ import annotations

import argparse
import difflib
import hashlib
import json
import re
import sys
from collections import defaultdict
from pathlib import Path, PurePosixPath

SOURCE_SUFFIXES = {".asm", ".inc"}
ZONE_NAMES = {
    "1": "Palmtree Panic", "3": "Collision Chaos", "4": "Tidal Tempest",
    "5": "Quartz Quadrant", "6": "Wacky Workbench", "7": "Stardust Speedway",
    "8": "Metallic Madness",
}
REGION_VALUES = {"japan": 0, "usa": 1, "europe": 2}
STAGE_CODE = re.compile(r"R([1345678])([123])([ABCD])")

ADDRESS_LABEL = re.compile(
    r"\b(loc|locret|unk|sub|byte|word|off|stru|dword|def|nullsub|asc|loc2|word2|byte2|dword2|off2)_([0-9A-Fa-f]{4,8})\b")
ZONE_PREFIX = re.compile(
    r"\b(?:R[1345678][123]?[ABCD]?(?:Act\d)?|PalmtreePanic|CollisionChaos|TidalTempest|QuartzQuadrant"
    r"|WackyWorkbench|StardustSpeedway|MetallicMadness)(?:Act\d)?(?:Past|Present|GoodFuture|BadFuture|Future)?_")
IDENT = re.compile(r"[A-Za-z_.@][\w.@]*")

# Engine areas, first match wins; patterns run on the lower-case path.
CATEGORIES = [
    ("mappings / art data", r"mappings|animations|dplcs|(^|/)(sprites|anims|maps|gfx)/|/data/"),
    ("player movement", r"(^|/)objects/(r\d/)?player\.asm|objects/sonic/"),
    ("player special collision", r"player special collision"),
    ("object collision", r"player object collision|player_object_collide|object_block_collide"),
    ("solid objects", r"object solid|solid_object"),
    ("player floor/block collision",
     r"collision floor|collision check|player_ground_collide|block_collide|player_block_check"),
    ("spawner / layout",
     r"object spawner|object_spawn|respawn_load|object layout|load saved data|object time check|time_check|s1_object_despawn"),
    ("object index (dispatch table)", r"object index|object_index"),
    ("level events", r"(^|/)_?events(\.asm| )|(^|/)events\.asm"),
    ("water", r"palette_load_water|palette load \(water|water_surface|/water"),
    ("palette", r"palette|pal_|palettes_"),
    ("scroll", r"scroll"),
    ("HUD / title card / results",
     r"hud|title.?card|results|game.?over"),
    ("level init / stage load",
     r"initialization|(^|/)r\d/main\.asm|(^|/)level/main\.asm|stage_data_load|load level data|stage_draw|block draw|section art|section_gfx|section data|(^|/)common/(main|initialization)\.asm"),
    ("animated tiles", r"animated tiles|animate tiles"),
    ("object animate / functions", r"object animate|object_animate|object functions|(^|/)object\.asm|functions \(|/events"),
    ("debug mode", r"debug"),
    ("stage data / graphics lists", r"(^|/)data|gfx_lists|graphics lists|object tile"),
]
# The two source dialects split the same engine code across different files, so classes are
# also computed over these unions.
AREAS = {
    "player movement": "AREA player (movement + special collision)",
    "player special collision": "AREA player (movement + special collision)",
    "player floor/block collision": "AREA collision (floor/block + object + solid)",
    "object collision": "AREA collision (floor/block + object + solid)",
    "solid objects": "AREA collision (floor/block + object + solid)",
    "spawner / layout": "AREA object core (draw + animate + spawn + time check)",
    "object animate / functions": "AREA object core (draw + animate + spawn + time check)",
}
CATEGORY_RES = [(n, re.compile(p)) for n, p in CATEGORIES]
LEGACY_RE = re.compile(r"legacy|padding|tail\b|\btail|leftover|retained|nonusa|non_usa|non-usa|unused", re.I)
COMPAT_RE = re.compile(r"compatibility", re.I)
OBJECT_PATH = re.compile(r"(?:^|/)_?objects/(?:r\d/)?([^/]+?)(?:/main[^/]*)?\.asm$", re.I)


# ---------------------------------------------------------------- expressions
class Undefined(Exception):
    pass


class ExprError(Exception):
    pass


TOKEN = re.compile(
    r"\s*(?:(\$[0-9A-Fa-f]+|%[01]+|\d+)|([A-Za-z_.@][\w.@]*)|(<>|<=|>=|<<|>>|==|!=|[-+*/&|^~!<>=(),]))")


def tokenize(expr: str) -> list[tuple[str, str]]:
    out, pos = [], 0
    expr = expr.strip()
    while pos < len(expr):
        m = TOKEN.match(expr, pos)
        if not m:
            raise ExprError(f"cannot tokenize {expr!r} at {pos}")
        pos = m.end()
        if m.group(1):
            out.append(("num", m.group(1)))
        elif m.group(2):
            out.append(("id", m.group(2)))
        else:
            out.append(("op", m.group(3)))
    return out


class Evaluator:
    PRECEDENCE = [["|"], ["^"], ["&"], ["=", "==", "<>", "!=", "<", ">", "<=", ">="], ["<<", ">>"], ["+", "-"], ["*", "/"]]

    def __init__(self, symbols: dict[str, int]):
        self.symbols = symbols

    def eval(self, expr: str) -> int:
        self.toks = tokenize(expr)
        self.pos = 0
        value = self.binary(0)
        if self.pos != len(self.toks):
            raise ExprError(f"trailing tokens in {expr!r}")
        return value

    def peek(self):
        return self.toks[self.pos] if self.pos < len(self.toks) else (None, None)

    def binary(self, level: int) -> int:
        if level == len(self.PRECEDENCE):
            return self.unary()
        left = self.binary(level + 1)
        while True:
            kind, tok = self.peek()
            if kind == "op" and tok in self.PRECEDENCE[level]:
                self.pos += 1
                right = self.binary(level + 1)
                left = self.apply(tok, left, right)
            else:
                return left

    @staticmethod
    def apply(op: str, a: int, b: int) -> int:
        if op == "|": return a | b
        if op == "^": return a ^ b
        if op == "&": return a & b
        if op in ("=", "=="): return int(a == b)
        if op in ("<>", "!="): return int(a != b)
        if op == "<": return int(a < b)
        if op == ">": return int(a > b)
        if op == "<=": return int(a <= b)
        if op == ">=": return int(a >= b)
        if op == "<<": return a << b
        if op == ">>": return a >> b
        if op == "+": return a + b
        if op == "-": return a - b
        if op == "*": return a * b
        if op == "/":
            if b == 0:
                raise ExprError("division by zero")
            return int(a / b)
        raise ExprError(op)

    def unary(self) -> int:
        kind, tok = self.peek()
        if kind == "op" and tok in ("-", "~", "!", "+"):
            self.pos += 1
            v = self.unary()
            return {"-": -v, "~": ~v, "!": int(not v), "+": v}[tok]
        return self.primary()

    def primary(self) -> int:
        kind, tok = self.peek()
        if kind is None:
            raise ExprError("unexpected end")
        self.pos += 1
        if kind == "num":
            if tok.startswith("$"): return int(tok[1:], 16)
            if tok.startswith("%"): return int(tok[1:], 2)
            return int(tok)
        if kind == "id":
            if tok.lower() == "def" and self.peek() == ("op", "("):
                self.pos += 1
                _, name = self.peek()
                self.pos += 1
                if self.peek() != ("op", ")"):
                    raise ExprError("bad def()")
                self.pos += 1
                return int(name in self.symbols)
            if self.peek() == ("op", "("):
                raise ExprError(f"unsupported function {tok}")
            if tok in self.symbols:
                return self.symbols[tok]
            raise Undefined(tok)
        if tok == "(":
            v = self.binary(0)
            if self.peek() != ("op", ")"):
                raise ExprError("missing )")
            self.pos += 1
            return v
        raise ExprError(f"unexpected {tok!r}")


# ---------------------------------------------------------------- preprocessing
def strip_comment(line: str) -> str:
    """Drop an assembler comment: `;` outside quotes, or `*` in column 0."""
    if line[:1] == "*":
        return ""
    quote = None
    for i, ch in enumerate(line):
        if quote:
            if ch == quote:
                quote = None
        elif ch in "\"'":
            quote = ch
        elif ch == ";":
            return line[:i]
    return line


LINE = re.compile(r"^(?P<label>[^\s;]+)?[ \t]*(?P<rest>.*)$")
DIRECTIVES = {"if", "ifne", "ifeq", "ifdef", "ifndef", "else", "elseif", "endif", "endc", "include",
              "incbin", "macro", "endm", "rept", "endr"}
QUOTED = re.compile(r"""["']([^"']+)["']""")


class Warnings:
    def __init__(self):
        self.items: list[str] = []

    def add(self, msg: str):
        if msg not in self.items:
            self.items.append(msg)


class Tree:
    """Source tree with case-insensitive include resolution and a file cache."""

    def __init__(self, root: Path):
        self.root = root
        self.src = root / "src"
        self.index: dict[str, Path] = {}
        for p in self.src.rglob("*"):
            if p.is_file():
                self.index.setdefault(p.relative_to(self.src).as_posix().lower(), p)
        self.text: dict[Path, list[str]] = {}

    def lines(self, path: Path) -> list[str]:
        if path not in self.text:
            self.text[path] = path.read_text(errors="replace").splitlines()
        return self.text[path]

    def rel(self, path: Path) -> str:
        return path.relative_to(self.src).as_posix()

    def resolve(self, including: Path, requested: str) -> Path | None:
        req = requested.replace("\\", "/")
        parent = PurePosixPath(self.rel(including)).parent
        for base in (PurePosixPath(), parent):
            parts: list[str] = []
            for part in (base / PurePosixPath(req)).parts:
                if part in ("", "."):
                    continue
                if part == "..":
                    if parts:
                        parts.pop()
                    continue
                parts.append(part)
            cand = PurePosixPath(*parts).as_posix()
            if (self.src / cand).is_file():
                return self.src / cand
            hit = self.index.get(cand.lower())
            if hit is not None:
                return hit
        return None


class Instance:
    """One file as assembled in one stage: active lines plus its children."""

    def __init__(self, path: Path, rel: str):
        self.path = path
        self.rel = rel
        self.active: list[tuple[int, str]] = []   # (lineno, comment-stripped text), non-directive
        self.includes: list[str] = []
        self.incbins: list[str] = []
        self.inactive_lines = 0
        self.total_lines = 0


ASSIGN = re.compile(r"^\s*([\w\\$#]+):?\s*(?:equ|=)\s*(.+)$", re.I)
SUBST = re.compile(r"\\([$#])(\w+)")


def replay_assignments(raw_lines: list[str], rept_line: int, count_expr: str, ev: Evaluator,
                       symbols: dict[str, int]) -> int:
    """Run a `rept` block made only of symbol assignments (the oVarNN table); returns last line used.

    Returns 0 (nothing skipped) for any other rept, which is then read once like ordinary text.
    """
    body = []
    for j in range(rept_line, len(raw_lines)):
        text = strip_comment(raw_lines[j]).strip()
        if not text:
            continue
        if text.lower().split()[0] == "endr":
            end = j + 1
            break
        body.append(text)
    else:
        return 0
    if not body or not all(ASSIGN.match(t) for t in body):
        return 0
    try:
        count = ev.eval(count_expr)
        for _ in range(count):
            for t in body:
                def sub(m: re.Match) -> str:
                    v = symbols[m.group(2)]
                    return format(v, "X") if m.group(1) == "$" else str(v)
                m = ASSIGN.match(SUBST.sub(sub, t))
                symbols[m.group(1)] = ev.eval(m.group(2))
    except (Undefined, ExprError, KeyError):
        return 0
    return end


STRUCTURAL = {"if", "ifne", "ifeq", "ifdef", "ifndef", "else", "elseif", "endif", "endc", "include", "incbin",
              "rept", "macro", "endm"}


def expand_rept(raw_lines: list[str], rept_line: int, count_expr: str, ev: Evaluator):
    """Unroll a flat `rept N` block so it compares equal to the same code written out N times.

    Blocks containing conditionals, includes, macros or nested rept are left alone (returns None).
    """
    body = []
    for j in range(rept_line, len(raw_lines)):
        text = strip_comment(raw_lines[j]).rstrip()
        if not text.strip():
            continue
        words = text.split()
        first = words[0].lower()
        if first == "endr":
            try:
                count = ev.eval(count_expr)
            except (Undefined, ExprError):
                return None
            if not 0 < count <= 4096:
                return None
            return [x for _ in range(count) for x in body], j + 1
        if first in STRUCTURAL or (len(words) > 1 and words[1].lower() in STRUCTURAL):
            return None
        body.append((j + 1, text))
    return None


def assemble_stage(tree: Tree, root_rel: str, defines: dict[str, int], warn: Warnings):
    """Walk includes depth-first in assembly order; returns (instances, unresolved)."""
    symbols = dict(defines)
    rs_counter = [0]
    instances: dict[str, Instance] = {}
    order: list[str] = []
    unresolved: list[str] = []
    ev = Evaluator(symbols)

    def condition(op: str, args: str, where: str) -> bool:
        args = args.strip()
        try:
            if op == "if":
                return bool(ev.eval(args))
            if op == "ifne":
                return bool(ev.eval(args))
            if op == "ifeq":
                return not ev.eval(args)
            if op == "ifdef":
                return args in symbols
            if op == "ifndef":
                return args not in symbols
            return bool(ev.eval(args))
        except Undefined as exc:
            warn.add(f"{where}: undefined symbol {exc} in `{op} {args}`; treated as false")
        except ExprError as exc:
            warn.add(f"{where}: cannot evaluate `{op} {args}` ({exc}); treated as false")
        return False

    def walk(path: Path, stack: tuple[str, ...]):
        rel = tree.rel(path)
        if rel in stack:
            warn.add(f"include cycle at {rel}")
            return
        inst = instances.get(rel)
        first_time = inst is None
        if first_time:
            inst = Instance(path, rel)
            instances[rel] = inst
            order.append(rel)
        cond: list[list[bool]] = []   # [parent_active, taken, active]
        in_macro = False

        def active() -> bool:
            return all(c[2] for c in cond)

        raw_lines = tree.lines(path)
        if first_time:
            inst.total_lines = len(raw_lines)
        skip_until = 0
        for lineno, raw in enumerate(raw_lines, 1):
            if lineno <= skip_until:
                continue
            text = strip_comment(raw).rstrip()
            if not text.strip():
                continue
            m = LINE.match(text)
            label = m.group("label")
            rest = m.group("rest").strip()
            if label and label.rstrip(":").lower() in DIRECTIVES and not rest.startswith(("equ", "=")):
                op, args = label.rstrip(":").lower(), rest
                label = None
            else:
                parts = rest.split(None, 1)
                op = parts[0].lower() if parts else ""
                args = parts[1] if len(parts) > 1 else ""
            if in_macro:
                if op == "endm":
                    in_macro = False
                continue
            if op == "macro":
                in_macro = True
                continue
            if op in ("if", "ifne", "ifeq", "ifdef", "ifndef"):
                parent = active()
                val = condition(op, args, f"{rel}:{lineno}") if parent else False
                cond.append([parent, val, parent and val])
                continue
            if op == "elseif":
                if not cond:
                    continue
                c = cond[-1]
                if c[1]:
                    c[2] = False
                else:
                    val = condition("if", args, f"{rel}:{lineno}") if c[0] else False
                    c[1], c[2] = val, c[0] and val
                continue
            if op == "else":
                if cond:
                    c = cond[-1]
                    c[2] = c[0] and not c[1]
                    c[1] = True
                continue
            if op in ("endif", "endc"):
                if cond:
                    cond.pop()
                continue
            if not active():
                if first_time:
                    inst.inactive_lines += 1
                continue
            if op == "rsreset":
                rs_counter[0] = 0
                continue
            if op == "rsset":
                try:
                    rs_counter[0] = ev.eval(args)
                except (Undefined, ExprError):
                    pass
                continue
            if op in ("rs.b", "rs.w", "rs.l"):
                size = {"rs.b": 1, "rs.w": 2, "rs.l": 4}[op]
                if size > 1 and rs_counter[0] % 2:
                    rs_counter[0] += 1
                if label:
                    symbols[label.rstrip(":")] = rs_counter[0]
                try:
                    rs_counter[0] += size * ev.eval(args)
                except (Undefined, ExprError):
                    pass
                continue
            if op in ("equ", "set", "=") or rest.startswith("="):
                if label:
                    expr = args if op in ("equ", "set", "=") else rest[1:]
                    try:
                        symbols[label.rstrip(":")] = ev.eval(expr)
                    except (Undefined, ExprError):
                        pass
                continue
            if op == "include":
                q = QUOTED.search(args)
                if q:
                    child = tree.resolve(path, q.group(1))
                    inst.includes.append(PurePosixPath(q.group(1).replace("\\", "/")).as_posix())
                    if child is None:
                        unresolved.append(f"{rel}:{lineno}: {q.group(1)}")
                    else:
                        walk(child, stack + (rel,))
                continue
            if op == "incbin":
                q = QUOTED.search(args)
                if q:
                    inst.incbins.append(q.group(1).replace("\\", "/"))
                continue
            if op == "rept":
                skip_until = replay_assignments(raw_lines, lineno, args, ev, symbols)
                if not skip_until:
                    expanded = expand_rept(raw_lines, lineno, args, ev)
                    if expanded:
                        inst.active.extend(expanded[0])
                        skip_until = expanded[1]
                continue
            if op == "endr":
                continue
            inst.active.append((lineno, text))

    root = tree.src / PurePosixPath(root_rel.replace("\\", "/"))
    walk(root, ())
    return instances, order, unresolved, symbols


# ---------------------------------------------------------------- normalisation
def canon_line(text: str) -> str:
    text = re.sub(r"\s+", " ", text.strip())
    text = re.sub(r"\s*,\s*", ",", text)
    return ZONE_PREFIX.sub("ZONE_", text)


def number_addresses(lines: list[str]) -> list[str]:
    """Replace ROM-address labels by ordinals in order of first appearance."""
    seen: dict[str, str] = {}

    def sub(m: re.Match) -> str:
        key = m.group(0)
        if key not in seen:
            seen[key] = f"{m.group(1)}_#{len(seen) + 1}"
        return seen[key]

    return [ADDRESS_LABEL.sub(sub, ln) for ln in lines]


def label_of(text: str) -> str | None:
    """Return the global label defined at column 0 of a canonical line, if named."""
    if text[:1].isspace():
        return None
    m = re.match(r"([A-Za-z_][\w]*):?(?:\s|$)", text)
    if not m:
        return None
    name = m.group(1)
    if name.lower() in DIRECTIVES or ADDRESS_LABEL.fullmatch(name):
        return None
    rest = text[m.end():].strip().split(None, 1)
    # `Name equ ...` / `Name = ...` are symbol definitions, not routines.
    if rest and rest[0].lower() in ("equ", "set", "=", "rs.b", "rs.w", "rs.l", "macro", "macros"):
        return None
    return name




REGISTERS = {f"d{i}" for i in range(8)} | {f"a{i}" for i in range(8)} | {"sp", "pc", "sr", "ccr", "usp"}
SKEL_TOKEN = re.compile(r"""("[^"]*"|'[^']*')|(?<![\w$%.@])([A-Za-z_.@][\w.@]*)""")


NUMBER = re.compile(r"(?<![\w$%.])(\$[0-9A-Fa-f]+|%[01]+|\d+)(?![\w])")
NUMRUN = re.compile(r"(?<![\w$%.)])(-?\d+(?:[-+*/]\d+)+)(?![\w(])")


def split_operands(ops: str) -> list[str]:
    out, depth, cur = [], 0, ""
    for ch in ops:
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
        if ch == "," and depth == 0:
            out.append(cur)
            cur = ""
        else:
            cur += ch
    out.append(cur)
    return out


PURE_EXPR = re.compile(r"^#?[\d$%A-Fa-f()<>|&^+\-*/~ ]+$")


def fold_numbers(ops: str) -> str:
    """Write every literal in decimal and fold pure-number arithmetic (#9+1 == #$A == #(5*2)).

    Only operands made solely of numbers and operators are folded, so addressing modes such as
    `4(a0)` or `(a0)+` and anything containing a symbol are left alone.
    """
    def dec(m: re.Match) -> str:
        t = m.group(1)
        return str(int(t[1:], 16) if t[0] == "$" else int(t[1:], 2) if t[0] == "%" else int(t))

    ops = NUMBER.sub(dec, ops)

    def fold_operand(op: str) -> str:
        if PURE_EXPR.match(op) and re.search(r"\d", op) and re.search(r"[-+*/<>|&^~]", op.lstrip("#-")):
            try:
                return ("#" if op.startswith("#") else "") + str(Evaluator({}).eval(op.lstrip("#")))
            except (ExprError, Undefined):
                return op
        return op

    return ",".join(fold_operand(o) for o in split_operands(ops))


ABS_SUFFIX = re.compile(r"([A-Za-z_@][\w@]*|\d+)\.[wl]\b")
REG_NAME = re.compile(r"[daDA][0-7]")
IMMEDIATE = re.compile(r"#(\d+)(?![\w.])")


def signed_immediate(m: re.Match, size: str) -> str:
    """#$F800 and #-$800 are the same word immediate; write both as the signed value."""
    v = int(m.group(1))
    if size == "w" and 0x8000 <= v <= 0xFFFF:
        v -= 0x10000
    elif size == "b" and 0x80 <= v <= 0xFF:
        v -= 0x100
    return f"#{v}"


def split_label(marked: str) -> tuple[str | None, str]:
    """Split a marked line into (column-0 label or None, remaining statement)."""
    if not marked.startswith("\x00"):
        return None, marked
    m = re.match(r"([^\s:]+):?\s*(.*)$", marked[1:])
    return m.group(1), m.group(2)


def skeletonize(statement: str, symbols: dict[str, int] | None = None) -> tuple[str, list[str]]:
    """Anonymise symbols: keep mnemonic, registers, numbers and punctuation.

    Symbols with a known numeric value (EQU constants, RAM addresses, rs offsets) are
    replaced by that value, so `#SCMD_YESSFX` equals `#$7A`; other symbols (code labels)
    become `S` and are compared by the rename-consistency check instead.  Label
    definitions are not part of the statement: renaming or splitting labels is invisible.
    """
    idents: list[str] = []
    parts = statement.split(" ", 1)
    size = parts[0][-1] if re.search(r"\.[bwl]$", parts[0]) else "w"
    mnem = re.sub(r"\.[ws]$", "", parts[0])      # default size / branch size are not semantic
    ops = re.sub(r"(?<=[-+])\*(?![\w$%(])", ".Index", parts[1]) if len(parts) > 1 else ""
    ops = ABS_SUFFIX.sub(lambda m: m.group(0) if REG_NAME.fullmatch(m.group(1)) else m.group(1), ops)

    def sub(m: re.Match) -> str:
        if m.group(1):
            return m.group(1)
        tok = m.group(2)
        if tok.lower() in REGISTERS:
            return tok.lower()
        if tok.lower().endswith((".w", ".l")) and len(tok) > 2:
            if tok[:-2].lower() in REGISTERS:
                return tok.lower()  # index register size is semantic: (a0,d0.w) != (a0,d0.l)
            tok = tok[:-2]          # absolute word/long addressing of the same symbol
        if symbols is not None and tok in symbols:
            return str(symbols[tok])
        idents.append(tok)
        return "S"

    folded = fold_numbers(SKEL_TOKEN.sub(sub, ops))
    return mnem + " " + IMMEDIATE.sub(lambda m: signed_immediate(m, size), folded), idents


LOOSE_NUMBER = re.compile(r"(?<![\w$%.])(\d{7,})(?![\w])")


def loosen(line: str) -> str:
    """Treat unresolved symbols and RAM/IO-sized addresses (>= $200000) alike."""
    line = LOOSE_NUMBER.sub(lambda m: "S" if int(m.group(1)) >= 0x200000 else m.group(1), line)
    return line


DATA_MNEMONIC = re.compile(r"(dc|ds|dcb|incbin|even|align|cnop|org|rs)(\.\w)? ", re.I)


class Unit:
    """A run of normalised lines with text and skeleton hashes (file or routine)."""
    __slots__ = ("name", "lines", "skel", "loose", "idents", "linenos", "text_hash", "skel_hash", "line", "end",
                 "code_lines")

    def __init__(self, name, lines, skel, idents, linenos):
        self.name = name
        self.lines, self.skel, self.idents, self.linenos = lines, skel, idents, linenos
        self.loose = [loosen(x) for x in skel]
        self.text_hash = hashlib.sha1("\n".join(lines).encode()).hexdigest()
        self.skel_hash = hashlib.sha1("\n".join(skel).encode()).hexdigest()
        self.line = linenos[0] if linenos else 0
        self.end = linenos[-1] if linenos else 0
        self.code_lines = sum(1 for ln in skel if not DATA_MNEMONIC.match(ln))


def block_starts(marked: list[str]) -> list[tuple[int, str]]:
    """Routine entry points: named column-0 labels, folding `Foo`-prefixed sub-labels into `Foo`.

    The two source dialects split routines differently (`LoadPalette`/`LoadPaletteLoop` vs one
    block with a local `.Load`), so a label that extends the current routine's name is treated
    as part of that routine.
    """
    out, root = [], None
    for i, m in enumerate(marked):
        if not m.startswith("\x00"):
            continue
        name = label_of(m[1:])
        if not name:
            continue
        if root and name.startswith(root) and len(name) > len(root):
            continue
        root = name
        out.append((i, ZONE_PREFIX.sub("ZONE_", name)))
    return out


class View:
    """Normalised, hashed view of one file instance (label-free statement stream)."""

    def __init__(self, inst: Instance, symbols: dict[str, int] | None = None):
        self.rel = inst.rel
        self.raw_hash = hashlib.sha1("\n".join(t for _, t in inst.active).encode()).hexdigest()
        marked_all = [("" if t[:1] in " \t" else "\x00") + canon_line(t) for _, t in inst.active]
        starts = block_starts(marked_all)
        # statements only: label-only lines vanish, labels are stripped from the rest
        keep, stmts, linenos, kept_before = [], [], [], []
        for idx, ((n, _), m) in enumerate(zip(inst.active, marked_all)):
            kept_before.append(len(stmts))
            _, st = split_label(m)
            if st:
                stmts.append(st)
                linenos.append(n)
        kept_before.append(len(stmts))
        skel, idents = [], []
        for st in stmts:
            sk, idn = skeletonize(st, symbols)
            skel.append(sk)
            idents.append(idn)
        self.unit = Unit(inst.rel, number_addresses(stmts), skel, idents, linenos)
        self.norm_hash, self.skel_hash = self.unit.text_hash, self.unit.skel_hash
        self.blocks: list[Unit] = []
        edges = [kept_before[i] for i, _ in starts] + [len(stmts)]
        for (i0, nm), e0, e1 in zip(starts, edges, edges[1:]):
            self.blocks.append(Unit(nm, number_addresses(stmts[e0:e1]), skel[e0:e1], idents[e0:e1], linenos[e0:e1]))
        self.head = None
        if starts and edges[0] > 0:
            self.head = Unit("(file head)", number_addresses(stmts[:edges[0]]), skel[:edges[0]],
                             idents[:edges[0]], linenos[:edges[0]])
        elif not starts and stmts:
            self.head = self.unit
        self.instr = sum(1 for ln in stmts if not DATA_MNEMONIC.match(ln + " "))
        self.data = len(stmts) - self.instr


def compare(ref: Unit, new: Unit, hunk_limit: int = 4) -> dict:
    """Classify how `new` differs from `ref`.

    identical              same text after normalisation
    rename-only            same instructions; symbols renamed consistently
    symbol-operands-differ same instructions; a symbol maps inconsistently, or an address operand is
                           a named symbol in one program and unresolved/different in the other
    instructions-differ    different instruction stream (constants, opcodes, added/removed lines)
    """
    if ref.text_hash == new.text_hash:
        return {"kind": "identical", "changed": 0}
    changed_text = sum(1 for a, b in zip(ref.lines, new.lines) if a != b)
    if ref.skel == new.skel:
        fwd, rev, conflicts = {}, {}, []
        for ia, ib, ln in zip(ref.idents, new.idents, new.linenos):
            for x, y in zip(ia, ib):
                if fwd.setdefault(x, y) != y or rev.setdefault(y, x) != x:
                    conflicts.append((ln, x, y))
        if not conflicts:
            return {"kind": "rename-only", "changed": changed_text,
                    "renamed_symbols": sum(1 for x, y in fwd.items() if x != y)}
        return {"kind": "symbol-operands-differ", "changed": changed_text,
                "conflicts": [{"line": ln, "ref_symbol": x, "new_symbol": y} for ln, x, y in conflicts[:5]]}
    if ref.loose == new.loose:
        return {"kind": "symbol-operands-differ", "changed": changed_text,
                "note": "address operands differ only as resolved value vs unresolved symbol"}
    if len(ref.loose) * len(new.loose) > 60_000_000:
        return {"kind": "instructions-differ", "changed": None, "note": "too large to diff"}
    sm = difflib.SequenceMatcher(None, ref.loose, new.loose, autojunk=True)
    ops = [op for op in sm.get_opcodes() if op[0] != "equal"]
    hunks = [{
        "ref_line": ref.linenos[i1] if i1 < len(ref.linenos) else None,
        "line": new.linenos[j1] if j1 < len(new.linenos) else None,
        "ref": ref.lines[i1:i2][:3], "new": new.lines[j1:j2][:3],
        "ref_n": i2 - i1, "new_n": j2 - j1,
    } for _, i1, i2, j1, j2 in ops[:hunk_limit]]
    return {"kind": "instructions-differ",
            "changed": sum(max(i2 - i1, j2 - j1) for _, i1, i2, j1, j2 in ops),
            "hunk_count": len(ops), "hunks": hunks}


def behaviour_groups(entries: list[dict]) -> list[dict]:
    """Merge text-classes whose bodies differ only by consistent symbol renames.

    Each entry: {"unit": Unit, "stages": [...], "files": set}.  Returns groups ordered
    by stage count; every group carries `variants` (text classes) and, for groups
    after the first, a `diff` against the first.
    """
    entries = sorted(entries, key=lambda e: (-len(e["stages"]), e["stages"][0]))
    groups: list[dict] = []
    for e in entries:
        for g in groups:
            if g["unit"].skel_hash == e["unit"].skel_hash and compare(g["unit"], e["unit"])["kind"] == "rename-only":
                g["stages"] += e["stages"]
                g["files"] |= e["files"]
                g["variants"] += 1
                break
        else:
            groups.append({"unit": e["unit"], "stages": list(e["stages"]), "files": set(e["files"]), "variants": 1})
    groups.sort(key=lambda g: (-len(g["stages"]), min(g["stages"])))
    for i, g in enumerate(groups):
        g["stages"].sort()
        g["diff"] = None if i == 0 else compare(groups[0]["unit"], g["unit"])
    return groups


# ---------------------------------------------------------------- stage discovery
ASSEMBLE = re.compile(r"^assemble\s+'([^']+)'\s+'([^']*)'\s+'[^']*'(.*)$")
FID = re.compile(r"^FID_(R[1345678][123][ABCD])\s+rs\.b\s+1\s*;\s*(.*)$")


def discover(root: Path, region: int):
    src = root / "src"
    stages, demos, fid_order = {}, {}, {}
    sysinc = src / "_Include/System.inc"
    if sysinc.exists():
        for line in sysinc.read_text(errors="replace").splitlines():
            m = FID.match(line.strip())
            if m:
                fid_order[m.group(1)] = m.group(2).strip()
    make = root / "make.sh"
    if make.exists():
        for line in make.read_text(errors="replace").splitlines():
            m = ASSEMBLE.match(line.strip())
            if not m:
                continue
            rootfile, out, tail = m.groups()
            out_name = out.replace("\\", "/").rsplit("/", 1)[-1]
            defs = {k: int(v) for k, v in re.findall(r"(\w+)=(-?\d+)", tail)}
            if out_name.startswith("DEMO"):
                demos[out_name.split(".")[0]] = {"root": rootfile.replace("\\", "/"), "defines": defs,
                                                 "base": "R" + out_name[4:7]}
            elif re.match(r"R[1345678][123][ABCD]__\.MMD$", out_name):
                stages[out_name[:4]] = {"root": rootfile.replace("\\", "/"), "defines": defs}
    for code, info in stages.items():
        z, act, period = STAGE_CODE.fullmatch(code).groups()
        info.update(code=code, zone=ZONE_NAMES[z], zone_digit=z, act=int(act),
                    period={"A": "Present", "B": "Past", "C": "Good Future", "D": "Bad Future"}[period],
                    title=fid_order.get(code, ""))
        info["defines"] = {**info["defines"], "REGION": region}
        info["defines"].setdefault("DEMO", 0)
    return stages, demos, fid_order


# ---------------------------------------------------------------- reporting helpers
def categorize(rel: str) -> str:
    low = rel.lower()
    for name, rx in CATEGORY_RES:
        if rx.search(low):
            return name
    return "other"


BEHAVIOURAL = ("real", "legacy-named code")
LEGACY_CODE_MIN = 20     # instruction lines; fewer means byte padding / tail / retained data


def kind_of(rel: str, instructions: int = 0) -> str:
    """real / legacy-named code / padding-legacy data / compatibility shim.

    A file whose name says Legacy, Padding, Tail, Retained, Leftover, NonUSA or Unused is
    padding or a leftover unless it carries real code: with LEGACY_CODE_MIN or more instruction
    lines (not dc/ds/incbin) it is kept in the behavioural analysis as `legacy-named code`.
    """
    base = rel.rsplit("/", 1)[-1]
    if LEGACY_RE.search(base):
        return "legacy-named code" if instructions >= LEGACY_CODE_MIN else "padding-legacy data"
    if COMPAT_RE.search(base):
        return "compatibility shim"
    return "real"


def role_key(rel: str) -> str:
    base = PurePosixPath(rel).stem.lower()
    base = re.sub(r"\(.*?\)", " ", base)
    base = re.sub(r"\bact ?\d\b|\bpresent\b|\bpast\b|\bgood future\b|\bbad future\b|\bfuture\b|\bvariant\b|\bshared\b", " ", base)
    base = re.sub(r"\br\d{1,2}[a-d]?\b", " ", base)
    base = re.sub(r"[ _]+", "_", base.strip())
    base = re.sub(r"(_\d+[a-d]?|_[a-d])+$", "", base)
    base = base.strip("_")
    return base.replace("collide", "collision")


def fmt_set(codes, all_codes) -> str:
    codes = sorted(codes)
    if len(codes) == len(all_codes):
        return f"all {len(codes)}"
    by_zone, zone_all = defaultdict(list), defaultdict(list)
    for c in codes:
        by_zone[c[1]].append(c)
    for c in all_codes:
        zone_all[c[1]].append(c)
    parts = []
    for z in sorted(by_zone):
        if len(by_zone[z]) == len(zone_all[z]):
            parts.append(f"R{z}*")
        else:
            parts.append(f"R{z}: " + " ".join(c[2:] for c in by_zone[z]))
    return ", ".join(parts)


def group_report(groups: list[dict], with_unit: bool = True) -> list[dict]:
    out = []
    for g in groups:
        d = g["diff"]
        out.append({
            "stages": g["stages"], "files": sorted(g["files"]), "variants": g["variants"],
            "lines": len(g["unit"].lines), "code_lines": g["unit"].code_lines, "source_line": g["unit"].line, "source_end": g["unit"].end,
            "kind_vs_largest": None if d is None else d["kind"],
            "changed_vs_largest": None if d is None else d.get("changed"),
            "diff": None if d is None else {k: v for k, v in d.items() if k not in ("kind", "changed")},
        })
    return out


WORD = re.compile(r"[A-Za-z_.@][\w.@]*")


class ViewFactory:
    """Builds Views, sharing them between stages when file text and symbol values agree."""

    def __init__(self):
        self.views: dict[tuple, View] = {}
        self.tokens: dict[tuple, list[str]] = {}

    def make(self, inst: Instance, symbols: dict[str, int]) -> View:
        text = "\n".join(t for _, t in inst.active)
        sig = hashlib.sha1(text.encode()).hexdigest()
        toks = self.tokens.get((inst.rel, sig))
        if toks is None:
            toks = self.tokens[(inst.rel, sig)] = sorted(set(WORD.findall(text)))
        relevant = hash(tuple((t, symbols[t]) for t in toks if t in symbols))
        key = (inst.rel, sig, relevant)
        if key not in self.views:
            self.views[key] = View(inst, symbols)
        return self.views[key]


def changed_files(base_views: dict, inst: dict, symbols: dict, factory: "ViewFactory") -> list[str]:
    out = []
    for rel in sorted(set(inst) | set(base_views)):
        a = base_views.get(rel)
        b = factory.make(inst[rel], symbols) if rel in inst else None
        if (a is None) != (b is None) or (a and b and a.raw_hash != b.raw_hash):
            out.append(("added:" if a is None else "removed:" if b is None else "changed:") + rel)
    return out


# ---------------------------------------------------------------- the census
def run_census(root: Path, region_name: str = "usa", compare_regions: bool = True) -> dict:
    region = REGION_VALUES[region_name]
    warn = Warnings()
    tree = Tree(root)
    stages, demos, fid = discover(root, region)
    if not stages:
        raise SystemExit(f"no stage programs found under {root} (need make.sh assemble lines)")
    if fid and set(fid) != set(stages):
        warn.add(f"stage list mismatch: make.sh-only={sorted(set(stages) - set(fid))} "
                 f"System.inc-only={sorted(set(fid) - set(stages))}")
    all_codes = sorted(stages)

    factory = ViewFactory()
    resolved: dict[str, dict] = {}
    for code in all_codes:
        info = stages[code]
        inst, order, unresolved, syms = assemble_stage(tree, info["root"], info["defines"], warn)
        views = {rel: factory.make(i, syms) for rel, i in inst.items()}
        resolved[code] = {"inst": inst, "order": order, "views": views, "unresolved": unresolved}

    file_stages: dict[str, list[str]] = defaultdict(list)
    for code in all_codes:
        for rel in resolved[code]["order"]:
            file_stages[rel].append(code)

    # ---- per file
    files = {}
    for rel, codes in sorted(file_stages.items()):
        by_text: dict[str, dict] = {}
        inc_classes: dict[tuple, list[str]] = defaultdict(list)
        for code in codes:
            v = resolved[code]["views"][rel]
            e = by_text.setdefault(v.norm_hash, {"unit": v.unit, "stages": [], "files": {rel}})
            e["stages"].append(code)
            inc_classes[tuple(resolved[code]["inst"][rel].includes)].append(code)
        groups = behaviour_groups(list(by_text.values()))
        inst0, v0 = resolved[codes[0]]["inst"][rel], resolved[codes[0]]["views"][rel]
        files[rel] = {
            "path": rel, "category": categorize(rel), "kind": kind_of(rel, v0.instr), "role": role_key(rel),
            "stages": codes, "stage_count": len(codes), "zones": sorted({c[1] for c in codes}),
            "instructions": v0.instr, "data_lines": v0.data, "inactive_lines": inst0.inactive_lines,
            "incbins": inst0.incbins, "text_classes": len(by_text),
            "classes": group_report(groups),
            "include_classes": [{"includes": list(k), "stages": v} for k, v in
                                sorted(inc_classes.items(), key=lambda kv: -len(kv[1]))],
        }

    # ---- routines (named blocks), grouped by name across all stages
    by_name: dict[str, dict[str, dict]] = defaultdict(dict)
    skel_names: dict[str, set] = defaultdict(set)
    for code in all_codes:
        seen = defaultdict(int)
        for rel in resolved[code]["order"]:
            if files[rel]["kind"] not in BEHAVIOURAL:
                continue
            for b in resolved[code]["views"][rel].blocks:
                seen[b.name] += 1
                key = b.name if seen[b.name] == 1 else f"{b.name}#{seen[b.name]}"
                e = by_name[key].setdefault(b.text_hash, {"unit": b, "stages": [], "files": set()})
                e["stages"].append(code)
                e["files"].add(rel)
                if len(b.lines) >= 6:
                    skel_names[b.skel_hash].add(key)
    routine_report = {}
    for name, by_text in by_name.items():
        if len(by_text) < 2:
            continue
        groups = behaviour_groups(list(by_text.values()))
        rep = group_report(groups)
        for r, g in zip(rep, groups):
            twins = sorted(skel_names.get(g["unit"].skel_hash, set()) - {name})
            r["same_instructions_as"] = twins[:5]
        present = {s for g in groups for s in g["stages"]}
        routine_report[name] = {
            "present_in": len(present), "group_count": len(groups), "text_variants": len(by_text),
            "category": categorize(next(iter(groups[0]["files"]))), "groups": rep,
            "worst_kind": ("instructions-differ" if any(r["kind_vs_largest"] == "instructions-differ" for r in rep)
                           else "symbol-operands-differ" if any(r["kind_vs_largest"] == "symbol-operands-differ" for r in rep)
                           else "rename-only")}

    # ---- engine category classes per stage (name-independent: skeleton hashes of routine blocks)
    def category_stream(code: str, cat: str):
        lines, loose, src = [], [], []
        for rel in resolved[code]["order"]:
            if not in_group(cat, rel) or files[rel]["kind"] not in BEHAVIOURAL:
                continue
            u = resolved[code]["views"][rel].unit
            lines += u.lines
            loose += u.loose
            src += [(rel, n) for n in u.linenos]
        return lines, loose, src

    def in_group(cat: str, rel: str) -> bool:
        c = files[rel]["category"]
        return c == cat or (cat.startswith("AREA ") and AREAS.get(c) == cat)

    cat_report = {}
    groups_to_report = sorted({f["category"] for f in files.values()} - {"other"} | set(AREAS.values()))
    for cat in groups_to_report:
        sigs: dict[str, dict] = {}
        for code in all_codes:
            sig = []
            for rel in resolved[code]["order"]:
                f = files[rel]
                if not in_group(cat, rel) or f["kind"] not in BEHAVIOURAL:
                    continue
                v = resolved[code]["views"][rel]
                for u in list(v.blocks) + ([v.head] if v.head else []):
                    sig.append((u.skel_hash, u.name if u.name != "(file head)" else f"<head:{f['role']}>"))
            key = hashlib.sha1("\n".join(sorted(h for h, _ in sig)).encode()).hexdigest()
            s_ = sigs.setdefault(key, {"stages": [], "names": defaultdict(list), "n": len(sig)})
            s_["stages"].append(code)
            if len(s_["stages"]) == 1:
                for h, n in sig:
                    s_["names"][h].append(n)
        if not sigs:
            continue
        ordered = sorted(sigs.values(), key=lambda s_: (-len(s_["stages"]), s_["stages"][0]))
        ref = ordered[0]["names"]
        ref_lines, ref_loose, ref_src = category_stream(ordered[0]["stages"][0], cat)
        ref_bag = defaultdict(int)
        for ln in ref_loose:
            ref_bag[ln] += 1
        classes = []
        for i, s_ in enumerate(ordered):
            added = sorted(n for h, ns in s_["names"].items() if h not in ref for n in ns)
            removed = sorted(n for h, ns in ref.items() if h not in s_["names"] for n in ns)
            entry = {"stages": s_["stages"], "routines": s_["n"],
                     "only_here": added[:60] if i else [], "only_in_largest": removed[:60] if i else [],
                     "only_here_count": len(added) if i else 0, "only_in_largest_count": len(removed) if i else 0}
            if i:
                lines, loose, src = category_stream(s_["stages"][0], cat)
                bag = defaultdict(int)
                for ln in loose:
                    bag[ln] += 1
                entry["bag_diff_lines"] = sum(abs(bag[k] - ref_bag.get(k, 0)) for k in set(bag) | set(ref_bag))
                entry["stream_lines"] = [len(ref_loose), len(loose)]
                if len(ref_loose) * len(loose) <= 60_000_000:
                    sm = difflib.SequenceMatcher(None, ref_loose, loose, autojunk=True)
                    ops = [op for op in sm.get_opcodes() if op[0] != "equal"]
                    entry["ordered_diff_lines"] = sum(max(i2 - i1, j2 - j1) for _, i1, i2, j1, j2 in ops)
                    entry["hunks"] = [{
                        "ref": f"{ref_src[i1][0]}:{ref_src[i1][1]}" if i1 < len(ref_src) else None,
                        "new": f"{src[j1][0]}:{src[j1][1]}" if j1 < len(src) else None,
                        "ref_lines": ref_lines[i1:i2][:3], "new_lines": lines[j1:j2][:3],
                        "ref_n": i2 - i1, "new_n": j2 - j1} for _, i1, i2, j1, j2 in
                        sorted(ops, key=lambda o: -max(o[2] - o[1], o[4] - o[3]))[:5]]
            classes.append(entry)
        cat_report[cat] = {"class_count": len(classes), "classes": classes}

    # ---- families (same role, several files)
    families: dict[str, dict] = defaultdict(lambda: defaultdict(list))
    for rel, f in files.items():
        for s in f["stages"]:
            families[f["role"]][rel].append(s)
    family_report = {}
    for role, by_file in families.items():
        if len(by_file) < 2:
            continue
        by_text: dict[str, dict] = {}
        for rel, codes in by_file.items():
            for code in codes:
                v = resolved[code]["views"][rel]
                e = by_text.setdefault(v.norm_hash, {"unit": v.unit, "stages": [], "files": set()})
                e["stages"].append(code)
                e["files"].add(rel)
        groups = behaviour_groups(list(by_text.values()))
        family_report[role] = {"category": categorize(next(iter(by_file))), "file_count": len(by_file),
                               "group_count": len(groups), "groups": group_report(groups)}

    dup = defaultdict(set)
    for rel, f in files.items():
        for code in f["stages"][:1]:
            u = resolved[code]["views"][rel].unit
            if len(u.lines) >= 20:
                dup[u.skel_hash].add(rel)
    duplicates = [sorted(v) for v in dup.values() if len(v) > 1]

    zone_only, stage_only = defaultdict(list), defaultdict(list)
    objects: dict[str, dict] = defaultdict(lambda: {"zones": set(), "files": set(), "stages": set()})
    for rel, f in files.items():
        if f["stage_count"] == 1:
            stage_only[f["stages"][0]].append(rel)
        if len(f["zones"]) == 1:
            zone_only[f["zones"][0]].append(rel)
        m = OBJECT_PATH.search(rel)
        if m and f["category"] not in ("debug mode", "mappings / art data") and f["kind"] in BEHAVIOURAL:
            o = objects[m.group(1).lower()]
            o["zones"].update(f["zones"])
            o["files"].add(rel)
            o["stages"].update(f["stages"])

    demo_report = {}
    for dname, dinfo in demos.items():
        base = dinfo["base"]
        if base in stages:
            inst, _, _, syms = assemble_stage(tree, dinfo["root"], {**dinfo["defines"], "REGION": region}, Warnings())
            demo_report[dname] = {"base": base, "differs": changed_files(resolved[base]["views"], inst, syms, factory)}
    region_report = {}
    if compare_regions:
        for name, val in REGION_VALUES.items():
            if val == region:
                continue
            out = {}
            for code in all_codes:
                inst, _, _, syms = assemble_stage(tree, stages[code]["root"], {**stages[code]["defines"], "REGION": val}, Warnings())
                ch = changed_files(resolved[code]["views"], inst, syms, factory)
                if ch:
                    out[code] = ch
            region_report[name] = out

    return {
        "stages": {c: {k: stages[c][k] for k in ("code", "zone", "act", "period", "title", "root", "defines")}
                   | {"files": resolved[c]["order"], "unresolved_includes": resolved[c]["unresolved"]}
                   for c in all_codes},
        "files": files,
        "routines_with_differences": routine_report,
        "category_classes": cat_report,
        "families": family_report,
        "content_identical_copies": duplicates,
        "zone_only_files": {ZONE_NAMES[z]: sorted(v) for z, v in sorted(zone_only.items())},
        "stage_only_files": {c: sorted(v) for c, v in sorted(stage_only.items())},
        "objects": {n: {"zones": sorted(o["zones"]), "files": sorted(o["files"]), "stage_count": len(o["stages"])}
                    for n, o in sorted(objects.items())},
        "demo_builds": demo_report,
        "region_deltas": region_report,
        "meta": {"region": region_name, "stage_count": len(all_codes), "file_count": len(files),
                 "warnings": warn.items, "disasm": str(root)},
    }


# ---------------------------------------------------------------- markdown
def write_markdown(result: dict, out: Path):
    all_codes = sorted(result["stages"])
    files, m = result["files"], result["meta"]
    md = [f"# Sonic CD variant census ({m['region'].upper()} build)\n",
          f"{m['stage_count']} stage programs; {m['file_count']} source files in the union of include graphs; "
          f"{len(result['routines_with_differences'])} named routines with more than one text body; "
          f"{len(result['families'])} role families spanning several files.\n"]
    if m["warnings"]:
        md.append(f"Warnings ({len(m['warnings'])}): see JSON `meta.warnings`.\n")
    md.append("Kinds: `rename-only` = same instructions, symbols renamed consistently; `symbol-operands-differ` = same "
              "instruction skeleton but a symbol maps inconsistently; `instructions-differ` = different instruction stream.\n")

    md.append("## Engine category classes (stages grouped by identical routine content)\n")
    md.append("| Category | Classes | Class sizes | Largest class |\n|---|---|---|---|")
    for cat, c in sorted(result["category_classes"].items()):
        md.append(f"| {cat} | {c['class_count']} | {', '.join(str(len(k['stages'])) for k in c['classes'][:12])} | "
                  f"{fmt_set(c['classes'][0]['stages'], all_codes)} |")

    md.append("\n## Shared files whose behaviour differs between stages\n")
    md.append("| File | Category | Kind | Groups (stages) | vs largest |\n|---|---|---|---|---|")
    for rel, f in sorted(files.items(), key=lambda kv: (kv[1]["category"], kv[0])):
        if f["stage_count"] > 1 and len(f["classes"]) > 1:
            cls = "; ".join(fmt_set(c["stages"], all_codes) for c in f["classes"][:6])
            kinds = ", ".join(f"{c['kind_vs_largest']}~{c['changed_vs_largest']}" for c in f["classes"][1:4])
            md.append(f"| `{rel}` | {f['category']} | {f['kind']} | {cls} | {kinds} |")
    ident = [(rel, f) for rel, f in sorted(files.items()) if f["stage_count"] > 1 and len(f["classes"]) == 1]
    md.append(f"\n## Shared files identical after normalisation in every stage that includes them ({len(ident)})\n")
    for rel, f in ident:
        extra = "" if f["text_classes"] == 1 else f", {f['text_classes']} text variants differing only by symbol renames"
        md.append(f"- `{rel}` ({f['stage_count']} stages, {f['category']}, {f['kind']}{extra})")
    md.append("\n## Role families spanning several files\n")
    md.append("| Role | Category | Files | Groups | Largest | Others |\n|---|---|---|---|---|---|")
    for role, fam in sorted(result["families"].items(), key=lambda kv: (kv[1]["category"], kv[0])):
        top = fam["groups"][0]
        rest = "; ".join(fmt_set(c["stages"], all_codes) for c in fam["groups"][1:5])
        md.append(f"| {role} | {fam['category']} | {fam['file_count']} | {fam['group_count']} | "
                  f"{fmt_set(top['stages'], all_codes)} | {rest} |")
    md.append("\n## Named routines with differences\n")
    md.append("| Routine | Category | Groups | Worst kind | Largest | Others | Source |\n|---|---|---|---|---|---|---|")
    for name, r in sorted(result["routines_with_differences"].items(), key=lambda kv: (kv[1]["category"], kv[0])):
        g0 = r["groups"][0]
        others = "; ".join(f"{fmt_set(c['stages'], all_codes)} ({c['kind_vs_largest']} ~{c['changed_vs_largest']})"
                           for c in r["groups"][1:4])
        md.append(f"| `{name}` | {r['category']} | {r['group_count']} | {r['worst_kind']} | "
                  f"{fmt_set(g0['stages'], all_codes)} | {others} | `{g0['files'][0]}:{g0['source_line']}` |")
    md.append("\n## Per-zone files used only by that zone\n")
    for zone, rels in result["zone_only_files"].items():
        md.append(f"- {zone}: {len(rels)} files")
    md.append("\n## Objects\n")
    md.append("| Object | Zones | Stages |\n|---|---|---|")
    for name, o in result["objects"].items():
        md.append(f"| {name} | {', '.join(ZONE_NAMES[z] for z in o['zones'])} | {o['stage_count']} |")
    md.append("\n## Demo builds and other regions\n")
    for d, info in result["demo_builds"].items():
        md.append(f"- {d} vs {info['base']}: {len(info['differs'])} files differ")
    for name, deltas in result["region_deltas"].items():
        md.append(f"- {name}: {len(deltas)} stages differ from the {m['region']} build "
                  f"({sum(len(v) for v in deltas.values())} file deltas)")
    (out / "variant-census.md").write_text("\n".join(md) + "\n")


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("disasm", type=Path, help="scddisasm checkout (contains make.sh and src/)")
    ap.add_argument("output", type=Path, help="output directory for variant-census.json / .md")
    ap.add_argument("--region", choices=sorted(REGION_VALUES), default="usa")
    ap.add_argument("--no-region-compare", action="store_true", help="skip Japan/Europe delta passes")
    args = ap.parse_args(argv)
    result = run_census(args.disasm, args.region, not args.no_region_compare)
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / "variant-census.json").write_text(json.dumps(result, indent=1, sort_keys=True, default=list))
    write_markdown(result, args.output)
    m = result["meta"]
    print(f"{m['stage_count']} stages, {m['file_count']} files, "
          f"{len(result['routines_with_differences'])} routines with differing bodies, {len(m['warnings'])} warnings")
    return 0


if __name__ == "__main__":
    sys.exit(main())
