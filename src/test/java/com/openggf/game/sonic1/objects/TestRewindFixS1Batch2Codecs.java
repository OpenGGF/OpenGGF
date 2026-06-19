package com.openggf.game.sonic1.objects;

import com.openggf.game.sonic1.objects.badniks.Sonic1BombFuseInstance;
import com.openggf.game.sonic1.objects.badniks.Sonic1BombShrapnelInstance;
import com.openggf.game.sonic1.objects.badniks.Sonic1BuzzBomberMissileDissolveInstance;
import com.openggf.game.sonic1.objects.badniks.Sonic1BuzzBomberMissileInstance;
import com.openggf.game.sonic1.objects.badniks.Sonic1CannonballInstance;
import com.openggf.game.sonic1.objects.badniks.Sonic1CaterkillerBodyInstance;
import com.openggf.game.sonic1.objects.badniks.Sonic1CrabmeatProjectileInstance;
import com.openggf.game.sonic1.objects.badniks.Sonic1NewtronMissileInstance;
import com.openggf.game.sonic1.objects.bosses.GHZBossWreckingBall;
import com.openggf.game.sonic1.objects.bosses.Sonic1SLZBossSpikeball;
import com.openggf.level.objects.DynamicObjectRewindCodec;
import com.openggf.level.objects.ObjectRewindDynamicCodecs;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link Sonic1ObjectRegistry} (unioned with the shared codecs)
 * now exposes a dynamic rewind recreate codec for every batch-2 S1 object that
 * was previously dropped on a held-rewind restore.
 *
 * <p>Pure registry-content test: it constructs a registry and reads
 * {@code dynamicRewindCodecs()} without a ROM, OpenGL, or an active gameplay
 * session. Full session round-trip is handled by the rewind coverage guard.
 *
 * <p><b>Intentionally absent:</b> {@code SYZBossSpike} is construction-spawned
 * (inside {@code Sonic1SYZBossInstance.initializeBossState()} → {@code spawnSpikeChild()}).
 * Registering a codec would double it on rewind restore (1 → 2). Re-established by
 * boss reconstruction. See {@code TestBossChildNoDoubleSpawnParity}.
 *
 * <p>{@code Sonic1MotobugSmokeInstance} is intentionally excluded: it is an
 * accept-drop transient cosmetic (re-emitted in-frame by its parent), documented
 * in {@code docs/KNOWN_DISCREPANCIES.md}.
 */
class TestRewindFixS1Batch2Codecs {

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
    void registersCodecsForBatch2S1Objects() {
        Set<String> names = codecClassNames();

        List<String> required = List.of(
                Sonic1BombFuseInstance.class.getName(),
                Sonic1BombShrapnelInstance.class.getName(),
                Sonic1BuzzBomberMissileInstance.class.getName(),
                Sonic1BuzzBomberMissileDissolveInstance.class.getName(),
                Sonic1CannonballInstance.class.getName(),
                Sonic1CaterkillerBodyInstance.class.getName(),
                Sonic1CrabmeatProjectileInstance.class.getName(),
                Sonic1NewtronMissileInstance.class.getName(),
                GHZBossWreckingBall.class.getName(),
                // SYZBossSpike intentionally absent: construction-spawned child.
                // Adding a codec would double it on restore. See TestBossChildNoDoubleSpawnParity.
                Sonic1SLZBossSpikeball.class.getName());

        for (String name : required) {
            assertTrue(names.contains(name),
                    "missing rewind recreate codec for " + name);
        }
    }
}
