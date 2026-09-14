# Slots capsule glass layering

The requested result is the player behind the central glass. The initial change
`4852b84c9` removed foreground priority promotion, then `3a4d91199` reversed it.
Neither established the requested framebuffer result: the existing regression
bootstrapped the slot runtime without running `GameLoop.updateBonusStageMode`.

## Cause and ownership

`GameLoop.forcePlayerHighPriorityInBonusStage` runs after each bonus physics
frame. It promotes the slot player above the foreground even though the slot
runtime initially sets low priority. Its cited `Obj_GumballMachine` instructions
(`sonic3k.asm`, `bset #7,(Player_1+art_tile)` and Player_2) belong to Gumball.
Slots instead initializes `art_tile` with `make_art_tile(ArtTile_Player_1,0,0)`
in `Obj_Sonic_RotatingSlotBonus` / `loc_4B9E8`.

The coordinator is the narrow owner of this stage-specific presentation choice.
An internal optional player-priority policy lets the game loop preserve a
stage-owned priority without adding game or zone identity checks or changing
the published/candidate Mod API surface. Providers without that policy retain
the previous high-priority enforcement. S3K Slots opts out; Gumball and Glowing
Spheres retain their behavior, including object-owned bucket overrides.

## Verification method

`TestGameLoopBonusPlayerPriority` exercises the same post-frame method for both
players, repeated frames, all three bonus types and an object-owned bucket.
`TestS3kSlotsGlassNative` enters Slots through a real bonus-stage request from
AIZ, steps `GameLoop` to the central machine and compares actual GPU pixels
against forced-front/forced-back renders of the same paused state. It requires
a nonempty occluded overlap, so a test of flags alone cannot pass it. Native
Sonic, Tails and Knuckles and all five supported widths are sampled, not every
character/width combination. Donor/team and rewind visual breadth remain open.

A direct `GameplayCaptureTool --zone 21` cold load was rejected as a reproduction:
its state CSV remained in LEVEL mode with the ordinary player falling through
the zone. Loading the tiles does not activate the bonus coordinator. The native
test uses real entry and reacquires the focused player after the runtime swaps
the playable instance. Captures are gameplay evidence, not a ROM pixel oracle.

## Observed validation

Integration base: `31a9a6bce5a75d04c354f89bfc47050260ca4324`. The baseline
worktree `slots-glass-render` at `599940518` adds only these tests and this
record. The native command below failed all five cases, with no errors or
skips. Wrong overlap pixels in the listed route order were 232, 216, 272,
232 and 232. Frame 210 placed Sonic at `(1120,1072)`, camera `(960,960)`,
in BONUS_STAGE with high priority true.

```sh
python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestS3kSlotsGlassNative \
  -Dopenggf.test.gl.native=true \
  "-Ds3k.rom.path=$S3K_ROM" test -B
```

The implementation worktree `slots-glass-runtime`, based on `599940518`,
passed 372 tests, zero failures/errors/skips, with the following selection:

```sh
python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestGameLoop,TestGameLoopBonusPlayerPriority,TestS3kBonusStageHeadlessBoot,**/bonusstage/slots/Test*,TestS3kSlotsGlassNative,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestArchitecturalSourceGuard' \
  -Dopenggf.test.gl.native=true \
  "-Ds3k.rom.path=$S3K_ROM" test -B
```

`S3K_ROM` was the existing root locked-on ROM's absolute path, with SHA-1
`cfbf98c36c776677290a872547ac47c53d2761d6`.
The five native cases are Sonic/320, Tails/352, Knuckles/400, Sonic/528 and
Sonic/800. The actual framebuffer has zero wrong overlap pixels in each case;
Sonic's matched frame/position now reports high priority false. Before/after
images were inspected. Optional `-Dopenggf.test.slots.captureDir=<task-dir>`
saved them with state CSVs outside the repository under
`<task-capture-root>/slots-glass-20260914/live-before` and `live-after`.
They are production gameplay captures, not trace/ROM image parity.

The change-based plan selects all 2,565 classes because the game-loop/provider
paths are unclassified. Proportionate validation applies: the change gates one
existing priority override through an internal coordinator policy, with no
physics, timing, renderer algorithm or Mod API change. Both players, repeated
overrides, the other bonus types, object-owned priority and real GPU overlap
are covered directly. This is focused validation, not a full-suite pass.
