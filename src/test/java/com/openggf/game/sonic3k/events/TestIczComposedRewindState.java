package com.openggf.game.sonic3k.events;

import java.nio.ByteBuffer;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestIczComposedRewindState {
    @Test void allShakeWordsRestoreAndMalformedPayloadsRejectAtomically() {
        var source = new Sonic3kICZEvents();
        source.triggerScreenShake(30);
        byte[] saved = source.captureIczState();
        assertTrue(Sonic3kICZEvents.hasValidIczState(saved));
        var target = new Sonic3kICZEvents();
        target.restoreIczState(saved);
        assertArrayEquals(saved, target.captureIczState());
        for (byte[] bad : new byte[][] {new byte[0], Arrays.copyOf(saved, saved.length - 1),
                Arrays.copyOf(saved, saved.length + 1), corrupt(saved, 0, 0),
                corrupt(saved, 4, 2), corrupt(saved, 8, -1),
                corrupt(saved, saved.length - 12, 65536),
                corrupt(saved, saved.length - 8, 65536), corrupt(saved, saved.length - 4, 65536)}) {
            assertFalse(Sonic3kICZEvents.hasValidIczState(bad));
            assertThrows(IllegalArgumentException.class, () -> target.restoreIczState(bad));
            assertArrayEquals(saved, target.captureIczState(), "rejection must preserve every word");
        }
        assertThrows(IllegalArgumentException.class, () -> target.restoreIczState(null));
        assertArrayEquals(saved, target.captureIczState());
    }
    @Test void malformedComponentCannotMutateManagerBaseState() {
        com.openggf.tests.TestEnvironment.resetAll();
        try {
            var manager = new com.openggf.game.sonic3k.Sonic3kLevelEventManager();
            manager.initLevel(com.openggf.game.sonic3k.constants.Sonic3kZoneIds.ZONE_ICZ, 0);
            manager.getIczEvents().triggerScreenShake(30);
            var before = manager.capture();
            byte[] extra = before.extra();
            int start = componentStart(extra, manager.getIczEvents().captureIczState());
            assertEquals(0x49435A31, ByteBuffer.wrap(extra).getInt(start));
            ByteBuffer.wrap(extra).putInt(start + 4, 99);
            var bad = new com.openggf.game.rewind.snapshot.LevelEventSnapshot(
                    99, 99, 99, 99, 99, 99, true, new short[8], new byte[8], extra);
            manager.restore(bad);
            var after = manager.capture();
            assertEquals(before.currentZone(), after.currentZone());
            assertEquals(before.currentAct(), after.currentAct());
            assertEquals(before.frameCounter(), after.frameCounter());
            assertArrayEquals(before.extra(), after.extra());
        } finally {
            com.openggf.game.session.SessionManager.clear();
        }
    }

    @Test void rejectsUnreachableShakeSamples() throws Exception {
        for (String field : new String[] {"screenShakeAppliedOffsetX", "screenShakeAppliedOffsetY"}) {
            assertInvalidField(field, -6);
            assertInvalidField(field, 6);
        }
        for (int at : new int[] {8, 4}) {
            byte[] valid = new Sonic3kICZEvents().captureIczState();
            assertFalse(Sonic3kICZEvents.hasValidIczState(corrupt(valid, valid.length - at, -6)));
            assertFalse(Sonic3kICZEvents.hasValidIczState(corrupt(valid, valid.length - at, 6)));
        }
    }

    @Test void rejectsUnreachableSnowValues() throws Exception {
        assertInvalidField("bigSnowOffset", 1);
        assertInvalidField("bigSnowOffset", -303);
        assertInvalidField("bigSnowOffsetSubpixels", 1);
        assertInvalidField("bigSnowOffsetSubpixels", (-302 << 16) - 1);
        assertInvalidField("bigSnowVelocity", -0x2400);
        assertInvalidField("bigSnowVelocity", 66 * 0x2400);
        assertInvalidField("bigSnowVelocity", 1);
    }

    @Test void rejectsUnreachablePhaseValues() throws Exception {
        assertInvalidField("introAutoAccumulator", 1);
        assertInvalidField("introAutoPhase", 1);
        assertInvalidField("introAutoAccumulator", 0x1000);
        assertInvalidField("introAutoPhase", 0x800);
    }

    @Test void acceptsReachableSnowTrajectoryAndWrappedPhaseEdges() throws Exception {
        int sub = 0;
        var source = new Sonic3kICZEvents();
        for (int step = 0; step <= 65; step++) {
            if (step > 0) sub = Math.max(-302 << 16, sub - step * 0x2400);
            put(source, "bigSnowVelocity", step * 0x2400);
            put(source, "bigSnowOffsetSubpixels", sub);
            put(source, "bigSnowOffset", sub >> 16);
            assertTrue(Sonic3kICZEvents.hasValidIczState(source.captureIczState()), "snow step " + step);
        }
        for (int phase : new int[] {0, 0x7FFFF800, 0x80000000, 0xFFFFF800}) {
            put(source, "introAutoPhase", phase);
            put(source, "introAutoAccumulator", phase + 0x800);
            assertTrue(Sonic3kICZEvents.hasValidIczState(source.captureIczState()), "wrapped phase");
        }
        for (int offset : new int[] {-5, 0, 5}) {
            put(source, "screenShakeAppliedOffsetX", offset);
            put(source, "screenShakeAppliedOffsetY", offset);
            byte[] payload = source.captureIczState();
            ByteBuffer.wrap(payload).putInt(payload.length - 8, offset).putInt(payload.length - 4, offset);
            assertTrue(Sonic3kICZEvents.hasValidIczState(payload), "shake edge " + offset);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void unexpectedValidatedRestoreFailureReachesCaller(boolean failureInFacadeCleanup) throws Exception {
        com.openggf.tests.TestEnvironment.resetAll();
        try {
            var manager = new com.openggf.game.sonic3k.Sonic3kLevelEventManager();
            manager.initLevel(com.openggf.game.sonic3k.constants.Sonic3kZoneIds.ZONE_ICZ, 0);
            var saved = manager.capture();
            var events = org.mockito.Mockito.spy(manager.getIczEvents());
            var failure = new IllegalStateException("validated ICZ restore failure");
            if (failureInFacadeCleanup) {
                org.mockito.Mockito.doThrow(failure).doCallRealMethod().when(events)
                        .discardHardwareWorkFacadesAfterRewind();
            } else {
                org.mockito.Mockito.doThrow(failure).doCallRealMethod().when(events)
                        .restoreIczState(org.mockito.ArgumentMatchers.any(byte[].class));
            }
            var field = manager.getClass().getDeclaredField("iczEvents");
            field.setAccessible(true);
            field.set(manager, events);
            assertSame(failure, assertThrows(IllegalStateException.class, () -> manager.restore(saved)),
                    "a validated restore must not become a logged malformed-input fallback");
        } finally {
            com.openggf.game.session.SessionManager.clear();
        }
    }

    private static void assertInvalidField(String name, int value) throws Exception {
        var target = new Sonic3kICZEvents();
        byte[] before = target.captureIczState();
        var source = new Sonic3kICZEvents();
        put(source, name, value);
        byte[] bad = source.captureIczState();
        assertFalse(Sonic3kICZEvents.hasValidIczState(bad), name + "=" + value);
        assertThrows(IllegalArgumentException.class, () -> target.restoreIczState(bad));
        assertArrayEquals(before, target.captureIczState());
    }

    private static void put(Object owner, String name, int value) throws Exception {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setInt(owner, value);
    }

    private static int componentStart(byte[] extra, byte[] component) {
        int found = -1;
        for (int at = 4; at <= extra.length - component.length; at++) {
            if (ByteBuffer.wrap(extra).getInt(at - 4) == component.length
                    && Arrays.equals(component, Arrays.copyOfRange(extra, at, at + component.length))) {
                assertEquals(-1, found, "component framing must be unique");
                found = at;
            }
        }
        assertTrue(found >= 0, "find framed ICZ component independently of sibling sidecars");
        return found;
    }

    private static byte[] corrupt(byte[] source, int at, int value) {
        byte[] result = source.clone();
        ByteBuffer.wrap(result).putInt(at, value);
        return result;
    }
}
