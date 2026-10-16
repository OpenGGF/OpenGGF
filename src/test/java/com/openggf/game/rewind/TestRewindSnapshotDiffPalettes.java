package com.openggf.game.rewind;

import com.openggf.level.Palette;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/** Complete raw RGB leaf contract, prompted by the 2026-10-14 HPZ deep-copy witness. */
class TestRewindSnapshotDiffPalettes {
    private record PaletteState(Palette[] palettes, int phase) {}

    @Test
    void deepCopiedPalettesRetainAllRawBytesAcrossSnapshotNesting() {
        Palette original = palette();
        Palette copied = original.deepCopy();
        assertNotSame(original, copied);
        for (int i = 0; i < Palette.PALETTE_SIZE; i++) assertNotSame(original.colors[i], copied.colors[i]);
        assertTrue(original.dataEquals(copied));
        assertTrue(RewindSnapshotDiff.keyEquals("palette", original, copied));
        assertTrue(RewindSnapshotDiff.diffKey("palette", original, copied).isEmpty());
        var a = new PaletteState(new Palette[]{original, original.deepCopy(), null}, 7);
        var b = new PaletteState(new Palette[]{copied, original.deepCopy(), null}, 7);
        assertTrue(RewindSnapshotDiff.keyEquals("native-state", a, b));
        assertTrue(RewindSnapshotDiff.diffKey("native-state", a, b).isEmpty());
    }

    @ParameterizedTest(name = "raw RGB component {0}")
    @MethodSource("components")
    void everyRawColorComponentRemainsComparedAndDiagnosed(int component) {
        Palette original = palette();
        Palette changed = original.deepCopy();
        int index = component / 3;
        String channel = switch (component % 3) { case 0 -> "r"; case 1 -> "g"; default -> "b"; };
        // Change the low bit too: comparison must not normalize to Genesis 3-bit colors.
        switch (component % 3) {
            case 0 -> changed.colors[index].r ^= 1;
            case 1 -> changed.colors[index].g ^= 1;
            default -> changed.colors[index].b ^= 1;
        }
        assertFalse(original.dataEquals(changed));
        assertFalse(RewindSnapshotDiff.keyEquals("palette", original, changed));
        var differences = RewindSnapshotDiff.diffKey("palette", original, changed);
        assertEquals(1, differences.size());
        assertTrue(differences.getFirst().contains("colors[" + index + "]." + channel), differences.toString());
        var a = new PaletteState(new Palette[]{original}, 7);
        var b = new PaletteState(new Palette[]{changed}, 7);
        assertFalse(RewindSnapshotDiff.keyEquals("native-state", a, b));
        assertTrue(RewindSnapshotDiff.diffKey("native-state", a, b).getFirst()
                .contains("palettes[0].colors[" + index + "]." + channel));
    }

    @Test
    void missingPaletteArrayLengthAndOtherSnapshotFieldsRemainRequired() {
        var original = palette();
        assertFalse(RewindSnapshotDiff.keyEquals("native-state",
                new PaletteState(new Palette[]{original}, 7), new PaletteState(new Palette[]{null}, 7)));
        assertFalse(RewindSnapshotDiff.diffKey("native-state",
                new PaletteState(new Palette[]{original}, 7), new PaletteState(new Palette[0], 7)).isEmpty());
        assertFalse(RewindSnapshotDiff.diffKey("native-state",
                new PaletteState(new Palette[]{original}, 7), new PaletteState(new Palette[]{original}, 8)).isEmpty());
    }

    private static IntStream components() { return IntStream.range(0, Palette.PALETTE_SIZE * 3); }

    private static Palette palette() {
        Palette palette = new Palette();
        for (int i = 0; i < Palette.PALETTE_SIZE; i++) {
            palette.colors[i].r = (byte) (17 * i + 1);
            palette.colors[i].g = (byte) (31 * i + 2);
            palette.colors[i].b = (byte) (47 * i + 3);
        }
        return palette;
    }
}
