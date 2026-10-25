package com.openggf.net.chat;

import com.openggf.net.protocol.QuickChatPhrase;

/** English presentation catalogue; stable phrase IDs remain independent of translated wording. */
@com.openggf.game.ModApi
public final class EnglishQuickChat {
    private EnglishQuickChat() { }

    public static String text(QuickChatPhrase phrase, String zoneName) {
        return switch (phrase) {
            case BRUTAL -> "Brutal!";
            case TOO_SLOW -> "You're too slow!";
            case KNOW_THE_WAY -> "Do you know the way?";
            case THIS_IS_THE_WAY -> "This is the way.";
            case THATS_NO_GOOD -> "That's no good!";
            case THIS_IS_ZONE -> "This is "
                    + (zoneName == null || zoneName.isBlank() ? "the zone" : zoneName) + "!";
            case GOOD_LUCK -> "Good luck!";
            case WELL_PLAYED -> "Well played!";
        };
    }
}
