package com.openggf.game.rewind.coverage;

import com.openggf.game.GameId;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Analyzes rewind coverage for spawnable game objects by scanning source files
 * for concrete subclasses of {@code AbstractObjectInstance}.
 *
 * <p>Phase 1: skeleton + enumeration only. {@code hasRecreatePath} defaults
 * to {@code true} and the field lists are empty. Tasks 2–4 populate them.
 *
 * <p>The enumeration is source-file-based (mirroring the existing guard tests
 * such as {@code TestObjectServicesMigrationGuard}) rather than reflection-based,
 * because {@code AbstractObjectRegistry.factories} is {@code protected} and
 * its values are opaque lambdas that do not reveal the concrete object class
 * without invoking them (which has construction side effects).
 */
public final class RewindCoverageAnalyzer {

    private RewindCoverageAnalyzer() {
    }

    /**
     * Analyzes rewind coverage for all spawnable objects belonging to the
     * given game. Objects in shared packages ({@code com.openggf.level.objects})
     * are included for all games.
     *
     * @param game the game to analyze
     * @return the coverage report, objects sorted by class name
     */
    public static RewindCoverageReport analyze(GameId game) {
        return buildReport(game);
    }

    /**
     * Analyzes rewind coverage across all games.
     *
     * @return the coverage report covering S1, S2, and S3K objects
     */
    public static RewindCoverageReport analyzeAll() {
        return buildReport(null);
    }

    private static RewindCoverageReport buildReport(GameId filterGame) {
        Path srcMain = ObjectClasspathScan.findSourceRoot();
        if (srcMain == null) {
            // Source tree unavailable (e.g., running from a packaged JAR);
            // fail loudly so CI doesn't pass vacuously.
            throw new IllegalStateException(
                    "Rewind coverage scan found no objects (source root not resolved). " +
                    "The analyzer must run from a source checkout.");
        }

        List<ObjectClasspathScan.SourceClass> classes;
        try {
            classes = ObjectClasspathScan.findConcreteObjectInstances(srcMain);
        } catch (IOException e) {
            throw new RuntimeException("Failed to scan object classes for rewind coverage", e);
        }

        List<ObjectCoverage> coverages = new ArrayList<>();
        for (ObjectClasspathScan.SourceClass sc : classes) {
            if (filterGame != null && !isRelevantForGame(sc.packagePath(), filterGame)) {
                continue;
            }

            // Phase 1 defaults: hasRecreatePath = true, no field gaps.
            // Tasks 2-4 will inspect the rewind schema to fill these.
            coverages.add(new ObjectCoverage(
                    sc.fqn(),
                    true,   // isLayoutSpawnable — conservative default; Task 2 refines
                    true,   // isDynamicSpawnable — conservative default; Task 2 refines
                    true,   // hasRecreatePath — default true; Task 3 fills via schema probe
                    List.of(),  // uncapturedFinalScalarFields — Task 4 fills
                    List.of()   // unIdObjectRefFields — Task 4 fills
            ));
        }

        // Fail loudly if the scan produced zero objects (misconfiguration)
        if (coverages.isEmpty()) {
            throw new IllegalStateException(
                    "Rewind coverage scan found no objects (source root: " + srcMain + "). " +
                    "The analyzer must run from a source checkout with concrete objects.");
        }

        // Sort by className for stable output
        coverages.sort((a, b) -> a.className().compareTo(b.className()));

        return new RewindCoverageReport(coverages);
    }

    /**
     * Returns {@code true} when the given package path should be included
     * for the requested game.
     *
     * <ul>
     *   <li>Game-specific packages ({@code sonic1}, {@code sonic2}, {@code sonic3k})
     *       are included only when the game matches.</li>
     *   <li>Shared packages ({@code com/openggf/level/objects}) are included
     *       for all games.</li>
     * </ul>
     */
    static boolean isRelevantForGame(String packagePath, GameId game) {
        if (packagePath.contains("sonic1/objects") || packagePath.contains("sonic1\\objects")) {
            return game == GameId.S1;
        }
        if (packagePath.contains("sonic2/objects") || packagePath.contains("sonic2\\objects")) {
            return game == GameId.S2;
        }
        if (packagePath.contains("sonic3k/objects") || packagePath.contains("sonic3k\\objects")) {
            return game == GameId.S3K;
        }
        // Shared level/objects package — include for all games
        return true;
    }
}
