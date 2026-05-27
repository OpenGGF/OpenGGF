package com.openggf.tests;

import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kICZEvents;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kIczAct1TransitionHeadless {

    @Test
    void act1OutdoorTransitionReloadsIcz2AndAppliesRomCameraBounds() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_ICZ, 0)
                .build();
        AbstractPlayableSprite sonic = fixture.sprite();
        Sonic3kLevelEventManager manager =
                (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        Sonic3kICZEvents events = manager.getIczEvents();

        sonic.setCentreX((short) 0x6950);
        sonic.setCentreY((short) 0x0700);
        Camera camera = GameServices.camera();
        camera.setX((short) 0x6900);
        camera.setY((short) 0x0600);
        camera.setMinX((short) 0);
        camera.setMaxX((short) 0x7000);
        camera.setMinY((short) -0x0100);
        camera.setMaxY((short) 0x0800);
        camera.setMaxYTarget((short) 0x0800);
        events.forceAct1NormalBackgroundRoutineForTest();

        manager.update();

        assertTrue(events.isAct2TransitionRequested(),
                "ICZ1BGE_Transition must request the ICZ2 reload at camera X=$6900 "
                        + "(docs/skdisasm/sonic3k.asm:110280-110323)");
        assertEquals(1, GameServices.level().getCurrentAct(),
                "ROM writes Current_zone_and_act=$0501 before Load_Level");
        assertEquals(0x00D0, sonic.getCentreX() & 0xFFFF,
                "ICZ1BGE_Transition subtracts d0=$6880 from player x_pos");
        assertEquals(0x0800, sonic.getCentreY() & 0xFFFF,
                "ICZ1BGE_Transition subtracts d1=-$100 from player y_pos");
        assertEquals(0x0000, camera.getMinX() & 0xFFFF);
        assertEquals(0x7000, camera.getMaxX() & 0xFFFF);
        assertEquals(0x0000, camera.getMinY() & 0xFFFF);
        assertEquals(0x0B20, camera.getMaxY() & 0xFFFF);
        assertEquals(0x0B20, camera.getMaxYTarget() & 0xFFFF);
    }
}
