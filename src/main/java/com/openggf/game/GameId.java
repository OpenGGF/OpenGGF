package com.openggf.game;

import com.openggf.data.RomGame;

/**
 * Identifies a specific Sonic game for cross-game resource isolation
 * (e.g., palette contexts, art providers).
 */
@com.openggf.game.ModApi
public enum GameId {
    S1("s1"),
    S2("s2"),
    S3K("s3k"),
    /** Sonic CD, hosted from a user-supplied disc rather than a cartridge ROM. */
    SCD("scd"),
    STANDALONE("standalone");

    private final String code;

    GameId(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public RomGame romGame() {
        return switch (this) {
            case S1 -> RomGame.S1;
            case S2 -> RomGame.S2;
            case S3K -> RomGame.S3K;
            case SCD -> throw new IllegalStateException(
                    "Sonic CD is hosted from a disc, not a cartridge ROM");
            case STANDALONE -> throw new IllegalStateException(
                    "Standalone games do not have a stock ROM mapping");
        };
    }

    public static GameId fromCode(String code) {
        for (GameId id : values()) {
            if (id.code.equalsIgnoreCase(code)) {
                return id;
            }
        }
        throw new IllegalArgumentException("Unknown game: " + code);
    }
}
