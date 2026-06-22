package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.DynamicObjectRewindCodec;
import com.openggf.level.objects.EggPrisonAnimalInstance;
import com.openggf.level.objects.RewindRecreatable;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link Sonic3kObjectRegistry} (unioned with the shared codecs)
 * now exposes a dynamic rewind recreate codec for every batch-7 S3K object that
 * was previously dropped on a held-rewind restore: the Pachinko bonus-stage
 * energy trap and sloped flipper, the boss-defeat-to-signpost orchestrator, the
 * song-fade transition object, the AIZ/LRZ rock debris fragment, and the
 * egg-prison released animal.
 *
 * <p>Batch 7 has no accept-drop objects: each listed class is genuinely
 * recreated on restore (gameplay-critical traps/terrain/orchestrators, or
 * persistent multi-frame debris/animals whose captured siblings are restored
 * rather than dropped), so there are no documented transient-cosmetic drops to
 * exclude here.
 *
 * <p>Pure registry-content test: it constructs a registry and reads
 * {@code dynamicRewindCodecs()} without a ROM, OpenGL, or an active gameplay
 * session. Full session round-trip is handled by the rewind coverage guard.
 */
class TestRewindFixS3KBatch7Codecs {

    private static Set<String> codecClassNames() {
        Set<String> names = new HashSet<>();
        List<DynamicObjectRewindCodec> codecs = new Sonic3kObjectRegistry().dynamicRewindCodecs();
        for (DynamicObjectRewindCodec codec : codecs) {
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
    void registersCodecsForReleaseSliceBatch7Objects() {
        Set<String> names = codecClassNames();

        List<String> required = List.of(
                // Pachinko bonus-stage capture trap + sloped flipper.
                PachinkoEnergyTrapObjectInstance.class.getName(),
                PachinkoFlipperObjectInstance.class.getName(),
                // AIZ/LRZ breakable-rock gravity debris.
                RockDebrisChild.class.getName(),
                // Boss-defeat -> signpost -> results -> act-transition flow.
                S3kBossDefeatSignpostFlow.class.getName(),
                // Pending music-transition object.
                SongFadeTransitionInstance.class.getName(),
                // Egg-prison released animal (game-agnostic, spawned by S3K capsules).
                EggPrisonAnimalInstance.class.getName());

        for (String name : required) {
            assertTrue(hasDynamicRecreatePath(name, names),
                    "missing rewind recreate path for " + name);
        }
    }
}
