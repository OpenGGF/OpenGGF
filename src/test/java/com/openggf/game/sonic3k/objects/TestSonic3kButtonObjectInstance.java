package com.openggf.game.sonic3k.objects;

import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.sonic3k.Sonic3kLevelTriggerManager;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.SolidContact;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class TestSonic3kButtonObjectInstance {

    @AfterEach
    void resetTriggers() {
        Sonic3kLevelTriggerManager.reset();
        com.openggf.level.objects.AbstractObjectInstance.resetCameraBoundsForTests();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"-16,true", "15,true", "16,false", "17,false"})
    @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_3K)
    void topOnlyButtonLandingUsesInclusiveLeftAndExclusiveRightEdge(int offset, boolean lands) {
        var fixture = com.openggf.tests.HeadlessTestFixture.builder().withZoneAndAct(4, 1).build();
        var manager = com.openggf.game.GameServices.level().getObjectManager();
        var button = manager.createDynamicObject(() -> new Sonic3kButtonObjectInstance(
                new ObjectSpawn(0x0748, 0x09FA, Sonic3kObjectIds.BUTTON, 0x20, 0, false, 0)));
        com.openggf.level.objects.AbstractObjectInstance.updateCameraBounds(0x700, 0x980, 0x840, 0xA60, 0);
        button.snapshotPreUpdatePosition();
        var player = fixture.sprite();
        player.setCentreX((short) (button.getX() + offset));
        player.setCentreY((short) (button.getY() - 0x12));
        player.setAir(false);
        player.setYSpeed((short) 0);
        int beforeY = player.getCentreY();

        manager.processImmediateInlineSolidCheckpoint(button, player, java.util.List.of());

        org.junit.jupiter.api.Assertions.assertEquals(lands, manager.isRidingObject(player, button));
        org.junit.jupiter.api.Assertions.assertEquals(lands, manager.hasObjectStandingBit(player, button));
        org.junit.jupiter.api.Assertions.assertEquals(lands, Sonic3kLevelTriggerManager.testBit(0, 0));
        org.junit.jupiter.api.Assertions.assertEquals(lands ? button.getY() - 6 - player.getYRadius() - 1 : beforeY,
                player.getCentreY(), "loc_1E42E rejects x >= object x + d1 before any top lift");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"0,true", "0,false", "32,true", "32,false"})
    @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_3K)
    void buttonContactRequiresItsOwnRenderedBounds(int subtype, boolean visible) {
        var fixture = com.openggf.tests.HeadlessTestFixture.builder().withZoneAndAct(4, 1).build();
        var manager = com.openggf.game.GameServices.level().getObjectManager();
        var button = manager.createDynamicObject(() -> new Sonic3kButtonObjectInstance(
                new ObjectSpawn(0xED8, 0x4BA, Sonic3kObjectIds.BUTTON, subtype, 0, false, 0)));
        int cameraY = visible ? 0x400 : 0x308;
        com.openggf.level.objects.AbstractObjectInstance.updateCameraBounds(
                0xE3A, cameraY, 0xF7A, cameraY + 224, 0);
        button.snapshotPreUpdatePosition();
        var player = fixture.sprite();
        player.setCentreX((short) button.getX());
        player.setCentreY((short) (button.getY() - button.getSolidParams().airHalfHeight()
                - player.getYRadius() - 2));
        player.setAir(true);
        player.setYSpeed((short) 0x100);
        int entryY = player.getCentreY();

        manager.processImmediateInlineSolidCheckpoint(button, player, java.util.List.of());

        org.junit.jupiter.api.Assertions.assertEquals(visible, manager.isRidingObject(player, button));
        org.junit.jupiter.api.Assertions.assertEquals(visible, Sonic3kLevelTriggerManager.testBit(0, 0));
        if (visible) {
            com.openggf.level.objects.AbstractObjectInstance.updateCameraBounds(
                    0xE3A, 0x308, 0xF7A, 0x308 + 224, 0);
            player.setAir(true);
            player.setCentreY((short) (player.getCentreY() - 7));
            int airborneY = player.getCentreY();
            manager.processImmediateInlineSolidCheckpoint(button, player, java.util.List.of());
            assertTrue(manager.isRidingObject(player, button), "skipped helper preserves its owned ride");
            assertTrue(manager.hasObjectStandingBit(player, button));
            assertTrue(player.getAir(), "skipped helper does not replace airborne state");
            org.junit.jupiter.api.Assertions.assertEquals(airborneY, player.getCentreY());
        } else {
            org.junit.jupiter.api.Assertions.assertEquals(entryY, player.getCentreY());
            org.junit.jupiter.api.Assertions.assertEquals(0x100, player.getYSpeed());
            Sonic3kLevelTriggerManager.setBit(0, 0);
            button.update(0, player);
            assertTrue(Sonic3kLevelTriggerManager.testBit(0, 0),
                    "offscreen loc_2C5BE/loc_2C62C skips trigger writes as well as contact");
        }
    }

    @Test
    void topSolidButtonRejectsExactSurfaceBoundary() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        Sonic3kButtonObjectInstance fullSolid = new Sonic3kButtonObjectInstance(
                new ObjectSpawn(0x03E0, 0x05B3, Sonic3kObjectIds.BUTTON, 0x00, 0, false, 0));
        Sonic3kButtonObjectInstance topSolid = new Sonic3kButtonObjectInstance(
                new ObjectSpawn(0x03E0, 0x05B3, Sonic3kObjectIds.BUTTON, 0x20, 0, false, 0));

        assertTrue(fullSolid.usesInclusiveRightEdge());
        assertFalse(topSolid.usesInclusiveRightEdge());
        assertFalse(fullSolid.rejectsZeroDistanceTopSolidLanding(null));
        assertTrue(topSolid.rejectsZeroDistanceTopSolidLanding(null),
                "Obj_Button subtype bit 5 calls S3K SolidObjectTop, whose zero-distance boundary rejects");
    }

    @Test
    void standingSolidContactSetsLevelTriggerImmediatelyForNextPrePhysicsPass() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        ObjectServices services = mock(ObjectServices.class);
        Sonic3kButtonObjectInstance button = new Sonic3kButtonObjectInstance(
                new ObjectSpawn(0x03E0, 0x05B3, Sonic3kObjectIds.BUTTON, 0x20, 0, false, 0));
        button.setServices(services);

        button.onSolidContact(null, standingContact(), 897);

        assertTrue(Sonic3kLevelTriggerManager.testBit(0, 0));
        verify(services).playSfx(anyInt());
    }

    @Test
    void repeatedStandingSolidContactDoesNotReplaySwitchSfxWhileTriggerByteIsSet() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        ObjectServices services = mock(ObjectServices.class);
        Sonic3kButtonObjectInstance button = new Sonic3kButtonObjectInstance(
                new ObjectSpawn(0x03E0, 0x05B3, Sonic3kObjectIds.BUTTON, 0x20, 0, false, 0));
        button.setServices(services);
        Sonic3kLevelTriggerManager.setBit(0, 0);

        button.onSolidContact(null, standingContact(), 898);

        assertTrue(Sonic3kLevelTriggerManager.testBit(0, 0));
        verify(services, never()).playSfx(anyInt());
    }

    private static SolidContact standingContact() {
        return new SolidContact(true, false, false, true, false);
    }
}
