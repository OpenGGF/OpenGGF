package com.openggf.audio.presentation;

import com.openggf.audio.rewind.AudioCommand;

/** Typed optional ingress plus forward lifecycle for a game-owned request front end. */
public interface AudioRequestService extends AudioPresentationForwardService {
    void submitMusic(int nativeRequestId, AudioCommand command);

    void submitSound(int nativeRequestId, AudioCommand command);
}
