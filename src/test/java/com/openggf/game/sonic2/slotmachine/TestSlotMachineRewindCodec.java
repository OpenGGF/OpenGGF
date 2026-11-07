package com.openggf.game.sonic2.slotmachine;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Modifier;
import static org.junit.jupiter.api.Assertions.*;

class TestSlotMachineRewindCodec {
    @Test void everyMutableFieldRoundTripsWithoutAliasing() throws Exception {
        var machine = new CNZSlotMachineManager();
        int seed = 17;
        for (var field : CNZSlotMachineManager.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            if (field.getType() == int.class) field.setInt(machine, seed++);
            else if (field.getType() == boolean.class) field.setBoolean(machine, true);
            else if (field.getType() == int[].class) {
                var array = (int[]) field.get(machine);
                for (int i = 0; i < array.length; i++) array[i] = seed++;
            }
        }
        var adapter = machine.rewindAdapter();
        var saved = adapter.capture();
        var expected = new java.util.HashMap<String, Object>();
        for (var field : CNZSlotMachineManager.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            if (field.getType() == int.class || field.getType() == boolean.class)
                expected.put(field.getName(), field.get(machine));
            else if (field.getType() == int[].class)
                expected.put(field.getName(), ((int[]) field.get(machine)).clone());
        }
        for (var field : CNZSlotMachineManager.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            if (field.getType() == int.class) field.setInt(machine, -100);
            else if (field.getType() == boolean.class) field.setBoolean(machine, false);
            else if (field.getType() == int[].class) java.util.Arrays.fill((int[]) field.get(machine), -100);
        }
        adapter.restore(saved);
        for (var entry : expected.entrySet()) {
            var field = CNZSlotMachineManager.class.getDeclaredField(entry.getKey());
            field.setAccessible(true);
            if (entry.getValue() instanceof int[] values)
                assertArrayEquals(values, (int[]) field.get(machine), entry.getKey());
            else assertEquals(entry.getValue(), field.get(machine), entry.getKey());
        }
        assertEquals(saved, adapter.capture());
    }

    @Test void midSpinRestoreReplaysSameFutureAndDispatchLatch() {
        var machine = new CNZSlotMachineManager();
        for (int i = 0; i < 30; i++) machine.update(i);
        machine.activate();
        for (int i = 30; i < 55; i++) machine.update(i);
        var adapter = machine.rewindAdapter();
        var saved = adapter.capture();
        for (int i = 54; i < 600; i++) machine.update(i);
        var future = adapter.capture();
        adapter.restore(saved);
        for (int i = 54; i < 600; i++) machine.update(i);
        assertEquals(future, adapter.capture());
    }
}
