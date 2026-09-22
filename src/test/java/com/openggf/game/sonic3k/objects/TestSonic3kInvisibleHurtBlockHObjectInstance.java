package com.openggf.game.sonic3k.objects;

import com.openggf.game.DamageCause;
import com.openggf.game.ShieldType;
import com.openggf.game.sonic1.objects.TestPlayableSprite;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.TestObjectServices;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestSonic3kInvisibleHurtBlockHObjectInstance {

    @Test
    public void subtypeDecodesSolidDimensionsLikeInvisibleBlock() {
        Sonic3kInvisibleHurtBlockHObjectInstance block = new Sonic3kInvisibleHurtBlockHObjectInstance(
                new ObjectSpawn(0x100, 0x180, 0x6A, 0xF1, 0, false, 0));

        SolidObjectParams params = block.getSolidParams();

        assertEquals(0x80 + 0x0B, params.halfWidth());
        assertEquals(0x10, params.airHalfHeight());
        assertEquals(0x11, params.groundHalfHeight());
    }

    @Test
    public void defaultPlacementHurtsOnStandingContact() {
        Sonic3kInvisibleHurtBlockHObjectInstance block = new Sonic3kInvisibleHurtBlockHObjectInstance(
                new ObjectSpawn(0x100, 0x180, 0x6A, 0x11, 0, false, 0));
        RecordingPlayer player = new RecordingPlayer();

        block.onSolidContact(player, new SolidContact(true, false, false, true, false), 7);

        assertTrue(player.hurtOrDeathCalled);
        assertEquals(0x100, player.lastSourceX);
        assertEquals(DamageCause.NORMAL, player.lastCause);
        assertFalse(player.lastHadRings);
    }

    @Test
    public void xFlipHurtsOnSideContactOnly() {
        Sonic3kInvisibleHurtBlockHObjectInstance block = new Sonic3kInvisibleHurtBlockHObjectInstance(
                new ObjectSpawn(0x100, 0x180, 0x6A, 0x11, 0x01, false, 0));
        RecordingPlayer player = new RecordingPlayer();

        block.onSolidContact(player, new SolidContact(false, true, false, false, false, 6, true), 7);

        assertTrue(player.hurtOrDeathCalled);
    }

    @Test
    public void yFlipHurtsOnBottomContactOnly() {
        Sonic3kInvisibleHurtBlockHObjectInstance block = new Sonic3kInvisibleHurtBlockHObjectInstance(
                new ObjectSpawn(0x100, 0x180, 0x6A, 0x11, 0x02, false, 0));
        RecordingPlayer player = new RecordingPlayer();

        block.onSolidContact(player, new SolidContact(false, false, true, false, false), 7);

        assertTrue(player.hurtOrDeathCalled);
    }

    @Test
    public void inactiveFaceDoesNotHurt() {
        Sonic3kInvisibleHurtBlockHObjectInstance block = new Sonic3kInvisibleHurtBlockHObjectInstance(
                new ObjectSpawn(0x100, 0x180, 0x6A, 0x11, 0x01, false, 0));
        RecordingPlayer player = new RecordingPlayer();

        block.onSolidContact(player, new SolidContact(true, false, false, true, false), 7);

        assertFalse(player.hurtOrDeathCalled);
        assertFalse(player.hurtCalled);
    }

    @Test
    public void invulnerablePlayerIsIgnored() {
        Sonic3kInvisibleHurtBlockHObjectInstance block = new Sonic3kInvisibleHurtBlockHObjectInstance(
                new ObjectSpawn(0x100, 0x180, 0x6A, 0x11, 0, false, 0));
        RecordingPlayer player = new RecordingPlayer();
        player.setInvulnerableFrames(60);

        block.onSolidContact(player, new SolidContact(true, false, false, true, false), 7);

        assertFalse(player.hurtOrDeathCalled);
        assertFalse(player.hurtCalled);
    }

    @Test
    public void ringedPlayerUsesOrdinaryHurtCharacterLostRingOrdering() {
        Sonic3kInvisibleHurtBlockHObjectInstance block = new Sonic3kInvisibleHurtBlockHObjectInstance(
                new ObjectSpawn(0x100, 0x180, 0x6A, 0x11, 0, false, 0));
        RecordingServices services = new RecordingServices();
        block.setServices(services);
        RecordingPlayer player = new RecordingPlayer();
        player.setRingCount(42);

        block.onSolidContact(player, new SolidContact(true, false, false, true, false), 0x0C66);

        assertEquals(0, services.immediateLostRingSpawns,
                "sub_1F58C allocates Obj_Bouncing_Ring through HurtCharacter");
        assertEquals(1, services.delayedLostRingSpawns);
        assertFalse(services.deferredOwnerLostRingSpawn,
                "invisible hurt blocks use the same ring-clear timing as other HurtCharacter callers");
        assertTrue(player.hurtOrDeathCalled);
        assertTrue(player.lastHadRings);
    }

    @Test
    public void hurtRewindsPlayerYByCurrentYSpeedBeforeApplyingHurt() {
        Sonic3kInvisibleHurtBlockHObjectInstance block = new Sonic3kInvisibleHurtBlockHObjectInstance(
                new ObjectSpawn(0x100, 0x180, 0x6A, 0x11, 0, false, 0));
        RecordingPlayer player = new RecordingPlayer();
        int initialY = player.getCentreY();
        player.setYSpeed((short) -0x240);

        block.onSolidContact(player, new SolidContact(true, false, false, true, false), 7);

        assertEquals(initialY + 2, player.getCentreY());
        assertEquals(0x4000, player.getYSubpixelRaw());
        assertTrue(player.hurtOrDeathCalled);
    }

    /**
     * {@code Obj_InvisibleLavaBlock} (sonic3k.asm:43270-43272) is {@code bset #4,shield_reaction}
     * and a fall through into this routine, and {@code sub_1F58C} (43427-43438) skips the hurt when
     * {@code shield_reaction(a0) & $73 & shield_reaction(a1)} is non-zero. For a player slot that
     * second byte is {@code status_secondary}, whose bit 4 is {@code Status_FireShield}.
     */
    @Test
    public void lavaBlockIsHarmlessToAFireShieldAndHurtsEveryoneElse() {
        for (ShieldType shieldType : new ShieldType[] {null, ShieldType.BASIC,
                ShieldType.LIGHTNING, ShieldType.BUBBLE, ShieldType.FIRE}) {
            Sonic3kInvisibleLavaBlockObjectInstance block = new Sonic3kInvisibleLavaBlockObjectInstance(
                    new ObjectSpawn(0x100, 0x180, 0x6E, 0x11, 0, false, 0));
            RecordingPlayer player = new RecordingPlayer();
            player.shieldType = shieldType;

            block.onSolidContact(player, new SolidContact(true, false, false, true, false), 7);

            boolean expectHurt = shieldType != ShieldType.FIRE;
            assertEquals(expectHurt, player.hurtOrDeathCalled,
                    "lava block against shield " + shieldType);
            if (expectHurt) {
                assertEquals(DamageCause.FIRE, player.lastCause);
            }
        }
    }

    /** The plain {@code $6A} block sets no reaction bit, so no shield makes it harmless. */
    @Test
    public void plainHurtBlockIgnoresTheFireShield() {
        Sonic3kInvisibleHurtBlockHObjectInstance block = new Sonic3kInvisibleHurtBlockHObjectInstance(
                new ObjectSpawn(0x100, 0x180, 0x6A, 0x11, 0, false, 0));
        RecordingPlayer player = new RecordingPlayer();
        player.shieldType = ShieldType.FIRE;

        block.onSolidContact(player, new SolidContact(true, false, false, true, false), 7);

        assertTrue(player.hurtOrDeathCalled);
        assertEquals(DamageCause.NORMAL, player.lastCause);
    }

    /** The immunity runs before the ring spawn, so a fire-shielded player keeps its rings. */
    @Test
    public void fireShieldedPlayerLosesNoRingsOnTheLavaBlock() {
        Sonic3kInvisibleLavaBlockObjectInstance block = new Sonic3kInvisibleLavaBlockObjectInstance(
                new ObjectSpawn(0x100, 0x180, 0x6E, 0x11, 0, false, 0));
        RecordingServices services = new RecordingServices();
        block.setServices(services);
        RecordingPlayer player = new RecordingPlayer();
        player.shieldType = ShieldType.FIRE;
        player.setRingCount(42);

        block.onSolidContact(player, new SolidContact(true, false, false, true, false), 0x0C66);

        assertEquals(0, services.delayedLostRingSpawns);
        assertEquals(0, services.immediateLostRingSpawns);
        assertFalse(player.hurtOrDeathCalled);
    }

    /** {@code $6E} carries the same subtype-derived solid box as {@code $6A}. */
    @Test
    public void lavaBlockKeepsTheSharedSubtypeDimensions() {
        Sonic3kInvisibleLavaBlockObjectInstance block = new Sonic3kInvisibleLavaBlockObjectInstance(
                new ObjectSpawn(0x100, 0x180, 0x6E, 0xF1, 0, false, 0));

        SolidObjectParams params = block.getSolidParams();

        assertEquals(0x80 + 0x0B, params.halfWidth());
        assertEquals(0x10, params.airHalfHeight());
        assertEquals(0x11, params.groundHalfHeight());
    }

    /** Obj_InvisibleShockBlock sets shield_reaction bit 5 before the shared routine. */
    @Test
    public void shockReactionProtectsOnlyTheLightningShieldOnEverySelectedFace() {
        SolidContact[] faces = {
                new SolidContact(true, false, false, true, false),
                new SolidContact(false, true, false, false, false),
                new SolidContact(false, false, true, false, false)};
        for (int flags = 0; flags < 4; flags++) {
            int activeFace = (flags & 1) != 0 ? 1 : (flags & 2) != 0 ? 2 : 0;
            for (ShieldType shield : new ShieldType[] {null, ShieldType.BASIC,
                    ShieldType.FIRE, ShieldType.BUBBLE, ShieldType.LIGHTNING}) {
                for (int face = 0; face < faces.length; face++) {
                    var block = new Sonic3kInvisibleShockBlockObjectInstance(
                            new ObjectSpawn(0x100, 0x180, 0x6D, 0x11, flags, false, 0));
                    var services = new RecordingServices();
                    block.setServices(services);
                    var player = new RecordingPlayer();
                    player.shieldType = shield;
                    player.setRingCount(7);
                    block.update(0, player);
                    block.update(1, player);
                    block.onSolidContact(player, faces[face], 7);
                    boolean expected = face == activeFace && shield != ShieldType.LIGHTNING;
                    assertEquals(expected, player.hurtOrDeathCalled,
                            "flags=" + flags + " face=" + face + " shield=" + shield);
                    assertEquals(expected && shield == null ? 1 : 0, services.delayedLostRingSpawns);
                }
            }
        }
    }

    @Test
    public void shockFlippedInitializationReturnsBeforeSolidAndRangeTail() {
        for (int flags = 0; flags < 4; flags++) {
            var block = new Sonic3kInvisibleShockBlockObjectInstance(
                    new ObjectSpawn(0x100, 0x180, 0x6D, 0x11, flags, false, 0));
            var player = new RecordingPlayer();
            assertFalse(block.isSolidFor(player));
            block.update(0, player);
            assertEquals(flags == 0, block.isSolidFor(player));
            assertEquals(flags == 0, block.isCustomOutOfRange(0x1000),
                    "flipped init return skips even the range tail");
            block.update(1, player);
            assertTrue(block.isSolidFor(player));
            assertTrue(block.checksOutOfRangeAfterRoutine());
            assertFalse(block.isCustomOutOfRange(0x180), "coarse distance zero");
            assertTrue(block.isCustomOutOfRange(0x200), "unsigned negative distance retires");
        }
    }

    private static final class RecordingPlayer extends TestPlayableSprite {
        private boolean hurtOrDeathCalled;
        private boolean hurtCalled;
        private int lastSourceX;
        private DamageCause lastCause;
        private boolean lastHadRings;
        private int ringCount;

        private ShieldType shieldType;

        @Override
        public boolean hasShield() {
            return shieldType != null;
        }

        @Override
        public ShieldType getShieldType() {
            return shieldType;
        }

        @Override
        public int getRingCount() {
            return ringCount;
        }

        @Override
        public void setRingCount(int ringCount) {
            this.ringCount = ringCount;
        }

        @Override
        public boolean applyHurt(int sourceX) {
            hurtCalled = true;
            lastSourceX = sourceX;
            return true;
        }

        @Override
        public boolean applyHurtOrDeath(int sourceX, DamageCause cause, boolean hadRings) {
            hurtOrDeathCalled = true;
            lastSourceX = sourceX;
            lastCause = cause;
            lastHadRings = hadRings;
            return true;
        }
    }

    private static final class RecordingServices extends TestObjectServices {
        private int immediateLostRingSpawns;
        private int delayedLostRingSpawns;
        private int lastDelayedFrame;
        private boolean deferredOwnerLostRingSpawn;

        @Override
        public void spawnLostRings(com.openggf.game.PlayableEntity player, int frameCounter) {
            immediateLostRingSpawns++;
        }

        @Override
        public void spawnLostRingsAfterCurrentFrame(com.openggf.game.PlayableEntity player, int frameCounter) {
            delayedLostRingSpawns++;
            lastDelayedFrame = frameCounter;
        }

        @Override
        public void spawnLostRingsWithDeferredOwner(
                com.openggf.game.PlayableEntity player, int frameCounter) {
            delayedLostRingSpawns++;
            lastDelayedFrame = frameCounter;
            deferredOwnerLostRingSpawn = true;
        }
    }
}
