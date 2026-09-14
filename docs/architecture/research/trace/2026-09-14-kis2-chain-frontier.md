# KiS2 canonical chain frontier

Base: develop `31a9a6bce`; worktree `.worktrees/kis2-chain-frontier`.
The published 36-segment fixture and its approved bytes remain unchanged.
This work follows the EHZ1 production-ownership stop at BK2 cursor 2003.

## Causes and changes

The chain harness prepared the recorded roster but did not reopen the session
through `TraceReplaySessionBootstrap.resolveReplayModule`, as the standalone
harness already did. It therefore ran the KiS2 movie on a stock S2 module.
Both harnesses now share that launch helper. A short regression observes the
first production pass, verifies the KiS2 module and Knuckles, then aborts with
a private sentinel. The interior-prefix helper was rejected for this check:
it supports special-stage interiors and requires a subsequent armed segment;
using it for the initial level produced a harness assertion, not launch evidence.

`TraceReplayBootstrap.resolveS2TitleCardPreludeFrames` also applied a sidekick
requirement to level objects. Native `Level` calls `RunObjects` once after
placement and on each title-card leave-loop iteration, independently of roster
(`docs/kis2disasm/s2.asm`, `Level` before `Level_MainLoop`; stock S2 agrees).
The object prelude now keeps the existing one leading plus 25 leave-loop passes
for solo teams. The separate sidekick prelude still returns zero without a
sidekick. Its new regression failed with actual zero on the unchanged engine.

Native aux identifies the first enemy as Coconuts (`Obj9D`), not Buzzer. It
reaches Y $238, enters throwing on row 155 and becomes explosion $27 on row
156. `Touch_KillEnemy` / `loc_3F844` adds $100 to rising Y velocity, explaining
native row 156's $0010. No Coconuts timing or hit-response code was changed.

The chip's renderer overlays had not replaced the stock PLC queue table. The
KiS2 `ArtLoadCues` table is at **$33A3FC** in the full lock-on address space:
67 relative word offsets; the first offset $0086 reaches `PlrList_Std1` at
$33A482, including the chip life-icon pointer $33AC46. All 67 lists decode
from the verified lock-on image. `PlrList_Std2` sources are $279A86, $279550,
$33B15E and $33AD40; the last is one 66-pattern grey shield/stars stream at
tile $4BE. Queue fingerprints use physical ROM addresses and destination
**tile** indices. The first EHZ waiting job is Coconuts at $27393C. The module
now serves one chip-backed PLC service to production lifecycle and rewind;
tier one retains the stock service. No disassembly assets are runtime inputs.

Native `Knuckles_BeginClimb` / `Knuckles_Gliding_HitWall` first tests terrain
fit, while the engine accepted every glide wall contact. At row 1807 the ROM
rejects the grab and preserves falling velocity; the old engine entered climb
and zeroed it. The shared helper now checks both wall ends, the left-only
one-pixel exact-fit correction, and the ledge probe's unsigned 0..11 distance
range with the live LRB solid bit. S3K's reverse-gravity branch mirrors that
probe and correction. References: KiS2's `Knuckles_BeginClimb` and S3K
`Knuckles_Gliding_HitWall`, including `.checkFloorCommon` and `.fail`.

The glide floor checks now keep tile-flip-transformed angles. Flat glide
landing retains the glide animation ID while writing the slide mapping frame,
as the ROM does; it does not invent an animation-register write. These changes
add no persistent scalar state. Wall-grab suppression/displacement-detach and
full gameplay rewind route coverage remain separate obligations.

## Measured route and remaining work

The original chain stopped at cursor 2003 with 17,024 comparison errors over
1,260 rows. With launch, prelude, chip queue and glide fixes, the initial EHZ1
segment completes all **3,180 rows** with **92 errors**, zero warnings:
91 initial player-history bootstrap differences and one ring-count difference.
There are no position/velocity, PLC or dynamic-art differences in that segment.
The counts cover different route lengths and must not be treated as matched
full-run totals. The first non-bootstrap mismatch is row **2462**, `rings`,
expected 43, actual 53; native catches up next row.

The native monitor at slot 20 spawns its contents in already-passed slot 16 on
row 2430. The child first rises on row 2431 and grants rings on row 2463.
The existing engine delay is conditional on relative allocation slots. Do not
add a fixed reward delay without explaining the engine's allocation order.

The chain now enters and runs the first special stage, then reaches cursor
**9366** still in `TITLE_CARD` rather than rearming the next EHZ1 segment.
This is the next structural frontier. Special-stage interiors use the existing
uncompared gameplay policy with art-ledger comparison; reaching the return is
not a claim of special-stage physics parity or emerald success.

The independent short KiS2 EHZ1 fixture improves from 194 to 179 errors, with
91 history bootstrap errors and zero warnings in each run. It is a different
BK2 from the full-run segment and remains red.

## Validation

All Maven invocations use `python3 tools/testing/maven_queue.py -Dmse=off`,
`test -B`, and absolute verified ROM properties (`sonic2.rom.path`,
`s3k.rom.path`, and `kis2.rom.path` for KiS2 selections). Tests below are
focused validation, not full-suite or complete-chain passes.

- Solo prelude regression: 12 tests pass after reproducing its failure on base.
- `TestKis2PlcService`: three tests pass, covering all chip lists, both module
  tiers, lifecycle/rewind owner identity, and service/restore/forward replay.
  Eight stock PLC service checks also pass.
- `TestGlideWallGrabTerrain`: three tests pass for exact wall fit, both sides,
  ledge rejection/acceptance boundaries, unequal radii and reverse gravity.
- `TestPlayableSpriteMovement`: 177 tests pass, including the transformed-angle
  and slide-animation regressions.
- `TestKis2CompleteEmeraldRunChain`: launch regression passes; full chain fails
  at the cursor-9366 return boundary described above, zero skips.
- Matched base `31a9a6bce` in `.worktrees/kis2-chain-baseline`:
  `TestS2Ehz1TraceReplay` fails with 16,388 errors, zero warnings; first row 6
  `dynamic_art.outstanding_transfer_ids`, expected [2], actual [].
  `TestS3kKnucklesSuperEmeraldRunChain` fails because segment 0's `giant_ring`
  exit is not observed. Neither baseline test skips.

Shared current-tree comparisons, combined ordinary/guard validation and
integration evidence will be recorded after they complete.
