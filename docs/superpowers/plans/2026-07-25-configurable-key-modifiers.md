# Configurable Key Modifiers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move a shortcut's modifiers out of its call site and into its
`config.yaml` binding, so `capture.toggleKey: "SHIFT+O"` is the whole truth about
what toggles live recording and a player can write `CTRL+SHIFT+O` or drop the
Shift entirely.

**Architecture:** `KeyChord` is the parse/match value type. `SonicConfigurationService`
gains one chord-returning accessor beside the existing `getInt`, and both accessors
agree on the key code for every value form. `InputHandler` gains the Super query and
a focus-loss key-state clear so an exactly-matched chord is reachable and a latched
modifier cannot disable every unmodified shortcut. `capture.toggleKey` is the one
binding converted; every other binding keeps reading `getInt` and is unaffected.

**Tech Stack:** Java 21, JUnit 5/Jupiter, Maven, LWJGL GLFW, SnakeYAML/Jackson
config pipeline.

## Global Constraints

- Authoritative design: `docs/superpowers/specs/2026-07-25-configurable-key-modifiers-design.md`.
- Work only in the worktree `/home/farrell/code/projects/OpenGGF-key-modifiers`
  on `feature/ai-key-modifiers`. Base commit for this plan: `0fdc873ec`.
- Step 0 of the spec is landed as `9ecb0fb56` (`KeyChord` + `TestKeyChord`, 13 tests).
  Do not redo it; Task 1 corrects it.
- JUnit 5 / Jupiter only. No `org.junit.*` (JUnit 4) imports, runners, or rules.
- Every non-merge commit carries the full trailer block: `Changelog`, `Guide`,
  `Known-Discrepancies`, `S3K-Known-Discrepancies`, `Agent-Docs`,
  `Configuration-Docs`, `Skills` — each starting `updated` or `n/a`.
- A `feat`/`fix`/`perf` commit touching `src/main/` must either set
  `Changelog: updated` with `CHANGELOG.md` staged, or justify with
  `Changelog: n/a: <reason>`. A bare `n/a` is rejected by `commit-msg` and CI.
- Never `--no-verify`.
- `Configuration-Docs` maps to `CONFIGURATION.md` exactly; `Guide` maps to the
  `docs/guide/` prefix. `docs/superpowers/` maps to no trailer, so a plan/spec-only
  commit is all `n/a`.
- Use `-Dmse=off` on every Maven command in this plan so the output is readable.
- Test selectors use fully-qualified class names (verified working:
  `mvn -Dmse=off -Dtest=com.openggf.configuration.TestKeyChord test`). Package globs
  need `**`, not `*`.
- A full `mvn test` regenerates `docs/rewind/real-gaps.md`. Restore it with
  `git checkout -- docs/rewind/real-gaps.md` before committing. A focused
  `-Dtest=...` run does not touch it (verified).
- Tests must not leak temp files. Use `@TempDir` or `com.openggf.tests.TestTempFiles`.
  `TestNoLeakedTemporaryFiles` enforces this. Every test this plan adds either uses
  `@TempDir` (via `SonicConfigurationService.createStandalone(tempDir)`) or creates
  no files at all.
- `TestArchitecturalSourceGuard` holds a release-critical class-size ratchet.
  Neither `SonicConfigurationService.java` nor `InputHandler.java` is in
  `RELEASE_CRITICAL_CLASS_EFFECTIVE_SOURCE_LINE_BUDGETS`. `Engine.java` has
  `MethodBudget("init", 181)` and `init()` currently spans lines 431–607 = **177
  lines**, so Task 2's one-line focus-callback edit leaves 3 lines of headroom.
  If a budget is exceeded, extract a collaborator — do not raise the ratchet.
- `TestArchitecturalSourceGuard.engineLiveCaptureOrderingStaysAtThePresentationBoundary`
  asserts the literal string `handleLiveCaptureShortcut();` precedes
  `updateDisplayShaderInput()` in `Engine.java`. Keep that method name and call site.
- Trace fixes and behaviour gates must not add zone/route/frame carve-outs. Nothing
  in this plan is allowed to branch on game, zone, or trace identity.
- Before each task, `git status --short --untracked-files=no` must be empty.
- Each task is independently shippable and leaves the build green.
- Honesty: exact commands, exact counts. NOT RUN is never a pass. Never weaken,
  delete, or `@Disabled` an assertion to make a gate green.

---

## File and ownership map

| File | Responsibility |
| --- | --- |
| `configuration/KeyChord.java` | Parse/format/match a binding with modifiers; name-table-first key resolution |
| `configuration/GlfwKeyNameResolver.java` | Unchanged. Name↔code tables `KeyChord` and the config service both resolve through |
| `configuration/SonicConfigurationService.java` | `getKeyChord` accessor; chord-tolerant `resolveInt`/`resolveKeyCode`; chord cache invalidation; `capture.toggleKey` default |
| `configuration/ConfigMigrationService.java` | One-time rewrite of a persisted default `capture.toggleKey` to `SHIFT+O` |
| `configuration/ConfigCatalog.java` | `CAPTURE_TOGGLE_KEY` description text emitted as the saved-config comment |
| `configuration/ConfigYamlWriter.java` | Canonical chord rendering for `KEY` values on save |
| `src/main/resources/config.yaml` | Bundled default `capture.toggleKey` and its syntax comment |
| `control/InputHandler.java` | `isSuperDown()`, Super in the modifier aggregates, focus-loss key state clear, logical-override symmetry for Alt/Super |
| `control/LogicalInputSnapshot.java` | Carries all four debug modifier columns |
| `debug/playback/Bk2FrameInput.java` | Carries all four debug modifier columns; convenience ctors absorb the new two as `false` |
| `debug/playback/RecordedInputSnapshots.java` | Passes all four columns through to the snapshot |
| `game/rewind/LiveRewindInputSource.java` | Records all four live modifier states per rewind frame |
| `capture/LiveCaptureChord.java` | Rising-edge detector over a `KeyChord`, no hardcoded modifier |
| `Engine.java` | Reads `capture.toggleKey` as a chord; focus callback clears key state |
| `debug/DebugOverlayManager.java` | Debug overlay toggles stop firing while a modifier is held (resolves the Shift+O overlap) |
| `CHANGELOG.md` | User-visible chord syntax, the Shift+O migration, and the customised-value behaviour change |
| `CONFIGURATION.md` | Chord syntax, aliases, exactness rule, plus-key escape, BK2 limit, three-state per-binding support table |
| `docs/guide/playing/configuration.md` | Player-facing chord syntax |

## Interface ledger

All later tasks use these exact signatures.

```java
// com.openggf.configuration — corrected in Task 1, otherwise as landed in 9ecb0fb56
public record KeyChord(int keyCode, Set<Modifier> modifiers) {
    public enum Modifier { CTRL, SHIFT, ALT, META }
    public static final int NO_KEY = -1;
    public static KeyChord of(int keyCode, Modifier... modifiers);
    public static KeyChord parse(Object configured);
    public boolean isBound();
    public boolean matchesModifiers(boolean shift, boolean ctrl, boolean alt, boolean meta);
    public String format();
}

// com.openggf.control.InputHandler — Task 2
public boolean isSuperDown();
/** Zeroes keys[] and previousKeys[] so a modifier whose release went to another
 *  window cannot latch. Mouse state is untouched. */
public void clearKeyState();
// isAnyModifierDown() gains || isSuperDown()
// isAltDown() / isSuperDown() consult logicalOverride — Task 3

// com.openggf.control.LogicalInputSnapshot — Task 3
public record LogicalInputSnapshot(
        PlayerInputState player1, PlayerInputState player2,
        boolean menuUp, boolean menuDown, boolean menuLeft, boolean menuRight,
        boolean menuAccept, boolean menuBack, boolean menuStart,
        boolean anyActionPressed, boolean debugModeTogglePressed,
        boolean debugShiftDown, boolean debugControlDown,
        boolean debugAltDown, boolean debugSuperDown) {
    public LogicalInputSnapshot withDebugInput(boolean modeTogglePressed,
            boolean shiftDown, boolean controlDown,
            boolean altDown, boolean superDown);
}

// com.openggf.debug.playback.Bk2FrameInput — Task 3
public record Bk2FrameInput(
        int frameIndex, int p1InputMask, int p1ActionMask, boolean p1StartPressed,
        int p2InputMask, int p2ActionMask, boolean p2StartPressed,
        boolean debugModeTogglePressed, boolean debugShiftDown, boolean debugControlDown,
        boolean debugAltDown, boolean debugSuperDown, String rawLine) {
    // both existing convenience constructors keep their current arity and pass false
}

// com.openggf.configuration.SonicConfigurationService — Task 4
/** Reads a KEY binding as a chord. Honours session overrides and the transient
 *  overlay, the DERIVED fallback for JUMP/P2_JUMP, and falls back to the
 *  registered default when the configured value is unresolvable — exactly as
 *  getInt does. Returns an unbound chord when the default is itself unbound. */
public KeyChord getKeyChord(SonicConfiguration binding);

// com.openggf.configuration.ConfigMigrationService — Task 5
/** Rewrites capture.toggleKey to "SHIFT+O" only when the persisted value is still
 *  the superseded bare-O default. Returns true when the map was modified. */
public boolean migrateDeprecatedCaptureToggleKey(Map<String, Object> config);

// com.openggf.capture.LiveCaptureChord — Task 5
/** Rising edge of "the chord is completely satisfied". An unbound chord never
 *  fires. Replaces the 4-arg form whose Shift requirement was hardcoded. */
public boolean update(KeyChord chord, boolean keyDown, boolean shiftDown,
                      boolean controlDown, boolean altDown, boolean superDown);
```

---

### Task 1: Correct KeyChord's key resolution order and separator-only input

Two defects landed in `9ecb0fb56`. Both are verified against the source, not assumed.

`KeyChord.keyCode(String)` (`KeyChord.java:88-99`) calls `Integer.parseInt` first and
only falls through to `GlfwKeyNameResolver`. `SonicConfigurationService.resolveInt`
(`:183-190`) does the opposite for `ConfigType.KEY` entries, with an explicit comment
saying `"1"` must mean the number-row key. So `KeyChord.parse("1").keyCode()` is `1`
while `getInt` on the same value is `49`; `1` is below `GLFW_KEY_SPACE` (32) so
`isBound()` returns true and the binding is silently dead. `format()` also fails to
round-trip for `GLFW_KEY_0`..`GLFW_KEY_9`, because `nameOf(49)` is `"1"`.

`parse` indexes `segments[segments.length - 1]` (`:74`). Verified with a JDK probe:
`"+".split("\\+")` , `"++"` and `"+++"` each yield a **zero-length** array, so the
index is `-1`. `"CTRL+"` yields length 1 and `"+O"` length 2, both safe. `"+"` is
exactly what a player writes to bind the plus key once `+` is documented as the
separator, and after Task 5 this parse runs on the render path inside
`Engine.handleLiveCaptureShortcut` — an uncaught per-frame throw, not a startup warning.

Also fix the broken `{@link #matches}` in the class javadoc (`:21`); the method is
`matchesModifiers`.

**Files:**
- Modify: `src/main/java/com/openggf/configuration/KeyChord.java`
- Modify: `src/test/java/com/openggf/configuration/TestKeyChord.java`

**Interfaces:**
- Consumes: `GlfwKeyNameResolver.resolve(String)` / `.nameOf(int)`.
- Produces: no signature change. `KeyChord.parse` now resolves the key segment
  name-table-first and integer-second, and returns `NO_KEY` for separator-only input.

- [ ] **Step 1: Write the failing digit-precedence, full-table round-trip, and separator-only tests**

Add to `TestKeyChord`:

```java
/**
 * getInt resolves a KEY value through the name table first so "1" means the
 * number-row key, not raw code 1. KeyChord must agree or a digit binding is
 * silently dead: 1 is below GLFW_KEY_SPACE (32), so isBound() would be true.
 */
@Test
void aDigitBindingMeansTheNumberRowKeyNotTheRawKeyCode() {
    assertEquals(GLFW_KEY_1, KeyChord.parse("1").keyCode());
    assertEquals(KeyChord.of(GLFW_KEY_1, CTRL), KeyChord.parse("CTRL+1"));
}

@Test
void aNumericStringWithNoNameStillResolvesAsARawKeyCode() {
    assertEquals(GLFW_KEY_O, KeyChord.parse("79").keyCode());
}

@Test
void everyNameInTheTableFormatsBackToAnEqualChord() {
    for (int keyCode = 0; keyCode <= GLFW_KEY_LAST; keyCode++) {
        String name = GlfwKeyNameResolver.nameOf(keyCode);
        if (GlfwKeyNameResolver.resolve(name).isEmpty()) {
            continue; // code has no GLFW_KEY_* constant
        }
        KeyChord plain = KeyChord.parse(name);
        assertEquals(keyCode, plain.keyCode(), name);
        assertEquals(plain, KeyChord.parse(plain.format()), name);

        KeyChord chorded = KeyChord.of(keyCode, CTRL, SHIFT, ALT, META);
        assertEquals(chorded, KeyChord.parse(chorded.format()), name);
    }
}

/**
 * "+" is what a player writes to bind the plus key once '+' is the documented
 * separator. text.split("\\+") returns a zero-length array for it, so the
 * last-segment index was -1. After Task 5 this parse runs per frame on the
 * render path, where a throw is not recoverable.
 */
@ParameterizedTest
@ValueSource(strings = {"+", "++", "+++"})
void separatorOnlyInputIsUnboundRatherThanThrowing(String configured) {
    KeyChord chord = KeyChord.parse(configured);

    assertFalse(chord.isBound(), configured);
    assertEquals(KeyChord.NO_KEY, chord.keyCode(), configured);
}
```

Add the imports `GLFW_KEY_1`, `GLFW_KEY_LAST`.

- [ ] **Step 2: Run the focused test and verify RED**

```bash
mvn -Dmse=off -Dtest=com.openggf.configuration.TestKeyChord test
```

Expected RED: `aDigitBindingMeansTheNumberRowKeyNotTheRawKeyCode` fails with
`expected: <49> but was: <1>`; `separatorOnlyInputIsUnboundRatherThanThrowing`
errors with `ArrayIndexOutOfBoundsException: Index -1 out of bounds for length 0`;
`everyNameInTheTableFormatsBackToAnEqualChord` fails on the first digit name.
The 13 landed tests still pass.

- [ ] **Step 3: Correct `keyCode` and guard `parse`**

In `keyCode(String)`, resolve through `GlfwKeyNameResolver.resolve(name)` first and
fall back to `Integer.parseInt` only when the name is unknown, mirroring
`SonicConfigurationService.resolveInt:183-202`. Keep `NO_KEY` for an empty segment
and for an unparseable one.

In `parse`, return an unbound chord immediately when `segments.length == 0`, before
the modifier loop. Do not change the `Number`, `null`, or empty-string branches.

Fix the javadoc link. No other behaviour changes.

- [ ] **Step 4: Run the focused test and the untouched key-resolution regressions**

```bash
mvn -Dmse=off -Dtest=com.openggf.configuration.TestKeyChord,com.openggf.configuration.TestGlfwKeyNameResolver,com.openggf.configuration.TestConfigKeyNameResolution test
```

Expected: all pass. `TestConfigKeyNameResolution` and `TestGlfwKeyNameResolver` are
unmodified evidence that the config service's own resolution is untouched.

- [ ] **Step 5: Commit exact files**

```bash
git add src/main/java/com/openggf/configuration/KeyChord.java \
  src/test/java/com/openggf/configuration/TestKeyChord.java
git commit -m "fix(config): resolve chord keys by name before number

KeyChord.parse read its key segment as an integer first, so '1' meant raw
key code 1 while getInt on the same config value meant the number-row key
(49). 1 is below the lowest GLFW key code, so isBound() was true and the
binding was silently dead rather than reported. format() could not round
trip any of GLFW_KEY_0..GLFW_KEY_9 for the same reason. The name table now
wins, matching resolveInt.

parse also indexed the last segment of text.split(\"\\\\+\"), which is a
zero-length array for '+', '++' and '+++' -- so binding the plus key threw
rather than reporting itself unbound. Separator-only input is now unbound.
That matters because the conversion two commits from now parses on the
render path every frame, where a throw is not a startup warning.

Changelog: n/a: KeyChord still has no consumer, so no configuration behaves
differently yet; the capture.toggleKey conversion carries the user-visible entry.
Guide: n/a
Known-Discrepancies: n/a
S3K-Known-Discrepancies: n/a
Agent-Docs: n/a
Configuration-Docs: n/a
Skills: n/a"
```

---

### Task 2: Add Super support and close the latched-modifier hole

`InputHandler` exposes Shift/Ctrl/Alt only (`:173-192`), so `"META+..."` parses but
can never match. Adding `isSuperDown()` alone is not enough: `isAnyModifierDown()`
gates `isKeyPressedWithoutModifiers`, which has 31 direct call sites in `src/main` —
`UserRecordingMenuState` (12 menu keys), `PlaybackDebugManager` (9 `PLAYBACK_*`
shortcuts), `TestModeTracePicker` (9 keys), and
`GameLoop.isUnmodifiedDebugKeyPressed:1864`, which itself fans out to 45 further call
sites. Every one stops responding while Super is held.

That is not a new hazard, it is a widened one. `handleKeyEvent:68-76` clears a key
only on an observed `GLFW_RELEASE`, and the focus callback (`Engine.java:494-503`)
only pauses and resumes — nothing zeroes `keys[]`. Super is the standard
window-switch modifier on Linux and Windows, so its release is routinely delivered
to another window and the key latches forever. Alt already latches this way via
Alt+Tab. Add Super to the aggregates **and** clear key state on focus loss in the
same commit, so the latch is closed rather than widened.

**Files:**
- Modify: `src/main/java/com/openggf/control/InputHandler.java`
- Modify: `src/main/java/com/openggf/Engine.java`
- Modify: `src/test/java/com/openggf/tests/TestInputHandler.java`
- Modify: `src/test/java/com/openggf/recording/menu/TestUserRecordingMenu.java`
- Modify: `CHANGELOG.md`

**Interfaces:**
- Consumes: `GLFW_KEY_LEFT_SUPER`, `GLFW_KEY_RIGHT_SUPER`.
- Produces: `InputHandler.isSuperDown()`, `InputHandler.clearKeyState()`;
  `isAnyModifierDown()` accounts for Super.

- [ ] **Step 1: Write the failing Super, aggregate, and focus-loss tests**

Add to `TestInputHandler`:

```java
@Test
public void testSuperIsAModifierLikeShiftControlAndAlt() {
    InputHandler handler = new InputHandler();

    handler.handleKeyEvent(GLFW_KEY_LEFT_SUPER, GLFW_PRESS);

    assertTrue(handler.isSuperDown());
    assertTrue(handler.isAnyModifierDown());
    assertFalse(handler.isKeyPressedWithoutModifiers(GLFW_KEY_B));
}

@Test
public void testRightSuperCountsAsSuper() {
    InputHandler handler = new InputHandler();

    handler.handleKeyEvent(GLFW_KEY_RIGHT_SUPER, GLFW_PRESS);

    assertTrue(handler.isSuperDown());
}

/**
 * Super is the window-switch modifier on Linux and Windows, so its GLFW_RELEASE
 * is routinely delivered to whichever window took focus. Without a focus-loss
 * clear the key latches and every isKeyPressedWithoutModifiers call site stays
 * dead for the rest of the process.
 */
@Test
public void testFocusLossClearsALatchedModifierAndItsHeldKeys() {
    InputHandler handler = new InputHandler();
    handler.handleKeyEvent(GLFW_KEY_LEFT_SUPER, GLFW_PRESS);
    handler.handleKeyEvent(GLFW_KEY_B, GLFW_PRESS);

    handler.clearKeyState();

    assertFalse(handler.isSuperDown());
    assertFalse(handler.isAnyModifierDown());
    assertFalse(handler.isKeyDown(GLFW_KEY_B));
    assertFalse(handler.isKeyPressed(GLFW_KEY_B), "no stale rising edge survives the clear");
}

@Test
public void testAKeyPressedAfterFocusLossStillRegisters() {
    InputHandler handler = new InputHandler();
    handler.handleKeyEvent(GLFW_KEY_LEFT_SUPER, GLFW_PRESS);
    handler.clearKeyState();

    handler.handleKeyEvent(GLFW_KEY_B, GLFW_PRESS);

    assertTrue(handler.isKeyPressedWithoutModifiers(GLFW_KEY_B));
}
```

Add to `TestUserRecordingMenu` — one real menu path, using the existing `entry(...)`
helper, proving the ~30 affected shortcuts recover:

```java
@Test
void aLatchedSuperKeyStopsTheMenuUntilFocusLossClearsIt() {
    UserRecordingMenuState state =
            new UserRecordingMenuState("s2", List.of(entry("s2", 60), entry("s2", 120)));
    InputHandler input = new InputHandler();
    input.handleKeyEvent(GLFW_KEY_LEFT_SUPER, GLFW_PRESS);
    input.handleKeyEvent(GLFW_KEY_DOWN, GLFW_PRESS);

    state.update(input);
    assertEquals(0, state.cursor(), "held Super suppresses the unmodified menu key");

    input.clearKeyState();
    input.handleKeyEvent(GLFW_KEY_DOWN, GLFW_PRESS);
    state.update(input);

    assertEquals(1, state.cursor(), "the menu responds again after focus loss");
}
```

- [ ] **Step 2: Run the focused tests and verify RED**

```bash
mvn -Dmse=off -Dtest=com.openggf.tests.TestInputHandler,com.openggf.recording.menu.TestUserRecordingMenu test
```

Expected RED: compilation fails —
`cannot find symbol: method isSuperDown()` and
`cannot find symbol: method clearKeyState()` in `InputHandler`.

- [ ] **Step 3: Implement Super, the aggregate, and the clear**

In `InputHandler`:

```java
public boolean isSuperDown() {
    return isKeyDown(GLFW_KEY_LEFT_SUPER) || isKeyDown(GLFW_KEY_RIGHT_SUPER);
}

public boolean isAnyModifierDown() {
    return isShiftDown() || isControlDown() || isAltDown() || isSuperDown();
}

/**
 * Drops all key state. A key is otherwise cleared only by an observed
 * GLFW_RELEASE, and the release for a window-switch modifier goes to the
 * window that took focus.
 */
public void clearKeyState() {
    java.util.Arrays.fill(keys, false);
    java.util.Arrays.fill(previousKeys, false);
}
```

`previousKeys` must be cleared too, or the first press after the clear is not seen
as a rising edge by `isRawKeyPressed`. Do not touch mouse state — a focus change
does not strand a mouse button the way it strands a modifier.

In `Engine`, inside the existing `glfwSetWindowFocusCallback` (`:494-503`), add one
line to the not-focused branch:

```java
} else {
    paused = true;
    gameLoop.pause();
    if (inputHandler != null) {
        inputHandler.clearKeyState();
    }
}
```

`init()` is 177 lines against a budget of 181, so this fits. Confirm with the guard
in Step 4 rather than assuming.

- [ ] **Step 4: Run the focused tests plus the guard and the affected shortcut suites**

```bash
mvn -Dmse=off -Dtest=com.openggf.tests.TestInputHandler,com.openggf.recording.menu.TestUserRecordingMenu,com.openggf.tests.TestArchitecturalSourceGuard,com.openggf.control.TestInputHandlerLogicalSnapshot,com.openggf.control.TestPlayerInputState test
```

Expected: all pass, including
`rootDispatchMethodsDoNotGrowBeyondCurrentBudgets`. If `Engine#init` now exceeds
181 lines, extract the focus/iconify callbacks into a private method rather than
raising the ratchet.

- [ ] **Step 5: Update changelog and commit exact files**

Add under `## Unreleased` in `CHANGELOG.md`:

```markdown
- Fix: keyboard shortcuts no longer stop working after switching windows with the Super/Command key. A key was forgotten only when its release arrived, and the release for the window-switch modifier goes to whichever window took focus — so the modifier stayed held forever and every shortcut that requires no modifier held (the playback controls, the recording menu, the trace picker, and the debug keys) silently stopped responding. Losing window focus now clears held keys. The Super/Command key is also now recognised as a modifier, which is what lets a binding ask for it.
```

```bash
git add CHANGELOG.md \
  src/main/java/com/openggf/control/InputHandler.java \
  src/main/java/com/openggf/Engine.java \
  src/test/java/com/openggf/tests/TestInputHandler.java \
  src/test/java/com/openggf/recording/menu/TestUserRecordingMenu.java
git commit -m "fix(input): drop held keys when the window loses focus

Adds isSuperDown() so a binding can ask for the Super/Command key, and
counts it in isAnyModifierDown() so chord matching and
isKeyPressedWithoutModifiers cannot disagree about what is held.

That widens an existing hole, so it closes it in the same change. A key
was cleared only on an observed GLFW_RELEASE and the focus callback only
paused, so a modifier used to switch windows -- Super on Linux and
Windows, Alt via Alt+Tab -- never saw its release and latched. With Super
in the aggregate that would have disabled all 31 isKeyPressedWithoutModifiers
call sites: the twelve recording-menu keys, the nine playback shortcuts,
the nine trace-picker keys, and GameLoop's unmodified debug keys -- which
alone fan out to 45 shortcuts.

Changelog: updated
Guide: n/a
Known-Discrepancies: n/a
S3K-Known-Discrepancies: n/a
Agent-Docs: n/a
Configuration-Docs: n/a
Skills: n/a"
```

---

### Task 3: Extend the logical-override surface to Alt and Super

`isShiftDown()` and `isControlDown()` consult `logicalOverride` so rewind and
playback drive them deterministically. `isAltDown()` does not — it always reads live
hardware — and the `isSuperDown()` added in Task 2 inherits that asymmetry. An Alt or
Meta chord would therefore not be reproducible under playback while the same chord on
Ctrl would be.

This is a record change, not an `InputHandler` edit, which is why it is its own task.
Verified blast radius — `debugShiftDown`/`debugControlDown` are named in exactly six
files outside their declaring record:

| File | Change |
| --- | --- |
| `control/LogicalInputSnapshot.java` | 2 new components; the canonical ctor is used 3× in-file (`ofPlayers`, `withMenuPolicy`, `withDebugInput`); `withDebugInput` goes 3 args → 5 |
| `debug/playback/Bk2FrameInput.java` | 2 new components; both convenience ctors absorb them as `false` |
| `debug/playback/RecordedInputSnapshots.java:32-35` | pass the two new fields through; its inline neutral `Bk2FrameInput` uses the 8-arg convenience ctor and is unaffected |
| `game/rewind/LiveRewindInputSource.java:36-47,108-111` | canonical ctor goes 11 args → 13; supply `isAltDown()`/`isSuperDown()` and two more `false` in `neutralFrameInput` |
| `control/InputHandler.java:186-192` | `isAltDown()`/`isSuperDown()` consult `logicalOverride` |
| `test/control/TestPlayerInputState.java:111`, `test/control/TestInputHandlerLogicalSnapshot.java:66` | `withDebugInput` arity |

`debug/playback/Bk2MovieLoader.java:162-166` builds frames with the 8-arg convenience
constructor and is unchanged — which is also why BK2 movies supply `false` for all
four modifiers. That is the documented limit recorded in Task 6, not a defect fixed
here. The two record *types* appear in 77 files, but the rest read components rather
than calling a canonical constructor. Confirm with a compile, not with this table.

**Files:**
- Modify: `src/main/java/com/openggf/control/LogicalInputSnapshot.java`
- Modify: `src/main/java/com/openggf/control/InputHandler.java`
- Modify: `src/main/java/com/openggf/debug/playback/Bk2FrameInput.java`
- Modify: `src/main/java/com/openggf/debug/playback/RecordedInputSnapshots.java`
- Modify: `src/main/java/com/openggf/game/rewind/LiveRewindInputSource.java`
- Modify: `src/test/java/com/openggf/control/TestPlayerInputState.java`
- Modify: `src/test/java/com/openggf/control/TestInputHandlerLogicalSnapshot.java`
- Modify: `src/test/java/com/openggf/game/rewind/TestLiveRewindInputSource.java`
- Modify: `CHANGELOG.md`

**Interfaces:**
- Consumes: Task 2's `InputHandler.isSuperDown()`.
- Produces: the 15-component `LogicalInputSnapshot`, the 13-component
  `Bk2FrameInput`, and the 5-arg `withDebugInput` from the ledger.

- [ ] **Step 1: Write the failing symmetry tests**

In `TestInputHandlerLogicalSnapshot`, change the existing `withDebugInput(true, true, true)`
at `:66` to `withDebugInput(true, true, true, true, true)` and add:

```java
/**
 * All four modifier queries must answer from the same source. Shift and Ctrl
 * already consulted the logical override; Alt read live hardware, so an Alt
 * chord was not reproducible under playback while the same chord on Ctrl was.
 */
@Test
void allFourModifierQueriesAnswerFromTheLogicalOverride() {
    InputHandler input = new InputHandler();
    input.setLogicalOverride(LogicalInputSnapshot.neutral()
            .withDebugInput(false, true, true, true, true));

    assertTrue(input.isShiftDown());
    assertTrue(input.isControlDown());
    assertTrue(input.isAltDown());
    assertTrue(input.isSuperDown());
}

@Test
void aLogicalOverrideHidesLiveModifierHardware() {
    InputHandler input = new InputHandler();
    input.handleKeyEvent(GLFW_KEY_LEFT_ALT, GLFW_PRESS);
    input.handleKeyEvent(GLFW_KEY_LEFT_SUPER, GLFW_PRESS);

    input.setLogicalOverride(LogicalInputSnapshot.neutral()
            .withDebugInput(false, false, false, false, false));

    assertFalse(input.isAltDown());
    assertFalse(input.isSuperDown());

    input.clearLogicalOverride();

    assertTrue(input.isAltDown());
    assertTrue(input.isSuperDown());
}
```

In `TestPlayerInputState`, update `:111` to the 5-arg call and add
`assertTrue(snapshot.debugAltDown())` / `assertTrue(snapshot.debugSuperDown())`
beside the existing `:117-118` assertions, plus the matching `assertFalse` pair
beside `:101-102`.

In `TestLiveRewindInputSource`, beside the existing `:122-123` and `:130-131`
assertions, assert `debugAltDown()`/`debugSuperDown()` for a frame appended while
Alt and Super are held.

- [ ] **Step 2: Run the focused tests and verify RED**

```bash
mvn -Dmse=off -Dtest=com.openggf.control.TestInputHandlerLogicalSnapshot,com.openggf.control.TestPlayerInputState,com.openggf.game.rewind.TestLiveRewindInputSource test
```

Expected RED: compilation fails —
`method withDebugInput in record LogicalInputSnapshot cannot be applied to given types; required: boolean,boolean,boolean found: boolean,boolean,boolean,boolean,boolean`
and `cannot find symbol: method debugAltDown()`.

- [ ] **Step 3: Widen the two records and the three producers**

Add `debugAltDown` and `debugSuperDown` after `debugControlDown` in
`LogicalInputSnapshot`, pass `false, false` from `ofPlayers`, thread the existing
values through `withMenuPolicy`, and grow `withDebugInput` to 5 parameters.

Add the same two components to `Bk2FrameInput` after `debugControlDown` and before
`rawLine`, with javadoc naming what they are. Both existing convenience constructors
keep their current arity and pass `false` for the new fields, so `Bk2MovieLoader` and
`RecordedInputSnapshots`' neutral frame do not change.

`RecordedInputSnapshots.fromBk2` passes `current.debugAltDown()` and
`current.debugSuperDown()` into the 5-arg `withDebugInput`.

`LiveRewindInputSource.appendFrame` supplies `input.isAltDown()` and
`input.isSuperDown()`; `neutralFrameInput` gains two more `false`.

`InputHandler.isAltDown()` and `isSuperDown()` take the same shape as
`isShiftDown()`:

```java
public boolean isAltDown() {
    if (logicalOverride != null) {
        return logicalOverride.debugAltDown();
    }
    return isKeyDown(GLFW_KEY_LEFT_ALT) || isKeyDown(GLFW_KEY_RIGHT_ALT);
}
```

- [ ] **Step 4: Compile-check the full blast radius, then run the affected suites**

```bash
mvn -Dmse=off -DskipTests package
```

Expected: `BUILD SUCCESS`. Any other caller of a canonical constructor surfaces here
as a compile error; fix it rather than adding a lossy default constructor.

```bash
mvn -Dmse=off -Dtest=com.openggf.control.TestInputHandlerLogicalSnapshot,com.openggf.control.TestPlayerInputState,com.openggf.game.rewind.TestLiveRewindInputSource,com.openggf.tests.TestInputHandler test
```

Expected: all pass.

- [ ] **Step 5: Update changelog and commit exact files**

Add under `## Unreleased`:

```markdown
- Fix: rewind and movie playback now reproduce the Alt and Super/Command keys the same way they already reproduced Shift and Ctrl. Alt was read from live hardware even while recorded input was driving the engine, so a shortcut using Alt behaved differently on replay than the identical shortcut using Ctrl. BK2 movies record no modifier column at all, so all four read as released under BK2 playback.
```

```bash
git add CHANGELOG.md \
  src/main/java/com/openggf/control/LogicalInputSnapshot.java \
  src/main/java/com/openggf/control/InputHandler.java \
  src/main/java/com/openggf/debug/playback/Bk2FrameInput.java \
  src/main/java/com/openggf/debug/playback/RecordedInputSnapshots.java \
  src/main/java/com/openggf/game/rewind/LiveRewindInputSource.java \
  src/test/java/com/openggf/control/TestPlayerInputState.java \
  src/test/java/com/openggf/control/TestInputHandlerLogicalSnapshot.java \
  src/test/java/com/openggf/game/rewind/TestLiveRewindInputSource.java
git commit -m "fix(input): reproduce Alt and Super from recorded input

isShiftDown and isControlDown consulted the logical override so rewind and
playback drive them deterministically. isAltDown did not, and the Super
query added last commit inherited that. An Alt chord was therefore not
reproducible under playback while the identical chord on Ctrl was.

Both modifier columns now live in LogicalInputSnapshot and Bk2FrameInput,
live rewind records all four, and all four queries read the override. BK2
movies carry no modifier column, so their convenience constructors supply
false -- the same as Shift and Ctrl already did there.

Changelog: updated
Guide: n/a
Known-Discrepancies: n/a
S3K-Known-Discrepancies: n/a
Agent-Docs: n/a
Configuration-Docs: n/a
Skills: n/a"
```

---

### Task 4: Add the getKeyChord accessor and make getInt chord-tolerant

`getInt(KEY)` must keep returning the bare key code so the other 50 bindings are
untouched. **That requires two edits, not zero.** Today a chorded string is neither a
name nor an integer, so `resolveInt` (`:183-217`) warns and falls back to the
*default*'s key code — and once Task 5 makes the default itself `"SHIFT+O"`, the
fallback calls `resolveKeyCode("SHIFT+O")` (`:853-869`), which is chord-blind, yields
`-1`, and takes the "Defaulting to unbound" branch, returning `-1` on every cold
cache. Verified by reading both methods.

Routing both through `KeyChord.parse(...).keyCode()` fixes them together and is
order-preserving after Task 1: `parse` is now name-first then integer, which is
exactly `resolveInt`'s documented KEY rule, so `"1"` still means 49 and `"81"` still
means 81.

`getKeyChord` must additionally honour what `getInt` already honours:
`sessionOverrides` and `transientResolved` via `getConfigValue` (`:293-305`), the
`DERIVED` fallback for `JUMP`/`P2_JUMP` (`ConfigCatalog:95,106`; `resolveInt:171-176`),
and the fall-back-to-registered-default rule (`:204-217`).

**Files:**
- Modify: `src/main/java/com/openggf/configuration/SonicConfigurationService.java`
- Create: `src/test/java/com/openggf/configuration/TestConfigKeyChordResolution.java`
- Modify: `CHANGELOG.md`

**Interfaces:**
- Consumes: Task 1 `KeyChord.parse`.
- Produces: `SonicConfigurationService.getKeyChord(SonicConfiguration)`.

- [ ] **Step 1: Write the failing accessor-agreement tests**

Create `TestConfigKeyChordResolution` following `TestConfigKeyNameResolution`'s
`@TempDir` + `createStandalone(tempDir)` setup (no temp-file leak):

```java
@ParameterizedTest
@ValueSource(strings = {"O", "GLFW_KEY_O", "79"})
void anUnmodifiedBindingReadsTheSameThroughBothAccessors(String configured) {
    configService.setConfigValue(SonicConfiguration.FRAME_STEP_KEY, configured);

    assertEquals(GLFW_KEY_O, configService.getInt(SonicConfiguration.FRAME_STEP_KEY));
    assertEquals(KeyChord.of(GLFW_KEY_O),
            configService.getKeyChord(SonicConfiguration.FRAME_STEP_KEY));
}

/** getInt keeps returning the bare key so the 50 unconverted bindings are untouched. */
@Test
void aChordedBindingKeepsItsBareKeyCodeForGetInt() {
    configService.setConfigValue(SonicConfiguration.FRAME_STEP_KEY, "CTRL+SHIFT+O");

    assertEquals(GLFW_KEY_O, configService.getInt(SonicConfiguration.FRAME_STEP_KEY));
    assertEquals(KeyChord.of(GLFW_KEY_O, CTRL, SHIFT),
            configService.getKeyChord(SonicConfiguration.FRAME_STEP_KEY));
}

@Test
void aDigitBindingMeansTheNumberRowKeyThroughBothAccessors() {
    configService.setConfigValue(SonicConfiguration.FRAME_STEP_KEY, "1");

    assertEquals(GLFW_KEY_1, configService.getInt(SonicConfiguration.FRAME_STEP_KEY));
    assertEquals(GLFW_KEY_1,
            configService.getKeyChord(SonicConfiguration.FRAME_STEP_KEY).keyCode());
}

/** JUMP is DERIVED and falls back to P1_A when unset; a chord accessor built on
 *  getConfigValue alone would return NO_KEY for it. */
@Test
void aDerivedBindingFallsBackToItsSourceBinding() {
    configService.setConfigValue(SonicConfiguration.P1_A, "SPACE");

    assertEquals(GLFW_KEY_SPACE, configService.getInt(SonicConfiguration.JUMP));
    assertEquals(KeyChord.of(GLFW_KEY_SPACE),
            configService.getKeyChord(SonicConfiguration.JUMP));
}

/** resolveInt logs a warning and returns the registered default, not unbound.
 *  getKeyChord reconciles at the accessor so both agree. */
@Test
void anUnresolvableValueFallsBackToTheRegisteredDefault() {
    configService.setConfigValue(SonicConfiguration.FRAME_STEP_KEY, "NOT_A_KEY");

    assertEquals(GLFW_KEY_Q, configService.getInt(SonicConfiguration.FRAME_STEP_KEY));
    assertEquals(KeyChord.of(GLFW_KEY_Q),
            configService.getKeyChord(SonicConfiguration.FRAME_STEP_KEY));
}

/** A default that is itself unbound stays unbound rather than resolving to -1
 *  as a live key code. All nine PLAYBACK_* keys ship this way. */
@Test
void anUnboundDefaultReportsAsUnbound() {
    assertFalse(configService.getKeyChord(SonicConfiguration.PLAYBACK_TOGGLE_KEY).isBound());
}

@Test
void aSessionOverrideWinsOverThePersistedValue() {
    configService.setConfigValue(SonicConfiguration.FRAME_STEP_KEY, "O");
    configService.setSessionOverride(SonicConfiguration.FRAME_STEP_KEY, "CTRL+P");

    assertEquals(KeyChord.of(GLFW_KEY_P, CTRL),
            configService.getKeyChord(SonicConfiguration.FRAME_STEP_KEY));

    configService.clearSessionOverrides();

    assertEquals(KeyChord.of(GLFW_KEY_O),
            configService.getKeyChord(SonicConfiguration.FRAME_STEP_KEY));
}

/** The chord cache must be invalidated wherever intCache is, or a rebind is
 *  visible through getInt and stale through getKeyChord. */
@Test
void rebindingIsVisibleThroughBothAccessorsImmediately() {
    assertEquals(KeyChord.of(GLFW_KEY_Q),
            configService.getKeyChord(SonicConfiguration.FRAME_STEP_KEY));

    configService.setConfigValue(SonicConfiguration.FRAME_STEP_KEY, "ALT+P");

    assertEquals(GLFW_KEY_P, configService.getInt(SonicConfiguration.FRAME_STEP_KEY));
    assertEquals(KeyChord.of(GLFW_KEY_P, ALT),
            configService.getKeyChord(SonicConfiguration.FRAME_STEP_KEY));
}

@Test
void aConvertedAndAnUnconvertedBindingCoexist() {
    configService.setConfigValue(SonicConfiguration.FRAME_STEP_KEY, "SHIFT+O");
    configService.setConfigValue(SonicConfiguration.PAUSE_KEY, "ENTER");

    assertEquals(KeyChord.of(GLFW_KEY_O, SHIFT),
            configService.getKeyChord(SonicConfiguration.FRAME_STEP_KEY));
    assertEquals(GLFW_KEY_ENTER, configService.getInt(SonicConfiguration.PAUSE_KEY));
}
```

- [ ] **Step 2: Run the focused test and verify RED**

```bash
mvn -Dmse=off -Dtest=com.openggf.configuration.TestConfigKeyChordResolution test
```

Expected RED: compilation fails —
`cannot find symbol: method getKeyChord(SonicConfiguration)`.

- [ ] **Step 3: Route both accessors through KeyChord and add the cache**

In `resolveInt`'s `ConfigType.KEY` branch (`:183-190`), replace the
`GlfwKeyNameResolver.resolve(str)` probe with `KeyChord.parse(str)` and return
`chord.keyCode()` when `chord.isBound()`. Leave the numeric-parse and name-resolution
steps that follow, and leave the default-fallback branch, unchanged — they are still
reached for values `KeyChord` cannot resolve.

In `resolveKeyCode(Object)` (`:853-869`), replace the body's parse/resolve pair with
`KeyChord.parse(value).keyCode()`. This is a private static helper called from exactly
one place (`:206`), so the change is contained. `KeyChord.parse` already handles the
`Number` case. Verify no `putDefaultKey`/`putDefault` registers a digit key name
before relying on the name-first order here.

Add the accessor and the cache:

```java
private final Map<SonicConfiguration, KeyChord> keyChordCache =
        new EnumMap<>(SonicConfiguration.class);

public KeyChord getKeyChord(SonicConfiguration binding) {
    KeyChord cached = keyChordCache.get(binding);
    if (cached != null) {
        return cached;
    }
    KeyChord resolved = resolveKeyChord(binding);
    keyChordCache.put(binding, resolved);
    return resolved;
}

private KeyChord resolveKeyChord(SonicConfiguration binding) {
    if (binding == SonicConfiguration.JUMP && !hasExplicitValue(SonicConfiguration.JUMP)) {
        return getKeyChord(SonicConfiguration.P1_A);
    }
    if (binding == SonicConfiguration.P2_JUMP && !hasExplicitValue(SonicConfiguration.P2_JUMP)) {
        return getKeyChord(SonicConfiguration.P2_A);
    }
    KeyChord chord = KeyChord.parse(getConfigValue(binding));
    if (chord.isBound()) {
        return chord;
    }
    // resolveInt falls back to the registered default rather than reporting
    // unbound; reconcile here so both accessors agree.
    return KeyChord.parse(defaults.get(binding.name()));
}
```

Add one private invalidator and use it at **all seven** existing `intCache.clear()`
sites — verified at lines `372, 396, 401, 406, 508, 536, 554`:

```java
private void invalidateResolvedCaches() {
    intCache.clear();
    keyChordCache.clear();
}
```

`resetToDefaults()` (`:508`) is one of the seven; do not miss it.

- [ ] **Step 4: Run the new test plus every untouched key-resolution regression**

```bash
mvn -Dmse=off -Dtest=com.openggf.configuration.TestConfigKeyChordResolution,com.openggf.configuration.TestConfigKeyNameResolution,com.openggf.configuration.TestPlaybackKeyDefaultsUnbound,com.openggf.configuration.TestKeyChord,com.openggf.configuration.CaptureConfigDefaultsTest,com.openggf.configuration.TestSonicConfigurationSessionOverrides,com.openggf.configuration.TestConfigServiceYamlRoundTrip test
```

Expected: all pass. `TestConfigKeyNameResolution` and
`TestPlaybackKeyDefaultsUnbound` passing **unmodified** is the evidence that
`getInt`'s contract is preserved for every existing value form, including the digit
rule and the unbound `PLAYBACK_*` defaults.

- [ ] **Step 5: Update changelog and commit exact files**

Add under `## Unreleased`:

```markdown
- Fix: a key binding written with a modifier — `"CTRL+SHIFT+O"` — is now understood instead of being reported as an invalid key and silently replaced by the default. Bindings without a modifier are unaffected and still resolve exactly as before.
```

```bash
git add CHANGELOG.md \
  src/main/java/com/openggf/configuration/SonicConfigurationService.java \
  src/test/java/com/openggf/configuration/TestConfigKeyChordResolution.java
git commit -m "fix(config): read key bindings that carry modifiers

Adds getKeyChord beside getInt. It honours the same rules getInt does:
session overrides and the transient overlay, the DERIVED fallback that
sends JUMP to P1_A when unset, and the fall-back-to-registered-default
that resolveInt performs for an unresolvable value.

getInt keeps returning the bare key code, which took two edits rather
than none. A chorded string was neither a name nor an integer, so
resolveInt warned and fell back to the default -- and once the default is
itself chorded, resolveKeyCode is equally chord-blind and returns -1, so
every cold cache read would be unbound. Both now resolve through KeyChord,
which after the earlier fix uses the same name-before-number order
resolveInt already documented.

The chord cache is invalidated at all seven sites that clear the int
cache, so a rebind cannot be fresh through one accessor and stale through
the other.

Changelog: updated
Guide: n/a
Known-Discrepancies: n/a
S3K-Known-Discrepancies: n/a
Agent-Docs: n/a
Configuration-Docs: n/a
Skills: n/a"
```

---

### Task 5: Convert capture.toggleKey to a chord, with a migration

The default becomes `"SHIFT+O"` and `Engine.handleLiveCaptureShortcut` drops its
hardcoded Shift. Edge-triggering is unchanged — this changes *which* modifiers are
required, not when the shortcut fires.

The default lives in **two** places and both must change or the behaviour is unchanged
for everyone: `putDefaultKey(CAPTURE_TOGGLE_KEY, GLFW_KEY_O)`
(`SonicConfigurationService:646`) and `src/main/resources/config.yaml:172`.
`loadConfig:101-114` loads that classpath resource as `config` when no user file
exists, and `putDefault:719-727` only inserts when the key is absent, so on a fresh
install the explicit `O` wins over the Java default. `ConfigCatalog:265-266` is a third
source: its description is what `ConfigYamlWriter` emits as the comment into every
saved config, and it still says "Shift+key toggles…".

**Existing installs need a migration.** `ConfigYamlWriter` emits every key in
`emitOrder()`, so every user who has launched the engine has a literal
`capture.toggleKey: O` persisted. Changing the default reaches none of them: their
bare `"O"` wins, exact matching rejects the held Shift, Shift+O stops working, and a
stray `O` starts a recording.

A verified overlap must be resolved here too. `DebugOverlayToggle.OBJECT_DEBUG` is
`GLFW_KEY_O` and `DebugOverlayManager.updateInput:58-65` fires every toggle on a bare
`isKeyPressed` with no modifier filter, so Shift+O toggles object debug *and* live
capture whenever debug shortcuts are enabled. **Decision: make the toggle loop use
`isKeyPressedWithoutModifiers`.** These are hardcoded single keys with no chord, so
requiring no modifier is the correct reading of the exactness rule, and it is a
smaller change than moving a default that `README.md:271` and `CONFIGURATION.md:279`
already document as Shift+O. Leave the `PERFORMANCE` toggle (outside the
debug-shortcuts gate) and the explicit Ctrl+P clipboard chord as they are.

**Decision on customised values:** a player who set `toggleKey: P` is left alone.
Their binding becomes a bare `P` chord — Shift is no longer required — which is the
same one-time change the migration spares default users, and inferring `SHIFT+P`
would silently rewrite a value the player chose. The changelog says so.

**Files:**
- Modify: `src/main/java/com/openggf/configuration/SonicConfigurationService.java`
- Modify: `src/main/java/com/openggf/configuration/ConfigMigrationService.java`
- Modify: `src/main/java/com/openggf/configuration/ConfigCatalog.java`
- Modify: `src/main/java/com/openggf/configuration/ConfigYamlWriter.java`
- Modify: `src/main/resources/config.yaml`
- Modify: `src/main/java/com/openggf/capture/LiveCaptureChord.java`
- Modify: `src/main/java/com/openggf/Engine.java`
- Modify: `src/main/java/com/openggf/debug/DebugOverlayManager.java`
- Modify: `src/test/java/com/openggf/capture/LiveCaptureChordTest.java`
- Modify: `src/test/java/com/openggf/configuration/CaptureConfigDefaultsTest.java`
- Modify: `src/test/java/com/openggf/configuration/TestConfigMigrationService.java`
- Modify: `src/test/java/com/openggf/configuration/TestConfigYamlWriter.java`
- Modify: `CONFIGURATION.md`
- Modify: `CHANGELOG.md`

**Interfaces:**
- Consumes: Task 4 `getKeyChord`, Task 2 `isSuperDown`.
- Produces: `ConfigMigrationService.migrateDeprecatedCaptureToggleKey` and the
  chord-driven `LiveCaptureChord.update` from the ledger.

- [ ] **Step 1: Write the failing chord-detector, migration, and default tests**

Rewrite `LiveCaptureChordTest` against the chord-driven signature, preserving every
edge-detection case it already covers and adding the ones the hardcoded Shift made
impossible:

```java
private static final KeyChord SHIFT_O = KeyChord.parse("SHIFT+O");

@Test void togglesWhenShiftPrecedesKey() {
    LiveCaptureChord chord = new LiveCaptureChord();
    assertFalse(chord.update(SHIFT_O, false, true, false, false, false));
    assertTrue(chord.update(SHIFT_O, true, true, false, false, false));
    assertFalse(chord.update(SHIFT_O, true, true, false, false, false));
}

@Test void requiresReleaseBeforeRetoggle() { /* as today, chord-driven */ }

@Test void anUnmodifiedBindingTogglesWithNoModifierHeld() {
    KeyChord plain = KeyChord.parse("O");
    LiveCaptureChord chord = new LiveCaptureChord();

    assertFalse(chord.update(plain, true, true, false, false, false), "shift held");
    assertTrue(chord.update(plain, true, false, false, false, false));
}

@Test void aTwoModifierBindingRequiresBoth() {
    KeyChord ctrlShiftO = KeyChord.parse("CTRL+SHIFT+O");
    LiveCaptureChord chord = new LiveCaptureChord();

    assertFalse(chord.update(ctrlShiftO, true, true, false, false, false));
    assertTrue(chord.update(ctrlShiftO, true, true, true, false, false));
}

@Test void aSuperBindingMatchesARealSuperPress() {
    KeyChord metaO = KeyChord.parse("META+O");
    assertTrue(new LiveCaptureChord().update(metaO, true, false, false, false, true));
}

/**
 * isKeyDown(-1) is not simply false: it falls through to the gamepad rewind-key
 * comparison, and an unbound rewindKey is also -1. An unbound capture binding
 * must never fire.
 */
@Test void anUnboundBindingNeverFires() {
    LiveCaptureChord chord = new LiveCaptureChord();
    assertFalse(chord.update(KeyChord.parse(""), true, false, false, false, false));
}
```

In `TestConfigMigrationService`, mirror the existing
`migrateDeprecatedDisplayColorProfileToggleKey` pair:

```java
@Test
void migrateDeprecatedCaptureToggleKey_rewritesEverySpellingOfTheSupersededDefault() {
    ConfigMigrationService service = new ConfigMigrationService();
    String key = SonicConfiguration.CAPTURE_TOGGLE_KEY.name();

    for (Object superseded : new Object[] {"O", "GLFW_KEY_O", "KEY_O", GLFW_KEY_O}) {
        Map<String, Object> config = new HashMap<>();
        config.put(key, superseded);

        assertTrue(service.migrateDeprecatedCaptureToggleKey(config), String.valueOf(superseded));
        assertEquals("SHIFT+O", config.get(key));
    }
}

@Test
void migrateDeprecatedCaptureToggleKey_leavesACustomisedBindingAlone() {
    Map<String, Object> config = new HashMap<>();
    config.put(SonicConfiguration.CAPTURE_TOGGLE_KEY.name(), "P");

    assertFalse(new ConfigMigrationService().migrateDeprecatedCaptureToggleKey(config));
    assertEquals("P", config.get(SonicConfiguration.CAPTURE_TOGGLE_KEY.name()));
}

@Test
void migrateDeprecatedCaptureToggleKey_isIdempotent() {
    Map<String, Object> config = new HashMap<>();
    config.put(SonicConfiguration.CAPTURE_TOGGLE_KEY.name(), "SHIFT+O");

    assertFalse(new ConfigMigrationService().migrateDeprecatedCaptureToggleKey(config));
}
```

In `CaptureConfigDefaultsTest`, change the bundled-YAML assertion at `:30` from `"O"`
to `"SHIFT+O"`, and add the fresh/migrated-install assertions. Leave `:21`
(`assertEquals(GLFW_KEY_O, c.getInt(CAPTURE_TOGGLE_KEY))`) **unchanged** — it must
keep passing, and it is the evidence that Task 4's `resolveInt` edit landed correctly:

```java
@Test
void theDefaultBindingIsShiftO() {
    SonicConfigurationService c = SonicConfigurationService.createStandalone();

    assertEquals(KeyChord.of(GLFW_KEY_O, SHIFT),
            c.getKeyChord(SonicConfiguration.CAPTURE_TOGGLE_KEY));
}
```

In `TestConfigYamlWriter`, add that a `KEY` value carrying modifiers is written in
canonical form and survives a round trip:

```java
@Test
void aChordedKeyValueIsWrittenInCanonicalForm() {
    Map<String, Object> config = new HashMap<>();
    config.put(SonicConfiguration.CAPTURE_TOGGLE_KEY.name(), "shift+o");

    assertTrue(new ConfigYamlWriter().write(config).contains("toggleKey: SHIFT+O"));
}
```

- [ ] **Step 2: Run the focused tests and verify RED**

```bash
mvn -Dmse=off -Dtest=com.openggf.capture.LiveCaptureChordTest,com.openggf.configuration.TestConfigMigrationService,com.openggf.configuration.CaptureConfigDefaultsTest,com.openggf.configuration.TestConfigYamlWriter test
```

Expected RED: compilation fails —
`method update in class LiveCaptureChord cannot be applied to given types` and
`cannot find symbol: method migrateDeprecatedCaptureToggleKey(Map<String,Object>)`.

- [ ] **Step 3: Move the modifier into the binding**

`LiveCaptureChord.update` takes the ledger signature:

```java
public boolean update(KeyChord chord, boolean keyDown, boolean shiftDown,
                      boolean controlDown, boolean altDown, boolean superDown) {
    boolean complete = chord != null && chord.isBound() && keyDown
            && chord.matchesModifiers(shiftDown, controlDown, altDown, superDown);
    boolean rising = complete && !previousComplete;
    previousComplete = complete;
    return rising;
}
```

Delete the 4-arg form; leaving it would leave a hardcoded-Shift path in the tree.

`Engine.handleLiveCaptureShortcut` (`:1938-1954`) keeps its name and call site — the
architecture guard asserts the literal `handleLiveCaptureShortcut();` string — and
becomes:

```java
KeyChord chord = configService.getKeyChord(SonicConfiguration.CAPTURE_TOGGLE_KEY);
if (!chord.isBound()) {
    return;
}
if (!liveCaptureChord.update(chord, inputHandler.isKeyDown(chord.keyCode()),
        inputHandler.isShiftDown(), inputHandler.isControlDown(),
        inputHandler.isAltDown(), inputHandler.isSuperDown())) {
    return;
}
```

The `isBound()` guard is load-bearing: `InputHandler.isKeyDown(-1)` falls through to
`keyCode == inputBindings.rewindKey()`, which is also `-1` when rewind is unbound.

`SonicConfigurationService:646` becomes `putDefault(SonicConfiguration.CAPTURE_TOGGLE_KEY, "SHIFT+O")`
(not `putDefaultKey`, which renders a single key code).

`src/main/resources/config.yaml:172` becomes:

```yaml
  toggleKey: SHIFT+O   # Toggles live viewport audio/video recording. Modifiers go in the value: CTRL+SHIFT+O, or plain O for no modifier.
```

`ConfigCatalog:265-266` description becomes
`"Live viewport recording toggle. Modifiers belong in the value (e.g. SHIFT+O, CTRL+SHIFT+O)"`.

`ConfigYamlWriter.formatKey` (`:115-133`): when the resolve/parse pair both fail,
try `KeyChord.parse(s)` and emit `chord.format()` when it `isBound()`, before falling
back to the raw string. `needsKeyQuote` already leaves a chord unquoted, which is a
valid YAML plain scalar.

`ConfigMigrationService.migrateDeprecatedCaptureToggleKey` is modelled on
`migrateDeprecatedDisplayColorProfileToggleKey:179-201`: rewrite to `"SHIFT+O"` only
when the persisted value is still the superseded default — `79` as a `Number`, or
`"O"` / `"KEY_O"` / `"GLFW_KEY_O"` case-insensitively as a `String`. Return `false`
for anything else, including a value already `"SHIFT+O"`. Log at the same shape as
its sibling. Wire it into the migration block in `loadConfig` (after
`migrateDeprecatedDisplayColorProfileToggleKey`, before `applyDefaults()`), setting
`configChanged = true`.

`DebugOverlayManager.updateInput`: change the toggle loop's
`handler.isKeyPressed(toggle.keyCode())` to
`handler.isKeyPressedWithoutModifiers(toggle.keyCode())`. Do not touch the
`PERFORMANCE` check above the gate or the Ctrl+P clipboard chord below it.

Update `CONFIGURATION.md:433`'s default cell from `O` to `SHIFT+O` and reword its
description to say the chord lives in the value. `CONFIGURATION.md:279` and
`README.md:271` still read `Shift+O` and stay correct.

- [ ] **Step 4: Run the focused tests, the capture suites, and the guard**

```bash
mvn -Dmse=off -Dtest=com.openggf.capture.LiveCaptureChordTest,com.openggf.configuration.TestConfigMigrationService,com.openggf.configuration.CaptureConfigDefaultsTest,com.openggf.configuration.TestConfigYamlWriter,com.openggf.configuration.TestBundledConfigResource,com.openggf.configuration.TestBundledConfigExamplePublication,com.openggf.configuration.TestConfigServiceYamlRoundTrip,com.openggf.configuration.TestConfigCatalog,com.openggf.configuration.TestLegacyConfigMigration,com.openggf.tests.TestArchitecturalSourceGuard test
```

Expected: all pass, including
`engineLiveCaptureOrderingStaysAtThePresentationBoundary` and the `Engine#display`
method budget.

Then the full suite, since this is the commit that changes shipped behaviour:

```bash
mvn -Dmse=off test
git checkout -- docs/rewind/real-gaps.md
```

Expected: `BUILD SUCCESS`. Record the exact `Tests run` totals. Restore
`real-gaps.md` before staging — the full run regenerates it.

- [ ] **Step 5: Update changelog and commit exact files**

Add under `## Unreleased`:

```markdown
- Feature: `capture.toggleKey` now carries its own modifiers. It defaults to `SHIFT+O`, and writing `CTRL+SHIFT+O`, `META+O` or a plain `O` works as written — the Shift used to be hardcoded in the engine, so the binding claimed to be "the key" while the shortcut was really Shift plus that key, and there was no way to change or remove the Shift. Modifiers are matched exactly: a binding with no modifier does not fire while one is held. An existing `capture.toggleKey: O` is migrated to `SHIFT+O` automatically, so Shift+O keeps working with no user action.
- Change: if you had customised `capture.toggleKey` to something other than `O`, it is deliberately left as you wrote it and now fires **without** Shift, because guessing that you meant `SHIFT+<your key>` would silently rewrite a value you chose. Add `SHIFT+` yourself to restore the old behaviour.
- Fix: the debug overlay toggles no longer fire while a modifier is held, so `Shift+O` starts a recording without also toggling the object-debug overlay.
```

```bash
git add CHANGELOG.md CONFIGURATION.md \
  src/main/resources/config.yaml \
  src/main/java/com/openggf/configuration/SonicConfigurationService.java \
  src/main/java/com/openggf/configuration/ConfigMigrationService.java \
  src/main/java/com/openggf/configuration/ConfigCatalog.java \
  src/main/java/com/openggf/configuration/ConfigYamlWriter.java \
  src/main/java/com/openggf/capture/LiveCaptureChord.java \
  src/main/java/com/openggf/Engine.java \
  src/main/java/com/openggf/debug/DebugOverlayManager.java \
  src/test/java/com/openggf/capture/LiveCaptureChordTest.java \
  src/test/java/com/openggf/configuration/CaptureConfigDefaultsTest.java \
  src/test/java/com/openggf/configuration/TestConfigMigrationService.java \
  src/test/java/com/openggf/configuration/TestConfigYamlWriter.java
git commit -m "feat(config): put the capture toggle's modifiers in its binding

capture.toggleKey defaults to SHIFT+O and Engine no longer hardcodes the
Shift. The binding called itself 'the key' while the shortcut was really
Shift plus that key, so the half a player could see was configurable and
the half that mattered was not.

The default lived in two places -- putDefaultKey and the bundled
config.yaml -- and the bundled value wins on a fresh install, so both
changed. ConfigCatalog's description is a third: it is emitted as the
comment into every saved config and still advertised the hardcoded Shift.

Every existing install has a literal 'capture.toggleKey: O' persisted,
which changing the default would not reach: their bare O wins, exact
matching rejects the held Shift, and a stray O starts a recording. A
migration rewrites it, but only while it is still the superseded default.
A customised value is left as the player wrote it and now fires without
Shift; inferring SHIFT+<their key> would rewrite a choice they made.

Also fixes an overlap this exposes. DebugOverlayToggle.OBJECT_DEBUG is O
and fired on a bare isKeyPressed, so Shift+O toggled object debug as well
as recording. The toggles are hardcoded single keys, so they now require
no modifier held, which is the same exactness rule the bindings follow.

Changelog: updated
Guide: n/a
Known-Discrepancies: n/a
S3K-Known-Discrepancies: n/a
Agent-Docs: n/a
Configuration-Docs: updated
Skills: n/a"
```

---

### Task 6: Document chord syntax and the three-state per-binding reality

Both `CONFIGURATION.md` and `docs/guide/playing/configuration.md` list every key
binding, so the per-binding support table must be **three-state**, not two. The third
state is the one a user would file a bug about and it is invisible from the config
file: `debug.playback.toggleKey: "CTRL+P"` gives `getInt` a live key code and still
never fires.

| State | Meaning | Bindings |
| --- | --- | --- |
| Chord honoured | read via `getKeyChord`, exact matching | `capture.toggleKey` |
| Modifiers ignored | read via `getInt` + `isKeyDown`/`isKeyPressed`; a chord resolves to its bare key and the modifiers are dropped | most bindings |
| Chord permanently dead | read via `isKeyPressedWithoutModifiers`, which is `isKeyPressed(key) && !isAnyModifierDown()` — the modifier must be held to type the chord, and holding it blocks the shortcut | the nine `PLAYBACK_*` keys, and every `GameLoop.isUnmodifiedDebugKeyPressed` binding: `SPECIAL_STAGE_KEY`, `SPECIAL_STAGE_COMPLETE_KEY`, `SPECIAL_STAGE_FAIL_KEY`, `SPECIAL_STAGE_SPRITE_DEBUG_KEY`, `SPECIAL_STAGE_PLANE_DEBUG_KEY`, `NEXT_ACT`, `NEXT_ZONE`, `DEBUG_LAST_CHECKPOINT_KEY`, `LEVEL_SELECT_KEY`, `DEBUG_MODE_KEY`, and `UP`/`DOWN`/`LEFT`/`RIGHT` while debug movement is active |

Also record the hardcoded non-config keys from the spec's inventory, which swallow a
keystroke regardless of what any binding says: `GameLoop:1877-1895` (Shift/Ctrl/Alt+B,
exactly one modifier), `GameLoop:903-905` (Shift+Tab), `DebugOverlayManager` (Ctrl+P),
`EditorInputHandler:99-101,113-122` (Tab-without-Shift, Ctrl+Z/S/Y),
`DisplayShaderPickerController:177`. And the two deferred conversions
(`RECORDING_RECORD_KEY` at `UserRecordingRuntimeControls:33,44` and
`MasterTitleScreen:375`), so a reader knows their prose-documented Shift is still
hardcoded.

**Files:**
- Modify: `CONFIGURATION.md`
- Modify: `docs/guide/playing/configuration.md`

**Interfaces:** none — documentation only. This task touches no `src/` path, so the
changelog-justification rule does not apply and the commit is `docs:`.

- [ ] **Step 1: Extend the CONFIGURATION.md key-binding format section**

In the `## Key Bindings` format table (`:532-543`), add a `Chord` row
(`"SHIFT+O"`, `"CTRL+SHIFT+O"`, `"META+LEFT_BRACKET"`) and, beneath it, prose covering:

- the accepted modifier aliases, exactly as `KeyChord.modifier` implements them:
  `CTRL`/`CONTROL`, `SHIFT`, `ALT`/`OPTION`, and `META`/`SUPER`/`CMD`/`COMMAND`/`WIN`;
- case-insensitivity and tolerated whitespace around `+`;
- canonical order `CTRL, SHIFT, ALT, META`, which is what the engine writes back;
- **exact matching**: a binding fires only with exactly its modifiers, so a plain
  binding does not fire while any modifier is held;
- **binding the plus key itself**: `+` is the separator, so use `EQUAL` or `KP_ADD`;
- separator-only or unrecognised input is unbound, and an unresolvable value falls
  back to that binding's registered default with a logged warning.

- [ ] **Step 2: Add the three-state support table and the playback limit**

Add the three-state table above, naming the bindings in each state. Then add the
playback note: rewind supplies real Shift/Ctrl/Alt/Super values, but BK2 movies carry
no modifier column at all — `Bk2MovieLoader:162-166` builds every frame through the
convenience constructor — so all four read as released under BK2 playback and any
chord requiring a modifier does not fire there. Update the per-binding table's
`CAPTURE_TOGGLE_KEY` row to point at the "chord honoured" state.

- [ ] **Step 3: Mirror the player-facing subset into the guide**

In `docs/guide/playing/configuration.md:108-121`, add `"SHIFT+O"` and
`"CTRL+SHIFT+O"` to the accepted-formats list with the alias list, one sentence on
exact matching, one on binding the plus key, and one sentence naming which shortcuts
actually honour a chord today with a pointer to `CONFIGURATION.md` for the full table.
Keep it short — the guide is a player document, not the reference.

- [ ] **Step 4: Verify the documented behaviour against the suite**

```bash
mvn -Dmse=off -Dtest=com.openggf.configuration.TestKeyChord,com.openggf.configuration.TestConfigKeyChordResolution,com.openggf.configuration.CaptureConfigDefaultsTest,com.openggf.configuration.TestConfigCatalog,com.openggf.configuration.TestBundledConfigResource test
```

Expected: all pass. Every alias, the exactness rule, the plus-key escape, and the
fallback-to-default rule this task documents has a test above; if a documented claim
has no test, add the test rather than the sentence.

- [ ] **Step 5: Commit exact files**

```bash
git add CONFIGURATION.md docs/guide/playing/configuration.md
git commit -m "docs: describe key chords and which bindings honour them

Documents the chord syntax, the modifier aliases KeyChord accepts, the
canonical order the engine writes back, the exact-matching rule, and how
to bind the plus key now that '+' is the separator.

The per-binding table is three-state rather than two. The third state is
the one a user would file a bug about and it is invisible from the config
file: a binding read through isKeyPressedWithoutModifiers needs the
modifier held to type the chord, and holding it is exactly what blocks the
shortcut, so debug.playback.toggleKey: 'CTRL+P' resolves to a live key
code and still never fires.

Also records that BK2 movies carry no modifier column, so every chord
requiring a modifier is inert under BK2 playback, and lists the hardcoded
non-config shortcuts that swallow a keystroke whatever a binding says.

Changelog: n/a: documentation only, no engine change.
Guide: updated
Known-Discrepancies: n/a
S3K-Known-Discrepancies: n/a
Agent-Docs: n/a
Configuration-Docs: updated
Skills: n/a"
```

---

## Deferred — not in this plan

Spec steps 6 and 7. Both are recorded here so the boundary is explicit, not forgotten.

**Rolling out to the remaining 50 bindings** is not mechanical and must not be done
in bulk:

- **Bindings whose key *is* a modifier key cannot use exact matching as-is.**
  `putDefaultKey(P2_A, GLFW_KEY_RIGHT_SHIFT)` (`:602`),
  `LIVE_REWIND_HALF_SPEED_KEY = GLFW_KEY_LEFT_CONTROL` (`:650`) and
  `LIVE_REWIND_DOUBLE_SPEED_KEY = GLFW_KEY_LEFT_SHIFT` (`:651`) are all defaults
  today. `isShiftDown()` is true whenever either Shift is down, so a `RIGHT_SHIFT`
  chord with no declared modifiers can never satisfy `matchesModifiers` — pressing
  player 2's jump makes its own chord fail. Either such bindings bypass exact
  matching, or `KeyChord` must exclude a held modifier that is the chord's own key.
- **Gameplay/movement bindings keep permissive matching.** They are read with
  `isKeyDown`/`isKeyPressed`, which do not require modifiers released; exact matching
  would silently kill movement while any modifier is held.
- **Any binding consumed via `isKeyPressedWithoutModifiers`** must move to
  `getKeyChord` + `matchesModifiers` at the same time it is advertised as
  chord-capable, or be documented as single-key-only. Converting one without the
  other produces the permanently-dead state.

**`RECORDING_RECORD_KEY`** is the obvious second conversion: a configurable `KEY`
whose Shift is hardcoded at `UserRecordingRuntimeControls:33,44` and
`MasterTitleScreen:375`, with a `ConfigCatalog:327` description advertising the Shift
in prose exactly as `CAPTURE_TOGGLE_KEY`'s did. Both call sites must convert together
or they disagree about what starts a recording, and the stop-on-plain-key rule
(`isKeyPressed(recordKey) && !isShiftDown()`) is a second, derived chord that needs a
decision: its own binding, or "the record chord without its modifiers".

## Acceptance criteria

1. Every form that parsed before parses to the same key with no modifiers,
   **including the ten number-row digit names** (Task 1).
2. `"CTRL+SHIFT+O"`, `"META+LEFT_BRACKET"`, and case/whitespace/alias variants
   resolve to the same chord as their canonical form (Task 1, landed coverage).
3. `format()` round-trips through `parse` for every chord across the whole key name
   table, not a hand-picked sample (Task 1).
4. A plain binding does not fire while any modifier is held (Tasks 1, 5).
5. A chord fires only with exactly its modifiers (Tasks 1, 5).
6. Unresolvable input is unbound, never an exception — including separator-only
   input such as `"+"` (Task 1).
7. Live recording still toggles on Shift+O with no user action, on a fresh install
   **and** on an existing install carrying a persisted `toggleKey: O`, the latter via
   the Task 5 migration.
8. Meta chords match real Super presses (Tasks 2, 5).
9. Alt and Meta chords are reproducible under trace playback on the same terms as
   Shift and Ctrl, and the BK2 no-modifier-column limit is documented (Tasks 3, 6).
10. A latched modifier does not survive window focus loss (Task 2).
11. Full suite green; the `capture.toggleKey` conversion carries changelog entries
    covering both the visible-Shift change and the customised-value case (Task 5).
12. `getKeyChord` and `getInt` agree on the key code for every form — digits, chords,
    `DERIVED` bindings, and values that fall back to their default — and a converted
    and unconverted binding coexist in one `config.yaml` (Task 4).

## Task execution and review protocol

- Work tasks in order. Task 4 depends on Task 1's resolution order; Task 5 depends on
  Tasks 2, 3, and 4.
- Before starting a task, `git status --short --untracked-files=no` must be empty.
- Run the RED command before writing production code and record the exact failure
  text. NOT RUN is never a pass.
- Never weaken, delete, or `@Disabled` an assertion to make a gate green. If a claim
  in this plan is wrong, say so with the evidence rather than working around it.
- `CaptureConfigDefaultsTest:21`, `TestConfigKeyNameResolution`,
  `TestGlfwKeyNameResolver`, `TestPlaybackKeyDefaultsUnbound`,
  `TestBundledConfigResource` and `TestConfigYamlWriter`'s existing cases are the
  backward-compatibility evidence. Where a task does not list them as modified, they
  must stay green **untouched**.
- Run the full `mvn -Dmse=off test` at least at Task 5 and before the branch merges,
  restoring `docs/rewind/real-gaps.md` with `git checkout --` afterwards.
- Each task receives an independent plan-compliance and code-quality review before
  the next dependent task.
- When this branch merges into `develop`, stage a `README.md` update summarising the
  change in the release/change log section, per the branch documentation policy.
