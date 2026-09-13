# Flying Battery Zone outstanding actions

Status updated during the 2026-09-12 route investigation. This branch contains a
large FBZ implementation uplift, but FBZ is not yet accepted as pixel-perfect.
The remaining work is intentionally recorded here rather than hidden behind a
green completion claim.

## Current native trace baseline

The 2026-09-12 replay on `f177bbdb7` reports **5,666 errors, 0 warnings**.
Its first error is frame **34**, `queue.s3k_kos_direct.busy`
(expected `true`, actual `false`). This is the current V5 complete-run baseline;
the July frame-18766/9-error result predates subsequent timing-contract changes
and must not be used as current release evidence.

The baseline used the `trace-replay-r7` profile, one alphabetical fork, and the
verified locked-on S3K ROM. See [trace frontier log](../../../status/trace-frontier-log.md)
for the exact command and worktree. Route-controller changes do not establish
trace parity; investigate the measured timing frontier separately.

## Native FBZ2 compatibility route

The native cold-Act 2 route remains red. The controller now recovers
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

All five team rows and the S2 donated profile now stand against the arena's
`$2BDD` wall at frames 14,345-14,858 with 55-88 rings and no damage, and
`Obj_FBZ2Subboss` has allocated its children. The 512px row reaches the same
`$2BD1` position at frame 14,857 but the route's subboss proxy (`Camera_X_pos
>= $2B30`, the native-width `FBZ2SE_Normal` threshold) reads `$2AD1` there,
so that row still reports the event unreached; the proxy is width-specific,
not the route. The remaining fixed-program runs are no longer consumed. The
next work is the subboss fight, the end boss, the capsule and the exit hall;
the route currently stops at the arena and fails the SOZ request assertion.

Required complete-route evidence remains:

- every encountered Obj28 binds and clears exactly once, with real car entry/exit;
- every Obj74 encounter has its appropriate safe traversal evidence;
- the route reaches the subboss, boss, capsule, and Sandopolis handoff;
- native and S2 never consume the S1-only squeeze assist.

Diagnostic authority and geometry dumps remain useful while these routes are
red. Remove temporary print probes; do not remove the safety assertions.

## Compatibility matrix

The 13-row matrix remains pending and must not be relabelled PASS:

- five multi-sidekick team rows;
- five viewport widths: 320, 400, 512, 640, and 800;
- donation off, Sonic 1, and Sonic 2.

The 640px and 800px rows and the S1 donated profile never leave the BK2 route
segment: at frame 2889 they stand at `$0890,$02EC` with two to four rings and
now fail the lower-floor ring floor there instead of dying later inside the
removed filler (their earlier frames 25729, 25885 and 7343). The 400px row
advances to frame 10131, hurt at `$1F92,$07D9` beside the `$1F40` descending
car. Every remaining row reaches the subboss arena through the lower-crane
controller; the 512px row is held only by the native-width camera proxy.

After the native route is green, run the focused donation, team, and viewport
methods, then the full `TestFbzCompatibilityMatrix`. The S1 row must prove that
Spindash remains absent, the squeeze assist is consumed exactly once, and the
car is acquired/exited. Native and S2 rows must prove that they never consume
the assist. Keep the existing consumption and car-acquisition/exit evidence
assertions in the route completion contract.

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

After trace and compatibility are green:

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
mvn -Dmse=off -Dtest=TestFbzAct2RouteHeadless \
  "-Ds3k.rom.path=$S3K_ROM" test -B
mvn -Dmse=off -Dtest=TestFbzCompatibilityMatrix \
  "-Dsonic1.rom.path=$S1_ROM" "-Dsonic2.rom.path=$S2_ROM" \
  "-Ds3k.rom.path=$S3K_ROM" test -B
mvn -Dmse=off -Ptrace-replay-r7 -Dsurefire.forkCount=1 \
  -Dsurefire.runOrder=alphabetical -Dtest=TestS3kFbzCompleteRunTraceReplay \
  "-Ds3k.rom.path=$S3K_ROM" test -B
```
