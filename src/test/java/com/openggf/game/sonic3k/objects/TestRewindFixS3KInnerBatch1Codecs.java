package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.DynamicObjectRewindCodec;
import com.openggf.level.objects.ObjectRewindDynamicCodecs;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link Sonic3kObjectRegistry} (unioned with the shared codecs)
 * now exposes a dynamic rewind recreate codec for every batch-inner1 inner-class
 * child that was previously dropped on a held-rewind restore.
 *
 * <p>These are static nested children (hazards, ridable platforms, projectiles,
 * a logic shim, and a defeat-cutscene ship) keyed by their JVM binary name
 * ({@code Outer$Inner}). Each codec either relinks the live parent recreated
 * earlier in the restore or re-runs the child's constructor from the captured
 * spawn; non-spawn differentiator scalars were un-finaled so the generic field
 * capturer reapplies them after recreate.
 *
 * <p>Pure registry-content test: it constructs a registry and reads
 * {@code dynamicRewindCodecs()} without a ROM, OpenGL, or an active gameplay
 * session. Full session round-trip coverage is enforced by the rewind coverage
 * guard ({@code TestRewindCoverageGuard}).
 */
class TestRewindFixS3KInnerBatch1Codecs {

    private static Set<String> codecClassNames() {
        Set<String> names = new HashSet<>();
        List<DynamicObjectRewindCodec> codecs = new Sonic3kObjectRegistry().dynamicRewindCodecs();
        for (DynamicObjectRewindCodec codec : codecs) {
            names.add(codec.className());
        }
        for (DynamicObjectRewindCodec codec : ObjectRewindDynamicCodecs.sharedCodecs()) {
            names.add(codec.className());
        }
        return names;
    }

    @Test
    void registersCodecsForBatchInner1S3KChildren() {
        Set<String> names = codecClassNames();

        List<String> required = List.of(
                "com.openggf.game.sonic3k.objects.AizSpikedLogObjectInstance$SpikedLogCollisionChild",
                "com.openggf.game.sonic3k.objects.AizFallingLogObjectInstance$FallingLogChild",
                "com.openggf.game.sonic3k.objects.AizHollowTreeObjectInstance$AizTreeRevealControlObjectInstance",
                "com.openggf.game.sonic3k.objects.HCZWaterDropObjectInstance$WaterDropChild",
                "com.openggf.game.sonic3k.objects.badniks.BlastoidBadnikInstance$BlastoidProjectile",
                "com.openggf.game.sonic3k.objects.badniks.CorkeyBadnikInstance$CorkeyShotChild",
                "com.openggf.game.sonic3k.objects.badniks.DragonflyBadnikInstance$LinkedBodyChild",
                "com.openggf.game.sonic3k.objects.badniks.RibotBadnikInstance$RibotActiveChild",
                "com.openggf.game.sonic3k.objects.badniks.SnaleBlasterBadnikInstance$SnaleBlasterProjectile",
                "com.openggf.game.sonic3k.objects.badniks.SpikerBadnikInstance$SpikerTopSpikeChild",
                "com.openggf.game.sonic3k.objects.badniks.SpikerBadnikInstance$SpikerSpikeProjectile",
                "com.openggf.game.sonic3k.objects.badniks.StarPointerBadnikInstance$OrbitingPointInstance",
                "com.openggf.game.sonic3k.objects.badniks.OrbinautBadnikInstance$OrbinautOrbInstance",
                "com.openggf.game.sonic3k.objects.badniks.TurboSpikerBadnikInstance$TurboSpikerShellChild",
                "com.openggf.game.sonic3k.objects.badniks.MadmoleBadnikInstance$SideDrillChild",
                "com.openggf.game.sonic3k.objects.bosses.IczEndBossInstance$IczEndBossRobotnikEscapeShip");

        for (String name : required) {
            assertTrue(names.contains(name),
                    "missing rewind recreate codec for " + name);
        }
    }
}
