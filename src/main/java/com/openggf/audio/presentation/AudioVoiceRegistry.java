package com.openggf.audio.presentation;

import com.openggf.audio.ChannelType;
import com.openggf.audio.driver.SmpsDriver;
import com.openggf.audio.presentation.AudioPresentationCommand.AddSmpsSfx;
import com.openggf.audio.presentation.AudioPresentationCommand.ChangeMusicTempo;
import com.openggf.audio.presentation.AudioPresentationCommand.EndMusicOverride;
import com.openggf.audio.presentation.AudioPresentationCommand.FadeMusic;
import com.openggf.audio.presentation.AudioPresentationCommand.HardReset;
import com.openggf.audio.presentation.AudioPresentationCommand.MusicVoiceEntry;
import com.openggf.audio.presentation.AudioPresentationCommand.PushMusicOverride;
import com.openggf.audio.presentation.AudioPresentationCommand.ReplaceMusic;
import com.openggf.audio.presentation.AudioPresentationCommand.ReplaceRawPcm;
import com.openggf.audio.presentation.AudioPresentationCommand.ResetRingAlternation;
import com.openggf.audio.presentation.AudioPresentationCommand.RestoreMusicOverride;
import com.openggf.audio.presentation.AudioPresentationCommand.RewindBoundary;
import com.openggf.audio.presentation.AudioPresentationCommand.SampleVoiceDescriptor;
import com.openggf.audio.presentation.AudioPresentationCommand.SetSpeedMultiplier;
import com.openggf.audio.presentation.AudioPresentationCommand.SetSpeedShoes;
import com.openggf.audio.presentation.AudioPresentationCommand.SetVoiceGain;
import com.openggf.audio.presentation.AudioPresentationCommand.SetVoicePitch;
import com.openggf.audio.presentation.AudioPresentationCommand.StartSampleSfx;
import com.openggf.audio.presentation.AudioPresentationCommand.StopAllSfx;
import com.openggf.audio.presentation.AudioPresentationCommand.StopMusic;
import com.openggf.audio.presentation.AudioPresentationCommand.StopRawPcm;
import com.openggf.audio.presentation.AudioPresentationCommand.SmpsVoiceDescriptor;
import com.openggf.audio.presentation.AudioPresentationCommand.ToggleMute;
import com.openggf.audio.presentation.AudioPresentationCommand.ToggleSolo;
import com.openggf.audio.presentation.AudioPresentationCommand.VoiceDescriptor;
import com.openggf.audio.smps.SmpsCoordFlagHandlerOwner;
import com.openggf.audio.smps.SmpsCoordFlagRuntimeState;
import com.openggf.audio.smps.SmpsSequencer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Deterministic fixed-slot owner for software presentation voices.
 */
public final class AudioVoiceRegistry implements PresentationVoiceSource {
    public static final int MAX_SAMPLE_SFX_VOICES = 32;
    public static final int MAX_DEFERRED_MUTATIONS = 64;
    private static final int MAX_MUSIC_OVERRIDES =
            AudioPresentationCommandQueue.CAPACITY;
    private static final int ORDERED_VOICE_CAPACITY =
            MAX_SAMPLE_SFX_VOICES + 3;
    private static final int WARNED_REJECTION_CAPACITY =
            AudioPresentationCommandQueue.CAPACITY;

    private record MusicSlot(
            int musicId,
            com.openggf.audio.rewind.AudioSourceDescriptor sourceDescriptor,
            PresentationVoice voice) {
    }

    private record PreparedRestore(
            MusicSlot activeMusic,
            MusicSlot[] overrides,
            SmpsCompositeVoice standaloneSmps,
            SampleBackedVoice rawPcm,
            SampleBackedVoice[] sampleSfx) {
    }

    public static final class PreparedSnapshotRestore {
        private final PreparedRestore voices;
        private final AudioPresentationSnapshot snapshot;
        private boolean consumed;

        private PreparedSnapshotRestore(
                PreparedRestore voices,
                AudioPresentationSnapshot snapshot) {
            this.voices = voices;
            this.snapshot = snapshot;
        }
    }

    private final Thread ownerThread;
    private final SmpsSfxInstantiation sfxInstantiation;
    private final AudioPresentationDependencyResolver dependencyResolver;
    private final SmpsCoordFlagHandlerOwner coordFlagHandlers;
    private final Consumer<String> warningConsumer;
    private final MusicSlot[] overrideStack =
            new MusicSlot[MAX_MUSIC_OVERRIDES];
    private final SampleBackedVoice[] sampleSfx =
            new SampleBackedVoice[MAX_SAMPLE_SFX_VOICES];
    private final PresentationVoice[] orderedVoices =
            new PresentationVoice[ORDERED_VOICE_CAPACITY];
    private final long[] deferredRemovals =
            new long[MAX_DEFERRED_MUTATIONS];
    private final long[] warnedRejectionVoiceIds =
            new long[WARNED_REJECTION_CAPACITY];

    private MusicSlot activeMusic;
    private SmpsCompositeVoice standaloneSmps;
    private SampleBackedVoice rawPcm;
    private int overrideCount;
    private int sampleSfxCount;
    private int orderedVoiceCount;
    private int deferredRemovalCount;
    private int completionSweepCount;
    private int warnedRejectionCount;
    private int warnedRejectionCursor;
    private boolean rendering;
    private boolean completionSweepRequired;
    private boolean sfxBlocked;
    private boolean pendingRestore;
    private boolean speedShoesEnabled;
    private int speedMultiplier = 1;
    private int fmMuteMask;
    private int fmSoloMask;
    private int psgMuteMask;
    private int psgSoloMask;
    private boolean ringLeft = true;
    private long nextVoiceId;

    public AudioVoiceRegistry() {
        this(new SmpsSfxInstantiation() {
            @Override
            public SmpsSequencer instantiateCached(
                    ResolvedSmpsSfxSource source, SmpsDriver currentOwner) {
                return null;
            }

            @Override
            public SmpsCompositeVoice instantiateStandaloneCached(
                    ResolvedSmpsSfxSource source) {
                return null;
            }
        }, rejectingDependencyResolver(),
                new SmpsCoordFlagHandlerOwner(new SmpsCoordFlagRuntimeState()),
                ignored -> {
                });
    }

    public AudioVoiceRegistry(
            SmpsSfxInstantiation sfxInstantiation,
            SmpsCoordFlagHandlerOwner coordFlagHandlers,
            Consumer<String> warningConsumer) {
        this(sfxInstantiation, rejectingDependencyResolver(),
                coordFlagHandlers, warningConsumer);
    }

    public AudioVoiceRegistry(
            SmpsSfxInstantiation sfxInstantiation,
            AudioPresentationDependencyResolver dependencyResolver,
            SmpsCoordFlagHandlerOwner coordFlagHandlers,
            Consumer<String> warningConsumer) {
        ownerThread = Thread.currentThread();
        this.sfxInstantiation =
                Objects.requireNonNull(sfxInstantiation, "sfxInstantiation");
        this.dependencyResolver =
                Objects.requireNonNull(dependencyResolver, "dependencyResolver");
        this.coordFlagHandlers =
                Objects.requireNonNull(coordFlagHandlers, "coordFlagHandlers");
        this.warningConsumer =
                Objects.requireNonNull(warningConsumer, "warningConsumer");
    }

    public void apply(AudioPresentationCommand command) {
        assertOwnerBoundary();
        Objects.requireNonNull(command, "command");

        if (command instanceof ReplaceMusic replace) {
            replaceMusic(replace.music());
        } else if (command instanceof PushMusicOverride push) {
            pushMusicOverride(push.music());
        } else if (command instanceof RestoreMusicOverride) {
            restoreMusicOverride();
        } else if (command instanceof EndMusicOverride end) {
            endMusicOverride(end.musicId());
        } else if (command instanceof AddSmpsSfx add) {
            addSmpsSfx(add.source());
        } else if (command instanceof StartSampleSfx start) {
            admitSampleSfx(start.voice());
        } else if (command instanceof ReplaceRawPcm replace) {
            replaceRawPcm(replace.voice());
        } else if (command instanceof StopRawPcm) {
            stopRawPcm();
        } else if (command instanceof StopMusic) {
            stopMusic();
        } else if (command instanceof StopAllSfx) {
            stopAllSfx();
        } else if (command instanceof FadeMusic fade) {
            fadeMusic(fade.steps(), fade.delay());
        } else if (command instanceof SetVoiceGain gain) {
            setVoiceGain(gain.voiceId(), gain.gainQ16());
        } else if (command instanceof SetVoicePitch pitch) {
            setVoicePitch(pitch.voiceId(), pitch.sourceStepQ32());
        } else if (command instanceof SetSpeedShoes speedShoes) {
            setSpeedShoes(speedShoes.enabled());
        } else if (command instanceof SetSpeedMultiplier speed) {
            setSpeedMultiplier(speed.multiplier());
        } else if (command instanceof ChangeMusicTempo tempo) {
            changeMusicTempo(tempo.dividingTiming());
        } else if (command instanceof ResetRingAlternation reset) {
            ringLeft = reset.ringLeft();
        } else if (command instanceof ToggleMute mute) {
            toggleMute(mute.type(), mute.channel());
        } else if (command instanceof ToggleSolo solo) {
            toggleSolo(solo.type(), solo.channel());
        } else if (command instanceof HardReset) {
            clear();
            return;
        } else if (!(command instanceof RewindBoundary)) {
            throw new IllegalArgumentException(
                    "unsupported presentation command " + command.getClass());
        }
        rebuildOrderedVoices();
    }

    @Override
    public int orderedVoiceCount() {
        return orderedVoiceCount;
    }

    @Override
    public PresentationVoice orderedVoiceAt(int index) {
        if (index < 0 || index >= orderedVoiceCount) {
            throw new IndexOutOfBoundsException(index);
        }
        return orderedVoices[index];
    }

    public long allocateVoiceId() {
        assertOwnerBoundary();
        return nextVoiceId++;
    }

    public boolean isMuted(ChannelType type, int channel) {
        int bit = channelBit(type, channel);
        return type == ChannelType.PSG
                ? (psgMuteMask & bit) != 0
                : (fmMuteMask & bit) != 0;
    }

    public boolean isSoloed(ChannelType type, int channel) {
        int bit = channelBit(type, channel);
        return type == ChannelType.PSG
                ? (psgSoloMask & bit) != 0
                : (fmSoloMask & bit) != 0;
    }

    public void beginRendering() {
        assertOwnerThread();
        if (rendering) {
            throw new IllegalStateException("audio voice traversal already active");
        }
        rendering = true;
        deferredRemovalCount = 0;
        completionSweepRequired = false;
    }

    public void endRendering() {
        assertOwnerThread();
        if (!rendering) {
            throw new IllegalStateException("audio voice traversal is not active");
        }
        for (int index = 0; index < orderedVoiceCount; index++) {
            PresentationVoice voice = orderedVoices[index];
            if (voice.isComplete()) {
                deferRemovalInternal(voice.voiceId());
            }
        }
        rendering = false;

        if (completionSweepRequired) {
            completionSweepCount++;
            sweepCompletedAndDeferredVoices();
        } else {
            for (int index = 0; index < deferredRemovalCount; index++) {
                removeVoiceById(deferredRemovals[index]);
            }
        }
        deferredRemovalCount = 0;
        completionSweepRequired = false;
        rebuildOrderedVoices();
    }

    public boolean isRendering() {
        return rendering;
    }

    public void deferRemoval(long voiceId) {
        assertOwnerThread();
        if (!rendering) {
            throw new IllegalStateException(
                    "voice removal may be deferred only during traversal");
        }
        deferRemovalInternal(voiceId);
    }

    public void onVoiceFailure(PresentationVoice voice) {
        assertOwnerThread();
        Objects.requireNonNull(voice, "voice");
        warningConsumer.accept(
                "Audio presentation voice " + voice.voiceId()
                        + " failed and was removed");
        if (rendering) {
            deferRemovalInternal(voice.voiceId());
        } else {
            removeVoiceById(voice.voiceId());
            rebuildOrderedVoices();
        }
    }

    public boolean completionSweepRequired() {
        return completionSweepRequired;
    }

    public int completionSweepCount() {
        return completionSweepCount;
    }

    public AudioPresentationSnapshot snapshot() {
        assertOwnerBoundary();
        List<PresentationVoiceSnapshot> voices = new ArrayList<>();
        addMusicSnapshot(voices, activeMusic);
        for (int index = 0; index < overrideCount; index++) {
            addMusicSnapshot(voices, overrideStack[index]);
        }
        addVoiceSnapshot(voices, standaloneSmps, null);
        addVoiceSnapshot(voices, rawPcm, null);
        for (int index = 0; index < sampleSfxCount; index++) {
            addVoiceSnapshot(voices, sampleSfx[index], null);
        }

        List<AudioPresentationSnapshot.MusicSlotSnapshot> overrides =
                new ArrayList<>(overrideCount);
        for (int index = 0; index < overrideCount; index++) {
            overrides.add(slotSnapshot(overrideStack[index]));
        }
        return new AudioPresentationSnapshot(
                nextVoiceId,
                voices,
                activeMusic == null ? null : slotSnapshot(activeMusic),
                overrides,
                standaloneSmps == null ? null : standaloneSmps.voiceId(),
                rawPcm == null ? null : rawPcm.voiceId(),
                fmMuteMask,
                fmSoloMask,
                psgMuteMask,
                psgSoloMask,
                sfxBlocked,
                pendingRestore,
                speedShoesEnabled,
                speedMultiplier,
                ringLeft,
                coordFlagHandlers.state().snapshot());
    }

    public void restore(
            AudioPresentationSnapshot snapshot,
            AudioPresentationDependencyResolver resolver) {
        commitPreparedRestore(prepareSnapshotRestore(snapshot, resolver));
    }

    public PreparedSnapshotRestore prepareSnapshotRestore(
            AudioPresentationSnapshot snapshot,
            AudioPresentationDependencyResolver resolver) {
        assertOwnerBoundary();
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(resolver, "resolver");
        SmpsCoordFlagRuntimeState.Snapshot previousCoordState =
                coordFlagHandlers.state().snapshot();
        PresentationVoice[] recreated =
                new PresentationVoice[snapshot.voices().size()];
        PreparedRestore prepared;
        try {
            coordFlagHandlers.state().restore(snapshot.coordFlagRuntimeState());
            for (int index = 0; index < recreated.length; index++) {
                PresentationVoiceSnapshot voiceSnapshot =
                        snapshot.voices().get(index);
                recreated[index] = recreate(voiceSnapshot, resolver);
            }
            prepared = prepareRestore(snapshot, recreated);
            applyPreparedControls(prepared, snapshot);
        } catch (RuntimeException failure) {
            for (PresentationVoice voice : recreated) {
                if (voice != null) {
                    voice.stop();
                }
            }
            throw failure;
        } finally {
            coordFlagHandlers.state().restore(previousCoordState);
        }
        return new PreparedSnapshotRestore(prepared, snapshot);
    }

    public void commitPreparedRestore(PreparedSnapshotRestore restore) {
        assertOwnerBoundary();
        Objects.requireNonNull(restore, "restore");
        if (restore.consumed) {
            throw new IllegalStateException(
                    "Prepared presentation restore already consumed");
        }
        restore.consumed = true;
        PreparedRestore prepared = restore.voices;
        AudioPresentationSnapshot snapshot = restore.snapshot;
        coordFlagHandlers.state().restore(snapshot.coordFlagRuntimeState());
        stopAndRemoveAllVoices();
        activeMusic = prepared.activeMusic();
        overrideCount = prepared.overrides().length;
        System.arraycopy(prepared.overrides(), 0, overrideStack, 0,
                overrideCount);
        standaloneSmps = prepared.standaloneSmps();
        rawPcm = prepared.rawPcm();
        for (SampleBackedVoice sample : prepared.sampleSfx()) {
            insertSampleSorted(sample);
        }

        nextVoiceId = snapshot.nextVoiceId();
        fmMuteMask = snapshot.fmMuteMask();
        fmSoloMask = snapshot.fmSoloMask();
        psgMuteMask = snapshot.psgMuteMask();
        psgSoloMask = snapshot.psgSoloMask();
        sfxBlocked = snapshot.sfxBlocked();
        pendingRestore = snapshot.pendingRestore();
        speedShoesEnabled = snapshot.speedShoesEnabled();
        speedMultiplier = snapshot.speedMultiplier();
        ringLeft = snapshot.ringLeft();
        rebuildOrderedVoices();
    }

    public void discardPreparedRestore(PreparedSnapshotRestore restore) {
        assertOwnerBoundary();
        if (restore == null || restore.consumed) {
            return;
        }
        restore.consumed = true;
        PreparedRestore prepared = restore.voices;
        if (prepared.activeMusic() != null) {
            prepared.activeMusic().voice().stop();
        }
        for (MusicSlot slot : prepared.overrides()) {
            slot.voice().stop();
        }
        if (prepared.standaloneSmps() != null) {
            prepared.standaloneSmps().stop();
        }
        if (prepared.rawPcm() != null) {
            prepared.rawPcm().stop();
        }
        for (SampleBackedVoice sample : prepared.sampleSfx()) {
            if (sample != null) {
                sample.stop();
            }
        }
    }

    private void applyPreparedControls(
            PreparedRestore prepared,
            AudioPresentationSnapshot snapshot) {
        if (prepared.activeMusic() != null
                && prepared.activeMusic().voice()
                instanceof SmpsCompositeVoice composite) {
            SmpsSequencer sequencer = composite.driver().firstMusicSequencer();
            if (sequencer != null) {
                sequencer.setSpeedShoes(snapshot.speedShoesEnabled());
                sequencer.setSpeedMultiplier(snapshot.speedMultiplier());
            }
            applyDriverMasks(composite.driver(), snapshot.fmMuteMask(),
                    snapshot.fmSoloMask(), snapshot.psgMuteMask(),
                    snapshot.psgSoloMask());
        }
        if (prepared.standaloneSmps() != null) {
            applyDriverMasks(prepared.standaloneSmps().driver(),
                    snapshot.fmMuteMask(), snapshot.fmSoloMask(),
                    snapshot.psgMuteMask(), snapshot.psgSoloMask());
        }
    }

    private PreparedRestore prepareRestore(
            AudioPresentationSnapshot snapshot,
            PresentationVoice[] recreated) {
        boolean[] claimed = new boolean[recreated.length];
        MusicSlot preparedActive = restoreMusicSlot(
                snapshot.activeMusic(), recreated, claimed);
        if (snapshot.overrideStack().size() > overrideStack.length) {
            throw new IllegalArgumentException(
                    "snapshot override stack exceeds fixed capacity");
        }
        MusicSlot[] preparedOverrides =
                new MusicSlot[snapshot.overrideStack().size()];
        int preparedOverrideCount = 0;
        for (AudioPresentationSnapshot.MusicSlotSnapshot slot
                : snapshot.overrideStack()) {
            preparedOverrides[preparedOverrideCount++] =
                    restoreMusicSlot(slot, recreated, claimed);
        }

        SmpsCompositeVoice preparedStandalone = null;
        if (snapshot.standaloneSmpsVoiceId() != null) {
            PresentationVoice voice = claimVoice(
                    snapshot.standaloneSmpsVoiceId(), recreated, claimed);
            if (!(voice instanceof SmpsCompositeVoice composite)) {
                throw new IllegalArgumentException(
                        "standalone SMPS slot does not reference a composite");
            }
            preparedStandalone = composite;
        }
        SampleBackedVoice preparedRawPcm = null;
        if (snapshot.rawPcmVoiceId() != null) {
            PresentationVoice voice = claimVoice(
                    snapshot.rawPcmVoiceId(), recreated, claimed);
            if (!(voice instanceof SampleBackedVoice sample)) {
                throw new IllegalArgumentException(
                        "raw PCM slot does not reference a sample voice");
            }
            preparedRawPcm = sample;
        }

        SampleBackedVoice[] preparedSamples =
                new SampleBackedVoice[recreated.length];
        int preparedSampleCount = 0;
        for (int index = 0; index < recreated.length; index++) {
            if (claimed[index]) {
                continue;
            }
            if (!(recreated[index] instanceof SampleBackedVoice sample)) {
                throw new IllegalArgumentException(
                        "unclaimed composite voice in snapshot");
            }
            if (preparedSampleCount == sampleSfx.length) {
                throw new IllegalArgumentException(
                        "snapshot sample SFX exceeds fixed capacity");
            }
            preparedSamples[preparedSampleCount++] = sample;
            claimed[index] = true;
        }
        return new PreparedRestore(
                preparedActive, preparedOverrides, preparedStandalone,
                preparedRawPcm,
                java.util.Arrays.copyOf(preparedSamples, preparedSampleCount));
    }

    public void stopTransientVoices() {
        assertOwnerBoundary();
        stopAllSfx();
        rebuildOrderedVoices();
    }

    public void clear() {
        assertOwnerBoundary();
        stopAndRemoveAllVoices();
        fmMuteMask = 0;
        fmSoloMask = 0;
        psgMuteMask = 0;
        psgSoloMask = 0;
        sfxBlocked = false;
        pendingRestore = false;
        speedShoesEnabled = false;
        speedMultiplier = 1;
        ringLeft = true;
        nextVoiceId = 0;
        deferredRemovalCount = 0;
        completionSweepCount = 0;
        completionSweepRequired = false;
        warnedRejectionCount = 0;
        warnedRejectionCursor = 0;
        coordFlagHandlers.reset();
        rebuildOrderedVoices();
    }

    public void setSfxBlocked(boolean blocked) {
        assertOwnerBoundary();
        sfxBlocked = blocked;
    }

    public void setPendingRestore(boolean pending) {
        assertOwnerBoundary();
        pendingRestore = pending;
    }

    private MusicSlot materializeMusic(MusicVoiceEntry music) {
        PresentationVoice voice = materializeVoice(music.voiceDescriptor());
        return new MusicSlot(
                music.musicId(), music.sourceDescriptor(), voice);
    }

    private SampleBackedVoice materializeSample(
            SampleVoiceDescriptor descriptor) {
        return (SampleBackedVoice) materializeVoice(descriptor);
    }

    private PresentationVoice materializeVoice(VoiceDescriptor descriptor) {
        PresentationVoice voice = null;
        try {
            voice = Objects.requireNonNull(
                    dependencyResolver.recreateVoice(descriptor),
                    "dependency resolver returned no voice");
            if (voice.voiceId() != descriptor.voiceId()
                    || voice.priority() != descriptor.priority()) {
                throw new IllegalStateException(
                        "dependency resolver changed voice identity");
            }
            if (descriptor instanceof SampleVoiceDescriptor
                    && !(voice instanceof SampleBackedVoice)) {
                throw new IllegalStateException(
                        "sample descriptor recreated as "
                                + voice.getClass().getName());
            }
            if (descriptor instanceof SmpsVoiceDescriptor
                    && !(voice instanceof SmpsCompositeVoice)) {
                throw new IllegalStateException(
                        "SMPS descriptor recreated as "
                                + voice.getClass().getName());
            }
            return voice;
        } catch (RuntimeException failure) {
            disposeUnpublishedVoice(voice, failure);
            throw failure;
        }
    }

    private void replaceMusic(MusicVoiceEntry entry) {
        MusicSlot music = materializeMusic(entry);
        boolean published = false;
        RuntimeException primaryFailure = null;
        try {
            applyMusicControls(music, speedShoesEnabled, speedMultiplier,
                    fmMuteMask, fmSoloMask, psgMuteMask, psgSoloMask);
            stopMusic();
            activeMusic = music;
            noteVoiceId(music.voice());
            published = true;
        } catch (RuntimeException failure) {
            primaryFailure = failure;
            throw failure;
        } finally {
            if (!published) {
                disposeUnpublishedVoice(music.voice(), primaryFailure);
            }
        }
    }

    private void pushMusicOverride(MusicVoiceEntry entry) {
        if (activeMusic != null
                && activeMusic.musicId() != entry.musicId()
                && overrideCount == overrideStack.length) {
            throw new IllegalStateException(
                    "music override stack exceeds fixed capacity");
        }

        MusicSlot music = materializeMusic(entry);
        boolean published = false;
        RuntimeException primaryFailure = null;
        try {
            applyMusicControls(music, speedShoesEnabled, speedMultiplier,
                    fmMuteMask, fmSoloMask, psgMuteMask, psgSoloMask);
            if (activeMusic != null
                    && activeMusic.musicId() == music.musicId()) {
                stopVoicesAtomically(activeMusic.voice());
                activeMusic = music;
                noteVoiceId(music.voice());
                published = true;
                return;
            }
            if (activeMusic != null) {
                overrideStack[overrideCount++] = activeMusic;
            }
            activeMusic = music;
            noteVoiceId(music.voice());
            published = true;
        } catch (RuntimeException failure) {
            primaryFailure = failure;
            throw failure;
        } finally {
            if (!published) {
                disposeUnpublishedVoice(music.voice(), primaryFailure);
            }
        }
    }

    private void restoreMusicOverride() {
        if (overrideCount == 0) {
            pendingRestore = false;
            return;
        }
        MusicSlot restored = overrideStack[overrideCount - 1];
        PresentationVoice current =
                activeMusic == null ? null : activeMusic.voice();
        mutateVoicesAtomically(() -> {
            applyMusicControls(restored, speedShoesEnabled, speedMultiplier,
                    fmMuteMask, fmSoloMask, psgMuteMask, psgSoloMask);
            if (current != null) {
                current.stop();
            }
        }, restored.voice(), current);
        activeMusic = restored;
        overrideStack[--overrideCount] = null;
        pendingRestore = false;
    }

    private void endMusicOverride(int musicId) {
        if (activeMusic != null && activeMusic.musicId() == musicId) {
            restoreMusicOverride();
            return;
        }
        for (int index = overrideCount - 1; index >= 0; index--) {
            if (overrideStack[index].musicId() != musicId) {
                continue;
            }
            stopVoicesAtomically(overrideStack[index].voice());
            int remaining = overrideCount - index - 1;
            if (remaining > 0) {
                System.arraycopy(overrideStack, index + 1, overrideStack,
                        index, remaining);
            }
            overrideStack[--overrideCount] = null;
            return;
        }
    }

    private void applyMusicControls(
            MusicSlot music,
            boolean targetSpeedShoes,
            int targetSpeedMultiplier,
            int targetFmMuteMask,
            int targetFmSoloMask,
            int targetPsgMuteMask,
            int targetPsgSoloMask) {
        if (!(music.voice() instanceof SmpsCompositeVoice composite)) {
            return;
        }
        SmpsSequencer sequencer = composite.driver().firstMusicSequencer();
        if (sequencer != null) {
            sequencer.setSpeedShoes(targetSpeedShoes);
            sequencer.setSpeedMultiplier(targetSpeedMultiplier);
        }
        applyDriverMasks(composite.driver(),
                targetFmMuteMask, targetFmSoloMask,
                targetPsgMuteMask, targetPsgSoloMask);
    }

    private void disposeUnpublishedVoice(
            PresentationVoice voice, RuntimeException primaryFailure) {
        if (voice == null) {
            return;
        }
        try {
            voice.stop();
        } catch (RuntimeException disposalFailure) {
            if (primaryFailure != null) {
                primaryFailure.addSuppressed(disposalFailure);
            } else {
                throw disposalFailure;
            }
        }
    }

    /*
     * Registry structure is published only after every throwable preparation
     * step completes. The helpers below capture only live voices touched by a
     * command. SMPS voices use their dedicated identity-bearing command token;
     * ordinary rewind snapshots keep their callback-free recreation semantics.
     */
    private void mutateVoicesAtomically(
            Runnable mutation, PresentationVoice... candidates) {
        PresentationVoice[] voices =
                new PresentationVoice[candidates.length];
        Object[] rollbackStates = new Object[candidates.length];
        int count = 0;
        for (PresentationVoice candidate : candidates) {
            if (candidate == null || containsIdentity(voices, count, candidate)) {
                continue;
            }
            voices[count] = candidate;
            rollbackStates[count] =
                    captureVoiceMutationState(candidate);
            count++;
        }
        try {
            mutation.run();
        } catch (RuntimeException failure) {
            for (int index = count - 1; index >= 0; index--) {
                try {
                    rollbackVoiceMutation(
                            voices[index], rollbackStates[index]);
                } catch (RuntimeException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            throw failure;
        }
    }

    private void stopVoicesAtomically(PresentationVoice... voices) {
        mutateVoicesAtomically(() -> {
            for (PresentationVoice voice : voices) {
                if (voice != null) {
                    voice.stop();
                }
            }
        }, voices);
    }

    private static boolean containsIdentity(
            PresentationVoice[] voices, int count, PresentationVoice candidate) {
        for (int index = 0; index < count; index++) {
            if (voices[index] == candidate) {
                return true;
            }
        }
        return false;
    }

    private static Object captureVoiceMutationState(
            PresentationVoice voice) {
        if (voice instanceof SmpsCompositeVoice composite) {
            return composite.captureLiveCommandMutation();
        }
        return voice.snapshot();
    }

    private static void rollbackVoiceMutation(
            PresentationVoice voice, Object rollbackState) {
        if (voice instanceof SampleBackedVoice sample
                && rollbackState
                instanceof PresentationVoiceSnapshot.Sample state) {
            sample.restore(state);
            return;
        }
        if (voice instanceof SmpsCompositeVoice composite
                && rollbackState
                instanceof SmpsCompositeVoice.LiveCommandMutationToken token) {
            composite.rollbackLiveCommandMutation(token);
            return;
        }
        throw new IllegalStateException(
                "unsupported presentation voice rollback "
                        + voice.getClass().getName());
    }

    private void addSmpsSfx(ResolvedSmpsSfxSource source) {
        if (sfxBlocked) {
            warnRejected(source.standaloneVoiceId(),
                    "SMPS SFX blocked at presentation boundary");
            return;
        }
        SmpsCompositeVoice owner = activeMusic != null
                && activeMusic.voice() instanceof SmpsCompositeVoice composite
                ? composite : standaloneSmps;
        if (owner != null) {
            SmpsCoordFlagRuntimeState.Snapshot coordState =
                    coordFlagHandlers.state().snapshot();
            try {
                mutateVoicesAtomically(
                        () -> addSmpsSfxToOwner(source, owner), owner);
            } catch (RuntimeException cacheFailure) {
                rollbackCoordFlagState(coordState, cacheFailure);
                if (!(cacheFailure instanceof SfxCacheRejection)) {
                    throw cacheFailure;
                }
                warnRejected(source.standaloneVoiceId(),
                        "SMPS SFX cache rejected " + source.assetKey());
            }
            return;
        }

        SmpsCoordFlagRuntimeState.Snapshot coordState =
                coordFlagHandlers.state().snapshot();
        SmpsCompositeVoice standalone;
        try {
            standalone = sfxInstantiation.instantiateStandaloneCached(source);
        } catch (RuntimeException cacheFailure) {
            rollbackCoordFlagState(coordState, cacheFailure);
            warnRejected(source.standaloneVoiceId(),
                    "SMPS SFX cache rejected " + source.assetKey());
            return;
        }
        if (standalone == null) {
            coordFlagHandlers.state().restore(coordState);
            warnRejected(source.standaloneVoiceId(),
                    "SMPS SFX cache miss for " + source.assetKey());
            return;
        }
        boolean published = false;
        RuntimeException primaryFailure = null;
        try {
            applyDriverMasks(standalone.driver());
            SmpsSequencer sequencer;
            try {
                sequencer = sfxInstantiation.instantiateCached(
                        source, standalone.driver());
            } catch (RuntimeException cacheFailure) {
                rollbackCoordFlagState(coordState, cacheFailure);
                warnRejected(source.standaloneVoiceId(),
                        "SMPS SFX cache rejected " + source.assetKey());
                return;
            }
            if (sequencer == null) {
                coordFlagHandlers.state().restore(coordState);
                warnRejected(source.standaloneVoiceId(),
                        "SMPS SFX cache miss for " + source.assetKey());
                return;
            }
            standalone.driver().addSequencer(sequencer, true);
            if (source.continuousSfxId() != 0) {
                standalone.driver().startContinuousSfx(
                        source.continuousSfxId(), source.trackCount());
            }
            standaloneSmps = standalone;
            noteVoiceId(standalone);
            published = true;
        } catch (RuntimeException failure) {
            primaryFailure = failure;
            rollbackCoordFlagState(coordState, failure);
            throw failure;
        } finally {
            if (!published) {
                disposeUnpublishedVoice(standalone, primaryFailure);
            }
        }
    }

    private void rollbackCoordFlagState(
            SmpsCoordFlagRuntimeState.Snapshot state,
            RuntimeException primaryFailure) {
        try {
            coordFlagHandlers.state().restore(state);
        } catch (RuntimeException rollbackFailure) {
            primaryFailure.addSuppressed(rollbackFailure);
        }
    }

    private void addSmpsSfxToOwner(
            ResolvedSmpsSfxSource source, SmpsCompositeVoice owner) {
        if (owner.driver().extendContinuousSfx(
                source.continuousSfxId(), source.trackCount())) {
            return;
        }
        SmpsSequencer sequencer;
        try {
            sequencer =
                    sfxInstantiation.instantiateCached(source, owner.driver());
        } catch (RuntimeException cacheFailure) {
            throw new SfxCacheRejection(cacheFailure);
        }
        if (sequencer == null) {
            warnRejected(source.standaloneVoiceId(),
                    "SMPS SFX cache miss for " + source.assetKey());
            return;
        }
        owner.driver().addSequencer(sequencer, true);
        if (source.continuousSfxId() != 0) {
            owner.driver().startContinuousSfx(
                    source.continuousSfxId(), source.trackCount());
        }
    }

    private static final class SfxCacheRejection extends RuntimeException {
        private SfxCacheRejection(RuntimeException cause) {
            super(cause);
        }
    }

    private void admitSampleSfx(SampleVoiceDescriptor descriptor) {
        if (sfxBlocked) {
            warnRejected(descriptor.voiceId(),
                    "sample SFX blocked at presentation boundary");
            return;
        }
        int replacement = -1;
        if (sampleSfxCount == sampleSfx.length) {
            int replacementPriority = Integer.MAX_VALUE;
            for (int index = 0; index < sampleSfxCount; index++) {
                int existingPriority = sampleSfx[index].priority();
                if (existingPriority < descriptor.priority()
                        && existingPriority < replacementPriority) {
                    replacement = index;
                    replacementPriority = existingPriority;
                }
            }
            if (replacement < 0) {
                warnRejected(descriptor.voiceId(),
                        "sample SFX capacity rejected voice "
                                + descriptor.voiceId());
                return;
            }
        }

        SampleBackedVoice voice = materializeSample(descriptor);
        boolean published = false;
        RuntimeException primaryFailure = null;
        try {
            if (replacement >= 0) {
                stopVoicesAtomically(sampleSfx[replacement]);
                int remaining = sampleSfxCount - replacement - 1;
                if (remaining > 0) {
                    System.arraycopy(sampleSfx, replacement + 1, sampleSfx,
                            replacement, remaining);
                }
                sampleSfx[--sampleSfxCount] = null;
            }
            insertSampleSorted(voice);
            noteVoiceId(voice);
            published = true;
        } catch (RuntimeException failure) {
            primaryFailure = failure;
            throw failure;
        } finally {
            if (!published) {
                disposeUnpublishedVoice(voice, primaryFailure);
            }
        }
    }

    private void insertSampleSorted(SampleBackedVoice voice) {
        int insertion = sampleSfxCount;
        while (insertion > 0
                && sampleSfx[insertion - 1].voiceId() > voice.voiceId()) {
            sampleSfx[insertion] = sampleSfx[insertion - 1];
            insertion--;
        }
        sampleSfx[insertion] = voice;
        sampleSfxCount++;
    }

    private void replaceRawPcm(SampleVoiceDescriptor descriptor) {
        SampleBackedVoice voice = materializeSample(descriptor);
        boolean published = false;
        RuntimeException primaryFailure = null;
        try {
            if (rawPcm != null && rawPcm != voice) {
                stopVoicesAtomically(rawPcm);
            }
            rawPcm = voice;
            noteVoiceId(voice);
            published = true;
        } catch (RuntimeException failure) {
            primaryFailure = failure;
            throw failure;
        } finally {
            if (!published) {
                disposeUnpublishedVoice(voice, primaryFailure);
            }
        }
    }

    private void stopRawPcm() {
        if (rawPcm != null) {
            stopVoicesAtomically(rawPcm);
            rawPcm = null;
        }
    }

    private void stopMusic() {
        PresentationVoice[] voices =
                new PresentationVoice[overrideCount + 1];
        int voiceCount = 0;
        if (activeMusic != null) {
            voices[voiceCount++] = activeMusic.voice();
        }
        for (int index = 0; index < overrideCount; index++) {
            voices[voiceCount++] = overrideStack[index].voice();
        }
        int capturedVoiceCount = voiceCount;
        mutateVoicesAtomically(() -> {
            for (int index = 0; index < capturedVoiceCount; index++) {
                voices[index].stop();
            }
        }, voices);
        activeMusic = null;
        for (int index = 0; index < overrideCount; index++) {
            overrideStack[index] = null;
        }
        overrideCount = 0;
        pendingRestore = false;
    }

    private void stopAllSfx() {
        PresentationVoice[] voices = allOwnedVoices();
        mutateVoicesAtomically(() -> {
            stopOwnedSfx(activeMusic);
            for (int index = 0; index < overrideCount; index++) {
                stopOwnedSfx(overrideStack[index]);
            }
            if (standaloneSmps != null) {
                standaloneSmps.stop();
            }
            if (rawPcm != null) {
                rawPcm.stop();
            }
            for (int index = 0; index < sampleSfxCount; index++) {
                sampleSfx[index].stop();
            }
        }, voices);
        standaloneSmps = null;
        rawPcm = null;
        for (int index = 0; index < sampleSfxCount; index++) {
            sampleSfx[index] = null;
        }
        sampleSfxCount = 0;
    }

    private PresentationVoice[] allOwnedVoices() {
        PresentationVoice[] voices =
                new PresentationVoice[overrideCount + sampleSfxCount + 4];
        int count = 0;
        if (activeMusic != null) {
            voices[count++] = activeMusic.voice();
        }
        for (int index = 0; index < overrideCount; index++) {
            voices[count++] = overrideStack[index].voice();
        }
        if (standaloneSmps != null) {
            voices[count++] = standaloneSmps;
        }
        if (rawPcm != null) {
            voices[count++] = rawPcm;
        }
        for (int index = 0; index < sampleSfxCount; index++) {
            voices[count++] = sampleSfx[index];
        }
        return java.util.Arrays.copyOf(voices, count);
    }

    private static void stopOwnedSfx(MusicSlot music) {
        if (music != null
                && music.voice() instanceof SmpsCompositeVoice composite) {
            composite.driver().stopAllSfx();
        }
    }

    private void fadeMusic(int steps, int delay) {
        SmpsSequencer sequencer = activeMusicSequencer();
        if (sequencer != null) {
            mutateVoicesAtomically(
                    () -> sequencer.triggerFadeOut(steps, delay),
                    activeMusic.voice());
        }
    }

    private void setVoiceGain(long voiceId, int gainQ16) {
        PresentationVoice voice = voiceById(voiceId);
        if (voice instanceof SampleBackedVoice sample) {
            PresentationVoiceSnapshot.Sample snapshot =
                    (PresentationVoiceSnapshot.Sample) sample.snapshot();
            mutateVoicesAtomically(() -> sample.restore(
                    new PresentationVoiceSnapshot.Sample(
                            snapshot.voiceId(), snapshot.priority(),
                            snapshot.assetId(), snapshot.musicId(),
                            snapshot.sourceDescriptor(),
                            snapshot.sourcePositionQ32(),
                            snapshot.sourceStepQ32(), gainQ16,
                            snapshot.looping(), snapshot.stopped())), sample);
        }
    }

    private void setVoicePitch(long voiceId, long sourceStepQ32) {
        PresentationVoice voice = voiceById(voiceId);
        if (voice instanceof SampleBackedVoice sample) {
            PresentationVoiceSnapshot.Sample snapshot =
                    (PresentationVoiceSnapshot.Sample) sample.snapshot();
            mutateVoicesAtomically(() -> sample.restore(
                    new PresentationVoiceSnapshot.Sample(
                            snapshot.voiceId(), snapshot.priority(),
                            snapshot.assetId(), snapshot.musicId(),
                            snapshot.sourceDescriptor(),
                            snapshot.sourcePositionQ32(), sourceStepQ32,
                            snapshot.gainQ16(), snapshot.looping(),
                            snapshot.stopped())), sample);
        }
    }

    private void setSpeedShoes(boolean enabled) {
        updateActiveMusicSpeed(enabled, speedMultiplier);
        speedShoesEnabled = enabled;
    }

    private void setSpeedMultiplier(int multiplier) {
        updateActiveMusicSpeed(speedShoesEnabled, multiplier);
        speedMultiplier = multiplier;
    }

    private void updateActiveMusicSpeed(
            boolean targetSpeedShoes, int targetSpeedMultiplier) {
        SmpsSequencer sequencer = activeMusicSequencer();
        if (sequencer != null) {
            mutateVoicesAtomically(() -> {
                sequencer.setSpeedShoes(targetSpeedShoes);
                sequencer.setSpeedMultiplier(targetSpeedMultiplier);
            }, activeMusic.voice());
        }
    }

    private void changeMusicTempo(int dividingTiming) {
        SmpsSequencer sequencer = activeMusicSequencer();
        if (sequencer != null) {
            mutateVoicesAtomically(
                    () -> sequencer.updateDividingTiming(dividingTiming),
                    activeMusic.voice());
        }
    }

    private SmpsSequencer activeMusicSequencer() {
        if (activeMusic != null
                && activeMusic.voice() instanceof SmpsCompositeVoice composite) {
            return composite.driver().firstMusicSequencer();
        }
        return null;
    }

    private void toggleMute(ChannelType type, int channel) {
        int bit = channelBit(type, channel);
        int targetFmMuteMask = fmMuteMask;
        int targetPsgMuteMask = psgMuteMask;
        if (type == ChannelType.PSG) {
            targetPsgMuteMask ^= bit;
        } else {
            targetFmMuteMask ^= bit;
        }
        applyDriverControlsAtomically(targetFmMuteMask, fmSoloMask,
                targetPsgMuteMask, psgSoloMask);
        fmMuteMask = targetFmMuteMask;
        psgMuteMask = targetPsgMuteMask;
    }

    private void toggleSolo(ChannelType type, int channel) {
        int bit = channelBit(type, channel);
        int targetFmSoloMask = fmSoloMask;
        int targetPsgSoloMask = psgSoloMask;
        if (type == ChannelType.PSG) {
            targetPsgSoloMask ^= bit;
        } else {
            targetFmSoloMask ^= bit;
        }
        applyDriverControlsAtomically(fmMuteMask, targetFmSoloMask,
                psgMuteMask, targetPsgSoloMask);
        fmSoloMask = targetFmSoloMask;
        psgSoloMask = targetPsgSoloMask;
    }

    private static int channelBit(ChannelType type, int channel) {
        int limit = type == ChannelType.PSG ? 4 : 6;
        if (channel < 0 || channel >= limit) {
            throw new IllegalArgumentException("channel outside " + type + " range");
        }
        return 1 << channel;
    }

    private void applyDriverControlsAtomically(
            int targetFmMuteMask,
            int targetFmSoloMask,
            int targetPsgMuteMask,
            int targetPsgSoloMask) {
        PresentationVoice active =
                activeMusic == null ? null : activeMusic.voice();
        mutateVoicesAtomically(() -> {
            if (active instanceof SmpsCompositeVoice composite) {
                applyDriverMasks(composite.driver(), targetFmMuteMask,
                        targetFmSoloMask, targetPsgMuteMask,
                        targetPsgSoloMask);
            }
            if (standaloneSmps != null) {
                applyDriverMasks(standaloneSmps.driver(), targetFmMuteMask,
                        targetFmSoloMask, targetPsgMuteMask,
                        targetPsgSoloMask);
            }
        }, active, standaloneSmps);
    }

    private void applyDriverMasks(SmpsDriver driver) {
        applyDriverMasks(driver, fmMuteMask, fmSoloMask,
                psgMuteMask, psgSoloMask);
    }

    private static void applyDriverMasks(
            SmpsDriver driver,
            int fmMuteMask,
            int fmSoloMask,
            int psgMuteMask,
            int psgSoloMask) {
        boolean anySolo = fmSoloMask != 0 || psgSoloMask != 0;
        for (int channel = 0; channel < 6; channel++) {
            int bit = 1 << channel;
            boolean mute = (fmMuteMask & bit) != 0
                    || (anySolo && (fmSoloMask & bit) == 0);
            driver.setFmMute(channel, mute);
        }
        for (int channel = 0; channel < 4; channel++) {
            int bit = 1 << channel;
            boolean mute = (psgMuteMask & bit) != 0
                    || (anySolo && (psgSoloMask & bit) == 0);
            driver.setPsgMute(channel, mute);
        }
    }

    private void deferRemovalInternal(long voiceId) {
        if (completionSweepRequired) {
            return;
        }
        for (int index = 0; index < deferredRemovalCount; index++) {
            if (deferredRemovals[index] == voiceId) {
                return;
            }
        }
        if (deferredRemovalCount < deferredRemovals.length) {
            deferredRemovals[deferredRemovalCount++] = voiceId;
        } else {
            completionSweepRequired = true;
        }
    }

    private void sweepCompletedAndDeferredVoices() {
        for (int index = orderedVoiceCount - 1; index >= 0; index--) {
            PresentationVoice voice = orderedVoices[index];
            if (voice.isComplete() || isDeferred(voice.voiceId())) {
                removeVoiceById(voice.voiceId());
            }
        }
    }

    private boolean isDeferred(long voiceId) {
        for (int index = 0; index < deferredRemovalCount; index++) {
            if (deferredRemovals[index] == voiceId) {
                return true;
            }
        }
        return false;
    }

    private void removeVoiceById(long voiceId) {
        if (activeMusic != null && activeMusic.voice().voiceId() == voiceId) {
            activeMusic = null;
            return;
        }
        if (standaloneSmps != null && standaloneSmps.voiceId() == voiceId) {
            standaloneSmps = null;
            return;
        }
        if (rawPcm != null && rawPcm.voiceId() == voiceId) {
            rawPcm = null;
            return;
        }
        for (int index = 0; index < sampleSfxCount; index++) {
            if (sampleSfx[index].voiceId() != voiceId) {
                continue;
            }
            int remaining = sampleSfxCount - index - 1;
            if (remaining > 0) {
                System.arraycopy(sampleSfx, index + 1, sampleSfx,
                        index, remaining);
            }
            sampleSfx[--sampleSfxCount] = null;
            return;
        }
    }

    private void rebuildOrderedVoices() {
        orderedVoiceCount = 0;
        if (activeMusic != null) {
            orderedVoices[orderedVoiceCount++] = activeMusic.voice();
        }
        if (standaloneSmps != null) {
            orderedVoices[orderedVoiceCount++] = standaloneSmps;
        }
        if (rawPcm != null) {
            orderedVoices[orderedVoiceCount++] = rawPcm;
        }
        for (int index = 0; index < sampleSfxCount; index++) {
            orderedVoices[orderedVoiceCount++] = sampleSfx[index];
        }
        for (int index = orderedVoiceCount; index < orderedVoices.length; index++) {
            orderedVoices[index] = null;
        }
    }

    private void stopAndRemoveAllVoices() {
        PresentationVoice[] voices = allOwnedVoices();
        stopVoicesAtomically(voices);
        activeMusic = null;
        for (int index = 0; index < overrideCount; index++) {
            overrideStack[index] = null;
        }
        overrideCount = 0;
        standaloneSmps = null;
        rawPcm = null;
        for (int index = 0; index < sampleSfxCount; index++) {
            sampleSfx[index] = null;
        }
        sampleSfxCount = 0;
        rebuildOrderedVoices();
    }

    private void addMusicSnapshot(
            List<PresentationVoiceSnapshot> voices, MusicSlot music) {
        if (music != null) {
            addVoiceSnapshot(voices, music.voice(), music);
        }
    }

    private void addVoiceSnapshot(
            List<PresentationVoiceSnapshot> voices,
            PresentationVoice voice,
            MusicSlot music) {
        if (voice == null || containsVoiceId(voices, voice.voiceId())) {
            return;
        }
        PresentationVoiceSnapshot snapshot = voice.snapshot();
        if (music != null
                && snapshot instanceof PresentationVoiceSnapshot.Sample sample) {
            snapshot = new PresentationVoiceSnapshot.Sample(
                    sample.voiceId(), sample.priority(), sample.assetId(),
                    music.musicId(), music.sourceDescriptor(),
                    sample.sourcePositionQ32(), sample.sourceStepQ32(),
                    sample.gainQ16(), sample.looping(), sample.stopped());
        } else if (music != null
                && snapshot instanceof PresentationVoiceSnapshot.Smps smps) {
            snapshot = new PresentationVoiceSnapshot.Smps(
                    smps.voiceId(), smps.priority(), music.musicId(),
                    music.sourceDescriptor(), smps.maxStereoFrames(),
                    smps.driver());
        }
        voices.add(snapshot);
    }

    private static boolean containsVoiceId(
            List<PresentationVoiceSnapshot> voices, long voiceId) {
        for (PresentationVoiceSnapshot voice : voices) {
            if (snapshotVoiceId(voice) == voiceId) {
                return true;
            }
        }
        return false;
    }

    private static long snapshotVoiceId(PresentationVoiceSnapshot snapshot) {
        if (snapshot instanceof PresentationVoiceSnapshot.Sample sample) {
            return sample.voiceId();
        }
        return ((PresentationVoiceSnapshot.Smps) snapshot).voiceId();
    }

    private static AudioPresentationSnapshot.MusicSlotSnapshot slotSnapshot(
            MusicSlot music) {
        return new AudioPresentationSnapshot.MusicSlotSnapshot(
                music.musicId(), music.sourceDescriptor(),
                music.voice().voiceId());
    }

    private PresentationVoice recreate(
            PresentationVoiceSnapshot snapshot,
            AudioPresentationDependencyResolver resolver) {
        if (snapshot instanceof PresentationVoiceSnapshot.Sample sample) {
            DecodedPcm pcm = Objects.requireNonNull(
                    resolver.resolvePcm(sample.assetId()),
                    "resolver returned no PCM for " + sample.assetId());
            return SampleBackedVoice.restore(sample, pcm);
        }
        SmpsCompositeVoice composite = Objects.requireNonNull(
                resolver.recreateSmps((PresentationVoiceSnapshot.Smps) snapshot),
                "resolver returned no SMPS composite");
        PresentationVoiceSnapshot.Smps smps =
                (PresentationVoiceSnapshot.Smps) snapshot;
        if (composite.voiceId() != smps.voiceId()
                || composite.snapshot() instanceof PresentationVoiceSnapshot.Smps
                recreated && recreated.maxStereoFrames() != smps.maxStereoFrames()) {
            throw new IllegalArgumentException(
                    "resolver did not honor composite snapshot identity");
        }
        return composite;
    }

    private MusicSlot restoreMusicSlot(
            AudioPresentationSnapshot.MusicSlotSnapshot slot,
            PresentationVoice[] recreated,
            boolean[] claimed) {
        if (slot == null) {
            return null;
        }
        PresentationVoice voice =
                claimVoice(slot.voiceId(), recreated, claimed);
        return new MusicSlot(
                slot.musicId(), slot.sourceDescriptor(), voice);
    }

    private static PresentationVoice claimVoice(
            long voiceId,
            PresentationVoice[] recreated,
            boolean[] claimed) {
        for (int index = 0; index < recreated.length; index++) {
            if (recreated[index].voiceId() == voiceId) {
                if (claimed[index]) {
                    throw new IllegalArgumentException(
                            "voice referenced by more than one ledger slot: "
                                    + voiceId);
                }
                claimed[index] = true;
                return recreated[index];
            }
        }
        throw new IllegalArgumentException(
                "ledger slot references missing voice " + voiceId);
    }

    private PresentationVoice voiceById(long voiceId) {
        if (activeMusic != null && activeMusic.voice().voiceId() == voiceId) {
            return activeMusic.voice();
        }
        for (int index = 0; index < overrideCount; index++) {
            if (overrideStack[index].voice().voiceId() == voiceId) {
                return overrideStack[index].voice();
            }
        }
        if (standaloneSmps != null && standaloneSmps.voiceId() == voiceId) {
            return standaloneSmps;
        }
        if (rawPcm != null && rawPcm.voiceId() == voiceId) {
            return rawPcm;
        }
        for (int index = 0; index < sampleSfxCount; index++) {
            if (sampleSfx[index].voiceId() == voiceId) {
                return sampleSfx[index];
            }
        }
        return null;
    }

    private void noteVoiceId(PresentationVoice voice) {
        nextVoiceId = Math.max(nextVoiceId, voice.voiceId() + 1);
    }

    private void warnRejected(long voiceId, String warning) {
        for (int index = 0; index < warnedRejectionCount; index++) {
            if (warnedRejectionVoiceIds[index] == voiceId) {
                return;
            }
        }
        if (warnedRejectionCount < warnedRejectionVoiceIds.length) {
            warnedRejectionVoiceIds[warnedRejectionCount++] = voiceId;
        } else {
            warnedRejectionVoiceIds[warnedRejectionCursor++] = voiceId;
            if (warnedRejectionCursor == warnedRejectionVoiceIds.length) {
                warnedRejectionCursor = 0;
            }
        }
        try {
            warningConsumer.accept(warning);
        } catch (RuntimeException ignored) {
            // Diagnostics cannot turn an accepted/rejected command into a
            // retained command whose warning-side effects are not reversible.
        }
    }

    private void assertOwnerBoundary() {
        assertOwnerThread();
        if (rendering) {
            throw new IllegalStateException(
                    "audio registry mutation is forbidden during rendering");
        }
    }

    private void assertOwnerThread() {
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException(
                    "audio registry accessed outside its owner thread");
        }
    }

    private static AudioPresentationDependencyResolver
            rejectingDependencyResolver() {
        return new AudioPresentationDependencyResolver() {
            @Override
            public DecodedPcm resolvePcm(String assetId) {
                throw new IllegalStateException(
                        "no presentation PCM resolver for " + assetId);
            }

            @Override
            public SmpsCompositeVoice recreateSmps(
                    PresentationVoiceSnapshot.Smps snapshot) {
                throw new IllegalStateException(
                        "no presentation SMPS snapshot resolver");
            }
        };
    }
}
