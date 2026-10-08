package com.openggf.tests;

import com.openggf.camera.NativeViewportFraming;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.events.Sonic3kMHZEvents;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/** loc_55312 / loc_5560C keep native gameplay coordinates in the wide chase. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kMhzEndBossViewport {
    @AfterEach void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        SessionManager.clear();
    }

    @ParameterizedTest @EnumSource(WidescreenAspect.class)
    void chaseStartsAtTheNativeThresholdWithoutPullingThePlayerBack(WidescreenAspect aspect) {
        var fixture = boot(aspect);
        var camera = fixture.camera();
        var events = events();
        camera.setX((short) NativeViewportFraming.visibleLeft(0x3EFF, aspect.pixelWidth()));
        events.update(1, 0);
        assertFalse(events.isEndBossArenaBackgroundActive(), "one pixel before loc_55312's gate");
        camera.setX((short) NativeViewportFraming.visibleLeft(0x3F00, aspect.pixelWidth()));
        events.update(1, 1);
        assertTrue(events.isEndBossArenaBackgroundActive(), "native $3F00 gate must not wait for visible-left");
        assertEquals(0x0C, events.getSpecialEventsRoutine());
        assertTrue(camera.getFrozen());
        if (aspect.pixelWidth() > 320) {
            assertTrue(com.openggf.game.internal.NativeArenaCameraFraming.current().centerNativeArenaCamera());
        }
        int playerX = fixture.sprite().getCentreX();
        events.updateSpecialEvents(1);
        assertEquals(NativeViewportFraming.visibleLeft(0x3F04, aspect.pixelWidth()), camera.getX() & 0xFFFF);
        assertEquals(0x3F04, camera.getMinX() & 0xFFFF);
        assertEquals(0x3F04, camera.getMaxX() & 0xFFFF);
        assertEquals(playerX, fixture.sprite().getCentreX(), "sub_556B8 already contains this native-window player");

        // Admit the production scroll/animation owners before taking a full
        // snapshot, then replay the moving camera and its semantic framing.
        fixture.stepFrame(false, false, false, false, false);
        var registry = fixture.gameplayMode().getRewindRegistry();
        var saved = registry.capture();
        for (int n = 0; n < 8; n++) fixture.stepFrame(false, false, false, false, false);
        var expected = registry.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(saved);
            same(saved, registry.capture(), "chase restore");
            assertTrue(events.isEndBossNativeCameraActive());
            for (int n = 0; n < 8; n++) fixture.stepFrame(false, false, false, false, false);
            same(expected, registry.capture(), "chase replay");
        }
    }

    @ParameterizedTest @EnumSource(WidescreenAspect.class)
    void chaseWrapKeepsNativePlayerAndBoundaryWords(WidescreenAspect aspect) {
        var fixture = boot(aspect);
        var camera = fixture.camera();
        var events = events();
        camera.setX((short) NativeViewportFraming.visibleLeft(0x3F00, aspect.pixelWidth()));
        events.update(1, 0);
        assertTrue(events.isEndBossArenaBackgroundActive());
        camera.setX((short) NativeViewportFraming.visibleLeft(0x427C, aspect.pixelWidth()));
        camera.setMinX((short) 0x427C);
        camera.setMaxX((short) 0x427C);
        fixture.sprite().setCentreX((short) 0x42FC);
        events.updateSpecialEvents(1);
        assertEquals(0x200, events.getLevelRepeatOffset());
        assertEquals(NativeViewportFraming.visibleLeft(0x4080, aspect.pixelWidth()), camera.getX() & 0xFFFF);
        assertEquals(0x4080, camera.getMinX() & 0xFFFF);
        assertEquals(0x4080, camera.getMaxX() & 0xFFFF);
        assertEquals(0x40FC, fixture.sprite().getCentreX() & 0xFFFF);
    }

    @ParameterizedTest @EnumSource(WidescreenAspect.class)
    void pillarRampRetainsRomFractionsAndNativeHelperPositions(WidescreenAspect aspect) {
        var fixture = boot(aspect);
        var events = events();
        fixture.camera().setX((short) NativeViewportFraming.visibleLeft(0x3F00, aspect.pixelWidth()));
        events.update(1, 0);
        events.setEndBossArenaForegroundRefreshActiveForTest(true);
        var state = com.openggf.game.sonic3k.runtime.S3kRuntimeStates
                .currentMhz(GameServices.zoneRuntimeRegistry()).orElseThrow();
        var handler = new com.openggf.game.sonic3k.scroll.SwScrlMhz();
        int[] scroll = new int[224];
        int inset = NativeViewportFraming.inset(aspect.pixelWidth());
        int visibleX = NativeViewportFraming.visibleLeft(0x4180, aspect.pixelWidth());
        handler.init(1, visibleX, 0x280);
        handler.update(scroll, visibleX, 0x280, 1, 1);
        // loc_55586: $00180000 / $30 = $00008000, then negate.
        assertEquals((short) (-0x4100 + inset), (short) scroll[0]);
        assertEquals((short) (-0x4118 + inset), (short) scroll[47]);
        assertEquals(0x4220, state.endBossArenaTallSupportX());
        int[] expectedSpikes = {0x4224, 0x4224, 0x4229, 0x4229, 0x422F, 0x422F};
        for (int i = 0; i < expectedSpikes.length; i++) {
            assertEquals(expectedSpikes[i], state.endBossArenaSpikeX(i), "spike " + i);
        }

        // Native movie frame 299985 independently samples camera $417F:
        // all five words and the seven helper positions agree with the ROM.
        handler.update(scroll, NativeViewportFraming.visibleLeft(0x417F, aspect.pixelWidth()), 0x280, 2, 1);
        assertEquals((short) (-16663 + inset), (short) scroll[47]);
        assertEquals(0x4220, state.endBossArenaTallSupportX());
        for (int i = 0; i < expectedSpikes.length; i++) {
            assertEquals(expectedSpikes[i], state.endBossArenaSpikeX(i));
        }

        // The signed loc_55552 gate clears the ramp without moving helpers.
        handler.update(scroll, NativeViewportFraming.visibleLeft(0x403F, aspect.pixelWidth()), 0x280, 3, 1);
        for (int line = 0; line < 48; line++) {
            assertEquals((short) inset, (short) scroll[line], "cleared line " + line);
        }
        assertEquals(0x4220, state.endBossArenaTallSupportX());
        for (int i = 0; i < expectedSpikes.length; i++) {
            assertEquals(expectedSpikes[i], state.endBossArenaSpikeX(i));
        }
        handler.update(scroll, NativeViewportFraming.visibleLeft(0x4040, aspect.pixelWidth()), 0x280, 4, 1);
        assertEquals((short) (-0x3FC0 + inset), (short) scroll[0], "first admitted ramp");
    }

    private static HeadlessTestFixture boot(WidescreenAspect aspect) {
        var config = SonicConfigurationService.getInstance();
        config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, aspect.name());
        config.resolveDisplayAspect();
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(7, 1)
                .startPosition((short) 0x3FA0, (short) 0x02F0).startPositionIsCentre().build();
        assertEquals(aspect.pixelWidth(), fixture.camera().getWidth());
        events().setEventRoutine(0x10);
        events().setAct2BackgroundRoutineForTest(0x10);
        fixture.camera().setY((short) 0x0280);
        fixture.camera().setFrozen(false);
        return fixture;
    }

    private static Sonic3kMHZEvents events() {
        return ((Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider()).getMhzEvents();
    }

    private static void same(com.openggf.game.rewind.CompositeSnapshot expected,
            com.openggf.game.rewind.CompositeSnapshot actual, String label) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), label);
        for (String key : expected.entries().keySet()) {
            var differences = com.openggf.game.rewind.RewindSnapshotDiff.diffKey(
                    key, expected.get(key), actual.get(key));
            assertTrue(differences.isEmpty(), () -> label + " " + key + ": " + differences);
        }
    }
}
