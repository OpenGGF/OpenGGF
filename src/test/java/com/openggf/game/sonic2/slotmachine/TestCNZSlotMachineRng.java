package com.openggf.game.sonic2.slotmachine;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TestCNZSlotMachineRng {
    @Test
    void setupTargetsUsesVintCounterBytesForSpeedsAndTarget() throws Exception {
        CNZSlotMachineManager manager = new CNZSlotMachineManager();
        setIntField(manager, "routine", 0x18);
        manager.activate();

        manager.update(0x1200);

        assertArrayEquals(new int[]{
                (((0x00 & 0x07) - 4) + 0x30),
                (((Integer.rotateLeft(0x00, 4) & 0xFF) & 0x07) - 4) + 0x30,
                (((0x12 & 0x07) - 4) + 0x30)
        }, intArray(manager, "slotSpeeds"));
        assertEquals(3, intField(manager, "slot1Target"));
        assertEquals(0x33, intField(manager, "slot23Target"));
    }

    @Test
    void fineTuneTimerUsesVintCounterLowNibble() throws Exception {
        CNZSlotMachineManager manager = new CNZSlotMachineManager();
        setIntField(manager, "routine", 0x0C);
        setIntField(manager, "slotTimer", 0);

        manager.update(0xABCD);

        assertEquals((0xCD & 0x0F) + 0x0C, intField(manager, "slotTimer"));
    }

    @Test
    void packedTargetsMapToDisplayedReelOrder() throws Exception {
        CNZSlotMachineManager manager = new CNZSlotMachineManager();
        setIntField(manager, "slot1Target", CNZSlotMachineManager.FACE_EGGMAN);
        setIntField(manager, "slot23Target",
                (CNZSlotMachineManager.FACE_RING << 4) | CNZSlotMachineManager.FACE_BAR);

        assertEquals(CNZSlotMachineManager.FACE_EGGMAN, invokeGetTargetForSlot(manager, 0));
        assertEquals(CNZSlotMachineManager.FACE_RING, invokeGetTargetForSlot(manager, 1));
        assertEquals(CNZSlotMachineManager.FACE_BAR, invokeGetTargetForSlot(manager, 2));
    }

    @Test
    void changingStoppedFaceUpdatesMatchingRewardSlot() throws Exception {
        CNZSlotMachineManager manager = new CNZSlotMachineManager();
        setIntField(manager, "slot1Target", CNZSlotMachineManager.FACE_SONIC);
        setIntField(manager, "slot23Target",
                (CNZSlotMachineManager.FACE_TAILS << 4) | CNZSlotMachineManager.FACE_RING);

        invokeSetTargetForSlot(manager, 0, CNZSlotMachineManager.FACE_EGGMAN);
        assertEquals(CNZSlotMachineManager.FACE_EGGMAN, intField(manager, "slot1Target") & 0x07);
        assertEquals((CNZSlotMachineManager.FACE_TAILS << 4) | CNZSlotMachineManager.FACE_RING,
                intField(manager, "slot23Target"));

        invokeSetTargetForSlot(manager, 2, CNZSlotMachineManager.FACE_BAR);
        assertEquals(CNZSlotMachineManager.FACE_EGGMAN, intField(manager, "slot1Target") & 0x07);
        assertEquals((CNZSlotMachineManager.FACE_TAILS << 4) | CNZSlotMachineManager.FACE_BAR,
                intField(manager, "slot23Target"));
    }

    @Test
    void managerDoesNotUseJvmRandomSources() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/openggf/game/sonic2/slotmachine/CNZSlotMachineManager.java"));

        assertFalse(source.contains("java.util.Random"));
        assertFalse(source.contains("new Random"));
        assertFalse(source.contains("random.next"));
    }

    private static int intField(Object target, String fieldName) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static void setIntField(Object target, String fieldName, int value) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setInt(target, value);
    }

    private static int[] intArray(Object target, String fieldName) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return (int[]) field.get(target);
    }

    private static int invokeGetTargetForSlot(CNZSlotMachineManager manager, int slot)
            throws ReflectiveOperationException {
        var method = CNZSlotMachineManager.class.getDeclaredMethod("getTargetForSlot", int.class);
        method.setAccessible(true);
        return (int) method.invoke(manager, slot);
    }

    private static void invokeSetTargetForSlot(CNZSlotMachineManager manager, int slot, int face)
            throws ReflectiveOperationException {
        var method = CNZSlotMachineManager.class.getDeclaredMethod("setTargetForSlot", int.class, int.class);
        method.setAccessible(true);
        method.invoke(manager, slot, face);
    }
}


