package com.openggf.game.sonic2.objects.badniks;

import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.objects.TouchActorContextPolicy;
import com.openggf.level.objects.TouchAttackBouncePolicy;
import com.openggf.level.objects.TouchCategoryDecodeMode;
import com.openggf.level.objects.TouchOverlapStopPolicy;
import com.openggf.level.objects.TouchResponseProfile;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.sprites.playable.Sonic;
import com.openggf.level.objects.TouchShieldDeflectCapability;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestCrawlBadnikInstance {

    @Test
    void touchProfileNamesSonic2AttackSpecialPolicyWhileKeepingBadnikDefaults() {
        CrawlBadnikInstance crawl = new CrawlBadnikInstance(
                new ObjectSpawn(0x0F00, 0x0400, 0xC8, 0, 0, false, 0));

        TouchResponseProfile profile = crawl.getTouchResponseProfile();

        assertEquals(TouchCategoryDecodeMode.SONIC2_SPECIAL_PROPERTY, profile.categoryDecodeMode());
        assertFalse(profile.continuousCallbacks());
        assertFalse(profile.requiresRenderFlagForTouch());
        assertFalse(profile.multiRegionSource());
        assertEquals(TouchShieldDeflectCapability.NONE, profile.shieldDeflectCapability());
        assertEquals(0, profile.shieldReactionFlags());
        assertEquals(TouchAttackBouncePolicy.STANDARD_ENEMY_KILL, profile.attackBouncePolicy());
        assertEquals(TouchActorContextPolicy.MAIN_FULL_SIDEKICK_HURT_ONLY, profile.actorContextPolicy());
        assertEquals(TouchOverlapStopPolicy.STOP_AFTER_FIRST_OVERLAP_FOR_ALL_ACTORS,
                profile.stopAfterFirstOverlapPolicy());

        assertTrue(crawl.usesSonic2TouchSpecialPropertyResponse());
        assertFalse(crawl.requiresContinuousTouchCallbacks());
        assertFalse(crawl.requiresRenderFlagForTouch());
        assertEquals(0x17, crawl.getCollisionFlags());
        assertEquals(0, crawl.getCollisionProperty());
        assertEquals(profile, crawl.getTouchResponseProfile(false));
    }

    @Test
    void nonRollingSpecialTouchSwitchesAttackingCrawlToHurtCollision() throws Exception {
        CrawlBadnikInstance crawl = new CrawlBadnikInstance(
                new ObjectSpawn(0x0F00, 0x0400, 0xC8, 0, 0, false, 0));
        crawl.setServices(new TestObjectServices());
        forceState(crawl, "ATTACKING", "WALKING");

        Sonic sonic = new Sonic("sonic", (short) 0x0F00, (short) 0x0400);
        sonic.setRolling(false);
        sonic.setRollingJump(false);
        sonic.setJumping(false);
        sonic.setAir(false);
        sonic.setInvincibleFrames(0);

        crawl.onTouchResponse(sonic, new TouchResponseResult(0x17, 8, 8, TouchCategory.SPECIAL), 0);
        crawl.update(0, sonic);

        assertEquals(0x97, crawl.getCollisionFlags());
    }

    @Test
    void uncurledSuperSonicSpecialTouchSelectsOrdinaryEnemyFlags() throws Exception {
        CrawlBadnikInstance crawl = new CrawlBadnikInstance(
                new ObjectSpawn(0x0180, 0x00A0, 0xC8, 0, 0, false, 0));
        crawl.setServices(new TestObjectServices());
        forceState(crawl, "ATTACKING", "PAUSING");
        Sonic sonic = new Sonic("sonic", (short) 0x0170, (short) 0x0090);
        sonic.setRolling(false);
        sonic.setRollingJump(false);
        sonic.setJumping(false);
        sonic.setInvincibleFrames(0);
        sonic.setSuperSonic(true);
        crawl.onTouchResponse(sonic, new TouchResponseResult(0x17, 8, 8, TouchCategory.SPECIAL), 1);

        crawl.update(1, sonic);

        assertEquals(0x17, crawl.getCollisionFlags());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void enemyTouchKillsCrawlWithoutRepeatingSpecialShieldChecks(boolean rolling) {
        CrawlBadnikInstance crawl = new CrawlBadnikInstance(
                new ObjectSpawn(0x0180, 0x00A0, 0xC8, 0, 0, false, 0));
        crawl.setServices(new TestObjectServices().withGameState(
                org.mockito.Mockito.mock(com.openggf.game.GameStateManager.class)));
        Sonic sonic = new Sonic("sonic", (short) 0x0178, (short) 0x0080);
        sonic.setRolling(rolling);
        sonic.setRollingJump(rolling);
        sonic.setAir(true);
        sonic.setInvincibleFrames(120);
        sonic.setYSpeed((short) 0x0480);

        // Touch_Enemy has already accepted the attack. Crawl's radial shield
        // branch belongs to Touch_Special, not to the ordinary kill callback.
        crawl.onPlayerAttack(sonic, new TouchResponseResult(0x17, 8, 8, TouchCategory.ENEMY));

        assertTrue(crawl.isDestroyed());
        assertEquals(0x0480, sonic.getYSpeed(),
                "the touch controller applies the native kill rebound once after this callback");
    }

    @Test
    void pausingCrawlChecksProximityOnTheZeroTimerPass() throws Exception {
        CrawlBadnikInstance crawl = new CrawlBadnikInstance(
                new ObjectSpawn(0x0180, 0x00A0, 0xC8, 0, 0, false, 0));
        crawl.setServices(new TestObjectServices());
        forceState(crawl, "PAUSING", "WALKING");
        Field timer = CrawlBadnikInstance.class.getDeclaredField("pauseTimer");
        timer.setAccessible(true);
        timer.setInt(crawl, 1);
        Sonic sonic = new Sonic("sonic", (short) 0x0170, (short) 0x0090);

        crawl.update(1, sonic);

        Field state = CrawlBadnikInstance.class.getDeclaredField("state");
        state.setAccessible(true);
        Field previous = CrawlBadnikInstance.class.getDeclaredField("previousState");
        previous.setAccessible(true);
        assertEquals("ATTACKING", state.get(crawl).toString());
        assertEquals("PAUSING", previous.get(crawl).toString());
        assertEquals(0, timer.getInt(crawl));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void forceState(CrawlBadnikInstance crawl, String stateName, String previousStateName)
            throws ReflectiveOperationException {
        Field stateField = CrawlBadnikInstance.class.getDeclaredField("state");
        stateField.setAccessible(true);
        stateField.set(crawl, Enum.valueOf((Class<Enum>) stateField.getType(), stateName));

        Field previousStateField = CrawlBadnikInstance.class.getDeclaredField("previousState");
        previousStateField.setAccessible(true);
        previousStateField.set(crawl, Enum.valueOf((Class<Enum>) previousStateField.getType(), previousStateName));
    }
}
