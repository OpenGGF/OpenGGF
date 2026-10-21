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
