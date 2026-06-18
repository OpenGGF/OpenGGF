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
 * now exposes a dynamic rewind recreate codec for every batch-inner1 S1
 * inner-class hazard/solid/cutscene child that was previously dropped on a
 * held-rewind restore (no codec matched their JVM binary name).
 *
 * <p>These are static nested children keyed by their JVM binary name
 * ({@code Outer$Inner}). Each codec relinks the live parent recreated earlier
 * in the restore loop, then either re-runs the child's constructor from the
 * captured spawn or reflection-constructs the private nested type; non-spawn
 * differentiator scalars were un-finaled so the generic field capturer reapplies
 * them after recreate:
 * <ul>
 *   <li>{@code Sonic1FalseFloorInstance$FalseFloorBlock} — SBZ2 boss collapsing-floor
 *       tile. Parent-relink codec re-registers the block into the master's
 *       {@code childBlocks}; {@code currentX}/{@code currentY}/{@code blockIndex}
 *       were un-finaled.</li>
 *   <li>{@code Sonic1OrbinautBadnikInstance$OrbSpikeObjectInstance} — Orbinaut HURT
 *       satellite/projectile. Parent-relink codec (reflection ctor); all live state
 *       is non-final and reapplied after recreate.</li>
 *   <li>{@code Sonic1ScrapEggmanInstance$ScrapEggmanButton} — SBZ2 final-boss cutscene
 *       button. Parent-relink codec re-running the
 *       {@code (ObjectSpawn, Sonic1ScrapEggmanInstance)} ctor; {@code buttonX}/{@code buttonY}
 *       are spawn-derived, {@code buttonPhase}/{@code buttonFrame} reapplied after recreate.</li>
 * </ul>
 *
 * <p>The fourth batch-inner1 child
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
        for (DynamicObjectRewindCodec codec : ObjectRewindDynamicCodecs.sharedCodecs()) {
            names.add(codec.className());
        }
        return names;
    }

    @Test
    void registersCodecsForBatchInner1S1Children() {
        Set<String> names = codecClassNames();

        List<String> required = List.of(
                "com.openggf.game.sonic1.objects.bosses.Sonic1FalseFloorInstance"
                        + "$FalseFloorBlock",
                "com.openggf.game.sonic1.objects.badniks.Sonic1OrbinautBadnikInstance"
                        + "$OrbSpikeObjectInstance",
                "com.openggf.game.sonic1.objects.bosses.Sonic1ScrapEggmanInstance"
                        + "$ScrapEggmanButton");

        for (String name : required) {
            assertTrue(names.contains(name),
                    "missing rewind recreate codec for " + name);
        }
    }
}
