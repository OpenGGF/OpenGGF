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
