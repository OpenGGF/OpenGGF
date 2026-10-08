package com.openggf.game.session;

import com.openggf.game.sonic2.Sonic2GameModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Isolated
class TestScheduledPlaybackInputPublisher {
    @BeforeEach
    void configureServices() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
    }

    @Test
    void aStaleOwnerCannotClearTheReplacementPublisher() {
        var context = new GameplayModeContext(new WorldSession(new Sonic2GameModule()));
        AtomicInteger oldCalls = new AtomicInteger();
        AtomicInteger newCalls = new AtomicInteger();
        Runnable oldPublisher = oldCalls::incrementAndGet;
        Runnable newPublisher = newCalls::incrementAndGet;
        context.setScheduledPlaybackInputPublisher(oldPublisher);
        context.setScheduledPlaybackInputPublisher(newPublisher);
        context.clearScheduledPlaybackInputPublisher(oldPublisher);
        context.publishScheduledPlaybackInput();
        assertEquals(0, oldCalls.get());
        assertEquals(1, newCalls.get());
        context.clearScheduledPlaybackInputPublisher(newPublisher);
        context.publishScheduledPlaybackInput();
        assertEquals(1, newCalls.get());
    }

    @Test
    void teardownDisablesPublicationBeforeReentrantCloseHooks() {
        var context = new GameplayModeContext(new WorldSession(new Sonic2GameModule()));
        AtomicInteger calls = new AtomicInteger();
        context.setScheduledPlaybackInputPublisher(calls::incrementAndGet);
        context.setHardwareTimingReplayCloseHook(() -> {
            context.publishScheduledPlaybackInput();
            context.setScheduledPlaybackInputPublisher(calls::incrementAndGet);
            context.publishScheduledPlaybackInput();
        });
        context.tearDownManagers();
        context.publishScheduledPlaybackInput();
        assertEquals(0, calls.get());
    }
}
