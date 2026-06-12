package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.GroundMode;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.physics.Direction;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.sprites.playable.Tails;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kHcz1GroundWallProbe {
    private static SharedLevel sharedLevel;

    @BeforeAll
    static void loadLevel() throws Exception {
        sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, Sonic3kZoneIds.ZONE_HCZ, 0);
    }

    @AfterAll
    static void cleanup() {
        if (sharedLevel != null) {
            sharedLevel.dispose();
            sharedLevel = null;
        }
    }

    @BeforeEach
    void setUp() {
        HeadlessTestFixture.builder()
                .withSharedLevel(sharedLevel)
                .build();
    }

    @Test
    void hcz1FlatRightWallProbeDoesNotPushOnFirstCpuAccelerationFromRest() {
        Tails tails = newRecordedHczTails();
        tails.setXSpeed((short) 0x0000);
        tails.setGSpeed((short) 0x0000);
        tails.capturePreCpuControlSnapshot();
        tails.setXSpeed((short) 0x0006);
        tails.setGSpeed((short) 0x0006);

        assertRecordedWallSeam();

        GameServices.collision().resolveGroundWallCollision(tails);

        assertEquals(0x0006, tails.getGSpeed() & 0xFFFF,
                "The first S3K Tails CPU follow-acceleration tick from rest should not treat the zero-distance seam as penetration");
        assertEquals(0x0006, tails.getXSpeed() & 0xFFFF,
                "The f939 wall probe should leave the just-applied CPU acceleration intact");
        assertFalse(tails.getPushing(),
                "Status_Push starts on the next frame, after Tails entered CPU control with existing ground speed");
    }

    @Test
    void hcz1FlatRightWallProbePushesAtRecordedTailsFrontier() {
        Tails tails = newRecordedHczTails();
        tails.setXSpeed((short) 0x0006);
        tails.setGSpeed((short) 0x0006);
        tails.capturePreCpuControlSnapshot();
        tails.setXSpeed((short) 0x000C);
        tails.setGSpeed((short) 0x000C);

        assertRecordedWallSeam();

        GameServices.collision().resolveGroundWallCollision(tails);
        assertEquals(0x000C, tails.getGSpeed() & 0xFFFF,
                "S3K CPU Tails applies the zero-distance wall velocity response after the same-frame position step");
        assertEquals(0x000C, tails.getXSpeed() & 0xFFFF,
                "The f940 position step should still use the pre-wall-response velocity");
        tails.move(tails.getXSpeed(), tails.getYSpeed());
        GameServices.collision().applyDeferredGroundWallVelocityResponse(tails);

        assertEquals(0, tails.getGSpeed(),
                "CalcRoomInFront should clear ground_vel as soon as the projected right-wall probe overlaps terrain");
        assertEquals(0xFF0C, tails.getXSpeed() & 0xFFFF,
                "Distance -1 at x=$031F should subtract one pixel of velocity, matching S3K Tails_InputAcceleration_Path");
        assertTrue(tails.getPushing(),
                "S3K ground-wall collision should set Status_Push when Tails faces into the right wall");
    }

    private static Tails newRecordedHczTails() {
        Tails tails = new Tails("tails", (short) 0x0315, (short) 0x0610);
        tails.setCpuControlled(true);
        tails.setCentreX((short) 0x0315);
        tails.setCentreY((short) 0x0610);
        tails.setSubpixelRaw(0x1500, 0x5E00);
        tails.setGroundMode(GroundMode.GROUND);
        tails.setDirection(Direction.RIGHT);
        tails.setAir(false);
        tails.setRolling(false);
        tails.setPushing(false);
        tails.setInWater(true);
        tails.setYSpeed((short) 0x0000);
        return tails;
    }

    private static void assertRecordedWallSeam() {
        TerrainCheckResult terrain = ObjectTerrainUtils.checkRightWallDist(0x031F, 0x0618);
        assertTrue(terrain.hasCollision(),
                "HCZ1 terrain sanity check: ROM CalcRoomInFront probes x_pos+$A,y_pos+$8 at this frame");
        assertEquals(0, terrain.distance(),
                "HCZ1 terrain sanity check: object terrain sees the right-wall surface exactly at the probe");
    }
}
