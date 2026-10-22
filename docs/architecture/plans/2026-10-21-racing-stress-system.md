# Racing adversarial and capacity testing

## Outcome and design

Deliver reproducible hostile-input tests through connection, lobby and round states,
and a real-socket capacity harness for the Time Attack networking stack. The integration
base is `efd605142603146509995cffbb56a6b48f9c1c1b` (`develop`). Work is owned by
`feature/ai-racing-stress-system-20261021` in its cow-helper-created worktree; this
filesystem fell back to ordinary checkout. Astra owns design, validation and delivery;
6.1 Sol workers own the separate room/protocol, master, and socket-harness slices.

The existing `GhostLoadTestTool` calls `RoomHost` directly and times only `room.tick()`.
Its fake connections cannot establish socket capacity or transport queue occupancy.
Keep that narrow diagnostic, correct its packet-size label, and add complementary
coverage at the owners that can actually prove each contract.

`RoomHost` owns authenticated membership, slots and attempts. `HostRoundEngine` owns
lobby/countdown/running/end/vote transitions. The master broker owns admission, trust,
directory entries and relay grants; `MasterChannelHandler` transfers a socket from
broker processing to its room or tunnel owner. Each relay room has its own serialized
executor. Clients receive typed messages into bounded inbound queues, drained by the
game thread. Test clocks may drive deterministic state tests; socket measurements use
real elapsed time and complete work observed over the wire.

## Chosen shape

1. Seeded stateful room and broker tests use the production state owners, authenticated
   test peers and bounded recording sinks. Every failure identifies seed, phase and
   operation. Fresh offenders keep one malformed case from hiding later cases behind
   a closed connection. Healthy witnesses prove continued progress and isolation.
2. Small socket/transport tests cover framing, handoff and disconnect races that a
   fake connection cannot expose. Existing TLS pin, handshake-intent, HTTP-body-budget,
   client-timeout and direct-server contract tests remain complementary coverage.
3. `RacingSocketStressTool.run(Config, Path)` owns an ephemeral master/direct server,
   clients, source-address proxy and report. The CLI selects transport, player count,
   seed, duration, cycles and workload. Reports distinguish admission, measured load,
   expected rejections, observed successful work and teardown. Ordinary tests run
   small cases; full capacity and performance runs are explicit commands.

The room limits are eight direct players and 256 relay players. All transports retain
their four-connections-per-source-IP limit. Local capacity clients use distinct
loopback source addresses through an owned proxy, rather than weakening that policy.
Capacity identities are established before the timed interval because new identities
are deliberately refused above 80% room occupancy. Separate tests cover those trust
gates. There is no current global master-connection maximum: a 256-player result
certifies one relay room, not arbitrary master-server scale.

The public harness surface hides setup, pacing, message draining, protocol barriers,
metrics and cleanup behind one run operation. No new public Mod API is needed.
Synthetic pose streams exercise production network clients and servers; they are
not ROM-backed gameplay or authoritative recording verification.

## Alternatives and accepted limits

- Extending only the in-process bot was rejected: it cannot reveal socket framing,
  actor handoff, transport backpressure or disconnect behavior, and its timing omits
  ingress validation.
- An external-server fleet was considered. It separates server/generator process
  metrics and enables distributed capacity testing, but introduces deployment,
  certificate and process orchestration before deterministic coverage exists. The
  selected local harness uses real sockets while explicitly reporting colocated
  process metrics; it makes no deployed-throughput claim.
- Sending every fuzz mutation through sockets was rejected as the sole test layer:
  authentication and scheduling obscure repeatable state boundaries. A bounded
  socket matrix complements the broader deterministic state corpus.

Additional priorities are saturation recovery, stale credentials and attempts,
cross-player isolation, handoff/disconnect races, slow consumers, repeated lifecycle
cycles and shutdown. Existing bounded HTTP and ghost-backpressure tests should be
reused, not copied. Source inspection found possible unbounded master/tunnel queues
and client outbound chains; those are risks to investigate with concrete reproductions,
not claims that the new harness certifies all denial-of-service resistance.

## Implementation and acceptance

- Room/protocol worker: structured-envelope mutations, invalid ordering/token/attempt
  input across room phases, healthy-peer progress, deterministic max occupancy, and
  minimal reproduced decoder/state fixes.
- Master worker: broker handshake/proof/admitted attacks, stale/cross-client credentials,
  transport handoff and disconnect cleanup, and minimal reproduced lifecycle fixes.
- Socket worker: real relay/direct clients, simultaneous capacity, overflow rejection,
  paced varied traffic, observed standings and ghost delivery, churn/rejoin and teardown;
  bounded samples and explicit failures in a machine-readable report.
- Lead: correct the legacy microbenchmark's evidence, expose the tool, document use
  and coverage boundaries, run combined validation and isolated measurements, review,
  integrate into `develop`, push only `develop`, and remove accounted-for task work.

Successful capacity validation requires the requested number simultaneously admitted,
unique membership, overflow refused for the expected reason, expected healthy attempts
finished, actual ghost/control work delivered, and recovered capacity/removed rooms
after cleanup. A timeout or a fast rejection is not completed useful work. Performance
reports separate warmup and admission from measured traffic, include failure counts,
and label CPU/heap/GC as whole-process values when the generator shares the server JVM.
No ordinary correctness test fails solely because a shared machine misses a benchmark
latency threshold.

## Validation and measurements

Preflight passed with `JAVA_HOME=/usr/lib/jvm/java-25-openjdk` and
`LUA_BIN=/usr/bin/lua5.4`; the host's default `lua` is not 5.4. The first focused run
exposed two harness defects rather than product regressions: a deferred-connect test
used the mutable connection field, and one malformed corpus element failed during test
construction. Stable per-event connection capture and a valid malformed wire case
fixed those oracles. No timing threshold was relaxed.

The final new-regression selection was:

```text
TestRoomHostAdversarial, TestControlCodecAdversarial,
TestRoomBrokerAdversarial, TestMasterTransportLifecycle,
TestGuestTunnelBounds, TestGhostLoadTest, TestRacingSocketStress
```

It passed 31 tests with no failures, errors or skips. After adding the final
12-case admitted-own-token dispatch matrix, `TestRoomBrokerAdversarial` passed all
four methods independently. The existing 30-class consumer selection covered the
shared codec and fuzz tests, room/round/standings owners, client budgets and timeouts,
master/broker/relay owners, TLS and handshake binding, both direct servers, fallback
tunnelling, direct/relay/vote/verified end-to-end flows, and `TestNetIsolationRules`.
It passed 234 tests with no failures, errors or skips. `python3 racing/server/build.py
--skip-engine` also built the distributable CLI successfully.

The change-based smoke planner selected all 3,521 smoke classes and guards because
the racing server paths are currently unclassified. That broad selection was not
launched: the concurrent Jackson/security delivery already owned the all-change smoke
run, while this delivery used the focused owner/consumer selection above plus real
maximum-capacity measurements. This avoids duplicating unrelated engine work while
covering every changed executable owner and its relevant transport consumers.

The CLI was built before measurement. Each command ran the prebuilt binary through
`tools/performance/run_isolated.py`, which recorded an exit code of zero while holding
exclusive test and build-activity leases. All used `--duration 10 --cycles 1
--seed 4675398 --mix mixed --timeout 30`.

| Transport | Capacity result | Observed traffic | Local latency | Whole-process cost |
|---|---|---|---|---|
| Relay | 256/256 admitted and finished; one overflow rejected; one hostile peer sanctioned; replacement, next-round reset, room removal and shutdown passed | 134,400 frames sent; 1,075,104 received; 2.06 MB TCP up / 85.11 MB down | 2,560 samples; p50 3.12 ms, p95 8.91 ms, p99 9.02 ms; 9.11 ms max generator lateness | 10.35 s measured; 13.69 CPU-s; 1.00 GiB peak used heap; 4 collections / 20 ms GC |
| JDK direct | 8/8 admitted and finished; overflow, sanction, replacement, reset and shutdown passed | 4,200 frames sent; 29,400 received; 64.5 kB TCP up / 343.0 kB down | 80 samples; p50 3.64 ms, p95 4.61 ms, p99 4.62 ms; 4.18 ms max lateness | 10.02 s measured; 1.56 CPU-s; 73.3 MiB peak used heap; no measured GC |
| Netty direct | 8/8 admitted and finished; overflow, sanction, replacement, reset and shutdown passed | 4,200 frames sent; 29,400 received; 64.5 kB TCP up / 343.0 kB down | 80 samples; p50 3.62 ms, p95 3.91 ms, p99 5.75 ms; 2.71 ms max lateness | 10.02 s measured; 1.55 CPU-s; 66.8 MiB peak used heap; 1 collection / 2 ms GC |

These are single local observations, not throughput thresholds. Server, clients and
source proxy share the JVM; synthetic poses do not run the engine or ROM playback.
The relay result certifies one full room. It does not establish a global master-room
limit, external-network behavior, sustained soak stability or isolated server cost.

## Full-capacity video follow-up

The 2026-10-21 video request uses an isolated worktree based on `02325fd02`.
`TimeAttackCapacityVideoTool` drives the real Engine through its legal screen,
master title, Extras entry, Time Attack setup, master browser, relay lobby,
countdown and stock GHZ1 run. A fresh external working directory owns identity,
ghosts, settings, saves and all capture output. Surfaceless EGL and direct input
handler events keep the shared desktop outside the capture.

The one rendered player consumes only P1 controller input from the maintained
`src/test/resources/traces/s1/ghz1_fullrun/ghz1_fullrun.bk2`, starting at row 840.
There are no position, clock, ring or completion seeds and no trace-state reads.
A separate cold route check consumed 3,966 inputs without death, reached the
signpost at frame 3,229 and rendered the native 0:53 results. The remaining 255
real authenticated relay connections publish delayed cosmetic copies of the
visible player's observed poses and synthetic, unverified finish claims. This
is one engine simulation beside 255 protocol clients, not 256 independently
completed ROM simulations. Established identity admission is seeded for the
owned server, as in the capacity harness; production address/room caps remain.

The end-to-end recording uncovered gaps hidden by lower-level tests:

- Before any game had loaded, mod-scene updates dereferenced a missing world timer
  and drawing dereferenced a missing camera. Both now respect the absence of a
  gameplay world; a real title-entry scene regression covers update/input/draw.
- The generic gameplay reset retired the Time Attack scene during hosted launch
  and return. The runtime-only reset now retains that owner; ordinary title and
  stock-game transitions continue retiring it. The regression exercises repeated
  failed launches, return and explicit retirement on the real Engine owner path.
- A client could reach the advertised countdown expiry before the host's next
  scheduled tick. Its valid start was rejected, and subsequent ghost packets
  caused disconnection. Start/finish admission now advances countdown state from
  the host clock. Tests cover exact expiry before a tick and unchanged deadline
  and grace limits. The socket harness no longer adds an 80 ms delay that masked
  this boundary.
- Ordinary Engine launch preparation replaced an injected headless audio backend
  with LWJGL, leaking sound to desktop devices. The owned recording was stopped
  when reported. Configured headless sessions now retain the exact supplied
  backend and rebuild only its no-device sink; the capture checks its identity
  around every frame. Regressions exercise repeated resets/launch preparation,
  the actual headless backend, no-device sinks and offline PCM packets.

The first offscreen prototype used a 960x672 GL context whose fixed-function
projection did not match the Engine's 320x224 shader projection; its gameplay HUD
was visibly misplaced. The retained recording uses native 320x224 rendering and
FFmpeg nearest-neighbour scaling. Gameplay-only capture was useful for route
qualification, but the actual Engine presentation owner is required to record
startup, scenes, hosted HUD, fades and production audio together.

The tool records a 30 fps silent MP4, presentation-aligned stereo PCM, chapter
positions, native stills and observed state. It requires every accepted standings
row and checks owned socket/proxy shutdown before marking cleanup complete.
Timing includes rendering, encoding and colocated peers and is not a benchmark.

Final focused verification used Java 25 and:

```sh
python3 tools/testing/maven_queue.py --lean -Dmse=off \
  '-Dtest=TestFirstBootModScene,TestEngineConfiguredHeadlessStartup,TestEngine,TestMenuScreenModeController,TestSceneRunLauncher,TestHostedRunController,TestModEngineWiringSeams,TestHostRoundEngine,TestHostRoundEngineVote,TestHostRoundEngineVerified,TestRacingSocketStress' test
```

It passed 113 tests with zero failures, errors or skips. The final tool-only
constructor-failure cleanup adjustment compiled independently with `javac --release
25`; it changes no recording or gameplay behavior. The server CLI rebuilt with
`python3 racing/server/build.py --skip-engine`. Change-based planning selected all
3,523 smoke classes and guards. Focused owner/consumer checks replace that broad
selection here: runtime reset consumers, fresh scenes, configured audio ownership,
round timing/verification/votes and all socket stress transports are covered;
no initialized gameplay algorithm or ROM behavior changes.

The completed recording has 5,137 presentations (85.616 seconds), 4,302 consecutive
presentations at 256 admitted connections, a natural player finish after 3,230
movie inputs, and all 256 accepted unverified standings rows. It received 2,214,259
ghost entries, then closed all owned peer/server/proxy resources successfully.
The final 1280x720/30 fps MP4 has chapter markers and offline stereo 48 kHz AAC.
The backend log contains only Null then Headless SMPS initialization; exact-owner
checks remained active across every presented frame. Selected lobby, gameplay and
native results frames were inspected. The audio track is non-silent and peaks at
-9.4 dBFS. These are capture observations, not server performance measurements.

The recording and selected stills live outside the repository under
`$CAPTURE_ROOT/timeattack-capacity-video-20261021/`.
This follow-up leaves its branch local for integration; it does not mutate or
push the main checkout.

## Mid-pack recording and component analysis follow-up

The first recording at `731307ac4` put the live player ahead of every delayed
copy. The follow-up records a fresh independent ROM-backed controller playback
before network setup, then pairs faster/slower cosmetic timelines around the live
player. Smooth periodic surges exercise production near/far selection and actual
viewport departures/returns without reversing or jumping the recorded route.
Ahead poses cannot be derived from the live player's not-yet-observed future;
this separate playback avoids injecting trace state or moving the live player.
Bot claims remain synthetic and unverified.

The capture now requires a middle accepted rank and counts rendered-ghost and
viewport-eligible transitions only during stepped gameplay, including the natural
completion presentation. An initial counter implementation continued through the
results tail; read-only review rejected that as evidence of gameplay visibility
churn, and the counters were restricted before recording. Viewport eligibility
uses the native camera rectangle with sprite margins; it is not pixel occlusion
proof. The runtime's actual eight-ghost list supplies the observed identities.

The HUD now reserves the stock upper-left and lives areas, uses a compact right
column with at most three nearby rows, marks coarse progress as an estimate, and
moves finished-run panels clear of the stock act card. Pure layout tests check
containment and phase-specific separation across supported viewport widths.

Component measurements use prebuilt executables under one exclusive test/build
activity lease. The [component audit](../audits/performance/2026-10-21-multiplayer-component-performance.md)
records exact workload boundaries, sampling limits, scaling and unmeasured paths.
In particular, colocated socket/video process CPU cannot establish dedicated
server or pure-client capacity, and cosmetic accepted finishes do not exercise the
ROM replay verifier. Raw diagnostic recordings are consumed after interpretation.

Focused verification for this follow-up passed 63 tests, with no failures, errors
or skips, using Java 25 and the exact selector below:

```sh
env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py --lean -Dmse=off \
  '-Dtest=TestGhostPackSchedule,TestMultiplayerHudLayout,TestHudTextLayout,TestMinimapLayout,TestMultiplayerRaceCoordinator,TestRemoteGhostPresentation,TestTimeAttackRuntime,TestTimeAttackRuntimeMultiplayerSeams,TestRacingSocketStress,TestHostRoundEngine' test
```

Change-based inspection against `731307ac4` selected all 3,525 smoke classes and
guards because the racing subtree and standalone profiling helper have no narrower
selection rule. Focused tests and the actual rendered route replace that broad
selection for this bounded follow-up: no shared gameplay, physics, protocol,
parser, transport or build contract changes; production behavior changes are HUD
layout and its existing finished-state argument. The other executable changes are
capture/scheduling tools and optional diagnostic events.

The first mid-pack capture exposed a scheduler error: a two-pose skip during fast
vertical movement produced `delta 10,34 exceeds 32`, after which production stream
contiguity correctly rejected later batches and disconnected the offenders. That
failed capture is discarded. The scheduler now retains original intermediate
poses when necessary, using the unchanged global speed ceiling; it never edits
recorded coordinates or relaxes server admission. A steep-route regression sends
the resulting frames through `GhostStreamValidator`. A bounded analysis of the
actual 3,230-pose export found a maximum original one-frame delta of 30, required
59 intermediate-pose decisions across 255 schedules, and predicted a live finish
near rank 129 (ties remain production-ordered).

The follow-up regression command passed all three scheduler tests without
failures, errors or skips:

```sh
JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  python3 tools/testing/maven_queue.py --lean -Dmse=off \
  '-Dtest=TestGhostPackSchedule' test
```

Read-only review also identified that WebSocket `FrameAssembler` had initially
been grouped with ghost presentation in the JFR analyzer. The classifier was
corrected and all five existing recordings were reprocessed. Whole-recording and
marked-window event counts, Java/native/allocation sample counts and total
allocation weights were identical before and after; only attribution labels
changed. No unchanged socket workload was rerun to repair its offline summary.

The corrected recording completed with the live player ranked 130/256, all 256
accepted unverified finishes, 181 distinct rendered ghost identities, 771 entries
and 763 exits from the actual runtime ghost list, and 518 viewport entries / 516
exits / 390 returns during stepped gameplay. Peak rendered remote ghosts stayed
at eight. It retained 256 connections for 4,935 presentations, completed the live
route naturally after 3,230 controller inputs, and closed owned resources cleanly.
The 5,770-presentation video is 96.167 seconds, 1280x720 at 30 fps with synchronized
48 kHz stereo AAC and chapter markers. Gameplay, stock act-clear card and final
panels were visually inspected; the latter leave the act card and lives clear.
Audio initialization was Null then Headless SMPS with exact-owner checks around
every frame; the offline track is non-silent and peaks at -8.8 dBFS. No desktop
playback was used for inspection.

The revised video, selected stills, state/chapter CSVs and standalone interpreted
performance report are under `$CAPTURE_ROOT/timeattack-midpack-20261021/`. The
[component audit](../audits/performance/2026-10-21-multiplayer-component-performance.md)
contains the five socket profiles and successful rendered route's bounded JFR
observations. Raw recordings, expanded diagnostic summaries, temporary scripts,
PCM, local identities and server data are consumed rather than archived.

Final pixel inspection caught unsupported `#` glyphs in the new compact rank
labels. The renderer now uses plain local-rank digits and periods in standings
rows; the existing legacy text-composition API is unchanged. The final focused
check passed all eight tests without failures, errors or skips:

```sh
JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  python3 tools/testing/maven_queue.py --lean -Dmse=off \
  '-Dtest=TestMultiplayerHudLayout,TestHudTextLayout' test
```

The demonstration is regenerated after this label-only correction without JFR.
The performance audit retains the earlier isolated build's exact fingerprint and
observations; no result is reassigned to the revised wording. Profiled state and
chapter CSVs remain distinguishable from the final demonstration's metadata.

The final wording capture also completed at rank 130/256 with all 256 finishes
accepted and clean shutdown. It observed 181 ghost identities, 763 runtime entries
/ 755 exits and 510 viewport entries / 508 exits / 383 returns, with an eight-ghost
peak. It contains 5,768 presentations (96.133 seconds); the muxed H.264/AAC streams
have matching duration, 1280x720/30 fps video and stereo 48 kHz audio. Final rank
text and stock act-clear separation were inspected in the actual final MP4; the
audio is non-silent with -9.0 dBFS peak. This is the delivered video. The preceding
390-return metrics belong specifically to the isolated profiled recording.
