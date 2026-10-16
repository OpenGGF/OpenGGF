package com.openggf.game;

import com.openggf.configuration.SonicConfigurationService;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import static com.openggf.configuration.SonicConfiguration.*;
import static org.junit.jupiter.api.Assertions.*;

class TestHubMusicController {
    @Test
    void optionalSessionOwnsVolumeDuckingSwitchAndCleanupWithoutGameplayAudio() {
        SonicConfigurationService config = SonicConfigurationService.createStandalone();
        List<String> calls = new ArrayList<>();
        HubMusicController controller = new HubMusicController(config, (game, ignored) -> {
            calls.add("open " + game);
            return new HubMusicController.Session() {
                @Override public void volume(int percent, boolean ducked) { calls.add("volume " + percent + " " + ducked); }
                @Override public void frame() { calls.add("frame"); }
                @Override public void close() { calls.add("close " + game); }
            };
        }, Runnable::run);
        controller.update("s2", false);
        assertTrue(calls.isEmpty(), "Off by default");
        config.setConfigValue(AUDIO_HUB_MUSIC_ENABLED, true);
        controller.update("s2", false);
        config.setConfigValue(AUDIO_HUB_MUSIC_VOLUME, 35);
        controller.update("s2", true);
        controller.update("s1", false);
        controller.update("standalone.game", false);
        controller.close();
        controller.update("s2", false);
        assertEquals(List.of("open s2", "volume 20 false", "frame", "volume 35 true", "frame",
                "close s2", "open s1", "volume 35 false", "frame", "close s1"), calls);
    }

    @Test
    void unavailableRomIsNotRetriedEveryFrameAndDisableClosesTheOwner() {
        SonicConfigurationService config = SonicConfigurationService.createStandalone();
        config.setConfigValue(AUDIO_HUB_MUSIC_ENABLED, true);
        int[] opens = {0};
        HubMusicController controller = new HubMusicController(config, (game, ignored) -> {
            opens[0]++; throw new java.io.IOException("missing ROM");
        }, Runnable::run);
        controller.update("s2", false);
        controller.update("s2", true);
        assertEquals(1, opens[0]);
        config.setConfigValue(SONIC_2_ROM, "new image.gen");
        controller.update("s2", false);
        assertEquals(2, opens[0], "A changed source retries");
        config.setConfigValue(AUDIO_ENABLED, false);
        controller.update("s2", false);
        assertEquals(2, opens[0]);
        controller.close();
    }

    @Test
    void naturallyEndedThemeRestartsAndFinalCleanupRetriesAHostFailure() {
        SonicConfigurationService config = SonicConfigurationService.createStandalone();
        config.setConfigValue(AUDIO_HUB_MUSIC_ENABLED, true);
        int[] frames = {0}, restarts = {0}, closes = {0};
        HubMusicController controller = new HubMusicController(config, (game, ignored) -> new HubMusicController.Session() {
            @Override public void volume(int percent, boolean ducked) { }
            @Override public void frame() { frames[0]++; }
            @Override public boolean complete() { return frames[0] == 1; }
            @Override public void restart() { restarts[0]++; }
            @Override public void close() { if (++closes[0] == 1) throw new IllegalStateException("retryable device close"); }
        }, Runnable::run);
        controller.update("s2", false);
        controller.update("s2", false);
        assertEquals(1, restarts[0], "Only native completion requests a restart");
        controller.close();
        assertEquals(2, closes[0], "Final cleanup retries the retained owner");
    }

    @Test
    void hubClosedDuringRomOpenDisposesLateSessionWithoutPresenting() {
        SonicConfigurationService config = SonicConfigurationService.createStandalone();
        config.setConfigValue(AUDIO_HUB_MUSIC_ENABLED, true);
        HubMusicController[] holder = new HubMusicController[1];
        int[] closes = {0};
        holder[0] = new HubMusicController(config, (game, ignored) -> {
            holder[0].close();
            return new HubMusicController.Session() {
                @Override public void volume(int percent, boolean ducked) { fail("Closed session must not present"); }
                @Override public void frame() { fail("Closed session must not present"); }
                @Override public void close() { closes[0]++; }
            };
        }, Runnable::run);
        holder[0].update("s2", false);
        assertEquals(1, closes[0]);
    }

    @Test
    void queuedOpenCannotStartAfterHubCleanup() {
        var jobs = new ArrayDeque<Runnable>();
        SonicConfigurationService config = SonicConfigurationService.createStandalone();
        config.setConfigValue(AUDIO_HUB_MUSIC_ENABLED, true);
        HubMusicController controller = new HubMusicController(config, (game, ignored) -> {
            fail("Closed hub must not open a late session"); return null;
        }, jobs::add);
        controller.update("s2", false);
        controller.update("s1", false);
        assertEquals(1, jobs.size(), "Frames coalesce while loading instead of building a queue");
        controller.close();
        while (!jobs.isEmpty()) jobs.remove().run();
    }
}
