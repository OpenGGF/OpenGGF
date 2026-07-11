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
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestModApiSignatureSurface {
    private static final String BASELINE = "mods/mod-api-signatures-1.1.txt";
    private static final SemanticVersion BASELINE_VERSION = new SemanticVersion(1, 1, 0);

    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.TYPE_USE)
    @interface ClassTypeUse {
        String value();
    }

    static final class ClassTypeUseFixture {
        public List<@ClassTypeUse("seed") String> values;
    }

    @Test
    void recursiveSurfaceIsAnnotatedAndHasNoUnauditedSignatureTypes() {
        List<String> missing = ModApiSignatureSurface.recursiveTypes().stream()
                .filter(type -> !type.isAnnotationPresent(ModApi.class))
                .map(Class::getName)
                .toList();
        assertTrue(missing.isEmpty(), () -> "Recursive API types lack @ModApi: " + missing);
        assertTrue(ModApiSignatureSurface.externalSignatureTypes().isEmpty(),
                () -> "Unaudited signature types: " + ModApiSignatureSurface.externalSignatureTypes());
        assertEquals(47, ModApiSignatureSurface.allowedPlatformTypeNames().size(),
                "Platform allowlist changes require explicit review");
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
    void versionOneOneBaselineRemainsAnAdditiveSubset() throws IOException {
        List<String> baseline;
        try (var input = getClass().getClassLoader().getResourceAsStream(BASELINE)) {
            if (input == null) throw new IOException("Missing " + BASELINE);
            try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                baseline = reader.lines().toList();
            }
        }
        List<String> sorted = new ArrayList<>(new TreeSet<>(baseline));
        assertEquals(sorted, baseline, "Baseline must be unique, sorted canonical UTF-8 text");

        Set<String> current = ModApiSignatureSurface.snapshotLines();
        List<String> violations = ModApiSignatureSurface.baselineViolations(
                BASELINE_VERSION, Set.copyOf(baseline), ModApiVersion.CURRENT, current);
        assertTrue(violations.isEmpty(), () -> String.join("\n", violations));
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
