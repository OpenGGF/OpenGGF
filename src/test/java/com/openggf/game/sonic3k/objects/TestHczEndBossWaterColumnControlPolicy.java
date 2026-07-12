package com.openggf.game.sonic3k.objects;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.objects.bosses.HczEndBossInstance;
import com.openggf.game.sonic3k.objects.bosses.HczEndBossTurbine;
import com.openggf.game.sonic3k.objects.bosses.HczEndBossWaterColumn;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestHczEndBossWaterColumnControlPolicy {

    @Test
    void turbineRetainsHurtCollisionUntilSlowdownCallback() throws Exception {
        HczEndBossTurbine turbine = newTurbine();
        setIntField(turbine, "routine", 8);
        turbine.setCollisionFlags(0xA6);
        setIntField(turbine, "animFrame", 0);
        setIntField(turbine, "animCounter", 0);
        assertFalse(turbine.usesCurrentTouchResponseState(),
                "routine 8 consumes the frame-start response-list coordinate");

        invokeNoArgPrivate(turbine, "updateStopping");

        assertEquals(0xA6, turbine.getCollisionFlags(),
                "loc_6B244 does not clear collision_flags while Animate_RawGetSlower is active");

        int slowdownTicks = 1;
        while (turbine.getCollisionFlags() != 0 && slowdownTicks < 256) {
            invokeNoArgPrivate(turbine, "updateStopping");
            slowdownTicks++;
        }

        assertEquals(0, turbine.getCollisionFlags(),
                "loc_6B262 clears collision_flags only after the slowdown callback");
        assertEquals(2, getIntField(turbine, "routine"));
        assertTrue(slowdownTicks > 32);
        assertTrue(turbine.usesCurrentTouchResponseState(),
                "the idle/active child helpers retain refreshed-coordinate touch");
    }

    @Test
    void descentRetainsTheLaterSpraySlotsFinalInteraction() throws Exception {
        HczEndBossWaterColumn column = newWaterColumn();
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x4000, (short) 0x0738);
        setIntField(column, "routine", 6);

        invokePrivate(column, "updateHold", player);

        assertEquals(8, getIntField(column, "routine"));
        assertTrue(getBooleanField(column, "pendingSprayTailInteraction"),
                "loc_6B3DE runs after loc_6B34A changes the parent to DESCEND");

        invokePrivate(column, "updateRiseDescend", player);

        assertFalse(getBooleanField(column, "pendingSprayTailInteraction"),
                "the first descent dispatch consumes exactly one retained spray interaction");
    }

    @Test
    void initialGrabUsesNativeBit0ObjectControlPolicy() throws Exception {
        HczEndBossWaterColumn column = newWaterColumn();
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x4000, (short) 0x0738);
        player.setXSpeed((short) 0x180);
        player.setYSpeed((short) -0x100);
        player.setGSpeed((short) 0x200);

        invokeGrab(column, player, true);

        assertAll(
                () -> assertTrue(player.isObjectControlled(),
                        "HCZ water column ROM move.b #1,object_control should own the player"),
                () -> assertTrue(player.isObjectControlAllowsCpu(),
                        "object_control bits 0-6 allow CPU-side object-control handling"),
                () -> assertTrue(player.isObjectControlSuppressesMovement(),
                        "the water column carry suppresses normal player movement"),
                () -> assertFalse(player.isTouchResponseSuppressedByObjectControl(),
                        "bit-0 object control must not suppress touch response like bit 7"),
                () -> assertEquals(0, player.getXSpeed(), "grab clears x_vel"),
                () -> assertEquals(0, player.getYSpeed(), "grab clears y_vel"),
                () -> assertEquals(0, player.getGSpeed(), "grab clears ground_vel"),
                () -> assertEquals(Sonic3kAnimationIds.DEATH.id(), player.getForcedAnimationId(),
                        "grab forces the water-column tumble animation"));
    }

    @Test
    void releaseClearsWaterColumnObjectControlPolicy() throws Exception {
        HczEndBossWaterColumn column = newWaterColumn();
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x4000, (short) 0x0738);
        invokeGrab(column, player, true);

        invokeRelease(column, player, true);

        assertAll(
                () -> assertFalse(player.isObjectControlled(), "release clears object control ownership"),
                () -> assertFalse(player.isObjectControlAllowsCpu(), "release clears CPU object-control allowance"),
                () -> assertFalse(player.isObjectControlSuppressesMovement(), "release clears movement suppression"),
                () -> assertFalse(player.isTouchResponseSuppressedByObjectControl(), "release clears touch suppression"),
                () -> assertTrue(player.getAir(), "release leaves the player airborne"),
                () -> assertEquals((short) -0x200, player.getYSpeed(), "release applies ROM upward launch velocity"),
                () -> assertEquals(Sonic3kAnimationIds.ROLL.id(), player.getForcedAnimationId(),
                        "release restores the roll animation"));
    }

    private static HczEndBossWaterColumn newWaterColumn() {
        ObjectSpawn spawn = new ObjectSpawn(0x4000, 0x0738, 0x9A, 0, 0, false, 0);
        ObjectServices services = new TestObjectServices()
                .withConfiguration(SonicConfigurationService.createStandalone());
        HczEndBossInstance boss = withConstructionContext(services, () -> new HczEndBossInstance(spawn));
        boss.setServices(services);
        HczEndBossTurbine turbine = withConstructionContext(services, () -> new HczEndBossTurbine(boss, 0, 0x24));
        turbine.setServices(services);
        HczEndBossWaterColumn column = withConstructionContext(services, () -> new HczEndBossWaterColumn(boss, turbine));
        column.setServices(services);
        return column;
    }

    private static HczEndBossTurbine newTurbine() {
        ObjectSpawn spawn = new ObjectSpawn(0x4000, 0x0738, 0x9A, 0, 0, false, 0);
        ObjectServices services = new TestObjectServices()
                .withConfiguration(SonicConfigurationService.createStandalone());
        HczEndBossInstance boss = withConstructionContext(services, () -> new HczEndBossInstance(spawn));
        boss.setServices(services);
        HczEndBossTurbine turbine = withConstructionContext(
                services, () -> new HczEndBossTurbine(boss, 0, 0x24));
        turbine.setServices(services);
        return turbine;
    }

    private static void invokeGrab(HczEndBossWaterColumn column, TestablePlayableSprite player, boolean isPlayer1)
            throws Exception {
        Method method = HczEndBossWaterColumn.class.getDeclaredMethod(
                "doInitialGrab", com.openggf.sprites.playable.AbstractPlayableSprite.class, boolean.class);
        method.setAccessible(true);
        method.invoke(column, player, isPlayer1);
    }

    private static void invokeRelease(HczEndBossWaterColumn column, TestablePlayableSprite player, boolean isPlayer1)
            throws Exception {
        Method method = HczEndBossWaterColumn.class.getDeclaredMethod(
                "releasePlayer", com.openggf.sprites.playable.AbstractPlayableSprite.class, boolean.class);
        method.setAccessible(true);
        method.invoke(column, player, isPlayer1);
    }

    private static void invokePrivate(HczEndBossWaterColumn column, String name, TestablePlayableSprite player)
            throws Exception {
        Method method = HczEndBossWaterColumn.class.getDeclaredMethod(name, com.openggf.game.PlayableEntity.class);
        method.setAccessible(true);
        method.invoke(column, player);
    }

    private static void invokeNoArgPrivate(Object target, String name) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(target);
    }

    private static void setIntField(Object target, String name, int value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setInt(target, value);
    }

    private static int getIntField(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static boolean getBooleanField(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    private static <T> T withConstructionContext(ObjectServices services, ThrowingSupplier<T> supplier) {
        try {
            Method set = AbstractObjectInstance.class.getDeclaredMethod("setConstructionContext", ObjectServices.class);
            set.setAccessible(true);
            set.invoke(null, services);
            return supplier.get();
        } catch (Exception e) {
            throw new AssertionError(e);
        } finally {
            try {
                Method clear = AbstractObjectInstance.class.getDeclaredMethod("clearConstructionContext");
                clear.setAccessible(true);
                clear.invoke(null);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
