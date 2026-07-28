package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.data.Rom;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.objects.bosses.HczEndBossGeyserCutscene;
import com.openggf.game.sonic3k.resources.S3kKosModuleQueue;
import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.game.timing.HardwareTimingService;
import com.openggf.game.timing.HardwareWorkHandle;
import com.openggf.game.timing.HardwareWorkKind;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kObjectKosOwnerRewind {

    @Test
    void largeFanRebindsPendingAndReadyArtByCapturedOrdinal()
            throws Exception {
        OwnerContext context = ownerContext();
        HCZLargeFanObjectInstance fan = new HCZLargeFanObjectInstance(
                new ObjectSpawn(0x200, 0x200, 0x39, 0, 0, false, 0));
        fan.setServices(context.services());
        invoke(fan, "queueFanArt");

        verifyPendingAndReadyRestore(
                fan, context.timing(),
                List.of("artQueue", "artHandle"),
                () -> invoke(fan, "rebindArtAfterRestore"),
                () -> {
                    setField(fan, "phase", 1);
                    fan.update(1, null);
                });
    }

    @Test
    void waterWallRebindsPendingAndReadyArtByCapturedOrdinal()
            throws Exception {
        OwnerContext context = ownerContext();
        HCZWaterWallObjectInstance wall = new HCZWaterWallObjectInstance(
                new ObjectSpawn(0x200, 0x200, 0x3B, 0, 0, false, 0));
        wall.setServices(context.services());
        invoke(wall, "queueArtIfNeeded",
                new Class<?>[] {int.class},
                Sonic3kConstants.ART_KOSM_HCZ_GEYSER_HORZ_ADDR);

        verifyPendingAndReadyRestore(
                wall, context.timing(),
                List.of("artQueue", "artHandle"),
                () -> invoke(wall, "rebindArtAfterRestore"),
                () -> assertTrue((boolean) invoke(
                        wall, "queueArtIfNeeded",
                        new Class<?>[] {int.class},
                        Sonic3kConstants.ART_KOSM_HCZ_GEYSER_HORZ_ADDR)));
    }

    @Test
    void endGeyserRebindsPendingAndReadyArtByCapturedOrdinal()
            throws Exception {
        OwnerContext context = ownerContext();
        HczEndBossGeyserCutscene geyser =
                new HczEndBossGeyserCutscene(0x200, 0x200);
        geyser.setServices(context.services());
        invoke(geyser, "serviceQueuedArt");

        verifyPendingAndReadyRestore(
                geyser, context.timing(),
                List.of("artQueue", "artHandle"),
                () -> invoke(geyser, "rebindArtAfterRestore"),
                () -> invoke(geyser, "serviceQueuedArt"));
    }

    @Test
    void planeIntroRebindsBothPendingAndReadyArtOrdinals()
            throws Exception {
        OwnerContext context = ownerContext();
        AizPlaneIntroInstance intro = new AizPlaneIntroInstance(
                new ObjectSpawn(0x60, 0x30, 0x00, 0, 0, false, 0));
        intro.setServices(context.services());
        invoke(intro, "queueIntroSpriteArt");

        verifyPendingAndReadyRestore(
                intro, context.timing(),
                List.of("introSpriteArtQueue",
                        "planeArtHandle", "emeraldArtHandle"),
                () -> invoke(intro, "rebindIntroSpriteArtAfterRestore"),
                () -> {
                    invoke(intro, "rebindIntroSpriteArtAfterRestore");
                    invoke(intro, "claimIntroSpriteArtIfReady");
                });
    }

    private static void verifyPendingAndReadyRestore(
            AbstractObjectInstance owner,
            HardwareTimingService timing,
            List<String> transientFields,
            ThrowingRunnable rebind,
            ThrowingRunnable consume) throws Exception {
        var pendingSnapshot = timing.capture();
        List<HardwareWorkHandle> expectedHandles =
                List.copyOf(timing.pendingHandles());
        long nextOrdinal = nextKosOrdinal(timing);
        clear(owner, transientFields);
        timing.restore(pendingSnapshot);
        rebind.run();
        assertEquals(expectedHandles, timing.pendingHandles());
        assertEquals(nextOrdinal, nextKosOrdinal(timing),
                "pending restore must not submit replacement work");

        drain(timing);
        var readySnapshot = timing.capture();
        clear(owner, transientFields);
        timing.restore(readySnapshot);
        consume.run();
        assertTrue(timing.pendingHandles().isEmpty(),
                "ready restore must claim the original work");
        assertEquals(nextOrdinal, nextKosOrdinal(timing),
                "ready restore must not submit replacement work");
    }

    private static OwnerContext ownerContext() {
        HardwareTimingService timing = new HardwareTimingService();
        Rom rom = TestEnvironment.currentRom();
        TestObjectServices services = new TestObjectServices() {
            @Override
            public HardwareTimingService hardwareTiming() {
                return timing;
            }

            @Override
            public Rom rom() {
                return rom;
            }
        };
        services.withCamera(new Camera());
        return new OwnerContext(timing, services);
    }

    private static void drain(HardwareTimingService timing) {
        for (int frame = 0;
                frame < 4096
                        && timing.incompleteCount(
                        HardwareWorkKind.KOS_MODULE_QUEUE) > 0;
                frame++) {
            timing.service(HardwareServiceBoundary.PRE_MAIN_LOOP);
            timing.service(HardwareServiceBoundary.POST_OBJECTS);
        }
        assertEquals(0,
                timing.incompleteCount(HardwareWorkKind.KOS_MODULE_QUEUE));
    }

    private static long nextKosOrdinal(HardwareTimingService timing) {
        return timing.capture().nextOrdinals().getOrDefault(
                HardwareWorkKind.KOS_MODULE_QUEUE, 0L);
    }

    private static void clear(Object owner, List<String> fields)
            throws Exception {
        for (String field : fields) {
            setField(owner, field, null);
        }
    }

    private static Object invoke(Object target, String name)
            throws Exception {
        return invoke(target, name, new Class<?>[0]);
    }

    private static Object invoke(
            Object target, String name, Class<?>[] parameterTypes,
            Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(
                name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static void setField(Object target, String name, Object value)
            throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private record OwnerContext(
            HardwareTimingService timing,
            TestObjectServices services) {
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
