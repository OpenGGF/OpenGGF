package com.openggf.net.hub;

import java.util.function.Predicate;

/** Optional relay/master behavior layered onto the Phase 2 room host. */
public record RoomHostHooks(boolean relevanceFiltering,
                            ChatGate chatGate,
                            RoundOutcomeListener roundOutcomeListener,
                            String roundOwnerFingerprint,
                            Predicate<String> isNewPlayer) {
    @FunctionalInterface
    public interface ChatGate {
        boolean mayChat(String fingerprint, long memberSinceMillis);
    }

    @FunctionalInterface
    public interface RoundOutcomeListener {
        void onRoundComplete(String fingerprint, boolean clean);
    }

    public static RoomHostHooks none() {
        return new RoomHostHooks(false, null, null, null, null);
    }
}
