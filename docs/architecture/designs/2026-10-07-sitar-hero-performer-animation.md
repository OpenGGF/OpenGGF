# Sitar Hero native performer articulation — 2026-10-07

Implementation workstream based on parent contract commit `cabd66f442af65e62711a52eaad7a1cbbbe4e4a3`,
with main `develop` originally at `09282b173`. Local branch:
`feature/ai-sitar-hero-performers`; worktree: `.worktrees/ai-sitar-hero-performers`.
The parent owns scene wiring, release prose, combined validation, merge/push and worktree cleanup.

## Runtime contract

[PerformerArt](../../../examples/sitar-hero/src/main/java/sitarhero/PerformerArt.java)
retains `draw(SceneCanvas, int x, int y, long ticks, String instrument, boolean playing)`
and adds exactly this package-local hook:

```java
void notePlayed(sitarhero.model.Role role, int lanes, long ticks)
```

Call the hook once for each **judged successful note-on**, including HOPO, chords,
autoplay judgments and drum hits. Supply that note's five-bit lane mask and the
same 60 Hz scene tick domain used by `draw`, not music samples or raw button edges.
Sustain scoring and missed notes do not call it. This workstream deliberately does
not edit `SitarScene`, highway/UI, models, catalogue, controls, audio or shared engine APIs.

`playing=false` retains the selected prop and clears pending gestures/effects;
the parent must pass false on paused and inactive displays. Resume cannot replay
a pre-pause strike. Both drawing and hit admission clear stale state on a backwards
tick. There is quiet one-pixel breathing and Tails' native standing-tail swish;
neither creates instrument attacks. An active display without hits stays idle.

[PerformerMotion](../../../examples/sitar-hero/src/main/java/sitarhero/PerformerMotion.java)
stores only bounded cosmetic note-on envelopes per role/lane. Preparation, contact,
rebound and settling finish within 12 ticks; sparse sound arcs expire at 18 ticks.
Drum bits 0/2 drive the left pad, bits 1/3 the right pad and bit 4 the pedal/native
foot when available. Synth keys light independently, with separate low/high hand
attacks. Sitar/harp use short native hand attacks. Sustaining a note never
restarts the attack or emits repeated waves.

## Native pixels and layer ownership

[PerformerRig](../../../examples/sitar-hero/src/main/java/sitarhero/PerformerRig.java)
contains only reference polygons and native mapping origins/pivots.
[PerformerCutout](../../../examples/sitar-hero/src/main/java/sitarhero/PerformerCutout.java)
partitions the **owning mapping frame**, preserving its untouched pixels, then
composes backing layers. Every arm/hand/foot pixel comes from `SceneRomArt`.
Nearest-neighbour rotations are cached; there is no generic painted humanoid arm,
interpolated limb palette, runtime disassembly fallback or committed raster/ROM asset.

Drawing order is native tail/backing/body, native foot, selected instrument and
key accents, foreground native arm/hand, then small transient arcs/pedal accents.
Feet remain behind the instrument body; placing them after it made shoes appear
on drum shells and keybeds. The harp has shorter near strings within the native
hands' reach and a tall far pillar that leaves faces readable. The sitar uses a
stepped gourd and long stringed neck. Props are the mod's original/code-drawn art.

The all-ROM roster uses these measured poses and extracted opaque pixel counts:

| Performer / donor | Native pose | Arms/hands | Native foot |
| --- | --- | --- | --- |
| Sonic / S3K | Waiting frame `$BA` (186) | 59 / 35 | 61 |
| Tails / S3K | Waiting frame `$AD` (173), separate tail `$22..$26` | 23 / 23 | 49 |
| Knuckles / S3K | Waiting frame `$56` (86) | 77 / 32 | 36 |
| Robotnik / S3K | Eggmobile frame 5 plus occupant frame 2 at `(0,-8)` | 32 / 38 sleeve pixels | No exposed foot |
| Silver Sonic / S2 | Frontal mapping frame 5 | 141 / 141 | 87 |
| Mecha Sonic / S3K | Frontal mapping frame `$0A` (10) | 114 / 114 | 54 |
| Egg Robo / S3K | Body/head frame 1 plus legs frame 4 at `(-12,16)` | 220, one visible forearm/glove | 17 |

Silver/Mecha's frontal frames supply distinct mechanical limbs. Sonic, Tails and
Knuckles retain their own gloves, forearms and palettes rather than sharing a cutout.
Egg Robo frame 1 already includes its head; frame 2 is its gun/hand assembly,
not another head. The selected instrument replaces that weapon. Its one exposed
arm makes a limited native wrist/forearm attack, leaning in opposite directions
for left/right drum hits; pad arcs/key accents distinguish targets. A second
invented arm would misrepresent its source anatomy. Leg placement was reduced
from the inherited 28-pixel offset to 16 to attach beneath the torso and fit the stage.

Robotnik's S3K standing occupant frame 0 exposes no useful separable hands. Native
raised-sleeve frame 2 is the concrete alternate: shoulders pivot down toward the
prop with a small per-hit rebound. This is sleeve articulation, not a claim of two
surgically isolated visible palms. The eggmobile has no foot; kick is shown by the
pedal and its ring. Props sit below the face rather than covering the moustache.

Additional available donors were rendered in every role: Sonic S1 waiting frame 1
(48/22/50 arm/arm/foot pixels), Sonic S2 frame 1 (54/22/45), Tails S2 frame 1
(23/23/49), Robotnik S1 FZ frame 0 (27/72/62) and Robotnik S2 three-bank WFZ
frame 0 (104/41 single glove/foot). The S2 composite exposes one glove; it uses
limited motion instead of importing S1 hands or duplicating a barrel-shaped part.

## Rejected approaches and correction evidence

* Whole-sprite waiting animation plus a foreground prop hid native hands and could
  imply periodic playing without a hit. Fixed native poses with extracted foreground
  parts replace that behavior; envelopes require the hit hook.
* Masking after composition passed total-pixel reconstruction but was anatomically
  wrong: Robotnik's masks included 24 eggmobile glass pixels (54/40 before, 32/38
  after ownership correction); Egg Robo's arm included 40 leg pixels (260 before,
  220 after). Masking each owner first keeps backing glass/legs in the body. A
  contrasting glass/sleeve regression explicitly checks this distinction.
* Foreground shoes obscured shells/keybeds; feet now draw before props. Harp strings
  initially sat beyond the native hand; final contact captures show the shorter
  near strings at the hand and the far pillar clear of the face.
* The first synthetic reconstruction check caught transparent empty limbs extending
  composition bounds. Empty cutouts now retain the appropriate pivot without
  adding visible pixels or changing reconstruction bounds.

## Verification and review artifacts

Java was `21.0.12.1`. Root ROM CRC32/SHA-1 matched the repository's S1 REV01,
S2 REV01 and locked-on S3K identities (`AFE05EEE`, `7B905383`, `63522553`). All
ROM inputs used existing absolute `$OPENGGF_ROOT/{s1,s2,s3k}.gen`
paths; no ROMs or ROM links were created for validation.

The change-based plan at base `cabd66f44` selects 3,007 ordinary classes plus all
guards because `examples/` is unclassified. A full engine run would cost roughly
24 minutes ordinary plus 10 minutes guards. Proportionate focused validation applies:
these are external example art/state helpers with no engine algorithm, API, chart,
clock, scoring or selection-policy changes. This is **focused validation**, not a
full engine-suite pass. The parent owns combined integration checks.

Completed commands in this worktree, expressed with `OPENGGF_ROOT` for the
resolved absolute main-checkout path and `SITAR_PERFORMER_CAPTURE_DIR` for the
authorized external performer capture directory:

```bash
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base cabd66f44 --preflight
python3 tools/testing/maven_queue.py -B -q -Dmse=off \
  -Dtest=TestSitarHeroPerformers,TestSitarHeroArcade test
python3 tools/testing/maven_queue.py -B -q -Dmse=off \
  -Dsonic1.rom.path=$OPENGGF_ROOT/s1.gen \
  -Dsonic2.rom.path=$OPENGGF_ROOT/s2.gen \
  -Ds3k.rom.path=$OPENGGF_ROOT/s3k.gen \
  -Dtest=TestSitarHeroModel,TestSitarHeroControls,TestSitarHeroCharts,TestSitarHeroCareer test
python3 tools/testing/maven_queue.py -B -q -Dmse=off -Dtest=TestSitarHeroPerformers test
python3 examples/sitar-hero/build.py --skip-engine
```

The six JUnit classes comprise 44 cases (1 performer case running seven external
checks, 11 arcade, 10 model, 5 controls, 5 charts, 12 career): zero failures/errors/skips.
The final performer check and package were repeated after the final extraction
change. `ggfmod package` validated the real example, and `git diff --check` passed.
Tool preflight passed with explicit Lua 5.4; the default Lua was the wrong version.
The existing measurement-hazard table was read before interpreting results.

All captured assets stay outside Git in:
`$SITAR_PERFORMER_CAPTURE_DIR/`.

* `contact-sheet.png`: all seven performers × four roles, nearest-neighbour 2×
  stage crops. `native-scene-contact-sheet.png`: corresponding full 400×224 scenes
  at their native scale, including the 76-pixel stage panel and actor origin `(355,133)`.
* `phases-{sitar,bongos,synth,harp}.png`: idle, contact, sustain, paused and resumed-idle
  for every actor. `shots/` retains native full-scene phase/contact/rebound PNGs.
* `{sitar,bongos,synth,harp}-animation.mp4`: four seven-actor clips, 864×504, 210
  logical frames at 30 fps (seven seconds, deliberately half speed for review).
* `donors/`: all four roles for the additional S1/S2 poses above.

The temporary `probe/PerformerCapture.java` runs `ExampleModHarness` and
`HeadlessGameBoot` against the actual display/GL renderer. Its capture-only scene
uses the production art pipeline and this worktree's performer sources. Each of
the 28 combinations completed 210 logical frames: idle 0–59, hit hooks 60/78/96,
no new hits 114–147, another hook at 148, pause 150–179, idle resume 180–189 and
a new hook at 190. Every native cutout is nonempty; composing native body plus
untransformed cuts exactly reconstructs the chosen source pose in all 48 donor/role
checks. All 28 idle/pause stage crops are pixel-identical at matched breathing/tail
phases (40 versus 160). No mod fault findings occurred. The temporary source and
`probe/build_evidence.py` remain in the external task directory for reproduction.

This specifically proves **hook-driven presentation**; it does not claim that the
unchanged scene already forwards its judgments. The existing `SitarHeroCapture`
also passed production-ROM roster/roles, four performances, pause/resume, finite
results/retry and settings acceptance. Its scene captures cover readable stage,
control hints and existing gameplay behavior; scene hit-hook wiring is still the
parent's integration obligation.

The final production acceptance visit used the current worktree's compiled engine,
test classes and generated dependency classpath:

```bash
java -cp "$performerCaptureClasspath" com.openggf.tools.SitarHeroCapture \
  --rom-root "$OPENGGF_ROOT" \
  --mod examples/sitar-hero \
  --out $SITAR_PERFORMER_CAPTURE_DIR/production-final \
  --subset all
```

`performerCaptureClasspath` comprises this worktree's `target/classes`,
`target/test-classes` and `target/examples-classpath.txt`. The final visit exited
0 with `VISUAL ACCEPTANCE: all`; snapshots are in `production-final/all/`.
Run capture after Maven has finished compiling this worktree: one premature
launch during test recompilation failed to find the capture class, without
running acceptance. No capture failure was counted as a passing check.
