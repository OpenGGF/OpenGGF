package com.openggf.audio;

import java.util.Objects;

/** Internal automatic-playlist admission; direct music cues and native SFX retain their routes. */
public final class LevelMusicAccess {
    private LevelMusicAccess() { }

    /** A director owns this scope across native producer replacements, and closes it on stop. */
    public static Control suppressAutomatic(AudioManager audio) {
        return new Control(Objects.requireNonNull(audio), false);
    }

    /**
     * Native load presentation must not enqueue music controls into a director's next song.
     * Establish this before loading, so a deferred mailbox cannot outlive the entry policy.
     * Ordinary effects and native entry bookkeeping keep their normal owners.
     */
    public static Control suppressAutomaticEntry(AudioManager audio) {
        return new Control(Objects.requireNonNull(audio), true);
    }

    static boolean allowsEntryMusic(AudioManager audio) {
        synchronized (audio) { return audio.levelEntryMusicControls == 0; }
    }

    static boolean allowsEntryRequest(AudioManager audio, com.openggf.audio.rewind.AudioCommand command) {
        return allowsEntryMusic(audio) || !(command instanceof com.openggf.audio.rewind.AudioCommand.PlayMusic
                || command instanceof com.openggf.audio.rewind.AudioCommand.FadeOutMusic
                || command instanceof com.openggf.audio.rewind.AudioCommand.StopMusic);
    }

    public static boolean allowsAutomatic(AudioManager audio) {
        synchronized (audio) { return audio.levelMusicControls == 0; }
    }

    public static final class Control implements AutoCloseable {
        private final AudioManager audio;
        private final boolean entry;
        private boolean closed;

        private Control(AudioManager audio, boolean entry) {
            this.audio = audio; this.entry = entry;
            synchronized (audio) { audio.levelMusicControls++; if (entry) audio.levelEntryMusicControls++; }
        }

        @Override public void close() {
            synchronized (audio) {
                if (!closed) { closed = true; audio.levelMusicControls--; if (entry) audio.levelEntryMusicControls--; }
            }
        }
    }
}
