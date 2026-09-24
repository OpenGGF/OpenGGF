package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.sprites.playable.ObjectControlState;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS3kBossDefeatSignpostFlow {

    /**
     * A seamless act change moves this flow with every other world-space slot.
     *
     * <p>{@code loc_787E0} (sonic3k.asm:160569-160576) rewrites the defeated boss's OWN SST entry
     * to {@code Obj_EndSignControl}, keeping its {@code render_flags}, and
     * {@code Offset_ObjectsDuringTransition} (sonic3k.asm:104166-104178) subtracts the handover's
     * {@code d0} from {@code x_pos} of every slot in
     * {@code Dynamic_object_RAM+object_size}..{@code Breathing_bubbles} whose bit 2 is set. So the
     * flow must carry the native position contract. Before it did, the Lava Reef handover refused
     * to run at all the moment a real miniboss defeat put this object on the live slot list:
     * "SST slot 36 reports render_flags bit 2 without a native ROM position contract".
     */
    @Test
    void theActChangeOffsetMovesTheSignpostXWord() {
        S3kBossDefeatSignpostFlow flow = new S3kBossDefeatSignpostFlow(
                0x2CA0, 0, S3kBossDefeatSignpostFlow.CleanupAction.NONE);

        assertTrue(flow.participatesInRomWorldTransitionOffset(),
                "the flow keeps the boss slot's render_flags bit 2");
        flow.offsetNativePositionWordsPreserveSubpixel(-0x2C00, 0);

        assertEquals(0x00A0, flow.getX(),
                "sub.w d0,x_pos(a1) with the Lava Reef handover's $2C00 (sonic3k.asm:104174)");
        assertEquals(0, flow.getY(),
                "no routine after loc_787E0 reads this slot's y_pos, and the Lava Reef d1 is 0");
    }

    @Test
    void displacedBossCollisionEntriesAdvanceEndSignControlWait() {
        S3kBossDefeatSignpostFlow flow = new S3kBossDefeatSignpostFlow(
                0x1180, 0,
                S3kBossDefeatSignpostFlow.CleanupAction.RESTORE_AIZ_FIRE_PALETTE,
                12, 0, 0, 0);

        assertEquals(0x77 - 12, flow.waitTimerAfterInitialization(),
                "the boss-owned collision bridge entries advance Obj_EndSignControl's "
                        + "native $77 wait instead of shortening Obj_EndSign's landed timer");
    }

    @Test
    void restorePlayerControlMatchesEndSignControlAwaitStartWrites() {
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        ObjectControlState.nativeBit7FullControl().applyTo(player);
        player.setInteractSlotIndex(23);
        player.setControlLocked(true);
        player.setAir(true);
        player.setAnimationId(Sonic3kAnimationIds.VICTORY);
        player.getAnimationManager().publishPreviousAnimationId(Sonic3kAnimationIds.VICTORY.id());
        player.setAnimationFrameIndex(7);
        player.setAnimationTick(11);
        player.setXSpeed((short) -7);
        player.setYSpeed((short) 2);

        S3kBossDefeatSignpostFlow.restoreNativePlayerControl(player);

        assertFalse(player.isObjectControlled());
        assertEquals(0, player.getInteractSlotIndex());
        assertTrue(player.isControlLocked(),
                "Restore_PlayerControl must not clear the title-card controller lock");
        assertFalse(player.getAir(),
                "Restore_PlayerControl clears Status_InAir");
        assertEquals(Sonic3kAnimationIds.WAIT.id(), player.getAnimationId());
        assertEquals(Sonic3kAnimationIds.WAIT.id(),
                player.getAnimationManager().captureRewindState().lastAnimationId(),
                "Restore_PlayerControl writes anim and prev_anim together");
        assertEquals(0, player.getAnimationFrameIndex());
        assertEquals(0, player.getAnimationTick());
        assertEquals(-7, player.getXSpeed());
        assertEquals(2, player.getYSpeed());
    }
}
