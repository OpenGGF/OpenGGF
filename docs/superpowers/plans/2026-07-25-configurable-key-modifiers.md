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

- Authoritative design: `docs/superpowers/specs/2026-07-25-configurable-key-modifiers-design.md`,
  as revised by `cce3bed1b`. Where this plan and the spec disagree, the spec wins and the
  plan is the defect — say so rather than following the plan.
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

/**
 * The guard must be a round-trip identity check, not a presence check.
 * `nameOf` returns the numeric string for a code with no GLFW_KEY_* constant
 * (`GlfwKeyNameResolver:96`), and the lowest real constant is GLFW_KEY_SPACE
 * = 32 — so `nameOf(0)`..`nameOf(9)` are `"0"`..`"9"`, which ARE real key
 * names resolving to 48..57. A `resolve(name).isEmpty()` guard would not skip
 * them, and `assertEquals(0, 48)` on the first iteration is unfixable by any
 * correct change to KeyChord. Verified by reflecting over lwjgl-glfw-3.3.3:
 * the identity guard covers 120 codes, which is every distinct code in the
 * table, and skips 229 — the 10 digit codes plus 219 gaps.
 */
@Test
void everyNameInTheTableFormatsBackToAnEqualChord() {
    int covered = 0;
    for (int keyCode = 0; keyCode <= GLFW_KEY_LAST; keyCode++) {
        String name = GlfwKeyNameResolver.nameOf(keyCode);
        OptionalInt resolved = GlfwKeyNameResolver.resolve(name);
        if (resolved.isEmpty() || resolved.getAsInt() != keyCode) {
            continue; // no constant for this code; nameOf gave back a number
        }
        covered++;
        KeyChord plain = KeyChord.parse(name);
        assertEquals(keyCode, plain.keyCode(), name);
        assertEquals(plain, KeyChord.parse(plain.format()), name);

        KeyChord chorded = KeyChord.of(keyCode, CTRL, SHIFT, ALT, META);
        assertEquals(chorded, KeyChord.parse(chorded.format()), name);
    }
    assertTrue(covered >= 100, "guard must not skip the table itself: " + covered);
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

Add the imports `GLFW_KEY_1`, `GLFW_KEY_LAST`, `java.util.OptionalInt`, and
`assertTrue` if not already present.

- [ ] **Step 2: Run the focused test and verify RED**

```bash
mvn -Dmse=off -Dtest=com.openggf.configuration.TestKeyChord test
```

Expected RED: `aDigitBindingMeansTheNumberRowKeyNotTheRawKeyCode` fails with
`expected: <49> but was: <1>`; `separatorOnlyInputIsUnboundRatherThanThrowing`
errors with `ArrayIndexOutOfBoundsException: Index -1 out of bounds for length 0`;
`everyNameInTheTableFormatsBackToAnEqualChord` fails at `keyCode = 48`, whose name
is `"0"`, with `expected: <48> but was: <0> ==> 0` — the digit codes 0–9 are skipped
by the identity guard, so the first covered digit is the number-row `0`.
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
helper (`:258`) and `UserRecordingMenuState.cursor()` (`:247`), proving the ~30
affected shortcuts recover.

**Its file path and its package deliberately disagree:** the file is
`src/test/java/com/openggf/recording/menu/TestUserRecordingMenu.java` but line 1
declares `package com.openggf.game.recording.menu;`, matching the production class
under `src/main/java/com/openggf/game/recording/menu/`. Surefire matches `-Dtest`
against the compiled class, so the selector must be
`com.openggf.game.recording.menu.TestUserRecordingMenu`. A directory-shaped selector
matches nothing, and because the other class in the same comma list does match,
`failIfNoSpecifiedTests` never trips and the run reports success with this test never
executed. Also add the static imports `GLFW_KEY_DOWN` and `GLFW_KEY_LEFT_SUPER` —
the file currently imports only `GLFW_KEY_ENTER`, `GLFW_KEY_LEFT_SHIFT`,
`GLFW_PRESS`, `GLFW_RELEASE` (`:38-41`).

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
mvn -Dmse=off -Dtest=com.openggf.tests.TestInputHandler,com.openggf.game.recording.menu.TestUserRecordingMenu test
```

Confirm the Surefire summary names **both** classes. If `TestUserRecordingMenu` is
absent from the run, the selector is wrong — do not proceed on a green that skipped it.

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
mvn -Dmse=off -Dtest=com.openggf.tests.TestInputHandler,com.openggf.game.recording.menu.TestUserRecordingMenu,com.openggf.tests.TestArchitecturalSourceGuard,com.openggf.control.TestInputHandlerLogicalSnapshot,com.openggf.control.TestPlayerInputState test
```

Expected: all five classes appear in the Surefire summary and all pass, including
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

Blast radius is **two questions, not one**. Naming a component is the smaller one:
`debugShiftDown`/`debugControlDown` appear in six files outside their declaring
record. Calling a canonical constructor is the larger one and breaks on arity alone,
naming nothing — a whole-tree scan of `new Bk2FrameInput(` (64 call sites) finds
exactly three at the canonical 11-argument form: `LiveRewindInputSource.java:36`,
`LiveRewindInputSource.java:109`, and `TestLiveRewindLogicalInput.java:51`. The third
is a test, so it is easy to miss and must be edited and staged with the rest.

| File | Change |
| --- | --- |
| `control/LogicalInputSnapshot.java` | 2 new components; the canonical ctor is used 3× in-file (`ofPlayers`, `withMenuPolicy`, `withDebugInput`); `withDebugInput` goes 3 args → 5 |
| `debug/playback/Bk2FrameInput.java` | 2 new components; both convenience ctors absorb them as `false` |
| `debug/playback/RecordedInputSnapshots.java:32-35` | pass the two new fields through; its inline neutral `Bk2FrameInput` uses the 8-arg convenience ctor and is unaffected |
| `game/rewind/LiveRewindInputSource.java:36-47,108-111` | canonical ctor goes 11 args → 13; supply `isAltDown()`/`isSuperDown()` and two more `false` in `neutralFrameInput` |
| `control/InputHandler.java:186-192` | `isAltDown()`/`isSuperDown()` consult `logicalOverride` |
| `test/control/TestPlayerInputState.java:111`, `test/control/TestInputHandlerLogicalSnapshot.java:66` | `withDebugInput` arity |
| `test/game/rewind/TestLiveRewindLogicalInput.java:51-53` | canonical `Bk2FrameInput` ctor, 11 args → 13; found by arity, not by component name |

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
- Modify: `src/test/java/com/openggf/game/rewind/TestLiveRewindLogicalInput.java`
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

In `TestLiveRewindLogicalInput`, the `recordedSnapshotCarriesDebugModifiersIntoInputOverride`
case builds a canonical `Bk2FrameInput` at `:51-53`:

```java
Bk2FrameInput current = new Bk2FrameInput(
        1, 0, 0, false, 0, 0, false,
        true, true, true, "current");
```

Pass two more `true` before `rawLine` and assert `input.isAltDown()` /
`input.isSuperDown()` beside the existing `isShiftDown()` / `isControlDown()`
assertions — it is the natural place to prove the new columns reach the override.
The `previous` frame at `:50` uses the 8-arg convenience constructor and is unchanged.

- [ ] **Step 2: Run the focused tests and verify RED**

```bash
mvn -Dmse=off -Dtest=com.openggf.control.TestInputHandlerLogicalSnapshot,com.openggf.control.TestPlayerInputState,com.openggf.game.rewind.TestLiveRewindInputSource,com.openggf.game.rewind.TestLiveRewindLogicalInput test
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

Expected: `BUILD SUCCESS`. `-DskipTests` skips test *execution* only —
`maven-compiler-plugin:testCompile` still runs (the pom sets no `maven.test.skip`),
so this compiles `src/test` too and any remaining canonical-constructor caller
surfaces here as a compile error. Fix it rather than adding a lossy default
constructor, and add it to the Step 5 `git add` — an edited-but-unstaged file breaks
Task 4's empty-`git status` precondition.

```bash
mvn -Dmse=off -Dtest=com.openggf.control.TestInputHandlerLogicalSnapshot,com.openggf.control.TestPlayerInputState,com.openggf.game.rewind.TestLiveRewindInputSource,com.openggf.game.rewind.TestLiveRewindLogicalInput,com.openggf.tests.TestInputHandler test
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
  src/test/java/com/openggf/game/rewind/TestLiveRewindInputSource.java \
  src/test/java/com/openggf/game/rewind/TestLiveRewindLogicalInput.java
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

`getInt(KEY)` must keep returning the bare key code so the other 52 bindings are
untouched. (`ConfigCatalog` declares **53** `KEY`-typed entries: 52 name `KEY` on the
same line as their `put(`, and `CROSS_GAME_S1_DATA_SELECT_IMAGE_COORD_LOG_KEY` names it
on the continuation line `:359`. The two `derived(KEY, …)` entries — `JUMP` at `:95` and
`P2_JUMP` at `:106` — are `KEY`-typed and counted. Do not transcribe this number; it is
`grep -oE '(\(|, )KEY,' ConfigCatalog.java | wc -l`.)
**That requires two edits, not zero.** Today a chorded string is neither a
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

/** getInt keeps returning the bare key so the 52 unconverted bindings are untouched. */
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

/**
 * Deliberately unbinding a shortcut must unbind it through BOTH accessors.
 * resolveInt's default fallback is gated on `!str.isEmpty()` (:204), so an
 * explicitly empty value returns -1 without consulting the default. A chord
 * accessor that falls back unconditionally would re-bind the shortcut, and
 * after Task 5 that means an unbound capture.toggleKey silently fires on
 * SHIFT+O. FRAME_STEP_KEY is used rather than a PLAYBACK_* key because its
 * registered default is bound (Q), so the two paths actually differ.
 */
@Test
void anExplicitlyEmptyValueStaysUnboundThroughBothAccessors() {
    configService.setConfigValue(SonicConfiguration.FRAME_STEP_KEY, "");

    assertEquals(-1, configService.getInt(SonicConfiguration.FRAME_STEP_KEY));
    assertFalse(configService.getKeyChord(SonicConfiguration.FRAME_STEP_KEY).isBound());
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
    Object value = getConfigValue(binding);
    KeyChord chord = KeyChord.parse(value);
    if (chord.isBound()) {
        return chord;
    }
    // resolveInt falls back to the registered default rather than reporting
    // unbound -- but only for a NON-EMPTY value (:204, `if (!str.isEmpty())`).
    // An explicitly empty value returns -1 with no default lookup, so the same
    // gate belongs here or a player who writes `capture.toggleKey: ""` to unbind
    // the shortcut gets getInt == -1 and getKeyChord == SHIFT+O, and the
    // shortcut keeps firing because Engine reads the chord.
    if (value == null || value.toString().trim().isEmpty()) {
        return chord;
    }
    return KeyChord.parse(defaults.get(binding.name()));
}
```

The gate is load-bearing for acceptance criterion 12 and is **not** covered by
`anUnboundDefaultReportsAsUnbound` — `PLAYBACK_TOGGLE_KEY`'s registered default is
itself `""`, so that test passes either way and hides the divergence. The explicit
test below is the one that pins it.

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
capture whenever debug shortcuts are enabled. **Decision: every toggle dispatch uses
`isKeyPressedWithoutModifiers`.** These are hardcoded single keys with no chord, so
requiring no modifier is the correct reading of the exactness rule, and it is a
smaller change than moving a default that `README.md:271` and `CONFIGURATION.md:279`
already document as Shift+O.

**That includes `PERFORMANCE`, which is dispatched *before* the `debugShortcutsEnabled`
gate and is easy to miss.** Verified in `updateInput` (`:47`): `PERFORMANCE`
(`GLFW_KEY_P`) fires on a bare `handler.isKeyPressed(...)` at `:51`, above the
`debugShortcutsEnabled` return at `:55`; the loop at `:58` `continue`s past it and
dispatches the other 15 at `:62`; and `:68` fires `copyPerformanceStatsToClipboard()` on
`isKeyDown(GLFW_KEY_LEFT_CONTROL) && isKeyPressed(GLFW_KEY_P)`. So **Ctrl+P today both
toggles the performance overlay and copies the stats** — a pre-existing double-fire that
giving `PERFORMANCE` the same modifier-exclusive treatment incidentally fixes. Leave the
Ctrl+P clipboard chord itself alone: it is a deliberate chord and is the half that should
survive.

All 16 toggles therefore stop responding while *any* modifier is held, and after Task 2
that includes Super. The focus-loss clear from Task 2 is what keeps that from latching —
which is why this bullet cannot ship before it.

Severity without the fix is developer-facing, not player-facing: the bundled
`config.yaml` ships `debugView: false`, so the `OBJECT_DEBUG` collision needs debug
shortcuts turned on. That is why it rides in this task rather than blocking Task 1.

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
- Modify: `src/test/java/com/openggf/debug/TestDebugOverlayManagerReset.java`
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

Those three call the migration directly on a hand-built map, so they prove the
function works and prove **nothing** about it being wired in. That wiring is a single
`if (...) { configChanged = true; }` block among four siblings in `loadConfig`
(`:118-136`), and if it is omitted or placed after `applyDefaults()`, every existing
install keeps its bare `O`, exact matching rejects the held Shift, and Shift+O stops
working — precisely the failure acceptance criterion 7 exists to prevent, with the
whole suite green. Add the end-to-end case, which `createStandalone(Path)` makes cheap
because it reads `<dir>/config.yaml`:

```java
/**
 * Criterion 7, existing-install half. The three cases above test the migration
 * function; this one tests that loadConfig actually calls it, which is the part
 * a user would notice.
 */
@Test
void anExistingInstallCarryingTheSupersededDefaultIsMigratedOnLoad(@TempDir Path tempDir)
        throws IOException {
    Files.writeString(tempDir.resolve("config.yaml"), "capture:\n  toggleKey: O\n");

    SonicConfigurationService service = SonicConfigurationService.createStandalone(tempDir);

    assertEquals(KeyChord.of(GLFW_KEY_O, SHIFT),
            service.getKeyChord(SonicConfiguration.CAPTURE_TOGGLE_KEY));
    assertTrue(Files.readString(tempDir.resolve("config.yaml")).contains("toggleKey: SHIFT+O"),
            "the migration must be persisted, not recomputed on every read");
}
```

**The migration is value-based, so it re-runs, and that makes bare `O` a reserved value
for this binding.** The migration block in `loadConfig` runs unconditionally on every
load, before `applyDefaults`, and `configChanged` forces a `saveConfig()`. There is no
config schema or version marker anywhere in `src/main` to hang a one-shot guard on. So a
player who deliberately writes `toggleKey: O` to drop the Shift gets it rewritten to
`SHIFT+O` on the next launch — and so does `GLFW_KEY_O`, `KEY_O` and `79`, since the
match set covers every spelling.

Accepted, not mitigated. This is the established shape in this file:
`migrateDeprecatedDisplayColorProfileToggleKey` has exactly the same property for
`#`/`WORLD_1`. Introducing a `configVersion` marker to make this one migration one-shot
would touch `ConfigFlattener`, `ConfigYamlWriter.emitOrder`, `TestBundledConfigResource`
and every other migration — disproportionate to a config-syntax feature, and better done
as its own change if the pattern keeps biting.

Two consequences that must not be lost: the changelog entry may **not** claim a plain `O`
works as written, and Task 6 must document `O` as reserved for `capture.toggleKey`. A
player who wants a bare unmodified key must choose a different one.

`@TempDir` keeps this leak-free for `TestNoLeakedTemporaryFiles`. If
`TestConfigMigrationService` has no `@TempDir`/`SonicConfigurationService` imports
yet, add them; if a service-level test fits its file better, put this case in
`CaptureConfigDefaultsTest` instead and stage that file — either is fine, but the
assertion must exist somewhere.

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

Add the overlay-collision regression to `TestDebugOverlayManagerReset` (package
`com.openggf.debug`; it already imports `InputHandler` and `GLFW`). This is acceptance
criterion 14 and is the only evidence that the collision is actually closed — the
`DebugOverlayManager` edit is otherwise untested. `DebugOverlayManager` is a singleton,
so call `resetState()` first, as the existing cases in this file do:

```java
/**
 * OBJECT_DEBUG is GLFW_KEY_O and the toggles fired on a bare isKeyPressed, so the
 * new SHIFT+O capture default toggled object debug on the same keystroke that
 * started a recording.
 */
@Test
public void aModifiedKeystrokeDoesNotToggleAnOverlay() {
    DebugOverlayManager manager = DebugOverlayManager.getInstance();
    manager.resetState();
    boolean before = manager.isEnabled(DebugOverlayToggle.OBJECT_DEBUG);
    InputHandler handler = new InputHandler();
    handler.handleKeyEvent(GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_PRESS);
    handler.handleKeyEvent(GLFW.GLFW_KEY_O, GLFW.GLFW_PRESS);

    manager.updateInput(handler, true);

    assertEquals(before, manager.isEnabled(DebugOverlayToggle.OBJECT_DEBUG));
}

@Test
public void anUnmodifiedKeystrokeStillTogglesAnOverlay() {
    DebugOverlayManager manager = DebugOverlayManager.getInstance();
    manager.resetState();
    boolean before = manager.isEnabled(DebugOverlayToggle.PLAYER_PANEL);
    InputHandler handler = new InputHandler();
    handler.handleKeyEvent(GLFW.GLFW_KEY_F3, GLFW.GLFW_PRESS);

    manager.updateInput(handler, true);

    assertNotEquals(before, manager.isEnabled(DebugOverlayToggle.PLAYER_PANEL));
}

/**
 * PERFORMANCE is dispatched above the debugShortcutsEnabled gate, so it needs the
 * same treatment separately. Ctrl+P is the clipboard-copy chord and must not also
 * toggle the overlay -- a pre-existing double-fire.
 */
@Test
public void ctrlPCopiesStatsWithoutAlsoTogglingThePerformanceOverlay() {
    DebugOverlayManager manager = DebugOverlayManager.getInstance();
    manager.resetState();
    boolean before = manager.isEnabled(DebugOverlayToggle.PERFORMANCE);
    InputHandler handler = new InputHandler();
    handler.handleKeyEvent(GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_PRESS);
    handler.handleKeyEvent(GLFW.GLFW_KEY_P, GLFW.GLFW_PRESS);

    manager.updateInput(handler, true);

    assertEquals(before, manager.isEnabled(DebugOverlayToggle.PERFORMANCE));
}
```

Add the `assertNotEquals` import. If `copyPerformanceStatsToClipboard()` cannot run
headless (it reaches `GameServices.profiler()` and the AWT clipboard), do **not** delete
the third case — narrow it to assert the toggle state only, and if the copy path itself
throws headlessly, that is a real finding to report rather than route around.

- [ ] **Step 2: Run the focused tests and verify RED**

```bash
mvn -Dmse=off -Dtest=com.openggf.capture.LiveCaptureChordTest,com.openggf.configuration.TestConfigMigrationService,com.openggf.configuration.CaptureConfigDefaultsTest,com.openggf.configuration.TestConfigYamlWriter,com.openggf.debug.TestDebugOverlayManagerReset test
```

Expected RED: compilation fails —
`method update in class LiveCaptureChord cannot be applied to given types` and
`cannot find symbol: method migrateDeprecatedCaptureToggleKey(Map<String,Object>)`.
`TestDebugOverlayManagerReset` compiles, so its two collision cases fail at runtime
instead: `aModifiedKeystrokeDoesNotToggleAnOverlay` and
`ctrlPCopiesStatsWithoutAlsoTogglingThePerformanceOverlay` both report
`expected: <false> but was: <true>`. Confirm all five classes appear in the Surefire
summary.

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

`DebugOverlayManager.updateInput`: change **both** dispatch sites from
`handler.isKeyPressed(...)` to `handler.isKeyPressedWithoutModifiers(...)` — the
`PERFORMANCE` check at `:51` above the `debugShortcutsEnabled` gate, and
`toggle.keyCode()` in the loop at `:62`. Missing the first one leaves Ctrl+P toggling the
performance overlay as well as copying the stats. Leave the Ctrl+P clipboard chord at
`:68` exactly as it is.

`CONFIGURATION.md` needs **two** edits, not one:

- `:433` — the `CAPTURE_TOGGLE_KEY` row's default cell goes from `O` to `SHIFT+O`,
  and its description ("Complete-chord live viewport recording toggle (`Shift` +
  this key, with Ctrl/Alt released)") is reworded to say the chord lives in the value.
- `:279` — currently reads ``press `Shift+O` (or `Shift+<capture.toggleKey>`)``. The
  parenthetical becomes wrong the moment the Shift moves into the value: it tells a
  reader to press Shift *plus* their binding, so anyone who set `CTRL+SHIFT+O` reads
  it as Shift+Ctrl+Shift+O. Reword to ``press `Shift+O` — or whatever
  `capture.toggleKey` is set to, since the modifiers are part of the value``. Task 6
  is scoped to the format table and the support table and does not revisit this line,
  so it must be fixed here, in the commit that carries `Configuration-Docs: updated`.

`README.md:271` names only the concrete default `Shift+O` with no
`<capture.toggleKey>` parenthetical, so it stays correct and is not staged.

- [ ] **Step 4: Run the focused tests, the capture suites, and the guard**

```bash
mvn -Dmse=off -Dtest=com.openggf.capture.LiveCaptureChordTest,com.openggf.configuration.TestConfigMigrationService,com.openggf.configuration.CaptureConfigDefaultsTest,com.openggf.configuration.TestConfigYamlWriter,com.openggf.debug.TestDebugOverlayManagerReset,com.openggf.configuration.TestBundledConfigResource,com.openggf.configuration.TestBundledConfigExamplePublication,com.openggf.configuration.TestConfigServiceYamlRoundTrip,com.openggf.configuration.TestConfigCatalog,com.openggf.configuration.TestLegacyConfigMigration,com.openggf.tests.TestArchitecturalSourceGuard test
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
- Feature: `capture.toggleKey` now carries its own modifiers. It defaults to `SHIFT+O`, and writing `CTRL+SHIFT+O`, `META+O` or a plain `P` works as written — the Shift used to be hardcoded in the engine, so the binding claimed to be "the key" while the shortcut was really Shift plus that key, and there was no way to change or remove the Shift. Modifiers are matched exactly: a binding with no modifier does not fire while one is held. An existing `capture.toggleKey: O` is migrated to `SHIFT+O` automatically, so Shift+O keeps working with no user action.
- Change: if you had customised `capture.toggleKey` to something other than `O`, it is deliberately left as you wrote it and now fires **without** Shift, because guessing that you meant `SHIFT+<your key>` would silently rewrite a value you chose. Add `SHIFT+` yourself to restore the old behaviour.
- Known limitation: a bare `O` is a reserved value for `capture.toggleKey`. The migration that rescues existing installs matches on the value rather than on a schema version, so it re-runs on every launch and rewrites `O` (and `GLFW_KEY_O`, `KEY_O`, `79`) back to `SHIFT+O`. To bind the toggle to a single unmodified key, pick a different key.
- Fix: the debug overlay toggles no longer fire while a modifier is held, so `Shift+O` starts a recording without also toggling the object-debug overlay. This also stops `Ctrl+P` toggling the performance overlay as well as copying the performance stats to the clipboard.
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
  src/test/java/com/openggf/configuration/TestConfigYamlWriter.java \
  src/test/java/com/openggf/debug/TestDebugOverlayManagerReset.java
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
Shift; inferring SHIFT+<their key> would rewrite a choice they made. The
match is on the value rather than a schema version, so it re-runs every
launch and bare O cannot be bound to this action -- the same property the
two existing key migrations already have. Documented as reserved.

Also fixes an overlap this exposes. DebugOverlayToggle.OBJECT_DEBUG is O
and fired on a bare isKeyPressed, so Shift+O toggled object debug as well
as recording. The toggles are hardcoded single keys, so they now require
no modifier held, which is the same exactness rule the bindings follow.
That covers PERFORMANCE too, which is dispatched above the debug-shortcuts
gate -- incidentally fixing a double-fire where Ctrl+P both toggled the
performance overlay and copied the stats to the clipboard.

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
- Modify: `docs/guide/playing/controls.md`

**Interfaces:** none — documentation only. This task touches no `src/` path, so the
changelog-justification rule does not apply and the commit is `docs:`.

**Both guide pages are in scope, not just one.** Each states the two-format claim that
this work makes wrong, and each is the front door to a different reference:

- `docs/guide/playing/configuration.md:108-121` is the page that *defines* the syntax
  ("Key bindings accept either GLFW key codes (integers) or human-readable names. The
  following formats all work:"), and its "Invalid names log a warning and fall back to
  the default binding" sentence is the one that does not hold for an empty value.
- `docs/guide/playing/controls.md:3-6` repeats the claim verbatim — "using either GLFW
  integer codes or human-readable key names such as `"SPACE"` and `"F9"`" — as the
  opening of the controls reference. Verified present at the branch base.

Because `Guide` maps to the `docs/guide/` prefix, this makes `Guide: updated` mandatory
on this commit. A `Guide: n/a` here would be wrong and the hook will reject it once these
files are staged.

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
- separator-only or unrecognised input is unbound, and a **non-empty** unresolvable value
  falls back to that binding's registered default with a logged warning — while an
  **explicitly empty** value is unbound outright, with no default substituted and no
  warning. That asymmetry is how a shortcut is deliberately switched off, and it is the
  sentence `docs/guide/playing/configuration.md` currently gets wrong;
- **`O` is reserved for `capture.toggleKey`.** The migration that rescues existing
  installs matches on the value rather than a schema version, so it re-runs on every
  launch and rewrites `O`/`GLFW_KEY_O`/`KEY_O`/`79` back to `SHIFT+O`. Say plainly that
  binding this one action to a bare `O` is not possible and to pick another key —
  a reader who tries it and watches their config file change under them will otherwise
  file it as a bug.

- [ ] **Step 2: Add the three-state support table and the playback limit**

**Generate the dead set from the call sites; do not transcribe the table above.** It is
the set of bindings read through `isKeyPressedWithoutModifiers` or
`GameLoop.isUnmodifiedDebugKeyPressed`, and it will drift the moment either gains a
caller. Regenerate it and reconcile against the table before writing:

```bash
grep -rn "isKeyPressedWithoutModifiers" src/main | grep -v "control/InputHandler.java"
grep -rn "isUnmodifiedDebugKeyPressed" src/main
```

At the branch base the first yields **31** call sites across four files —
`GameLoop.java` (1), `debug/playback/PlaybackDebugManager.java` (9),
`game/recording/menu/UserRecordingMenuState.java` (12), and
`testmode/TestModeTracePicker.java` (9) — and Task 5 adds `debug/DebugOverlayManager.java`
(2, both hardcoded keys rather than bindings, so they do not enter the table). If a
`SonicConfiguration` binding appears in the grep output and is missing from the table,
the table is wrong, not the grep.

Note that a binding can be in **two** states on different paths, and the table must say
so rather than picking one: `UP`/`DOWN`/`LEFT`/`RIGHT` are "modifiers ignored" on the
gameplay path and "chord permanently dead" on the special-stage sprite-debug path at
`GameLoop:1135-1146`.

Then add the playback note: rewind supplies real Shift/Ctrl/Alt/Super values, but BK2 movies carry
no modifier column at all — `Bk2MovieLoader:162-166` builds every frame through the
convenience constructor — so all four read as released under BK2 playback and any
chord requiring a modifier does not fire there. Update the per-binding table's
`CAPTURE_TOGGLE_KEY` row to point at the "chord honoured" state.

- [ ] **Step 3: Mirror the player-facing subset into both guide pages**

In `docs/guide/playing/configuration.md:108-121`, add `"SHIFT+O"` and
`"CTRL+SHIFT+O"` to the accepted-formats list with the alias list, one sentence on
exact matching, one on binding the plus key, and one sentence naming which shortcuts
actually honour a chord today with a pointer to `CONFIGURATION.md` for the full table.
Keep it short — the guide is a player document, not the reference.

Correct the fallback sentence in the same section: "Invalid names log a warning and fall
back to the default binding for that action" is true only for a non-empty value. Add that
leaving a binding empty (`""`) switches the shortcut off and no default is substituted.

In `docs/guide/playing/controls.md:3-6`, extend the same two-format sentence to mention
modifiers and point at `configuration.md` for the syntax. One clause is enough — this page
is a key/action table, not a syntax reference — but leaving it claiming only two formats
contradicts the page it links to.

- [ ] **Step 4: Verify the documented behaviour against the suite**

```bash
mvn -Dmse=off -Dtest=com.openggf.configuration.TestKeyChord,com.openggf.configuration.TestConfigKeyChordResolution,com.openggf.configuration.CaptureConfigDefaultsTest,com.openggf.configuration.TestConfigCatalog,com.openggf.configuration.TestBundledConfigResource test
```

Expected: all pass. Every alias, the exactness rule, the plus-key escape, and the
fallback-to-default rule this task documents has a test above; if a documented claim
has no test, add the test rather than the sentence.

- [ ] **Step 5: Commit exact files**

```bash
git add CONFIGURATION.md \
  docs/guide/playing/configuration.md \
  docs/guide/playing/controls.md
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
requiring a modifier is inert under BK2 playback, that O is a reserved
value for capture.toggleKey because its migration matches on the value and
re-runs every launch, and the hardcoded non-config shortcuts that swallow a
keystroke whatever a binding says.

Both guide pages claimed bindings accept only integers or key names, and
configuration.md additionally claimed an invalid value always falls back to
the default -- which is not true of an empty value, the one form that
switches a shortcut off.

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

**Rolling out to the remaining 52 bindings** is not mechanical and must not be done
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
   the Task 5 migration — proved end-to-end through `loadConfig`, not only by calling
   the migration function on a hand-built map.
8. Meta chords match real Super presses (Tasks 2, 5).
9. Alt and Meta chords are reproducible under trace playback on the same terms as
   Shift and Ctrl, and the BK2 no-modifier-column limit is documented (Tasks 3, 6).
10. A latched modifier does not survive window focus loss (Task 2).
11. Full suite green; the `capture.toggleKey` conversion carries changelog entries
    covering both the visible-Shift change and the customised-value case (Task 5).
12. `getKeyChord` and `getInt` agree on the key code for every form — digits, chords,
    `DERIVED` bindings, values that fall back to their default, and an explicitly
    empty value that falls back to neither and stays unbound — and a converted and
    unconverted binding coexist in one `config.yaml` (Task 4).
13. An unbound chord never fires, including while a gamepad rewind button is held and
    `LIVE_REWIND_KEY` is itself unbound — the case where `isKeyDown(-1)` is not false
    (Task 4's `isBound()` guard rule, Task 5's `anUnboundBindingNeverFires` and the
    `Engine` call-site guard).
14. `Shift+O` starts a capture without also toggling the `OBJECT_DEBUG` overlay when
    debug shortcuts are enabled, a bare F-key overlay toggle still works, and `Ctrl+P`
    copies the performance stats without also toggling the performance overlay
    (Task 5, `TestDebugOverlayManagerReset`).
15. A chord saved in a non-canonical spelling (`shift+o`, `Shift + O`) is rewritten to
    its canonical form by `saveConfig()` rather than persisted verbatim through
    `ConfigYamlWriter.formatKey`'s fall-through branch (Task 5).
16. Both guide pages that define binding syntax — `docs/guide/playing/configuration.md`
    and `docs/guide/playing/controls.md` — describe chords, and the first no longer
    claims that every invalid value falls back to the default (Task 6).
17. `O` is documented as a reserved value for `capture.toggleKey`, with the reason
    (a value-based migration that re-runs every launch) rather than only the rule
    (Tasks 5, 6).

## Task execution and review protocol

- Work tasks in order. Task 4 depends on Task 1's resolution order; Task 5 depends on
  Tasks 2, 3, and 4.
- Before starting a task, `git status --short --untracked-files=no` must be empty.
- Run the RED command before writing production code and record the exact failure
  text. NOT RUN is never a pass.
- A `-Dtest` selector is a **package** name, not a directory path, and at least one
  test file in this tree deliberately disagrees with its directory
  (`src/test/java/com/openggf/recording/menu/TestUserRecordingMenu.java` declares
  `package com.openggf.game.recording.menu;`). Surefire's `failIfNoSpecifiedTests`
  only trips when *nothing* in the comma list matches, so one bad name in a list
  silently drops that class while the build reports success. Check every class you
  selected appears in the Surefire summary before treating a green as evidence.
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
