package com.openggf.sprites.managers;

import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Literal ROM control for the animation state retained in the fixed tail SST. */
class TestS2FixedTailStoragePrerequisite {
    @Test
    void flickChangeRetainsTheOldFrameUntilTheNextDispatch() {
        var player = new TestablePlayableSprite("tails", (short) 0, (short) 0);
        player.setAnimationId(7);
        var controller = new TailsTailsController(player, null, false);
        for (int tick = 1; tick <= 20; tick++) controller.update();
        var before = controller.captureRewindState();
        assertEquals(2, before.currentAnim());
        assertEquals(5, before.frameIndex());
        assertEquals(0, before.frameTick());
        assertEquals(0x0D, before.mappingFrame());

        controller.update();
        var transition = controller.captureRewindState();
        // TAnim_End_FD (s2.asm:41327-41333) writes anim=1 and returns.
        // prev_anim=2, anim_frame=5 and mapping_frame=$0D survive until
        // Tails_Animate_Part2 (s2.asm:41271-41285) resets on the next dispatch.
        assertAll(
                () -> assertEquals(1, transition.currentAnim(), "native anim=$01"),
                () -> assertEquals(2, transition.previousAnim(), "native prev_anim=$02"),
                () -> assertEquals(5, transition.frameIndex(), "native anim_frame=$05"),
                () -> assertEquals(3, transition.frameTick(), "native duration=$03"),
                () -> assertEquals(0x0D, transition.mappingFrame(), "native old mapping_frame=$0D"));
        controller.update();
        var after = controller.captureRewindState();
        assertEquals(1, after.previousAnim());
        assertEquals(1, after.frameIndex());
        assertEquals(7, after.frameTick());
        assertEquals(9, after.mappingFrame());
        assertEquals(0x0D, TailsTailPose.sample(false, 7, 20, false, 0, 0).mappingFrame());
        assertEquals(9, TailsTailPose.sample(false, 7, 21, false, 0, 0).mappingFrame());
        controller.restoreRewindState(transition);
        assertEquals(transition, controller.captureRewindState());
        controller.update();
        assertEquals(after, controller.captureRewindState(), "FD boundary must replay after restore");
    }
}
