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


## Shared-owner qualification follow-up (2026-10-29)

Leaf T3-provisioned isolated checkout `t3-7fb25674`, local branch
`qualify-grounded-detach`, fetched and fast-forwarded to pushed develop
`9999bcbdfc5ac777d335f129c3ae2908bfa7c297`. Candidate `09af441307910006d3b02d4bdb1c0d7993f14d47`
was incorporated with its source attribution preserved as `04e3b285e1537f230fcf4b4609c1318615751333`.
The original worker's checkout was not modified. This follow-up owns focused
qualification only; the lead owns combined smoke and integration.

`TestKnucklesDetachPoseRewind` now continues the actual registered Knuckles
component through a neutral airborne tick. It asserts that crouch clears,
the ground-movement animation snapshot expires, and the published Duck byte
and ROM mapping `$9B` remain. It restores the detached checkpoint and replays
that continuation, then repeats both ticks from the inlet in the existing two
whole-registry replay cycles. All registered keys remain compared.

`TestGroundedDetachNativeProfiles` boots ROM-backed production sessions for
S1 Sonic; S2 Sonic and Tails; S3K Sonic, Tails and Knuckles; KiS2 Knuckles;
and S3K-donor Knuckles in S1. Each case restores its declared component inlet
before neutral, Down, Up, simultaneous Up/Down, forced animation,
movement-suppressing object control, and move-lock witnesses. It invokes the
real movement and animation managers, with actual terrain detachment rather
than setting an airborne flag after an artificial speed snapshot. Successful
pose selections continue through a neutral air tick. Failing pose assertions
stop their continuation; `assertAll` still executes the other inlet witnesses.
This is bounded 320px, solo, first-act component coverage, not route, pixels,
team, all-donor or load-boundary certification.

The native expectation is independent of the candidate: S1
`Sonic_LookUp (_incObj/01 Sonic.asm:465-483)` and S2
`Sonic_Lookup (s2.asm:36729-36756)` / `Tails_Lookup (s2.asm:39751-39777)`
test Up before Down. The locally available KiS2 source object `c336fed`
(`knuckles-in-sonic-2`, as catalogued in `docs/kis2/BRANCH_DIFFS.md`) retains
that order in `Sonic_Lookup` at source lines 39297-39323; its `Sonic_Roll`
at 39910-39924 returns below `$80` without writing Duck. No disassembly pin
was changed. S3K Sonic's `loc_11276 (sonic3k.asm:22650-22677)` and Knuckles'
`loc_172A8 (sonic3k.asm:31928-31956)` test Down first; the later
`SonicKnux_Roll (sonic3k.asm:23258-23275)` also writes Duck below `$100`,
including after Move was gated by move-lock. Donor composition keeps the
host's ground-pose rules; the test checks the resolved semantic rule rather
than selecting runtime behavior by game name.

Original ROMs were read in place, with no copies or links. None was missing:

| Original filename under `$OPENGGF_SOURCE_ROM_ROOT` | CRC32 | SHA-1 |
| --- | --- | --- |
| `Sonic The Hedgehog (W) (REV01) [!].gen` | `AFE05EEE` | `69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B` |
| `Sonic The Hedgehog 2 (W) (REV01) [!].gen` | `7B905383` | `8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9` |
| `Sonic and Knuckles & Sonic 3 (W) [!].gen` | `63522553` | `CFBF98C36C776677290A872547AC47C53D2761D6` |
| `kis2.gen` (3,407,872 bytes) | `2AC1E7C6` | `6CD0537A3AEE0E012BB86D5837DDFF9342595004` |

The KiS2 session asserts the production `kis2` patch, and the donor session
asserts active donation. Their passing boot assertions do not establish native
pixel parity. Configuration points the KiS2 catalogue at the original ROM
directory; Maven test properties alone do not populate that catalogue.

### Matched measurements and stopping boundary

All commands use Java 25, unset `DISPLAY`/`WAYLAND_DISPLAY`,
`ALSOFT_DRIVERS=null`, `EGL_PLATFORM=surfaceless`, `--normal -Dmse=off`,
`-Dopenggf.headless.gl=surfaceless-egl`, and the four absolute ROM paths above
as `sonic1.rom.path`, `sonic2.rom.path`, `s3k.rom.path`, and `kis2.rom.path`.
The focused command shape below uses `OPENGGF_SOURCE_ROM_ROOT` for the local
directory containing those original verified files; the local handoff reports
its exact absolute value. No ROM alias was created:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  ALSOFT_DRIVERS=null EGL_PLATFORM=surfaceless \
  python3 tools/testing/maven_queue.py --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestGroundedDetachNativeProfiles,TestKnucklesDetachPoseRewind,TestScriptedVelocityAnimationProfile' \
  "-Dsonic1.rom.path=${OPENGGF_SOURCE_ROM_ROOT}/Sonic The Hedgehog (W) (REV01) [!].gen" \
  "-Dsonic2.rom.path=${OPENGGF_SOURCE_ROM_ROOT}/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  "-Ds3k.rom.path=${OPENGGF_SOURCE_ROM_ROOT}/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
  "-Dkis2.rom.path=${OPENGGF_SOURCE_ROM_ROOT}/kis2.gen" test -B
```

The first candidate run completed **44 tests, 8 failures, 0 errors, 0 skips**
(Maven 1m32s, reported admission wait 489s). The extended Knuckles component
passed both cases and the semantic profile passed all 34. Each of the eight
native-profile cases failed Up-only: expected `$07`, actual `$05`. The five
S1/S2/KiS2/host-S1-donor cases additionally failed simultaneous Up/Down:
expected `$07`, actual `$08`. All neutral, Down, forced-byte,
movement-suppression and neutral move-lock witnesses passed. This initial
run predates the added held-Down move-lock witness and is not claimed to cover it.

For matched baseline attribution, only the production animation file was
replaced with its exact `9999bcbdf` blob, removing the candidate's seven lines;
all other production sources remained identical. The final test source includes
both neutral and held-Down move-lock witnesses. The same three-class command
completed **44 tests, 10 failures, 0 errors, 0 skips** (Maven 1m31s, admission
wait 436s). The eight matrix cases fail in both trees. Baseline additionally
fails the existing semantic crouch test (expected Duck 6, actual Wait 0) and the
Knuckles Down component (expected `$08`, actual `$05`). Neutral registered
continuation still passes on baseline.

| Witness | Native expectation | Baseline observation | Initial candidate observation |
| --- | --- | --- | --- |
| Neutral, all eight configurations | Wait `$05` | `$05` | `$05` |
| Down, all eight configurations | Duck `$08` | `$05` | `$08` |
| Up only, all eight configurations | LookUp `$07` | `$05` | `$05` |
| Up+Down, S1/S2/KiS2 and host-S1 donor | LookUp `$07` | `$05` | `$08` |
| Up+Down, three S3K native profiles | Duck `$08` | `$05` | `$08` |
| Held-Down move-lock, three S3K profiles | Duck `$08` | `$05` | not run in initial candidate command |

The Up-only failures are reproduced on baseline. The earlier-games'
simultaneous-input assertion also fails on baseline, but its actual byte changes
under the candidate; this is explicitly not an unchanged error fingerprint.
No old-green/new-red witness has been demonstrated. The known native mismatch
is not made correct by reducing totals, and the supplied shared correction is
not certified for all inputs. Its pending LookUp/precedence model is an evidenced
boundary for separate semantic work. No expectations were weakened, tests
disabled, new accepted discrepancy declared, or game/fixture predicates added.
The qualification branch deliberately preserves the native-correct red witnesses.

The final candidate run restores the production file exactly to `04e3b285e`
and uses the same final tests as the matched baseline. It adds
`LUA_BIN=/usr/bin/lua5.4` to the environment and replaces only the selector with:

```text
-Dtest=TestGroundedDetachNativeProfiles,TestKnucklesDetachPoseRewind,TestScriptedVelocityAnimationProfile,TestPlayableSpriteAnimation,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestHardwareTimingAuthorityGuard
```

Completed result: **178 tests, 8 failures, 0 errors, 0 skips**, Maven **1m39s**
after **582s** reported admission wait. The native-profile class has the same
eight failing cases and exact thirteen Up assertion failures listed above;
its newly added held-Down move-lock witness passes in all eight configurations.
In particular S3K's three expected Duck writes become `$08`, versus baseline
`$05`. S1/S2/KiS2/host-S1-donor move-lock keeps incoming LookUp as required.
Neutral, Down and the three S3K simultaneous-input cases complete their neutral
air continuations; Up failures occur before those continuations.

The other **170 cases pass**: 2 extended registered Knuckles cases, 34 semantic
profile cases, 49 animation-manager cases, 8 AIZ-skip cases, 6 bootstrap cases,
3 decoding cases, both concrete level-loading classes (36 + 7), and 25 hardware
authority guard cases. This is focused affected coverage, not shared smoke,
a complete trace chain or public-release qualification. The command returns
failure because the native-correct Up witnesses remain red. It is not a green
integration candidate or a claim of all-game native parity.

All three command sessions completed (initial candidate `40372`, matched
baseline `99086`, final candidate `29741`); no owned job remains. Queue wait
and Maven execution above are separate measurements; total delivery wall time
was not measured. The runtime file is unchanged from the supplied candidate.
Only evidence prose and diagnostic cleanup follow the final runtime/test
measurement. Consumed task logs and Surefire reports are removed; no pass cache,
raw-log archive or validation receipt is retained. The final local qualification
commit records these tested sources and the evidence. No delegation, checkout
creation, original-worker modification, integration, push, PR, release, broad
smoke, release-only full suite or unrelated cleanup was performed.


## Shared ground-owner implementation (2026-10-29)

Leaf `t3-38011090`, local `bugfix/ai-grounded-air-animation-owner`, fetched
pushed develop `430051ee4` and incorporated the supplied commits as `4bc22c4a8`
and `a77ace833`. The only cherry-pick conflicts were independent appended
coverage/frontier entries; both histories were retained. The source checkout
`t3-7fb25674` was read without modification. No other checkout or agent was
created. The lead retains smoke, integration and publication ownership.

The matched pre-fix command on `a77ace833` selected exactly
`TestGroundedDetachNativeProfiles,TestKnucklesDetachPoseRewind,TestScriptedVelocityAnimationProfile`
with the four absolute original ROM paths rooted at
`$OPENGGF_SOURCE_ROM_ROOT` (the original-ROM directory resolved locally and
reported in the worker handoff), Java 25, Lua 5.4 and the same offscreen
flags as the qualification above. It completed **44 tests, 8 failures, 0 errors,
0 skips**: all eight Up-only assertions and the five Up+Down assertions have
the exact expected/actual bytes in the prior table. The two Knuckles cases and
34 profile cases pass. Queue admission waited **407 seconds**; Maven execution
was **1m38s**. The actual category-tool preflight also passed its Java 25,
Lua 5.4 and PowerShell prerequisites without running tests.

### Native ordering and semantic owner

The ground dispatch owns its selection before position integration and terrain
contact. `Tails_Stand_Path (sonic3k.asm:27558-27567)` explicitly runs input,
Roll, screen boundaries, movement and AnglePos in that order. S1
`Sonic_LookUp (_incObj/01 Sonic.asm:465-483)` and S2
`Sonic_Lookup (s2.asm:36729-36756)` test Up before Down. KiS2's pinned local
source object `c336fed` retains that order (source lines 39297-39323) and its
later Roll returns below `$80` without writing Duck (39910-39924).
S3K `loc_11276 (sonic3k.asm:22650-22677)`, Tails'
`loc_14AA0 (sonic3k.asm:27900-27926)` and Knuckles'
`loc_172A8 (sonic3k.asm:31928-31956)` test Down before Up. The independent
later `SonicKnux_Roll (sonic3k.asm:23258-23275)` can replace Move's byte with
Duck below `$100`, including under move-lock. Its held Left/Right test must
observe the logical controller word even when Move filters directional input.
No native evidence or constant was derived from the failing fixture.

`PlayerGroundPoseRules.downBeforeUp` is the smallest semantic owner for the
standing tail's input precedence. It is independent of `movingCrouchThreshold`,
which describes the later roll check. Both belong to host movement; donor
capabilities donate neither. The cross-game values are:

| Rule | S1 | S2 | S3K | KiS2 | Donor composition |
| --- | --- | --- | --- | --- | --- |
| `downBeforeUp` | false | false | true | false | host value |
| `movingCrouchThreshold` | 0 | 0 | `$100` | 0 | host value |

The existing three-argument candidate constructor retains Up-first. The mutable
0.7 signature pin records the additional constructor/accessor, and the current
candidate's description records the semantic owner. No version, release
status, descriptor topology or published baseline changes.

`PlayableSpriteMovement.modeNormal` now finishes its pose selection and
`PlayableSpriteAnimation.publishGroundPoseAnimation` publishes an admitted native
byte before screen boundaries, position integration and AnglePos. The later
roll Duck selection clears the earlier LookUp projection. Normal crouch does
not overwrite an Up-first selection or run through Move's lock. The detached
profile consumes LookUp as well as Duck before its locomotion fallback.
Transient looking/crouching projections expire at the next movement dispatch;
the native animation byte remains in the existing player/controller snapshot.
There is no additional persistent manager, pending-byte cache or rewind authority.
Object-control and forced-animation ownership continue through their existing
gates. No shared runtime branch reads a game, character, fixture or route name.

The eight real-ROM cases retain every prior native expectation and strengthen
their witnesses: they check the selected byte immediately after movement,
continue each neutral/Up/Down/simultaneous result through the next air tick,
create a fresh configured player from the active character definition, attach
production ROM art, then restore every registered key and replay the air tick.
Move-lock covers both its final tick and a still-positive timer, held Down and
held horizontal input. Configured-player recreation is explicit test setup;
SpriteManager.restore is not claimed to create an absent configured main.
These remain solo 320px first-act component witnesses, not whole-act, team,
all-donor, load-boundary or native pixel certification.

### Implementation validation

The first focused implementation attempt did not execute tests: compilation
rejected an out-of-scope local rule reference in doGroundMove. It waited
**311 seconds** for admission and Maven stopped after **1m03s**. The stale
Surefire reports were the already-consumed baseline, not new results. The
reference was corrected before build-only compilation and renewed testing.

Build-only `maven_queue.py -Dmse=off test-compile -B` then passed in **1m40s**.
The next exact selection combined the 13 focused classes below with four
original-movie replay methods. It used `-Ptrace-replay`,
`-Dsurefire.forkCount=1 -Dsurefire.reuseForks=true`,
`-Dsurefire.runOrder=alphabetical`, the previously declared four absolute ROM
properties and offscreen/Java/Lua environment:

```text
-Dtest=TestGroundedDetachNativeProfiles,TestKnucklesDetachPoseRewind,TestScriptedVelocityAnimationProfile,TestPlayableSpriteAnimation,TestPlayableSpriteMovement,TestCrossGameRuleComposer,TestPerGameRuleArchitectureGuard,TestModApiSignatureSurface,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestHardwareTimingAuthorityGuard,TestS1Ghz1TraceReplay#replayMatchesTrace,TestS2Ehz1TraceReplay#replayMatchesTrace,TestKis2Ehz1TraceReplay#replayMatchesTrace,TestS3kAizTraceReplay#replayMatchesTrace
```

This completed **394 tests, 6 failures, 0 errors, 0 skips**, Maven **1m09s**
after **624s** admission wait. The failures are distinguished rather than
reported as a green candidate:

- The mutable API pin omitted the new `RECORD-COMPONENT` inventory line.
  Comparing compiled inventory with the pin showed exactly that one additional
  line and no obsolete entries; it was added without changing the guard.
- Three matrix cases reached fresh-player restore with incomplete test setup.
  S2 Sonic/Tails lacked their native-speed world binding, so the captured
  native speed-word payload disappeared. S3K Sonic lacked its power-up spawner,
  so its pending insta-shield could not be recreated. The test now binds the
  existing world authority and injects the original production spawner before
  restore. It still compares every registered key and every pose expectation;
  no production rewind owner or value was changed to accommodate the fixture.
- Original S2 EHZ1 and KiS2 EHZ1 replay failures require matched attribution,
  recorded below. They are not declared pre-existing from totals alone.

The other **388 cases pass**, including all 188 movement, 49 animation-manager,
35 semantic-profile, 25 hardware-authority, 9 cross-game-rule and 3 rule-architecture
cases; both old Knuckles components; all four S3K anchor selections; and the
original S1 and S3K movie methods. The simple level-loading selector executes
both concrete classes (36 + 7 cases). Five of eight profile cases complete all
new replay controls; the remaining three stop on the structural setup defects
above and are not certified by the passing cases.

S1 GHZ1 compares **3,905 rows** with zero physics, animation or bootstrap
errors/warnings. S3K AIZ compares **20,463 rows**, with zero errors/warnings and
`hcz_handoff_complete` reached. Its report advertises 20,798 planned rows;
compared rows are not silently equated with planned rows. Seven advertised aux
schemas remain absent: `velocity_write_per_frame`, `position_write_per_frame`,
`aiz_ship_loop_per_frame`, `sonic_record_pos_per_frame`,
`tails_cpu_normal_step_per_frame`, `aiz_boundary_state_per_frame` and
`aiz_transition_floor_solid_per_frame`. This is the ordinary ALL comparison
scope, not newly complete auxiliary observation or native pixel qualification.

S2 EHZ1 compares **5,852 rows** and reports **1,637 errors** (1,635 physics,
2 animation), zero warnings/bootstrap errors. First error is row 3667
`dynamic_art.edge[2].present`, expected false / actual true; the first Tails
animation span is rows 3667-3673, native Walk `$00` / engine Wait `$05`.
KiS2 EHZ1 compares **1,817 rows** with **93 errors** (91 bootstrap physics,
2 animation), zero warnings. First bootstrap error is `player_history.pos`,
expected `$0068` (slot `$19`) / actual `$003F`; the first animation difference
is row 289, native `$00` / engine `$20`.

For exact identity comparison, the SHA-256 of canonical sorted-key compact JSON
containing complete `bootstrap`, `errors` and `warnings` arrays is
`66d07c985ffef15bb1fa39c5a0a07415c2d97c8cd87f86a21b339fa719203ce3`
for S2 and
`c671376dd4874bfae19ee943980961db71445d11d810d9f2504c3330388d071d`
for KiS2. A bounded matched run restores only the three animation/movement
owner files to their exact `a77ace833` blobs. The new rule field and candidate
inventory remain present but unused by those ablated owners. Fixture bytes,
ALL scope, ROMs, flags, environment and catalogue are unchanged. This attributes
the behavior of the correction without creating another checkout or changing
any comparison expectation.

The ignored local `config.yaml` temporarily points `roms.directory` to the
verified original ROM root so production KiS2 catalogue resolution can find its
chip. Original bytes are backed up for exact restoration; no ROM is copied or
linked. The original supplied temporary Knuckles observer is copied read-only
into this checkout solely for the strict return-closure command and will be
removed before commit.

The matched two-method baseline completed **2 tests, 1 failure, 0 errors,
0 skips**; Maven **1m41s**, admission wait **562s**. KiS2 has the identical
93 errors and exact complete-array hash above. S2 is **clean on baseline**:
all 5,852 rows match with zero errors/warnings/bootstrap errors. Consequently
the unconstrained early publisher was a demonstrated regression, not an
inherited S2 frontier. That implementation is rejected.

The earliest causal disagreement is the standing Move gate. The S2 trace
shows Tails stationary at angle `$D0`, retaining Walk through row 3666; row
3667 detaches him with the same angle/inertia. `Obj02_NotRight (s2.asm:39685-39694)` branches to ResetScr before the zero-inertia Wait write
when `(angle + $20) & $C0` is nonzero. `Tails_SlopeRepel (s2.asm:40683-40706)` runs later and arms move-lock while setting InAir. The
unconstrained publisher called the locomotion resolver before that lock, and
its zero-speed fallback invented Wait even though Move made no native write.
The later lock retained that wrong byte into Animate/DPLC; the first
observable error was the extra dynamic-art edge, followed by two animation
spans. This is producer ordering, not a reason to alter DPLC timing input.

The revised early publisher is confined to actual admitted ground poses:
the standing tail requires pre-friction zero inertia and its existing flat-angle
gate. Actual LookUp or later Roll Duck projections also admit their native
writes, so the independent low-speed Roll Duck remains available on a slope
and under move-lock. Ordinary locomotion retains its existing owner. A new
independent vertical-wall inlet (`$C0`, no held buttons) in every real-ROM
profile checks that Move does not invent Wait before SlopeRepel's detach/lock;
it also repeats neutral air continuation and fresh-player registered replay.
No game/character/fixture predicate or new numeric runtime threshold is added.

Because this correction changes the early publication gate, the complete exact
focused/movie selection is repeated, adding only
`TestKnucklesFirstAnimationProbe#observeReturnedLevelClosure` and selecting
`-Ptrace-replay-r7` for the Knuckles closure. The selected ordinary replay
classes are untagged, so the explicit methods remain selected in that lane;
this does not replace the completed release-6-lane invocation above. It is
still an exact bounded selection, not broad smoke. Repaired matrix/API,
regression repair and original Knuckles closure qualification completed below.

### Final focused qualification and remaining frontiers

The final command completed **395 tests, 2 failures, 0 errors, 0 skips**;
Maven execution was **2m25s**, after **428s** admission wait. Its selector is
exactly the 17-class/method selector above plus
`TestKnucklesFirstAnimationProbe#observeReturnedLevelClosure`, with the same
ROM/environment/fork flags and `-Ptrace-replay-r7`. The test inventory confirms
that all explicit methods executed; nothing was filtered into an empty pass.

All **390 permanent focused cases pass**: the eight real-ROM profile cases
complete neutral, Up, Down, simultaneous, forced/object-control, both move-lock
ticks, held-horizontal and independent vertical-wall witnesses, plus their
applicable neutral-air and fresh-player registered replays. The two extended
Knuckles cases, 35 semantic profile, 49 animation, 188 movement, 9 cross-game
rules, 3 rule architecture, 11 API inventory, 25 timing authority and all four
S3K anchor selections pass. No expected value, snapshot key, comparator,
fixture, skip or control was weakened.

The original S1 GHZ1 (**3,905 rows**), S2 EHZ1 (**5,852 rows**) and S3K AIZ
(**20,463 compared rows**, HCZ handoff reached) replay methods are clean in ALL
scope, including bootstrap and both comparison groups. The S2 no-write
regression is repaired: its complete error/bootstrap/warning arrays now match
the clean matched baseline (hash
`4485e8ea75c8a35ee0c9e70da70b902f2ce6061e60842af06af7b2e9babd6332`).
The two remaining failures are explicit:

- KiS2 EHZ1 retains **93 errors**, including 91 bootstrap errors and two
  animation spans over 1,817 rows. Its complete arrays still have hash
  `c671376dd4874bfae19ee943980961db71445d11d810d9f2504c3330388d071d`,
  identical to the matched baseline and first candidate. No KiS2 route pass
  or fix to its bootstrap/glide frontier is claimed.
- The strict original Knuckles return-closure observer fails at production
  ownership, BK2 cursor **12011**, load generation 4, LEVEL, progression/ROM
  zone 0 act 0. At cursor 8424 its first returned update selects Duck `$08`
  and ROM mapping `$9B`; cursors 8425/8426 retain those bytes after the ground
  projection/snapshot expires. The live comparator's first strict error is
  row **446**, X native `$2026` / engine `$201B`. Segment 0 completes cleanly;
  segment 2 is **complete=false**, with **37,726 errors** (33,100 physics,
  4,626 animation), zero warnings/bootstrap errors/lagged frames. Both dynamic
  art gaps report zero failures. The observer case elapsed 13.059s within the
  final Maven invocation. Fresh suppressed completion observations remain
  KOS_DECOMPRESSION_QUEUE #35 at raw 2889/PRE_MAIN_LOOP (fingerprint
  `e0704fa4bac5e0862b2f4c633a83a74b2ab3897b7c0553770adae20ad64cc3cb`)
  and KOS_MODULE_QUEUE #24 at raw 2890/POST_OBJECTS (fingerprint
  `b218e238a2aff440adaeeb63571042dfa0ee0accaa18f1722284e498e8d55057`),
  each with no pending engine job. These match the earlier recorded frontier,
  group totals and ownership assertion; this is not a comparison of every
  downstream historical error identity. The native vine's phase, position and
  admission decision remain unproven, and strict unmatched timing ownership
  remains outside this animation repair.

The Maven command therefore returns failure. It is a bounded focused
qualification with attributed remaining frontiers, not a full chain, broad
smoke, full suite, all-product or native-pixel pass. The lead retains combined
smoke/integration. No release/API/policy publication, push or PR is performed.
The candidate API remains unpublished 0.7.0; only its mutable semantic field
inventory changes.

The final production/permanent-test/pin fingerprint is SHA-256
`71f11bfe818379e19db465a51654d2b7477042fd8cfa6123940aae5c639340c8`
(sorted repository path/NUL/bytes/NUL over the seven changed production files,
four permanent test files including the inherited Knuckles component, and the
0.7 pin). No runtime or permanent-test edit follows this final measurement.
Only evidence prose, exact ignored-configuration restoration, removal of the
task-owned temporary observer and consumed diagnostic cleanup follow. All
owned command sessions have completed; no job remains. Queue waiting and
Maven execution above are measured separately; total delivery wall time is
unmeasured. The final local commit identifies the complete source state.
