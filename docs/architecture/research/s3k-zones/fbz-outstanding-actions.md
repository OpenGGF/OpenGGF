# Flying Battery Zone outstanding actions

Status at the 2026-07-17 integration checkpoint. This branch contains a large,
reviewed FBZ implementation uplift, but FBZ is not yet accepted as pixel-perfect.
The remaining work is intentionally recorded here rather than hidden behind a
green completion claim.

## Last authoritative native trace

- Complete-run frontier: frame `18766`.
- Last run before the final Blaster fix: 9 errors, 0 warnings.
- The prior lost-ring mismatch at frame `18257` is closed by restoring the two
  native stationary-cage SST occupants through ROM coarse-X lifetime semantics.
- Frame `18766` was caused by a duplicate Blaster primary projectile. The
  reviewed fix preserves the patrol raw animation index/timer across the attack
  transition; `TestFbzBlaster` is 17/17 green. The strict trace has not been run
  after that fix.

Next action: run the strict replay with a 4 GiB single fork, update
`docs/TRACE_FRONTIER_LOG.md`, and continue from the measured frontier only.

## Native FBZ2 compatibility route

The native cold-Act 2 route remains red, although it no longer dies in the
Obj28/elevator squeeze and both Obj74 magnetic-platform episodes pass with two
bindings and two clearances for layouts 250 and 255.

The route currently stops during the button-to-terrain launch transition before
the squeeze. Production projection and S1-donation assist code is focused-green
and reviewed. The remaining change is test-controller-only: review and TDD a
bounded stage-0 bridge for the expected button egress transient, keeping P1
before the geometry-derived Obj28 frontier until strict launch-floor authority
returns. Do not widen the production S1 assist to airborne state.

Required native evidence before proceeding:

- layout 273 binds and clears exactly once;
- native spindash releases at the safe projected speed;
- the selected rising elevator car is actually acquired, traversed, and exited;
- Obj74 remains 2/2 green;
- the route reaches the subboss, boss, capsule, and Sandopolis handoff.

Temporary route diagnostics in `TestFbzAct2TraversalPreboss` should be removed
after this route is green: `squeezeLaunchAuthorityEvidence(...)`, the
`launchAuthority=` bind text, and the expanded abort authority/geometry dump.
Update squeeze Javadocs at the same time so they say that charge occurs on the
terrain/button and the elevator car is acquired after release.

## Compatibility matrix

The 13-row matrix remains pending and must not be relabelled PASS:

- five multi-sidekick team rows;
- five viewport widths: 320, 400, 512, 640, and 800;
- donation off, Sonic 1, and Sonic 2.

After the native route is green, run the focused donation, team, and viewport
methods, then the full `TestFbzCompatibilityMatrix`. The S1 row must prove that
Spindash remains absent, the squeeze assist is consumed exactly once, and the
car is acquired/exited. Native and S2 rows must prove that they never consume
the assist. Export the squeeze-assist consumption bit in route completion
evidence if necessary so the matrix can assert it directly.

## Visual and final validation

`docs/s3k-zones/fbz-validation.md` remains the authoritative honest record: the
immutable native/engine checkpoint pairs and comparison sidecars are incomplete,
so the visual gate is still FAIL. Do not commit ROM-derived screenshots under
`refs/`.

After trace and compatibility are green:

1. Capture the required BizHawk references and native engine frames.
2. Complete every named static and time-series comparison sidecar.
3. Run the focused FBZ suite, shared collision/movement/rewind regressions,
   policy guards, compatibility matrix, strict complete-run trace, and package.
4. Repeat independent review/fix loops until all gates are green.

## Useful commands

```powershell
mvn -Dmse=off "-Dtest=com.openggf.tests.TestFbzAct2RouteHeadless#nativeStartWaveCompletesFbz2AndRequestsSandopolisAct0" "-Ds3k.rom.path=s3k.gen" test
mvn -Dmse=off "-Dtest=com.openggf.tests.TestFbzCompatibilityMatrix" "-Ds3k.rom.path=s3k.gen" test
mvn -Dmse=off "-Dsurefire.argLine=-Xmx4g -Dnet.bytebuddy.experimental=true" "-Dsurefire.forkCount=1" "-Dtrace.frontierOnly=true" "-Dtest=com.openggf.tests.trace.s3k.TestS3kFbzCompleteRunTraceReplay#replayMatchesTrace" "-Ds3k.rom.path=s3k.gen" test
```
