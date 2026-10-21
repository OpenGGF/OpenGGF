# Multiplayer component performance, 2026-10-21

This investigation separates production server work, colocated load generation,
and the rendered client. The socket harness's process CPU includes all three of
its server, client and source-address proxy roles. It cannot establish server CPU
capacity by itself. Component samples below are observations, not conversions of
that CPU into estimated component nanoseconds.

The full-capacity rendered recording also colocates its master, bots and proxy
with the engine and capture/encoding work. It supplies observations of the real
client pump, ghost presentation, HUD and renderer, but no isolated client CPU or
incremental multiplayer frame-time estimate. A matched baseline with the same
route, viewport, audio, capture settings and source state would be needed to
measure an incremental client cost; the whole capture wall time is not that cost.
The encoder is a separate FFmpeg process. Its CPU and allocations are outside the
JVM recording, although waiting while writing its pipe can affect the capture's
wall time. The captured framebuffer is 320×224 at 60 presentations/s, with every
other framebuffer sent to FFmpeg at 30 fps and scaled to 960×672. Capture readback,
PNG snapshots, PCM conversion/writes and per-frame CSV are additional diagnostic
work. Offline SMPS synthesis remains real work through a verified no-device sink.

Origin: the Time Attack capacity/video task, development worktree
`.worktrees/ai-timeattack-capacity-video-20261021`, starting at `731307ac4`.
The component helper and optional diagnostic JFR boundaries are part of this task.
The prepared runtime state adds nine modified `src/main/java` files to that base;
the sorted path-NUL-content-NUL SHA-256 is
`c8db003fc3e35921f058c25cd612a15a76da531f9abb4837d74311eab7e73c8c`.
The bundled mod SHA-256 is
`6ca2e5adcbbd0cb75c4a6deb9370521887304e58714d3708075a1c10c8d3fed6`.
The analyzer's later category-label correction does not modify either runtime.
The final capture-only scheduler correction changes that runtime source digest to
`74770a5c60c966093bfb428e3b29acd6f9c961b1887d8072f9ee151be04387b6`;
the socket workload does not execute the corrected capture scheduler. The socket
tool's contents remain SHA-256
`2583058898e3a0c33d6a7858a6b68ff4c6944af0f99006d8919adadcfdc37ab4`
through all five measured socket arms and the final source state.

Host: AMD Ryzen 9 9950X, 32 logical CPUs, 31,920,576 KiB RAM; Linux
`7.2.10-1-cachyos`, glibc 2.44, Arch Linux OpenJDK `25.0.4.1`.
Socket runs use the JVM's default heap configuration; the rendered capture uses
`-Xmx4g`, surfaceless EGL and `java.awt.headless=true`. All recordings use JFR
`settings=profile`, `stackdepth=128`, `maxsize=256m`, `dumponexit=true`.
Recorded settings confirm Java sampling at 10 ms, native sampling at 20 ms,
allocation sampling throttled at 300/s and socket events at a 1 ms threshold,
also throttled at 300/s. These settings are part of this measurement, rather than
assumptions from another JDK's default profile.

## Measurement boundaries

Use prebuilt artifacts under `tools/performance/run_isolated.py`. Its exclusive
test admission and build-activity lease exclude linked-worktree tests/builds; its
process guard rejects bypassing Maven/javac/profiler processes. Acquire the lease
after all preparation, compilation and relevant correctness checks. Do not compile
the analyzer or invoke Maven inside the lease.

`RacingSocketStressTool` emits optional `openggf.RacingStressWindow` duration
events from measured traffic through accepted finishes and complete standings.
Admission, identity creation, room creation, warmup and shutdown belong to the
whole-recording analysis, outside those windows. Rendered recordings can emit
`openggf.MultiplayerCaptureWindow` around the actual route/frame window. Missing
markers produce no measured-window result; the analyzer does not infer boundaries
from thread names or record start/end.

Warmup exchanges control pings only. The marked interval contains first ghost
validation, round, codec and aggregation work as well as any JIT compilation
overlapping it; a 20 s recording does not establish warmed server throughput.
Repeated equivalent fresh JVMs describe variation of this bounded workload,
including that startup effect. JFR overhead means their elapsed/CPU figures do not
replace an unprofiled acceptance run.

The profiler uses JFR's `profile` settings and a bounded recording. Compile and
run the summary as follows, outside measurement preparation/execution respectively:

```bash
mkdir -p target/perf-tools
"$JAVA_HOME/bin/javac" -d target/perf-tools tools/performance/MultiplayerJfrSummary.java
java -Xmx256m -cp target/perf-tools MultiplayerJfrSummary RECORDING.jfr SUMMARY.json
```

The streaming reader retains counters and bounded method/thread dictionaries,
never all events or expanded stacks. Its output includes event counts, custom
windows, the first recognized owner outward from the sampled leaf, overlapping
inclusive component presence, estimated allocation weights/classes, GC pause
events, and components with no Java observations. Java execution and native
method samples remain separate. Native samples can contain waiting. Sample
periods differ, so their counts must not be combined into CPU percentages.
Allocation weights estimate churn, not retained heap or exact allocated bytes.
Socket events obey the recording's duration threshold: their bytes are a sampled
subset, while the stress report's proxy counters observe actual transferred TCP
bytes at its measurement boundary. Neither is Ethernet/IP overhead.

Thread names identify Netty server event loops, the JDK room actor/IO threads,
JDK HTTP client threads, source proxy virtual threads and client proof-of-work
workers. The main load generator also drains clients; a rendered engine thread
mixes gameplay, network pump, ghost presentation and rendering. Unknown ownership
remains explicit. Component stack presence identifies calls executing on these
threads without pretending their inclusive costs are additive.

## Coverage and scaling

This table is a source inspection. The bounds are not measurements or evidence
that a particular path executed in a recording.

| Component | Exercising workload and observable evidence | Work/limit and measurement gap |
|---|---|---|
| Master broker, room directory and relay routing | Real relay socket joins, room-state delivery, relay authentication, control forwarding; admission appears in whole-recording samples | Broker has one Netty actor loop; rooms use the relay group. This does not measure many simultaneous rooms or browser fanout across rooms. |
| Identity, proof of work, authentication and SQLite trust | Whole-recording startup/joins; provider, authentication and storage stack groups | Stress uses plaintext loopback, an 8-bit PoW test configuration and seeded established identities. Production default 20-bit PoW, TLS handshakes, certificate work, persistent database growth and attack-mode 22-bit PoW are unmeasured. |
| Direct Netty host and JDK host | Independent real socket runs at the direct cap of eight | The implementations have different actor/IO models. Loopback throughput is not Internet latency or TLS throughput. |
| Shared room actor, controls, round engine and standings | Attempt starts/finishes, rounds, pings, control messages and standings page contracts | Per-room mutable state is serialized. Full standings sorting and room-state fanout scale with room population; counts of accepted finishes are correctness observations, not verifier outcomes. |
| Ghost validator and binary codec | Real upstream three-frame batches and downstream aggregate decoding | Cosmetic synthetic poses validate protocol/stream admission, not simulated ROM correctness. The source-address proxy continuously drains traffic. |
| Ghost hub and relevance | Relay traffic and recipient-identity/roster assertions; inclusive hub/codec samples | Hub runs at 20 Hz and caps nearby senders at eight. The measured baseline recipient loop still scans every drained sender: O(n²) work even with bounded outgoing nearby payloads. The follow-up below removes that second full scan. |
| Spatial classification | Hub rebucketing and per-recipient near sets | Buckets are 512 px, enter/exit distances 480/800 px. The measured baseline sorts crowded candidates per recipient, worst case O(n² log n). The follow-up retains a bounded nearest-eight insertion, while candidate discovery remains quadratic in a dense room. |
| Backpressure and queues | Existing focused correctness tests; ordinary continuously draining sockets | Hub thresholds degrade above 64 KiB, roster-only above 256 KiB, disconnect above 1 MiB or 30 s sustained degradation. These thresholds are not measured queue occupancy or proof of sustained slow-client recovery. Client inbound queue is bounded to 512 events. |
| Client transport, pump, ping and publishing | Rendered client plus colocated production `RaceClient`/`MasterClient`; client-vs-server thread groups | Main-thread pump drains controls/aggregate/roster events. Three-frame upstream batching hashes the exact cosmetic frame stream. Colocated bots do not run the rendered client's complete coordinator. |
| Jitter buffer, remote ghost registry and pose conversion | Rendered client route; registry/playback/presentation groups | Per-slot playback buffers at most 128 frames, uses 6–30 frame adaptive delay and at most six frames extrapolation. Stale playbacks are removed after 120 quiet `advanceAll` calls, so eight-neighbor output does not prove only eight live playback objects. |
| Multiplayer HUD, minimap and compact text | Rendered client with the full roster, running/finish/round-end states; HUD stack group | HUD state builds far/near lists, progress scans roster positions; pre-rank nearest-player selection sorts candidates. A full roster stresses these O(n)/O(n log n) paths even when drawn ghosts are bounded. |
| Ghost art, sprite rendering and graphics submission | Actual engine rendering, not the socket harness | Ghost sprite and general renderer groups distinguish attribution when sampled. Native graphics waits and capture readback are not a GPU frame-time measurement; software EGL performance does not establish hardware/windowed rendering performance. |
| Local Time Attack attempt, input recording, ghost recording and storage | Ordinary rendered route and attempt callbacks where exercised | Record/capture/serialization work belongs to the actual client, not bots. No upload or verifier job is inferred merely from a cosmetic finish. |
| Recording upload, bounded HTTP admission, blob retention, verification queue, verdict processing and sanctions | Source inspection and focused correctness coverage; no completed production verification cycle in cosmetic socket stress | Defaults include 64 KiB recording limit, 512 MiB blob storage, 4,096 tracked verification jobs and a 300 s worker lease. These are admission/storage bounds, not upload throughput or verification capacity. |
| Verifier replay worker | Unmeasured by cosmetic socket and rendered demonstration workloads | One job at a time: fetch/hash/decode input, replay ROM-backed engine, compare finish/time/ghost hash, sign/post verdict. Capacity depends on route duration, replay speed and worker count and needs a separate completed real recording/ROM replay workload. |
| Configuration, track catalogue, browser/lobby settings and retry paths | Source inspection; route setup covers only paths actually traversed | Initialization or a screenshot does not establish steady-state browser refresh, many-room operation, persistence or retry performance. |

Owning sources:
[hub](../../../../src/main/java/com/openggf/net/hub/GhostHub.java),
[classification](../../../../src/main/java/com/openggf/net/hub/RelevanceClassifier.java),
[coordinator](../../../../racing/time-attack/src/main/java/openggf/timeattack/mp/MultiplayerRaceCoordinator.java),
[playback](../../../../src/main/java/com/openggf/net/client/RemoteGhostPlayback.java),
[master defaults](../../../../racing/server/src/main/java/openggf/racing/server/master/MasterConfig.java),
[verification queue](../../../../racing/server/src/main/java/openggf/racing/server/master/VerificationJobQueue.java),
[worker](../../../../racing/server/src/main/java/openggf/racing/server/verifier/VerifierWorker.java).

The steady cosmetic pose payload is much smaller than a synchronized finish
burst. At exactly 60 frames/s and three frames/batch, a seven-byte pose and
ten-byte upstream packet header imply 620 B/s per sender. Eight nearby senders
with one three-frame aggregate entry each imply
`20 * (6 + 8 * (10 + 3 * 7)) = 5,080 B/s` per recipient. A full 256-player binary
roster is `3 + 256 * 5 = 1,283` bytes, emitted once per 20 hub ticks. These are
illustrative payload arithmetic for an ideal cadence, excluding control messages,
relay/WebSocket framing, jitter/backlogs and network overhead; they are not measured
bandwidth or a hard traffic ceiling.

Every improved finish broadcasts a top-ten `StandingsDelta` to the entire room.
An all-player finish burst therefore generates O(n²) control-message deliveries,
even though each standings payload is capped at ten rows. The measurement window
deliberately includes this work and page requests. Dividing all observed downstream
bytes by gameplay duration must not be presented as steady ghost-stream bandwidth.
`RoomHost.broadcast` already serializes each control message once and shares that
string across recipients; the colocated clients still individually receive and
decode their deliveries. Large aggregate JSON CPU/allocation samples therefore
need thread attribution before they are called a server serialization bottleneck.
Likewise, sorting all bests for standings and rank lookup is distinct from the
ten-row broadcast cap. Client recording custody is bounded to 128 entries / 2 MiB;
the uploader admits eight pending recordings and processes them on one worker,
with a 10 s request timeout and 2 s retry delay. Those limits do not establish
successful upload/verification throughput.

## Recorded results

The parent launched one prebuilt sequential batch:

```bash
python3 tools/performance/run_isolated.py --estimate-seconds 360 \
  --audit-report target/multiplayer-profile/isolation.json -- \
  python3 target/multiplayer-profile/full_batch.py
```

Its socket portion ran three fresh relay JVMs and one fresh JVM for each direct
transport. Every invocation used:

```text
target/racing-server/racing-server socket-stress --transport TRANSPORT \
  --players CAP --duration 20 --cycles 1 --seed 4675398 --mix mixed --timeout 30 \
  --report target/multiplayer-profile/NAME.json
```

`JDK_JAVA_OPTIONS` supplied the JFR flags listed above and a unique recording
destination. Completed per-arm reports establish their success. The encompassing
batch exited 1 because its subsequent rendered capture correctly failed the
production validator; that failure does not turn the five completed socket
contracts into failures. The isolation audit records both exclusive leases and no
detected bypass interference, with a 0.5 s guard interval; it does not establish
that all unrelated OS work was absent. Admission waiting precedes its start time.

All five socket arms admitted and finished every requested player, recorded zero
unexpected failures, one expected overflow rejection, one expected hostile-peer
sanction, two reconnects, one next-round reset and verified owned shutdown. Relay
arms additionally observed removal of their master-owned room. These are bounded
protocol/lifecycle observations with synthetic poses, not verified ROM replays.

| Arm | Harness measured wall / whole-JVM CPU | TCP upstream / downstream, decimal MB | Local ping p50 / p95 / p99, ms | GC collections / ms; peak sampled used heap, MiB |
|---|---|---|---|---|
| Relay 256, fresh JVM 1 | 20.392 s / 21.11 CPU-s | 4.005 / 99.591 | 3.01 / 6.55 / 9.75 | 6 / 23; 879.9 |
| Relay 256, fresh JVM 2 | 20.389 s / 20.96 CPU-s | 4.005 / 99.261 | 3.08 / 10.27 / 11.14 | 6 / 25; 817.5 |
| Relay 256, fresh JVM 3 | 20.431 s / 22.42 CPU-s | 4.005 / 99.591 | 2.98 / 6.74 / 8.94 | 5 / 21; 929.0 |
| JDK direct 8 | 20.019 s / 3.02 CPU-s | 0.125 / 0.650 | 3.61 / 4.09 / 4.29 | 0 / 0; 109.5 |
| Netty direct 8 | 20.019 s / 2.83 CPU-s | 0.125 / 0.652 | 3.63 / 4.25 / 5.78 | 2 / 7; 57.8 |

Each relay arm sent 268,800 poses and observed about 2,150,817 delivered ghost
frames, 580,479 aggregate entries and 5,120 ping samples. The third arm observed
one additional aggregate entry and one additional full-room roster tick; timing
and batching are therefore not byte-identical. Direct arms each sent 8,400 poses;
JDK delivered 58,632 versus Netty's 58,800. No claim that Netty is faster or more
memory efficient follows from these single, instrumented, slightly different
completed workloads. Ping is through the source proxy on this host. The three
relay JVMs used 1.028–1.097 whole-process CPU seconds per wall second; this is
neither server utilization nor a production capacity ceiling. The observed 21–25 ms
of GC collection time per roughly 20.4 s relay window is small in this workload;
it supplies no evidence of a retained-memory leak or a GC pause bottleneck. Peak
used heap excludes native memory and is not process RSS.

### Component observations

The three relay traffic windows contain 337, 305 and 334 Java execution samples
(976 pooled), versus 1,015, 1,015 and 1,017 native samples. The complete recordings
contain many more startup/admission samples. Only the marked windows appear in
the following table. Ownership is the first recognized component outward from
the observed leaf, so these Java rows are disjoint; they are observation counts,
not additive CPU time.
The JFR duration marker begins just after the harness counter baseline and ends
just after its final counter read; their wall boundaries differ by about 0.01 s.

| Exclusive Java owner, pooled relay windows | Observations |
|---|---:|
| JDK client transport: control JSON codec | 288 |
| Netty server actor: ghost hub/relevance | 202 |
| Netty server transport machinery | 125 |
| Main load generator/client drain: workload/control | 92 |
| JDK client transport: HTTP/WebSocket implementation | 55 |
| Every other component/role combined | 214 |

By thread role, those same 976 observations divide into 373 client transport,
370 Netty server actor/transport, 146 generator/drain, 20 source proxy and 67
unresolved. This shows why whole-JVM CPU cannot be assigned to the server. It
also does not assign JVM compilation, GC or all native work to an application
component. The relay server's inclusive stack counts were room actor 275, hub/
relevance 228, relay routing 221, broker/directory 83 and round/standings 39.
Those counts overlap and must not be summed. In contrast, server JSON appeared
in only 12 observations while client JSON appeared in 288; the sampled control
decode burden is principally on the colocated clients in this workload.

JDK direct contains only 43 Java observations and 24 allocation observations;
Netty direct contains 28 Java observations and 337 allocation observations. Each
contains 997 native samples, many from waits. These are too sparse to rank small
components or either transport. Zero or one observations establish no zero-cost
claim. Authentication/crypto/storage appear during whole-recording admission;
they must not be assigned the traffic-window sample distribution or extrapolated
from the deliberately inexpensive test PoW to production login storms.

Estimated allocation sample weights were 6.97, 6.35 and 6.29 GiB per relay window,
35.83 MB for JDK direct and 46.21 MB for Netty direct. These estimates measure
churn rather than heap retention. Individual site/class weights vary substantially
with sampling and throttle; they are prioritization evidence, not exact byte
accounts. Pooled allocation weights for the three relay windows were:

| Exclusive owner | Estimated allocation sample weight, GiB |
|---|---:|
| JDK client transport: binary ghost codec | 6.44 |
| Netty server actor: ghost hub/relevance | 3.95 |
| JDK client transport: WebSocket fragment assembly | 2.98 |
| Main generator/client drain: workload/control | 1.94 |
| JDK client transport: control JSON codec | 1.42 |
| JDK client transport: HTTP/WebSocket implementation | 1.36 |
| All other owners combined | 1.52 |

The initial analyzer incorrectly called WebSocket `FrameAssembler` buffering
“ghost presentation”. The corrected classifier separates that component; the
parent regenerated all five summaries and checked that event counts, Java/native/
allocation sample counts and total estimated weights remained identical for each
whole recording and marked window. No recording or measured runtime changed.
Socket bots do not run the remote ghost presentation registry. This distinction
avoids assigning nearly 3 GiB of sampled buffering weight to a rendered client
subsystem that this workload never invokes.

### Rendered route and follow-up priorities

The first rendered attempt is excluded: retiming skipped intermediate recorded
poses and produced a 34 px vertical delta beyond the unchanged 32 px validator
limit. Production correctly sanctioned those bots, so the recording has no
committed successful route window. The corrected capture scheduler retains
required intermediate poses; its three focused regressions passed against the
real ghost validator, and no gameplay admission limit is weakened.

The corrected capture-only rerun completed successfully in a fresh profile,
reusing the prepared 3,230-input route. Its standalone prebuilt payload was
`openggf.racing.server.tools.TimeAttackCapacityVideoTool OUTPUT ROOT 256 180
ROUTE.ghost`, with the Java/capture/JFR flags recorded above and a classpath of
`target/racing-server/classes`, `target/classes` and the generated
`target/racing-server-classpath.txt`. Output belonged to
`$CAPTURE_ROOT/timeattack-midpack-20261021`;
the route was `$CAPTURE_ROOT/timeattack-midpack-route.ghost`.
The exact isolated launch was:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  python3 tools/performance/run_isolated.py --estimate-seconds 150 \
  --audit-report target/multiplayer-profile/capture-isolation.json -- \
  python3 target/multiplayer-profile/capture_only.py
```

The capture-only isolation audit exited 0, retained both exclusive leases and
recorded no detected bypass interference. Its execution lease lasted 108.248 s;
the parent observed approximately 1,150 s of queue admission waiting beforehand.
Those are separate from the 53.933 s rendered route window and are not added to
its gameplay duration.

Exactly one committed `openggf.MultiplayerCaptureWindow` spans
`2026-10-21T13:27:55.103516426Z` through `13:28:49.036770588Z`. The complete
recording starts at `13:27:22.064465058Z` and ends at `13:29:08.839843782Z`, so
startup and post-finish samples are present outside the route window. The
25,901,380-byte recording is below its 256 MiB limit and reports no `jdk.DataLoss`.
The interpretation therefore uses a complete committed route window, rather
than silently treating an incomplete/ring-truncated recording as full coverage.

All 256 players remained connected and had accepted standings; the rendered
player finished at rank 130. The run consumed 3,230 controller inputs over 5,770
presentations, reaching natural completion at presentation 4,716. During stepped
gameplay through that natural finish, the frozen visibility counters observed
181 distinct remote ghosts, 771 entries / 763 exits / 590 returns in active ghost
presentation, and 518 entries / 516 exits / 390 returns in the viewport. Peak
active ghosts was eight. These counters establish dynamic replacement and
reappearance on this route; the 256-player roster is not a claim that 255 full
sprites were drawn together. Owned cleanup completed. Encoding/muxing and media
delivery are outside this performance result.

The marked route contains 1,216 Java execution observations, 2,687 native
observations and 16,001 allocation observations. Of the Java observations, 638
belong to server event-loop threads, 394 to the engine/capture thread, 138 to
client transport, 14 to the proxy and 32 remain unresolved. The server hub/
relevance group owns 521 disjoint leaf observations and appears inclusively in
551 stacks. Comparator, hash-map lookup, relevance candidate sorting and TimSort
appear among its observed leaves, strengthening the earlier hub/classification
investigation priority under this clustered moving workload.

| Recognized component on the engine/capture thread | Exclusive Java observations | Inclusive stack observations, overlapping | Estimated allocation sample weight, MiB |
|---|---:|---:|---:|
| General graphics/renderer | 29 | 68 | 158.3 |
| Remote ghost registry/playback/presentation | 20 | 39 | 86.2 |
| Multiplayer HUD/menu/layout | 15 | 25 | 116.0 |
| Client pump/session/coordinator | 5 | 62 | 1.1 |
| Ghost sprite renderer | 3 | 16 | No allocation sample attributed |

These method groups are heuristic attribution of observed stacks. For example,
coordinator state composition can appear in the client-pump group while called
from the HUD. They do not isolate each component's complete causal cost, and
inclusive columns must not be summed. The thread's other 271 exclusive
observations belong to other engine work, including ordinary gameplay and real
offline audio synthesis; the remaining observations include diagnostic bot
driving, capture, transport and crypto. The complete marked JVM's estimated
allocation weight is 6.318 GB, of which the server hub owns 2.869 GB. These
estimates include the colocated server and bot clients and are not a rendered
client memory budget.

Native observations are dominated by `EPoll.wait`: 2,342 client, 314 server and
18 unresolved samples. They establish waiting observations, not 2,674 samples
of useful CPU work. A single native graphics observation is no GPU-time estimate;
unattached native driver threads and the FFmpeg child are not comprehensively
sampled by these Java/native method events. There were 29 GC events and 35
top-level pause events totalling 91.7 ms in the route window; cumulative pause
duration is neither a frame-time percentile nor a client-only pause attribution.

This recording observes the previously missing real client pump, buffered ghost
replacement, HUD and sprite/renderer paths. It has no matched unprofiled route
control, isolated client CPU counter or per-frame component timers. Therefore
neither the 53.933 s paced duration nor these sample counts establish incremental
multiplayer frame cost or a performance regression. The socket and rendered
workloads have different pose cadence, fanout, warmup, heap and capture work;
their allocation or sample distributions must not be treated as a before/after
optimization comparison.

Evidence supports the following investigation order, without claiming measured
savings from changes that have not been implemented:

1. Profile a server in a separate process before setting server CPU or many-room
   budgets. Current role observations are useful attribution but cannot apportion
   whole-process CPU, shared compilation or GC exactly.
2. Investigate the hub's repeated sender scans, candidate sorts and temporary
   collections under clustered 256-player traffic. Preserve hysteresis, near caps,
   backpressure, byte ownership and protocol behavior when comparing alternatives.
3. Measure finish-burst control fanout/client decode separately from steady pose
   traffic; both are part of this workload. The server already encodes a control
   broadcast once. Any coalescing would need explicit rank/finish/round ordering
   guarantees rather than silently dropping updates.
4. Audit binary packet/frame assembly copies and allocation ownership with a
   targeted allocation profile. Defensive copies enforce ownership; removing them
   without equivalent lifetime guarantees is not an established optimization.
   For the rendered client, separately investigate reusable HUD/roster/layout
   data with a matched route control: the sampled HUD allocation weight warrants
   a targeted allocation check, but its 15 Java observations do not quantify a
   frame-time saving.
5. Profile production TLS/PoW, many rooms, slow readers and real recording upload/
   verification in separate bounded workloads. The source limits above identify
   which resources to saturate; cosmetic success is no evidence of verifier
   throughput or Internet behavior.

Raw recordings and summaries are temporary diagnostics for parent inspection and
cleanup, not committed evidence archives. The durable record is this interpreted
audit and the reproducible helper. Production TLS/login storms, many-room browser/
broker load, sustained slow readers, upload/recording storage throughput and
completed ROM-backed verifier cycles remain explicitly unmeasured.

### Final presentation wording

Pixel inspection after the profiled capture found that the compact font substitutes
`?` for unsupported `#`. The final HUD uses plain local-rank digits and a supported
period in compact standings rows. This label-only correction follows the measured
source fingerprint above; no profiler totals are reassigned to the final wording,
and no savings or regression is inferred. The final demonstration is regenerated
without JFR as an ordinary offscreen capture. The earlier profiled route remains
the source of the component observations and its stated visibility counters.

The final unprofiled demonstration also finished at rank 130/256 with all 256
accepted finishes and clean shutdown. Its gameplay-only visibility counts were
181 distinct identities, 763 rendered-list entries / 755 exits and 383 viewport
returns, with a peak of eight ghosts. Its 96.133 s video is distinct from the
profiled recording above; its counters are not substituted into that JFR window.

## Optimization follow-up from `625db2f45`

The follow-up investigates the observed hub, control and allocation costs in an
isolated `feature/ai-multiplayer-performance-20261021` worktree. It leaves the main
checkout and integration/pushing to the coordinating thread.

Candidate changes preserve public signatures and packet bytes:

- `GhostHub` looks up at most eight selected nearby senders instead of scanning
  every drained sender per recipient. It caps membership in nearest-first order,
  then sorts a separate small slot array for the existing ascending wire order.
  Missing pending data does not refill a degraded recipient's four-neighbor set.
- `RelevanceClassifier` retains only the nearest eight candidates by bounded
  insertion, with distance/slot ties, Chebyshev distance and 480/800 px inclusive
  hysteresis unchanged. Primitive history replaces temporary sets; a full-sort
  independent oracle checks movement, negative coordinates, boundaries and reuse.
  Dense candidate scanning still has quadratic room-population behavior; this
  does not make all hub work linear or establish a larger supported room cap.
- Removing a slow consumer after rebucketing previously left its slot in a bucket,
  allowing a later query to dereference a missing position. The classifier now
  skips removed positions. Dedicated coverage exercises removal during a hub tick
  and excludes the departed player from later aggregates and roster output.
- `HostRoundEngine` sorts accepted bests once to construct the top-ten broadcast
  and find the current finisher's private rank. Broadcast-only paths build at most
  ten rows; complete standings, page and results operations remain complete.
- `GhostPackets.encodeAggregate` reads its nested record's already-owned array
  internally, avoiding three public-accessor clones per entry. Constructors and
  public accessors keep defensive copies, and the output packet owns its bytes.
- `FrameAssembler` gives a complete binary message one newly owned output array,
  avoiding accumulation and a second copy when there are no buffered fragments.
  Fragmented messages keep their accumulator; cap checks happen before consuming
  the supplied buffer, with widened arithmetic for the running binary size.

Identical top-ten broadcast suppression was rejected. A `StandingsDelta` also
triggers `MultiplayerRaceCoordinator`'s rate-limited around-you page refresh;
`RankUpdate` is private to the current finisher. Dropping apparently redundant
broadcasts could leave other participants' private rank/page stale. The candidate
keeps every existing finish, verification, expiry and round-end notification and
makes no intentional fanout or downstream-byte reduction claim.

A separate text-byte suspicion did not establish a remote bypass. JDK WebSocket
text callbacks contain legal UTF-16 sequences; its UTF-8 decoder carries incomplete
network bytes and emits supplementary codepoints together. Synthetic callbacks
that split a surrogate pair are outside that guarantee. Whole-message direct,
master and inner-relay codec limits also check UTF-8 bytes before parsing. The
binary-allocation change leaves text counting and parsing behavior unchanged.
Local JDK 25 API/decoder source corroborated the callback contract; the available
source archive was a different vendor build, not asserted to be the active binary.

Independent read-only reviews found no regressions in the hub/classifier ordering
or the codec/assembler/standings ownership and notification changes. Dynamic
verification and fresh matched measurements are recorded below; their limits still
apply to any performance claim.

### Availability findings from the optimization corpus

The stricter burst corpus exposed two independent failures before optimization
was measured. Both inputs remain within the published per-connection control and
frame bursts.

First, several short valid attempts can accumulate before one 20 Hz hub tick.
The hub previously drained them into one recipient aggregate. Three senders with
15 twelve-frame attempts produce a packet above 4 KiB; eight senders with 32
one-frame attempts produce more than 255 entries. Either case throws from
`GhostPackets.encodeAggregate` after the queues have been drained. A third exact
boundary produces a valid 4,094-byte aggregate, but `HostMasterLink` cannot add
its three-byte fallback envelope because the outer binary cap is 4,096 bytes.
The focused before-state run failed all three cases with `aggregate exceeds binary
cap`, `aggregate entry count`, and `relay payload length 4094`, respectively.

`GhostHub` now retains the one-encode/one-send path for ordinary traffic. When a
trusted aggregate exceeds its collection cap or the 4,093-byte fallback payload
budget, it bisects contiguous entry ranges and submits them in order with the same
hub tick. Intrinsic entry errors still propagate. Encoding is the only operation
inside the capacity-recovery catch; a transport exception is submitted once and
propagates, because an already submitted prefix cannot safely be retried.

Second, fallback guests share their direct host's one master WebSocket. The master
previously charged every host-to-guest wrapper to that socket's ordinary 60/s,
120-message burst. Four healthy fallback recipients generate 80 binary wrappers
per second at the hub cadence and exhaust a full shared bucket after roughly six
seconds; seven generate 140/s. Closing that broker socket removes the host's rooms
and all of its tunnels. The socket stress transports do not exercise this path:
relay mode uses a master-hosted room, while JDK and Netty modes connect directly.

Broker frames are now classified on the broker loop. Only an admitted source with
exact tunnel ownership (and, for text, the existing valid session token) bypasses
the ordinary broker quota. Each owned tunnel has an independent combined
text/binary 60/s, 120-message bucket; excess removes that guest alone. Malformed,
unknown, unauthenticated, foreign-owner and wrong-token traffic keeps the original
broker quota and cannot spend a victim tunnel's allowance. Broker binary work is
serialized with the existing attach handoff and auto-read pause. One classified
frame may be in flight and at most 960 decoded frames may wait, equal to one
120-message burst for each supported direct-room player. A separate 4 MiB retained
budget admits all 960 maximum-size binary wrappers (3.75 MiB), counts pending text
conservatively at twice its UTF-8 payload size and rejects broker binary above
4 KiB before copying. This admits a coalesced healthy fanout without permitting
roughly 121 MiB of maximum master frames; the next frame beyond either bound closes
the broker connection. Because that preclassification path cannot yet trust a
claimed tunnel owner, its memory ceiling protects the process rather than proving
guest-only isolation for a deliberately oversized coalesced host backlog.

The advertised direct-room limit remains eight. A custom in-process `RoomHostConfig`
can request up to 256 without relevance filtering, so ordinary traffic reaches the
aggregate byte cap around 133 players. That is a public configuration/API mismatch,
not an advertised direct-room path, and this change does not claim a larger direct
capacity. Large published rooms continue to use relay routing and eight-neighbor
relevance filtering.

### Matched socket measurement

The baseline is clean `625db2f45`; the measured candidate uses the eight runtime
files listed above with sorted path/content SHA-256
`6a10994ba51fd44ac70a97020c361a317fdab086f0c31fff35ba224dca5b5add`.
The stress-tool source remains
`2583058898e3a0c33d6a7858a6b68ff4c6944af0f99006d8919adadcfdc37ab4`.
Prebuilt launchers ran under one exclusive test/build-activity lease with the
0.5 s process guard; its audit recorded exit 0 and no bypass interference.
The later broker pending-byte hardening changes the final eight-file digest to
`2dd192630ed52f7c31701d0edfeadb2debc9f3c5fc14c89c5b955b8aa4684b41`.
It leaves the existing post-attachment room path unchanged and acts only while the
socket is in broker mode, so no measured totals are reassigned to it.

The fixed unprofiled order was baseline 1, candidate 1, candidate 2, baseline 2,
baseline 3, candidate 3. Each arm was a fresh JVM using:

```text
target/racing-server/racing-server socket-stress --transport relay \
  --players 256 --duration 20 --cycles 1 --seed 4675398 \
  --mix mixed --timeout 30 --data ARM-data --report ARM.json
```

All six arms admitted and finished 256 players and observed one expected overflow
rejection, one hostile-peer sanction, two reconnects, one round reset, one room
removal and clean owned shutdown, with no unexpected failure. Every arm submitted
exactly 268,800 ghost frames in 89,600 binary messages and 4,004,870 measured
upstream bytes. Recipient scheduling changes aggregate delivery counts slightly,
so downstream results are observations rather than an equality oracle.

| Unprofiled 256-player relay, three fresh JVMs | Baseline median [range] | Candidate median [range] |
|---|---:|---:|
| Measured wall, s | 20.360 [20.343–20.379] | 20.352 [20.342–20.380] |
| Whole colocated-process CPU, s | 17.75 [17.42–19.99] | 19.15 [18.11–20.15] |
| TCP downstream, decimal MB | 99.591 [98.729–99.591] | 99.591 [99.062–99.591] |
| Local ping p50 / p95 / p99, ms | 3.05 / 6.74 / 10.08 | 3.16 / 7.37 / 10.41 |
| GC collections / collection ms | 5 / 21 [20–24 ms] | 4 [4–5] / 20 [17–21 ms] |
| Peak sampled used heap, MiB | 897.2 [893.1–899.4] | 897.6 [897.0–899.2] |

The candidate-minus-baseline CPU deltas for corresponding fresh-JVM ordinals were
+0.16, +0.36 and +1.73 seconds. The median CPU is 7.9% higher, while measured wall
is effectively unchanged and the ranges overlap. This bounded colocated workload
therefore does not demonstrate an end-to-end throughput improvement. It also does
not isolate server CPU: JSON decoding, JDK client transport, the load generator and
the source proxy share the process, and the third pair shows material host/JIT
variation. No rerun was selected or discarded based on its result.

One fixed profiled baseline/candidate pair followed the unprofiled arms with the
same workload and JFR settings described above. Both again delivered 580,479
aggregate entries and 2,150,817 ghost frames. Whole-process CPU was 22.85 versus
21.12 seconds, which conflicts with treating the unprofiled median difference as
a stable candidate cost and is not a substitute for the unprofiled controls.

| JFR measured traffic window, one arm each | Baseline | Candidate |
|---|---:|---:|
| Java execution observations | 318 | 256 |
| Native observations, including waiting | 1,018 | 1,015 |
| Hub/relevance exclusive Java observations | 64 | 14 |
| Hub/relevance inclusive stack observations | 72 | 22 |
| Estimated hub/relevance allocation sample weight, decimal MB | 886.9 | 143.8 |
| Estimated complete-window allocation sample weight, decimal GB | 8.214 | 4.437 |
| Top-level GC pauses / cumulative ms | 5 / 20.1 | 3 / 14.7 |

The profile directly observes the intended component path: hub/relevance exclusive
samples fell 78% and its estimated allocation weight fell 84%. The whole-window
allocation estimate fell 46%. These are single throttled sampling profiles, not
exact allocated bytes or proof of retained-heap reduction. They establish that the
temporary-collection and full-sort work moved in the intended direction; the
unprofiled process results prevent promoting that local evidence into a system-wide
speedup claim.

### Isolated hub measurement

The existing deterministic `GhostLoadTestTool` narrows the measurement to room
ticks. With relevance filtering enabled, 256 normal in-process participants each
submit three frames before every 50 ms simulated tick. The timer surrounds only
`RoomHost.tick`; identity setup, connection handshakes, upstream validation and
the final finish burst remain outside each tick sample. This exercises the hub,
classifier, aggregation, room tick and periodic roster path without sockets,
client JSON decoding or proxy work.

A second exclusive test/build-activity lease recorded exit 0 with no bypass
interference. Fourteen fresh JVMs ran a fixed alternating order, seven per revision,
with 60 simulated seconds and 1,202 measured ticks each:

```text
target/racing-server/racing-server load-test \
  --n 256 --duration 60 --mix normal
```

| 256-player in-process room ticks, seven fresh JVMs | Baseline median [range] | Candidate median [range] |
|---|---:|---:|
| Per-arm mean tick, ms | 0.775 [0.764–0.801] | 0.243 [0.238–0.269] |
| Per-arm p99 tick, ms | 2.485 [2.405–2.708] | 0.990 [0.943–1.148] |

Every arm finished all 256 healthy clients, sanctioned none and emitted the same
1,283-byte maximum packet. The median mean tick fell 68.6% (3.19×), and the median
p99 fell 60.2%. This is repeatable evidence for the local hub/classifier path and
agrees with the JFR attribution. It is not socket throughput, many-room capacity,
Internet latency or isolated production-server CPU; the full socket controls above
remain the system-level bound for this task.
