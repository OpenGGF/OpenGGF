package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.openggf.game.soniccd.objects.CdObjectAnimation.Kind.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static org.junit.jupiter.api.Assertions.*;

/** Constructed literal-format controls, not media authentication or World admission.
 * Native pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: both AnimateObject forms.
 * Expectations name native stores, including equal-value writes, independently of final parity.
 */
class TestCdObjectAnimationPrefix {
    private static final int SLOT = 57;

    @Test void changedIdPublishesEveryResetAndBothFlipStoresInNativeOrder() {
        var pool = pool(); seed(pool, state(0, 9, 0, 0, 1, 0xBF, 2, 6, 8));
        var trace = new Trace(pool);
        new CdObjectAnimation.Prefix(pool, SLOT, trace).step(single(3, 0x25));
        assertEquals(List.of("PREVIOUS_ANIMATION=0", "ANIMATION_FRAME=0", "ANIMATION_TIME=0",
                "ANIMATION_TIME=255", "ANIMATION_TIME=3", "MAP_FRAME=5", "SPRITE_FLAGS=188",
                "SPRITE_FLAGS=191", "ANIMATION_FRAME=1"), trace.stores);
        assertEquals(List.of(ENTER, STORE, STORE, STORE, STORE, DIRECTORY, SPEED, STORE, FRAME,
                STORE, STORE, STORE, STORE, RETURN), trace.kinds);
    }

    @Test void allTimerBytesAndUnselectedInvalidIdsKeepNativeSignedByteGate() {
        for (int timer = 0; timer < 256; timer++) {
            var pool = pool(); var input = state(255, 255, 233, timer, 0xE1, 0xFF, 3, 8, 9);
            seed(pool, input); var before = pool.capture(); var trace = new Trace(pool);
            var prefix = new CdObjectAnimation.Prefix(pool, SLOT, trace);
            if (((timer - 1) & 255) < 128) {
                assertEquals(CdObjectAnimation.step(input, single(3, 4)), prefix.step(single(3, 4)));
                assertEquals(List.of(ENTER, STORE, RETURN), trace.kinds);
            } else {
                var ex = assertThrows(CdObjectAnimation.SelectedReadFailure.class, () -> prefix.step(single(3, 4)));
                assertEquals(DIRECTORY, ex.boundary());
            }
            assertOnly(pool, before, SLOT, ANIMATION_TIME, (timer - 1) & 255);
        }
    }

    @Test void everyFrameAndCommandHasPureParityAndLiteralStoreControls() {
        for (int frame = 0; frame < 256; frame++) {
            var pool = pool(); var input = state(0, 0, 1, 0, 0xE1, 0xFF, 3, 0xFF, 0xFE);
            seed(pool, input); var before = pool.capture(); var trace = new Trace(pool);
            var table = single(0x83, 0xFC, frame, 1);
            var result = new CdObjectAnimation.Prefix(pool, SLOT, trace).step(table);
            assertEquals(CdObjectAnimation.step(input, table), result, "byte " + frame);
            assertEquals(result, read(pool.state(SLOT)));
            var expected = new ArrayList<>(List.of("ANIMATION_TIME=255", "ANIMATION_TIME=131"));
            if (frame < 128 || frame == 255 || frame == 254) {
                int selected = frame < 128 ? frame : 0xFC;
                if (frame >= 254) expected.add("ANIMATION_FRAME=0");
                expected.add("MAP_FRAME=" + (selected & 31));
                expected.add("SPRITE_FLAGS=252");
                expected.add("SPRITE_FLAGS=" + (252 | ((selected >>> 5 ^ 3) & 3)));
                expected.add("ANIMATION_FRAME=" + (frame < 128 ? 2 : 1));
            } else if (frame == 253) expected.add("ANIMATION=1");
            else if (frame == 252) expected.add("ROUTINE=1");
            else if (frame == 251) { expected.add("ANIMATION_FRAME=0"); expected.add("ROUTINE_2=0"); }
            else if (frame == 250) expected.add("ROUTINE_2=0");
            assertEquals(expected, trace.stores, "byte " + frame);
            assertNonwrites(pool, before, result);
        }
    }

    @Test void frameIndexAndFeSubtractionWrapWithNoRecursiveControlDispatch() {
        byte[] bytes = new byte[259]; bytes[1] = 2; bytes[2] = 7;
        bytes[3] = (byte) 0xFE; bytes[4] = 1; bytes[258] = (byte) 0xFA;
        var table = table(0, 1, bytes);
        var pool = pool(); seed(pool, state(0, 0, 0, 0, 0xE1, 0xA7, 1, 0xFE, 0xFF));
        var trace = new Trace(pool);
        var result = new CdObjectAnimation.Prefix(pool, SLOT, trace).step(table);
        assertEquals(state(0, 0, 0, 7, 26, 0xA6, 1, 0xFE, 0xFF), result);
        assertEquals(List.of("ANIMATION_TIME=255", "ANIMATION_TIME=7", "ANIMATION_FRAME=255",
                "MAP_FRAME=26", "SPRITE_FLAGS=164", "SPRITE_FLAGS=166", "ANIMATION_FRAME=0"), trace.stores);
    }

    @Test void missingSelectedReadsRetainOnlyTheirReachedNativePrefixAndPureRemainsUnpublished() {
        var cases = List.of(
                new Fault(single(3, 9), state(1, 9, 4, 8, 0xE1, 0xFF, 3, 8, 9), DIRECTORY, 255, 0),
                new Fault(table(0, 1, new byte[]{0, 2}), initial(), SPEED, 255, 0),
                new Fault(table(0, 1, new byte[]{(byte) 0xFF, (byte) 0xFF}), initial(), SPEED, 255, 0),
                new Fault(single(3), initial(), FRAME, 3, 0),
                new Fault(single(3, 0xFE), initial(), FE_OPERAND, 3, 0),
                new Fault(single(3, 0xFE, 1), initial(), FE_TARGET, 3, 255),
                new Fault(single(3, 0xFD), initial(), FD_OPERAND, 3, 0));
        for (var c : cases) {
            var input = c.input; var pool = pool(); seed(pool, input); var before = pool.capture();
            var pureFailure = assertThrows(UnsupportedOperationException.class, () -> CdObjectAnimation.step(input, c.table));
            assertEquals(input, read(before.slots().get(SLOT - 32)));
            var trace = new Trace(pool); var prefix = new CdObjectAnimation.Prefix(pool, SLOT, trace);
            var failure = assertThrows(CdObjectAnimation.SelectedReadFailure.class, () -> prefix.step(c.table));
            assertEquals(c.boundary, failure.boundary());
            assertEquals(pureFailure.getMessage(), failure.getMessage());
            assertEquals(pureFailure.getClass(), failure.getCause().getClass());
            var expected = state(input.animationId(), input.animationId(), c.index, c.timer,
                    input.mapFrame(), input.spriteFlags(), input.statusFlags(), input.routine(), input.routine2());
            assertEquals(expected, read(pool.state(SLOT))); assertNonwrites(pool, before, expected);
            assertFalse(prefix.isCurrent(trace.last));
        }
    }

    @Test void ffResetsIndexBeforeFirstFrameSourceGuardFailure() {
        // A missing first byte after a successful speed and later FF is impossible in one contiguous
        // immutable extent. Fail the mandatory source Guard at that reached read instead; do not pad it.
        var pool = pool(); seed(pool, state(0, 0, 1, 0, 0xE1, 0xFF, 3, 8, 9));
        var failure = new IllegalStateException("source retired at FF first read");
        var trace = new Trace(pool) {
            @Override public void before(CdObjectAnimation.Operation op) {
                super.before(op); if (op.kind() == FF_FIRST) throw failure;
            }
        };
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> new CdObjectAnimation.Prefix(pool, SLOT, trace).step(single(3, 9, 0xFF))));
        assertEquals(state(0, 0, 0, 3, 0xE1, 0xFF, 3, 8, 9), read(pool.state(SLOT)));
    }

    @Test void everyReachedBeforeAndAfterCutRetainsExactlyItsCompletedStoresOnExceptionAndError() {
        for (var table : List.of(single(3, 0x25), single(3, 0xFF), single(3, 0xFE, 0),
                single(3, 0xFD, 255), single(3, 0xFC), single(3, 0xFB), single(3, 0xFA), single(3, 0x80))) {
            var controlPool = pool(); seed(controlPool, state(0, 9, 0, 0, 0xE1, 0xFF, 3, 255, 255));
            var control = new Trace(controlPool);
            new CdObjectAnimation.Prefix(controlPool, SLOT, control).step(table);
            for (int cut = 0; cut < control.kinds.size(); cut++) for (boolean after : new boolean[]{false, true})
                for (boolean error : new boolean[]{false, true}) {
                    var pool = pool(); seed(pool, state(0, 9, 0, 0, 0xE1, 0xFF, 3, 255, 255));
                    var expected = control.images.get(2 * cut + (after ? 1 : 0));
                    Throwable original = error ? new AssertionError("original") : new IllegalStateException("original");
                    final int selected = cut;
                    var trace = new Trace(pool) {
                        int at = -1;
                        @Override public void before(CdObjectAnimation.Operation op) {
                            super.before(op); if (++at == selected && !after) raise(original);
                        }
                        @Override public void after(CdObjectAnimation.Operation op) {
                            super.after(op); if (at == selected && after) raise(original);
                        }
                    };
                    var prefix = new CdObjectAnimation.Prefix(pool, SLOT, trace);
                    assertSame(original, assertThrows(Throwable.class, () -> prefix.step(table)));
                    for (int n = 0; n < 96; n++) assertArrayEquals(expected.slots().get(n).copyBytes(), pool.state(n + 32).copyBytes());
                    assertFalse(prefix.isCurrent(trace.last));
                }
        }
    }

    @Test void beforeFailureKeepsOriginalAndSuppressesAfterVerificationFailure() {
        var pool = pool(); var original = new AssertionError("before"); var secondary = new IllegalStateException("after");
        var trace = new Trace(pool) {
            @Override public void before(CdObjectAnimation.Operation op) { super.before(op); throw original; }
            @Override public void after(CdObjectAnimation.Operation op) { super.after(op); throw secondary; }
        };
        var prefix = new CdObjectAnimation.Prefix(pool, SLOT, trace);
        assertSame(original, assertThrows(AssertionError.class, () -> prefix.step(single(0, 1))));
        assertArrayEquals(new Throwable[]{secondary}, original.getSuppressed()); assertFalse(prefix.isCurrent(trace.last));
    }

    @Test void readFailureRemainsPrimaryWhenSourceGuardAlsoFailsOnUnwind() {
        var pool = pool(); seed(pool, initial()); var secondary = new AssertionError("after read");
        var trace = new Trace(pool) {
            @Override public void after(CdObjectAnimation.Operation op) { super.after(op); if (op.kind() == FRAME) throw secondary; }
        };
        var prefix = new CdObjectAnimation.Prefix(pool, SLOT, trace);
        var ex = assertThrows(CdObjectAnimation.SelectedReadFailure.class, () -> prefix.step(single(3)));
        assertEquals(FRAME, ex.boundary()); assertArrayEquals(new Throwable[]{secondary}, ex.getSuppressed());
        assertEquals(3, read(pool.state(SLOT)).timer()); assertFalse(prefix.isCurrent(trace.last));
    }

    @Test void issuedOperationsAreIdentityScopedThreadBoundAndRetiredAcrossFreshInvocations() throws Exception {
        var pool = pool(); seed(pool, initial()); var retained = new ArrayList<CdObjectAnimation.Operation>();
        var scope = new AtomicReference<CdObjectAnimation.Prefix>();
        var trace = new Trace(pool) {
            @Override public void before(CdObjectAnimation.Operation op) {
                super.before(op); assertTrue(scope.get().isCurrent(op));
                for (var old : retained) assertFalse(scope.get().isCurrent(old));
                assertThrows(IllegalStateException.class, () -> scope.get().step(single(0, 1)));
                var problem = new AtomicReference<Throwable>();
                Thread other = new Thread(() -> {
                    try { assertFalse(scope.get().isCurrent(op));
                        assertThrows(IllegalStateException.class, () -> scope.get().step(single(0, 1)));
                    } catch (Throwable ex) { problem.set(ex); }
                });
                other.start(); try { other.join(); } catch (InterruptedException ex) { throw new AssertionError(ex); }
                if (problem.get() != null) throw new AssertionError(problem.get());
                retained.add(op);
            }
            @Override public void after(CdObjectAnimation.Operation op) { super.after(op); assertTrue(scope.get().isCurrent(op)); }
        };
        scope.set(new CdObjectAnimation.Prefix(pool, SLOT, trace));
        assertFalse(scope.get().isCurrent(null)); scope.get().step(single(1, 1)); scope.get().step(single(1, 1));
        for (var op : retained) assertFalse(scope.get().isCurrent(op));
        var foreign = new CdObjectAnimation.Prefix(pool, SLOT, new Trace(pool));
        for (var op : retained) assertFalse(foreign.isCurrent(op));
    }

    @Test void callbackCannotSmuggleOwnNonAnimationOrNeighborWrites() {
        for (int slot : new int[]{SLOT, 32, 127}) {
            var pool = pool(); var trace = new Trace(pool) {
                @Override public void before(CdObjectAnimation.Operation op) {
                    super.before(op); pool.write(pool.state(slot).addByte(ID, 1));
                }
            };
            var prefix = new CdObjectAnimation.Prefix(pool, SLOT, trace);
            assertThrows(IllegalStateException.class, () -> prefix.step(single(0, 1)));
            assertFalse(prefix.isCurrent(trace.last));
        }
    }

    @Test void mandatoryGuardNullsSlotBoundsAndLegacyNullOrderRemainExplicit() {
        var pool = pool(); seed(pool, initial());
        assertThrows(NullPointerException.class, () -> new CdObjectAnimation.Prefix(pool, SLOT, null));
        assertThrows(NullPointerException.class, () -> new CdObjectAnimation.Prefix(null, SLOT, new Trace(pool)));
        assertThrows(IndexOutOfBoundsException.class, () -> new CdObjectAnimation.Prefix(pool, 31, new Trace(pool)));
        assertEquals("state", assertThrows(NullPointerException.class, () -> CdObjectAnimation.step(null, null)).getMessage());
        assertEquals("table", assertThrows(NullPointerException.class, () -> CdObjectAnimation.step(initial(), null)).getMessage());
        var prefix = new CdObjectAnimation.Prefix(pool, SLOT, new Trace(pool));
        assertThrows(NullPointerException.class, () -> prefix.step(null));
        prefix.step(single(0, 1));
    }

    @Test void signedWordTableAliasesHaveTheSamePrefixAndPureResults() {
        var table = table(4, 1, new byte[]{2, 0x46, (byte) 0xFF, 0, (byte) 0xFF, (byte) 0xFC});
        var pool = pool(); seed(pool, initial());
        assertEquals(CdObjectAnimation.step(initial(), table), new CdObjectAnimation.Prefix(pool, SLOT, new Trace(pool)).step(table));
    }

    private record Fault(CdObjectAnimation.AnimationTable table, CdObjectAnimation.State input,
                         CdObjectAnimation.Kind boundary, int timer, int index) { }
    private static class Trace implements CdObjectAnimation.Guard {
        final CdObjectPool pool; final List<String> stores = new ArrayList<>();
        final List<CdObjectAnimation.Kind> kinds = new ArrayList<>();
        final List<CdObjectPool.Snapshot> images = new ArrayList<>(); CdObjectAnimation.Operation last;
        Trace(CdObjectPool pool) { this.pool = pool; }
        @Override public void before(CdObjectAnimation.Operation op) {
            last = op; kinds.add(op.kind()); images.add(pool.capture()); assertEquals(SLOT, op.wholeIndex());
            assertFalse(op.completed());
        }
        @Override public void after(CdObjectAnimation.Operation op) {
            assertSame(last, op); images.add(pool.capture());
            if (op.kind() == STORE && op.completed()) stores.add(op.field() + "=" + op.value());
        }
    }
    private static void raise(Throwable ex) { if (ex instanceof Error e) throw e; throw (RuntimeException) ex; }
    private static CdObjectAnimation.State initial() { return state(0, 0, 0, 0, 0xE1, 0xFF, 3, 8, 9); }
    private static CdObjectAnimation.State state(int a, int p, int i, int t, int m, int s, int f, int r, int r2) {
        return new CdObjectAnimation.State(a, p, i, t, m, s, f, r, r2);
    }
    private static CdObjectAnimation.State read(CdObjectState s) {
        return state(s.unsignedByte(ANIMATION), s.unsignedByte(PREVIOUS_ANIMATION), s.unsignedByte(ANIMATION_FRAME),
                s.unsignedByte(ANIMATION_TIME), s.unsignedByte(MAP_FRAME), s.spriteFlags(), s.flags(), s.routine(), s.unsignedByte(ROUTINE_2));
    }
    private static CdObjectPool pool() {
        var pool = new CdObjectPool();
        for (int whole = 32; whole < 128; whole++) {
            var s = pool.state(whole);
            for (var f : CdObjectState.LongField.values()) s = s.moveLong(f, 0x96A5C37E ^ whole);
            for (var f : CdObjectState.ByteField.values()) s = s.moveByte(f, whole + 0x83);
            pool.write(s);
        }
        return pool;
    }
    private static void seed(CdObjectPool pool, CdObjectAnimation.State s) { pool.write(apply(pool.state(SLOT), s)); }
    private static CdObjectState apply(CdObjectState b, CdObjectAnimation.State s) {
        return b.moveByte(ANIMATION, s.animationId()).moveByte(PREVIOUS_ANIMATION, s.previousId())
                .moveByte(ANIMATION_FRAME, s.scriptIndex()).moveByte(ANIMATION_TIME, s.timer()).moveByte(MAP_FRAME, s.mapFrame())
                .moveByte(SPRITE_FLAGS, s.spriteFlags()).moveByte(FLAGS, s.statusFlags()).moveByte(ROUTINE, s.routine()).moveByte(ROUTINE_2, s.routine2());
    }
    private static void assertOnly(CdObjectPool pool, CdObjectPool.Snapshot before, int slot, CdObjectState.ByteField f, int v) {
        for (var b : before.slots()) assertArrayEquals((b.identity().wholeIndex() == slot ? b.moveByte(f, v) : b).copyBytes(),
                pool.state(b.identity().wholeIndex()).copyBytes());
    }
    private static void assertNonwrites(CdObjectPool pool, CdObjectPool.Snapshot before, CdObjectAnimation.State result) {
        for (var b : before.slots()) assertArrayEquals((b.identity().wholeIndex() == SLOT ? apply(b, result) : b).copyBytes(),
                pool.state(b.identity().wholeIndex()).copyBytes());
    }
    private static CdObjectAnimation.AnimationTable single(int speed, int... frames) {
        byte[] bytes = new byte[3 + frames.length]; bytes[1] = 2; bytes[2] = (byte) speed;
        for (int n = 0; n < frames.length; n++) bytes[3 + n] = (byte) frames[n];
        return table(0, 1, bytes);
    }
    private static CdObjectAnimation.AnimationTable table(int base, int count, byte[] bytes) {
        return new CdObjectAnimation.AnimationTable(CdAssetBytes.copyOf(bytes), base, count);
    }
}
