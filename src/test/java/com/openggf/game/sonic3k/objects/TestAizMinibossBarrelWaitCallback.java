package com.openggf.game.sonic3k.objects;

import com.openggf.data.RomByteReader;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.lang.reflect.Field;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Obj_Wait callback/tail contract, driven through ordinary barrel activation and first burst. */
class TestAizMinibossBarrelWaitCallback {
    @BeforeEach void init() { GraphicsManager.getInstance().initHeadless(); }
    @AfterEach void reset() {
        AbstractObjectInstance.resetCameraBoundsForTests();
        GraphicsManager.getInstance().resetState();
    }

    @Test void native1cWaitHoldsZeroThenAllocatesTheNextPairOnTheUnderflowPass() throws Exception {
        var h = harness(320);
        int first = firstBurst(h);
        assertEquals(2, h.barrel(7).getPositionCounter());
        assertEquals(0x1C, field(h.barrel(7), "timer"));
        for (int pass = 1; pass <= 0x1C; pass++) h.step(first + pass);
        assertEquals(1, shots(h), "zero is still a held native wait");
        assertEquals(0, field(h.barrel(7), "timer"));
        h.step(first + 0x1D);
        assertEquals(2, shots(h), "Obj_Wait calls FireShot on this same underflow dispatch");
        assertEquals(1, h.barrel(7).getPositionCounter());
        assertEquals(1, h.manager.getActiveObjects().stream()
                .filter(AizMinibossNapalmProjectile.class::isInstance)
                .filter(o -> {
                    try { return field(o,"barrel") == h.barrel(7); }
                    catch(Exception e) { throw new AssertionError(e); }
                }).count(), "the second callback's literal ChildObjDat selects FallingShot");
        assertEquals(0x1C, field(h.barrel(7), "timer"));
        assertEquals("BETWEEN_SHOTS", field(h.barrel(7), "state").toString());
    }

    @Test void fourthCallbackAllocatesBeforeTheNegativeCounterTailEntersReturn() throws Exception {
        var h = harness(320);
        int first = firstBurst(h);
        for (int pass = 1; pass < 3 * 0x1D; pass++) h.step(first + pass);
        assertEquals(3, shots(h));
        assertEquals(0, h.barrel(7).getPositionCounter());
        assertEquals("BETWEEN_SHOTS", field(h.barrel(7), "state").toString());
        h.step(first + 3 * 0x1D);
        assertEquals(4, shots(h), "the fourth pair is allocated before WaitAfterShot's signed-counter tail");
        assertEquals(0xFF, h.barrel(7).getPositionCounter(), "native signed-negative byte $39");
        assertEquals("CLOSING", field(h.barrel(7), "state").toString(),
                "native Return is selected on the callback's dispatch, not the next frame");
    }

    @ParameterizedTest @ValueSource(ints = {320, 352, 400, 528, 800})
    void bothCallbackBoundariesRecreateAndReplayForTwo45PassCycles(int width) throws Exception {
        var h = harness(width);
        h.manager.setRewindInPlaceRestoreEnabledForTest(false);
        int first = firstBurst(h);
        int current = first;
        for (int boundary : new int[]{first + 0x1D, first + 3 * 0x1D}) {
            while (current < boundary - 1) h.step(++current);
            var rewind = h.manager.rewindSnapshottable();
            var before = rewind.capture();
            h.step(boundary);
            var callback = rewind.capture();
            assertEquals(boundary == first + 0x1D ? 2 : 4, shots(h));
            for (int frame = boundary + 1; frame <= boundary + 45; frame++) h.step(frame);
            var after = rewind.capture();
            for (int cycle = 0; cycle < 2; cycle++) {
                rewind.restore(before); same(before, rewind.capture());
                h.step(boundary); same(callback, rewind.capture());
                for (int frame = boundary + 1; frame <= boundary + 45; frame++) h.step(frame);
                same(after, rewind.capture());
                rewind.restore(callback); same(callback, rewind.capture());
                for (int frame = boundary + 1; frame <= boundary + 45; frame++) h.step(frame);
                same(after, rewind.capture());
                rewind.restore(before); same(before, rewind.capture());
            }
            h.step(boundary); current = boundary;
        }
    }

    private static TestAizMinibossFlareNativeGraph.Harness harness(int width) {
        var h = new TestAizMinibossFlareNativeGraph.Harness(width);
        h.services.withRomReader(TestAizMinibossFallingShotNativeGraph.rawReader());
        h.boss().setCustomFlag(0x38, 2); // declared native StartSwing trigger, no barrel phase/clock seeding
        return h;
    }
    private static int firstBurst(TestAizMinibossFlareNativeGraph.Harness h) throws Exception {
        for (int frame = 0; frame < 100; frame++) {
            h.step(frame);
            if (shots(h) == 1) {
                assertEquals("BETWEEN_SHOTS", field(h.barrel(7), "state").toString());
                return frame;
            }
        }
        fail("ordinary activated barrel never allocated its first pair"); return -1;
    }
    private static int shots(TestAizMinibossFlareNativeGraph.Harness h) throws Exception {
        int count = 0;
        for (var object : h.manager.getActiveObjects()) {
            if ((object instanceof AizMinibossBarrelShotChild || object instanceof AizMinibossNapalmProjectile)
                    && field(object, "barrel") == h.barrel(7)) count++;
        }
        return count;
    }
    private static Object field(Object object, String name) throws Exception {
        Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object);
    }
    private static void same(Object expected, Object actual) {
        var diff = RewindSnapshotDiff.diffKey("object-manager", expected, actual);
        assertTrue(diff.isEmpty(), () -> diff.toString());
    }
}
