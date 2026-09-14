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
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"-16,true", "15,true", "16,false", "17,false"})
    @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_3K)
    void topOnlyButtonLandingUsesInclusiveLeftAndExclusiveRightEdge(int offset, boolean lands) {
        var fixture = com.openggf.tests.HeadlessTestFixture.builder().withZoneAndAct(4, 1).build();
        var manager = com.openggf.game.GameServices.level().getObjectManager();
        var button = manager.createDynamicObject(() -> new Sonic3kButtonObjectInstance(
                new ObjectSpawn(0x0748, 0x09FA, Sonic3kObjectIds.BUTTON, 0x20, 0, false, 0)));
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
