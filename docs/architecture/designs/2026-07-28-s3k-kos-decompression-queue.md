# S3K direct Kosinski decompression queue

Date: 2026-07-28

## Status

Proposed for implementation. This design promotes the S3K direct Kosinski
decompression queue from the hardware-timing inventory's pending-review state
to a separately versioned authoritative readiness owner.

## Requirements

### Goals

- Model the ROM's shared four-entry `Queue_Kos` FIFO as runtime-owned,
  rewind-safe production state.
- Make direct completion visible at the ROM's pre-main-loop boundary, before
  the same frame's object and screen-event scans.
- Route every KosM module through that shared direct FIFO so ordinary Kosinski
  work and KosM work contend exactly as they do in the ROM.
- Replace AIZ intro and ICZ1-to-ICZ2 synthetic readiness with the ROM-owned
  `Kos_decomp_queue_count == 0` predicate.
- Record and replay direct completion without allowing trace data to create
  work, provide payload bytes, or call gameplay consumers.
- Preserve schema-1 parsing and module-only authority while introducing a
  schema that can authorize both queue kinds. Schema-1 AIZ/ICZ fixtures that
  cross a direct-count consumer must be regenerated before remaining replay
  gates.

### Non-goals

- Cycle-accurate 68000 execution or VBlank interruption.
- Recording decoder progress or copying decompressed bytes from a trace.
- Modeling synchronous Kosinski calls that do not enter `Queue_Kos`.
- Porting every unimplemented S3K act transition as part of this change.
- Using a fixed frame delay, zone exception, route identity, or trace frame as
  production behavior.

### Constraints

- Runtime bytes come from the user-supplied ROM.
- The direct queue is a single session-owned physical FIFO. Facades must not
  create independent queues.
- A module-created direct stream is a real direct submission and receives a
  direct ordinal and fingerprint in schema 2. Suppressing these children would
  omit the stream whose completion can make the queue-empty predicate true.
- Existing module archive ordinals and final `POST_OBJECTS` readiness remain a
  separate lifecycle from child direct-stream completion.
- Rewind captures both FIFOs, active decoder state, parent-child associations,
  ordinals, prepared payloads, readiness, and recorded-edge cursors.

## Exploration synthesis

### ROM evidence

`Queue_Kos` appends an eight-byte source/destination entry and increments
`Kos_decomp_queue_count`. `Process_Kos_Queue` owns the FIFO head, sets bit 15
while decoding, resumes through the VInt bookmark, clears the busy bit, retires
one stream, and shifts remaining entries
(`docs/skdisasm/sonic3k.asm:2803-2967`).

`Process_Kos_Module_Queue` does not have a private decoder. It appends the
current module to the same direct FIFO, sets the module busy bit, waits for the
entire direct FIFO to become empty, enqueues the module DMA, then advances the
module/archive state (`docs/skdisasm/sonic3k.asm:2668-2791`).

The ordinary level loop services the direct queue before `Wait_VSync`, runs
objects and screen events, then services the module queue
(`docs/skdisasm/sonic3k.asm:7884-7922`). Therefore:

- direct completion is observable at `PRE_MAIN_LOOP`;
- AIZ and ICZ screen events can consume queue-empty readiness in that same
  admitted scan; and
- final module retirement remains `POST_OBJECTS`.

The two gameplay consumers of the direct count are AIZ intro progression
(`docs/skdisasm/sonic3k.asm:104575-104590`) and ICZ act-transition progression
(`docs/skdisasm/sonic3k.asm:110259-110287`). Numerous act transitions enqueue
ordinary chunks/blocks beside KosM art, so the shared FIFO also affects module
completion indirectly.

### Current engine and recorder

`S3kKosModuleQueue` currently embeds a `ResumableKosinskiDecoder` and explicitly
does not submit child direct work. `HardwareTimingService` services the first
globally unprepared job and admits prepared jobs in global submission order.
Adding child jobs without changing that rule can deadlock a module parent
behind its own child or impose ordering between unrelated physical queues.

The native recorder's `HardwareTimingEventEngine` and the Lua corroboration
module currently mirror only the module FIFO. Both already have the ROM
scanner and stable fingerprint machinery needed for a second ledger, but the
direct ledger must use the low 15 count bits, retain FIFO identity across the
busy bit, and detect head shifts without requiring a sampled zero.

### Considered approaches

1. **Recommended: compositional direct child jobs with schema-2 authority.**
   The direct FIFO owns standard Kosinski decoding; module parents submit
   children to it and advance only after the shared FIFO empties. Both child
   and parent completions are recorded at their real boundaries. This matches
   ROM ownership and preserves strict identity.
2. **Record only ordinary direct jobs.** This reduces fixture events, but a
   module child may be the last physical stream whose completion releases the
   AIZ/ICZ direct-count poll. Omitting it makes queue-empty timing host-derived
   and is rejected.
3. **Keep independent module/direct decoders.** This is additive but cannot
   reproduce FIFO capacity, contention, or the ROM's queue-wide wait. It is
   rejected.

## Architecture decision

### Queue ownership

Add a session-owned `S3kKosDecompressionQueue` accessed through the S3K object
art/resource owner. It represents the one physical direct FIFO and exposes:

- `queueStandardKos(...)` for ordinary ROM `Queue_Kos` submissions;
- an internal module-child submission API carrying the parent archive handle
  and module index;
- `decompressionsPending()` matching the low-15-bit ROM predicate;
- handle-specific readiness and claim operations; and
- complete rewind capture/restore.

An `S3kKosModuleQueue` facade retains archive-level submission and readiness,
but its preparation becomes a coordinator-owned, boundary-driven aggregate
job. A module parent performs no decoder work through the generic scheduler:

1. initialize the active archive/module;
2. when the module is not busy and direct capacity permits, submit its exact
   standard-Kosinski child to the shared direct FIFO;
3. at `PRE_MAIN_LOOP`, the direct owner advances only the FIFO head;
4. at `POST_OBJECTS`, if the active child is ready and the entire direct FIFO
   is empty, claim its bytes, append them to the archive output, perform the
   ROM-equivalent DMA/publication state change, and advance;
5. after the final child, prepare the module parent payload and retire the
   module archive through its existing readiness owner.

Each `POST_OBJECTS` invocation performs at most one ROM-equivalent module
transition. Queueing a child returns immediately. Retiring child N also
returns immediately, so child N+1 cannot be submitted until the following
`POST_OBJECTS`.

The direct owner distinguishes physical FIFO occupancy from payload ownership.
A `PRE_MAIN_LOOP` retirement removes the job from physical occupancy and makes
its payload ready and claimable. A ready-but-unclaimed child neither keeps
`decompressionsPending()` true nor consumes one of the four slots. A full FIFO
causes module submission to defer and retry on a later module-service call,
matching the ROM; it is not a fatal fifth-child submission.

### Timing scheduler

Hardware preparation and live admission become per-kind rather than globally
head-blocking. FIFO order is preserved within each `HardwareWorkKind`.
Dependencies between kinds are expressed only by the S3K queue coordinator;
the generic timing ledger cannot advance module-parent preparation.

The service continues to expose only typed submission, preparation, and final
readiness. Recorded authority cannot submit a child or cause module
advancement.

### Authority schemas

- No timing stream configures both kinds as `LIVE`.
- Hardware timing schema 1 configures `KOS_MODULE_QUEUE` as `RECORDED` and
  `KOS_DECOMPRESSION_QUEUE` as `LIVE`. Direct jobs run through the production
  scheduler and do not require recorded edges.
- Hardware timing schema 2 authorizes `KOS_MODULE_QUEUE` and
  `KOS_DECOMPRESSION_QUEUE` as `RECORDED`. A schema-2 direct job prepared early
  remains pending until its matching edge.
- The metadata value selects the allowed kind registry. Unknown kinds or kinds
  not authorized by that schema fail fixture loading.
- Trace schema remains 7 because the container and event shape do not change;
  recorder versions are bumped to identify the expanded semantics.

This is a compatibility migration, not a permissive fallback. A schema-2
fixture missing a production direct edge fails at the normal segment/run
completion checks. Schema 1 remains loadable, but it cannot be claimed as a
sync gate for AIZ intro or ICZ act-transition gameplay until that fixture is
regenerated under schema 2. The committed fixture inventory must identify
every schema-1 route that reaches either consumer.

`HardwareTimingService` snapshots the per-kind admission map. Its recorded
capability rejects an edge for any kind that is not `RECORDED` under the
installed schema.

### Direct identity

Each physical `Queue_Kos` submission receives a per-kind monotonic ordinal.
The fingerprint uses the existing canonical tuple:

- kind `KOS_DECOMPRESSION_QUEUE`;
- canonical ROM source address;
- scanned compressed length;
- canonical destination address;
- decoded destination length;
- variant `kosinski`;
- module count `1`.

For module children, the canonical source is the exact aligned child stream
address derived independently by the engine and recorder. Canonical
destination is always the exact 32-bit longword stored by the ROM's
`Queue_Kos`. Word-addressed RAM destinations are therefore sign-extended
(`0xFFFFxxxx`), not masked to `0x00FFxxxx`; this includes ordinary
`RAM_start`/`Block_table` destinations and the shared `Kos_decomp_buffer`.
Those exact bits are serialized big-endian on both sides; Java uses the
corresponding signed `int` bits and C# uses `unchecked((int)u32)`. Host array
offsets, 24-bit bus-normalized addresses, and publication targets are separate
metadata and never enter this field. Parent ordinal and module index are
production dependency metadata but are not trace-supplied and do not replace
the canonical fingerprint fields.

Golden engine-Java/native-C# vectors cover one AIZ ordinary destination, one
ICZ ordinary destination, and one module-buffer destination.

The standard-Kosinski scanner starts at the stream descriptor: there is no
KosM total-size header and no inter-module alignment. Compressed length ends
after the zero terminator. Decoded length counts literal and match output,
ignores the one-byte no-output marker, and validates descriptor refills,
backreferences, terminators, and ROM bounds.

### Recorder lifecycle

The native recorder adds a run-wide direct FIFO mirror at `$FF40-$FF5F` and
reads `Kos_decomp_queue_count` at `$FF0E`.

- Bit 15 is busy state; bits 0-14 are the physical count.
- The mirror retains the sampled busy state as lifecycle evidence; it is not
  discarded after extracting the physical count. While the prior head remains
  busy, slot zero must retain its canonical identity and cannot be inferred as
  retired. A busy-to-not-busy transition proves retirement of that canonical
  head, including the otherwise byte-ambiguous case where an identical job is
  appended into slot zero before the next sample.
- Newly observed slots allocate ordinals in FIFO order, including identical
  adjacent submissions.
- A retirement is detected from the prior mirrored head plus count/shift
  reconciliation. A sampled zero is not required.
- Retirement and append reconciliation supports every observable count
  transition permitted between PRE samples, including `1 -> 1`, `1 -> 2`,
  `2 -> 3`, and unchanged-count transitions from longer queues. It first
  derives the longest valid suffix/prefix overlap between the prior canonical
  ledger and the sampled physical slots, retires only the proven leading
  entries, then allocates every proven appended slot in FIFO order.
- Canonical identity is captured when a slot first appears and retained in the
  mirrored ledger. Interrupted progress lives in saved registers. A final
  retirement writes advanced pointers into slot zero, so stale zero-count RAM
  is never used to invent or recanonicalize a job.
- Every retirement emits a `pre_main_loop` direct event in schema 2.
- The module observer continues to emit final archive retirement separately.
- When direct and module jobs retire in one raw frame, canonical output order
  is the direct `pre_main_loop` edge followed by the module `post_objects`
  edge.
- Within an armed observable interval, count is always
  `Kos_decomp_queue_count & 0x7FFF`; zero-sentinel scanning is forbidden.
  Every occupied physical slot, including slot zero, is identity-checked
  against the canonical ledger. Interrupted decoder progress is represented
  only by `$FF10-$FF37`, so a changed slot zero while the prior head remains
  busy is fatal. After a busy-state transition proves or excludes head
  retirement, a shifted head must match a later prior ledger entry and
  appended slots begin only after that proven overlap. Identical descriptors
  use the busy transition plus count and maximum-overlap constraints so an
  admitted identical replacement receives a new ordinal while a stable
  identical snapshot emits nothing.
- A completion requires a previously mirrored submission. Phase-local work
  that appears and retires wholly inside a declared unrepresented gap emits no
  fabricated edge; the next represented interval reboots from observable
  pending slots. Unexplained loss or mutation inside an armed interval fails.
- Unrepresented gaps, special-stage detours, and segment handoffs preserve
  both ledgers. An accepted standard-recorder discard/reset atomically
  discards both ledgers and streams and initializes both ordinal bases to
  zero.

STANDARD differential validation accepts production `6.38-s3k`,
trace-schema 7, hardware-schema 2 metadata and validates both direct and
module ledgers. Its compatibility path continues to load committed
`6.37-s3k`, trace-schema 7, hardware-schema 1 fixtures, but does not grant
those fixtures direct-count boundary authority.

The deferred-resource load overload must not worsen the existing
`LevelManager` size ratchet. Move its self-contained pattern-location search
algorithm to a package-local level utility and retain the public
`LevelManager.findPatternOffset(...)` API as a thin delegate. This is an
ownership-preserving extraction only: map selection, coordinate conversion,
search order, return shape, and callers remain unchanged.

The native headless recorder is the sole maintained fixture authority. The
frozen Lua recorder is not extended merely for parity; independent Java/C#
golden vectors and ROM/disassembly lifecycle tests provide cross-implementation
corroboration. A throwaway Lua diagnostic may be used if a disputed capture
needs it, but it is not a deliverable.

### AIZ consumer

At the ROM trigger, queue the main-level 16x16 standard-Kosinski stream and
the main-level KosM archive through the shared owner. Split the current
combined publication:

- the direct 16x16 block overlay is published when
  `decompressionsPending() == false`, and `Events_fg_5`/deformation/draw
  progression advances in that same screen-event scan;
- the KosM 8x8 pattern payload is claimed and published only after module
  retirement at `POST_OBJECTS`, so it becomes consumer-visible no earlier
  than the following scan.

Predecoded bytes may be retained only as a payload optimization; neither
publication fence is bypassed.

The immutable S3K level-resource profile for AIZ intro composes two
ROM-derived LevelLoadBlock identities: entry 0 remains the immediate initial
resource plan, while entry 26 contributes only the exact main-level 16x16
block and KosM pattern descriptors as deferred declarations. Entry 0's
secondary intro resources are not omitted. Every `Sonic3k.loadLevel` call
creates a fresh local
exact-consumption tracker from that profile while constructing the ordinary
initial `LevelResourcePlan`, omits each descriptor exactly once, and verifies
the profile was fully consumed. Selection is owned by the
ROM-derived S3K LevelLoadBlock/profile identity; shared loading code contains
no AIZ/zone-name branch. The non-intro AIZ1 profile and later ordinary loads
do not inherit the deferral. Re-loading the intro profile creates a new local
tracker and reproduces the same deferral; no consumption state is retained in
the immutable profile.

This makes the initial target buffers genuinely pre-publication data. AIZ
tests compare the affected block and pattern bytes before the trigger, after
direct PRE publication, and after module POST publication; phase flags or
handle claims alone are insufficient evidence. Profile tests cover repeated
intro loads, prove entry 0 intro assets remain visible while entry 26
main-level bytes remain absent, and reject missing, duplicate, and
non-matching deferred descriptors.

### ICZ consumer

At camera X `$6900`, queue the ICZ2 secondary chunk and block standard-Kosinski
streams plus the secondary KosM archive. Remove the fixed 41-frame timer.
Request the act transition on the first screen-event scan observing the shared
direct queue empty, preserving the existing seamless-transition mutation and
save behavior.

The secondary module archive is an intentional seamless-transition handoff.
Its parent and ready child payload remain session-owned across
`executeActTransition`; the timing ledger is not reset. ICZ2 receives the
exact transferred handles/facade, the same frame's later `POST_OBJECTS` may
retire the parent, and ICZ2 publishes the payload through its level-resource
owner. The parent is exportable across this structural handoff only with that
exact production owner and matching future edge.

The source event registers an immutable, transition-scoped
`SeamlessTransitionResourceHandoff` in a session-owned,
rewind-snapshottable `SeamlessTransitionHandoffRegistry`. The request carries
only its opaque handoff id. The registered payload contains:

- a generic `LevelResourceLoadPolicy` whose entries are exact resource
  descriptors, not zone/game names; and
- the production-owned queue handles/facades that the handoff transfers to the
  newly initialized target event provider.

Before any level/game-state mutation, `LevelActTransitionExecutor` atomically
claims and removes the id from the registry. Claim returns the immutable
payload and creates a per-execution consumption tracker from its policy. A
failed transition does not put the payload back, so retrying or applying the
same request twice fails before resource loading. Rewind before the claim
restores the registered payload; rewind after the claim preserves its consumed
state. The executor passes the tracker through
`LevelManager.loadLevelData(levelIndex, policy)` to the game loader. The base
`Game` overload accepts only an empty policy; `Sonic3k.loadLevel` consumes
matching descriptors while building its `LevelResourcePlan`. The S3K resource
owner matches the exact already-submitted ICZ2 secondary chunk, block, and
KosM descriptors and omits those load operations. Shared loading code carries
and verifies descriptor identity only; it contains no ICZ or zone-name branch.
A missing, duplicate, or non-matching descriptor fails the transition. The
tracker verifies that every requested descriptor was consumed exactly once
before transition execution continues.

After `initLevelEventsForCurrentZoneAct` constructs the target provider,
`LevelActTransitionExecutor` invokes the claimed handoff to transfer the
exact handles/facades atomically. Immediate execution and queued
`requestSeamlessTransition` execution therefore use one path. When
`RELOAD_SAME_LEVEL` is rebuilt as a target request, the handoff is copied
unchanged. A handoff is single-use for one transition execution and cannot
affect a later ordinary load.

Consequently, target buffers remain at their pre-publication state across the
in-frame reload. The transferred direct chunk/block payloads and the KosM
parent payload are published through the mutation/resource owner only after
their respective production readiness fences. Tests compare target buffer
bytes before and after publication; handle readiness alone is insufficient
evidence.

### Failure handling

- Queue overflow, unexplained recorder FIFO mutation/loss, malformed Kosinski
  input, parent-child mismatch, wrong kind/ordinal/fingerprint/boundary, an
  unprepared recorded completion, or leftover non-exportable work is fatal.
- A module child cannot be claimed while another physical direct entry remains pending,
  matching the ROM's queue-wide wait.
- Rewind restore rebinding must locate exact handles by kind and ordinal;
  missing or mismatched handles fail rather than resubmit work.

## Feature design and acceptance tests

### Queue behavior

- Four direct entries are accepted; a fifth fails.
- Standard Kosinski bytes decode from the ROM and are returned only after the
  direct handle becomes ready.
- Only `PRE_MAIN_LOOP` advances direct decoding or admits direct readiness.
- Busy/interrupted snapshots restore byte-identical decoder progress.
- Head shift, adjacent identical jobs, and retire-plus-append preserve ordinal
  identity.
- Final retirement leaves a ready/claimable payload while physical occupancy
  is zero and slot-zero RAM contains stale advanced pointers.

### Module composition

- A KosM archive submits exactly one direct child per module.
- Ordinary direct work ahead of or behind a child participates in the same
  capacity and queue-empty predicate.
- A child direct completion is observable at `PRE_MAIN_LOOP`; the corresponding
  module advancement/final retirement occurs at `POST_OBJECTS`.
- Recorded authority cannot release the module parent before its child payload
  is prepared and claimed.
- Frame-by-frame multi-module service is pinned as POST queue child N, next PRE
  complete child N, next POST retire child N, following POST queue child N+1.
- A child behind ordinary work and ordinary work appended behind a child both
  delay module POST advancement until the entire direct FIFO empties.
- A full direct FIFO defers a module child without failing.
- A final child direct edge at PRE and parent module edge at POST may share a
  raw frame and retain canonical boundary ordering.

### Replay compatibility

- Schema 1 accepts only module events and services direct jobs live; its module
  parent remains recorded while its direct children can become live-ready.
- Schema 2 accepts both kinds and holds early direct completion for the exact
  recorded edge.
- Schema 1 rejects direct events; schema 2 rejects missing/extra/mismatched
  direct events.
- Ordinal bases, handoffs, rewind cursors, and canonical ordering operate
  independently per kind.
- The hardware-timing authority guard continues to forbid physics, auxiliary,
  reflection, and gameplay mutation paths.

### Gameplay consumers

- AIZ does not publish the main-level terrain while any direct stream remains
  pending. Direct block/event progression occurs on frame N's scan, while a
  final module parent retiring at frame N's POST publishes KosM art no earlier
  than frame N+1.
- ICZ no longer contains or depends on a 41-frame synthetic drain and starts
  its act transition from the direct queue-empty predicate.
- Rewind before, during, and after both transitions reproduces queue state and
  the same future completion once.
- ICZ preserves parent/child ownership across PRE completion, in-frame act
  reload, POST module retirement, ICZ2 publication, and rewind on both sides.
- Rewind after direct retirement but before the module POST claim restores a
  zero-occupancy, ready-unclaimed child exactly.

### Recorder

- Native and engine scanners produce matching fingerprints and boundaries for
  ordinary and module-child direct streams through one checked-in,
  language-neutral golden-vector source consumed independently by Java and
  native C# tests.
- Tests cover busy heads, every retire-plus-append count transition (including
  `1 -> 1`, `1 -> 2`, and `2 -> 3`), slot-zero mutation, shifts without zero,
  unchanged-count retire-and-append, busy-to-not-busy identical replacement,
  stable identical jobs, busy-head mutation, mixed ordinary/module
  submissions, segment handoff, bootstrap, and reset.
- Scanner goldens cover descriptor refill, extended matches, no-output
  commands, invalid backreferences, ROM bounds, and decoded length.
- Phase-loop/gap tests prove that only previously mirrored submissions emit
  completions and resets discard both ledgers atomically.
- Newly regenerated schema-2 fixtures contain canonical event ordering and
  pass committed-fixture validation.

## Migration and rollback

Implementation first lands schema-2 parsing and per-kind authority while
schema 1 remains loadable and module-only. Production direct ownership and
consumers then move onto the shared queue. Recorder schema 2 and selected
fixture regeneration follow. Every committed schema-1 fixture reaching AIZ
intro or the ICZ transition is removed from replay-gate status or regenerated
before delivery; loader compatibility alone is not sync compatibility. A
rollback can stop producing schema 2 and retain schema-1 module-only fixtures,
but must not restore fixed gameplay delays once the shared production queue
owns AIZ/ICZ readiness.

## Risks

- Parent/child deadlock if generic service remains globally ordered.
- One-frame-late gameplay if direct readiness is applied at `POST_OBJECTS`.
- Double modeling if module preparations retain their embedded decoder.
- Fixture breakage if schema 1 is silently reinterpreted.
- Recorder/engine fingerprint drift for aligned module child sources or the
  shared buffer destination.
- Rewind gaps in transient facades or parent-child handles.
- Large fixture regeneration scope; selected AIZ/ICZ traces must be proven
  first before fleet-wide publication.
