# Implicit state machines: inventory and first remediation

## Scope and ownership

This is a static inventory of state-machine families, followed by two bounded
interventions. It is not an exhaustive count of runtime machines or a claim of
whole-project correctness. Discovery started on `22dcba2a1`; the integration
branch starts on fetched develop `a3ab4c74e`. The intervening upstream commit
changes media configuration, not either lifecycle owner.

The parallel code-complexity effort owns contact/population/linked-child rewind
and draft PR 266. Its source and validation trees remain untouched. This work
uses a separate task branch and draft delivery against develop, without merging
or pushing develop. No rubric score is inferred from
these changes.

## Inventory

A machine is implicit here when transition authority or future behavior depends
on combinations of fields, retained resources, history or call order beyond a
single named phase. An explicit local enum can still participate in such a
protocol. ROM routine machines are intentional translations, not automatically
refactoring debt.

| Family | Main owners | Consequence for contributors |
| --- | --- | --- |
| Level admission and transitions | `LevelIterationAdmissionController`, `FreshLevelTransitionBoundaryController`, `LevelTransitionCoordinator` | Loaded, title-complete, published and playable are different boundaries. |
| Physical service and art readiness | `PlcFrameLifecycleCoordinator`, `HardwareTimingJob`, `KosinskiModuleQueue` | Prepared data, eligible service, publication and consumer claim are separate facts. |
| Trace runs | `TraceSessionLauncher`, run playback coordinator/walker | Comparison closure does not necessarily complete physical service or teardown. |
| Rewind | `LiveRewindManager`, release retry coordinator, `RewindRegistry` | Installation identity, pending release and restoration order matter beyond captured values. |
| Suspension | `GameLoopSuspension`, suspended gameplay | Parking preserves ownership; it does not authorize advancing or destroying the parked world. |
| Host pacing | `LiveFrameRuntime`, fixed scheduler | A host iteration, simulation tick and displayed picture need not correspond one-to-one. |
| Stage round trips | Special/bonus-stage and ending sessions | Provider retention, results, fades, return-state restoration and destination admission have separate lifetimes. |
| Player control | Playable sprite, movement, sidekick, abilities | Queued controls, one-tick markers, histories and effect timers affect subsequent transitions. |
| Object execution | Object manager, placement, execution and retirement | Existence, slot occupancy, spawn eligibility and same-frame execution differ. Reserved to the parallel effort where overlapping. |
| ROM actors | Objects, badniks, bosses, ending actors | Routine/subroutine, animation, timer and family signals jointly determine the next update. Preserve shipped semantics. |
| Zone systems | Events, zone runtime holders, water, scroll and palette owners | Camera, terrain, event routine and published presentation must progress coherently. |
| Checkpoints | Checkpoint/respawn coordinators | Selected restart state is a different contract from a complete rewind snapshot. |
| Sound | SMPS sequencer/driver, request pipelines | Track, voice, fade, tempo and request-admission state have distinct clocks and owners. |
| Audio presentation | Command batches, producer and voice registry | Applied, prepared, committed, rolled back and reverse-release work have different retry rules. |
| GPU presentation | Renderer, upload queues, batches and framebuffer scopes | Pending output and GL restoration require balanced ownership on failure paths too. |
| Input and recording | Input handler, text/recorded-input scopes, recording sessions | Suppressed gameplay may still require edge/cursor bookkeeping. |
| Live capture | `LiveCaptureController`, frame grabbers and recorder | Current audio source, detach obligation, GL readback and asynchronous finalization differ. |
| UI and Stageplay | Native menus, title/results providers, Stageplay/editor workspaces | Selection/playback status does not capture pending navigation or temporary control ownership. |
| Creator execution | Mod runtime/context, scene host and mutators | Registration, failure, disablement, visits and capability closure constrain retries. |
| Networking | Handshake, round engine, client session and outbound writer | Deadlines, generations and pending work must remain attached to the correct attempt. |
| Boot/resource scopes | Headless boot, ROM/asset/audio/GL scopes | Reused JVMs expose initialization/teardown obligations hidden by fresh-process tests. |
| Tooling and extensions | Maven queue/category control, release workflows, racing/examples | OS leases, requests, verdicts and extension run states need separate analysis; script/dependency coverage is incomplete. |

## Fresh-level admission

Owner: `src/main/java/com/openggf/LevelIterationAdmissionController.java`.
Two progression booleans had three reachable combinations: awaiting title,
awaiting the ordinary boundary, and awaiting assembly. A private phase now names
these directly. Whether a boundary exists remains LevelManager's authority.
The independent same-iteration hold still prevents reentering LEVEL on the load
iteration. Pause holds progression. Observation still precedes publication,
including when publication throws; retirement still follows successful assembly
and its observer. No world activation, object admission, identity or rewind
registration order was changed. No behavioral defect was established.

The worker's baseline-with-characterizations and candidate each ran the same 36
test identities, without failures, errors or skips:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestLevelIterationAdmissionController,TestLevelIterationHardwareTimingAdmissionOrder,TestGameLoopFreshLevelHandoff,TestFreshLevelBoundaryAfterSetup' test -B
```

Baseline queue/execution: 34m54s/1m46s. Candidate: 55m13s/1m32s.
An earlier candidate request exited 143 while queued, executing no tests.
The baseline is original runtime plus the new characterization tests; this is
not a broad-suite comparison. Worker source commit: `ffece6a6a`.

## Capture audio lease ownership

Owner: `src/main/java/com/openggf/capture/LiveCaptureController.java`.
The previous `audio` reference represented both a frame source and the real
producer lease. Refused detach deliberately retained that reference, but silence
fallback or restart could overwrite it. The repair keeps `audioLease` until
caller-thread detach succeeds, independently of the source used to produce the
current frame. Restart retries detach before acquisition. Continued refusal uses
silence without acquiring another lease. Background clearing does not detach.

Four deterministic witnesses failed on the original source: losing the producer
lease during drain fallback, failing to retire the lease on restart, acquiring
again while detach remains refused, and losing metadata-fallback cleanup. All
26 pre-existing controller cases passed in that RED run. Post-fix focused checks:

```bash
python3 tools/testing/maven_queue.py --normal -Dmse=off \
  '-Dtest=LiveCaptureControllerTest,LiveCapturePresentationCoordinatorTest,TestLiveCaptureCadenceBoundary,CaptureBufferOwnershipTest' test -B
```

Working tree subsequently committed as `43254c5c5`: 56 passed, no
failures/errors/skips. RED queue/execution:
34m20s/1m45s; GREEN: 53m49s/1m32s. An earlier GREEN request exited 143 before
admission after at least 61m14s waiting; its cause is unknown. Java 25 and Lua 5.4
were used; ROM discovery reported missing=none. These tests need no GL context.

## Review, remaining questions and delivery

Combined reviewed source: `6cb012490`, following capture cherry-pick `5c377304f`.
Opus 5.5 reviewed it at maximum effort with fast mode off, finding no code defect
in either change. It independently parsed the base and candidate catch sites
and inspected worker logs and compiled capture code, without running Maven.
The broad-catch guard fails at that source until three existing pins are
reconciled. The review approved a one-for-one replacement: ten capture sites
remain ten, seven unchanged, with no extra method or engine call sheltered.

The inner start boundary still reports acquisition/metadata failure and selects
silence; the outer boundary still reports FAILED if start cannot complete.
The close helper still returns detach failure so its caller can reach FAILED
or silence while retaining the real lease. Removing those boundaries would
strand STARTING/ACTIVE or lose recovery. The three old/new fingerprints are:

| Site | Old prefix | Reviewed replacement |
| --- | --- | --- |
| `start` inner | `51a9752d` | `a2499506accdee8cac2dfd1ca8122c78eaa2951a81f24b0e0688150f56444a77` |
| `start` outer | `b43f47a7` | `a1d5cf73e351fb5d8c8ec7e6232d7011957018d75df8674aa69543bf7a7d84bb` |
| `closeAudioOnCaller` | `e1062e6c` | `7f4d2856a8a29a754b892b48c3489ba6a99153f272d04128b239389ffcc047af` |

Only these fingerprints were replaced; count and debt reasons remain unchanged.
At review time the exact guard and combined source-matched validation remained
pending. The delivery validation below records their actual execution. Shared category jobs owned by the
parallel effort are not reusable for this branch's source and inputs. The dry
plan selects 4,483 smoke classes plus guards, two workers, with the runner's
40-minute execution cap per invocation excluding shared queue wait.

The review noted that the observer-failure test checks boundary completion, not
phase retirement ordering; it cannot prove that ordering independently. A
post-review extension exercises a subsequent notified load after observer
failure without inspecting private phase fields. This extension passed in the
combined 107-test request below. The real-AudioManager capture/backend-swap
fixture also ran seven passing cases; the worker witnesses use the raw producer.

Capture still admits start after terminal close in the inspected source, and an
old finalizer surviving timeout may interact with that restart. These are
unwitnessed follow-up hypotheses, not defects claimed fixed by the lease repair.
Other inventory families remain research targets, not remediated systems.
The reviewer also noted that other tools can arm a boundary without notifying
admission, potentially retaining stale progression; the old flags behaved the
same way. A refused lease across FAILED abort/restart lacks its own witness.
These remain research gaps, not reported new regressions.

Prefer local phases for mutually exclusive progression and distinct fields for
independent ownership obligations. Reject a generic state-machine framework,
mass conversion of ROM routines, or package splitting as evidence of reduced
complexity: none establishes a better lifecycle contract by itself.

This audit does not claim whole-suite certification. User wait is zero;
unmeasured coordination time remains unknown. Worker worktrees and uncommitted
submodule state remain reserved to the parent; they are not cleanup inputs here.

## Delivery validation: focused admission

The exact combined 12-class request at `6cb012490` plus the four supplied
review/doc changes executed 107 tests: 106 passed, one failed, no errors or
skips. The guard really ran (four cases, one failure), and the real
`com.openggf.audio.TestLiveCaptureSurvivesBackendSwap` ran seven passing cases.
All 30 controller cases, the 20 admission cases including the observer-recovery
extension, and the S3K fresh-release/ending witnesses passed.

The sole failure was
`com.openggf.tools.TestBroadCatchSwallowingGuard#mainSourcesHaveOnlyInventoriedLegacyHandlers`.
It reported two old `TraceCapturePresentationSetup.open` signatures without the
trailing `boolean`, and their two observed replacements with it. This is inherited
from `22dcba2a1`: its overloads preserve native-sprite-limit defaults and move the
same cleanup handlers into explicitly configurable overloads. The fingerprints
`4523cca6e5c9b9159a820caf45d81a82cdf9d297d02cf9cdfdbee142a48b87c6` and
`6890b2ce885ff34c942c65f6a389569d0ac79c8242e62cf887243e494781ddd6`
pin the identical attempted cleanup and handler bodies. Only the two method-signature
keys were corrected one-for-one; hashes, counts, reasons and protected cleanup
work are unchanged. No allowance was added or guard weakened. This small
mechanical prerequisite repair is additional to the three reviewed capture pins.

The first request waited 733 seconds for admission; Maven reported 1m43s
execution (launcher wall about 838s). Java 25, Lua 5.4 and surfaceless EGL were
selected; DISPLAY/WAYLAND_DISPLAY were unset; ROM discovery reported missing=none.
The exact combined request is being repeated after the two signature corrections.
The frozen baseline category run was submitted independently while focused
admission waited: 4,483 identical ordinary candidates, 91 batches, guards, two
workers. Its preparation passed in 120.59s. Bounded diagnostics are retained only
for the requested full negative-case/skip comparison, then acknowledged. No
category source tree will be edited or built while its run is active.

The repeated exact selection passed: **107 tests, zero failures/errors/skips**,
including four actual broad-catch guard cases and seven real-AudioManager
backend-swap cases (confirmed in Maven class lines and Surefire XML). Admission
wait was 660s; Maven execution 40.538s; launcher wall 701.03s. Both focused
requests used this command, without `-Pguards`:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestBroadCatchSwallowingGuard,TestLiveCaptureSurvivesBackendSwap,LiveCaptureControllerTest,LiveCapturePresentationCoordinatorTest,TestLiveCaptureCadenceBoundary,CaptureBufferOwnershipTest,TestLevelIterationAdmissionController,TestLevelIterationHardwareTimingAdmissionOrder,TestGameLoopFreshLevelHandoff,TestFreshLevelBoundaryAfterSetup,TestS3kFreshLevelReleaseVint,TestS3kEndingFreshLevelTerrainWait' test -B
```

Tested source was `6cb012490b05aaa8e7a9c856c34395b9eaecd503` plus the observer
extension and reconciled pins recorded here. Runtime was unchanged after the
combined review. The four supplied pending files are preserved together in the
evidence commit; the pin file additionally includes the two inherited signature
corrections above. Broad comparison and draft delivery remain pending. Sol owns
validation and routine delivery; no further agents, reviewers or observers were
started. User wait is zero; unmeasured coordination time remains unknown.

## Final matched smoke and guard comparison (7–8 November)

Validation owner: one Sol lead, with no additional agents, reviews, duplicate
observers, reused peer jobs or source edits during category execution. Both
requests reached terminal verdicts; no validation job remains live. Both
preparations passed. The candidate tested commit is
`bea21c02731aeed4f213a940fcec4f0ff8d98ff0`; this final audit update is documentation
only. Runtime remains the combined reviewed `6cb012490` source. The evidence
commit accounts for all four supplied pending files, including the observer
extension and all five one-for-one pin corrections (three reviewed capture
fingerprints and two inherited cleanup signatures). Counts/reasons did not grow.

Both actual plans selected the identical 4,483 ordinary source classes in all ten
categories, partitioned into 91 batches, plus separate guards. Ordinary execution
used two workers; guards used one. Both produced 4,431 ordinary reports and 103
guard reports. The baseline CLI calls its explicit `--category all` mode
`focused`; it still selected every ordinary smoke candidate. Neither plan's
`full` flag means the public-release full suite.

The environment for each command was Java 25
(`JAVA_HOME=/usr/lib/jvm/java-25-openjdk`), Lua 5.4 (`LUA_BIN=/usr/bin/lua5.4`),
`DISPLAY` and `WAYLAND_DISPLAY` unset, and explicit surfaceless EGL. The runner
forwarded `-Dmse=off` and the EGL property through preparation, every ordinary
batch and guards. ROM discovery found all six properties (`kis2`, `s3k`, S1/S2
REV00 and REV01), with no caller override or missing property. No ROM was copied,
linked, renamed or moved. Declared optional inputs were absent for display-backed
GL, S2 BK2, S3K observations, S1 BizHawk references and generated audio references.
EGL selection does not certify those omitted optional reference/display lanes.

Commands, each run in its own supplied worktree:

```bash
# Baseline, detached a3ab4c74e0ba2ea504a3a94a80fc93158c3a46d1
env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/run_categories.py --category all --workers 2 \
  --headless-gl surfaceless-egl --keep-diagnostics --run

# Candidate, bea21c02731aeed4f213a940fcec4f0ff8d98ff0
env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/run_categories.py --base a3ab4c74e --workers 2 \
  --headless-gl surfaceless-egl --keep-diagnostics --run
```

Bounded diagnostic retention supplied the explicitly requested full assertion and
skip comparison; it was not a pass cache. The defaults remained 40 execution
minutes per invocation, excluding shared queue waiting, and 10 minutes without
output. Neither request timed out, ran out of memory, changed source, omitted a
negative case, or stopped with incomplete coverage. No queue lock or admission
policy was changed. Owned detached launchers recorded stdout, PID and exit status
under each worktree's `target/`; requests survived their long queue waits.

| Input | Ordinary tests / failures / errors / skips | Guard tests / failures / errors / skips | Coverage |
| --- | --- | --- | --- |
| Baseline | 37,052 / 30 / 0 / 49 | 836 / 1 / 0 / 0 | 91/91 ordinary, 1/1 guards |
| Candidate | 37,071 / 30 / 0 / 49 | 836 / 0 / 0 / 0 | 91/91 ordinary, 1/1 guards |

The 19 additional ordinary cases are the task's characterizations. All 49 skips
are policy-allowed on both inputs; unexplained, required, undeclared-input and
unidentified skip counts are all zero. Missing ROM properties are none. The
candidate's actual guards passed; this is not a static-parser-only claim.

Exact terminal verdicts:

```text
VERDICT FAILED: head=a3ab4c74e0ba source=3e812022772f focused 4483/4483 candidate classes; ordinary 91/91 batches; guards 1/1 batches; tests=37888 failures=31 errors=0 skipped=49 (allowed=49 unexplained=0); missing ROM properties=none; skip inputs audio_reference_files=false,gl=false,s1_bizhawk_reference=false,s2_bk2=false,s3k_observations=false; bounded smoke selection, not a full-suite pass
VERDICT FAILED: head=bea21c02731a source=d582697784c2 change-based 4483/4483 candidate classes; ordinary 91/91 batches; guards 1/1 batches; tests=37907 failures=30 errors=0 skipped=49 (allowed=49 unexplained=0); missing ROM properties=none; skip inputs audio_reference_files=false,gl=false,s1_bizhawk_reference=false,s2_bk2=false,s3k_observations=false; bounded smoke selection, not a full-suite pass
```

Source/run provenance:

| Input | Tested SHA | Source fingerprint | Run |
| --- | --- | --- | --- |
| baseline | `a3ab4c74e0ba2ea504a3a94a80fc93158c3a46d1` | `3e812022772f9f441a228035fd47b5b9ad553f52de265dab60bd393cc25a5282` | `20261107T181546Z-b7feecc8` |
| candidate | `bea21c02731aeed4f213a940fcec4f0ff8d98ff0` | `d582697784c219ddeb9528fbd0d71813356f9cff905e6e1dd1f38228ac05ad5f` | `20261107T183537Z-19500305` |

Per-command timing, in seconds. Queue figures sum the 92 printed integer
admission waits in each input (91 batches plus guards). Execution includes
preparation and Maven lanes. The unattributed remainder includes preflight,
bookkeeping and queue-report rounding; it is not relabelled as execution or user
wait. Build-activity waiting was below 0.001s on each input. These overlapping
commands must not be added to infer delivery wall time. Coordination outside
these measured commands remains unknown.

| Input | UTC start → finish | Queue (reported) | Execution | User wait | Unattributed remainder | Command wall |
| --- | --- | --- | --- | --- | --- | --- |
| baseline | 2026-11-07T18:15:45+00:00 → 2026-11-08T02:54:33+00:00 | 29164 | 1941.13 | 0 | 22.84 | 31127.97 |
| candidate | 2026-11-07T18:35:36+00:00 → 2026-11-08T03:20:05+00:00 | 29610 | 1836.75 | 0 | 22.31 | 31469.06 |

### Exact negative-case comparison and reviewed text differences

The complete ordinary failure identities, kinds, exception types and full
assertion lines were compared, along with every skip identity and complete causal
line. Both lanes were validated against their counters and completion/exit status.
There were no omitted, duplicate, truncated or unavailable negative identities.

The existing tool ran without normalization options:

```bash
python3 tools/testing/compare_category_outcomes.py \
  --baseline target/lifecycle-baseline-summary.json \
  --results target/category-tests/20261107T183537Z-19500305/results.json \
  --output target/lifecycle-comparison.json
```

It returned **1**, with `negative_cases_unchanged=false`: zero new or missing
failures, zero new/missing skips, zero changed skip causes, no candidate guard
failure/skip, and exactly four changed failure strings. This literal result is
preserved as such, rather than called a comparator pass. All thirty ordinary
failure identities, kinds and exception types match; 26 full assertions match
literally. The other four were resolved by the following bounded source review:

- `mods.code.TestThreeIslandsScene#physicalTrailsConnectFieldsAndBattleUsesTheEncounterField`:
  both assertions expect `threeislands.screen.FieldScreen` and receive
  `threeislands.screen.StoryScreen`. Baseline identity suffixes are
  `FieldScreen@218d1898` / `StoryScreen@7f369239`; candidate suffixes are
  `FieldScreen@1e2c023a` / `StoryScreen@6392fc05`. Every other character of the
  complete assertion matches. At `TestThreeIslandsScene.java:485`, the unchanged
  test compares the original screen with the current screen. Both final classes
  directly implement `Screen`, inherit `Object.equals/hashCode/toString`, and
  have no override supplying gameplay values through these strings. Their source
  and the test/interface blobs are identical to the baseline. The two suffixes
  are object identities, not changed screen states.
- `tests.TestS3kMhzAuthoredRoute#freshSonicCompletesActOneAndReplaysItsLiveInteractions`,
  `tests.TestS3kMhzTailsAuthoredRoute#freshTailsCompletesActOneAndReplaysItsLiveInteractions`,
  and `tests.TestS3kMhzWideAuthoredRoute#wideSonicCompletesActOneAndReplaysItsLiveInteractions`:
  the unchanged owners compare `Set.of(...)` with a collected `HashSet` at
  lines 79, 75 and 89–91 respectively. Only the expected set's display order
  differs. Expected and actual membership are exactly equal across sources,
  contain no duplicates, and the complete actual rendering is also identical.
  Set equality does not assert iteration order. The three owner source blobs are
  unchanged. The exact compared memberships are recorded below.

All shortened identities in this section and the inventories carry the prefix
`com.openggf.`. No comparator, test, guard, allowance, gameplay value, clock,
frame index or trace data was modified to resolve these textual differences.
No generic hash/order normalization was introduced. The independent SSZ blob
normalization option was not used.

| MHZ Set assertion | Expected members on both sources | Actual members on both sources |
| --- | --- | --- |
| Sonic | act-two-released, defeat, first-hit, last-hit-approach, pulley, sticky-vine | pulley |
| Tails | act-two-released, defeat, early-traversal, first-hit, last-hit-approach, lower-approach, upper-flight | early-traversal, lower-approach, upper-flight |
| Wide Sonic | act-two-released, arena-approach, before-pulley, boss-hp-1, boss-hp-2, boss-hp-3, boss-hp-4, boss-hp-5, defeat, early-traversal, incoming-results, lower-loop, pulley-pull, pulley-release, spike-passage, sticky-vine, upper-loop | arena-approach, before-pulley, early-traversal, lower-loop, pulley-pull, pulley-release, spike-passage, sticky-vine, upper-loop |

The sole baseline guard failure is
`tools.TestBroadCatchSwallowingGuard#mainSourcesHaveOnlyInventoriedLegacyHandlers`.
Its full assertion contains only the two old `TraceCapturePresentationSetup.open`
signatures and their trailing-boolean replacements, with unchanged cleanup/body
hashes, described above. Candidate guards have no failure or skip. This inherited
mechanical repair removes that failure without growing any allowance.

After this explicit review, **no new or worsened failure is observed**. Thirty
ordinary failures remain; the runs are not green and neither establishes ROM
route certification, exhaustive coverage, a public-release full-suite pass, or a
trace-frontier fix. No unrelated route, scene, network or reference-producer fix
was attempted.

### Inherited ordinary failure inventory

Every entry below has kind `failure` and exception type
`org.opentest4j.AssertionFailedError`; both inputs have zero errors. These are
concise causal summaries, not substitutes for the full assertion comparison
above. Parameter indices are part of the exact test identity.

| Identity (prefix `com.openggf.`) | Observed cause |
| --- | --- |
| `mods.code.TestThreeIslandsScene#tailsJoinsInTheFieldAndTheTravellingShopReturnsToTheSameSpot` | expected: <true> but was: <false> |
| `mods.code.TestThreeIslandsScene#aSonic1ZonePlaysItsOwnRomSongWithoutPlaceholderSwitches` | expected: <DUNGEON> but was: <STORY> |
| `mods.code.TestThreeIslandsScene#startupIsPlayableAndArrivalDialogueAndRestStayInTheField` | expected: <FIELD> but was: <STORY> |
| `mods.code.TestThreeIslandsScene#greenHillMysteriesUseRealInteractionsAndPersistThroughContinue` | expected: <FIELD> but was: <STORY> |
| `mods.code.TestThreeIslandsScene#physicalTrailsConnectFieldsAndBattleUsesTheEncounterField` | Expected FieldScreen, received StoryScreen; identity-only string variation reviewed above. |
| `mods.code.TestThreeIslandsScene#allStoryLandmarksHavePlayableInteriorsWithIndependentGatesSavesAndRewards` | ghz enters through its actual doorway ==> expected: <DUNGEON> but was: <MENU_ROOT> |
| `mods.code.TestThreeIslandsScene#travellerDialogueWaitsForTheDiscoveryOrRescueItDescribes` | expected: <STORY> but was: <MENU_ROOT> |
| `mods.code.TestThreeIslandsScene#explorationUsesTwoAxesAndStoryDiscoveriesPersistAcrossContinue` | a discovery opens dialogue ==> expected: <STORY> but was: <FIELD> |
| `mods.scene.host.TestPrivateLobbyScreenRendering#actualLobbyAndFullTextPixelsSupportMultipleReadablePages` | Right reaches new full-text pixels ==> expected: <false> but was: <true> |
| `net.host.TestHostMasterLink#hostCloseDiscardsPendingTunnelOpenAndItsCleanupCell` | expected: <0> but was: <1> |
| `tests.TestLrzSszCheckpointRoutes#ordinarySszOpeningPhysicallySavesTheFirstAuthoredPost` | ordinary opening remains alive at input 6499 ==> expected: <false> but was: <true> |
| `tests.TestS3kCnz1MinibossCompletionEdge#placedMinibossOrdinaryHitsDriveDefeatResultsAndReleasedCnz2(Row)[2]` | sonic+solo@320: fixed input did not finish the edge; hits at 1150/1593/2046, no kill/sign/results/load/title completion. |
| `tests.TestS3kCnz1MinibossCompletionEdge#placedMinibossOrdinaryHitsDriveDefeatResultsAndReleasedCnz2(Row)[4]` | sonic+solo@800: P1 died at frame 1564, expected alive. |
| `tests.TestS3kCnz1MinibossCompletionEdge#recordedOrdinaryInputReplaysAndRewindsEveryEdgeBoundaryTwice(Row)[2]` | sonic+solo@320: fixed input did not finish the edge; hits at 1150/1593/2046, no kill/sign/results/load/title completion. |
| `tests.TestS3kCnz1MinibossCompletionEdge#recordedOrdinaryInputReplaysAndRewindsEveryEdgeBoundaryTwice(Row)[4]` | sonic+solo@800: P1 died at frame 1564, expected alive. |
| `tests.TestS3kCnz1MinibossCompletionEdge#truncatedAfterApproachNeverCompletesTheEdge` | sonic+solo@320: fixed input did not finish the edge; hits at 1150/1593/2046, no kill/sign/results/load/title completion. |
| `tests.TestS3kFreshLevelRngState#actualFreshBoundaryClearsRngAndTheRegisteredWorldRestoresIt(int, int)[2]` | native D01 skips the ordinary title owner ==> expected: <false> but was: <true> |
| `tests.TestS3kIcz1PhysicalLowerPlatformReplay#nativeSonicContactsAndRidesTheLowerPieceWithTwoFreshWorldReplays` | actual final neutral crosses the sampled turn ==> expected: <true> but was: <false> |
| `tests.TestS3kMhzAct2AuthoredRoute#incomingRoutesCompleteActTwoWithLiveRewindBoundaries(String, int)[1]` | expected: <[defeat, ship-carry, capsule-results, weather-fade, chase-hit, fbz-loaded, ship, admission, last-hit]> but was: <[]> |
| `tests.TestS3kMhzAct2AuthoredRoute#incomingRoutesCompleteActTwoWithLiveRewindBoundaries(String, int)[2]` | cold route death at 36000 ==> expected: <false> but was: <true> |
| `tests.TestS3kMhzAct2AuthoredRoute#incomingRoutesCompleteActTwoWithLiveRewindBoundaries(String, int)[3]` | cold route death at 26152 ==> expected: <false> but was: <true> |
| `tests.TestS3kMhzAct2EntryHeadless#coldEntryPressesLiftsAndReleasesNativeTeamWithReplay(WidescreenAspect, String, String)[12]` | level event must preserve the cutscene-owned minimum ==> expected: <872> but was: <896> |
| `tests.TestS3kMhzAuthoredRoute#freshSonicCompletesActOneAndReplaysItsLiveInteractions` | Expected 6 checkpoint members, observed only pulley; exact membership above. |
| `tests.TestS3kMhzTailsAuthoredRoute#freshTailsCompletesActOneAndReplaysItsLiveInteractions` | Expected 7 checkpoint members, observed 3; exact membership above. |
| `tests.TestS3kMhzTeamAuthoredRoute#nativePairCompletesActOneAndReplaysItsLiveInteractions` | cold route death at 26152 ==> expected: <false> but was: <true> |
| `tests.TestS3kMhzWideAuthoredRoute#wideSonicCompletesActOneAndReplaysItsLiveInteractions` | Expected 17 checkpoint members, observed 9; exact membership above. |
| `tools.TestDezIncomingFinalRouteCapture#incomingFinalFightRestoresAndReplaysEveryPhase(int)[1]` | death at 14633 ==> expected: <false> but was: <true> |
| `tools.TestDezIncomingFinalRouteCapture#incomingFinalFightRestoresAndReplaysEveryPhase(int)[2]` | death at 14638 ==> expected: <false> but was: <true> |
| `tools.TestIcz2ColdCubeBridgeRoute#coldSonicShattersThePlacedCubePressesButton2AndCrossesTheFallingBridgeWithWorldReplay` | expected: <6> but was: <5> |
| `tools.audio.completerun.s3k.TestS3kCompleteRunReferenceProducer#fixedProducerRejectsEveryMismatchedTypedRequestBeforePublication` | Unexpected exception type thrown, expected: <java.nio.file.FileAlreadyExistsException> but was: <java.lang.IllegalArgumentException> |

### Complete allowed skip inventory

All 49 exact identities and complete causal lines are identical between inputs.
The shared release policy classifies every entry as allowed with the declared
optional inputs above; there is no missing-ROM skip or unexplained skip. Guard
skips are zero.

| Identity (prefix `com.openggf.`) | Complete cause |
| --- | --- |
| `audio.TestSmpsRepeatedPlaybackBenchmark#repeatedPublicMusicAndSfxPlaybackEmitsStableRawSamples` | System property [openggf.audio.repeatedPlaybackBenchmark] does not exist |
| `audio.presentation.TestOpenAlIsolatedContext#isolatedOwnerOpenPumpAndClosePreserveTheGameplayContext` | org.opentest4j.TestAbortedException: Assumption failed: Requires explicitly isolated OpenAL null driver |
| `editor.project.TestEditorHudPictures#captureControllerHud` | org.opentest4j.TestAbortedException: Assumption failed: Explicit HUD picture refresh only |
| `editor.project.TestStageplayHudPictures#captureAnimatedCameraFormForNativeWorld` | org.opentest4j.TestAbortedException: Assumption failed: Explicit Stageplay HUD picture refresh only |
| `editor.project.TestStageplayHudPictures#captureCollisionRouteAndVisualFootprintForms` | org.opentest4j.TestAbortedException: Assumption failed: Explicit Stageplay HUD picture refresh only |
| `editor.project.TestStageplayHudPictures#captureCompleteMenuSurvey` | org.opentest4j.TestAbortedException: Assumption failed: Explicit menu survey only |
| `editor.project.TestStageplayHudPictures#captureDayoffAudioCue` | org.opentest4j.TestAbortedException: Assumption failed: Explicit HUD picture refresh only |
| `editor.project.TestStageplayHudPictures#captureObjectCastArenaRestrictionAndModeForm` | org.opentest4j.TestAbortedException: Assumption failed: Explicit Stageplay HUD picture refresh only |
| `editor.project.TestStageplayHudPictures#captureReadabilityCheckpoint` | org.opentest4j.TestAbortedException: Assumption failed: Explicit HUD picture refresh only |
| `editor.project.TestStageplayHudPictures#captureScreenplayImportRefineAndExportForms` | org.opentest4j.TestAbortedException: Assumption failed: Explicit Stageplay HUD picture refresh only |
| `editor.project.TestStageplayHudPictures#captureStageplayCastTimelineAndCommands` | org.opentest4j.TestAbortedException: Assumption failed: Explicit Stageplay HUD picture refresh only |
| `editor.project.TestStageplayRouteOverlayPictures#captureProposedFailedAndAppliedRoutePlayback` | org.opentest4j.TestAbortedException: Assumption failed: Explicit HUD picture refresh only |
| `game.rewind.TestLiveRewindCheckpointCost#compareCheckpointCadencesOnTheSameRecordedRoute` | System property [openggf.checkpoint.measure] does not exist |
| `game.rewind.TestRewindTorture#tortureProgressiveLongRewinds` | org.opentest4j.TestAbortedException: Assumption failed: Long-running soak profile; excluded from normal runs — run manually with -Drewind.soak=true |
| `game.rewind.TestS3kRewindAllocationProbe#measure` | System property [openggf.rewind.alloc.measure] does not exist |
| `game.sonic3k.TestS3kCnzVisualCapture#captureCnzReferenceFrames` | org.opentest4j.TestAbortedException: Assumption failed: CNZ visual capture environment was not initialized |
| `game.sonic3k.TestS3kCnzVisualCapture#captureTraversalObjectReferenceFrames` | org.opentest4j.TestAbortedException: Assumption failed: CNZ visual capture environment was not initialized |
| `game.sonic3k.objects.TestS3kAiz1CompatibilityRoutes#axisRouteCompletes(int, String)` | System property [openggf.aiz1.routes] does not exist |
| `game.sonic3k.objects.TestS3kAiz1SpringRecovery#liveSpringJumpCrossesAndReplaysWhileWalkingIsRejected(int, String)` | System property [openggf.aiz1.recovery] does not exist |
| `game.sonic3k.objects.TestSozAct1VictoryCapture#captureVictoryAndHandoff` | System property [soz.act1.victory.capture] does not exist |
| `game.sonic3k.objects.TestSozColdAct1Capture#fixedControllerRouteReachesVisiblePlayableAct2FromColdAct1` | System property [soz.cold.act1.capture] does not exist |
| `game.sonic3k.objects.TestSozColdAct2Capture#fixedControllerRouteReachesVisibleLavaReefFromColdAct2` | System property [soz.cold.act2.capture] does not exist |
| `game.sonic3k.objects.TestSozEndBossVictoryCapture#captureBattleAndLrz` | System property [soz.endboss.victory.capture] does not exist |
| `game.sonic3k.objects.TestSozMinibossCapture#positionedAwakening` | System property [soz.miniboss.capture] does not exist |
| `graphics.TestScrollBufferUploadNative#arraysAndViewsUploadExactValuesAcrossResourceAndContextRecreation` | System property [openggf.scrollNative] does not exist |
| `graphics.TestSlotWindowGpuPassNative#pixelsAndDrawStateSurviveResizeCleanupAndContextRecreation(boolean)` | System property [openggf.slotNative] does not exist |
| `graphics.TestTilemapUniformLifetimeNative#uniformWritesSurviveRepeatedCachingAndProgramReplacement` | System property [openggf.shaderNative] does not exist |
| `graphics.shaderlib.TestDisplayShaderPackDiagnostics#writeCompatibilityReportForLocalShaderPack` | org.opentest4j.TestAbortedException: Assumption failed: Set -Dshaderlib.diagnostic.enabled=true to scan a local shader pack |
| `level.TestLevelRendererBackgroundSamplingPerformance#captureLiveBackgroundSamplingScenes` | org.opentest4j.TestAbortedException: Assumption failed: enable with -Dopenggf.capture.backgroundSampling=true |
| `level.TestLevelRendererBackgroundSamplingPerformance#postWarmupRenderSamplingAllocationProbe` | org.opentest4j.TestAbortedException: Assumption failed: enable with -Dopenggf.measure.backgroundSampling=true |
| `level.objects.TestObjectRewindTypeSafetyDispatchPerformance#measureMixedRouteDispatchAllocationAndTime` | System property [openggf.performance.rewindDispatch.measure] does not exist |
| `mods.code.TestSecondStrikeCheckpointGameplay#nativePost476EarnedInvitationDeathReloadAndForwardEscape` | System property [secondstrike.checkpoint.jar] does not exist |
| `mods.code.TestSecondStrikeCheckpointGameplay#nativePost476MatchedStockPostDeathReloadControl` | System property [secondstrike.checkpoint.jar] does not exist |
| `mods.code.TestSecondStrikeCheckpointGameplay#nativePost697DeathReloadStartsStoppedAtZeroRingsAndEscapesForward` | System property [secondstrike.checkpoint.jar] does not exist |
| `mods.code.TestSecondStrikeCheckpointGameplay#nativeRemainingPostsDeathReloadAndForwardEscape(int)` | System property [secondstrike.checkpoint.jar] does not exist |
| `mods.code.TestSecondStrikeGiantGameplay#nativeSlotPersistsPlayedGiantEmeraldAcrossFreshMenuSession` | System property [secondstrike.giant.jar] does not exist |
| `mods.code.TestSecondStrikeGiantGameplay#originalGiantPlaysModifiedBlueSphereAndReturnsWithOneEmerald` | System property [secondstrike.giant.jar] does not exist |
| `mods.code.TestSecondStrikeInvitationGameplay#zeroRingActTwoInvitationPlaysNativeGlowingSpheresAndReturnsExactlyOnce` | System property [secondstrike.invitation.jar] does not exist |
| `mods.code.TestSecondStrikeInvitationGameplay#zeroRingInvitationPlaysNativeGumballAndReturnsExactlyOnce` | System property [secondstrike.invitation.jar] does not exist |
| `mods.code.TestSonicSurvivors#balanceProbe` | org.opentest4j.TestAbortedException: Assumption failed: opt-in diagnostic |
| `tests.TestCPZObjectBugs#testSpinTubeForcesRolling` | org.opentest4j.TestAbortedException: Assumption failed: Spin tube at (1920,896) did not capture/release Sonic |
| `tests.TestSozAct1ArenaCapture#naturalArenaAdmission` | System property [soz.act1.capture] does not exist |
| `tests.TestSozConnectedMechanismCapture#capture(Scene)` | System property [soz.connected.capture] does not exist |
| `tests.TestSozPostBossRedrawCapture#capture(int)` | System property [soz.postboss.redraw.capture] does not exist |
| `tools.TestS3kSlotsGlassNative#glassOccludesPlayerAfterRealBonusFrame(String, int)` | System property [openggf.test.gl.native] does not exist |
| `tools.TestSecondStrikeHczAirRecovery#zeroRingNativeBubbleRefillsReturnsAndRecreatesForWholeWorldReplay` | System property [secondstrike.hcz.air.mod] does not exist |
| `tools.TestWestMgzBossNativeArena#packagedCreatorKnucklesHasAnOrdinaryDamageFreeSideResponse` | System property [secondstrike.mgz.packaged.arena] does not exist |
| `tools.audio.parity.TestS1OpenGgfAudioCapture#capturesTheCompleteReferenceControlledInterval` | org.opentest4j.TestAbortedException: Assumption failed: local deterministic BizHawk reference required |
| `tools.audio.timeline.TestS1Ghz1OpenGgfAudioTimelineCapture#captureRequestedOutput` | org.opentest4j.TestAbortedException: Assumption failed: no local OpenGGF timeline capture was requested |

### Delivery and remaining ownership

The comparison qualifies the separately authorized draft against develop, with
these inherited failures and research hypotheses disclosed. It does not authorize
merging or pushing develop, next or master. Only the task branch is a push input.
The candidate branch/worktree stays available for the draft. The supplied clean,
detached baseline is the only worktree eligible for removal after diagnostic
consumption and delivery inspection. Parent-owned worker trees, branches and
unknown dirty submodule state remain untouched and reserved to the parent.

Remaining hypotheses are unchanged: start after terminal close/stale finalizer,
a tool-armed fresh boundary without admission notification, and the distinct
FAILED-abort/refused-lease witness. The observer extension proves subsequent
notified-load recovery, not direct private-phase retirement ordering. Other
inventoried state-machine families are research targets, not remediated systems.

Both retained category diagnostic directories were acknowledged with
`run_categories.py --acknowledge 20261107T181546Z-b7feecc8` in the baseline and
`run_categories.py --acknowledge 20261107T183537Z-19500305` in the candidate after
the evidence above was preserved. Both commands exited zero with “Diagnostics
deleted.” No raw Maven logs, build trees, generated comparison JSON or pass
receipts are committed. All required validation is consumed; draft publication
and the supplied baseline's final cleanup follow under the stated ownership.

## Second increment: wider lifecycle research (8 November)

The user authorized continuing the wider work. Develop advanced to
`33c3b8e56`; that upstream fix restores the original trace-capture setup methods
and their pinned cleanup handlers. The task tree merges that base before new
workers edit it. Consequently the two signature-key adjustments from the first
increment are retired in favor of upstream's original rows; only the three
reviewed capture fingerprints remain different. Historical validation above
still describes its original inputs, not this new merged source. No catch
allowance is added.

This increment investigates terminal capture close/finalizer ownership, checks
whether tool-armed fresh boundaries really leave admission state stale, and
examines networking pending-work retirement beginning with the inherited
HostMasterLink close witness. The existing PR266 contact/population/reconstruction
owners remain outside scope. New source requires focused checks, Opus review
and a new appropriate source-matched comparison before updating draft269.
No validation of the new merged tree is claimed yet.

### Fresh-boundary ownership: corrected hypothesis

Read-only Sol research on `4eb5d5e55` did not establish the suspected stale
ordinary-admission defect from the two cited tool/controlled load paths. The
live GameLoop retains one admission controller, but controlled stepping returns
through ControlledLevelIteration before using it. HOLD-to-ADVANCE stays in the
controlled runtime. RecordingFrameDriver owns a separate normal-title, initial
publication and ordinary-boundary protocol; it does not share that live
controller. Ordinary `doZoneAct`, reset and title completion overwrite the
relevant observations. A mock changing pending false-to-true alone would not
prove a production stale-phase path.

| Host | Authority and progression |
| --- | --- |
| Ordinary live | LevelManager owns the boundary; GameLoop's controller observes title completion, publication admission and assembly. |
| Controlled live or recording | ControlledFrameRuntime remains the dispatcher across HOLD/release; it bypasses ordinary admission. |
| Recording/headless | RecordingFrameDriver owns its own title/load-row/publication observations. |
| Full visual trace | Prepared-level adoption keeps the live controller and observes its launch barrier. |
| Special-stage return | Its actual load/assembly/title callbacks explicitly enter title presentation before live admission resumes. |

External clearing can leave a phase inert; no concrete rearming path before
notification, reset or another title observation was established. A different
controlled-native-destination question remains: which owner consumes the new
normal title request and finishes a fresh boundary before ADVANCE? Explicit
`CourseControl.finishInitialPresentation()` is not evidence that passive native
destination handling supplies it. A real S3K transition witness, including the
synchronous-load and explicit-omission contracts, is still required. Do not
reset ordinary admission or introduce a shared dispatcher on this evidence.

### Capture terminal owner lifetime

Sol's `8e6fc3547` adds one monitor-guarded closed fact, separate from the public
recording phase and the retained real audio lease. Close marks owner closure
before waiting. Start cannot acquire audio, GL or encoder resources afterward,
including while close is in progress. Ordinary user-stop/FAILED restart remains
possible while the owner is open; repeated terminal close still retries an owed
audio detach. This removes the demonstrated later-session target of an old
finalizer without adding an enum value or generation framework.

Eight RED cases on original runtime first produced four assertion failures and
two stale-viewport NPE errors, with zero skips. The asserted RED made those
observed NPEs explicit expectations, exposing all six intended failure cases
without runtime changes. GREEN passed 92 cases without failures/errors/skips,
including real AudioManager backend swaps, bounded shutdown, caller-thread
cleanup, clocked silence and final-frame ownership controls. Exact selectors:

```bash
python3 tools/testing/maven_queue.py --normal -Dmse=off \
  '-Dtest=LiveCaptureTerminalCloseTest,LiveCaptureControllerTest#repeatedTerminalCloseRetriesRealLeaseRefusedOnForeignThread' test -B
python3 tools/testing/maven_queue.py --normal -Dmse=off \
  '-Dtest=LiveCaptureTerminalCloseTest,LiveCaptureControllerTest,CaptureBufferOwnershipTest,TestLiveCaptureCadenceBoundary,CapturedFrameTest,CaptureRecorderTest,LiveCapturePresentationCoordinatorTest,EncoderSinkTest,DrainPcmAudioTapTest,TestLiveCaptureSurvivesBackendSwap' test -B
```

All used Java 25, Lua 5.4 and unset displays. Initial RED queue/Maven: 36s/90s;
asserted RED: 13s/32.648s; GREEN: 32s/95s. Wrapper walls were 126.763s, 46.294s
and 128.147s; residual overhead is unseparated, user wait zero. The parse-only
catch comparison found all ten fingerprints unchanged; an executed combined
guard pass is still pending. No broad validation of this source is claimed.

### Networking completion witness, not runtime leakage

Sol used a real host with a held authority-completion callback. Pending open
admission charged five items; host close discarded queued work and retained two
running obligations; authority publication retained one profile cell; room
executor termination returned to zero. The remaining cell was attributed to
this host's profile account. Late opens and repeated retirement did not change
it. The inherited fixture asserted zero immediately after authority publication,
which precedes profile release. That controlled old-fixture witness failed with
`expected: <0> but was: <1>`. No networking runtime defect was established.

Parent's `fe3c58f4f` therefore changes the fixture completion predicate to the
existing public `retirementReceipt().ioReleased()` and then retains the exact
zero-retention assertion. The bounded wait is unchanged; it does not wait for
the ledger itself to become zero. A promoted latch-controlled test pins the
distinction between authority and physical cleanup, including account attribution
and late-open/repeated-retirement controls. No runtime source, protocol or
reservation allowance changes.

The worker's RED selector was
`TestHostMasterLink#controlledAuthorityPublicationExposesInheritedTeardownWitness`
(one expected assertion failure, no errors/skips; queue 92s, Maven 35.080s). Its
original single pending-open test passed (queue 81s, Maven about 92s); the three
class diagnostic control run passed 25 cases (queue zero, Maven 35.881s). Parent's
corrected fixture and promoted test then passed all 25 cases with no failures,
errors or skips, Maven 34.054s:

```bash
env JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py --normal -Dmse=off \
  '-Dtest=TestHostMasterLink,TestWorkBudget,TestOutboundRetirement' test -B
```

This demonstrates a sufficient explanation for the same inherited assertion, not
a unique retrospective attribution of historical runs without account evidence.
The runtime must not release a running profile cell merely to satisfy teardown.

### Capacity and current delivery boundary

The first controlled-transition witness delegate never started: T3 failed to
provision its worktree, without a more specific Git error. The worktree filesystem
had roughly 526 MB free. Parent reclaimed only generated target directories from
the two completed first-wave workers after verifying their source commits were
accounted for in draft269. All source, branches and inherited submodule changes
were preserved; a cleanup receipt remains outside the source tree. The witness
will use the existing isolated parent task checkout rather than allocate another
copy on that filesystem. This is an infrastructure failure, not a test outcome.

The merge, capture repair, fixture correction and these research notes remain
local. Draft269 still has its first-increment head. New combined Opus review and
source-matched verification are required before its update; the earlier broad
results cannot certify this merged source. Other inventoried families remain
research targets, not completed remediation.
