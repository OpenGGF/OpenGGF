package com.openggf.game.sonic2.objects;

import com.openggf.game.sonic2.objects.badniks.RexonHeadObjectInstance;
import com.openggf.game.sonic2.objects.bosses.CPZBossDripper;
import com.openggf.game.sonic2.objects.bosses.CPZBossPipeSegment;
import com.openggf.level.objects.DynamicObjectRewindCodec;
import com.openggf.level.objects.ObjectRewindDynamicCodecs;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link Sonic2ObjectRegistry} (unioned with the shared codecs)
 * now exposes a dynamic rewind recreate codec for every batch-5 S2 object that
 * was previously dropped on a held-rewind restore.
 *
 * <p>Batch-5 adds parent/sibling-relink codecs for the CPZ-boss Dripper and
 * PipeSegment, the HTZ Rexon head, and the Egg-prison button, plus an exact-spawn
 * codec for the act-results screen. The destroyed Egg-prison body, ARZ leaf
 * particle, and act-results screen now use generic recreate. Remaining explicit
 * codecs are gameplay-relevant (boss progression and solid bodies), so none are
 * accept-drop.
 *
 * <p>Pure registry-content test: it constructs a registry and reads
 * {@code dynamicRewindCodecs()} without a ROM, OpenGL, or an active gameplay
 * session. Full session round-trip is handled by the rewind coverage guard.
 */
class TestRewindFixS2Batch5Codecs {

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
    void registersCodecsForBatch5S2Objects() {
        Set<String> names = codecClassNames();

        List<String> required = List.of(
                CPZBossDripper.class.getName(),
                CPZBossPipeSegment.class.getName(),
                RexonHeadObjectInstance.class.getName(),
                EggPrisonButtonObjectInstance.class.getName());

        for (String name : required) {
            assertTrue(names.contains(name),
                    "missing rewind recreate codec for " + name);
        }

        assertFalse(names.contains(LeafParticleObjectInstance.class.getName()),
                "LeafParticleObjectInstance must restore through RewindRecreatable generic recreate, "
                        + "not a batch-5 codec");
        assertFalse(names.contains(DestroyedEggPrisonObjectInstance.class.getName()),
                "DestroyedEggPrisonObjectInstance must restore through RewindRecreatable generic recreate, "
                        + "not a batch-5 codec");
        assertFalse(names.contains(ResultsScreenObjectInstance.class.getName()),
                "ResultsScreenObjectInstance must restore through RewindRecreatable generic recreate, "
                        + "not a batch-5 codec");
    }
}
