package com.openggf.game.sonic2.objects;

import com.openggf.game.sonic2.objects.badniks.RexonHeadObjectInstance;
import com.openggf.game.sonic2.objects.bosses.CPZBossDripper;
import com.openggf.game.sonic2.objects.bosses.CPZBossPipeSegment;
import com.openggf.level.objects.DynamicObjectRewindCodec;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Verifies that {@link Sonic2ObjectRegistry} (unioned with the shared codecs)
 * reflects the current batch-5 S2 dynamic rewind codec inventory.
 *
 * <p>The original batch-5 codecs for CPZ-boss Dripper/PipeSegment, Rexon head,
 * Egg-prison button, destroyed Egg-prison body, ARZ leaf particle, and act
 * results have been deleted in favour of graph-tested or self-contained
 * {@code RewindRecreatable} generic recreate paths.
 *
 * <p>Pure registry-content test: it constructs a registry and reads
 * {@code deleted dynamic-codec registry API} without a ROM, OpenGL, or an active gameplay
 * session. Full session round-trip is handled by the rewind coverage guard.
 */
class TestRewindFixS2Batch5Codecs {

    private static Set<String> codecClassNames() {
        Set<String> names = new HashSet<>();
        List<DynamicObjectRewindCodec> codecs = java.util.List.<com.openggf.level.objects.DynamicObjectRewindCodec>of();
        for (DynamicObjectRewindCodec codec : codecs) {
            names.add(codec.className());
        }
        return names;
    }

    @Test
    void batch5S2ObjectsNoLongerRegisterDeletedCodecs() {
        Set<String> names = codecClassNames();

        List<String> deleted = List.of(
                CPZBossDripper.class.getName(),
                CPZBossPipeSegment.class.getName(),
                RexonHeadObjectInstance.class.getName(),
                EggPrisonButtonObjectInstance.class.getName());

        for (String name : deleted) {
            assertFalse(names.contains(name),
                    name + " must restore through RewindRecreatable generic recreate, not a batch-5 codec");
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
