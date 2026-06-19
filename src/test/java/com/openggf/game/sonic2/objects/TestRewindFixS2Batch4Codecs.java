package com.openggf.game.sonic2.objects;

import com.openggf.game.sonic2.objects.bosses.CPZBossContainer;
import com.openggf.game.sonic2.objects.bosses.CPZBossContainerFloor;
import com.openggf.game.sonic2.objects.bosses.CPZBossFlame;
import com.openggf.game.sonic2.objects.bosses.CPZBossGunk;
import com.openggf.game.sonic2.objects.bosses.CPZBossPipe;
import com.openggf.game.sonic2.objects.bosses.CPZBossPipePump;
import com.openggf.game.sonic2.objects.bosses.CPZBossPump;
import com.openggf.game.sonic2.objects.bosses.CPZBossRobotnik;
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
 * now exposes a dynamic rewind recreate codec for every batch-4 S2 object that
 * was previously dropped on a held-rewind restore.
 *
 * <p>The CPZ boss-component chain (container/floor/falling-part/flame/gunk/pipe/
 * pump/robotnik) and the OOZ burner flame retain recreate codecs (parent-relink
 * or sibling-relink). The falling part and ARZ rising bubble now use generic recreate.
 * {@link
 * com.openggf.game.sonic2.objects.bosses.CPZBossSmokePuff} is intentionally
 * accept-drop (cosmetic + dead code) and the scalar-only lava/MCZ falling hazards
 * now use generic recreate, so they are therefore NOT required here.
 *
 * <p>Pure registry-content test: it constructs a registry and reads
 * {@code dynamicRewindCodecs()} without a ROM, OpenGL, or an active gameplay
 * session. Full session round-trip is handled by the rewind coverage guard.
 */
class TestRewindFixS2Batch4Codecs {

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
    void registersCodecsForBatch4S2Objects() {
        Set<String> names = codecClassNames();

        List<String> required = List.of(
                CPZBossContainer.class.getName(),
                CPZBossContainerFloor.class.getName(),
                CPZBossFlame.class.getName(),
                CPZBossGunk.class.getName(),
                CPZBossPipe.class.getName(),
                CPZBossPipePump.class.getName(),
                CPZBossPump.class.getName(),
                CPZBossRobotnik.class.getName(),
                OOZBurnerFlameObjectInstance.class.getName());

        for (String name : required) {
            assertTrue(names.contains(name),
                    "missing rewind recreate codec for " + name);
        }

        assertFalse(names.contains(BubbleObjectInstance.class.getName()),
                "BubbleObjectInstance must restore through RewindRecreatable generic recreate, "
                        + "not a batch-4 codec");
        assertFalse(names.contains("com.openggf.game.sonic2.objects.bosses.CPZBossFallingPart"),
                "CPZBossFallingPart must restore through RewindRecreatable generic recreate, "
                        + "not a batch-4 codec");
    }
}
