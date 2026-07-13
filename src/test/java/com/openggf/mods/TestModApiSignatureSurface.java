package com.openggf.mods;

import com.openggf.game.ModApi;
import com.openggf.mods.code.ModApiSignatureSurface;
import com.openggf.mods.code.ModApiSurfaceInventory;
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
    private static final String BASELINE = "mods/mod-api-signatures-1.1.txt";
    private static final String PUBLISHED_BASELINE = "mods/mod-api-signatures-2.0.txt";
    private static final String PLATFORM_ALLOWLIST = "mods/mod-api-platform-allowlist.txt";
    private static final SemanticVersion BASELINE_VERSION = new SemanticVersion(1, 1, 0);
    private static final SemanticVersion PUBLISHED_VERSION = new SemanticVersion(2, 0, 0);

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
    void publishedTwoZeroSurfaceIsPinnedToTheCurrentSurface() throws Exception {
        List<String> published;
        try (var input = getClass().getClassLoader().getResourceAsStream(PUBLISHED_BASELINE)) {
            assertNotNull(input, "Missing published 2.0 API snapshot");
            try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                published = reader.lines().toList();
            }
        }
        assertEquals(new ArrayList<>(new TreeSet<>(published)), published,
                "Published API baseline must be unique, sorted canonical UTF-8 text");
        assertEquals(new ArrayList<>(ModApiSignatureSurface.snapshotLines()), published,
                "Review 2.0 API changes and refresh the full published snapshot (mod-api-signatures-2.0.txt)");
        assertEquals(PUBLISHED_VERSION, ModApiVersion.CURRENT,
                "The published Mod API version must match the frozen 2.0 baseline");
    }

    @Test
    void oneOneToTwoZeroIsADeclaredBreakingTransition() throws IOException {
        List<String> historical = readBaseline(BASELINE);
        assertEquals(new ArrayList<>(new TreeSet<>(historical)), historical,
                "Historical 1.1 baseline must remain unique, sorted canonical UTF-8 text");
        List<String> published = readBaseline(PUBLISHED_BASELINE);
        assertEquals(new ArrayList<>(new TreeSet<>(published)), published,
                "Published 2.0 baseline must be unique, sorted canonical UTF-8 text");

        Set<String> current = ModApiSignatureSurface.snapshotLines();

        // 1.1 -> current is a genuine break: the frozen 1.1 surface has removed/changed
        // signatures (rewind-state closure consolidation, per-game rules records,
        // SpriteManager.drawUnifiedBucketWithPriority), so 1.1 is a closed historical
        // baseline, not an additive subset of the current surface. Even at the new major
        // version these removals are reported, which is why the transition is major.
        List<String> historicalViolations = ModApiSignatureSurface.baselineViolations(
                BASELINE_VERSION, Set.copyOf(historical), PUBLISHED_VERSION, current);
        assertFalse(historicalViolations.isEmpty(),
                "1.1 -> 2.0 must be a declared breaking transition with removed/changed signatures");
        assertTrue(historicalViolations.stream().anyMatch(v -> v.startsWith("Breaking Mod API signature removals")),
                () -> "Expected breaking removals in the 1.1 -> 2.0 transition:\n" + String.join("\n", historicalViolations));

        // The published 2.0 baseline is frozen against the current surface: no removals,
        // no unreviewed additions. Any drift from here fails and must be reviewed and
        // refrozen (a same-major minor bump for additions, a new major for removals).
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
                BASELINE_VERSION, baseline, BASELINE_VERSION, additive).isEmpty());
        assertTrue(ModApiSignatureSurface.baselineViolations(
                BASELINE_VERSION, baseline, new SemanticVersion(1, 2, 0), additive).isEmpty());
        assertFalse(ModApiSignatureSurface.baselineViolations(
                BASELINE_VERSION, baseline, new SemanticVersion(2, 0, 0), additive).isEmpty());
        assertFalse(ModApiSignatureSurface.baselineViolations(
                BASELINE_VERSION, baseline, new SemanticVersion(1, 2, 0), Set.of()).isEmpty());
    }
}
