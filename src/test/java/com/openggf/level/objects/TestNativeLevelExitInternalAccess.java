package com.openggf.level.objects;

import com.openggf.game.session.EngineContext;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSession;
import com.openggf.level.LevelManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestNativeLevelExitInternalAccess {
    @Test
    void currentInjectedOwnerIsNotResolvedThroughTheAmbientSession() {
        Harness h = harness();
        try (var ambient = mockStatic(SessionManager.class)) {
            assertTrue(NativeLevelExitInternalAccess.endSourceComparisonAtRomModeChange(h.services()));
            ambient.verifyNoInteractions();
        }
        verify(h.mode()).endDynamicArtComparisonSegmentAtRomModeChange();
    }

    @Test
    void reattachedLevelManagerCannotBeClosedByRetiredServices() {
        Harness h = harness();
        when(h.mode().getLevelManager()).thenReturn(mock(LevelManager.class));
        assertRejected(h);
    }

    @Test
    void reattachedWorldCannotBeClosedByRetiredServices() {
        Harness h = harness();
        when(h.mode().getWorldSession()).thenReturn(mock(WorldSession.class));
        assertRejected(h);
    }

    @Test
    void replacementObjectManagerInTheSameLevelRejectsOldServices() {
        Harness h = harness();
        ObjectManager replacement = mock(ObjectManager.class);
        when(replacement.getObjectServices()).thenReturn(mock(ObjectServices.class));
        when(h.mode().getLevelManager().getObjectManager()).thenReturn(replacement);
        assertRejected(h);
    }

    @Test
    void disposedContextCannotCloseAnotherWorld() {
        Harness h = harness();
        when(h.mode().getLevelManager()).thenReturn(null);
        assertRejected(h);
    }

    @Test
    void standaloneObjectHostHasNoComparisonWindowOwner() {
        assertFalse(NativeLevelExitInternalAccess.endSourceComparisonAtRomModeChange(new TestObjectServices()));
    }

    private static void assertRejected(Harness h) {
        assertFalse(NativeLevelExitInternalAccess.endSourceComparisonAtRomModeChange(h.services()));
        verify(h.mode(), never()).endDynamicArtComparisonSegmentAtRomModeChange();
    }

    private static Harness harness() {
        GameplayModeContext mode = mock(GameplayModeContext.class, RETURNS_DEEP_STUBS);
        DefaultObjectServices services = new DefaultObjectServices(mode, mock(EngineContext.class));
        when(mode.getLevelManager().getObjectManager().getObjectServices()).thenReturn(services);
        return new Harness(mode, services);
    }

    private record Harness(GameplayModeContext mode, DefaultObjectServices services) { }
}
