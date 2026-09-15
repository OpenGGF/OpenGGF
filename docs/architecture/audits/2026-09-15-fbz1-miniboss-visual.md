# FBZ Act 1 miniboss visual comparison

Base: `dedd18877da190e65aeb74970929e2f2b4ece6c3` (`develop`).
Task tree: `.worktrees/fbz1-miniboss-visual`.

## Setup and observed differences

The user requested an emulator/engine comparison after Sonic stands on the
miniboss's top plunger and opens the capsule. BizHawk 2.11 loaded the earlier
agent's `fresh-fbz1-selection-v3/fbz1-lfc35.State`, produced by ordinary level
selection. The supplied locked-on ROM matches SHA-1
`CFBF98C36C776677290A872547AC47C53D2761D6`.

The local visual setup writes Sonic's centre to `$2EE1/$540` once, advances
601 neutral frames, then places him at `$2F00/$570` above the plunger and
continues neutral input. The engine independently boots FBZ1 and performs the
same two position writes through `NativePositionOps`, with natural camera and
production `GameLoop` execution. No emulator state is imported into the engine.
Both runs reach the depressed-plunger standing centre `$2F00/$5A0` on frame
622. Frame 720 shows the opened capsule and both deployed arms, with camera
`$2E60/$540`. These are local visual fixtures, not a complete-route replay.

Evidence lives outside the repository in
`$FBZ_MINIBOSS_CAPTURE_ROOT/` (the external `fbz1-miniboss-20260915` task directory):
`native-plunger/`, `engine-natural-before/`, and the corrected capture.
The raw BizHawk framebuffer includes a border; the engine output is 320x224.
Their HUD clocks, Tails pose, cloud phase and background history are not an
assertion of whole-frame equality.

A bounded frame-720 observer corroborated all 18 root/child X/Y pairs (root,
three covers, plunger, aimer, two arms and ten links), plus the root/arm timers
and each chain angle/stagger. Signed Java bytes match the native unsigned
words modulo their ROM widths. Thus the apparent small arm difference in raw
screenshots is not evidence of different gameplay positions or a reason to
retune timing. `native-positions/objects.txt` and `engine-positions/objects.txt`
retain the local observation. The screenshots alone do not establish identical
presentation boundaries.

The first `GameplayCaptureTool --x/--y` comparison was rejected as equivalent
setup evidence: its teleport reinitializes level events and executes an extra
object update outside the next frame's palette transaction. This produced an
indoor background and missing one-time boss palette load. A natural-camera
capture restored the expected sky and boss colours without changing gameplay.
Do not patch FBZ palettes or events to fit those setup artifacts.

## ROM-owned corrections

- `loc_6EFF6` installs `ObjDat_FBZSpringPlunger`, whose mapping is
  `Map_FBZEggCapsule`, frame 5, with level-backed `art_tile = 0`. The engine
  incorrectly rendered `Map_FBZMiniboss` frame 8 (eyes) at the plunger position.
  Retain its separate low tile priority and priority bucket 5.
- `loc_6F07C` installs `word_6FA58`, mapping frame 8. The waiting aimer must
  start with neutral eyes; frames 9–16 are selected only by `loc_6F0B8` after
  the opening delay. The previous initial frame was 9.
- `ObjDat_FBZMiniboss` sets the high `art_tile` bit. `CreateChild1_Normal`
  copies it, while `SetUp_ObjAttributes3` preserves it when configuring the
  cover and aimer. Both child classes previously inherited the engine's low
  priority default. Their existing SAT priority bucket 2 remains correct.

These changes select existing ROM-backed artwork and sprite priority. They do
not alter movement, collision, boss clocks, allocation or encounter progression.

## Verification

The final change-based plan (`python3 tools/testing/run_categories.py --base
dedd18877da190e65aeb74970929e2f2b4ece6c3`) selects 2,234 ordinary classes plus
guards, including broad physics/tooling dependencies. Proportionate validation
applies: three object render selections change, with no shared renderer,
physics, queue, state-schema or build-policy changes. Focused object contracts,
existing rewind/art checks, the required four S3K regressions and a real rendered
capture exercise the affected consumers. This is focused validation, not a
full-suite or category-suite pass.

`LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base
dedd18877da190e65aeb74970929e2f2b4ece6c3 --preflight` passed. The first preflight
with ambient Lua failed its version check before executing tests; explicit Lua
5.4 repaired the launch environment. Java is OpenJDK 21.0.11.

On the task tree (base above plus the reviewed rendering diff), the completed
2026-09-15 focused commands were:

```bash
python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestFbzMinibossChildren test
python3 tools/testing/maven_queue.py -Dmse=off "-Ds3k.rom.path=$S3K_ROM_PATH" '-Dtest=TestFbzAct1Miniboss,TestFbzMinibossRewind,TestFbzBossGraphRewind,TestSonic3kObjectArtProvider,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' test
```

- The child class, including both new rendering regressions: 50 tests, zero
  failures/errors/skips, 17.862 seconds Maven time, completed 08:41:24 BST.
- Existing encounter, art, rewind and required S3K checks: 128 tests, zero
  failures/errors/skips, 48.503 seconds Maven time, completed 08:39:08 BST.
  The simple `TestSonic3kLevelLoading` selector includes both existing packages.
- Corrected production rendering: `engine-after/frame-720.png` visibly restores
  the orange plunger. The complete 781-frame player/camera CSV is byte-identical
  before/after, as is the observed frame-720 boss graph. This capture uses the
  compiled task classes and the same local `MinibossCapture.java` setup.
- `git diff --check` and the new relative documentation links passed.

SHA-256 identities of the reviewed stills:

| Picture | SHA-256 |
| --- | --- |
| `native-plunger/frame-720.png` | `2fc2d4fc736ef725d0314c7fac54ea6b0b408ffd6d1823000ea4869b8d6e5ace` |
| `engine-natural-before/frame-720.png` | `e1203c03f49ad4b6921e6aea532ed68e15704721ded82d518e22fcf770419495` |
| `engine-after/frame-720.png` | `1e7b917052e9012ccbd54ddd72ea2aa1400fc10318c39a590f04777c1279e47f` |

Full-act certification, complete-run trace parity and the inherited matrix gaps
remain outside this local rendering correction. Integration verification is
recorded below once complete.
