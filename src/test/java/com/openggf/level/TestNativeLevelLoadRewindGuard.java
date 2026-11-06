package com.openggf.level;

import com.openggf.game.LevelLoadMode;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.level.rewind.LevelRewindSnapshotAdapter;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestNativeLevelLoadRewindGuard {
    @Test
    void pendingLoadRejectsRegistryOperationsBeforeAnyOtherOwnerRuns() {
        var manager = mock(LevelManager.class);
        manager.nativeLoads = new NativeLevelLoadController();
        manager.nativeLoads.pending = new NativeLevelLoadController.PendingLoad(
                null, null, 0, LevelLoadMode.FULL, null);
        var calls = new AtomicInteger();
        var registry = new RewindRegistry();
        registry.register(new RewindSnapshottable<Integer>() {
            public String key() { return "earlier-owner"; }
            public Integer capture() { calls.incrementAndGet(); return 1; }
            public void restore(Integer snapshot) { calls.incrementAndGet(); }
        });
        var levelAdapter = LevelRewindSnapshotAdapter.create(manager);
        registry.register(levelAdapter);
        var snapshot = mock(CompositeSnapshot.class);
        assertThrows(IllegalStateException.class, registry::capture);
        assertThrows(IllegalStateException.class, registry::captureCourse);
        assertThrows(IllegalStateException.class, () -> registry.restore(snapshot));
        assertThrows(IllegalStateException.class, () -> registry.restoreCourse(snapshot));
        assertEquals(0, calls.get());
        verifyNoInteractions(snapshot);
        assertThrows(IllegalStateException.class, levelAdapter::capture);
        assertThrows(IllegalStateException.class, () -> levelAdapter.restore(null));
        verify(manager, never()).discardPreparedLevelLoad();
    }

    @Test
    void guardAllowsStableBoundariesBeforeAndAfterTheContinuation() {
        var manager = mock(LevelManager.class);
        manager.nativeLoads = new NativeLevelLoadController();
        assertDoesNotThrow(() -> NativeLevelLoadAccess.requireComplete(manager));
        manager.nativeLoads.pending = new NativeLevelLoadController.PendingLoad(
                null, null, 0, LevelLoadMode.FULL, null);
        assertThrows(IllegalStateException.class, () -> NativeLevelLoadAccess.requireComplete(manager));
        manager.nativeLoads.pending = null;
        assertDoesNotThrow(() -> NativeLevelLoadAccess.requireComplete(manager));
    }
}
