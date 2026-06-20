package com.openggf.game.sonic2.objects;

import com.openggf.game.sonic2.objects.badniks.GrounderBadnikInstance;
import com.openggf.game.sonic2.objects.badniks.ShellcrackerClawInstance;
import com.openggf.game.sonic2.objects.badniks.SlicerPincerInstance;
import com.openggf.game.sonic2.objects.badniks.SolFireballObjectInstance;
import com.openggf.game.sonic2.objects.badniks.SpikerDrillObjectInstance;
import com.openggf.game.sonic2.objects.badniks.TurtloidJetInstance;
import com.openggf.game.sonic2.objects.badniks.TurtloidRiderInstance;
import com.openggf.game.sonic2.objects.bosses.CNZBossElectricBall;
import com.openggf.game.sonic2.objects.bosses.HTZBossFlamethrower;
import com.openggf.game.sonic2.objects.bosses.HTZBossLavaBall;
import com.openggf.level.objects.DynamicObjectRewindCodec;
import com.openggf.level.objects.ObjectRewindDynamicCodecs;
import com.openggf.level.objects.RewindRecreatable;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link Sonic2ObjectRegistry} (unioned with the shared codecs)
 * now exposes a dynamic rewind recreate path for every batch-3 S2 object that
 * was previously dropped on a held-rewind restore.
 *
 * <p>Pure registry-content test: it constructs a registry and reads
 * {@code dynamicRewindCodecs()} and checks {@link RewindRecreatable} opt-ins
 * without a ROM, OpenGL, or an active gameplay session. Full session round-trip
 * is handled by the rewind coverage guard.
 */
class TestRewindFixS2Batch3Codecs {

    // EHZ boss construction-spawned children and HTZ boss hazards no longer need
    // explicit dynamic codecs; graph/generic recreate coverage owns them.
    private static final List<String> EXPLICIT_CODEC_CLASSES = List.of();

    private static final List<String> GENERIC_RECREATE_CLASSES = List.of(
            GrounderBadnikInstance.class.getName(),
            ShellcrackerClawInstance.class.getName(),
            SlicerPincerInstance.class.getName(),
            TurtloidJetInstance.class.getName(),
            TurtloidRiderInstance.class.getName(),
            SolFireballObjectInstance.class.getName(),
            SpikerDrillObjectInstance.class.getName(),
            WallTurretShotInstance.class.getName(),
            VerticalLaserObjectInstance.class.getName(),
            SpikyBlockSpikeInstance.class.getName(),
            BombPrizeObjectInstance.class.getName(),
            CNZBossElectricBall.class.getName(),
            HTZBossFlamethrower.class.getName(),
            HTZBossLavaBall.class.getName());

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
    void registersRestorePathsForBatch3S2Objects() {
        Set<String> names = codecClassNames();

        for (String name : EXPLICIT_CODEC_CLASSES) {
            assertTrue(names.contains(name),
                    "missing explicit rewind recreate codec for " + name);
        }
        for (String name : GENERIC_RECREATE_CLASSES) {
            assertTrue(implementsRewindRecreatable(name),
                    "missing RewindRecreatable generic recreate path for " + name);
        }
    }

    private static boolean implementsRewindRecreatable(String className) {
        try {
            return RewindRecreatable.class.isAssignableFrom(Class.forName(className));
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }
}
