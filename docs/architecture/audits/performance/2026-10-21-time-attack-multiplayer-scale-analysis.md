# Time Attack multiplayer scale and lifecycle analysis

## Purpose and evidence boundary

This audit consolidates the 2026-10-21 Time Attack networking investigation:
adversarial protocol coverage, full-room socket stress, a rendered 256-player
demonstration, isolated component profiling, multi-room server measurements,
bandwidth reductions, deployment sizing and the follow-up survey of established
large-multiplayer replication techniques.

The detailed measurements and their hazards remain in
[the component performance audit](2026-10-21-multiplayer-component-performance.md).
The stress-system implementation record remains in
[the racing stress plan](../../plans/2026-10-21-racing-stress-system.md).
This document interprets those results as one operational picture. It does not
turn a local synthetic workload into an Internet capacity guarantee.

Measured claims below come from real production WebSocket clients and servers on
loopback, with one distinct source address per admitted connection. Synthetic
racers publish protocol-valid cosmetic poses; they do not run 1,024 ROM-backed
engines. The rendered demonstration runs one real Engine client and 255 protocol
peers. TLS, production-strength first-use proof of work, shared NAT, WAN loss,
sustained slow readers and completed verifier replays remain separate limits.

## Executive assessment

The system can operate four simultaneous 256-player relay rooms on the measured
host with large timing headroom. After optimization, steady server work used about
0.66 CPU-core equivalent, the worst room tick p99 was 5.07 ms against a 50 ms
cadence, Java heap peaked at 97.2 MiB and four-room downstream payload averaged
about 40.6 Mbit/s before transport overhead.

The original immediate failure was lobby admission traffic. Broadcasting a full
roster after every join made delivered messages quadratic and cumulative roster
bytes cubic. Protocol v3 now gives the entrant one full snapshot and sends bounded
upsert/leave deltas to existing members. Four-room admission fell from 3,316.0 MiB
to 51.2 MiB, a 98.5% reduction, while the slowest fill fell from 12.27 to 7.66 s.

Packet-local ghost compression reduced steady downstream traffic from 64.0 to
48.4 MiB per ten seconds and upstream traffic by 23.5%. It retains no cross-packet
state and falls back to raw frames when compression would expand a packet.

The next practical limit is egress, followed by the synchronized finish/standings
burst. CPU, Java heap and GC are not current pilot constraints. Verification
capacity is unknown because no completed ROM-backed replay matrix was measured.

## Delivered behavior and hardening

- Stateful fuzz and adversarial tests cover connection, broker, tunnel, lobby,
  countdown, attempt, standings, reconnect and teardown states.
- Relay capacity is 256 players; direct hosting remains capped at eight.
- A real-socket scale harness drives 1, 2 and 4 full rooms through admission,
  browser polling, 60 Hz pose production, standings, reset, reconnect and removal.
- The master rejects over-limit guest tunnel text and binary messages before
  wrapping them, closing only the offending guest. The host broker link and
  unrelated hosted rooms survive.
- Control JSON rejects primitive nulls, duplicate identities, oversized values,
  polymorphic input and invalid protocol/state combinations.
- Recording publication is staged and atomic; cached storage totals change only
  after publication. Startup removes abandoned staging files.
- SQLite has live-predicate indexes for sanction expiry and new-identity cleanup.
- Verifier workers heartbeat during synchronous replay, while the configuration
  prevents a stale interval shorter than a timed-out heartbeat plus retry.

## Measured scale results

All optimized runs admitted and finished every healthy player, rejected capacity
plus one, completed browser and churn traffic, reset and reconnected, removed all
rooms after host departure and terminated child processes.

| 256-player rooms | Server CPU / 10 s | Peak RSS / used heap | Worst tick p99 | Queue p99 | Admission down | Steady down / 10 s | Slowest fill |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 2.22 s | 422.3 / 91.2 MiB | 2.50 ms | 0.027 ms | 12.5 MiB | 12.1 MiB | 6.14 s |
| 2 | 3.83 s | 447.1 / 91.8 MiB | 3.47 ms | 0.050 ms | 25.2 MiB | 24.2 MiB | 7.02 s |
| 4 | 6.58 s | 591.9 / 97.2 MiB | 5.07 ms | 0.043 ms | 51.2 MiB | 48.4 MiB | 7.66 s |

The four-room RSS result rose from the baseline 477.3 MiB while used Java heap
fell. One native/off-heap sample is insufficient evidence of a leak or of a safe
smaller allocation. Retain the 4 GiB deployment recommendation until repeated
longer measurements explain native retention.

## Performance through the lifecycle

| Stage | Main components | What is established | Remaining risk |
|---|---|---|---|
| Cold boot | JVM, Netty, configuration, track catalogue, SQLite, blob reconciliation | Correct startup and owned shutdown | No isolated boot CPU/time at production storage cardinality |
| Room publication | Broker, directory and browser JSON | Four-room browser maximum 10.78 ms | Many-room browsing still scans/copies matches |
| Identity and admission | Source limits, PoW, SQLite, control codec, membership | 1,024 admissions; 7.66 s slowest room; 49.61 ms worst join p99 | TLS, 20/22-bit PoW, new identities and CGNAT unmeasured |
| Countdown | Round configuration, control fanout, publisher priming | Every client agreed on one start | Warmup cost is not isolated from adjacent phases |
| Steady race | Validator, room actor, relevance, hub, aggregate relay | CPU, heap, GC, TCP bytes, tick and queue results above | Egress grows with detailed visible racers; dense candidate discovery remains quadratic |
| Rendered route | Client pump, jitter buffers, registry, HUD, sprites, renderer | Middle runner finished 130/256; 181 remote identities rotated through a peak of eight active ghosts | No matched multiplayer-off client frame-time control |
| Finish burst | Attempt validation, ranks, top-ten delta, fanout | All 1,024 finishes accepted | O(n²) delivered control updates; stage bytes/CPU/time unmeasured |
| Standings and round end | Sorting, pagination, client decode | 104 pages returned every unique rank | Burst decode and sorting need a separate marker |
| Reset and reconnect | Membership, session, playback retirement | One clean reset/replacement reconnect per room | Reconnect storms and retained-state latency unmeasured |
| Host departure | Broker removal, tunnel/client close, executors | Rooms removed, proxies stopped, port released | Simultaneous slow-close storm unmeasured |
| Upload and storage | HTTP admission, hashing, atomic blob publication, queue | 10,000-file write p50 improved 22.18 ms to 0.055 ms | Concurrent real uploads, flush latency and full-volume behavior |
| Verification | Input decode, ROM replay, hashes, heartbeat, verdict | A 36,000-frame mask scan improved from 96.3 ms/1.297 GB allocated to 1.29 ms/36 KB | End-to-end replay throughput remains unknown |

The profiled rendered recording spans a 33.039 s setup envelope, a 53.933 s
marked route and a 19.803 s post-route envelope. The outer intervals include
diagnostics and capture work and are not isolated lobby or finish timings. The
final unprofiled demonstration is 96.133 s end to end.

## Component assessment

### Master, broker and directory

The broker remains a single actor loop. Measured dispatch and browser latency are
small at four rooms, but the process is not horizontally distributed. Room
ownership must remain sticky if relay workers are separated. The directory should
gain conditional responses before a new index. Browsing currently uses
`RoomListRequest`/`RoomListResult` on the master WebSocket, so a client-known
generation and an explicit unchanged result can make an unchanged poll nearly empty
without adding an HTTP endpoint. Page caches must be bounded and keyed by every
filter, sort, page and visibility-authority input.

### Authentication and persistence

Proof-of-work allocation and CPU were materially reduced, and the SQLite cleanup
predicates are indexed. Production login storms remain a distinct workload because
the capacity tests seed established identities and use cheap test PoW. Source-IP
limits protect the service but can reject legitimate households, schools and
carrier-NAT populations; measure that case rather than silently relaxing the cap.

### Room actor, relevance and ghost hub

The room cadence has substantial measured headroom. `RelevanceClassifier` already
uses 512 px X-axis buckets and bounded nearest selection, avoiding a global sort.
Candidate work within crowded nearby buckets remains pairwise. Refining that index
with route or Y partitioning, or caching evaluations, can reduce CPU after racers
spread out; it cannot protect a starting pack where all racers occupy one bucket. A
per-recipient byte budget must protect both dense and distributed cases.

### Relay and transport

Current total steady relay traffic is roughly 5 KiB/s per recipient with eight nearby
racers; the measurement includes coarse roster, control and probe traffic as well as
detailed ghosts. The state portion grows approximately linearly with the number of
detailed racers. Increasing the fixed cap to 16 could move the four-room payload from
about 41 toward 81 Mbit/s if the whole measured rate doubled, but that is deliberately
a rough upper extrapolation because fixed coarse/control overhead does not double. A
mixed-rate policy should be judged against separated state/control counters before it
claims that 16 racers fit the current byte envelope.

WebSocket/TCP gives reliable ordering and makes baseline recovery simpler, but an
obsolete pose cannot be removed after it enters the socket. Slow-client protection
must replace or coalesce poses before encoding/write. QUIC datagrams could improve
freshness under loss later; they do not inherently reduce healthy-link bytes.

### Client transport, playback and rendering

Control JSON led sampled client Java work in the colocated relay profile. The
rendered route proved real pump, interpolation, replacement, HUD and rendering
paths. It did not isolate incremental multiplayer frame cost. Current
`RemoteGhostPlayback` assumes contiguous canonical frame indices: a missing index
enters its dry/stall path and velocity comes from the immediately preceding index.
Lower network sample rates therefore require a sparse source-timeline playback
contract rather than merely announcing the intended interval. Full roster scans and
layout deserve matched on/off timing before increasing visual density on low-end
clients.

### Recording, storage and verifier

Recording mask snapshots no longer allocate per scan, and blob writes no longer
rescan the directory. Attempt uploads remain capped and asynchronous. One verifier
process performs one replay at a time, so worker count should follow queue age and
measured replay wall/CPU time rather than room population.

## Relevant large-multiplayer techniques

The external survey supports the following patterns:

- [Epic Replication Graph](https://dev.epicgames.com/documentation/unreal-engine/replication-graph-in-unreal-engine)
  uses persistent shared relevance lists to avoid repeating actor-by-connection
  work at Fortnite-scale actor counts.
- [Unity Netcode](https://docs.unity.cn/Packages/com.unity.netcode%401.5/manual/optimizations.html)
  fills a bounded snapshot with entities ranked by importance and age, supports
  distance scaling and separates simulation rate from send rate.
- [Photon interest management](https://doc.photonengine.com/fusion/v2/manual/data-transfer/eventual-consistency)
  separates relevance from per-recipient send priority and uses eventual
  consistency for replicated state.
- [Valve Source networking](https://developer.valvesoftware.com/wiki/Source_Multiplayer_Networking)
  sends acknowledged delta snapshots under a client byte-rate limit, with full
  snapshots for startup/recovery and interpolation between lower-rate updates.
- [Roblox streaming](https://create.roblox.com/docs/workspace/streaming)
  varies replication around per-player focus and accounts for device memory and
  performance, while warning that extra foci multiply server/client cost.
- [Steam networking](https://partner.steamgames.com/doc/api/steamnetworkingtypes)
  exposes pending bytes/queue time and supports data that should be discarded
  when it cannot be sent promptly.
- [QUIC DATAGRAM](https://datatracker.ietf.org/doc/html/rfc9221) supports
  congestion-controlled unreliable messages beside reliable streams; this is a
  later freshness option, not a first bandwidth optimization.

These sources reinforce a common architecture: fixed per-recipient budgets,
importance plus aging, mixed update rates, dormancy for unchanged state, persistent
interest membership, acknowledged baselines and replacement of obsolete movement.

## Recommended next design

Replace the hard interpretation of “eight nearby racers” with a byte-budgeted
quality policy. As an initial experimental schedule:

```text
2 closest/on-screen racers × 60 samples/s
6 nearby racers           × 20 samples/s
8 contextual racers       ×  5 samples/s
-----------------------------------------
16 represented racers,      280 samples/s
```

The current eight-at-60 model represents 480 samples/s. The proposed mix therefore
uses 42% fewer detailed pose samples before headers and control traffic while
doubling the represented field. Rendering can continue at display rate only after
playback gains the sparse source-timeline contract described above. This is a test
hypothesis, not a capacity promise.

Within the fixed budget, prioritize on-screen proximity, approaching/overtaking,
abrupt state change, predicted viewport entry, time since last update and current
prediction error. Age must raise omitted racers to prevent starvation. Preserve
hysteresis so minor distance changes do not churn membership.

Then add lossless integer residual prediction across baselines. Predict the encoded
position from prior exact values and transmit the residual; do not simulate remote
Sonic gameplay. Send full state on first visibility, attempt/generation change,
reconnect, teleport, missing baseline and periodic recovery. Retain raw fallback.

Separately, stop routine poses for stationary racers (finished and reset senders already
leave near sets; players submit several attempts per round, see
[the multiple-attempts plan](../../plans/2026-10-21-time-attack-multiple-attempts.md)), replace stale unsent
movement, coalesce standings broadcasts within one room tick, run-length encode
recorded input masks and use conditional directory responses.

## Deployment starting point

| Role | Recommended initial allocation |
|---|---|
| Master plus relay, up to four 256-player rooms | 4 recent general-purpose vCPU, 4 GiB RAM, `-Xmx1g`, local NVMe, 250 Mbit/s symmetric minimum; 1 Gbit/s preferred |
| Directory-only master | 2 vCPU, 2 GiB RAM, local NVMe |
| Verifier worker | 2 fast vCPU, 4 GiB RAM, `-Xmx2g`, ROM on local read-only storage |
| Recording/blob and SQLite volume | 20 GiB NVMe initially, backups, 70/85% alerts |
| Eight-player direct host | 2 modern cores, 1–2 GiB RAM, 20 Mbit/s symmetric |
| Rendered client | 4 modern cores, 4 GiB available RAM, hardware OpenGL |

The economical scaling order is regional relay/egress, verifier workers when queue
age rises, storage before retention pressure, and directory/relay separation when
browser latency correlates with room ticks. Larger heaps provide less immediate
user value.

## Measurement work still required

1. Add explicit markers and byte/CPU/queue metrics for boot, publication, countdown,
   finish burst, standings, reset/reconnect, upload and verifier replay.
2. Compare fixed-eight against budgeted 8/12/16/24 visibility using the same motion,
   crowding and source fingerprint.
3. Measure client frame time with multiplayer disabled and enabled on the same route,
   including weaker hardware.
4. Run loss/jitter/slow-reader cases and verify newest-state replacement, keyframe
   recovery and bounded reliable control.
5. Run TLS plus production PoW admission, fresh identities and shared-NAT cohorts.
6. Complete real recording upload and ROM-backed verification cycles; report replay
   seconds per gameplay second and queue-service rate.
7. Repeat longer four-room runs before interpreting native RSS retention.

## Rejected shortcuts

- Full input-only remote simulation moves bandwidth cost into client CPU, version
  coupling and divergence around objects and local world state.
- Global time dilation suits one shared overloaded battle, not independent Time
  Attack simulations.
- Generic WebSocket compression adds CPU, memory and parsing risk to already-small
  binary frames without demonstrated benefit.
- Spatial cells alone do not protect the dense starting pack.
- Raising the visible cap without a byte budget makes network cost predictable only
  by overprovisioning.
