package com.openggf.game.sonic2.objects;

import com.openggf.level.objects.DynamicObjectRewindCodec;
import com.openggf.level.objects.ObjectRewindDynamicCodecs;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link Sonic2ObjectRegistry} (unioned with the shared codecs)
 * now exposes a dynamic rewind recreate codec for every batch-inner1 S2
 * inner-class hazard/solid child that was previously dropped on a held-rewind
 * restore.
 *
 * <p>These are static nested children keyed by their JVM binary name
 * ({@code Outer$Inner}):
 * <ul>
 *   <li>{@code SmallMetalPformObjectInstance$SmallMetalPformChildInstance} — the WFZ
 *       ObjBD rideable metal platform child (top-solid {@code SolidObjectProvider}).
 *       Self-contained {@code exactSpawnCodec}: the constructor only needs
 *       {@code (spawn, xFlipped)} and {@code xFlipped = (renderFlags & 0x01) != 0} is
 *       fully spawn-derivable, so no parent relink and no un-finaling is required.</li>
 *   <li>{@code Sonic2DEZEggmanInstance$BarrierWall} — the DEZ Eggman solid barrier wall
 *       ({@code SolidObjectProvider}). Parent-relink codec re-running the
 *       {@code (x, y)} ctor and relinking the restored Eggman's {@code barrierWall}
 *       back-reference; {@code wallX}/{@code wallY} were un-finaled so the capturer
 *       reapplies them.</li>
 *   <li>{@code Sonic2MTZBossInstance$MTZBossLaser} — the MTZ boss fired laser
 *       ({@code TouchResponseProvider} HURT hazard). Parent-relink codec; {@code xVel}
 *       (firing direction, not spawn-derivable) was un-finaled so the capturer reapplies
 *       it after recreate.</li>
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
        for (DynamicObjectRewindCodec codec : ObjectRewindDynamicCodecs.sharedCodecs()) {
            names.add(codec.className());
        }
        return names;
    }

    @Test
    void registersCodecsForBatchInner1S2Children() {
        Set<String> names = codecClassNames();

        List<String> required = List.of(
                "com.openggf.game.sonic2.objects.SmallMetalPformObjectInstance"
                        + "$SmallMetalPformChildInstance",
                "com.openggf.game.sonic2.objects.bosses.Sonic2DEZEggmanInstance$BarrierWall",
                "com.openggf.game.sonic2.objects.bosses.Sonic2MTZBossInstance$MTZBossLaser");

        for (String name : required) {
            assertTrue(names.contains(name),
                    "missing rewind recreate codec for " + name);
        }
    }
}
