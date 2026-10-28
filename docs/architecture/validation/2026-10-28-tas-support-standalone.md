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
Implementation and the remaining native execution checks are still in progress.

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
  through the existing checkpoint/load owners.

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

## Validation status

Two build-only `test-compile` commands passed under Java 25, in 1m28s and 1m35s.
They did not execute tests. Further native intro/component edits followed the
second build and remain subject to the queued focused checks.

The first focused request completed Maven with four tests, zero failures,
zero errors and zero skips in `TestSonic3kStandaloneStartup`. These cover
verified SK bounds/identity, SK gameplay-loader construction, real SK menu
readiness/selection and original-S3 title resources/control. Maven reported
`BUILD SUCCESS`, 1m31s, at `2026-10-28T19:06:32Z`. The exact requested selector
was `TestSonic3kStandaloneStartup,TestTasS3kTitleCapabilities#currentLimitationStandaloneKnucklesTitleStartStillRoutesToLockedOnSaveMenu`;
the historical second method was replaced by the positive
`nativeStandaloneKnucklesTitleUsesMhzWhileLockedOnKeepsSaveGrid` and therefore
was not covered by this invocation. Its replacement is selected separately in
the native EGL request.

The source was HEAD `95362786c` plus the then-current uncommitted candidate.
The four tested title/bootstrap paths were unchanged by the subsequent intro
object/component corrections. This is candidate evidence, not baseline red.
Queue telemetry recorded completed execution, wait 3086.3s, hold 92.3s,
peak RSS 2.664 GiB and mean 1.83 cores. Wait plus hold is 3178.6s; total command
wall time including unmeasured startup/cleanup is unknown. The outer tool session
returned 143 despite Maven success and completed telemetry. That discrepancy
remains unresolved: these are bounded observed test results, not a clean outer
command exit. The parent explicitly accepted that distinction and instructed
that unchanged coverage not be repeated merely to erase the note.

The native EGL and precise SK-intro component requests remain queued. Exact
executed commands, identities, counts, failures/skips, source state and timing
will be added after completion. No full release suite is requested.

All Maven commands use `JAVA_HOME=/usr/lib/jvm/java-25-openjdk`,
`LUA_BIN=/usr/bin/lua5.4`, `-Dmse=off`, `maven_queue.py`, unset DISPLAY and
WAYLAND_DISPLAY, and absolute original ROM properties. EGL qualification also
passes `-Dopenggf.headless.gl=surfaceless-egl`; it does not fall back to a desktop.

Citation checks use the immutable SK pin. A bounded comparison of the same
owned files at the assigned base and candidate found 14 inherited stale anchors
and no new hard errors or stale anchors. Newly added/changed citations classified
as 10 valid, 2 near and 2 unanchored-but-in-range; native labels/ranges were also
read at the pin. The unrelated dirty S1/S2 references remain preserved.

Intended focused qualification covers real cartridge bounds/identity, title
selection and pixels, actual MHZ world/player/object updates, Start from each
controller, neutral natural intro completion, capture/restore-forward, startup
progress reset and no-save identity, ordinary Sonic and locked-on controls,
and the existing S2 options launch consumer. The actual Knuckles launch also
checks that real title iterations accumulate a nonzero visible semantic clock,
that the new ObjectManager inherits it, and that the admitted native intro
seeds RNG from that update count. It uses the production clock lifecycle; no
SK-specific clock reset or fitted seed is added. The parent owns broad smoke and
the four mandatory S3K gates.

## Bounds

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
