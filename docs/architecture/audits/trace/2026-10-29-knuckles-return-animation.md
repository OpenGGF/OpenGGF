# Knuckles first-return animation ownership — 2026-10-29

This leaf uses the T3-provisioned checkout `t3-5a0ab4ca`, local branch
`bugfix/ai-knuckles-chain-frontier-20261029`. Its clean provisioned source
`c4cd6ae0` was fast-forwarded to pushed develop
`9999bcbdfc5ac777d335f129c3ae2908bfa7c297`. No other checkout was created.
The [matched canonical/candidate evidence](../2026-10-29-s3k-native-terminal-evidence.md#current-source-candidate-consumption--bounded-java-evidence)
is the inherited frontier, not a new measurement by this leaf.

## Inputs and bounded native decisions

The original locked-on ROM was read in place: 4194304 bytes, CRC32 `63522553`,
SHA-1 `CFBF98C36C776677290A872547AC47C53D2761D6`. The canonical run movie
`src/test/resources/traces/s3k/runs/s3k-knuckles-complete-superemeralds/s3k-knuckles-complete-superemeralds.bk2`
has SHA-256 `aa892856df22b7bb1fe5accb48db10b90dc26845d1dccee90352da30349f53cc`.
Neither file was copied, linked or changed. The external candidate remains
comparison evidence; no endpoint, producer or canonical fixture was edited.

An external read-only C# observer uses the pinned `703bd92` TraceChaser host.
All 112 archived source/build/dependency files used by the reused native host
match that exact Git pin. Host SHA-256:
`dad8d30f427978166fcc048dafc8a8d41e6dfce9615a664f33b159cd2f6c44a3`.
Official BizHawk 2.11 preflight passed. The probe replays only original movie
inputs, stops after 8428 completed hardware frames and writes observations
only; no state writes, setup seeds, input padding or native patches are used.
Both display variables are unset. The source/binary SHA-256 values are
`223f8aeccc78036310fd55ce1f0f6181fad0af60555e2cb896860b7d7e153375` /
`c06ed2d71b2e576bc06951113fda20e23a07ec5bbcb39ad0e93ddac423ded2d0`.

Execute callbacks read player state at ROM addresses `$0165BE`, `$0172A8`,
`$017322` and `$016614`; the first 16 bytes at each address were checked against
the verified original ROM. During the hardware frame beginning at completed
frame 8423, the level counter is 1. Dispatch enters `loc_165BE` with status `$00`,
animation `$00`, zero velocities and held-Down logical word `$0200`. `loc_172A8` observes
animation `$05`, then `loc_17322` observes animation `$08` while status is still00. At
`loc_16614`, status is `$02` and anim remains08. At completed hardware frame 8424,
Knuckles has animation `$08` / mapping `$9B` at centre `$1BC8/$04C0`; the next four airborne passes
retain `$08/$9B`. Alignment uses the level counter, player state and these callback
boundaries, not a fitted movie/row offset.

`loc_172A8 (sonic3k.asm:31928-31932)` owns the held-Down write. It runs inside
`Knux_InputAcceleration_Path` before `Knux_Stand_Path (sonic3k.asm:30664-30673)` reaches `Call_Player_AnglePos`. The shipped path has
no conditional FixBugs alternative here. `loc_16614 (sonic3k.asm:30489-30501)` then calls animation after collision. This proves a
grounded Duck write followed by same-dispatch detachment, rather than a
restored animation byte or a native asynchronous timing effect.

The queued native launcher command was `python3 "$TASK_ROOT/run_probe.py"`;
it uses the repository's `maven_slot` for its bounded 120-second native command,
and build/activity leases for preflight and compilation. It completed exit 0:
637.478s admission wait, 14.350s native execution. Preflight took 0.164s and
observer compilation 0.615s. These are separate intervals, not total delivery
wall time. Native observations are diagnostic evidence, not canonical traces
or pixel acceptance.

## Matched engine observation

At unchanged runtime `9999bcbdf`, a temporary test-only subclass of
`AbstractRunChainTest` calls `assertChainReplayThroughSegmentRow` on the
canonical original run through segment 2 row 2. It only observes before/after
production steps. The complete command is:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME="$JDK25" LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl "-Ds3k.rom.path=$S3K_ROM" \
  -Ptrace-replay-r7 -Dsurefire.forkCount=1 -Dsurefire.reuseForks=true \
  -Dtest=TestKnucklesFirstAnimationProbe test -B
```

Result: 1 test, zero failures/errors/skips, case 12.777s, Maven 1m34s, reported
admission wait 609s. This is the known reached-prefix assertion, **not** a
zero-error destination comparison. The strict comparison logs the inherited
row 0 animation `$08/$05` and mapping `$9B/$56` mismatches. After that production step,
the engine reports `air=true`, `airAtFrameStart=false`, `crouch=true`, grounded
movement snapshot present and both object-support entry snapshots false.
On the next airborne pass, crouch clears and the incorrect `$05/$56` persists.
All row 0 physics, camera, rings and queue fields match in the printed comparison.
The EGL option establishes no rendered-pixel claim for this logic-only harness.

The causal owner is the grounded-to-air branch in
`ScriptedVelocityAnimationProfile.resolveAnimationId`: it reconstructs Wait
from the grounded zero-inertia snapshot while overlooking the already-selected
crouch pose. The loader has supplied the correct grounded inlet, and movement
has selected the correct crouch. No load, timing or engine-loop change is
needed for this first mismatch.

## Regression proof and bounded repair

Before any runtime edit, the focused pre-fix command retained the same unset
displays, Java 25, Lua 5.4, EGL and absolute verified ROM settings above, omitted
the trace profile/fork flags and selected:

```text
-Dtest=TestScriptedVelocityAnimationProfile#groundedCrouchSelectionSurvivesAnglePosDetachBeforeAnimationPublication+airborneCrouchWithoutGroundDispatchCannotPublishDuck,TestKnucklesDetachPoseRewind
```

The run completed 4 tests / 2 failures / 0 errors / 0 skips, Maven 35.053s,
reported admission wait 501s. The semantic profile control expected Duck 6 and
observed Wait 0; the actual ROM-backed Knuckles Down case expected 8 and
observed 5. The no-ground-dispatch profile control and neutral Knuckles case
passed. The latter completed both registered whole-world restore/replay cycles,
so the expected Down failure is independent of that test's rewind machinery.

The repair adds only the existing crouch-to-Duck selection inside the existing
grounded-movement-snapshot branch of `resolveAnimationId`. It adds no state,
constants, game/route/frame predicates or new rewind contract. The following
airborne dispatch retains the published raw animation under the existing owner.
The independent component inlet is `$400/$100` above AIZ terrain, not any recorded
return position. It uses actual ROM collision, animation scripts/mappings and the
production registry; its setup and narrow scope are declared in the test.

The change-based plan against pinned source `9999bcbdf` selects all 4,319 smoke
classes and guards because this is a shared animation owner. The user's explicit
leaf instruction forbids a broad suite. Focused profile/animation checks,
ROM-backed component rewind, the four required S3K anchors and the hardware
authority guard are used for this bounded repair, alongside the strict returned
segment-closure measurement. This is not broad or release qualification.


## Focused qualification

With the seven-line runtime repair and the committed regression-test sources,
the following command completed successfully on Java `25.0.5.0.2`:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME="$JDK25" LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl "-Ds3k.rom.path=$S3K_ROM" \
  '-Dtest=TestScriptedVelocityAnimationProfile,TestPlayableSpriteAnimation,TestKnucklesDetachPoseRewind,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestHardwareTimingAuthorityGuard' \
  test -B
```

Actual Surefire XML inventories total **170 tests, zero failures/errors/skips**:
34 semantic-profile cases, 49 player-animation cases, 2 real-ROM detach-pose
restore/replay cases, 8 AIZ-skip cases, 6 bootstrap cases, 3 decoding cases,
36 game-package level-loading cases, 7 tests-package level-loading cases and
25 hardware authority guard cases. The same simple level-loading selector
intentionally executes both concrete classes. The native 320px Knuckles component
qualifies Down and neutral input with two registered whole-world restore/replay
cycles each; its Duck case now publishes the actual ROM mapping `$9B`.
Maven reports 1m38s execution and 589s admission wait. The test cases themselves
are bounded component evidence; this does not certify whole-act breadth,
load-boundary rewind, a complete chain or pixel parity.

The dated audit's four disassembly citations pass the strict citation
checker. Checking the whole existing animation file also reports three old S2
anchor findings (`Obj02_Dead`, `Obj41`, `Sonic_ResetOnFloor`); removing only the
seven-line addition reproduces the exact baseline file, so they are unchanged
and remain outside this task. The added code citation is the same verified
`loc_172A8` range as this audit.


## Strict returned-level frontier and first remaining unknown

The final temporary observer selected only
`TestKnucklesFirstAnimationProbe#observeReturnedLevelClosure`, using the first
engine command's exact flags and canonical run/movie, with target segment 3 row 1.
It completed **1 test, 1 failure, zero errors/skips**, case 13.761s, Maven 48.029s,
reported admission wait 670s. It is an intentional bounded frontier measurement,
not a passing regression or a complete destination segment.

The first strict error is now row 446, X native `$2026` / engine `$201B`; there is no
preceding camera or animation error in this invocation. At the first returned
row the observer now sees `$08/$9B`, and the following airborne passes retain it.
The aborted segment 2 report is `complete=false`, 37,726 errors (33,100 physics,
4,626 animation), zero warnings/lagged frames/bootstrap errors. Segment 0 completes
with zero errors/warnings/bootstrap errors. The two dynamic-art gaps have zero
failures. Production ownership still fails at:

```text
segment 2 lost production ownership before source closure (mode=LEVEL, level=LevelIdentity[loadGeneration=4, progressionZone=0, romZone=0, act=0], BK2 cursor=12011)
```

The same suppressed close observations remain: direct #35 at raw 2889/
PRE_MAIN_LOOP, fingerprint
`sha256:e0704fa4bac5e0862b2f4c633a83a74b2ab3897b7c0553770adae20ad64cc3cb`,
and module #24 at raw 2890/POST_OBJECTS, fingerprint
`sha256:b218e238a2aff440adaeeb63571042dfa0ee0accaa18f1722284e498e8d55057`;
neither has a matching pending engine job. These checks remain strict.

The pushed matched canonical/candidate closure evidence was measured at runtime
`bca1c3ae2a230d727f7fe801cb7600b6db93b143`; this leaf's pre-fix row 0 observation
was measured at 9999bcbdf, and its repaired closure uses 9999bcbdf plus the
seven-line repair. Against the pushed evidence, the final total is 34 lower
(37,760→37,726), all in animation (4,660→4,626); the physics total remains 33,100.
The earliest physics mismatch, full ownership assertion and suppressed timing
identities match. This leaf did not repeat that pre-fix closure or compare every
individual downstream error row against it; equal physics totals alone are not
claimed as a full error-identity comparison.

At native trace row 446, the recorded giant-vine handle in absolute SST 35 is at
`$2026/$03D5`, the player is moved to `$2026/$03E9`, velocities clear, object_control
becomes `$03` and anim becomes `$14`. `AIZRideVineHandle_CheckGrab (sonic3k.asm:46754-46789)` owns the relevant admission. This narrows the remaining
symptom to native vine capture; it does not prove which upstream decision is
wrong. The first causal unknown is whether the production engine's live handle
phase/position, physical owner or grab-range decision already differs before
this capture. The earlier pose repair supplies no evidence to adjust those
values.

The next decisive experiment is a paired original-movie observation at the
actual handle's pre-position and CheckGrab boundaries: native AIZ_vine_angle
at `$FEBA`, physical handle/parent slots, angles, positions, player pre-contact pose,
and each range-test result, against corresponding engine-owned state. Begin at
the return's first normal update and continue through the first capture so a
phase drift is attributed to its first owner, rather than fitted at the
attachment row. A separate existing worker had uncommitted ring/flash changes
and a queued return diagnostic when ownership was checked; this leaf did not
copy, edit or rerun that work. Any cause requiring shared Engine/GameLoop,
timing, loading, audio or rewind changes belongs to the separately owned lane.

## Retained scope

No full chain, terminal tail, endpoint installation, pixel acceptance, broad
smoke or public-release suite is certified. Supported viewport/donor/team
breadth, Knuckles act-wide routes, real special-return load-boundary rewind and
the later physics/ownership frontier remain open. The added component check is
independent of the long chain and restores every registered key twice through
its local detach edge. No producer, fixture, comparator or timing contract was
changed, and no delegate, push, PR, integration or release was performed.


## Handoff and artifacts

Both queued final commands and the native observer are complete; no owned job
remains. The temporary run-chain observer was removed from the checkout after
its source was preserved beside the small native probe. Consumed Maven/trace
reports and success/failure logs are removed after their inventories, skips,
assertions and fingerprints are read. The external task root named
`knuckles-first-animation-20261029` retains the read-only C# observer, its compiled
binary, 26 observation rows (14 frame ends and 12 callbacks), native command/input
provenance, launcher and temporary Java observer source. These are bounded
research artifacts, not canonical fixture or validation caches. No foreign
checkout, job, dirty submodule or unknown output was cleaned.

This remains a local repair on top of the exact source SHA above. The permanent
runtime/test sources are unchanged since the completed 170-case focused run and
strict closure measurement. Only evidence prose, removal of the temporary
observer and diagnostic cleanup followed. Total delivery wall time and wrapper
hold durations beyond reported Maven/native execution are unmeasured; overlapping
queue intervals must not be summed as elapsed delivery time. The coordinator
retains integration, any broader qualification and the unresolved vine frontier.
