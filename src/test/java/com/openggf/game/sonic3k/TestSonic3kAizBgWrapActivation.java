package com.openggf.game.sonic3k;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kAIZEvents;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestSonic3kAizBgWrapActivation {

    @AfterEach
    void tearDown() { com.openggf.game.session.SessionManager.clear(); }

    @Test
    void aizForestLoopWrapsBgAtThe200ForestWindowExcludingFiller() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_AIZ, 1)
                .build();
        Sonic3kZoneFeatureProvider provider =
                (Sonic3kZoneFeatureProvider) GameServices.module().getZoneFeatureProvider();
        Sonic3kAIZEvents events =
                ((Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider()).getAizEvents();

        // Outside the loop, AIZ keeps the full-width BG (no horizontal wrap).
        events.setBattleshipAutoScrollActiveRaw(false);
        assertFalse(provider.bgWrapsHorizontally(),
                "AIZ must not wrap BG horizontally outside the forest loop");

        // Enter the forest loop with the side-effect-free setters.
        events.setBattleshipAutoScrollActiveRaw(true);
        events.setBattleshipWrapX(0x46C0);
        assertTrue(provider.bgWrapsHorizontally(),
                "AIZ must wrap BG horizontally during the post-bombing forest loop");

        // Build the BG window just below the wrap boundary, then again after a $200
        // renormalization; the visible window must be forest-only and unchanged.
        GameServices.camera().setX((short) (0x46C0 - 0x10));
        GameServices.level().recomputeParallaxAfterRewindRestore();
        GameServices.level().ensureBackgroundTilemapData();
        assertEquals(0x200, GameServices.level().getTilemapManager().getCurrentBgPeriodWidth(),
                "BG period must be the $200 forest window during the loop");
        int[] before = GameServices.level().bgVisibleSourceColumnsForTest();
        assertTrue(before.length > 0, "BG build must have sampled some source columns");

        GameServices.camera().setX((short) (0x46C0 - 0x10 - 0x200));
        GameServices.level().recomputeParallaxAfterRewindRestore();
        GameServices.level().ensureBackgroundTilemapData();
        int[] after = GameServices.level().bgVisibleSourceColumnsForTest();

        // Forest-only: no sampled source column lands in the empty filler half
        // ($200-$400, i.e. block columns 4-7).
        for (int col : before) {
            assertTrue(col * 128 < 0x200,
                    "BG window must contain only forest source columns (0-3); got col " + col);
        }
        // ...and the built window is identical across the $200 wrap (no seam, no filler).
        assertArrayEquals(before, after,
                "Forest BG window must be identical across the $200 wrap");
    }
}
