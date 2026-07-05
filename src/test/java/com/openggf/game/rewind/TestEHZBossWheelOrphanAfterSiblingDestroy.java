package com.openggf.game.rewind;

import com.openggf.camera.Camera;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.game.sonic2.objects.Sonic2ObjectRegistry;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.ObjectAnimationState;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.objects.boss.AbstractBossChild;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EHZ boss's three {@code EHZBossWheel} children are indistinguishable by runtime
 * class to {@code ObjectManager.adoptRewindReconstructionChild}, which historically
 * matched pending rewind-reconstruction children to captured
 * {@code DynamicObjectEntry} records purely by class name, FIFO. Once one wheel is
 * destroyed and pruned from {@code dynamicObjects} BEFORE the frame being captured,
 * the fixed 3-wheel re-spawn on restore has one more candidate than captured entries:
 * FIFO shifts captured state onto the wrong position (a subtype-2 entry adopted onto
 * a position constructed as subtype 1, leaving the {@code final animationState} field
 * -- configured from the position's ORIGINAL construction-time subtype, not the
 * corrected one -- silently wrong), and the last unmatched fresh child leaks as a
 * live orphan: dropped from {@code ObjectManager.dynamicObjects} but never removed
 * from {@code Sonic2EHZBossInstance.childComponents}, so it keeps receiving
 * {@code update()} forever and corrupts the shared {@code wheelYAccumulator}.
 *
 * <p>No ROM required: {@code Sonic2EHZBossInstance(ObjectSpawn)} reads no ROM.
 */
class TestEHZBossWheelOrphanAfterSiblingDestroy {

    private static final String EHZ_BOSS_CLASS =
            "com.openggf.game.sonic2.objects.bosses.Sonic2EHZBossInstance";
    private static final String EHZ_WHEEL_CLASS =
            "com.openggf.game.sonic2.objects.bosses.EHZBossWheel";

    @BeforeEach
    void initHeadless() {
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void tearDown() {
        GraphicsManager.getInstance().resetState();
    }

    @Test
    void survivingWheelsAreExactAfterASiblingIsDestroyedBeforeCapture() throws Exception {
        ObjectManager[] holder = new ObjectManager[1];
        Camera camera = mockCamera();
        ObjectServices services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return holder[0]; }
            @Override public Camera camera() { return camera; }
        };

        ObjectSpawn bossSpawn = new ObjectSpawn(160, 240,
                Sonic2ObjectIds.EHZ_BOSS, 0, 0, false, 0);
        Sonic2ObjectRegistry registry = new Sonic2ObjectRegistry();
        ObjectManager om = new ObjectManager(
                List.of(bossSpawn), registry,
                0, null, null,
                GraphicsManager.getInstance(), camera, services);
        holder[0] = om;
        om.reset(0);

        ObjectInstance boss = findActiveByClass(om, EHZ_BOSS_CLASS);
        assertNotNull(boss, "EHZ boss must be materialised as an active object");

        List<AbstractBossChild> wheels = wheelsOf(boss);
        assertEquals(3, wheels.size(), "EHZ boss must spawn exactly 3 EHZBossWheel children");

        // Destroy + prune the subtype-0 wheel, matching normal per-frame cleanup
        // (Sonic2EHZBossInstance.updateDefeatBounce() bouncing a wheel off-screen).
        AbstractBossChild destroyedWheel = wheelWithSubtype(wheels, 0);
        assertNotNull(destroyedWheel, "precondition: a subtype-0 wheel must exist before destroy");
        destroyedWheel.setDestroyed(true);
        invokePrivate(om, "cleanupDestroyedDynamicObjects");

        assertEquals(2, countByClass(om, EHZ_WHEEL_CLASS),
                "precondition: exactly 2 wheels should remain tracked after the destroy+prune");

        RewindRegistry rr = new RewindRegistry();
        rr.register(om.rewindSnapshottable());
        CompositeSnapshot snap = rr.capture();
        rr.restore(snap);

        ObjectInstance restoredBoss = findActiveByClass(om, EHZ_BOSS_CLASS);
        assertNotNull(restoredBoss, "EHZ boss must remain active after restore");
        List<AbstractBossChild> restoredWheels = wheelsOf(restoredBoss);

        assertEquals(2, restoredWheels.size(),
                "childComponents must contain EXACTLY the 2 surviving wheels after restore, no "
                        + "orphan/duplicate left behind by the unmatched fresh reconstruction child");

        List<ObjectInstance> active = new ArrayList<>(om.getActiveObjects());
        for (AbstractBossChild wheel : restoredWheels) {
            assertTrue(active.contains(wheel),
                    "every childComponents wheel entry must also be tracked by the manager "
                            + "(no untracked live orphan)");
        }

        for (AbstractBossChild wheel : restoredWheels) {
            int subtype = readInt(wheel, "subtype");
            assertTrue(subtype == 1 || subtype == 2,
                    "surviving wheels must be exactly the original subtype-1/2 siblings, got " + subtype);
            ObjectAnimationState animationState = readObject(wheel, "animationState", ObjectAnimationState.class);
            int expectedAnimId = subtype == 2 ? 2 : 1; // EHZBossWheel ctor: useBackgroundArt = (subtype == 2)
            assertEquals(expectedAnimId, animationState.getAnimId(),
                    "wheel subtype=" + subtype + "'s animationState must be configured for its OWN "
                            + "original subtype (foreground animId=1 vs background animId=2), not "
                            + "stale from whichever fresh-construction position FIFO adopted it onto");
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static List<AbstractBossChild> wheelsOf(ObjectInstance boss) throws Exception {
        Field f = findField(boss.getClass(), "childComponents");
        f.setAccessible(true);
        List<AbstractBossChild> out = new ArrayList<>();
        Object value = f.get(boss);
        if (value instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof AbstractBossChild c && c.getClass().getName().equals(EHZ_WHEEL_CLASS)) {
                    out.add(c);
                }
            }
        }
        return out;
    }

    private static AbstractBossChild wheelWithSubtype(List<AbstractBossChild> wheels, int subtype)
            throws Exception {
        for (AbstractBossChild wheel : wheels) {
            if (readInt(wheel, "subtype") == subtype) {
                return wheel;
            }
        }
        return null;
    }

    private static void invokePrivate(Object target, String methodName) throws Exception {
        Method m = target.getClass().getDeclaredMethod(methodName);
        m.setAccessible(true);
        m.invoke(target);
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // walk up
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static int readInt(Object target, String field) throws Exception {
        Field f = findField(target.getClass(), field);
        f.setAccessible(true);
        return f.getInt(target);
    }

    private static <T> T readObject(Object target, String field, Class<T> fieldType) throws Exception {
        Field f = findField(target.getClass(), field);
        f.setAccessible(true);
        return fieldType.cast(f.get(target));
    }

    private static ObjectInstance findActiveByClass(ObjectManager om, String className) {
        for (ObjectInstance o : om.getActiveObjects()) {
            if (o.getClass().getName().equals(className) && !o.isDestroyed()) {
                return o;
            }
        }
        return null;
    }

    private static int countByClass(ObjectManager om, String className) {
        int count = 0;
        for (ObjectInstance o : om.getActiveObjects()) {
            if (o.getClass().getName().equals(className) && !o.isDestroyed()) {
                count++;
            }
        }
        return count;
    }

    private static Camera mockCamera() {
        return new Camera() {
            @Override public short getX() { return 0; }
            @Override public short getY() { return 0; }
            @Override public short getWidth() { return 320; }
            @Override public short getHeight() { return 224; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
        };
    }
}
