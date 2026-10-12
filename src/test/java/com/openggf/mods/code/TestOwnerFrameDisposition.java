package com.openggf.mods.code;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.game.mode.CourseControl;
import com.openggf.game.mode.GameplayFrameController;
import com.openggf.game.mode.GameplayFrameDisposition;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.mods.runtime.OwnerBoundCallbacks;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestOwnerFrameDisposition {
    static GameplayFrameController bound(GameplayFrameController controller) {
        var boundary = new ModFaultBoundary(Map.of("dependent", Set.of("owner")), new ModRuntimeFindingStore(),
                ignored -> new ModStateSaveResult.Saved(), ignored -> { });
        return new OwnerBoundCallbacks("owner", boundary, 8, Map.of()).bind(GameplayFrameController.class, controller);
    }
    @Test void newDecisionAndLegacyMappingRunWithinTheVerifiedOwnerOnce() {
        int[] calls = {0, 0};
        var nativeController = bound(new GameplayFrameController() {
            public boolean beforeTick(CourseControl c, LogicalInputSnapshot i) { fail("new callback replaces the legacy decision"); return false; }
            public GameplayFrameDisposition beforeFrame(CourseControl c, LogicalInputSnapshot i) {
                assertEquals("owner", OwnerCallbackScope.current()); calls[0]++; return GameplayFrameDisposition.ADVANCE_NATIVE_INPUT;
            }
            public void afterTick(CourseControl c, boolean advanced) { }
        });
        assertEquals(GameplayFrameDisposition.ADVANCE_NATIVE_INPUT, nativeController.beforeFrame(null, LogicalInputSnapshot.neutral()));
        var legacy = bound(new GameplayFrameController() {
            public boolean beforeTick(CourseControl c, LogicalInputSnapshot i) { assertEquals("owner", OwnerCallbackScope.current()); calls[1]++; return true; }
            public void afterTick(CourseControl c, boolean advanced) { }
        });
        assertEquals(GameplayFrameDisposition.ADVANCE_CONTROLLED, legacy.beforeFrame(null, LogicalInputSnapshot.neutral()));
        assertArrayEquals(new int[]{1, 1}, calls);
        assertNull(OwnerCallbackScope.current());
    }
    @Test void nullDecisionIsAnOwnerAttributedFault() {
        var controller = bound(new GameplayFrameController() {
            public boolean beforeTick(CourseControl c, LogicalInputSnapshot i) { return false; }
            public GameplayFrameDisposition beforeFrame(CourseControl c, LogicalInputSnapshot i) { return null; }
            public void afterTick(CourseControl c, boolean advanced) { }
        });
        var failure = assertThrows(ModFaultBoundary.CallbackAborted.class,
                () -> controller.beforeFrame(null, LogicalInputSnapshot.neutral()));
        assertEquals("owner", failure.owner());
        assertInstanceOf(NullPointerException.class, failure.getCause());
        assertNull(OwnerCallbackScope.current());
    }
}
