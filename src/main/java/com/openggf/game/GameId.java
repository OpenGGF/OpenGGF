package com.openggf.game;

import com.openggf.data.RomGame;

import java.util.Optional;

/**
 * Identifies a specific Sonic game for cross-game resource isolation
 * (e.g., palette contexts, art providers).
 */
@com.openggf.game.ModApi
public enum GameId {
    S1("s1"),
    S2("s2"),
    S3K("s3k"),
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
        return cartridgeGame().orElseThrow(() -> new IllegalStateException(
                "Game " + code + " has no cartridge ROM mapping"));
    }

    /** Cartridge-media capability, independent of stock-game membership. */
    public Optional<RomGame> cartridgeGame() {
        return switch (this) {
            case S1 -> Optional.of(RomGame.S1);
            case S2 -> Optional.of(RomGame.S2);
            case S3K -> Optional.of(RomGame.S3K);
            case SCD, STANDALONE -> Optional.empty();
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
