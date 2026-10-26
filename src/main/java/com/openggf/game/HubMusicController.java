package com.openggf.game;

import com.openggf.audio.GameAudioProfile;
import com.openggf.audio.debug.StandaloneAudioPresentationHost;
import com.openggf.audio.presentation.HubMusicPresentationSink;
import com.openggf.audio.smps.SmpsLoader;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Rom;
import com.openggf.data.RomIdentity;
import com.openggf.data.RomImageCatalogue;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.openggf.configuration.SonicConfiguration.*;

/** Owns an isolated optional hub session; never replaces or mutates the suspended game audio. */
public final class HubMusicController implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(HubMusicController.class.getName());
    interface Session extends AutoCloseable {
        void volume(int percent, boolean ducked);
        void frame();
        default boolean complete() { return false; }
        default void restart() { }
        @Override void close();
    }
    interface Factory { Session open(String gameId, SonicConfigurationService config) throws Exception; }
    private record Request(String gameId, boolean enabled, int volume, boolean ducked, String romKey) { }
    private final SonicConfigurationService config;
    private final Factory factory;
    private final Executor executor;
    private final AtomicBoolean pending = new AtomicBoolean();
    private volatile boolean closed;
    private volatile Request request;
    // Owned exclusively by the worker. A failed source is retried only after its identity changes.
    private String source;
    private Session session;

    public HubMusicController(SonicConfigurationService config) {
        this(config, (gameId, settings) -> openRomSession(gameId, settings, false), Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "hub-music");
            thread.setDaemon(true);
            return thread;
        }));
    }

    HubMusicController(SonicConfigurationService config, Factory factory, Executor executor) {
        this.config = Objects.requireNonNull(config);
        this.factory = Objects.requireNonNull(factory);
        this.executor = Objects.requireNonNull(executor);
    }

    /** Called once per hub update, including child menus. Slow ROM/device work stays off the UI. */
    public void update(String gameId, boolean ducked) {
        if (closed) return;
        boolean stock = "s1".equals(gameId) || "s2".equals(gameId) || "s3k".equals(gameId);
        request = new Request(gameId, stock && config.getBoolean(AUDIO_ENABLED)
                && config.getBoolean(AUDIO_HUB_MUSIC_ENABLED), config.getInt(AUDIO_HUB_MUSIC_VOLUME), ducked,
                config.getString(ROMS_DIRECTORY) + "|" + config.getString(SONIC_1_ROM) + "|"
                        + config.getString(SONIC_2_ROM) + "|" + config.getString(SONIC_3K_ROM)
                        + "|" + config.getBoolean(ROMS_PREFER_COMPOSITE));
        if (pending.compareAndSet(false, true)) executor.execute(() -> {
            try { if (!closed) present(request); }
            finally { pending.set(false); }
        });
    }

    private void present(Request next) {
        String nextSource = next.enabled ? next.gameId + "|" + next.romKey : null;
        if (!Objects.equals(source, nextSource)) {
            closeSession();
            source = nextSource;
            if (source != null) {
                try { session = factory.open(next.gameId, config); }
                catch (Exception failure) { LOGGER.log(Level.FINE, "Hub ROM theme unavailable", failure); }
            }
        }
        if (closed) { closeSession(); return; }
        if (session != null) {
            session.volume(next.volume, next.ducked);
            session.frame();
            if (session.complete()) session.restart();
        }
    }

    private void closeSession() {
        if (session != null) { session.close(); session = null; }
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        executor.execute(() -> {
            for (int attempt = 0; attempt < 2; attempt++) {
                try { closeSession(); return; }
                catch (RuntimeException failure) {
                    LOGGER.log(attempt == 0 ? Level.FINE : Level.WARNING, "Hub theme cleanup failed", failure);
                }
            }
        });
        if (executor instanceof ExecutorService service) service.shutdown();
    }

    static Session openRomSession(String gameId, SonicConfigurationService config, boolean noDevice) throws Exception {
        GameId stockGame = GameId.fromCode(gameId);
        RomIdentity identity = stockGame.cartridgeGame().orElseThrow(() ->
                new IllegalArgumentException("Cartridge title audio does not support game: " + gameId)).identity();
        var source = GameModuleRegistry.createStockMenuAudio(stockGame);
        GameAudioProfile profile = source.profile();
        int titleId = source.titleMusicId();
        try (Rom rom = Rom.fromReader(RomImageCatalogue.forCurrentWorkingDirectory(config).open(identity), "hub " + gameId)) {
            SmpsLoader loader = profile.createSmpsLoader(rom);
            var music = loader.loadMusic(titleId);
            var dac = loader.loadDacData();
            if (music == null) throw new IllegalStateException("Missing ROM title music");
            HubMusicPresentationSink[] filter = new HubMusicPresentationSink[1];
            StandaloneAudioPresentationHost host = StandaloneAudioPresentationHost.open(gameId, profile, config, null, noDevice,
                    sink -> filter[0] = new HubMusicPresentationSink(sink));
            try { host.playMusic(music, dac); }
            catch (Throwable failure) { host.close(); throw failure; }
            return new Session() {
                @Override public void volume(int percent, boolean ducked) { filter[0].setVolume(percent, ducked); }
                @Override public void frame() { host.presentFrame(); }
                @Override public boolean complete() { return host.isMusicComplete(); }
                @Override public void restart() { host.playMusic(music, dac); }
                @Override public void close() { host.close(); }
            };
        }
    }
}
