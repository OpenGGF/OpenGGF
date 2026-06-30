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
}
