package com.openggf.audio;

import java.util.Objects;

/** Internal automatic-playlist admission; direct music cues and native SFX retain their routes. */
public final class LevelMusicAccess {
    private final AudioManager audio;
    private int levelMusicControls;
    private int levelEntryMusicControls;

    LevelMusicAccess(AudioManager audio) {
        this.audio = audio;
    }

    /** A director owns this scope across native producer replacements, and closes it on stop. */
    public static Control suppressAutomatic(AudioManager audio) {
        return Objects.requireNonNull(audio).levelMusicAccess().acquire(false);
    }

    /**
     * Native load presentation must not enqueue music controls into a director's next song.
     * Establish this before loading, so a deferred mailbox cannot outlive the entry policy.
     * Ordinary effects and native entry bookkeeping keep their normal owners.
     */
    public static Control suppressAutomaticEntry(AudioManager audio) {
        return Objects.requireNonNull(audio).levelMusicAccess().acquire(true);
    }

    static boolean allowsEntryMusic(AudioManager audio) {
        return queryOwner(audio).allowsEntryMusic();
    }

    static boolean allowsEntryRequest(AudioManager audio, com.openggf.audio.rewind.AudioCommand command) {
        return queryOwner(audio).allowsEntryRequest(command);
    }

    public static boolean allowsAutomatic(AudioManager audio) {
        return queryOwner(audio).allowsAutomatic();
    }

    /** Preserve the original null query's monitor failure, without locking valid forwarding. */
    private static LevelMusicAccess queryOwner(AudioManager audio) {
        if (audio == null) {
            synchronized (audio) { }
        }
        return audio.levelMusicAccess();
    }

    private Control acquire(boolean entry) {
        Control control = new Control(this, entry);
        synchronized (audio) { levelMusicControls++; if (entry) levelEntryMusicControls++; }
        return control;
    }

    private boolean allowsEntryMusic() {
        synchronized (audio) { return levelEntryMusicControls == 0; }
    }

    private boolean allowsEntryRequest(com.openggf.audio.rewind.AudioCommand command) {
        return allowsEntryMusic() || !(command instanceof com.openggf.audio.rewind.AudioCommand.PlayMusic
                || command instanceof com.openggf.audio.rewind.AudioCommand.FadeOutMusic
                || command instanceof com.openggf.audio.rewind.AudioCommand.StopMusic);
    }

    private boolean allowsAutomatic() {
        synchronized (audio) { return levelMusicControls == 0; }
    }

    private void release(Control control) {
        synchronized (audio) {
            if (!control.closed) {
                control.closed = true;
                levelMusicControls--;
                if (control.entry) levelEntryMusicControls--;
            }
        }
    }

    public static final class Control implements AutoCloseable {
        private final LevelMusicAccess owner;
        private final boolean entry;
        private boolean closed;

        private Control(LevelMusicAccess owner, boolean entry) {
            this.owner = owner; this.entry = entry;
        }

        @Override public void close() {
            owner.release(this);
        }
    }
}
