package com.openggf.game.sonic2.objects;

import com.openggf.game.sonic2.objects.badniks.BalkiryJetObjectInstance;
import com.openggf.game.sonic2.objects.bosses.ARZBossArrow;
import com.openggf.game.sonic2.objects.bosses.ARZBossPillar;
import com.openggf.game.sonic2.objects.bosses.EHZBossSpike;
import com.openggf.game.sonic2.objects.bosses.EHZBossWheel;
import com.openggf.level.objects.DynamicObjectRewindCodec;
import com.openggf.level.objects.ObjectRewindDynamicCodecs;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link Sonic2ObjectRegistry} (unioned with the shared codecs)
 * now exposes a dynamic rewind recreate codec for every batch-2 S2 object that
 * was previously dropped on a held-rewind restore.
 *
 * <p>Pure registry-content test: it constructs a registry and reads
 * {@code dynamicRewindCodecs()} without a ROM, OpenGL, or an active gameplay
 * session. Full session round-trip is handled by the rewind coverage guard.
 */
class TestRewindFixS2Batch2Codecs {

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
    void registersCodecsForBatch2S2Objects() {
        Set<String> names = codecClassNames();

        List<String> required = List.of(
                ARZBossArrow.class.getName(),
                ARZBossPillar.class.getName(),
                GrounderRockProjectile.class.getName(),
                GrounderWallInstance.class.getName(),
                HtzFireProjectileObjectInstance.class.getName(),
                HtzGroundFireObjectInstance.class.getName(),
                EHZBossSpike.class.getName(),
                EHZBossWheel.class.getName(),
                BalkiryJetObjectInstance.class.getName(),
                ArrowProjectileInstance.class.getName());

        for (String name : required) {
            assertTrue(names.contains(name),
                    "missing rewind recreate codec for " + name);
        }
    }
}
