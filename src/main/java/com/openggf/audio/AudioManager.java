package com.openggf.audio;

import com.openggf.audio.rewind.AudioCommand;
import com.openggf.audio.rewind.AudioCommandTimeline;
import com.openggf.audio.rewind.AudioLogicalSnapshot;
import com.openggf.audio.rewind.AudioBackendLogicalSnapshot;
import com.openggf.audio.rewind.AudioPresentationPolicy;
import com.openggf.audio.rewind.AudioReplayReason;
import com.openggf.audio.rewind.AudioReplayScope;
import com.openggf.audio.rewind.AudioSourceDescriptor;
import com.openggf.audio.rewind.AudioTimelineEntry;
import com.openggf.audio.rewind.SmpsDriverSnapshot;
import com.openggf.audio.rewind.SmpsSourceDescriptor;
import com.openggf.audio.runtime.DeterministicAudioRuntime;
import com.openggf.audio.runtime.FrameAudioMode;
import com.openggf.audio.runtime.NoOpDeterministicAudioRuntime;
import com.openggf.audio.runtime.AudioFrameClock;
import com.openggf.audio.runtime.AudioOutputFifo;
import com.openggf.audio.runtime.PcmHistoryRing;
import com.openggf.audio.runtime.StreamBackedDeterministicAudioRuntime;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsLoader;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.data.Rom;
import com.openggf.game.GameServices;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AudioManager {
    private static final Logger LOGGER = Logger.getLogger(AudioManager.class.getName());
    private static final int PCM_HISTORY_SECONDS = 60;
    private static final int OUTPUT_FIFO_SECONDS = 2;
    private static final int REVERSE_RELEASE_CROSSFADE_MS = 45;
    private static AudioManager instance;
    private AudioBackend backend;
    private SmpsLoader smpsLoader;
    private DacData dacData;
    private Map<GameSound, Integer> soundMap;
    private GameAudioProfile audioProfile;
    private boolean ringLeft = true;
    private int rewindReplaySuppressionDepth;
    private final AudioCommandTimeline commandTimeline = new AudioCommandTimeline();
    private DeterministicAudioRuntime deterministicAudioRuntime = NoOpDeterministicAudioRuntime.INSTANCE;
    private DeterministicAudioRuntime preCaptureRuntime;
    private StreamBackedDeterministicAudioRuntime captureRuntime;
    private boolean deterministicRuntimeExplicitlyConfigured;
    private boolean audioFrameOwnedExternally;
    private boolean audioFrameAdvanced;
    private boolean reverseAudioPresentationActive;

    // Donor audio overlay: secondary SFX path for cross-game feature donation
    private final Map<String, SmpsLoader> donorLoaders = new HashMap<>();
    private final Map<String, DacData> donorDacData = new HashMap<>();
    private final Map<String, SmpsSequencerConfig> donorConfigs = new HashMap<>();
    private final Map<GameSound, DonorSfxBinding> donorSoundBindings = new EnumMap<>(GameSound.class);
    private final Map<String, Map<GameMusic, Integer>> donorMusicBindings = new HashMap<>();
    private final Map<SmpsSourceDescriptor, AbstractSmpsData> restoreSmpsResolveCache = new HashMap<>();

    private record DonorSfxBinding(String gameId, int sfxId) {}

    private AudioManager() {
        // Default to NullBackend
        backend = new NullAudioBackend();
    }

    public static synchronized AudioManager getInstance() {
        if (instance == null) {
            instance = new AudioManager();
        }
        return instance;
    }

    public AudioBackend getBackend() {
        return backend;
    }

    /**
     * The active backend's output (synthesis) sample rate. Routed through
     * AudioManager so callers don't reach the backend directly (see
     * TestAudioBackendBypassGuard).
     */
    public int outputSampleRate() {
        return backend.outputSampleRate();
    }

    void setDeterministicAudioRuntime(DeterministicAudioRuntime deterministicAudioRuntime) {
        deterministicRuntimeExplicitlyConfigured = true;
        applyDeterministicAudioRuntime(deterministicAudioRuntime);
    }

    private void applyDeterministicAudioRuntime(DeterministicAudioRuntime deterministicAudioRuntime) {
        this.deterministicAudioRuntime = deterministicAudioRuntime != null
                ? deterministicAudioRuntime
                : NoOpDeterministicAudioRuntime.INSTANCE;
        this.deterministicAudioRuntime.setCommandHandler(this::replayTimelineCommand);
        if (backend != null) {
            backend.attachDeterministicAudioRuntime(this.deterministicAudioRuntime);
        }
    }

    /**
     * Force-installs a deterministic streaming runtime for offline capture,
     * independent of {@code backend.supportsDeterministicRuntimePresentation()}.
     * Drive {@link #advanceGameplayFrameAudio()} each frame, then
     * {@link #drainCaptureFrame}.
     */
    public void beginCaptureMode(int sampleRate, int frameRate) {
        preCaptureRuntime = this.deterministicAudioRuntime;
        int minFrameCapacity = Math.max(1, sampleRate / Math.max(1, frameRate));
        int fifoFrames = Math.max(minFrameCapacity, sampleRate * OUTPUT_FIFO_SECONDS);
        int historyFrames = Math.max(minFrameCapacity, configuredPcmHistoryFrames(sampleRate));
        int crossfadeFrames = Math.max(1, sampleRate * REVERSE_RELEASE_CROSSFADE_MS / 1000);
        captureRuntime = new StreamBackedDeterministicAudioRuntime(
                new AudioFrameClock(sampleRate, frameRate),
                new AudioOutputFifo(fifoFrames),
                new PcmHistoryRing(historyFrames),
                crossfadeFrames);
        applyDeterministicAudioRuntime(captureRuntime);
    }

    /**
     * Drains the current frame's presentation PCM into {@code target} and
     * returns the stereo-frame count produced by the most recent NORMAL
     * audio frame (never re-advances the clock).
     */
    public int drainCaptureFrame(short[] target) {
        if (captureRuntime == null) {
            throw new IllegalStateException("beginCaptureMode() not called");
        }
        // Drain exactly what the most recent NORMAL advanceFrame produced, and
        // report the ACTUAL drained count. Returning the requested size would
        // mask an underrun (FIFO short) or a double-drain (FIFO already empty).
        int requested = captureRuntime.lastProducedFrames();
        return captureRuntime.drainPcm(target, requested);
    }

    /** Restores the runtime that was active before {@link #beginCaptureMode}. */
    public void endCaptureMode() {
        if (captureRuntime == null) {
            return;
        }
        applyDeterministicAudioRuntime(
                preCaptureRuntime != null ? preCaptureRuntime : NoOpDeterministicAudioRuntime.INSTANCE);
        captureRuntime = null;
        preCaptureRuntime = null;
    }

    private void configureDeterministicRuntimeForBackend() {
        if (deterministicRuntimeExplicitlyConfigured) {
            applyDeterministicAudioRuntime(deterministicAudioRuntime);
            return;
        }
        if (backend != null && backend.supportsDeterministicRuntimePresentation()) {
            int sampleRate = Math.max(1, backend.outputSampleRate());
            int frameRate = configuredFrameRate();
            int minFrameCapacity = Math.max(1, sampleRate / frameRate);
            int fifoFrames = Math.max(minFrameCapacity, sampleRate * OUTPUT_FIFO_SECONDS);
            int historyFrames = Math.max(minFrameCapacity, configuredPcmHistoryFrames(sampleRate));
            int crossfadeFrames = Math.max(1, sampleRate * REVERSE_RELEASE_CROSSFADE_MS / 1000);
            applyDeterministicAudioRuntime(new StreamBackedDeterministicAudioRuntime(
                    new AudioFrameClock(sampleRate, frameRate),
                    new AudioOutputFifo(fifoFrames),
                    new PcmHistoryRing(historyFrames),
                    crossfadeFrames));
        } else {
            applyDeterministicAudioRuntime(NoOpDeterministicAudioRuntime.INSTANCE);
        }
    }

    private static int configuredFrameRate() {
        var config = configuredServicesOrNull();
        if (config == null) {
            return 60;
        }
        String region = config.getString(SonicConfiguration.REGION);
        if ("PAL".equalsIgnoreCase(region)) {
            return 50;
        }
        return Math.max(1, config.getInt(SonicConfiguration.FPS));
    }

    private static int configuredPcmHistoryFrames(int sampleRate) {
        var config = configuredServicesOrNull();
        if (config == null) {
            return sampleRate * PCM_HISTORY_SECONDS;
        }
        String limitType = config.getString(SonicConfiguration.REWIND_AUDIO_HISTORY_LIMIT_TYPE);
        int seconds = config.getInt(SonicConfiguration.REWIND_AUDIO_HISTORY_SECONDS);
        int sizeMB = config.getInt(SonicConfiguration.REWIND_AUDIO_HISTORY_SIZE_MB);
        return PcmHistoryRing.capacityFramesFor(sampleRate, limitType, seconds, sizeMB);
    }

    private static com.openggf.configuration.SonicConfigurationService configuredServicesOrNull() {
        try {
            return GameServices.configuration();
        } catch (IllegalStateException e) {
            return null;
        }
    }

    public void setBackend(AudioBackend backend) {
        destroyBackendQuietly(this.backend, "previous AudioBackend");
        this.backend = backend;
        try {
            this.backend.init();
            this.backend.setAudioProfile(audioProfile);
            configureDeterministicRuntimeForBackend();
            LOGGER.info("AudioBackend initialized: " + backend.getClass().getSimpleName());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize AudioBackend", e);
            destroyBackendQuietly(this.backend, "failed AudioBackend");
            this.backend = new NullAudioBackend();
            this.backend.init();
            this.backend.setAudioProfile(audioProfile);
            configureDeterministicRuntimeForBackend();
        }
    }

    private static void destroyBackendQuietly(AudioBackend backend, String description) {
        if (backend == null) {
            return;
        }
        try {
            backend.destroy();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to destroy " + description, e);
        }
    }

    public void setAudioProfile(GameAudioProfile audioProfile) {
        this.audioProfile = audioProfile;
        clearRestoreSmpsResolveCache();
        if (backend != null) {
            backend.setAudioProfile(audioProfile);
        }
    }

    public GameAudioProfile getAudioProfile() {
        return audioProfile;
    }

    public void setRom(Rom rom) {
        clearRestoreSmpsResolveCache();
        if (audioProfile == null) {
            this.smpsLoader = null;
            this.dacData = null;
            return;
        }
        this.smpsLoader = audioProfile.createSmpsLoader(rom);
        this.dacData = smpsLoader != null ? smpsLoader.loadDacData() : null;
    }

    public void setSoundMap(Map<GameSound, Integer> soundMap) {
        this.soundMap = soundMap;
    }

    public void resetRingSound() {
        ringLeft = true;
        recordTimelineCommand(new AudioCommand.ResetRingAlternation(true));
    }

    public AudioCommandTimeline commandTimeline() {
        return commandTimeline;
    }

    public void beginCommandTimelineFrame(long frame) {
        commandTimeline.beginFrame(frame);
        audioFrameOwnedExternally = true;
        audioFrameAdvanced = false;
    }

    public void beginGameplayAudioFrame(long frame) {
        // Clamp forward-only. The GameLoop's gameplayAudioFrame counter only
        // increments while shouldAdvanceGameplayAudioForCurrentMode() is true
        // (LEVEL / BONUS_STAGE / TITLE_CARD), but commandTimeline.currentFrame
        // also advances every tick via audioManager.update() in non-gameplay
        // modes (MASTER_TITLE_SCREEN, LEVEL_SELECT, DATA_SELECT, etc.). If a
        // session transitions through a long non-gameplay mode and then enters
        // a level, currentFrame can be far ahead of gameplayAudioFrame at the
        // first gameplay tick. Without the clamp, beginCommandTimelineFrame
        // would set currentFrame BACKWARD to gameplayAudioFrame — and any
        // command submitted during the non-gameplay window (e.g. playMusic
        // for the selected level, or fadeOutMusic during exitLevelSelect)
        // would sit in pendingCommands with a frame number larger than the
        // new currentFrame, leaving consumeCommands' frame<=current filter
        // unable to drain it. SFX queued fresh in-level uses the new low
        // frame and processes immediately, which explains the music-delayed-
        // but-SFX-works symptom. Rewind seeks go through
        // beginCommandTimelineFrame directly and remain unaffected.
        long monotonic = Math.max(frame, commandTimeline.currentFrame() + 1);
        beginCommandTimelineFrame(monotonic);
    }

    public void discardAudioCommandsAfter(long frame) {
        commandTimeline.discardAfter(frame);
        deterministicAudioRuntime.discardSubmittedCommandsAfter(frame);
    }

    /**
     * Drops command-timeline history below the given absolute entry index.
     * Keyed off the earliest retained audio keyframe's
     * {@code commandEntryCount} when rewind history is pruned, so every
     * retained keyframe keeps a valid replay range while the timeline stays
     * bounded.
     */
    public void pruneAudioCommandsBefore(int entryIndex) {
        commandTimeline.pruneBefore(entryIndex);
    }

    public AudioLogicalSnapshot captureLogicalSnapshot() {
        Set<String> donorGameIds = new LinkedHashSet<>();
        donorGameIds.addAll(donorLoaders.keySet());
        donorGameIds.addAll(donorDacData.keySet());
        donorGameIds.addAll(donorConfigs.keySet());

        Set<AudioLogicalSnapshot.DonorSfxBindingSnapshot> donorBindings = new LinkedHashSet<>();
        for (Map.Entry<GameSound, DonorSfxBinding> entry : donorSoundBindings.entrySet()) {
            DonorSfxBinding binding = entry.getValue();
            donorBindings.add(new AudioLogicalSnapshot.DonorSfxBindingSnapshot(
                    entry.getKey(), binding.gameId(), binding.sfxId()));
        }

        return new AudioLogicalSnapshot(
                ringLeft,
                commandTimeline.currentFrame(),
                commandTimeline.nextOrder(),
                commandTimeline.entryCount(),
                backend != null ? backend.captureLogicalSnapshot() : AudioBackendLogicalSnapshot.empty(),
                donorGameIds,
                donorBindings);
    }

    public void restoreLogicalSnapshot(AudioLogicalSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        ringLeft = snapshot.ringLeft();
        commandTimeline.restoreCursor(snapshot.commandTimelineFrame(), snapshot.commandTimelineNextOrder());

        donorSoundBindings.clear();
        for (AudioLogicalSnapshot.DonorSfxBindingSnapshot binding : snapshot.donorBindings()) {
            donorSoundBindings.put(binding.sound(), new DonorSfxBinding(binding.donorGameId(), binding.sfxId()));
        }

        if (backend != null) {
            backend.restoreLogicalSnapshot(
                    snapshot.backend(),
                    createSmpsDependencyResolver(),
                    reverseAudioPresentationActive);
        }
    }

    private SmpsDriverSnapshot.DependencyResolver createSmpsDependencyResolver() {
        return new SmpsDriverSnapshot.DependencyResolver() {
            @Override
            public AbstractSmpsData resolveSmpsData(SmpsDriverSnapshot.SequencerEntry entry) {
                return resolveRestoreSmpsData(entry);
            }

            @Override
            public DacData resolveDacData(SmpsDriverSnapshot.SequencerEntry entry) {
                SmpsSourceDescriptor source = entry.source();
                return switch (source.kind()) {
                    case DONOR_MUSIC, DONOR_SFX_ID -> donorDacData.getOrDefault(source.donorGameId(), entry.dacData());
                    default -> dacData != null ? dacData : entry.dacData();
                };
            }

            @Override
            public AudioManager resolveAudioManager(SmpsDriverSnapshot.SequencerEntry entry) {
                return AudioManager.this;
            }

            @Override
            public SmpsSequencerConfig resolveConfig(SmpsDriverSnapshot.SequencerEntry entry) {
                SmpsSourceDescriptor source = entry.source();
                if (source.kind() == SmpsSourceDescriptor.Kind.DONOR_MUSIC
                        || source.kind() == SmpsSourceDescriptor.Kind.DONOR_SFX_ID) {
                    return donorConfigs.getOrDefault(source.donorGameId(), entry.config());
                }
                SmpsSequencerConfig config = audioProfile != null ? audioProfile.getSequencerConfig() : null;
                return config != null ? config : entry.config();
            }
        };
    }

    private AbstractSmpsData resolveRestoreSmpsData(SmpsDriverSnapshot.SequencerEntry entry) {
        SmpsSourceDescriptor source = entry.source();
        if (source.kind() == SmpsSourceDescriptor.Kind.UNKNOWN) {
            return entry.smpsData();
        }
        AbstractSmpsData cached = restoreSmpsResolveCache.get(source);
        if (cached != null) {
            return cached;
        }
        AbstractSmpsData resolved = switch (source.kind()) {
            case UNKNOWN -> entry.smpsData();
            case BASE_MUSIC -> smpsLoader != null ? smpsLoader.loadMusic(source.id()) : entry.smpsData();
            case BASE_SFX_ID -> smpsLoader != null ? smpsLoader.loadSfx(source.id()) : entry.smpsData();
            case BASE_SFX_NAME -> smpsLoader != null ? smpsLoader.loadSfx(source.name()) : entry.smpsData();
            case DONOR_MUSIC -> resolveDonorLoader(source).loadMusic(source.id());
            case DONOR_SFX_ID -> resolveDonorLoader(source).loadSfx(source.id());
        };
        if (resolved == null || !source.matchesData(resolved)) {
            resolved = entry.smpsData();
        }
        restoreSmpsResolveCache.put(source, resolved);
        return resolved;
    }

    private SmpsLoader resolveDonorLoader(SmpsSourceDescriptor source) {
        SmpsLoader loader = donorLoaders.get(source.donorGameId());
        if (loader == null) {
            throw new IllegalStateException("No donor SMPS loader registered for " + source.donorGameId());
        }
        return loader;
    }

    private void clearRestoreSmpsResolveCache() {
        restoreSmpsResolveCache.clear();
    }

    public AudioReplayScope beginRewindReplay(int fromFrame, int targetFrame, AudioReplayReason reason) {
        rewindReplaySuppressionDepth++;
        return new AudioReplayScope() {
            private boolean closed;

            @Override
            public void close() {
                if (closed) {
                    return;
                }
                closed = true;
                if (rewindReplaySuppressionDepth > 0) {
                    rewindReplaySuppressionDepth--;
                }
            }
        };
    }

    public boolean isRewindReplaySuppressed() {
        return rewindReplaySuppressionDepth > 0;
    }

    public void replayTimelineCommand(AudioCommand command) {
        if (backend == null || command == null) {
            return;
        }
        switch (command) {
            case AudioCommand.PlayMusic playMusic -> replayMusic(playMusic);
            case AudioCommand.PlaySfx playSfx -> replaySfx(playSfx);
            case AudioCommand.FadeOutMusic fade -> backend.fadeOutMusic(fade.steps(), fade.delay());
            case AudioCommand.StopMusic ignored -> backend.stopPlayback();
            case AudioCommand.StopAllSfx ignored -> backend.stopAllSfx();
            case AudioCommand.EndMusicOverride end -> backend.endMusicOverride(end.musicId());
            case AudioCommand.RestoreMusic ignored -> backend.restoreMusic();
            case AudioCommand.SetSpeedShoes speed -> backend.setSpeedShoes(speed.enabled());
            case AudioCommand.SetSpeedMultiplier speed -> backend.setSpeedMultiplier(speed.multiplier());
            case AudioCommand.ChangeMusicTempo tempo -> backend.changeMusicTempo(tempo.dividingTiming());
            case AudioCommand.ResetRingAlternation reset -> ringLeft = reset.ringLeft();
        }
    }

    public void replayTimelineCommandLogically(AudioCommand command) {
        if (command == null) {
            return;
        }
        switch (command) {
            case AudioCommand.PlayMusic playMusic -> applyLogicalMusic(playMusic);
            case AudioCommand.PlaySfx playSfx -> applyLogicalSfx(playSfx);
            case AudioCommand.StopMusic ignored -> restoreBackendLogicalSnapshot(
                    new AudioBackendLogicalSnapshot(null, false, false, false, 1, List.of()));
            case AudioCommand.EndMusicOverride end -> applyLogicalEndMusicOverride(end.musicId());
            case AudioCommand.RestoreMusic ignored -> applyLogicalRestoreMusic();
            case AudioCommand.SetSpeedShoes speed -> {
                AudioBackendLogicalSnapshot current = currentBackendLogicalSnapshot();
                restoreBackendLogicalSnapshot(new AudioBackendLogicalSnapshot(
                        current.currentMusic(),
                        current.sfxBlocked(),
                        current.pendingRestore(),
                        speed.enabled(),
                        current.speedMultiplier(),
                        current.overrideStack()));
            }
            case AudioCommand.SetSpeedMultiplier speed -> {
                AudioBackendLogicalSnapshot current = currentBackendLogicalSnapshot();
                restoreBackendLogicalSnapshot(new AudioBackendLogicalSnapshot(
                        current.currentMusic(),
                        current.sfxBlocked(),
                        current.pendingRestore(),
                        current.speedShoesEnabled(),
                        speed.multiplier(),
                        current.overrideStack()));
            }
            case AudioCommand.ResetRingAlternation reset -> ringLeft = reset.ringLeft();
            case AudioCommand.FadeOutMusic ignored -> {
            }
            case AudioCommand.StopAllSfx ignored -> {
            }
            case AudioCommand.ChangeMusicTempo ignored -> {
            }
        }
    }

    private void applyLogicalMusic(AudioCommand.PlayMusic command) {
        AudioSourceDescriptor descriptor = descriptorFor(command);
        AudioBackendLogicalSnapshot current = currentBackendLogicalSnapshot();
        List<AudioSourceDescriptor> overrides = new ArrayList<>(current.overrideStack());
        if (command.override() && current.currentMusic() != null) {
            overrides.addFirst(current.currentMusic());
        } else if (!command.override()) {
            overrides.clear();
        }
        restoreBackendLogicalSnapshot(new AudioBackendLogicalSnapshot(
                descriptor,
                current.sfxBlocked(),
                false,
                current.speedShoesEnabled(),
                current.speedMultiplier(),
                overrides));
    }

    private void applyLogicalSfx(AudioCommand.PlaySfx command) {
        if (command.route() != AudioCommand.SfxRoute.RING_RESOLVED || command.sfxName() == null) {
            return;
        }
        if (GameSound.RING_LEFT.name().equals(command.sfxName())) {
            ringLeft = false;
        } else if (GameSound.RING_RIGHT.name().equals(command.sfxName())) {
            ringLeft = true;
        }
    }

    private void applyLogicalRestoreMusic() {
        AudioBackendLogicalSnapshot current = currentBackendLogicalSnapshot();
        if (current.overrideStack().isEmpty()) {
            return;
        }
        List<AudioSourceDescriptor> overrides = new ArrayList<>(current.overrideStack());
        AudioSourceDescriptor restored = overrides.removeFirst();
        restoreBackendLogicalSnapshot(new AudioBackendLogicalSnapshot(
                restored,
                false,
                false,
                current.speedShoesEnabled(),
                current.speedMultiplier(),
                overrides));
    }

    private void applyLogicalEndMusicOverride(int musicId) {
        AudioBackendLogicalSnapshot current = currentBackendLogicalSnapshot();
        if (current.currentMusic() != null && current.currentMusic().id() == musicId) {
            applyLogicalRestoreMusic();
            return;
        }
        List<AudioSourceDescriptor> overrides = new ArrayList<>(current.overrideStack());
        overrides.removeIf(descriptor -> descriptor != null && descriptor.id() == musicId);
        restoreBackendLogicalSnapshot(new AudioBackendLogicalSnapshot(
                current.currentMusic(),
                current.sfxBlocked(),
                current.pendingRestore(),
                current.speedShoesEnabled(),
                current.speedMultiplier(),
                overrides));
    }

    private AudioSourceDescriptor descriptorFor(AudioCommand.PlayMusic command) {
        return switch (command.route()) {
            case BASE_SMPS -> AudioSourceDescriptor.baseMusic(command.musicId());
            case DONOR_SMPS -> AudioSourceDescriptor.donorMusic(command.donorGameId(), command.musicId());
            case FALLBACK_WAV -> AudioSourceDescriptor.fallbackMusic(command.musicId());
            case SYSTEM_COMMAND -> null;
        };
    }

    private AudioBackendLogicalSnapshot currentBackendLogicalSnapshot() {
        return backend != null ? backend.captureLogicalSnapshot() : AudioBackendLogicalSnapshot.empty();
    }

    private void restoreBackendLogicalSnapshot(AudioBackendLogicalSnapshot snapshot) {
        if (backend != null) {
            backend.restoreLogicalSnapshot(snapshot);
        }
    }

    private void replayMusic(AudioCommand.PlayMusic command) {
        switch (command.route()) {
            case BASE_SMPS -> {
                if (smpsLoader != null) {
                    AbstractSmpsData data = smpsLoader.loadMusic(command.musicId());
                    if (data != null) {
                        backend.prepareLogicalMusicSource(AudioSourceDescriptor.baseMusic(command.musicId()));
                        backend.playSmps(data, dacData);
                    }
                }
            }
            case DONOR_SMPS -> {
                SmpsLoader loader = donorLoaders.get(command.donorGameId());
                DacData dData = donorDacData.get(command.donorGameId());
                if (loader != null && dData != null) {
                    AbstractSmpsData data = loader.loadMusic(command.musicId());
                    if (data != null) {
                        backend.prepareLogicalMusicSource(AudioSourceDescriptor.donorMusic(
                                command.donorGameId(), command.musicId()));
                        backend.playSmps(data, dData, donorConfigs.get(command.donorGameId()), true);
                    }
                }
            }
            case FALLBACK_WAV -> {
                backend.prepareLogicalMusicSource(AudioSourceDescriptor.fallbackMusic(command.musicId()));
                backend.playMusic(command.musicId());
            }
            case SYSTEM_COMMAND -> {
            }
        }
    }

    private void replaySfx(AudioCommand.PlaySfx command) {
        switch (command.route()) {
            case BASE_SMPS_ID -> {
                if (smpsLoader != null) {
                    AbstractSmpsData sfx = smpsLoader.loadSfx(command.sfxId());
                    if (sfx != null) {
                        backend.playSfxSmps(sfx, dacData, command.pitch());
                    }
                }
            }
            case BASE_SMPS_NAME -> {
                if (smpsLoader != null) {
                    AbstractSmpsData sfx = smpsLoader.loadSfx(command.sfxName());
                    if (sfx != null) {
                        backend.playSfxSmps(sfx, dacData, command.pitch());
                    }
                }
            }
            case DONOR_SMPS -> {
                SmpsLoader loader = donorLoaders.get(command.donorGameId());
                DacData dData = donorDacData.get(command.donorGameId());
                if (loader != null && dData != null) {
                    AbstractSmpsData sfx = loader.loadSfx(command.sfxId());
                    if (sfx != null) {
                        SmpsSequencerConfig config = donorConfigs.get(command.donorGameId());
                        if (config != null) {
                            backend.playSfxSmps(sfx, dData, command.pitch(), config);
                        } else {
                            backend.playSfxSmps(sfx, dData, command.pitch());
                        }
                    }
                }
            }
            case FALLBACK_NAME, RING_RESOLVED -> backend.playSfx(command.sfxName(), command.pitch());
        }
    }

    private boolean suppressingRewindReplay() {
        return rewindReplaySuppressionDepth > 0;
    }

    public void afterRewindRestore(int frame, AudioPresentationPolicy policy) {
        if (policy == null) {
            return;
        }
        if (policy != AudioPresentationPolicy.SUPPRESSED_INTERNAL_RESTORE) {
            endReverseAudioPresentation();
            deterministicAudioRuntime.flushPresentationFifo();
        }
        if (backend == null) {
            return;
        }
        switch (policy) {
            case SUPPRESSED_INTERNAL_RESTORE -> {
            }
            case STOP_TRANSIENT_SFX_RESYNC_MUSIC -> {
                backend.stopAllSfx();
                backend.restoreMusic();
            }
            case STOP_ALL_PRESENTATION -> {
                backend.stopAllSfx();
                backend.stopPlayback();
            }
        }
    }

    public void beginReverseAudioPresentation() {
        reverseAudioPresentationActive = true;
        deterministicAudioRuntime.beginReversePresentation();
        if (backend != null) {
            backend.beginReversePresentation();
        }
    }

    public boolean isReverseAudioPresentationActive() {
        return reverseAudioPresentationActive;
    }

    public void endReverseAudioPresentation() {
        reverseAudioPresentationActive = false;
        deterministicAudioRuntime.endReversePresentation();
        if (backend != null) {
            backend.endReversePresentation();
        }
    }

    public void setReversePlaybackRate(double rate) {
        deterministicAudioRuntime.setReversePlaybackRate(rate);
        if (backend != null) {
            backend.setReversePlaybackRate(rate);
        }
    }

    public void advancePausedFrameStepAudio() {
        advanceRuntimeFrame(FrameAudioMode.SILENT_STEP);
    }

    public void advanceGameplayFrameAudio() {
        advanceRuntimeFrame(FrameAudioMode.NORMAL);
    }

    private void advanceRuntimeFrame(FrameAudioMode mode) {
        deterministicAudioRuntime.advanceFrame(commandTimeline.currentFrame(), mode);
        audioFrameAdvanced = true;
    }

    public void restoreMusic() {
        if (suppressingRewindReplay()) {
            return;
        }
        recordTimelineCommand(new AudioCommand.RestoreMusic(AudioCommand.RestoreCause.EXPLICIT));
        if (sendLiveBackendCommands()) {
            backend.restoreMusic();
        }
    }

    public void setSpeedShoes(boolean enabled) {
        if (suppressingRewindReplay()) {
            return;
        }
        recordTimelineCommand(new AudioCommand.SetSpeedShoes(enabled));
        if (sendLiveBackendCommands()) {
            backend.setSpeedShoes(enabled);
        }
    }

    public void setSpeedMultiplier(int multiplier) {
        if (suppressingRewindReplay()) {
            return;
        }
        recordTimelineCommand(new AudioCommand.SetSpeedMultiplier(multiplier));
        if (sendLiveBackendCommands()) {
            backend.setSpeedMultiplier(multiplier);
        }
    }

    public void playMusic(int musicId) {
        if (suppressingRewindReplay()) {
            return;
        }
        if (audioProfile != null) {
            if (audioProfile.handleSystemCommand(musicId, this)) {
                return;
            }
            if (musicId == audioProfile.getSpeedShoesOnCommandId()) {
                if (audioProfile.getSpeedMode() == GameAudioProfile.SpeedMode.FRAME_MULTIPLY) {
                    setSpeedMultiplier(audioProfile.getSpeedMultiplierValue());
                } else {
                    setSpeedShoes(true);
                }
                return;
            } else if (musicId == audioProfile.getSpeedShoesOffCommandId()) {
                if (audioProfile.getSpeedMode() == GameAudioProfile.SpeedMode.FRAME_MULTIPLY) {
                    setSpeedMultiplier(1);
                } else {
                    setSpeedShoes(false);
                }
                return;
            }
        }

        if (smpsLoader != null) {
            AbstractSmpsData data = smpsLoader.loadMusic(musicId);
            if (data != null) {
                recordTimelineCommand(new AudioCommand.PlayMusic(
                        musicId, AudioCommand.MusicRoute.BASE_SMPS, false, null));
                if (sendLiveBackendCommands()) {
                    backend.prepareLogicalMusicSource(AudioSourceDescriptor.baseMusic(musicId));
                    backend.playSmps(data, dacData);
                }
                return;
            }
        }
        recordTimelineCommand(new AudioCommand.PlayMusic(
                musicId, AudioCommand.MusicRoute.FALLBACK_WAV, false, null));
        if (sendLiveBackendCommands()) {
            backend.prepareLogicalMusicSource(AudioSourceDescriptor.fallbackMusic(musicId));
            backend.playMusic(musicId);
        }
    }

    public boolean playMusic(GameMusic music) {
        Integer musicId = resolveMusic(audioProfile, music);
        if (musicId == null) {
            return false;
        }
        playMusic(musicId);
        return true;
    }

    private static Integer resolveMusic(GameAudioProfile profile, GameMusic music) {
        if (music == null || profile == null) {
            return null;
        }
        return profile.getMusicMap().get(music);
    }

    public void playSfx(String sfxName) {
        playSfx(sfxName, 1.0f);
    }

    public void playSfx(String sfxName, float pitch) {
        if (suppressingRewindReplay()) {
            return;
        }
        if (smpsLoader != null) {
            AbstractSmpsData sfx = smpsLoader.loadSfx(sfxName);
            if (sfx != null) {
                recordTimelineCommand(new AudioCommand.PlaySfx(
                        -1, sfxName, AudioCommand.SfxRoute.BASE_SMPS_NAME, pitch, null));
                if (sendLiveBackendCommands()) {
                    backend.playSfxSmps(sfx, dacData, pitch);
                }
                return;
            }
        }
        recordTimelineCommand(new AudioCommand.PlaySfx(
                -1, sfxName, AudioCommand.SfxRoute.FALLBACK_NAME, pitch, null));
        if (sendLiveBackendCommands()) {
            backend.playSfx(sfxName, pitch);
        }
    }

    public void playSfx(GameSound sound) {
        playSfx(sound, 1.0f);
    }

    public void playSfx(GameSound sound, float pitch) {
        if (suppressingRewindReplay()) {
            return;
        }
        if (sound == GameSound.RING) {
            playSfx(ringLeft ? GameSound.RING_LEFT : GameSound.RING_RIGHT, pitch);
            ringLeft = !ringLeft;
            return;
        }

        float effectivePitch = audioProfile != null ? audioProfile.adjustSfxPitch(sound, pitch) : pitch;
        boolean played = false;
        if (soundMap != null && soundMap.containsKey(sound)) {
            played = playSfx(soundMap.get(sound), effectivePitch);
        }
        if (!played) {
            DonorSfxBinding binding = donorSoundBindings.get(sound);
            if (binding != null) {
                SmpsLoader loader = donorLoaders.get(binding.gameId());
                DacData dData = donorDacData.get(binding.gameId());
                if (loader != null && dData != null) {
                    AbstractSmpsData sfx = loader.loadSfx(binding.sfxId());
                    if (sfx != null) {
                        recordTimelineCommand(new AudioCommand.PlaySfx(
                                binding.sfxId(),
                                sound.name(),
                                AudioCommand.SfxRoute.DONOR_SMPS,
                                effectivePitch,
                                binding.gameId()));
                        SmpsSequencerConfig donorConfig = donorConfigs.get(binding.gameId());
                        if (sendLiveBackendCommands()) {
                            if (donorConfig != null) {
                                backend.playSfxSmps(sfx, dData, effectivePitch, donorConfig);
                            } else {
                                backend.playSfxSmps(sfx, dData, effectivePitch);
                            }
                        }
                        played = true;
                    }
                }
            }
        }
        if (!played) {
            AudioCommand.SfxRoute route = sound == GameSound.RING_LEFT || sound == GameSound.RING_RIGHT
                    ? AudioCommand.SfxRoute.RING_RESOLVED
                    : AudioCommand.SfxRoute.FALLBACK_NAME;
            recordTimelineCommand(new AudioCommand.PlaySfx(
                    -1, sound.name(), route, effectivePitch, null));
            if (sendLiveBackendCommands()) {
                backend.playSfx(sound.name(), effectivePitch);
            }
        }
    }

    public boolean playSfx(int sfxId) {
        return playSfx(sfxId, 1.0f);
    }

    public boolean playSfx(int sfxId, float pitch) {
        if (suppressingRewindReplay()) {
            return false;
        }
        if (smpsLoader != null) {
            AbstractSmpsData sfx = smpsLoader.loadSfx(sfxId);
            if (sfx != null) {
                recordTimelineCommand(new AudioCommand.PlaySfx(
                        sfxId, null, AudioCommand.SfxRoute.BASE_SMPS_ID, pitch, null));
                if (sendLiveBackendCommands()) {
                    backend.playSfxSmps(sfx, dacData, pitch);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Plays an SFX from a donor game's SMPS loader with the donor's sequencer config.
     * Used for cross-game SFX that aren't in the base game's sound map (e.g., S3K
     * Super Sonic transformation sound).
     *
     * @param donorGameId the donor game identifier (e.g., "s3k")
     * @param sfxId the SFX ID in the donor game's format
     */
    public void playDonorSfx(String donorGameId, int sfxId) {
        if (suppressingRewindReplay()) {
            return;
        }
        SmpsLoader loader = donorLoaders.get(donorGameId);
        DacData dData = donorDacData.get(donorGameId);
        if (loader != null && dData != null) {
            AbstractSmpsData sfx = loader.loadSfx(sfxId);
            if (sfx != null) {
                recordTimelineCommand(new AudioCommand.PlaySfx(
                        sfxId, null, AudioCommand.SfxRoute.DONOR_SMPS, 1.0f, donorGameId));
                SmpsSequencerConfig config = donorConfigs.get(donorGameId);
                if (sendLiveBackendCommands()) {
                    if (config != null) {
                        backend.playSfxSmps(sfx, dData, 1.0f, config);
                    } else {
                        backend.playSfxSmps(sfx, dData, 1.0f);
                    }
                }
            }
        }
    }

    public void update() {
        if (!reverseAudioPresentationActive
                && deterministicAudioRuntime.consumesSubmittedCommands()
                && !audioFrameAdvanced) {
            advanceRuntimeFrame(FrameAudioMode.NORMAL);
        }
        backend.update();
        if (!audioFrameOwnedExternally) {
            commandTimeline.beginFrame(commandTimeline.currentFrame() + 1);
        }
        audioFrameOwnedExternally = false;
        audioFrameAdvanced = false;
    }

    /**
     * Plays music from a donor game's SMPS loader with the donor's sequencer config.
     * Used for cross-game Super Sonic music (e.g., S3K invincibility in an S2 base game).
     *
     * @param donorGameId the donor game identifier (e.g., "s3k")
     * @param musicId the music ID in the donor game's format
     */
    public void playDonorMusic(String donorGameId, int musicId) {
        if (suppressingRewindReplay()) {
            return;
        }
        SmpsLoader loader = donorLoaders.get(donorGameId);
        DacData dData = donorDacData.get(donorGameId);
        if (loader != null && dData != null) {
            AbstractSmpsData data = loader.loadMusic(musicId);
            if (data != null) {
                recordTimelineCommand(new AudioCommand.PlayMusic(
                        musicId, AudioCommand.MusicRoute.DONOR_SMPS, true, donorGameId));
                SmpsSequencerConfig config = donorConfigs.get(donorGameId);
                // forceOverride=true: the base game's audioProfile won't recognize
                // donor music IDs, so force the override path to push zone music
                // onto the stack for restoration when Super Sonic ends.
                if (sendLiveBackendCommands()) {
                    backend.prepareLogicalMusicSource(AudioSourceDescriptor.donorMusic(donorGameId, musicId));
                    backend.playSmps(data, dData, config, true);
                }
            }
        }
    }

    public boolean playDonorMusic(String donorGameId, GameMusic music) {
        Integer musicId = resolveDonorMusic(donorGameId, music);
        if (musicId == null) {
            return false;
        }
        playDonorMusic(donorGameId, musicId);
        return true;
    }

    public void endMusicOverride(int musicId) {
        if (suppressingRewindReplay()) {
            return;
        }
        recordTimelineCommand(new AudioCommand.EndMusicOverride(musicId));
        if (sendLiveBackendCommands()) {
            backend.endMusicOverride(musicId);
        }
    }

    public boolean endMusicOverride(GameMusic music) {
        if (music == null || audioProfile == null) {
            return false;
        }
        Integer musicId = audioProfile.getMusicMap().get(music);
        if (musicId == null) {
            return false;
        }
        endMusicOverride(musicId);
        return true;
    }

    public boolean endDonorMusicOverride(String donorGameId, GameMusic music) {
        Integer musicId = resolveDonorMusic(donorGameId, music);
        if (musicId == null) {
            return false;
        }
        endMusicOverride(musicId);
        return true;
    }

    private Integer resolveDonorMusic(String donorGameId, GameMusic music) {
        if (donorGameId == null || music == null) {
            return null;
        }
        Map<GameMusic, Integer> musicMap = donorMusicBindings.get(donorGameId);
        if (musicMap == null) {
            return null;
        }
        return musicMap.get(music);
    }

    /**
     * Change the music dividing timing (tempo).
     * ROM: Change_Music_Tempo. Lower values = faster playback.
     *
     * @param newDividingTiming the new dividing timing value
     */
    public void changeMusicTempo(int newDividingTiming) {
        if (suppressingRewindReplay()) {
            return;
        }
        recordTimelineCommand(new AudioCommand.ChangeMusicTempo(newDividingTiming));
        if (sendLiveBackendCommands()) {
            backend.changeMusicTempo(newDividingTiming);
        }
    }

    /**
     * Stops all playing SFX without affecting music.
     * Clears both SFX sequencers in the active music driver and the standalone SFX stream.
     */
    public void stopAllSfx() {
        if (suppressingRewindReplay()) {
            return;
        }
        recordTimelineCommand(new AudioCommand.StopAllSfx());
        if (sendLiveBackendCommands()) {
            backend.stopAllSfx();
        }
    }

    /**
     * Stops all music and sound playback.
     * Used when exiting special stages or changing game modes.
     */
    public void stopMusic() {
        if (suppressingRewindReplay()) {
            return;
        }
        recordTimelineCommand(new AudioCommand.StopMusic());
        if (sendLiveBackendCommands()) {
            backend.stopPlayback();
        }
    }

    /**
     * Fade out the currently playing music using ROM default timing.
     * ROM equivalent: MusID_FadeOut (0xF9) / zFadeOutMusic.
     * Does not affect SFX - only music channels fade.
     *
     * <p>ROM uses fadeOutMusic() in these situations (for future implementation):
     * <ul>
     *   <li>Special stage entry (s2.asm:6540) - IMPLEMENTED</li>
     *   <li>Special stage checkpoint fail (Obj5A, s2.asm:71358, 71878) - IMPLEMENTED</li>
     *   <li>Level entry - before entering a level with title card (s2.asm:4757) - IMPLEMENTED</li>
     *   <li>Boss area triggers - when approaching end-of-act boss fights
     *       (EHZ:20404, MTZ:20512, HTZ:21230, HPZ:21332, ARZ:21421, MCZ:21529, OOZ:21613, CNZ:21760)</li>
     *   <li>Title screen - starting new game (s2.asm:4526)</li>
     *   <li>Demo playback - before playing a demo (s2.asm:4581)</li>
     *   <li>WFZ/DEZ boss setup (s2.asm:77011, 80751)</li>
     *   <li>Ending sequence - final boss defeated, going to credits (s2.asm:82064, 82525)</li>
     * </ul>
     */
    /**
     * Monotonic count of music fade-out requests actually issued (excludes
     * rewind-replay-suppressed calls). Lets out-of-engine observers — e.g. the
     * trace video-capture tool — detect a music fade such as the AIZ2 end-boss
     * fade in {@code AizEndBossInstance} without inspecting object state.
     */
    private long fadeOutMusicCount;

    /** @return the monotonic {@link #fadeOutMusic(int, int)} request count. */
    public long musicFadeOutCount() {
        return fadeOutMusicCount;
    }

    public void fadeOutMusic() {
        // ROM default: 0x28 (40) steps, delay of 3 frames between steps
        fadeOutMusic(0x28, 3);
    }

    /**
     * Fade out the currently playing music over time.
     * ROM equivalent: MusID_FadeOut (0xF9) / zFadeOutMusic.
     * Does not affect SFX - only music channels fade.
     *
     * @param steps total number of volume steps (ROM default: 0x28 = 40)
     * @param delay frames between each volume step (ROM default: 3)
     */
    public void fadeOutMusic(int steps, int delay) {
        if (suppressingRewindReplay()) {
            return;
        }
        fadeOutMusicCount++;
        recordTimelineCommand(new AudioCommand.FadeOutMusic(steps, delay));
        if (sendLiveBackendCommands()) {
            backend.fadeOutMusic(steps, delay);
        }
    }

    /**
     * Registers a donor SmpsLoader and DacData for cross-game SFX playback.
     */
    public void registerDonorLoader(String gameId, SmpsLoader loader, DacData dacData) {
        clearRestoreSmpsResolveCache();
        donorLoaders.put(gameId, loader);
        this.donorDacData.put(gameId, dacData);
    }

    /**
     * Registers a donor SmpsLoader, DacData, and SmpsSequencerConfig for cross-game SFX playback.
     * The config will be passed to the backend so the donor SFX uses the correct driver settings.
     */
    public void registerDonorLoader(String gameId, SmpsLoader loader, DacData dacData,
                                    SmpsSequencerConfig config) {
        clearRestoreSmpsResolveCache();
        donorLoaders.put(gameId, loader);
        this.donorDacData.put(gameId, dacData);
        if (config != null) {
            donorConfigs.put(gameId, config);
        }
    }

    public void registerDonorMusicMap(String gameId, Map<GameMusic, Integer> musicMap) {
        if (gameId == null || musicMap == null || musicMap.isEmpty()) {
            return;
        }
        donorMusicBindings.put(gameId, Map.copyOf(musicMap));
    }

    /**
     * Registers a donor sound binding so that a GameSound missing from the
     * base game's sound map will be routed through the specified donor loader.
     */
    public void registerDonorSound(GameSound sound, String gameId, int sfxId) {
        donorSoundBindings.put(sound, new DonorSfxBinding(gameId, sfxId));
    }

    /**
     * Clears all donor audio state (loaders, DAC data, and sound bindings).
     */
    public void clearDonorAudio() {
        clearRestoreSmpsResolveCache();
        donorLoaders.clear();
        donorDacData.clear();
        donorConfigs.clear();
        donorSoundBindings.clear();
        donorMusicBindings.clear();
    }

    /**
     * Resets mutable state without destroying the singleton instance.
     * Used by TestEnvironment to prevent state leaking between tests
     * (e.g. Sonic 1 SMPS loader contaminating Sonic 2 tests).
     */
    public void resetState() {
        if (backend != null) {
            backend.stopPlayback();
        }
        this.smpsLoader = null;
        this.dacData = null;
        this.soundMap = null;
        this.audioProfile = null;
        this.ringLeft = true;
        this.rewindReplaySuppressionDepth = 0;
        this.audioFrameOwnedExternally = false;
        this.audioFrameAdvanced = false;
        this.reverseAudioPresentationActive = false;
        this.deterministicAudioRuntime.clearSubmittedCommands();
        this.deterministicAudioRuntime.clearPcmHistory();
        this.commandTimeline.clear();
        clearDonorAudio();
        this.deterministicRuntimeExplicitlyConfigured = false;
        configureDeterministicRuntimeForBackend();
    }

    private AudioTimelineEntry recordTimelineCommand(AudioCommand command) {
        if (!suppressingRewindReplay()) {
            AudioTimelineEntry entry = commandTimeline.record(command);
            deterministicAudioRuntime.submit(entry);
            return entry;
        }
        return null;
    }

    private boolean sendLiveBackendCommands() {
        return backend != null && !deterministicAudioRuntime.consumesSubmittedCommands();
    }

    public void destroy() {
        if (backend != null) {
            backend.destroy();
        }
    }

    /**
     * Pauses audio playback. Called when the game window is minimized or loses focus.
     */
    public void pause() {
        if (backend != null) {
            backend.pause();
        }
    }

    /**
     * Resumes audio playback after being paused.
     */
    public void resume() {
        if (backend != null) {
            backend.resume();
        }
    }
}
