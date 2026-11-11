#!/usr/bin/env python3
"""Write the Gilded (Open Cone), Hyper Prism Gilt and Solar (Corona Crest) palette data.

Purpose: one reviewable source for the powered cycles in production/*/presentation/{gilded,solar}.yaml.
Inputs: the normal sheet palettes; colour words below (Mega Drive BGR, as in palettes.txt).
Origin: powered-form concepts lane, 2026-11-11. Run from the repository root, then
`tools/modding/playable_art/gilded_java.py` and `solar_java.py` to refresh palettes.txt.
`--check` compares without writing.
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
PROD = ROOT / "examples/mica-and-talon/production"
ART = ROOT / "examples/mica-and-talon/art"
STEPS = [0, 36, 73, 109, 146, 182, 219, 255]


def rgb(word):
    return [STEPS[(word & 0xF) // 2], STEPS[((word >> 4) & 0xF) // 2], STEPS[((word >> 8) & 0xF) // 2]]


def snap(value):
    return min(STEPS, key=lambda s: abs(s - value))


def blend(a, b, t):
    return [[snap(x + (y - x) * t) for x, y in zip(ca, cb)] for ca, cb in zip(a, b)]


def row(base, slots, words):
    out = [list(c) for c in base]
    for slot, word in zip(slots, words):
        out[slot] = rgb(word)
    return out


# MICA: slots 2-6 are the plates, deep amber underside first; face, belly, boots stay fixed.
MICA_FIXED = [0x000, 0x222, 0, 0, 0, 0, 0, 0x8CE, 0x6AC, 0x68A, 0x44E, 0x22A, 0xEEE, 0xCCC, 0x88E, 0x024]
GOLD = [
    [0x04A, 0x08E, 0x0CE, 0x4EE, 0xCEE],
    [0x04C, 0x08E, 0x2CE, 0x6EE, 0xCEE],
    [0x06C, 0x0AE, 0x4EE, 0x8EE, 0xEEE],
    [0x06E, 0x0AE, 0x6EE, 0xAEE, 0xEEE],
    [0x08E, 0x0CE, 0x8EE, 0xCEE, 0xEEE],
]
# Gold breathes from saturated to bright and back; the amber underside never turns pale.
GOLD_CYCLE = [0, 1, 2, 3, 4, 3, 2, 1, 0]
WHITE_HOT = [0x0AE, 0x6CE, 0xAEE, 0xCEE, 0xEEE]
GOLD_TO_WHITE = [0x08E, 0x2CE, 0x6EE, 0xAEE, 0xEEE]
GOLD_TO_JADE = [0x460, 0x6A2, 0x6C4, 0xAE6, 0xCEA]
GOLD_TO_ROSE = [0x26A, 0x48C, 0x6AE, 0x8CE, 0xCEE]
JADE = [0x440, 0x660, 0x680, 0x8A2, 0xAC6]
ROSE = [0x248, 0x46C, 0x68E, 0x8AE, 0xCCE]

# TALON: slot 2 stays dark bronze (bars, moustache, feather breaks), 3-5 white-gold plumage,
# 6 the cycling orange flame tip; 7 chest cream, 8 tan. Fixed amber 14/15 and red 10 finish the flames.
TALON_FIXED = [0x000, 0x222, 0, 0, 0, 0, 0, 0xACE, 0x8AC, 0x246, 0x44E, 0x22A, 0xEEE, 0xAAA, 0x2AE, 0x26A]
SOLAR = [
    [0x026, 0x6CE, 0xAEE, 0xCEE, 0x08E],
    [0x026, 0x6CE, 0xAEE, 0xEEE, 0x0AE],
    [0x046, 0x8CE, 0xCEE, 0xEEE, 0x0CE],
    [0x046, 0x8EE, 0xCEE, 0xEEE, 0x0AE],
]
SOLAR_CYCLE = [0, 1, 2, 3, 2, 1, 0, 1, 0]
FADE_STEPS = [0.5, 0.8, 1, 1, 1, 1]


def fixed(words, sheet):
    normal = json.loads((ART / sheet).read_text())["colors"]
    return [rgb(w) if w or i < 2 else list(normal[i]) for i, w in enumerate(words)]


def prism(base):
    def at(words):
        return row(base, range(2, 7), words)

    # Gold, white-hot, gold, jade, gold, rose-gold. Every phase holds at least one 7-tick row;
    # white-hot and jade are always separated by gold (photosensitivity rule in design.md).
    # Transition rows are authored, not RGB blends: a blend of gold and white-hot or jade passes
    # through brown/olive, which read as dirt on the plates. White-hot keeps the deep plate gap
    # (slot 2) in the gold family so the dark rims stay without brown mid-tones.
    return [at(w) for w in (
        GOLD[0], GOLD[1], GOLD[2], GOLD_TO_WHITE, WHITE_HOT, WHITE_HOT, GOLD_TO_WHITE,
        GOLD[0], GOLD[1], GOLD_TO_JADE, JADE, GOLD_TO_JADE,
        GOLD[0], GOLD[1], GOLD_TO_ROSE, ROSE, ROSE, GOLD_TO_ROSE)]


def build(path, fmt, sheet, fixed_words, slots, levels, order, note, hyper=False):
    data = json.loads(path.read_text())
    normal = json.loads((ART / sheet).read_text())["colors"]
    base = fixed(fixed_words, sheet)
    first = row(base, slots, levels[order[0]])
    target = [list(c) for c in normal]
    for i in data["changed_indices"]:
        target[i] = first[i]
    data.update(format=fmt, note=note)
    # Native fades step through six rows while the transformation plays: PalCycle_SuperSonic
    # (s2.asm:3119-3145) and SuperHyper_PalCycle_FadeIn (sonic3k.asm:4664-4673). Creator forms
    # front-load them instead (half, then most, then the powered row), and the transformation script
    # holds its fixed-white flash beat across the change, so no still frame of the transformation
    # shows an olive or grey in-between and the plates are already gold when they snap open.
    data["fade_frames"] = [blend(normal, first, step) for step in FADE_STEPS]
    for host in ("s2", "s3k"):
        data["cycles"][host]["frames"] = [row(base, slots, levels[i]) for i in order]
    if hyper:
        data["cycles"]["hyper"] = dict(data["cycles"]["s3k"], frames=prism(base),
                                       native_source_label="Prism Gilt (creator): gold, white-hot, gold, jade, gold, rose-gold",
                                       frame_count=18)
    return data


def main(argv):
    out = {
        PROD / "mica/presentation/gilded.yaml": build(
            PROD / "mica/presentation/gilded.yaml", "openggf-mica-gilded-2", "mica/sheet/palette.yaml",
            MICA_FIXED, range(2, 7), GOLD, GOLD_CYCLE,
            "Open Cone (owner pick 2026-11-11): slots 2-6 are lifted plates, deep amber underside to pale-gold "
            "edge, breathing to a brighter step; face, belly, gloves and coral boots keep their sheet colours. "
            "Plate/silhouette contour ink is slot 2. Hyper uses the Prism Gilt cycle on the same bank.",
            hyper=True),
        PROD / "talon/presentation/solar.yaml": build(
            PROD / "talon/presentation/solar.yaml", "openggf-talon-solar-2", "talon/sheet/palette.yaml",
            TALON_FIXED, range(2, 7), SOLAR, SOLAR_CYCLE,
            "Corona Crest (owner pick 2026-11-11): slot 2 stays dark bronze for chest bars, moustache and "
            "feather breaks; 3-5 white-gold plumage; 6 orange flame tip flickering to gold; fixed amber 14/15 and "
            "red 10 finish every flame. Chest cream 7 and tan 8 belong to the powered view."),
    }
    stale = []
    for path, data in out.items():
        text = json.dumps(data, indent=2) + "\n"
        if "--check" in argv:
            if path.read_text() != text:
                stale.append(path)
        else:
            path.write_text(text)
    if stale:
        print("Stale powered palettes:", *stale)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
