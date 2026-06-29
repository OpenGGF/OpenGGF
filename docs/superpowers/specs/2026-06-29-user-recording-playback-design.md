# User Recording And Playback — Design

**Date:** 2026-06-29
**Status:** Approved (brainstorm) — ready for implementation planning
**Branch base:** `develop`

## 1. Purpose And Scope

Add an engine-owned recording and playback workflow for interactive OpenGGF
gameplay. A user can start a recording from the current live gameplay setup,
have the engine restart that setup from a clean slate, record input to a BK2
movie, and later replay the movie in-engine without needing trace data.

This is not a ROM trace replay replacement. Trace Test Mode remains the
authoritative ROM-parity workflow. User recordings are engine-authored movies
with an optional lightweight diagnostic sidecar for detecting playback drift.
That sidecar is comparison-only and must never hydrate or correct engine state.

### MVP Scope

- Start recording from live `LEVEL` mode by holding `Shift+Record` for one
  second.
- Snapshot the current live launch setup, rebuild it fresh, and arm recording
  before the first gameplay frame.
- Record controller input to a BK2-compatible zip file under
  `recordings/<game-id>/`.
- Include OpenGGF private BK2 entries for metadata and every-frame lightweight
  desync diagnostics.
- Provide a master-title recordings menu opened with `Shift+Record` on the
  highlighted game.
- Replay recordings in-engine with optional target-frame gating.
- Optionally pause on first desync.
- Optionally fast-forward playback by skipping normal scene rendering and
  showing only progress/status until a target, desync, movie end, or level
  boundary is reached.

### Out Of Scope For MVP

- Creating ROM trace fixtures from user recordings.
- Hydrating engine state from the recording diagnostic sidecar.
- Capturing video/audio output.
- Editing BK2 movies.
- Sparse diagnostic sidecars, except for forward-compatible manifest fields.
- Persisting arbitrary live object state as the recording start point.

## 2. Architecture

Add a new user-recording feature layer rather than expanding Trace Test Mode or
overloading `PlaybackDebugManager`.

### Core Units

| Unit | Responsibility |
|------|----------------|
| `UserRecordingSessionLauncher` | Entry point from live gameplay and master-title playback menu. Validates mode, snapshots launch context, starts recording or playback sessions. |
| `UserRecordingSession` | Owns the active record/playback lifecycle, stop conditions, HUD state, and teardown. |
| `UserRecordingCatalog` | Scans `recordings/<game-id>/*.bk2`, reads OpenGGF manifests, sorts newest first, and reports schema/load status. |
| `UserRecordingWriter` | Writes BK2 zip entries: `Header.txt`, `Input Log.txt`, `OpenGGF/manifest.json`, and `OpenGGF/desync-lite.jsonl`. |
| `UserRecordingVerifier` | Reads `desync-lite` rows during playback and reports clean/mismatch/missing/truncated/unsupported state without mutating the engine. |
| `RecordingLaunchContext` | Immutable value object describing the fresh restart setup: game id, zone, act, team/profile/session overrides, and deterministic start seed/counter inputs. |
| `UserRecordingPlaybackOptions` | Target frame, pause-on-desync toggle, and fast-forward toggle selected from the playback menu. |
| `UserRecordingHud` | Recording/playback overlay, hold prompt, fast-forward progress screen, mismatch summary, and completion stats. |

Low-level BK2 parsing should continue to reuse `Bk2MovieLoader`,
`Bk2Movie`, `Bk2FrameInput`, and `PlaybackTimelineController`. Any writer-side
helpers can live near those playback classes or in the new recording package,
but the session and UI behavior should stay under a focused
`com.openggf.recording` package.

Trace Test Mode should not depend on this feature. Shared extraction from
Trace Test Mode is acceptable only for genuinely generic BK2 or launch-context
utilities.

## 3. Recording Flow

Recording starts from live gameplay:

1. While in `GameMode.LEVEL`, the user holds `Shift+Record`.
2. A top-left red prompt appears:
   `Hold Shift+R for 1 Sec to Begin Recording`.
3. A 60-frame progress bar fills while the key chord is held.
4. On completion, the launcher snapshots the current live setup into
   `RecordingLaunchContext`.
5. The current gameplay session is torn down and rebuilt from that context.
6. Recording is armed before the first gameplay frame of the rebuilt session.
7. Each gameplay frame writes:
   - BK2 controller input, including P1 and P2 lanes where available;
   - one `desync-lite` row;
   - frame and status counters used by the recording HUD.
8. Pressing `Record` again stops and finalizes the BK2.

Recording also finalizes automatically when a level-end, act-transition, new
level-load, return-to-master-title, or engine teardown boundary occurs. The
final manifest records the stop reason and final frame count.

The fresh restart is based on setup, not mutable live state. It should preserve
game/module, zone, act, team/profile, launch overrides, and deterministic start
inputs. It should not preserve the live player's position, object slots, rings,
timer, RNG progress, editor mutations, or other runtime state unless a later
design explicitly extends `RecordingLaunchContext`.

Default output path:

```text
recordings/<game-id>/<game-id>-<zone-act>-YYYY-MM-DD-HHMMSS.bk2
```

Example:

```text
recordings/s3k/s3k-aiz1-2026-06-29-143022.bk2
```

## 4. Playback Flow

Playback starts from master title:

1. The user highlights a game.
2. The user presses `Shift+Record`.
3. A recordings menu opens for `recordings/<game-id>/`.
4. The menu lists loadable BK2s newest first, showing game, zone/act, frame
   count, schema status, and verification-sidecar availability.
5. `Enter` opens playback options:
   - target frame, optional; blank means run to movie end or a boundary;
   - pause on desync, default off;
   - fast-forward, default off.
6. Starting playback rebuilds the recorded launch context from
   `OpenGGF/manifest.json`, starts BK2 input driving, and enters `LEVEL`.

Default playback renders normally. If the BK2 contains
`OpenGGF/desync-lite.jsonl`, verification status is shown in the HUD. A missing
sidecar is informational and does not block playback.

If a target frame is selected, playback pauses at that frame with normal
rendering active. A later implementation decision controls whether resuming
continues BK2 playback or hands control back to live player input.

`Esc` exits playback, tears down the active session, and returns to the
recordings menu or master-title flow.

## 5. Fast-Forward

Fast-forward is a playback execution/display mode, not a different simulation
path. Gameplay still advances normally, consumes BK2 input normally, runs level
logic normally, and advances recording verification normally.

While fast-forward is active:

- Full scene rendering is skipped.
- The screen renders a minimal black status view with:
  - current movie frame;
  - target frame or movie end;
  - elapsed engine frames;
  - verification state;
  - stop reason when known.
- The engine runs as fast as practical.

Fast-forward exits and restores normal rendering when any of these occurs:

1. Target frame is reached.
2. Movie end is reached.
3. A desync is detected while pause-on-desync is enabled.
4. A level completion, act transition, or new level load occurs.
5. The user exits playback.

On target-frame or desync exit, playback pauses on the reached frame with
normal rendering restored. On level completion or transition, playback pauses
and shows completion stats such as zone/act, movie frame, time, rings, score,
lives, stop reason, and whether verification stayed clean.

Implementation should prefer a bounded fast-forward pump that can simulate
multiple frames per outer loop while still yielding often enough for window
events and exit input. The implementation plan should choose the exact pump
budget. The invariant is that skipped rendering must not skip gameplay updates,
input consumption, level transitions, audio/session counters needed by gameplay,
or verifier rows.

## 6. BK2 Format

The movie remains a normal zip-compatible BK2-style file. OpenGGF owns private
entries under `OpenGGF/`.

Required entries:

```text
Header.txt
Input Log.txt
OpenGGF/manifest.json
OpenGGF/desync-lite.jsonl
```

### `Header.txt`

Contains basic BK2 metadata plus OpenGGF creator/version fields. It should stay
simple enough that external BK2 tooling can ignore OpenGGF-specific data.

### `Input Log.txt`

Stores per-frame controller input using the same button vocabulary that
`Bk2MovieLoader` already parses. The writer should include P1 and P2 lanes so
sidekick input and future two-player/debug uses are not lost.

### `OpenGGF/manifest.json`

Carries recording metadata:

- manifest schema version;
- desync-lite schema version;
- game id;
- zone and act;
- team/profile/session overrides;
- launch context;
- output title and creation timestamp;
- frame count;
- stop reason;
- deterministic start seed/counter inputs where available;
- `sidecar.sampleMode: "every-frame"`;
- reserved optional `sidecar.sampleInterval` for future sparse modes.

### `OpenGGF/desync-lite.jsonl`

Every line is one frame's lightweight diagnostic snapshot. MVP writes every
frame because the zip container should compress the repeated structure well.
Sparse modes can be added later through the manifest contract.

Initial fields should be stable and cheap:

- frame index;
- game id, zone, act;
- level frame counter or equivalent frame-visible counter;
- main player center x/y;
- x/y speed, ground speed, angle;
- status/control bits and routine/mode where exposed;
- camera x/y;
- rings, timer, score, lives;
- RNG state if exposed through current services;
- level completion/transition marker;
- sidekick center/status rows when sidekicks are present.

The sidecar is diagnostic-only. It must not be used to hydrate start state,
repair playback, suppress gameplay, or add tolerance carve-outs.

## 7. Verification Model

`UserRecordingVerifier` compares current engine state after each playback frame
with that frame's `desync-lite` row.

Verifier outcomes:

- `clean`;
- `missing-sidecar`;
- `first-mismatch(frame, field, expected, actual)`;
- `schema-unsupported`;
- `truncated-sidecar`.

Default playback behavior is informational. The HUD shows verification state,
but playback continues through mismatches. If pause-on-desync is enabled, the
first mismatch stops fast-forward/playback on the mismatching frame, restores
normal rendering, pauses the engine, and displays the mismatch summary.

Verification must compare only the recording's fields. It should not grow
zone/route/frame exceptions and should not reuse trace replay hydration logic.

## 8. UI Details

### Live Recording HUD

During the one-second hold:

```text
Hold Shift+R for 1 Sec to Begin Recording
[progress bar]
```

During active recording:

```text
REC  frame <n>  <mm:ss>  <filename>
```

Use the existing pixel-font/debug-HUD style: compact text, top-left, red for
recording state.

### Recordings Menu

The recordings menu can follow `TestModeTracePicker`'s text-list pattern:

- black background;
- game heading;
- newest-first list;
- selected-recording info panel;
- schema/sidecar status;
- `Enter` for options/playback;
- `Esc` to return.

It should be a sibling to the trace picker, not a trace picker mode.

### Playback Options

Minimal prompt fields:

- target frame: blank/end or `0..movieLength-1`;
- pause on desync: toggle;
- fast-forward: toggle.

## 9. Testing

Unit tests:

- `UserRecordingWriter` creates a valid zip with all expected entries.
- Written BK2 can be loaded by `Bk2MovieLoader`.
- Manifest reader accepts current schema and rejects unsupported required
  schema versions.
- Manifest sidecar fields accept `sampleMode: "every-frame"` and tolerate
  reserved sparse-mode fields.
- Verifier reports clean match.
- Verifier reports first mismatch with field/frame/expected/actual.
- Verifier reports missing sidecar.
- Verifier reports truncated sidecar.
- Verifier reports unsupported sidecar schema.
- Playback timeline/options pause at target frame.
- Pause-on-desync stops at the mismatching frame.
- Fast-forward exits at target frame.
- Fast-forward exits at movie end.
- Fast-forward exits on level boundary/completion.
- Menu scrolling/selection/options behavior, modeled after
  `TestModeTracePickerTest`.

Integration tests:

- Synthetic no-ROM recording fixture exercises writer, loader, manifest reader,
  and verifier.
- Focused runtime test, where practical, records a tiny fresh-start session and
  replays it cleanly without requiring trace data.

## 10. Open Implementation Decisions

- Exact default `Record` key and config location. The feature should use a
  configurable key and render key names through `GlfwKeyNameResolver`.
- Exact fast-forward pump budget and yield strategy.
- Exact deterministic start seed/counter fields available in
  `RecordingLaunchContext`.
- Whether playback should allow resuming into live user control after target
  frame by ending the BK2 session immediately or keeping the session paused
  until explicit stop.
- Whether finalized recordings should be retained after zero gameplay frames
  or deleted as cancelled recordings.
