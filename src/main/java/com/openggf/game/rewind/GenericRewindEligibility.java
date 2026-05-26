package com.openggf.game.rewind;

import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.AbstractBadnikInstance;
import com.openggf.level.objects.PerObjectRewindSnapshot;

import java.lang.reflect.Modifier;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class GenericRewindEligibility {
    private static final Set<Class<?>> PRODUCTION_ELIGIBLE = Set.of();
    private static final Set<Class<?>> TEST_OR_MIGRATION_ELIGIBLE = ConcurrentHashMap.newKeySet();

    public static boolean isEligible(Class<?> type) {
        Objects.requireNonNull(type, "type");
        return PRODUCTION_ELIGIBLE.contains(type) || TEST_OR_MIGRATION_ELIGIBLE.contains(type);
    }

    public static boolean usesDefaultObjectSubclassCapture(Class<?> type) {
        Objects.requireNonNull(type, "type");
        return AbstractObjectInstance.class.isAssignableFrom(type)
                && type != AbstractObjectInstance.class
                && !Modifier.isAbstract(type.getModifiers())
                && !declaresConcreteObjectRewindOverride(type);
    }

    public static boolean usesDefaultBadnikSubclassCapture(Class<?> type) {
        Objects.requireNonNull(type, "type");
        return AbstractBadnikInstance.class.isAssignableFrom(type)
                && type != AbstractBadnikInstance.class
                && !Modifier.isAbstract(type.getModifiers())
                && !declaresConcreteObjectRewindOverrideBefore(type, AbstractBadnikInstance.class);
    }

    public static Set<Class<?>> eligibleClassesForAudit() {
        Set<Class<?>> eligible = ConcurrentHashMap.newKeySet();
        eligible.addAll(PRODUCTION_ELIGIBLE);
        eligible.addAll(TEST_OR_MIGRATION_ELIGIBLE);
        return Set.copyOf(eligible);
    }

    public static void registerForTestOrMigration(Class<?> type) {
        TEST_OR_MIGRATION_ELIGIBLE.add(Objects.requireNonNull(type, "type"));
    }

    public static void clearForTest() {
        TEST_OR_MIGRATION_ELIGIBLE.clear();
    }

    public static boolean declaresConcreteObjectRewindOverride(Class<?> type) {
        Objects.requireNonNull(type, "type");
        return declaresConcreteMethod(type, "captureRewindState")
                || declaresConcreteMethod(type, "restoreRewindState", PerObjectRewindSnapshot.class);
    }

    private static boolean declaresConcreteMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        for (Class<?> current = type;
                current != null && current != AbstractObjectInstance.class;
                current = current.getSuperclass()) {
            try {
                var method = current.getDeclaredMethod(name, parameterTypes);
                if (!Modifier.isAbstract(method.getModifiers())
                        && !method.isSynthetic()
                        && !method.isBridge()) {
                    return true;
                }
            } catch (NoSuchMethodException e) {
                // Continue walking toward AbstractObjectInstance.
            }
        }
        return false;
    }

    private static boolean declaresConcreteObjectRewindOverrideBefore(Class<?> type, Class<?> stopExclusive) {
        return declaresConcreteMethodBefore(type, stopExclusive, "captureRewindState")
                || declaresConcreteMethodBefore(type, stopExclusive,
                "restoreRewindState", PerObjectRewindSnapshot.class);
    }

    private static boolean declaresConcreteMethodBefore(Class<?> type, Class<?> stopExclusive,
            String name, Class<?>... parameterTypes) {
        for (Class<?> current = type;
                current != null && current != stopExclusive;
                current = current.getSuperclass()) {
            try {
                var method = current.getDeclaredMethod(name, parameterTypes);
                if (!Modifier.isAbstract(method.getModifiers())
                        && !method.isSynthetic()
                        && !method.isBridge()) {
                    return true;
                }
            } catch (NoSuchMethodException e) {
                // Continue walking toward the stop class.
            }
        }
        return false;
    }

    private GenericRewindEligibility() {
    }
}
