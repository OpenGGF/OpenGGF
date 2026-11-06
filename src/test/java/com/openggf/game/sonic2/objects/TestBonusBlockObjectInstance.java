package com.openggf.game.sonic2.objects;

import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchActorContextPolicy;
import com.openggf.level.objects.TouchAttackBouncePolicy;
import com.openggf.level.objects.TouchCategoryDecodeMode;
import com.openggf.level.objects.TouchOverlapStopPolicy;
import com.openggf.level.objects.TouchResponseProfile;
import com.openggf.level.objects.TouchShieldDeflectCapability;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestBonusBlockObjectInstance {

    @Test
    void touchProfileNamesSonic2SpecialLatchPolicy() {
        BonusBlockObjectInstance block = new BonusBlockObjectInstance(
                new ObjectSpawn(0x1820, 0x0168, Sonic2ObjectIds.BONUS_BLOCK, 0, 0, false, 0),
                "BonusBlock");

        TouchResponseProfile profile = block.getTouchResponseProfile();

        assertEquals(TouchCategoryDecodeMode.SONIC2_SPECIAL_PROPERTY, profile.categoryDecodeMode());
        assertTrue(profile.continuousCallbacks());
        assertFalse(profile.requiresRenderFlagForTouch());
        assertFalse(profile.multiRegionSource());
        assertEquals(TouchShieldDeflectCapability.NONE, profile.shieldDeflectCapability());
        assertEquals(0, profile.shieldReactionFlags());
        assertEquals(TouchAttackBouncePolicy.STANDARD_ENEMY_KILL, profile.attackBouncePolicy());
        assertEquals(TouchActorContextPolicy.MAIN_FULL_SIDEKICK_HURT_ONLY, profile.actorContextPolicy());
        assertEquals(TouchOverlapStopPolicy.STOP_AFTER_FIRST_OVERLAP_FOR_ALL_ACTORS,
                profile.stopAfterFirstOverlapPolicy());

        assertTrue(block.usesSonic2TouchSpecialPropertyResponse());
        assertTrue(block.requiresContinuousTouchCallbacks());
        assertFalse(block.requiresRenderFlagForTouch());
        assertEquals(0xD7, block.getCollisionFlags());
        assertEquals(0, block.getCollisionProperty());
        assertEquals(profile, block.getTouchResponseProfile(false));
    }

    @Test
    void bonusBlockUsesRomTouchSizeRadii() {
        int objectX = 0x1820;
        int objectY = 0x0168;
        assertFalse(BonusBlockObjectInstance.overlapsRomTouchBox(
                0x180C, 0x014C, 16, objectX, objectY, 8, 8),
                "CNZ trace frame 6276 geometry is still outside ObjD8's $17 touch radius");

        assertTrue(BonusBlockObjectInstance.overlapsRomTouchBox(
                0x182B, 0x0151, 19, objectX, objectY, 8, 8),
                "CNZ trace frame 6281 geometry reaches ObjD8's $17 touch radius after standing radius restore");
    }

    @Test
    void bonusBlockBouncePreservesInertia() throws Exception {
        BonusBlockObjectInstance block = new BonusBlockObjectInstance(
                new ObjectSpawn(0x1820, 0x0168, Sonic2ObjectIds.BONUS_BLOCK, 0, 0, false, 0),
                "BonusBlock");
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x182B, (short) 0x0151);
        player.setGSpeed((short) 0x040E);
        player.setRollingJump(true);
        player.setJumping(true);

        Method applyBounce = BonusBlockObjectInstance.class
                .getDeclaredMethod("applyBounce", com.openggf.sprites.playable.AbstractPlayableSprite.class);
        applyBounce.setAccessible(true);
        applyBounce.invoke(block, player);

        assertEquals(0x040E, player.getGSpeed() & 0xFFFF,
                "ObjD8 loc_2C806 does not clear inertia after bounce");
        assertFalse(player.getRollingJump());
        assertFalse(player.isJumping());
        assertTrue(player.getAir());
    }

    @Test
    void spentBlockStaysATouchTargetUntilItsHitScriptEnds() throws Exception {
        BonusBlockObjectInstance block = new BonusBlockObjectInstance(
                new ObjectSpawn(0x11D0, 0x05A0, Sonic2ObjectIds.BONUS_BLOCK, 0, 0, false, 0),
                "BonusBlock");
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x11DE, (short) 0x05A9);
        player.setXSpeed((short) 0x0284);
        player.setYSpeed((short) 0x0762);
        // Third hit: loc_2C806 sets routine 4 and leaves collision_flags=$D7.
        java.lang.reflect.Field spent = BonusBlockObjectInstance.class.getDeclaredField("spentPasses");
        spent.setAccessible(true);
        spent.setInt(block, 0);
        java.lang.reflect.Field property = BonusBlockObjectInstance.class.getDeclaredField("collisionProperty");
        property.setAccessible(true);

        for (int pass = 1; pass < 12; pass++) {
            // TouchResponse still takes this block as its single overlap.
            assertEquals(0xD7, block.getCollisionFlags());
            property.setInt(block, 0x01);
            block.update(pass, player);
            assertFalse(block.isDestroyed(), "routine 4 pass " + pass);
            assertEquals((short) 0x0284, player.getXSpeed(), "loc_2C884 never reads collision_property");
            assertEquals((short) 0x0762, player.getYSpeed());
        }
        block.update(12, player);
        assertTrue(block.isDestroyed(),
                "the hit script's $FD returns anim below 3 on the twelfth pass, where loc_2C884 deletes");
    }

    @Test
    void bonusBlockDoesNotPollCurrentOverlapInOwnUpdate() {
        BonusBlockObjectInstance block = new BonusBlockObjectInstance(
                new ObjectSpawn(0x1B78, 0x02E0, Sonic2ObjectIds.BONUS_BLOCK, 0x40, 0, false, 0),
                "BonusBlock");
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x1B6D, (short) 0x02EF);
        player.setXSpeed((short) -0x05FC);
        player.setYSpeed((short) -0x01B2);

        block.update(0, player);

        assertEquals((short) -0x05FC, player.getXSpeed(),
                "ObjD8_Main consumes Touch_Special's collision_property latch; it does not re-poll current overlap");
        assertEquals((short) -0x01B2, player.getYSpeed(),
                "Trace frame 6815 must keep the previous air velocity until TouchResponse latches the hit");
    }
}
