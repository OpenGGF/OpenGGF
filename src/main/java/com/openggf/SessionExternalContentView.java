package com.openggf;

import com.openggf.audio.StreamedMusicPort;
import com.openggf.mods.ModMusicResolver;
import com.openggf.mods.PreparedModMusic;
import com.openggf.mods.StreamedMusicPlayer;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/** Immutable session-installed external-content handles and their presentation lease. */
public final class SessionExternalContentView implements AutoCloseable {
    public static final SessionExternalContentView EMPTY = new SessionExternalContentView(
            ModMusicResolver.EMPTY, StreamedMusicPort.EMPTY, true);

    private final ModMusicResolver musicResolver;
    private final StreamedMusicPort streamedMusicPort;
    private final boolean permanentEmpty;
    private ModSubsystem.SessionAudioBoundary transferredOwner;
    private boolean closed;

    public SessionExternalContentView(ModMusicResolver musicResolver,
                                      StreamedMusicPort streamedMusicPort) {
        this(musicResolver, streamedMusicPort, false);
    }

    /** Builds the root adapter and transfers the prepared PCM lease into this view. */
    public static SessionExternalContentView fromPreparedMusic(PreparedModMusic music,
                                                               String gameCode) {
        Objects.requireNonNull(music, "music");
        ModMusicResolver resolver = ModMusicResolver.from(music);
        ModStreamedMusicPort port;
        try {
            port = new ModStreamedMusicPort(music,
                    new StreamedMusicPlayer(music.outputRate()), gameCode);
        } catch (RuntimeException error) {
            music.close();
            throw error;
        }
        return new SessionExternalContentView(resolver, port);
    }

    private SessionExternalContentView(ModMusicResolver musicResolver,
                                       StreamedMusicPort streamedMusicPort,
                                       boolean permanentEmpty) {
        this.musicResolver = Objects.requireNonNull(musicResolver, "musicResolver");
        StreamedMusicPort port = Objects.requireNonNull(streamedMusicPort, "streamedMusicPort");
        this.streamedMusicPort = permanentEmpty ? port : new CloseOncePort(port);
        this.permanentEmpty = permanentEmpty;
    }

    public ModMusicResolver musicResolver() { return musicResolver; }

    public StreamedMusicPort streamedMusicPort() { return streamedMusicPort; }

    synchronized void transferTo(ModSubsystem.SessionAudioBoundary owner) {
        Objects.requireNonNull(owner, "owner");
        if (permanentEmpty) return;
        if (closed || transferredOwner != null) {
            throw new IllegalStateException("Session external-content view is not transferable");
        }
        try {
            owner.install(streamedMusicPort);
            transferredOwner = owner;
        } catch (RuntimeException error) {
            try {
                owner.clear();
            } catch (RuntimeException cleanupError) {
                error.addSuppressed(cleanupError);
            }
            streamedMusicPort.close();
            closed = true;
            throw error;
        }
    }

    public synchronized boolean isClosed() { return closed; }

    @Override
    public synchronized void close() {
        if (permanentEmpty || closed) return;
        closed = true;
        if (transferredOwner == null) streamedMusicPort.close();
        else transferredOwner.clear();
    }

    /** Makes ambiguous install-failure ownership safe at the underlying PCM lease. */
    private static final class CloseOncePort implements StreamedMusicPort {
        private final StreamedMusicPort delegate;
        private final AtomicBoolean closed = new AtomicBoolean();
        private CloseOncePort(StreamedMusicPort delegate) { this.delegate = delegate; }
        @Override public int outputRate() { return delegate.outputRate(); }
        @Override public boolean hasStockOverride(int id) { return delegate.hasStockOverride(id); }
        @Override public boolean isCurrentStockOverride(int id) { return delegate.isCurrentStockOverride(id); }
        @Override public void playStockOverride(int id) { delegate.playStockOverride(id); }
        @Override public boolean hasTrack(TrackRef track) { return delegate.hasTrack(track); }
        @Override public void playTrack(TrackRef track) { delegate.playTrack(track); }
        @Override public boolean hasSfx(SfxRef sfx) { return delegate.hasSfx(sfx); }
        @Override public OneShot openSfx(SfxRef sfx) { return delegate.openSfx(sfx); }
        @Override public boolean hasSource() { return delegate.hasSource(); }
        @Override public int mixInto(short[] output, int frames) { return delegate.mixInto(output, frames); }
        @Override public void pause(int reason) { delegate.pause(reason); }
        @Override public void resume(int reason) { delegate.resume(reason); }
        @Override public void fadeOut(int steps, int delay) { delegate.fadeOut(steps, delay); }
        @Override public void fadeIn(int steps, int delay) { delegate.fadeIn(steps, delay); }
        @Override public void advanceFade() { delegate.advanceFade(); }
        @Override public boolean fadeActive() { return delegate.fadeActive(); }
        @Override public boolean fadeAtFullGain() { return delegate.fadeAtFullGain(); }
        @Override public void setSpeedMultiplier(int multiplier) { delegate.setSpeedMultiplier(multiplier); }
        @Override public void stop() { delegate.stop(); }
        @Override public void reset() { delegate.reset(); }
        @Override public Optional<State> captureState() { return delegate.captureState(); }
        @Override public boolean restoreState(State state) { return delegate.restoreState(state); }
        @Override public void close() {
            if (closed.compareAndSet(false, true)) delegate.close();
        }
    }
}
