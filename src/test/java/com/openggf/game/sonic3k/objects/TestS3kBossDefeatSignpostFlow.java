package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.sprites.playable.ObjectControlState;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS3kBossDefeatSignpostFlow {

    @Test
    void restorePlayerControlMatchesEndSignControlAwaitStartWrites() {
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        ObjectControlState.nativeBit7FullControl().applyTo(player);
        player.setInteractSlotIndex(23);
        player.setControlLocked(true);
        player.setAnimationId(Sonic3kAnimationIds.VICTORY);
        player.setXSpeed((short) -7);
        player.setYSpeed((short) 2);

        S3kBossDefeatSignpostFlow.restoreNativePlayerControl(player);

        assertFalse(player.isObjectControlled());
        assertEquals(0, player.getInteractSlotIndex());
        assertTrue(player.isControlLocked(),
                "Restore_PlayerControl must not clear the title-card controller lock");
        assertEquals(Sonic3kAnimationIds.VICTORY.id(), player.getAnimationId());
        assertEquals(-7, player.getXSpeed());
        assertEquals(2, player.getYSpeed());
    }
}
