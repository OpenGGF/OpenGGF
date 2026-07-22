package com.openggf.mods;

import com.openggf.game.AbstractLevelInitProfile;
import com.openggf.game.ModApi;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.mods.code.ModApiSignatureSurface;
import com.openggf.mods.code.ModApiSurfaceInventory;
import com.openggf.physics.GroundSensor;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
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
    private static final String PUBLISHED_BASELINE = "mods/mod-api-signatures-0.7.txt";
    private static final String PLATFORM_ALLOWLIST = "mods/mod-api-platform-allowlist.txt";
    private static final SemanticVersion PUBLISHED_VERSION = new SemanticVersion(0, 7, 0);

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
    void publishedZeroSevenSurfaceIsPinnedToTheCurrentSurface() throws Exception {
        List<String> published = readBaseline(PUBLISHED_BASELINE);
        assertEquals(new ArrayList<>(new TreeSet<>(published)), published,
                "Published API 0.7 baseline must be unique, sorted canonical UTF-8 text");
        assertEquals(new ArrayList<>(ModApiSignatureSurface.snapshotLines()), published,
                "Review current changes and regenerate mod-api-signatures-0.7.txt");
        assertEquals(PUBLISHED_VERSION, ModApiVersion.CURRENT,
                "The current Mod API version must match the 0.7 baseline");
    }

    @Test
    void engineOnlyRuntimeHelpersDoNotLeakIntoTheZeroSevenSurface() {
        Set<String> current = ModApiSignatureSurface.snapshotLines();
        assertFalse(current.stream().anyMatch(line -> line.startsWith(
                        "METHOD com.openggf.sprites.playable.PlayableSpriteController ")
                        && (line.contains(" captureFrameStartState(")
                        || line.contains(" restoreFrameStartState(")
                        || line.contains("AtFrameStart(")
                        || line.contains("ObjectControlledSolidContact")
                        || line.contains("SpringHandoff(")
                        || line.contains(" publishRawAnimation(")
                        || line.contains(" publishLandingAnimationWrite("))),
                "Playable controller frame/contact handoff helpers are engine internals");
        assertFalse(current.contains(
                        "METHOD com.openggf.game.ObjectArtProvider public  void processRuntimeArtQueue()"),
                "Runtime decompression pumping is an engine capability, not creator API");
        assertFalse(current.stream().anyMatch(line -> line.contains("GameplayInputFilterAccess")
                        || line.contains("HudProfileAccess")
                        || line.contains("OwnerAwareGameplayInputFilter")
                        || line.contains("installGameplayInputFilter")
                        || line.contains("currentGameplayInputFilter")
                        || line.contains("setGameplayInputFilter")
                        || line.contains("getGameplayInputFilter")
                        || line.contains("installProfile")),
                "Engine-only policy installation bridges and runtime mutators must stay internal");
        assertFalse(current.contains(
                        "METHOD com.openggf.game.patch.GameplayLaunchRequest public,static  com.openggf.game.patch.GameplayLaunchRequest fromSelectedTeam(java.lang.String,com.openggf.game.save.SelectedTeam)"),
                "SelectedTeam-to-request conversion is an engine launch helper, not creator API");
        assertFalse(current.contains(
                        "METHOD com.openggf.game.patch.GameplayLaunchRequest public  com.openggf.game.GameplayLaunchTeam team()"),
                "Request-to-policy conversion is an engine launch helper, not creator API");
        assertFalse(current.contains(
                        "METHOD com.openggf.game.save.SaveSessionContext public  com.openggf.game.save.SaveSessionContext withLaunchTeam(com.openggf.game.GameplayLaunchTeam)"),
                "Launch-only save-context substitution must remain behind an engine access bridge");
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
        SemanticVersion baselineVersion = new SemanticVersion(0, 7, 0);
        assertFalse(ModApiSignatureSurface.baselineViolations(
                baselineVersion, baseline, baselineVersion, additive).isEmpty());
        assertTrue(ModApiSignatureSurface.baselineViolations(
                baselineVersion, baseline, new SemanticVersion(0, 8, 0), additive).isEmpty());
        assertFalse(ModApiSignatureSurface.baselineViolations(
                baselineVersion, baseline, new SemanticVersion(1, 0, 0), additive).isEmpty());
        assertFalse(ModApiSignatureSurface.baselineViolations(
                baselineVersion, baseline, new SemanticVersion(0, 8, 0), Set.of()).isEmpty());
    }

    private List<String> readBaseline(String resource) throws IOException {
        try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (input == null) throw new IOException("Missing " + resource);
            try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                return reader.lines().toList();
            }
        }
    }
}
