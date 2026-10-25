package com.openggf.net.chat;

import com.openggf.net.protocol.QuickChatPhrase;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestEnglishQuickChat {
    @Test
    void catalogueKeepsRequestedWordingAndEveryPhraseHasOneEnglishRendering() {
        Map<QuickChatPhrase, String> expected = Map.of(
                QuickChatPhrase.BRUTAL, "Brutal!",
                QuickChatPhrase.TOO_SLOW, "You're too slow!",
                QuickChatPhrase.KNOW_THE_WAY, "Do you know the way?",
                QuickChatPhrase.THIS_IS_THE_WAY, "This is the way.",
                QuickChatPhrase.THATS_NO_GOOD, "That's no good!",
                QuickChatPhrase.THIS_IS_ZONE, "This is Chemical Plant!",
                QuickChatPhrase.GOOD_LUCK, "Good luck!",
                QuickChatPhrase.WELL_PLAYED, "Well played!");
        assertEquals(java.util.Set.of(QuickChatPhrase.values()), expected.keySet());
        expected.forEach((phrase, text) -> assertEquals(text, EnglishQuickChat.text(phrase, "Chemical Plant")));
        assertEquals("This is the zone!", EnglishQuickChat.text(QuickChatPhrase.THIS_IS_ZONE, null));
        assertEquals("This is the zone!", EnglishQuickChat.text(QuickChatPhrase.THIS_IS_ZONE, " "));
    }
}
