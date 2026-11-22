package com.openggf.mods;

import com.openggf.catalog.StockGameCatalog;
import java.util.Objects;
import java.util.Optional;

/** Read-only resolver over an open {@link PreparedModMusic} session lease. */
public final class ModMusicResolver {
    public static final ModMusicResolver EMPTY = new ModMusicResolver(PreparedModMusic.permanentEmpty());
    private final PreparedModMusic music;

    private ModMusicResolver(PreparedModMusic music) {
        this.music = Objects.requireNonNull(music, "music");
    }

    public static ModMusicResolver from(PreparedModMusic music) {
        return new ModMusicResolver(music);
    }

    public Optional<ResolvedMusic> resolveStockOverride(String gameCode, int musicId) {
        Objects.requireNonNull(gameCode, "gameCode");
        if (!StockGameCatalog.isStockCode(gameCode)) {
            throw new IllegalArgumentException("Game code must be one of " + String.join(", ", StockGameCatalog.stockCodes()));
        }
        if (musicId < 0) throw new IllegalArgumentException("Music id must be nonnegative");
        return Optional.ofNullable(music.resolveStockOverride(gameCode, musicId));
    }

    public Optional<PreparedTrack> resolve(TrackKey key) {
        return Optional.ofNullable(music.resolve(Objects.requireNonNull(key, "key")));
    }

    /** Metadata of actual prepared publication; obtaining it never starts playback. */
    public java.util.List<PreparedModMusic.OverrideTarget> overrideReport() {
        return music.overrideReport();
    }
}
