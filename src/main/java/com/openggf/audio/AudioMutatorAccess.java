package com.openggf.audio;

import com.openggf.util.EngineCallerAccess;
import java.util.Objects;

/** Engine-only, exact-owner binding independent of the diagnostic observer. No global world lookup. */
public final class AudioMutatorAccess {
    private AudioMutatorAccess() { }

    public enum Kind { MUSIC, COMMAND, RESTORE, END_OVERRIDE, SPEED_SHOES, SPEED_MULTIPLIER, TEMPO, STOP, FADE, FOREIGN }
    public record MusicIntent(Kind kind, int value) { }
    @FunctionalInterface public interface Listener {
        void onMusicIntent(MusicIntent intent);
        default void onNativeSfx(int sfxId) { }
        default void onDisplaced() { }
    }

    public static Binding bind(AudioManager audio, Listener listener) {
        if (EngineCallerAccess.callerOutside(AudioMutatorAccess.class).getClassLoader()
                != AudioMutatorAccess.class.getClassLoader()
                || listener.getClass().getClassLoader() != AudioMutatorAccess.class.getClassLoader())
            throw new SecurityException("Native audio bindings belong to the engine");
        var binding = new Binding(Objects.requireNonNull(audio), Objects.requireNonNull(listener));
        var previous = audio.mutatorBinding;
        audio.mutatorBinding = binding;
        if (previous != null) previous.listener.onDisplaced();
        return binding;
    }

    public static final class Binding implements AutoCloseable {
        private final AudioManager audio;
        private final Listener listener;
        private final Thread owner = Thread.currentThread();
        private Binding(AudioManager audio, Listener listener) { this.audio = audio; this.listener = listener; }
        public boolean active() { return audio.mutatorBinding == this; }
        private void requireOwner() {
            if (EngineCallerAccess.callerOutside(Binding.class).getClassLoader() != Binding.class.getClassLoader())
                throw new SecurityException("Native audio bindings belong to the engine");
            if (Thread.currentThread() != owner || !active())
                throw new IllegalStateException("Native audio binding is no longer owned by this world/thread");
        }
        public boolean permitsMusic(int id) {
            requireOwner();
            return audio.getAudioProfile() instanceof NativeAudioEffectCues cues && cues.permitsTemporaryMusic(id);
        }
        public boolean isOverride(int id) {
            requireOwner();
            var profile = audio.getAudioProfile();
            return profile != null && profile.isMusicOverride(id);
        }
        public boolean isWarning(int id) {
            requireOwner();
            return audio.getAudioProfile() instanceof NativeAudioEffectCues cues && cues.isWarningCue(id);
        }
        public boolean warningActive() { requireOwner(); return audio.mutatorWarningActive(); }
        /** Driver-completed 1-up restoration is observed, never commanded by this binding. */
        public Integer foregroundMusic() {
            requireOwner();
            if (audio.isRewindReplaySuppressed() || audio.isReverseAudioPresentationActive()) return null;
            var music = audio.captureLogicalSnapshot().presentation().activeMusic();
            return music == null ? null : music.musicId();
        }
        public void prepareMusic(int id) { requireOwner(); audio.prepareMutatorCue(id, true); }
        public void prepareLandingCue(int id) { requireOwner(); audio.prepareMutatorCue(id, false); }
        public void playTemporaryMusic(int id) {
            requireOwner();
            audio.prepareMutatorCue(id, true);
            submit(() -> audio.playMusic(id));
        }
        public void resumeNativeMusic(int id) {
            requireOwner();
            // Resume only a currently valid ordinary native intent, never a saved song or control snapshot.
            audio.prepareMutatorCue(id, true);
            submit(() -> audio.playMusic(id));
        }
        public void playLandingCue(int id) {
            requireOwner();
            audio.prepareMutatorCue(id, false);
            if (audio.mutatorWarningActive()) throw new IllegalStateException("Native warning audio has priority");
            submit(() -> {
                if (!audio.playSfx(id)) {
                    if (!audio.isRewindReplaySuppressed()) throw new IllegalStateException("Native landing cue was not submitted");
                }
            });
        }
        private void submit(Runnable action) {
            audio.mutatorSubmissionDepth++;
            try { action.run(); }
            finally { audio.mutatorSubmissionDepth--; }
        }
        void musicIntent(Kind kind, int value) { listener.onMusicIntent(new MusicIntent(kind, value)); }
        void sfxIntent(int id) { listener.onNativeSfx(id); }
        @Override public void close() {
            if (EngineCallerAccess.callerOutside(Binding.class).getClassLoader() != Binding.class.getClassLoader())
                throw new SecurityException("Native audio bindings belong to the engine");
            if (Thread.currentThread() != owner) throw new IllegalStateException("Native audio binding belongs to another thread");
            if (active()) audio.mutatorBinding = null;
        }
    }
}
