package com.openggf.game.sonic3k.events;

import com.openggf.game.rewind.snapshot.LevelEventSnapshot;
import com.openggf.game.GameServices;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState;
import com.openggf.level.Block;
import com.openggf.level.Chunk;
import com.openggf.level.Level;
import com.openggf.level.Map;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.SolidTile;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.RingSpawn;
import com.openggf.level.rings.RingSpriteSheet;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestFbzEventRewindRoundTrip {
    @Test
    void managerSidecarDoesNotCaptureAuthoritativeFbzHandlerFieldsTwice() {
        Sonic3kLevelEventManager manager = new Sonic3kLevelEventManager();
        manager.initLevel(Sonic3kZoneIds.ZONE_FBZ, 1);
        Sonic3kFBZEvents events = manager.getFbzEvents();
        FbzZoneRuntimeState runtime = new FbzZoneRuntimeState(1, PlayerCharacter.SONIC_ALONE, events);
        events.setMagneticState(Sonic3kFBZEvents.MagneticPolarity.INACTIVE, 33);
        byte[] runtimeBytes = runtime.captureBytes();
        LevelEventSnapshot managerSnapshot = manager.capture();

        events.setMagneticState(Sonic3kFBZEvents.MagneticPolarity.ACTIVE, 2);
        manager.restore(managerSnapshot);
        assertEquals(Sonic3kFBZEvents.MagneticPolarity.ACTIVE, events.getMagneticPolarity(),
                "level-event sidecar must not restore FBZ authoritative runtime fields");

        runtime.restoreBytes(runtimeBytes);
        manager.reconcileAfterRewindRestore();
        assertEquals(Sonic3kFBZEvents.MagneticPolarity.INACTIVE, events.getMagneticPolarity());
        assertArrayEquals(runtimeBytes, runtime.captureBytes());
    }

    @Test
    void act2ActiveLayoutAndBackgroundRedrawWordsRoundTripThroughRuntimeOwner() {
        Sonic3kFBZEvents events = new Sonic3kFBZEvents();
        events.init(1);
        events.initializeAct2Screen(0x1800);
        events.initializeAct2Background(0x1800);
        events.setForegroundLayoutRegion(4);
        events.updateAct2BackgroundEvent(0xD80, 0xA40, false);
        Sonic3kFBZEvents.Act2TraversalState expected = events.captureAct2TraversalState();

        FbzZoneRuntimeState runtime = new FbzZoneRuntimeState(
                1, PlayerCharacter.SONIC_ALONE, events);
        byte[] snapshot = runtime.captureBytes();
        for (int i = 0; i < 8; i++) {
            events.updateAct2BackgroundEvent(0xD80, 0xA41, false);
        }
        assertNotEquals(expected, events.captureAct2TraversalState());

        runtime.restoreBytes(snapshot);
        assertEquals(expected, events.captureAct2TraversalState());
        assertArrayEquals(snapshot, runtime.captureBytes());
    }

    @Test
    void detachedRestoreWorkspaceDoesNotNormalizeAgainstAmbientTinyMap() {
        TestEnvironment.configureGameModuleFixture(new Sonic3kGameModule());
        try {
            GameServices.level().setLevel(new TinyFbzLevel());
            Sonic3kFBZEvents activeOwner = new Sonic3kFBZEvents();
            activeOwner.init(1);
            GameServices.zoneRuntimeRegistry().install(new FbzZoneRuntimeState(
                    1, PlayerCharacter.SONIC_ALONE, activeOwner));

            Sonic3kFBZEvents detached = new Sonic3kFBZEvents();
            detached.init(1);
            detached.setBossBackgroundState(16, 0x12000, -0x8000);
            detached.setBossLoadPositionAdjustmentPending(true);

            assertDoesNotThrow(() -> detached.updateAct2BackgroundEvent(0, 0, false));
            assertTrue(detached.isBossLoadPositionAdjustmentPending(),
                    "a detached rewind workspace must retain its pending state until rebound as runtime owner");
        } finally {
            TestEnvironment.resetAll();
        }
    }

    @Test
    void activeOwnerSkipsBossWindowClearWhenLiveLayoutCannotContainIt() {
        TestEnvironment.configureGameModuleFixture(new Sonic3kGameModule());
        try {
            GameServices.level().setLevel(new TinyFbzLevel());
            Sonic3kFBZEvents activeOwner = new Sonic3kFBZEvents();
            activeOwner.init(1);
            GameServices.zoneRuntimeRegistry().install(new FbzZoneRuntimeState(
                    1, PlayerCharacter.SONIC_ALONE, activeOwner));
            activeOwner.setBossBackgroundState(16, 0x12000, -0x8000);
            activeOwner.setBossLoadPositionAdjustmentPending(true);

            assertDoesNotThrow(() -> activeOwner.updateAct2BackgroundEvent(0, 0, false));
            assertFalse(activeOwner.isBossLoadPositionAdjustmentPending(),
                    "the live position normalization still completes when only its layout surface is unavailable");
        } finally {
            TestEnvironment.resetAll();
        }
    }

    private static final class TinyFbzLevel implements Level {
        private final Map map = new Map(2, 1, 1);

        @Override public int getPaletteCount() { return 0; }
        @Override public Palette getPalette(int index) { return null; }
        @Override public int getPatternCount() { return 0; }
        @Override public Pattern getPattern(int index) { return null; }
        @Override public int getChunkCount() { return 0; }
        @Override public Chunk getChunk(int index) { return null; }
        @Override public int getBlockCount() { return 0; }
        @Override public Block getBlock(int index) { return null; }
        @Override public SolidTile getSolidTile(int index) { return null; }
        @Override public Map getMap() { return map; }
        @Override public List<ObjectSpawn> getObjects() { return List.of(); }
        @Override public List<RingSpawn> getRings() { return List.of(); }
        @Override public RingSpriteSheet getRingSpriteSheet() { return null; }
        @Override public int getMinX() { return 0; }
        @Override public int getMaxX() { return 128; }
        @Override public int getMinY() { return 0; }
        @Override public int getMaxY() { return 128; }
        @Override public int getZoneIndex() { return Sonic3kZoneIds.ZONE_FBZ; }
    }
}
