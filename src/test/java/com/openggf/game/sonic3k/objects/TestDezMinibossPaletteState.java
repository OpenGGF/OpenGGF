package com.openggf.game.sonic3k.objects;

import com.openggf.game.palette.PaletteOwnershipRegistry;
import com.openggf.game.palette.PaletteWrite;
import com.openggf.level.objects.ObjectServices;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestDezMinibossPaletteState {
    private record Fixture(ObjectServices services, PaletteOwnershipRegistry registry,
                           List<PaletteWrite> writes) { }

    private Fixture fixture() throws Exception {
        var services = mock(ObjectServices.class);
        when(services.rom()).thenReturn(TestEnvironment.objectServices().rom());
        var registry = mock(PaletteOwnershipRegistry.class);
        when(services.paletteOwnershipRegistryOrNull()).thenReturn(registry);
        List<PaletteWrite> writes = new ArrayList<>();
        doAnswer(call -> { writes.add(call.getArgument(0)); return null; }).when(registry).submit(any());
        return new Fixture(services, registry, writes);
    }

    @Test void attackWritesFortyRowsEightPassesApartThenCallsBackWithoutWriting() throws Exception {
        var f = fixture(); var state = new DezMinibossPaletteState(); state.start(f.services);
        int[][] colors = {{0x66A,0x448,0x226},{0x66C,0x44A,0x228},
                {0x66A,0x448,0x226},{0x666,0x444,0x222}};
        for (int pass = 0; pass < 320; pass++) {
            assertFalse(state.tick(f.services), "early callback at " + pass);
            assertEquals(pass / 8 + 1, f.writes.size());
            var write = f.writes.getLast();
            assertEquals(1, write.lineIndex()); assertEquals(11, write.startColor());
            assertArrayEquals(bytes(colors[(pass / 8) % 4]), write.segaData());
        }
        assertTrue(state.tick(f.services)); assertEquals(40, f.writes.size());
        assertFalse(state.tick(f.services), "callback is a one-shot transition");
    }

    @Test void paletteDisableFreezesDelayAndCursorAndRestartDiscardsOldProgress() throws Exception {
        var f = fixture(); var state = new DezMinibossPaletteState(); state.start(f.services);
        for (int i = 0; i < 13; i++) state.tick(f.services);
        var before = state.captureRewindStateValue();
        when(f.registry.isPaletteRotationDisabled()).thenReturn(true);
        for (int i = 0; i < 64; i++) assertFalse(state.tick(f.services));
        assertEquals(before, state.captureRewindStateValue());
        when(f.registry.isPaletteRotationDisabled()).thenReturn(false);
        state.start(f.services); f.writes.clear();
        assertFalse(state.tick(f.services));
        assertArrayEquals(bytes(0x66A,0x448,0x226), f.writes.getFirst().segaData());
    }

    @Test void restoreReplaysRemainingRowsAndCallbackAtSamePass() throws Exception {
        var f = fixture(); var state = new DezMinibossPaletteState(); state.start(f.services);
        for (int i = 0; i < 157; i++) state.tick(f.services);
        var saved = state.captureRewindStateValue();
        var expected = remaining(state, f);
        var recreated = new DezMinibossPaletteState(); recreated.restoreRewindStateValue(saved);
        assertEquals(expected, remaining(recreated, f));
    }

    @Test void eyeFlashWritesFourColorsAndEndsOnTheNormalQuartet() throws Exception {
        var f = fixture();
        for (int counter = 32; counter > 0; counter--) {
            DezMinibossPaletteState.flash(f.services, counter);
            var write = f.writes.getLast();
            assertEquals(1, write.lineIndex()); assertEquals(11, write.startColor());
            assertArrayEquals((counter & 1) == 0 ? bytes(0x888,0xAAA,0xCCC,0xEEE)
                    : bytes(0x666,0x444,0x222,0), write.segaData());
        }
    }

    private List<String> remaining(DezMinibossPaletteState state, Fixture f) {
        f.writes.clear(); List<String> result = new ArrayList<>();
        for (int pass = 0; pass < 200; pass++) {
            int oldSize = f.writes.size();
            if (state.tick(f.services)) { result.add("callback:" + pass); return result; }
            if (f.writes.size() != oldSize) result.add(pass + ":" + Arrays.toString(f.writes.getLast().segaData()));
        }
        fail("missing callback"); return result;
    }

    private byte[] bytes(int... words) {
        byte[] result = new byte[words.length * 2];
        for (int i = 0; i < words.length; i++) { result[i*2] = (byte)(words[i] >> 8); result[i*2+1] = (byte)words[i]; }
        return result;
    }
}
