package com.openggf.game.launch;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class TestHubProgressStore {
    @TempDir Path directory;

    @Test
    void navigationRemembersImmediatelyAndPersistsOnlyAfterSettling() throws Exception {
        var config = SonicConfigurationService.createStandalone(directory);
        config.saveConfig();
        var clock = new AtomicLong();
        var store = new HubProgressStore(config, clock::get);
        String originalFile = Files.readString(directory.resolve("config.yaml"));
        store.rememberGame("s1");
        assertEquals("s1", store.lastGame());
        clock.set(700_000_000L);
        store.update();
        store.rememberGame("s3k");
        clock.set(1_000_000_000L);
        store.update();
        assertEquals(originalFile, Files.readString(directory.resolve("config.yaml")));
        clock.set(1_450_000_000L);
        store.update();
        assertEquals("s3k", SonicConfigurationService.createStandalone(directory).getString(SonicConfiguration.HUB_LAST_GAME));
    }

    @Test
    void flushPersistsTheLatestSelectionAndPreservesInterveningSettings() {
        var config = SonicConfigurationService.createStandalone(directory);
        var store = new HubProgressStore(config, () -> 0L);
        store.rememberGame("s1");
        store.rememberGame("s3k");
        config.setConfigValue(SonicConfiguration.AUDIO_ENABLED, false);
        store.flush();
        var fresh = SonicConfigurationService.createStandalone(directory);
        assertEquals("s3k", fresh.getString(SonicConfiguration.HUB_LAST_GAME));
        assertFalse(fresh.getBoolean(SonicConfiguration.AUDIO_ENABLED));
    }

    @Test
    void returningToPersistedSelectionCancelsPendingWrite() throws Exception {
        var config = SonicConfigurationService.createStandalone(directory);
        config.saveConfig();
        var clock = new AtomicLong();
        var store = new HubProgressStore(config, clock::get);
        String original = store.lastGame();
        String originalFile = Files.readString(directory.resolve("config.yaml"));
        store.rememberGame("custom-game");
        store.rememberGame(original);
        clock.set(1_000_000_000L);
        store.update();
        store.flush();
        assertEquals(originalFile, Files.readString(directory.resolve("config.yaml")));
    }
}
