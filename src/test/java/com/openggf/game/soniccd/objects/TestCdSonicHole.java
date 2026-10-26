package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdObjectPlacements;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Independently authored instruction/branch vectors, frozen before production exists.
 * N=1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * Palmtree Panic/Objects/Tunnel Path/Main.asm:11-103; Object Spawner.asm:385-446;
 * Object Functions.asm:137-196,228-237. All ports here are constructed test ports,
 * never native invocation/Frame/World authority. All Java was UNRUN at authoring.
 *
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
class TestCdSonicHole {
    private static final int WHOLE = 46;
    private static final int MAP = 0x00208AD4;
    private static final byte[] MAPS = HexFormat.of().parseHex("000203f00d0000f000040008f8080c000af0");

    @Test void initClearOldDisplayFallsThroughToControlWithoutDraw() {
        var h = new H();
        mark(h.p, 6, 0xA5);
        var before = h.p.capture();
        byte[] expected = initOracle(h.slot(), MAP, 0x03A0);
        h.reader = (field, cut, source) -> {
            assertEquals(CdSonicHole.PlayerField.CONTROL_2C, field);
            assertArrayEquals(expected, source.copyBytes());
            assertEquals(0xA5, h.bank(6));
            return 0;
        };
        var result = h.body.run(WHOLE, world());
        assertArrayEquals(expected, result.state().copyBytes());
        assertEquals(List.of("CONTROL_2C"), h.calls);
        assertEquals(List.of(
                new CdSonicHole.RoutineEntered(CdSonicHole.NativeRoutine.INIT),
                new CdSonicHole.SavedBitWrite(6, 6, false, true),
                new CdSonicHole.SavedBitWrite(6, 6, true, false),
                new CdSonicHole.RoutineEntered(CdSonicHole.NativeRoutine.MAIN),
                new CdSonicHole.PlayerRead(CdSonicHole.PlayerField.CONTROL_2C, 0),
                new CdSonicHole.DespawnChecked(false)), result.effects());
        assertFalse(result.deleted());
        assertOtherSlots(before.pool(), h.p.pool().capture());
        assertArrayEquals(before.savedFlags().copy(), h.p.capture().savedFlags().copy());
        assertEquals(before.status(), h.p.status());
    }

    @Test void rawNegativeTimeZoneUsesNonPastTileAndInitClearBeforeControl() {
        for (int raw : new int[]{0x80, 0x81}) {
            var h = new H();
            byte[] expected = initOracle(h.slot(), MAP, 0x03A0);
            var before = h.p.capture();
            var stop = new AssertionError("stop after exact Init clear");
            h.reader = (field, cut, source) -> {
                assertEquals(CdSonicHole.PlayerField.CONTROL_2C, field);
                assertArrayEquals(expected, source.copyBytes());
                assertArrayEquals(before.savedFlags().copy(), h.p.capture().savedFlags().copy());
                throw stop;
            };
            assertSame(stop, assertThrows(AssertionError.class, () -> h.body.run(WHOLE, g(raw, 0, 0x1080))));
            assertArrayEquals(expected, h.slot().copyBytes());
            assertEquals(List.of("CONTROL_2C"), h.calls);
        }
    }

    @Test void initSetOldDisplayPublishesRoutine4AndDrawsWithoutPlayerReadOrFm() {
        var h = new H();
        mark(h.p, 6, 0xC5);
        byte[] expected = initOracle(h.slot(), MAP, 0x03A0);
        expected[0x24] = 4;
        h.reader = (f, c, s) -> { throw new AssertionError("old display bypasses player"); };
        h.fm = (f, c, s) -> { throw new AssertionError("old display bypasses FM"); };
        var result = h.body.run(WHOLE, world());
        assertArrayEquals(expected, h.drawBefore.copyBytes());
        assertArrayEquals(expected, result.state().copyBytes());
        assertEquals(List.of("DRAW"), h.calls);
        assertEquals(List.of(
                new CdSonicHole.RoutineEntered(CdSonicHole.NativeRoutine.INIT),
                new CdSonicHole.SavedBitWrite(6, 6, true, true),
                new CdSonicHole.RoutineEntered(CdSonicHole.NativeRoutine.DISPLAY),
                new CdSonicHole.CallReturned(CdSonicHole.NativeCall.DRAW_OBJECT),
                new CdSonicHole.DespawnChecked(false)), result.effects());
        assertEquals(0xC5, h.bank(6));
    }

    @Test void dirtyInitRetainsAll64BytesOutsideExactNativeStores() {
        for (int raw : new int[]{0, 1, 0x7F}) {
            var h = new H(new CdSonicHole.Assets(visual(), 0xF1234567));
            h.write(dirty(h.slot()).moveByte(ID, 0x31).moveByte(ROUTINE, 0)
                    .moveByte(SAVED_FLAGS_ID, 1).moveByte(SPRITE_FLAGS, 0xA3));
            var before = h.p.capture();
            byte[] expected = initOracle(h.slot(), 0xF1234567, raw == 0 ? 0x03BB : 0x03A0);
            var result = h.body.run(WHOLE, g(raw, 0, 0x1080));
            assertArrayEquals(expected, result.state().copyBytes());
            assertEquals(before.status(), h.p.status());
            assertArrayEquals(before.savedFlags().copy(), h.p.capture().savedFlags().copy());
            assertOtherSlots(before.pool(), h.p.pool().capture());
            assertEquals(List.of("CONTROL_2C"), h.calls);
        }
    }

    @Test void everyRawSavedAndTimeZonePairSelectsExactSetDisplayAddressOrStops() {
        var h = new H();
        h.edit(s -> s.moveByte(ROUTINE, 2));
        h.control = 1;
        var baseline = h.p.capture();
        var stopped = new IllegalArgumentException("constructed FM stop");
        int valid = 0, invalid = 0, last = 0, firstBad = 0;
        for (int saved = 0; saved < 256; saved++) {
            for (int period = 0; period < 256; period++) {
                h.p.restore(baseline);
                h.resetCalls();
                final int savedValue = saved;
                h.edit(s -> s.moveByte(SAVED_FLAGS_ID, savedValue));
                final int index = 2 + 3 * saved + period; // independent scalar oracle
                var before = h.p.capture();
                h.fm = (sound, cut, source) -> {
                    assertSame(CdSonicHole.FmSound.FM_A3, sound);
                    assertEquals(0xA3, sound.nativeId());
                    assertEquals(2, source.routine());
                    assertEquals(0x40, h.bank(index));
                    throw stopped;
                };
                final int raw = period;
                var failure = assertThrows(RuntimeException.class, () -> h.body.run(WHOLE, g(raw, 0, 0x1080)));
                if (index < 766) {
                    valid++;
                    assertSame(stopped, failure);
                    assertEquals(0, stopped.getSuppressed().length);
                    byte[] bank = before.savedFlags().copy();
                    bank[index] = 0x40;
                    assertArrayEquals(bank, h.p.capture().savedFlags().copy());
                    assertEquals(List.of("CONTROL_2C", "X_WORD", "Y_WORD", "FM"), h.calls);
                } else {
                    invalid++;
                    var boundary = assertInstanceOf(CdSonicHole.UnsupportedSonicHole.class, failure);
                    assertEquals(CdSonicHole.UnsupportedBoundary.SAVED_FLAGS_ADDRESS, boundary.boundary());
                    assertEquals(index, boundary.value());
                    assertEquals(WHOLE, boundary.wholeIndex());
                    assertArrayEquals(before.savedFlags().copy(), h.p.capture().savedFlags().copy());
                    assertEquals(List.of("CONTROL_2C", "X_WORD", "Y_WORD"), h.calls);
                }
                assertEquals(before.status(), h.p.status());
                assertEquals(before.pool().slots(), h.p.pool().capture().slots());
                if (index == 765) last++;
                if (index == 766) firstBad++;
            }
        }
        assertEquals(54400, valid);
        assertEquals(11136, invalid);
        assertEquals(85, last);
        assertEquals(85, firstBad);
    }

    @Test void everySavedAndTimeZonePairUsesExactTailAddressOrSavedZeroBypass() {
        var h = new H();
        h.edit(s -> s.moveByte(ROUTINE, 4).moveByte(SPRITE_FLAGS, 0x84));
        fillBody(h.p, 0xFF);
        var baseline = h.p.capture();
        int cases = 0;
        for (int saved = 0; saved < 256; saved++) {
            for (int period = 0; period < 256; period++) {
                h.p.restore(baseline);
                h.resetCalls();
                final int savedValue = saved;
                h.edit(s -> s.moveByte(SAVED_FLAGS_ID, savedValue));
                var before = h.p.capture();
                int selected = period < 128 ? period : Math.min(period - 128, 2);
                int index = 2 + 3 * saved + selected;
                final int raw = period;
                if (saved != 0 && index >= 766) {
                    var failure = assertThrows(CdSonicHole.UnsupportedSonicHole.class,
                            () -> h.body.run(WHOLE, g(raw, 0, 0)));
                    assertEquals(CdSonicHole.UnsupportedBoundary.SAVED_FLAGS_ADDRESS, failure.boundary());
                    assertEquals(index, failure.value());
                    byte[] expected = before.pool().slots().get(WHOLE - 32).copyBytes();
                    expected[1] &= 0x7F;
                    assertArrayEquals(expected, h.slot().copyBytes());
                    assertArrayEquals(before.savedFlags().copy(), h.p.capture().savedFlags().copy());
                } else {
                    var result = h.body.run(WHOLE, g(raw, 0, 0));
                    assertTrue(result.deleted());
                    assertArrayEquals(new byte[64], h.slot().copyBytes());
                    byte[] bank = before.savedFlags().copy();
                    if (saved != 0) bank[index] &= 0x7F;
                    assertArrayEquals(bank, h.p.capture().savedFlags().copy());
                }
                assertEquals(List.of("DRAW"), h.calls);
                assertOtherSlots(before.pool(), h.p.pool().capture());
                assertEquals(before.status(), h.p.status());
                cases++;
            }
        }
        assertEquals(65536, cases);
    }

    @Test void everyRawDirectionAndWarpPeriodUsesSignedSubtractAndNativeClamps() {
        var h = new H();
        h.edit(s -> s.moveByte(ROUTINE, 4));
        fillBody(h.p, 0xFF);
        var baseline = h.p.capture();
        int cases = 0;
        for (int period = 0; period < 128; period++) {
            for (int direction = 0; direction < 256; direction++) {
                h.p.restore(baseline);
                h.resetCalls();
                int signed = direction < 128 ? direction : direction - 256;
                int shifted = period - signed;
                int selected = shifted < 0 ? 0 : Math.min(shifted, 2);
                byte[] expected = baseline.savedFlags().copy();
                expected[5 + selected] &= 0x7F;
                var result = h.body.run(WHOLE, g(128 + period, direction, 0x1080));
                assertTrue(result.deleted());
                assertArrayEquals(expected, h.p.capture().savedFlags().copy());
                assertArrayEquals(new byte[64], h.slot().copyBytes());
                assertOtherSlots(baseline.pool(), h.p.pool().capture());
                cases++;
            }
        }
        assertEquals(32768, cases);
    }

    @Test void allSavedByteValuesPreserveOtherBitsAcrossSetClearAndLoadedClear() {
        var h = new H();
        var baseline = h.p.capture();
        for (int value = 0; value < 256; value++) {
            h.p.restore(baseline);
            h.resetCalls();
            mark(h.p, 6, value);
            var init = h.body.run(WHOLE, world());
            assertEquals(value, h.bank(6));
            assertEquals((value & 0x40) == 0 ? 2 : 4, init.state().routine());
            assertEquals((value & 0x40) == 0 ? List.of("CONTROL_2C") : List.of("DRAW"), h.calls);

            h.p.restore(baseline);
            h.resetCalls();
            mark(h.p, 6, value);
            h.edit(s -> s.moveByte(ROUTINE, 2));
            h.control = 1;
            var active = h.body.run(WHOLE, world());
            assertEquals(value | 0x40, h.bank(6));
            assertEquals(4, active.state().routine());
            assertEquals(List.of("CONTROL_2C", "X_WORD", "Y_WORD", "FM", "DRAW"), h.calls);
            h.control = 0;

            h.p.restore(baseline);
            h.resetCalls();
            mark(h.p, 6, value);
            h.edit(s -> s.moveByte(ROUTINE, 4));
            var deleted = h.body.run(WHOLE, g(1, 0, 0));
            assertTrue(deleted.deleted());
            assertEquals(value & 0x7F, h.bank(6));
        }
    }

    @Test void conditionalControlXAndYKeepAllRawControlValuesAndNativeWordEdges() {
        for (int control = 0; control < 256; control++) {
            var h = new H();
            h.edit(s -> s.moveByte(ROUTINE, 2));
            h.control = control;
            var result = h.body.run(WHOLE, world());
            assertEquals(control == 0 ? 2 : 4, result.state().routine());
            assertEquals(control == 0 ? List.of("CONTROL_2C")
                    : List.of("CONTROL_2C", "X_WORD", "Y_WORD", "FM", "DRAW"), h.calls);
        }
        int[] anchors = {0, 0x10, 0x7FFF, 0x8000, 0xFFF0, 0xFFFF};
        int[] deltas = {-32768, -33, -32, -1, 0, 31, 32, 32767};
        for (int anchor : anchors) {
            for (int delta : deltas) {
                for (boolean xAxis : new boolean[]{true, false}) {
                    var h = new H();
                    h.control = 1;
                    h.edit(s -> s.moveByte(ROUTINE, 2).moveWord(X, anchor).moveWord(Y, anchor));
                    h.x = xAxis ? (anchor + delta) & 65535 : anchor;
                    h.y = xAxis ? anchor : (anchor + delta) & 65535;
                    // Sprite sign bypasses unrelated horizontal tail deletion; no warp is active.
                    h.edit(s -> s.moveByte(SPRITE_FLAGS, 0x80));
                    h.draw = (pool, slot) -> pool.write(pool.state(slot.wholeIndex()).moveByte(SPRITE_FLAGS, 0x80));
                    var result = h.body.run(WHOLE, world());
                    boolean overlap = delta >= -32 && delta <= 31;
                    assertEquals(overlap ? 4 : 2, result.state().routine());
                    assertEquals(overlap ? List.of("CONTROL_2C", "X_WORD", "Y_WORD", "FM", "DRAW")
                            : xAxis ? List.of("CONTROL_2C", "X_WORD")
                            : List.of("CONTROL_2C", "X_WORD", "Y_WORD"), h.calls);
                    assertFalse(result.deleted());
                }
            }
        }
    }

    @Test void eachReadAndFmGetsFreshActualFull96AndExactSourceAfterNativeStores() {
        var h = new H();
        h.control = 1;
        var before = h.p.capture();
        var result = h.body.run(WHOLE, world());
        assertEquals(4, h.cutCount);
        assertEquals(4, result.state().routine());
        assertOtherSlots(before.pool(), h.p.pool().capture());
        assertEquals(List.of(
                new CdSonicHole.RoutineEntered(CdSonicHole.NativeRoutine.INIT),
                new CdSonicHole.SavedBitWrite(6, 6, false, true),
                new CdSonicHole.SavedBitWrite(6, 6, true, false),
                new CdSonicHole.RoutineEntered(CdSonicHole.NativeRoutine.MAIN),
                new CdSonicHole.PlayerRead(CdSonicHole.PlayerField.CONTROL_2C, 1),
                new CdSonicHole.PlayerRead(CdSonicHole.PlayerField.X_WORD, 0x1000),
                new CdSonicHole.PlayerRead(CdSonicHole.PlayerField.Y_WORD, 0x200),
                new CdSonicHole.SavedBitWrite(6, 6, false, true),
                new CdSonicHole.CallReturned(CdSonicHole.NativeCall.PLAY_FM_SOUND),
                new CdSonicHole.RoutineEntered(CdSonicHole.NativeRoutine.DISPLAY),
                new CdSonicHole.CallReturned(CdSonicHole.NativeCall.DRAW_OBJECT),
                new CdSonicHole.DespawnChecked(false)), result.effects());
    }

    @Test void readAndFmCheckEveryPlacementPartitionEvenWhenTheCallbackThrows() {
        for (int site = 0; site < 4; site++) {
            for (int kind = 0; kind < 6; kind++) {
                for (int failureKind = 0; failureKind < 3; failureKind++) {
                    var h = new H();
                    h.edit(s -> s.moveByte(ROUTINE, 2));
                    h.control = 1;
                    final int selectedSite = site, mutation = kind, thrown = failureKind;
                    RuntimeException runtime = new IllegalArgumentException("original callback");
                    Error error = new AssertionError("original callback Error");
                    Runnable bad = () -> {
                        mutate(h, mutation);
                        if (thrown == 1) throw runtime;
                        if (thrown == 2) throw error;
                    };
                    h.reader = (field, cut, source) -> {
                        if (field.ordinal() == selectedSite) bad.run();
                        return h.readValue(field, source);
                    };
                    h.fm = (sound, cut, source) -> { if (selectedSite == 3) bad.run(); };
                    Throwable got = assertThrows(Throwable.class, () -> h.body.run(WHOLE, world()));
                    if (failureKind == 0) {
                        assertInstanceOf(IllegalStateException.class, got);
                    } else {
                        assertSame(failureKind == 1 ? runtime : error, got);
                        assertTrue(Arrays.stream(got.getSuppressed()).anyMatch(t -> t instanceof IllegalStateException));
                    }
                    assertEquals(site + 1, h.calls.size());
                    assertFalse(h.calls.contains("DRAW"));
                    assertMutationRetained(h, mutation);
                }
            }
        }
    }

    @Test void originalReadAndFmThrowablesSurviveWithoutInventedVerificationFaults() {
        for (int site = 0; site < 4; site++) {
            for (boolean useError : new boolean[]{false, true}) {
                var h = new H();
                h.edit(s -> s.moveByte(ROUTINE, 2));
                h.control = 1;
                var before = h.p.capture();
                final int selected = site;
                RuntimeException runtime = new IllegalArgumentException("original");
                Error error = new AssertionError("original");
                Runnable stop = () -> { if (useError) throw error; else throw runtime; };
                h.reader = (field, cut, source) -> {
                    if (field.ordinal() == selected) stop.run();
                    return h.readValue(field, source);
                };
                h.fm = (sound, cut, source) -> { if (selected == 3) stop.run(); };
                Throwable got = assertThrows(Throwable.class, () -> h.body.run(WHOLE, world()));
                assertSame(useError ? error : runtime, got);
                assertEquals(0, got.getSuppressed().length);
                assertEquals(before.pool().slots(), h.p.pool().capture().slots());
                byte[] bank = before.savedFlags().copy();
                if (site == 3) bank[6] = 0x40;
                assertArrayEquals(bank, h.p.capture().savedFlags().copy());
                assertEquals(site + 1, h.calls.size());
            }
        }
    }

    @Test void invalidReadWidthsAreNotNormalizedAndMutationVerificationComesFirst() {
        for (var field : CdSonicHole.PlayerField.values()) {
            for (int bad : new int[]{-1, field == CdSonicHole.PlayerField.CONTROL_2C ? 256 : 65536}) {
                var h = new H();
                h.edit(s -> s.moveByte(ROUTINE, 2));
                h.control = 1;
                var before = h.p.capture();
                h.reader = (f, cut, source) -> f == field ? bad : h.readValue(f, source);
                assertThrows(IllegalArgumentException.class, () -> h.body.run(WHOLE, world()));
                samePlacement(h.p, before);
                assertEquals(field.ordinal() + 1, h.calls.size());
            }
        }
        var h = new H();
        h.reader = (field, cut, source) -> {
            h.p.bsetSavedFlag(1, 1, CdObjectPlacementRuntime.SavedBit.MEMBER_1);
            return -1;
        };
        assertThrows(IllegalStateException.class, () -> h.body.run(WHOLE, world()));
        assertEquals(1, h.bank(6));
        assertEquals(2, h.slot().routine());
    }

    @Test void caughtAndUncaughtReentryNeverDispatchNestedNativeWork() {
        for (int site = 0; site < 5; site++) {
            for (boolean caught : new boolean[]{false, true}) {
                var h = new H();
                h.edit(s -> s.moveByte(ROUTINE, 2));
                h.control = 1;
                final int selected = site;
                IllegalStateException[] observed = {null};
                Runnable nested = () -> {
                    try {
                        h.body.run(WHOLE, world());
                        fail("nested dispatch returned");
                    } catch (IllegalStateException failure) {
                        observed[0] = failure;
                        if (!caught) throw failure;
                    }
                };
                h.reader = (field, cut, source) -> {
                    if (field.ordinal() == selected) nested.run();
                    return h.readValue(field, source);
                };
                h.fm = (sound, cut, source) -> { if (selected == 3) nested.run(); };
                h.draw = (pool, slot) -> { if (selected == 4) nested.run(); };
                if (caught) {
                    assertEquals(4, h.body.run(WHOLE, world()).state().routine());
                    assertEquals(List.of("CONTROL_2C", "X_WORD", "Y_WORD", "FM", "DRAW"), h.calls);
                } else {
                    var got = assertThrows(IllegalStateException.class, () -> h.body.run(WHOLE, world()));
                    assertSame(observed[0], got);
                    assertEquals(site + 1, h.calls.size());
                    assertEquals(site == 4 ? 4 : 2, h.slot().routine());
                }
                assertNotNull(observed[0]);
            }
        }
    }

    @Test void drawMutationsAreAuthoritativeAndTailReloadsIdFlagsXAndSavedId() {
        var h = new H();
        h.edit(s -> s.moveByte(ROUTINE, 4));
        mark(h.p, 6, 0xC0);
        mark(h.p, 9, 0xF5);
        h.draw = (pool, slot) -> pool.write(pool.state(slot.wholeIndex()).moveByte(ID, 0x22)
                .moveByte(SPRITE_FLAGS, 0x80).moveWord(X, 0x9000).moveByte(SAVED_FLAGS_ID, 2)
                .moveByte(MAP_FRAME, 255));
        var result = h.body.run(WHOLE, g(0x81, 0, 0x1080));
        assertTrue(result.deleted());
        assertArrayEquals(new byte[64], h.slot().copyBytes());
        assertEquals(0xC0, h.bank(6));
        assertEquals(0x75, h.bank(9));
        assertEquals(List.of("DRAW"), h.calls);

        var kept = new H();
        kept.edit(s -> s.moveByte(ROUTINE, 4));
        kept.draw = (pool, slot) -> pool.write(pool.state(slot.wholeIndex())
                .moveByte(ID, 0).moveByte(SPRITE_FLAGS, 0x80).moveWord(X, 0x9000)
                .moveByte(SAVED_FLAGS_ID, 255).moveByte(MAP_FRAME, 255));
        var keptResult = kept.body.run(WHOLE, world());
        assertFalse(keptResult.deleted());
        assertEquals(0, keptResult.state().id());
        assertSame(kept.slot(), keptResult.state());
        assertEquals(255, keptResult.state().unsignedByte(MAP_FRAME));
    }

    @Test void drawThrowableKeepsItsMutationsAndPreventsAllTailWork() {
        for (boolean useError : new boolean[]{false, true}) {
            var h = new H();
            h.edit(s -> s.moveByte(ROUTINE, 4));
            mark(h.p, 6, 0xC0);
            RuntimeException runtime = new IllegalArgumentException("Draw");
            Error error = new AssertionError("Draw");
            h.draw = (pool, slot) -> {
                pool.write(pool.state(slot.wholeIndex()).moveWord(X, 0x9000).moveByte(MAP_FRAME, 255));
                h.p.bsetSavedFlag(2, 1, CdObjectPlacementRuntime.SavedBit.MEMBER_1);
                if (useError) throw error; else throw runtime;
            };
            Throwable got = assertThrows(Throwable.class, () -> h.body.run(WHOLE, g(0x81, 0, 0)));
            assertSame(useError ? error : runtime, got);
            assertEquals(0, got.getSuppressed().length);
            assertEquals(0x31, h.slot().id());
            assertEquals(0x9000, h.slot().centreX());
            assertEquals(255, h.slot().unsignedByte(MAP_FRAME));
            assertEquals(0xC0, h.bank(6));
            assertEquals(1, h.bank(9));
        }
    }

    @Test void sharedTailUsesChunkBoundsSignWarpAndSavedZeroBypass() {
        for (int x : new int[]{0x1280, 0x1300}) {
            var h = new H();
            h.edit(s -> s.moveByte(ROUTINE, 4).moveWord(X, x));
            assertEquals(x == 0x1300, h.body.run(WHOLE, world()).deleted());
        }
        var negative = new H();
        negative.edit(s -> s.moveByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, 0x80).moveWord(X, 0x9000));
        assertFalse(negative.body.run(WHOLE, world()).deleted());
        assertEquals(List.of("CONTROL_2C"), negative.calls);

        var wrapped = new H();
        wrapped.edit(s -> s.moveByte(ROUTINE, 4).moveWord(X, 0xFF80));
        assertFalse(wrapped.body.run(WHOLE, g(1, 0, 0)).deleted());

        var zero = new H();
        zero.edit(s -> s.moveByte(ROUTINE, 4).moveByte(SAVED_FLAGS_ID, 0));
        var before = zero.p.capture();
        assertTrue(zero.body.run(WHOLE, g(0xFF, 0x80, 0)).deleted());
        assertArrayEquals(before.savedFlags().copy(), zero.p.capture().savedFlags().copy());

        for (int saved : new int[]{254, 255}) {
            var h = new H();
            h.edit(s -> s.moveByte(ROUTINE, 4).moveByte(SAVED_FLAGS_ID, saved));
            mark(h.p, 765, 0xFF);
            if (saved == 254) {
                assertTrue(h.body.run(WHOLE, g(1, 0, 0)).deleted());
                assertEquals(0x7F, h.bank(765));
            } else {
                var failure = assertThrows(CdSonicHole.UnsupportedSonicHole.class,
                        () -> h.body.run(WHOLE, g(0, 0, 0)));
                assertEquals(767, failure.value());
                assertEquals(0xFF, h.bank(765));
                assertEquals(0x31, h.slot().id());
            }
        }
        var lastBad = new H();
        lastBad.edit(s -> s.moveByte(ROUTINE, 4).moveByte(SAVED_FLAGS_ID, 254));
        var bad = assertThrows(CdSonicHole.UnsupportedSonicHole.class,
                () -> lastBad.body.run(WHOLE, g(2, 0, 0)));
        assertEquals(766, bad.value());
        assertEquals(List.of("DRAW"), lastBad.calls);
    }

    @Test void reachedInitBankFaultKeepsOnlyExactOrderedInitPrefix() {
        var h = new H();
        h.write(dirty(h.slot()).moveByte(ID, 0x31).moveByte(ROUTINE, 0).moveByte(SAVED_FLAGS_ID, 255));
        var before = h.p.capture();
        byte[] expected = initOracle(h.slot(), MAP, 0x03A0);
        var failure = assertThrows(CdSonicHole.UnsupportedSonicHole.class,
                () -> h.body.run(WHOLE, g(255, 255, 0)));
        assertEquals(CdSonicHole.UnsupportedBoundary.SAVED_FLAGS_ADDRESS, failure.boundary());
        assertEquals(1022, failure.value());
        assertArrayEquals(expected, h.slot().copyBytes());
        assertArrayEquals(before.savedFlags().copy(), h.p.capture().savedFlags().copy());
        assertOtherSlots(before.pool(), h.p.pool().capture());
        assertTrue(h.calls.isEmpty());
    }

    @Test void everyUnownedRoutineStopsBeforeAnyWriteAndDirtyFrameIsNeverSelected() {
        var h = new H();
        var baseline = h.p.capture();
        for (int routine = 0; routine < 256; routine++) {
            if (routine == 0 || routine == 2 || routine == 4) continue;
            h.p.restore(baseline);
            final int raw = routine;
            h.edit(s -> s.moveByte(ROUTINE, raw));
            var before = h.p.capture();
            var failure = assertThrows(CdSonicHole.UnsupportedSonicHole.class,
                    () -> h.body.run(WHOLE, world()));
            assertEquals(CdSonicHole.UnsupportedBoundary.ROUTINE_DISPATCH, failure.boundary());
            assertEquals(raw, failure.value());
            samePlacement(h.p, before);
        }
        for (int frame : new int[]{1, 255}) {
            var dirty = new H();
            dirty.edit(s -> s.moveByte(MAP_FRAME, frame).moveByte(SPRITE_FLAGS, 0x20));
            mark(dirty.p, 6, 0x40);
            var result = dirty.body.run(WHOLE, world());
            assertEquals(frame, result.state().unsignedByte(MAP_FRAME));
            assertEquals(0x24, result.state().spriteFlags());
            assertEquals(1, visual().mappingFrames().size());
        }
    }

    @Test void transientGuardRetiresFinallyWithoutAWorldRetryClaim() {
        var h = new H();
        h.edit(s -> s.moveByte(ROUTINE, 2));
        var stopped = new AssertionError("read");
        h.reader = (f, c, s) -> { throw stopped; };
        assertSame(stopped, assertThrows(AssertionError.class, () -> h.body.run(WHOLE, world())));
        h.reader = (f, c, s) -> h.readValue(f, s);
        h.resetCalls();
        assertDoesNotThrow(() -> h.body.run(WHOLE, world()));
        h.edit(s -> s.moveByte(ROUTINE, 255));
        assertThrows(CdSonicHole.UnsupportedSonicHole.class, () -> h.body.run(WHOLE, world()));
        h.edit(s -> s.moveByte(ROUTINE, 2));
        assertDoesNotThrow(() -> h.body.run(WHOLE, world()));
        // Constructed component calls are not World recovery/retry/capture authority.
    }

    @Test void scalarEffectsAreImmutableAndForgedMetadataExecutesNothing() {
        var h = new H();
        var result = h.body.run(WHOLE, world());
        var before = h.p.capture();
        int calls = h.calls.size();
        assertThrows(UnsupportedOperationException.class,
                () -> result.effects().add(new CdSonicHole.DespawnChecked(true)));
        var supplied = new ArrayList<CdSonicHole.Effect>();
        supplied.add(new CdSonicHole.DespawnChecked(true));
        var forged = new CdSonicHole.Result(h.slot(), supplied);
        supplied.clear();
        assertTrue(forged.deleted());
        assertEquals(1, forged.effects().size());
        assertFalse(result.deleted());
        samePlacement(h.p, before);
        assertEquals(calls, h.calls.size());
    }

    @Test void constructorAssetAndInputContractsHaveNoDefaultsOrNormalization() {
        var h = new H();
        assertThrows(NullPointerException.class, () -> new CdSonicHole(null, assets(), h.reader, h.fm, h.draw));
        assertThrows(NullPointerException.class, () -> new CdSonicHole(h.p, null, h.reader, h.fm, h.draw));
        assertThrows(NullPointerException.class, () -> new CdSonicHole(h.p, assets(), null, h.fm, h.draw));
        assertThrows(NullPointerException.class, () -> new CdSonicHole(h.p, assets(), h.reader, null, h.draw));
        assertThrows(NullPointerException.class, () -> new CdSonicHole(h.p, assets(), h.reader, h.fm, null));
        assertThrows(NullPointerException.class, () -> new CdSonicHole.Assets(null, MAP));
        assertThrows(NullPointerException.class, () -> CdSonicHole.Assets.fromObjectAssets(null));
        assertDoesNotThrow(() -> new CdSonicHole.Assets(visual(), 0xF1234567));
        var tooWide = CdObjectVisualAssets.decodeStatic(
                HexFormat.of().parseHex("000201f00f0000f0"), 1, 16);
        assertThrows(IllegalArgumentException.class, () -> new CdSonicHole.Assets(tooWide, MAP));
        var two = CdObjectVisualAssets.decodeStatic(
                HexFormat.of().parseHex("0004000a01f00f0000f001f80d0000f0"), 2, 16);
        assertThrows(IllegalArgumentException.class, () -> new CdSonicHole.Assets(two, MAP));
        var scripted = CdObjectVisualAssets.decode(MAPS, 1, HexFormat.of().parseHex("00020100ff"), 1, 14);
        assertThrows(IllegalArgumentException.class, () -> new CdSonicHole.Assets(scripted, MAP));
        assertDoesNotThrow(() -> g(255, 255, 65535));
        for (int bad : new int[]{-1, 256}) {
            assertThrows(IllegalArgumentException.class, () -> g(bad, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> g(0, bad, 0));
        }
        for (int bad : new int[]{-1, 65536}) {
            assertThrows(IllegalArgumentException.class, () -> g(0, 0, bad));
        }
        var before = h.p.capture();
        assertThrows(NullPointerException.class, () -> h.body.run(WHOLE, null));
        assertThrows(IndexOutOfBoundsException.class, () -> h.body.run(31, world()));
        assertThrows(IndexOutOfBoundsException.class, () -> h.body.run(128, world()));
        h.edit(s -> s.moveByte(ID, 0x33));
        var wrong = h.p.capture();
        assertThrows(IllegalArgumentException.class, () -> h.body.run(WHOLE, world()));
        samePlacement(h.p, wrong);
        h.p.restore(before);
    }

    // Native byte offsets, independently authored from N Init:25-36; no production helper calls.
    private static byte[] initOracle(CdObjectState before, int pointer, int tile) {
        byte[] b = before.copyBytes();
        b[0x24] += 2; b[1] |= 4; b[2] = (byte) (tile >>> 8); b[3] = (byte) tile;
        b[4] = (byte) (pointer >>> 24); b[5] = (byte) (pointer >>> 16);
        b[6] = (byte) (pointer >>> 8); b[7] = (byte) pointer;
        b[0x19] = 0x10; b[0x16] = 0x10; b[0x18] = 6;
        return b;
    }

    private static void samePlacement(CdObjectPlacementRuntime p, CdObjectPlacementRuntime.Snapshot expected) {
        p.validateRestore(expected);
        var now = p.capture();
        assertEquals(expected.status(), now.status());
        assertArrayEquals(expected.savedFlags().copy(), now.savedFlags().copy());
        assertEquals(expected.pool().slots(), now.pool().slots());
    }

    private static void assertOtherSlots(CdObjectPool.Snapshot before, CdObjectPool.Snapshot after) {
        assertEquals(96, before.slots().size());
        assertEquals(96, after.slots().size());
        for (int i = 0; i < 96; i++) if (i != WHOLE - 32) assertEquals(before.slots().get(i), after.slots().get(i));
    }

    private static void mutate(H h, int kind) {
        switch (kind) {
            case 0 -> h.edit(s -> s.moveByte(MAP_FRAME, 255));
            case 1 -> h.p.pool().write(h.p.pool().state(127).moveByte(MAP_FRAME, 255));
            case 2 -> h.p.bsetSavedFlag(1, 1, CdObjectPlacementRuntime.SavedBit.MEMBER_1);
            case 3 -> h.p.resetSavedObjectFlags();
            case 4 -> h.p.spawnObjects(0x3000, 1);
            case 5 -> h.p.pool().clearDynamicSlots();
            default -> throw new AssertionError("mutation fixture");
        }
    }

    private static void assertMutationRetained(H h, int kind) {
        switch (kind) {
            case 0 -> assertEquals(255, h.slot().unsignedByte(MAP_FRAME));
            case 1 -> assertEquals(255, h.p.pool().state(127).unsignedByte(MAP_FRAME));
            case 2 -> assertEquals(1, h.bank(6) & 1);
            case 3 -> { assertEquals(1, h.p.capture().savedFlags().unsignedByte(0)); assertEquals(1, h.p.capture().savedFlags().unsignedByte(1)); }
            case 4 -> assertEquals(2, h.p.status().routine());
            case 5 -> assertArrayEquals(new byte[64], h.slot().copyBytes());
            default -> throw new AssertionError("mutation fixture");
        }
    }

    private static void mark(CdObjectPlacementRuntime p, int index, int value) {
        int id = (index - 2) / 3, period = (index - 2) % 3;
        for (int bit = 0; bit < 8; bit++) {
            if ((value & (1 << bit)) != 0) p.bsetSavedFlag(id, period, CdObjectPlacementRuntime.SavedBit.values()[bit]);
            else p.bclrSavedFlag(id, period, CdObjectPlacementRuntime.SavedBit.values()[bit]);
        }
    }

    private static void fillBody(CdObjectPlacementRuntime p, int value) {
        for (int index = 2; index < 766; index++) mark(p, index, value);
    }

    private static CdObjectState dirty(CdObjectState s) {
        // Public typed aliases only; unaddressed bytes retain known initial zeros.
        for (var f : CdObjectState.LongField.values()) s = s.moveLong(f, 0xA5C37E19 ^ f.byteOffset() * 0x01010101);
        for (var f : CdObjectState.WordField.values()) s = s.moveWord(f, 0xD15B ^ f.byteOffset() * 0x0101);
        for (var f : CdObjectState.ByteField.values()) s = s.moveByte(f, 0x51 + f.byteOffset() * 7);
        return s;
    }

    private static CdObjectPlacements layout() {
        var b = ByteBuffer.allocate(34);
        b.putShort((short) 12).putShort((short) 28).putShort((short) 0xFFFF).putInt(0).putShort((short) 0);
        b.putShort((short) 0x1000).putShort((short) 0x200).put((byte) 0xB1).put((byte) 0).put((byte) 0xE0).put((byte) 0);
        b.putShort((short) 0xFFFF).putInt(0).putShort((short) 0);
        b.putShort((short) 0xFFFF).putInt(0);
        return CdObjectPlacements.decodeLayout(b.array());
    }

    private static CdObjectVisualAssets visual() { return CdObjectVisualAssets.decodeStatic(MAPS, 1, 14); }
    private static CdSonicHole.Assets assets() { return new CdSonicHole.Assets(visual(), MAP); }
    private static CdSonicHole.Globals world() { return g(1, 0, 0x1080); }
    private static CdSonicHole.Globals g(int raw, int direction, int camera) {
        return new CdSonicHole.Globals(raw, direction, camera);
    }

    private static final class H {
        final CdObjectPlacementRuntime p = new CdObjectPlacementRuntime(layout());
        final List<String> calls = new ArrayList<>();
        final CdSonicHole body;
        int control;
        int x = -1, y = -1;
        int cutCount;
        CdObjectPool.Snapshot lastCut;
        CdObjectState drawBefore;
        CdSonicHole.PrimaryPlayerRead reader = (field, cut, source) -> readValue(field, source);
        CdSonicHole.PlayFmCall fm = (sound, cut, source) -> {};
        CdSonicHole.DrawObjectCall draw = (pool, slot) -> {};

        H() { this(assets()); }
        H(CdSonicHole.Assets assets) {
            for (int whole = 32; whole < 128; whole++) {
                p.pool().write(p.pool().state(whole).moveByte(MAP_FRAME, whole).moveLong(MAPPINGS, 0x12340000 | whole));
            }
            write(slot().moveByte(ID, 0x31).moveByte(ROUTINE, 0).moveByte(SAVED_FLAGS_ID, 1)
                    .moveByte(MAP_FRAME, 0).moveWord(X, 0x1000).moveWord(Y, 0x200));
            body = new CdSonicHole(p, assets,
                    (field, cut, source) -> {
                        assertCut(cut, source);
                        calls.add(field.name());
                        return reader.read(field, cut, source);
                    },
                    (sound, cut, source) -> {
                        assertCut(cut, source);
                        assertEquals(CdSonicHole.FmSound.FM_A3, sound);
                        assertEquals(2, source.routine());
                        calls.add("FM");
                        fm.playFmSound(sound, cut, source);
                    },
                    (pool, slot) -> {
                        assertSame(p.pool(), pool);
                        assertEquals(WHOLE, slot.wholeIndex());
                        calls.add("DRAW");
                        drawBefore = pool.state(slot.wholeIndex());
                        pool.write(drawBefore.moveByte(SPRITE_FLAGS, drawBefore.spriteFlags() & 0x7F));
                        draw.drawObject(pool, slot);
                    });
        }

        int readValue(CdSonicHole.PlayerField field, CdObjectState source) {
            return switch (field) {
                case CONTROL_2C -> control;
                case X_WORD -> x < 0 ? source.centreX() : x;
                case Y_WORD -> y < 0 ? source.centreY() : y;
            };
        }
        void assertCut(CdObjectPool.Snapshot cut, CdObjectState source) {
            p.pool().validateRestore(cut);
            assertEquals(96, cut.slots().size());
            assertEquals(p.pool().capture().slots(), cut.slots());
            assertSame(cut.slots().get(WHOLE - 32), source);
            assertSame(slot(), source);
            assertNotSame(lastCut, cut);
            lastCut = cut;
            cutCount++;
        }
        void resetCalls() { calls.clear(); lastCut = null; cutCount = 0; drawBefore = null; }
        CdObjectState slot() { return p.pool().state(WHOLE); }
        void write(CdObjectState s) { p.pool().write(s); }
        void edit(java.util.function.UnaryOperator<CdObjectState> f) { write(f.apply(slot())); }
        int bank(int index) { return p.capture().savedFlags().unsignedByte(index); }
    }
}
