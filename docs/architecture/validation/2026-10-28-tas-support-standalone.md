# Standalone cartridge startup qualification — 2026-10-28

## Ownership and candidate

This stream owns `s3k-standalone:1`: original Sonic & Knuckles title selection
through a real no-save Knuckles MHZ1 world. It also owns the original Sonic 3
title resource profile needed by the separate Competition stream. The parent
owns final module composition, central classification/discrepancies, combined
validation and delivery. This record does not certify either complete campaign.

Assigned base: `8945c0a789b27be57b2361c70f561f31872a76ca`.
Workspace: `${TASK_WORKTREE}` (the assigned isolated standalone tree).
Branch: `feature/ai-tas-support-standalone-20261028`.

The separable module-lived title selection API is commit
`987de58c20ce339101e6e5bdc054b2589fb6bc90`. The exact authorized audio identity
dependency `30fb4fae0d684b274df14f566c36275ff2c2e8b7` is merged by
`95362786c`; audio loader/profile binding remains with the audio worker.
The qualified source sequence is `ee66cbf0aa1e767a5711f1dcf0306cfa11d955b2`,
`ad6ab2f157dd3d22cc24c1c893f374d0c24bc34b`,
`c665e08ca95a83abceb0ddc937e3a2d5da7c5dd9`, then
`b4a9267613d4b84e0dac335f71ee04237d155ef5`. The last change affects title
label draw order only; affected draw consumers passed on that exact source.
The parent owns stock module composition and its final receiving validation.

## Historical evidence and current audit

The historical report's `s3k-standalone:1` was unsupported at engine base
`c69447d77baddd8d3f7e4a177d73c8d327dd223d`, evidence commit
`964c29d25fdab09fe8edd4acbe541a27fc64904c`. Its reached test was
`TestTasS3kTitleCapabilities#currentLimitationStandaloneKnucklesTitleStartStillRoutesToLockedOnSaveMenu`:
a mocked standalone header selected a stock module, but ONE_PLAYER resolved
DATA_SELECT; a separate TWO_PLAYER request established that routing ran.
That negative evidence is retained. The original report remains unchanged.

At the assigned base, current source still used only the locked-on title
resources, validated AIZ/S3-half resource pointers when constructing an SK
loader, and defaulted the MHZ intro object's native `SK_alone_flag` to false.
The component's natural falling motion and final landing animation callback
were also absent; tests could supply helper signals. These are actual owner
gaps, independently established from the pinned routines below. An early
request intended to demonstrate a fresh red baseline remained queued while
implementation proceeded under the parent's explicit instruction. It must be
reported only against the candidate it actually compiles, never as an executed
unchanged baseline red.

## Original bytes and native expectations

`ROM_ROOT` denotes the supplied original-ROM directory, with its absolute
local path recorded in the parent handoff. `TASK_WORKTREE` denotes this assigned
isolated worktree; no main-workspace operation is part of this stream.

No original standalone file was present in the supplied root inventory. Both
existing original `s3k.gen` and `kis2.gen` contain the exact original SK
cartridge at physical offset zero, length `0x200000`. The immutable reader
window serves those actual bytes; no ROM was copied, linked, synthesized or
extended with Sonic 3 data. Reads at/above `0x200000` fail.

| Logical view | Original physical file/window | CRC32 | SHA-1 |
| --- | --- | --- | --- |
| SK | `${ROM_ROOT}/s3k.gen`, `0..0x1FFFFF` | `0658F691` | `88D6499D874DCB5721FF58D76FE1B9AF811192E3` |
| S3 | same file, `0x200000..0x3FFFFF` | `9BC192CE` | `75E9C4705259D84112B3E697A6C00A0813D47D71` |
| Locked-on control | same complete original file | `63522553` | `CFBF98C36C776677290A872547AC47C53D2761D6` |

The immutable disassembly pin is
`8b2cf3c4178f2382f844da67798de4befc50c5e0`. The inherited dirty S1/S2
disassemblies were preserved; they do not select these expectations.

- `Title_Screen` (sonic3k.asm:5424-5481) clears active level/camera/object state,
  checkpoint and transient mode flags, then branches on `SK_alone_flag`.
- `SK_Alone_Title_Screen` and `loc_539A` (sonic3k.asm:6382-6650) implement the
  cartridge's own title. Start selects `Player_option = 1 + 2*Title_screen_option`,
  Sonic or Knuckles, sets lives to 3, clears score/rings/timers/continues,
  initializes the next-life threshold, clears special-stage/emerald/ring
  inventories and save pointer, and enters `$0700` without a save grid.
- `Obj_SKTitle_Icon` (sonic3k.asm:7126-7190) normalizes the retained title option
  with `&1` and owns the Up/Down selection and rotation gate. A warm title/reset
  retains this explicit module-lived byte; cold/new state is zero.
- `MHZ1_ScreenInit` and `sub_54B80` (sonic3k.asm:112215-112305) choose native SK
  entry camera coordinates and min-X `$680`. Fresh Knuckles starts the camera
  at `$560,$948`; Sonic uses the separate `$718,$62D` player and `$680,$5CD`
  camera start. The checkpoint-one Knuckles reload overrides camera X only.
- `CutsceneKnux_SKIntro`, `loc_634CA`, `loc_6358E`, `loc_635FC` and
  `Obj_SkipIntro` (sonic3k.asm:130611-130805) admit only native SK Knuckles with
  no checkpoint; own control, native `+8` falling acceleration, the ROM raw
  landing callback, and P1/P2 Start. Restart saves checkpoint 1 at `$6F4,$9EC`
  through the existing checkpoint/restart owners. `Restart_level_flag` uses
  the existing restart request, retaining the checkpoint bank; a fresh zone
  launch would clear it. The engine's absent-checkpoint sentinel is `-1`, so
  fresh-entry predicates use `<=0`, matching native Last_star_post_hit being
  clear without changing the engine's checkpoint representation.
- `loc_68C2` (sonic3k.asm:8212-8216) admits the MHZ carry-in Tails only with
  SK_alone_flag clear. Original SK Sonic starts on foot and never reads absent
  original-S3 Tails art for this entry.
- `loc_622A` (sonic3k.asm:7720-7730) selects Knuckles music `$1F` for a fresh
  original-SK Knuckles MHZ1 entry and ordinary MHZ music `$0F` at checkpoint 1.

Core-table validation samples the cartridge's own MHZ1 entry, rather than
treating absent AIZ bytes as bad global offsets. AIZ's art/layout/collision
pointers are `$3A566A/$281420/$274761`, outside SK. MHZ's corresponding pointers
are `$19ABF2/$09F3B6/$0987C0`, within SK. Locked-on validation/scanner behavior
and global address constants remain unchanged.

Title assets were matched to original ROM bytes against the pin. SK's native
background, standing art, final plane map, menu art and palette are at
`$CCA20/$CD910/$CC1C4/$D5CC6/$5588`; the bomb uses `$165000`, mappings `$67412`
and palette `$66A32`. All are in the original cartridge. Original S3 animation
art/maps happen to match the upper lock-on window, but code-section data has
independent addresses: menu `$4362`, transition palette `$3C78`, Sonic palette
`$3CE8`, final palette `$3E88`, water cycle `$3FD4`. Its profile omits the
S&K-only overlay and requests semantic S3 title music `$125`; SK/locked-on use
their native `$25`. No global address subtraction selects gameplay resources.
Original-S3 palette ordering follows `Pal_Title` / `Pal_TitleSonicD`
(s3.asm:5327-5368); its menu uses `ArtNem_TitleScreenText`
(s3.asm:5672-5680).

## Integration contract

`Sonic3kTitleScreenManager.nativeTitleState()` exposes `Sonic3kTitleState` with
`option()`, `setOption(int)`, `capture()` and `apply(Snapshot)`. The snapshot is
a value. The title owns this policy for the module lifetime; no retired world
or old-world supplier is retained.

The parent's module composition must suppress both `getDataSelectProvider()`
and `getDataSelectPresentationProvider()` only when
`Sonic3kCartridgePolicy.isStandaloneSk()` is true. Its native mode provider's
independent single-player exit supplier is
`titleScreenProvider::standaloneSinglePlayerExit`. The generic native exit now
contains nonnegative `zone`/`act`; its existing three-argument constructor still
defaults to `0/0`, preserving S2 options launches.

The parent's existing ending input overlay composes
`Sonic3kStandaloneStartup.handleIntroInput(InputHandler)`. This looks up the
current world's live admitted intro each call, consumes Start ownership, and
latches P1/P2 Start on that object. The object performs the checkpoint/restart
on its own update. No module captures an old world's intro.

`TestSonic3kStandaloneNativeLaunch` supplies only these pending module wiring
hunks through `DelegatingGameModule`; its actual title, input, GameLoop, world,
resource, object, rewind and production drawing owners run. It never injects an
exit action, gameplay observation, player position or helper completion signal.
Final stock-module registration still requires the parent's integration check.

## Validation results and exact selection

All executed ROM selectors had zero skips. Two build-only `test-compile`
commands passed (1m28s and 1m35s) before the later corrections; these executed
no tests. Subsequent queued checks compiled their actual candidate source.

| Run | Tested source | Tests / failures / errors / skips | Maven time | Outer exit | Finished UTC |
| --- | --- | --- | --- | --- | --- |
| A, initial logical request | `95362786c` plus then-uncommitted candidate | 4 / 0 / 0 / 0 | 1m31s | 143, unresolved | 19:06:32 |
| B, native + consumers | `ee66cbf0a` | 19 / 3 / 1 / 0 | 1m40s | 1 | 19:45:22 |
| C, exact intro components | `ee66cbf0a` | 17 / 0 / 0 / 0 | 32.328s | 0 | 19:45:55 |
| D, affected receiving class | unchanged `ee66cbf0a` | 5 / 3 / 1 / 0 | 33.897s | 1 | 19:49:40 |
| E, checkpoint/carry/recreation repair | `ad6ab2f15` | 5 / 2 / 1 / 0 | 1m39s | 1 | 19:55:30 |
| F, restart/link/output repair | `c665e08ca` | 6 / 0 / 0 / 0 | 1m25s | 0 | 20:01:01 |
| G, affected final draw consumers | `b4a926761` | 3 / 0 / 0 / 0 | 1m40s | 0 | 20:05:03 |

A observed four tests in `TestSonic3kStandaloneStartup`: verified original
SK identity/bounds, actual SK loader construction, native SK menu selection,
and original-S3 palette/resources/selection. Its historical method selector
matched no method after replacement; that method was not executed, rather
than counted as a JUnit skip. The request intended as a baseline remained
queued during implementation, so this is candidate evidence. Its whole
uncommitted fingerprint is not recoverable as a commit. Maven reported
success and queue telemetry recorded completed execution, but the outer tool
session returned 143. This remains an unresolved, non-clean command result;
the parent accepted the bounded four-test observation and instructed that
unchanged coverage not be repeated to erase that note.

B's 14 passing consumers covered the standalone capability with its real
locked-on save-grid control, title bootstrap/frame-load/Sega timing and fade,
title state/generic Exit coordinates, and real S2 options launch. The two
Knuckles receiving failures expected music `$1F` but got `$0F`; the new fresh
predicate incorrectly excluded the engine's `-1` checkpoint sentinel. The
natural test's `==0` loop also stopped before executing any route, a fixture
entry mistake. Sonic's error reached locked-on carry-in Tails and an absent
`$3200E0` art read. These were corrected by semantic checkpoint admission and
the native cartridge carry gate, without restoring a historical defect.

D was submitted while diagnosing those failures and admitted before the
planned edits. It compiled unchanged `ee66cbf0a` and repeated B's four reached
receiving failures; edits began at 19:49:47, after its 19:49:40 completion.
It supplies no additional claim of corrected behavior.

E passed actual Sonic entry/control/render and original-S3 real Start/fade
callback. The stronger bomb-retirement witness exposed a null recreated
owner; the two Start routes also reached a fresh-zone request instead of a
checkpoint restart. The bomb now captures its managed owner slot and relinks
only after restore settles. Completion requests the existing checkpoint
restart owner. No shared rewind policy or global reset seam changed.

F passed all five native receiving invocations and the changed exact restart
component method. It establishes actual title selection, GameLoop/world
replacement, native no-save MHZ `$0700`, lives/score/continues/emerald/special
stage/special-ring initialization, semantic music selection, P1-only title
Start, P1/P2 intro Start, checkpoint reload without life loss, released controls
and ordinary movement. Native title iterations accumulated a nonzero semantic
clock; the new ObjectManager retained it and the admitted intro seeded RNG
from its update count. No fitted seed or SK-specific clock patch was used.

The neutral route naturally allocates and retires its bomb, restores that
retired bomb and owner, replays forward, executes the `+8` fall and actual
ROM landing callback, then reaches checkpoint 1. It uses real terrain and no
manual completion signal. Both Start cases also capture/restore the live
intro and compare complete world snapshots, forward snapshots and restored
production pixels. Component C's other 16 unchanged routine controls remain
applicable; F reran the changed restart component explicitly.

Production EGL title/level drawing produced four inspected PNGs under
`${ARTIFACT_ROOT}/captures`: `sk-title-knuckles.png`,
`sk-mhz-knuckles-intro.png`, `sk-mhz-knuckles-playable.png`, and
`s3-title-two-player.png`. Inspection found the missing Knuckles hand blocks
and incorrect selected label/portrait frames; the native ROM blocks, paired
labels and ROM portrait script now supply them. G passed the three affected
SK render/launch invocations after the final native label overlap-order fix.
Other green consumers were reused because their relevant source and coverage
were unchanged. These are bounded focused results, not a full-suite pass.

The commands below share this exact queue/environment prefix. `ROM_ROOT` is
the supplied absolute original-ROM root; `ARTIFACT_ROOT` is the task-owned
external standalone artifact directory. Absolute local values are in the
parent handoff, rather than machine-specific paths in repository prose.

```bash
q() {
  env -u DISPLAY -u WAYLAND_DISPLAY \
    JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
    python3 tools/testing/maven_queue.py -Dmse=off "$@"
}
q test-compile # two build-only invocations; no tests

# A
q -Ds3k.rom.path="${ROM_ROOT}/s3k.gen" \
  '-Dtest=TestSonic3kStandaloneStartup,TestTasS3kTitleCapabilities#currentLimitationStandaloneKnucklesTitleStartStillRoutesToLockedOnSaveMenu' test

# B
q -Dopenggf.headless.gl=surfaceless-egl \
  -Ds3k.rom.path="${ROM_ROOT}/s3k.gen" -Dsonic2.rom.path="${ROM_ROOT}/s2.gen" \
  -Dtas.standalone.capture.dir="${ARTIFACT_ROOT}/captures" \
  '-Dtest=TestSonic3kStandaloneNativeLaunch,TestTasS3kTitleCapabilities#nativeStandaloneKnucklesTitleUsesMhzWhileLockedOnKeepsSaveGrid,TestSonic3kTitleState,TestSonic3kTitleScreenBootstrap,TestSonic3kTitleScreenSegaTiming,TestSonic3kTitleScreenSegaFade,TestSonic3kTitleScreenFrameLoadTiming,TestSonic2NativeMenuLaunch' test

# C (wildcard is only the exact SK-intro method group, not a full suite)
q -Ds3k.rom.path="${ROM_ROOT}/s3k.gen" '-Dtest=TestMhz1CutsceneObjects#standaloneSkIntro*+skIntro*' test

# D, then E after the repair, each once
q -Dopenggf.headless.gl=surfaceless-egl -Ds3k.rom.path="${ROM_ROOT}/s3k.gen" \
  -Dtas.standalone.capture.dir="${ARTIFACT_ROOT}/captures" \
  '-Dtest=TestSonic3kStandaloneNativeLaunch' test

# F
q -Dopenggf.headless.gl=surfaceless-egl -Ds3k.rom.path="${ROM_ROOT}/s3k.gen" \
  -Dtas.standalone.capture.dir="${ARTIFACT_ROOT}/captures" \
  '-Dtest=TestSonic3kStandaloneNativeLaunch,TestMhz1CutsceneObjects#skIntroCompletionSavesRestartPointAndQueuesMhzReload' test

# G
q -Dopenggf.headless.gl=surfaceless-egl -Ds3k.rom.path="${ROM_ROOT}/s3k.gen" \
  -Dtas.standalone.capture.dir="${ARTIFACT_ROOT}/captures" \
  '-Dtest=TestSonic3kStandaloneNativeLaunch#nativeKnucklesTitleLaunchClearsProgressReplaysIntroAndAcceptsEitherStart+nativeSonicTitleSelectionUsesStandaloneMhzStartWithoutKnucklesIntro' test
```

| Run | Measured queue wait seconds | Measured lease hold seconds |
| --- | --- | --- |
| A | 3086.3 | 92.3 |
| B | 2557.7 | 101.5 |
| C | 2302.6 | 33.1 |
| D | 25.3 | 34.6 |
| E | 161.0 | 100.5 |
| F | 47.9 | 86.3 |
| G | 82.1 | 101.6 |

All telemetry outcomes were completed. Queue waits overlap and are not summed
as elapsed delivery time. Total command wall including unmeasured startup and
cleanup is unknown. No admission limits, scheduler policy or unrelated jobs
were changed; no request was cancelled/restarted to escape waiting.

The standard citation parser checked the 24 owned files against immutable
SK pin `8b2cf3c4178f2382f844da67798de4befc50c5e0`, comparing the same
files at the assigned base. New/changed citations: 13 valid, 4 near,
2 unanchored-but-in-range, zero new hard errors or stale anchors. The 18
inherited stale anchors (including four in the newly scoped event manager)
and unrelated dirty S1/S2 references were preserved. Owning native ranges and
actual cartridge bytes were read independently. Consumed Maven diagnostics
are removed after recording; no pass cache or validation receipts are kept.

The worker's owned tree is committed. No tests or native probes remain live.
The branch/worktree are retained for the parent. The ownership registry
expires 2026-10-29T18:07:50Z; the parent owns registration/cleanup. No main-workspace
operation, fetch/pull, push or delivery integration was performed by this worker.

## Bounds

The representative `s3k-standalone:1` engine route is qualified through the
worker's explicit pending-module composition seam. The parent owns final
stock service registration, stock receiving checks, central classification,
broad smoke and the four mandatory S3K gates. Its integrated classification
must use those receiving results; this record does not certify stock binding
from a decorated test alone. Audio source/profile binding and audible output
remain with the audio stream; these checks select semantic music IDs with a
no-device backend.

The representative startup route is native 320-pixel, no donor, original SK
Sonic/Knuckles with no sidekick. Tails is not a native SK title option.
Publication movies, every cartridge revision, donor/team/viewport products,
later acts, the entire standalone campaign, exact title frame timing and full
native-frame pixel parity are not certified by loading or nonblank rendering.
Original S3's title profile is a prerequisite for the separate competition
host qualification; it does not certify original-S3 one-player or save-grid
campaign behavior. Existing MHZ coverage outside this startup route is inherited.

Trace/capture observations remain comparison-only. No CPU/cycle, VDP/status,
hardware bus protocol, arbitrary RAM-corruption compatibility, frame-keyed
route correction or observed gameplay-state hydration is introduced.


## Parent integration scope

The later [composed receiving record](2026-10-28-tas-engine-support.md) uses stock
module/audio bindings, including recorded P1/P2 Start, rather than the worker's
explicit pending-module decorator. Earlier commands and their source identity
remain historical evidence of the worker stage.

| Cartridge / canonical route | Reached local obligations | Open obligations |
| --- | --- | --- |
| Original SK / Knuckles / MHZ Act 1 (`$0700`) | Real title selection, new no-save progress, native intro/clock/RNG, natural terrain/fall/ROM-animation completion, captured bomb retirement/recreation, checkpoint restart without life debit, released movement and production pixels. | Complete MHZ/campaign traversal, other viewport/donor/team/revision axes and native frame/pixel conformance. |
| Original SK / Sonic / MHZ Act 1 (`$0700`) | Real title/level entry, standalone no-Tails carry control and short ordinary movement/rendering. | Full act/campaign, alternate lifecycle and configuration products. |
| Original S3 / title to Competition | Actual original resource presentation and Start/fade action, with the separate Competition host matrix. | Original-S3 single-player campaign and synchronized movie qualification. |

All three are bounded startup/host increments at native 320 with no donor.
They add to the coverage backlog and do not replace existing locked-on MHZ
matrices or repair their unrelated route frontiers.
