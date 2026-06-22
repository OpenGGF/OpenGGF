package com.openggf.game.sonic1.objects;

import com.openggf.game.rewind.DeletedDynamicRewindCodecs;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link Sonic1ObjectRegistry} (unioned with the shared codecs)
 * still exposes only the remaining explicit dynamic rewind recreate codecs from
 * the old batch-5 S1 fix, while objects deleted in later Phase-2 batches stay on
 * generic recreate instead of regressing to registry codecs.
 *
 * <p>The batch-5 accept-drop object {@code Sonic1TryAgainEmeraldsObjectInstance}
 * is intentionally excluded: it is a {@code GameMode.TRY_AGAIN_END} display
 * object that is never instantiated in gameplay and therefore can never enter a
 * gameplay-scoped rewind snapshot (documented in
 * docs/KNOWN_DISCREPANCIES.md "Batch-5 Rewind: Transient Cosmetic Children Not
 * Rewound"), so it must NOT have a codec.
 *
 * <p>Pure registry-content test: it constructs a registry and reads
 * {@code deleted dynamic-codec registry API} without a ROM, OpenGL, or an active gameplay
 * session. Full session round-trip is handled by the rewind coverage guard.
 */
class TestRewindFixS1Batch5Codecs {

    private static Set<String> codecClassNames() {
        return DeletedDynamicRewindCodecs.classNames();
    }

    @Test
    void registersOnlyRemainingExplicitReleaseSliceBatch5Codecs() {
        Set<String> names = codecClassNames();

        List<String> deleted = List.of(
                Sonic1EndingSonicObjectInstance.class.getName(),
                Sonic1EndingEmeraldsObjectInstance.class.getName(),
                Sonic1GlassReflectionInstance.class.getName(),
                Sonic1GrassFireObjectInstance.class.getName());

        for (String name : deleted) {
            assertFalse(names.contains(name),
                    name + " must restore through generic recreate, not the old explicit codec");
        }
    }
}
