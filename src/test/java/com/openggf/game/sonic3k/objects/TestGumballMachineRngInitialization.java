package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameRng;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Literal Obj_GumballMachine init owner, independent of replay metadata and prior RNG entropy. */
class TestGumballMachineRngInitialization {
    @ParameterizedTest
    @ValueSource(ints = {0, 0x12345678, 0xF3456789})
    void firstObjectPassCopiesTheFullNativeVintLongThenLaterPassesPreserveRng(int nativeVint) {
        var rng = new GameRng(GameRng.Flavour.S3K, 0x13579BDFL);
        var services = new TestObjectServices().withRng(rng).withIsolatedObjectManager();
        var machine = services.objectManager().createDynamicObject(() ->
                new GumballMachineObjectInstance(new ObjectSpawn(0x100, 0x200, 0x86, 0, 0, false, 0)));
        machine.update(nativeVint, null);
        // Obj_GumballMachine, sonic3k.asm:127466: move.l(V_int_run_count),RNG_seed.
        // V_int_run_count is ds.l, including bit31; this is an assignment, not a
        // claim that a pre-existing independently advanced seed is equal entropy.
        assertEquals(Integer.toUnsignedLong(nativeVint), rng.getSeed());
        rng.nextRaw();
        long evolved = rng.getSeed();
        machine.update(nativeVint + 1, null);
        assertEquals(evolved, rng.getSeed(), "loc_60CB8 ordinary passes never repeat initialization");
    }

    @Test
    void preparedStateSurvivesClockServiceAndFirstChildCreationWithoutReseeding() {
        var rng = new GameRng(GameRng.Flavour.S3K, 0x13579BDFL);
        var services = new TestObjectServices().withRng(rng).withIsolatedObjectManager();
        var objects = services.objectManager();
        var machine = objects.createDynamicObject(() -> new GumballMachineObjectInstance(
                new ObjectSpawn(0x100, 0x200, 0x86, 0, 0, false, 0)));
        objects.initVblaCounter(0x12345678);
        int beforePreparation = objects.getActiveObjects().size();
        machine.prepareInitialMachineState();
        assertEquals(0x12345678L, rng.getSeed());
        assertEquals(beforePreparation, objects.getActiveObjects().size(), "preparation creates no children");
        int preparedY = machine.getY();
        var prepared = machine.captureRewindState();

        rng.nextRaw();
        long evolved = rng.getSeed();
        objects.advanceVblaCounter();
        machine.prepareInitialMachineState();
        assertEquals(evolved, rng.getSeed(), "preparation is idempotent across clock service");
        assertEquals(preparedY, machine.getY(), "preparation does not advance drift");
        machine.update(objects.getVblaCounter(), null);
        assertEquals(evolved, rng.getSeed(), "initial child creation must retain the pre-interrupt seed");
        assertTrue(objects.getActiveObjects().size() > beforePreparation,
                "the ordinary initial dispatch still creates children");

        machine.restoreRewindState(prepared);
        rng.nextRaw();
        long restoredEvolution = rng.getSeed();
        objects.advanceVblaCounter();
        machine.prepareInitialMachineState();
        assertEquals(restoredEvolution, rng.getSeed(), "the captured preparation flag prevents reseeding after restore");
    }

}
