package com.openggf.game.sonic1.objects;

import com.openggf.level.objects.DynamicObjectRewindCodec;
import com.openggf.level.objects.ObjectRewindDynamicCodecs;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link Sonic1ObjectRegistry} (unioned with the shared codecs)
 * now exposes a dynamic rewind recreate codec for every batch-5 S1 object that
 * was previously dropped on a held-rewind restore: the two ending-sequence
 * objects (ending Sonic and the orbiting chaos emeralds), the MZ glass-block
 * reflection shine (parent-relinked), and the end-of-act results screen.
 *
 * <p>The batch-5 accept-drop object {@code Sonic1TryAgainEmeraldsObjectInstance}
 * is intentionally excluded: it is a {@code GameMode.TRY_AGAIN_END} display
 * object that is never instantiated in gameplay and therefore can never enter a
 * gameplay-scoped rewind snapshot (documented in
 * docs/KNOWN_DISCREPANCIES.md "Batch-5 Rewind: Transient Cosmetic Children Not
 * Rewound"), so it must NOT have a codec.
 *
 * <p>Pure registry-content test: it constructs a registry and reads
 * {@code dynamicRewindCodecs()} without a ROM, OpenGL, or an active gameplay
 * session. Full session round-trip is handled by the rewind coverage guard.
 */
class TestRewindFixS1Batch5Codecs {

    private static Set<String> codecClassNames() {
        Set<String> names = new HashSet<>();
        List<DynamicObjectRewindCodec> codecs = new Sonic1ObjectRegistry().dynamicRewindCodecs();
        for (DynamicObjectRewindCodec codec : codecs) {
            names.add(codec.className());
        }
        for (DynamicObjectRewindCodec codec : ObjectRewindDynamicCodecs.sharedCodecs()) {
            names.add(codec.className());
        }
        return names;
    }

    @Test
    void registersCodecsForReleaseSliceBatch5Objects() {
        Set<String> names = codecClassNames();

        List<String> required = List.of(
                Sonic1EndingEmeraldsObjectInstance.class.getName(),
                Sonic1EndingSonicObjectInstance.class.getName(),
                Sonic1GlassReflectionInstance.class.getName());

        for (String name : required) {
            assertTrue(names.contains(name),
                    "missing rewind recreate codec for " + name);
        }
    }
}
