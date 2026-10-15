package com.openggf.game.launch;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameModule;
import com.openggf.game.MasterTitleEntry;
import com.openggf.game.MasterTitleScreen.GameEntry;
import com.openggf.game.ZoneKey;
import com.openggf.game.ZoneRegistry;
import com.openggf.game.sonic2.Sonic2ZoneRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestHubGameplayProgress {
    @TempDir Path root;

    private GameModule module() {
        var module = mock(GameModule.class);
        when(module.getGameCode()).thenReturn("s2");
        when(module.getZoneRegistry()).thenReturn(new Sonic2ZoneRegistry());
        return module;
    }

    private MasterTitleEntry.Launch launch(MasterTitleEntry.Action action) {
        return new MasterTitleEntry.Launch(new MasterTitleEntry.Stock(GameEntry.SONIC_2), action);
    }

    @Test void reachedActPersistsAndContinueRestartsThroughRealCoordinatorCallback() {
        var config = SonicConfigurationService.createStandalone(root);
        var coordinator = new MasterTitleLaunchCoordinator(config);
        var tracker = new HubGameplayProgress(config);
        var module = module();
        tracker.prepare(launch(MasterTitleEntry.Action.NEW_GAME), false, coordinator, () -> module, (z, a) -> fail());
        tracker.observe(module, 1, 1, true);
        // Reopen configuration as a new application session would.
        var reopened = SonicConfigurationService.createStandalone(root);
        var next = new HubGameplayProgress(reopened);
        var starts = new AtomicReference<String>();
        next.prepare(launch(MasterTitleEntry.Action.CONTINUE), false, coordinator, () -> module,
                (z, a) -> starts.set(z + ":" + a));
        assertNull(starts.get(), "Continue waits until gameplay initialization has completed");
        coordinator.stagePendingLaunchCallback();
        coordinator.runAfterStepLaunchCallbackIfPresent();
        assertEquals("1:1", starts.get());
        starts.set(null);
        coordinator.runAfterStepLaunchCallbackIfPresent();
        assertNull(starts.get(), "the launch runs once");
    }

    @Test void replaySessionsCannotWriteAndDoNotReplaceTheBootstrapCallback() {
        var config = SonicConfigurationService.createStandalone(root);
        var store = spy(new HubProgressStore(config));
        var tracker = new HubGameplayProgress(store);
        var coordinator = new MasterTitleLaunchCoordinator(config);
        var bootstrap = new java.util.concurrent.atomic.AtomicInteger();
        coordinator.setPendingLaunchCallback(bootstrap::incrementAndGet);
        tracker.prepare(launch(MasterTitleEntry.Action.NEW_GAME), true, coordinator, this::module, (z,a) -> fail());
        tracker.observe(module(), 1, 0, true);
        verify(store, never()).reached(any(), anyInt(), anyInt(), anyString());
        coordinator.stagePendingLaunchCallback();
        coordinator.runAfterStepLaunchCallbackIfPresent();
        assertEquals(1, bootstrap.get());

        tracker.prepare(launch(MasterTitleEntry.Action.NEW_GAME), false, coordinator, this::module, (z,a) -> fail());
        tracker.observe(module(), 1, 0, false); // recording / bonus / title mode
        verify(store, never()).reached(any(), anyInt(), anyInt(), anyString());
    }

    @Test void unchangedFramesAreNotWritesAndModZonesAndEndedSessionsAreIgnored() {
        var config = SonicConfigurationService.createStandalone(root);
        var store = spy(new HubProgressStore(config));
        var tracker = new HubGameplayProgress(store);
        var module = module();
        tracker.prepare(launch(MasterTitleEntry.Action.NEW_GAME), false,
                new MasterTitleLaunchCoordinator(config), () -> module, (z,a) -> fail());
        tracker.observe(module, 0, 0, true);
        tracker.observe(module, 0, 0, true);
        verify(store, times(1)).reached(GameEntry.SONIC_2, 0, 0, "sonic");
        var zones = mock(ZoneRegistry.class);
        when(zones.getZoneCount()).thenReturn(2);
        when(zones.getActCount(1)).thenReturn(2);
        when(zones.zoneKey(1)).thenReturn(ZoneKey.mod("custom-mod", "stage"));
        when(module.getZoneRegistry()).thenReturn(zones);
        tracker.observe(module, 1, 0, true);
        tracker.observe(module, 40, 0, true);
        verify(store, times(1)).reached(any(), anyInt(), anyInt(), anyString());
        tracker.end();
        tracker.observe(module(), 1, 0, true);
        verify(store, times(1)).reached(any(), anyInt(), anyInt(), anyString());
    }
}
