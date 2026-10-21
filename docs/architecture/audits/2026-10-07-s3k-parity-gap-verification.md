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

## R2 bounded HCZ breakable-bar follow-up

On ancestor `45c6eed2d6e3a85442cd42e12b2c74c347ec9c8d`, standalone HCZ independently reproduced the row 653–727 Tails Y span. At the first reported difference, native Tails has Y `0585`, engine `0586`, with matching subpixels `E100/DA00`, zero X/Y velocity, ground speed `FFA6`, status `46`, routine `02`, vertical-hang animation `11` and mapping `9D`; native object control is `01`. The native integer Y remains fixed while Sonic descends. BK2 inputs 54260–54262 hold P1 Down and leave P2 neutral. This is independent of inherited AIZ segment 6/8 discrepancies.

The owning routines establish the missing semantic: `HCZBreakableBar_VerticalCheckPlayers` reads raw `(Ctrl_2).w` (`sonic3k.asm:42828`); `HCZBreakableBar_CheckVerticalGrab` tests held Up/Down in that word's high byte. The horizontal loop reads the same raw word at line 42952 and tests held Left/Right. The former Java object used logical playable directions, allowing CPU-generated steering to move a captured sidekick. The bounded correction uses existing `SidekickCpuController.isRawController2InputHeld(mask)` for CPU-controlled playables and retains ordinary human direction reads. The pressed-byte jump release behavior is unchanged. No shared controller, API, fixture, animation or timing owner changed.

Two new tests in the existing bar test class cover both orientations: raw-neutral P2 plus logical Down/Right must retain position; ordinary main-player held input must move by one; manual P2 held input with no pressed edge must move by one. The bar's own snapshot is restored and the object dispatch is repeated after explicitly resetting the player's position. This proves bar-state capture/restore and repeated dispatch, **not whole-world registry rewind/replay**. Existing capture, subpixel, release, native-slot, extension and unload checks remain selected.

Exact commands use the same verified absolute ROM expansion described above:

- Red: `DISPLAY=:0 LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off -Dtest=TestHCZBreakableBarObjectInstance test`: exit 1, 15 tests, 2 failures, 0 errors/skips, 0.212 s class time. Both new tests failed expected coordinate 512, actual 513 with raw P2 neutral. An earlier identical non-lean request was cancelled while waiting, exit 130, before Maven/test execution; it supplies no test evidence.
- Green: `DISPLAY=:0 LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off -Dtest=TestHCZBreakableBarObjectInstance,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils -Dsonic1.rom.path=${OPENGGF_ROM_ROOT}/s1.gen -Dsonic2.rom.path=${OPENGGF_ROM_ROOT}/s2.gen -Ds3k.rom.path=${OPENGGF_ROM_ROOT}/s3k.gen test`: exit 0, **75 tests, 0 failures/errors/skips**, Maven 23.725 s. Both level-loading classes and all four mandatory S3K controls execute.
- Standalone: the previously recorded `-Ptrace-replay -Dtest=TestS3kSonicTailsHczSegmentTraceReplay` command, unchanged: exit 0, **1 test, 0 failures/errors/skips**, 3.412 s class time. Report physics/animation/bootstrap/warnings all zero; 3,519 non-lag frames represent all 3,574 rows less 55 lag rows. The previously listed missing auxiliary channels remain unverified.
- Matched chain: the previously recorded `-Ptrace-replay -Dtest=TestS3kSonicTailsCompleteEmeraldRunChain` command, unchanged: exit 1, **1 test, 1 failure, 0 errors/skips**, 24.42 s class time (Maven 44.658 s). HCZ **563 → 488** errors (402 physics, 86 animation), first non-camera mismatch **row 3532 primary `x` native `1457`, engine `1452`**. All 3,574 rows complete, warnings 0, lag 55, bootstrap 0. The giant-ring exit is still missed. AIZ 6/8 retain exact 189/13,254 profiles. The bar correction removes exactly the 75 local Y observations; it does not close the remaining 488 late boundary observations.

Each invocation used frozen source and normal queue admission; canonical replays did not use lean mode or fork/heap overrides. The S3K player dynamic-art audit-disabled gate remains unchanged. Updated-base broad validation, combined cross-game checks, central frontier/matrix reconciliation and delivery remain coordinator-owned. No additional physics or giant-ring correction is inferred from these results.

## R2 collected-ring mask causal verification

A comparison-only probe on ancestor `b3eff6209ed90d42f88b0264b15f4d8d841e852a` logged the existing captured mask after production and at entry-ring initialization. It changed no RAM, input, admission or comparator state. The same recorded chain command completed exit 1, 1 test, 1 failure, 0 errors/skips, 24.07 s class time; the probe executed. Observed engine mask events (movie cursors are post-production where sampled):

| Boundary | Movie cursor | Engine mask |
|---|---:|---:|
| First AIZ special-stage entry | 2868 | `00000002` |
| Seamless AIZ act 1→2 | 12061 | `00000002` |
| Subsequent entries / returns | 12842 / 28011 / 39830 | `0A` / `1A` / `3A` |
| AIZ post-results end flag | 53053 | `0000003A` |
| HCZ loaded / title / release | 53487 / 53488 / 53588 | `0000003A` |

The actual HCZ ring initialization reports zone 1, center `(1440,05C0)`, bit index 1, mask `0000003A`, **delete=true**. Thus ring absence is established, not inferred solely from a nearby-object list. The probe did not sample inside `updateTally`; the specific native SaveGame gate remains source-inferred from its existing Java implementation and completed results path.

The coordinator's independent native GPGX original-movie observation completed exit 0 in 101.719 s with unchanged movie SHA-256 and no RAM writes, state loads, input substitutions or recorder-source edits. Its observations were inspected directly: at BK2 52944 / AIZ_5 row 6512, zone 0 act 1, level counter 6492, PC `$C4CC` sees mask `0000003A`; PC `$C4D0` sees `00000000` with identical player state/clocks. Native HCZ load/release retains zero. Eleven native HCZ rows 3527–3537 align to the committed fixture; Sonic object control changes `00→53` at row 3531, while the ring mask remains zero until SS entry at movie 57182. This contrasts with the measured engine HCZ delete decision. Durable native observations remain in the coordinator-owned task capture directory outside the repository.

Direct locked-on ROM bytes corroborate `$C4CC: 42B8 FF92 4E75` (`CLR.L $FF92; RTS`). `$C434: 4A78 FFAE 6600 0092` branches SK-alone to the same clear; `$C43C: 2038 E660 6700 008A` branches a zero Save_pointer there. The native capture did not include those two branch inputs, so it is **not** a measured No Save/SK-alone execution; source and ROM bytes independently establish their contract.

`Collected_special_ring_array` is at `$FF92`, outside normal Level_ClrRam's listed clear ranges. Same-zone seamless changes, SS/bonus return loads and death/reload preserve the collection mask; the observed native seamless/SS returns corroborate those two paths, while death/reload remains a source-backed obligation. `SaveGame_SpecialStage` and `SaveGame_LivesContinues` use separate returns and do not clear it. A blanket level-load or generic `PROGRESSION_SAVE` clear is rejected: Java AIZ seamless and late AIZ2 post-boss persistence requests have no native full SaveGame call.

Exact native full SaveGame callers already represented in Java: results tally's act-2/Sky-Sanctuary gate (`62678`); HPZ exit branches (`91524`, `91718`); SSZ final defeat (`165014`); DEZ escape (`171521`); DDZ ending (`173818`); StartNewLevel object's Knuckles/LRZ gate (`181385`). Native AIZEndBoss_CheckLevelTransitionY calls StartNewLevel only (`138340`); StartNewLevel (`180642`) itself does not clear the mask. The appropriate owner is the explicit full SaveGame semantic boundary, preserving every existing caller gate and persistence-only request. Native clears after the SRAM write; Java persistence is asynchronous and its payload omits this mask, so a clear before requesting persistence must not be described as exact SRAM ordering. Existing GameStateSnapshot already captures the mask. No new public Mod API or rewind storage is needed.

The temporary probe source, compiled probe classes, probe report/log and backups were removed after this evidence extraction. Production mask correction was not yet applied when these measurements were recorded.

### Full SaveGame correction and final qualification

The stateless `S3kFullSaveGame.complete(services)` clears the existing 32-bit collection mask through an unannotated `NativeGameStateOps` engine bridge and package-private `GameStateManager` writer, then makes the existing asynchronous persistence request. Native `loc_C4CC` clears after SRAM writing; Java's persistence payload omits this mask, so this implements the gameplay completion contract rather than claiming identical SRAM ordering. No new state or rewind adapter, public Mod API signature/version/pin, singleton lookup, generic persistence semantics, or caller gate changed.

Exactly seven verified full SaveGame sites dispatch through this helper: Results zero-increment completion (62678; act != 0 or SSZ act 0), both HPZ route exits (91524/91718), SSZ defeat completion (165014), DEZ escape (171521), DDZ ending (173818), and Knuckles LRZ StartNewLevel (181385). Generic progression requests, AIZ seamless handoff and late boss persistence, special-stage/lives endpoints, death and reload remain unchanged. The direct locked-on bytes at C434/C43C establish SK-alone and zero Save_pointer branches to C4CC, and C4CC bytes `42B8FF924E75` establish CLR.L/RTS. Neither the native observation nor Java test is a live SK-alone execution.

Withdraw preliminary Results RED claims: sessions 72543 (3/0/3/0) and 37945 (3/2/1/0) had fixture errors; 25051 (3/2/0/0) and preliminary candidate 60985 (138/2/0/0) used a public constructor which calculated nonzero bonuses and therefore did not reach the actual zero-increment full SaveGame gate. Waiting 42286 was cancelled before execution to correct an invalid record-array equality assertion. The corrected fixture uses the canonical constructor with runtime initialization disabled and zero bonuses, injected services, and the identity capture context. Its old-code request 22226 never executed as RED: while holding the normal `worktree_metadata_slot`, the log showed no Maven execution; at 2026-10-08T02:20:32.530322Z it was safely reclassified to the exact candidate without touching queue metadata or priority. Results SHA-256 changed from `3dcd67a3e9ff293e49f488d8886ac0d0a1373ce0e750af0707653c4b90fd875d` to `f0916c5ba711b8a61c27151a8ce75df17ce60685196da36a88986f028bdabc50`; helper comment changed word to mask. Completed engine probe 75265 and native pre/post C4CC observations remain the causal baseline.

Actual commands from this worktree (ROM root denotes the verified absolute primary checkout; DISPLAY=:0 and LUA_BIN=/usr/bin/lua5.4):

```bash
python3 tools/testing/maven_queue.py --lean -Dmse=off -Dtest=TestS3kFullSaveGameBoundary test
python3 tools/testing/maven_queue.py --lean -Dmse=off -Dtest=TestS3kFullSaveGameBoundary,TestS3kResultsScreenObjectInstance,TestS3kStartNewLevel,TestS3kHpzTeleporterHeadless,TestDezFinalEscapeShip,TestSszMechaDefeatRunner,TestGameStateRewindSnapshot,TestSessionSaveRequests,TestS3kSaveSnapshotProvider,TestModApiSignatureSurface,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils -Dsonic1.rom.path=${OPENGGF_ROM_ROOT}/s1.gen -Dsonic2.rom.path=${OPENGGF_ROM_ROOT}/s2.gen -Ds3k.rom.path=${OPENGGF_ROM_ROOT}/s3k.gen test
python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay -Dtest=TestS3kSonicTailsHczSegmentTraceReplay -Ds3k.rom.path=${OPENGGF_ROM_ROOT}/s3k.gen test
python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay -Dtest=TestS3kSonicTailsCompleteEmeraldRunChain -Ds3k.rom.path=${OPENGGF_ROM_ROOT}/s3k.gen test
```

Candidate 22226: exit 0, 5/0/0/0, class 0.572s, Maven 1:17 including compile. Final focus 72937: exit 0, 138/0/0/0, 29.349s, including exact API signature pins and all four mandatory startup keepers. The new regression directly exercises actual Results act-2/SSZ tally completion, act-1 preservation, all 32 bits, existing captured mask plus Results restore and repeated completion, and missing-session/missing-context/No Save helper completion. Persistence-only preservation uses an absent session, not a successful disk write. Other focused consumers exercise existing bounded gates; they do not certify the other six full live routes. Independent read-only S2 review found no material defect. DDZ real-services seeded ending coverage remains for the coordinator's combined ordinary suite; inherited late cold-route gaps remain open.

Standalone HCZ 48273: exit 0, 1/0/0/0, 23.469s; all 3519 executed samples (3574 rows minus 55 lag) match, zero physics, animation, bootstrap and warnings. Matched chain 14539: exit 1, 1/1/0/0, class 27.27s / Maven 52.227s. HCZ segment 9 is complete with zero errors, warnings and bootstrap, 55 lag: previous 488 errors (402 physics/86 animation) and row3531 animation/row3532 X frontier are closed. Production giant-ring handoff now advances through the special stage to segment 11. AIZ segments6/8 remain 189/13254, first sidekick-X rows3319/1583 unchanged. Segment11 is the newly reachable incomplete frontier: 82067 errors (69393 physics/12674 animation), zero bootstrap/warnings, first non-camera physics row1510 primary Y native07D6/engine07DF; production ownership lost before closure at BK2 cursor68801. This downstream frontier was measured, not investigated or repaired in this round.

All invocations used their normal POM single-fork shape, no timing/heap/fork/profile overrides beyond supported lean for the small focus, and source remained frozen during execution. The temporary comparison-only mask probe and its exact compiled artifacts/reports/backups were removed after evidence extraction; final Results backup was removed at reclassification. Remaining Maven outputs are ordinary ignored target artifacts. Coordinator owns seven affected act-matrix obligations, central status/release prose, actual-main composition and broad validation; these focused/canonical measurements are not a full-suite or delivery claim.


## Round 3: returned HCZ fan/conveyor frontier

Pinned base `098053c4a01c2af283ca6797463bb5051442ef0b`. Fresh canonical baseline (session 42315) completed 1/1/0/0: returned HCZ segment 11 remains incomplete with 82067 errors (69393 physics, 12674 animation), 50 lag rows, zero bootstrap/warnings. First compared disagreement is mapping row1507, native `0x0063` versus engine `0x0095` (hexadecimal); first non-camera physics is row1510 Y, native `0x07D6` versus engine `0x07DF`. The production ownership loss remains LEVEL, generation9, zone1/act0, movie cursor68801. Segment9 remains complete/zero errors, with 55 lag rows and successful giant-ring handoff. This is not full returned-act qualification.

Native original-movie observations use the verified locked-on ROM (CRC63522553), untouched movie SHA256 `ad40fb0b0a74fa12b08ab71b2e48a7455b388d14f43f4cded502ac4a15d1b3c0`, and the pinned GPGX host SHA256 `5e455b0cb3fa52d6415ef64677b8079b088204eefe971a37573667bb59efe917`. Durable observations are outside the repository in the owned Round3 fan/conveyor task directory. No RAM writes, state loads or controller substitutions were used. Native session28389 completed exit0 with 1431 observations; 17 surrounding player rows match ten compared fields. Fan2250/0874 occupies native slot10, before conveyor21A8/07CB slot91. Native fan timer/toggle is active around the first span; the suggested idle-reset constant change is rejected as its cause. Earlier native launch attempts failed before useful observations (missing BIZHAWK_HOME, then unsupported register name A0); the successful callback uses M68K A0.

`HCZCGZFan_Bubble` at `$30834` calls one `MoveSprite2` (`$1AB52`) after checking water, then jumps to `Draw_Sprite` (`$1ABC6`). Verified ROM bytes and Draw_Sprite's enqueue-only path contradict the engine's doubled movement. There are 565 contiguous native same-code/slot observations moving exactly -8 pixels. Native lifecycle session41640 completed exit0 with 50341 observations: 11900 below-water bubble entries beyond cameraY-64 survive in the same slot/X and end Y-8; 254 water-eligible entries retire or reuse their slot (247 absent, 7 reused, no same-owner survivor). The observed longest moving lifetime is only100 updates, so absence of the engine's120 cap is source-backed, not exercised by this span. Parent independently matched630 represented frame ends across seven fixture fields with zero mismatches, and corroborated these counts. Separately, all281 sampled first appearances or same-slot upward-Y resets occur at Level_frame_counter mod4==0; VBlank mod4 varies (248 at3,9 at0,9 at1,15 at2). These observations support distinct movement/lifetime/clock owners; no shared allocator or dispatcher alteration is justified.

Frozen focused RED9264 used `maven_queue.py --lean -Dmse=off -Dtest=TestHCZCGZFanObjectInstance test`: 6/1/0/0, class0.471s, Maven1:11; the new actual child update expected Y108 but got100, establishing the doubled MoveSprite2 defect. Frozen comparison-only canonical order probe60429 used the same canonical chain/profile/verified absolute ROM as baseline: 1/1/0/0, class26.16s, Maven48.259s; returned HCZ retains the exact82067 profile/first fields. Engine belt slot11 sees ground velocity0 and phase0 through the opening conveyor interval. Fan slot24 is later than the belt, but active-only fan logging has no corresponding call in this interval: reversed slots alone do not establish the complete active-fan discrepancy. Temporary logging was removed by restoring both fan/belt files byte-for-byte to HEAD, then deleting probe-built classes. `R3_FAN`/`R3_BELT` have no remaining source occurrence.

The first candidate changes only the child movement to one MoveSprite2. Fan timer/reset, offscreen/lifetime guards, spawn clock/RNG order, shared dispatcher and Bubbler behavior remain unchanged for this isolated measurement. The regression captures/restores the child before movement and repeats forward water-boundary retirement; whole-world replay is separately represented by existing graph rewind controls. Candidate results follow below.

### Isolated movement and child-lifecycle measurements

Speed-only focused75140 completed84/0/0/0, Maven1:16: fan movement/water/restore, two graph rewind controls, sixteen conveyor tests, and all four mandatory startup selectors passed (both matching LevelLoading classes execute). Canonical1419 completed5/2/0/0, Maven1:32: the full chain retains82067 and both first fields unchanged; initial HCZ and both prefix tests pass. AIZ6/8 retain189/13254 and HCZ9 retains complete0/55lag. The additional returned-HCZ standalone reports3454 errors, zero warnings, firstrow0 y_speed native0000/engine000E. Matched pre-speed production check55655 independently reproduces that exact profile (1/1/0/0, class6.235s, Maven1:12), so this standalone startup limitation is inherited and cannot qualify the returned native opening. The movement fix is independently correct but did not close the conveyor frontier.

Lifecycle RED47566 runs eight fan tests on speed-only production:8/2/0/0, class0.618s, Maven1:13. Off-camera movement expectsY392 but remains400 because the child is prematurely destroyed; the independent visible deep-water control fails the alive assertion at the existing age cap. The candidate removes only the two non-native child retirement guards and the unsupported lifetime field. A deep-water regression exercises200 updates plus subsequent water retirement, capture/restore and repeated forward completion. This is a source-contract age test, not a claim that the native observation covered120 updates. Existing graph recreation now checks the captured native velocity word and forward movement instead of an invented age field. Timer, shared allocation/order, spawn clock and RNG sequencing remain untouched for this separate measurement.

Lifecycle focus60690 passes86/0/0/0, Maven1:13. Separate canonical81889 completes5/2/0/0, Maven1:34: unchanged full-chain82067/mapping1507/Y1510, inherited returned standalone3454, initialHCZ/prefix green. Thus child lifetime is independently ROM-correct but has no demonstrated frontier contribution.

Clock/allocation RED setup35418 failed compilation from a wrong registry package, so no tests executed. Attempt15013 ran12/4/0/0, but its positive allocation control exposed a missing injected LevelManager-to-ObjectManager mock binding; that attempt is not the qualified semantic RED. Corrected82191 reaches12/3/0/0 (class0.657s, Maven24.186s): positive lowest-slot/exactly-one RNG control passes, while bubble/SFX differing-clock gates and saturated-pool RNG preservation fail. RNG actually changes seed305419896 to3281639736 despite full pool. The next candidate changes only bubble and FAN_SMALL gates to the injected Level_frame_counter; the fan physics/timer clock and RNG ordering remain unchanged for this isolated stage.

Clock focus3759 ran81/1/0/0: all three selected clock/positive allocation cases, conveyor and startup keepers pass, but the graph harness had no injected owning level clock and therefore created zero bubbles. This is a fixture mismatch, not grounds for a VInt runtime fallback. Clock canonical5920 independently remains5/2/0/0 (Maven1:31), unchanged82067/mapping1507/Y1510 and inherited standalone3454; initial HCZ/prefix pass. The graph harness now explicitly provides Level_frame_counter0 for its real production fan update.

Final fan-only allocation candidate uses the existing reserved-slot factory so AllocateObject failure precedes Random_Number and successful creation retains lowest-free-slot scheduling. No shared API/allocator/dispatcher or Bubbler change is made. The July26 Bubbler allocation-epoch proposal's historical canonical-regression kill evidence remains applicable caution; this fix has a separate owner and requires its own matched replay qualification. Existing RNG rewind storage is reused; no new runtime state or adapter is introduced. Final focus/canonical results follow.

Final candidate requests5704/38351 were compile-incomplete: the graph fixture's new Mockito calls were unqualified without imports, so neither invocation executed tests/replays. Both sources remained frozen until terminal; calls are now explicitly qualified, and owning clock/service binding plus positive allocation controls were inspected before resubmission. This is not a GREEN/RED gameplay result.

### Final Round3 lane qualification and remaining frontier

Corrected final focus81850 completes90/0/0/0, Maven25.447s, including all12 fan tests, two graph recreation controls, sixteen conveyor controls and all four mandatory startup selectors. Final canonical33300 completes5/2/0/0, Maven1:31: chain class25.434s; both prefix tests pass; initial standalone HCZ compares3519 executed samples from3574 rows (55 lag), zero physics/animation/bootstrap/warnings. Returned standalone compares14149 samples across its14208-row source and retains exactly3454 errors (2936physics/518animation), zero bootstrap/warnings, firstrow0 y_speed native0000/engine000E; matched old production55655 qualifies that inherited startup limitation. Existing advertised-but-absent auxiliary schema inventory is unchanged and is not certified by these samples.

The production full chain retains AIZ6=189 (first3319 sidekickX31C1/31CA), AIZ8=13254 (13113physics/141animation, first1583 sidekickX366C/3674), and complete HCZ9=0 with55lag and successful giant-ring return. Returned HCZ11 remains incomplete82067=69393physics+12674animation, 50 observed lag rows, zero bootstrap/warnings. First mapping1507 remains native0x0063/engine0x0095; first non-camera physics1510 remains nativeY07D6/engine07DF. Ownership is lost at movie68801 in LEVEL, generation9, progression/ROM zone1, act0. Recent compared rows end at5725; the14208-row source is not closed. No movement/lifetime/clock/allocation stage changes that frontier. The native fan is active before the conveyor; the engine active-only probe does not establish its full first-span timer/branch state. A local timer or shared ordering workaround is therefore rejected pending causal evidence. These four source/ROM-backed fan semantics are corrected independently, not claimed as conveyor or whole-act closure.

Exact focused/canonical recipes (all commands used DISPLAY=:0, LUA_BIN=/usr/bin/lua5.4, Java21 and the verified original locked-on ROM; substitute its root only for this portable record):

```bash
python3 tools/testing/maven_queue.py --lean -Dmse=off -Dtest=TestHCZCGZFanObjectInstance,TestS3kHczCgzFanGraphRewind,TestHCZConveyorBeltObjectInstance,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils "-Ds3k.rom.path=${OPENGGF_ROM_ROOT}/Sonic and Knuckles & Sonic 3 (W) [!].gen" test
python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay -Dtest=TestS3kSonicTailsCompleteEmeraldRunChain,TestS3kSonicTailsHczSegmentTraceReplay,TestS3kSonicTailsHcz2SegmentTraceReplay,TestS3kSonicTailsCompleteEmeraldRunPrefix "-Ds3k.rom.path=${OPENGGF_ROM_ROOT}/Sonic and Knuckles & Sonic 3 (W) [!].gen" test
python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay -Dtest=TestS3kSonicTailsHcz2SegmentTraceReplay "-Ds3k.rom.path=${OPENGGF_ROM_ROOT}/Sonic and Knuckles & Sonic 3 (W) [!].gen" test
python3 tools/testing/run_categories.py --base 098053c4a01c2af283ca6797463bb5051442ef0b
```

REDs select the existing fan class alone with supported lean. The clock-only focus selects its three named clock/positive allocation methods plus the same graph/conveyor/keeper selectors; pending saturation semantics were intentionally excluded only from that intermediate clock measurement, then included in the final90-test focus. All canonical stages use the identical four-class selection and normal trace-profile fork1; no fixture/input/comparator/heap/fork changes. Source remains frozen throughout each invocation. The category plan selects2648 ordinary classes from3027 plus fresh guards; it was inspected, not executed by this lane. Coordinator owns combined actual-destination broad validation/integration/delivery. Native observations remain durable outside the repository; temporary production logging, backups and probe-built classes are removed. Final target outputs are normal ignored lane artifacts, not archived evidence or committed tools.
