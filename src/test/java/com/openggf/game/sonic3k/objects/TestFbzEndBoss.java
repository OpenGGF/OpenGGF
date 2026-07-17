package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic1.objects.TestPlayableSprite;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.StubObjectServices;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/** Locked-on oracle for Obj_FBZEndBoss (sonic3k.asm:148698-149618). */
class TestFbzEndBoss {
    @Test
    void nativeRootShapeAndCharacterCadenceAreExact() {
        assertEquals(0xAC, FbzEndBossInstance.OBJECT_ID);
        assertEquals(8, FbzEndBossInstance.INITIAL_HITS);
        assertEquals(0x16, FbzEndBossInstance.ACTIVE_COLLISION_FLAGS);
        assertEquals(0x20, FbzEndBossInstance.INVULNERABILITY_FRAMES);
        assertEquals(9, FbzEndBossInstance.attackRounds());
        assertEquals(0x1FF, FbzEndBossInstance.rotationTimer(PlayerCharacter.SONIC_ALONE));
        assertEquals(0x1FF, FbzEndBossInstance.rotationTimer(PlayerCharacter.TAILS_ALONE));
        assertEquals(0x1FF, FbzEndBossInstance.rotationTimer(PlayerCharacter.SONIC_AND_TAILS));
        assertEquals(0xFF, FbzEndBossInstance.rotationTimer(PlayerCharacter.KNUCKLES));
        FbzEndBossInstance fresh = new FbzEndBossInstance(
                new com.openggf.level.objects.ObjectSpawn(0x3000, 0x600,
                        FbzEndBossInstance.OBJECT_ID, 0, 0, false, 0));
        assertTrue(fresh.isFacingRight(),
                "fresh render bit0 is clear, so the initial target semantic is right/unflipped");
    }

    @Test
    void rootChildTablePreservesNativeOrderOffsetsAndIndependentWeapon() {
        assertEquals(List.of(
                new FbzEndBossInstance.RootChildSpec(FbzEndBossInstance.RootChildRole.LEFT_ARM, -0x30, 0x48),
                new FbzEndBossInstance.RootChildSpec(FbzEndBossInstance.RootChildRole.RIGHT_ARM, 0x30, 0x48),
                new FbzEndBossInstance.RootChildSpec(FbzEndBossInstance.RootChildRole.WEAPON, 0, -0x28)),
                FbzEndBossInstance.rootChildTable());
    }

    @Test
    void fixedPointEntryAndCircleMotionPreserveFractions() {
        var entry = FbzEndBossInstance.initialPosition(0x2F20, 0x690);
        assertEquals(0x2FC0, entry.x());
        assertEquals(0x630, entry.y());
        assertArrayEquals(new int[]{0, 2, 4, 5, 7, 9, 0x47, 0x48},
                FbzEndBossInstance.circleLookup1Sentinels());

        var moved = FbzEndBossInstance.move8_8(0x630, 0x80, 0x180);
        assertEquals(0x632, moved.position());
        assertEquals(0, moved.fraction());
        assertEquals(0, FbzEndBossInstance.nativeInitialDescentVelocity());
        assertEquals(0x38, FbzEndBossInstance.nativeDescentGravity());
        assertArrayEquals(new int[]{0x48, 0x32, 0, -0x48, -0x32, 0},
                new int[]{
                        FbzEndBossInstance.circleOffset1(0),
                        FbzEndBossInstance.circleOffset1(0x20),
                        FbzEndBossInstance.circleOffset1(0x40),
                        FbzEndBossInstance.circleOffset1(0x80),
                        FbzEndBossInstance.circleOffset1(0xA0),
                        FbzEndBossInstance.circleOffset1(0xC0)});
        assertArrayEquals(new int[]{0x20, 0x16, 0, -0x20, -0x16, 0},
                new int[]{
                        FbzEndBossInstance.circleOffset2(0),
                        FbzEndBossInstance.circleOffset2(0x20),
                        FbzEndBossInstance.circleOffset2(0x40),
                        FbzEndBossInstance.circleOffset2(0x80),
                        FbzEndBossInstance.circleOffset2(0xA0),
                        FbzEndBossInstance.circleOffset2(0xC0)});
    }

    @Test
    void nativeAttackGateKeepsBit0LatchSeparateFromBit3ArmWave() throws Exception {
        assertEquals(new FbzEndBossInstance.AttackGate(true, false, true),
                FbzEndBossInstance.attackGate(true, false, false, false));
        assertEquals(new FbzEndBossInstance.AttackGate(false, false, true),
                FbzEndBossInstance.attackGate(false, false, false, true));
        assertEquals(new FbzEndBossInstance.AttackGate(false, false, true),
                FbzEndBossInstance.attackGate(false, false, true, false));
        assertEquals(new FbzEndBossInstance.AttackGate(false, false, true),
                FbzEndBossInstance.attackGate(true, false, true, false));
        assertEquals(new FbzEndBossInstance.AttackGate(false, true, true),
                FbzEndBossInstance.attackGate(false, true, false, true));

        FbzEndBossInstance boss = new FbzEndBossInstance(
                new com.openggf.level.objects.ObjectSpawn(0x3000, 0x600,
                        FbzEndBossInstance.OBJECT_ID, 0, 0, false, 0));
        invoke(boss, "beginAttack");
        assertTrue(boss.isArmTriggerActive(), "loc_70700 sets bit3 for the immediate arm wave");
        assertFalse(boss.isWeaponTriggerActive(), "loc_70700 does not set attack-latch bit0");
        assertFalse(getBoolean(boss, "attackLatch"), "loc_70700 does not set bit0");

        TestPlayableSprite target = new TestPlayableSprite();
        target.setCentreX((short) 0x3008);
        target.setCentreY((short) 0x600);
        boss.setServices(new StubObjectServices().withPlayerQuery(
                new ObjectPlayerQuery(() -> target, List::of)));

        set(boss, "weaponTrigger", true);
        set(boss, "armTrigger", false);
        int preExistingBusyTimer = getInt(boss, "timer");
        invoke(boss, "updateAttack", com.openggf.game.PlayableEntity.class, target);
        assertFalse(getBoolean(boss, "attackLatch"),
                "an already-set bit1 returns before proximity and cannot synthesize bit0");
        assertEquals(preExistingBusyTimer, getInt(boss, "timer"));
        boss.clearWeaponTrigger();
        set(boss, "armTrigger", true);

        invoke(boss, "updateAttack", com.openggf.game.PlayableEntity.class, target);
        assertTrue(getBoolean(boss, "attackLatch"), "proximity sets bit0");
        assertTrue(boss.isWeaponTriggerActive(), "proximity sets weapon bit1");
        assertTrue(boss.isArmTriggerActive(), "the existing immediate bit3 wave remains active");

        boss.clearArmTrigger();
        int beforeBusyTimer = getInt(boss, "timer");
        invoke(boss, "updateAttack", com.openggf.game.PlayableEntity.class, target);
        assertEquals(beforeBusyTimer, getInt(boss, "timer"),
                "weapon-busy bit1 freezes the non-Knuckles countdown");
        assertTrue(getBoolean(boss, "attackLatch"), "bit0 stays latched throughout weapon busy");

        boss.clearWeaponTrigger();
        target.setCentreX((short) 0x3100);
        invoke(boss, "updateAttack", com.openggf.game.PlayableEntity.class, target);
        assertEquals(beforeBusyTimer - 1, getInt(boss, "timer"),
                "latched bit0 bypasses proximity after the weapon becomes idle");

        set(boss, "timer", 0);
        invoke(boss, "updateAttack", com.openggf.game.PlayableEntity.class, target);
        assertFalse(getBoolean(boss, "attackLatch"), "countdown expiry clears bit0");
        assertTrue(boss.isArmTriggerActive(), "countdown expiry starts the next bit3 arm wave");
    }

    @Test
    void nativeArmTableUsesPositiveVelocityForTargetRightAndNegativeForTargetLeft() throws Exception {
        FbzEndBossInstance boss = new FbzEndBossInstance(
                new com.openggf.level.objects.ObjectSpawn(0x3000, 0x600,
                        FbzEndBossInstance.OBJECT_ID, 0, 0, false, 0));
        FbzEndBossArmChild arm = new FbzEndBossArmChild(boss, 0, -0x30, 0x48);
        set(arm, "jointSpawnAttempted", true);
        set(arm, "motionPhase", 1);
        set(boss, "armTrigger", true);
        set(boss, "facingRight", true);
        arm.update(0, null);
        assertEquals(0x100, getInt(arm, "velocityX"),
                "render bit0 clear / target-right selects +$100");

        set(arm, "motionPhase", 1);
        set(boss, "armTrigger", true);
        set(boss, "facingRight", false);
        arm.update(1, null);
        assertEquals(-0x100, getInt(arm, "velocityX"),
                "render bit0 set / target-left selects -$100");
    }

    @Test
    void nativeDamageAuthorityIsTheRootAndOrdinaryHazardChildrenAreNotAttackable() {
        assertTrue(com.openggf.level.objects.TouchResponseAttackable.class
                .isAssignableFrom(FbzEndBossInstance.class));
        assertFalse(com.openggf.level.objects.TouchResponseAttackable.class
                .isAssignableFrom(FbzEndBossArmChild.class));
        assertFalse(com.openggf.level.objects.TouchResponseAttackable.class
                .isAssignableFrom(FbzEndBossFlameChild.class));
    }

    private static void invoke(Object target, String methodName) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        method.invoke(target);
    }

    private static void invoke(Object target, String methodName, Class<?> parameterType,
                               Object argument) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterType);
        method.setAccessible(true);
        method.invoke(target, argument);
    }

    private static void set(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static int getInt(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static boolean getBoolean(Object target, String fieldName) {
        return assertDoesNotThrow(() -> {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getBoolean(target);
        }, "native state field " + fieldName + " must be modelled independently");
    }
}
