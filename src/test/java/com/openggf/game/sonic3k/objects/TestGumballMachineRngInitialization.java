package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameRng;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        // Obj_GumballMachine, sonic3k.asm:127412: move.l(V_int_run_count),RNG_seed.
        // V_int_run_count is ds.l, including bit31; this is an assignment, not a
        // claim that a pre-existing independently advanced seed is equal entropy.
        assertEquals(Integer.toUnsignedLong(nativeVint), rng.getSeed());
        rng.nextRaw();
        long evolved = rng.getSeed();
        machine.update(nativeVint + 1, null);
        assertEquals(evolved, rng.getSeed(), "loc_60CB8 ordinary passes never repeat initialization");
    }
}
