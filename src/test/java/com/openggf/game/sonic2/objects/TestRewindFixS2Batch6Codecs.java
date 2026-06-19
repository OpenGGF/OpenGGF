package com.openggf.game.sonic2.objects;

import com.openggf.game.sonic2.objects.bosses.CPZBossContainerExtend;
import com.openggf.level.objects.DynamicObjectRewindCodec;
import com.openggf.level.objects.ObjectRewindDynamicCodecs;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link Sonic2ObjectRegistry} (unioned with the shared codecs)
 * now exposes a dynamic rewind recreate codec for every batch-6 S2 object that
 * was previously dropped on a held-rewind restore.
 *
 * <p>Batch-6 adds exact-spawn codecs for the CNZ slot-machine ring prize and the
 * MTZ steam puff, plus parent/sibling-relink codecs for the HTZ seesaw ball
 * (relinked to its placed parent seesaw) and the CPZ-boss container extend
 * (relinked to its live boss + container parents). All four carry gameplay-relevant
 * state (in-flight ring award, frame-3 harmful collision, seesaw launch state,
 * extend->gunk progression), so none are accept-drop.
 *
 * <p>The two cosmetic transient children evaluated in this batch —
 * {@code SuperSonicStarsObjectInstance} (Super Sonic sparkle, re-emitted each frame
 * from the live player) and {@code com.openggf.level.objects.SplashObjectInstance}
 * (water splash, re-emitted on water entry/exit) — are intentionally accept-drop and
 * are documented in {@code docs/KNOWN_DISCREPANCIES.md}; they are deliberately NOT
 * required to have a codec here.
 *
 * <p>Pure registry-content test: it constructs a registry and reads
 * {@code dynamicRewindCodecs()} without a ROM, OpenGL, or an active gameplay
 * session. Full session round-trip is handled by the rewind coverage guard.
 */
class TestRewindFixS2Batch6Codecs {

    private static Set<String> codecClassNames() {
        Set<String> names = new HashSet<>();
        List<DynamicObjectRewindCodec> codecs = new Sonic2ObjectRegistry().dynamicRewindCodecs();
        for (DynamicObjectRewindCodec codec : codecs) {
            names.add(codec.className());
        }
        for (DynamicObjectRewindCodec codec : ObjectRewindDynamicCodecs.sharedCodecs()) {
            names.add(codec.className());
        }
        return names;
    }

    @Test
    void registersCodecsForBatch6S2Objects() {
        Set<String> names = codecClassNames();

        List<String> required = List.of(
                RingPrizeObjectInstance.class.getName(),
                SeesawBallObjectInstance.class.getName(),
                CPZBossContainerExtend.class.getName());

        for (String name : required) {
            assertTrue(names.contains(name),
                    "missing rewind recreate codec for " + name);
        }

        assertFalse(names.contains(SteamPuffObjectInstance.class.getName()),
                "SteamPuffObjectInstance must restore through RewindRecreatable generic recreate, "
                        + "not a batch-6 codec");
    }
}
