# Configurable Key Modifiers — Design

Date: 2026-07-25
Branch: `feature/ai-key-modifiers` (worktree
`/home/farrell/code/projects/OpenGGF-key-modifiers`, based on `origin/develop`
at `1474a7a48`)

## Problem

A key binding in `config.yaml` is a bare key code. Any shortcut that wants a
modifier has to hardcode it at its call site, which splits one user-facing
concept across two places and leaves half of it unconfigurable.

`capture.toggleKey` is the worked example. The player can change the key, but
the Shift it must be pressed with lives in `Engine.handleLiveCaptureShortcut`:

```java
int key = configService.getInt(SonicConfiguration.CAPTURE_TOGGLE_KEY);
if (!liveCaptureChord.update(inputHandler.isKeyDown(key), inputHandler.isShiftDown(),
        inputHandler.isControlDown(), inputHandler.isAltDown())) {
```

So the binding documents itself as "the key", the chord is really "Shift + that
key", and there is no way to express `CTRL+SHIFT+O` or to drop the Shift. The
same shape has already recurred five more times (see the inventory below).

## Goal

Express modifiers in the binding itself:

```yaml
capture:
  toggleKey: "SHIFT+O"
debug:
  keys:
    something: "CTRL+SHIFT+O"
    other: "META+LEFT_BRACKET"
```

Non-goals: rebinding UI, per-context keymaps, chord sequences (`C-x C-s`),
gamepad chords.

## Current state (verified)

| Fact | Location |
|---|---|
| `KEY` bindings resolve **name first, integer second** | `SonicConfigurationService.resolveInt:183-202` |
| Registered *defaults* resolve **integer first, name second** | `SonicConfigurationService.resolveKeyCode:853-869` |
| Unresolvable `KEY` value falls back to the **default**, not unbound | `SonicConfigurationService.resolveInt:204-217`; `docs/guide/playing/configuration.md:118` |
| Name↔code tables already exist both ways | `GlfwKeyNameResolver.resolve` / `.nameOf` |
| 51 `KEY`-typed config entries | `ConfigCatalog` |
| Only Shift/Ctrl/Alt are queryable; **no Super/Meta** | `InputHandler:173-192` |
| Key state is cleared **only** on an observed `GLFW_RELEASE` | `InputHandler.handleKeyEvent:68-76`; focus callback `Engine:495-502` does not clear |
| Default is the bare key, in two places | `putDefaultKey(CAPTURE_TOGGLE_KEY, GLFW_KEY_O)` **and** `src/main/resources/config.yaml:172` |

The two resolution orders in rows 1 and 2 are the root of a landed bug — see
"Finding: two key-resolution orders" below. The earlier draft of this spec cited
only `resolveKeyCode` and so mis-stated the live rule.

### Inventory: hardcoded chords

`capture.toggleKey` is **not** the only one. Verified call sites:

| Site | Chord | Key source | In scope |
|---|---|---|---|
| `Engine.java:1943` | Shift + key | `CAPTURE_TOGGLE_KEY` (config) | **Yes — step 4** |
| `game/recording/UserRecordingRuntimeControls.java:33,44` | Shift+key to start; key alone to stop | `RECORDING_RECORD_KEY` (config) | Deferred — step 7, both sites together |
| `game/MasterTitleScreen.java:375` | Shift + key | `RECORDING_RECORD_KEY` (config) | Deferred — step 7, with the above |
| `GameLoop.java:1877-1895` | Shift+B / Ctrl+B / Alt+B, exactly-one-modifier | hardcoded `GLFW_KEY_B` | No — key is not configurable |
| `GameLoop.java:903-905` | Shift+Tab | hardcoded `GLFW_KEY_TAB` | No — key is not configurable |
| `debug/DebugOverlayManager.java:68` | Ctrl+P | hardcoded `GLFW_KEY_P` | No — documented as non-configurable |
| `editor/EditorInputHandler.java:99-101,113-122` | Tab-without-Shift, Ctrl+Z/S/Y | hardcoded, raw `GLFW_KEY_LEFT_CONTROL`/`LEFT_SHIFT` | No — editor bindings are documented as hardcoded |
| `graphics/shaderlib/DisplayShaderPickerController.java:177` | Shift branch | picker-local | No |

`RECORDING_RECORD_KEY` is the obvious second conversion candidate: it is a
configurable `KEY` whose Shift is hardcoded, its `ConfigCatalog:327` description
advertises the Shift in prose exactly the way `CAPTURE_TOGGLE_KEY`'s does, and
it is read from **two** call sites that must be converted together or they will
disagree about what starts a recording.

### Finding: two key-resolution orders

`resolveInt` resolves a `KEY`-typed string through the name table **first** —
there is an explicit branch and comment for it, because `"1"` must mean the
number-row 1 key (49), not raw key code 1. `TestConfigKeyNameResolution.
getInt_digitKeyNameResolvesToNumberRowKeyBeforeRawIntegerParsing` pins that.

`KeyChord.keyCode` (landed in step 0) does `Integer.parseInt` **first**. So:

- `KeyChord.parse("1").keyCode()` is `1`, while `getInt` on the same value is
  `49`. `1` is below the lowest GLFW key code (`SPACE` = 32), so `isBound()`
  returns true and the binding is silently dead rather than reported.
- `format()` does not round-trip for `GLFW_KEY_0`..`GLFW_KEY_9`:
  `nameOf(49)` is `"1"`, and `parse("1")` is `1`.

`TestKeyChord` exercises only `"O"` / `"GLFW_KEY_O"` / `"79"` / `LEFT_BRACKET`,
all unambiguous, so its 13 tests do not catch this. `CONFIGURATION.md:536-541`
documents both numeric strings and single-character names as valid, so digit
bindings are a supported form.

**Decision: `KeyChord` adopts `resolveInt`'s order — name table first, integer
parse second.** Fixed in step 0b.

### Finding: `parse` throws on separator-only input

`text.split("\\+")` drops trailing empty tokens, so `"+"`, `"++"` and `"+++"`
each yield a **zero-length** array and `segments[segments.length - 1]` indexes
`-1`. Verified with a JDK probe: `[+] len=0`, `[++] len=0`, `[+++] len=0`, while
`[CTRL+] len=1` and `[+O] len=2` are safe.

`"+"` is exactly what a player writes to bind the plus key once the docs
advertise `+` as the separator, and after step 4 the parse happens inside
`Engine.handleLiveCaptureShortcut` on the render path — an uncaught per-frame
throw, not a startup warning. Fixed in step 0b.

(The `+`-is-a-safe-separator claim itself holds: every name in the table derives
from a `GLFW_KEY_*` Java identifier, plus the one manual `"#"` alias at
`GlfwKeyNameResolver:67`, and none can contain `+`.)

### Finding: modifier queries are inconsistent about logical input

`isShiftDown()` and `isControlDown()` consult `logicalOverride`
(`debugShiftDown()` / `debugControlDown()`) so trace playback and rewind can
drive them deterministically. **`isAltDown()` does not** — it always reads live
hardware. Any chord using Alt would therefore not be reproducible under trace
playback, unlike the same chord using Ctrl or Shift.

Two qualifications the earlier draft missed:

- Even for Shift/Ctrl, only *live rewind* supplies real values.
  `Bk2MovieLoader:162-166` builds frames with the 8-arg convenience constructor,
  so `debugShiftDown`/`debugControlDown` are hardcoded `false` for every BK2
  frame. A Shift+O capture chord is already untriggerable under BK2 playback.
  That is pre-existing, but it means "deterministic" here means "deterministically
  false".
- Extending the surface is a record change, not an `InputHandler` edit —
  see step 2 for the exact blast radius.

**Decision: extend the logical-override surface to Alt and Meta** (step 2), so
`isSuperDown()` does not inherit the asymmetry, and document that BK2 movies
carry no modifier column so all four read false under BK2 playback.

## Design

### `KeyChord` — landed, `9ecb0fb56`

```java
public record KeyChord(int keyCode, Set<Modifier> modifiers) {
    public enum Modifier { CTRL, SHIFT, ALT, META }
    public static KeyChord parse(Object configured);
    public boolean matchesModifiers(boolean shift, boolean ctrl, boolean alt, boolean meta);
    public String format();
    public boolean isBound();
}
```

Decisions taken:

- **Backward compatible for the forms that resolve unambiguously.** An integer,
  `"O"` and `"GLFW_KEY_O"` all parse to that key with an empty modifier set.
  Digits are the exception until step 0b lands; see the first Finding.
- **`matchesModifiers` is exact.** Declared modifiers must be held and the
  others released, so a plain `"O"` does not fire while Ctrl is down and steal a
  chord another binding has claimed. Note this only arbitrates between bindings
  that both route through `KeyChord` — it does nothing about hardcoded keys that
  read `isKeyPressed` directly (see Risks).
- **Aliases follow what players type**: `CONTROL`, `OPTION`, and
  `SUPER`/`CMD`/`COMMAND`/`WIN` for META, because GLFW, macOS and Windows each
  name that key differently.
- **`+` is a safe separator**: no GLFW key name contains one (the plus key is
  `KP_ADD` / `EQUAL`).
- **Unresolvable input yields `NO_KEY` from `parse`.** This does **not** match
  how bindings behaved before: `resolveInt` logs a warning and falls back to the
  registered *default*, returning `-1` only when the default is itself unbound
  (which is why the empty-defaulted `PLAYBACK_*` keys read as unbound). The
  earlier draft recorded this as a no-op; it is a divergence.
  **Decision: `getKeyChord` reconciles at the accessor, not in `parse`** — when
  the configured value yields `NO_KEY`, `getKeyChord` parses the registered
  default, exactly as `resolveInt` does, and logs the same warning shape. `parse`
  stays a pure function with no config knowledge.
- **Canonical format order** CTRL, SHIFT, ALT, META, so `format()` round-trips.

## Remaining work

Ordered by dependency. Each step is independently shippable and leaves the
build green.

### 0b. Correct the landed `KeyChord`

Two defects in `9ecb0fb56`, both described under Findings:

- `keyCode(String)`: resolve through `GlfwKeyNameResolver` **first**, fall back
  to `Integer.parseInt`, matching `resolveInt:183-202`.
- `parse`: return `NO_KEY` when `segments.length == 0`, so separator-only input
  cannot throw.

**Done when:** `parse("1")` is `GLFW_KEY_1` (49), `parse("79")` is `GLFW_KEY_O`,
`format()`/`parse` round-trips across `GLFW_KEY_0`..`GLFW_KEY_9` plus a
representative sample of the whole name table, and `"+"` / `"++"` join the
unbound-input cases in `TestKeyChord` instead of throwing.

### 1. Meta/Super support in `InputHandler`

Add `isSuperDown()` over `GLFW_KEY_LEFT_SUPER` / `GLFW_KEY_RIGHT_SUPER`.
Until this exists `"META+..."` parses but can never match.

`isAnyModifierDown()` must account for Super, or it will silently disagree with
chord matching. **That is not free:** it gates `isKeyPressedWithoutModifiers`,
which has ~30 call sites — `PlaybackDebugManager` (9 shortcuts),
`UserRecordingMenuState` (11 menu keys), `TestModeTracePicker` (9 keys) and
`GameLoop.isUnmodifiedDebugKeyPressed:1864`. Every one of those stops responding
while a Super/Cmd key is held, which on macOS is common.

That widens an existing hazard rather than creating one: `handleKeyEvent:68-76`
clears a key only on an observed `GLFW_RELEASE`, and the focus callback at
`Engine:495-502` only pauses/resumes — nothing zeroes `keys[]`. Super is the
standard window-switch modifier on Linux and Windows, so the release is
routinely delivered to another window and the key latches on forever, disabling
every call site above. The same latch already exists for Alt via Alt+Tab.

**Decision: include Super in the aggregates, and pair it with a focus-loss key
state clear** in the same step, so the latch is closed rather than widened.

**Done when:** Super is queryable; `isAnyModifierDown()` and
`isKeyPressedWithoutModifiers()` account for it; losing window focus zeroes
`keys`/`previousKeys`; and a regression test shows a latched modifier is dropped
on focus loss and one menu path (`UserRecordingMenuState` or
`TestModeTracePicker`) still responds afterwards.

### 2. Extend the logical-override surface to Alt and Meta

Its own step because it is a record change, not an `InputHandler` edit.
`debugShiftDown` / `debugControlDown` are components of two records:

| File | Change |
|---|---|
| `control/LogicalInputSnapshot.java` | 2 new components; canonical ctor used 3× in-file (`ofPlayers`, `withMenuPolicy`, `withDebugInput`); `withDebugInput` grows to 5 args |
| `debug/playback/Bk2FrameInput.java` | 2 new components; both convenience ctors absorb them as `false` |
| `debug/playback/RecordedInputSnapshots.java:32-35` | pass the two new fields through |
| `debug/playback/Bk2MovieLoader.java:162-166` | unchanged — uses the 8-arg convenience ctor |
| `game/rewind/LiveRewindInputSource.java:36-47` | supply `isAltDown()` / `isSuperDown()` |
| `control/InputHandler.java:173-192` | `isAltDown()` / `isSuperDown()` consult `logicalOverride` |
| `test/.../TestPlayerInputState.java:111`, `TestInputHandlerLogicalSnapshot.java:66`, `TestLiveRewindInputSource.java` | `withDebugInput` arity |

Six files name those components today; the two record *types* appear in 77
files, but almost all of those read components rather than call the canonical
constructor, so the arity change is contained to the table above. Confirm with a
compile rather than assuming.

BK2 movies have no Alt or Meta column, so the new fields are `false` by
construction under BK2 playback — same as Shift/Ctrl are today. That is the
documented limit, not a defect to fix here.

**Done when:** all four modifier queries consult `logicalOverride` identically,
the two records carry all four, live rewind records all four, and
`CONFIGURATION.md` states that BK2 playback supplies no modifier data so chords
requiring a modifier do not fire under it.

*Alternative if this cost is not worth paying now:* leave the surface at
Shift/Ctrl and document Alt/Meta chords as not playback-reproducible. Criterion
9 is satisfied either way, but the choice must be recorded, not left open.

### 3. `getKeyChord` accessor

```java
public KeyChord getKeyChord(SonicConfiguration binding);
```

on `SonicConfigurationService`. It must:

- read through `getConfigValue` (`:293-305`) so `sessionOverrides` and
  `transientResolved` still win;
- reuse `resolveInt`'s `DERIVED` fallback (`:171-176`) — `JUMP` and `P2_JUMP`
  are `DERIVED` (`ConfigCatalog:95,106`) and fall back to `P1_A`/`P2_A` when
  unset, so a `getKeyChord` built on `getConfigValue` alone returns `NO_KEY` for
  them;
- fall back to parsing the registered default when the configured value yields
  `NO_KEY`, matching `resolveInt:204-217` (see the Design decision above);
- clear any chord cache everywhere `intCache` is cleared — seven sites:
  `:372, 396, 401, 406, 508, 536, 554`.

`getInt(KEY)` keeps returning the bare key code so the other 50 bindings are
untouched. **That requires two edits, not zero.** Today a chorded string is
neither a name nor an integer, so `resolveInt` warns and returns the *default*'s
key code — and once step 4 makes the default itself `"SHIFT+O"`, the fallback
runs `resolveKeyCode("SHIFT+O")`, which is chord-blind, yields `-1`, and drops
into the "Defaulting to unbound" branch, returning `-1` on every cold cache. So:

- `resolveInt` (`:183-202`) must strip modifier segments before name/integer
  resolution for `ConfigType.KEY` entries;
- `resolveKeyCode` (`:853-869`) must do the same, since it resolves defaults.

**Done when:** both accessors agree on the key code for every form — including
digits, chords, `DERIVED` bindings, and values that fall back to their default —
and a converted and unconverted binding can coexist in one `config.yaml`.

### 4. Convert `capture.toggleKey`

Default becomes `"SHIFT+O"`. `Engine.handleLiveCaptureShortcut` reads the chord
and drops the hardcoded Shift. Keep the existing edge-triggering
(`liveCaptureChord.update`) — this changes *which* modifiers are required, not
when the shortcut fires.

The default lives in **two** places and both must change, or the behaviour is
unchanged for everyone:

- `putDefaultKey(CAPTURE_TOGGLE_KEY, GLFW_KEY_O)` — the Java default.
- `src/main/resources/config.yaml:172` — `toggleKey: O`. `loadConfig:101-114`
  loads this classpath resource as `config` when no user file exists, and
  `putDefault:719-727` only inserts when the key is *absent*, so on a fresh
  install this explicit `O` wins over the Java default.

`ConfigCatalog:266`'s description string is a third source — it is what
`ConfigYamlWriter` emits as the comment into every saved config, and it still
says "Shift+key toggles…". It must be reworded now that the Shift is in the
value.

**Existing installs need a migration.** `putDefault` never overwrites, and
`ConfigYamlWriter` emits every key in `emitOrder()`, so every user who has ever
launched the engine has a literal `capture.toggleKey: O` persisted. Changing the
default reaches none of them: their bare `"O"` wins, exact matching rejects the
held Shift, Shift+O stops working, and a stray `O` starts a recording. A
changelog note does not mitigate a shortcut silently changing meaning.

Add `ConfigMigrationService.migrateDeprecatedCaptureToggleKey(config)`, modelled
on `migrateDeprecatedDisplayColorProfileToggleKey:179-201`: rewrite to
`"SHIFT+O"` **only** when the persisted value still equals the superseded
default (`"O"` / `"GLFW_KEY_O"` / `"KEY_O"` / `79`). Wire it into the migration
block at `SonicConfigurationService:119-136` with the `configChanged = true`
flag.

**Decision on customised values:** a player who set `toggleKey: P` is left
alone. Their binding becomes a bare `P` chord — Shift is no longer required —
which is the same one-time behaviour change the migration spares default users,
and inferring `SHIFT+P` would silently rewrite a value the player chose. Call it
out in the changelog.

`CaptureConfigDefaultsTest` **must** be updated as part of this step, contrary to
the earlier draft's test plan:

- `:30` asserts the bundled YAML value is literally `"O"` — becomes `"SHIFT+O"`.
- `:21` asserts `getInt(CAPTURE_TOGGLE_KEY) == GLFW_KEY_O` — this one must keep
  passing **unchanged**, and is the assertion that proves the `resolveInt` edit
  in step 3 landed correctly.

**Done when:** a fresh install and a migrated existing install both toggle on
Shift+O with no user action; `"O"` alone toggles without Shift; `"CTRL+SHIFT+O"`
requires both; a `TestConfigMigrationService` case covers the rewrite and the
leave-alone; and the changelog records the customised-value behaviour change.

### 5. Documentation

`CONFIGURATION.md`: chord syntax, the accepted modifier aliases, the exactness
rule, how to bind the plus key itself (`EQUAL` or `KP_ADD`, since `+` is the
separator), and the BK2-playback limit from step 2. Bundled `config.yaml`:
syntax note by `capture.toggleKey`.

Both files list every key binding, so the per-binding support table must be
**three-state**, not two:

| State | Meaning | Example |
|---|---|---|
| Chord honoured | read via `getKeyChord`, exact matching | `capture.toggleKey` |
| Modifiers ignored | read via `getInt` + `isKeyDown`/`isKeyPressed`; a chord resolves to its bare key and the modifiers are dropped | most bindings |
| **Chord permanently dead** | read via `isKeyPressedWithoutModifiers`, which is `isKeyPressed(key) && !isAnyModifierDown()` — the modifier must be held to type the chord, and holding it blocks the shortcut | all 9 `PLAYBACK_*` keys, `GameLoop`'s debug shortcuts |

The third state is the one a user would file a bug about, and it is invisible
from the config file. `debug.playback.toggleKey: "CTRL+P"` would give `getInt` a
live key code and still never fire.

Also note the hardcoded non-config keys from the inventory, which will swallow a
keystroke regardless of what any binding says.

**Done when:** a reader can tell, per binding, which of the three states it is
in.

### 6. Optional — roll out to remaining bindings

Not mechanical. Per subsystem, and only where a call site does not already
implement its own modifier handling that would then be duplicated. Exclusions
and rules:

- **Bindings whose key *is* a modifier key cannot use exact matching as-is.**
  `putDefaultKey(P2_A, GLFW_KEY_RIGHT_SHIFT)` (`:602`),
  `LIVE_REWIND_HALF_SPEED_KEY = GLFW_KEY_LEFT_CONTROL` (`:650`) and
  `LIVE_REWIND_DOUBLE_SPEED_KEY = GLFW_KEY_LEFT_SHIFT` (`:651`) are all
  defaults today. `isShiftDown()` is true whenever either Shift is down, so a
  chord of `RIGHT_SHIFT` with no declared modifiers can never satisfy
  `matchesModifiers` — pressing player 2's jump makes its own chord fail. Either
  such bindings bypass exact matching, or `KeyChord` must exclude a held
  modifier that is the chord's own key.
- **Gameplay/movement bindings keep permissive matching** unless deliberately
  converted. They are read with `isKeyDown`/`isKeyPressed`, which do not require
  modifiers released; adopting exact matching silently kills movement while any
  modifier is held.
- **Any binding consumed via `isKeyPressedWithoutModifiers`** must be converted
  to `getKeyChord` + `matchesModifiers` at the same time it is advertised as
  chord-capable, or documented as single-key-only. Converting one without the
  other produces the dead-chord state above.
- `JUMP` / `P2_JUMP` need step 3's `DERIVED` fallback.

### 7. Optional — `RECORDING_RECORD_KEY`

The second configurable binding with a hardcoded Shift, at
`UserRecordingRuntimeControls:33,44` and `MasterTitleScreen:375`. Both sites
must be converted together, and `ConfigCatalog:327`'s prose description updated
the same way `CAPTURE_TOGGLE_KEY`'s is in step 4. The stop-on-plain-key rule
(`isKeyPressed(recordKey) && !isShiftDown()`) is a second, derived chord — decide
whether it becomes its own binding or stays "the record chord without its
modifiers".

## Acceptance criteria

1. Every form that parsed before parses to the same key with no modifiers,
   **including the ten number-row digit names**, which requires step 0b.
2. `"CTRL+SHIFT+O"`, `"META+LEFT_BRACKET"`, and case/whitespace/alias variants
   resolve to the same chord as their canonical form.
3. `format()` round-trips through `parse` for every chord, across the whole key
   name table rather than a hand-picked sample.
4. A plain binding does not fire while any modifier is held.
5. A chord fires only with exactly its modifiers.
6. Unresolvable input is unbound, never an exception — including separator-only
   input such as `"+"`.
7. Live recording still toggles on Shift+O with no user action, on a fresh
   install **and** on an existing install carrying a persisted `toggleKey: O`,
   the latter via the step-4 migration.
8. Meta chords match real Super presses.
9. Alt and Meta chords behave under trace playback as decided in step 2, and
   that decision — including the BK2 no-modifier-column limit — is documented.
10. A latched modifier does not survive window focus loss.
11. Full suite green; `capture.toggleKey` conversion carries a changelog entry
    covering both the visible-Shift change and the customised-value case.

## Risks

| Risk | Mitigation |
|---|---|
| An existing `toggleKey: "O"` starts firing without Shift | Step 4 migration rewrites the untouched default; a customised value is deliberately left alone and called out in the changelog |
| Plain bindings shadowing chords on the same key | `matchesModifiers` is exact — **but only between bindings that route through `KeyChord`**, which after step 4 is one binding |
| Hardcoded keys shadowing a chord | `DebugOverlayToggle.OBJECT_DEBUG` is `GLFW_KEY_O` and `DebugOverlayManager:58-65` fires it on a bare `isKeyPressed` with no modifier filter, so Shift+O toggles object debug *and* live capture whenever debug shortcuts are enabled. Decide in step 4: move the capture default off `O`, make `DebugOverlayToggle` dispatch modifier-aware, or document the overlap |
| Adding Super to `isAnyModifierDown` disables ~30 shortcuts while Cmd is held | Step 1 pairs it with the focus-loss clear and a menu regression test |
| Alt chords not reproducible under trace playback | Step 2 decision; blocking for advertising Alt |
| Partial rollout confusing users | Step 5's three-state per-binding table |

## Test plan

- `TestKeyChord` — 13 tests landed. Step 0b adds: digit precedence (`"1"`,
  `"CTRL+1"`, `"79"`), a full-table `format()`/`parse` round-trip, and `"+"` /
  `"++"` as unbound rather than throwing.
- New: `getKeyChord`/`getInt` agreement — chorded value, digit value, `DERIVED`
  binding, and a value that falls back to its default.
- New: `Engine` chord evaluation via the existing non-GL seam pattern —
  default Shift+O, unmodified `"O"`, and a Ctrl+Shift chord.
- New: `isSuperDown`, the aggregate modifier helpers, and the focus-loss clear.
- New: `TestConfigMigrationService` — `capture.toggleKey` migrated from each
  spelling of the old default, and left alone when customised.
- **Changed by step 4:** `CaptureConfigDefaultsTest:30` (bundled value becomes
  `"SHIFT+O"`). `:21` must keep passing unchanged and is the evidence that the
  step-3 `resolveInt` edit preserved `getInt`.
- Evidence for criterion 1 is `TestKeyboardInputMapper`,
  `TestConfigKeyNameResolution`, `TestBundledConfigResource` and
  `TestConfigYamlWriter` staying green untouched — not
  `CaptureConfigDefaultsTest`, which this work must edit.
