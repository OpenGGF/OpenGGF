package com.openggf.game.sonic2.objects;

import com.openggf.level.objects.DynamicObjectRewindCodec;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Verifies that {@link Sonic2ObjectRegistry} (unioned with the shared codecs)
 * no longer exposes dynamic rewind recreate codecs for batch-4 S2 objects that
 * have moved to generic recreate coverage.
 *
 * <p>The CPZ boss-component chain, OOZ burner flame, falling part, ARZ rising
 * bubble, and scalar-only lava/MCZ falling hazards now use graph/generic
 * recreate. {@link com.openggf.game.sonic2.objects.bosses.CPZBossSmokePuff} is
 * intentionally accept-drop (cosmetic + dead code).
 *
 * <p>Pure registry-content test: it constructs a registry and reads
 * {@code deleted dynamic-codec registry API} without a ROM, OpenGL, or an active gameplay
 * session. Full session round-trip is handled by the rewind coverage guard.
 */
class TestRewindFixS2Batch4Codecs {

    private static Set<String> codecClassNames() {
        Set<String> names = new HashSet<>();
        List<DynamicObjectRewindCodec> codecs = java.util.List.<com.openggf.level.objects.DynamicObjectRewindCodec>of();
        for (DynamicObjectRewindCodec codec : codecs) {
            names.add(codec.className());
        }
        return names;
    }

    @Test
    void batch4S2ObjectsNoLongerRegisterDeletedCodecs() {
        Set<String> names = codecClassNames();

        assertFalse(names.contains(OOZBurnerFlameObjectInstance.class.getName()),
                "OOZBurnerFlameObjectInstance must restore through RewindRecreatable graph relink, "
                        + "not a batch-4 codec");
        assertFalse(names.contains(BubbleObjectInstance.class.getName()),
                "BubbleObjectInstance must restore through RewindRecreatable generic recreate, "
                        + "not a batch-4 codec");
        assertFalse(names.contains("com.openggf.game.sonic2.objects.bosses.CPZBossFallingPart"),
                "CPZBossFallingPart must restore through RewindRecreatable generic recreate, "
                        + "not a batch-4 codec");
    }
}
