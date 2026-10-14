package com.openggf.audio;

/** Internal profile-owned admission rules for optional native ROM audio. */
public interface NativeAudioEffectCues {
    boolean permitsTemporaryMusic(int musicId);
    boolean permitsLandingCue(int sfxId);
    boolean isWarningCue(int sfxId);
}
