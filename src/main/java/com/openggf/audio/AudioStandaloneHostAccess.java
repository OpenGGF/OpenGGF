package com.openggf.audio;

/** Host-only completion query kept outside the creator-facing AudioManager API. */
public final class AudioStandaloneHostAccess {
    private AudioStandaloneHostAccess() { }
    public static boolean musicComplete(AudioManager manager) {
        return manager.standaloneMusicComplete();
    }
}
