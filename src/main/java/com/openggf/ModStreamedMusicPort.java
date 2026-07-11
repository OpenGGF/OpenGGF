package com.openggf;

import com.openggf.audio.StreamedMusicPort;
import com.openggf.mods.ModMusicResolver;
import com.openggf.mods.PreparedModMusic;
import com.openggf.mods.PreparedTrack;
import com.openggf.mods.ResolvedMusic;
import com.openggf.mods.StreamedFadeSnapshot;
import com.openggf.mods.StreamedMusicPlayer;
import com.openggf.mods.StreamedPlaybackSnapshot;
import com.openggf.mods.TrackKey;

import java.util.Objects;
import java.util.Optional;

/** Mod-runtime adapter for the audio-owned streamed presentation boundary. */
public final class ModStreamedMusicPort implements StreamedMusicPort {
    private final PreparedModMusic music;
    private final ModMusicResolver resolver;
    private final StreamedMusicPlayer player;
    private final String gameCode;
    private boolean closed;

    public ModStreamedMusicPort(PreparedModMusic music, StreamedMusicPlayer player, String gameCode) {
        this.music = Objects.requireNonNull(music, "music");
        this.resolver = ModMusicResolver.from(music);
        this.player = Objects.requireNonNull(player, "player");
        this.gameCode = requireGameCode(gameCode);
        if (music.outputRate() != player.outputRate()) {
            throw new IllegalArgumentException("Prepared music and player output rates differ");
        }
    }

    @Override
    public int outputRate() {
        requireOpen();
        return music.outputRate();
    }

    @Override
    public boolean hasStockOverride(int musicId) {
        requireOpen();
        return resolver.resolveStockOverride(gameCode, musicId).isPresent();
    }

    @Override
    public boolean isCurrentStockOverride(int musicId) {
        requireOpen();
        Optional<ResolvedMusic> resolved = resolver.resolveStockOverride(gameCode, musicId);
        Optional<StreamedPlaybackSnapshot> current = player.capture();
        return resolved.isPresent() && current.isPresent()
                && current.get().logicalMusicId() == musicId
                && current.get().key().equals(resolved.get().track().key());
    }

    @Override
    public void playStockOverride(int musicId) {
        requireOpen();
        ResolvedMusic resolved = resolver.resolveStockOverride(gameCode, musicId).orElseThrow(() ->
                new IllegalArgumentException("No streamed override for stock music id " + musicId));
        player.play(resolved.logicalMusicId(), resolved.track());
    }

    @Override public boolean hasSource() { requireOpen(); return player.playing(); }
    @Override public int mixInto(short[] output, int frames) { requireOpen(); return player.mixInto(output, frames); }
    @Override public void pause(int reason) { requireOpen(); player.pause(reason); }
    @Override public void resume(int reason) { requireOpen(); player.resume(reason); }
    @Override public void fadeOut(int steps, int stepDelay) { requireOpen(); player.fadeOut(steps, stepDelay); }
    @Override public void fadeIn(int steps, int stepDelay) { requireOpen(); player.fadeIn(steps, stepDelay); }
    @Override public void advanceFade() { requireOpen(); player.advanceFade(); }
    @Override public boolean fadeActive() { requireOpen(); return player.fadeActive(); }
    @Override public boolean fadeAtFullGain() { requireOpen(); return player.fadeAtFullGain(); }
    @Override public void setSpeedMultiplier(int multiplier) { requireOpen(); player.setSpeedMultiplier(multiplier); }
    @Override public void stop() { requireOpen(); player.stop(); }
    @Override public void reset() { requireOpen(); player.reset(); }

    @Override
    public Optional<State> captureState() {
        requireOpen();
        return player.capture().map(ModStreamedMusicPort::toPortState);
    }

    @Override
    public boolean restoreState(State state) {
        requireOpen();
        Objects.requireNonNull(state, "state");
        TrackKey key = new TrackKey(state.track().owner(), state.track().name());
        Optional<PreparedTrack> prepared = resolver.resolve(key);
        if (prepared.isEmpty()) return false;
        StreamedPlaybackSnapshot snapshot = new StreamedPlaybackSnapshot(key, state.logicalMusicId(),
                state.sourceFramePosition(), state.pauseMask(),
                new StreamedFadeSnapshot(state.fade().gain(), state.fade().remainingSteps(),
                        state.fade().stepDelay(), state.fade().delayCounter(), state.fade().stepAmount()),
                state.rate());
        player.restore(snapshot, ignored -> prepared.get());
        return true;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try {
            player.close();
        } finally {
            music.close();
        }
    }

    private static State toPortState(StreamedPlaybackSnapshot snapshot) {
        StreamedFadeSnapshot fade = snapshot.fade();
        return new State(new TrackRef(snapshot.key().modId(), snapshot.key().localName()),
                snapshot.logicalMusicId(), snapshot.sourceFramePosition(), snapshot.pauseMask(),
                new FadeState(fade.gain(), fade.remainingSteps(), fade.stepDelay(),
                        fade.delayCounter(), fade.stepAmount()), snapshot.rate());
    }

    private static String requireGameCode(String gameCode) {
        if (!"s1".equals(gameCode) && !"s2".equals(gameCode) && !"s3k".equals(gameCode)) {
            throw new IllegalArgumentException("Game code must be exactly s1, s2, or s3k");
        }
        return gameCode;
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Streamed music port is closed");
    }
}
