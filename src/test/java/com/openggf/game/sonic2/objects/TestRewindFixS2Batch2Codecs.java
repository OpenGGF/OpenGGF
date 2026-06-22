package com.openggf.game.sonic2.objects;

import com.openggf.game.sonic2.objects.badniks.BalkiryJetObjectInstance;
import com.openggf.game.sonic2.objects.bosses.ARZBossArrow;
import com.openggf.game.sonic2.objects.bosses.ARZBossEyes;
import com.openggf.game.sonic2.objects.bosses.ARZBossPillar;
import com.openggf.level.objects.RewindRecreatable;
import org.junit.jupiter.api.Test;

import java.util.List;

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

    @Test
    void batch2S2ObjectsUseCurrentRewindPaths() {
        List<Class<?>> phase2Deleted = List.of(
                ARZBossArrow.class,
                ARZBossPillar.class,
                GrounderRockProjectile.class,
                GrounderWallInstance.class,
                // EHZBossSpike / EHZBossWheel codecs intentionally REMOVED:
                // construction-spawned children re-established by boss reconstruction
                // (see TestBossChildNoDoubleSpawnParity / KNOWN_DISCREPANCIES).
                BalkiryJetObjectInstance.class,
                HtzFireProjectileObjectInstance.class,
                HtzGroundFireObjectInstance.class,
                ArrowProjectileInstance.class);
        for (Class<?> type : phase2Deleted) {
            assertTrue(RewindRecreatable.class.isAssignableFrom(type),
                    type.getName() + " must restore through RewindRecreatable generic recreate");
        }

        assertTrue(RewindRecreatable.class.isAssignableFrom(ARZBossArrow.class),
                "ARZBossArrow must restore through RewindRecreatable graph relink");
        assertTrue(RewindRecreatable.class.isAssignableFrom(ARZBossEyes.class),
                "ARZBossEyes must support generic recreate for the ARZ arrow graph");
        assertTrue(RewindRecreatable.class.isAssignableFrom(ARZBossPillar.class),
                "ARZBossPillar must keep its RewindRecreatable generic recreate path");
    }
}
