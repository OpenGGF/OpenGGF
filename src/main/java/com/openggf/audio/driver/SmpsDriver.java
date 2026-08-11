package com.openggf.audio.driver;

import com.openggf.audio.AudioStream;
import com.openggf.audio.MusicRestoreSink;
import com.openggf.audio.rewind.SmpsDriverSnapshot;
import com.openggf.audio.rewind.SmpsSourceDescriptor;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.audio.synth.VirtualSynthesizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class SmpsDriver extends VirtualSynthesizer implements AudioStream {
    public enum ReadMode {
        SAMPLE_ACCURATE,
        HYBRID
    }

    private static final int MIN_BATCH_SAMPLES = 32;

    private final Object sequencersLock = new Object();
    private final List<SmpsSequencer> sequencers = new ArrayList<>();
    private final Set<SmpsSequencer> sfxSequencers = new HashSet<>();
    private final SmpsSequencer[] fmLocks = new SmpsSequencer[6];
    private final SmpsSequencer[] psgLocks = new SmpsSequencer[4];
    private final Map<Object, Integer> psgLatches = new HashMap<>();
    private SmpsSequencer.Region region = SmpsSequencer.Region.NTSC;

    private final List<SmpsSequencer> pendingRemovals = new ArrayList<>();

    // Reusable buffer for stopAllSfx() to avoid per-call ArrayList allocation
    private final List<SmpsSequencer> sfxRemovalBuffer = new ArrayList<>();

    // Scratch buffer for read() to avoid per-frame allocations
    private final short[] scratchFrameBuf = new short[2];
    private short[] chunkScratch = new short[0];
    private ReadMode readMode = ReadMode.HYBRID;
    private int hybridChunkCountForTesting;

    // --- Continuous SFX state (Z80: zContinuousSFX, zContinuousSFXFlag, zContSFXLoopCnt) ---
    // S3K continuous SFX (0xBC+) loop via the 0xFC coord flag (cfLoopContinuousSFX).
    // When the same continuous SFX is re-triggered, the flag is set to extend playback
    // instead of restarting. The loop counter tracks how many tracks still need to hit
    // their loop point before the flag is consumed.
    private int continuousSfxId;
    private boolean continuousSfxFlag;
    private int contSfxLoopCnt;

    /**
     * Exact, identity-bearing rollback state for one live presentation command.
     * This is intentionally separate from {@link SmpsDriverSnapshot}, whose
     * rewind contract recreates sequencers and omits process-local callbacks.
     */
    public static final class LiveCommandMutationToken {
        private final SmpsDriver owner;
        private final SmpsSequencer[] sequencers;
        private final SmpsSequencer.LiveCommandMutationToken[] sequencerStates;
        private final SmpsSequencer[] sfxSequencers;
        private final SmpsSequencer[] fmLocks;
        private final SmpsSequencer[] psgLocks;
        private final Object[] psgLatchSources;
        private final int[] psgLatchChannels;
        private final SmpsSequencer[] pendingRemovals;
        private final SmpsSequencer[] sfxRemovalBuffer;
        private final SmpsSequencer.Region region;
        private final ReadMode readMode;
        private final int hybridChunkCountForTesting;
        private final int continuousSfxId;
        private final boolean continuousSfxFlag;
        private final int contSfxLoopCnt;
        private final DacData liveDacDataReference;
        private final VirtualSynthesizer.Snapshot synthSnapshot;

        private LiveCommandMutationToken(
                SmpsDriver owner,
                SmpsSequencer[] sequencers,
                SmpsSequencer.LiveCommandMutationToken[] sequencerStates,
                SmpsSequencer[] sfxSequencers,
                SmpsSequencer[] fmLocks,
                SmpsSequencer[] psgLocks,
                Object[] psgLatchSources,
                int[] psgLatchChannels,
                SmpsSequencer[] pendingRemovals,
                SmpsSequencer[] sfxRemovalBuffer,
                SmpsSequencer.Region region,
                ReadMode readMode,
                int hybridChunkCountForTesting,
                int continuousSfxId,
                boolean continuousSfxFlag,
                int contSfxLoopCnt,
                DacData liveDacDataReference,
                VirtualSynthesizer.Snapshot synthSnapshot) {
            this.owner = owner;
            this.sequencers = sequencers;
            this.sequencerStates = sequencerStates;
            this.sfxSequencers = sfxSequencers;
            this.fmLocks = fmLocks;
            this.psgLocks = psgLocks;
            this.psgLatchSources = psgLatchSources;
            this.psgLatchChannels = psgLatchChannels;
            this.pendingRemovals = pendingRemovals;
            this.sfxRemovalBuffer = sfxRemovalBuffer;
            this.region = region;
            this.readMode = readMode;
            this.hybridChunkCountForTesting = hybridChunkCountForTesting;
            this.continuousSfxId = continuousSfxId;
            this.continuousSfxFlag = continuousSfxFlag;
            this.contSfxLoopCnt = contSfxLoopCnt;
            this.liveDacDataReference = liveDacDataReference;
            this.synthSnapshot = synthSnapshot;
        }
    }

    public SmpsDriver() {
        super();
    }

    public SmpsDriver(double outputSampleRate) {
        super(outputSampleRate);
    }

    // --- Continuous SFX API ---

    /**
     * Prepares the continuous-extension fast path without mutating live state.
     * Returns {@code null} when the current SFX cannot be extended.
     */
    public PreparedSfxAdmission prepareContinuousSfxExtension(
            int sfxId, int trackCount) {
        validateContinuousMetadata(sfxId, trackCount);
        if (sfxId == 0) {
            return null;
        }
        synchronized (sequencersLock) {
            if (continuousSfxId != sfxId) {
                return null;
            }
            for (SmpsSequencer sequencer : sfxSequencers) {
                if (sequencer.getSmpsData().getId() == sfxId) {
                    return new PreparedSfxAdmission(
                            this, null, true, 0, 0, sfxId, trackCount,
                            null, null, null);
                }
            }
            return null;
        }
    }

    /**
     * Validates a new SFX and records only same-id/channel-bounded conflicts.
     */
    public PreparedSfxAdmission prepareNewSfxAdmission(
            SmpsSequencer sequencer, int sfxId, int trackCount) {
        Objects.requireNonNull(sequencer, "sequencer");
        validateContinuousMetadata(sfxId, trackCount);
        if (!sequencer.isBoundTo(this)) {
            throw new IllegalArgumentException(
                    "SFX sequencer belongs to another SMPS driver");
        }
        if (sequencer.getSfxPriority() < 0
                || sequencer.getSfxPriority() > 0xFF) {
            throw new IllegalArgumentException(
                    "SFX priority must fit one unsigned byte");
        }
        sequencer.validateSfxAdmissionMetadata();

        synchronized (sequencersLock) {
            if (sequencers.contains(sequencer)
                    || sfxSequencers.contains(sequencer)) {
                throw new IllegalArgumentException(
                        "SFX sequencer is already attached");
            }

            SmpsSequencer replaced = null;
            int newId = sequencer.getSmpsData().getId();
            for (SmpsSequencer existing : sfxSequencers) {
                if (existing.getSmpsData().getId() == newId) {
                    replaced = existing;
                    break;
                }
            }

            SmpsSequencer[] displacedOwners =
                    new SmpsSequencer[sequencer.trackCount()];
            SmpsSequencer.Track[] displacedTracks =
                    new SmpsSequencer.Track[sequencer.trackCount()];
            int fmMask = 0;
            int psgMask = 0;

            for (int newIndex = 0;
                    newIndex < sequencer.trackCount(); newIndex++) {
                SmpsSequencer.Track newTrack = sequencer.trackAt(newIndex);
                if (newTrack.type == SmpsSequencer.TrackType.FM
                        || newTrack.type == SmpsSequencer.TrackType.DAC) {
                    if (newTrack.channelId < 0
                            || newTrack.channelId >= fmLocks.length) {
                        throw new IllegalArgumentException(
                                "SFX FM channel is outside hardware bounds");
                    }
                    fmMask |= 1 << newTrack.channelId;
                } else if (newTrack.type == SmpsSequencer.TrackType.PSG) {
                    if (newTrack.channelId < 0
                            || newTrack.channelId >= psgLocks.length) {
                        throw new IllegalArgumentException(
                                "SFX PSG channel is outside hardware bounds");
                    }
                    psgMask |= 1 << newTrack.channelId;
                } else {
                    throw new IllegalArgumentException(
                            "SFX track has an unsupported channel type");
                }
            }

            for (int newIndex = 0;
                    newIndex < sequencer.trackCount(); newIndex++) {
                SmpsSequencer.Track newTrack = sequencer.trackAt(newIndex);
                for (SmpsSequencer existing : sfxSequencers) {
                    if (existing == replaced) {
                        continue;
                    }
                    for (int trackIndex = 0;
                            trackIndex < existing.trackCount(); trackIndex++) {
                        SmpsSequencer.Track track =
                                existing.trackAt(trackIndex);
                        if (!track.active
                                || track.type != newTrack.type
                                || track.channelId != newTrack.channelId
                                || isAlreadyDisplaced(displacedTracks,
                                        newIndex, track)) {
                            continue;
                        }
                        if (displacedOwners[newIndex] != null) {
                            throw new IllegalStateException(
                                    "multiple active SFX tracks own one exact channel type");
                        }
                        displacedOwners[newIndex] = existing;
                        displacedTracks[newIndex] = track;
                    }
                }
            }

            return new PreparedSfxAdmission(
                    this, sequencer, false, fmMask, psgMask,
                    sfxId, trackCount, replaced,
                    displacedOwners, displacedTracks);
        }
    }

    /** Applies one already-validated admission in deterministic native order. */
    public void commitSfxAdmission(PreparedSfxAdmission admission) {
        Objects.requireNonNull(admission, "admission");
        if (admission.owner() != this) {
            throw new IllegalArgumentException(
                    "prepared SFX admission belongs to another driver");
        }
        synchronized (sequencersLock) {
            if (admission.continuousExtension()) {
                admission.claimCommit();
                continuousSfxFlag = true;
                contSfxLoopCnt = admission.trackCount();
                return;
            }

            admission.claimCommit();
            LiveCommandMutationToken rollbackState;
            try {
                rollbackState = hasChipWriteObserver()
                        ? captureLiveCommandMutation() : null;
            } catch (RuntimeException failure) {
                admission.releaseCommit();
                throw failure;
            }
            try {
                commitNewSfxAdmission(admission);
            } catch (RuntimeException failure) {
                if (rollbackState != null) {
                    try {
                        rollbackLiveCommandMutation(rollbackState);
                    } catch (RuntimeException rollbackFailure) {
                        failure.addSuppressed(rollbackFailure);
                    } finally {
                        admission.releaseCommit();
                    }
                }
                throw failure;
            }
        }
    }

    private void commitNewSfxAdmission(PreparedSfxAdmission admission) {
        SmpsSequencer sequencer = admission.sequencer();
        sequencer.commitSfxAdmissionInitialization();
        sequencer.setRegion(region);
        sequencer.setIsSfx(true);

        SmpsSequencer replaced = admission.replacedSequencer;
        if (replaced != null) {
            sequencers.remove(replaced);
            releaseLocks(replaced);
            sfxSequencers.remove(replaced);
        }

        boolean killedPsg3Track = false;
        for (int action = 0;
                action < admission.displacedTracks.length; action++) {
            SmpsSequencer.Track track = admission.displacedTracks[action];
            if (track == null) {
                continue;
            }
            SmpsSequencer owner = admission.displacedOwners[action];
            track.active = false;
            owner.stopNote(track);
            int channel = track.channelId;
            if (track.type == SmpsSequencer.TrackType.PSG
                    && psgLocks[channel] == owner) {
                psgLocks[channel] = null;
                updateOverrides(SmpsSequencer.TrackType.PSG,
                        channel, false);
            } else if (track.type != SmpsSequencer.TrackType.PSG
                    && fmLocks[channel] == owner) {
                fmLocks[channel] = null;
                updateOverrides(SmpsSequencer.TrackType.FM,
                        channel, false);
            }
            killedPsg3Track |= track.type == SmpsSequencer.TrackType.PSG
                    && channel == 2;
        }

        if (sequencer.trackCount() > 0) {
            removeInactiveSfxSequencers();
        }
        if (killedPsg3Track) {
            writeRawPsg(0xDF);
            writeRawPsg(0xFF);
        }

        sequencers.add(sequencer);
        sfxSequencers.add(sequencer);
        restoreMusicDacData();
        if (admission.continuousSfxId() != 0) {
            continuousSfxFlag = false;
            continuousSfxId = admission.continuousSfxId();
            contSfxLoopCnt = admission.trackCount();
        }
    }

    private static void validateContinuousMetadata(
            int sfxId, int trackCount) {
        if (sfxId < 0 || sfxId > 0xFF) {
            throw new IllegalArgumentException(
                    "continuous SFX id must fit one unsigned byte");
        }
        if (trackCount < 0) {
            throw new IllegalArgumentException(
                    "continuous SFX track count must not be negative");
        }
    }

    private static boolean isAlreadyDisplaced(
            SmpsSequencer.Track[] tracks,
            int limit,
            SmpsSequencer.Track track) {
        for (int index = 0; index < limit; index++) {
            if (tracks[index] == track) {
                return true;
            }
        }
        return false;
    }

    private void removeInactiveSfxSequencers() {
        Iterator<SmpsSequencer> iterator = sfxSequencers.iterator();
        while (iterator.hasNext()) {
            SmpsSequencer sequencer = iterator.next();
            if (!allTracksInactive(sequencer)) {
                continue;
            }
            sequencers.remove(sequencer);
            releaseLocks(sequencer);
            iterator.remove();
        }
    }

    private static boolean allTracksInactive(SmpsSequencer sequencer) {
        for (int index = 0; index < sequencer.trackCount(); index++) {
            if (sequencer.trackAt(index).active) {
                return false;
            }
        }
        return true;
    }

    /**
     * Attempt to extend a currently-playing continuous SFX instead of restarting it.
     * ROM: when the same continuous SFX ID is re-triggered, set zContinuousSFXFlag = 0x80
     * and refresh zContSFXLoopCnt, but do NOT restart the SFX.
     *
     * @param sfxId the SFX ID being triggered
     * @param trackCount total FM+PSG track count for the SFX (from header byte 3)
     * @return true if the SFX was extended (caller should skip creating a new sequencer)
     */
    public boolean extendContinuousSfx(int sfxId, int trackCount) {
        PreparedSfxAdmission admission =
                prepareContinuousSfxExtension(sfxId, trackCount);
        if (admission == null) {
            return false;
        }
        commitSfxAdmission(admission);
        return true;
    }

    /**
     * Mark a new continuous SFX as starting playback.
     * ROM: store SFX index in zContinuousSFX, clear zContinuousSFXFlag, set loop count.
     */
    public void startContinuousSfx(int sfxId, int trackCount) {
        synchronized (sequencersLock) {
            continuousSfxFlag = false;
            continuousSfxId = sfxId;
            contSfxLoopCnt = trackCount;
        }
    }

    /** ROM: read zContinuousSFXFlag (0x80 when set). */
    public boolean isContinuousSfxFlagSet() {
        return continuousSfxFlag;
    }

    /** ROM: clear zContinuousSFX. */
    public void clearContinuousSfxId() {
        continuousSfxId = 0;
    }

    /** ROM: clear zContinuousSFXFlag. */
    public void clearContinuousSfxFlag() {
        continuousSfxFlag = false;
    }

    /**
     * ROM: decrement zContSFXLoopCnt and return whether it reached zero.
     * When zero, all tracks have passed their loop point for this extension cycle.
     */
    public boolean decrementContSfxLoopCnt() {
        return --contSfxLoopCnt <= 0;
    }

    public void setRegion(SmpsSequencer.Region region) {
        this.region = region;
        synchronized (sequencersLock) {
            for (SmpsSequencer seq : sequencers) {
                seq.setRegion(region);
            }
        }
    }

    public void setReadModeForTesting(ReadMode readMode) {
        this.readMode = readMode;
    }

    public SmpsSequencer firstMusicSequencer() {
        synchronized (sequencersLock) {
            for (SmpsSequencer sequencer : sequencers) {
                if (!isSfx(sequencer)) {
                    return sequencer;
                }
            }
            return null;
        }
    }

    public void bindMusicFadeCompleteCallback(Runnable callback) {
        Objects.requireNonNull(callback, "callback");
        synchronized (sequencersLock) {
            for (SmpsSequencer sequencer : sequencers) {
                if (!isSfx(sequencer)) {
                    sequencer.setOnFadeComplete(callback);
                }
            }
        }
    }

    public int getHybridChunkCountForTesting() {
        return hybridChunkCountForTesting;
    }

    public List<SmpsSequencer> sequencersForTesting() {
        synchronized (sequencersLock) {
            return List.copyOf(sequencers);
        }
    }

    public LiveCommandMutationToken captureLiveCommandMutation() {
        synchronized (sequencersLock) {
            SmpsSequencer[] capturedSequencers =
                    sequencers.toArray(SmpsSequencer[]::new);
            SmpsSequencer.LiveCommandMutationToken[] sequencerStates =
                    new SmpsSequencer.LiveCommandMutationToken[
                            capturedSequencers.length];
            for (int index = 0;
                 index < capturedSequencers.length;
                 index++) {
                sequencerStates[index] =
                        capturedSequencers[index]
                                .captureLiveCommandMutation();
            }
            Object[] latchSources = new Object[psgLatches.size()];
            int[] latchChannels = new int[psgLatches.size()];
            int latchIndex = 0;
            for (Map.Entry<Object, Integer> entry
                    : psgLatches.entrySet()) {
                latchSources[latchIndex] = entry.getKey();
                latchChannels[latchIndex] = entry.getValue();
                latchIndex++;
            }
            return new LiveCommandMutationToken(
                    this,
                    capturedSequencers,
                    sequencerStates,
                    sfxSequencers.toArray(SmpsSequencer[]::new),
                    fmLocks.clone(),
                    psgLocks.clone(),
                    latchSources,
                    latchChannels,
                    pendingRemovals.toArray(SmpsSequencer[]::new),
                    sfxRemovalBuffer.toArray(SmpsSequencer[]::new),
                    region,
                    readMode,
                    hybridChunkCountForTesting,
                    continuousSfxId,
                    continuousSfxFlag,
                    contSfxLoopCnt,
                    captureLiveDacDataReference(),
                    captureSynthSnapshot());
        }
    }

    public void rollbackLiveCommandMutation(
            LiveCommandMutationToken token) {
        Objects.requireNonNull(token, "token");
        if (token.owner != this) {
            throw new IllegalArgumentException(
                    "live command token belongs to another SMPS driver");
        }
        synchronized (sequencersLock) {
            for (int index = 0;
                 index < token.sequencers.length;
                 index++) {
                token.sequencers[index].rollbackLiveCommandMutation(
                        token.sequencerStates[index]);
            }

            sequencers.clear();
            for (SmpsSequencer sequencer : token.sequencers) {
                sequencers.add(sequencer);
            }
            sfxSequencers.clear();
            for (SmpsSequencer sequencer : token.sfxSequencers) {
                sfxSequencers.add(sequencer);
            }
            System.arraycopy(token.fmLocks, 0, fmLocks, 0,
                    fmLocks.length);
            System.arraycopy(token.psgLocks, 0, psgLocks, 0,
                    psgLocks.length);
            psgLatches.clear();
            for (int index = 0;
                 index < token.psgLatchSources.length;
                 index++) {
                psgLatches.put(token.psgLatchSources[index],
                        token.psgLatchChannels[index]);
            }
            pendingRemovals.clear();
            for (SmpsSequencer sequencer : token.pendingRemovals) {
                pendingRemovals.add(sequencer);
            }
            sfxRemovalBuffer.clear();
            for (SmpsSequencer sequencer : token.sfxRemovalBuffer) {
                sfxRemovalBuffer.add(sequencer);
            }
            region = token.region;
            readMode = token.readMode;
            hybridChunkCountForTesting =
                    token.hybridChunkCountForTesting;
            continuousSfxId = token.continuousSfxId;
            continuousSfxFlag = token.continuousSfxFlag;
            contSfxLoopCnt = token.contSfxLoopCnt;
            restoreLiveDacDataReference(token.liveDacDataReference);
            restoreSynthSnapshot(token.synthSnapshot);
        }
    }

    public SmpsDriverSnapshot captureSnapshot() {
        synchronized (sequencersLock) {
            IdentityHashMap<SmpsSequencer, Integer> sequencerIds = new IdentityHashMap<>();
            IdentityHashMap<AbstractSmpsData, SmpsSourceDescriptor> sourceDescriptors = new IdentityHashMap<>();
            for (SmpsSequencer sequencer : sequencers) {
                sourceDescriptors.put(sequencer.getSmpsData(), sequencer.getSourceDescriptor());
            }
            List<SmpsDriverSnapshot.SequencerEntry> entries = new ArrayList<>(sequencers.size());
            for (int i = 0; i < sequencers.size(); i++) {
                SmpsSequencer sequencer = sequencers.get(i);
                sequencerIds.put(sequencer, i);
                AbstractSmpsData fallbackVoiceData = sequencer.getFallbackVoiceData();
                SmpsSourceDescriptor fallbackVoiceSource = null;
                if (fallbackVoiceData != null) {
                    fallbackVoiceSource = sourceDescriptors.get(fallbackVoiceData);
                    if (fallbackVoiceSource == null) {
                        fallbackVoiceSource = SmpsSourceDescriptor.from(fallbackVoiceData);
                        sourceDescriptors.put(fallbackVoiceData, fallbackVoiceSource);
                    }
                }
                entries.add(new SmpsDriverSnapshot.SequencerEntry(
                        isSfx(sequencer),
                        sequencer.getSourceDescriptor(),
                        sequencer.getSourceDescriptorTrust(),
                        fallbackVoiceSource,
                        sequencer.getSmpsData(),
                        sequencer.getDacData(),
                        sequencer.getAudioManager(),
                        sequencer.getConfig(),
                        sequencer.captureSnapshot()));
            }

            return new SmpsDriverSnapshot(
                    region,
                    readMode,
                    continuousSfxId,
                    continuousSfxFlag,
                    contSfxLoopCnt,
                    entries,
                    captureLockIds(fmLocks, sequencerIds),
                    captureLockIds(psgLocks, sequencerIds),
                    captureSynthSnapshot());
        }
    }

    /**
     * Restores logical SMPS driver state only. Native/audio-chip presentation state is
     * cleared and must be refreshed by subsequent sequencer advancement.
     */
    public void restoreSnapshot(SmpsDriverSnapshot snapshot) {
        restoreSnapshot(snapshot, SmpsDriverSnapshot.liveReferences());
    }

    /**
     * Restores logical SMPS driver state using a caller-provided dependency resolver.
     * This is the descriptor-backed boundary used by rewind restore paths that should
     * not depend on object identity captured inside the snapshot.
     */
    public void restoreSnapshot(
            SmpsDriverSnapshot snapshot,
            SmpsDriverSnapshot.DependencyResolver resolver) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(resolver, "resolver");
        List<SmpsDriverSnapshot.SequencerEntry> entries = snapshot.sequencers();
        List<ResolvedSequencerDependencies> resolved = resolveSequencerDependencies(entries, resolver);
        synchronized (sequencersLock) {
            sequencers.clear();
            sfxSequencers.clear();
            psgLatches.clear();
            pendingRemovals.clear();
            Arrays.fill(fmLocks, null);
            Arrays.fill(psgLocks, null);

            region = snapshot.region();
            readMode = snapshot.readMode();
            continuousSfxId = snapshot.continuousSfxId();
            continuousSfxFlag = snapshot.continuousSfxFlag();
            contSfxLoopCnt = snapshot.contSfxLoopCnt();

            for (int i = 0; i < entries.size(); i++) {
                SmpsDriverSnapshot.SequencerEntry entry = entries.get(i);
                ResolvedSequencerDependencies dependency = resolved.get(i);
                SmpsSequencer sequencer = new SmpsSequencer(
                        dependency.smpsData(),
                        dependency.dacData(),
                        this,
                        dependency.audioManager(),
                        dependency.config(),
                        entry.source(),
                        dependency.sourceDescriptorTrust());
                sequencer.setRegion(region);
                sequencer.restoreSnapshot(entry.snapshot());
                sequencer.setIsSfx(entry.sfx());
                sequencers.add(sequencer);
                if (entry.sfx()) {
                    sfxSequencers.add(sequencer);
                }
            }

            for (int i = 0; i < entries.size(); i++) {
                SmpsSourceDescriptor fallbackVoiceSource = entries.get(i).fallbackVoiceSource();
                if (fallbackVoiceSource != null) {
                    AbstractSmpsData fallbackData = findRestoredDataBySource(
                            entries,
                            resolved,
                            fallbackVoiceSource);
                    if (fallbackData != null) {
                        sequencers.get(i).setFallbackVoiceData(fallbackData);
                    }
                }
            }

            restoreLocks(snapshot.fmLockSequencerIds(), fmLocks);
            restoreLocks(snapshot.psgLockSequencerIds(), psgLocks);
        }
        if (snapshot.synthSnapshot() != null) {
            restoreSynthSnapshot(snapshot.synthSnapshot());
        } else {
            silenceAll();
        }
    }

    private static List<ResolvedSequencerDependencies> resolveSequencerDependencies(
            List<SmpsDriverSnapshot.SequencerEntry> entries,
            SmpsDriverSnapshot.DependencyResolver resolver) {
        List<ResolvedSequencerDependencies> resolved = new ArrayList<>(entries.size());
        for (SmpsDriverSnapshot.SequencerEntry entry : entries) {
            AbstractSmpsData smpsData = Objects.requireNonNull(
                    resolver.resolveSmpsData(entry), "resolved SMPS data");
            boolean trustedIdentity = entry.sourceDescriptorTrust()
                    == SmpsSequencer.SourceDescriptorTrust.PRECOMPUTED_IMMUTABLE
                    && smpsData == entry.smpsData();
            SmpsSequencer.SourceDescriptorTrust effectiveTrust = trustedIdentity
                    ? SmpsSequencer.SourceDescriptorTrust.PRECOMPUTED_IMMUTABLE
                    : SmpsSequencer.SourceDescriptorTrust.LEGACY_RECOMPUTE;
            SmpsSourceDescriptor resolvedSource = trustedIdentity
                    ? entry.source() : SmpsSourceDescriptor.from(smpsData);
            if (!entry.source().matches(resolvedSource)) {
                throw new IllegalStateException(
                        "resolved SMPS source does not match snapshot source: expected "
                                + entry.source() + ", got " + resolvedSource);
            }
            resolved.add(new ResolvedSequencerDependencies(
                    smpsData,
                    Objects.requireNonNull(resolver.resolveDacData(entry), "resolved DAC data"),
                    Objects.requireNonNull(resolver.resolveAudioManager(entry), "resolved audio manager"),
                    Objects.requireNonNull(resolver.resolveConfig(entry), "resolved config"),
                    effectiveTrust));
        }
        return resolved;
    }

    private static AbstractSmpsData findRestoredDataBySource(
            List<SmpsDriverSnapshot.SequencerEntry> entries,
            List<ResolvedSequencerDependencies> restoredData,
            SmpsSourceDescriptor source) {
        for (int i = 0; i < entries.size(); i++) {
            SmpsDriverSnapshot.SequencerEntry entry = entries.get(i);
            if (entry.source().equals(source)) {
                return restoredData.get(i).smpsData();
            }
        }
        return null;
    }

    private record ResolvedSequencerDependencies(
            AbstractSmpsData smpsData,
            DacData dacData,
            MusicRestoreSink audioManager,
            SmpsSequencerConfig config,
            SmpsSequencer.SourceDescriptorTrust sourceDescriptorTrust) {
    }

    private static int[] captureLockIds(
            SmpsSequencer[] locks,
            IdentityHashMap<SmpsSequencer, Integer> sequencerIds) {
        int[] ids = new int[locks.length];
        Arrays.fill(ids, -1);
        for (int i = 0; i < locks.length; i++) {
            Integer id = sequencerIds.get(locks[i]);
            if (id != null) {
                ids[i] = id;
            }
        }
        return ids;
    }

    private void restoreLocks(int[] ids, SmpsSequencer[] target) {
        for (int i = 0; i < target.length && i < ids.length; i++) {
            int id = ids[i];
            if (id >= 0 && id < sequencers.size()) {
                target[i] = sequencers.get(id);
            }
        }
    }

    public void addSequencer(SmpsSequencer seq, boolean isSfx) {
        if (isSfx) {
            PreparedSfxAdmission admission =
                    prepareNewSfxAdmission(seq, 0, 0);
            seq.beginSfxAdmission();
            commitSfxAdmission(admission);
            return;
        }
        seq.setRegion(region);
        seq.setIsSfx(false); // Cache isSfx flag on the sequencer for O(1) lookup
        synchronized (sequencersLock) {
            sequencers.add(seq);
        }
    }

    /**
     * Restores the music (non-SFX) sequencer's DAC data on the shared synthesizer.
     * Called after committing an SFX sequencer whose initialization selected its bank.
     */
    private void restoreMusicDacData() {
        for (int i = 0; i < sequencers.size(); i++) {
            SmpsSequencer s = sequencers.get(i);
            if (!isSfx(s) && s.getDacData() != null) {
                setDacData(s.getDacData());
                return;
            }
        }
    }

    public void stopAll() {
        synchronized (sequencersLock) {
            sequencers.clear();
            sfxSequencers.clear();
            for (int i = 0; i < 6; i++)
                fmLocks[i] = null;
            for (int i = 0; i < 4; i++)
                psgLocks[i] = null;
            psgLatches.clear();
            continuousSfxId = 0;
            continuousSfxFlag = false;
            contSfxLoopCnt = 0;
        }
        // Silence hardware (ROM: zFMSilenceAll + zPSGSilenceAll)
        silenceAll();
    }

    /**
     * Stop all SFX sequencers, releasing their channel locks and silencing them.
     * Used when starting override music to prevent partial SFX playback on restore.
     */
    public void stopAllSfx() {
        synchronized (sequencersLock) {
            sfxRemovalBuffer.clear();
            sfxRemovalBuffer.addAll(sfxSequencers);
            for (int i = 0; i < sfxRemovalBuffer.size(); i++) {
                SmpsSequencer sfx = sfxRemovalBuffer.get(i);
                sequencers.remove(sfx);
                releaseLocks(sfx);
                sfxSequencers.remove(sfx);
            }
            continuousSfxId = 0;
            continuousSfxFlag = false;
            contSfxLoopCnt = 0;
        }
    }

    @Override
    public int read(short[] buffer) {
        return read(buffer, buffer.length);
    }

    @Override
    public int read(short[] buffer, int length) {
        if (readMode == ReadMode.HYBRID) {
            return readHybrid(buffer, length);
        }
        return readSampleAccurate(buffer, length);
    }

    private int readSampleAccurate(short[] buffer, int length) {
        int frames = length / 2;

        // Per-sample processing is required because sequencer state changes (note events,
        // instrument changes, etc.) must happen in lockstep with rendering. Batching
        // breaks audio fidelity because synth state changes mid-batch would be lost.
        synchronized (sequencersLock) {
            for (int i = 0; i < frames; i++) {
                int size = sequencers.size();
                for (int j = 0; j < size; j++) {
                    SmpsSequencer seq = sequencers.get(j);
                    seq.advance(1.0);
                    if (seq.isComplete()) {
                        pendingRemovals.add(seq);
                    }
                }

                removeCompletedSequencers();

                super.render(scratchFrameBuf);
                buffer[i * 2] = scratchFrameBuf[0];
                buffer[i * 2 + 1] = scratchFrameBuf[1];
            }
        }
        return length;
    }

    private int readHybrid(short[] buffer, int length) {
        int frames = length / 2;
        hybridChunkCountForTesting = 0;

        synchronized (sequencersLock) {
            int frameIndex = 0;
            while (frameIndex < frames) {
                if (requiresSampleAccurateFallback()) {
                    renderSingleSample(buffer, frameIndex++);
                    continue;
                }

                int safeChunk = computeSafeChunkSamples(frames - frameIndex);
                if (safeChunk < MIN_BATCH_SAMPLES) {
                    renderSingleSample(buffer, frameIndex++);
                    continue;
                }

                advanceSequencersBatch(safeChunk);
                removeCompletedSequencers();
                renderChunk(buffer, frameIndex, safeChunk);
                hybridChunkCountForTesting++;
                frameIndex += safeChunk;
            }
        }
        return length;
    }

    private boolean requiresSampleAccurateFallback() {
        for (int i = 0; i < sequencers.size(); i++) {
            if (sequencers.get(i).requiresSampleAccurateFallback()) {
                return true;
            }
        }
        return false;
    }

    // The tempo cap is nextTempoFrame - 1 because sample-accurate mode advances one
    // sample before rendering it; the boundary sample must stay on that path so the
    // pre-boundary samples do not render with post-tick state.
    private int computeSafeChunkSamples(int maxFrames) {
        int safe = maxFrames;
        for (int i = 0; i < sequencers.size(); i++) {
            SmpsSequencer seq = sequencers.get(i);
            int preTempoSafe = Math.max(0, seq.getSamplesUntilNextTempoFrame() - 1);
            safe = Math.min(safe, preTempoSafe);
            int preEventSafe = Math.max(0, seq.getSamplesUntilNextObservableEvent() - 1);
            safe = Math.min(safe, preEventSafe);
        }
        return safe;
    }

    private void advanceSequencersBatch(int frames) {
        int size = sequencers.size();
        for (int i = 0; i < size; i++) {
            SmpsSequencer seq = sequencers.get(i);
            seq.advanceBatch(frames);
            if (seq.isComplete()) {
                pendingRemovals.add(seq);
            }
        }
    }

    private void renderSingleSample(short[] buffer, int frameIndex) {
        advanceSequencersBatch(1);
        removeCompletedSequencers();

        super.render(scratchFrameBuf);
        buffer[frameIndex * 2] = scratchFrameBuf[0];
        buffer[frameIndex * 2 + 1] = scratchFrameBuf[1];
    }

    private void renderChunk(short[] target, int frameOffset, int frames) {
        super.renderFrames(target, frameOffset, frames);
    }

    private void removeCompletedSequencers() {
        if (!pendingRemovals.isEmpty()) {
            for (int j = 0; j < pendingRemovals.size(); j++) {
                SmpsSequencer seq = pendingRemovals.get(j);
                sequencers.remove(seq);
                releaseLocks(seq);
                sfxSequencers.remove(seq);
            }
            pendingRemovals.clear();
        }
    }

    @Override
    public boolean isComplete() {
        return sequencers.isEmpty();
    }

    /**
     * Check if a source is an SFX sequencer.
     * Uses cached isSfx field on SmpsSequencer for O(1) lookup instead of HashSet.contains().
     */
    private boolean isSfx(Object source) {
        if (source instanceof SmpsSequencer seq) {
            return seq.isSfx();
        }
        // Fallback to HashSet for non-SmpsSequencer sources (shouldn't happen normally)
        return sfxSequencers.contains(source);
    }

    private void releaseLocks(SmpsSequencer seq) {
        boolean isSfx = isSfx(seq);
        for (int i = 0; i < 6; i++) {
            if (fmLocks[i] == seq) {
                // If this was an SFX, ensure the channel is silenced before handing it back.
                if (isSfx) {
                    seq.forceSilence(SmpsSequencer.TrackType.FM, i);
                }
                fmLocks[i] = null;
                updateOverrides(SmpsSequencer.TrackType.FM, i, false);
            }
        }
        for (int i = 0; i < 4; i++) {
            if (psgLocks[i] == seq) {
                if (isSfx) {
                    seq.forceSilence(SmpsSequencer.TrackType.PSG, i);
                }
                psgLocks[i] = null;
                updateOverrides(SmpsSequencer.TrackType.PSG, i, false);
            }
        }
        // Clear cached PSG latch channel and remove from fallback HashMap
        seq.setPsgLatchChannel(-1);
        psgLatches.remove(seq);
    }

    private void updateOverrides(SmpsSequencer.TrackType type, int ch, boolean overridden) {
        synchronized (sequencersLock) {
            for (SmpsSequencer s : sequencers) {
                if (!isSfx(s)) {
                    s.setChannelOverridden(type, ch, overridden);
                }
            }
        }
    }

    @Override
    public void writeFm(Object source, int port, int reg, int val) {
        int ch = -1;
        int rawReg = reg & 0xFF;

        // Map Register to Channel
        if (rawReg >= 0x30 && rawReg <= 0x9E) {
            ch = (rawReg & 0x03) + (port * 3);
        } else if (rawReg >= 0xA0 && rawReg <= 0xA2) {
            ch = (rawReg - 0xA0) + (port * 3);
        } else if (rawReg >= 0xA4 && rawReg <= 0xA6) {
            ch = (rawReg - 0xA4) + (port * 3);
        } else if (rawReg >= 0xB0 && rawReg <= 0xB2) {
            ch = (rawReg - 0xB0) + (port * 3);
        } else if (rawReg >= 0xB4 && rawReg <= 0xB6) {
            ch = (rawReg - 0xB4) + (port * 3);
        } else if (rawReg == 0x28) {
            // Key On/Off: 0x28 is Port 0 only.
            // Val: d7-d4 (slot mask), d2-d0 (channel). d2 (bit 4 of ch?) No.
            // Channel is 0-2 (0,1,2) or 4-6 (4,5,6).
            // Ym2612Chip: "if (chIdx >= 4) chIdx -= 1;" -> Maps 4,5,6 to 3,4,5.
            // So Ch 0,1,2 -> 0,1,2. Ch 4,5,6 -> 3,4,5.
            // We need linear channel 0-5.
            int c = val & 0x07;
            if (c >= 4)
                c -= 1;
            ch = c;
        }

        if (ch >= 0 && ch < 6) {
            if (isSfx(source)) {
                if (shouldStealLock(fmLocks[ch], (SmpsSequencer) source)) {
                    // Silence channel if stealing from music (not from another SFX or self)
                    if (fmLocks[ch] != source && !isSfx(fmLocks[ch])) {
                        silenceFmChannel(ch);
                    }
                    fmLocks[ch] = (SmpsSequencer) source;
                    updateOverrides(SmpsSequencer.TrackType.FM, ch, true);
                }

                if (fmLocks[ch] == source) {
                    super.writeFm(source, port, reg, val);
                }
            } else {
                if (fmLocks[ch] == null) {
                    super.writeFm(source, port, reg, val);
                }
            }
        } else {
            // Global or unmapped
            super.writeFm(source, port, reg, val);
        }
    }

    @Override
    public void writePsg(Object source, int val) {
        // Use cached psgLatchChannel on SmpsSequencer for O(1) lookup instead of HashMap
        SmpsSequencer seq = (source instanceof SmpsSequencer) ? (SmpsSequencer) source : null;

        if ((val & 0x80) != 0) {
            // Latch
            int ch = (val >> 5) & 0x03;

            // Cache latch channel on sequencer (fast path) and in HashMap (fallback)
            if (seq != null) {
                seq.setPsgLatchChannel(ch);
            } else {
                psgLatches.put(source, ch);
            }

            if (isSfx(source)) {
                if (shouldStealLock(psgLocks[ch], (SmpsSequencer) source)) {
                    // Silence channel if stealing from music (not from another SFX or self)
                    if (psgLocks[ch] != source && !isSfx(psgLocks[ch])) {
                        silencePsgChannel(ch);
                    }
                    psgLocks[ch] = (SmpsSequencer) source;
                    updateOverrides(SmpsSequencer.TrackType.PSG, ch, true);
                }

                if (psgLocks[ch] == source) {
                    super.writePsg(source, val);
                }
            } else {
                if (psgLocks[ch] == null) {
                    super.writePsg(source, val);
                }
            }
        } else {
            // Data - get cached latch channel
            int ch = (seq != null) ? seq.getPsgLatchChannel() : -1;
            if (ch < 0) {
                // Fallback to HashMap for non-SmpsSequencer sources
                Integer chObj = psgLatches.get(source);
                ch = (chObj != null) ? chObj : -1;
            }

            if (ch >= 0) {
                if (isSfx(source)) {
                    // Update lock just in case? Already locked by Latch.
                    if (shouldStealLock(psgLocks[ch], (SmpsSequencer) source)) {
                        // Silence channel if stealing from music (not from another SFX or self)
                        if (psgLocks[ch] != source && !isSfx(psgLocks[ch])) {
                            silencePsgChannel(ch);
                        }
                        psgLocks[ch] = (SmpsSequencer) source;
                        updateOverrides(SmpsSequencer.TrackType.PSG, ch, true);
                    }

                    if (psgLocks[ch] == (SmpsSequencer) source) {
                        super.writePsg(source, val);
                    }
                } else {
                    if (psgLocks[ch] == null) {
                        super.writePsg(source, val);
                    }
                }
            } else {
                // Unknown channel (no previous latch from this source), drop or pass?
                // Pass for safety/compatibility
                super.writePsg(source, val);
            }
        }
    }

    // Override other methods if needed (setInstrument calls writeFm, so it's
    // covered)
    @Override
    public void setInstrument(Object source, int channelId, byte[] voice) {
        // Channel ID is passed explicitly.
        if (channelId >= 0 && channelId < 6) {
            if (isSfx(source)) {
                if (shouldStealLock(fmLocks[channelId], (SmpsSequencer) source)) {
                    // Silence channel if stealing from music (not from another SFX or self)
                    if (fmLocks[channelId] != source && !isSfx(fmLocks[channelId])) {
                        silenceFmChannel(channelId);
                    }
                    fmLocks[channelId] = (SmpsSequencer) source;
                    updateOverrides(SmpsSequencer.TrackType.FM, channelId, true);
                }

                if (fmLocks[channelId] == source) {
                    super.setInstrument(source, channelId, voice);
                }
            } else {
                if (fmLocks[channelId] == null) {
                    super.setInstrument(source, channelId, voice);
                }
            }
        }
    }

    @Override
    public void playDac(Object source, int note) {
        // DAC is on Channel 5 (FM6)
        int ch = 5;
        if (isSfx(source)) {
            if (shouldStealLock(fmLocks[ch], (SmpsSequencer) source)) {
                // Silence channel if stealing from music (not from another SFX or self)
                if (fmLocks[ch] != source && !isSfx(fmLocks[ch])) {
                    silenceFmChannel(5);
                    super.stopDac(null);
                }
                fmLocks[ch] = (SmpsSequencer) source;
                updateOverrides(SmpsSequencer.TrackType.FM, ch, true);
            }

            if (fmLocks[ch] == source) {
                super.playDac(source, note);
            }
        } else {
            if (fmLocks[ch] == null) {
                super.playDac(source, note);
            }
        }
    }

    private boolean shouldStealLock(SmpsSequencer currentLock, SmpsSequencer challenger) {
        if (currentLock == null)
            return true;
        if (currentLock == challenger)
            return true;
        if (!isSfx(currentLock))
            return true; // Challenger is SFX, current is Music -> Steal

        // Both are SFX.
        // Sonic 1 has a dedicated "special SFX" class (e.g. GHZ waterfall) that can be
        // overridden by normal SFX on shared channels, but not vice versa.
        boolean currentSpecial = currentLock.isSpecialSfx();
        boolean challengerSpecial = challenger.isSpecialSfx();
        if (currentSpecial && !challengerSpecial) {
            return true;
        }
        if (!currentSpecial && challengerSpecial) {
            return false;
        }

        // Priority arbitration:
        // Higher priority steals. If current priority has bit 7 set, treat it as
        // non-storing/transient (ROM-style), so any subsequent SFX can steal.
        int currentPriority = currentLock.getSfxPriority();
        int challengerPriority = challenger.getSfxPriority();
        if ((currentPriority & 0x80) != 0) {
            return true;
        }

        if (challengerPriority > currentPriority) {
            return true; // Higher priority always steals
        } else if (challengerPriority == currentPriority) {
            // Equal priority: newer SFX wins (prevents old SFX from stealing back)
            int currentIdx = sequencers.indexOf(currentLock);
            int challengerIdx = sequencers.indexOf(challenger);
            return challengerIdx > currentIdx;
        }
        return false; // Lower priority cannot steal
    }

    /**
     * Silence an FM channel before SFX takes it over from music.
     * This directly resets envelope state to prevent the "chirp" artifact
     * that occurs when SFX first samples inherit envelope state from the
     * previous music note.
     *
     * Unlike register writes (which would be overwritten by the subsequent
     * voice load), this directly resets the envelope counters to fully
     * silent state, ensuring the next Key On starts from a clean slate.
     */
    private void silenceFmChannel(int ch) {
        // Directly reset envelope state - this takes effect immediately
        // without needing audio samples to be rendered
        super.forceSilenceChannel(ch);

        // Also send Key Off via registers for completeness
        int port = (ch < 3) ? 0 : 1;
        int hwCh = ch % 3;
        int chVal = (port == 0) ? hwCh : (hwCh + 4);
        super.writeFm(null, 0, 0x28, 0x00 | chVal);
    }

    /**
     * Write directly to PSG hardware, bypassing SFX lock checks.
     * Used for unconditional channel silencing during SFX load (ROM: zPlaySound).
     * Protected to allow test spy access.
     */
    protected void writeRawPsg(int val) {
        super.writePsg(null, val);
    }

    /**
     * Silence a PSG channel before SFX takes it over from music.
     * Sets volume to 0xF (silence).
     */
    private void silencePsgChannel(int ch) {
        if (ch >= 0 && ch <= 3) {
            super.writePsg(null, 0x80 | (ch << 5) | (1 << 4) | 0x0F);
        }
    }

    @Override
    public void stopDac(Object source) {
        int ch = 5;
        if (isSfx(source)) {
            // Don't release lock here, just stop sound.
            // Lock is released when track ends or channel unused?
            // Actually, stopDac is just stopping sound.
            super.stopDac(source);
        } else {
            if (fmLocks[ch] == null) {
                super.stopDac(source);
            }
        }
    }
}
