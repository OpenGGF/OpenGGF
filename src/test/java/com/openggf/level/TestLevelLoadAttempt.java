package com.openggf.level;

import com.openggf.game.GameModule;
import com.openggf.game.LevelInitProfile;
import com.openggf.game.LevelLoadContext;
import com.openggf.game.LevelLoadMode;
import com.openggf.level.objects.ObjectCallbackAbortException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Neutral callback aborts preserve typed recovery while load cleanup still runs. */
class TestLevelLoadAttempt {
    private static final class HostAbort extends ObjectCallbackAbortException {
        HostAbort(String message) { super(message, new IllegalStateException(message)); }
    }

    @Test void originalTypedAbortSurvivesAbortDuringCancellation() {
        var module = mock(GameModule.class);
        var profile = mock(LevelInitProfile.class);
        when(module.getLevelInitProfile()).thenReturn(profile);
        var original = new HostAbort("load owner unavailable");
        doThrow(new HostAbort("cleanup owner unavailable")).when(profile).cancelPendingLevelLoadWork();
        var discarded = new AtomicBoolean();
        var attempt = new LevelLoadAttempt(null, null, LevelLoadMode.FULL, new LevelLoadContext());

        assertSame(original, assertThrows(HostAbort.class,
                () -> attempt.failure(original, () -> module, () -> discarded.set(true), 0)));
        assertTrue(discarded.get());
        assertEquals(0, original.getSuppressed().length);
        verify(profile).cancelPendingLevelLoadWork();
    }

    @Test void ioCauseIsUnwrappedAfterQuarantinedCancellation() {
        var module = mock(GameModule.class);
        var profile = mock(LevelInitProfile.class);
        when(module.getLevelInitProfile()).thenReturn(profile);
        var cleanup = new HostAbort("cleanup owner unavailable");
        doThrow(cleanup).when(profile).cancelPendingLevelLoadWork();
        var io = new IOException("ROM unavailable");
        var original = new RuntimeException(io);
        var discarded = new AtomicBoolean();
        var attempt = new LevelLoadAttempt(null, null, LevelLoadMode.FULL, new LevelLoadContext());

        assertSame(io, attempt.failure(original, () -> module, () -> discarded.set(true), 0));
        assertTrue(discarded.get());
        assertArrayEquals(new Throwable[]{cleanup}, original.getSuppressed());
        verify(profile).cancelPendingLevelLoadWork();
    }
}
