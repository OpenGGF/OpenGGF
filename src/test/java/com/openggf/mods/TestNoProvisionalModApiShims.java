package com.openggf.mods;

import com.openggf.game.CheckpointState;
import com.openggf.game.CollisionModel;
import com.openggf.game.rewind.snapshot.CameraSnapshot;
import com.openggf.game.rewind.snapshot.GameStateSnapshot;
import com.openggf.game.rewind.snapshot.WaterSystemSnapshot;
import com.openggf.game.rules.AirCollisionRules;
import com.openggf.game.rules.CollisionRules;
import com.openggf.game.rules.ObjectInteractionRules;
import com.openggf.game.rules.PlayerAnimationRules;
import com.openggf.game.rules.PlayerCapabilityRules;
import com.openggf.game.rules.RingRules;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.mods.code.ModZoneContribution;
import com.openggf.sprites.managers.PlayableSpriteMovement;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.PlayableSpriteController;
import com.openggf.trace.TraceMetadata;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestNoProvisionalModApiShims {
    private static final Path MAIN_JAVA = Path.of("src", "main", "java");

    private static final List<String> PROHIBITED_MARKERS = List.of(
            "Compatibility constructor for API 1.",
            "Compatibility constructor for API 2.",
            "Binary-compatible constructor for the Mod API 2.4",
            "Binary-compatible constructor for Mod API 2.4",
            "Compatibility overload for API 1.1",
            "Historical Mod API 2.4 view");
    private static final Comparator<MarkerOccurrence> MARKER_ORDER = Comparator
            .comparing((MarkerOccurrence occurrence) -> portablePath(occurrence.relativePath()))
            .thenComparing(MarkerOccurrence::marker);
    private static final Set<MarkerOccurrence> ALLOWED_MARKER_OCCURRENCES = Set.of();

    @Test
    void productionSourcesContainOnlyExplicitlyAllowedProvisionalMarkers() throws Exception {
        Set<MarkerOccurrence> actual = new TreeSet<>(MARKER_ORDER);
        try (var sources = Files.walk(MAIN_JAVA)) {
            sources.filter(path -> path.toString().endsWith(".java"))
                    .sorted(Comparator.comparing(path -> portablePath(MAIN_JAVA.relativize(path))))
                    .forEach(path -> PROHIBITED_MARKERS.stream()
                            .filter(marker -> sourceContains(path, marker))
                            .map(marker -> new MarkerOccurrence(MAIN_JAVA.relativize(path), marker))
                            .forEach(actual::add));
        }
        Set<MarkerOccurrence> allowed = new TreeSet<>(MARKER_ORDER);
        allowed.addAll(ALLOWED_MARKER_OCCURRENCES);

        assertEquals(allowed, actual,
                "Production compatibility markers must match the reviewed exact allowlist");
    }

    @Test
    void removedRecordOverloadsStayAbsent() {
        assertOnlyCanonicalRecordConstructor(TraceMetadata.class);
        assertOnlyCanonicalRecordConstructor(PlayableSpriteMovement.RewindState.class);
        assertOnlyCanonicalRecordConstructor(PlayableSpriteController.RewindState.class);
        assertOnlyCanonicalRecordConstructor(CheckpointState.RewindState.class);
        assertOnlyCanonicalRecordConstructor(ObjectInteractionRules.class);
        assertOnlyCanonicalRecordConstructor(PlayerAnimationRules.class);
        assertOnlyCanonicalRecordConstructor(PlayerCapabilityRules.class);
        assertOnlyCanonicalRecordConstructor(RingRules.class);
        assertOnlyCanonicalRecordConstructor(CameraSnapshot.class);
        assertOnlyCanonicalRecordConstructor(GameStateSnapshot.class);
        assertOnlyCanonicalRecordConstructor(WaterSystemSnapshot.DynamicWaterEntry.class);
        assertOnlyCanonicalRecordConstructor(PerObjectRewindSnapshot.SidekickCpuRewindExtra.class);
        assertOnlyCanonicalRecordConstructor(PerObjectRewindSnapshot.PlayerRewindExtra.class);
        assertOnlyCanonicalRecordConstructor(ModZoneContribution.class);
    }

    @Test
    void collisionRulesRetainsOnlyCanonicalAndNestedAirConstructors() {
        assertPublicConstructorSet(CollisionRules.class,
                recordComponentTypes(CollisionRules.class),
                new Class<?>[] {
                        CollisionModel.class,
                        boolean.class,
                        boolean.class,
                        boolean.class,
                        boolean.class,
                        AirCollisionRules.class,
                        boolean.class,
                        boolean.class,
                        boolean.class,
                        boolean.class,
                        boolean.class,
                        boolean.class,
                        boolean.class,
                        boolean.class,
                        boolean.class,
                        boolean.class,
                        boolean.class,
                        int.class
                });
    }

    @Test
    void removedMethodsAndLegacyMgzFieldsStayAbsent() {
        assertThrows(NoSuchMethodException.class, () -> SpriteManager.class.getDeclaredMethod(
                "drawUnifiedBucketWithPriority",
                int.class, GraphicsManager.class, Runnable.class, Runnable.class));
        assertThrows(NoSuchMethodException.class, () -> ObjectManager.class.getDeclaredMethod(
                "snapshotPersistentDynamicObjectsForTransition"));

        assertThrows(NoSuchFieldException.class, () -> AbstractPlayableSprite.class.getDeclaredField(
                "mgzTopPlatformCarrySolidContactObject"));
        assertThrows(NoSuchFieldException.class, () -> AbstractPlayableSprite.class.getDeclaredField(
                "mgzTopPlatformSpringHandoffPending"));
        assertThrows(NoSuchFieldException.class, () -> AbstractPlayableSprite.class.getDeclaredField(
                "mgzTopPlatformSpringHandoffXVel"));
        assertThrows(NoSuchFieldException.class, () -> AbstractPlayableSprite.class.getDeclaredField(
                "mgzTopPlatformSpringHandoffYVel"));
    }

    private static void assertOnlyCanonicalRecordConstructor(Class<?> recordType) {
        assertPublicConstructorSet(recordType, recordComponentTypes(recordType));
        Constructor<?> constructor = Arrays.stream(recordType.getDeclaredConstructors())
                .filter(candidate -> Modifier.isPublic(candidate.getModifiers()))
                .findFirst()
                .orElseThrow();
        assertArrayEquals(recordComponentTypes(recordType), constructor.getParameterTypes(),
                recordType.getName() + " must expose its canonical record constructor");
    }

    private static Class<?>[] recordComponentTypes(Class<?> recordType) {
        return Arrays.stream(recordType.getRecordComponents())
                .map(component -> component.getType())
                .toArray(Class<?>[]::new);
    }

    private static void assertPublicConstructorSet(Class<?> type, Class<?>[]... expectedSignatures) {
        Set<String> expected = Arrays.stream(expectedSignatures)
                .map(TestNoProvisionalModApiShims::signature)
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> actual = Arrays.stream(type.getDeclaredConstructors())
                .filter(constructor -> Modifier.isPublic(constructor.getModifiers()))
                .map(Constructor::getParameterTypes)
                .map(TestNoProvisionalModApiShims::signature)
                .collect(Collectors.toCollection(TreeSet::new));

        assertEquals(expected, actual, type.getName() + " public constructor surface changed");
    }

    private static String signature(Class<?>[] parameterTypes) {
        return Arrays.stream(parameterTypes)
                .map(Class::getName)
                .collect(Collectors.joining(",", "(", ")"));
    }

    private static String portablePath(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static boolean sourceContains(Path path, String marker) {
        try {
            return Files.readString(path).contains(marker);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not inspect " + path, exception);
        }
    }

    private record MarkerOccurrence(Path relativePath, String marker) {}
}
