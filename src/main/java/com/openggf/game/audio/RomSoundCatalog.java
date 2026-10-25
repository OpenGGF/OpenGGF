package com.openggf.game.audio;

import com.openggf.game.GameId;
import com.openggf.game.GameModuleRegistry;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Named music and sound effects addressed by the shipped ROM's request IDs.
 * Engine playback IDs are kept separate from native IDs and from the other sound kind.
 * This catalog contains metadata only; playback assets still come from the ROM pipeline.
 */
public final class RomSoundCatalog {
    /**
     * @param name stable enum key
     * @param title human-readable display name
     * @param soundId native ROM request ID
     * @param engineId existing engine playback ID for this sound kind
     */
    public record Entry(String name, String title, int soundId, int engineId) { }

    /** Immutable profile-owned inventory, sorted by native request ID. */
    public record Catalog(List<Entry> music, List<Entry> sfx) {
        public Catalog {
            music = music.stream().sorted(Comparator.comparingInt(Entry::soundId)).toList();
            sfx = sfx.stream().sorted(Comparator.comparingInt(Entry::soundId)).toList();
        }
    }

    private RomSoundCatalog() { }

    /** Returns the immutable music catalog, sorted by native request ID. */
    public static List<Entry> music(String game) {
        return catalog(game).music();
    }

    /** Returns the immutable SFX catalog, sorted by native request ID. */
    public static List<Entry> sfx(String game) {
        return catalog(game).sfx();
    }

    /** Resolves a native music request; SFX and system commands are rejected. */
    public static Entry music(String game, int nativeId) {
        return byNativeId(music(game), game, "music", nativeId);
    }

    /** Resolves a native SFX request; music and system commands are rejected. */
    public static Entry sfx(String game, int nativeId) {
        return byNativeId(sfx(game), game, "SFX", nativeId);
    }

    /** Resolves a stable enum key or display title, ignoring case. */
    public static Entry music(String game, String name) {
        for (Entry entry : music(game)) {
            if (entry.name().equalsIgnoreCase(name) || entry.title().equalsIgnoreCase(name)) {
                return entry;
            }
        }
        throw new IllegalArgumentException("Unknown ROM music name for " + game + ": " + name);
    }

    /** Converts an engine music ID back to a native request ID. */
    public static int nativeMusicId(String game, int engineId) {
        for (Entry entry : music(game)) {
            if (entry.engineId() == engineId) {
                return entry.soundId();
            }
        }
        throw new IllegalArgumentException("Unknown engine music ID for " + game + ": " + hex(engineId));
    }

    private static Catalog catalog(String game) {
        if (game == null || !List.of("s1", "s2", "s3k").contains(game)) {
            throw new IllegalArgumentException("Unknown ROM sound game: " + game
                    + " (expected s1, s2 or s3k)");
        }
        return GameModuleRegistry.stockRomSoundCatalog(GameId.fromCode(game));
    }

    private static Entry byNativeId(List<Entry> entries, String game, String kind, int nativeId) {
        for (Entry entry : entries) {
            if (entry.soundId() == nativeId) {
                return entry;
            }
        }
        throw new IllegalArgumentException("Unknown ROM " + kind + " soundId for " + game
                + ": " + hex(nativeId));
    }

    private static String hex(int id) {
        return String.format(Locale.ROOT, "$%02X", id);
    }

}
