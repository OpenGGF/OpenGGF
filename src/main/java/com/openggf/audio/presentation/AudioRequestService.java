package com.openggf.audio.presentation;

import com.openggf.audio.rewind.AudioCommand;

/** Typed optional ingress plus forward lifecycle for a game-owned request front end. */
public interface AudioRequestService extends AudioPresentationForwardService {
    void submitMusic(int nativeRequestId, AudioCommand command);

    void submitSound(int nativeRequestId, AudioCommand command);

    /** Uses a game's secondary sound-request mailbox when it has one. */
    default void submitSecondarySound(int nativeRequestId, AudioCommand command) {
        submitSound(nativeRequestId, command);
    }
}
