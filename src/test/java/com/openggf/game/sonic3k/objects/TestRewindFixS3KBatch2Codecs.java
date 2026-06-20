package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.objects.badniks.BuggernautBabyInstance;
import com.openggf.game.sonic3k.objects.badniks.CaterkillerJrBodyInstance;
import com.openggf.game.sonic3k.objects.bosses.IczEndBossEggCapsuleInstance;
import com.openggf.level.objects.DynamicObjectRewindCodec;
import com.openggf.level.objects.ObjectRewindDynamicCodecs;
import com.openggf.level.objects.RewindRecreatable;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link Sonic3kObjectRegistry} (unioned with the shared codecs)
 * now exposes a dynamic rewind recreate codec for every batch-2 S3K object that
 * was previously dropped on a held-rewind restore.
 *
 * <p>The accept-drop {@code MgzEndBossDefeatDebrisChild} is intentionally NOT
 * listed: it stays uncovered (transient cosmetic, re-emitted in-frame) and is
 * documented in {@code docs/S3K_KNOWN_DISCREPANCIES.md}.
 *
 * <p>Pure registry-content test: it constructs a registry and reads
 * {@code dynamicRewindCodecs()} without a ROM, OpenGL, or an active gameplay
 * session. Full session round-trip is handled by the rewind coverage guard.
 */
class TestRewindFixS3KBatch2Codecs {

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
    void registersCodecsForReleaseSliceBatch2Objects() {
        Set<String> names = codecClassNames();

        List<String> required = List.of(
                AizRockFragmentChild.class.getName(),
                CnzMinibossDebrisChild.class.getName(),
                S3kBossExplosionChild.class.getName(),
                S3kSignpostSparkleChild.class.getName(),
                MhzPollenParticleInstance.class.getName(),
                IczEndBossEggCapsuleInstance.class.getName(),
                CaterkillerJrBodyInstance.class.getName(),
                MhzMinibossEscapeShardInstance.class.getName(),
                Sonic3kStarPostBonusStarChild.class.getName(),
                Sonic3kSSEntryFlashObjectInstance.class.getName(),
                BuggernautBabyInstance.class.getName());

        for (String name : required) {
            assertTrue(hasDynamicRecreatePath(name, names),
                    "missing rewind recreate path for " + name);
        }
    }
}
