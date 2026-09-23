package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.events.DezFinalScreenEvents;
import com.openggf.game.sonic3k.runtime.DezFinalBossZoneRuntimeState;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestDezFinalScreenEntry {
    @org.junit.jupiter.api.AfterEach void resetViewport() {
        com.openggf.configuration.SonicConfigurationService.getInstance().clearSessionOverrides();
        com.openggf.game.session.SessionManager.clear();
    }
    private HeadlessTestFixture boot() { return boot(320); }
    private HeadlessTestFixture boot(int width) {
        var config = com.openggf.configuration.SonicConfigurationService.getInstance();
        config.setSessionOverride(com.openggf.configuration.SonicConfiguration.DISPLAY_ASPECT,
                com.openggf.configuration.WidescreenAspect.NATIVE_4_3.name());
        config.resolveDisplayAspect();
        config.setSessionOverride(com.openggf.configuration.SonicConfiguration.SCREEN_WIDTH_PIXELS, width);
        com.openggf.game.session.SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        return HeadlessTestFixture.builder().withZoneAndAct(23, 0).build();
    }
    private DezFinalBossZoneRuntimeState state() {
        return (DezFinalBossZoneRuntimeState) GameServices.zoneRuntimeState();
    }
    @Test void firstProductionFrameAllocatesSupportsAndStartsForcedEntryOnlyOnce() {
        var fixture = boot();
        var manager = GameServices.level().getObjectManager();
        assertFalse(state().screenInitApplied());
        fixture.stepIdleFrames(1);
        assertTrue(state().screenInitApplied());
        assertEquals(2, manager.activeObjectsOfType(DezFinalArenaFloor.class).size());
        assertEquals(1, manager.activeObjectsOfType(DezFinalBossController.class).size());
        assertTrue(fixture.sprite().isObjectControlled());
        assertEquals(0x30, fixture.sprite().getCentreX());
        assertEquals(0x80, fixture.camera().getXCopy());
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var saved = registry.capture();
        fixture.stepIdleFrames(3);
        int x = fixture.sprite().getCentreX();
        registry.restore(saved);
        fixture.stepIdleFrames(3);
        assertEquals(x, fixture.sprite().getCentreX());
        assertEquals(1, manager.activeObjectsOfType(DezFinalBossController.class).size());
        assertEquals(2, manager.activeObjectsOfType(DezFinalArenaFloor.class).size());
    }
    @Test void allocationPressureKeepsNativePrefixAndStillInitializesCamera() {
        for (int free = 0; free <= 3; free++) {
            var fixture = boot();
            var manager = GameServices.level().getObjectManager();
            manager.reserveAllButNFreeSlots(free);
            DezFinalScreenEvents.initializeObjectsAndCamera(manager, state());
            assertEquals(Math.min(2, free), manager.activeObjectsOfType(DezFinalArenaFloor.class).size());
            assertEquals(free == 3 ? 1 : 0, manager.activeObjectsOfType(DezFinalBossController.class).size());
            assertEquals(free == 3 ? 0x3C0 : 0, state().bossX());
            assertEquals(free == 3 ? 0xF8 : 0, state().bossY());
            assertEquals(0x80, fixture.camera().getXCopy());
            assertEquals(0x6C0, state().windowBase());
            assertEquals(0, state().uploadedLaserOffset());
            manager.releaseDynamicSlot(80);
            DezFinalScreenEvents.initializeObjectsAndCamera(manager, state());
            assertEquals(free == 3 ? 1 : 0, manager.activeObjectsOfType(DezFinalBossController.class).size());
        }
    }
    @Test void productionRunReachesHandsAndRetainedPlaneStagesThenReplaysAfterRestore() {
        var fixture = boot();
        var manager = GameServices.level().getObjectManager();
        for (int frame = 0; frame < 410; frame++)
            fixture.stepFrame(false, false, false, true, false);
        assertFalse(fixture.sprite().getDead());
        assertEquals(0x2C0, state().windowBase());
        assertEquals(0x10, state().backgroundRoutine());
        assertEquals(2, manager.activeObjectsOfType(DezFinalHand.class).size());
        assertEquals(6, manager.activeObjectsOfType(DezFinalHand.Finger.class).size());
        assertTrue(state().plane().revision() > 16);
        assertTrue(state().arenaPlane().revision() > 16);
        assertEquals(0, state().redrawRequest(), "screen event consumes floor hole redraws");
        var features = new com.openggf.game.sonic3k.Sonic3kZoneFeatureProvider();
        assertEquals(0, features.foregroundDescriptorAt(0x100, 0x160));
        assertEquals(0, features.foregroundDescriptorAt(0x500, 0x160));
        assertEquals(state().plane().descriptor(0x2C0, 0x160),
                features.foregroundDescriptorAt(0x2C0, 0x160));
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var saved = registry.capture();
        fixture.stepIdleFrames(12);
        byte[] stateBytes = state().captureBytes();
        int x = fixture.sprite().getCentreX(), y = fixture.sprite().getCentreY();
        registry.restore(saved);
        fixture.stepIdleFrames(12);
        assertArrayEquals(stateBytes, state().captureBytes());
        assertEquals(x, fixture.sprite().getCentreX());
        assertEquals(y, fixture.sprite().getCentreY());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {320, 352, 400, 528, 800})
    void widenedViewportKeepsNativeEntrySupportAndReachesHands(int width) {
        var fixture = boot(width);
        assertEquals(width, fixture.camera().getWidth());
        fixture.stepIdleFrames(1);
        var provider = new com.openggf.game.sonic3k.Sonic3kZoneFeatureProvider();
        var columns = provider.backgroundColumns();
        if (width == 320) assertNull(columns);
        else {
            assertNotNull(columns);
            assertEquals(0xC0, columns.sourceYEndExclusive(), "native $E0 split minus $20 FBO origin");
            for (int x = 0; x < 320; x++) {
                int screenX = (width - 320) / 2 + x;
                assertEquals(x, columns.sourceX().get(screenX));
                assertEquals(0, columns.yOffsets().get(screenX));
            }
            assertSame(columns, provider.backgroundColumns());
        }
        for (int frame = 0; frame < 410; frame++) {
            fixture.stepFrame(false, false, false, true, false);
            assertFalse(fixture.sprite().getDead(), "death at frame " + frame + ", width " + width);
        }
        assertEquals(2, GameServices.level().getObjectManager().activeObjectsOfType(DezFinalHand.class).size());
        assertFalse(fixture.sprite().isObjectControlled());
        assertEquals(0x10, state().backgroundRoutine());
        int nativeLeft = state().retainedPlaneX() != 0 ? state().retainedPlaneX() : state().planeX();
        for (int x = 0x200; x < 0x400; x += 8) for (int y = 0x100; y < 0x200; y += 8) {
            boolean outside = x + 8 <= nativeLeft || x >= nativeLeft + 320;
            int expected = width > 320 && outside
                    ? GameServices.level().getForegroundTileDescriptorAtWorld(x, y)
                    : state().plane().descriptor(x, y);
            assertEquals(expected, provider.foregroundDescriptorAt(x, y), "body margin/native overlap");
        }
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var saved = registry.capture();
        fixture.stepIdleFrames(12);
        int x = fixture.sprite().getCentreX(), y = fixture.sprite().getCentreY();
        int cameraX = fixture.camera().getX();
        byte[] bytes = state().captureBytes();
        registry.restore(saved);
        fixture.stepIdleFrames(12);
        assertEquals(x, fixture.sprite().getCentreX());
        assertEquals(y, fixture.sprite().getCentreY());
        assertEquals(cameraX, fixture.camera().getX());
        assertArrayEquals(bytes, state().captureBytes());
    }

    @Test void nativeWindowEdgeRefreshesTheCacheBeforeTheNextRomColumnDraw() {
        var fixture = boot(800); fixture.stepIdleFrames(1);
        state().windowBase(0x2C0); state().bossPosition(0x280, 0x100);
        state().publishPlanePosition(0x80, 0); state().publishDisplayedWindow();
        var provider = new com.openggf.game.sonic3k.Sonic3kZoneFeatureProvider();
        int original = GameServices.level().getForegroundTileDescriptorAtWorld(0x200, 0x100);
        int retained = original ^ 0x800;
        state().plane().writeRow(0x200, 0x100, 1, (x, y) -> retained);
        // Source $200 is outside [$C0,$200), then overlaps the native view
        // after a one-pixel move. No retained-plane write happens between reads.
        assertEquals(original, provider.foregroundDescriptorAt(0x200, 0x100));
        long revision = provider.foregroundDescriptorRevision();
        state().publishPlanePosition(0x81, 0);
        assertEquals(retained, provider.foregroundDescriptorAt(0x200, 0x100));
        assertNotEquals(revision, provider.foregroundDescriptorRevision());
    }

    @Test void screenEventPublishesRomFloorHoleIntoTheRetainedPlaneOnly() {
        var fixture = boot();
        fixture.stepIdleFrames(1);
        var level = GameServices.level();
        int untouched = state().arenaPlane().descriptor(0x40, 0xE0);
        state().redrawRequest(0x20);
        fixture.stepIdleFrames(1);
        assertEquals(0, state().redrawRequest());
        for (int y = 0; y < 32; y += 8) for (int x = 0; x < 32; x += 8)
            assertEquals(level.getBackgroundTileDescriptorAtWorld(0x20 + x, 0x1E0 + y),
                    state().arenaPlane().descriptor(0x20 + x, 0xE0 + y));
        assertEquals(untouched, state().arenaPlane().descriptor(0x40, 0xE0));
    }

    @Test void rendererUsesIndependentBossScrollIncludingZeroVerticalOffset() {
        var fixture = boot(); fixture.stepIdleFrames(1);
        var provider = new com.openggf.game.sonic3k.Sonic3kZoneFeatureProvider();
        var controller = new com.openggf.game.render.AdvancedRenderModeController();
        provider.registerAdvancedRenderModes(controller, 23, 0);
        state().bossPosition(0x3C0, 0x1A0); state().publishPlanePosition(0x80, 0);
        var render = controller.resolve(new com.openggf.game.render.AdvancedRenderModeContext(
                fixture.camera(), 0, GameServices.level(), 23, 0, fixture.camera().getX()));
        assertTrue(render.enablePerLineForegroundScroll());
        assertTrue(render.hasForegroundVScrollOverride());
        assertEquals(0, render.foregroundVScrollOverride());
        controller.clear(); provider.registerAdvancedRenderModes(controller, 23, 1);
        assertTrue(controller.isEmpty(), "sanctuary keeps its own rendering");
    }

}
