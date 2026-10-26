package com.openggf.audio;

import com.openggf.util.EngineCallerAccess;
import java.util.Objects;

/** Engine-only, exact-owner binding independent of the diagnostic observer. No global world lookup. */
public final class AudioMutatorAccess {
    private Binding currentBinding;
    private int submissionDepth;

    AudioMutatorAccess() { }

    private Binding install(Binding binding) {
        var previous = currentBinding;
        currentBinding = binding;
        if (previous != null) previous.listener.onDisplaced();
        return binding;
    }

    private boolean isCurrent(Binding binding) { return currentBinding == binding; }

    private void release(Binding binding) {
        if (isCurrent(binding)) currentBinding = null;
    }

    void reset() {
        if (currentBinding != null) {
            var previous = currentBinding;
            currentBinding = null;
            previous.musicIntent(Kind.STOP, 0);
        }
    }

    void musicIntent(Kind kind, int value) {
        if (currentBinding != null && submissionDepth == 0) currentBinding.musicIntent(kind, value);
    }

    void sfxIntent(int id) {
        if (currentBinding == null || submissionDepth != 0) return;
        if (id >= 0xE0) currentBinding.musicIntent(Kind.COMMAND, id);
        else currentBinding.sfxIntent(id);
    }

    void submit(Runnable action) {
        submissionDepth++;
        try { action.run(); }
        finally { submissionDepth--; }
    }

    public enum Kind { MUSIC, COMMAND, RESTORE, NATURAL_RESTORE, END_OVERRIDE, SPEED_SHOES, SPEED_MULTIPLIER, TEMPO, STOP, FADE, FOREIGN, SOURCE }
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
        return AudioManager.mutatorAccess(audio).install(binding);
    }

    public static final class Binding implements AutoCloseable {
        private final AudioManager audio;
        private final Listener listener;
        private final Thread owner = Thread.currentThread();
        private Binding(AudioManager audio, Listener listener) { this.audio = audio; this.listener = listener; }
        public boolean active() { return AudioManager.mutatorAccess(audio).isCurrent(this); }
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
        public void prepareMusic(int id) { requireOwner(); audio.prepareMutatorCue(id, true); }
        public void prepareLandingCue(int id) { requireOwner(); audio.prepareMutatorCue(id, false); }
        public void playTemporaryMusic(int id) {
            requireOwner();
            audio.prepareMutatorCue(id, true);
            if (audio.mutatorWarningActive()) throw new IllegalStateException("Native warning audio has priority");
            AudioManager.mutatorAccess(audio).submit(() -> audio.playMusic(id));
        }
        public void resumeNativeMusic(int id) {
            requireOwner();
            // Resume only a currently valid ordinary native intent, never a saved song or control snapshot.
            audio.prepareMutatorCue(id, true);
            AudioManager.mutatorAccess(audio).submit(() -> audio.playMusic(id));
        }
        public void playLandingCue(int id) {
            requireOwner();
            audio.prepareMutatorCue(id, false);
            if (audio.mutatorWarningActive()) throw new IllegalStateException("Native warning audio has priority");
            AudioManager.mutatorAccess(audio).submit(() -> {
                if (!audio.playSfx(id)) {
                    if (!audio.isRewindReplaySuppressed()) throw new IllegalStateException("Native landing cue was not submitted");
                }
            });
        }
        void musicIntent(Kind kind, int value) { listener.onMusicIntent(new MusicIntent(kind, value)); }
        void sfxIntent(int id) { listener.onNativeSfx(id); }
        @Override public void close() {
            if (EngineCallerAccess.callerOutside(Binding.class).getClassLoader() != Binding.class.getClassLoader())
                throw new SecurityException("Native audio bindings belong to the engine");
            if (Thread.currentThread() != owner) throw new IllegalStateException("Native audio binding belongs to another thread");
            AudioManager.mutatorAccess(audio).release(this);
        }
    }
}
