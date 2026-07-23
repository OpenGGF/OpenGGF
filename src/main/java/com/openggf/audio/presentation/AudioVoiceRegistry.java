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
import com.openggf.audio.presentation.AudioPresentationCommand.SetSpeedMultiplier;
import com.openggf.audio.presentation.AudioPresentationCommand.SetSpeedShoes;
import com.openggf.audio.presentation.AudioPresentationCommand.SetVoiceGain;
import com.openggf.audio.presentation.AudioPresentationCommand.SetVoicePitch;
import com.openggf.audio.presentation.AudioPresentationCommand.StartSampleSfx;
import com.openggf.audio.presentation.AudioPresentationCommand.StopAllSfx;
import com.openggf.audio.presentation.AudioPresentationCommand.StopMusic;
import com.openggf.audio.presentation.AudioPresentationCommand.StopRawPcm;
import com.openggf.audio.presentation.AudioPresentationCommand.ToggleMute;
import com.openggf.audio.presentation.AudioPresentationCommand.ToggleSolo;
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

    private final Thread ownerThread;
    private final SmpsSfxInstantiation sfxInstantiation;
    private final SmpsCoordFlagHandlerOwner coordFlagHandlers;
    private final Consumer<String> warningConsumer;
    private final MusicVoiceEntry[] overrideStack =
            new MusicVoiceEntry[MAX_MUSIC_OVERRIDES];
    private final SampleBackedVoice[] sampleSfx =
            new SampleBackedVoice[MAX_SAMPLE_SFX_VOICES];
    private final PresentationVoice[] orderedVoices =
            new PresentationVoice[ORDERED_VOICE_CAPACITY];
    private final long[] deferredRemovals =
            new long[MAX_DEFERRED_MUTATIONS];
    private final long[] warnedRejectionVoiceIds =
            new long[WARNED_REJECTION_CAPACITY];

    private MusicVoiceEntry activeMusic;
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
        }, new SmpsCoordFlagHandlerOwner(new SmpsCoordFlagRuntimeState()),
                ignored -> {
                });
    }

    public AudioVoiceRegistry(
            SmpsSfxInstantiation sfxInstantiation,
            SmpsCoordFlagHandlerOwner coordFlagHandlers,
            Consumer<String> warningConsumer) {
        ownerThread = Thread.currentThread();
        this.sfxInstantiation =
                Objects.requireNonNull(sfxInstantiation, "sfxInstantiation");
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
            speedShoesEnabled = speedShoes.enabled();
            updateActiveMusicSpeed();
        } else if (command instanceof SetSpeedMultiplier speed) {
            speedMultiplier = speed.multiplier();
            updateActiveMusicSpeed();
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
        if (!rendering) {
            throw new IllegalStateException(
                    "voice removal may be deferred only during traversal");
        }
        deferRemovalInternal(voiceId);
    }

    public void onVoiceFailure(PresentationVoice voice) {
        Objects.requireNonNull(voice, "voice");
        warningConsumer.accept(
                "Audio presentation voice " + voice.voiceId()
                        + " failed and was removed");
        if (rendering) {
            deferRemovalInternal(voice.voiceId());
        } else {
            assertOwnerBoundary();
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
                coordFlagHandlers.state().snapshot());
    }

    public void restore(
            AudioPresentationSnapshot snapshot,
            AudioPresentationDependencyResolver resolver) {
        assertOwnerBoundary();
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(resolver, "resolver");
        stopAndRemoveAllVoices();

        coordFlagHandlers.state().restore(snapshot.coordFlagRuntimeState());
        PresentationVoice[] recreated =
                new PresentationVoice[snapshot.voices().size()];
        boolean[] claimed = new boolean[recreated.length];
        for (int index = 0; index < recreated.length; index++) {
            PresentationVoiceSnapshot voiceSnapshot = snapshot.voices().get(index);
            recreated[index] = recreate(voiceSnapshot, resolver);
        }

        activeMusic = restoreMusicSlot(
                snapshot.activeMusic(), recreated, claimed);
        if (snapshot.overrideStack().size() > overrideStack.length) {
            throw new IllegalArgumentException(
                    "snapshot override stack exceeds fixed capacity");
        }
        for (AudioPresentationSnapshot.MusicSlotSnapshot slot
                : snapshot.overrideStack()) {
            overrideStack[overrideCount++] =
                    restoreMusicSlot(slot, recreated, claimed);
        }

        if (snapshot.standaloneSmpsVoiceId() != null) {
            PresentationVoice voice = claimVoice(
                    snapshot.standaloneSmpsVoiceId(), recreated, claimed);
            if (!(voice instanceof SmpsCompositeVoice composite)) {
                throw new IllegalArgumentException(
                        "standalone SMPS slot does not reference a composite");
            }
            standaloneSmps = composite;
        }
        if (snapshot.rawPcmVoiceId() != null) {
            PresentationVoice voice = claimVoice(
                    snapshot.rawPcmVoiceId(), recreated, claimed);
            if (!(voice instanceof SampleBackedVoice sample)) {
                throw new IllegalArgumentException(
                        "raw PCM slot does not reference a sample voice");
            }
            rawPcm = sample;
        }

        for (int index = 0; index < recreated.length; index++) {
            if (claimed[index]) {
                continue;
            }
            if (!(recreated[index] instanceof SampleBackedVoice sample)) {
                throw new IllegalArgumentException(
                        "unclaimed composite voice in snapshot");
            }
            if (sampleSfxCount == sampleSfx.length) {
                throw new IllegalArgumentException(
                        "snapshot sample SFX exceeds fixed capacity");
            }
            insertSampleSorted(sample);
            claimed[index] = true;
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
        applyDriverControls();
        rebuildOrderedVoices();
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

    private void replaceMusic(MusicVoiceEntry music) {
        stopMusic();
        activeMusic = music;
        noteVoiceId(music.voice());
        applyActiveMusicControls();
    }

    private void pushMusicOverride(MusicVoiceEntry music) {
        if (activeMusic != null) {
            if (overrideCount == overrideStack.length) {
                throw new IllegalStateException(
                        "music override stack exceeds fixed capacity");
            }
            overrideStack[overrideCount++] = activeMusic;
        }
        activeMusic = music;
        noteVoiceId(music.voice());
        applyActiveMusicControls();
    }

    private void restoreMusicOverride() {
        if (activeMusic != null) {
            activeMusic.voice().stop();
        }
        activeMusic =
                overrideCount == 0 ? null : overrideStack[--overrideCount];
        if (overrideCount >= 0) {
            overrideStack[overrideCount] = null;
        }
        pendingRestore = false;
        applyActiveMusicControls();
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
            overrideStack[index].voice().stop();
            int remaining = overrideCount - index - 1;
            if (remaining > 0) {
                System.arraycopy(overrideStack, index + 1, overrideStack,
                        index, remaining);
            }
            overrideStack[--overrideCount] = null;
            return;
        }
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
        if (owner != null
                && owner.driver().extendContinuousSfx(
                source.continuousSfxId(), source.trackCount())) {
            return;
        }

        if (owner != null) {
            SmpsSequencer sequencer;
            try {
                sequencer =
                        sfxInstantiation.instantiateCached(source, owner.driver());
            } catch (RuntimeException cacheFailure) {
                warnRejected(source.standaloneVoiceId(),
                        "SMPS SFX cache rejected " + source.assetKey());
                return;
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
            return;
        }

        SmpsCompositeVoice standalone;
        try {
            standalone = sfxInstantiation.instantiateStandaloneCached(source);
        } catch (RuntimeException cacheFailure) {
            warnRejected(source.standaloneVoiceId(),
                    "SMPS SFX cache rejected " + source.assetKey());
            return;
        }
        if (standalone == null) {
            warnRejected(source.standaloneVoiceId(),
                    "SMPS SFX cache miss for " + source.assetKey());
            return;
        }
        standaloneSmps = standalone;
        noteVoiceId(standalone);
    }

    private void admitSampleSfx(SampleBackedVoice voice) {
        if (sfxBlocked) {
            warnRejected(voice.voiceId(),
                    "sample SFX blocked at presentation boundary");
            return;
        }
        if (sampleSfxCount < sampleSfx.length) {
            insertSampleSorted(voice);
            noteVoiceId(voice);
            return;
        }
        int replacement = -1;
        int replacementPriority = Integer.MAX_VALUE;
        for (int index = 0; index < sampleSfxCount; index++) {
            int existingPriority = sampleSfx[index].priority();
            if (existingPriority < voice.priority()
                    && existingPriority < replacementPriority) {
                replacement = index;
                replacementPriority = existingPriority;
            }
        }
        if (replacement < 0) {
            warnRejected(voice.voiceId(),
                    "sample SFX capacity rejected voice " + voice.voiceId());
            return;
        }
        sampleSfx[replacement].stop();
        int remaining = sampleSfxCount - replacement - 1;
        if (remaining > 0) {
            System.arraycopy(sampleSfx, replacement + 1, sampleSfx,
                    replacement, remaining);
        }
        sampleSfx[--sampleSfxCount] = null;
        insertSampleSorted(voice);
        noteVoiceId(voice);
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

    private void replaceRawPcm(SampleBackedVoice voice) {
        if (rawPcm != null && rawPcm != voice) {
            rawPcm.stop();
        }
        rawPcm = voice;
        noteVoiceId(voice);
    }

    private void stopRawPcm() {
        if (rawPcm != null) {
            rawPcm.stop();
            rawPcm = null;
        }
    }

    private void stopMusic() {
        if (activeMusic != null) {
            activeMusic.voice().stop();
            activeMusic = null;
        }
        for (int index = 0; index < overrideCount; index++) {
            overrideStack[index].voice().stop();
            overrideStack[index] = null;
        }
        overrideCount = 0;
        pendingRestore = false;
    }

    private void stopAllSfx() {
        stopOwnedSfx(activeMusic);
        for (int index = 0; index < overrideCount; index++) {
            stopOwnedSfx(overrideStack[index]);
        }
        if (standaloneSmps != null) {
            standaloneSmps.stop();
            standaloneSmps = null;
        }
        stopRawPcm();
        for (int index = 0; index < sampleSfxCount; index++) {
            sampleSfx[index].stop();
            sampleSfx[index] = null;
        }
        sampleSfxCount = 0;
    }

    private static void stopOwnedSfx(MusicVoiceEntry music) {
        if (music != null
                && music.voice() instanceof SmpsCompositeVoice composite) {
            composite.driver().stopAllSfx();
        }
    }

    private void fadeMusic(int steps, int delay) {
        SmpsSequencer sequencer = activeMusicSequencer();
        if (sequencer != null) {
            sequencer.triggerFadeOut(steps, delay);
        }
    }

    private void setVoiceGain(long voiceId, int gainQ16) {
        PresentationVoice voice = voiceById(voiceId);
        if (voice instanceof SampleBackedVoice sample) {
            PresentationVoiceSnapshot.Sample snapshot =
                    (PresentationVoiceSnapshot.Sample) sample.snapshot();
            sample.restore(new PresentationVoiceSnapshot.Sample(
                    snapshot.voiceId(), snapshot.priority(), snapshot.assetId(),
                    snapshot.musicId(), snapshot.sourceDescriptor(),
                    snapshot.sourcePositionQ32(), snapshot.sourceStepQ32(),
                    gainQ16, snapshot.looping(), snapshot.stopped()));
        }
    }

    private void setVoicePitch(long voiceId, long sourceStepQ32) {
        PresentationVoice voice = voiceById(voiceId);
        if (voice instanceof SampleBackedVoice sample) {
            PresentationVoiceSnapshot.Sample snapshot =
                    (PresentationVoiceSnapshot.Sample) sample.snapshot();
            sample.restore(new PresentationVoiceSnapshot.Sample(
                    snapshot.voiceId(), snapshot.priority(), snapshot.assetId(),
                    snapshot.musicId(), snapshot.sourceDescriptor(),
                    snapshot.sourcePositionQ32(), sourceStepQ32,
                    snapshot.gainQ16(), snapshot.looping(), snapshot.stopped()));
        }
    }

    private void updateActiveMusicSpeed() {
        SmpsSequencer sequencer = activeMusicSequencer();
        if (sequencer != null) {
            sequencer.setSpeedShoes(speedShoesEnabled);
            sequencer.setSpeedMultiplier(speedMultiplier);
        }
    }

    private void changeMusicTempo(int dividingTiming) {
        SmpsSequencer sequencer = activeMusicSequencer();
        if (sequencer != null) {
            sequencer.updateDividingTiming(dividingTiming);
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
        if (type == ChannelType.PSG) {
            psgMuteMask ^= bit;
        } else {
            fmMuteMask ^= bit;
        }
        applyDriverControls();
    }

    private void toggleSolo(ChannelType type, int channel) {
        int bit = channelBit(type, channel);
        if (type == ChannelType.PSG) {
            psgSoloMask ^= bit;
        } else {
            fmSoloMask ^= bit;
        }
        applyDriverControls();
    }

    private static int channelBit(ChannelType type, int channel) {
        int limit = type == ChannelType.PSG ? 4 : 6;
        if (channel < 0 || channel >= limit) {
            throw new IllegalArgumentException("channel outside " + type + " range");
        }
        return 1 << channel;
    }

    private void applyActiveMusicControls() {
        updateActiveMusicSpeed();
        if (activeMusic != null
                && activeMusic.voice() instanceof SmpsCompositeVoice composite) {
            applyDriverMasks(composite.driver());
        }
    }

    private void applyDriverControls() {
        applyActiveMusicControls();
        if (standaloneSmps != null) {
            applyDriverMasks(standaloneSmps.driver());
        }
    }

    private void applyDriverMasks(SmpsDriver driver) {
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
        if (activeMusic != null) {
            activeMusic.voice().stop();
            activeMusic = null;
        }
        for (int index = 0; index < overrideCount; index++) {
            overrideStack[index].voice().stop();
            overrideStack[index] = null;
        }
        overrideCount = 0;
        if (standaloneSmps != null) {
            standaloneSmps.stop();
            standaloneSmps = null;
        }
        stopRawPcm();
        for (int index = 0; index < sampleSfxCount; index++) {
            sampleSfx[index].stop();
            sampleSfx[index] = null;
        }
        sampleSfxCount = 0;
        rebuildOrderedVoices();
    }

    private void addMusicSnapshot(
            List<PresentationVoiceSnapshot> voices, MusicVoiceEntry music) {
        if (music != null) {
            addVoiceSnapshot(voices, music.voice(), music);
        }
    }

    private void addVoiceSnapshot(
            List<PresentationVoiceSnapshot> voices,
            PresentationVoice voice,
            MusicVoiceEntry music) {
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
            MusicVoiceEntry music) {
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

    private MusicVoiceEntry restoreMusicSlot(
            AudioPresentationSnapshot.MusicSlotSnapshot slot,
            PresentationVoice[] recreated,
            boolean[] claimed) {
        if (slot == null) {
            return null;
        }
        PresentationVoice voice =
                claimVoice(slot.voiceId(), recreated, claimed);
        return new MusicVoiceEntry(
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
        warningConsumer.accept(warning);
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
}
