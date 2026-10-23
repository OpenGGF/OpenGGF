package com.openggf.net.protocol;

/**
 * When a room's round start waits for every present player to be ready. The room owner picks
 * one; the host applies it ({@link ControlMessage.SetReadyRule}, {@link ControlMessage.ReadyRule}).
 */
@com.openggf.game.ModApi
public enum ReadyRules {
    /** The owner's round start is refused until every present player is ready. */
    REQUIRED,
    /** Everyone counts as ready on joining; nothing waits. */
    AUTO,
    /** {@link #REQUIRED} with {@value #MAX_PLAYERS_REQUIRED_BY_DEFAULT} or fewer players present, {@link #AUTO} with more. */
    DEFAULT_BY_SIZE;

    /** The largest room in which {@link #DEFAULT_BY_SIZE} still requires everyone to be ready. */
    public static final int MAX_PLAYERS_REQUIRED_BY_DEFAULT = 5;

    /** True when, with {@code presentPlayers} in the room, the round start waits for everyone. */
    public boolean required(int presentPlayers) {
        return switch (this) {
            case REQUIRED -> true;
            case AUTO -> false;
            case DEFAULT_BY_SIZE -> presentPlayers <= MAX_PLAYERS_REQUIRED_BY_DEFAULT;
        };
    }

    /** The rule named {@code name}, or null for anything that is not one. */
    public static ReadyRules parse(String name) {
        if (name == null) return null;
        for (ReadyRules rule : values()) {
            if (rule.name().equals(name)) return rule;
        }
        return null;
    }
}
