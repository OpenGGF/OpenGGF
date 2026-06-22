package com.openggf.game.sonic2.objects;

import com.openggf.level.objects.DynamicObjectRewindCodec;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Verifies that {@link Sonic2ObjectRegistry} (unioned with the shared codecs)
 * no longer exposes explicit dynamic rewind recreate codecs for batch-inner1 S2
 * children that now restore through generic recreate.
 *
 * <p>These are static nested children keyed by their JVM binary name
 * ({@code Outer$Inner}):
 * <ul>
 *   <li>{@code SmallMetalPformObjectInstance$SmallMetalPformChildInstance} — the WFZ
 *       ObjBD rideable metal platform child (top-solid {@code SolidObjectProvider}).
 *       Restores through generic recreate; {@code xFlipped = (renderFlags & 0x01) != 0}
 *       is fully spawn-derivable, so no parent relink is required.</li>
 *   <li>{@code Sonic2DEZEggmanInstance$BarrierWall} — the DEZ Eggman solid barrier wall
 *       ({@code SolidObjectProvider}). Restores through generic recreate; Eggman's
 *       structural {@code barrierWall} back-reference is relinked by the
 *       {@code RewindRecreatable} hook.</li>
 *   <li>{@code Sonic2MTZBossInstance$MTZBossLaser} — the MTZ boss fired laser
 *       ({@code TouchResponseProvider} HURT hazard). Now restored through generic
 *       recreate; {@code xVel} (firing direction, not spawn-derivable) is reapplied
 *       by the capturer after recreate.</li>
 * </ul>
 *
 * <p>Pure registry-content test: it constructs a registry and reads
 * {@code dynamicRewindCodecs()} without a ROM, OpenGL, or an active gameplay
 * session. Full session round-trip coverage is enforced by the rewind coverage
 * guard ({@code TestRewindCoverageGuard}).
 */
class TestRewindFixS2InnerBatch1Codecs {

    private static Set<String> codecClassNames() {
        Set<String> names = new HashSet<>();
        List<DynamicObjectRewindCodec> codecs = new Sonic2ObjectRegistry().dynamicRewindCodecs();
        for (DynamicObjectRewindCodec codec : codecs) {
            names.add(codec.className());
        }
        return names;
    }

    @Test
    void batchInner1S2ChildrenHaveExpectedCodecCoverage() {
        Set<String> names = codecClassNames();

        assertFalse(names.contains(
                        "com.openggf.game.sonic2.objects.SmallMetalPformObjectInstance"
                                + "$SmallMetalPformChildInstance"),
                "SmallMetalPform child must restore through RewindRecreatable generic recreate, "
                        + "not a batch-inner1 codec");
        assertFalse(names.contains(
                        "com.openggf.game.sonic2.objects.bosses.Sonic2DEZEggmanInstance$BarrierWall"),
                "DEZ barrier wall must restore through RewindRecreatable generic recreate, "
                        + "not a batch-inner1 codec");
        assertFalse(names.contains(
                        "com.openggf.game.sonic2.objects.bosses.Sonic2MTZBossInstance$MTZBossLaser"),
                "MTZ boss laser must restore through RewindRecreatable generic recreate, "
                        + "not a batch-inner1 codec");
    }
}
