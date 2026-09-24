package com.openggf.game.sonic3k.objects;

import com.openggf.game.ShieldType;
import com.openggf.game.sonic1.objects.TestPlayableSprite;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSonic3kInvisibleShockBlockObjectInstance {
    @Test
    void lightningShieldIsImmuneToTheShockFace() {
        RecordingPlayer player = new RecordingPlayer();
        player.giveShield(ShieldType.LIGHTNING);

        block().onSolidContact(player, standing(), 7);

        assertFalse(player.hurt);
    }

    @Test
    void fireShieldDoesNotMatchShieldReactionBitFive() {
        RecordingPlayer player = new RecordingPlayer();
        player.giveShield(ShieldType.FIRE);

        block().onSolidContact(player, standing(), 7);

        assertTrue(player.hurt);
    }

    @Test
    void noShieldUsesTheSharedInvisibleHurtPath() {
        RecordingPlayer player = new RecordingPlayer();

        block().onSolidContact(player, standing(), 7);

        assertTrue(player.hurt);
    }

    private static Sonic3kInvisibleShockBlockObjectInstance block() {
        return new Sonic3kInvisibleShockBlockObjectInstance(
                new ObjectSpawn(0x100, 0x180, 0x6D, 0xE1, 0, false, 0));
    }

    private static SolidContact standing() {
        return new SolidContact(true, false, false, true, false);
    }

    private static final class RecordingPlayer extends TestPlayableSprite {
        private boolean hurt;

        @Override
        public boolean applyHurtOrDeath(int sourceX, com.openggf.game.DamageCause cause, boolean hadRings) {
            hurt = true;
            return true;
        }
    }
}
