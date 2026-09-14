# Flying Battery Zone outstanding actions

Status updated during the 2026-09-14 remaining-items delivery. This branch contains a
large FBZ implementation uplift, but FBZ is not yet accepted as pixel-perfect.
The remaining work is intentionally recorded here rather than hidden behind a
green completion claim.

## Current native trace baseline

The remaining-items delivery is pinned to develop `51677cdd2`. Its inherited
strict baseline was 4,501 complete-run errors, first frame 16,600 Tails
animation, and 5,109 independent-segment errors, first frame 116 Tails subpixel.
Both recordings remain red; aggregate mismatch counts do not measure how far a
playable route completes.

At `d87e42bdd`, the complete recording compares 44,144 comparison entries with **16 grouped errors / 18 field rows**, zero warnings and zero skips. All FBZ gameplay fields agree; the first remaining error is frame **44,230**, destination SOZ initialization X (`$00C0` expected, `$0000` actual). The remaining position/camera and terrain KosM submission/completion errors belong to that fresh-load boundary. Comparison entries include unmatched timing-completion rows and must not be described as raw gameplay frames.

The native Level prologue waits for title readiness and the Nem queue (`loc_62CC`), then publishes `Get_LevelSizeStart` and `LoadLevelLoadBlock` (`loc_6310`). The recording driver instead retains cleared players/camera until its generic title completion. A bounded observer confirms that it is still in title EXIT during the first mismatching row. Correcting this requires the actual fresh-load/Nem-gated phase; changing a title timer or publishing on an observed row would fit the fixture. No such adjustment was made. Live SOZ load/timeline isolation is tested separately and does not establish this strict boundary's parity.

The independent segment now reports 4,152 errors, first frame 7,619 main
`x_speed`. This frontier needs an authentic progression prerequisite: native
Sonic transforms into Hyper Sonic, but the isolated segment supplies no emerald
progression contract. Its native trail owner and transformation routines explain
the speed difference. Neither auxiliary comparison rows nor manifest comparison
fields authorize loading emeralds into gameplay. Keep this explicit prerequisite
gap separate from FBZ movement defects; do not invent a fixture-specific
transformation or weaken the comparison.

Queue ownership, launcher/CPU animation, magnetic collision and ceiling contact,
miniboss body/prison contact, chain precision and hurt-entry rejection, vertical
cage orbit, title dispatch and camera targets now have focused ROM-backed
regressions. See the [trace frontier log](../../../status/trace-frontier-log.md)
and [completion record](../../plans/2026-09-14-fbz-completion.md) for exact
commands, commits and rejected approaches. Historical July near-green results
are obsolete; ordinary route completion does not establish emulator parity.

## Native FBZ2 compatibility route

Native Tails and Knuckles solo routes now also pass every mandatory interaction,
boss defeat, capsule and outgoing SOZ assertion in `TestFbzMainCharacterCompletion`.
This closes their complete-route gap but not the separate visual/rewind matrix.

The native cold-Act 2 route now runs from the ROM start to the forced SOZ
Act 0 request for every viewport width, every team row and the S2 donated
profile; the new focused S1 route also completes (see "Compatibility matrix"
below). The controller recovers
from button egress using a fresh live elevator candidate, clears Obj28 layout
273 exactly once, and proves acquisition and exit of that car. It also waits
for the following descending car to clear its live spike wall and steers the
lower descent into the spike gap before an ordinary exit jump.

Charging occurs on terrain after the screw door opens. The retail landing
contract (`RideObject_SetRide` → `Player_TouchFloor`) unrolls Sonic on the car;
the oracle checks the resulting standing clearance instead of requiring rolling
through the entire crossing. These changes affect ordinary test inputs and
assertions, not production gameplay or the S1 squeeze assist.

The spike at `$2330,$0970` beside the magnetic platform at `$2360,$0970` is
now cleared by a distinct Obj74 ride interaction. When a live placed spike
blocks the flat approach at P1's own level, the controller holds until the
platform rests during INACTIVE polarity with a full jump of runway, jumps onto
it with ordinary inputs, hops between resting columns at floor level (the
raised columns share their height with the horizontal Obj72 chain links, which
would grab an airborne P1), rides the last column to its `$C0` rise, and exits
right onto the far floor. All four `$0970` columns (layouts 331, 340, 348, 355)
bind and clear exactly once with no damage or ring loss.

The authored program was the next obstruction. On `65b555341` the source
BK2 program spent 12,121 frames after landing on the `$0A80` lower floor
(frame 2889), and the former chain/lower-door program, nine lower-backtrack
cycles and a 3,000-frame neutral wait added about 9,000 more, without changing
a milestone, ring or position band; the program expired at frame 34,978 about
1,000 frames below the act's 36,000-frame time-over limit. The pilot now ends
the BK2 program at its 81st run (frame 2889, 23 rings) and the midpoint
approach is only the 27 runs that climb to the `$06AC` ledge, press the
`$0948` button and board the `$08C0` car.

That earlier timing exposed every phase-dependent controller. The following
are now gated on live geometry or AnPal_FBZ polarity instead of arrival phase:

- the `$1718` corridor: hold on the subtype-`$24` button until a fresh ACTIVE
  half-cycle (render-flag-2 Blasters and Obj73 balls rise off the floor), then
  spindash; the non-magnetic `$1790` Blaster is destroyed by the roll;
- the trigger-7 egress: leave the `$1B28` button only inside a fresh ACTIVE
  half-cycle so the magnetic `$19B0` Blaster hangs from the ceiling;
- placed button/screw-door pairs (`Obj_Button` trigger bit = subtype low
  nibble): a generic controller steers onto the linked button, waits for the
  door routine to latch and walks through; sidekick-held trigger bits are
  accepted as shared authority for the subtype-`$22` door as well;
- Obj28 squeeze corridors: a proactive geometry hold before any episode
  binds, a walking-speed cap over the last `$30` before the fence, and a
  charged spindash that rebinds the next upward car instead of aborting;
- the `$208E` ledge over the `$2090`/`$20F0` spike pair: run-off speed is
  capped, the drop is owned from its first falling frame, and a raised Obj74
  column in the gap is ridden down to rest before the exit jump.

With those, all five team rows, the 512px viewport row and the S2 donated
profile reach the `$26C8` spider crane alive with about 21,000 frames of act
time in hand. The fallback tail now hands over beside that crane to a
dedicated lower-crane controller whose gates read placed-object identity,
live geometry and AnPal_FBZ polarity only:

- stand in the crane's `$20` window until `loc_3D11E` captures, ride the
  `$B0` travel to the `$2831` release;
- hop from `$2850` over the TechnoSqueek into the `$2904` horizontal chain,
  hand over to the `$2924` vertical link, ride its `$D8` descent and leave
  with a directional jump; the arc grabs the `$29C0` chain, which hands over
  to the `$29E0` link and its `$90` descent onto the `$0A80` floor;
- stand on the trigger-D `$2970` button and walk onto the `$2924` floor door,
  which drops P1 into the bottom corridor;
- hold beside the resting `$2840` column until both `$0B70` columns hang
  raised (`$31`, capped by the `$0B00` ceiling) with `$80` frames of ACTIVE
  runway, then spindash west; the roll passes under both columns and destroys
  the `$2700` Blaster;
- board a rising `$2640` car, walk off the moment the `$0A80` floor is level,
  stand on the `$2708` launcher, and let its throw enter the `$2780` vertical
  wire cage, which lifts P1 to the `$0800` room;
- jump the `$60` step at `$2800`, hop onto the resting `$2840` column from
  its left face, ride the ACTIVE `$D0` rise and, with `$70` frames of runway,
  hop across the raised `$28C0` column onto the `$0680` corridor, then clear
  the `$2A80` pit into the `$2B40` subboss arena.

All five team rows, the 400px and 512px rows and the S2 donated profile
reach the `$2BDD` arena wall with the act timer intact; the remaining
fixed-program runs are no longer consumed.

### Arena to exit

A dedicated controller now owns everything after the arena wall, gated on
live object state only:

- `Obj_FBZ2Subboss`: dodge each beam by running past the machine to the far
  live side wall (`Fbz2SubbossSolidSideChild`, half-width `$13`) whenever the
  machine tracks within `$50` of P1, until the seventh cycle releases the arena;
- arena exit: RIGHT with a hop from the `$2BF0`-`$2C40` ledge, then board the
  `Obj_FBZEndBossEventControl` plane carrier and ride it at the controller's
  own offset (`-$123`, then `-$167` once the carrier passes `y=$0380`) so the
  rebase to the `$2E5C`/`$32B8` lock lands P1 inside the arena;
- `Obj_FBZEndBoss`: stand at `$2E74` during DESCEND, then meet each ATTACK
  round with a charged spindash from the far wall timed to the pod's
  `$0660`-`$0690` bob and the `$78` wait distance, releasing on the frame the
  roll clears (a jump on the release frame cancels the roll); eight hits;
- capsule: approach from `$3038` and hop onto the `$307C` capsule button;
  the forced exit walk to Sandopolis is then production-owned (mask 0).

The earlier route stages that a shifted arrival phase exposed are now gated on
live geometry too: the `$08C0` shaft is boarded exactly as the BK2 does it
(stand at `$092B`, walk LEFT for the ~33 frames that reach `$0911` at
`g=$FE74`, jump when the descending car's centre sits `$08`-`$12` below P1;
rows 24792-24882), the `$0790` retracting spring is waited for at the `$0795`
wall instead of by cadence (rows 27102-27432), the `$0C20`-`$0D60` Obj73 ball
corridor commits only with ACTIVE runway covering P1 and every trailing CPU
sidekick and all balls risen, an Obj28 release holds while another car of the
same column spans P1's rolling height, and a TechnoSqueek at body height
ahead of a running P1 is cleared with a jump-roll.

### BK2 prefix replaced

The BK2 input replay is now consumed only until P1 lands on the `$01EC` ledge
after the `$078C` trap spring (rows 23527-23540). A ledge controller then
owns the route to the `$076C` loop floor: hop the `$01F8` mice with released
short jumps (the chain grab band is `$90`-`$A8` below the link), grab the
`$0868` Obj72 chain from `$085C` and ride it to its `$B0` extension, release
LEFT and steer the `-$200` drift into the `$0810`/`$0870` spike gap, break the
`$0810` monitor with a jump from `$0829`, stand on the `$0810` Obj78 launcher,
and settle from its `$1000` throw just right of the upper-loop approach facing
LEFT (the S1 upper-loop assist consumes itself on any LEFT-pressed leftward
motion inside `$0A40`-`$0A70`, so no LEFT is pressed there). The BK2's own
player grabbed the `$0818` chain first and was knocked off it by a mouse at
row 23622; a wider viewport meets the mice in another phase and that chain
lowers P1 into the spikes, which is why the fixed replay could never finish
at 640 or 800 pixels. The 27 authored loop-climb runs are no longer consumed:
the settle point is inside `acquireMidpointCar`'s brake range.

After the `$08C0` shaft the run to the `$0B38` button is capped at `$400` so
the door stage can brake inside its `$0B00`-`$0B80` envelope instead of the
authored RIGHT/JUMP cadence carrying P1 over the button into the `$0C00`
spikes.

### Widescreen object windows (production fix)

At 640 and 800 pixels the `$0B68` screw door never existed when P1 arrived:
its delete-touch check used the native `$280` while the placement window
loads objects `$80 + width + $C0` ahead, so it was deleted on the frame it
loaded, and the `$0BC0` elevator's cars, the Obj73 balls and every other FBZ
object with a bare `$280` `Sprite_OnScreen_Test2` range did the same. They
now share `AbstractObjectInstance.coarseXCullViewport()` (native unchanged).
See [known-discrepancies.md](../../../status/known-discrepancies.md), Object
Despawn and Visibility Windows.

### Sidekick contract

CPU sidekick deaths are shipped behaviour: the ROM's own Tails dies three
times in the act-2 rows of `fbz_completerun` (sidekick routine 6 at row 35308
beside the `$2924` chain descent, 38697 on the plane carrier and 39721 in the
end-boss arena) and `Tails_CPU_Control` brings it back through the `$7F00`
respawn each time. The audited team contract is therefore: every death
respawns within `$100` frames, the team is alive when `Obj_FBZEndBoss`
allocates and when the SOZ exit is requested, and identity, CPU ownership
and the leader chain never change. The route still keeps followers safe where
P1 can: Obj74 crossings budget the runway for the trailing chain
(`Tails_CPU_Control` loc_13DA6 follows the leader's Pos_table entry 17 frames
back), the shaft jump waits for the gathered chain, and P1 holds right of the
`$08C0` column until every sidekick is out of it. The two ring floors the
route once asserted (`>= 6`) are the ROM's `>= 1`: the BK2 reaches the
`$082A,$02EC` landing with one ring (rows 23622, 23796, 24081).

Required complete-route evidence remains:

- every encountered Obj28 binds and clears exactly once, with real car entry/exit;
- every Obj74 encounter has its appropriate safe traversal evidence;
- the route reaches the subboss, boss, capsule, and Sandopolis handoff;
- native and S2 never consume the S1-only squeeze assist.

Diagnostic authority and geometry dumps remain useful while these routes are
red. Remove temporary print probes; do not remove the safety assertions.

## Compatibility matrix

Current focused donor evidence on `9fa8fc0a0` plus completion controller
changes: both S1 and S2 complete the mandatory cold-start route, including the
boss, capsule and SOZ request, with zero failures/errors/skips. The exact
command and rejected approaches are recorded in the
[completion record](../../plans/2026-09-14-fbz-completion.md).

The S1 squeeze uses a longer ordinary run-up and DOWN-only timed roll onto a
live ascending car. It proves actual acquisition, block clearance, release and
continued movement without consuming the squeeze assist or using spindash.
The previous demand to consume that assist exactly once was a controller
assumption, not a ROM requirement; successful ordinary movement disproves it.
The helper also authors ordinary bottom-column, Blaster and end-boss inputs.

The final candidate's eleven exhaustive team/width/donor routes now pass, with
zero failures/errors/skips (62.31 seconds). All 96 local elevator arrival delays,
15 width/donor combinations, three independent two-cycle rewind spots and the
expanded 105-case Act 1 lifecycle product also pass in focused execution.
These are current focused results, not a full ordinary/guard suite pass. See the
[Act 1](../../validation/levels/s3k-fbz-act1.md) and
[Act 2](../../validation/levels/s3k-fbz-act2.md) matrices for remaining full
main-character route, presentation and rewind obligations.

## September verification

The full ordinary baseline on `f177bbdb7` and combined candidate in
`.worktrees/ai-fbz-route-closure` both completed **20,217 tests, 14 failures,
0 errors, and 25 skips**. Comparison by test identity and full failure diagnostic
found exactly ten intended frontier advances and no other changed outcomes.
Four FBZ failures remain byte-for-byte unchanged. The 25 skips are unchanged;
they include opt-in measurements and unavailable reference/capture prerequisites,
not silently missing S1/S2/S3K runtime ROMs.

Separate fresh-JVM structural-guard runs completed **656 tests, all passing,
with no skips**, for both baseline and candidate. Candidate source SHA-256 is
`9216a2a35c125ef8912f28ec6ecc46df1d76b7dc8648430405b5791bdd2d9f8a` for
`TestFbzAct2TraversalPreboss.java`. Full logs, per-test snapshots, and the exact
comparison are archived under
the external task directory `$FBZ_EVIDENCE_ROOT` (`fbz-20260912`).

These results validate bounded controller progress. They do not close the
complete-route, canonical trace, or visual acceptance gates.

## Visual and final validation

[fbz-validation.md](fbz-validation.md) remains the authoritative honest record: the
immutable native/engine checkpoint pairs and comparison sidecars are incomplete,
so the visual gate is still FAIL. Do not commit ROM-derived screenshots under
`refs/`.

Remaining native visual obligations (the ordinary compatibility routes pass; strict trace gaps above remain):

1. Capture the required BizHawk references and native engine frames.
2. Complete every named static and time-series comparison sidecar.
3. Run the focused FBZ suite, shared collision/movement/rewind regressions,
   policy guards, compatibility matrix, strict complete-run trace, and package.
4. Review discrepancies and rerun affected gates after each fix.

## Useful commands

Set `S1_ROM`, `S2_ROM`, and `S3K_ROM` to the absolute paths of the existing,
verified ROMs. Do not create aliases or links to match an example. Check skips
in the completed report.

```bash
python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestFbzAct2RouteHeadless \
  "-Ds3k.rom.path=$S3K_ROM" test -B
python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestFbzCompatibilityMatrix \
  "-Dsonic1.rom.path=$S1_ROM" "-Dsonic2.rom.path=$S2_ROM" \
  "-Ds3k.rom.path=$S3K_ROM" test -B
python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay-r7 -Dsurefire.forkCount=1 \
  -Dsurefire.runOrder=alphabetical -Dtest=TestS3kFbzCompleteRunTraceReplay \
  "-Ds3k.rom.path=$S3K_ROM" test -B
```
