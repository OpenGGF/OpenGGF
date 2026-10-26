# Multiplayer authority and lifecycle hardening

Date: 2026-10-26. Status: reviewed design; implementation not started. Task base:
`08f0a5e78b4c7aaa11604e6e9eb9f2cb4151daa4` (`develop`, engine
`0.7.prerelease`, networking protocol 7).

## Intent

Repair the reviewed resource, identity, lifecycle and race-authority failures in
the common multiplayer stack and bundled Time Attack mod without centralising
the existing owners or changing the product model. Local simulation and timing
remain authoritative for the local player. Remote state remains cosmetic ghost
and event data.

The common `com.openggf.net` stack remains engine-free and owns protocol,
transport, room membership and generic round state. Time Attack owns route and
character eligibility, gameplay launch, recordings and presentation. The
operator server owns discovery, sanctions, relay/tunnel routing and replay
verification. Room mutation stays on the room owner, broker state stays on the
broker loop and gameplay adoption stays on the engine thread.

## Caller usage

### Joining a listed room

The authenticated master identity is also the prepared room-join subject. A
caller cannot authenticate to the broker as one identity and supply another
identity to the room operation.

```java
PreparedRoomJoin prepared = operation.own(
        operation.await(master.requestJoin(roomId), replyDeadline),
        PreparedRoomJoin::close);
RaceConnection connection = operation.own(
        operation.await(master.joinPrepared(prepared, displayName, appearance, options),
                joinDeadline),
        RaceConnection::close);
return () -> session.adopt(connection);
```

The preparation owns the one-use broker grant, target instance and epochs,
direct route and fallback transition. Only a classified reachability failure
before delivered admission may fall back. The operation first retires the old
direct route on the player host and waits for its acknowledgement, then obtains
a fresh route generation and opens the tunnel. Security, protocol, access,
certificate and identity failures remain terminal.

### Hosting

Listener intent is explicit before remote admission is possible.

```java
JdkRaceHostServer manual = JdkRaceHostServer.startTls(
        port, config, identity, profiles, AdmissionMode.MANUAL);

JdkRaceHostServer pending = JdkRaceHostServer.startTls(
        port, config, identity, profiles, AdmissionMode.BROKER_PENDING);
HostRoomRegistration registration = operation.own(
        operation.await(DirectRoomRegistration.register(master, pending, voteTracks),
                replyDeadline),
        HostRoomRegistration::close);
```

`MANUAL` applies the room's pinned identity, TLS, password and capacity policy
without claiming operator sanctions. `BROKER_PENDING` rejects remote admission
until the complete registration operation installs its authorization bridge and
publishes the listing, then becomes `BROKER_REQUIRED`. Master loss changes it to
`BROKER_UNAVAILABLE`: existing direct members may continue, new admissions fail
closed and tunnel children retire. A failed opportunistic LAN registration
retires that uncertain instance and creates a fresh manual instance; it never
silently downgrades the same listener.

### Launching a round

The mod launches from one host-issued projection containing the committed round
configuration and the local participant's frozen effective character.

```java
session.launch().filter(runLease::isNew).ifPresent(launch -> {
    TimeAttackRuntime runtime = runtimeFactory.get();
    runLease.launch(launch, runtime, launcher);
});
```

Menu preferences and character requests are not launch authority. The lobby
shows the host's committed next configuration. The owner starts that revision,
not a saved `RoundConfig`. Runtime setup, attempt recording, finish metadata and
verification expectations derive from the same launch value. Unsupported local
boot fails before `AttemptStart`; it never substitutes another character and
then submits sanctionable evidence.

## Core shape

### Owner-local cells and incarnations

Each mutable owner retains its current thread or loop. Small private cells own a
resource for its complete lifetime and qualify callbacks with an identity and
generation.

```java
record ConnectionIncarnation(long bindingId, long generation, int peerId) { }
record JoinRoute(long joinId, long routeGeneration) { }
record RoomGeneration(String instanceId, long generation) { }

sealed interface ListenerAdmission {
    record Manual(RoomGeneration room) implements ListenerAdmission { }
    record BrokerPending(RoomGeneration room) implements ListenerAdmission { }
    record BrokerRequired(RoomGeneration room, long registrationId)
            implements ListenerAdmission { }
    record BrokerUnavailable(RoomGeneration room, long registrationId)
            implements ListenerAdmission { }
}
```

Late success disposes what it produced. Old detach/close callbacks compare the
exact binding before removal. Repeated close, kick, disconnect, cancellation,
master loss and scene exit converge on one retirement transition. Observer
cancellation cannot cancel or forge the owner's retirement receipt.

### Reservations

Every cross-owner retained payload and active physical write holds an all-or-none
reservation acquired before copying, encoding or publishing the work.

```java
record WorkCost(int items, long bytes) { }

final class WorkReservation implements AutoCloseable {
    @Override public void close(); // exactly-once release
}

final class WorkBudget {
    Optional<WorkReservation> tryReserve(
            WorkOrigin origin, WorkDestination destination, WorkCost cost);
}
```

The reservation covers origin, destination and process totals in one short
critical section. It remains charged while queued and executing, and while a
transport write is in flight. Failed copy, scheduling, discard, disconnect and
late completion all release exactly once. Admitted connections, handshakes,
join operations and cleanup cells are bounded too. A sampled `queuedBytes()` is
telemetry, not a reservation.

Cleanup does not use an unlimited priority queue. Every bounded connection or
operation preallocates a finite disconnect/retirement/completion cell. The room
owner checks these trusted signals before a bounded data slice. Retirement and
watchdog deadlines do not depend on the congested room data executor.

### Physical writers

`RaceClient` and `MasterClient` each own one bounded serial writer. Logical relay
and hosted-room traffic uses the parent master writer. Accepted messages retain
FIFO ordering, including request cancellation, attach and the room Hello.

```java
final class OutboundWriter {
    OfferResult offerText(String text, WorkOrigin origin);
    OfferResult offerBinary(byte[] bytes, WorkOrigin origin);
    CompletionStage<Void> stop(String reason, Duration deadline);
}
```

There is at most one transport write in flight. Stop rejects new offers
immediately and arms an independent absolute deadline. It does not wait for
ordinary queue capacity. Completion or abort releases its own reservation but
cannot restart a retired writer. Overflow retires the relevant connection or
tunnel and voids an active local attempt; ordered ghost evidence is never
silently dropped.

### Broker-authorized admission

Manual and listed admission are different policies. A listed direct client
presents an opaque prepared-grant reference in its signed room intent. After the
room verifies the client's identity proof, the current registered host redeems
that reference over its authenticated master link with exact request
correlation. The broker rechecks room instance, access/invite epochs, expiry,
sanctions, trust and determinism, atomically consumes the grant and returns
trusted evidence naming the authenticated fingerprint and route generation.

The room owner receives connection plus admission evidence in one operation.
It cannot call `onConnected` first and bolt on an expected fingerprint later.
Password/access, duplicate identity and actual capacity remain room-authority
checks before membership publication.

Player-host tunnel open carries the same trusted subject, registration and route
context. `HostMasterLink` owns tunnel children on the room owner; master callbacks
only offer bounded generation-qualified events. Master loss closes the binding
gate and retires each child once without touching independent direct peers.

An ambiguous direct EOF, reset or silent admission timeout follows this barrier:

1. Fence the old route from new redemption.
2. Retire its pending proof/access work or admitted member on the room owner.
3. Await the exact route retirement acknowledgement.
4. Recheck preparation policy and issue a fresh route generation.
5. Attach the fallback tunnel once.

If the barrier cannot complete, fallback is unavailable. A possibly consumed
grant is never reused and direct and tunnel membership never overlap.

### Race plan and launch

`HostRoundEngine` owns one complete next configuration and a monotonic revision.
A vote commits the winning configuration before broadcasting the result. Start
references the committed revision rather than resupplying a stale configuration.

`RoomHost` owns the effective participant character. A successful request is
acknowledged through the existing participant projection. At round commit, one
owner operation freezes the committed configuration plus each participant's
effective character into an immutable launch entry.

```java
record RaceLaunch(long roundId, long planRevision,
                  ControlMessage.RoundConfig config,
                  String participantId, int slot, String fingerprint,
                  String effectiveCharacter,
                  long countdownEndsAtHubMillis, long deadlineHubMillis) { }
```

The launch may be carried inside an evolved start/admission message rather than
exporting a new family of public plan and selection types. The client reducer
accepts it only for its admitted participant and current room instance. Duplicate
identical launch is idempotent; contradiction is a protocol failure, not cheating
evidence. Active-round join receives a frozen entry under the existing round and
deadline policy.

### Replay evidence

Native post-step capture moves to a reachable engine-internal helper that creates
the same `PlayerPose` used by hosted gameplay. A pure helper converts that pose to
the nine-field `GhostFrame`. Live Time Attack and operator replay use the same
conversion and `GhostFrameCodec`, including `hurt` and the existing
`finished=false` digest convention.

Replay completion and infrastructure unavailability remain distinct. Harness,
ROM/bootstrap, blob and unsupported-worker failures use the existing
`VOID_VERIFIER_UNAVAILABLE` path and never reach `VerdictCodec.isFail` or trust
consequences. This is containment of an observed source risk, not a claim of a
fourteenth reproduced incident. Existing participant, attempt and recording-hash
routing remains authoritative.

## Module ownership

| Owner | Primary files and extracted mechanisms |
| --- | --- |
| Common protocol | `ControlMessage`, `ControlJsonCodec`, `AuthenticationTranscript`, exhaustive semantic registry and direction checks |
| Common flow internals | bounded reservation ledger and serial physical writer; public-but-unannotated bridges only where the current unnamed-module build requires them, guarded by allowed-consumer tests |
| Common clients | `RaceClient`, `MasterClient`, prepared join and exact host-registration handles; no gameplay imports |
| Room authority | `RoomHost`, `HostRoundEngine`, atomic transport-open/admission evidence, committed round and frozen launch entries |
| JDK host | bounded room ingress, coalesced tick, independent watchdogs and truthful retirement/resource receipts |
| Operator master | grant redemption/revocation, registration and route generations, bounded attached ingress and tunnel routing |
| Time Attack mod | authoritative launch consumption, engine-thread session/run leases and eligibility choice |
| Engine host boundary | shared native post-step `PlayerPose` capture |
| Operator verifier | pose-to-frame parity and completed/unavailable decision boundary |

There is no global multiplayer supervisor. Extractions remove mechanisms from the
existing large orchestration classes while leaving state with the present owner.

## Finding closure

| Finding | Owning invariant and seam |
| ---: | --- |
| 1 | Listed admission redeems a subject-bound grant after room proof; tunnel open carries the same trusted subject; manual is an explicit listener policy. |
| 2 | Every master I/O-to-broker/room/tunnel handoff reserves tasks and bytes before copy and validates inner as well as outer frame limits. |
| 3 | The JDK room mailbox is bounded; stop gates data and signals owner retirement outside the data FIFO. A running interruption-ignoring task keeps retirement observably incomplete. |
| 4 | Every protocol family has universal null/range/cardinality/direction validation. Slot cosmetics are member-gated and batch application is atomic. |
| 5 | One bounded physical writer replaces each future chain; close has independent deadline/abort and counts the active write. |
| 6 | Typed first-terminal failures permit only reachability fallback, after exact old-route retirement and a fresh generation. |
| 7 | Live and replay share native pose capture and nine-field pose-to-frame conversion, including hurt. |
| 8 | Runtime, recording, finish and verifier metadata derive from the room-frozen effective character; local substitution is removed. |
| 9 | Exact host binding owns tunnel children; master loss retires them once on the room owner. |
| 10 | The existing gameplay pump latches kick/loss, fences new attempt work and requests safe run leave in every run phase. |
| 11 | Vote commits the host-owned next configuration; start references its revision and the launch carries that same configuration. |
| 12 | Complete registration/binding handles replace nullable unowned cleanup; the transitional null path clears before authentication and is idempotent. |
| 13 | Writer, admission, registration and run-resource mechanisms are extracted at current owners; no new central owner or pass-through stack is introduced. |

## Shutdown contract

Closing a host atomically rejects new reservations, closes listener and peer I/O,
cancels timers, discards queued data with reservation release and makes owner
retirement the next trusted action. Completed authority retirement means no later
room-state work. I/O release is a separate receipt.

A currently executing arbitrary administrative callback may ignore interruption.
Java provides no safe forced thread termination. In that case the listener is
closed, queued/new work is fenced and the retirement future remains incomplete
until the callback yields. Repeated close observes the same pending operation.
An off-owner blocking close reports an incomplete timeout rather than marking the
host closed; an on-owner close requests retirement without waiting on itself.

## Implementation order

1. Add focused regressions for semantic validation, hurt parity, character/vote
   authority, race-time terminal cleanup and null/master-link retirement. Land
   their bounded local corrections first.
2. Add atomic reservations and the deterministic serial-writer harness, then
   replace both client send chains without changing admission semantics.
3. Add bounded JDK and attached-master ingress, trusted stop cells, retirement
   receipts and exact host-binding ownership.
4. Add explicit listener modes and complete registration ownership.
5. Activate coherent protocol 8 admission redemption, route retirement/fallback,
   trusted tunnel context, committed-plan start and frozen launch. Delete old
   authority bypasses in the same activation.
6. Integrate the corrected replay worker gate and unavailable containment.
7. Regenerate the candidate Mod API signatures, rebuild the bundled mod, run
   focused integration plus the change-based smoke and guards, then remove only
   obsolete fields/adapters proved unused by the migrated consumers.

Protocol 8 activation is atomic. Mixed versions reject terminally. Intermediate
slices must not claim broker-bound direct admission or authoritative launch while
an old admission/start path remains enabled. If corrected verified support is not
ready, verified rooms remain unavailable rather than accepting mixed evidence.

## Verification obligations

- Broker identity A versus room proof B rejects before membership on listed
  direct, fallback tunnel and hosted relay routes.
- Lost direct admission success retires the exact direct route before a single
  tunnel fallback. Pending proof, password verification and late redemption all
  converge without duplicate membership.
- Simultaneous last-byte and last-item reservations admit no more than the limit;
  copy, encode, schedule, exception, discard and late completion conserve counts.
- A stalled first client write retains bounded memory; close reaches independent
  abort; no later completion restarts sending.
- Saturated JDK/master ingress still processes disconnect and retirement. A
  releasable stuck-callback test proves truthful incomplete shutdown.
- Null/coerced appearances, invalid slots in every family, invalid collections and
  illegal sentinels reject before state mutation. Nonmember cosmetic messages do
  not grow client state.
- Failed registration creates a fresh explicit manual LAN room where that product
  permits recovery. Cleanup for binding A cannot detach replacement B.
- Vote-to-second-round launch, runtime character, recording descriptor, result and
  verification job all agree. Cover locked/open, unsupported choice, late join,
  no-vote and existing early-restart behavior.
- Master loss retires all fallback guests exactly once and leaves direct peers.
  Kick/loss leaves countdown, active, finished/tally, loading, retry and resume
  states without further attempt or upload work.
- A ROM-backed damaged/recovery attempt produces identical live/replay bytes and
  digest; required-ROM skips are incomplete evidence.
- A valid 256-player roster near the measured 61,389-byte payload, worst supported
  text wrapping and a valid 4,096-byte inner binary fit their named limits.

## Mod API and documentation boundary

The Mod API remains unpublished candidate `0.7.0`. Change only public declarations
needed to remove an authority bypass or expose a complete operation. Do not use
candidate status as permission for unrelated API cleanup.

Expected public changes include the prepared join/complete registration handles,
explicit hosting admission mode, revisioned round-start intent and a self-contained
launch projection. Raw attach/redemption/binding controls cannot remain publicly
issuable outside their owning operation. Trusted admission evidence, reservations,
writer state, tunnel wrappers, native capture and verifier outcomes remain outside
the Mod API. Bounded administrative `RaceRoomHost.execute`/`room` may remain when
they funnel through the same owner and cannot bypass the active listener policy.

Update `Protocol.VERSION`, the authentication transcript domain, public signatures,
`ModApiVersion` description, normalized 0.7 pins and compatibility guide together.
Rebuild and validate the bundled Time Attack mod through the real validator.

## Rationale

### Problem

Thirteen failures arise where cross-owner work is unbounded or where identity,
lifecycle and race facts are copied into independent mutable fields. A broker can
authorize one identity while a listed room admits another; queued data can starve
cleanup; and gameplay or replay can disagree with the authority that sanctions a
result. Repairs must preserve manual/direct/relay paths, local simulation, room
capacity authority and the dependency/Mod API fences.

### Usage (caller's view)

Consumers request and consume one prepared join without supplying another
identity. Hosting starts explicitly as manual or broker-pending and successful
registration returns one owned handle. Time Attack consumes one host-issued launch
containing the committed track and effective character. Callers do not coordinate
grants, raw tunnel messages, nullable host links or independent config/character
copies.

### Shape

Owner-local lifetime cells qualify callbacks by generation and make close/detach
idempotent. Atomic reservations bind lifecycle admission to retained bytes/items
before copy, and one physical writer owns order and shutdown. Broker redemption
binds the proven room subject to the prepared grant, while explicit listener mode
preserves manual hosting without creating a listed-room bypass. A host-frozen
launch supplies runtime, recording and verification. These mechanisms hide
complexity behind complete join, registration and launch operations while leaving
broker, room and engine state with their existing owners.

### Synthesis decision

We selected Candidate B's owner-local lifetime and reservation model because it
closes the hard cross-boundary races: broker authorization is bound to proved room
identity, ambiguous direct admission is retired on its owner before fallback,
retained work is charged before publication, and each run and verifier claim
derive from one frozen participant entry. We adapted Candidate A's explicit
Manual/BrokerPending listener startup and its smaller participant/next-round
projection, while keeping B's complete registration handle and strengthening the
projection into a self-contained host-issued launch. We rejected A's installed-
claim reuse after uncertain direct success and caller-managed registration stages.
We also deferred B's broad creator-API/schema cleanup, separate public selection
protocol and generalized verifier signing/lease migration because they are not
required to close the thirteen findings.

### Tradeoffs accepted

- Listed direct admission adds a correlated broker redemption round trip and is
  unavailable when the host's authenticated master link is lost.
- Ambiguous direct fallback waits for room-owner retirement; it fails unavailable
  rather than overlapping routes when that barrier cannot complete.
- Writer or ingress overflow retires/voids instead of silently dropping evidence.
- Protocol 8 requires coordinated operator/client/mod deployment rather than an
  insecure compatibility mode.
- A running callback that ignores interruption can delay completed retirement;
  the API reports this truthfully instead of claiming quiescence.

### Alternatives considered

- Host-installed admission claims avoid a redemption round trip but replicate
  authorization lifetime and require revocation plus the same ambiguous-route
  barrier. Broker-owned redemption has one authorization writer.
- A global actor/supervisor would unify scheduling but cross the established
  broker/room/engine ownership and dependency boundaries.
- Patching only the lobby's saved configuration or local launch character leaves
  multiple authorities and permits the same disagreement to recur. The frozen
  launch is the smaller durable authority.
- A full public protocol/domain rewrite and generalized verifier signing migration
  improve encapsulation but are not necessary to close the reviewed findings.

### Open questions and risks

- Initial byte/item budgets and drain slices are safety defaults, not measured
  throughput optima. They require stress validation with the full supported roster.
- The exact minimal public signature delta depends on the complete consumer
  inventory found during implementation and the protocol-8 deployment gate.
- Product behavior for an already-running listed direct room after master loss is
  fixed here as existing-direct-members-continue, new-admission-unavailable; tests
  must prevent later code from silently choosing a different policy.
- Infrastructure-failure containment must not accidentally weaken the existing
  policy for authenticated, completed deterministic mismatches.

### Next implementation step

Add the focused hurt-parity, semantic-validation, host-link retirement,
character/round-authority and race-time disconnect regressions, observe their
expected failures, then land those independent corrections before the transport
and protocol migration.
