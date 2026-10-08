package com.openggf.tests;

import com.openggf.camera.NativeViewportFraming;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.objects.MhzMinibossInstance;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/** Native camera words stay world-space authorities when the visible viewport widens. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kMhzMinibossViewport {
    @AfterEach void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        SessionManager.clear();
    }

    @ParameterizedTest @EnumSource(WidescreenAspect.class)
    void nativeThresholdArmsAndPositionsTheBossAtEveryViewport(WidescreenAspect aspect) {
        var config = SonicConfigurationService.getInstance();
        config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, aspect.name());
        config.resolveDisplayAspect();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, aspect.pixelWidth());
        SessionManager.clear(); TestEnvironment.activeGameplayMode();
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(7, 0)
                .startPosition((short) 0x4338, (short) 0x07AC).startPositionIsCentre().build();
        var camera = fixture.camera();
        assertEquals(aspect.pixelWidth(), camera.getWidth());
        var events = ((Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider()).getMhzEvents();
        int visibleGate = NativeViewportFraming.visibleLeft(0x4298, aspect.pixelWidth());
        camera.setX((short) (visibleGate - 1));
        camera.setY((short) 0x0710);
        events.update(0, 0);
        assertFalse(events.isBossFlag(), "loc_54B4E: one pixel before native X threshold");
        camera.setX((short) visibleGate);
        camera.setY((short) 0x070F);
        events.update(0, 1);
        assertFalse(events.isBossFlag(), "loc_54B4E: Y gate remains independent");
        camera.setY((short) 0x0710);
        events.update(0, 2);
        assertTrue(events.isBossFlag(), "wide visible-left must not delay the native $4298 gate");
        assertEquals(8, events.getSpecialEventsRoutine());
        var bosses = GameServices.level().getObjectManager().activeObjectsOfType(MhzMinibossInstance.class);
        assertEquals(1, bosses.size());
        var boss = bosses.getFirst();
        boss.update(2, fixture.sprite());
        assertEquals(0x43A8, boss.getX(), "loc_75220: native Camera_X_pos + $110");
        events.updateSpecialEvents(0);
        assertEquals(0x4298, camera.getMinX() & 0xFFFF, "native boundary word, not visible-left");
        assertEquals(visibleGate, camera.getX() & 0xFFFF, "projection must not shift the visible camera");
        events.update(0, 3);
        assertEquals(1, GameServices.level().getObjectManager()
                .activeObjectsOfType(MhzMinibossInstance.class).size(), "no duplicate activation");

        // Admit a production frame so scroll/art derived state is initialized
        // before a whole-world snapshot; raw event setup alone has not rendered.
        fixture.stepFrame(false, false, false, false, false);
        var registry = fixture.gameplayMode().getRewindRegistry();
        var saved = registry.capture();
        wrapArena(fixture, events, aspect.pixelWidth());
        var expected = registry.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(saved);
            same(saved, registry.capture(), "active arena restore");
            assertTrue(com.openggf.game.internal.NativeArenaCameraFraming.current().centerNativeArenaCamera(),
                    "framing is derived from the restored event flag");
            wrapArena(fixture, events, aspect.pixelWidth());
            same(expected, registry.capture(), "arena wrap replay");
        }
    }

    @ParameterizedTest @EnumSource(WidescreenAspect.class)
    void inheritedResultsLockKeepsProjectionUntilTheNativeBoundaryExpands(WidescreenAspect aspect) {
        var config = SonicConfigurationService.getInstance();
        config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, aspect.name());
        config.resolveDisplayAspect();
        SessionManager.clear(); TestEnvironment.activeGameplayMode();
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(7, 1)
                .startPosition((short) 448, (short) 1964).startPositionIsCentre().build();
        var camera = fixture.camera();
        var events = ((Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider()).getMhzEvents();
        var framing = com.openggf.game.internal.NativeArenaCameraFraming.current();
        assertFalse(framing.centerNativeArenaCamera(), "ordinary cold Act 2 has no inherited lock");
        camera.setMinX((short) 0x98);
        camera.setMaxX((short) 0x98);
        camera.setX((short) NativeViewportFraming.visibleLeft(0x98, aspect.pixelWidth()));
        camera.setY((short) 0x710);
        camera.setMinY((short) 0x710);
        GameServices.gameState().setEndOfLevelActive(true);
        assertTrue(framing.centerNativeArenaCamera());
        events.update(1, 0);
        assertEquals(0x620, camera.getMinY() & 0xffff,
                "negative wide visible-left must still enter the early ROM corridor");
        assertEquals(0x98, camera.getMinX() & 0xffff);
        GameServices.gameState().setEndOfLevelActive(false);
        assertTrue(framing.centerNativeArenaCamera(), "title-card wait retains the same boundary");
        camera.setMaxX((short) 0x99);
        assertFalse(framing.centerNativeArenaCamera(), "Change_Act2Sizes releases the inherited boundary");
    }

    private static void wrapArena(HeadlessTestFixture fixture,
            com.openggf.game.sonic3k.events.Sonic3kMHZEvents events, int width) {
        var camera = fixture.camera();
        camera.setX((short) NativeViewportFraming.visibleLeft(0x43FF, width));
        events.updateSpecialEvents(0);
        assertEquals(0, events.getLevelRepeatOffset(), "before loc_54CB0's native $4400 threshold");
        assertEquals(0x43FF, camera.getMinX() & 0xFFFF);
        int playerX = fixture.sprite().getCentreX();
        var boss = GameServices.level().getObjectManager().activeObjectsOfType(MhzMinibossInstance.class).getFirst();
        int bossX = boss.getX();
        camera.setX((short) NativeViewportFraming.visibleLeft(0x4400, width));
        events.updateSpecialEvents(0);
        assertEquals(0x200, events.getLevelRepeatOffset());
        assertEquals(NativeViewportFraming.visibleLeft(0x4200, width), camera.getX() & 0xFFFF);
        assertEquals(0x4200, camera.getMinX() & 0xFFFF);
        assertEquals(0x4298, camera.getMaxX() & 0xFFFF);
        assertEquals(playerX - 0x200, fixture.sprite().getCentreX());
        assertEquals(bossX - 0x200, boss.getX());
    }

    private static void same(com.openggf.game.rewind.CompositeSnapshot expected,
            com.openggf.game.rewind.CompositeSnapshot actual, String label) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), label);
        for (String key : expected.entries().keySet()) {
            var differences = com.openggf.game.rewind.RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(differences.isEmpty(), () -> label + " " + key + ": " + differences);
        }
    }
}
