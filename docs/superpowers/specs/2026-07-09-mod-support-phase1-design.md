# Mod Support Phase 1 — Loader + Music Packs Design

**Date:** 2026-07-09
**Status:** implementation-ready after the 2026-07-10 review
**Branch baseline:** `next`
**Parent:** `docs/superpowers/specs/2026-07-09-mod-support-design.md`
**Plan:** `docs/superpowers/plans/2026-07-09-mod-support-phase1.md`

## Goal

Ship a data-only mod catalog, a gamepad-accessible manager, and bounded streamed
music overrides for S1, S2, and S3K. Phase 1 never loads mod classes, never changes
stock behavior when mods are disabled, and never scans external jars in deterministic
trace, test, attempt-replay, recording-replay, or capture sessions.

## A. Catalog and compatibility contract

- The mod root is the normalized absolute `${user.dir}/mods` directory. Tests and
  `ggfmod run` inject an explicit root; production code never consults another working
  directory implicitly.
- A manifest declares a globally unique, lower-case namespaced id; display name;
  semantic version; ordered author list; description; content type; singular base
  game; an **`engineApiRange` engine API semver range**; and dependency semver
  ranges. `authors` and `description` are required, nonblank, and retained for the
  manager detail view. One Phase 1 patch jar targets exactly one game. Data-format
  versions are separate integers owned by each format.
  Phase 1 publishes mod API version `1.0.0`.
- Duplicate ids refuse **all** colliding jars. Newly discovered mods default disabled.
  Catalog entries are a tagged valid/invalid union: an unreadable or malformed jar
  remains visible by filename and structured findings, never a null/fabricated
  manifest, and never enters eligibility.
  `standalone` and code-bearing manifests parse for forward compatibility but are
  blocked with an explicit Phase 1 reason.
- Eligibility is computed over the complete dependency graph. Strongly connected
  components are rejected first; a stable Kahn topological sort then orders the DAG,
  preserving user order between independent nodes. A dependent inherits the reason
  when any dependency is missing, incompatible, untrusted, or otherwise blocked.
- Manifest, audio-manifest, and override conflicts are structured catalog findings,
  not log-only warnings. The manager shows them and a mod with an error cannot be
  enabled. Override conflicts are warnings and name both owners; later eligible order
  wins.
- `modstate.json` has a validated schema version, unique ids, defensive immutable
  values, atomic replace-on-save, quarantine for structurally invalid state, and a
  surfaced save result. Newly added fields are ignored by older readers only where
  the old behavior remains safe.
  On providers without handle-relative directory operations or reliable file identity,
  the normalized mod root and its parent are trusted against concurrent malicious
  mutation for the full state load/quarantine/save transaction; the shared security
  contract defines the defense-in-depth boundary.

All jar paths and resource limits follow
`2026-07-10-mod-support-format-security-contracts.md`.

## B. Manager and apply timing

The master-title manager is reachable as a normal logical menu action from keyboard
and gamepad. Enable, disable, order, and later trust changes write pending state and
show **Restart required**. They never hot-load, hot-unload, or mutate an active
catalog. Dependency cascades use arm/confirm/cancel and report persistence failures.
The effective catalog is immutable for the process lifetime in Phase 1.

An engine-owned `ExternalContentPolicy` covers startup test/headless/trace mode and
later attempt replay, recording replay, and capture. Startup-known deterministic modes
gate before filesystem scanning. Later modes cannot undo a normal boot scan; they
atomically install empty session patch/audio registries, clear prepared PCM, rebuild
the session where required, invoke no mod callback, and perform no further scan.
Reset/return-to-title clears sticky backend resolver and decoded-track state.

## C. Audio identity and manifests

Phase 1 has two distinct identity domains:

1. `audioOverrides` maps an existing game-scoped numeric music id to a mod track.
2. `TrackKey(modId, localName)` identifies new tracks for Phases 2–3. A
   `ModTrackRegistry` maps keys to immutable sources; new content never steals a stock
   numeric id. Phase 1 publishes the registry contract even though only overrides are
   user-visible.

Audio manifests use the exact shared-contract v1 YAML shape, normalized `audio/...`
entry paths, strict booleans, gain in
`[0.0,4.0]`, sample rates 8,000–192,000 Hz, mono/stereo only, and integer frame
positions. A loop is `[loopStartFrame, loopEndFrame)`; omitted end means decoded end.
Invalid static metadata is a catalog error. A corrupt codec payload
discovered during launch preparation is a runtime finding with the pending-disable
outcome below; it never mutates the frozen catalog retroactively.

Enabled tracks are validated and decoded/resampled during launch preparation, before
the gameplay session opens. Per-entry and total decoded budgets are enforced before
allocation; the resolver cache key includes source identity and output sample rate.
The gameplay audio callback performs no jar I/O, parsing, or unbounded allocation.
Preparation returns immutable `PreparedAudioSession(tracks, findings, failedOwners)`.
Failed owners and their dependents are excluded from that session audio view and are
written disabled to pending state and atomically persisted immediately for the next
restart. Persistence failure becomes a visible finding but does not re-enable them in
the current session. The process-lifetime effective catalog remains immutable and base
music is the fallback.

Preparation findings publish to an engine-owned boot-lifetime runtime-finding store
consumed by the manager. Phase 2 reuses it for code callback failures; findings do not
mutate the immutable effective catalog.

## D. Presentation-stream ownership

`AbstractSmpsAudioBackend` owns one logical foreground source: SMPS or streamed. An
active streamed source participates in the same presentation-pump contract used by
LWJGL:

- it makes the backend report presentation work even when `currentStream == null`;
- starting it starts/queues the device pump;
- it exposes the exact upload sample rate, including internal-rate output;
- stopping, reset, resolver replacement, or shutdown unqueues it cleanly; and
- mixing occurs once into the final upload buffer.

Tests drive `playSmps` through an instrumented backend and assert a non-zero uploaded
buffer. Direct calls to `mixInto` are unit coverage, not integration proof.

Jingles are foreground SMPS sources stacked over the streamed base track. Pause and
restore happen through a pending transition consumed at the audio update/pump
boundary, matching the existing buffer-mutation rule. Fade targets the foreground
source. Speed-shoes tempo is active only for multipliers greater than one; the
documented initial streamed approximation is a 1.25 playback-rate/pitch shift.

## E. Rewind and capture

Streamed presentation remains outside deterministic PCM capture, but its **logical
keyframe state is captured**: track key, logical music id, frame position, pause
reason, fade state, and playback rate. Restore clears queued buffers first, restores
that state, and resumes from the keyframed position. Replaying the same logical id and
source is idempotent; two ids sharing a file are distinct starts. Reverse playback
pauses presentation and forward playback reconciles from restored logical state.

Trace/test/headless processes never scan mod audio. Attempt/recording replay and
capture entered after normal boot disable and release prepared mod audio before their
deterministic session and do not scan again. Normal user rewind outside those modes
follows the keyframe contract above.

## F. Failure and resource handling

Parsing uses depth, alias, token, and duplicate-field limits. Scanner failures are
isolated per jar after bounds checks; the scanner catches expected filesystem and
parser failures, but does not claim to recover from VM errors. Audio cache ownership
is immutable/exclusive and bounded with deterministic eviction between sessions.

## Acceptance criteria

- Missing directory, malformed jars, duplicate ids, cycles, blocked dependency
  chains, incompatible ranges, and invalid persisted state all terminate with stable
  structured results and no hangs.
- The manager is fully operable by logical keyboard/gamepad actions and reports
  restart-required and save failures.
- Three single-game music-pack fixtures collectively override one track in S1, S2,
  and S3K; fallback is bit-identical with an empty resolver.
- Actual LWJGL-style pumping produces uploaded PCM for a streamed-only foreground
  source at device and internal output rates.
- Stacked jingles, fade, speed shoes, reset, backend replacement, rewind seek, and
  shutdown preserve the ownership rules above.
- A deterministic-mode test places a malformed enabled jar in the real mod root and
  proves the scanner is not invoked.

## Non-goals

Code mods, hot reload, live apply, new-zone routing, standalone games, streamed SFX,
MP3, and deterministic streamed PCM capture are later work.
