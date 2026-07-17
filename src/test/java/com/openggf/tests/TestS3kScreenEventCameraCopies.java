package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kFBZEvents;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** ROM Camera_X/Y_pos_copy timing around S3K ScreenEvents and FBZ1BGE_Normal. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kScreenEventCameraCopies {
    @Test
    void fbzReloadOffsetsTheFreshScreenEventsXCopyAndPreservesEveryYAndTargetWord() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_FBZ, 0)
                .startPosition((short) 0x2EE1, (short) 0x0540)
                .startPositionIsCentre()
                .build();
        var camera = fixture.camera();
        camera.setX((short) 0x2EA0);
        camera.setY((short) 0x0540);
        camera.setXCopy((short) 0x1111);
        camera.setYCopy((short) 0x0222);
        camera.setMinX((short) 0x2E20);
        camera.setMaxX((short) 0x2EA0);
        camera.setMinY((short) 0x0500);
        camera.setMaxY((short) 0x0580);
        camera.setMinXTarget((short) 0x2D10);
        camera.setMaxXTarget((short) 0x2FB0);
        camera.setMinYTarget((short) 0x0520);
        camera.setMaxYTarget((short) 0x0560);

        Sonic3kLevelEventManager manager = (Sonic3kLevelEventManager)
                GameServices.module().getLevelEventProvider();
        Sonic3kFBZEvents act1Events = manager.getFbzEvents();
        act1Events.setEventsFg5(true);

        // Production ScreenEvents dispatch: copies live X/Y first, then the
        // FBZ background handler reloads Act 2 and subtracts $2E00 from both
        // the live X and freshly-copied X word.
        manager.update();

        assertEquals(1, GameServices.level().getCurrentAct());
        assertEquals(0x00A0, camera.getX() & 0xFFFF);
        assertEquals(0x00A0, camera.getXCopy() & 0xFFFF);
        assertEquals(0x0540, camera.getY() & 0xFFFF);
        assertEquals(0x0540, camera.getYCopy() & 0xFFFF);
        assertEquals(0x0020, camera.getMinX() & 0xFFFF);
        assertEquals(0x00A0, camera.getMaxX() & 0xFFFF);
        assertEquals(0x0500, camera.getMinY() & 0xFFFF);
        assertEquals(0x0580, camera.getMaxY() & 0xFFFF);
        assertEquals(0x2D10, camera.getMinXTarget() & 0xFFFF);
        assertEquals(0x2FB0, camera.getMaxXTarget() & 0xFFFF);
        assertEquals(0x0520, camera.getMinYTarget() & 0xFFFF);
        assertEquals(0x0560, camera.getMaxYTarget() & 0xFFFF);
    }
}
