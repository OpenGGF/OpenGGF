# Visual trace title-card and frame-zero dynamic-art parity

Date: 2026-08-03

## Problem

The master-title visual trace path still differs from the headless replay path
at the boundary between level presentation and compared production:

1. it suppresses the level title card, so a user enters the trace directly;
2. it opens an externally managed dynamic-art comparison segment before the
   first replay production claim;
3. S1 level loading has already prepared the player's initial DPLC at that
   point, so the first production V-blank publishes its real `submitted` and
   `completed` edges into comparison row zero; and
4. headless replay services that bootstrap preparation while no comparison
   segment is open, then opens and publishes an empty row zero.

For S1 GHZ1 this produces the observed visual-only mismatch
`dynamic_art.edges expected=[] actual=[0, 1]` even though every physics,
animation, camera, and PLC queue field matches and the headless trace remains
green.

## Constraints

- The title card is presentation before the trace. It must not consume BK2
  rows or comparison rows.
- Title-card PLC/DPLC work must not leak into the replay's production ledger,
  hardware-timing admission, or dynamic-art comparison generation.
- The replay after the card must retain the same deterministic bootstrap used
  by headless tests. Visual presentation cannot replace or mutate that
  contract.
- Dynamic-art diagnostics remain exact and comparison-only. No edge may be
  discarded, filtered by expected trace data, or moved by a game/zone/frame
  exception.
- Complete-run segment zero uses the same launch boundary. Later run segments
  retain their existing production-driven transition and external segment
  ownership.
- Standalone special-stage traces do not acquire a level title card.

## Options considered

### Run the title card inside the compared replay context

This is superficially simple, but it is not safe. The title card can service
PLC/DPLC and hardware queues, advance playable/object preludes, and mutate the
title-card provider before trace row zero. Hardware-timed traces also create
their gameplay context with recorded admission, while the recorded schedule
does not describe this extra visual prelude. Allowing that work to share the
replay context either contaminates row zero or requires trace-specific cleanup.

### Draw a synthetic title-card overlay over an already-running replay

This avoids runtime contamination but is not the production title card. It
would either obscure live compared frames or need a parallel animation clock,
and would duplicate game-specific title-card behavior outside its owner.

### Use a disposable presentation context, then reopen a clean replay context

This is the selected design. The first gameplay context loads the requested
zone and runs its normal title card with no playback, comparator, hardware
timing schedule, or externally managed dynamic-art segment. When the card
releases back to level mode, that context is destroyed. A new gameplay context
is opened with the trace's required hardware-admission policy and the existing
`TraceReplayDriver` performs the unchanged headless-equivalent reset, level
load, omitted-title-card tail, bootstrap, counter alignment, playback start,
and comparator attachment.

This makes presentation disposable and keeps the compared run reproducible.

## Design

### 1. Two-phase visual level launch

`TraceSessionLauncher` gains an explicit launch phase for level-backed visual
sessions:

- `TITLE_CARD_PRESENTATION`: the requested zone/act is loaded in the initial
  live context, the pending automatic title-card request is consumed, and
  `GameLoop.enterTitleCard` starts the normal card immediately. The session is
  made active so Escape and teardown remain owned, but the fixture, playback,
  comparator, rewind controller, run coordinator, hardware schedule, and
  external dynamic-art controller remain absent.
- `REPLAY_BOOTSTRAP`: after the all-mode, after-step hook observes that the
  card has returned to `LEVEL` and its overlay reports complete, it reopens a
  clean gameplay context using the stored trace admission policy and runs the
  existing replay launch body.
- `ACTIVE`: the comparator/run coordinator is installed and normal visual
  trace behavior resumes.

The after-step hook is the handoff point because title-card release may fall
through into one gameplay tick in the same host step, and the slide-out tail
continues as an in-level overlay. Those presentation ticks remain in the
disposable context and are destroyed only after the provider reports the full
overlay complete; no active PLC lifecycle frame is destroyed mid-iteration.

Both standalone level traces and complete runs use this phase. Special-stage
launch remains direct.

The master-title game bootstrap always opens the disposable presentation
context with live hardware admission. The session stores whether the actual
trace needs live or recorded admission and applies that policy only when it
opens the clean replay context. This prevents an unrepresented title-card job
from waiting on or consuming the trace's hardware-timing schedule.

### 2. Clean context handoff

`TraceSessionLauncher` asks the `Engine` composition root to reopen the visual
replay context through a narrow static action owned by `Engine`; the launcher
never acquires the process singleton. `Engine` validates its active instance
internally and delegates lifecycle replacement to
`VisualTraceReplayContextHandoff`, passing callbacks that reset module-scoped
providers and bind the new mode through its normal `bindGameplayMode` seam.
This ownership matters because `GameLoop` caches the managers used by
simulation, while `Engine.draw()` separately caches the `LevelManager`,
`SpriteManager`, and `Camera` used by rendering. The operation:

1. resets the completed presentation title-card provider;
2. resets every retained module-owned rewind adapter to its missing-snapshot
   baseline (notably the S1/S2 `PlcLifecycleService` queue);
3. reopens the current world session with the requested
   `HardwareReadinessAdmissionPolicy`;
4. attaches production gameplay managers through `GameplaySessionFactory`;
5. atomically rebinds both `Engine` rendering references and `GameLoop`
   simulation/graphics-managed references to the new context without mutating
   the legacy `GameModuleRegistry`; and
6. clears the loop's cached module-scoped title-card provider so the replay's
   omitted-presentation tail starts from a reset provider.

Destroying the presentation context resets its dynamic-art lifecycle,
context-owned runtime-art queues, hardware timing state, PLC lifecycle
coordinator, rewind registry, and other manager-owned state. The explicit
adapter reset is required because reopening retains the `WorldSession` and
`GameModule`, and the S1/S2 Nemesis PLC services are mutable module fields
rather than gameplay-context fields. The selected game module and loaded ROM
remain in the world session. `TraceReplayDriver.start` then registers the
recorded team and loads the trace level exactly as it does today.

Launch failures in either phase use the existing held launch error and abort
path. Escape during `TITLE_CARD_PRESENTATION` aborts immediately even though no
comparator exists yet.

### 3. First comparison window opens after bootstrap service

The clean replay context still prepares S1's initial player DPLC during level
load. The visual launcher therefore requests a deferred transfer of comparison
segment ownership from `PlcFrameLifecycleCoordinator` for segment zero.

At request time the coordinator:

- rejects an existing external owner;
- requires any automatic open window to have published;
- closes that completed automatic window;
- marks comparison segments externally managed; and
- records that the external segment must open after the next production
  service.

On the next `PlcLifecycleFrame.claim`, the normal production service runs
first. Any initial S1 submit/complete pair is therefore recorded as genuine
run-gap diagnostics, matching the headless order. The coordinator then opens
external segment zero before the iteration body and
`finishProductionIteration`, so row zero is still published atomically in the
new generation. A lag claim that does not service DMA behaves identically to
automatic headless ownership: it still opens row zero after the claim's
service decision.

The segment controller's first `open` uses this deferred acquisition. Later
run-segment opens remain immediate because their source close, transition gap,
and destination open are already production-defined. Closing or aborting
before the first claim cancels the pending open and restores automatic
ownership without inventing a row. Production ledger entries, pending work,
mapping decisions, and monotonic identities are never cleared by the handoff.

The launcher's pre-iteration snapshot precedes that deferred open, so row zero
may atomically arrive in the immediately following segment generation. The
launcher arms a one-shot structural authorization only for this first deferred
window. `LiveTraceComparator` accepts the rollover only when that authorization
is present, the expected row is zero, the before snapshot is unpublished and
identifies no row, the generation is exactly adjacent, the delivery serial is
newer, and the after snapshot is published row zero. The authorization is
consumed by the first publication attempt, including a stable-generation
publication. Stable-generation rows otherwise retain the existing rule, and
generation skips, published-before snapshots, or nonzero-row handoffs remain
launch failures. The comparator still compares the complete published edge and
outstanding-transfer payload exactly; the exception carries no gameplay value
and does not discard or reclassify work.

### 4. Comparison and UI behavior

The title card renders through the existing `TITLE_CARD` engine draw path.
Playback does not start until the disposable context has been replaced, so the
first BK2 row remains aligned with comparison row zero. The HUD and ghost are
installed only in `ACTIVE`; the ordinary title card is the only presentation
during the prelude.

After replay bootstrap, `LiveTraceComparator` retains its exact dynamic-art
assertions. For GHZ1 the bootstrap submit/complete edges still exist in
production diagnostics as pre-segment gap transitions, while the compared row
zero remains empty as recorded.

## Testing

- A PLC lifecycle test prepares an S1 transfer, requests deferred external
  ownership, drives one logical iteration, and proves the submit/complete
  transitions occur outside the segment while a fresh generation publishes an
  empty row zero.
- A launcher lifecycle test proves the initial controller open is deferred,
  publishes row zero atomically, and later segment opens remain immediate.
- Comparator tests prove the one-shot authorized adjacent row-zero generation
  is accepted while a generation skip, stale delivery, published-before
  snapshot, nonzero row, or reuse after a stable first publication is rejected.
- Cleanup tests prove abort before the first production claim cancels the
  pending open and restores automatic ownership.
- Title-card phase tests prove replay bootstrap waits for both the transition
  back to `LEVEL` and completion of the visible exit overlay, runs once, and
  Escape is owned before a comparator exists.
- A gameplay-context handoff test proves presentation lifecycle state does not
  survive the reopen, including non-empty active/queued S1/S2 PLC state, and
  the requested hardware-admission policy owns the new context. It also proves
  that `Engine` and `GameLoop` cache the replacement context's level, sprite,
  and camera managers, preventing gameplay from advancing behind a black frame
  through destroyed presentation managers.
- The S1 GHZ1 headless trace remains green, focused visual-launch lifecycle
  tests pass, and cross-game launch/run/dynamic-art suites show no regression.

## Follow-up boundary

This change shows the initial level title card before standalone and complete
run playback. It does not add replay comparison during title cards, rewind
across the presentation/replay context boundary, or new title cards for direct
special-stage traces. Later in-run zone transitions continue to show or omit
their production title cards according to the run's real transition path.
