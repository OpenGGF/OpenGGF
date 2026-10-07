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
