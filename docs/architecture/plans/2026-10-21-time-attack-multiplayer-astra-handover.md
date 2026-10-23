# Time Attack multiplayer scale: Astra handover

## Mission and current state

Astra should continue from the completed stress, hardening, profiling and initial
bandwidth work, then lead the budgeted-replication follow-up described in
[the high-level design](../designs/2026-10-21-time-attack-budgeted-replication.md).

The task worktree is:

```text
<project-root>/.worktrees/ai-multiplayer-scale-study-20261021
```

The local branch is `feature/ai-multiplayer-scale-study-20261021`. At the start of
this handover update its documented task head was `a0614bc2c`; current `develop` was
`79e5ebea2`. The main workspace has not been switched, merged or pushed by this
thread. Re-read `git status`, `git log --graph --decorate --oneline --all -30` and
`git worktree list` before acting because the integration coordinator may have moved
`develop` after this document was written.

The prior coordinator instruction remains authoritative: commit and reconcile the
feature branch, but stop before fast-forwarding or pushing `develop`. Hand the final
branch/head and evidence to the integration owner unless the user explicitly changes
that flow.

## Documents to read first

1. [Consolidated scale analysis](../audits/performance/2026-10-21-time-attack-multiplayer-scale-analysis.md)
2. [Budgeted replication design](../designs/2026-10-21-time-attack-budgeted-replication.md)
3. [Detailed component performance audit](../audits/performance/2026-10-21-multiplayer-component-performance.md)
4. [Stress-system plan and historical validation](2026-10-21-racing-stress-system.md)
5. [Next-line subsystem reference](../next-line-subsystems.md)
6. [Performance measurement hazards](../../agent-workflow/briefing-trace-rounds.md#measurement-hazards--all-produce-plausible-output)
7. [Multiple attempts per round](2026-10-21-time-attack-multiple-attempts.md): repeated runs change dormancy, baseline resets, spawn crowding and verifier sizing

## Commit trail and ownership

| Commit | Purpose |
|---|---|
| `731307ac4` | Retain Time Attack hosted sessions and offline audio during full runs |
| `625db2f45` | Middle-pack demonstration and initial component profiling |
| `0ea6758fe` | Scale and isolate multiplayer fanout |
| `0ed6fc5f8` | Protocol v3 membership deltas, packet-local compression and scale hardening |
| `975037e5b` | Reconcile the task with then-current `develop` while preserving Stageplay teardown intent |
| `a0614bc2c` | Record lifecycle-stage performance interpretation |

The scale-study branch contains or merges the work above. Related worktrees/branches may
still exist because another thread owns integration and cleanup. Do not delete them until
every commit and capture is accounted for.

## What is implemented

- Stateful fuzz/adversarial suites for room, round, broker, tunnel and codec boundaries.
- Real relay/direct socket stress with capacity, mixed traffic, sanction, reset,
  reconnect and teardown witnesses.
- Multi-room `RacingScaleTool` with separate server/load processes, distinct loopback
  sources, real TCP byte counters and JFR broker/tick/queue metrics.
- Protocol v3 membership snapshot plus `PlayerUpsert`/`PlayerLeft` deltas.
- Packet-local exact ghost compression with unchanged/signed-delta/absolute field modes
  and raw fallback.
- Guest-tunnel inner text/binary limits enforced before wrapper expansion, isolating the
  offender from the host broker connection and unrelated rooms.
- Strict primitive-null parsing and duplicate-identity relocation cleanup.
- Cached attempt mask snapshots and reusable proof-of-work digest/buffers.
- Atomic blob staging/publication with cached totals and abandoned-stage cleanup.
- SQLite admission/cleanup indexes.
- Verifier heartbeat during replay and a safe minimum stale interval.
- Middle-pack Time Attack capture with dynamic visibility and corrected HUD/audio.

The budgeted scheduler, mixed send rates, cross-packet predictive residuals, finish
coalescing and conditional directory responses are designed but not implemented.

## Key measured results

Optimized four-room result, 256 players per room and a ten-second steady window:

```text
1,024 admitted and finished
server CPU                 6.58 s / 10.00 s
peak sampled RSS           591.9 MiB
peak used Java heap         97.2 MiB
GC                           11 collections / 11 ms
worst room tick p99          5.07 ms
worst room queue p99         0.043 ms
admission downstream        51.2 MiB
steady downstream           48.4 MiB / 10 s
steady upstream              6.36 MiB / 10 s
slowest room fill            7.66 s
worst join p99              49.61 ms
worst browser request       10.78 ms
```

Against the matched baseline, admission downstream fell 98.5%, steady downstream
24.4%, steady upstream 23.5%, slowest fill 37.6% and server CPU 7.6%. One four-room
RSS sample rose while Java heap fell; treat it as unresolved native/off-heap variance,
not a leak or a memory saving.

The rendered route finished rank 130/256. It observed 181 distinct remote identities,
with a peak of eight active ghosts and repeated visibility entry/exit/return. It proves
dynamic replacement, not 256 simultaneously rendered sprites.

## Durable and temporary artifacts

Durable interpretation lives in the three documents above. Raw reports remain task
diagnostics outside the repository:

```text
<agent-scratch>/multiplayer-scale-20261021/rooms-{1,2,4}.json
<agent-scratch>/multiplayer-scale-20261021/after-bandwidth/rooms-{1,2,4}.json
<agent-scratch>/multiplayer-scale-20261021/after-bandwidth/isolation-audit.json
```

Videos:

```text
<agent-scratch>/timeattack-capacity-video-20261021/timeattack-256.mp4
<agent-scratch>/timeattack-midpack-20261021/timeattack-256-midpack.mp4
```

Do not commit raw JFR, Maven logs or generated run directories. Preserve the videos and
JSON until the integration owner confirms that the delivery and user-facing artifacts
are accounted for.

## Validation already completed

Final affected network selection before the most recent documentation-only commits:

```bash
env JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
python3 tools/testing/maven_queue.py -Dmse=off \
'-Dtest=TestGhostPackets,TestGhostStreamPublisher,TestGhostHub,TestGhostHubScaleMode,TestGhostStreamValidator,TestProtocolFuzzing,TestRaceClientLoopback,TestControlCodec,TestControlCodecAdversarial,TestControlCodecPolymorphism,TestPhase3Protocol,TestRoomHost,TestRoomHostAdversarial,TestClientRaceSession,TestRemoteGhostPlayback,TestRosterConsumption,TestMultiplayerRaceCoordinator,TestRaceHostServer,TestMasterEndToEnd,TestDirectConnectEndToEnd,TestRacingSocketStress,TestRacingScaleTool,TestRecordingBlobStore,TestMasterConfig,TestVerifierWorker,TestMasterHttpRoutes,TestSqliteIdentityStore,TestProofOfWork,TestAttemptInputRecording' test
```

Result: 252 tests, zero failures/errors/skips.

The network change-based category run selected 294 candidate classes and completed
293 reports / 2,068 tests in six batches with zero failures/errors/skips. Its consumed
run directory was inspected and acknowledged.

Mod API selection:

```bash
python3 tools/testing/maven_queue.py --lean -Dmse=off \
  '-Dtest=TestModApiSignatureSurface,TestModApiReleasePolicy,TestModApiPinPolicy,TestModApiSdkPackager' test
```

Result: 40 tests, zero failures/errors/skips.

Post-merge conflict-boundary selection:

```bash
env JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
python3 tools/testing/maven_queue.py -Dmse=off \
'-Dtest=TestFirstBootModScene,TestModEngineWiringSeams,TestEngine,TestEngineConfiguredHeadlessStartup,TestHeadlessAudioOwnership,TestHeadlessGameBootAudioIdentity,TestStageplayLivePlayback,TestStageplayNativeBinding,TestStageplayRegistration,TestModApiSignatureSurface,TestModApiPinPolicy,TestModApiReleasePolicy,TestModApiSdkPackager' test
```

Result: 115 passed and one explicit skip. `TestStageplayLivePlayback` requires
surfaceless EGL and aborted because that flag was not supplied. Branch policy and the
release-tree audit passed at `975037e5b` and again after the lifecycle documentation.

These are focused and network-category smoke results, not a release-only full suite.
Do not repeat them on unchanged code. New replication code requires its affected focused
tests plus a new change-based smoke plan.

## Recommended next delivery

Implement the first budgeted-replication slice without cross-packet compression:

1. Add behavior-neutral telemetry: bytes requested/selected/written, candidates,
   update age, tier counts, interest churn and residual histograms.
2. Add a server-owned per-recipient state budget with control reservation.
3. Rank candidates by visibility/proximity/change plus age, with deterministic ties and
   a maximum-silence bound above a measured minimum viable budget plus an explicit
   coarse-only/disconnect overload path.
4. Before mixed rates, replace `RemoteGhostPlayback`'s contiguous-frame assumption with
   a sparse source timeline. Carry canonical source indices, interpolate across bracketed
   samples, distinguish intentional gaps from loss, and keep upstream 60 Hz validation
   and hashes unchanged.
5. Preserve absolute state on interest entry, attempt/generation change, reconnect and
   discontinuity.
6. Extend `RacingScaleTool` with fixed-eight versus budgeted 8/12/16/24 arms and a
   clustered/overtake/stationary/teleport/slow-reader workload.
7. Run the middle-runner rendered route with multiplayer off and each candidate preset.

Only after that evidence should Astra decide whether predictive residuals, shared
encoding, or refinement of the existing 512 px X-bucket index has the best next return.
Finish coalescing and generation-aware unchanged responses on the existing WebSocket
room-list protocol are independent, low-risk follow-ups and can proceed in parallel
worktrees if the integration owner can absorb separate commits.

## Acceptance gates for the next slice

- Lifecycle/control outcomes remain identical: admissions, attempts, finishes, ranks,
  reset, reconnect, removal and isolated offender handling.
- Every interest entry and baseline reset has an absolute recovery sample.
- No identity can occupy two slots or inherit another generation's state.
- Control messages progress under a saturated state budget.
- Queue memory and candidate work are bounded for a slow reader.
- Maximum update silence is enforced for admitted interests above the minimum viable
  budget; overload demotes to coarse state or disconnects under a bounded policy.
- A 16-racer policy stays near the current eight-racer byte envelope or supplies a
  documented quality/byte tradeoff.
- Server tick/queue p99 and client frame-time evidence do not materially regress.
- Fuzz and malformed-input blast radius remain per offender/room as designed.

## Risks and cautions

- Do not infer server CPU from old colocated one-JVM stress reports; use the multi-process
  scale tool.
- Do not call the setup/post-route JFR envelopes lobby/finish timings; they contain capture
  and diagnostic work.
- Do not divide finish-burst bytes by gameplay time and call the result steady traffic.
- Do not interpret allocation sampling weights as retained heap.
- Do not weaken source-IP limits to make local capacity work; use the owned source proxy.
- Do not automate the shared desktop. Captures require surfaceless EGL and injected
  no-device audio, following the headless-rendering skill.
- Do not build full remote Sonic simulation merely to save pose bytes. Object/world-state
  divergence and client CPU are unresolved.
- Do not treat refinement of the existing 512 px X-bucket index as a substitute for a
  byte budget; the starting pack remains dense.
- Do not introduce protocol compatibility branches. Multiplayer has not shipped, so move
  the one strict protocol and all clients/tools/tests together.

## Deployment recommendation to preserve

Start a pilot master/relay at four recent vCPU, 4 GiB RAM, `-Xmx1g`, local NVMe and
250 Mbit/s symmetric network, preferring 1 Gbit/s for TLS/bursts/growth. Use two fast
vCPU and 4 GiB per verifier worker, one replay at a time initially. Scale regional
relay egress first, verifier workers from queue age second, storage before retention
pressure, and directory separation only when browser latency correlates with relay work.

## Delivery procedure

1. Work only in an isolated worktree; never switch the main workspace branch.
2. Reconcile current `develop` into the feature worktree by intent and record conflicts.
3. Use the Maven queue for focused/category tests; do not launch the release-only full
   suite.
4. Use `tools/performance/run_isolated.py` for accepted measurements after compilation
   and correctness checks, with exclusive test and build-activity leases.
5. Keep diagnostic reports outside the repository and acknowledge category-run results.
6. Validate all required commit trailers with `git interpret-trailers --parse`.
7. Stop before mutating/pushing `develop` unless the integration owner explicitly hands
   that authority back to Astra.
