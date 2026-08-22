# Active-segment trace ownership validation

## Result

The bounded trace-run segment ownership implementation passes its feature-branch
acceptance gates. Run planning retains compact descriptors for the whole run and
opens one eager payload only for the active segment. The tested branch is
`feature/ai-trace-active-segment-cursor` at
`3a3c1fc52169a16f390a1d1b8083cf74473fb357`, based on synchronized `develop`
`d9650fd7dff2828c75c39ca575ffbafbbde7409d`. Independent final review and
main-workspace integration remain Task 8 Steps 8 and 9; this report does not
claim those controller-owned steps.

All measurements used Maven 3.9.16 and OpenJDK 21.0.11. Durable evidence is
under the managed agent scratch root:

```text
$AGENT_SCRATCH_ROOT/tasks/trace-active-segment-cursor-20260822T002616Z-260779-06974cb7/
```

## ROM inputs

The all-game measurements used the files discovered in the project root. They
were not renamed, copied, or symlinked for this validation.

| Game | File | CRC32 | SHA-1 |
|---|---|---|---|
| Sonic 1 World REV01 | `Sonic The Hedgehog (W) (REV01) [!].gen` | `AFE05EEE` | `69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B` |
| Sonic 2 World REV01 | `Sonic The Hedgehog 2 (W) (REV01) [!].gen` | `7B905383` | `8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9` |
| Sonic 3&K locked-on | `Sonic and Knuckles & Sonic 3 (W) [!].gen` | `63522553` | `CFBF98C36C776677290A872547AC47C53D2761D6` |

## Memory and resource gates

Task 7 ran the fixed warmed, forced-GC benchmark in two independent
Maven/Surefire processes. The reviewed eager denominator is fixed at
1,087,200,800 bytes.

| Fork | Descriptor graph | Maximum installed graph | Reduction | Maximum segment |
|---|---:|---:|---:|---|
| 1 | 9,255,056 bytes | 115,657,656 bytes | 89.36% | `s3k-59-soz_2` |
| 2 | 9,254,968 bytes | 115,553,504 bytes | 89.37% | `s3k-59-soz_2` |

Both forks stay below the 16 MiB descriptor cap and 256 MiB installed cap and
above the required 75% reduction. A disposable Task 8 diagnostic, removed
after measurement, also recorded the maximum special-stage sample in both
fresh forks: 22,737,320 bytes and 22,604,408 bytes, both at the real S2
composite `s2-1-ss`. The sample includes the real S2 pass binder; the benchmark
also installs real S1 and S3K special-stage driver graphs. The diagnostic's
overall samples remained consistent at 115,638,016 and 115,553,624 bytes, with
`s3k-59-soz_2` still the maximum.

`TestTraceReaderLifecycle` passed 3/3 twice in fresh forks. Each run performed
100 cycles and observed exactly 1,000 opens and 1,000 closes: 100 each for
plain ordinary physics/aux, gzip ordinary physics/aux, S2 aux, and S3K
physics, plus 200 each for S1 and S2 physics because those readers first scan
the stored frame domain and then parse typed rows. Construction failure and
observer restoration are covered by the other two methods.

The combined authority/ownership gate passed 12/12. The focused migration gate
passed 177/177. Real ordinary and special installed roots collect after normal
and injected-failure teardown; a retained-comparator mutation remains live
until its extra root is removed, proving that the reachability test is
sensitive to the leak shape.

## Recorded 67-segment oracle

The exact `TestS3kKnucklesSuperEmeraldRunChain` oracle ran alone with the trace
profile, alphabetical order, one fork, a freshly cleared report directory, and
the verified S3K ROM. It consumed all 1,653 AIZ rows and failed at the expected
terminal boundary.

- First comparison mismatch: row 0, `camera_x`, expected `0x1300`, actual
  `0x1308`. With `bk2_frame_offset=810`, that is BizHawk frame
  `810 + 0 + 1 = 811`.
- First non-camera physics mismatch: row 446, `y_speed`, expected `-0448`,
  actual `0x0448`; BizHawk frame `810 + 446 + 1 = 1257`.
- Last compared row: 1,652, proving all 1,653 rows were consumed; BizHawk frame
  `810 + 1652 + 1 = 2463`.
- Terminal result: segment 0 (`aiz`) exit boundary `giant_ring` was never
  observed, unchanged from the recorded oracle.
- Unmatched completions were unchanged: raw frame 1,617 at `PRE_MAIN_LOOP`,
  `KOS_DECOMPRESSION_QUEUE#14`, fingerprint
  `3c96d8b9573e86f26814cb8a605459c8fef23cc1ca5425db2fd1cc250d408d91`;
  and raw frame 1,618 at `POST_OBJECTS`, `KOS_MODULE_QUEUE#9`, fingerprint
  `70da89e553f70fe647a00489dec5f2612854986b444b87a2e8d81ab0f821e431`.
  Production held no pending match for either completion.
- Dynamic-art result: zero gaps and zero failures.

The trace report says `complete: true` and retains its final mismatch at row
1,652, so the expected red cannot be explained by early stop or payload
starvation.

## Fresh trace-profile sweep

The all-game command was:

```bash
mvn -Ptrace-replay -Dmse=off -Dsurefire.runOrder=alphabetical \
  -Dsonic1.rom.path=".../Sonic The Hedgehog (W) (REV01) [!].gen" \
  -Dsonic2.rom.path=".../Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  -Ds3k.rom.path=".../Sonic and Knuckles & Sonic 3 (W) [!].gen" test
```

The terminal result was 840 tests, 12 failures, 0 errors, and 6 skips. Maven
launched 165 unique classes and all 165 have a fresh XML suite; the launched
and XML class sets are identical, with no dump or dumpstream file. Three tagged
Slots nested containers are launched repeatedly with the same report names,
so two successful dynamic invocations are overwritten in XML and the XML
attribute sum is 838. The terminal Maven total is authoritative; all 12
failure nodes remain present in fresh XML.

The established red set and complete first-frontier messages are:

| Class/method | Recorded first frontier or structural result |
|---|---|
| `TestS3kReplayReferenceClosureIntegration#replayMatchesTrace` | 113 errors; frame 25,589 `player_animation_id`, `0x0013` / `0x0005` |
| `TestS1CompleteEmeraldRunChain#ghz1ToScrapBrainAcrossEverySpecialStage` | 14 axes; segment 12 first non-camera frame 101, `queue.s1_nemesis_plc.prepared`, `true` / `false`; segment-33 ownership stop also retained |
| `TestS1CompleteEmeraldVisualRun#replaysTheSecondGiantRingAndTheSpecialStageBehindIt` | known structural diagnostic-sink failure at destination segment 2 |
| `TestS1CompleteEmeraldVisualRun#replaysThroughTheSpecialStageAndItsReturnBridgeAdmission` | same known structural diagnostic-sink failure at destination segment 2 |
| `TestS2CompleteEmeraldRunChain#ehz1ToDeathEggAcrossEverySpecialStage` | 12 axes; segment 15 frame 2,252 `air`, `1` / `0`; segment-19 ownership stop also retained |
| `TestS3kSonicTailsCompleteEmeraldRunChain#aiz1ToDoomsdayCollectingEverySevenEmeralds` | 3 axes; segment 8 frame 6,000 `sidekick_x`, `0x4997` / `0x4996`; segment-9 giant-ring miss retained |
| `TestS2Cnz2LevelSelectTraceReplay#replayMatchesTrace` | 530 warnings; frame 0 `cnz_slot.slot2_pos`, `0x8C08` / `0x8C00` |
| `TestS2CnzLevelSelectTraceReplay#replayMatchesTrace` | 237 warnings; frame 0 `cnz_slot.slot2_pos`, `0x3808` / `0x3800` |
| `TestS2Cpz2Seg10CompleteEmeraldsSegmentTraceReplay#replayMatchesTrace` | 370 errors; frame 2,252 `air`, `1` / `0` |
| `TestS2SczLevelSelectTraceReplay#replayMatchesTrace` | 2 warnings; frame 0 `tornado.objoff_31`, `0x00FF` / `0x0000` |
| `TestS2WfzLevelSelectTraceReplay#replayMatchesTrace` | 3 warnings; frame 0 `tornado.status_byte`, `0x0008` / `0x0000` |
| `TestS3kAizTraceReplay#replayMatchesTrace` | 37 errors; frame 20,713 `air`, `0` / `1` |

The three chain messages and two visual messages are byte-identical to the
focused pre-sweep controls. The seven standalone frontiers are established
repository fleet reds and retain their documented full messages. No previously
green trace became red. This ownership change intentionally greened no trace,
so there is no greened fixture for which a rows-compared starvation check is
required; the dedicated oracle supplies the active-payload completion proof.

## Default-suite comparison

The controller synchronized `develop` with `origin/develop` at
`d9650fd7dff2828c75c39ca575ffbafbbde7409d` and ran the exact
`mvn -Dmse=off test` baseline on JDK 21. The feature worktree then ran the same
command. Exact `kind + class#method` sets came from fresh Surefire XML and were
compared in both directions.

| Tree | Tests | Failures | Errors | Skips | Failure/error identities |
|---|---:|---:|---:|---:|---:|
| synchronized `develop` | 15,295 | 56 | 81 | 26 | 137 |
| feature at `3a3c1fc52169a16f390a1d1b8083cf74473fb357` | 15,324 | 54 | 65 | 26 | 119 |

The feature adds 29 passing tests. Exact set results are 119 shared identities,
zero new or worsened identities, and 18 baseline identities absent from the
feature result. The absent set is allowed and consists of:

- errors in three `TestTraceSessionLauncherProductionFailureCleanup` methods;
- errors in three `TestTraceSessionLauncherRunBranch` methods;
- the failure in
  `TestTraceSessionLauncherProductionFailureCleanup#postFinishComparisonFailureIsContainedAndAbortsSession`;
- the failure in
  `TestMhzMushroomParachuteObjectInstance#fallingPlayerInGrabWindowIsCarriedAtRomOffsetAndParachuteStartsFalling`;
  and
- all ten methods in `TestS3kCompleteRunStateDecoder`, whose reused fork had an
  S3K ROM property available in the feature run.

Two non-authoritative attempts are retained rather than silently folded into
the result. Inheriting `JAVA_TOOL_OPTIONS` into a child-JVM safety test prepended
the JVM's diagnostic to stderr and created one false new audio-CLI failure. A
clean-environment rerun after deleting the native cache then hit the documented
parallel LWJGL extraction race (`UnsatisfiedLinkError: liblwjgl.so`) and created
2,293 environment errors. Serial 32-test LWJGL and one-test STB prewarms passed,
after which the exact command above completed without either signature. Only
that final healthy run is used for the identity comparison.

## Focused acceptance before and after the full suite

Each trace-profile group cleared `target/surefire-reports` immediately before
execution, used alphabetical order and a real one-fork override, waited for a
terminal Maven result, and preserved its fresh XML. Pre- and post-suite failure
identities and complete XML `message` attributes compare byte-for-byte.

| Group | Pre result | Post result | Full-message comparison |
|---|---|---|---|
| Catalog/planning, lease/reader, coordinator, launcher, comparator, timing, and special-stage ownership | 586 tests, 1 failure | 586 tests, 1 failure | identical known S2 pass-row failure |
| S1/S2/S3K special-stage traces | 32 tests, 7 errors | 32 tests, 7 errors | identical seven S3K stages 8–14 timing messages; S1/S2 and other S3K cases green |
| Exact headless chain subclasses | 16 tests, 4 failures, 1 error, 2 skips | same | all five messages identical |
| Visual/complete-audio prescribed group | 20 tests, 3 failures | same | both visual messages and the S2 pass-row message identical |

The baseline-equivalent focused reds are the existing S2 synthetic
`started_at_input_sample` omission, two S1 visual diagnostic-sink failures,
three complete-chain divergence messages, the Knuckles giant-ring miss, the
mega-run pending-module error, and seven S3K special-stage unmatched-completion
errors. No focused frontier changed.

## Authority debt and conclusion

The implementation does not certify or broaden pre-existing replay authority.
Physics/aux-derived loop selection and metadata RNG/bootstrap use in
`TraceReplaySessionBootstrap`, `TraceReplayBootstrap`, `TraceReplayRowPolicy`,
`TraceBinder`, `LoadQueueComparisonProjection`,
`TraceStructuralRowComparator`, and `TraceRunSpecialStageRowDriver` remain
quarantined debt. The active lease may expose the existing payload only to the
three production/harness owners and exact direct-test allowlist; source,
bytecode, reflection, method-handle, constructed-name, and recursive relay
guards enforce that boundary.

One evidence limitation remains explicit: the ownership reachability test was
already green when introduced, so it has no historical live-leak RED. Its
retained-comparator mutation proves sensitivity to the exact extra-root shape,
while the authority and eager-removal rules have direct RED evidence. This is
test-history debt, not an unguarded production authority expansion.

Every feature-branch gate applicable through Task 8 Step 7 passes. The design
is implemented and validated on the feature branch, subject to independent
Step 8 review and Step 9 integration/reverification.
