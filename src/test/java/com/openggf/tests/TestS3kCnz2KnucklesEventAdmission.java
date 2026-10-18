package com.openggf.tests;

import com.openggf.LevelFrameResult;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kCNZEvents;
import com.openggf.game.sonic3k.objects.CnzEggCapsuleInstance;
import com.openggf.game.sonic3k.objects.CnzTeleporterBeamInstance;
import com.openggf.game.sonic3k.objects.CnzTeleporterInstance;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Independent CNZ2 screen-event admission oracle, originating Root parity work 2026-10-18.
 * Uses an explicitly positioned ROM fixture and one ordinary level frame, not prior traversal.
 * Native044 CNZ2_ScreenEvent/loc_5216C admits at x>=4880/y>=B00 for Knuckles,
 * creates a teleporter and separate capsule4980/A20, then clamps X and advances FG to4.
 * Obj_CNZTeleporter's later4A38/4A40 arm and beam/capsule completion are separate obligations.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kCnz2KnucklesEventAdmission {
    @BeforeEach
    void setUp() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        var configuration = GameServices.configuration();
        configuration.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
        configuration.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        configuration.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, 320);
        configuration.setSessionOverride(SonicConfiguration.SCREEN_HEIGHT_PIXELS, 224);
    }

    @AfterEach
    void tearDown() {
        TestEnvironment.resetAll();
    }

    @Test
    void declaredKnucklesTriggerInletAdmitsTeleporterAndSeparateCapsule() throws Exception {
        var fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 1)
                .startPosition((short) 0x48C0, (short) 0x0B40)
                .startPositionIsCentre().build();
        var player = assertInstanceOf(Knuckles.class, fixture.sprite());
        var level = GameServices.level();
        var objects = level.getObjectManager();
        assertNotNull(level.getCurrentLevel());
        assertEquals(Sonic3kZoneIds.ZONE_CNZ, level.getCurrentZone());
        assertEquals(1, level.getCurrentAct());
        assertSame(player, fixture.camera().getFocusedSprite());
        var manager = assertInstanceOf(Sonic3kLevelEventManager.class,
                GameServices.module().getLevelEventProvider());
        var events = manager.getCnzEvents();
        assertNotNull(events);
        assertEquals(Sonic3kCNZEvents.FG_ACT2_ENTRY, events.getForegroundRoutine(),
                "fresh production event initialization, no event/route seed");
        requireDeclaredTriggerInlet(player);
        assertFalse(player.isControlLocked());
        int beforeLevelFrame = level.getFrameCounter();

        // The runner admits any original setup-only pass, then executes the ordinary pipeline.
        assertEquals(LevelFrameResult.GAMEPLAY_FRAME,
                fixture.runner().stepFrame(false, false, false, false, false));
        assertEquals((beforeLevelFrame + 1) & 0xFFFF, level.getFrameCounter() & 0xFFFF);
        requireDeclaredTriggerInlet(player);
        assertSame(objects, level.getObjectManager());
        assertSame(events, manager.getCnzEvents());
        var teleporters = objects.activeObjectsOfType(CnzTeleporterInstance.class);
        var capsules = objects.activeObjectsOfType(CnzEggCapsuleInstance.class);

        // Missing production admission is a final semantic old-red, never a setup prerequisite.
        assertAll("CNZ2_ScreenEvent admission before the later teleporter arm",
                () -> assertEquals(1, teleporters.size(), "native event allocates one teleporter"),
                () -> assertEquals(1, capsules.size(), "separate native event capsule"),
                () -> assertEquals(java.util.List.of("4980/A20"), capsules.stream()
                        .map(c -> Integer.toHexString(c.getSpawn().x()).toUpperCase()
                                + "/" + Integer.toHexString(c.getSpawn().y()).toUpperCase()).toList()),
                () -> assertEquals(Sonic3kCNZEvents.FG_ACT2_KNUCKLES_ROUTE,
                        events.getForegroundRoutine()),
                () -> assertEquals(0x4750, fixture.camera().getMinX() & 0xFFFF),
                () -> assertEquals(0x48E0, fixture.camera().getMaxX() & 0xFFFF),
                () -> assertFalse(player.isControlLocked(), "below the distinct4A38 object arm"),
                () -> assertTrue(objects.activeObjectsOfType(CnzTeleporterBeamInstance.class).isEmpty()));
    }

    private static void requireDeclaredTriggerInlet(Knuckles player) {
        int x = player.getCentreX() & 0xFFFF;
        int y = player.getCentreY() & 0xFFFF;
        assertFalse(player.getDead(), "declared positioned inlet must remain live");
        assertTrue(x >= 0x4880 && x < 0x4A38 && y >= 0x0B00,
                "actual ordinary-frame position must satisfy event admission without reaching object arm");
    }
}
