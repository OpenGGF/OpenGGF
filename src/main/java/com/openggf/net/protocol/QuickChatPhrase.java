package com.openggf.net.protocol;

/** Stable semantic wire IDs; presentation text is rendered by the receiving client. */
@com.openggf.game.ModApi
public enum QuickChatPhrase {
    BRUTAL,
    TOO_SLOW,
    KNOW_THE_WAY,
    THIS_IS_THE_WAY,
    THATS_NO_GOOD,
    THIS_IS_ZONE,
    GOOD_LUCK,
    WELL_PLAYED;

    /** The exact wire ID, or null for anything outside the catalogue. */
    public static QuickChatPhrase parse(String id) {
        if (id == null) return null;
        for (QuickChatPhrase phrase : values()) {
            if (phrase.name().equals(id)) return phrase;
        }
        return null;
    }
}
