package com.openggf.game.rewind.coverage;

import com.openggf.game.GameId;
import com.openggf.game.rewind.GenericFieldCapturer;
import com.openggf.game.rewind.RewindDeferred;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.rewind.schema.RewindFieldPolicy;
import com.openggf.game.rewind.schema.RewindPolicyRegistry;
import com.openggf.level.objects.AbstractObjectInstance;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

            List<String> uncapturedFinalScalars = findUncapturedFinalScalarFields(sc.fqn());

            coverages.add(new ObjectCoverage(
                    sc.fqn(),
                    true,   // isLayoutSpawnable — conservative default; Task 2 refines
                    true,   // isDynamicSpawnable — conservative default; Task 2 refines
                    true,   // hasRecreatePath — default true; Task 3 fills via schema probe
                    uncapturedFinalScalars,
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
     * Reflects all declared fields on the concrete class and its superclasses
     * (up to, but not including, {@link AbstractObjectInstance}) and returns
     * the names of fields that are uncaptured-final-scalar gaps.
     *
     * <p>A field is a gap when:
     * <ul>
     *   <li>It is {@code final}, non-static, non-transient, and non-synthetic.</li>
     *   <li>It is NOT annotated {@code @RewindTransient} or {@code @RewindDeferred}.</li>
     *   <li>Its declared type is a primitive or enum — types that
     *       {@code GenericFieldCapturer} would otherwise capture as scalars.</li>
     *   <li>{@link RewindPolicyRegistry#policyForAudit} does not mark it
     *       {@code TRANSIENT} or {@code DEFERRED} (i.e., it is not excluded
     *       by any registered policy rule).</li>
     *   <li>{@link GenericFieldCapturer#isCapturedByDefaultObjectScalarPolicy}
     *       returns {@code false} — confirming the capturer does not pick it up.</li>
     * </ul>
     *
     * <p>If the class cannot be loaded (e.g., the source was compiled but the
     * class is absent from the test classpath), the method returns an empty list
     * rather than failing, so CI does not die on partial classpaths.
     */
    private static List<String> findUncapturedFinalScalarFields(String fqn) {
        Class<?> cls;
        try {
            cls = Class.forName(fqn);
        } catch (ClassNotFoundException e) {
            // Source scan found the class but it isn't on the classpath; skip silently.
            return List.of();
        }

        List<String> gaps = new ArrayList<>();
        // Walk from the concrete class up to (but not including) AbstractObjectInstance.
        for (Class<?> c = cls;
                c != null && c != AbstractObjectInstance.class && c != Object.class;
                c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (isUncapturedFinalScalar(field)) {
                    gaps.add(field.getName());
                }
            }
        }
        gaps.sort(String::compareTo);
        return List.copyOf(gaps);
    }

    /**
     * Returns {@code true} when the field is a final primitive-or-enum scalar
     * that {@code GenericFieldCapturer}'s default object policy would capture
     * if the field were non-final, but currently skips solely because it is
     * {@code final}.
     *
     * <p>This is an audit-only predicate; it has no effect on runtime rewind
     * behaviour.
     */
    private static boolean isUncapturedFinalScalar(Field field) {
        int mods = field.getModifiers();

        // Must be final (that is the root cause of the gap).
        if (!Modifier.isFinal(mods)) {
            return false;
        }
        // Statics, transients, and synthetics are never part of rewind state.
        if (Modifier.isStatic(mods) || Modifier.isTransient(mods) || field.isSynthetic()) {
            return false;
        }
        // Explicitly suppressed by the object author — not a gap.
        if (field.isAnnotationPresent(RewindTransient.class)
                || field.isAnnotationPresent(RewindDeferred.class)) {
            return false;
        }
        // Only primitive and enum scalar types are in scope.
        // Strings, wrappers, arrays, records, and in-place helpers have
        // separate final-capture rules and are not reported here.
        Class<?> type = field.getType();
        if (!type.isPrimitive() && !type.isEnum()) {
            return false;
        }
        // A registered policy that marks the field TRANSIENT or DEFERRED means
        // the system has already decided to exclude it; not a gap.
        Optional<RewindFieldPolicy> policy = RewindPolicyRegistry.policyForAudit(field);
        if (policy.isPresent()) {
            RewindFieldPolicy p = policy.get();
            if (p == RewindFieldPolicy.TRANSIENT || p == RewindFieldPolicy.DEFERRED) {
                return false;
            }
        }
        // Confirm the capturer does not pick it up (it won't for a final primitive
        // without a codec, but this double-check guards against future changes).
        if (GenericFieldCapturer.isCapturedByDefaultObjectScalarPolicy(field)) {
            return false;
        }
        return true;
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
