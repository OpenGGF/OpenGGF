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
 * now exposes a dynamic rewind recreate codec for every batch-6 S3K object that
 * was previously dropped on a held-rewind restore: the LBZ1/CNZ2/AIZ1 Knuckles
 * cutscene family, the MGZ end-of-act capsule/animals/results/collapse-floor/boss
 * objects, the MHZ1 cutscene door, the MHZ ship-sequence controller, the S3K
 * insta-shield, and the lightning-shield spark particle.
 *
 * <p>Batch 6 has no accept-drop objects: each listed class is genuinely recreated
 * on restore (gameplay-critical cutscene/terrain/boss objects, or a one-shot
 * cosmetic spark that lives many frames), so there are no documented
 * transient-cosmetic drops to exclude here.
 *
 * <p>Pure registry-content test: it constructs a registry and reads
 * {@code dynamicRewindCodecs()} without a ROM, OpenGL, or an active gameplay
 * session. Full session round-trip is handled by the rewind coverage guard.
 */
class TestRewindFixS3KBatch6Codecs {

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
    void registersCodecsForReleaseSliceBatch6Objects() {
        Set<String> names = codecClassNames();

        List<String> required = List.of(
                // LBZ1 Knuckles cutscene family.
                CutsceneKnucklesLbz1CollapseChild.class.getName(),
                CutsceneKnucklesLbz1RangeHelper.class.getName(),
                CutsceneKnucklesLbz1ThrownBomb.class.getName(),
                // AIZ1 intro Knuckles rock cutscene (parent + child).
                CutsceneKnucklesAiz1Instance.class.getName(),
                CutsceneKnucklesRockChild.class.getName(),
                // CNZ2 Knuckles cutscene blocking wall.
                CutsceneKnuxCnz2WallInstance.class.getName(),
                // S3K shields / spark.
                InstaShieldObjectInstance.class.getName(),
                LightningSparkObjectInstance.class.getName(),
                // MGZ end-of-act objects.
                Mgz2EndEggCapsuleInstance.class.getName(),
                Mgz2CapsuleAnimalInstance.class.getName(),
                S3kResultsScreenObjectInstance.class.getName(),
                Mgz2ResultsScreenObjectInstance.class.getName(),
                Mgz2LevelCollapseSolidInstance.class.getName(),
                MgzDrillingRobotnikInstance.class.getName(),
                MgzEndBossInstance.class.getName(),
                // MHZ cutscene door + ship-sequence controller.
                Mhz1CutsceneDoorInstance.class.getName(),
                MhzShipSequenceControllerInstance.class.getName());

        for (String name : required) {
            assertTrue(names.contains(name),
                    "missing rewind recreate codec for " + name);
        }
    }
}
