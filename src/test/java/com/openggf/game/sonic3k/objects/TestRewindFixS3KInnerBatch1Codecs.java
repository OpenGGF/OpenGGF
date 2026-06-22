package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.DynamicObjectRewindCodec;
import com.openggf.level.objects.RewindRecreatable;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link Sonic3kObjectRegistry} (unioned with the shared codecs)
 * still exposes a dynamic rewind recreate codec for remaining batch-inner1
 * inner-class children that have not moved to the Phase-2 generic recreate path.
 *
 * <p>These are static nested children (hazards, ridable platforms, projectiles,
 * a logic shim, and a defeat-cutscene ship) keyed by their JVM binary name
 * ({@code Outer$Inner}).
 *
 * <p>Pure registry-content test: it constructs a registry and reads
 * {@code dynamicRewindCodecs()} without a ROM, OpenGL, or an active gameplay
 * session. Full session round-trip coverage is enforced by the rewind coverage
 * guard ({@code TestRewindCoverageGuard}).
 */
class TestRewindFixS3KInnerBatch1Codecs {
    private static final String FALLING_LOG_CHILD =
            "com.openggf.game.sonic3k.objects.AizFallingLogObjectInstance$FallingLogChild";

    private static Set<String> codecClassNames() {
        Set<String> names = new HashSet<>();
        List<DynamicObjectRewindCodec> codecs = new Sonic3kObjectRegistry().dynamicRewindCodecs();
        for (DynamicObjectRewindCodec codec : codecs) {
            names.add(codec.className());
        }
        return names;
    }

    @Test
    void keepsDynamicRecreatePathsForBatchInner1S3KChildren() {
        List<String> required = List.of(FALLING_LOG_CHILD);

        for (String name : required) {
            assertTrue(dynamicRecreatePathExists(name),
                    "missing rewind dynamic recreate path for " + name);
        }
    }

    @Test
    void fallingLogChildHasDynamicRecreatePathWithoutExplicitCodec() {
        assertTrue(dynamicRecreatePathExists(FALLING_LOG_CHILD),
                "FallingLogChild must keep a dynamic recreate path after codec deletion");
        assertFalse(codecClassNames().contains(FALLING_LOG_CHILD),
                "FallingLogChild must no longer be registered as an explicit dynamic codec");
    }

    private static boolean dynamicRecreatePathExists(String className) {
        if (codecClassNames().contains(className)) {
            return true;
        }
        try {
            Class<?> cls = Class.forName(className);
            return RewindRecreatable.class.isAssignableFrom(cls);
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }
}
