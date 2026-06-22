package com.openggf.game.sonic1.objects;

import com.openggf.level.objects.DynamicObjectRewindCodec;
import com.openggf.level.objects.RewindRecreatable;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link Sonic1ObjectRegistry} (unioned with the shared codecs)
 * now exposes a dynamic rewind recreate path for every batch-inner1 S1
 * inner-class hazard/solid/cutscene child that was previously dropped on a
 * held-rewind restore (no codec matched their JVM binary name).
 *
 * <p>These are static nested children keyed by their JVM binary name
 * ({@code Outer$Inner}). Parent-owned children either keep an explicit codec or
 * implement {@link RewindRecreatable} and use the generic recreate path; non-spawn
 * differentiator scalars are then reapplied by the generic field capturer:
 * <ul>
 *   <li>{@code Sonic1OrbinautBadnikInstance$OrbSpikeObjectInstance} — Orbinaut HURT
 *       satellite/projectile. Uses {@link RewindRecreatable} generic recreate to
 *       relink/adopt into the live Orbinaut parent; all live state is non-final
 *       and reapplied after recreate.</li>
 *   <li>{@code Sonic1FalseFloorInstance$FalseFloorBlock} — SBZ2 boss collapsing-floor
 *       tile. Uses {@link RewindRecreatable} generic recreate to re-register with the
 *       restored master; {@code currentX}/{@code currentY}/{@code blockIndex} are
 *       restored afterward.</li>
 * </ul>
 *
 * <p><b>Intentionally absent:</b> {@code ScrapEggmanButton} is construction-spawned
 * (the {@code Sonic1ScrapEggmanInstance} constructor directly calls
 * {@code spawnDynamicObject(button)}). Registering a codec would double it on
 * rewind restore (1 → 2) because boss reconstruction already re-adds it.
 * See {@code TestBossChildNoDoubleSpawnParity} and {@code docs/KNOWN_DISCREPANCIES.md}.
 *
 * <p>The other batch-inner1 child
 * ({@code Sonic1JunctionObjectInstance$Sonic1JunctionChildInstance}) is intentionally
 * accept-drop (display-only, parent re-emits when {@code childInstance == null}), so it
 * is documented in {@code docs/KNOWN_DISCREPANCIES.md} and is NOT asserted here.
 *
 * <p>Pure registry-content test: it constructs a registry and reads
 * {@code dynamicRewindCodecs()} without a ROM, OpenGL, or an active gameplay
 * session. Full session round-trip coverage is enforced by the rewind coverage
 * guard ({@code TestRewindCoverageGuard}).
 */
class TestRewindFixS1InnerBatch1Codecs {

    private static Set<String> codecClassNames() {
        Set<String> names = new HashSet<>();
        List<DynamicObjectRewindCodec> codecs = new Sonic1ObjectRegistry().dynamicRewindCodecs();
        for (DynamicObjectRewindCodec codec : codecs) {
            names.add(codec.className());
        }
        return names;
    }

    @Test
    void orbSpikeUsesRewindRecreatableInsteadOfExplicitCodec() throws Exception {
        Set<String> names = codecClassNames();

        String orbSpike = "com.openggf.game.sonic1.objects.badniks.Sonic1OrbinautBadnikInstance"
                + "$OrbSpikeObjectInstance";
        Class<?> orbSpikeClass = Class.forName(orbSpike);

        assertFalse(names.contains(orbSpike),
                "OrbSpikeObjectInstance should restore via RewindRecreatable genericRecreate, "
                        + "not a Sonic1ObjectRegistry explicit codec");
        assertTrue(RewindRecreatable.class.isAssignableFrom(orbSpikeClass),
                "OrbSpikeObjectInstance must opt into the generic RewindRecreatable path");
    }

    @Test
    void falseFloorBlockUsesRewindRecreatableInsteadOfExplicitCodec() {
        String falseFloorBlock =
                "com.openggf.game.sonic1.objects.bosses.Sonic1FalseFloorInstance"
                        + "$FalseFloorBlock";

        assertFalse(codecClassNames().contains(falseFloorBlock),
                "FalseFloorBlock should restore via RewindRecreatable genericRecreate, "
                        + "not a Sonic1ObjectRegistry explicit codec");
        assertTrue(RewindRecreatable.class.isAssignableFrom(
                        com.openggf.game.sonic1.objects.bosses.Sonic1FalseFloorInstance
                                .FalseFloorBlock.class),
                "FalseFloorBlock must opt into the generic RewindRecreatable path");
    }
}
