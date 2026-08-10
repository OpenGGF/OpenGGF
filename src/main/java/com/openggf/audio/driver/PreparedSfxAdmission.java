package com.openggf.audio.driver;

import com.openggf.audio.smps.SmpsSequencer;

/**
 * A validated, channel-bounded SMPS SFX admission plan.
 *
 * <p>The referenced sequencers and tracks are live identities, but preparation
 * does not mutate them. Conflict storage is fixed by the Genesis hardware
 * channel counts rather than by the number of unrelated live sequencers.</p>
 */
public final class PreparedSfxAdmission {
    private final SmpsDriver owner;
    private final SmpsSequencer sequencer;
    private final boolean continuousExtension;
    private final int affectedFmMask;
    private final int affectedPsgMask;
    private final int continuousSfxId;
    private final int trackCount;
    final SmpsSequencer replacedSequencer;
    final SmpsSequencer[] displacedFmOwners;
    final SmpsSequencer.Track[] displacedFmTracks;
    final SmpsSequencer[] displacedPsgOwners;
    final SmpsSequencer.Track[] displacedPsgTracks;
    private boolean committed;

    PreparedSfxAdmission(
            SmpsDriver owner,
            SmpsSequencer sequencer,
            boolean continuousExtension,
            int affectedFmMask,
            int affectedPsgMask,
            int continuousSfxId,
            int trackCount,
            SmpsSequencer replacedSequencer,
            SmpsSequencer[] displacedFmOwners,
            SmpsSequencer.Track[] displacedFmTracks,
            SmpsSequencer[] displacedPsgOwners,
            SmpsSequencer.Track[] displacedPsgTracks) {
        this.owner = owner;
        this.sequencer = sequencer;
        this.continuousExtension = continuousExtension;
        this.affectedFmMask = affectedFmMask;
        this.affectedPsgMask = affectedPsgMask;
        this.continuousSfxId = continuousSfxId;
        this.trackCount = trackCount;
        this.replacedSequencer = replacedSequencer;
        this.displacedFmOwners = displacedFmOwners;
        this.displacedFmTracks = displacedFmTracks;
        this.displacedPsgOwners = displacedPsgOwners;
        this.displacedPsgTracks = displacedPsgTracks;
    }

    public SmpsDriver owner() {
        return owner;
    }

    public SmpsSequencer sequencer() {
        return sequencer;
    }

    public boolean continuousExtension() {
        return continuousExtension;
    }

    public int affectedFmMask() {
        return affectedFmMask;
    }

    public int affectedPsgMask() {
        return affectedPsgMask;
    }

    public int continuousSfxId() {
        return continuousSfxId;
    }

    public int trackCount() {
        return trackCount;
    }

    void claimCommit() {
        if (committed) {
            throw new IllegalStateException(
                    "prepared SFX admission was already committed");
        }
        committed = true;
    }
}
