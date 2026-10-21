# Time Attack budgeted replication design

## Status

Proposed follow-up to the delivered protocol v3 membership deltas and packet-local
ghost compression. This design deliberately introduces one current protocol rather
than a compatibility path; Time Attack multiplayer has not shipped.

The evidence and rationale are in
[the consolidated scale analysis](../audits/performance/2026-10-21-time-attack-multiplayer-scale-analysis.md)
and [the component performance audit](../audits/performance/2026-10-21-multiplayer-component-performance.md).

## Problem

The relay currently selects at most eight nearby racers and carries their 60 Hz pose
history in 20 Hz aggregate messages. The fixed count bounds payload, but gives every
selected racer the same sample density and does not express an actual per-connection
byte ceiling. Doubling the visible count would approximately double detailed ghost
traffic even when some racers are distant, predictable, off-screen, stationary or
already represented adequately by coarse progress.

The design must allow a richer visible field while bounding server work, client work,
queue memory and network output. It must recover cleanly from interest entry, slot
reuse, reconnect and missing compression state. Reliable lifecycle events must not be
starved by cosmetic movement.

## Goals

- Bound detailed ghost output per recipient in bytes and useful samples.
- Represent 12–16 racers at a quality comparable to the current nearest eight while
  remaining near or below the current network envelope.
- Prefer on-screen, approaching, overtaking and changing racers without starving
  lower-priority racers.
- Preserve smooth 60 Hz rendering through interpolation at lower network sample rates.
- Reduce unchanged, redundant and already-obsolete movement.
- Keep CPU and memory bounded with arrays and small per-interest records.
- Preserve canonical pose meaning, stream hashes, lifecycle ordering and strict decode
  budgets.
- Expose enough telemetry to explain every quality reduction and capacity limit.

## Non-goals

- Running complete remote Sonic simulations from controller input.
- Making cosmetic ghost state authoritative for gameplay or verification.
- Replacing WebSocket/TCP in the first implementation.
- Horizontally distributing one room across multiple servers.
- Raising the 256-player room limit.
- Guaranteeing a particular visibility count when a connection cannot sustain its
  configured byte budget.

## Architecture

### Two traffic classes

The connection maintains two bounded logical paths:

1. **Reliable control:** identity/membership, round transitions, attempt lifecycle,
   sanctions, private rank, final standings state and disconnect reasons. Control has
   reserved capacity and is never suppressed by ghost traffic.
2. **Replaceable state:** cosmetic ghost poses and coarse progress. A newer unsent state
   may replace an older state for the same racer. State is eventually consistent and
   always recoverable from an absolute sample.

Both may initially share the existing WebSocket. Separation is semantic and occurs
before encoding/write, where obsolete movement can still be discarded.

### Per-recipient budget

Each recipient has a token bucket expressed in encoded bytes per second with a bounded
burst. Every 50 ms hub tick reserves control headroom, then spends the remaining state
budget on the highest-priority useful updates.

The server owns minimum and maximum values. A client capability hint may request less
work but cannot raise server limits. The current measurement is approximately 5 KiB/s
of total steady relay traffic per recipient at eight nearby racers; it includes coarse
roster, control and probe traffic. Add separated counters first, reserve measured
control/coarse overhead, and derive the initial detailed-state allowance from what
remains rather than treating 5 KiB/s as a ghost-only budget.

The scheduler records requested, admitted and deferred bytes. It must stop candidate
serialization when the budget is exhausted; a byte cap that still serializes every
candidate only bounds egress, not CPU.

### Interest records

For every currently relevant sender, the recipient keeps a compact record:

```text
sender slot and identity generation
interest state and entry tick
last selected tick and last absolute tick
last exact pose values needed by the encoder
last delivered coarse state
current priority and intended sample interval
pending latest-state marker
```

The room limit bounds these records. Implement with arrays keyed by slot plus identity
generation checks; avoid maps in the 20 Hz selection loop unless profiling proves an
array unsuitable.

### Candidate discovery and stable membership

Retain the existing relevance classifier and 480/800 px entry/exit hysteresis for the
first implementation. Candidate discovery produces a bounded list; the scheduler
decides quality and frequency.

An incumbent keeps its detailed slot until a newcomer is materially more useful. A
newcomer receives an entry boost and an absolute pose. Teleport, respawn, camera jump,
attempt change and spectated-player change bypass staggered evaluation.

The existing classifier uses 512 px X-axis buckets. A later CPU optimization may
refine or replace those buckets with route/Y partitioning or cached evaluations. It
must not replace the dense-crowd budget because all racers can share one bucket at the
start.

### Priority and aging

Priority is an ordered policy, not a client-controlled number. Inputs include:

- visible in the camera or predicted to enter soon;
- distance and relative route progress;
- approaching, overtaking or being overtaken;
- abrupt pose/animation/facing change;
- prediction residual magnitude;
- time since the last selected sample;
- recent interest entry or re-entry;
- finished, stationary or off-screen state.

Age increases while a candidate is deferred and has a maximum-silence deadline. The
deadline guarantees eventual refresh only for admitted interests when the configured
budget meets a measured minimum viable rate. If all deadlines cannot fit, preserve
control, demote state to coarse-only, expose the overload metric, and disconnect a
persistently overloaded recipient under the bounded backpressure policy. Never exceed
the byte ceiling silently to keep the promise. Entry, teleport and discontinuity outrank
routine updates. Finished or unchanged racers move to dormancy after their reliable
state transition.

No formula constants become contract until the crowd/overtake/teleport matrix measures
their behavior. Deterministic tie-breaking uses slot and generation, never iteration
order from an unordered collection.

### Quality tiers

The first experiment uses an equivalent-sample envelope:

| Tier | Example membership | Target source samples | Presentation |
|---|---:|---:|---|
| Immediate | closest/on-screen 2 | 60/s | full pose and animation |
| Nearby | next 6 | 20/s | interpolated full pose |
| Contextual | next 8 | 5/s | interpolated pose or coarse-to-detailed bridge |
| Coarse | remaining room | approximately 1/s or dirty-only | progress, direction, status and rank |
| Dormant | stationary/finished unchanged | heartbeat only | retained final state |

This represents 16 detailed/contextual racers with 280 source samples/s, compared with
480 for the current eight-at-60 model. These are experimental defaults. The actual
encoder budget, not tier membership, is authoritative when payload sizes vary.

The canonical producer, validation and stream hash remain at the complete 60 Hz frame
sequence. Thinning occurs only in the recipient-specific presentation scheduler. Each
wire sample carries its canonical source frame index (or an exact delta from it), and a
tier transition begins with an absolute anchor.

The current `RemoteGhostPlayback` cannot consume this sparse stream unchanged: it
looks up every integer frame, treats a missing index as dry/stalled, and derives velocity
from index minus one. Replace that assumption with a sparse source timeline that
interpolates between bracketing received samples using their source-frame distance.
An intentional 60-to-20-to-5 Hz gap is not packet loss. The dry path begins only when
no future bracketing sample arrives by its delivery deadline, then bounded extrapolation
continues from the newest exact sample. Tier transitions reset presentation anchors,
not the canonical attempt or upstream hash.

### Lossless predictive encoding

Extend packet-local change masks with an optional cross-packet exact predictor. For
integer position fields, predict from prior exact samples and encode the residual.
Animation/facing/layer fields remain change-coded. The decoder reconstructs the exact
canonical pose before hashing or playback.

Every delta identifies its stream generation and baseline. Send an absolute pose when:

- a sender enters this recipient's interest;
- the attempt, connection, identity generation or slot ownership changes;
- a teleport/discontinuity is detected;
- the required baseline is unavailable;
- the periodic keyframe interval expires;
- raw encoding is smaller than the delta.

A malformed or missing baseline rejects the state packet without applying partial
updates. It must not close an unrelated host/master link. Decode work, baseline history
and residual magnitude remain bounded.

Before implementation, add residual histograms to the existing scale tool. The current
canonical pose is already seven bytes and packet-local compression is effective; do not
accept cross-packet bookkeeping without measured wire savings.

### Latest-state replacement and backpressure

The hub keeps at most one unsent replaceable update per sender/recipient. A newer pose
replaces it. Coalescing happens before aggregate encoding and socket write.

Backpressure stages remain bounded:

1. normal budgeted state;
2. lower contextual/near sample rates;
3. coarse-only state;
4. disconnect after the existing byte/time ceiling.

Reliable control remains ordered and bounded. A required delta may not depend on a pose
that was replaced before transmission; rebuild against the last delivered/declared
baseline or send absolute state.

### Coarse roster and metadata

Static identity and character metadata are established by membership messages and
referenced by slot/generation. Coarse progress should become dirty/delta based rather
than resending an unchanged full-room representation on a timer. A periodic complete
coarse snapshot provides recovery and diagnostics.

Names and fingerprints never enter the hot pose payload. Dictionary ownership remains
server-derived and bounded.

### Finish and standings coalescing

Record and validate every finish individually. Within one room tick, retain the newest
public top-ten/round state and broadcast it once. Send private final-rank/verification
messages individually. A joining or reconnecting client receives a complete current
standings page/state.

Coalescing must preserve finish order, best-time semantics, verification changes,
expiry and round-end transitions. It changes presentation frequency, not recorded
results.

### Directory conditional responses

Extend the existing WebSocket `RoomListRequest` with an optional known generation and
return either `RoomListResult` or an explicit unchanged result. A cache key includes the
generation plus every filter, sort, page and visibility-authority input. Bound entries,
bytes and lifetime. A bounded change journal may later provide upsert/remove deltas,
with a full page when the requested generation has expired. HTTP `ETag`/`304` semantics
apply only if a separate HTTP directory endpoint is deliberately added later.

## Safety invariants

- Control capacity is reserved before cosmetic state.
- No client hint can increase server work or byte ceilings.
- Slot reuse and identity relocation clear every baseline and playback owner.
- First interest, reconnect and discontinuity always have an absolute recovery path.
- Delta decoding is atomic, bounded and canonical before publication.
- No partial packet mutates receiver state after validation failure.
- Lower quality changes presentation only; standings, attempts and verification remain
  exact.
- The host broker connection and unrelated rooms survive one guest's malformed or
  oversized traffic.
- Queue memory is bounded independently of socket/TCP buffers.
- Metrics never use user-controlled unbounded labels.

## Telemetry

Report per room and per recipient:

- state/control bytes requested, selected, encoded and written;
- candidates examined, selected and deferred;
- samples by quality tier;
- update age and maximum silence;
- absolute, packet-local delta, predictive delta and raw-fallback counts;
- prediction residual histogram and encoded bytes saved;
- interest entries, exits, retained incumbents and churn;
- pending replaceable states, replacements and socket backlog;
- backpressure stage duration and disconnects;
- client interpolation delay, extrapolations, corrections and queue high-water mark;
- finish events received versus public standings broadcasts;
- directory full/unchanged responses, cached pages and encode time.

## Validation

### Deterministic tests

- Priority/tie-breaking, aging, maximum-silence bounds and deadline overload behavior.
- Exact token-bucket accounting and control reservation.
- Entry/exit hysteresis, overtakes, teleport, respawn, camera jump and spectator focus.
- Baseline reset for attempt, reconnect, identity generation and slot reuse.
- Absolute recovery after missing/invalid baselines.
- Sparse 60-to-20-to-5 Hz playback, source-frame interpolation, intentional gaps versus
  loss, tier transitions and recovery after a late/missing bracket.
- Raw fallback, residual bounds and exact canonical hash equivalence.
- Latest-state replacement without control reordering.
- Dormancy wakeup and final-state retention.
- Standings coalescing with exact finish/rank/verification outcomes.
- Directory cache-key isolation, bounds and expired-journal full fallback.

### Real-socket matrix

Run 1, 2 and 4 rooms at 256 players with clustered starts, spread running, repeated
overtakes, stationary/finished racers, teleport/discontinuity, reconnect and one slow
reader. Compare fixed-eight and budgeted policies from the same source fingerprint.

Test quality presets representing 8, 12, 16 and 24 contextual racers. Record actual TCP
bytes, server CPU/RSS/heap/GC, tick/queue latency, candidate visits, maximum update age,
correction error, client decode time and clean lifecycle outcomes.

### Rendered client matrix

Use the same middle-runner route with multiplayer disabled and with each quality preset.
Capture CPU/GPU frame percentiles, active sprites, distinct identities, interpolation
delay, corrections and viewport entry/return counts. Include a weaker supported client
before raising defaults.

### Failure matrix

Exercise delayed/lost/reordered state at the application model, slow TCP consumers,
missing baselines, reconnect during interest churn, malformed deltas and finish storms.
Control progress and unrelated-room survival are mandatory witnesses.

## Rollout and stopping rules

1. Add telemetry and residual collection without changing behavior.
2. Introduce the byte scheduler behind a test configuration and preserve fixed-eight as
   the comparison arm.
3. Land the sparse source-timeline playback contract, then mixed rates, aging and
   hysteresis once lifecycle correctness is green.
4. Accept predictive deltas only if matched wire bytes improve without material CPU,
   memory or recovery regression.
5. Land finish coalescing and conditional directory responses as independent changes.
6. Change the production default only after rendered and four-room evidence agree.

Stop or revert an optimization if it increases missed lifecycle/control messages,
maximum-silence violations, visible corrections, queue retention, malformed-input blast
radius or server tick p99 beyond the accepted envelope. Bandwidth reduction alone is not
success.

## Alternatives rejected

- **Input-only remote simulation:** saves state bytes but requires remote physics/world
  reproduction, increases client CPU and creates version/divergence recovery work.
- **Always show more racers at 60 Hz:** simple but makes bandwidth grow linearly with the
  visual cap.
- **Generic stream compression:** uncertain gain on small specialized packets with added
  CPU, memory and security complexity.
- **Spatial grid as the primary limit:** improves spread-out CPU, not dense-start output.
- **Immediate transport replacement:** datagrams improve freshness under loss but do not
  replace budgeting, prioritization or recovery design.
