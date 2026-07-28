# Time Attack Phase 1 — Recorded Debt

Carried out of the phase-1 final whole-branch review (base 9e5f7f3e8, head
b025ff780). None are blockers; each should be triaged into the phase-2 plan
(race session + direct connect) or fixed opportunistically when its file is
next touched.

## Lifecycle / gating

- ~~Move `voidCurrentAttempt()` inside `enterSpecialStage()` so the debug Tab
  handler (GameLoop ~:1628) and level-select entry (~:3594) are covered
  uniformly (the consume-site void covers only the star-post path).~~ Resolved:
  special/bonus stage entry is now gated directly inside `enterSpecialStage()`
  / `enterBonusStage()` (covers request-consume, debug Tab, and level-select
  uniformly) and swallows entry instead of voiding the attempt; the run
  continues unharmed. `TimeAttackRuntime.voidCurrentAttempt()` had no other
  callers and was removed.
  - Follow-up (found while landing the above): the GameLoop-only gate was not
    sufficient for the two giant-ring routes (S1 `Sonic1GiantRingObjectInstance`
    / S3K `Sonic3kSSEntryRingObjectInstance`) — both hide/control-lock the
    player (and S3K freezes the camera) *before* the request ever reaches the
    chokepoint, so swallowing it there alone left the run permanently frozen
    until a manual retry. Resolved by adding `GameStateManager.timeAttackActive`
    (set by `TimeAttackRuntime.onLevelReady()`, cleared by `deactivate()`,
    intentionally surviving `resetForLevel()` so a time-attack retry doesn't
    un-suppress the gate) and checking it at the top of each ring's touch
    reaction, before any state change — the ring stays fully inert and the
    player passes through. S2's checkpoint star and S3K's star-post bonus star
    needed no such change (no hide/freeze side effects).
- Gamepad Y/Triangle free-fly toggle and `LEVEL_SELECT_KEY` (F9) bypass the
  key-based taint net (dev-config-gated; taint them like the keyboard cheats).
- `armForLaunch` refusal (trace/test/playback active) is log-only — add a
  HUD/menu notice so "my timer never appeared" is explained.
- `actCompletionSignalActive` is not in the rewind `GameStateSnapshot` —
  harmless while its only consumer (time attack) blocks rewind; add it to the
  snapshot if a rewind-compatible consumer ever appears.
- Seamless mid-act reloads consume a gameplay frame with no input-recording
  row — phase-2 replay-verification alignment item.

## Codec / store hardening

- `GhostFileCodec.read`: validate `0 <= firstInputFrame <= finishFrame` — a
  hand-planted hostile "best" file with absurd `finalTimeFrames` blocks all
  future saves via the `<=` compare in `GhostStore.saveIfBest`.
- `GhostFileCodec.write`: splits count > 255 silently truncates via
  `writeByte` (unreachable under the 10-minute cap; guard anyway).
- `AttemptInputRecording.encode()` lacks a symmetric MAX_FRAMES guard
  (runtime caps at 36,000; defense-in-depth only).
- `GhostFrameCodec` has no bounds checks on `off`/`priorityBucket`
  (silent `& 0x07` truncation); decode `< 0` length branch untested;
  no negative-coordinate masking test in capture.
- `GhostFileCodec.write` NPEs if `path.getParent()` is null; redundant
  defensive clones in `write()`; `GhostHeader.hashCode` weak distribution
  (plan-mandated); zone/act int→byte wire truncation unguarded.
- `GhostStore`: full-file read just to compare `finalTimeFrames`; `rotate()`
  parameterization adds no value; `stemSuffix`/`sibling` duplicate stem logic.
- `PlayerIdentity`: partial-write recovery regenerates and silently
  OVERWRITES the old key when only one of the two key files survives; no
  concurrency guard on a shared identity dir.

## UI / polish

- Tied split delta (`0`) renders RED ("behind") — decide a tie color.
- `TimeAttackHudOverlay` is public (sibling overlays are package-private) —
  intentional cross-package asymmetry, documented here.
- `TIME_ATTACK_MENU_KEY` default F10 collides with the PLANE_SWITCHERS debug
  toggle's F10 in CONFIGURATION.md (different contexts, no functional clash).
- `TimeAttackMenu.render()` / GL render paths untested (matches sibling
  `UserRecordingMenu` gap).
- `GhostRenderRegistry.register` double-registration semantics undocumented
  (runtime now dedupes via `attachRenderer`).

## User-verify items deferred from Task 14 (manual pass)

1. Ghost draw-order vs loops/foreground (recorded render-layer byte).
2. Live-rewind suppression during an active time-attack session.
3. Dual-ghost simultaneous render (best + import).
4. Identity fingerprint INFO log stable across restarts.
