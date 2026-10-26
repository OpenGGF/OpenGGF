package com.openggf.game.sonic1.scroll;

import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.level.scroll.ZoneScrollHandler;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SingletonResetExtension.class)
class TestSonic1ScrollLifecycle {
    @BeforeEach
    void configure() {
        TestEnvironment.configureGameModuleFixture(new Sonic1GameModule());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7})
    void reentryUsesTheNewCameraSeedRatherThanThePreviousEntry(int zone) throws Exception {
        Sonic1ScrollHandlerProvider reused = provider();
        reused.initForZone(zone, 0, 0x50, 0x120);
        ZoneScrollHandler handler = reused.getHandler(zone);
        int[] actual = new int[224];
        handler.update(actual, 0x253, 0x179, 73, 0);
        reused.initForZone(zone, 0, 0x1360, 0x350);
        handler.update(actual, 0x1360, 0x350, 0, 0);

        Sonic1ScrollHandlerProvider fresh = provider();
        fresh.initForZone(zone, 0, 0x1360, 0x350);
        int[] expected = new int[224];
        fresh.getHandler(zone).update(expected, 0x1360, 0x350, 0, 0);
        assertArrayEquals(expected, actual, "new entry must discard previous seed and cloud epoch");
        assertEquals(fresh.getHandler(zone).getVscrollFactorBG(), handler.getVscrollFactorBG());
    }

    @Test
    void ghzFreshEntrySeedsBothNativeHorizontalWordsBeforeDeformation() throws Exception {
        Sonic1ScrollHandlerProvider provider = provider();
        provider.initForZone(0, 0, 0x1360, 0x400);
        int[] scroll = new int[224];
        provider.getHandler(0).update(scroll, 0x1360, 0x400, 0, 0);
        // BgScrollSpeed seeds BG2/BG3 to camera X; BgScroll_GHZ leaves them alone.
        assertEquals((short) -0x1360, (short) scroll[80]);
        assertEquals((short) -0x1360, (short) scroll[120]);
        assertEquals((short) -(0x1360 + 1), (short) scroll[0], "first Deform_GHZ cloud add");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7})
    void restoreThenReplayRetainsFractionalPositionsAndTheEntryEpoch(int zone) throws Exception {
        Sonic1ScrollHandlerProvider provider = provider();
        provider.initForZone(zone, 0, 0x50, 0x160);
        ZoneScrollHandler handler = provider.getHandler(zone);
        int[] scroll = new int[224];
        handler.update(scroll, 0x53, 0x163, 21, 0);
        Object saved = handler.captureRewindState();
        assertNotNull(saved, "all S1 delta accumulators require captured entry anchors");
        handler.update(scroll, 0x58, 0x166, 22, 0);
        int[] expected = scroll.clone();
        short expectedY = handler.getVscrollFactorBG();
        handler.update(scroll, 0x987, 0x359, 900, 0);
        handler.restoreRewindState(saved);
        handler.update(scroll, 0x58, 0x166, 22, 0);
        assertArrayEquals(expected, scroll);
        assertEquals(expectedY, handler.getVscrollFactorBG());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6})
    void nativeZoneSetupStillRunsAfterRestoringCheckpointWords(int zone) throws Exception {
        var module = new Sonic1GameModule();
        TestEnvironment.configureGameModuleFixture(module);
        var parallax = new com.openggf.level.ParallaxManager();
        parallax.load(null);
        var bank = new com.openggf.game.internal.CheckpointBackgroundScroll(
                0x123, 0x234, 0x345, 0x456, 0x567, 0x678);
        int cameraX = 0x900;
        int cameraY = 0x1C8;
        com.openggf.level.ParallaxCheckpointAccess.initialize(parallax, zone, 0, cameraX, cameraY, bank);
        var camera = com.openggf.game.GameServices.camera();
        camera.setX((short) cameraX);
        camera.setY((short) cameraY);
        parallax.update(zone, 0, camera, 0);
        var restored = com.openggf.level.ParallaxCheckpointAccess.capture(parallax, zone);
        switch (zone) {
            case 0 -> { // BgScroll_GHZ retains BG2/BG3 X, clears all Y.
                assertEquals(0x345, restored.bg2X());
                assertEquals(0x567, restored.bg3X());
                assertEquals(0, restored.bgY());
            }
            case 1 -> { // BgScroll_MZ does not replace X; Deform_MZ writes all Y.
                assertEquals(0x123, restored.bgX());
                assertEquals(0x345, restored.bg2X());
                assertEquals(0x567, restored.bg3X());
                assertEquals(0x200, restored.bgY());
                assertEquals(0x200, restored.bg2Y());
                assertEquals(0x200, restored.bg3Y());
            }
            case 2 -> assertEquals(((cameraY * 48) >> 8) + 1, restored.bgY());
            case 3 -> { // BgScroll_LZ replaces Y, preserving the lamp X bank.
                assertEquals(0x123, restored.bgX());
                assertEquals(cameraY >> 1, restored.bgY());
                assertEquals(0x345, restored.bg2X());
                assertEquals(0x456, restored.bg2Y());
            }
            case 4 -> {
                assertEquals(0, restored.bgX(), "REV01 BgScroll_SLZ clears X");
                assertEquals((cameraY >> 1) + 0xC0, restored.bgY());
            }
            case 5, 6 -> {
                assertEquals(0x123, restored.bgX());
                assertEquals(((cameraY & 0x7F8) >> 3) + 1, restored.bgY());
            }
            default -> fail("unexpected zone");
        }
    }

    @Test
    void scrapBrainThreeHasAnIndependentLabyrinthOwnerAndDoesNotLeakItsSelection() throws Exception {
        var provider = provider();
        provider.initForZone(Sonic1ZoneConstants.ZONE_SBZ, 2, 0xAE0, 0);
        var alias = provider.getHandler(Sonic1ZoneConstants.ZONE_SBZ);
        assertInstanceOf(SwScrlLz.class, alias);
        assertNotSame(provider.getHandler(Sonic1ZoneConstants.ZONE_LZ), alias);
        provider.initForZone(Sonic1ZoneConstants.ZONE_SBZ, 1, 0x200, 0x100);
        assertInstanceOf(SwScrlSbz.class, provider.getHandler(Sonic1ZoneConstants.ZONE_SBZ));
        provider.initForZone(Sonic1ZoneConstants.ZONE_SBZ, 0, 0x200, 0x100);
        assertInstanceOf(SwScrlSbz.class, provider.getHandler(Sonic1ZoneConstants.ZONE_SBZ));
        provider.initForZone(Sonic1ZoneConstants.ZONE_LZ, 2, 0x200, 0x100);
        assertInstanceOf(SwScrlSbz.class, provider.getHandler(Sonic1ZoneConstants.ZONE_SBZ),
                "another zone's act index must not select the inactive SBZ3 alias");
    }

    private static Sonic1ScrollHandlerProvider provider() throws Exception {
        Sonic1ScrollHandlerProvider provider = new Sonic1ScrollHandlerProvider();
        provider.load(null);
        return provider;
    }
}
