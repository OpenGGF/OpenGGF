package com.openggf.tools;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameModule;
import com.openggf.game.startup.DonatedDataSelectWarmupTask;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Headless title boot runs the same donated Data Select warm-up as Engine's startup. */
class TestHeadlessDonatedDataSelectWarmup {

    @Test
    void s3kDonationStartsTheHostWarmupAndPumpsRenderWorkUntilItSettles() {
        SonicConfigurationService config = donation("s3k");
        GameModule module = mock(GameModule.class);
        DonatedDataSelectWarmupTask task = mock(DonatedDataSelectWarmupTask.class);
        when(module.getDonatedDataSelectWarmupTask()).thenReturn(Optional.of(task));
        when(task.isRunning()).thenReturn(true, true, false);
        AtomicInteger pumps = new AtomicInteger();

        HeadlessGameBoot.warmUpDonatedDataSelect(module, config, true, pumps::incrementAndGet);

        verify(task).start();
        assertEquals(3, pumps.get(), "two pumps while running, then one to drain the queue");
    }

    @Test
    void withoutAnActiveS3kDonorNoWarmupStarts() {
        GameModule module = mock(GameModule.class);
        DonatedDataSelectWarmupTask task = mock(DonatedDataSelectWarmupTask.class);
        when(module.getDonatedDataSelectWarmupTask()).thenReturn(Optional.of(task));
        Runnable pump = mock(Runnable.class);

        HeadlessGameBoot.warmUpDonatedDataSelect(module, donation("s2"), true, pump);
        HeadlessGameBoot.warmUpDonatedDataSelect(module, donation("s3k"), false, pump);

        verify(task, never()).start();
        verify(pump, never()).run();
    }

    private static SonicConfigurationService donation(String source) {
        SonicConfigurationService config = mock(SonicConfigurationService.class);
        when(config.getBoolean(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED)).thenReturn(true);
        when(config.getString(SonicConfiguration.CROSS_GAME_SOURCE)).thenReturn(source);
        return config;
    }
}
