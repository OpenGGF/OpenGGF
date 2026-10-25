package com.openggf.net.client;

import com.openggf.net.protocol.*;

/** Shared direct and relay client-side instance check, before serializing any credential. */
final class ClientAccessGate implements AutoCloseable {
    private final RoomJoinOptions options;
    private RoomAccessPolicy challengedPolicy;

    ClientAccessGate(RoomJoinOptions options) { this.options = options.copy(); }

    ControlMessage.RoomAccessSubmit onChallenge(ControlMessage.RoomAccessChallenge challenge) {
        if (challengedPolicy != null || challenge.accessPolicy() == null
                || challenge.instanceId() == null || challenge.instanceId().isBlank()
                || challenge.accessEpoch() < 0 || challenge.challengeId() == null
                || challenge.challengeId().isBlank()) {
            throw new ProtocolViolationException("invalid room access challenge");
        }
        if (options.expectedInstanceId() != null
                && (!options.expectedInstanceId().equals(challenge.instanceId())
                || options.expectedAccessEpoch() != challenge.accessEpoch())) {
            throw new RaceClient.JoinRejectedException("room access changed; enter credentials again");
        }
        challengedPolicy = challenge.accessPolicy();
        if (!challengedPolicy.passwordRequired()) {
            if (options.hasPassword()) throw new RaceClient.JoinRejectedException("room access changed");
            return null;
        }
        if (!options.hasPassword()) {
            throw new RoomAccessRequiredException(challenge.instanceId(), challenge.accessEpoch(), challengedPolicy);
        }
        return new ControlMessage.RoomAccessSubmit(challenge.challengeId(), options.passwordCopy());
    }

    void requireAdmission(ControlMessage.JoinAccepted accepted) {
        if (accepted.room() == null || accepted.playerSlot() < 0
                || options.expectedInstanceId() != null && challengedPolicy == null
                || challengedPolicy != null && !challengedPolicy.equals(accepted.room().accessPolicy())
                || accepted.room() != null && accepted.room().accessPolicy().passwordRequired()
                        && (challengedPolicy == null || !options.hasPassword())) {
            throw new ProtocolViolationException("room admission bypassed access verification");
        }
    }

    @Override
    public void close() { options.close(); }
}
