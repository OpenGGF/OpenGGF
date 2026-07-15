package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.objects.badniks.MantisBadnikInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestMantisBadnikInstance {

    @Test
    void collisionResponseListReadsLiveMantisPosition() {
        MantisBadnikInstance mantis = new MantisBadnikInstance(
                new ObjectSpawn(0x0CA0, 0x05D0, 0x9D, 0, 0, false, 0));

        assertTrue(mantis.usesCurrentTouchResponseState(),
                "S3K retains the Mantis SST pointer, so TouchResponse reads its live x_pos/y_pos");
    }

    @Test
    void landingAnimationCompletionRetainsItsComposedYPosition() throws Exception {
        AbstractObjectInstance.updateCameraBounds(0, 0, 1024, 1024, 0);
        MantisBadnikInstance mantis = new MantisBadnikInstance(
                new ObjectSpawn(0x0200, 0x0669, 0x9D, 0, 0, false, 0));
        mantis.setServices(new StubObjectServices());
        setField(mantis, "initialized", true);
        setEnumField(mantis, "state", "LAND");
        setField(mantis, "currentY", 0x0680);
        setField(mantis, "animIndex", 3);
        setField(mantis, "animTimer", 0);

        mantis.update(12, null);

        assertEquals("WAIT", readField(mantis, "state").toString());
        assertEquals(0x0680, mantis.getY(),
                "loc_88F48 only writes routine=2; it does not restore the floor-hit Y");
    }

    private static Object readField(Object target, String name) throws Exception {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setEnumField(Object target, String name, String constant) throws Exception {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        field.set(target, Enum.valueOf((Class<? extends Enum>) field.getType(), constant));
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // Continue through the shared badnik base classes.
            }
        }
        throw new NoSuchFieldException(name);
    }
}
