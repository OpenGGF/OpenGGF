# Native-build modding guard — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On a GraalVM native-image build, prevent code-bearing mods (which cannot load under closed-world AOT) from loading or being enabled, warn the user via an in-engine boot notice + console log, and suppress crashing standalone entry points — while leaving data-only music/reskin mods fully working.

**Architecture:** A single `compiledModsSupported` boolean (`false` when `Engine.isNativeImage()`) is resolved once at boot and threaded to four consumers: the mod class-loader factory (load-time backstop), a new pure catalog helper that computes the unsupported-enabled list (drives the notice + console log), the master-title entry enumeration (standalone suppression), and the Mod Manager screen (grey-out + cascade-aware enable refusal). A minimal `NativeModNoticeScreen` mirrors `LegalDisclaimerScreen` and is slotted into the boot chain before the master title, only when the list is non-empty.

**Tech Stack:** Java 21, JUnit 5 (Jupiter) only, Maven (MSE relaxed by default; use `-Dmse=off` for full logs), LWJGL/OpenGL (native libs — `mvn test` must run with the sandbox OFF).

## Global Constraints

- **Reference spec:** `docs/superpowers/specs/2026-07-20-native-build-modding-guard-design.md`. Every task implements part of it.
- **JUnit 5 only.** No JUnit 4, no `org.junit.*` (non-jupiter) imports, no runners/rules.
- **Runtime suppression only.** Never mutate persisted mod state (`ModStateStore` / `ModState` on disk). The guard suppresses loading/enabling for the current process; saved enabled flags are untouched.
- **No `Engine` calls from the `mods` package.** The capability arrives as a plain `boolean` / value object. `com.openggf.mods.*` must not import `com.openggf.Engine`.
- **Default the flag to `true` (supported)** in every pre-existing/test constructor and overload so only the production boot path changes behavior.
- **Native detection is `System.getProperty("org.graalvm.nativeimage.imagecode") != null`** (already implemented as `Engine.isNativeImage()`, `Engine.java:369-371`). `compiledModsSupported == !isNativeImage()`.
- **Branch/commit policy:** branch `feature/ai-native-mod-guard` (worktree, off `next`). Every non-merge commit carries the trailer block. Engine `src/main/` changes → `Changelog: updated` + staged `CHANGELOG.md`. Others `updated`/`n/a` per staged files. Never `--no-verify`. End commit messages with the `Co-Authored-By:` and `Claude-Session:` trailers used elsewhere on this branch.
- **Build/test invocation (PowerShell):** quote `-D` props, e.g. `mvn "-Dtest=com.openggf.mods.TestNativeUnsupportedMods" test`. Trace/GL tests need the sandbox off.

---

## File Structure

**New files:**
- `src/main/java/com/openggf/mods/NativeUnsupportedMods.java` — pure helper: catalog + startup `ModState` + flag → unsupported-enabled descriptors; plus the standalone predicate and the notice-line builder.
- `src/main/java/com/openggf/game/NativeModNoticeScreen.java` — boot notice screen (mirrors `LegalDisclaimerScreen`).
- `src/test/java/com/openggf/mods/TestNativeUnsupportedMods.java`
- `src/test/java/com/openggf/mods/code/TestModClassLoaderFactoryNativeGuard.java`
- `src/test/java/com/openggf/mods/ui/TestModManagerScreenNativeGuard.java`
- `src/test/java/com/openggf/game/TestNativeModNoticeLines.java`
- `src/test/java/com/openggf/render/TestEngineRenderDispatcherNativeNotice.java`

**Modified files:**
- `src/main/java/com/openggf/mods/code/ModRuntime.java` — add `RejectionReason.NATIVE_UNSUPPORTED`.
- `src/main/java/com/openggf/mods/code/ModClassLoaderFactory.java` — `compiledModsSupported` param + top-of-loop gate.
- `src/main/java/com/openggf/ModSubsystem.java` — store startup `ModState` + `compiledModsSupported`; getters; `installAtBoot` overload; `createManager` passes flag.
- `src/main/java/com/openggf/mods/ui/ModManagerScreen.java` — constructor flag; `notLoaded` render + `UNSUPPORTED` badge; cascade-aware enable guard.
- `src/main/java/com/openggf/game/GameMode.java` — `NATIVE_MOD_NOTICE` constant.
- `src/main/java/com/openggf/render/EngineRenderDispatcher.java` — `NATIVE_MOD_NOTICE` clear + draw arms.
- `src/main/java/com/openggf/game/mode/BootScreenModeController.java` — `handles()` parity + notice update method.
- `src/main/java/com/openggf/GameLoop.java` — notice supplier/exit-handler + dispatch block.
- `src/main/java/com/openggf/Engine.java` — resolve flag; factory call; notice computation + boot-chain wiring; master-title suppression; standalone-launch guard; draw hook.
- `docs/modding/index.md`, `docs/modding/content-mods.md`, `docs/modding/troubleshooting.md`, `CHANGELOG.md`.

---

## Task 1: `NativeUnsupportedMods` pure helper

**Files:**
- Create: `src/main/java/com/openggf/mods/NativeUnsupportedMods.java`
- Test: `src/test/java/com/openggf/mods/TestNativeUnsupportedMods.java`

**Interfaces:**
- Consumes: `ModCatalogEntry`/`ModDescriptor` (`manifest().id()`, `manifest().name()`, `containsCode()`), `ModState` (`entries()`, `Entry.id()`, `Entry.enabled()`).
- Produces:
  - `static List<ModDescriptor> compute(List<ModCatalogEntry> scanned, ModState startupEnabledIntent, boolean compiledModsSupported)`
  - `static boolean blocksStandalone(ModDescriptor descriptor, boolean compiledModsSupported)` — `descriptor.containsCode() && !compiledModsSupported`
  - `static List<String> noticeLines(List<String> modNames, int maxLines)` — header + truncated body (used by the screen and its test).
  - `static final String NOTICE_HEADER = "The following enabled mods are not supported on OpenGGF native builds and have been disabled:"`

- [ ] **Step 1: Write the failing test**

```java
package com.openggf.mods;

import com.openggf.game.ModType;
import com.openggf.game.SemanticVersion;
import com.openggf.game.VersionRange;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

class TestNativeUnsupportedMods {

    private static ModDescriptor descriptor(String id, boolean code) {
        ModManifest manifest = new ModManifest(1, id, id + "-name",
                new SemanticVersion(1, 0, 0), List.of("a"), "d",
                new VersionRange(new SemanticVersion(2, 0, 0), new SemanticVersion(3, 0, 0)),
                ModType.PATCH, "sonic2", code ? "example." + id + ".Entry" : null,
                List.of(), Map.of(), Map.of(), null, OptionalInt.empty());
        return new ModDescriptor(Path.of("mods", id + ".jar"), manifest,
                "0".repeat(64), code, List.of());
    }

    private static ModState state(ModState.Entry... entries) {
        return new ModState(ModState.CURRENT_FORMAT_VERSION, List.of(entries));
    }

    @Test
    void includesEnabledCodeModExcludesDataAndDisabled() {
        List<ModCatalogEntry> scanned = List.of(
                descriptor("codeon", true), descriptor("codeoff", true),
                descriptor("dataon", false));
        ModState st = state(
                new ModState.Entry("codeon", true, 0),
                new ModState.Entry("codeoff", false, 1),
                new ModState.Entry("dataon", true, 2));

        List<ModDescriptor> result = NativeUnsupportedMods.compute(scanned, st, false);

        assertEquals(List.of("codeon"),
                result.stream().map(d -> d.manifest().id()).toList());
    }

    @Test
    void enabledUntrustedCodeModStillIncluded() {
        // Untrusted here means the ModState.Entry is enabled but not trusted; the
        // helper must key off enabled intent, not trust/eligibility.
        List<ModCatalogEntry> scanned = List.of(descriptor("codeon", true));
        ModState st = state(new ModState.Entry("codeon", true, 0)); // enabled, untrusted
        assertEquals(1, NativeUnsupportedMods.compute(scanned, st, false).size());
    }

    @Test
    void disabledUntrustedCodeModExcluded() {
        List<ModCatalogEntry> scanned = List.of(descriptor("codeoff", true));
        ModState st = state(new ModState.Entry("codeoff", false, 0));
        assertTrue(NativeUnsupportedMods.compute(scanned, st, false).isEmpty());
    }

    @Test
    void alwaysEmptyWhenSupported() {
        List<ModCatalogEntry> scanned = List.of(descriptor("codeon", true));
        ModState st = state(new ModState.Entry("codeon", true, 0));
        assertTrue(NativeUnsupportedMods.compute(scanned, st, true).isEmpty());
    }

    @Test
    void noticeLinesTruncatesWithAndNMore() {
        List<String> names = List.of("a", "b", "c", "d", "e");
        // maxLines = 3 → header + first 2 + "…and 3 more"
        List<String> lines = NativeUnsupportedMods.noticeLines(names, 3);
        assertEquals(NativeUnsupportedMods.NOTICE_HEADER, lines.get(0));
        assertEquals(List.of("a", "b"), lines.subList(1, 3));
        assertEquals("…and 3 more", lines.get(3));
        assertEquals(4, lines.size());
    }

    @Test
    void noticeLinesNoTruncationAtBoundary() {
        List<String> names = List.of("a", "b", "c");
        List<String> lines = NativeUnsupportedMods.noticeLines(names, 3);
        assertEquals(NativeUnsupportedMods.NOTICE_HEADER, lines.get(0));
        assertEquals(List.of("a", "b", "c"), lines.subList(1, 4));
        assertEquals(4, lines.size());
    }

    @Test
    void blocksStandaloneOnlyWhenCodeAndUnsupported() {
        assertTrue(NativeUnsupportedMods.blocksStandalone(descriptor("s", true), false));
        assertFalse(NativeUnsupportedMods.blocksStandalone(descriptor("s", true), true));
        assertFalse(NativeUnsupportedMods.blocksStandalone(descriptor("s", false), false));
    }

    @Test
    void freshDropInWithoutStateEntryExcluded() {
        // A code jar present in scanned but absent from ModState is disabled-by-default.
        List<ModCatalogEntry> scanned = List.of(descriptor("codenew", true));
        assertTrue(NativeUnsupportedMods.compute(scanned, ModState.EMPTY, false).isEmpty());
    }

    @Test
    void enabledStandaloneCodeModIncluded() {
        // Standalone mods are always code-bearing; compute() must list them
        // (they also feed blocksStandalone for master-title suppression).
        List<ModCatalogEntry> scanned = List.of(descriptor("standalone", true));
        ModState st = state(new ModState.Entry("standalone", true, 0));
        assertEquals(List.of("standalone"),
                NativeUnsupportedMods.compute(scanned, st, false).stream()
                        .map(d -> d.manifest().id()).toList());
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `mvn "-Dtest=com.openggf.mods.TestNativeUnsupportedMods" test`
Expected: FAIL — `NativeUnsupportedMods` does not exist (compilation error). (If the `ModManifest`/`ModDescriptor` constructor arity differs from the extraction, adjust the test fixture to match the real record components — verify against `src/main/java/com/openggf/mods/ModManifest.java` and `ModDescriptor.java`.)

- [ ] **Step 3: Implement the helper**

```java
package com.openggf.mods;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Pure computation of which enabled mods cannot run on a GraalVM native-image
 * build. Code-bearing mods require runtime classloading, which closed-world AOT
 * cannot do. Data-only mods (music/reskin) are unaffected.
 *
 * <p>The source of truth is the user's <em>startup</em> enabled intent
 * ({@link ModState}) intersected with {@code containsCode()} — NOT the eligibility
 * freeze (which cannot distinguish an enabled-but-untrusted code mod from a
 * disabled one) and NOT runtime rejections.
 */
public final class NativeUnsupportedMods {

    public static final String NOTICE_HEADER =
            "The following enabled mods are not supported on OpenGGF native builds and have been disabled:";

    private NativeUnsupportedMods() { }

    public static List<ModDescriptor> compute(List<ModCatalogEntry> scanned,
                                              ModState startupEnabledIntent,
                                              boolean compiledModsSupported) {
        Objects.requireNonNull(scanned, "scanned");
        Objects.requireNonNull(startupEnabledIntent, "startupEnabledIntent");
        if (compiledModsSupported) {
            return List.of();
        }
        Set<String> enabled = new HashSet<>();
        for (ModState.Entry entry : startupEnabledIntent.entries()) {
            if (entry.enabled()) {
                enabled.add(entry.id());
            }
        }
        List<ModDescriptor> result = new ArrayList<>();
        for (ModCatalogEntry catalogEntry : scanned) {
            if (catalogEntry instanceof ModDescriptor descriptor
                    && descriptor.containsCode()
                    && enabled.contains(descriptor.manifest().id())) {
                result.add(descriptor);
            }
        }
        return List.copyOf(result);
    }

    public static boolean blocksStandalone(ModDescriptor descriptor, boolean compiledModsSupported) {
        return descriptor.containsCode() && !compiledModsSupported;
    }

    /**
     * Header + one line per mod name, truncated to {@code maxLines} body lines.
     * When {@code names.size() > maxLines}, shows the first {@code maxLines - 1}
     * names and a final {@code "…and N more"} line.
     */
    public static List<String> noticeLines(List<String> modNames, int maxLines) {
        Objects.requireNonNull(modNames, "modNames");
        if (maxLines < 1) {
            throw new IllegalArgumentException("maxLines must be >= 1");
        }
        List<String> lines = new ArrayList<>();
        lines.add(NOTICE_HEADER);
        if (modNames.size() <= maxLines) {
            lines.addAll(modNames);
        } else {
            int shown = maxLines - 1;
            lines.addAll(modNames.subList(0, shown));
            lines.add("…and " + (modNames.size() - shown) + " more");
        }
        return List.copyOf(lines);
    }
}
```

- [ ] **Step 4: Run tests and confirm pass**

Run: `mvn "-Dtest=com.openggf.mods.TestNativeUnsupportedMods" test`
Expected: PASS (all 7 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/openggf/mods/NativeUnsupportedMods.java src/test/java/com/openggf/mods/TestNativeUnsupportedMods.java
git commit  # fill trailers: Changelog: n/a: pure helper, wired to runtime in a later task; others n/a
```

---

## Task 2: Factory load-time backstop + `NATIVE_UNSUPPORTED` reason

**Files:**
- Modify: `src/main/java/com/openggf/mods/code/ModRuntime.java:330-336` (enum)
- Modify: `src/main/java/com/openggf/mods/code/ModClassLoaderFactory.java:56-70` (new overload + loop-top gate)
- Test: `src/test/java/com/openggf/mods/code/TestModClassLoaderFactoryNativeGuard.java`

**Interfaces:**
- Consumes: `NativeUnsupportedMods` (not directly — this is the independent load backstop).
- Produces:
  - `ModRuntime.RejectionReason.NATIVE_UNSUPPORTED`
  - `ModRuntime create(EffectiveModCatalog catalog, Set<String> trustedCodeOwners, boolean compiledModsSupported)`
  - Existing `create(catalog, trusted)` delegates with `compiledModsSupported = true`.

- [ ] **Step 1: Add the enum constant**

In `ModRuntime.java`, extend the enum (currently `ModRuntime.java:330-336`):

```java
    public enum RejectionReason {
        HASH_MISMATCH,
        VALIDATION_FAILED,
        DEPENDENCY_UNAVAILABLE,
        INSPECTION_BUDGET_EXCEEDED,
        SNAPSHOT_FAILED,
        NATIVE_UNSUPPORTED
    }
```

- [ ] **Step 2: Write the failing test**

Locate an existing `ModClassLoaderFactory` / `ModRuntime` test (e.g. `TestModRuntime`, `TestModArtOverrides`) and copy its fixture construction for building an `EffectiveModCatalog` with a code-bearing enabled descriptor and a trusted-owner set. Then:

```java
package com.openggf.mods.code;

// imports mirrored from the existing ModClassLoaderFactory/ModRuntime test fixtures
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class TestModClassLoaderFactoryNativeGuard {

    // Build `catalog` with one enabled code-bearing owner "codeowner" that is
    // TRUSTED (present in trustedCodeOwners), plus one enabled data-only owner
    // "dataowner". Reuse the existing test's catalog/descriptor builders.

    @Test
    void nativeRejectsCodeModEvenWhenTrusted() throws Exception {
        var runtime = new ModClassLoaderFactory(getClass().getClassLoader())
                .create(catalog, Set.of("codeowner"), /* compiledModsSupported */ false);
        assertTrue(runtime.rejectedOwners().containsKey("codeowner"));
        assertEquals(ModRuntime.RejectionReason.NATIVE_UNSUPPORTED,
                runtime.rejectedOwners().get("codeowner").reason());
        assertFalse(runtime.owners().contains("codeowner"));
        runtime.close();
    }

    @Test
    void supportedLoadsTrustedCodeModAsBefore() throws Exception {
        var runtime = new ModClassLoaderFactory(getClass().getClassLoader())
                .create(catalog, Set.of("codeowner"), /* compiledModsSupported */ true);
        assertFalse(runtime.rejectedOwners().containsKey("codeowner")
                && runtime.rejectedOwners().get("codeowner").reason()
                        == ModRuntime.RejectionReason.NATIVE_UNSUPPORTED);
        runtime.close();
    }
}
```

Note: match `runtime.owners()` to the real accessor name if it differs (check `ModRuntime.java`). If constructing a real loadable code jar in a unit test is impractical, assert only the `rejectedOwners()` outcome (the loader is never reached on native) and keep the "supported" case as a regression that the owner is NOT rejected with `NATIVE_UNSUPPORTED`.

- [ ] **Step 3: Run it and confirm it fails**

Run: `mvn "-Dtest=com.openggf.mods.code.TestModClassLoaderFactoryNativeGuard" test`
Expected: FAIL — three-arg `create` does not exist / `NATIVE_UNSUPPORTED` unresolved before the enum edit compiles.

- [ ] **Step 4: Implement the overload + gate**

In `ModClassLoaderFactory.java`, change the two-arg `create` to delegate and add the three-arg version. Replace lines 56-57:

```java
    public ModRuntime create(EffectiveModCatalog catalog, Set<String> trustedCodeOwners)
            throws IOException {
        return create(catalog, trustedCodeOwners, true);
    }

    public ModRuntime create(EffectiveModCatalog catalog, Set<String> trustedCodeOwners,
                             boolean compiledModsSupported)
            throws IOException {
```

Then, as the **first statement inside the per-descriptor loop** (immediately after `String owner = descriptor.manifest().id();` at line 69, and **before** the existing `if (descriptor.containsCode() && !trusted.contains(owner)) continue;` at line 70), insert:

```java
                if (descriptor.containsCode() && !compiledModsSupported) {
                    rejections.put(owner, rejection(ModRuntime.RejectionReason.NATIVE_UNSUPPORTED,
                            "code-bearing mods are unsupported on OpenGGF native builds"));
                    continue;
                }
```

Ordering is load-bearing (spec Decision 3): the new check must precede the untrusted-code `continue` so the rejection is recorded rather than swallowed.

- [ ] **Step 5: Run tests and confirm pass**

Run: `mvn "-Dtest=com.openggf.mods.code.TestModClassLoaderFactoryNativeGuard" test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/openggf/mods/code/ModRuntime.java src/main/java/com/openggf/mods/code/ModClassLoaderFactory.java src/test/java/com/openggf/mods/code/TestModClassLoaderFactoryNativeGuard.java
git commit  # Changelog: updated (engine change; stage CHANGELOG in Task 7 or add a line now)
```

---

## Task 3: `ModSubsystem` — carry startup state + flag

**Files:**
- Modify: `src/main/java/com/openggf/ModSubsystem.java` (fields 50-61, private ctor 92-108, public ctors 63-90, `installAtBoot` 118-127, `createManager` 225-232, boot loader 407-410, getters 138-150)

**Interfaces:**
- Consumes: `ModState` (startup), `NativeUnsupportedMods` (indirectly via Engine).
- Produces:
  - `ModState startupModState()` — the startup enabled-intent state (or `ModState.EMPTY` off the production boot path).
  - `boolean compiledModsSupported()`
  - `static void installAtBoot(ExternalContentPolicy policy, Supplier<ModSubsystem> normalBootLoader, boolean compiledModsSupported)`
  - `createManager(font)` now constructs `ModManagerScreen` with the flag (Task 4 adds the param).

**Design note:** The startup `ModState` currently lives only inside the `normalBootLoader` closure (`ModSubsystem.java:378-397`) and in `PendingModStateEditor`. Add it as a stored field so Engine can read it for the notice. The `compiledModsSupported` flag is environment-level; store it too so `createManager` can pass it to the screen.

- [ ] **Step 1: Add fields**

After line 61 (`private AutoCloseable bootResource ...`), add:

```java
    private final ModState startupModState;
    private boolean compiledModsSupported = true;
```

- [ ] **Step 2: Thread `startupModState` through the private constructor**

Change the private constructor signature (line 92-95) to accept `ModState startupModState` and assign it (default `ModState.EMPTY` when unknown). Add the parameter at the end:

```java
    private ModSubsystem(ModCatalog processCatalog, PendingModStateEditor pendingEditor,
                         ModRuntimeFindingStore runtimeFindings,
                         SessionViewFactory sessionFactory, SessionAudioBoundary audioBoundary,
                         ExternalContentPolicy policy, Set<String> trustedCodeOwners,
                         ModState startupModState) {
        // ... existing assignments ...
        this.startupModState = Objects.requireNonNull(startupModState, "startupModState");
    }
```

Update the four public constructors (63-90) to pass `ModState.EMPTY` as the new last argument (they default `compiledModsSupported` to `true` via the field initializer). Update the `disabled(...)` factory (around line 300) similarly to pass `ModState.EMPTY`.

- [ ] **Step 3: Pass the real startup state from the boot loader**

In `normalBootLoader` (line 407-410), the `new ModSubsystem(...)` call already has `startup` in scope (line 378/382/387). Add `, startup` as the final argument:

```java
            ModSubsystem subsystem = new ModSubsystem(catalog, editor, findings,
                    preparedAudioFactory(preparer, catalog.effective(), validated.registry(), validated.sfxRegistry()),
                    audioBoundary, new ExternalContentPolicy(ExternalContentMode.NORMAL),
                    trustedOwners, startup);
```

- [ ] **Step 4: `installAtBoot` overload carrying the flag**

Add a three-arg `installAtBoot` and keep the two-arg one delegating with `true`:

```java
    public static void installAtBoot(ExternalContentPolicy policy,
                                     Supplier<ModSubsystem> normalBootLoader) {
        installAtBoot(policy, normalBootLoader, true);
    }

    public static void installAtBoot(ExternalContentPolicy policy,
                                     Supplier<ModSubsystem> normalBootLoader,
                                     boolean compiledModsSupported) {
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(normalBootLoader, "normalBootLoader");
        ModSubsystem replacement = policy.mayScanAtBoot()
                ? Objects.requireNonNull(normalBootLoader.get(), "normalBootLoader result")
                : disabled(policy);
        replacement.compiledModsSupported = compiledModsSupported;
        installProcess(replacement);
    }
```

- [ ] **Step 5: Getters + `createManager` wiring**

Add near the other getters (after line 142):

```java
    public ModState startupModState() { return startupModState; }

    public boolean compiledModsSupported() { return compiledModsSupported; }
```

In `createManager` (line 230-231), pass the flag into the (Task 4) new constructor:

```java
        return new ModManagerScreenHost(new ModManagerScreen(
                processCatalog, pendingEditor, runtimeFindings, text, patternWindowAllocator,
                compiledModsSupported));
```

- [ ] **Step 6: Compile check (Task 4 provides the matching constructor)**

Run: `mvn -Dmse=off -DskipTests compile`
Expected: FAIL only on the `ModManagerScreen` 6-arg constructor until Task 4 adds it. That is expected; proceed to Task 4 before committing Task 3, or temporarily keep the 5-arg call and switch in Task 4. **Recommended:** implement Task 4 next, then compile+commit Tasks 3 and 4 together.

- [ ] **Step 7: Commit (with Task 4)**

Deferred to the end of Task 4 so the tree compiles.

---

## Task 4: Mod Manager — grey-out + cascade-aware enable refusal

**Files:**
- Modify: `src/main/java/com/openggf/mods/ui/ModManagerScreen.java` (fields 45-67; ctors 69-105; marker 162; `toggleSelected` 283-348; `toView` 581-598; `RowView` 689-694)
- Test: `src/test/java/com/openggf/mods/ui/TestModManagerScreenNativeGuard.java`

**Interfaces:**
- Consumes: `compiledModsSupported` boolean via new constructor param.
- Produces: `ModManagerScreen(catalog, editor, runtimeFindings, text, patternWindows, boolean compiledModsSupported)`; `RowView` gains a `notLoaded` component.

- [ ] **Step 1: Write the failing test**

Locate the existing Mod Manager test(s) (search `src/test` for `ModManagerScreen` usages) and reuse their catalog/editor/`TextSink` fixture builders. Cover:

```java
package com.openggf.mods.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestModManagerScreenNativeGuard {

    // Fixtures (reuse existing helpers): a catalog with
    //  - "codeon"  : code-bearing, enabled
    //  - "dataon"  : data-only, enabled
    //  - "datadep" : data-only, DISABLED, depends on trusted code mod "codedep"
    //  - "codedep" : code-bearing, trusted, disabled
    // Build the screen with compiledModsSupported = false.

    @Test
    void codeModRowIsUnsupportedAndNotLoaded() {
        var screen = newScreen(/*compiledModsSupported*/ false);
        var row = rowFor(screen, "codeon");
        assertTrue(row.badges().contains("UNSUPPORTED"));
        assertFalse(row.valid() && row.enabled()); // not rendered as a plain [ON]
        // notLoaded flag drives the [--] marker:
        assertTrue(row.notLoaded());
    }

    @Test
    void enablingCodeModRefusedOnNative() {
        var screen = newScreen(false);
        selectRow(screen, "codeon");
        accept(screen);
        assertTrue(screen.statusMessageForTest().contains("not supported on native"));
        assertFalse(isEnabledForTest(screen, "codeon"));
    }

    @Test
    void enablingDataModThatCascadesIntoCodeRefusedOnNative() {
        var screen = newScreen(false);
        selectRow(screen, "datadep"); // data-only, but its dependency codedep contains code
        accept(screen);
        assertTrue(screen.statusMessageForTest().contains("not supported on native"));
        assertFalse(isEnabledForTest(screen, "datadep"));
        assertFalse(isEnabledForTest(screen, "codedep"));
    }

    @Test
    void supportedBuildEnablesCodeModNormally() {
        var screen = newScreen(true);
        selectRow(screen, "codeon");
        // existing trust/enable flow proceeds (may require a second accept for trust)
        assertFalse(rowFor(screen, "codeon").badges().contains("UNSUPPORTED"));
    }
}
```

Use whatever inspection hooks the existing tests use (e.g. a package-private `visibleRows()`/`statusMessage` accessor). If none exist, add minimal package-private test accessors on `ModManagerScreen` (`String statusMessageForTest()`, `List<RowView> rowsForTest()`) rather than reflection.

- [ ] **Step 2: Run it and confirm it fails**

Run (sandbox off): `mvn "-Dtest=com.openggf.mods.ui.TestModManagerScreenNativeGuard" test`
Expected: FAIL — 6-arg constructor / `notLoaded()` absent.

- [ ] **Step 3: Add the field + constructor param**

Add field after line 54 (`patternWindows`):

```java
    private final boolean compiledModsSupported;
```

Add a 6-arg constructor and keep the existing 5-arg delegating with `true`. Change the 5-arg constructor (74-105) to delegate:

```java
    public ModManagerScreen(ModCatalog catalog, PendingModStateEditor editor,
                            ModRuntimeFindingStore runtimeFindings, TextSink text,
                            PatternWindowState patternWindows) {
        this(catalog, editor, runtimeFindings, text, patternWindows, true);
    }

    public ModManagerScreen(ModCatalog catalog, PendingModStateEditor editor,
                            ModRuntimeFindingStore runtimeFindings, TextSink text,
                            PatternWindowState patternWindows, boolean compiledModsSupported) {
        // ... existing body ...
        this.compiledModsSupported = compiledModsSupported;
    }
```

Also update the 4-arg constructor (69-72) to pass through `true` (it currently forwards to the 5-arg; leave as-is — it inherits the default).

- [ ] **Step 4: Add `notLoaded` to `RowView` + marker**

Change the `RowView` record (689-694) to add a `notLoaded` component:

```java
    public record RowView(String identity, String label, boolean enabled, boolean valid,
                          boolean notLoaded, List<String> badges) {
        public RowView {
            badges = List.copyOf(badges);
        }
    }
```

Update every `new RowView(...)` construction to supply `notLoaded`. In `toView` (581-598):

```java
    private RowView toView(Row row) {
        if (row.invalid() != null) {
            return new RowView(row.identity(), row.identity(), false, false, false, List.of("ERROR"));
        }
        ModDescriptor descriptor = row.descriptor();
        String id = descriptor.manifest().id();
        LinkedHashSet<String> badges = new LinkedHashSet<>();
        ModEligibility eligibility = catalog.eligibility().get(id);
        if (eligibility != null && eligibility.status() == ModEligibility.Status.BLOCKED) badges.add("BLOCKED");
        if (descriptor.containsCode() && !isTrusted(descriptor)) badges.add("TRUST REQUIRED");
        boolean notLoaded = descriptor.containsCode() && !compiledModsSupported;
        if (notLoaded) badges.add("UNSUPPORTED");
        addFindingBadges(badges, descriptor.findings(), false);
        addFindingBadges(badges, runtimeFindings.findingsFor(id), true);
        String label = descriptorCounts.getOrDefault(id, 0) > 1
                ? descriptor.manifest().name() + " (" + filename(descriptor.jarPath()) + ")"
                : descriptor.manifest().name();
        return new RowView(row.identity(), label, isEnabled(id), true, notLoaded,
                List.copyOf(badges));
    }
```

Update the marker expression (line 162) so a not-loaded row shows `[--]` but stays interactive (`valid()` still governs toggle eligibility):

```java
                String enabled = (!row.valid() || row.notLoaded()) ? "[--] "
                        : (row.enabled() ? "[ON] " : "[OFF]");
```

- [ ] **Step 5: Add the cascade-aware enable guard**

In `toggleSelected()`, inside the `if (!enabled)` branch, **before** `String refusal = enableRefusal(cascade, id);` (line 296), insert:

```java
            if (!compiledModsSupported) {
                for (String cascadeId : cascade) {
                    ModDescriptor cascadeDescriptor = descriptorsById.get(cascadeId);
                    if (cascadeDescriptor != null && cascadeDescriptor.containsCode()) {
                        statusMessage = id + " is not supported on native builds";
                        armedCascade = null;
                        armedTrust = null;
                        return;
                    }
                }
            }
```

(`descriptorsById` is an existing field, line 49. `cascade` already includes `id` — see line 294.)

- [ ] **Step 6: Run tests and confirm pass**

Run (sandbox off): `mvn "-Dtest=com.openggf.mods.ui.TestModManagerScreenNativeGuard" test`
Expected: PASS. Also run any pre-existing Mod Manager test to confirm no regression: `mvn "-Dtest=com.openggf.mods.ui.*" test`.

- [ ] **Step 7: Compile the full main tree + commit Tasks 3 & 4**

Run: `mvn -Dmse=off -DskipTests compile`
Expected: SUCCESS.

```bash
git add src/main/java/com/openggf/ModSubsystem.java src/main/java/com/openggf/mods/ui/ModManagerScreen.java src/test/java/com/openggf/mods/ui/TestModManagerScreenNativeGuard.java
git commit  # Changelog: updated
```

---

## Task 5: Engine — factory flag, master-title suppression, standalone-launch guard

**Files:**
- Modify: `src/main/java/com/openggf/Engine.java` — add flag field; `initializeExternalContentAtBoot` (1392-1426); `masterTitleEntries` (1450-1473); `exitStandaloneMasterTitle` (1132-1150)

**Interfaces:**
- Consumes: `Engine.isNativeImage()`, `NativeUnsupportedMods.blocksStandalone(...)`, `ModSubsystem.installAtBoot(policy, loader, flag)`, `create(..., flag)`.
- Produces: `private final boolean compiledModsSupported = !isNativeImage();` used across boot.

- [ ] **Step 1: Add the resolved flag field**

Near the other Engine instance fields (e.g. by line 259 where `modRuntime` is declared), add:

```java
	private final boolean compiledModsSupported = !isNativeImage();
```

- [ ] **Step 2: Thread the flag into boot install + factory**

In `initializeExternalContentAtBoot` (1395-1398 and 1405-1407):

```java
		ModSubsystem.installAtBoot(policy, ModSubsystem.normalBootLoader(
				() -> Path.of("mods").toAbsolutePath().normalize(),
				ModInputLimits.production(), StockMusicDomains::containsSupported,
				ModSubsystem.SessionAudioBoundary.audioManager(audioManager)),
				compiledModsSupported);
```

and

```java
			modRuntime = replaceModRuntime(modRuntime,
					new ModClassLoaderFactory(Engine.class.getClassLoader())
							.create(effectiveMods, ModSubsystem.current().trustedCodeOwners(),
									compiledModsSupported));
```

- [ ] **Step 3: Suppress standalone code-mod master-title entries**

In `masterTitleEntries()` (1456-1471), after resolving `descriptor` in the loop, skip unsupported standalone code mods. Change the loop body start (after line 1458's STANDALONE check) to also continue when blocked:

```java
			if (descriptor.manifest().type() != com.openggf.mods.ModType.STANDALONE) continue;
			if (com.openggf.mods.NativeUnsupportedMods.blocksStandalone(descriptor, compiledModsSupported)) {
				continue;
			}
```

- [ ] **Step 4: Defense-in-depth guard on standalone launch**

In `exitStandaloneMasterTitle` (1136-1141), after the descriptor is resolved via `.orElseThrow(...)`, add:

```java
		if (com.openggf.mods.NativeUnsupportedMods.blocksStandalone(descriptor, compiledModsSupported)) {
			throw new IllegalStateException(
					"Standalone mod requires the JVM build (code unsupported on native): "
							+ standalone.owner());
		}
```

(This path is unreachable once Step 3 removes the entry, but guards direct/programmatic launches.)

- [ ] **Step 5: Compile + smoke**

Run: `mvn -Dmse=off -DskipTests compile`
Expected: SUCCESS.

Because these are `Engine` internals wired to a native-only flag, there is no cheap headless unit test for `masterTitleEntries()` (it constructs `SaveManager`, reads save slots). Rely on the pure `NativeUnsupportedMods.blocksStandalone` test (Task 1) plus the manual verification in Task 8. Do **not** add a brittle reflection test here.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/openggf/Engine.java
git commit  # Changelog: updated
```

---

## Task 6: Boot notice screen + boot-chain wiring

This is the highest-integration task. Implement in the sub-order below.

**Files:**
- Modify: `src/main/java/com/openggf/game/GameMode.java` (add constant)
- Create: `src/main/java/com/openggf/game/NativeModNoticeScreen.java`
- Modify: `src/main/java/com/openggf/render/EngineRenderDispatcher.java` (clear + draw arms)
- Modify: `src/main/java/com/openggf/game/mode/BootScreenModeController.java` (`handles` + update method)
- Modify: `src/main/java/com/openggf/GameLoop.java` (supplier + exit-handler + dispatch)
- Modify: `src/main/java/com/openggf/Engine.java` (screen field, supplier/exit registration, boot-chain insertion, draw hook)
- Modify: `src/test/java/com/openggf/render/TestEngineRenderDispatcher.java` (existing `RecordingDrawActions` must implement the new interface method — see Step 2)
- Test: `src/test/java/com/openggf/game/TestNativeModNoticeLines.java`, `src/test/java/com/openggf/render/TestEngineRenderDispatcherNativeNotice.java`

**Interfaces:**
- Consumes: `NativeUnsupportedMods.compute(...)` + `noticeLines(...)`, `ModSubsystem.current().startupModState()`, `processCatalog().scanned()`, `compiledModsSupported`, `FadeManager`.
- Produces: `GameMode.NATIVE_MOD_NOTICE`; `NativeModNoticeScreen`; `EngineRenderDispatcher.DrawActions.nativeModNotice()` + `ClearActions` arm; `BootScreenModeController.updateNativeModNotice(...)`; `GameLoop.setNativeModNoticeScreenSupplier(...)` / `setNativeModNoticeExitHandler(...)`.

- [ ] **Step 1: Add the GameMode constant**

In `GameMode.java`, after `LEGAL_DISCLAIMER` (line 43):

```java
    /** Native-build notice: enabled code mods can't load on the native binary. */
    NATIVE_MOD_NOTICE,
```

- [ ] **Step 2: `EngineRenderDispatcher` arms + test**

Test first — `TestEngineRenderDispatcherNativeNotice.java`:

```java
package com.openggf.render;

import com.openggf.game.GameMode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestEngineRenderDispatcherNativeNotice {
    @Test
    void nativeNoticeClearsBlackAndDrawsNotice() {
        var dispatcher = new EngineRenderDispatcher();
        boolean[] black = {false};
        dispatcher.applyClearColor(GameMode.NATIVE_MOD_NOTICE, stubClear(black));
        assertTrue(black[0]);

        boolean[] drew = {false};
        dispatcher.draw(GameMode.NATIVE_MOD_NOTICE, false, null, stubDraw(drew));
        assertTrue(drew[0]);
    }
    // stubClear/stubDraw: anonymous impls of ClearActions/DrawActions where black()/
    // nativeModNotice() flip the flag and all others throw AssertionError.
}
```

Then implement: in `EngineRenderDispatcher.applyClearColor` (line 23) add `NATIVE_MOD_NOTICE` to the `black()` arm:

```java
            case TRY_AGAIN_END, MASTER_TITLE_SCREEN, LEGAL_DISCLAIMER, NATIVE_MOD_NOTICE, EDITOR -> actions.black();
```

In `draw` (after line 36) add:

```java
            case NATIVE_MOD_NOTICE -> actions.nativeModNotice();
```

Add to the `DrawActions` interface (after line 77):

```java
        void nativeModNotice();
```

**REQUIRED — fix the existing implementor (else the test tree fails to compile).** `EngineRenderDispatcher.DrawActions` is implemented by `RecordingDrawActions` in `src/test/java/com/openggf/render/TestEngineRenderDispatcher.java` (~line 125). Add the new method there, mirroring its existing record-a-call methods:

```java
        @Override public void nativeModNotice() { calls.add("nativeModNotice"); }
```

(Match the exact field/idiom `RecordingDrawActions` already uses — e.g. `calls.add(...)`. `ClearActions` is unaffected: `NATIVE_MOD_NOTICE` reuses the existing `black()` arm, so `RecordingClearActions` needs no change.) The production impl `EngineDrawActions` (Engine.java:2976) is handled in Step 7. There are no other `DrawActions` implementors in the tree.

**Also:** the new `TestEngineRenderDispatcherNativeNotice` stub below is an anonymous `DrawActions` — after this change it must implement **all 17** methods (16 existing + `nativeModNotice()`); the non-asserted ones throw `AssertionError`. Consider instead reusing/subclassing `RecordingDrawActions` if it is accessible, to avoid a 17-method anonymous class.

- [ ] **Step 3: The notice line-builder is already tested (Task 1).** Add `TestNativeModNoticeLines.java` only if you want the screen's `MAX_VISIBLE_MOD_LINES` constant asserted:

```java
package com.openggf.game;

import com.openggf.mods.NativeUnsupportedMods;
import org.junit.jupiter.api.Test;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class TestNativeModNoticeLines {
    @Test
    void screenBudgetProducesTruncation() {
        var names = IntStream.range(0, NativeModNoticeScreen.MAX_VISIBLE_MOD_LINES + 3)
                .mapToObj(i -> "mod" + i).toList();
        var lines = NativeUnsupportedMods.noticeLines(names, NativeModNoticeScreen.MAX_VISIBLE_MOD_LINES);
        assertEquals(NativeModNoticeScreen.MAX_VISIBLE_MOD_LINES + 1, lines.size()); // header + budget
        assertTrue(lines.get(lines.size() - 1).endsWith(" more"));
    }
}
```

- [ ] **Step 4: Create `NativeModNoticeScreen`** (mirror `LegalDisclaimerScreen`)

```java
package com.openggf.game;

import com.openggf.control.InputHandler;
import com.openggf.graphics.FadeManager;
import com.openggf.graphics.PixelFont;
import com.openggf.graphics.PngTextureLoader;
import com.openggf.graphics.TexturedQuadRenderer;
import com.openggf.mods.NativeUnsupportedMods;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

import static org.lwjgl.opengl.GL11.*;

/**
 * Boot screen shown on a native build when enabled code-bearing mods cannot load.
 * Mirrors {@link LegalDisclaimerScreen}'s lifecycle: fade-from-black reveal,
 * dismiss on any input, fade-to-black on dismiss. Purely informational.
 */
public final class NativeModNoticeScreen {
    private static final Logger LOGGER = Logger.getLogger(NativeModNoticeScreen.class.getName());
    public static final int MAX_VISIBLE_MOD_LINES = 12;
    private static final int SCREEN_W = 320;
    private static final int SCREEN_H = 224;
    private static final float BODY_SCALE = 1f;

    private final FadeManager fadeManager;
    private final List<String> noticeLines; // header + (truncated) mod names
    private boolean dismissRequested;
    private boolean dismissed;
    private boolean fadingOut;

    private TexturedQuadRenderer renderer;
    private PixelFont font;
    private int solidWhiteTextureId;

    public NativeModNoticeScreen(FadeManager fadeManager, List<String> modDisplayNames) {
        this.fadeManager = Objects.requireNonNull(fadeManager, "fadeManager");
        this.noticeLines = NativeUnsupportedMods.noticeLines(
                List.copyOf(modDisplayNames), MAX_VISIBLE_MOD_LINES);
    }

    public void initialize() {
        try {
            renderer = new TexturedQuadRenderer();
            renderer.init();
            font = new PixelFont();
            font.init("pixel-font.png", renderer);
            solidWhiteTextureId = createSolidWhiteTexture();
            fadeManager.startFadeFromBlack(null);
            LOGGER.info("Native mod notice screen initialized");
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize native mod notice screen", e);
        }
    }

    public void update(InputHandler inputHandler) {
        if (dismissed) return;
        if (!fadingOut && inputHandler.isAnyKeyJustPressed()) {
            fadingOut = true;
            fadeManager.startFadeToBlack(() -> dismissed = true);
        }
    }

    public boolean isDismissed() {
        return dismissed;
    }

    public void draw() {
        if (renderer == null) return;
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        renderer.drawTexture(solidWhiteTextureId, 0, 0, SCREEN_W, SCREEN_H, 0f, 0f, 0f, 1f);
        font.beginMegaBatch();
        int y = 40;
        for (String line : noticeLines) {
            int x = (SCREEN_W - font.measureWidth(line, BODY_SCALE)) / 2;
            font.drawText(line, x, y, BODY_SCALE, 0.95f, 0.95f, 0.95f, 1f);
            y += 12;
        }
        font.drawTextCentered("Press any key to continue", SCREEN_W, SCREEN_H - 20,
                0.8f, 0.8f, 0.8f, 1f);
        font.endMegaBatch();
    }

    public void setProjectionMatrix(float[] projectionMatrix) {
        if (renderer != null && projectionMatrix != null) {
            renderer.setProjectionMatrix(projectionMatrix);
        }
    }

    public void cleanup() {
        if (font != null) font.cleanup();
        PngTextureLoader.deleteTexture(solidWhiteTextureId);
        if (renderer != null) renderer.cleanup();
    }

    private static int createSolidWhiteTexture() {
        // Copy the exact implementation of LegalDisclaimerScreen.createSolidWhiteTexture()
        // (a 1x1 white RGBA texture). Extract to a shared helper only if trivial.
        throw new UnsupportedOperationException("copy from LegalDisclaimerScreen.createSolidWhiteTexture()");
    }
}
```

**Implementation note:** copy `createSolidWhiteTexture()` verbatim from `LegalDisclaimerScreen` (lines 144-155). If you prefer not to duplicate, extract it to a small package-visible helper and call it from both — but a verbatim copy is acceptable and lower-risk here. Verify the exact `FadeManager` / `PixelFont` / `TexturedQuadRenderer` method names against `LegalDisclaimerScreen`.

- [ ] **Step 5: `BootScreenModeController` update method + `handles` parity**

Add to `handles` (line 33-35):

```java
    public boolean handles(GameMode mode) {
        return mode == GameMode.LEGAL_DISCLAIMER || mode == GameMode.MASTER_TITLE_SCREEN
                || mode == GameMode.NATIVE_MOD_NOTICE;
    }
```

Add a sibling update method (mirror `updateLegalDisclaimer`, lines 48-58):

```java
    public void updateNativeModNotice(com.openggf.game.NativeModNoticeScreen screen,
                                      InputHandler inputHandler,
                                      Runnable onDismissed) {
        if (screen != null) {
            screen.update(inputHandler);
            if (screen.isDismissed() && onDismissed != null) {
                onDismissed.run();
            }
        }
        inputHandler.update();
    }
```

Update `TestBootScreenModeController` (if it asserts the `handles` set) to include `NATIVE_MOD_NOTICE`.

- [ ] **Step 6: `GameLoop` supplier + exit handler + dispatch**

Add fields near lines 176-177:

```java
    private java.util.function.Supplier<com.openggf.game.NativeModNoticeScreen> nativeModNoticeSupplier;
    private Runnable nativeModNoticeExitHandler;
```

Add setters (near the legal-disclaimer setters ~583-588):

```java
    public void setNativeModNoticeScreenSupplier(
            java.util.function.Supplier<com.openggf.game.NativeModNoticeScreen> supplier) {
        this.nativeModNoticeSupplier = supplier;
    }

    public void setNativeModNoticeExitHandler(Runnable handler) {
        this.nativeModNoticeExitHandler = handler;
    }
```

Add a dispatch block immediately after the `LEGAL_DISCLAIMER` block (after line 984):

```java
        if (currentGameMode == GameMode.NATIVE_MOD_NOTICE) {
            bootScreenModeController.updateNativeModNotice(
                    nativeModNoticeSupplier != null ? nativeModNoticeSupplier.get() : null,
                    inputHandler,
                    () -> {
                        if (nativeModNoticeExitHandler != null) {
                            nativeModNoticeExitHandler.run();
                            nativeModNoticeExitHandler = null;
                        }
                    });
            return;
        }
```

- [ ] **Step 7: `Engine` — screen field, registration, draw hook**

Add field near `legalDisclaimerScreen` (line 239):

```java
	private com.openggf.game.NativeModNoticeScreen nativeModNoticeScreen;
```

Register supplier + exit handler near lines 303-304:

```java
		this.gameLoop.setNativeModNoticeScreenSupplier(() -> nativeModNoticeScreen);
		this.gameLoop.setNativeModNoticeExitHandler(this::exitNativeModNotice);
```

Add the draw hook. In `EngineDrawActions` (near line 2977):

```java
		@Override public void nativeModNotice() { drawNativeModNotice(); }
```

And the private draw method (mirror `drawLegalDisclaimer`, 3295-3301):

```java
	private void drawNativeModNotice() {
		resetCameraForScreenSpaceIfPresent();
		if (nativeModNoticeScreen != null) {
			nativeModNoticeScreen.setProjectionMatrix(getProjectionMatrixBuffer());
			nativeModNoticeScreen.draw();
		}
	}
```

- [ ] **Step 8: `Engine` — boot-chain insertion (both paths)**

Refactor the "go to master title or game" logic into one reusable method and insert the notice check. First, extract from `exitLegalDisclaimer` (1173-1182) a method:

```java
	private void proceedToMasterTitleOrGame(boolean fadeFromBlack) {
		boolean masterTitleOnStartup = configService.getBoolean(SonicConfiguration.MASTER_TITLE_SCREEN_ON_STARTUP);
		if (masterTitleOnStartup) {
			masterTitleScreen = createMasterTitleScreen();
			masterTitleScreen.initialize();
			gameLoop.setGameMode(GameMode.MASTER_TITLE_SCREEN);
		} else {
			initializeGame();
		}
		if (fadeFromBlack) {
			graphicsManager.getFadeManager().startFadeFromBlack(null);
		}
	}
```

Add the notice-or-proceed gate:

```java
	private void enterBootModNoticeOrProceed(boolean fadeFromBlackWhenNoNotice) {
		java.util.List<com.openggf.mods.ModDescriptor> unsupported =
				com.openggf.mods.NativeUnsupportedMods.compute(
						ModSubsystem.current().processCatalog().scanned(),
						ModSubsystem.current().startupModState(),
						compiledModsSupported);
		if (unsupported.isEmpty()) {
			proceedToMasterTitleOrGame(fadeFromBlackWhenNoNotice);
			return;
		}
		java.util.List<String> names = unsupported.stream()
				.map(d -> d.manifest().name()).toList();
		LOGGER.warning(com.openggf.mods.NativeUnsupportedMods.NOTICE_HEADER + " "
				+ String.join(", ", unsupported.stream().map(d -> d.manifest().id()).toList()));
		nativeModNoticeScreen = new com.openggf.game.NativeModNoticeScreen(
				graphicsManager.getFadeManager(), names);
		nativeModNoticeScreen.initialize();
		gameLoop.setNativeModNoticeExitHandler(this::exitNativeModNotice);
		gameLoop.setGameMode(GameMode.NATIVE_MOD_NOTICE);
	}

	private void exitNativeModNotice() {
		if (nativeModNoticeScreen != null) {
			nativeModNoticeScreen.cleanup();
			nativeModNoticeScreen = null;
		}
		proceedToMasterTitleOrGame(true);
	}
```

Now rewrite `exitLegalDisclaimer` (1167-1183) to route through the gate (preserving today's fade-from-black on the disclaimer-on path):

```java
	public void exitLegalDisclaimer() {
		if (legalDisclaimerScreen != null) {
			legalDisclaimerScreen.cleanup();
			legalDisclaimerScreen = null;
		}
		enterBootModNoticeOrProceed(true);
	}
```

And rewrite the disclaimer-off boot branch (537-544) to route through the gate (preserving today's no-fade behavior when the notice does not show):

```java
		} else if (masterTitleOnStartup) {
			enterBootModNoticeOrProceed(false);
		} else {
			enterBootModNoticeOrProceed(false);
		}
```

Note: both disclaimer-off branches now call the same gate; `proceedToMasterTitleOrGame` re-reads `MASTER_TITLE_SCREEN_ON_STARTUP` and decides master-title vs. game, so the two branches collapse to one call. Simplify to a single `else { enterBootModNoticeOrProceed(false); }` replacing lines 537-544's `else if`/`else`, keeping the `showLegalDisclaimer` branch (530-536) unchanged.

**Boot-ordering check (critical):** `enterBootModNoticeOrProceed` reads `ModSubsystem.current()`, so `initializeExternalContentAtBoot()` MUST have run before the boot decision at 526-545 and before `exitLegalDisclaimer`. Verify the call order in `Engine.init()` (search for `initializeExternalContentAtBoot()`); if it currently runs after line 545, move the notice computation to just after it, or ensure the mod subsystem is installed before the boot-screen decision. Do not compute the notice against the default disabled subsystem.

- [ ] **Step 9: Compile + run the isolated tests**

Run: `mvn -Dmse=off -DskipTests compile`
Expected: SUCCESS.

Run (sandbox off): `mvn "-Dtest=com.openggf.render.TestEngineRenderDispatcherNativeNotice,com.openggf.game.TestNativeModNoticeLines,com.openggf.game.mode.TestBootScreenModeController" test`
Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add src/main/java/com/openggf/game/GameMode.java src/main/java/com/openggf/game/NativeModNoticeScreen.java src/main/java/com/openggf/render/EngineRenderDispatcher.java src/main/java/com/openggf/game/mode/BootScreenModeController.java src/main/java/com/openggf/GameLoop.java src/main/java/com/openggf/Engine.java src/test/java/com/openggf/render/TestEngineRenderDispatcher.java src/test/java/com/openggf/render/TestEngineRenderDispatcherNativeNotice.java src/test/java/com/openggf/game/TestNativeModNoticeLines.java
# include any TestBootScreenModeController update
git commit  # Changelog: updated
```

---

## Task 7: Documentation + changelog

**Files:**
- Modify: `docs/modding/index.md`, `docs/modding/content-mods.md`, `docs/modding/troubleshooting.md`, `CHANGELOG.md`

- [ ] **Step 1: `docs/modding/index.md`** — add a short "Native builds" subsection near the top run/enable instructions:

```markdown
## Native builds vs. the JVM jar

Code-bearing mods (objects, characters, zones, standalone games) require the
**JVM jar** — they are loaded at runtime by the engine's mod classloader, which
the GraalVM native-image binary cannot do (closed-world AOT). On a native build
these mods are not loaded, the Mod Manager shows them as `UNSUPPORTED` and refuses
to enable them, and a boot notice lists any that were enabled. **Data-only music
packs and reskins are unaffected and work on native builds.** To use code-bearing
mods, run `OpenGGF-<ver>-jar-with-dependencies.jar` (or the universal jar).
```

- [ ] **Step 2: `docs/modding/content-mods.md`** — add the same note (2-3 sentences) near the run/enable section.

- [ ] **Step 3: `docs/modding/troubleshooting.md`** — add a row to the findings table:

```markdown
| `NATIVE_UNSUPPORTED` | This code-bearing mod cannot load on a native build. Run the JVM jar (`OpenGGF-<ver>-jar-with-dependencies.jar`) to use it. Data-only music/reskin mods are unaffected. |
```

- [ ] **Step 4: `CHANGELOG.md`** — add an entry under the current prerelease section (check CRLF: this file is CRLF — keep line endings consistent, see repo note on Edit flipping CRLF→LF; prefer appending via an editor that preserves CRLF or verify `git diff` shows only the added lines):

```
- Native builds now detect that code-bearing mods cannot load under GraalVM
  native-image: such mods are skipped, shown as UNSUPPORTED in the Mod Manager
  (and cannot be enabled), and listed in a boot notice. Data-only music/reskin
  mods are unaffected.
```

- [ ] **Step 5: Commit**

```bash
git add docs/modding/index.md docs/modding/content-mods.md docs/modding/troubleshooting.md CHANGELOG.md
git commit  # Guide: updated, Changelog: updated, others n/a
```

---

## Task 8: Full verification

- [ ] **Step 1: Full compile**

Run: `mvn -Dmse=off -DskipTests compile`
Expected: SUCCESS.

- [ ] **Step 2: Run the new + adjacent tests (sandbox OFF)**

Run:
```
mvn "-Dtest=com.openggf.mods.TestNativeUnsupportedMods,com.openggf.mods.code.TestModClassLoaderFactoryNativeGuard,com.openggf.mods.ui.TestModManagerScreenNativeGuard,com.openggf.render.TestEngineRenderDispatcherNativeNotice,com.openggf.game.TestNativeModNoticeLines" test
```
Expected: all PASS.

- [ ] **Step 3: Run existing mod + boot-screen suites for regressions**

Run: `mvn "-Dtest=com.openggf.mods.*,com.openggf.mods.ui.*,com.openggf.mods.code.*,com.openggf.game.mode.*" test`
Expected: PASS (default the flag to `true` everywhere means legacy behavior is unchanged).

- [ ] **Step 4: Guard/architecture tests**

Run any ArchUnit/guard tests that could be affected by the new classes (e.g. package-boundary guards). If a guard freezes on the new `mods` class, confirm `NativeUnsupportedMods` has no `Engine`/`ModSubsystem` import and re-run. Do not weaken a guard baseline without documenting the reason.

- [ ] **Step 5: Manual native/JVM sanity (documented, not automated)**

**Coverage note (from the spec's Testing section):** the boot-chain insert/skip decision itself (`enterBootModNoticeOrProceed` inserting `NATIVE_MOD_NOTICE` only when the list is non-empty, and never on a JVM boot) lives in GL-bound `Engine` code and is **manual-only** here — the automated coverage is `NativeUnsupportedMods.compute` emptiness (Task 1) plus the dispatcher arms (Task 6). This is a deliberate scope decision, not an oversight.

Also note the accepted edge case (spec + review): if a code mod is *already* enabled in persisted config from a prior JVM run and the user enables a data-only dependent on native, the cascade guard (which only sees the *disabled* dependency closure) will not block it; the dependency then fails to load at runtime (`DEPENDENCY_UNAVAILABLE`). This is reversible and corrupts no persisted state — accepted.

Because the guard is native-only, record in the PR description:
- JVM run: mods behave exactly as before (build+run `mvn -Dmse=off package`, launch the jar with a code mod enabled → loads).
- Native behavior is exercised by the unit tests via the `compiledModsSupported=false` path; a full native-image build (`mvn -Pnative`) is optional but ideal if the toolchain is available — with an enabled code mod, confirm the boot notice appears and the Mod Manager shows `UNSUPPORTED`.

- [ ] **Step 6: Final commit / branch ready for review**

Ensure the working tree is clean and all trailers are correct. The branch is ready for review/merge into `next`.

---

## Self-Review Notes (author)

- **Spec coverage:** Goals 1-4 map to Tasks 6 (notice+console), 4 (manager gate), 5 (standalone suppression), 7 (docs). Decisions 1-5 map to: D1 runtime-only (no `ModStateStore` writes anywhere in the plan); D2 flag threaded (Tasks 3,4,5,6); D3 factory backstop ordering (Task 2); D4 catalog+`ModState` helper (Tasks 1,3); D5 boot-chain reuse (Task 6).
- **Startup-state exposure:** resolved — Task 3 adds `ModSubsystem.startupModState()` because no getter existed.
- **Type consistency:** helper signature `compute(List<ModCatalogEntry>, ModState, boolean)` is identical in Tasks 1, 3, 6. `RowView` gains `notLoaded` (Task 4) and every constructor site is updated in the same task. `create(..., boolean)` (Task 2) matches the Engine call (Task 5).
- **Known soft spots for the implementer:** (a) exact `ModManifest`/`ModDescriptor` record arity in test fixtures — verify against source; (b) Mod Manager test fixture construction depends on existing test helpers — locate and reuse; (c) boot-ordering of `initializeExternalContentAtBoot()` vs. the boot-screen decision — verify in `Engine.init()`; (d) `createSolidWhiteTexture()` copy from `LegalDisclaimerScreen`.
