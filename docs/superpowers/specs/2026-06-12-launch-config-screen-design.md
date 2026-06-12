# Launch Configuration Screen — Design

**Date:** 2026-06-12
**Status:** Approved for planning

## Goal

A per-game pre-launch configuration panel on the master title screen. Each game
(S1/S2/S3K) persists its own launch profile covering the engine's optional
features (rewind, cross-game donation, debug tools, characters). The default is
the stock game — no optional features — and options are enabled per game as
desired. The hover line on the master title shows how far the selected game's
profile diverges from stock and how to open the panel.

## Scope decisions (from brainstorming)

- **Per-game profiles**, not a single global option set.
- **Stock wins for everyone:** profiles start stock; the existing global config
  keys are *ignored* when launching through the master title screen. Direct
  launches (master title disabled, trace sessions) keep today's global-key
  behavior unchanged. No migration/seeding from globals.
- **Fully functional:** UI + persistence + options genuinely applied at launch.
- **Rows limited to finished features:** collision view and the level editor
  are excluded for now (unfinished). The debug row is named **Debug Tools**
  because `debug.flags.debugView` now gates both the overlay and the debug
  cheat keys (e.g. give emeralds).
- In-engine implementation only; no committed HTML mock-up.

## UX

### Master title hover line

When the selected game's ROM is present, a centered line renders above the game
menu (~y 177, between the ROM preview matte and the menu row):

- Stock profile: `Stock game · Tab to configure` (dim grey).
- Diverged: `3 options enabled · Tab to configure` (gold tint).

Missing-ROM games show no line and Tab is a no-op.

### Launch config panel

Pressing **Tab** in the `ACTIVE` state opens the panel over a semi-transparent
black matte (same style as the error overlay). Title: e.g.
`Sonic 2 — Launch Options`. Five rows:

| Row | Values (stock first) | Notes |
|---|---|---|
| Rewind | Off / On | maps to `rewind.liveEnabled` |
| Cross-Game Donation | Off / donor game | donor excludes the game itself; maps to `crossGame.enabled` + `crossGame.source` |
| Debug Tools | Off / On | overlay + debug cheat keys; maps to `debug.flags.debugView` |
| Main Character | Sonic / Tails / Knuckles | maps to `characters.main` |
| Sidekick | per-game stock / None / Tails / Sonic / Knuckles | S1 stock: None; S2/S3K stock: Tails; maps to `characters.sidekick` |

Controls: **Up/Down** select row, **Left/Right** cycle value, **Backspace**
reset all rows to stock, **Tab/Esc** close. Closing persists immediately via
`saveConfig()`. A footer line lists the controls.

### Row markers

Two independent markers:

- **(stock)** — dim marker on rows currently at their stock value.
- **`*` non-standard** — amber tint + asterisk on values impossible in the
  original game, with a footer legend `* not possible in the original game`.

Non-standard rules:

- Rewind On, Cross-Game Donation ≠ Off, Debug Tools On: always non-standard
  (engine features).
- Characters are validated **as a (main, sidekick) pair** against the game's
  authentic `Player_mode` combos:
  - S1: (sonic, none)
  - S2: (sonic, none), (sonic, tails), (tails, none)
  - S3K: (sonic, none), (sonic, tails), (tails, none), (knuckles, none)
  A non-standard pair flags **both** character rows. Tails-alone in S3K is
  non-stock but standard — it gets `(stock)` removed but no asterisk.

## Persistence schema

New `launch:` section in `config.yaml` with nested per-game blocks:

```yaml
launch:
  s1:
    rewind: false
    crossGameSource: "off"   # "off" or donor game id
    debugTools: false
    mainCharacter: "sonic"
    sidekick: "none"
  s2: …   # same shape; sidekick stock "tails"
  s3k: …  # same shape; sidekick stock "tails"
```

5 options × 3 games = **15 new `SonicConfiguration` keys**, each with
`ConfigCatalog` metadata. The `launch` section is placed after `crossGame` and
**before the `debug.*` block** (catalog ordering rule; `TestConfigCatalog`
enforces). Cross-game enabled/source collapse into a single
`crossGameSource` value (`"off"` = disabled) so a row maps to one key.

New package `com.openggf.game.launch`:

- **`LaunchProfile`** (record) — the 5 fields, plus:
  - `stockFor(MasterTitleScreen.GameEntry)` factory,
  - `enabledCount()` — rows differing from stock,
  - per-row cycling helpers (next/previous value given the game entry),
  - `isCharacterPairStandard(GameEntry)` and per-row standardness predicates.
- **`LaunchProfileStore`** — load/save a `LaunchProfile` for a game through
  `SonicConfigurationService`. Unknown hand-edited values clamp to stock with a
  log warning.

## Launch application — session-override overlay

**Hazard found during design:** `DisplayColorProfileController` calls
`saveConfig()` mid-game (V key), which writes the entire in-memory config map.
If the profile were applied by overwriting globals with `setConfigValue`, a
mid-game save would silently persist profile values into the user's global
keys.

**Mechanism:** `SonicConfigurationService` gains a **session-override
overlay** — a separate map checked first by all getters, never written by
`saveConfig()`, cleared with `clearSessionOverrides()`. (The trace picker's
existing "disable test mode for this session" `setConfigValue` call has the
same latent bug and can migrate to the overlay later — out of scope here.)

**`LaunchProfileApplier`** (in `com.openggf.game.launch`) maps a profile onto
the **6 affected global keys** as session overrides:
`rewind.liveEnabled`, `crossGame.enabled`, `crossGame.source`,
`debug.flags.debugView`, `characters.main`, `characters.sidekick`.
Value mappings: profile `crossGameSource="off"` → `crossGame.enabled=false`
(source left at its global value); profile `sidekick="none"` → empty string
for `characters.sidekick` (the existing disable convention).

Rules:

- Runs in `GameLoop.doExitMasterTitleScreen`, before the master-title exit
  handler, so every downstream reader (rewind controller construction,
  cross-game providers, sprite setup) sees profile values with **zero changes**
  to reader code.
- Always sets **all** managed keys — stock values included — so returning to
  the master title and launching a different game cannot leak the previous
  profile.
- **Skipped for programmatic selections:** `MasterTitleScreen.selectEntry`
  (used by `TraceSessionLauncher`) sets a `programmaticSelection` flag that
  `GameLoop` checks — trace determinism must not depend on user profiles.
- Direct launches that bypass the master title never run the applier; global
  keys behave exactly as today.

## Panel implementation

**`LaunchConfigPanel`** in `com.openggf.game`, following the
`TestModeTracePicker` pattern:

- Constructed with the `GameEntry`, its loaded `LaunchProfile`, the shared
  `PixelFont` / `TexturedQuadRenderer`, and the `LaunchProfileStore`.
- `update(InputHandler)` is pure state-machine logic (testable headless) and
  returns a `CLOSED`/`NONE` result; `MasterTitleScreen` consumes `CLOSED` by
  saving through the store and nulling the panel field.
- `render()` draws matte, title, rows with value + markers, footer. All
  foreground centered on `viewportWidth` like other master-title elements.
- `MasterTitleScreen` integration mirrors the trace picker: a panel field;
  when non-null, `update`/`draw` delegate to it. Tab (`GLFW_KEY_TAB`,
  hardcoded like Enter) opens it from `ACTIVE` when the selected ROM exists.

## Edge cases

- Hand-edited invalid yaml values → clamp to stock at load, log warning.
- Cross-game donor list excludes the launching game; if a stored donor equals
  the game (hand-edit), clamp to `off`.
- Widescreen: all panel elements center on `viewportWidth`; at native 320 the
  layout collapses to fixed literals like the rest of the screen.
- Panel open state blocks game-select navigation and confirm (delegation
  pattern guarantees this, same as the trace picker).
- Test mode (trace picker) takes precedence: when `TEST_MODE_ENABLED`, the
  picker path runs before any Tab handling, unchanged.

## Testing

- `LaunchProfile`: stock factories, `enabledCount`, cycling, standardness
  (pair rules per game) — pure unit tests.
- `LaunchProfileStore`: round-trip + invalid-value clamping — `@TempDir` with
  `user.dir` override (parallel-fork safety).
- Session overlay: getters honor overrides; `saveConfig()` output excludes
  them; `clearSessionOverrides()` restores persisted values.
- `LaunchProfileApplier`: sets all 6 keys; stock launch resets prior
  overrides; programmatic selection skips application.
- `LaunchConfigPanel`: navigation, cycling, reset-all, close result — stubbed
  `InputHandler`, no GL.
- Hover-line text: static pure function on `MasterTitleScreen` (existing
  `menuTextColor`-style test approach).
- `TestConfigCatalog` green with the 15 new keys.

## Out of scope / follow-ups

- Collision view and level editor rows (features unfinished).
- Migrating the trace picker's session `setConfigValue` to the overlay.
- Rewind tuning (history seconds, tape-coast) — config.yaml only.
- Per-profile key bindings, audio, or display options.
- Data select / in-game surfacing of the active profile.
