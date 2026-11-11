package com.openggf.game.sonic2;

import com.openggf.game.GameServices;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Level;
import com.openggf.level.Palette;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@RequiresRom(SonicGame.SONIC_2)
class TestSonic2CompetitionPaletteCycles {
    @ParameterizedTest @ValueSource(ints = {0, 11, 12})
    void paletteOnlyOwnerMatchesOrdinaryCyclesAndReplaysAfterRestore(int zone) throws Exception {
        GraphicsManager.getInstance().initHeadless();
        var first = level(zone);
        var second = level(zone);
        var ordinary = new Sonic2PaletteCycler(TestEnvironment.currentRom(), first, zone);
        var nativeOwner = Sonic2PaletteCycleInternalAccess.create(TestEnvironment.currentRom(), second, zone);
        assertNotEquals("sonic2.competition.animated-art", nativeOwner.key());
        byte[] initial = nativeOwner.capture().bytes();
        for (int tick = 0; tick < 120; tick++) {
            beginFrame(); ordinary.update();
            byte[] expected = colors(first);
            beginFrame(); nativeOwner.update();
            assertArrayEquals(ordinary.captureCyclerState(), nativeOwner.capture().bytes(), "state tick " + tick);
            assertArrayEquals(expected, colors(second), "palette tick " + tick);
        }
        assertFalse(java.util.Arrays.equals(initial, nativeOwner.capture().bytes()), "palette counters actually advanced");
        var held = nativeOwner.capture();
        var expectedStates = new java.util.ArrayList<byte[]>();
        for (int tick = 0; tick < 120; tick++) {
            beginFrame(); nativeOwner.update(); expectedStates.add(nativeOwner.capture().bytes());
        }
        nativeOwner.restore(held);
        assertArrayEquals(held.bytes(), nativeOwner.capture().bytes());
        for (int tick = 0; tick < 120; tick++) {
            beginFrame(); nativeOwner.update();
            assertArrayEquals(expectedStates.get(tick), nativeOwner.capture().bytes(), "replay tick " + tick);
        }
        byte[] copy = held.bytes(); java.util.Arrays.fill(copy, (byte) 0);
        assertFalse(java.util.Arrays.equals(copy, held.bytes()), "snapshot owns its captured bytes");
    }
    private static void beginFrame() {
        var registry = GameServices.paletteOwnershipRegistryOrNull();
        if (registry != null) registry.beginFrame();
    }
    private static Level level(int zone) {
        var level = mock(Level.class);
        when(level.getZoneIndex()).thenReturn(zone);
        when(level.getPaletteCount()).thenReturn(4);
        for (int i = 0; i < 4; i++) when(level.getPalette(i)).thenReturn(new Palette());
        return level;
    }
    private static byte[] colors(Level level) {
        byte[] bytes = new byte[192]; int offset = 0;
        for (int p = 0; p < 4; p++) for (int c = 0; c < 16; c++) {
            var color = level.getPalette(p).getColor(c);
            bytes[offset++] = color.r; bytes[offset++] = color.g; bytes[offset++] = color.b;
        }
        return bytes;
    }
}
