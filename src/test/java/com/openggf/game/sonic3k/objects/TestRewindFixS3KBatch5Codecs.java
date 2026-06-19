package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.objects.bosses.MhzEndBossArenaHelperInstance;
import com.openggf.game.sonic3k.objects.bosses.MhzEndBossDefeatFragmentChild;
import com.openggf.game.sonic3k.objects.bosses.MhzEndBossEggCapsuleInstance;
import com.openggf.game.sonic3k.objects.bosses.MhzEndBossHitProxyChild;
import com.openggf.game.sonic3k.objects.bosses.MhzEndBossInstance;
import com.openggf.game.sonic3k.objects.bosses.MhzEndBossPaletteFadeController;
import com.openggf.game.sonic3k.objects.bosses.MhzEndBossRobotnikHeadChild;
import com.openggf.game.sonic3k.objects.bosses.MhzEndBossRobotnikShipFlameInstance;
import com.openggf.game.sonic3k.objects.bosses.MhzEndBossSpikeChild;
import com.openggf.game.sonic3k.objects.bosses.MhzEndBossVisualChild;
import com.openggf.game.sonic3k.objects.bosses.MhzEndBossWeatherMachineChild;
import com.openggf.game.sonic3k.objects.bosses.MhzEndBossWeatherVisualChild;
import com.openggf.level.objects.DynamicObjectRewindCodec;
import com.openggf.level.objects.ObjectRewindDynamicCodecs;
import com.openggf.level.objects.RewindRecreatable;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that every batch-5 S3K object that was previously dropped on a
 * held-rewind restore still exposes a dynamic rewind recreate path: either a
 * registered codec or the Phase-2 {@link RewindRecreatable} generic path.
 *
 * <p>Batch 5 has no accept-drop objects: each listed class is genuinely
 * recreated on restore (gameplay-critical hazards/structures or persistent
 * cosmetic children that fly for many frames), so there are no documented
 * transient-cosmetic drops to exclude here.
 *
 * <p>Pure registry-content test: it constructs a registry and reads
 * {@code dynamicRewindCodecs()} without a ROM, OpenGL, or an active gameplay
 * session. Full session round-trip is handled by the rewind coverage guard.
 */
class TestRewindFixS3KBatch5Codecs {

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

    private static boolean hasDynamicRecreatePath(String className, Set<String> codecNames) {
        if (codecNames.contains(className)) {
            return true;
        }
        try {
            return RewindRecreatable.class.isAssignableFrom(Class.forName(className));
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void registersCodecsForReleaseSliceBatch5Objects() {
        Set<String> names = codecClassNames();

        List<String> required = List.of(
                // CNZ traversal / launch objects.
                CnzBumperObjectInstance.class.getName(),
                CnzCannonInstance.class.getName(),
                CnzCylinderInstance.class.getName(),
                CnzLightsFlashChildInstance.class.getName(),
                // MHZ end-boss family (boss + self-contained objects).
                MhzEndBossInstance.class.getName(),
                MhzEndBossEggCapsuleInstance.class.getName(),
                MhzEndBossPaletteFadeController.class.getName(),
                MhzEndBossArenaHelperInstance.class.getName(),
                // MHZ end-boss parent-relinked children.
                MhzEndBossRobotnikHeadChild.class.getName(),
                MhzEndBossRobotnikShipFlameInstance.class.getName(),
                MhzEndBossSpikeChild.class.getName(),
                MhzEndBossVisualChild.class.getName(),
                MhzEndBossWeatherMachineChild.class.getName(),
                MhzEndBossWeatherVisualChild.class.getName(),
                MhzEndBossHitProxyChild.class.getName(),
                MhzEndBossDefeatFragmentChild.class.getName());

        for (String name : required) {
            assertTrue(hasDynamicRecreatePath(name, names),
                    "missing rewind recreate path for " + name);
        }
    }
}
