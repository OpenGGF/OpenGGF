package com.openggf.mods;

import com.openggf.game.ModApi;
import com.openggf.game.AbstractLevelInitProfile;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.mods.code.ModApiSignatureSurface;
import com.openggf.mods.code.ModApiSurfaceInventory;
import com.openggf.physics.GroundSensor;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestModApiSignatureSurface {
    // Reconciled surface lineage: 1.1.0 -> 1.2.0 (additive) -> 2.0.0 (breaking)
    // -> 2.1.0 (additive) -> 2.2.0 (additive) -> 2.3.0 (additive). 1.1 through
    // 2.2 are closed historical baselines; 2.3 is the published surface.
    private static final String BASELINE_11 = "mods/mod-api-signatures-1.1.txt";
    private static final String BASELINE_12 = "mods/mod-api-signatures-1.2.txt";
    private static final String BASELINE_20 = "mods/mod-api-signatures-2.0.txt";
    private static final String BASELINE_21 = "mods/mod-api-signatures-2.1.txt";
    private static final String BASELINE_22 = "mods/mod-api-signatures-2.2.txt";
    private static final String PUBLISHED_BASELINE = "mods/mod-api-signatures-2.3.txt";
    private static final String PLATFORM_ALLOWLIST = "mods/mod-api-platform-allowlist.txt";
    private static final SemanticVersion VERSION_11 = new SemanticVersion(1, 1, 0);
    private static final SemanticVersion VERSION_12 = new SemanticVersion(1, 2, 0);
    private static final SemanticVersion VERSION_20 = new SemanticVersion(2, 0, 0);
    private static final SemanticVersion VERSION_21 = new SemanticVersion(2, 1, 0);
    private static final SemanticVersion VERSION_22 = new SemanticVersion(2, 2, 0);
    private static final SemanticVersion PUBLISHED_VERSION = new SemanticVersion(2, 3, 0);

    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.TYPE_USE)
    @interface ClassTypeUse {
        String value();
    }

    static final class ClassTypeUseFixture {
        public List<@ClassTypeUse("seed") String> values;
    }

    @Test
    void recursiveSurfaceIsAnnotatedAndHasNoUnauditedSignatureTypes() throws IOException {
        List<String> missing = ModApiSignatureSurface.recursiveTypes().stream()
                .filter(type -> !type.isAnnotationPresent(ModApi.class))
                .map(Class::getName)
                .toList();
        assertTrue(missing.isEmpty(), () -> "Recursive API types lack @ModApi: " + missing);
        assertTrue(ModApiSignatureSurface.externalSignatureTypes().isEmpty(),
                () -> "Unaudited signature types: " + ModApiSignatureSurface.externalSignatureTypes());
        List<String> pinned;
        try (var input = getClass().getClassLoader().getResourceAsStream(PLATFORM_ALLOWLIST)) {
            if (input == null) throw new IOException("Missing " + PLATFORM_ALLOWLIST);
            try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                pinned = reader.lines().toList();
            }
        }
        assertEquals(new ArrayList<>(new TreeSet<>(pinned)), pinned,
                "Platform allowlist baseline must be unique and sorted");
        assertEquals(new TreeSet<>(pinned), ModApiSignatureSurface.allowedPlatformTypeNames(),
                "Platform allowlist changes require exact explicit review");
    }

    @Test
    void annotatedClasspathInventoryExactlyMatchesRecursiveSurface() {
        Set<String> annotated = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.openggf")
                .stream()
                .filter(type -> type.isAnnotatedWith(ModApi.class))
                .map(type -> type.getName())
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        Set<String> recursive = ModApiSignatureSurface.recursiveTypes().stream()
                .map(Class::getName)
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        assertEquals(recursive, annotated,
                "Every @ModApi type must be reachable from the curated recursive API roots");
        assertEquals(new ArrayList<>(recursive), ModApiSurfaceInventory.annotatedTypeNames());
    }

    @Test
    void delegatingGameModuleIsAnExplicitPublishedRoot() {
        assertTrue(ModApiSurfaceInventory.rootTypes().contains(DelegatingGameModule.class));
    }

    @Test
    void prescribedStandaloneSupportTypesAreExplicitPublishedRoots() {
        assertTrue(ModApiSurfaceInventory.rootTypes().contains(GroundSensor.class));
        assertTrue(ModApiSurfaceInventory.rootTypes().contains(AbstractLevelInitProfile.class));
    }

    @Test
    void publishedTwoThreeSurfaceIsPinnedToTheCurrentSurface() throws Exception {
        List<String> published = readBaseline(PUBLISHED_BASELINE);
        assertNotNull(published, "Missing published 2.3 API snapshot");
        assertEquals(new ArrayList<>(new TreeSet<>(published)), published,
                "Published API baseline must be unique, sorted canonical UTF-8 text");
        assertEquals(new ArrayList<>(ModApiSignatureSurface.snapshotLines()), published,
                "Review 2.3 API changes and refresh the full published snapshot (mod-api-signatures-2.3.txt)");
        assertEquals(PUBLISHED_VERSION, ModApiVersion.CURRENT,
                "The published Mod API version must match the frozen 2.3 baseline");
    }

    @Test
    void oneOneToOneTwoIsAnAdditiveHistoricalStep() throws IOException {
        List<String> baselineEleven = readBaseline(BASELINE_11);
        assertEquals(new ArrayList<>(new TreeSet<>(baselineEleven)), baselineEleven,
                "Historical 1.1 baseline must remain unique, sorted canonical UTF-8 text");
        assertEquals(16_483, baselineEleven.size(), "Published API 1.1 baseline is immutable");

        List<String> baselineTwelve = readBaseline(BASELINE_12);
        assertEquals(new ArrayList<>(new TreeSet<>(baselineTwelve)), baselineTwelve,
                "Historical 1.2 baseline must remain unique, sorted canonical UTF-8 text");
        assertEquals(17_178, baselineTwelve.size(), "Historical API 1.2 baseline is immutable");

        // 1.1 -> 1.2 is a clean additive minor bump: the 1.2 surface is a strict
        // superset of 1.1 (the mod-support publish added standalone-game support,
        // character definitions, and additive published roots), with no removals.
        List<String> additiveViolations = ModApiSignatureSurface.baselineViolations(
                VERSION_11, Set.copyOf(baselineEleven), VERSION_12, Set.copyOf(baselineTwelve));
        assertTrue(additiveViolations.isEmpty(),
                () -> "1.1 -> 1.2 must be a clean additive minor bump:\n"
                        + String.join("\n", additiveViolations));
        assertTrue(Set.copyOf(baselineTwelve).containsAll(baselineEleven),
                "The 1.2 surface must contain every 1.1 signature");
    }

    @Test
    void oneTwoToTwoZeroIsADeclaredBreakingTransition() throws IOException {
        List<String> historical = readBaseline(BASELINE_12);
        assertEquals(new ArrayList<>(new TreeSet<>(historical)), historical,
                "Historical 1.2 baseline must remain unique, sorted canonical UTF-8 text");
        List<String> historicalTwoZero = readBaseline(BASELINE_20);
        assertEquals(new ArrayList<>(new TreeSet<>(historicalTwoZero)), historicalTwoZero,
                "Historical 2.0 baseline must remain unique, sorted canonical UTF-8 text");
        assertEquals(873, historicalTwoZero.stream().filter(line -> line.startsWith("TYPE ")).count(),
                "Historical 2.0 API baseline engine-type count is immutable");
        assertEquals(17_165, historicalTwoZero.size(), "Historical API 2.0 baseline is immutable");

        // 1.2 -> 2.0 is a genuine break: the frozen 1.2 surface has removed/changed
        // signatures (rewind-state closure consolidation, per-game rules records,
        // SpriteManager.drawUnifiedBucketWithPriority), so 1.2 is a closed historical
        // baseline, not an additive subset of the 2.0 surface. Even at the new major
        // version these removals are reported, which is why the transition is major. The
        // step is verified from 1.2 (not directly from 1.1) so 1.2's additions are never
        // silently absorbed into an undocumented 1.1 -> 2.0 jump. Both sides are now
        // closed historical baselines, so this check no longer depends on the live surface.
        List<String> historicalViolations = ModApiSignatureSurface.baselineViolations(
                VERSION_12, Set.copyOf(historical), VERSION_20, Set.copyOf(historicalTwoZero));
        assertFalse(historicalViolations.isEmpty(),
                "1.2 -> 2.0 must be a declared breaking transition with removed/changed signatures");
        assertTrue(historicalViolations.stream().anyMatch(v -> v.startsWith("Breaking Mod API signature removals")),
                () -> "Expected breaking removals in the 1.2 -> 2.0 transition:\n" + String.join("\n", historicalViolations));
    }

    @Test
    void twoZeroToTwoOneIsAnAdditiveMinorBump() throws IOException {
        List<String> historicalTwoZero = readBaseline(BASELINE_20);
        assertEquals(new ArrayList<>(new TreeSet<>(historicalTwoZero)), historicalTwoZero,
                "Historical 2.0 baseline must remain unique, sorted canonical UTF-8 text");
        List<String> historicalTwoOne = readBaseline(BASELINE_21);
        assertEquals(new ArrayList<>(new TreeSet<>(historicalTwoOne)), historicalTwoOne,
                "Historical 2.1 baseline must remain unique, sorted canonical UTF-8 text");
        assertEquals(875, historicalTwoOne.stream().filter(line -> line.startsWith("TYPE ")).count(),
                "Historical 2.1 API baseline engine-type count is immutable");
        assertEquals(17_196, historicalTwoOne.size(), "Historical API 2.1 baseline is immutable");

        // 2.0 -> 2.1 is a clean additive minor bump: the 2.1 surface is a strict superset
        // of 2.0 (the ROM-art intake publish added RomArtRequest, RomArtCompression, and
        // ModContext.registerRomObjectArt), with no removals. Both sides are now closed
        // historical baselines, so this check no longer depends on the live surface.
        List<String> additiveViolations = ModApiSignatureSurface.baselineViolations(
                VERSION_20, Set.copyOf(historicalTwoZero), VERSION_21, Set.copyOf(historicalTwoOne));
        assertTrue(additiveViolations.isEmpty(),
                () -> "2.0 -> 2.1 must be a clean additive minor bump:\n"
                        + String.join("\n", additiveViolations));
        assertTrue(Set.copyOf(historicalTwoOne).containsAll(historicalTwoZero),
                "The 2.1 surface must contain every 2.0 signature");
    }

    @Test
    void twoOneToTwoTwoIsAnAdditiveMinorBump() throws IOException {
        List<String> historicalTwoOne = readBaseline(BASELINE_21);
        assertEquals(new ArrayList<>(new TreeSet<>(historicalTwoOne)), historicalTwoOne,
                "Historical 2.1 baseline must remain unique, sorted canonical UTF-8 text");
        List<String> historicalTwoTwo = readBaseline(BASELINE_22);
        assertEquals(new ArrayList<>(new TreeSet<>(historicalTwoTwo)), historicalTwoTwo,
                "Historical 2.2 baseline must be unique, sorted canonical UTF-8 text");
        assertEquals(876, historicalTwoTwo.stream().filter(line -> line.startsWith("TYPE ")).count(),
                "Historical API 2.2 baseline engine-type count is immutable");
        assertEquals(17_205, historicalTwoTwo.size(), "Historical API 2.2 baseline is immutable");

        // 2.1 -> 2.2 is a clean additive minor bump: the 2.2 surface is a strict superset
        // of 2.1 (the playable-subclass rewind publish added
        // PlayerRewindExtra.PlayableSubclassRewindExtra, the subclassExtra record
        // component/accessor, the new canonical PlayerRewindExtra constructor, and the
        // AbstractPlayableSprite capture/restore hooks), with no removals.
        List<String> additiveViolations = ModApiSignatureSurface.baselineViolations(
                VERSION_21, Set.copyOf(historicalTwoOne), VERSION_22, Set.copyOf(historicalTwoTwo));
        assertTrue(additiveViolations.isEmpty(),
                () -> "2.1 -> 2.2 must be a clean additive minor bump:\n"
                        + String.join("\n", additiveViolations));
        assertTrue(Set.copyOf(historicalTwoTwo).containsAll(historicalTwoOne),
                "The 2.2 surface must contain every 2.1 signature");
    }

    @Test
    void twoTwoToTwoThreeIsAnAdditiveMinorBump() throws IOException {
        List<String> historicalTwoTwo = readBaseline(BASELINE_22);
        assertEquals(new ArrayList<>(new TreeSet<>(historicalTwoTwo)), historicalTwoTwo,
                "Historical 2.2 baseline must remain unique, sorted canonical UTF-8 text");
        List<String> published = readBaseline(PUBLISHED_BASELINE);
        assertEquals(new ArrayList<>(new TreeSet<>(published)), published,
                "Published 2.3 baseline must be unique, sorted canonical UTF-8 text");
        assertEquals(885, published.stream().filter(line -> line.startsWith("TYPE ")).count(),
                "Published API 2.3 baseline engine-type count must match the frozen S3K mod-zone surface");
        assertEquals(17_323, published.size(), "Published API 2.3 baseline is frozen");

        // 2.2 -> 2.3 publishes the host-adapted additive-zone surface without
        // removing or changing any previously supported signature.
        List<String> additiveViolations = ModApiSignatureSurface.baselineViolations(
                VERSION_22, Set.copyOf(historicalTwoTwo), PUBLISHED_VERSION, Set.copyOf(published));
        assertTrue(additiveViolations.isEmpty(),
                () -> "2.2 -> 2.3 must be a clean additive minor bump:\n"
                        + String.join("\n", additiveViolations));
        assertTrue(Set.copyOf(published).containsAll(historicalTwoTwo),
                "The 2.3 surface must contain every 2.2 signature");

        // The published 2.3 baseline is frozen against the current surface: no removals,
        // no unreviewed additions. Any drift from here fails and must be reviewed and
        // refrozen (a same-major minor bump for additions, a new major for removals).
        Set<String> current = ModApiSignatureSurface.snapshotLines();
        List<String> publishedViolations = ModApiSignatureSurface.baselineViolations(
                PUBLISHED_VERSION, Set.copyOf(published), ModApiVersion.CURRENT, current);
        assertTrue(publishedViolations.isEmpty(), () -> String.join("\n", publishedViolations));
    }

    private List<String> readBaseline(String resource) throws IOException {
        try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (input == null) throw new IOException("Missing " + resource);
            try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                return reader.lines().toList();
            }
        }
    }

    @Test
    void classRetainedTypeUseAnnotationsAreCapturedFromClassfiles() {
        Set<String> lines = ModApiSignatureSurface.classfileAnnotationLines(
                ClassTypeUseFixture.class);
        assertTrue(lines.stream().anyMatch(line -> line.contains("CF-TYPE-ANNOTATION FIELD")
                        && line.contains("invisible @" + ClassTypeUse.class.getName())
                        && line.contains("value=\"seed\"")),
                lines::toString);
        assertTrue(ModApiSignatureSurface.classfileAnnotationReferencedTypeNames(
                        ClassTypeUseFixture.class).contains(ClassTypeUse.class.getName()),
                "Invisible annotation types must participate in recursive/leak auditing");
    }

    @Test
    void additiveSignaturesRequireAMinorVersionBumpAndRemovalsAlwaysFail() {
        Set<String> baseline = Set.of("TYPE A");
        Set<String> additive = Set.of("TYPE A", "METHOD A added()");
        assertFalse(ModApiSignatureSurface.baselineViolations(
                VERSION_11, baseline, VERSION_11, additive).isEmpty());
        assertTrue(ModApiSignatureSurface.baselineViolations(
                VERSION_11, baseline, new SemanticVersion(1, 2, 0), additive).isEmpty());
        assertFalse(ModApiSignatureSurface.baselineViolations(
                VERSION_11, baseline, new SemanticVersion(2, 0, 0), additive).isEmpty());
        assertFalse(ModApiSignatureSurface.baselineViolations(
                VERSION_11, baseline, new SemanticVersion(1, 2, 0), Set.of()).isEmpty());
    }
}
