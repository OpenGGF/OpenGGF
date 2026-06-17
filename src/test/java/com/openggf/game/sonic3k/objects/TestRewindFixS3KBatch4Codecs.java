package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.objects.bosses.HczEndBossBlade;
import com.openggf.game.sonic3k.objects.bosses.HczEndBossBladeSplash;
import com.openggf.game.sonic3k.objects.bosses.HczEndBossBladeWaterChute;
import com.openggf.game.sonic3k.objects.bosses.HczEndBossEggCapsuleInstance;
import com.openggf.game.sonic3k.objects.bosses.HczEndBossGeyserCutscene;
import com.openggf.game.sonic3k.objects.bosses.HczEndBossInstance;
import com.openggf.game.sonic3k.objects.bosses.HczEndBossRobotnikShip;
import com.openggf.game.sonic3k.objects.bosses.HczEndBossTurbine;
import com.openggf.game.sonic3k.objects.bosses.HczEndBossWaterColumn;
import com.openggf.level.objects.DynamicObjectRewindCodec;
import com.openggf.level.objects.ObjectRewindDynamicCodecs;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link Sonic3kObjectRegistry} (unioned with the shared codecs)
 * now exposes a dynamic rewind recreate codec for every batch-4 S3K object that
 * was previously dropped on a held-rewind restore.
 *
 * <p>The accept-drop {@code AizIntroEmeraldGlowChild} is intentionally NOT
 * listed: it is never a dynamic-object snapshot entry (created via raw
 * {@code new}, held only as a structural sub-object ref, never rendered), so it
 * has no codec and stays documented in {@code docs/S3K_KNOWN_DISCREPANCIES.md}.
 *
 * <p>Pure registry-content test: it constructs a registry and reads
 * {@code dynamicRewindCodecs()} without a ROM, OpenGL, or an active gameplay
 * session. Full session round-trip is handled by the rewind coverage guard.
 */
class TestRewindFixS3KBatch4Codecs {

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
    void registersCodecsForReleaseSliceBatch4Objects() {
        Set<String> names = codecClassNames();

        List<String> required = List.of(
                // HCZ end-boss scene (gameplay-critical bosses/cutscenes/children).
                HczEndBossInstance.class.getName(),
                HczEndBossEggCapsuleInstance.class.getName(),
                HczEndBossGeyserCutscene.class.getName(),
                HczEndBossRobotnikShip.class.getName(),
                HczEndBossTurbine.class.getName(),
                HczEndBossBlade.class.getName(),
                HczEndBossBladeSplash.class.getName(),
                HczEndBossBladeWaterChute.class.getName(),
                HczEndBossWaterColumn.class.getName(),
                // AIZ boss / intro objects.
                AizEndBossInstance.class.getName(),
                Aiz2EndEggCapsuleInstance.class.getName(),
                AizIntroPlaneChild.class.getName(),
                AizIntroWaveChild.class.getName());

        for (String name : required) {
            assertTrue(names.contains(name),
                    "missing rewind recreate codec for " + name);
        }
    }
}
