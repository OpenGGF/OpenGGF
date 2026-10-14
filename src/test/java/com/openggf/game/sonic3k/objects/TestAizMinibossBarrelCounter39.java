package com.openggf.game.sonic3k.objects;

import com.openggf.data.RomByteReader;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.NativeSstSlotAccess;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.lang.reflect.Field;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Literal shared byte39 roles; component manager replay, not native scene or route products. */
class TestAizMinibossBarrelCounter39 {
    @BeforeEach void init() { GraphicsManager.getInstance().initHeadless(); }
    @AfterEach void reset() {
        AbstractObjectInstance.resetCameraBoundsForTests();
        GraphicsManager.getInstance().resetState();
    }

    @ParameterizedTest @ValueSource(ints = {0, 1})
    void fourthFireByteFeedsTheFirstNativeDropColumn(int flip) throws Exception {
        var h = harness(320, flip);
        int first = firstBurst(h);
        int afterFirstFire = h.barrel(7).getPositionCounter();
        for (int pass = 1; pass <= 105; pass++) h.step(first + pass);
        int before = h.barrel(7).getPositionCounter();
        h.step(first + 106); // 97 rise movements + 9 pause dispatches, after stationary Init
        assertAll("native $FF + 4 wraps to3 before masking $C",
                () -> assertEquals(2, afterFirstFire, "FireShot decrements the same byte39 read by SetShotPosition"),
                () -> assertEquals(0xFF, before),
                () -> assertEquals(3, h.barrel(7).getPositionCounter()),
                () -> assertEquals(0x2F00 + (flip == 0 ? 0x74 : 0xF4), firstShot(h).getX()),
                () -> assertEquals(0x300 - 0x20, firstShot(h).getY()));
    }

    @ParameterizedTest @ValueSource(ints = {0xFE, 0xFF})
    void aWrittenNegativeByteIsReadByTheWaitTailAndWrapsInLaunch(int initial) throws Exception {
        var h = harness(320, 0);
        int first = firstBurst(h);
        h.barrel(7).setPositionCounter(initial); // explicit native byte39 header vector
        h.step(first + 1);
        assertEquals("CLOSING", field(h.barrel(7), "state").toString(),
                "WaitAfterShot's signed test must consume the same written byte");
        for (int pass = 2; pass <= 106; pass++) h.step(first + pass);
        assertEquals((initial + 4) & 0xFF, h.barrel(7).getPositionCounter());
        assertEquals(0x2F74, firstShot(h).getX());
    }

    @ParameterizedTest @ValueSource(ints = {320, 352, 400, 528, 800})
    void firstLaunchAliasRecreatesAndReplaysBothWaysForTwo45PassCycles(int width) throws Exception {
        var h = harness(width, 0);
        h.manager.setRewindInPlaceRestoreEnabledForTest(false);
        int first = firstBurst(h);
        for (int pass = 1; pass <= 105; pass++) h.step(first + pass);
        assertEquals(0xFF, h.barrel(7).getPositionCounter());
        var rewind = h.manager.rewindSnapshottable();
        var before = rewind.capture();
        h.step(first + 106);
        assertEquals(3, h.barrel(7).getPositionCounter());
        assertEquals(0x2F74, firstShot(h).getX());
        var launched = rewind.capture();
        for (int pass = 107; pass <= 151; pass++) h.step(first + pass);
        var after = rewind.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            rewind.restore(before); same(before, rewind.capture());
            h.step(first + 106); same(launched, rewind.capture());
            for (int pass = 107; pass <= 151; pass++) h.step(first + pass);
            same(after, rewind.capture());
            rewind.restore(launched); same(launched, rewind.capture());
            for (int pass = 107; pass <= 151; pass++) h.step(first + pass);
            same(after, rewind.capture());
            rewind.restore(before); same(before, rewind.capture());
        }
    }

    private static TestAizMinibossFlareNativeGraph.Harness harness(int width, int flip) {
        var h = new TestAizMinibossFlareNativeGraph.Harness(width);
        var rom = mock(RomByteReader.class);
        when(rom.slice(0x6913F, 13)).thenReturn(new byte[]{7,1,7,1,8,1,9,3,10,3,11,3,(byte)0xF4});
        when(rom.slice(0x6914C, 4)).thenReturn(new byte[]{1,0x0C,0x0D,(byte)0xFC});
        h.services.withRomReader(rom);
        h.boss().setCustomFlag(0x38, 2);
        h.boss().getState().renderFlags = flip;
        return h;
    }
    private static int firstBurst(TestAizMinibossFlareNativeGraph.Harness h) {
        for (int frame = 0; frame < 100; frame++) {
            h.step(frame);
            if (NativeSstSlotAccess.currentOwner(h.manager, 10) instanceof AizMinibossBarrelShotChild) return frame;
        }
        fail("ordinary activated barrel never allocated its first pair"); return -1;
    }
    private static AizMinibossBarrelShotChild firstShot(TestAizMinibossFlareNativeGraph.Harness h) {
        return (AizMinibossBarrelShotChild) NativeSstSlotAccess.currentOwner(h.manager, 10);
    }
    private static Object field(Object object, String name) throws Exception {
        Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object);
    }
    private static void same(Object expected, Object actual) {
        var diff = RewindSnapshotDiff.diffKey("object-manager", expected, actual);
        assertTrue(diff.isEmpty(), () -> diff.toString());
    }
}
