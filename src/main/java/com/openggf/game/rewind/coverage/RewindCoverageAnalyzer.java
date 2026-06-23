package com.openggf.game.rewind.coverage;

import com.openggf.game.GameId;
import com.openggf.game.rewind.GenericFieldCapturer;
import com.openggf.game.rewind.RewindDeferred;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.rewind.schema.RewindFieldPolicy;
import com.openggf.game.rewind.schema.RewindPolicyRegistry;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Analyzes rewind coverage for spawnable game objects by scanning source files
 * for concrete subclasses of {@code AbstractObjectInstance}.
 *
 * <p><b>Recreate-path detection:</b> {@code hasRecreatePath} and
 * {@code isDynamicSpawnable} are derived from an explicit recreate path. A class
 * has a recreate path ({@code hasRecreatePath = true}) if its exact
 * fully-qualified class name appears in caller-supplied
 * {@code extraCodecClassNames}, or if the class implements
 * {@code RewindRecreatable}.
 *
 * <p><b>Phase-1 over-approximation (intentional):</b> This analyzer cannot
 * statically distinguish a <em>layout-spawnable</em> object (one recreated by the
 * registry/placement path from its spawn data, requiring no codec) from a
 * <em>dynamic</em> object that is simply <em>missing</em> a codec. The
 * {@link com.openggf.level.objects.AbstractObjectRegistry#factories factories} map
 * is opaque — its lambda values do not reveal the concrete class they produce
 * without invoking them (which has construction side effects). Therefore, in Phase 1,
 * any class with no codec is reported with a {@code #recreate} gap key even if it
 * is in fact correctly layout-recreated. These false positives are acceptable: they
 * are captured in the committed baseline (Task 7) and never fail the build (report-only).
 * Closing this set — by adding a registry audit API that declares layout-spawnable
 * classes — is explicitly deferred to Phase 2.
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
     * <p>No extra dynamic-codec names are consulted by this overload.
     *
     * @param game the game to analyze
     * @return the coverage report, objects sorted by class name
     */
    public static RewindCoverageReport analyze(GameId game) {
        return analyze(game, Set.of());
    }

    /**
     * Analyzes rewind coverage for all spawnable objects belonging to the
     * given game, using caller-supplied codec class names.
     *
     * <p>Use this overload when a caller needs to include external dynamic-codec
     * class names in recreate-path detection.
     *
     * @param game                  the game to analyze
     * @param extraCodecClassNames  additional fully-qualified class names that have
     *                              a dynamic recreate codec
     * @return the coverage report, objects sorted by class name
     */
    public static RewindCoverageReport analyze(GameId game, Set<String> extraCodecClassNames) {
        return buildReport(game, extraCodecClassNames);
    }

    /**
     * Analyzes rewind coverage across all games.
     *
     * <p>No extra dynamic-codec names are consulted by this overload.
     *
     * @return the coverage report covering S1, S2, and S3K objects
     */
    public static RewindCoverageReport analyzeAll() {
        return analyzeAll(Set.of());
    }

    /**
     * Analyzes rewind coverage across all games, using caller-supplied codec
     * class names.
     *
     * <p>Use this overload when a caller needs to include external dynamic-codec
     * class names in recreate-path detection.
     *
     * @param extraCodecClassNames  additional fully-qualified class names that have
     *                              a dynamic recreate codec
     * @return the coverage report covering S1, S2, and S3K objects
     */
    public static RewindCoverageReport analyzeAll(Set<String> extraCodecClassNames) {
        return buildReport(null, extraCodecClassNames);
    }

    private static RewindCoverageReport buildReport(GameId filterGame, Set<String> extraCodecClassNames) {
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

        // Build the set of class names that have a dynamic recreate codec.
        Set<String> dynamicCodecClassNames = buildDynamicCodecClassNames(extraCodecClassNames);

        List<ObjectCoverage> coverages = new ArrayList<>();
        for (ObjectClasspathScan.SourceClass sc : classes) {
            if (filterGame != null && !isRelevantForGame(sc.packagePath(), filterGame)) {
                continue;
            }

            List<String> uncapturedFinalScalars = findUncapturedFinalScalarFields(sc.fqn());
            List<String> unIdObjectRefs = findUnIdObjectRefFields(sc.fqn());
            boolean isDynamic = dynamicCodecClassNames.contains(sc.fqn())
                    || isRewindRecreatable(sc.fqn());

            coverages.add(new ObjectCoverage(
                    sc.fqn(),
                    true,       // isLayoutSpawnable — conservative default; Phase 2 refines
                    isDynamic,  // isDynamicSpawnable — true iff a dynamic recreate path exists
                    isDynamic,  // hasRecreatePath — true iff a dynamic recreate path exists
                    uncapturedFinalScalars,
                    unIdObjectRefs
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
     * Builds the set of fully-qualified class names that have a dynamic recreate codec
     * from the caller-supplied {@code extraCodecClassNames}.
     *
     * <p>The game registries no longer expose dynamic rewind codecs. The explicit
     * parameter remains for focused tests and migration tools that need to model a
     * temporary codec class without reintroducing registry coupling.
     */
    private static Set<String> buildDynamicCodecClassNames(Set<String> extraCodecClassNames) {
        Set<String> names = new HashSet<>();
        names.addAll(extraCodecClassNames);
        return names;
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
     * Reflects all declared fields on the concrete class and its superclasses
     * (up to, but not including, {@link AbstractObjectInstance}) and returns
     * the names of fields that are un-id'd object-reference gaps.
     *
     * <p>A field is a gap when:
     * <ul>
     *   <li>It is non-static and non-synthetic.</li>
     *   <li>It is NOT annotated {@code @RewindTransient} or {@code @RewindDeferred}.</li>
     *   <li>Its declared type is assignable to {@link ObjectInstance} or
     *       {@link AbstractPlayableSprite} — live object graph references that
     *       cannot be reconstructed from scalar data alone.</li>
     *   <li>{@link RewindPolicyRegistry#policyForAudit} does not mark it
     *       {@code TRANSIENT} or {@code DEFERRED} (excluded from rewind) or
     *       {@code CAPTURED} (restored through object-ref id capture).</li>
     *   <li>No object-ref id capture exists for it.</li>
     * </ul>
     *
     * <p>If the class cannot be loaded the method returns an empty list.
     */
    private static List<String> findUnIdObjectRefFields(String fqn) {
        Class<?> cls;
        try {
            cls = Class.forName(fqn);
        } catch (ClassNotFoundException e) {
            return List.of();
        }

        List<String> gaps = new ArrayList<>();
        for (Class<?> c = cls;
                c != null && c != AbstractObjectInstance.class && c != Object.class;
                c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (isUnIdObjectRef(field)) {
                    gaps.add(field.getName());
                }
            }
        }
        gaps.sort(String::compareTo);
        return List.copyOf(gaps);
    }

    /**
     * Returns {@code true} when the field is a live object-reference gap:
     * its type is an {@link ObjectInstance} or {@link AbstractPlayableSprite} subtype,
     * it is not excluded by annotation or policy, and it has no captured object-ref id.
     *
     * <p>This is an audit-only predicate; it has no effect on runtime rewind behaviour.
     */
    private static boolean isUnIdObjectRef(Field field) {
        int mods = field.getModifiers();

        // Statics and synthetics are never part of rewind state.
        if (Modifier.isStatic(mods) || field.isSynthetic()) {
            return false;
        }
        // Explicitly suppressed by the object author — not a gap.
        if (field.isAnnotationPresent(RewindTransient.class)
                || field.isAnnotationPresent(RewindDeferred.class)) {
            return false;
        }
        // Only object-ref types are in scope: ObjectInstance subtypes and
        // known playable/sidekick types (AbstractPlayableSprite hierarchy).
        Class<?> type = field.getType();
        boolean isObjectRef = ObjectInstance.class.isAssignableFrom(type)
                || AbstractPlayableSprite.class.isAssignableFrom(type);
        if (!isObjectRef) {
            return false;
        }
        // A registered policy that marks the field TRANSIENT or DEFERRED means
        // the system has already decided to exclude it. CAPTURED means the
        // object-reference codec restores it by ObjectRefId. None are gaps.
        // UNSUPPORTED remains reportable.
        Optional<RewindFieldPolicy> policy = RewindPolicyRegistry.policyForAudit(field);
        if (policy.isPresent()) {
            RewindFieldPolicy p = policy.get();
            if (p == RewindFieldPolicy.TRANSIENT
                    || p == RewindFieldPolicy.DEFERRED
                    || p == RewindFieldPolicy.CAPTURED) {
                return false;
            }
        }
        // Remaining object-ref fields are neither excluded nor explicitly id-captured.
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

    /**
     * Returns {@code true} if the class with the given fully-qualified name implements
     * {@link com.openggf.level.objects.RewindRecreatable}.
     *
     * <p>{@link com.openggf.level.objects.ObjectRewindDynamicCodecs#genericRecreate} handles
     * {@code RewindRecreatable} classes via Path 1, so they do not need an
     * explicit dynamic-codec registration to have a recreate path.
     */
    private static boolean isRewindRecreatable(String fqn) {
        try {
            Class<?> cls = Class.forName(fqn);
            return com.openggf.level.objects.RewindRecreatable.class.isAssignableFrom(cls);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
