package com.openggf.game.sonic3k.scroll;

import com.openggf.tests.TestEnvironment;

import com.openggf.data.Rom;
import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.tests.rules.SonicGame;
import com.openggf.level.scroll.ZoneScrollHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SwScrlMgzTest {
    private GameModule previousModule;

    @BeforeEach
    void setUpRuntime() {
        previousModule = GameModuleRegistry.getCurrent();
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
    }

    @AfterEach
    void tearDownRuntime() {
        SessionManager.clear();
        if (previousModule != null) {
            GameModuleRegistry.setCurrent(previousModule);
        } else {
            GameModuleRegistry.reset();
        }
    }

    @Test
    public void act1MatchesExactPackedScrollAndOffsetBounds() {
        SwScrlMgz handler = new SwScrlMgz();
        int[] hScroll = new int[224];

        handler.update(hScroll, 0x1200, 0x0400, 1, 0);

        assertEquals(0, handler.getVscrollFactorBG());
        assertEquals(3456, handler.getMinScrollOffset());
        assertEquals(4356, handler.getMaxScrollOffset());
        assertEquals(-301924604, hScroll[0]);
        assertEquals(-301924604, hScroll[15]);
        assertEquals(-301924676, hScroll[16]);
        assertEquals(-301924676, hScroll[19]);
        assertEquals(-301924748, hScroll[20]);
        assertEquals(-301924748, hScroll[23]);
        assertEquals(-301924820, hScroll[24]);
        assertEquals(-301924820, hScroll[31]);
        assertEquals(-301924892, hScroll[32]);
        assertEquals(-301924892, hScroll[39]);
        assertEquals(-301924928, hScroll[40]);
        assertEquals(-301925504, hScroll[223]);
    }

    @Test
    public void act2MatchesExactScatterFilledPackedScrollAndOffsetBounds() {
        SwScrlMgz handler = new SwScrlMgz();
        int[] hScroll = new int[224];

        handler.update(hScroll, 0x1200, 0x0200, 1, 1);

        assertEquals(96, handler.getVscrollFactorBG());
        assertEquals(2304, handler.getMinScrollOffset());
        assertEquals(4522, handler.getMaxScrollOffset());
        assertEquals(-301924772, hScroll[0]);
        assertEquals(-301924772, hScroll[7]);
        assertEquals(-301924535, hScroll[8]);
        assertEquals(-301924944, hScroll[16]);
        assertEquals(-301924675, hScroll[32]);
        assertEquals(-301924761, hScroll[40]);
        assertEquals(-301924589, hScroll[48]);
        assertEquals(-301924718, hScroll[64]);
        assertEquals(-301924449, hScroll[72]);
        assertEquals(-301924492, hScroll[80]);
        assertEquals(-301924438, hScroll[88]);
        assertEquals(-301924632, hScroll[93]);
        assertEquals(-301924944, hScroll[136]);
        assertEquals(-301925224, hScroll[148]);
        assertEquals(-301925504, hScroll[154]);
        assertEquals(-301925784, hScroll[160]);
        assertEquals(-301926096, hScroll[168]);
        assertEquals(-301926656, hScroll[200]);
        assertEquals(-301926656, hScroll[223]);
    }


    @Test void projectionIsIndependentOfDrawCountFrameAndRebuild() {
        var publication = new com.openggf.game.sonic3k.events.Sonic3kMGZEvents.ScreenPublication(
                8, 0x200, Integer.MIN_VALUE, -3, 0x12345678);
        var handler = new SwScrlMgz(() -> publication);
        int[] first = new int[224];
        handler.update(first, 0x3500, 0x850, 1, 1);
        assertEquals((short) 0x84D, handler.getVscrollFactorFG());
        assertEquals((short) 0x15D, handler.getVscrollFactorBG());
        assertEquals(0x300, handler.getBgCameraX());
        int[] second = new int[224];
        handler.rebuildOutput(second, 0x3500, 0x850, 999, 1);
        assertArrayEquals(first, second);
        var fresh = new SwScrlMgz(() -> publication);
        fresh.init(1, 0x3500, 0x850);
        fresh.update(second, 0x3500, 0x850, 0, 1);
        assertArrayEquals(first, second);
    }

    @Test void bossCursorChangesParallaxWithoutRelocatingBackgroundCamera() {
        int[][] outputs = {new int[224], new int[224]};
        for (int i = 0; i < 2; i++) {
            int cursor = 0x3C80 + i * 0x100;
            var handler = new SwScrlMgz(() ->
                    new com.openggf.game.sonic3k.events.Sonic3kMGZEvents.ScreenPublication(0, 0, cursor, 0, 0));
            handler.update(outputs[i], 0x3C80, 0x600, 1, 1);
            assertEquals(0, handler.getBgCameraX());
        }
        assertNotEquals(outputs[0][32], outputs[1][32]);
    }
    @Test void stateEightKeepsCloudBandsAndLockedTerrainWithVdpPeriod() {
        var p = new com.openggf.game.sonic3k.events.Sonic3kMGZEvents.ScreenPublication(
                8, 0x200, Integer.MIN_VALUE, 0, 0x800);
        var handler = new SwScrlMgz(() -> p);
        int[] output = new int[224];
        handler.update(output, 0x3500, 0x850, 1, 1);
        Set<Short> bands = new HashSet<>();
        for (int word : output) bands.add((short) word);
        assertTrue(bands.size() > 1);
        assertTrue(bands.contains((short) -0x300));
        assertNotEquals((short) -0x300, (short) output[0]);
        assertEquals(512, handler.getBgPeriodWidth());
    }

    @Test void publicationShakeOffsetsBothPlanes() {
        var plain = new SwScrlMgz(() -> new com.openggf.game.sonic3k.events.Sonic3kMGZEvents.ScreenPublication(
                8, 0x200, Integer.MIN_VALUE, 0, 0));
        var shaken = new SwScrlMgz(() -> new com.openggf.game.sonic3k.events.Sonic3kMGZEvents.ScreenPublication(
                8, 0x200, Integer.MIN_VALUE, 3, 0));
        plain.update(new int[224], 0x3500, 0x850, 1, 1);
        shaken.update(new int[224], 0x3500, 0x850, 1, 1);
        assertEquals(3, shaken.getVscrollFactorFG() - plain.getVscrollFactorFG());
        assertEquals(3, shaken.getVscrollFactorBG() - plain.getVscrollFactorBG());
        assertEquals(3, shaken.getShakeOffsetY());
    }

    @Test void completedPublicationKeepsCloudFreezeAcrossCToEight() {
        var player = new com.openggf.tests.TestablePlayableSprite("sonic", (short) 0x3500, (short) 0x850);
        player.setCentreX((short) 0x3500);
        player.setCentreY((short) 0x850);
        GameServices.camera().setFocusedSprite(player);
        var events = new com.openggf.game.sonic3k.events.Sonic3kMGZEvents();
        events.init(1);
        events.setBgRiseLoadStateInitialised(true);
        events.setBgRiseRoutine(8);
        events.setBgRiseOffset(0x1D0);
        events.updateAfterDynamicObjects(1, 1);
        assertTrue(events.screenPublication().cloudPhase() > 0);
        player.setCentreX((short) 0x3900);
        player.setCentreY((short) 0x7F0);
        events.updateAfterDynamicObjects(1, 2);
        assertEquals(12, events.screenPublication().riseRoutine());
        assertEquals(0, events.screenPublication().cloudPhase());
        player.setCentreX((short) 0x3A40);
        player.setCentreY((short) 0x800);
        events.updateAfterDynamicObjects(1, 3);
        assertEquals(8, events.screenPublication().riseRoutine());
        assertEquals(0, events.screenPublication().cloudPhase());
        events.updateAfterDynamicObjects(1, 4);
        assertEquals(0, events.screenPublication().cloudPhase());
    }

    @Test void providerRoutesMgzToDedicatedProjection() throws Exception {
        var provider = new Sonic3kScrollHandlerProvider();
        provider.load(new Rom());
        assertTrue(provider.getHandler(Sonic3kZoneConstants.ZONE_MGZ) instanceof SwScrlMgz);
    }

}
