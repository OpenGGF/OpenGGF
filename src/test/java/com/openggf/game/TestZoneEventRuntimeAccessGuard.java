package com.openggf.game;

import com.openggf.tests.RuntimeAccessSourceScanner;
import com.openggf.tests.RuntimeAccessSourceScanner.Access;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TestZoneEventRuntimeAccessGuard {
    private static final String S1 = "com/openggf/game/sonic1/events/";
    private static final String S2 = "com/openggf/game/sonic2/events/";
    private static final String S3K = "com/openggf/game/sonic3k/events/";
    private static final String MGZ = S3K + "Sonic3kMGZEvents.java";
    private static final String[] EVENT_PACKAGES = {S1, S2, S3K};

    // These shared helpers own event composition and runtime mutation dispatch.
    private static final Set<String> COMPOSITION_BRIDGES = Set.of(
            S1 + "Sonic1ZoneEvents.java", S1 + "Sonic1LZWaterEvents.java",
            S2 + "Sonic2ZoneEvents.java", S3K + "Sonic3kZoneEvents.java",
            S3K + "S3kSeamlessMutationExecutor.java");

    // Temporary MGZ debt. Deleting or moving a call requires tightening this inventory.
    private static final Map<Access, Integer> MGZ_ACCESSES = Map.of(
            mgz("updateBackgroundCollisionObjectRelease(int)", "levelOrNull"), 1,
            mgz("updateBackgroundCollisionObjectRelease(int)", "sprites"), 1,
            mgz("currentMgzRuntimeState()", "hasRuntime"), 1,
            mgz("currentMgzRuntimeState()", "zoneRuntimeRegistry"), 1,
            mgz("liftBgRisePassengers(AbstractPlayableSprite,int)", "sprites"), 1,
            mgz("resolveBossTransitionPlayerCharacter()", "configuration"), 1);

    @Test
    void zoneEventImplementations_shouldNotReferenceGameServicesDirectly() throws IOException {
        assertEquals(List.of(), violations(Path.of("src/main/java")),
                "Zone events must use shared event helpers. Changed temporary debt requires an exact inventory update.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"AizPreparedTransitionArtBridge", "Sonic3kAIZEvents", "Sonic3kHCZEvents", "NewEvents"})
    void rejectsAccessInUnapprovedFiles(String name, @TempDir Path root) throws IOException {
        fixture(root);
        write(root, S3K + name + ".java", "class " + name + " { void update() { GameServices . camera(); } }");
        assertTrue(violations(root).stream().anyMatch(line -> line.contains(name) && line.contains("GameServices.camera()")));
    }

    @Test
    void acceptsFormattingCommentsAndSharedComposition(@TempDir Path root) throws IOException {
        fixture(root);
        Path source = root.resolve(MGZ);
        Files.writeString(source, Files.readString(source).replace("GameServices.", "GameServices /* split */ \n . "));
        write(root, S3K + "Sonic3kZoneEvents.java", "class Sonic3kZoneEvents { void compose() { GameServices.camera(); } }");
        write(root, S3K + "Clean.java", """
                class Clean {
                    String example = "GameServices.camera()";
                    // GameServices.camera();
                    /* GameServices.camera(); */
                }
                """);
        assertEquals(List.of(), violations(root));
    }

    @Test
    void rejectsNewOperationInsideMgz(@TempDir Path root) throws IOException {
        fixture(root);
        replaceMgz(root, "GameServices.hasRuntime();", "GameServices.hasRuntime(); GameServices.camera();");
        assertTrue(violations(root).stream().anyMatch(line -> line.contains("GameServices.camera() observed=1 approved=0")));
    }

    @Test
    void rejectsSameCountCallerSubstitution(@TempDir Path root) throws IOException {
        fixture(root);
        replaceMgz(root, "currentMgzRuntimeState()", "replacementCaller()");
        List<String> differences = violations(root);
        assertEquals(4, differences.size());
        assertTrue(differences.stream().anyMatch(line -> line.contains("replacementCaller()") && line.contains("approved=0")));
        assertTrue(differences.stream().anyMatch(line -> line.contains("currentMgzRuntimeState()") && line.contains("observed=0")));
    }

    @Test
    void rejectsChangedCallerMultiplicityEvenWhenFileTotalIsUnchanged(@TempDir Path root) throws IOException {
        fixture(root);
        replaceMgz(root, "GameServices.levelOrNull(); GameServices.sprites();",
                "GameServices.levelOrNull(); GameServices.sprites(); GameServices.sprites();");
        replaceMgz(root, "void liftBgRisePassengers(AbstractPlayableSprite player, int delta) { GameServices.sprites(); }",
                "void liftBgRisePassengers(AbstractPlayableSprite player, int delta) {}");
        List<String> differences = violations(root);
        assertEquals(2, differences.size());
        assertTrue(differences.stream().anyMatch(line -> line.contains("observed=2 approved=1")));
        assertTrue(differences.stream().anyMatch(line -> line.contains("observed=0 approved=1")));
    }

    @Test
    void rejectsStalePermissionAfterCallOrFileRemoval(@TempDir Path root) throws IOException {
        fixture(root);
        replaceMgz(root, "GameServices.hasRuntime();", "");
        assertEquals(1, violations(root).size());
        Files.delete(root.resolve(MGZ));
        assertEquals(6, violations(root).size());
    }

    @Test
    void missingOrNonDirectoryPackageFails(@TempDir Path root) throws IOException {
        fixture(root);
        Path directory = root.resolve(S2);
        Files.delete(directory.resolve("Sonic2ZoneEvents.java"));
        Files.delete(directory);
        assertThrows(IOException.class, () -> violations(root));
        Files.writeString(directory, "not a package directory");
        assertThrows(IOException.class, () -> violations(root));
    }

    @Test
    void unreadableOrMalformedSourceFails(@TempDir Path root) throws IOException {
        fixture(root);
        Path source = root.resolve(S3K + "Unreadable.java");
        Files.createDirectory(source);
        assertThrows(IOException.class, () -> violations(root));
        Files.delete(source);
        Files.writeString(source, "class Unreadable { void broken( }");
        assertThrows(IOException.class, () -> violations(root));
    }

    private static Access mgz(String caller, String operation) {
        return new Access(MGZ, "Sonic3kMGZEvents#" + caller, "GameServices." + operation + "()");
    }

    private static List<String> violations(Path root) throws IOException {
        Map<String, String> sources = RuntimeAccessSourceScanner.readRequiredSources(root, EVENT_PACKAGES);
        for (String bridge : COMPOSITION_BRIDGES) {
            if (sources.remove(bridge) == null) throw new IOException("Required composition bridge is missing: " + bridge);
        }
        return RuntimeAccessSourceScanner.differences(
                RuntimeAccessSourceScanner.scan(sources, List.of("GameServices.")), MGZ_ACCESSES);
    }

    private static void fixture(Path root) throws IOException {
        for (String bridge : COMPOSITION_BRIDGES) {
            String name = Path.of(bridge).getFileName().toString().replace(".java", "");
            write(root, bridge, "class " + name + " {}");
        }
        write(root, MGZ, """
                class Sonic3kMGZEvents {
                    void updateBackgroundCollisionObjectRelease(int act) { GameServices.levelOrNull(); GameServices.sprites(); }
                    void currentMgzRuntimeState() { GameServices.hasRuntime(); GameServices.zoneRuntimeRegistry(); }
                    void liftBgRisePassengers(AbstractPlayableSprite player, int delta) { GameServices.sprites(); }
                    void resolveBossTransitionPlayerCharacter() { GameServices.configuration(); }
                }
                """);
    }

    private static void replaceMgz(Path root, String before, String after) throws IOException {
        Path source = root.resolve(MGZ);
        String text = Files.readString(source);
        assertTrue(text.contains(before));
        Files.writeString(source, text.replace(before, after));
    }

    private static void write(Path root, String path, String text) throws IOException {
        Path source = root.resolve(path);
        Files.createDirectories(source.getParent());
        Files.writeString(source, text);
    }
}
