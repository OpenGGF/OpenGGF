# Sonic 1 Object Implementation — ROM Behavioural Pitfalls

Anti-pattern catalogue accumulated from trace replay frontier advancement
fixes. **Read this before starting Phase 2 of `s1-implement-object/SKILL.md`**
and check each entry against the ROM behaviour of the object you're porting.

Each entry is a class of bug where the *naive* engine port diverges from ROM
in a way that survives unit tests but breaks trace replay parity. The
catalogue grows when a trace-replay-bug-fixing iteration commits an object
fix whose root cause could occur in any not-yet-implemented object — see
`.agents/skills/trace-replay-bug-fixing/SKILL.md` Phase 5 for the update
loop.

Format per entry: pattern name, symptom, root cause, what to check during
implementation, ROM citation, originating fix commit.

---

## P1 — CalcSine orbital position uses integer arithmetic shift, not floating-point round

**Symptom.** A satellite or orbiting child object is 1 px off its ROM
position in one or both axes, causing an off-by-one touch contact (premature
hurt, missed bounce) compared to ROM. The discrepancy is only visible at
certain orbit angles.

**Root cause.** ROM `Orb_CircleSpikeball` (and any object that calls
`CalcSine` to drive orbital/circular motion) does:

```asm
jsr CalcSine        ; d0 = sine (-0x100..0x100), d1 = cosine
asr.w #4,d1        ; cosine >> 4  (arithmetic right shift, truncates)
add.w obX(a1),d1   ; parent X + (cosine >> 4)
asr.w #4,d0        ; sine >> 4
add.w obY(a1),d0   ; parent Y + (sine >> 4)
```

Naive engine port uses `(int) Math.round(Math.cos(radians) * 16.0)`.
`CalcSine` sine=254 → `254 >> 4 = 15`; floating-point `Math.round(254/256.0 * 16) = Math.round(15.875) = 16`.
The 1-unit rounding difference places the child 1 px further from the parent
than ROM, which can make touch boxes meet when they should miss (or miss when
they should meet).

**What to check.** Any circular/orbital motion driven by `CalcSine`:
use `TrigLookupTable.sinHex(angle) >> 4` and
`TrigLookupTable.cosHex(angle) >> 4` for the positional components.
Do not use `Math.sin`/`Math.cos` with `Math.round` for ROM-accurate placement.

**ROM citation.** `docs/s1disasm/_incObj/60 Badnik - Orbinaut.asm:181-191`
(`Orb_CircleSpikeball`). `CalcSine` defined at
`docs/s1disasm/_incObj/sub CalcSine.asm`.

**Cross-game note.** S2 and S3K share the same `CalcSine` convention and the
same `asr.w #4` radius convention for circular child objects. Apply the same
`TrigLookupTable.sinHex/cosHex >> 4` pattern for any circular object in those
games.

**Originating commit.** See `bugfix/ai-s1-syz-slz-advance`
(SLZ2 f1016 -> f1493; Orbinaut spike placed 1px low by float round, touching
player touch-box top edge 4 frames before ROM's contact).

---
