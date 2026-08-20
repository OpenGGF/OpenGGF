package com.openggf.game.sonic3k.objects;

import com.openggf.game.session.SessionManager;
import com.openggf.game.session.EngineServices;
import com.openggf.tests.TestEnvironment;

import com.openggf.game.session.EngineContext;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.Tails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestAizVineHandleLogic {

    @BeforeEach
    void setUp() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    @Test
    void sidekickGrabMirrorsObjectControlBitZeroWithoutLockingSonicInputHistory() {
        AizVineHandleLogic.State handle = new AizVineHandleLogic.State();
        handle.x = 0x2000;
        handle.y = 0x0400;

        Sonic sonic = new Sonic("sonic", (short) 0, (short) 0);
        sonic.setCentreX((short) handle.x);
        sonic.setCentreY((short) handle.y);
        Tails tails = new Tails("tails_p2", (short) 0, (short) 0);
        tails.setCpuControlled(true);
        tails.setCentreX((short) handle.x);
        tails.setCentreY((short) handle.y);

        AizVineHandleLogic.updatePlayers(handle, null, sonic, tails, 0);

        assertFalse(sonic.isControlLocked(),
                "Sonic_Control still records Ctrl_1_Logical while held by object_control=3");
        assertTrue(sonic.isObjectControlled());
        assertTrue(sonic.isObjectControlAllowsCpu());
        assertTrue(sonic.isObjectControlSuppressesMovement());
        assertTrue(tails.isControlLocked(),
                "Tails CPU follow nudge must see object_control bit 0 set while held by the vine");
        assertTrue(tails.isObjectControlled());
        assertTrue(tails.isObjectControlAllowsCpu());
        assertTrue(tails.isObjectControlSuppressesMovement());
    }

    @Test
    void sidekickLogicalJumpPressReleasesGrabbedHandle() {
        AizVineHandleLogic.State handle = new AizVineHandleLogic.State();
        handle.x = 0x2000;
        handle.y = 0x0400;
        handle.prevX = handle.x;
        handle.prevY = handle.y;

        Tails tails = new Tails("tails_p2", (short) 0, (short) 0);
        tails.setCpuControlled(true);
        tails.setCentreX((short) handle.x);
        tails.setCentreY((short) handle.y);

        AizVineHandleLogic.updatePlayers(handle, null, null, tails, 0);
        assertEquals(1, handle.p2.grabFlag);

        tails.setForcedJumpPress(true);
        AizVineHandleLogic.updatePlayers(handle, null, null, tails, 0);
        AizVineHandleLogic.updatePostPlayer(handle, null, tails);

        assertEquals(0, handle.p2.grabFlag);
        assertFalse(tails.isObjectControlled());
        assertTrue(tails.getAir(), "sub_220C2 release path sets Status_InAir");
    }

    /**
     * AIZRideVineHandle_ProcessPlayer drops a held player whose render_flags
     * bit 7 is clear (sonic3k.asm:46490-46491), and the branch it takes is the
     * plain AIZRideVineHandle_ReleasePlayer (sonic3k.asm:46548-46552) - which
     * writes no velocity, no Status_InAir and no animation, unlike the forced
     * release directly above it.
     */
    @Test
    void offScreenRenderFlagReleasesGrabbedHandleWithoutLaunch() {
        AizVineHandleLogic.State handle = new AizVineHandleLogic.State();
        handle.x = 0x2000;
        handle.y = 0x0400;
        handle.prevX = handle.x;
        handle.prevY = handle.y;

        Tails tails = new Tails("tails_p2", (short) 0, (short) 0);
        tails.setCpuControlled(true);
        tails.setCentreX((short) handle.x);
        tails.setCentreY((short) handle.y);
        tails.setRenderFlagOnScreen(true);

        AizVineHandleLogic.updatePlayers(handle, null, null, tails, 0);
        assertEquals(1, handle.p2.grabFlag);
        assertTrue(tails.isObjectControlled());

        tails.setXSpeed((short) 0);
        tails.setYSpeed((short) 0);
        boolean airBeforeRelease = tails.getAir();

        tails.setRenderFlagOnScreen(false);
        AizVineHandleLogic.updatePlayers(handle, null, null, tails, 0);

        assertEquals(0, handle.p2.grabFlag,
                "render_flags bit 7 clear must clear the grab byte");
        // The engine clears object_control through clearPlayerControl's
        // deferred release (shared with the hurt/dead branch), which lifts
        // movement suppression here and publishes the rest on the next step;
        // the ROM's clr.b runs from the vine's slot, after the player's.
        assertFalse(tails.isObjectControlSuppressesMovement(),
                "AIZRideVineHandle_ReleasePlayer stops the vine owning movement");
        assertEquals(0, tails.getXSpeed(),
                "the off-screen release writes no x_vel");
        assertEquals(0, tails.getYSpeed(),
                "the off-screen release writes no y_vel");
        assertEquals(airBeforeRelease, tails.getAir(),
                "the off-screen release does not set Status_InAir");
    }

    /**
     * A held player whose render flag is still set must NOT be dropped - the
     * passing case for the same branch.
     */
    @Test
    void onScreenRenderFlagKeepsGrabbedHandle() {
        AizVineHandleLogic.State handle = new AizVineHandleLogic.State();
        handle.x = 0x2000;
        handle.y = 0x0400;
        handle.prevX = handle.x;
        handle.prevY = handle.y;

        Tails tails = new Tails("tails_p2", (short) 0, (short) 0);
        tails.setCpuControlled(true);
        tails.setCentreX((short) handle.x);
        tails.setCentreY((short) handle.y);
        tails.setRenderFlagOnScreen(true);

        AizVineHandleLogic.updatePlayers(handle, null, null, tails, 0);
        assertEquals(1, handle.p2.grabFlag);

        AizVineHandleLogic.updatePlayers(handle, null, null, tails, 0);

        assertEquals(1, handle.p2.grabFlag);
        assertTrue(tails.isObjectControlled());
    }
}
