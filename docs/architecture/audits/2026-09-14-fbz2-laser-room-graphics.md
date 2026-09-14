# FBZ2 laser-room graphics

Integration base: `5c150c85eb5bba6cbfde09dc45b52f4d1a394b64` (`develop`).
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

Final combined validation and integration remain pending. These local checks do not certify a complete route, all character/donor
combinations, the final boss refresh, or whole-frame emulator parity.
