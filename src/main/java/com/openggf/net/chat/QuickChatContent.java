package com.openggf.net.chat;

import com.openggf.net.protocol.QuickChatPhrase;

/** Semantic quick message and its host-announced track context, independent of wire layout. */
@com.openggf.game.ModApi
public record QuickChatContent(QuickChatPhrase phrase, String gameId, int zone, int act) {
    public QuickChatContent {
        if (phrase == null || gameId == null || gameId.isBlank() || zone < 0 || act < 0) {
            throw new IllegalArgumentException("invalid quick chat content");
        }
    }
}
