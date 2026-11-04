package com.openggf.level;

import com.openggf.game.LevelLoadMode;
import com.openggf.game.internal.NativeLevelLoadPhaseAdmission;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestNativeLevelLoadTimingRunAdmission {
    @Test void ordinaryLoadsKeepTheSynchronousBoundaryUntilAClientIsExplicitlyInstalled() {
        var manager = mock(LevelManager.class);
        manager.nativeLoads = new NativeLevelLoadController();
        assertFalse(NativeLevelLoadAccess.hasPhaseAdmission(manager));
        NativeLevelLoadAccess.setAdmission(manager, phase -> NativeLevelLoadPhaseAdmission.Decision.RESUME);
        assertTrue(NativeLevelLoadAccess.hasPhaseAdmission(manager));
        NativeLevelLoadAccess.setAdmission(manager, NativeLevelLoadPhaseAdmission.IMMEDIATE);
        assertFalse(NativeLevelLoadAccess.hasPhaseAdmission(manager));
    }

    @Test void explicitRepresentedRunResetsOrdinalBeforeInstallingItsAdmission() {
        var manager = mock(LevelManager.class);
        manager.nativeLoads = new NativeLevelLoadController();
        manager.nativeLoads.ordinal = 9;
        NativeLevelLoadPhaseAdmission admission = phase -> NativeLevelLoadPhaseAdmission.Decision.HOLD;
        NativeLevelLoadAccess.beginRepresentedTimingRun(manager, admission);
        assertEquals(0, manager.nativeLoads.ordinal);
        assertSame(admission, manager.nativeLoads.admission);
    }

    @Test void cannotRebaseOrReplaceAdmissionDuringARealPendingLoad() {
        var manager = mock(LevelManager.class);
        manager.nativeLoads = new NativeLevelLoadController();
        manager.nativeLoads.ordinal = 9;
        manager.nativeLoads.admission = NativeLevelLoadPhaseAdmission.IMMEDIATE;
        manager.nativeLoads.pending = new NativeLevelLoadController.PendingLoad(
                null, null, 0, LevelLoadMode.FULL, null);
        assertThrows(IllegalStateException.class, () -> NativeLevelLoadAccess.beginRepresentedTimingRun(
                manager, phase -> NativeLevelLoadPhaseAdmission.Decision.HOLD));
        assertEquals(9, manager.nativeLoads.ordinal);
        assertSame(NativeLevelLoadPhaseAdmission.IMMEDIATE, manager.nativeLoads.admission);
    }
}
