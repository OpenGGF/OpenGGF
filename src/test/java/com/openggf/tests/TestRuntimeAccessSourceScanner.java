package com.openggf.tests;

import com.openggf.tests.RuntimeAccessSourceScanner.Access;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestRuntimeAccessSourceScanner {
    @Test
    void countsDirectAccessesWithoutCountingChainedConsumersOrComments() throws IOException {
        var counts = scan("""
                class Fixture {
                    void update() {
                        GameServices /* spacing */ . sprites().getSidekicks();
                        com.openggf.game.GameServices.sprites();
                        GameServices.sprites(); GameServices.sprites();
                        String example = "GameServices.sprites()";
                        // GameServices.camera();
                        /* GameServices.camera(); */
                    }
                }
                """);
        assertEquals(Map.of(access("Fixture#update()", "GameServices.sprites()"), 4), counts);
    }

    @Test
    void recordsMethodReferencesStaticImportsAndFieldAccessAsUnapprovedOperations() throws IOException {
        var counts = scan("""
                import static com.openggf.game.GameServices.camera;
                class Fixture {
                    Object reference = GameServices :: camera;
                    Object value = GameServices.VALUE;
                    void update() { GameServices.configuration(1); }
                }
                """);
        assertEquals(Map.of(
                access("", "GameServices.camera"), 1,
                access("Fixture#field:reference", "GameServices.camera::reference"), 1,
                access("Fixture#field:value", "GameServices.VALUE"), 1,
                access("Fixture#update()", "GameServices.configuration(1 args)"), 1), counts);
    }

    @Test
    void recordsMethodReferencesForInvocationShapedPatterns() throws IOException {
        var counts = RuntimeAccessSourceScanner.scan(Map.of("Fixture.java", """
                class Fixture {
                    void update() {
                        Object direct = LevelManager :: getInstance;
                        Object qualified = com.openggf.level.LevelManager::getInstance;
                        Object otherMethod = LevelManager::getInstanceElse;
                        Object otherOwner = OtherLevelManager::getInstance;
                        String example = "LevelManager::getInstance";
                        // LevelManager::getInstance
                    }
                }
                """), List.of("LevelManager.getInstance("));
        Access reference = access("Fixture#update()", "LevelManager.getInstance::reference");
        assertEquals(Map.of(reference, 2), counts);
        var differences = RuntimeAccessSourceScanner.differences(counts,
                Map.of(access("Fixture#update()", "LevelManager.getInstance()"), 2));
        assertEquals(2, differences.size());
        assertTrue(differences.stream().anyMatch(line -> line.contains("::reference observed=2 approved=0")));
        assertTrue(differences.stream().anyMatch(line -> line.contains("getInstance() observed=0 approved=2")));
    }

    @Test
    void distinguishesOverloadsNestedLocalAnonymousTypesAndInitializers() throws IOException {
        var counts = scan("""
                class Fixture {
                    static { GameServices.camera(); }
                    { GameServices.camera(); }
                    Fixture() { GameServices.camera(); }
                    void update() { GameServices.camera(); }
                    void update(int x) {
                        Runnable lambda = () -> GameServices.camera();
                        class Local { void run() { GameServices.camera(); } }
                        Runnable first = new Runnable() { public void run() { GameServices.camera(); } };
                        Runnable second = new Runnable() { public void run() { GameServices.camera(); } };
                    }
                    static class Nested { void update() { GameServices.camera(); } }
                }
                """);
        assertEquals(9, counts.size());
        for (String caller : List.of("Fixture#<clinit>", "Fixture#<init-block>", "Fixture#<init>()",
                "Fixture#update()", "Fixture#update(int)", "Fixture#update(int)#<local:Local:1>#run()",
                "Fixture#update(int)#<anonymous:Runnable:1>#run()",
                "Fixture#update(int)#<anonymous:Runnable:2>#run()", "Fixture#Nested#update()")) {
            assertEquals(1, counts.get(access(caller, "GameServices.camera()")), caller);
        }
    }

    @Test
    void staleCountsAndCallerSubstitutionProduceSeparateDifferences() {
        Access oldCaller = access("Fixture#oldCaller()", "GameServices.camera()");
        Access newCaller = access("Fixture#newCaller()", "GameServices.camera()");
        var differences = RuntimeAccessSourceScanner.differences(Map.of(newCaller, 1), Map.of(oldCaller, 1));
        assertEquals(2, differences.size());
        assertTrue(differences.stream().anyMatch(line -> line.contains("observed=0 approved=1")));
        assertTrue(differences.stream().anyMatch(line -> line.contains("observed=1 approved=0")));
        assertEquals(List.of(oldCaller + " observed=2 approved=1"),
                RuntimeAccessSourceScanner.differences(Map.of(oldCaller, 2), Map.of(oldCaller, 1)));
    }

    @Test
    void acceptsEmptyInventoryButRejectsMalformedSource() throws IOException {
        assertEquals(Map.of(), RuntimeAccessSourceScanner.scan(Map.of(), List.of("GameServices.")));
        assertThrows(IOException.class, () -> scan("class Fixture { void update( }"));
    }

    @Test
    void requiredSourcePackagesMustExistAndContainReadableJavaInputs(@TempDir Path root) throws IOException {
        String[] packages = {"events"};
        assertThrows(IOException.class, () -> RuntimeAccessSourceScanner.readRequiredSources(root, packages));
        Path directory = Files.createDirectory(root.resolve("events"));
        assertThrows(IOException.class, () -> RuntimeAccessSourceScanner.readRequiredSources(root, packages));
        Path source = directory.resolve("Fixture.java");
        Files.createDirectory(source);
        assertThrows(IOException.class, () -> RuntimeAccessSourceScanner.readRequiredSources(root, packages));
        Files.delete(source);
        Files.writeString(source, "class Fixture {}");
        assertEquals(Map.of("events/Fixture.java", "class Fixture {}"),
                RuntimeAccessSourceScanner.readRequiredSources(root, packages));
    }

    private static Map<Access, Integer> scan(String source) throws IOException {
        return RuntimeAccessSourceScanner.scan(Map.of("Fixture.java", source), List.of("GameServices."));
    }

    private static Access access(String caller, String operation) {
        return new Access("Fixture.java", caller, operation);
    }
}
