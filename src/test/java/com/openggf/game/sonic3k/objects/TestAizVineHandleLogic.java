package com.openggf.game.sonic3k.objects;

import com.openggf.game.session.SessionManager;
import com.openggf.game.session.EngineServices;
import com.openggf.tests.TestEnvironment;

import com.openggf.game.session.EngineContext;
import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.Tails;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

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

    @Test
    void mainAndThreeSidekicksKeepIndependentIdentityOwnedGrabState() {
        AizVineHandleLogic.State handle = new AizVineHandleLogic.State();
        handle.x = 0x2000;
        handle.y = 0x0400;
        TestablePlayableSprite main = player("main", handle);
        TestablePlayableSprite nativeP2 = player("native-p2", handle);
        TestablePlayableSprite extensionOne = player("extension-1", handle);
        TestablePlayableSprite extensionTwo = player("extension-2", handle);
        TestObjectServices services = services(main, List.of(nativeP2, extensionOne, extensionTwo));

        AizVineHandleLogic.updatePlayers(handle, services, main, nativeP2, 0);

        for (TestablePlayableSprite player : List.of(main, nativeP2, extensionOne, extensionTwo)) {
            assertTrue(player.isObjectControlled(), player.getCode() + " must grab independently");
        }
        assertTrue(AizVineHandleLogic.anyGrabbed(handle));

        extensionOne.setDead(true);
        AizVineHandleLogic.updatePlayers(
                handle, services(main, List.of(extensionTwo, nativeP2)), main, extensionTwo, 0);
        assertFalse(extensionOne.isControlLocked(),
                "an omitted owned extension must still receive death cleanup before deferred frame-end release");
        assertEquals(-1, extensionOne.getForcedAnimationId());
        assertTrue(nativeP2.isObjectControlled(), "native-P2 state must follow its identity after reorder");
        assertTrue(extensionTwo.isObjectControlled());
    }

    @Test
    void replacementCleanupReleasesOwnersWithoutClearingUnrelatedPlayers() {
        AizVineHandleLogic.State handle = new AizVineHandleLogic.State();
        handle.x = 0x2000;
        handle.y = 0x0400;
        TestablePlayableSprite originalMain = player("original-main", handle);
        TestablePlayableSprite originalP2 = player("original-p2", handle);
        AizVineHandleLogic.updatePlayers(
                handle, services(originalMain, List.of(originalP2)), originalMain, originalP2, 0);

        TestablePlayableSprite replacementMain = player("replacement-main", handle);
        TestablePlayableSprite replacementP2 = player("replacement-p2", handle);
        replacementMain.setControlLocked(true);
        replacementMain.setForcedInputMask(TestablePlayableSprite.INPUT_JUMP);
        replacementP2.setControlLocked(true);
        replacementP2.setForcedInputMask(TestablePlayableSprite.INPUT_JUMP);

        AizVineHandleLogic.clearGrabbedPlayers(handle, replacementMain, replacementP2);

        assertFalse(originalMain.isControlLocked());
        assertFalse(originalP2.isControlLocked());
        assertTrue(replacementMain.isControlLocked(), "unrelated replacement main must retain its control owner");
        assertTrue(replacementP2.isControlLocked(), "unrelated replacement P2 must retain its control owner");
        assertEquals(TestablePlayableSprite.INPUT_JUMP, replacementMain.getForcedInputMask());
        assertEquals(TestablePlayableSprite.INPUT_JUMP, replacementP2.getForcedInputMask());
    }

    private static TestablePlayableSprite player(String code, AizVineHandleLogic.State handle) {
        TestablePlayableSprite player = new TestablePlayableSprite(
                code, (short) handle.x, (short) handle.y);
        player.setAir(false);
        return player;
    }

    private static TestObjectServices services(
            PlayableEntity main,
            List<? extends PlayableEntity> sidekicks) {
        return new TestObjectServices() {
            private final ObjectPlayerQuery query = new ObjectPlayerQuery(() -> main, () -> sidekicks);

            @Override
            public ObjectPlayerQuery playerQuery() {
                return query;
            }
        };
    }

    /**
     * AIZRideVineHandle_CheckGrab writes move.b #0,spin_dash_flag(a1)
     * (sonic3k.asm:46743). That is a byte write, so every meaning the byte
     * carries clears together -- pinball mode (bit 0), the pinball speed lock
     * (bit 7) and the spindash charge. Leaving bit 7 set kept a spin-tube lock
     * latched for the rest of the level, and Tails_RollSpeed's entry test
     * (sonic3k.asm:28180-28181) then skipped friction on every later roll.
     */
    @Test
    void grabClearsTheWholeSpinDashFlagByteIncludingTheSpeedLock() {
        AizVineHandleLogic.State handle = new AizVineHandleLogic.State();
        handle.x = 0x2000;
        handle.y = 0x0400;

        Tails tails = new Tails("tails_p2", (short) 0, (short) 0);
        tails.setCpuControlled(true);
        tails.setCentreX((short) handle.x);
        tails.setCentreY((short) handle.y);

        // A spin tube captured this sidekick earlier in the level.
        tails.setPinballMode(true);
        tails.setPinballSpeedLock(true);
        tails.setSpindash(true);

        AizVineHandleLogic.updatePlayers(handle, null, null, tails, 0);

        assertEquals(1, handle.p2.grabFlag);
        assertFalse(tails.getPinballSpeedLock(),
                "the grab's byte write clears spin_dash_flag bit 7");
        assertFalse(tails.getPinballMode(),
                "the grab's byte write clears spin_dash_flag bit 0");
        assertFalse(tails.getSpindash(),
                "the grab's byte write clears the spindash charge");
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
