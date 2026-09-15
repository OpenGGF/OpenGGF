package com.openggf.audio.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openggf.audio.smps.SmpsSequencerConfig;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Every sequencer-config setting must survive the presentation layer's copy.
 *
 * <p>The presentation catalog binds a session handler to a configuration's
 * settings. Every setting exposed by the builder must survive that operation;
 * validate through public accessors so the guard is independent of the private
 * settings representation.
 *
 * <p>This guard exists because that happened. The S3K collapse effect's volume
 * tail is carried by {@code psgVolumeTail}; the setting was declared on the S3K
 * config and read correctly by the sequencer, and the effect still ended flat
 * in a level, because the copier did not carry it. Driver-level fixtures that
 * pass the game config straight in stayed green throughout, since they never
 * touch the copier. Only a test issuing the effect through the runtime request
 * path could see it.
 *
 * <p>The check is a round trip: set one field away from its default, copy, and
 * read the same field back. Settings whose type has no mechanical "different
 * value" are listed in {@link #UNCOPYABLE_BY_REFLECTION} rather than skipped
 * quietly, so adding a field of a new shape forces a decision here instead of
 * slipping through. Collection and handler contracts are exercised explicitly in
 * {@code TestSmpsAssetCatalog}.
 */
class TestSmpsSequencerConfigCopyCoverageGuard {
    /**
     * Settings this guard cannot vary mechanically; collection and handler
     * behavior is covered by {@code TestSmpsAssetCatalog}.
     *
     * <p>{@code coordFlagHandler} is different in kind: {@code
     * copyConfigWithoutHandler} drops it deliberately and {@code
     * bindLegacyConfig} rebinds it per game, so a round trip is not the
     * contract for it.
     */
    private static final Set<String> UNCOPYABLE_BY_REFLECTION = Set.of(
            "coordFlagHandler",
            "coordFlagParamOverrides",
            "extraTrkEndFlags",
            "fmChannelOrder",
            "psgChannelOrder",
            "speedUpTempos");

    @Test
    void everySettingSurvivesThePresentationConfigCopy() {
        SmpsSequencerConfig baseline = new SmpsSequencerConfig.Builder().build();
        List<String> dropped = new ArrayList<>();
        Set<String> unvaried = new TreeSet<>();
        int checked = 0;

        for (Method setter : SmpsSequencerConfig.Builder.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(setter.getModifiers())
                    || setter.getParameterCount() != 1
                    || setter.getReturnType() != SmpsSequencerConfig.Builder.class) {
                continue;
            }
            String name = setter.getName();
            Method field = accessorNamed(name);
            if (field == null) {
                unvaried.add(name);
                continue;
            }
            Object nonDefault = nonDefaultValue(
                    setter.getParameterTypes()[0], read(field, baseline));
            if (nonDefault == null) {
                unvaried.add(name);
                continue;
            }

            SmpsSequencerConfig source;
            try {
                SmpsSequencerConfig.Builder builder =
                        new SmpsSequencerConfig.Builder();
                setter.invoke(builder, nonDefault);
                source = builder.build();
            } catch (ReflectiveOperationException | RuntimeException failed) {
                unvaried.add(name);
                continue;
            }

            SmpsSequencerConfig copied =
                    SmpsAssetCatalog.copyConfigWithoutHandler(source);
            checked++;
            if (!Objects.equals(read(field, source), read(field, copied))) {
                dropped.add(String.format(
                        "%s (set %s, copy returned %s)",
                        name, read(field, source), read(field, copied)));
            }
        }

        assertEquals(List.of(), dropped,
                "these sequencer-config settings are lost when the presentation"
                + " layer copies the config, so every sound played through"
                + " AudioManager ignores them and silently uses the builder"
                + " default. Preserve each setting through SmpsConfigBinding");
        assertEquals(UNCOPYABLE_BY_REFLECTION, unvaried,
                "the set of settings this guard cannot vary mechanically has"
                + " changed. A new one must either have explicit coverage in"
                + " TestSmpsAssetCatalog and be listed here, or made"
                + " round-trippable so the guard covers it");
        int roundTripped = checked;
        assertTrue(roundTripped >= 30, () ->
                "the guard should be exercising most of the config surface;"
                + " it only round-tripped " + roundTripped + " settings");
    }

    /** A value of {@code type} that differs from {@code current}, or null. */
    private Object nonDefaultValue(Class<?> type, Object current) {
        if (type == boolean.class || type == Boolean.class) {
            return !((Boolean) current);
        }
        if (type == int.class || type == Integer.class) {
            return ((Integer) current) + 1;
        }
        if (type == long.class || type == Long.class) {
            return ((Long) current) + 1L;
        }
        if (type.isEnum()) {
            for (Object constant : type.getEnumConstants()) {
                if (!Objects.equals(constant, current)) {
                    return constant;
                }
            }
        }
        return null;
    }

    private Method accessorNamed(String name) {
        String suffix = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        for (String prefix : List.of("get", "is")) {
            try {
                return SmpsSequencerConfig.class.getMethod(prefix + suffix);
            } catch (NoSuchMethodException absent) {
                // Boolean properties use isX; other properties use getX.
            }
        }
        return null;
    }

    private Object read(Method accessor, SmpsSequencerConfig config) {
        try {
            return accessor.invoke(config);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("cannot read " + accessor.getName(), failure);
        }
    }
}
