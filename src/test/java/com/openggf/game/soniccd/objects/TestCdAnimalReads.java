package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdObjectPlacements;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.openggf.game.soniccd.objects.CdAnimal.DespawnEntry.*;
import static com.openggf.game.soniccd.objects.CdAnimal.NativeCall.*;
import static com.openggf.game.soniccd.objects.CdAnimal.UnsupportedBoundary.*;
import static com.openggf.game.soniccd.objects.CdObjectPlacementRuntime.SavedBit.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Source-literal conditional-read controls, frozen before the production file existed. ALL JAVA UNRUN.
 * N=1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Animal/Main.asm:7-19;
 * Object Time Check.asm:55-85; Object Spawner.asm:385-446. Constructed layouts, mutable callbacks
 * and mixed timeZone sequences prove no genuine Frame/World origin, route, boot or native execution.
 * The existing TestCdAnimal is untouched; these are additional tests, not a replacement.
 */
class TestCdAnimalReads {
    @Test
    void literalReadTracesFollowTheReachedNativeInstructions() {
        var kept = new Harness(7, 0);
        var before = kept.cut();
        var reads = new Script("TZ", 1, "PROJ", 0, "CAMERA", 0x200, "TZ", 1);
        var result = kept.run(reads);
        reads.done("TZ", "PROJ", "CAMERA", "TZ");
        assertEquals(List.of(call(CHECK_ANIMAL_PRESENCE), call(CHECK_OBJ_DESPAWN)), result.effects());
        assertFalse(result.deleted());
        unchanged(before, kept);

        var warp = new Harness(7, 0);
        before = warp.cut();
        reads = new Script("TZ", 0x81, "PROJ", 0, "CAMERA", 0x200, "TZ", 0x81, "TZ", 0x81, "DIR", 1);
        result = warp.run(reads);
        reads.done("TZ", "PROJ", "CAMERA", "TZ", "TZ", "DIR");
        assertEquals(List.of(call(CHECK_ANIMAL_PRESENCE), new CdAnimal.Despawn(TIME_WARP, 7, 0),
                call(CHECK_OBJ_DESPAWN)), result.effects());
        nativeDelete(before, warp, result, 7, 0);

        var outside = new Harness(0, 0);
        before = outside.cut();
        reads = new Script("TZ", 1, "PROJ", 0, "CAMERA", 0x1FF);
        result = outside.run(reads);
        reads.done("TZ", "PROJ", "CAMERA");
        assertEquals(List.of(call(CHECK_ANIMAL_PRESENCE), new CdAnimal.Despawn(CURRENT_X_OFFSCREEN, 0, -1),
                call(CHECK_OBJ_DESPAWN)), result.effects());
        nativeDelete(before, outside, result, 0, -1);
    }

    @Test
    void hologramAndNonzeroPresencePredicatesStopBeforeBodiesAndWrites() {
        for (int subtype : new int[]{0x80, 0x81, 0xFF}) {
            var h = new Harness(255, subtype);
            var before = h.cut();
            var reads = new Script();
            var failure = assertThrows(CdAnimal.UnsupportedAnimal.class, () -> h.run(reads));
            assertEquals(HOLOGRAM_BODY, failure.boundary());
            assertEquals(34, failure.wholeIndex());
            reads.done();
            unchanged(before, h);
        }
        for (int nonzero : new int[]{1, 2, 0x7F, 0x80, 0xFF}) {
            var h = new Harness(255, 0x7F);
            var before = h.cut();
            var good = new Script("TZ", 2, "GF", nonzero);
            var failure = assertThrows(CdAnimal.UnsupportedAnimal.class, () -> h.run(good));
            assertEquals(GOOD_FUTURE_BODY, failure.boundary());
            good.done("TZ", "GF");
            unchanged(before, h);
            var projector = new Script("TZ", 0x82, "PROJ", nonzero);
            failure = assertThrows(CdAnimal.UnsupportedAnimal.class, () -> h.run(projector));
            assertEquals(PROJECTOR_DESTROYED_BODY, failure.boundary());
            projector.done("TZ", "PROJ");
            unchanged(before, h);
        }
    }

    @Test
    void everyRawTimeZoneByteKeepsItsSignedPreludeAndRawWarpBit() {
        for (int raw = 0; raw <= 0xFF; raw++) {
            var h = new Harness(0, 0);
            var before = h.cut();
            boolean future = raw >= 2 && raw < 0x80;
            var reads = future ? new Script("TZ", raw, "GF", 0)
                    : new Script("TZ", raw, "PROJ", 0, "CAMERA", 0x200, "TZ", raw);
            var result = h.run(reads);
            if (future) {
                reads.done("TZ", "GF");
                assertEquals(List.of(call(CHECK_ANIMAL_PRESENCE), new CdAnimal.Deleted(), call(DELETE_OBJECT)), result.effects());
                nativeDelete(before, h, result, 0, -1);
            } else if (raw >= 0x80) {
                reads.done("TZ", "PROJ", "CAMERA", "TZ");
                assertEquals(List.of(call(CHECK_ANIMAL_PRESENCE), new CdAnimal.Despawn(TIME_WARP, 0, -1),
                        call(CHECK_OBJ_DESPAWN)), result.effects());
                nativeDelete(before, h, result, 0, -1);
            } else {
                reads.done("TZ", "PROJ", "CAMERA", "TZ");
                assertFalse(result.deleted());
                unchanged(before, h);
            }
        }
    }

    @Test
    void dirtyRoutineAndLowSubtypeAreNeverDispatchedOnTheInertPath() {
        for (int subtype : new int[]{0, 1, 7, 0x7F}) {
            for (int routine : new int[]{0, 1, 2, 4, 5, 0xFE, 0xFF}) {
                var h = new Harness(7, subtype);
                h.write(h.source().moveByte(ROUTINE, routine));
                var before = h.cut();
                var reads = new Script("TZ", 0, "PROJ", 0, "CAMERA", 0x200, "TZ", 0);
                var result = h.run(reads);
                reads.done("TZ", "PROJ", "CAMERA", "TZ");
                assertFalse(result.deleted());
                assertEquals(routine, result.state().routine());
                unchanged(before, h);
            }
        }
    }

    @Test
    void onscreenAndZeroSavedIdSkipExactlyTheirUnreachedReads() {
        var h = new Harness(7, 0);
        h.write(h.source().moveByte(SPRITE_FLAGS, 0x80));
        var before = h.cut();
        var reads = new Script("TZ", 1, "PROJ", 0, "TZ", 1);
        assertFalse(h.run(reads).deleted());
        reads.done("TZ", "PROJ", "TZ");
        unchanged(before, h);
        var noSaved = new Harness(0, 0);
        noSaved.write(noSaved.source().moveByte(SPRITE_FLAGS, 0x80));
        before = noSaved.cut();
        reads = new Script("TZ", 0xFF, "PROJ", 0, "TZ", 0xFF);
        var result = noSaved.run(reads);
        reads.done("TZ", "PROJ", "TZ");
        nativeDelete(before, noSaved, result, 0, -1);
    }

    @Test
    void cameraChunkEqualityNextReachableDistanceAndWordWrapAreLiteral() {
        int[][] vectors = {
                {0x468, 0x200, 1}, {0x468, 0x27F, 1}, {0x468, 0x4FF, 1},
                {0x468, 0x1FF, 0}, {0x468, 0x180, 0}, {0x468, 0x500, 0},
                {0x468, 0x680, 0}, {0x468, 0, 0}, {0xFF80, 0, 1},
                {0, 0, 1}, {0, 0x100, 0}
        };
        for (int[] v : vectors) {
            var h = new Harness(0, 0);
            h.write(h.source().moveWord(X, v[0]));
            var before = h.cut();
            var reads = v[2] == 1 ? new Script("TZ", 1, "PROJ", 0, "CAMERA", v[1], "TZ", 1)
                    : new Script("TZ", 1, "PROJ", 0, "CAMERA", v[1]);
            var result = h.run(reads);
            if (v[2] == 1) {
                reads.done("TZ", "PROJ", "CAMERA", "TZ");
                assertFalse(result.deleted());
                unchanged(before, h);
            } else {
                reads.done("TZ", "PROJ", "CAMERA");
                assertTrue(result.effects().contains(new CdAnimal.Despawn(CURRENT_X_OFFSCREEN, 0, -1)));
                nativeDelete(before, h, result, 0, -1);
            }
        }
    }

    @Test
    void signedDirectionAndNativeClampChooseOnlyTheLoadedBit() {
        int[][] vectors = {{0x81, 0x80, 2}, {0x81, 0x7F, 0}, {0x81, 0, 1},
                {0x80, 1, 0}, {0x82, 0xFF, 2}, {0xFF, 0x80, 2}, {0xFF, 0x7F, 0}};
        for (int[] v : vectors) {
            var h = new Harness(7, 0);
            var before = h.cut();
            var reads = new Script("TZ", v[0], "PROJ", 0, "CAMERA", 0x200, "TZ", v[0], "TZ", v[0], "DIR", v[1]);
            var result = h.run(reads);
            reads.done("TZ", "PROJ", "CAMERA", "TZ", "TZ", "DIR");
            assertTrue(result.effects().contains(new CdAnimal.Despawn(TIME_WARP, 7, v[2])));
            nativeDelete(before, h, result, 7, v[2]);
        }
    }

    @Test
    void separateTimeZoneReadsAreFreshAndTheCopiedWarpBitIsNotReread() {
        var h = new Harness(7, 0);
        var before = h.cut();
        var reads = new Script("TZ", 0x81, "PROJ", 0, "CAMERA", 0x200, "TZ", 0x81, "TZ", 1);
        var result = h.run(reads);
        reads.done("TZ", "PROJ", "CAMERA", "TZ", "TZ");
        nativeDelete(before, h, result, 7, 1);
        h = new Harness(7, 0);
        before = h.cut();
        reads = new Script("TZ", 1, "PROJ", 0, "CAMERA", 0x200, "TZ", 0x80, "TZ", 0x82, "DIR", 0xFF);
        result = h.run(reads);
        reads.done("TZ", "PROJ", "CAMERA", "TZ", "TZ", "DIR");
        nativeDelete(before, h, result, 7, 2);
        h = new Harness(7, 0);
        before = h.cut();
        reads = new Script("TZ", 1, "PROJ", 0, "CAMERA", 0x1FF, "TZ", 2);
        result = h.run(reads);
        reads.done("TZ", "PROJ", "CAMERA", "TZ");
        assertTrue(result.effects().contains(new CdAnimal.Despawn(CURRENT_X_OFFSCREEN, 7, 2)));
        nativeDelete(before, h, result, 7, 2);
    }

    @Test
    void exactTwoFeBankAccepts254PeriodsZeroOneAndRejectsOtherUnownedBytesBeforeDelete() {
        for (int period : new int[]{0, 1}) {
            var h = new Harness(254, 0);
            var before = h.cut();
            assertEquals(0x2FE, before.savedFlags().length());
            var reads = new Script("TZ", 1, "PROJ", 0, "CAMERA", 0x1FF, "TZ", period);
            var result = h.run(reads);
            reads.done("TZ", "PROJ", "CAMERA", "TZ");
            nativeDelete(before, h, result, 254, period);
        }
        for (int[] v : new int[][]{{254, 2}, {255, 0}}) {
            var h = new Harness(v[0], 0);
            var before = h.cut();
            var reads = new Script("TZ", 1, "PROJ", 0, "CAMERA", 0x1FF, "TZ", v[1]);
            assertThrows(IndexOutOfBoundsException.class, () -> h.run(reads));
            reads.done("TZ", "PROJ", "CAMERA", "TZ");
            unchanged(before, h);
        }
        for (int rawPeriod : new int[]{3, 0x7F}) {
            var h = new Harness(7, 0);
            var before = h.cut();
            var reads = new Script("TZ", 1, "PROJ", 0, "CAMERA", 0x1FF, "TZ", rawPeriod);
            assertThrows(IllegalArgumentException.class, () -> h.run(reads));
            reads.done("TZ", "PROJ", "CAMERA", "TZ");
            unchanged(before, h);
        }
    }

    @Test
    void nullWrongIdAndReachedWidthFaultsNeverPrefetchOrWrite() {
        // Nonzero saved ID reaches the offscreen period and warp-direction width probes.
        var h = new Harness(7, 0);
        var before = h.cut();
        assertEquals("reads", assertThrows(NullPointerException.class, () -> h.animal.runWithReads(34, null)).getMessage());
        assertEquals("globals", assertThrows(NullPointerException.class, () -> h.animal.run(34, null)).getMessage());
        var empty = new Script();
        assertThrows(IllegalArgumentException.class, () -> h.animal.runWithReads(33, empty));
        assertThrows(IndexOutOfBoundsException.class, () -> h.animal.runWithReads(31, empty));
        empty.done();
        unchanged(before, h);
        Object[][] prefixes = {
                {"TZ"}, {"TZ", 2, "GF"}, {"TZ", 1, "PROJ"},
                {"TZ", 1, "PROJ", 0, "CAMERA"},
                {"TZ", 1, "PROJ", 0, "CAMERA", 0x200, "TZ"},
                {"TZ", 1, "PROJ", 0, "CAMERA", 0x1FF, "TZ"},
                {"TZ", 0x81, "PROJ", 0, "CAMERA", 0x200, "TZ", 0x81, "TZ", 0x81, "DIR"}
        };
        for (Object[] prefix : prefixes) {
            boolean camera = prefix[prefix.length - 1].equals("CAMERA");
            for (int bad : camera ? new int[]{-1, 0x10000} : new int[]{-1, 0x100, 0x10000}) {
                Object[] tokens = Arrays.copyOf(prefix, prefix.length + 1);
                tokens[tokens.length - 1] = bad;
                var reads = new Script(tokens);
                assertThrows(IllegalArgumentException.class, () -> h.run(reads));
                reads.done();
                unchanged(before, h);
            }
        }
    }

    @Test
    void everyExternalAccessorRejectsReturnedPoolMutationBeforeAnyLaterBehavior() {
        Object[][] scripts = {
                {"TZ", 1}, {"TZ", 2, "GF", 0}, {"TZ", 1, "PROJ", 0},
                {"TZ", 1, "PROJ", 0, "CAMERA", 0x200},
                {"TZ", 0x81, "PROJ", 0, "CAMERA", 0x200, "TZ", 0x81, "TZ", 0x81, "DIR", 0}
        };
        for (Object[] tokens : scripts) {
            for (int whole : new int[]{34, 127}) {
                var h = new Harness(7, 0);
                var expected = new CdObjectPlacementRuntime.Snapshot[1];
                var reads = new Script(tokens).intercept(tokens.length / 2, () -> {
                    h.write(h.pool.state(whole).moveByte(ROUTINE, 0x55));
                    expected[0] = h.cut();
                }, null);
                assertThrows(IllegalStateException.class, () -> h.run(reads));
                reads.done();
                unchanged(expected[0], h);
            }
        }
    }

    @Test
    void returnedBankAndStatusMutationsAreRejectedIncludingAFalseProjectorResult() {
        for (int kind : new int[]{0, 1}) {
            var h = new Harness(7, 0);
            var expected = new CdObjectPlacementRuntime.Snapshot[1];
            var reads = new Script("TZ", 1, "PROJ", 0).intercept(2, () -> {
                if (kind == 0) { h.runtime.bclrSavedFlag(7, 1, LOADED); }
                else { h.runtime.spawnObjects(0x200, 1); }
                expected[0] = h.cut();
            }, null);
            assertThrows(IllegalStateException.class, () -> h.run(reads));
            reads.done("TZ", "PROJ");
            unchanged(expected[0], h);
        }
    }

    @Test
    void throwingAccessorsPreserveOriginalRuntimeExceptionOrErrorAndSuppressMutationFault() {
        for (boolean mutate : new boolean[]{false, true}) {
            for (boolean error : new boolean[]{false, true}) {
                var h = new Harness(7, 0);
                var expected = new CdObjectPlacementRuntime.Snapshot[]{h.cut()};
                Throwable original = error ? new AssertionError("owner read unavailable")
                        : new UnsupportedOperationException("owner read unavailable");
                var reads = new Script("TZ", 1, "PROJ", 0).intercept(2, () -> {
                    if (mutate) { h.runtime.bclrSavedFlag(7, 1, LOADED); }
                    expected[0] = h.cut();
                }, original);
                var observed = assertThrows(Throwable.class, () -> h.run(reads));
                assertSame(original, observed);
                assertEquals(mutate ? 1 : 0, observed.getSuppressed().length);
                if (mutate) { assertInstanceOf(IllegalStateException.class, observed.getSuppressed()[0]); }
                reads.done("TZ", "PROJ");
                unchanged(expected[0], h);
            }
        }
    }

    @Test
    void reachedWidthFaultSurvivesWithSuppressedVerificationAndNoDelete() {
        var h = new Harness(7, 0);
        var expected = new CdObjectPlacementRuntime.Snapshot[1];
        var reads = new Script("TZ", 2, "GF", 0x100).intercept(2, () -> {
            h.write(h.source().moveByte(ID, 0));
            expected[0] = h.cut();
        }, null);
        var failure = assertThrows(IllegalArgumentException.class, () -> h.run(reads));
        assertEquals(1, failure.getSuppressed().length);
        assertInstanceOf(IllegalStateException.class, failure.getSuppressed()[0]);
        reads.done("TZ", "GF");
        unchanged(expected[0], h);
    }

    @Test
    void legacyGlobalsKeepTheirEagerBoundsAndTheReadResultsKeepImmutableEffects() {
        for (int tz : new int[]{3, 0x7F, 0x83, 0xFF, -1, 0x100}) {
            assertThrows(IllegalArgumentException.class, () -> new CdAnimal.Globals(tz, 0, 0, 0, 0));
        }
        assertThrows(IllegalArgumentException.class, () -> new CdAnimal.Globals(1, 0x100, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdAnimal.Globals(1, 0, -1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdAnimal.Globals(1, 0, 0, 0x100, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdAnimal.Globals(1, 0, 0, 0, 0x10000));
        CdAnimal.Reads known = new CdAnimal.Globals(0x82, 0xFF, 0x80, 0xFF, 0xFFFF);
        assertEquals(0x82, known.timeZone());
        var h = new Harness(0, 0);
        var result = h.run(new Script("TZ", 2, "GF", 0));
        assertThrows(UnsupportedOperationException.class, () -> result.effects().clear());
        assertEquals(0x24, CdAnimal.OBJECT_ID);
    }

    @Test
    void legacyAndReadEntrypointsAgreeAcrossBoundedNativeVectorsIncludingFailures() {
        // TZ, GF, projector, direction, camera, saved ID, sprite flags, subtype.
        int[][] vectors = {
                {0, 0, 0, 0, 0x200, 0, 0, 0}, {1, 0, 0, 0, 0x200, 7, 0, 0x7F},
                {1, 0, 0, 0, 0x1FF, 7, 0, 0}, {1, 0, 0, 0, 0, 0, 0x80, 0},
                {2, 0, 0xFF, 0xFF, 0, 255, 0, 1}, {2, 1, 0, 0, 0, 7, 0, 0},
                {0, 0, 0x80, 0, 0x200, 7, 0, 0}, {1, 0, 1, 0, 0x200, 0, 0, 0},
                {0x80, 1, 0, 1, 0x200, 7, 0x80, 0}, {0x81, 0, 0, 0xFF, 0x200, 7, 0, 0},
                {0x82, 0, 0, 0x80, 0x200, 7, 0, 0}, {0x81, 0, 0, 0x7F, 0x200, 7, 0, 0},
                {0x81, 0, 0, 0, 0x200, 0, 0, 0}, {1, 0, 0, 0, 0x500, 254, 0, 0},
                {2, 0, 0, 0, 0x200, 255, 0, 0}, {1, 0, 0, 0, 0x1FF, 255, 0, 0},
                {0x82, 0, 0, 0, 0x200, 254, 0, 0}, {2, 0, 0, 0, 0, 0, 0, 0x80}
        };
        for (int[] v : vectors) {
            var legacy = new Harness(v[5], v[7]);
            var port = new Harness(v[5], v[7]);
            legacy.write(legacy.source().moveByte(SPRITE_FLAGS, v[6]));
            port.write(port.source().moveByte(SPRITE_FLAGS, v[6]));
            var globals = new CdAnimal.Globals(v[0], v[1], v[2], v[3], v[4]);
            CdAnimal.Result left = null, right = null;
            Throwable leftFailure = null, rightFailure = null;
            try { left = legacy.animal.run(34, globals); }
            catch (RuntimeException | Error failure) { leftFailure = failure; }
            try { right = port.animal.runWithReads(34, globals); }
            catch (RuntimeException | Error failure) { rightFailure = failure; }
            if (leftFailure != null) {
                assertNotNull(rightFailure);
                assertEquals(leftFailure.getClass(), rightFailure.getClass());
                assertEquals(leftFailure.getMessage(), rightFailure.getMessage());
            } else {
                assertNull(rightFailure);
                assertNotNull(right);
                assertEquals(left.effects(), right.effects());
                assertArrayEquals(left.state().copyBytes(), right.state().copyBytes());
            }
            var a = legacy.cut();
            var b = port.cut();
            assertEquals(a.status(), b.status());
            assertArrayEquals(a.savedFlags().copy(), b.savedFlags().copy());
            for (int i = 0; i < 96; i++) {
                assertArrayEquals(a.pool().slots().get(i).copyBytes(), b.pool().slots().get(i).copyBytes());
            }
        }
    }

    private static CdAnimal.Effect call(CdAnimal.NativeCall call) { return new CdAnimal.CallReturned(call); }

    private static void unchanged(CdObjectPlacementRuntime.Snapshot before, Harness h) {
        h.runtime.validateRestore(before);
        assertSame(h.pool, h.runtime.pool());
        var after = h.cut();
        assertEquals(before.status(), after.status());
        assertEquals(0x2FE, after.savedFlags().length());
        assertArrayEquals(before.savedFlags().copy(), after.savedFlags().copy());
        assertEquals(before.pool().slots(), after.pool().slots());
        for (int i = 0; i < 96; i++) {
            assertArrayEquals(before.pool().slots().get(i).copyBytes(), after.pool().slots().get(i).copyBytes());
        }
    }

    private static void nativeDelete(CdObjectPlacementRuntime.Snapshot before, Harness h,
                                     CdAnimal.Result result, int savedId, int period) {
        h.runtime.validateRestore(before);
        assertSame(h.pool, h.runtime.pool());
        assertTrue(result.deleted());
        assertArrayEquals(new byte[64], result.state().copyBytes());
        assertArrayEquals(new byte[64], h.source().copyBytes());
        assertEquals(before.status(), h.runtime.status());
        byte[] bank = before.savedFlags().copy();
        if (savedId != 0) { bank[2 + 3 * savedId + period] &= (byte) 0x7F; }
        assertArrayEquals(bank, h.cut().savedFlags().copy());
        for (int whole = 32; whole < 128; whole++) {
            if (whole != 34) { assertEquals(before.pool().slots().get(whole - 32), h.pool.state(whole)); }
        }
    }

    /** Every unlisted getter throws. A script is a test control, never an authentic native global owner. */
    private static final class Script implements CdAnimal.Reads {
        private final Object[] tokens;
        private final List<String> trace = new ArrayList<>();
        private int next;
        private int interceptOrdinal = -1;
        private Runnable action;
        private Throwable fault;
        Script(Object... tokens) {
            assertEquals(0, tokens.length % 2);
            this.tokens = tokens.clone();
        }
        Script intercept(int ordinal, Runnable action, Throwable fault) {
            this.interceptOrdinal = ordinal;
            this.action = action;
            this.fault = fault;
            return this;
        }
        private int take(String name) {
            trace.add(name);
            if (next >= tokens.length) { throw new AssertionError("forbidden unused getter: " + name); }
            assertEquals(tokens[next++], name, "native read order");
            int value = (Integer) tokens[next++];
            if (trace.size() == interceptOrdinal) {
                if (action != null) { action.run(); }
                if (fault instanceof RuntimeException failure) { throw failure; }
                if (fault instanceof Error failure) { throw failure; }
            }
            return value;
        }
        void done(String... expectedTrace) {
            assertEquals(tokens.length, next, "all and only reached native reads");
            if (expectedTrace.length != 0) { assertEquals(List.of(expectedTrace), trace); }
            else if (tokens.length == 0) { assertTrue(trace.isEmpty()); }
        }
        @Override public int timeZone() { return take("TZ"); }
        @Override public int goodFuture() { return take("GF"); }
        @Override public int projDestroyed() { return take("PROJ"); }
        @Override public int timeWarpDir() { return take("DIR"); }
        @Override public int cameraXWord() { return take("CAMERA"); }
    }

    private static final class Harness {
        final CdObjectPlacementRuntime runtime;
        final CdObjectPool pool;
        final CdAnimal animal;
        Harness(int savedId, int subtype) {
            // Empty COMMON list plus real distinct backward/forward/Null sentinels; no production provenance.
            var bytes = ByteBuffer.allocate(26);
            bytes.putShort((short) 12).putShort((short) 20).putShort((short) 0xFFFF).putInt(0).putShort((short) 0);
            bytes.putShort((short) 0xFFFF).putInt(0).putShort((short) 0);
            bytes.putShort((short) 0xFFFF).putInt(0);
            runtime = new CdObjectPlacementRuntime(CdObjectPlacements.decodeLayout(bytes.array()));
            pool = runtime.pool();
            animal = new CdAnimal(runtime);
            runtime.resetSavedObjectFlags();
            for (int id : new int[]{0, 1, 7, 253, 254}) {
                for (int p = 0; p < (id == 254 ? 2 : 3); p++) {
                    runtime.bsetSavedFlag(id, p, LOADED);
                    runtime.bsetSavedFlag(id, p, MEMBER_1);
                    runtime.bsetSavedFlag(id, p, MEMBER_6);
                }
            }
            for (int whole = 32; whole < 128; whole++) {
                write(pool.state(whole).moveByte(ID, 0x35).moveByte(ROUTINE, 0xFF)
                        .moveLong(MAPPINGS, 0x12340000 | whole).moveLong(X_POSITION, 0x0468ABCD)
                        .moveLong(Y_POSITION, 0x06E0FEDC).moveWord(X_VELOCITY, 0x8123)
                        .moveWord(Y_VELOCITY, 0x7FFE).moveByte(COLLISION_TYPE, 0x2B)
                        .moveByte(PRIORITY, 3).moveByte(MAP_FRAME, 0x1F).moveWord(TILE, 0x388)
                        .moveByte(FLAGS, 0x55).moveByte(RAW_CONTROL_2C, whole).moveByte(STAND_LINK_3D, 0xFA));
            }
            write(source().moveByte(ID, 0x24).moveByte(SPRITE_FLAGS, 0).moveByte(SUBTYPE, subtype)
                    .moveByte(SUBTYPE2, 0xA7).moveByte(SAVED_FLAGS_ID, savedId));
        }
        CdObjectState source() { return pool.state(34); }
        void write(CdObjectState image) { pool.write(image); }
        CdObjectPlacementRuntime.Snapshot cut() { return runtime.capture(); }
        CdAnimal.Result run(CdAnimal.Reads reads) { return animal.runWithReads(34, reads); }
    }
}
