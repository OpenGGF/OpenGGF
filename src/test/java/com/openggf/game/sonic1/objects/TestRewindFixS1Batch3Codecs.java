package com.openggf.game.sonic1.objects;

import com.openggf.game.sonic1.objects.bosses.FZCylinder;
import com.openggf.game.sonic1.objects.bosses.FZPlasmaBall;
import com.openggf.game.sonic1.objects.bosses.FZPlasmaLauncher;
import com.openggf.game.sonic1.objects.bosses.Sonic1BossBlockInstance;
import com.openggf.level.objects.DynamicObjectRewindCodec;
import com.openggf.level.objects.ObjectRewindDynamicCodecs;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link Sonic1ObjectRegistry} (unioned with the shared codecs)
 * now exposes a dynamic rewind recreate codec for every batch-3 S1 object that
 * was previously dropped on a held-rewind restore.
 *
 * <p>Pure registry-content test: it constructs a registry and reads
 * {@code dynamicRewindCodecs()} without a ROM, OpenGL, or an active gameplay
 * session. Full session round-trip is handled by the rewind coverage guard.
 *
 * <p>{@code Sonic1SplashObjectInstance} is intentionally excluded: it is an
 * accept-drop transient cosmetic (LZ water splash re-emitted on water
 * entry/exit), documented in {@code docs/KNOWN_DISCREPANCIES.md}.
 */
class TestRewindFixS1Batch3Codecs {

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
    void registersCodecsForBatch3S1Objects() {
        Set<String> names = codecClassNames();

        List<String> required = List.of(
                FZCylinder.class.getName(),
                FZPlasmaLauncher.class.getName(),
                FZPlasmaBall.class.getName(),
                Sonic1BossBlockInstance.class.getName(),
                Sonic1CollapsingFloorObjectInstance.class.getName(),
                Sonic1EggPrisonObjectInstance.class.getName(),
                Sonic1ExplosionItemObjectInstance.class.getName(),
                Sonic1GrassFireObjectInstance.class.getName(),
                Sonic1LamppostTwirlInstance.class.getName(),
                Sonic1MonitorPowerUpObjectInstance.class.getName(),
                Sonic1RingFlashObjectInstance.class.getName(),
                Sonic1RingInstance.class.getName(),
                Sonic1SeesawBallObjectInstance.class.getName(),
                Sonic1SpikedBallChainObjectInstance.class.getName());

        for (String name : required) {
            assertTrue(names.contains(name),
                    "missing rewind recreate codec for " + name);
        }
    }
}
