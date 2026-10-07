package com.openggf.audio.smps;

/** Production semantic stream events used by finite ROM music preparation. */
public interface SmpsNoteListener {
    void attack(SmpsSequencer.Track track);
    void release(SmpsSequencer.Track track);
}
