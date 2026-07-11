package com.openggf;

import com.openggf.game.sonic1.audio.Sonic1Music;
import com.openggf.game.sonic2.audio.Sonic2Music;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;

/** Composition-root stock-music id domain used while validating packed overrides. */
public final class StockMusicDomains {
    private StockMusicDomains() { }

    public static boolean containsSupported(String gameCode, int musicId) {
        return switch (gameCode) {
            case "s1" -> Sonic1Music.titleMap().containsKey(musicId);
            case "s2" -> Sonic2Music.titleMap().containsKey(musicId);
            case "s3k" -> Sonic3kMusic.titleMap().containsKey(musicId);
            default -> false;
        };
    }
}
