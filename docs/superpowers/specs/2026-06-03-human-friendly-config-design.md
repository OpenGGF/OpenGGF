# Human-Friendly Configuration Design

**Date:** 2026-06-03
**Status:** Approved design, ready for implementation planning
**Topic:** Replace the flat, undocumented, unordered `config.json` with a grouped, commented, ordered `config.yaml` projected from an enriched `SonicConfiguration` enum.

## Problem

`config.json` is a flat `Map<String, Object>` keyed by `SonicConfiguration` enum names (~95 keys). It is hard to live with for four distinct reasons:

1. **No grouping.** All 95 keys share one namespace; `P2_DOWN` sits next to `DAC_INTERPOLATE`.
2. **No ordering.** JSON objects are unordered and Jackson serializes the backing `HashMap` in hash order, so every `saveConfig()` reshuffles the file.
3. **No inline documentation.** JSON forbids comments, yet `SonicConfiguration` already carries excellent per-key Javadoc that never reaches the user editing the file.
4. **No discoverability/validation.** Nothing tells a user which values are legal (`DISPLAY_ASPECT` presets, GLFW key names, `s1`/`s2`/`s3k`), and a mistyped key is silently ignored.

## Goals

- Group keys into human-readable sections on disk.
- Guarantee deterministic ordering across saves.
- Surface the existing per-key documentation as inline comments.
- Warn on unknown keys and illegal enumerated values instead of failing silently.
- Do all of the above with **minimal blast radius** on the ~existing read path and callers.

## Non-Goals

- In-engine settings UI (future work; noted, not built here).
- Reworking what each setting *does* or its defaults.
- Per-profile / multi-file config, includes, or env-var overrides.

## Chosen Approach

**The `SonicConfiguration` enum becomes the single source of truth; the on-disk file becomes a projection of it.** The file format moves from flat JSON to **nested YAML** with generated section headers and per-key comments.

Jackson already ships YAML support (`jackson-dataformat-yaml`) in the same `ObjectMapper` family used today, so reading costs minimal new dependency surface. Writing uses a small **custom emitter** (Jackson/SnakeYAML cannot cleanly emit the comments we want), driven entirely by enum metadata.

Critically, **the read path stays almost untouched.** Nested YAML is parsed and immediately *flattened* back into the existing enum-name-keyed `Map<String, Object>` (`config`). Therefore `getConfigValue()`, `getInt()`/`getShort()`/`getString()`/`getDouble()`/`getBoolean()`, the `transientResolved` display overlay, `applyDefaults()`, and every caller continue to work unchanged. Nesting is purely a disk-serialization concern.

### Example on-disk file (`config.yaml`)

```yaml
# ── Display ──
display:
  aspect: NATIVE_4_3        # Display aspect preset (NATIVE_4_3, WIDE_16_10, WIDE_16_9, ULTRA_21_9, SUPER_32_9)
  windowAutosize: true      # Derive the window from the aspect preset at the 2x baseline
  scale: 1.0                # Scale factor for BufferedImage rendering (AWT debug viewers)
  fps: 60                   # Frames per second to render

# ── Input · Player 1 ──
input:
  player1:
    up: UP                  # Key to look up
    down: DOWN              # Key to crouch/roll
    left: LEFT              # Key to move left
    right: RIGHT            # Key to move right
    jump: SPACE             # Key to jump
  player2:
    up: I
    jump: RIGHT_SHIFT

# ── Audio ──
audio:
  enabled: true             # Music + SFX
  region: NTSC              # NTSC/PAL audio timing
```

## Architecture

### 1. Enum metadata model

Each `SonicConfiguration` constant gains structured metadata, supplied via constructor arguments:

| Field | Purpose |
|---|---|
| `section` | Dotted section path, e.g. `"input.player1"`, `"audio"`, `"debug.keys"`. |
| `leaf` | The key name inside its section, e.g. `"jump"`. Defaults derivable from the constant name but specified explicitly for clarity. |
| `type` | One of `BOOL`, `INT`, `DOUBLE`, `STRING`, `KEY`, `ENUM` — drives validation and value formatting. |
| `description` | One-line doc, migrated from the existing Javadoc, emitted as the inline `# comment`. |
| `allowedValues` | Optional set for `ENUM`-typed keys (`DISPLAY_ASPECT`, `REGION`, `DEFAULT_ROM`, `WIDESCREEN_DEADZONE_MODE`, `DISPLAY_COLOR_PROFILE`, `CROSS_GAME_SOURCE`, `REWIND_AUDIO_HISTORY_LIMIT_TYPE`). |

**Declaration order = emit order.** The enum's current order is already a sensible grouping; we tidy it so constants in the same section are contiguous.

The enum's `name()` remains the canonical map key, so nothing about the in-memory representation or the `defaults` map changes.

### 2. Section taxonomy

Derived from enum metadata. Initial sections (final list driven by the metadata, not hardcoded elsewhere):

- `display` — screen pixels/window, scale, fps, color profile, aspect, deadzone, autosize
- `input.player1` — UP/DOWN/LEFT/RIGHT/JUMP
- `input.player2` — P2_* keys
- `audio` — enabled, region, DAC interpolate, internal-rate output, PSG noise mode, FM6 DAC off
- `characters` — main/sidekick character codes, data-select extra combos
- `roms` — S1/S2/S3K ROM filenames, default ROM
- `startup` — title/level-select/master-title/legal-disclaimer on startup, S3K skip intros
- `debug.flags` — debug view, editor enabled, collision view
- `debug.keys` — TEST, NEXT_ACT/ZONE, debug-mode, color-profile-toggle, special-stage keys, pause, frame-step, last-checkpoint, level-select, super-sonic, give-emeralds
- `playback` — PLAYBACK_* movie path + keys + start offset
- `rewind` — trace rewind key, LIVE_REWIND_* tape-coast, REWIND_AUDIO_*
- `crossGame` — CROSS_GAME_* flags/keys/source
- `testMode` — TEST_MODE_ENABLED, TRACE_CATALOG_DIR
- `discord` — DISCORD_RICH_PRESENCE_*

`VERSION` is currently unused (no references in `src/`). Leave it out of the emitted file (or assign it a `meta` section); do not invent behavior for it.

### 3. Read path

`loadConfig()`:
1. Resolve the config file (working dir or next-to-executable for native image — unchanged logic, new filename).
2. Parse YAML into a nested `Map<String, Object>` via the Jackson YAML mapper.
3. **Flatten** the tree to enum-name keys: for each leaf, find the constant whose `section` + `leaf` matches, and store `config.put(constant.name(), value)`.
4. Collect any leaf with no matching constant into an `unknownKeys` list and log a warning (problem #4).
5. Run existing migration hooks and `applyDefaults()` exactly as today.

Flattening is the **only** new step on read; everything downstream sees the same flat map it sees today.

### 4. Write path

`saveConfig()` delegates to a new `ConfigYamlWriter`:
1. Walk `SonicConfiguration.values()` in declaration order.
2. When `section` changes, emit a blank line + `# ── <Human Section Title> ──` header and open the nested mapping blocks for that path.
3. For each constant, emit `<leaf>: <formatted value>   # <description>`, reading the value from the flat `config` map (falling back to the default).
4. Values formatted by `type` (quote strings when needed, bare booleans/numbers, key names as bare tokens).

This guarantees deterministic ordering and grouping every save (problems #1 and #2), and inline docs (problem #3). The writer is pure-text and metadata-driven — no reflection on live objects beyond the enum.

`transientResolved` (derived display values) is **never written**, preserving today's invariant that `SCREEN_WIDTH_PIXELS` etc. are computed, not persisted.

### 5. Migration

Extend the existing `ConfigMigrationService` pattern. On startup, in priority order:
1. If `config.yaml` exists → load it (steady state).
2. Else if legacy `config.json` exists → read the flat JSON, map each key through the enum to populate `config`, then write `config.yaml`, and rename the old file to `config.json.bak`. Log a one-line migration notice.
3. Else → classpath fallback (`/config.json` resource) then defaults, then write a fresh `config.yaml`.

Existing in-map migrations (`detectAwtKeyCodes`, `migrateDeprecatedS1PreviewCoordLogKey`, `migrateDeprecatedDisplayColorProfileToggleKey`, `S3K_SKIP_AIZ1_INTRO` rename) continue to run against the flat map after load, unchanged.

### 6. Validation

A startup validation pass (building on the warning logic already in `getInt()`):
- Unknown section/leaf keys → `LOGGER.warning` (already collected during flatten).
- `ENUM`-typed values not in `allowedValues` → warn and fall back to the registered default.
- Key-typed values that resolve to no GLFW code → existing behavior (warn, default/unbound).

### 7. Filename references to update

`config.json` (the literal filename) appears in 6 files; all must be reconciled with the new default and migration:

- `src/main/java/com/openggf/configuration/SonicConfigurationService.java` — load/save/resolve logic (primary change site).
- `src/main/java/com/openggf/testmode/TestModeTracePicker.java` — verify why it references the filename; update if it reads/writes config.
- `src/packaging/assemble-macos-app.sh` — packaging copies the config next to the executable; update to `config.yaml` (and/or ship both during transition).
- `src/test/java/com/openggf/configuration/TestSonicConfigurationFileBootstrap.java`
- `src/test/java/com/openggf/tests/TestSonicConfigurationService.java`
- `src/test/java/com/openggf/widescreen/TestWidescreenNativeRegression.java`

The classpath resource `/config.json` may be kept as a legacy fallback or converted to `/config.yaml`; decide during planning (keeping both eases transition).

## Testing Strategy

JUnit 5 only (per repo policy). New/updated tests:

1. **Round-trip stability** — load → save → load produces byte-identical YAML; ordering is deterministic across repeated saves (directly refutes problem #2).
2. **Flatten/unflatten symmetry** — every constant survives nest→flatten→nest with its value intact.
3. **Migration** — a sample legacy flat `config.json` migrates to an equivalent `config.yaml`, `.bak` is created, and all values match.
4. **Unknown-key warning** — an unrecognized leaf logs a warning and does not crash.
5. **Metadata completeness** — every `SonicConfiguration` constant (except dormant `VERSION`) has a non-empty `section` and `description`; `ENUM`-typed constants have non-empty `allowedValues`.
6. **Existing tests** — update the 3 config tests and the widescreen native regression test to the new filename/format; keep their assertions on behavior intact.

## Risks & Mitigations

- **YAML whitespace sensitivity** — hand-editing can break indentation. Mitigation: the emitter always rewrites a clean file on save, and validation warns on parse problems; document the format in `CONFIGURATION.md`.
- **Comment emission** — Jackson's YAML writer can't emit our comments, so the custom emitter is load-bearing. Mitigation: keep it small and fully test-covered (round-trip test). Reading still uses the robust library parser.
- **External tooling reading `config.json`** — the packaging script and any docs must move together; the `.bak` + transition fallback reduce breakage.

## Stretch (optional, not required)

- Generate a `config.schema.json` from the same enum metadata for editor autocomplete/validation. Inline comments already cover discoverability, so this is additive, not essential.

## Documentation Obligations

- `CONFIGURATION.md` — document the new YAML layout, sections, and the migration behavior.
- `CHANGELOG.md` — engine change touching `src/main/` (`Changelog: updated`).
- Commit trailers per repo policy.
