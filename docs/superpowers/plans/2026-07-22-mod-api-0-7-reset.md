# Mod API 0.7 Baseline Reset Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the unreleased Mod API 1.x/2.x lineage with one clean, pinned Mod API 0.7 contract.

**Architecture:** Keep the existing recursive `@ModApi` discovery and packaging machinery, but point every current consumer at canonical SemVer `0.7.0` and a single `mod-api-signatures-0.7.txt` pin. Remove only API members demonstrably retained for provisional compatibility, migrate engine/test callers to canonical shapes, and make active documentation describe 0.7 without rewriting genuinely historical plans.

**Tech Stack:** Java 21, JUnit 5, Maven, PowerShell, `rg`, checked-in canonical signature text.

---

## File map

- Version authority: `src/main/java/com/openggf/mods/ModApiVersion.java`.
- Range consumers: SDK templates, maintained sample manifests/properties, and their JUnit assertions under `src/test`.
- Compatibility removal: the explicitly marked constructors/aliases in rules, rewind snapshots, trace metadata, playable control, `ModZoneContribution`, `ObjectManager`, and `SpriteManager`.
- Signature authority: `src/test/java/com/openggf/mods/TestModApiSignatureSurface.java` and `src/test/resources/mods/mod-api-signatures-0.7.txt`.
- Active documentation: `AGENTS.md`, `CLAUDE.md`, `README.md`, `CHANGELOG.md`, `docs/architecture/*.md`, and `docs/modding/**`.
- Historical evidence: only the exact dated plans/specs listed in the rewritten compatibility guide; they must not be cited as current version authority.

Preserve the user's unrelated `docs/rewind/real-gaps.md` and `HANDOFF-mhz-trace-2026-07-18.md` changes throughout. Stage files by exact path, never with `git add .`.

### Task 1: Establish the 0.7 version and range contract

**Files:**
- Modify: `src/test/java/com/openggf/mods/TestSemanticVersionAndRange.java`
- Modify: `src/test/java/com/openggf/tools/modsdk/TestSampleModsPackage.java`
- Modify: `src/main/java/com/openggf/mods/ModApiVersion.java`
- Modify: `src/main/resources/META-INF/openggf-mod-sdk/templates/README.md.template`
- Modify: `src/main/resources/META-INF/openggf-mod-sdk/templates/openggf-mod.yaml.template`
- Modify: `docs/modding/samples/phase4-gallery-music-pack/META-INF/openggf-mod.yaml`
- Modify: `src/test/resources/mods/sample-character-src/project/src/main/resources/META-INF/openggf-mod.yaml`
- Modify: `src/test/resources/mods/sample-flappy-src/sample.properties`
- Modify: `src/test/resources/mods/sample-flappy-src/project/src/main/resources/META-INF/openggf-mod.yaml`
- Modify: `src/test/resources/mods/sample-mod-src/project/src/main/resources/META-INF/openggf-mod.yaml`
- Modify: `src/test/resources/mods/sample-platformer-src/project/src/main/resources/META-INF/openggf-mod.yaml`
- Modify: `src/test/resources/mods/sample-reskin-src/META-INF/openggf-mod.yaml`
- Modify: `src/test/resources/mods/sample-rom-art-remix-src/project/src/main/resources/META-INF/openggf-mod.yaml`
- Modify: `src/test/resources/mods/sample-standalone-src/project/src/main/resources/META-INF/openggf-mod.yaml`
- Modify: `src/test/resources/mods/dynamic-rewind-src/META-INF/openggf-mod.yaml`
- Modify current-API fixtures in: `src/test/java/com/openggf/mods/TestDevelopmentModSource.java`, `TestEffectiveCatalogBuilder.java`, `TestNativeUnsupportedMods.java`, `TestModManifestParser.java`, `src/test/java/com/openggf/tools/modsdk/TestJarPackager.java`, and `TestModJarValidator.java`

- [ ] **Step 1: Change the contract assertions first**

Replace the current-version assertions with:

```java
@Test
void currentApiVersionIsZeroSeven() {
    assertEquals(new SemanticVersion(0, 7, 0), ModApiVersion.CURRENT);
    assertTrue(VersionRange.parse(">=0.7.0 <0.8.0").contains(ModApiVersion.CURRENT));
    assertFalse(VersionRange.parse(">=0.6.0 <0.7.0").contains(ModApiVersion.CURRENT));
}
```

Set every value in `TestSampleModsPackage.EXPECTED_API_RANGES` to
`">=0.7.0 <0.8.0"`. Do not rewrite generic dependency-range parser examples; only
fixtures whose field is `engineApiRange` represent the current Mod API.

- [ ] **Step 2: Run the focused tests and confirm the old contract fails**

Run:

```powershell
mvn -q "-Dtest=com.openggf.mods.TestSemanticVersionAndRange,com.openggf.tools.modsdk.TestSampleModsPackage" test
```

Expected: FAIL because `ModApiVersion.CURRENT` is `2.5.0` and maintained samples
still declare provisional ranges.

- [ ] **Step 3: Replace the version authority with one clean declaration**

Make `ModApiVersion.java` contain this contract instead of the lineage Javadoc:

```java
package com.openggf.mods;

public final class ModApiVersion {
    /**
     * Current compiled-mod compatibility contract.
     *
     * <p>Mod API 0.7 is the first release baseline. Earlier 1.x/2.x values were
     * provisional development markers and carry no compatibility promise.
     */
    public static final SemanticVersion CURRENT = SemanticVersion.parse("0.7.0");

    private ModApiVersion() {
    }
}
```

- [ ] **Step 4: Normalize maintained ranges and current-API fixtures**

Use `engineApiRange: ">=0.7.0 <0.8.0"` in YAML and
`engineApiRange=>=0.7.0 <0.8.0` in `sample.properties`. Change template/sample prose
to “Mod API 0.7”. Update current-API test fixture strings to the same range while
leaving deliberately invalid ranges and ordinary mod-dependency examples unchanged.

- [ ] **Step 5: Re-run the version and sample tests**

Run:

```powershell
mvn -q "-Dtest=com.openggf.mods.TestSemanticVersionAndRange,com.openggf.tools.modsdk.TestSampleModsPackage,com.openggf.mods.TestModManifestParser,com.openggf.tools.modsdk.TestJarPackager,com.openggf.tools.modsdk.TestModJarValidator" test
```

Expected: PASS.

- [ ] **Step 6: Commit the version/range reset**

Stage only the files in this task and commit with:

```text
fix(mods): reset current API contract to 0.7

Changelog: n/a: aggregate reset entry follows with active documentation
Guide: n/a: guide normalization follows
Known-Discrepancies: n/a
S3K-Known-Discrepancies: n/a
Agent-Docs: n/a
Configuration-Docs: n/a
Skills: n/a
```

### Task 2: Remove provisional compatibility-only members

**Files:**
- Create: `src/test/java/com/openggf/mods/TestNoProvisionalModApiShims.java`
- Modify: `src/main/java/com/openggf/trace/TraceMetadata.java`
- Modify: `src/main/java/com/openggf/sprites/managers/PlayableSpriteMovement.java`
- Modify: `src/main/java/com/openggf/sprites/managers/SpriteManager.java`
- Modify: `src/main/java/com/openggf/sprites/playable/PlayableSpriteController.java`
- Modify: `src/main/java/com/openggf/sprites/playable/AbstractPlayableSprite.java`
- Modify: `src/main/java/com/openggf/game/CheckpointState.java`
- Modify: `src/main/java/com/openggf/game/rules/CollisionRules.java`
- Modify: `src/main/java/com/openggf/game/rules/ObjectInteractionRules.java`
- Modify: `src/main/java/com/openggf/game/rules/PlayerAnimationRules.java`
- Modify: `src/main/java/com/openggf/game/rules/PlayerCapabilityRules.java`
- Modify: `src/main/java/com/openggf/game/rules/RingRules.java`
- Modify: `src/main/java/com/openggf/game/rewind/snapshot/CameraSnapshot.java`
- Modify: `src/main/java/com/openggf/game/rewind/snapshot/GameStateSnapshot.java`
- Modify: `src/main/java/com/openggf/game/rewind/snapshot/WaterSystemSnapshot.java`
- Modify: `src/main/java/com/openggf/level/objects/PerObjectRewindSnapshot.java`
- Modify: `src/main/java/com/openggf/level/objects/ObjectManager.java`
- Modify: `src/main/java/com/openggf/mods/code/ModZoneContribution.java`
- Delete: `src/test/java/com/openggf/sprites/playable/TestLegacyMgzCarryAliases.java`
- Modify affected constructor/method callers under `src/main/java`, `src/test/java`, and maintained sample Java sources.

- [ ] **Step 1: Add a failing source/reflection guard for the exact shim classes**

Create `TestNoProvisionalModApiShims.java` with this structure:

```java
package com.openggf.mods;

import com.openggf.mods.code.ModZoneContribution;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestNoProvisionalModApiShims {
    private static final List<String> PROVISIONAL_MARKERS = List.of(
            "Compatibility constructor for API 1.",
            "Compatibility constructor for API 2.",
            "Binary-compatible constructor for the Mod API 2.4",
            "Binary-compatible constructor for Mod API 2.4",
            "Compatibility overload for API 1.1",
            "Historical Mod API 2.4 view");

    @Test
    void productionSourcesContainNoProvisionalCompatibilityMarkers() throws IOException {
        try (var paths = Files.walk(Path.of("src/main/java"))) {
            List<Path> offenders = paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> containsMarker(path))
                    .toList();
            assertEquals(List.of(), offenders);
        }
    }

    @Test
    void namedAliasesAndZoneOverloadAreGone() {
        assertThrows(NoSuchFieldException.class, () ->
                AbstractPlayableSprite.class.getDeclaredField("mgzTopPlatformCarrySolidContactObject"));
        assertThrows(NoSuchFieldException.class, () ->
                AbstractPlayableSprite.class.getDeclaredField("mgzTopPlatformSpringHandoffPending"));
        long publicConstructors = java.util.Arrays.stream(ModZoneContribution.class.getDeclaredConstructors())
                .filter(constructor -> Modifier.isPublic(constructor.getModifiers()))
                .count();
        assertEquals(1, publicConstructors);
    }

    private static boolean containsMarker(Path path) {
        try {
            String source = Files.readString(path);
            return PROVISIONAL_MARKERS.stream().anyMatch(source::contains);
        } catch (IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }
}
```

Add the remaining three MGZ alias field names to `namedAliasesAndZoneOverloadAreGone`
using the same `assertThrows` form.

- [ ] **Step 2: Run the guard and confirm it fails on the inventoried shims**

Run:

```powershell
mvn -q "-Dtest=com.openggf.mods.TestNoProvisionalModApiShims" test
```

Expected: FAIL listing the marked production files and the legacy MGZ fields.

- [ ] **Step 3: Delete only the evidence-backed overload inventory**

Remove the constructors whose comments identify provisional API compatibility from:

```text
TraceMetadata
PlayableSpriteMovement.RewindState
SpriteManager.drawUnifiedBucketWithPriority (four-argument overload)
PlayableSpriteController.RewindState
CheckpointState.RewindState
CollisionRules (API 1.1 flat form and Mod API 2.4 flat form)
ObjectInteractionRules
PlayerAnimationRules
PlayerCapabilityRules
RingRules (both compatibility forms)
CameraSnapshot
GameStateSnapshot
WaterSystemSnapshot.DynamicWaterEntry
PerObjectRewindSnapshot.SidekickCpuRewindExtra
PerObjectRewindSnapshot.PlayerRewindExtra (all four pre-canonical overloads at the
current source blocks beginning near lines 548, 636, 725, and 809)
```

Also remove the four-argument `ModZoneContribution` constructor and migrate every
caller to the canonical five-argument form with an explicit final `false` unless the
call already represents a game-start contribution.

Do not remove unrelated configuration, file-format, editor-save, ROM, or runtime
compatibility helpers.

- [ ] **Step 4: Collapse MGZ carry state onto its canonical controller owner**

Remove the four deprecated `mgzTopPlatform*` fields and the
`syncControllerFromLegacyMgzAliases` / `clearMgzTopPlatformCarryCompatibilityState`
methods. Keep the public gameplay methods, but make them delegate directly:

```java
public void setMgzTopPlatformCarrySolidContactObject(ObjectInstance instance) {
    controller.setObjectControlledSolidContactOwner(instance);
}

public boolean isMgzTopPlatformCarryOwnedBy(ObjectInstance instance) {
    return controller.isObjectControlledSolidContactOwnedBy(instance);
}

public void recordMgzTopPlatformSpringHandoff(int xVel, int yVel) {
    controller.recordSpringHandoff(xVel, yVel);
}

public boolean hasMgzTopPlatformSpringHandoffPending() {
    return controller.isSpringHandoffPending();
}
```

Use the controller getters for rewind capture, restore only through
`controller.restoreSpringHandoff(...)`, clear through `controller.clearSpringHandoff()`,
and use `controller.hasObjectControlledSolidContactOwner()` for hurt suppression.
Delete the legacy-alias test; keep and update the controller/rewind behavior tests.

- [ ] **Step 5: Remove the historical transition-object view**

Delete `ObjectManager.snapshotPersistentDynamicObjectsForTransition()`. Migrate tests
to `snapshotPersistentTransitionOccupants()` and assert on each occupant's
`identity()` and `slotIndex()` instead of discarding slot identity. Update
`TestSeamlessCarryExcludesBossChildren` Javadoc and remove the old method token from
`TestArchitecturalSourceGuard`.

- [ ] **Step 6: Compile to find and migrate every canonical-constructor caller**

Run:

```powershell
mvn -q -DskipTests test-compile
```

Expected initially: compilation failures naming callers of deleted overloads. Update
each caller by supplying the current canonical record components (using the same
defaults previously supplied inside the deleted delegating overload), then repeat
until compilation succeeds. Do not recreate any deleted overload under a new name.

- [ ] **Step 7: Run shim, rewind, rule, lifecycle, and mod-zone tests**

Run:

```powershell
mvn -q "-Dtest=com.openggf.mods.TestNoProvisionalModApiShims,com.openggf.sprites.playable.TestAbstractPlayableSpriteRewindCapture,com.openggf.level.objects.TestObjectManagerLifecycle,com.openggf.level.objects.TestSeamlessCarryExcludesBossChildren,com.openggf.mods.code.TestModZoneLoader,com.openggf.mods.code.TestModGameStartResolver,com.openggf.tests.game.TestPerGameRuleArchitectureGuard" test
```

Expected: PASS.

- [ ] **Step 8: Commit the shim removal**

Use subject `refactor(mods): remove provisional API compatibility shims`. Because
this changes `src/main`, stage `CHANGELOG.md` only in Task 4; for this intermediate
commit use `Changelog: n/a: aggregate Mod API 0.7 reset entry follows` and the
remaining required trailers as `n/a` with the same scoped reasons used in Task 1.

### Task 3: Replace the signature lineage with one 0.7 pin

**Files:**
- Modify: `src/test/java/com/openggf/mods/TestModApiSignatureSurface.java`
- Create: `src/test/resources/mods/mod-api-signatures-0.7.txt`
- Delete: `src/test/resources/mods/mod-api-signatures-1.1.txt`
- Delete: `src/test/resources/mods/mod-api-signatures-1.2.txt`
- Delete: `src/test/resources/mods/mod-api-signatures-2.0.txt`
- Delete: `src/test/resources/mods/mod-api-signatures-2.1.txt`
- Delete: `src/test/resources/mods/mod-api-signatures-2.2.txt`
- Delete: `src/test/resources/mods/mod-api-signatures-2.3.txt`
- Delete: `src/test/resources/mods/mod-api-signatures-2.4.txt`
- Delete: `src/test/resources/mods/mod-api-signatures-2.5.txt`

- [ ] **Step 1: Rewrite the pin test before generating the baseline**

Keep the annotation-closure, curated-root, classfile-annotation, platform-allowlist,
and engine-internal leak tests. Replace every lineage constant/test with:

```java
private static final String PUBLISHED_BASELINE = "mods/mod-api-signatures-0.7.txt";
private static final SemanticVersion PUBLISHED_VERSION = new SemanticVersion(0, 7, 0);

@Test
void publishedZeroSevenSurfaceIsPinnedToTheCurrentSurface() throws Exception {
    List<String> published = readBaseline(PUBLISHED_BASELINE);
    assertEquals(new ArrayList<>(new TreeSet<>(published)), published,
            "Published API 0.7 baseline must be unique, sorted canonical UTF-8 text");
    assertEquals(new ArrayList<>(ModApiSignatureSurface.snapshotLines()), published,
            "Review current changes and regenerate mod-api-signatures-0.7.txt");
    assertEquals(PUBLISHED_VERSION, ModApiVersion.CURRENT,
            "The current Mod API version must match the 0.7 baseline");
}
```

Rename `postTwoFourRuntimeHelpersDoNotLeakIntoTheCreatorSurface` to
`engineOnlyRuntimeHelpersDoNotLeakIntoTheZeroSevenSurface`. Retain its negative
signature assertions with version-neutral messages.

Keep the generic `baselineViolations` unit test but use baseline `0.7.0`, additive
current `0.8.0`, same-version `0.7.0`, wrong-major `1.0.0`, and a removal case.

- [ ] **Step 2: Run the test and confirm the new baseline is missing**

Run:

```powershell
mvn -q "-Dtest=com.openggf.mods.TestModApiSignatureSurface" test
```

Expected: ERROR/FAIL with `Missing mods/mod-api-signatures-0.7.txt`.

- [ ] **Step 3: Delete provisional pins and generate the deterministic 0.7 pin**

Delete the eight 1.x/2.x files with `apply_patch`, then run:

```powershell
mvn -q "-DskipTests" compile
mvn -q dependency:build-classpath "-Dmdep.outputFile=target/mod-api-snapshot-classpath.txt"
$modApiCp = "target/classes;$((Get-Content target/mod-api-snapshot-classpath.txt -Raw).Trim())"
java -cp $modApiCp com.openggf.mods.code.ModApiSignatureSurface --snapshot |
    Set-Content -Encoding utf8NoBOM src/test/resources/mods/mod-api-signatures-0.7.txt
```

- [ ] **Step 4: Run all signature and SDK surface gates**

Run:

```powershell
mvn -q "-Dtest=com.openggf.mods.TestModApiSignatureSurface,com.openggf.tools.modsdk.TestModApiJavadocTool,com.openggf.tools.modsdk.TestModApiSdkPackager" test
```

Expected: PASS.

- [ ] **Step 5: Prove no provisional pin remains and commit**

Run `rg --files src/test/resources/mods | rg "mod-api-signatures-"`; expected output
is exactly `mod-api-signatures-0.7.txt`. Commit as
`test(mods): pin the clean Mod API 0.7 surface` with required trailers; use the same
aggregate changelog justification as Task 2.

### Task 4: Rewrite active creator documentation and historical authority

**Files:**
- Modify: `AGENTS.md`
- Modify: `CLAUDE.md`
- Modify: `README.md`
- Modify: `CHANGELOG.md`
- Rewrite: `docs/architecture/mod-api-compatibility.md`
- Modify: `docs/architecture/per-game-rule-placement.md`
- Modify: `docs/architecture/archunit-exceptions.md`
- Modify all version-bearing active files under `docs/modding/**`, including
  `BACKLOG.md`, `characters.md`, `content-mods.md`, `formats/manifest.md`, `ggfmod.md`,
  `guides/native-tails-flappy.md`, `guides/rom-art-remix.md`,
  `guides/standalone-platformer.md`, `index.md`, `music-packs.md`,
  `quickstarts/object.md`, `samples/index.md`, and `standalone-games.md`.
- Modify version-bearing sample README/POM/Java prose under `src/test/resources/mods`.
- Modify version-bearing test names/messages/comments under `src/test/java`.

- [ ] **Step 1: Rewrite the compatibility guide around one baseline**

Replace the lineage/migration sections in `mod-api-compatibility.md` with:

```markdown
# Mod API compatibility

## Current contract

Mod API 0.7 (`0.7.0`) is the first release baseline. The exact recursive
`@ModApi` surface is pinned by
`src/test/resources/mods/mod-api-signatures-0.7.txt`. No compatibility is promised
for provisional development markers previously numbered 1.x or 2.x.

Code-bearing mods should declare:

```yaml
engineApiRange: ">=0.7.0 <0.8.0"
```

`formatVersion: 1` is the manifest wire-format version and is independent of the
Mod API version.
```

Retain the existing recursive-surface rules, annotation requirements, platform
allowlist policy, snapshot command (retargeted to `mod-api-signatures-0.7.txt`), SDK
packaging explanation, and engine-internal leak policy.

Add a “Reset inventory” table listing every member removed in Task 2 and its source
marker as evidence. Add a “Historical allowlist” naming these exact files only:

```text
docs/superpowers/specs/2026-07-10-mod-support-format-security-contracts.md
docs/superpowers/specs/2026-07-13-example-mods-design.md
docs/superpowers/specs/2026-07-14-flappy-native-tails-design.md
docs/superpowers/specs/2026-07-14-mod-gap-fixes-design.md
docs/superpowers/specs/2026-07-14-rom-art-remix-sample-design.md
docs/superpowers/specs/2026-07-14-s3k-mod-zone-adapter-design.md
docs/superpowers/specs/2026-07-22-mod-api-0-7-reset-design.md
docs/superpowers/plans/2026-07-13-mod-rom-art-intake.md
docs/superpowers/plans/2026-07-13-sample-flappy-mod.md
docs/superpowers/plans/2026-07-13-sample-platformer-mod.md
docs/superpowers/plans/2026-07-14-mod-gap-fixes.md
docs/superpowers/plans/2026-07-14-mod-gameplay-policies.md
docs/superpowers/plans/2026-07-14-native-tails-flappy.md
docs/superpowers/plans/2026-07-14-rom-art-remix-sample.md
docs/superpowers/plans/2026-07-14-s3k-mod-zone-adapter.md
```

State that these preserve development history and are not current version authority.

- [ ] **Step 2: Normalize active handbook prose to 0.7**

Replace feature-introduction phrasing such as “added in 2.2” with direct 0.7
capability phrasing such as “Mod API 0.7 exposes playable-subclass rewind hooks.”
Remove advice for pre-2.2 snapshots and 1.x/2.x engine ranges. Every maintained code
example uses `>=0.7.0 <0.8.0`.

In `docs/modding/BACKLOG.md`, label the linked design/plan corpus as historical scope
provenance and link `docs/architecture/mod-api-compatibility.md` as the only current
version authority.

- [ ] **Step 3: Update agent authority without preserving stale citations**

Update both `AGENTS.md` and `CLAUDE.md` together. Describe 0.7 as the current and first
baseline, summarize its accumulated creator capabilities without a version ladder,
and point API/format work at `docs/modding/index.md` plus
`docs/architecture/mod-api-compatibility.md`. Remove the phase-0/format-security spec
citations where they are presented as current Mod API contracts; historical rationale
may remain only when explicitly labeled historical.

- [ ] **Step 4: Consolidate changelog version narratives**

Add one current entry headed `Mod API 0.7 establishes the first creator contract` and
summarize the accumulated object, art, zone, character, standalone, rewind-hook, S3K
adapter, and gameplay-policy surface. Remove standalone “bumped to 2.0” and additive
2.1–2.5 publication entries, and neutralize feature entries that call provisional
versions published compatibility contracts. Preserve the actual feature history.

- [ ] **Step 5: Normalize code/test comments and sample metadata prose**

Rename version-specific tests/messages to behavior names. Examples:

```text
TestSamplePlatformerIntegration: “the 0.7 subclass rewind hooks”
TestPerGameRuleArchitectureGuard: “the pinned 0.7 CollisionRules shape”
TestArchUnitRules: “the creator-facing input-filter/ROM-art dependency”
sample-flappy README/POM: “Mod API 0.7”
sample-mod README: “Mod API 0.7 compatibility sample”
SpringPad Javadoc: describe available 0.7 surface directly
```

- [ ] **Step 6: Run documentation, link, SDK, and gallery tests**

Run:

```powershell
mvn -q "-Dtest=com.openggf.tools.modsdk.TestModApiJavadocTool,com.openggf.tools.modsdk.TestSampleModsPackage,com.openggf.mods.integration.TestPhase2SampleModIntegration,com.openggf.mods.integration.TestSamplePlatformerIntegration,com.openggf.mods.code.TestSampleFlappyRegistration" test
```

`TestSampleModsPackage` supplies the maintained-gallery path/reference checks.
Expected: PASS.

- [ ] **Step 7: Commit active documentation**

Commit as `docs(mods): publish the Mod API 0.7 contract`. Stage `CHANGELOG.md`, both
agent docs, all changed modding guides, and active architecture docs. Trailers must be:

```text
Changelog: updated
Guide: updated
Known-Discrepancies: n/a
S3K-Known-Discrepancies: n/a
Agent-Docs: updated
Configuration-Docs: n/a
Skills: n/a
```

### Task 5: Prove repository consistency

**Files:**
- Modify only if verification exposes a missed active reference or test expectation.

- [ ] **Step 1: Run the active-source stale-version scan**

Run:

```powershell
rg -n --glob '!target/**' `
  --glob '!docs/superpowers/plans/**' `
  --glob '!docs/superpowers/specs/**' `
  "Mod API (1\.[0-9]|2\.[0-9])|ModApi (1\.[0-9]|2\.[0-9])|mod-api-signatures-(1\.[0-9]|2\.[0-9])|engineApiRange:.*[12]\." `
  AGENTS.md CLAUDE.md README.md CHANGELOG.md docs src pom.xml
```

Expected: no output. Generic semantic-version/dependency-range fixtures are outside
this API-specific pattern and remain valid.

- [ ] **Step 2: Prove allowlisted documents are historical, not current authority**

Run:

```powershell
rg -n "2026-07-(10-mod-support-format-security-contracts|13-example-mods|14-(flappy-native-tails|mod-gap-fixes|rom-art-remix|s3k-mod-zone-adapter|mod-gameplay-policies))" `
  AGENTS.md CLAUDE.md README.md docs/modding docs/architecture
```

Expected: either no output or only the compatibility guide's explicit historical
allowlist and `BACKLOG.md`'s explicitly historical provenance paragraph. Any wording
that treats these files as current version policy must be removed.

- [ ] **Step 3: Run focused Mod API and creator-tool suites**

Run:

```powershell
mvn -q "-Dtest=com.openggf.mods.TestSemanticVersionAndRange,com.openggf.mods.TestModApiSignatureSurface,com.openggf.mods.TestNoProvisionalModApiShims,com.openggf.mods.TestModManifestParser,com.openggf.tools.modsdk.TestModApiJavadocTool,com.openggf.tools.modsdk.TestModApiSdkPackager,com.openggf.tools.modsdk.TestSampleModsPackage,com.openggf.tools.modsdk.TestJarPackager,com.openggf.tools.modsdk.TestModJarValidator,com.openggf.mods.integration.TestPhase2SampleModIntegration,com.openggf.mods.integration.TestSamplePlatformerIntegration" test
```

Expected: PASS with zero failures/errors.

- [ ] **Step 4: Run the broad Maven suite**

Run:

```powershell
mvn -q test
```

Expected: PASS. If unrelated pre-existing failures remain, capture the exact test
names/output and demonstrate that the focused Mod API suite and changed-area tests
are green; do not claim the broad suite passes.

- [ ] **Step 5: Review the final diff and working-tree ownership**

Run:

```powershell
git diff --check
git status --short
git diff --stat next...HEAD
git diff next...HEAD -- src/main/java/com/openggf/mods src/test/java/com/openggf/mods docs/modding docs/architecture AGENTS.md CLAUDE.md README.md CHANGELOG.md
```

Expected: no whitespace errors; the user's unrelated rewind/handoff files remain
unstaged and unmodified by this branch.

- [ ] **Step 6: Commit any verification-only corrections**

If Steps 1–5 required corrections, commit them as
`fix(mods): complete Mod API 0.7 consistency reset` with trailers matching the files
actually staged. If no correction was required, do not create an empty commit.

- [ ] **Step 7: Request final code review before integration**

Invoke `superpowers:requesting-code-review`, give the reviewer the design spec, this
plan, and `next...HEAD`, and resolve all actionable findings before declaring the
branch complete.
