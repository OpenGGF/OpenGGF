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
same shape will recur for every shortcut that outgrows a single key.

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
| Bindings resolve as int / `"O"` / `"GLFW_KEY_O"` | `SonicConfigurationService.resolveKeyCode` (~:853) |
| Name↔code tables already exist both ways | `GlfwKeyNameResolver.resolve` / `.nameOf` |
| 51 `KEY`-typed config entries | `ConfigCatalog` |
| Only Shift/Ctrl/Alt are queryable; **no Super/Meta** | `InputHandler:173-192` |
| `capture.toggleKey` is the one hardcoded chord found | `Engine.handleLiveCaptureShortcut` (~:1938) |
| Default is the bare key | `putDefaultKey(CAPTURE_TOGGLE_KEY, GLFW_KEY_O)` |

### Finding: modifier queries are inconsistent about logical input

`isShiftDown()` and `isControlDown()` consult `logicalOverride`
(`debugShiftDown()` / `debugControlDown()`) so trace playback and rewind can
drive them deterministically. **`isAltDown()` does not** — it always reads live
hardware. Any chord using Alt would therefore not be reproducible under trace
playback, unlike the same chord using Ctrl or Shift.

This predates the feature and is out of scope to fix silently, but it must be
decided before Alt chords are advertised: either extend the logical-override
surface to Alt (and Meta), or document Alt/Meta chords as not
playback-reproducible. Extending it is preferred — the asymmetry is a latent
trap, not a design.

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

- **Backward compatible by construction.** An integer, `"O"` and `"GLFW_KEY_O"`
  all parse to that key with an empty modifier set, so every existing
  `config.yaml` keeps its meaning with no migration.
- **`matchesModifiers` is exact.** Declared modifiers must be held and the
  others released, so a plain `"O"` does not fire while Ctrl is down and steal a
  chord another binding has claimed. With 51 bindings, collisions are likely
  once chords are in use.
- **Aliases follow what players type**: `CONTROL`, `OPTION`, and
  `SUPER`/`CMD`/`COMMAND`/`WIN` for META, because GLFW, macOS and Windows each
  name that key differently.
- **`+` is a safe separator**: no GLFW key name contains one (the plus key is
  `KP_ADD` / `EQUAL`).
- **Unresolvable input is unbound, not fatal** — `NO_KEY`, matching how
  unresolvable bindings already behaved.
- **Canonical format order** CTRL, SHIFT, ALT, META, so `format()` round-trips.

## Remaining work

Ordered by dependency. Each step is independently shippable and leaves the
build green.

### 1. Meta/Super support in `InputHandler`

Add `isSuperDown()` over `GLFW_KEY_LEFT_SUPER` / `GLFW_KEY_RIGHT_SUPER`.
Until this exists `"META+..."` parses but can never match.

Decide the logical-override question above at the same time, since
`isSuperDown()` is new code and should not inherit the `isAltDown()` asymmetry.

Also revisit `isAnyModifierDown()` and `isKeyPressedWithoutModifiers()`, which
currently ignore Super and will silently disagree with chord matching.

**Done when:** Super is queryable, its logical-override behaviour matches
Shift/Ctrl, and the two aggregate helpers account for it.

### 2. `getKeyChord` accessor

```java
public KeyChord getKeyChord(SonicConfiguration binding);
```

on `SonicConfigurationService`, resolving through `KeyChord.parse`.

`getInt(KEY)` keeps returning the bare key code so the other 50 bindings are
untouched. A chorded value read through `getInt` must still return its key code
rather than `-1`, so a player who adds a modifier to a binding that has not been
converted yet gets the unmodified key rather than a dead binding.

**Done when:** both accessors agree on the key code for every form, and a
converted and unconverted binding can coexist in one `config.yaml`.

### 3. Convert `capture.toggleKey`

Default becomes `"SHIFT+O"`. `Engine.handleLiveCaptureShortcut` reads the chord
and drops the hardcoded Shift. Behaviour is identical out of the box; the
difference is that the Shift is now visible and changeable.

Keep the existing edge-triggering (`liveCaptureChord.update`) — this changes
*which* modifiers are required, not when the shortcut fires.

**Done when:** default behaviour is unchanged, `"O"` alone toggles without
Shift, `"CTRL+SHIFT+O"` requires both, and an existing config containing a bare
`toggleKey: "O"` still works — as an unmodified binding, which is a deliberate
behaviour change for that player and must be called out in the changelog.

### 4. Documentation

`CONFIGURATION.md`: chord syntax, the accepted modifier aliases, the exactness
rule, and which bindings accept chords today. Bundled `config.yaml`: syntax note
by `capture.toggleKey`. Both files list every key binding, so state plainly that
unconverted bindings ignore modifiers rather than implying repo-wide support.

**Done when:** a reader can tell, per binding, whether a chord will be honoured.

### 5. Optional — roll out to remaining bindings

Convert the other `KEY` entries to `getKeyChord`. Mechanical but broad; worth
doing per subsystem rather than in one sweep, and only where a call site does
not already implement its own modifier handling that would then be duplicated.

## Acceptance criteria

1. Every form that parsed before parses to the same key with no modifiers.
2. `"CTRL+SHIFT+O"`, `"META+LEFT_BRACKET"`, and case/whitespace/alias variants
   resolve to the same chord as their canonical form.
3. `format()` round-trips through `parse` for every chord.
4. A plain binding does not fire while any modifier is held.
5. A chord fires only with exactly its modifiers.
6. Unresolvable input is unbound, never an exception at startup.
7. Live recording still toggles on Shift+O with no config change.
8. Meta chords match real Super presses.
9. Alt and Meta chords behave under trace playback as decided in step 1, and
   that decision is documented.
10. Full suite green; `capture.toggleKey` conversion carries a changelog entry.

## Risks

| Risk | Mitigation |
|---|---|
| A player's bare `toggleKey: "O"` starts firing without Shift | Intended, but a behaviour change on upgrade — call out in the changelog, and note that `config.yaml.example` shows the new default |
| Plain bindings shadowing chords on the same key | `matchesModifiers` is exact; covered by test |
| Alt chords not reproducible under trace playback | Step 1 decision; blocking for advertising Alt |
| Partial rollout confusing users | Step 4 documents per-binding support explicitly |

## Test plan

- `TestKeyChord` — 13 tests, landed: back-compat forms, aliases, ordering,
  round-trip, exact matching, unbound handling, value equality.
- New: `getKeyChord`/`getInt` agreement, including a chorded value read through
  `getInt`.
- New: `Engine` chord evaluation via the existing non-GL seam pattern —
  default Shift+O, unmodified `"O"`, and a Ctrl+Shift chord.
- New: `isSuperDown` and the aggregate modifier helpers.
- Existing `TestKeyboardInputMapper` and the config defaults tests must stay
  green untouched; that they do not need changing is the evidence for
  criterion 1.
