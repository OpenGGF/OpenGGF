package com.openggf.game.sonic3k.competition;

import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.nativemenu.NativeMenuHostContext;
import com.openggf.game.nativemenu.NativeMenuScreen;
import com.openggf.game.nativemenu.NativeTitleModeProvider;
import com.openggf.game.save.SaveManager;
import com.openggf.game.save.SavePaths;
import com.openggf.level.LevelArtInternalAccess;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Stateless menu composition. A module never retains a world, input source or GPU view. */
public final class S3kNativeTitleModeProvider implements NativeTitleModeProvider {
    private final Supplier<NativeMenuScreen.Exit> singlePlayerExit;

    public S3kNativeTitleModeProvider() { this(() -> NativeMenuScreen.Exit.NONE); }
    public S3kNativeTitleModeProvider(Supplier<NativeMenuScreen.Exit> singlePlayerExit) {
        this.singlePlayerExit = Objects.requireNonNull(singlePlayerExit);
    }

    @Override public NativeMenuScreen optionsScreen() { return null; }
    @Override public NativeMenuScreen.Exit singlePlayerTitleExit() {
        return Objects.requireNonNull(singlePlayerExit.get(), "single-player title policy");
    }

    @Override public NativeMenuScreen competitionScreen(NativeMenuHostContext context) {
        Objects.requireNonNull(context);
        try {
            var rom = context.engine().roms().getRom();
            var resources = S3kCompetitionResources.identify(rom);
            var saves = new SaveManager(SavePaths.root());
            String key = resources == S3kCompetitionResources.ORIGINAL_S3 ? "s3-competition" : "s3k-competition";
            var saved = saves.readSlotSummary(key, 0);
            byte[] block = rom.readBytes(resources.defaultRecords(), S3kCompetitionRecords.BYTE_COUNT);
            if (saved.isLoadable()) {
                Object encoded = saved.payload().get("nativeRecords");
                if (!(encoded instanceof String text)) throw new IOException("Invalid Competition records payload");
                try { block = HexFormat.of().parseHex(text); }
                catch (IllegalArgumentException failure) { throw new IOException("Invalid Competition record bytes", failure); }
                if (block.length != S3kCompetitionRecords.BYTE_COUNT)
                    throw new IOException("Competition records payload must contain 80 bytes");
            }
            var views = new S3kCompetitionViews(context.engine().graphics());
            try {
                var host = new S3kCompetitionNativeHost(context.engine(), context.rootModule(), context.input(),
                        context.publishMode(), new S3kCompetitionRecords(block), updated -> {
                            try { saves.writeSlotAsync(key, 0, Map.of("nativeRecords", HexFormat.of().formatHex(updated))); }
                            catch (IOException failure) { throw new UncheckedIOException("Cannot persist Competition records", failure); }
                        }, views, LevelArtInternalAccess::initializeObjectArt);
                return new Visit(host, views);
            } catch (IOException | RuntimeException | Error failure) {
                views.close();
                throw failure;
            }
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot open ROM-native Competition", failure);
        }
    }

    /** One visit owns its host and all its views, including the menu-only exit. */
    public static final class Visit implements NativeMenuScreen {
        private final S3kCompetitionNativeHost host;
        private final S3kCompetitionViews views;
        private boolean closed;
        private Visit(S3kCompetitionNativeHost host, S3kCompetitionViews views) { this.host = host; this.views = views; }
        public S3kCompetitionNativeHost host() { return host; }
        @Override public GameMode mode() { return host.screen().mode(); }
        @Override public void initialize() {
            if (closed) throw new IllegalStateException("Competition visit has closed");
            host.screen().initialize();
        }
        @Override public void update(InputHandler input) { host.screen().update(input); }
        @Override public void afterFrame() { host.screen().afterFrame(); }
        @Override public void draw() { host.screen().draw(); }
        @Override public void setClearColor() { host.screen().setClearColor(); }
        @Override public Exit consumeExit() { return host.screen().consumeExit(); }
        @Override public void reset() {
            if (closed) return;
            try { host.screen().reset(); }
            finally {
                try { host.stopVisitMusic(); } finally { views.close(); closed = true; }
            }
        }
    }
}
