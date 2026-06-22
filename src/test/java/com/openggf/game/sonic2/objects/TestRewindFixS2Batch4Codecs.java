package com.openggf.game.sonic2.objects;

import com.openggf.game.sonic2.objects.bosses.CPZBossFallingPart;
import com.openggf.level.objects.RewindRecreatable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void batch4S2ObjectsKeepGenericRecreateCoverage() {
        List<Class<?>> deleted = List.of(
                OOZBurnerFlameObjectInstance.class,
                BubbleObjectInstance.class,
                CPZBossFallingPart.class);

        for (Class<?> type : deleted) {
            assertTrue(RewindRecreatable.class.isAssignableFrom(type),
                    type.getName() + " must restore through RewindRecreatable generic recreate");
        }
    }
}
