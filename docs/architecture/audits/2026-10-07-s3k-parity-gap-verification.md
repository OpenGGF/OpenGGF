# S3K parity-gap verification — 2026-10-07

Base: `09282b17305cb5794e43a26855cd2b9543b4ff5f`; development tree
`.worktrees/ai-parity-swarm-20261007-s3k`, branch
`bugfix/ai-parity-swarm-20261007-s3k`. The coordinator owns integration,
release prose, the central frontier ledger, and broad validation.

## Prior work inspected without changes

Both `${OPENGGF_WORKTREE_ROOT}/s3k-aiz2-sidekick-r1` (HEAD `ffa3c20e7`) and
`${OPENGGF_WORKTREE_ROOT}/s3k-aiz5-sidekick` (HEAD `139fb4aed`) were clean when
inspected. Their August measurements are historical evidence, not a matched
baseline for this October tree.

The AIZ5 branch's `498f0e34e` derives the post-defeat waits from the
`BossDefeated_StopTimer` fallthrough into `BossDefeated` (`$3F`) and
`loc_85674` (`(2*60)-1`). Current `AizEndBossInstance` already implements
these values. No integration of that older commit is needed.

Its `bd30d61de` describes a reverted diagnostic that moved the defeat one
dispatch later: capsule Y matched 62/62 observations, P2 triggered at row 6006,
and the old row 6000 `sidekick_x` frontier moved to row 6111 `y`. This is a
causal hypothesis for a current reproduction, not authority to delay a boss.

The AIZ2 branch's `1f277c251` attributes old HCZ row 1433 `y_speed` to the
Mega Chopper's `-$100` defeat bounce one dispatch early. Current production
prepares touch snapshots before player physics, so the older frame-order
claim must be re-established before changing a shared owner.

## LBZ boundary inspection

The committed LBZ slice physics rows show the player unharmed at row 23533
(`rings=$15`, `routine=2`, `x_speed=$16F`) and hurt at row 23534 (`rings=0`,
`routine=4`, `x_speed=$200`, `y_speed=-$400`). Thus the campaign ledger's
“unwanted hurt” description does not establish an entirely spurious contact;
a one-dispatch lead is a candidate. Aux evidence was grouped by its `frame`
field, not by JSONL line number. Nearby objects include Ribot's active head
`loc_8C370` and visual links `loc_8C522`.

### Confirmed source discrepancy under investigation

`RibotActiveChild.update` creates its visual children, then runs the active
state in the same dispatch. ROM `loc_8C396` instead initializes attributes,
stores origins, and branches to `loc_8C594`, whose child-creation helper
returns. It does not execute the active dispatch `loc_8C3BC` or the subtype 4
orbit routine `loc_8C41E` on initialization. The existing unit test expected
an orbit displacement on the first child dispatch; its assertion now checks
the retained origin before the next dispatch begins the orbit.

The earlier [Ribot attribution](trace/2026-08-21-lbz-frame-23533-ribot-child-lead.md)
already established a constant one-row child lead. It rejected a creation
offset because both engines dispatch the child during its parent's creation
frame. That does not prove both execute the same child routine: native
initialization returns before the active orbit. The fixture's
`object_appeared` event at row 23436 records slot 20 at `$12B0,$063C`, exactly
the parent `$12B0,$064C` plus the child's `$00,-$10` creation offset, and
records its three visual links at that same position. A first active orbit
would already move away from that position. This creation-row observation
is outside the near-player `object_state` gap cited in the old audit.

Native near-list samples at rows 23530–23534 place that head at X `$1270`
and Y `$0632,$0635,$0639,$063C,$063F`; the hurt at row 23534 consumes the
preceding object's state through the player-slot touch loop.

The current-touch override alone is not established as a cause: the refreshed
snapshot and live object position coincide at the current player-slot touch
phase. No shared collision or CPU owner was changed on that hypothesis.

## Fresh matched baseline and correction

All commands below run from this lane's worktree, with `DISPLAY=:0` and
`LUA_BIN=/usr/bin/lua5.4`. The root S3K ROM was independently hashed:
CRC32 `63522553`, SHA1 `CFBF98C36C776677290A872547AC47C53D2761D6`.
Production code was unchanged for all three baseline commands; the new
initialization assertion existed only in the regression test.

The first three waiting submissions specified `-Dsurefire.forkCount=1`.
They never admitted Maven. At the coordinator's direction, only those exact
waiting wrappers were interrupted after checking their worktree and absence
of child processes, then the same commands were resubmitted without that
redundant override. The POM global fork-count property is `1`, and the trace
profiles reference it. One Java 21 Surefire JVM was observed during the chain.
The discarded queue waits are not test executions or validation failures.

| Actual command (`python3 tools/testing/maven_queue.py` prefix) | Completed result |
|---|---|
| `-Dmse=off -Ptrace-replay -Dtest=TestS3kSonicTailsCompleteEmeraldRunChain -Ds3k.rom.path=${OPENGGF_REPO}/s3k.gen test` | 1 test, 1 failure, 0 errors/skips; method 23.44 seconds. Segments 0/2/4 report zero errors. Segment 6: 189 errors, first 3319 `sidekick_x` `$31C1` vs `$31CA`. Segment 8: 13,254 errors (13,113 physics + 141 animation), first 1583 `sidekick_x` `$366C` vs `$3674`. HCZ segment 9: 32,343 errors (30,131 physics + 2,212 animation), first 0 `y_sub` `$0000` vs `$3800`, complete comparison, giant-ring exit never observed. |
| `-Dmse=off -Ptrace-segments -Dtest=TestS3kLbzZoneSliceTraceReplay -Ds3k.rom.path=${OPENGGF_REPO}/s3k.gen test` | 1 test, 1 failure, 0 errors/skips, 15.22 seconds. All 46,075 rows; 4,585 errors (3,955 physics + 630 animation), zero warnings. First 23533 `x_speed` `$016F` vs `$0200`; first animation error on the same row, ID 2 vs `$1A`. |
| `-Dmse=off -Dtest=TestRibotBadnikInstance#subtypeFourHeadSphereUsesRomCircularRadius test` | 1 test, 1 failure, 0 errors/skips, 0.764 seconds. The new assertion fails: initialization X expected 512, actual 515. |

The chain's HCZ manifest contains 3,574 rows. `complete=true` establishes
comparison coverage, not parity; the existing campaign's terse “all rows
compared, exit missed” must not be read as a zero-error claim. The earliest
row 0 fractional-position discrepancy remains unattributed in this task.

The production correction returns after creating the Ribot active child's
visual links. Its existing `visualChildrenSpawned` state already captures
that initialization boundary for recreation/rewind; no new field, fitted
delay, touch override, comparator or fixture change was introduced.
The active orbit begins on the following dispatch, matching `loc_8C396`'s
return through `loc_8C594`.

The same LBZ command on the corrected source completed with 1 test, 1 failure,
0 errors/skips: all 46,075 rows compared, 1,665 errors (1,419 physics +
246 animation), zero warnings. The first remaining error is row 30582
`tails_y`, ROM `$013D`, engine `$012C`; first animation error is row 30587
`tails_animation_id`, ROM 0, engine 2. The fix removes 2,920 errors and
advances the frontier by 7,049 rows. The remaining divergence is unattributed;
the fixture remains red.

The focused command was `python3 tools/testing/maven_queue.py -Dmse=off
-Dtest=TestRibotBadnikInstance,TestScalarOnlyCodecDeletion#s3kRibotVisualGraphBatch226ClassesRoundTripPassedWithoutCodec,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils
-Dsonic1.rom.path=${OPENGGF_REPO}/s1.gen
-Dsonic2.rom.path=${OPENGGF_REPO}/s2.gen
-Ds3k.rom.path=${OPENGGF_REPO}/s3k.gen test`
(the selector is one quoted shell argument). It passed 68 tests, no
failures/errors/skips, in 25.835 seconds Maven time. This includes both
packages of `TestSonic3kLevelLoading`, all four mandated S3K keep-green
classes, seven Ribot tests and one visual-graph rewind round trip.
The subtype-zero test now explicitly performs the child initialization pass
before the existing parent gate and extension assertions. The subtype-four
regression failed before the production edit and passes afterward.

The unchanged AIZ/HCZ chain baseline was not repeated for this LBZ object-only
change. Tails full-chain and MGZ slot order were not executed in this bounded
lane. Broad baseline, candidate and integrated validation belong to the
coordinator; these focused passes do not claim a full-suite pass.

The verified ROM bytes corroborate the dirty/read-only disassembly reference:
`$8C396` begins `43FA 023E 4EB9 0008 4040` (attribute setup), and its final
`6000 01DA` at `$8C3B8` branches to `$8C594`, past the active dispatch at
`$8C3BC`. The orbit at `$8C41E` begins `5428 003C 7402`, matching the separate
angle increment and radius-selector routine. No disassembly asset or trace
state supplies runtime behavior.

## Stale descriptions checked against current source

These are source-audit dispositions at the pinned base, not fresh executions.
No additional broad scope was opened and no test pass is inferred from presence.

| Ledger description | Current disposition and exact references | Still open |
|---|---|---|
| Reverse gravity: “collision probes not yet”, unreachable without tests | Superseded by the later updates in the same ledger. `CollisionSystem.resolveGroundAttachment` and its reverse-gravity sensor/angle helpers, `PlayerSensorActivation`, and the production DEZ gravity writers implement grounded and airborne paths. `TestS3kReverseGravityDezCorridor#invertedGravityLandsOnTheCorridorCeiling`, `#anInvertedPlayerStaysAttachedToTheCeiling`, and `#anInvertedPlayerWalksAlongTheCeiling` exercise actual DEZ terrain. | Native whole-route/roster/lifecycle/presentation certification and complete inverted Knuckles traversal; these are explicitly retained by the ledger's later text. |
| SSZ Mecha: “only its entry and attack loop” | This remains only in the stale TOC label. The actual section is already “Hit-Window Phase and Slot-Reuse Fidelity”. `SszMechaSonicObjectInstance#onDefeatFall` / `#onDefeatLanded`, `SszMechaSonicActEndObjectInstance`, and SSZ events' Death Egg launch implement defeat/results and destination flow. `TestS3kSszMechaSpawnHeadless#theDefeatWaitsTheCartridgesFrameCountAndThenHandsTheActOver` and `#nativeTeamsCompleteTheLaunch` exercise defeat and actual DEZ destination loading with restore/replay checks. `TestSszColdRouteCapture` also exists as independent route coverage, so the ledger's cold-route absence claim needs current execution evidence before retention. | Native hit-window phase and reused-parent-slot bytes remain unverified/unmodelled as described in the current section; no fresh native or whole-act certification is claimed here. |
| SSZ boss defeats: “draw no explosion” | This also remains only in the stale TOC. Both `SszGhzBossObjectInstance` and `SszMtzBossObjectInstance` call `SszBossExplosionController.spawnFor`; the current section already documents implemented cadence, forward allocation, RNG and rewind. `TestSszBossExplosionAllocation#failedForwardAllocationConsumesAttemptWithoutRngThenFollowsSlotOccupant` exercises allocation and occupant-follow behavior. | Arbitrary replacement occupants' raw `$38` stop-bit semantics, plus cold-route/native pixel and timing certification, remain open. |

The coordinator can align the reverse-gravity opening paragraph and all three
TOC labels with their later/current sections without describing those remaining
obligations as solved. This lane did not execute these unrelated test classes.

**Completed-class evidence supplied by the coordinator:** the shared exact-base
ordinary run's fresh `TestSszColdRouteCapture` XML reports 2 tests, 2 failures,
0 errors/skips, 17.825 seconds. Both
`coldCompleteRouteDefeatsMechaAndLoadsDeathEggWithRewindAtLateEvents` and
`coldRouteDefeatsBothReplicasAndReplaysTraversalAndTransport` fail from player
death at input 7311. Thus cold-route coverage exists as executable source, but
its current reproduction is red. The component launch test and source presence
do not certify a successful cold route. The ordinary run was still incomplete
when this class result was supplied; its command and combined failure table are
owned by the coordinator.

Machine-local paths above are normalized to `${OPENGGF_REPO}` (the main
repository root) and `${OPENGGF_WORKTREE_ROOT}` (the sibling worktree root).
Executed ROM arguments were absolute paths under the main repository root.

## Second swarm: HCZ fresh-load boundary at `5b3a63641`

Workspace `.worktrees/trace-s3k-hcz-handoff-20261007-r2`, branch
`bugfix/ai-trace-s3k-hcz-handoff-20261007-r2`; this round is independent of
all earlier numbers above. The coordinator owns combined validation and ledgers.

The run manifest has no transition record for segment 8 `aiz_5` to segment 9
`hcz`: this is a fresh zone load, not a giant-ring Saved2 return. Native
`aiz_5`'s last row has HCZ players at `$0280/$0020` and `$0260/$0024`, routine
0, zero velocity and fractions. HCZ row 0 has routine 2, airborne, Y velocity
`$0038` and Y fraction zero for both. This comparison evidence supplies no
runtime state.

The owning ROM path is `SpawnLevelMainSprites` / `loc_6834` (airborne falling
state), `Sonic_Init` (routine 0 returns), `loc_6468` (initial `Process_Sprites`
without a V-int), then `LevelLoop`. `MoveSprite` loads the old `y_vel`, adds
`$38` to stored velocity, and integrates the old value. Retail `FixBugs=0`
remains selected; the nearby HCZ Knuckles initialization timer bug is unrelated
and must not be silently corrected.

Rejected as unsupported by current source: clearing a stale fractional word,
changing gravity integration, or treating this seam as Saved2 restoration.
`setCentreY` already clears the fraction; `doObjectMoveAndFall` integrates old
velocity; the initial playable setup slot explicitly bypasses physics. The
remaining disagreement needs execution evidence at the actual load boundary.

The historical 2026-08-21 frontier entries ("engine's HCZ player exists 120
rows early" and subsequent stage-boundary census) measured four gravity passes
spread across the load. Their rejected two-plus-one lag arithmetic is not a
current causal explanation. Current `FreshLevelTransitionBoundaryController`
retains destination state behind the title and performs setup before ordinary
admission; its direct test lacked fixed-point assertions. This round adds
those assertions to the live pause/repeated-load scenario, preserving the
existing dispatch-count checks.

A temporary coordinator-approved read-only probe observes HCZ velocity changes
immediately around `GameLoop.step` in `AbstractRunChainTest.stepEngineFrame`:
pre/post shared playback cursor, Y/fraction/velocity, mode, fresh-load pending
flag, native V-int and level clocks, and comparator cursor. Its bounded window
is diagnostic only, does not change inputs or production decisions, and must
be removed after extracting evidence. The baseline wrapper was still waiting
(no Maven execution) when this instrumentation was added; production remains
exactly the pinned base.

This round independently rehashed all three main-repository ROMs without
creating aliases: S1 CRC32 `AFE05EEE`, SHA1
`69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B`; S2 CRC32 `7B905383`, SHA1
`8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9`; S3K CRC32 `63522553`, SHA1
`CFBF98C36C776677290A872547AC47C53D2761D6`. These match the required retail
identities. Queue admission and execution results are separate evidence.

### R2 fresh HCZ boundary measurement and rejected early comparison

Pinned production base `5b3a63641033506fc0d89ad5188a0c97fae29089`, worktree
`.worktrees/trace-s3k-hcz-handoff-20261007-r2`. The baseline harness carried a
temporary read-only pre/post production sampler; gameplay, input and comparison
behavior remained unchanged. Both original queued requests completed:

- `DISPLAY=:0 LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay -Dtest=TestS3kSonicTailsCompleteEmeraldRunChain -Ds3k.rom.path=${OPENGGF_ROM_ROOT}/s3k.gen test`: exit 1; 1 test, 1 failure, 0 errors/skips; 24.90 s class time. Segments 0/2/4 complete with zero errors/warnings; segment 6 has 189 physics errors, first row 3319 `sidekick_x` native `31C1`, engine `31CA`; segment 8 has 13,254 errors (13,113 physics, 141 animation), first row 1583 `sidekick_x` native `366C`, engine `3674`. HCZ segment 9 compares all 3,574 rows with 32,343 errors (30,131 physics, 2,212 animation), 0 warnings, 55 lagged rows, first row 0 `y_sub` native `0000`, engine `3800`; its giant-ring exit is not observed.
- `DISPLAY=:0 LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestGameLoopFreshLevelHandoff -Ds3k.rom.path=${OPENGGF_ROM_ROOT}/s3k.gen test`: exit 0; XML 4 tests, 0 failures/errors/skips, 1.046 s. The new direct HCZ first-ordinary assertions passed (`y=0020`, fraction `0000`, velocity `0038`).

The sampler executed at the real `GameLoop.step` boundary. Its cursor is the
next zero-based BK2 input index, not the native CSV frame or either ROM clock:

| Pre/post input cursor | Boundary | Y fraction / velocity after | `ObjectManager.vblaCounter` | `SpriteManager.frameCounter` |
|---|---|---|---:|---:|
| 53486 / 53487 | AIZ replacement load, fresh boundary raised | `0000 / 0000` | 53445 | 0 |
| 53589 / 53590 | Fresh boundary completed | `0000 / 0000` | 53545 | 0 |
| 53607 / 53608 | First ordinary HCZ move, no comparator attached | `0000 / 0038` | 53563 | 1 |
| 53608 / 53609 | Destination comparator compares row 0 | `3800 / 0070` | 53564 | 2 |

The proposed early-comparison attachment is **rejected**. V5 uses BK2 input
index `bk2_frame_offset + row` and native sample frame `offset + row + 1`:
`S3KCompleteRunCaptureRunner.cs:137–150` and measurement hazard 32 document the
two quantities. HCZ's offset is 53608, so the first move above consumed input
53607, the unrecorded arm/gap row. Reassigning that state to destination row 0
would hide early input admission. There was no reseek at this boundary: after
the gap step, cursor 53608 equalled the offset even though the helper retained
zero rows consumed. The concrete defect is the plain admission helper's raw
`stepEngineFrame` while admission is denied, unlike its sibling's existing
`SHARED_GAP` frame-driver path.

Native first-move arithmetic already matches: `SpawnLevelMainSprites` /
`loc_6834` establishes the HCZ airborne spawn, `Sonic_Init` returns without
movement, and `MoveSprite` integrates old Y velocity before adding `$38`.
`loc_64DC -> LevelLoop -> Wait_VSync -> Process_Sprites` does not permit a
new destination ordinary pass in the blocking entry gap. No velocity/fraction
reset, comparator tolerance change, physics-row hydration or fitted hold is
justified. The bounded candidate routes the plain pre-window rows through the
existing production gap owner, with source-ended state because preparation
already observed the destination load; comparator row 0 remains attached at
its correct input index. The temporary sampler source is removed after this
measurement. Candidate execution remains pending at this checkpoint.

### R2 candidate and independent remaining frontier

The matched continuous candidate used the same baseline command, ROM and input
movie, with the temporary sampler removed. It completed exit 1 (1 test, 1 failure,
0 errors/skips, 25.30 s class time): segments 0/2/4 remain zero, AIZ segments 6/8
retain their exact 189/13,254 profiles. HCZ compares all 3,574 rows with **563
errors**, 477 physics and 86 animation, 0 warnings, 55 lagged rows, bootstrap 0.
Its first mismatch moves to row **653**, `sidekick_y` native `0585`, engine `0586`.
The giant-ring exit remains missed. This is a verified opening-admission repair,
not a green continuous HCZ route. The final advertised-offset safety guard is a
later review refinement and requires its own completed checks.

The focused old-behavior reproduction used
`DISPLAY=:0 LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestHeadlessPlainLevelGap -Ds3k.rom.path=${OPENGGF_ROM_ROOT}/s3k.gen test`:
1 test, 1 failure, 0 errors/skips, 0.704 s, because the raw gap step advanced
`SpriteManager.frameCounter` from 0 to 1 before the advertised input window.
Two earlier invocations stopped at test compilation (missing required observer
method, then package-private mode-entry access); neither executed the regression.
The corrected test uses the public mode-entry method and real production dispatch.

Candidate focused command:
`DISPLAY=:0 LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestHeadlessPlainLevelGap,TestGameLoopFreshLevelHandoff,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils -Dsonic1.rom.path=${OPENGGF_ROM_ROOT}/s1.gen -Dsonic2.rom.path=${OPENGGF_ROM_ROOT}/s2.gen -Ds3k.rom.path=${OPENGGF_ROM_ROOT}/s3k.gen test`:
exit 0, **65 tests, 0 failures/errors/skips**. Both classes named
`TestSonic3kLevelLoading` were selected (36 and 7 tests); bootstrap 6, decoding 3,
AIZ skip 8, startup 4 and the gap regression 1 all passed.

The standalone command
`DISPLAY=:0 LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay -Dtest=TestS3kSonicTailsHczSegmentTraceReplay -Ds3k.rom.path=${OPENGGF_ROM_ROOT}/s3k.gen test`
completed exit 1: 1 test, 1 failure, 0 errors/skips, 3.230 s. Its report has one
physics mismatch **span** (rows 653–727, 75 rows), `tails_y` native `0585`, engine
`0586`; no animation/bootstrap errors or warnings. `total_frames=3519` accounts
for 3,574 advertised rows less 55 lagged rows. Standalone reports group mismatch
spans, while the chain counts mismatched field observations; the two error totals
must not be treated as equivalent counting units. The report also advertises
missing cage, velocity/position-write, Sonic-history, Tails-normal-step and CNZ
cylinder aux schemas. It does not certify those comparison channels. It proves
the earliest remaining HCZ one-pixel frontier independently of the chain handoff;
chain camera/animation and giant-ring behavior need further causal work.

Native HCZ `interact_state` first publishes Sonic `object_control=53` at row 3531,
leaving 43 represented rows through 3573, consistent with the existing entry
flash's 43 object-dispatch sequence. The boundary waiter continues production
after comparator exhaustion. Neither observation proves the cause of the missed
exit; no ring/flash timer, sidekick controller or physics owner was changed.

Review added a stronger plain-admission bound: preserve preparation's legitimate
0/1 title-release fall-through, reject counts beyond the first opening row, try
admission at the destination offset, and fail **before** a denied suppressed step
can consume an advertised destination input. A real ROM-backed denied-at-offset
regression checks unchanged cursor, sprite clock and player fractions/velocity.
This protects full row-0 physics/animation/art comparison rather than advancing a
comparator past unexecuted gameplay.

`python3 tools/testing/run_categories.py --base 5b3a63641033506fc0d89ad5188a0c97fae29089`
was inspected without `--run`: full ordinary selection, 3,007 classes, all
categories plus guards because these are shared test infrastructure changes.
The swarm coordinator owns updated-base, combined candidate, cross-game canonical
replays and post-integration broad verification. No local broad pass is claimed.
The lane is deliberately still pinned despite concurrent `develop` advances;
those destination changes are not covered by these pinned measurements.

Final reviewed-bound verification (pinned base `5b3a63641033506fc0d89ad5188a0c97fae29089`, same lane): the exact focused command above completed exit 0, **66 tests, 0 failures/errors/skips**, Maven 25.971 s. The two gap regressions passed alongside the four startup tests and all mandatory S3K controls. The exact chain command above completed exit 1, **1 test, 1 failure, 0 errors/skips**, class 24.08 s (Maven 45.434 s). Its HCZ report remains complete with 563 errors (477 physics, 86 animation), 0 warnings, 55 lagged rows, bootstrap 0, first row 653 `sidekick_y` `0585/0586`; AIZ segment 6/8 retain 189/13,254 and the giant-ring exit remains missed. Both final requests executed after queue admission on unchanged source; no waiting request was cancelled.

The S3K `supportsPlayerDynamicArtAudit=false` gate remains unchanged. Preserving the existing comparator/publication attachment does not establish an S3K player dynamic-art audit pass. Cross-game canonical chains and combined updated-base validation remain coordinator-owned. This lane closes the causal pre-window ordinary-physics admission defect, not the remaining HCZ route or later LBZ frontier. The temporary comparison-only sampler, generated probe classes and raw probe output were removed after extracting the evidence above; no probe is part of the change.

For the commands in this section, `OPENGGF_ROM_ROOT` denotes the verified absolute primary checkout containing the existing root ROMs; each recorded invocation expanded these properties to absolute paths. The neutral spelling preserves reproducibility without committing a machine-local home path.
