package com.openggf.net.hub;

import java.util.function.Predicate;

/** Optional relay/master behavior layered onto the Phase 2 room host. */
public record RoomHostHooks(boolean relevanceFiltering,
                            ChatGate chatGate,
                            RoundOutcomeListener roundOutcomeListener,
                            String roundOwnerFingerprint,
                            Predicate<String> isNewPlayer,
                            Predicate<String> knownVoteTrack,
                            String roomId,
                            VerificationHooks verificationHooks) {
    @FunctionalInterface
    public interface ChatGate {
        boolean mayChat(String fingerprint, long memberSinceMillis);
    }

    @FunctionalInterface
    public interface RoundOutcomeListener {
        void onRoundComplete(String fingerprint, boolean clean);
    }

    public interface VerificationHooks {
        void onFinishNeedingVerification(String roomId, int slot,
                String identityFingerprint, com.openggf.net.protocol.ControlMessage.AttemptFinish finish,
                String trackKey, String character, String determinismFingerprint,
                boolean spotCheck);

        default void onPendingExpired(String roomId, int slot, int attemptId) { }
    }

    public static RoomHostHooks none() {
        return new RoomHostHooks(false, null, null, null, null, null,
                null, null);
    }

    public RoomHostHooks(boolean relevanceFiltering, ChatGate chatGate,
                         RoundOutcomeListener roundOutcomeListener,
                         String roundOwnerFingerprint,
                         Predicate<String> isNewPlayer) {
        this(relevanceFiltering, chatGate, roundOutcomeListener,
                roundOwnerFingerprint, isNewPlayer, null, null, null);
    }

    public RoomHostHooks(boolean relevanceFiltering, ChatGate chatGate,
                         RoundOutcomeListener roundOutcomeListener,
                         String roundOwnerFingerprint,
                         Predicate<String> isNewPlayer,
                         Predicate<String> knownVoteTrack) {
        this(relevanceFiltering, chatGate, roundOutcomeListener,
                roundOwnerFingerprint, isNewPlayer, knownVoteTrack, null, null);
    }
}
