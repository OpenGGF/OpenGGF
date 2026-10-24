package com.openggf.level.objects;

import com.openggf.game.session.EngineContext;
import com.openggf.tests.ObjectGuardSourceScanner;
import com.openggf.tests.ObjectGuardSourceScanner.SourceText;
import com.openggf.tests.RuntimeAccessSourceScanner;
import com.openggf.tests.RuntimeAccessSourceScanner.Access;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Migration guard: detects direct singleton {@code getInstance()} calls in
 * object instance classes that should use {@code services()} instead.
 * <p>
 * As objects are migrated to use {@code ObjectServices}, remove them from
 * the {@link #KNOWN_UNMIGRATED} set. New objects must not appear in this set —
 * they should use {@code services()} from the start.
 * <p>
 * This test scans source files (not bytecode) for the literal pattern
 * {@code "<MonitoredClass>.getInstance("} or any reference to
 * {@code "GameServices."}. The previous bytecode scan was a constant-pool
 * heuristic with documented false-positive risk; with the migration complete
 * (KNOWN_UNMIGRATED is empty), the simpler source-level approach is
 * sufficient and matches the companion {@code GameServices.}-source-scan
 * tests in this file.
 */
class TestObjectServicesMigrationGuard {

    /**
     * Object classes known to still use getInstance() directly.
     * As each is migrated, remove it from this set. The test will fail if a
     * class is in this set but no longer uses getInstance() (migration complete),
     * or if a class NOT in this set starts using getInstance() (regression).
     */
    // Reason codes for KNOWN_UNMIGRATED entries:
    //   GAME_SINGLETON — uses game-specific singleton (Sonic1SwitchManager, Sonic2LevelEventManager, etc.)
    //   MISSING_METHOD — uses a LevelManager method not yet in ObjectServices
    //   STATIC_METHOD  — uses GameServices in a static context where services() isn't available
    //   NOT_OBJECT     — class doesn't extend AbstractObjectInstance, has no services()
    //   REGISTRY       — ObjectRegistry class, not an object instance
    //   DEBUG_ONLY     — only remaining calls are GameServices.debugOverlay() (scanner sees GameServices ref)
    //
    // To unblock GAME_SINGLETON: expose through ZoneFeatureProvider or ObjectServices
    // To unblock MISSING_METHOD: add the method to ObjectServices + DefaultObjectServices
    // To unblock STATIC_METHOD:  convert to instance method or accept as permanent exception
    // NOT_OBJECT/REGISTRY:       permanent exceptions — these classes don't participate in DI

    private static final Set<String> KNOWN_UNMIGRATED = Set.of(
            // All object classes have been migrated to use services().
            // Any new violations will be caught as regressions.
    );

    /**
     * Permanent exceptions: classes in the scanned packages that are NOT object instances
     * (no services() method), or are registry/utility classes that legitimately use
     * getInstance() or GameServices for non-object purposes.
     */
    private static final Set<String> PERMANENT_EXCEPTIONS = Set.of(
            // All previous whole-class exceptions have been migrated or are clean.
            // Add only narrowly justified non-object/registry exceptions here.
    );

    /** Packages containing object instance classes to scan. */
    private static final String[] OBJECT_PACKAGES = ObjectGuardSourceScanner.GAME_OBJECT_PACKAGE_PATHS;

    private static final String SHARED_OBJECT_PACKAGE = "com/openggf/level/objects";
    private static final String[] OBJECT_GLOBAL_ACCESS_PACKAGES = ObjectGuardSourceScanner.OBJECT_PACKAGE_PATHS;

    /** Approved compatibility accesses, pinned to lexical caller, operation and multiplicity. */
    private static final List<ApprovedGlobalRuntimeAccess> APPROVED_GLOBAL_RUNTIME_ACCESSES = List.of(
            approved("com.openggf.level.objects.AbstractObjectInstance", "staticDebugViewEnabled()",
                    "GameServices.configuration()", "lazy configuration bridge for legacy direct-construction tests"),
            approved("com.openggf.level.objects.AbstractObjectInstance", "staticDebugOverlay()",
                    "GameServices.debugOverlay()", "optional debug-overlay fallback"),
            approved("com.openggf.level.objects.AbstractObjectInstance", "staticRomManager()",
                    "GameServices.rom()", "optional ROM-service fallback"),
            approved("com.openggf.level.objects.AbstractObjectInstance", "staticLevelManager()",
                    "GameServices.levelOrNull()", "optional level-manager fallback for direct object construction"),
            approved("com.openggf.level.objects.AbstractObjectRegistry", "currentRomZoneId()",
                    "GameServices.levelOrNull()", "registry placement bootstrap fallback"),
            approved("com.openggf.level.objects.AbstractObjectRegistry", "currentLevel()",
                    "GameServices.levelOrNull()", "registry level bootstrap fallback"),
            approved("com.openggf.game.sonic1.objects.bosses.Sonic1BossBlockInstance", "spawnAllBlocks()",
                    "staticObjectManager()", "SYZ level-event bootstrap creates initial boss blocks outside an object callback"),
            approved("com.openggf.level.objects.BootstrapObjectServices", "<init>()",
                    "EngineServices.current()", "ObjectServices composition boundary"),
            approved("com.openggf.level.objects.BootstrapObjectServices", "requireGameplayMode()",
                    "SessionManager.getCurrentGameplayMode()", "ObjectServices composition boundary"),
            approved("com.openggf.level.objects.ObjectManager", "defaultServices()",
                    "EngineServices.current()", "ObjectServices composition boundary"),
            approved("com.openggf.level.objects.ObjectManager", "defaultServices()",
                    "SessionManager.getCurrentGameplayMode()", "ObjectServices composition boundary")
    );

    private static final Set<String> SHARED_OBJECT_SOURCE_EXCEPTIONS = Set.of(
            "com.openggf.level.objects.AbstractObjectInstance"
    );

    private static final String[] OBJECT_SERVICE_NULL_CHECK_PACKAGES = ObjectGuardSourceScanner.OBJECT_PACKAGE_PATHS;

    private static final java.util.regex.Pattern STRICT_SERVICES_NULL_CHECK =
            java.util.regex.Pattern.compile("services\\(\\)\\s*(==|!=)\\s*null");

    @Test
    void objectImplementationClasses_shouldNotAccessGlobalRuntimeRootsExceptApprovedBridges() throws IOException {
        Path srcMain = Path.of("src/main/java");
        List<String> violations = collectForbiddenGlobalAccessViolations(srcMain);

        if (!violations.isEmpty()) {
            fail("Object implementation classes must access runtime dependencies through ObjectServices.\n"
                    + "Move the dependency behind services(), tryServices(), ObjectServices, or an approved bridge.\n\n  "
                    + String.join("\n  ", violations));
        }
    }

    @Test
    void approvedGlobalRuntimeAccessEntriesStillMatchCurrentSource() throws IOException {
        Path srcMain = Path.of("src/main/java");
        Map<Access, Integer> actual = scanGlobalAccesses(srcMain);
        Map<Access, Integer> approved = approvedAccessCounts();
        Map<Access, Integer> observedApproved = new TreeMap<>();
        actual.forEach((access, count) -> {
            if (approved.containsKey(access)) observedApproved.put(access, count);
        });
        assertEquals(List.of(), RuntimeAccessSourceScanner.differences(observedApproved, approved),
                "Approved bridge accesses must still exist in the exact caller with the exact multiplicity");
    }

    @Test
    void consolidatedGlobalAccessScan_coversSharedObjectInstances(@TempDir Path tempDir) throws IOException {
        Path srcMain = fixtureSourceRoot(tempDir);
        Path source = srcMain.resolve("com/openggf/level/objects/SharedFixtureObjectInstance.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package com.openggf.level.objects;

                final class SharedFixtureObjectInstance {
                    void update() {
                        LevelManager.getInstance();
                    }
                }
                """);

        List<String> violations = collectForbiddenGlobalAccessViolations(srcMain);

        assertTrue(violations.stream().anyMatch(v -> v.contains("SharedFixtureObjectInstance")
                        && v.contains("LevelManager.getInstance(")),
                () -> "Expected shared object singleton access to be reported, got: " + violations);
    }

    @Test
    void consolidatedGlobalAccessScan_rejectsLeafManagerBridgeCalls(@TempDir Path tempDir) throws IOException {
        Path srcMain = fixtureSourceRoot(tempDir);
        Path source = srcMain.resolve("com/openggf/level/objects/SharedFixtureObjectInstance.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package com.openggf.level.objects;
                final class SharedFixtureObjectInstance {
                    void update() {
                        staticObjectManager();
                        staticLevelManager();
                        staticRingManager();
                    }
                }
                """);
        List<String> violations = collectForbiddenGlobalAccessViolations(srcMain);
        for (String bridge : List.of("staticObjectManager(", "staticLevelManager(", "staticRingManager(")) {
            assertTrue(violations.stream().anyMatch(line -> line.contains(bridge)),
                    () -> "Expected leaf manager bridge to be reported: " + bridge + " / " + violations);
        }
    }

    @Test
    void consolidatedGlobalAccessScan_coversGameObjectRuntimeFallbacks(@TempDir Path tempDir) throws IOException {
        Path srcMain = fixtureSourceRoot(tempDir);
        Path source = srcMain.resolve("com/openggf/game/sonic1/objects/GameFixtureObjectInstance.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package com.openggf.game.sonic1.objects;

                final class GameFixtureObjectInstance {
                    void update() {
                        EngineServices.current();
                    }
                }
                """);

        List<String> violations = collectForbiddenGlobalAccessViolations(srcMain);

        assertTrue(violations.stream().anyMatch(v -> v.contains("GameFixtureObjectInstance")
                        && v.contains("EngineServices.")),
                () -> "Expected game object EngineServices access to be reported, got: " + violations);
    }

    @Test
    void consolidatedGlobalAccessScan_doesNotTrustWholeBridgeClasses(@TempDir Path tempDir) throws IOException {
        Path srcMain = fixtureSourceRoot(tempDir);
        Path source = srcMain.resolve("com/openggf/level/objects/DefaultObjectServices.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package com.openggf.level.objects;

                final class DefaultObjectServices {
                    void unexpectedBridgeGrowth() {
                        GameServices.camera();
                    }
                }
                """);

        List<String> violations = collectForbiddenGlobalAccessViolations(srcMain);

        assertTrue(violations.stream().anyMatch(v -> v.contains("DefaultObjectServices")
                        && v.contains("GameServices.")),
                () -> "Expected unapproved bridge-class GameServices access to be reported, got: " + violations);
    }

    @Test
    void consolidatedGlobalAccessScan_allowsDocumentedCallerBridgeAccess(@TempDir Path tempDir)
            throws IOException {
        Path srcMain = fixtureSourceRoot(tempDir);
        Path source = srcMain.resolve("com/openggf/level/objects/ObjectManager.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package com.openggf.level.objects;

                final class ObjectManager {
                    void defaultServices() {
                        EngineServices.current();
                    }
                }
                """);

        List<String> violations = collectForbiddenGlobalAccessViolations(srcMain);

        assertTrue(violations.isEmpty(),
                () -> "Expected documented bridge access to be allowed, got: " + violations);
    }

    @Test
    void consolidatedGlobalAccessScan_rejectsApprovedOperationInAnotherCaller(@TempDir Path tempDir)
            throws IOException {
        Path root = fixtureSourceRoot(tempDir);
        Files.writeString(root.resolve("com/openggf/level/objects/ObjectManager.java"), """
                class ObjectManager {
                    void replacementCaller() { EngineServices /* split */ . current(); }
                }
                """);
        List<String> violations = collectForbiddenGlobalAccessViolations(root);
        assertEquals(1, violations.size());
        assertTrue(violations.getFirst().contains("ObjectManager#replacementCaller()->EngineServices.current()"));
    }

    @Test
    void consolidatedGlobalAccessScan_rejectsDuplicateApprovedOperationOnOneLine(@TempDir Path tempDir)
            throws IOException {
        Path root = fixtureSourceRoot(tempDir);
        Files.writeString(root.resolve("com/openggf/level/objects/ObjectManager.java"), """
                class ObjectManager {
                    void defaultServices() { EngineServices.current(); EngineServices.current(); }
                }
                """);
        List<String> violations = collectForbiddenGlobalAccessViolations(root);
        assertEquals(1, violations.size());
        assertTrue(violations.getFirst().contains("observed=2 approved=1"));
    }

    @Test
    void consolidatedGlobalAccessScan_rejectsRedistributedRegistryCallsAndStaleCaller(@TempDir Path tempDir)
            throws IOException {
        Path root = fixtureSourceRoot(tempDir);
        Files.writeString(root.resolve("com/openggf/level/objects/AbstractObjectRegistry.java"), """
                class AbstractObjectRegistry {
                    void currentRomZoneId() { GameServices.levelOrNull(); GameServices.levelOrNull(); }
                    void currentLevel() {}
                }
                """);
        List<String> violations = collectForbiddenGlobalAccessViolations(root);
        assertEquals(1, violations.size());
        assertTrue(violations.getFirst().contains("currentRomZoneId()") && violations.getFirst().contains("observed=2 approved=1"));
        List<String> inventory = RuntimeAccessSourceScanner.differences(scanGlobalAccesses(root), approvedAccessCounts());
        assertTrue(inventory.stream().anyMatch(line -> line.contains("AbstractObjectRegistry#currentLevel()")
                && line.contains("observed=0 approved=1")));
    }

    @Test
    void consolidatedGlobalAccessScan_failsOnMissingPackageOrUnreadableInput(@TempDir Path tempDir)
            throws IOException {
        Path root = fixtureSourceRoot(tempDir);
        Path source = root.resolve("com/openggf/level/objects/Broken.java");
        Files.createDirectory(source);
        assertThrows(IOException.class, () -> collectForbiddenGlobalAccessViolations(root));
        Files.delete(source);
        Path directory = root.resolve(OBJECT_GLOBAL_ACCESS_PACKAGES[0]);
        Files.delete(directory.resolve("Clean.java"));
        Files.delete(directory);
        assertThrows(IOException.class, () -> collectForbiddenGlobalAccessViolations(root));
    }

    /**
     * Monitored singleton class names. Source-level scan looks for
     * {@code <SimpleName>.getInstance(} in object source files. The
     * second-column value (internal class name) is retained for traceability
     * to the previous bytecode-scan implementation but is not used by the
     * source scan.
     */
    private static final Map<String, String> MONITORED_SINGLETONS = Map.ofEntries(
            // Core runtime-owned managers
            Map.entry("Camera", "com/openggf/camera/Camera"),
            Map.entry("LevelManager", "com/openggf/level/LevelManager"),
            Map.entry("AudioManager", "com/openggf/audio/AudioManager"),
            Map.entry("GameStateManager", "com/openggf/game/GameStateManager"),
            Map.entry("SpriteManager", "com/openggf/sprites/managers/SpriteManager"),
            Map.entry("WaterSystem", "com/openggf/level/WaterSystem"),
            Map.entry("FadeManager", "com/openggf/graphics/FadeManager"),
            Map.entry("GraphicsManager", "com/openggf/graphics/GraphicsManager"),
            // Game-specific singletons (should use services().gameService() or services().levelEventProvider())
            Map.entry("Sonic1SwitchManager", "com/openggf/game/sonic1/Sonic1SwitchManager"),
            Map.entry("Sonic1ConveyorState", "com/openggf/game/sonic1/Sonic1ConveyorState"),
            Map.entry("Sonic2LevelEventManager", "com/openggf/game/sonic2/Sonic2LevelEventManager"),
            Map.entry("Sonic2SpecialStageManager", "com/openggf/game/sonic2/specialstage/Sonic2SpecialStageManager"),
            Map.entry("Sonic3kLevelEventManager", "com/openggf/game/sonic3k/Sonic3kLevelEventManager"),
            Map.entry("Sonic3kTitleCardManager", "com/openggf/game/sonic3k/Sonic3kTitleCardManager"),
            Map.entry("CrossGameFeatureProvider", "com/openggf/game/CrossGameFeatureProvider")
    );

    @Test
    void objectInstances_shouldNotCallGetInstance() throws IOException {
        Path srcMain = Path.of("src/main/java");
        if (!Files.isDirectory(srcMain)) {
            // Sources not present — skip gracefully (e.g., running from a
            // packaged JAR without the source tree).
            return;
        }

        Map<String, List<String>> violations = new TreeMap<>();

        for (String pkg : OBJECT_PACKAGES) {
            Path pkgDir = srcMain.resolve(pkg);
            if (!Files.isDirectory(pkgDir)) continue;

            try (Stream<Path> sourceFiles = Files.walk(pkgDir)) {
                sourceFiles
                        .filter(p -> p.toString().endsWith(".java"))
                        .forEach(sourceFile -> {
                            String className = srcMain.relativize(sourceFile).toString()
                                    .replace('\\', '/').replace(".java", "").replace('/', '.');
                            List<String> found = scanForGetInstance(sourceFile);
                            if (!found.isEmpty()) {
                                violations.put(className, found);
                            }
                        });
            }
        }

        // Check for regressions: classes not in KNOWN_UNMIGRATED or PERMANENT_EXCEPTIONS
        Set<String> regressions = new TreeSet<>();
        for (String violator : violations.keySet()) {
            if (!KNOWN_UNMIGRATED.contains(violator) && !PERMANENT_EXCEPTIONS.contains(violator)) {
                regressions.add(violator);
            }
        }

        // Check for completed migrations: classes in KNOWN_UNMIGRATED that no longer violate
        Set<String> migrated = new TreeSet<>();
        for (String known : KNOWN_UNMIGRATED) {
            if (!violations.containsKey(known)) {
                migrated.add(known);
            }
        }

        StringBuilder msg = new StringBuilder();

        if (!regressions.isEmpty()) {
            msg.append("\n=== REGRESSIONS: new getInstance() usage in object code ===\n");
            msg.append("These classes use getInstance() but are not in KNOWN_UNMIGRATED.\n");
            msg.append("Migrate them to services() or add to KNOWN_UNMIGRATED if migration is pending:\n\n");
            for (String cls : regressions) {
                msg.append("  \"").append(cls).append("\",  // ").append(String.join(", ", violations.get(cls))).append("\n");
            }
        }

        if (!migrated.isEmpty()) {
            msg.append("\n=== COMPLETED MIGRATIONS: remove from KNOWN_UNMIGRATED ===\n");
            msg.append("These classes no longer use getInstance() — remove them from the set:\n\n");
            for (String cls : migrated) {
                msg.append("  \"").append(cls).append("\",\n");
            }
        }

        if (!msg.isEmpty()) {
            fail(msg.toString());
        }
    }

    @Test
    void objectPackages_shouldNotReferenceGameServicesInSource() throws IOException {
        Path srcMain = Path.of("src/main/java");
        if (!Files.isDirectory(srcMain)) {
            return;
        }

        List<String> violations = new ArrayList<>();
        for (String pkg : OBJECT_PACKAGES) {
            Path pkgDir = srcMain.resolve(pkg);
            if (!Files.isDirectory(pkgDir)) continue;

            try (Stream<Path> files = Files.walk(pkgDir)) {
                files.filter(path -> path.toString().endsWith(".java"))
                        .forEach(path -> {
                            try {
                                String className = srcMain.relativize(path).toString()
                                        .replace('\\', '/').replace(".java", "").replace('/', '.');
                                if (PERMANENT_EXCEPTIONS.contains(className)) {
                                    return;
                                }
                                String content = Files.readString(path);
                                if (content.contains("GameServices.")) {
                                    violations.add(className);
                                }
                            } catch (IOException ignored) {
                            }
                        });
            }
        }

        if (!violations.isEmpty()) {
            fail("Object packages must not reference GameServices directly:\n  "
                    + String.join("\n  ", new TreeSet<>(violations)));
        }
    }

    @Test
    void sharedObjectInstances_shouldNotReferenceGameServicesInSource() throws IOException {
        Path srcMain = Path.of("src/main/java");
        if (!Files.isDirectory(srcMain)) {
            return;
        }

        Path pkgDir = srcMain.resolve(SHARED_OBJECT_PACKAGE);
        if (!Files.isDirectory(pkgDir)) {
            return;
        }

        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(pkgDir)) {
            files.filter(path -> path.toString().endsWith("ObjectInstance.java"))
                    .forEach(path -> {
                        try {
                            String className = srcMain.relativize(path).toString()
                                    .replace('\\', '/').replace(".java", "").replace('/', '.');
                            if (SHARED_OBJECT_SOURCE_EXCEPTIONS.contains(className)) {
                                return;
                            }
                            String content = Files.readString(path);
                            if (content.contains("GameServices.")) {
                                violations.add(className);
                            }
                        } catch (IOException ignored) {
                        }
                    });
        }

        if (!violations.isEmpty()) {
            fail("Shared object instances must not reference GameServices directly:\n  "
                    + String.join("\n  ", new TreeSet<>(violations)));
        }
    }

    @Test
    void productionCode_shouldNotCallAizTerrainSwapNoArgHelper() throws IOException {
        Path srcMain = Path.of("src/main/java");
        if (!Files.isDirectory(srcMain)) {
            return;
        }

        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(srcMain)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            if (path.endsWith(Path.of("com/openggf/game/sonic3k/objects/AizIntroTerrainSwap.java"))) {
                                return;
                            }
                            String content = Files.readString(path);
                            if (content.contains("AizIntroTerrainSwap.applyMainLevelOverlays();")
                                    || content.contains("AizIntroTerrainSwap.preloadOverlayData();")
                                    || content.contains("AizIntroTerrainSwap.precomputeTransitionTilemaps();")) {
                                violations.add(srcMain.relativize(path).toString());
                            }
                        } catch (IOException ignored) {
                        }
                    });
        }
        if (!violations.isEmpty()) {
            fail("AIZ terrain swap must use explicit ObjectServices:\n  "
                    + String.join("\n  ", new TreeSet<>(violations)));
        }
    }

    @Test
    void objectPackages_shouldNotReferenceProcessGlobalRuntimeFallbacksInSource() throws IOException {
        Path srcMain = Path.of("src/main/java");
        if (!Files.isDirectory(srcMain)) {
            return;
        }

        List<String> violations = new ArrayList<>();
        for (String pkg : OBJECT_PACKAGES) {
            Path pkgDir = srcMain.resolve(pkg);
            if (!Files.isDirectory(pkgDir)) continue;

            try (Stream<Path> files = Files.walk(pkgDir)) {
                files.filter(path -> path.toString().endsWith(".java"))
                        .forEach(path -> {
                            try {
                                String className = srcMain.relativize(path).toString()
                                        .replace('\\', '/').replace(".java", "").replace('/', '.');
                                if (PERMANENT_EXCEPTIONS.contains(className)) {
                                    return;
                                }
                                String content = Files.readString(path);
                                if (content.contains("RuntimeManager.getCurrent()")
                                        || content.contains("EngineContext.fromLegacySingletonsForBootstrap()")) {
                                    violations.add(className);
                                }
                            } catch (IOException ignored) {
                            }
                        });
            }
        }

        if (!violations.isEmpty()) {
            fail("Object/helper packages must not reach process-global runtime fallbacks directly:\n  "
                    + String.join("\n  ", new TreeSet<>(violations)));
        }
    }

    @Test
    void objectPackages_shouldNotNullCheckStrictServicesAccessor() throws IOException {
        Path srcMain = Path.of("src/main/java");
        if (!Files.isDirectory(srcMain)) {
            return;
        }

        List<String> violations = new ArrayList<>();
        for (String pkg : OBJECT_SERVICE_NULL_CHECK_PACKAGES) {
            Path pkgDir = srcMain.resolve(pkg);
            if (!Files.isDirectory(pkgDir)) continue;

            try (Stream<Path> files = Files.walk(pkgDir)) {
                files.filter(path -> path.toString().endsWith(".java"))
                        .forEach(path -> {
                            try {
                                SourceText source = ObjectGuardSourceScanner.sourceWithoutCommentOnlyLines(
                                        Files.readAllLines(path));
                                java.util.regex.Matcher matcher = STRICT_SERVICES_NULL_CHECK.matcher(source.text());
                                while (matcher.find()) {
                                    violations.add(String.format("%s:%d",
                                            srcMain.relativize(path).toString(), source.lineAt(matcher.start())));
                                }
                            } catch (IOException ignored) {
                            }
                        });
            }
        }

        if (!violations.isEmpty()) {
            fail("Object code must not null-check services(); use tryServices() for optional fallback paths:\n  "
                    + String.join("\n  ", violations));
        }
    }

    @Test
    void objectConstructionThreadLocalWrites_shouldStayInsideScopedContextHelper() throws IOException {
        Path srcMain = Path.of("src/main/java");
        if (!Files.isDirectory(srcMain)) {
            return;
        }

        List<String> violations = new ArrayList<>();
        Path packageDir = srcMain.resolve(SHARED_OBJECT_PACKAGE);
        try (Stream<Path> files = Files.walk(packageDir)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> collectForbiddenConstructionContextWrites(srcMain, path, violations));
        }

        if (!violations.isEmpty()) {
            fail("Object construction ThreadLocal set/remove must stay inside ObjectConstructionContext.\n"
                    + "Use ObjectConstructionContext.with(...) so nested construction restores the prior scope.\n\n  "
                    + String.join("\n  ", violations));
        }
    }

    /**
     * Scans a Java source file for direct calls to monitored singletons —
     * either {@code <SimpleClassName>.getInstance(} for any class in
     * {@link #MONITORED_SINGLETONS}, or any reference to {@code GameServices.}.
     * Returns the singleton names found. Comment-only lines are stripped so
     * documentation references don't false-positive.
     */
    private List<String> scanForGetInstance(Path sourceFile) {
        try {
            String content = ObjectGuardSourceScanner.sourceWithoutCommentOnlyLines(
                    Files.readAllLines(sourceFile)).text();

            List<String> found = new ArrayList<>();
            for (var entry : MONITORED_SINGLETONS.entrySet()) {
                String simpleName = entry.getKey();
                if (content.contains(simpleName + ".getInstance(")) {
                    found.add(simpleName);
                }
            }
            if (content.contains("GameServices.")) {
                found.add("GameServices");
            }
            return found;
        } catch (IOException e) {
            return List.of();
        }
    }

    private List<String> collectForbiddenGlobalAccessViolations(Path srcMain) throws IOException {
        Map<Access, Integer> actual = scanGlobalAccesses(srcMain);
        Map<Access, Integer> approved = approvedAccessCounts();
        List<String> violations = new ArrayList<>();
        actual.forEach((access, count) -> {
            int allowed = approved.getOrDefault(access, 0);
            if (count > allowed) violations.add(access + " observed=" + count + " approved=" + allowed);
        });
        return violations;
    }

    private static Map<Access, Integer> scanGlobalAccesses(Path srcMain) throws IOException {
        Map<String, String> sources = RuntimeAccessSourceScanner.readRequiredSources(srcMain, OBJECT_GLOBAL_ACCESS_PACKAGES);
        Map<Access, Integer> actual = RuntimeAccessSourceScanner.scan(sources, forbiddenGlobalAccessPatterns());
        // The base owns the existing construction-first manager bridges.
        actual.keySet().removeIf(access -> access.path().equals("com/openggf/level/objects/AbstractObjectInstance.java")
                && MANAGER_BRIDGE_PATTERNS.stream().anyMatch(access.operation()::startsWith));
        return actual;
    }

    private static Map<Access, Integer> approvedAccessCounts() {
        Map<Access, Integer> counts = new TreeMap<>();
        for (ApprovedGlobalRuntimeAccess approved : APPROVED_GLOBAL_RUNTIME_ACCESSES) {
            if (counts.putIfAbsent(approved.access(), approved.count()) != null) {
                throw new IllegalStateException("Duplicate approved bridge: " + approved.access());
            }
        }
        return counts;
    }

    private static Path fixtureSourceRoot(Path tempDir) throws IOException {
        Path srcMain = tempDir.resolve("src/main/java");
        for (String pkg : OBJECT_GLOBAL_ACCESS_PACKAGES) {
            Path directory = Files.createDirectories(srcMain.resolve(pkg));
            Files.writeString(directory.resolve("Clean.java"), "class Clean {}");
        }
        return srcMain;
    }

    private void collectForbiddenConstructionContextWrites(Path srcMain, Path sourceFile, List<String> violations) {
        try {
            String className = ObjectGuardSourceScanner.className(srcMain, sourceFile);
            if ("com.openggf.level.objects.ObjectConstructionContext".equals(className)) {
                return;
            }

            SourceText source = ObjectGuardSourceScanner.sourceWithoutCommentOnlyLines(Files.readAllLines(sourceFile));
            for (String pattern : forbiddenConstructionContextWritePatterns()) {
                int searchFrom = 0;
                while (true) {
                    int match = source.text().indexOf(pattern, searchFrom);
                    if (match < 0) {
                        break;
                    }
                    violations.add(String.format("%s:%d uses `%s` in `%s`",
                            className,
                            source.lineAt(match),
                            pattern,
                            source.lineTextAt(match).trim()));
                    searchFrom = match + pattern.length();
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static List<String> forbiddenConstructionContextWritePatterns() {
        return List.of(
                "CONSTRUCTION_CONTEXT.set(",
                "CONSTRUCTION_CONTEXT.remove(",
                "PRE_ALLOCATED_SLOT.set(",
                "PRE_ALLOCATED_SLOT.remove(");
    }

    private static List<String> forbiddenGlobalAccessPatterns() {
        List<String> patterns = new ArrayList<>();
        patterns.addAll(MANAGER_BRIDGE_PATTERNS);
        patterns.add("GameServices.");
        patterns.add("EngineServices.");
        patterns.add("SessionManager.");
        patterns.add("RuntimeManager.getCurrent(");
        patterns.add("EngineContext.fromLegacySingletonsForBootstrap(");
        patterns.add("GameModuleRegistry.getCurrent(");
        for (String singleton : MONITORED_SINGLETONS.keySet()) {
            patterns.add(singleton + ".getInstance(");
        }
        return patterns;
    }

    private static final List<String> MANAGER_BRIDGE_PATTERNS = List.of(
            "staticObjectManager(", "staticLevelManager(", "staticRingManager(");

    private static ApprovedGlobalRuntimeAccess approved(
            String className, String caller, String operation, String reason) {
        String simpleName = className.substring(className.lastIndexOf('.') + 1);
        return new ApprovedGlobalRuntimeAccess(new Access(className.replace('.', '/') + ".java",
                simpleName + "#" + caller, operation), 1, reason);
    }

    private record ApprovedGlobalRuntimeAccess(Access access, int count, String reason) {
        private ApprovedGlobalRuntimeAccess {
            if (count <= 0 || reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("Approved global access requires a positive count and reason");
            }
        }
    }
}
