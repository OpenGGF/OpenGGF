package com.openggf.net.hub;

import com.openggf.net.protocol.ChatMode;

import java.util.function.Predicate;

/** Optional relay/master behavior layered onto the Phase 2 room host. */
@com.openggf.game.ModApi
public record RoomHostHooks(boolean relevanceFiltering,
                            ChatGate chatGate,
                            RoundOutcomeListener roundOutcomeListener,
                            String roundOwnerFingerprint,
                            Predicate<String> isNewPlayer,
                            Predicate<String> knownVoteTrack,
                            String roomId,
                            VerificationHooks verificationHooks,
                            ProfileListener profileListener,
                            ChatMode chatModeLimit) {
    public RoomHostHooks {
        chatModeLimit = chatModeLimit == null ? ChatMode.FULL_CHAT : chatModeLimit;
    }

    public RoomHostHooks(boolean relevanceFiltering, ChatGate chatGate,
                         RoundOutcomeListener roundOutcomeListener, String roundOwnerFingerprint,
                         Predicate<String> isNewPlayer, Predicate<String> knownVoteTrack,
                         String roomId, VerificationHooks verificationHooks,
                         ProfileListener profileListener) {
        this(relevanceFiltering, chatGate, roundOutcomeListener, roundOwnerFingerprint, isNewPlayer,
                knownVoteTrack, roomId, verificationHooks, profileListener, ChatMode.FULL_CHAT);
    }
    /** Told when an admitted member renames itself in the lobby, after the host accepted it. */
    @FunctionalInterface
    @com.openggf.game.ModApi
    public interface ProfileListener {
        void onDisplayNameChanged(String fingerprint, String displayName);
    }

    public RoomHostHooks(boolean relevanceFiltering, ChatGate chatGate,
                         RoundOutcomeListener roundOutcomeListener, String roundOwnerFingerprint,
                         Predicate<String> isNewPlayer, Predicate<String> knownVoteTrack,
                         String roomId, VerificationHooks verificationHooks) {
        this(relevanceFiltering, chatGate, roundOutcomeListener, roundOwnerFingerprint, isNewPlayer,
                knownVoteTrack, roomId, verificationHooks, null);
    }

    @FunctionalInterface
    @com.openggf.game.ModApi
    public interface ChatGate {
        boolean mayChat(String fingerprint, long memberSinceMillis);
    }

    @FunctionalInterface
    @com.openggf.game.ModApi
    public interface RoundOutcomeListener {
        void onRoundComplete(String fingerprint, boolean clean);
    }

    @com.openggf.game.ModApi
    public interface VerificationHooks {
        void onFinishNeedingVerification(String roomId, int slot,
                String identityFingerprint, com.openggf.net.protocol.ControlMessage.AttemptFinish finish,
                String trackKey, String character, String determinismFingerprint,
                boolean spotCheck);

        default void onPendingExpired(String roomId, int slot, int attemptId) { }
    }

    public static RoomHostHooks none() {
        return new RoomHostHooks(false, null, null, null, null, null,
                null, null, null);
    }

    public RoomHostHooks(boolean relevanceFiltering, ChatGate chatGate,
                         RoundOutcomeListener roundOutcomeListener,
                         String roundOwnerFingerprint,
                         Predicate<String> isNewPlayer) {
        this(relevanceFiltering, chatGate, roundOutcomeListener,
                roundOwnerFingerprint, isNewPlayer, null, null, null, null);
    }

    public RoomHostHooks(boolean relevanceFiltering, ChatGate chatGate,
                         RoundOutcomeListener roundOutcomeListener,
                         String roundOwnerFingerprint,
                         Predicate<String> isNewPlayer,
                         Predicate<String> knownVoteTrack) {
        this(relevanceFiltering, chatGate, roundOutcomeListener,
                roundOwnerFingerprint, isNewPlayer, knownVoteTrack, null, null, null);
    }
}
