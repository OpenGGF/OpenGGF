# FBZ2 laser-room graphics

Initial base: `5c150c85eb5bba6cbfde09dc45b52f4d1a394b64` (`develop`).
Integration base: `8fff63a7078ba871f852978d520685c099db7803`.
Implementation: `cd2483bf6`; reconciled candidate: `2e11a08a8`.
Task tree: `.worktrees/fbz2-room-graphics`.

## ROM ownership

`ObjDat_FBZ2Subboss` sets `make_art_tile(ArtTile_FBZ2Subboss,1,1)`.
`CreateChild1_Normal` and `CreateChild3_NormalRepeated` copy the parent's
`art_tile`; `SetUp_ObjAttributes3` changes the sprite-table bucket without
changing that VDP priority bit. `ObjDat3_703BC` independently installs the
high bit for Robotnik/EggRobo. The Java root already reported high priority,
but its visible children inherited `AbstractObjectInstance`'s false default.
Their common FBZ2 subboss shell now retains the native high priority while
preserving each child's independent sprite-table bucket.

`FBZ2SE_Normal` starts `SetUp_FBZ2BossEvent` at camera X `$2B30`, near the
right edge of the laser room. `FBZ2BGE_Normal` then selects background stage
`$10`. `FBZ2BGE_BossEvent` draws logical background terrain on physical Plane A;
`FBZ2SE_BossEvent` draws logical foreground terrain on physical Plane B.
`PlainDeformation_Flipped` and `loc_530F0` route the corresponding horizontal
and vertical scroll words. `FBZ2_CloudDeform` computes background X as
camera X minus `$2600` minus `_unkEE98`, and background Y as camera Y minus
`$300` plus `_unkEE9C` (with the separate screen-shake pipeline).

Previously the Java mode swapped texture sources and vertical scroll without
requesting the foreground horizontal-scroll word. Its background/FBO pass
retained ordinary background-cache geometry after selecting the foreground
texture. The correction uses the existing per-line scroll controls and requests
the full logical background map. No ROM scroll calculation or gameplay state
is adjusted to fit the image.

Reversed-plane composition also needs the VDP order B-low, A-low, B-high,
A-high. A captured render command replays B-high above A-low; the same sampling
path adds B-high to the sprite-occlusion mask. Both paths use the low HScroll
word and rear-plane VScroll, while the front pass uses the high word. This is
gated by semantic plane reversal, not a game/zone test in the shared renderer.

A zone-state restore installs a compact 64-column retained snapshot. Full-map
mode now rejects that undersized cache even if its mode flag did not change;
the next ensure rebuilds the derived world map. The zone-state byte contract
and gameplay restoration are unchanged.

## Measurement limits

Short `GameplayCaptureTool` teleports at `$2BB0/$660`, `$2B40/$6AC`, and
`$2C20/$68C` locate the ordinary room and reversed-plane boundary. They skip
the approach's plane switchers and do not establish full-route parity. The
first two runs show the purple patterned room; the third reaches reversed
mode. Captures live outside the repository under the task's scratch directory.
The supplied locked-on ROM matches SHA-1
`CFBF98C36C776677290A872547AC47C53D2761D6`.

## Focused evidence

All Maven commands use `python3 tools/testing/maven_queue.py -Dmse=off` from
the task tree, with `-Ds3k.rom.path=<absolute verified ROM path>` where needed.

- `-Dtest=TestFbzBossPlaneRenderMode,TestFbzBossPlanePixels,TestFbzAct2Subboss,TestFbz2SubbossRewind,TestFbz2SubbossCharacterArt test`:
  47 tests, two failures, no errors/skips. The child-priority correction was
  compiled, but the plane changes were not yet compiled (`javap` confirmed
  the old mode). The missing horizontal-scroll request failed independently;
  4,369 of 4,370 sampled room-exit pixels differed from the ROM calculation.
- Recompiling the initial plane correction and running the two graphics classes
  passed both tests, no skips.
- Expanding to the full viewport exposed 20/546/6,270 differing pixels at
  widths 400/528/800 before the B-high replay. The 320/352 views were fully
  covered by Plane A in this setup: demanding rear-plane coverage there was
  a test setup error, not an engine defect. The test now requires rear-plane
  coverage in the wide views that actually expose it.
- `-Dtest=TestFbzBossPlanePixels,TestFbzBossPlaneRenderMode,TestLevelRendererBackgroundViewport,TestTilemapGpuRendererPerLineSampling test`:
  20 tests passed, no failures/errors/skips (2026-09-14 19:24 BST). The pixel
  checks cover widths 320/352/400/528/800, initial and independently offset
  terrain, then zone-state reconciliation and a forward tick after restore.
  Every sampled opaque tile matches decoded ROM descriptors, patterns,
  palettes and physical plane priority.

- `-Dtest=TestFbzBossPlanePixels test`: all five viewport cases passed with
  direct sprite-mask texture readback, no failures/errors/skips (19:28 BST).
  The mask matches the union of opaque high-priority pixels from both planes
  before movement, after independent offsets, and after zone-state restore.

An authored approach (`67 R; 25 R+A; 35 R; 180 -`) from `$2930/$66C`
reached the room at `$2BDD/$68C` without dying; Sonic is visible after crossing
the approach plane switcher. This short capture does not complete the fight.

## Combined validation

`LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base 8fff63a7078ba871f852978d520685c099db7803 --run`
on `2e11a08a8` selected the full ordinary inventory (2,572 classes) and guards.
The completed 2026-09-14 run reported:

- Ordinary: 20,378 tests, zero failures/errors, 18 skips; 641.51 seconds.
- Guards: 667 tests, two failures, zero errors/skips; 176.89 seconds.

The already-completed integration run on the exact destination commit
`8fff63a70` in `.worktrees/kis2-chain-frontier`, using
`LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base 24cdc64e6 --run`,
reported 20,372 ordinary tests with zero failures/errors and 18 skips, plus
667 guards with the same two failures. Both runs' `results.json` were inspected;
failure identities/messages and skip identities/reasons matched exactly:

- `TestBuildToolingGuard#supportedDocumentationMustUseDirectMavenAndExplicitHookBootstrap`:
  stale expectations for direct Maven wording and `<printed-pinned-base>` in
  AGENTS/CLAUDE, conflicting with the existing queue guidance.
- `TestNoAssertionFreeDiagnostics#noAssertionFreeTestMethodsUnderTestsTree`:
  existing `FbzRouteEvidenceProbe#printEvidence` and
  `LevelSolidityMapProbe#writeSolidityMap` have no assertion oracle.

The ordinary skips comprise opt-in diagnostics/route/soak tests, unavailable
EGL/OpenGL checks (`TestForegroundWindowRendering`, `TestShaderPixelCentreSampling`),
a CPZ spin-tube setup assumption, and local audio-reference/capture prerequisites.
The five new FBZ pixel/mask cases and required S3K bootstrap/loading/AIZ checks
executed without skips. No new or worsened failures were observed; the combined
run is not globally green. Independent read-only code review found no significant
issues. Consumed task-run diagnostics were acknowledged and removed.

Upstream HCZ rewind, KiS2, and documentation changes merged without conflicts.
The final follow-up changes only this evidence and the existing pitfall catalogue;
completed engine checks are not repeated for unchanged code under repository policy.
These local checks do not certify a complete route, all character/donor
combinations, the final boss refresh, or whole-frame emulator parity.


## Local object follow-up (2026-09-15)

Normal approach reproduced missing Robotnik/control-panel objects, despite ready
art and high priority. They were already absent on the room's first active frame.
`loc_6FFFA` and `loc_70068` draw attached children without a generic distance unload;
the shared child shell now opts out of that check and retains each child's own
escape/deletion rules. Repeating the identical 308-frame approach on Wayland
retained machine slot 20 and character slot 21 and visibly restored both.
The barrier's alternate-frame drawing remains native behavior.

`FBZ2_CloudDeform` supplies VDP coordinates with the $80 origin bias. Cloud
rendering now removes that bias, uses native $2C/$0C visibility radii, and translates
native frames 1–3 to the filtered cloud sheet's indices 0–2. Cloud unit checks
exercise coordinate and frame translation; the complete moving-terrain event
has not been visually certified by this follow-up.

Related local reports corrected placement flips for disappearing platforms and
screw doors, and the magnetic chain helper's fixed end. `Obj_FBZMagneticPlatform`
sets sub2 to frame 3 at original platform Y+$C; its own Y-$70 is only the fixed
multi-sprite culling anchor, with height $80. Drawing frame 0 there created an
extra platform. The moving links still follow `sub_3B488`.

Validation: queued Maven `-Dmse=off
-Dtest=TestFbzAct2Subboss,TestFbzBossCloudIdentity,TestFbzMagneticObjects,TestFbzDisappearingPlatformAndScrewDoor
-Ds3k.rom.path=<absolute verified ROM path> test` completed on 2026-09-14
at 21:05 BST: 46 tests, zero failures/errors/skips. The same compiled classes
passed the repeated Wayland approach diagnostic. Launch used `WAYLAND_DISPLAY=wayland-0`,
`XDG_RUNTIME_DIR=/run/user/1000`, and no DISPLAY, with captures outside the repo.

The combined selection inspected against destination `a9060373f` selected 2,574
classes, including unrelated local files. Proportionate validation applies:
these changes affect only the named objects' rendering and child lifetime;
no shared algorithm, physics, build policy, or public contract changed. The
focused checks and matched production approach cover the reported failures;
this is not a full-suite or whole-act certification.
