package com.openggf.level.objects;

import com.openggf.game.common.CommonObjectPlacementEncoding;
import com.openggf.level.AbstractLevel;
import com.openggf.level.LevelPlacementPlan;
import com.openggf.level.RegisteredLevelPlacements;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Added entries may sort ahead of native entries: state still belongs to the same native spawn on return. */
class TestAuthoredPlacementNativeState {
    private static final class NativeLevel extends AbstractLevel {
        final ObjectSpawn post = new ObjectSpawn(0x100, 0x150, 0x79, 2, 0, false, 0x150, 12);
        final ObjectSpawn mechanism = new ObjectSpawn(0x180, 0x150, 0x80, 0, 0, false, 0x150, 13);
        NativeLevel() { super(7); objects = List.of(post, mechanism); rings = List.of(); }
    }

    @Test void twoAxisNativeLowerBitsAndDestructionSurviveDeterministicAuthoredReturn() throws Exception {
        var source = source();
        NativeLevel original = new NativeLevel();
        source.install("s3k", 7, original, true);
        ObjectPlacementController beforeReturn = placement(original);
        beforeReturn.setCounterStateBit(original.post, 2);
        beforeReturn.setCounterStateBit(original.mechanism, 4);
        beforeReturn.markRemembered(original.mechanism);
        var persistent = beforeReturn.capturePersistentRespawn();

        NativeLevel returned = new NativeLevel();
        source.install("s3k", 7, returned, true);
        ObjectPlacementController afterReturn = placement(returned);
        afterReturn.restorePersistentRespawn(persistent);
        assertNotSame(original.post, returned.post, "return has actually recreated the native placement record");
        assertEquals(original.post, returned.post);
        assertEquals(original.post.layoutIndex(), returned.post.layoutIndex());
        assertTrue(afterReturn.isCounterStateBitSet(returned.post, 2));
        assertTrue(afterReturn.isCounterStateBitSet(returned.mechanism, 4));
        assertTrue(afterReturn.isRemembered(returned.mechanism));
        ObjectSpawn addition = returned.getObjects().getLast();
        assertFalse(afterReturn.isCounterStateBitSet(addition, 2));
        assertFalse(afterReturn.isCounterStateBitSet(addition, 4));
        assertFalse(afterReturn.isRemembered(addition));
        assertSame(addition, afterReturn.getAllSpawns().getFirst(), "addition exercises index shifts before native owners");

        var initial = afterReturn.captureRewindState(0, Integer.MIN_VALUE);
        afterReturn.update(0x100);
        var expected = afterReturn.captureRewindState(0, Integer.MIN_VALUE);
        for (int cycle = 0; cycle < 2; cycle++) {
            afterReturn.restoreRewindState(initial);
            afterReturn.update(0x100);
            var replayed = afterReturn.captureRewindState(0, Integer.MIN_VALUE);
            assertArrayEquals(expected.activeSpawnIndices(), replayed.activeSpawnIndices());
            assertArrayEquals(expected.objState(), replayed.objState());
            assertArrayEquals(expected.rememberedBits(), replayed.rememberedBits());
            assertTrue(afterReturn.isCounterStateBitSet(returned.post, 2));
            assertTrue(afterReturn.isRemembered(returned.mechanism));
        }
    }

    private static ObjectPlacementController placement(NativeLevel level) {
        ObjectPlacementController controller = new ObjectPlacementController(level.getObjects(), () -> 320);
        controller.setTwoAxisCursorPlacement(true);
        controller.reset(0);
        return controller;
    }

    private static RegisteredLevelPlacements source() throws Exception {
        var constructor = RegisteredLevelPlacements.class.getDeclaredConstructor(String.class, String.class,
                Map.class, Consumer.class, RegisteredLevelPlacements.class, ObjectPlacementEncoding.class);
        constructor.setAccessible(true);
        var value = new LevelPlacementPlan(new LevelPlacementPlan.Bounds(0x40, 0x100, 0x200, 0x200),
                List.of(), List.of(new LevelPlacementPlan.ObjectAddition("sentry", 0x80, 0x150, 0, 0)));
        Consumer<Runnable> boundary = Runnable::run;
        return constructor.newInstance("owner", "s3k", Map.of(7, value), boundary, null,
                new CommonObjectPlacementEncoding());
    }
}
