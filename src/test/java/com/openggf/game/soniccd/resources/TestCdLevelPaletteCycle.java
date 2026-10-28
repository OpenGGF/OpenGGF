package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.level.CdAssetBytes;
import org.junit.jupiter.api.Test;

import java.nio.ReadOnlyBufferException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.openggf.game.soniccd.resources.CdLevelPaletteCycle.*;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit constructed component inputs; none establishes native cold-ingress palette/timer knowledge. */
class TestCdLevelPaletteCycle {
    private static final int[] DESTINATIONS = {0x31, 0x32, 0x33, 0x2C, 0x2D, 0x2F};

    @Test
    void actualSixScriptLiteralWritesOnlyItsSixNormalWordsAndRetainsEveryOtherByte() {
        var source = state(new int[6], new int[6]);
        byte[] expected = source.palette().copy();
        int[] colours = {0xCC6, 0xEEE, 0xEEA, 0xEA8, 0xE86, 0xECA};
        for (int i = 0; i < 6; i++) word(expected, DESTINATIONS[i] * 2, colours[i]);
        var result = pass(nativeScripts(), source);
        assertTrue(result.completed()); assertTrue(result.fault().isEmpty());
        assertArrayEquals(expected, result.state().palette().copy());
        assertArrayEquals(bytes(8, 8, 8, 6, 6, 6), result.state().timers().copy());
        assertArrayEquals(bytes(1, 1, 1, 1, 1, 1), result.state().steps().copy());
        assertEquals(6, result.writes().size());
        for (int i = 0; i < 6; i++) {
            assertEquals(new Write(i, DESTINATIONS[i], 0x8000 | DESTINATIONS[i], colours[i]), result.writes().get(i));
        }
        // CycleColor's palette.w destination has no water consumer. No water value is fabricated or required.
        assertArrayEquals(palette(), source.palette().copy());
        assertArrayEquals(new byte[6], source.timers().copy()); assertArrayEquals(new byte[6], source.steps().copy());
    }

    @Test
    void heldTimersDoNotReadEvenAnEmptyScriptOrColourViewAndKeepRawStepBytes() {
        var source = state(new int[]{1, 2, 0x7F, 0x80, 4, 0x80}, new int[]{0, 0xFF, 0x80, 0x7F, 0xFE, 1});
        var result = pass(repeat(script(new int[0], new int[0])), source);
        assertTrue(result.completed()); assertTrue(result.writes().isEmpty());
        assertArrayEquals(bytes(0, 1, 0x7E, 0x7F, 3, 0x7F), result.state().timers().copy());
        assertArrayEquals(source.steps().copy(), result.state().steps().copy());
        assertArrayEquals(source.palette().copy(), result.state().palette().copy());
    }

    @Test
    void decrementIsByteArithmeticAndBplUsesTheResultingSignedByte() {
        for (int timer : new int[]{0, 0x81, 0xFF}) {
            var result = pass(nativeScripts(), state(new int[]{timer, 1, 1, 1, 1, 1}, new int[6]));
            assertTrue(result.completed()); assertEquals(1, result.writes().size());
            assertEquals(8, result.state().timers().unsignedByte(0));
            assertEquals(1, result.state().steps().unsignedByte(0));
        }
        var wrappedPositive = pass(nativeScripts(), state(new int[]{0x80, 1, 1, 1, 1, 1}, new int[6]));
        assertTrue(wrappedPositive.writes().isEmpty());
        assertEquals(0x7F, wrappedPositive.state().timers().unsignedByte(0));
    }

    @Test
    void incrementWrapsAsAByteThenComparesUnsignedWithTheFrameCount() {
        int[] before = {0, 1, 2, 0x7F, 0x80, 0xFE, 0xFF};
        int[] after = {1, 2, 0, 0, 0, 0, 0};
        int[] colours = {0xCC6, 0xEEA, 0xEEE, 0xEEE, 0xEEE, 0xEEE, 0xEEE};
        for (int i = 0; i < before.length; i++) {
            var result = pass(nativeScripts(), state(new int[]{0, 1, 1, 1, 1, 1}, new int[]{before[i], 0, 0, 0, 0, 0}));
            assertEquals(after[i], result.state().steps().unsignedByte(0));
            assertEquals(colours[i], result.state().palette().unsignedWord(0x62));
        }
    }

    @Test
    void eightAndSixDelaysHoldForTheirEntireCountdownBeforeTheNextUnderflow() {
        State current = state(new int[6], new int[6]);
        for (int call = 1; call <= 28; call++) {
            var result = pass(nativeScripts(), current); assertTrue(result.completed()); current = result.state();
            if (call == 1) assertArrayEquals(bytes(1, 1, 1, 1, 1, 1), current.steps().copy());
            if (call == 7) {
                assertArrayEquals(bytes(2, 2, 2, 0, 0, 0), current.timers().copy());
                assertArrayEquals(bytes(1, 1, 1, 1, 1, 1), current.steps().copy());
            }
            if (call == 8) assertArrayEquals(bytes(1, 1, 1, 2, 2, 2), current.steps().copy());
            if (call == 10) assertArrayEquals(bytes(2, 2, 2, 2, 2, 2), current.steps().copy());
            if (call == 19) assertArrayEquals(bytes(0, 0, 0, 0, 0, 0), current.steps().copy());
        }
        assertArrayEquals(bytes(1, 1, 1, 1, 1, 1), current.steps().copy());
        assertArrayEquals(bytes(8, 8, 8, 0, 0, 0), current.timers().copy());
    }

    @Test
    void sixScriptOrderIncludesOverlappingAndUnchangedColourStores() {
        List<Script> scripts = new ArrayList<>();
        for (int i = 0; i < 6; i++) scripts.add(script(new int[]{5, 1, i, 0}, new int[]{0xB000 + i}));
        var result = pass(scripts, state(new int[6], new int[6]));
        assertTrue(result.completed()); assertEquals(0xB005, result.state().palette().unsignedWord(10));
        for (int i = 0; i < 6; i++) {
            assertEquals(new Write(i, 5, i == 0 ? 0x8005 : 0xAFFF + i, 0xB000 + i), result.writes().get(i));
        }
        var unchanged = pass(replace(nativeScripts(), 0, script(new int[]{5, 1, 0xFF, 0}, new int[]{0x8005})),
                state(new int[]{0, 1, 1, 1, 1, 1}, new int[6]));
        assertEquals(List.of(new Write(0, 5, 0x8005, 0x8005)), unchanged.writes());
        assertEquals(0xFF, unchanged.state().timers().unsignedByte(0));
    }

    @Test
    void faultCutsRetainTheCompletedPrefixAndExactlyTheReachedLocalStores() {
        record Case(Script script, Access access, int offset, int bytes, int step, int timer) { }
        List<Case> cases = List.of(
                new Case(script(new int[0], new int[0]), Access.SCRIPT, 0, 1, 0, 0xFF),
                new Case(script(new int[]{0x33}, new int[0]), Access.SCRIPT, 1, 1, 0, 0xFF),
                new Case(script(new int[]{0x33, 3}, new int[0]), Access.SCRIPT, 4, 1, 1, 0xFF),
                new Case(script(new int[]{0x33, 3, 8, 0, 0x81}, new int[0]), Access.SCRIPT, 5, 1, 1, 0x81),
                new Case(script(new int[]{0x33, 3, 8, 0, 8, 3}, new int[]{1, 2, 3}), Access.COLOURS, 6, 2, 1, 8),
                new Case(script(new int[]{64, 3, 8, 0, 8, 0}, new int[]{0xFFFF}), Access.NORMAL_PALETTE, 128, 2, 1, 8));
        for (var value : cases) {
            var source = state(new int[6], new int[6]);
            var result = pass(replace(nativeScripts(), 2, value.script()), source);
            assertFalse(result.completed());
            assertEquals(new Fault(2, value.access(), value.offset(), value.bytes()), result.fault().orElseThrow());
            assertEquals(2, result.writes().size());
            assertEquals(0xCC6, result.state().palette().unsignedWord(0x62));
            assertEquals(0xEEE, result.state().palette().unsignedWord(0x64));
            byte[] expected = source.palette().copy(); word(expected, 0x62, 0xCC6); word(expected, 0x64, 0xEEE);
            assertArrayEquals(expected, result.state().palette().copy());
            assertArrayEquals(bytes(8, 8, value.timer(), 0, 0, 0), result.state().timers().copy());
            assertArrayEquals(bytes(1, 1, value.step(), 0, 0, 0), result.state().steps().copy());
            assertEquals(result, pass(replace(nativeScripts(), 2, value.script()), source));
        }
    }

    @Test
    void destinationBoundsAreNotCheckedBeforeTheReachedSourceReads() {
        var source = state(new int[]{0, 1, 1, 1, 1, 1}, new int[6]);
        var shortScript = script(new int[]{0xFF, 3}, new int[0]);
        assertEquals(new Fault(0, Access.SCRIPT, 4, 1), pass(replace(nativeScripts(), 0, shortScript), source).fault().orElseThrow());
        var shortColours = script(new int[]{0xFF, 1, 7, 0}, new int[0]);
        assertEquals(new Fault(0, Access.COLOURS, 0, 2), pass(replace(nativeScripts(), 0, shortColours), source).fault().orElseThrow());
        var availableColours = script(new int[]{0xFF, 1, 7, 0}, new int[]{0x1234});
        assertEquals(new Fault(0, Access.NORMAL_PALETTE, 510, 2),
                pass(replace(nativeScripts(), 0, availableColours), source).fault().orElseThrow());
    }

    @Test
    void colourIndexExtendsItsSignedByteBeforeWordDoublingWithoutClamping() {
        for (int[] witness : new int[][]{{0x80, -256}, {0xFF, -2}, {0x7F, 254}}) {
            var script = script(new int[]{0x31, 1, 9, witness[0]}, new int[]{0xEEE});
            var result = pass(replace(nativeScripts(), 0, script), state(new int[]{0, 1, 1, 1, 1, 1}, new int[6]));
            assertEquals(new Fault(0, Access.COLOURS, witness[1], 2), result.fault().orElseThrow());
            assertEquals(9, result.state().timers().unsignedByte(0));
            assertEquals(0, result.state().steps().unsignedByte(0));
        }
    }

    @Test
    void frameOffsetDoublesTheZeroExtendedStepWordAndZeroFrameCountStillSelectsFrameZero() {
        byte[] script = new byte[512]; script[0] = 1; script[1] = (byte) 255;
        script[258] = (byte) 0xFE; script[259] = 0;
        var highStep = new Script(CdAssetBytes.copyOf(script), CdAssetBytes.copyOf(bytes(0xAB, 0xCD)));
        var result = pass(replace(nativeScripts(), 0, highStep), state(new int[]{0, 1, 1, 1, 1, 1}, new int[]{127, 0, 0, 0, 0, 0}));
        assertTrue(result.completed()); assertEquals(128, result.state().steps().unsignedByte(0));
        assertEquals(0xFE, result.state().timers().unsignedByte(0)); assertEquals(0xABCD, result.state().palette().unsignedWord(2));
        var zeroFrames = script(new int[]{1, 0, 0x80, 0}, new int[]{0xF135});
        var zero = pass(replace(nativeScripts(), 0, zeroFrames), state(new int[]{0, 1, 1, 1, 1, 1}, new int[]{0xFE, 0, 0, 0, 0, 0}));
        assertTrue(zero.completed()); assertEquals(0, zero.state().steps().unsignedByte(0));
        assertEquals(0x80, zero.state().timers().unsignedByte(0)); assertEquals(0xF135, zero.state().palette().unsignedWord(2));
    }

    @Test
    void oneShortColourWordFailsWithNoPaletteWriteAndNoLaterTimerDecrement() {
        var shortWord = new Script(CdAssetBytes.copyOf(bytes(1, 1, 3, 0)), CdAssetBytes.copyOf(bytes(0x12)));
        var result = pass(replace(nativeScripts(), 0, shortWord), state(new int[6], new int[6]));
        assertEquals(new Fault(0, Access.COLOURS, 0, 2), result.fault().orElseThrow());
        assertTrue(result.writes().isEmpty()); assertArrayEquals(palette(), result.state().palette().copy());
        assertArrayEquals(bytes(3, 0, 0, 0, 0, 0), result.state().timers().copy());
    }

    @Test
    void stateAndResultAreImmutableValueCutsAndPassReplayHasNoHiddenOwner() {
        byte[] palette = palette(), timers = new byte[6], steps = new byte[6];
        var source = new State(CdAssetBytes.copyOf(palette), CdAssetBytes.copyOf(timers), CdAssetBytes.copyOf(steps));
        var same = state(new int[6], new int[6]); assertEquals(source, same); assertEquals(source.hashCode(), same.hashCode());
        var first = pass(nativeScripts(), source); var replay = pass(nativeScripts(), same);
        assertEquals(first, replay); assertEquals(first.hashCode(), replay.hashCode());
        Arrays.fill(palette, (byte) 1); Arrays.fill(timers, (byte) 2); Arrays.fill(steps, (byte) 3);
        assertArrayEquals(palette(), source.palette().copy());
        byte[] copy = first.state().palette().copy(); Arrays.fill(copy, (byte) 4);
        assertEquals(0xCC6, first.state().palette().unsignedWord(0x62));
        assertThrows(ReadOnlyBufferException.class, () -> first.state().palette().buffer().put((byte) 1));
        assertThrows(UnsupportedOperationException.class, () -> first.writes().clear());
        assertEquals(first, pass(nativeScripts(), source));
        assertNotEquals(source, first.state());
    }

    @Test
    void malformedPublicInputShapesFailWithoutInventingMissingStateOrScripts() {
        for (int n : new int[]{0, 127, 129}) {
            assertThrows(IllegalArgumentException.class, () -> new State(CdAssetBytes.copyOf(new byte[n]), CdAssetBytes.copyOf(new byte[6]), CdAssetBytes.copyOf(new byte[6])));
        }
        for (int n : new int[]{0, 5, 7}) {
            assertThrows(IllegalArgumentException.class, () -> new State(CdAssetBytes.copyOf(palette()), CdAssetBytes.copyOf(new byte[n]), CdAssetBytes.copyOf(new byte[6])));
            assertThrows(IllegalArgumentException.class, () -> new State(CdAssetBytes.copyOf(palette()), CdAssetBytes.copyOf(new byte[6]), CdAssetBytes.copyOf(new byte[n])));
        }
        var source = state(new int[6], new int[6]);
        assertThrows(NullPointerException.class, () -> new State(null, source.timers(), source.steps()));
        assertThrows(NullPointerException.class, () -> new Script(null, source.palette()));
        assertThrows(NullPointerException.class, () -> new Script(source.palette(), null));
        assertThrows(NullPointerException.class, () -> pass(null, source));
        assertThrows(NullPointerException.class, () -> pass(nativeScripts(), null));
        assertThrows(IllegalArgumentException.class, () -> pass(List.of(), source));
        assertThrows(IllegalArgumentException.class, () -> pass(nativeScripts().subList(0, 5), source));
        var nullScript = new ArrayList<>(nativeScripts()); nullScript.set(5, null);
        assertThrows(NullPointerException.class, () -> pass(nullScript, source));
    }

    private static State state(int[] timers, int[] steps) {
        return new State(CdAssetBytes.copyOf(palette()), CdAssetBytes.copyOf(bytes(timers)), CdAssetBytes.copyOf(bytes(steps)));
    }
    private static byte[] palette() {
        byte[] bytes = new byte[128]; for (int i = 0; i < 64; i++) word(bytes, i * 2, 0x8000 | i); return bytes;
    }
    private static List<Script> nativeScripts() {
        // Independent expansion of pinned N's six final dc.b/dc.w scripts (Present:120-171), not prototype data.
        return List.of(script(new int[]{0x31, 3, 8, 0, 8, 1, 8, 2}, new int[]{0xEEE, 0xCC6, 0xEEA}),
                script(new int[]{0x32, 3, 8, 0, 8, 1, 8, 2}, new int[]{0xEEA, 0xEEE, 0xCC6}),
                script(new int[]{0x33, 3, 8, 0, 8, 1, 8, 2}, new int[]{0xCC6, 0xEEA, 0xEEE}),
                script(new int[]{0x2C, 3, 6, 0, 6, 1, 6, 2}, new int[]{0xECA, 0xEA8, 0xE86}),
                script(new int[]{0x2D, 3, 6, 0, 6, 1, 6, 2}, new int[]{0xEA8, 0xE86, 0xECA}),
                script(new int[]{0x2F, 3, 6, 0, 6, 1, 6, 2}, new int[]{0xC86, 0xECA, 0xEA8}));
    }
    private static Script script(int[] script, int[] colours) {
        byte[] words = new byte[colours.length * 2]; for (int i = 0; i < colours.length; i++) word(words, i * 2, colours[i]);
        return new Script(CdAssetBytes.copyOf(bytes(script)), CdAssetBytes.copyOf(words));
    }
    private static List<Script> repeat(Script source) { return List.of(source, source, source, source, source, source); }
    private static List<Script> replace(List<Script> source, int cycle, Script replacement) {
        var result = new ArrayList<>(source); result.set(cycle, replacement); return result;
    }
    private static byte[] bytes(int... values) {
        byte[] bytes = new byte[values.length]; for (int i = 0; i < values.length; i++) bytes[i] = (byte) values[i]; return bytes;
    }
    private static void word(byte[] bytes, int offset, int value) { bytes[offset] = (byte) (value >>> 8); bytes[offset + 1] = (byte) value; }
}
