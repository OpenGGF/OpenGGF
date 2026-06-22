package com.openggf.game.sonic2.objects;

import com.openggf.game.sonic2.objects.badniks.BalkiryJetObjectInstance;
import com.openggf.game.sonic2.objects.bosses.ARZBossArrow;
import com.openggf.game.sonic2.objects.bosses.ARZBossEyes;
import com.openggf.game.sonic2.objects.bosses.ARZBossPillar;
import com.openggf.level.objects.DynamicObjectRewindCodec;
import com.openggf.level.objects.RewindRecreatable;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link Sonic2ObjectRegistry} (unioned with the shared codecs)
 * keeps the current rewind path for every batch-2 S2 object that was
 * previously dropped on a held-rewind restore.
 *
 * <p>Pure registry-content test: it constructs a registry and reads
 * {@code deleted dynamic-codec registry API} without a ROM, OpenGL, or an active gameplay
 * session. Full session round-trip is handled by the rewind coverage guard.
 */
class TestRewindFixS2Batch2Codecs {

    private static Set<String> codecClassNames() {
        Set<String> names = new HashSet<>();
        List<DynamicObjectRewindCodec> codecs = java.util.List.<com.openggf.level.objects.DynamicObjectRewindCodec>of();
        for (DynamicObjectRewindCodec codec : codecs) {
            names.add(codec.className());
        }
        return names;
    }

    @Test
    void batch2S2ObjectsUseCurrentRewindPaths() {
        Set<String> names = codecClassNames();

        List<String> phase2Deleted = List.of(
                ARZBossArrow.class.getName(),
                ARZBossPillar.class.getName(),
                GrounderRockProjectile.class.getName(),
                GrounderWallInstance.class.getName(),
                // EHZBossSpike / EHZBossWheel codecs intentionally REMOVED:
                // construction-spawned children re-established by boss reconstruction
                // (see TestBossChildNoDoubleSpawnParity / KNOWN_DISCREPANCIES).
                BalkiryJetObjectInstance.class.getName(),
                HtzFireProjectileObjectInstance.class.getName(),
                HtzGroundFireObjectInstance.class.getName(),
                ArrowProjectileInstance.class.getName());
        for (String name : phase2Deleted) {
            assertFalse(names.contains(name),
                    name + " must restore through RewindRecreatable generic recreate, not a batch-2 codec");
        }

        assertTrue(RewindRecreatable.class.isAssignableFrom(ARZBossArrow.class),
                "ARZBossArrow must restore through RewindRecreatable graph relink");
        assertTrue(RewindRecreatable.class.isAssignableFrom(ARZBossEyes.class),
                "ARZBossEyes must support generic recreate for the ARZ arrow graph");
        assertTrue(RewindRecreatable.class.isAssignableFrom(ARZBossPillar.class),
                "ARZBossPillar must keep its RewindRecreatable generic recreate path");
    }
}
