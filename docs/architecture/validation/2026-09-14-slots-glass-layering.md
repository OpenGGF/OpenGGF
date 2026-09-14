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

Validation commands and observed results are recorded here after execution.
