package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestSszCraneCameraPan {
    @org.junit.jupiter.api.AfterEach void resetViewport() {
        com.openggf.configuration.SonicConfigurationService.getInstance().clearSessionOverrides();
        com.openggf.game.CrossGameFeatureProvider.getInstance().resetState();
        com.openggf.game.session.SessionManager.clear();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(com.openggf.configuration.WidescreenAspect.class)
    void widerPanKeepsNativeTimingAndBoundsWithoutShowingNegativeWorldX(com.openggf.configuration.WidescreenAspect aspect) {
        var config = com.openggf.configuration.SonicConfigurationService.getInstance();
        int width = aspect.pixelWidth();
        config.clearSessionOverrides();
        config.setSessionOverride(com.openggf.configuration.SonicConfiguration.DISPLAY_ASPECT, aspect.name());
        config.resolveDisplayAspect();
        config.setSessionOverride(com.openggf.configuration.SonicConfiguration.SCREEN_WIDTH_PIXELS, width);
        com.openggf.game.CrossGameFeatureProvider.getInstance().resetState();
        com.openggf.game.session.SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        var pan = createPan();
        var camera = GameServices.camera();
        assertEquals(width, camera.getWidth());
        for (int frame = 1; frame <= 256; frame++) {
            pan.update(frame, null);
            assertEquals(Math.max(0, frame - (width - 320) / 2), camera.getX());
            assertEquals(frame, camera.getMinX());
            assertEquals(frame, camera.getMaxX());
            assertFalse(state().cutsceneFlag(4));
        }
        pan.update(257, null);
        assertTrue(state().cutsceneFlag(4));
        assertEquals(256, com.openggf.game.sonic3k.runtime.SszArenaCamera.nativeX(camera, state()));
        assertTrue(state().centerNativeArenaCamera());
    }

    private SszCraneCameraPan createPan() {
        HeadlessTestFixture.builder().withZoneAndAct(10, 1).build();
        var camera = GameServices.camera();
        camera.setX((short) 0);
        camera.setMinX((short) 0);
        camera.setMaxX((short) 0);
        camera.setY((short) 0x400);
        camera.setScrollLocked(true);
        camera.setHorizScrollDelay(7);
        var pan = new SszCraneCameraPan(new ObjectSpawn(0, 0, 0, 0, 0, false, 0));
        pan.setServices(TestEnvironment.objectServices());
        GameServices.level().getObjectManager().addDynamicObject(pan);
        return pan;
    }

    private SszZoneRuntimeState state() {
        return (SszZoneRuntimeState) GameServices.zoneRuntimeState();
    }

    @Test void nativePanSignalsOnlyOnTheUpdateAfterReachingTheBoundary() {
        var pan = createPan();
        var camera = GameServices.camera();
        for (int update = 1; update <= 256; update++) {
            pan.update(update, null);
            assertEquals(update, camera.getX());
            assertEquals(update, camera.getMinX());
            assertEquals(update, camera.getMaxX());
            assertEquals(0x400, camera.getY());
            assertTrue(camera.getFrozen());
            assertFalse(state().cutsceneFlag(4));
            assertFalse(pan.isDestroyed());
        }
        pan.update(257, null);
        assertEquals(0x100, camera.getX());
        assertEquals(0x400, camera.getY());
        assertFalse(camera.getFrozen());
        assertEquals(7, camera.getHorizScrollDelay(), "Scroll_lock does not clear the ROM history delay");
        assertTrue(state().cutsceneFlag(4));
        assertTrue(pan.isDestroyed());
    }

    @Test void restoreRecreatesPanAndReplaysReleaseFromTheSameCameraPosition() {
        var pan = createPan();
        for (int update = 0; update < 240; update++) pan.update(update, null);
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var saved = registry.capture();
        for (int update = 0; update < 17; update++) pan.update(update, null);
        assertTrue(state().cutsceneFlag(4));
        registry.restore(saved);
        var restored = GameServices.level().getObjectManager()
                .activeObjectsOfType(SszCraneCameraPan.class).getFirst();
        assertEquals(240, GameServices.camera().getX());
        assertFalse(state().cutsceneFlag(4));
        for (int update = 0; update < 16; update++) restored.update(update, null);
        assertFalse(state().cutsceneFlag(4));
        restored.update(17, null);
        assertTrue(state().cutsceneFlag(4));
        assertFalse(GameServices.camera().getFrozen());
        assertEquals(0x100, GameServices.camera().getX());
        assertEquals(0x400, GameServices.camera().getY());
    }
}
