package com.openggf.stageplay.runtime;

import com.openggf.audio.*;
import com.openggf.audio.smps.SmpsLoader;
import com.openggf.game.BuiltInRomDetectors;
import com.openggf.game.GameId;
import com.openggf.io.ModInputLimits;
import com.openggf.mods.PcmDecoder;
import com.openggf.mods.scene.host.SceneRomLibrary;
import com.openggf.stageplay.StageplayDocument;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/** Scene-owned ROM cues and prepared WAV/Ogg voice clips over the existing final PCM producer. */
public final class StageplayAudio implements AutoCloseable {
    @FunctionalInterface public interface Assets { byte[] read(String relativePath) throws IOException; }
    private final AudioManager audio;
    private final SceneRomLibrary roms;
    private final Assets assets;
    private final Map<String, SmpsLoader> loaders = new HashMap<>();
    private final Map<String, GameAudioProfile> profiles = new HashMap<>();
    private final Map<String, ScopedDonorAudio> scopes = new LinkedHashMap<>();
    private StageplayDocument document;
    private StageplayCueDispatcher cues;
    private AudioManager.SceneAudioSamples samples;
    private Map<String, StreamedMusicPort.SfxPcm> preparedSamples = Map.of();
    private com.openggf.audio.rewind.AudioLogicalSnapshot entryAudio;
    private final ArrayDeque<AudioManager.SceneAudioSamples> nativeVoices = new ArrayDeque<>();
    private int nativeSoundFrame = -1;
    private boolean closed;

    public StageplayAudio(AudioManager audio, SceneRomLibrary roms, Path projectRoot) {
        this(audio, roms, path -> readAsset(projectRoot, path));
    }
    public StageplayAudio(AudioManager audio, SceneRomLibrary roms, Assets assets) {
        this.audio = Objects.requireNonNull(audio); this.roms = Objects.requireNonNull(roms);
        this.assets = Objects.requireNonNull(assets);
    }

    /** Bounded, real-path-confined project asset read; symlinks cannot escape the project root. */
    public static byte[] readAsset(Path projectRoot, String relativePath) throws IOException {
        return StageplayAssets.read(projectRoot, relativePath);
    }

    /** Validates every cue before changing playback; missing assets never silently disappear. */
    public void prepare(StageplayDocument next) throws IOException {
        requireOpen(); Objects.requireNonNull(next);
        if (audio.presentationFrameRate() != StageplayDocument.FRAMES_PER_SECOND)
            throw new IOException("Stageplay requires 60 Hz audio presentation");
        if (next.equals(document)) return;
        var prepared = new LinkedHashMap<String, StreamedMusicPort.SfxPcm>();
        long retained = 0;
        for (var cue : next.audio()) {
            switch (cue.kind()) {
                case VOICE -> {
                    if (!prepared.containsKey(cue.asset())) {
                        byte[] encoded = assets.read(cue.asset());
                        var limits = ModInputLimits.production().withMaxAudioCacheBytes(
                                Math.min(ModInputLimits.production().maxAudioCacheBytes(), 64L * 1024 * 1024 - retained));
                        var pcm = new PcmDecoder().decodeSfx(cue.asset(), encoded, limits);
                        retained += (long) pcm.sampleCount() * 2;
                        if (retained > 64L * 1024 * 1024) throw new IOException("Scene voice cache exceeds 64 MiB");
                        prepared.put(cue.asset(), new StreamedMusicPort.SfxPcm(pcm.sampleRate(), pcm.channels(), pcm.copySamples(), 1));
                    }
                }
                case ROM_MUSIC, ROM_SFX -> {
                    if (cue.gain() != 1) throw new IOException("ROM cue gain must be 1; gain is available for voice clips");
                    SmpsLoader loader = loader(cue.gameId());
                    var data = cue.kind() == StageplayDocument.AudioKind.ROM_MUSIC
                            ? loader.loadMusic(cue.soundId()) : loader.loadSfx(cue.soundId());
                    if (data == null) throw new IOException("Missing " + cue.kind() + " " + cue.soundId() + " in " + cue.gameId());
                }
                case MUSIC_STOP -> { }
            }
        }
        var replacement = audio.prepareSceneSamples(prepared);
        stop();
        if (samples != null) samples.close();
        samples = replacement; preparedSamples = Map.copyOf(prepared); document = next; cues = new StageplayCueDispatcher(next);
    }

    private SmpsLoader loader(String game) throws IOException {
        if (loaders.containsKey(game)) return loaders.get(game);
        var rom = roms.sourceRom(game);
        if (rom == null) throw new IOException("Supply the " + game + " ROM for this sound cue");
        var profile = BuiltInRomDetectors.forGame(GameId.fromCode(game)).createModule().getAudioProfile();
        SmpsLoader loader = profile.createSmpsLoader(rom);
        profiles.put(game, profile); loaders.put(game, loader);
        return loader;
    }

    public void playFrame(StageplayDocument source, int frame) {
        requireOpen();
        if (!source.equals(document)) throw new IllegalStateException("Prepare this scene's audio before playback");
        if (entryAudio == null) entryAudio = audio.captureLogicalSnapshot();
        cues.advance(frame, this::dispatch);
    }

    /** Admits an isolated world's current native packet once during forward playback, never on draw/seek. */
    public void playNativeFrame(int frame, StreamedMusicPort.SfxPcm pcm) {
        requireOpen();
        if (frame <= nativeSoundFrame || pcm == null || pcm.samples().length == 0) return;
        while (nativeVoices.size() >= 2) nativeVoices.removeFirst().close();
        var voice = audio.prepareSceneSamples(Map.of("native", pcm));
        try { voice.play("native", 1); nativeVoices.addLast(voice); nativeSoundFrame = frame; }
        catch (RuntimeException | Error failure) { voice.close(); throw failure; }
    }
    private void stopNativeSound() {
        while (!nativeVoices.isEmpty()) nativeVoices.removeFirst().close();
        nativeSoundFrame = -1;
    }

    private void dispatch(StageplayDocument.AudioCue cue) {
        switch (cue.kind()) {
            case VOICE -> samples.play(cue.asset(), (float) cue.gain());
            case MUSIC_STOP -> audio.stopMusic();
            case ROM_MUSIC, ROM_SFX -> {
                boolean base = cue.gameId().equals(roms.activeGame());
                if (!base && !scopes.containsKey(cue.gameId())) {
                    var loader = loaders.get(cue.gameId()); var profile = profiles.get(cue.gameId());
                    scopes.put(cue.gameId(), ScopedDonorAudio.register(audio, cue.gameId(), loader, loader.loadDacData(),
                            profile.getSequencerConfig(), profile));
                }
                if (cue.kind() == StageplayDocument.AudioKind.ROM_MUSIC) {
                    if (base) audio.playMusic(cue.soundId()); else audio.playDonorMusic(cue.gameId(), cue.soundId());
                } else if (base) audio.playSfx(cue.soundId()); else audio.playDonorSfx(cue.gameId(), cue.soundId());
            }
        }
    }

    /** A LIVE shot replaced its native audio producer; renew bounded voices without replaying earlier cues. */
    public void refreshAfterNativeBoundary(int frame) {
        requireOpen();
        entryAudio = null;
        stopNativeSound();
        // These donor routes belonged to the retired producer, as in abandon().
        scopes.clear();
        if (samples != null) samples.close();
        samples = audio.prepareSceneSamples(preparedSamples);
        cues.seek(frame - 1);
    }

    /** The borrowed entry soundtrack is part of a cutscene's rewind state. */
    public com.openggf.audio.rewind.AudioLogicalSnapshot captureEntry() { return entryAudio; }
    public void restoreEntry(com.openggf.audio.rewind.AudioLogicalSnapshot entry) { requireOpen(); entryAudio = entry; }

    /** No ROM commands or old cues are emitted by a seek. Voice tails are silenced. */
    public void seek(int frame) {
        requireOpen(); if (cues == null) throw new IllegalStateException("No prepared scene");
        cues.seek(frame); samples.stop(); stopNativeSound();
    }
    /** Restores the borrowed soundtrack and drops owned voice tails. */
    public void stop() {
        requireOpen();
        stopNativeSound();
        if (samples != null) samples.stop();
        var borrowed = new ArrayList<>(scopes.values()); scopes.clear();
        for (int i = borrowed.size() - 1; i >= 0; i--) borrowed.get(i).close();
        if (entryAudio != null) { var saved = entryAudio; entryAudio = null; audio.restoreLogicalSnapshot(saved); }
        if (cues != null) cues.seek(-1);
    }
    private void requireOpen() { if (closed) throw new IllegalStateException("Stageplay audio is closed"); }
    /** A replaced gameplay/audio session must not receive this scene's old soundtrack snapshot. */
    public void abandon() {
        entryAudio = null;
        // Routes belong to the expired session. Reapplying their saved bindings could overwrite
        // the new visit's donor policy. Normal same-visit close restores them through stop().
        scopes.clear();
        close();
    }
    @Override public void close() {
        if (closed) return;
        try { stop(); } finally { closed = true; if (samples != null) samples.close(); loaders.clear(); profiles.clear(); preparedSamples = Map.of(); }
    }
}
