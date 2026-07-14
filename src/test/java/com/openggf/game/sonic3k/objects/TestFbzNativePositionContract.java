package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SubpixelMotion;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestFbzNativePositionContract {
    @Test
    void ordinaryMonitorMovesNativeCentreAndKeepsSubpixelBytes() throws Exception {
        Sonic3kMonitorObjectInstance monitor = new Sonic3kMonitorObjectInstance(
                new ObjectSpawn(0x3200, 0x0540, 1, 3, 0, false, 0));
        SubpixelMotion.State motion = field(monitor, "motion");
        motion.xSub = 0x5A;
        motion.ySub = 0xC3;

        monitor.offsetNativePositionWordsPreserveSubpixel(-0x2E00, 0);

        assertEquals(0x0400, monitor.getX() & 0xFFFF);
        assertEquals(0x0540, monitor.getY() & 0xFFFF);
        assertEquals(0x5A, motion.xSub);
        assertEquals(0xC3, motion.ySub);
    }

    @Test
    void fixedPointMinibossChildChangesOnlyPositionHighWords() throws Exception {
        FbzMinibossInstance boss = new FbzMinibossInstance(
                new ObjectSpawn(0x3200, 0x0540, 0xAA, 0, 0, false, 0));
        FbzMinibossArmChild child = FbzMinibossArmChild.forTest(boss, 0);
        setInt(child, "xFixed", (0x3200 << 8) | 0xA5);
        setInt(child, "yFixed", (0x0540 << 8) | 0x6C);

        child.offsetNativePositionWordsPreserveSubpixel(-0x2E00, 0);

        assertEquals(0x0400, child.getX() & 0xFFFF);
        assertEquals(0x0540, child.getY() & 0xFFFF);
        assertEquals(0xA5, ((int) field(child, "xFixed")) & 0xFF);
        assertEquals(0x6C, ((int) field(child, "yFixed")) & 0xFF);
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(target);
    }

    private static void setInt(Object target, String name, int value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setInt(target, value);
    }
}
