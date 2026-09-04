package com.openggf.audio.smps;

import com.openggf.audio.driver.SmpsDriverServiceObserver;

/**
 * Logical driver callbacks used by an SMPS sequencer independently of its
 * physical write target.
 */
public interface SmpsSequencerHost {
    SmpsSequencerHost NONE = NoOp.INSTANCE;

    SmpsDriverServiceObserver.ServiceEvent beginSequencerService(
            SmpsSequencer sequencer,
            SmpsDriverServiceObserver.ServiceKind kind);

    void endSequencerService(SmpsDriverServiceObserver.ServiceEvent event);

    void reconcileInactiveSfxTracks(SmpsSequencer sequencer);

    /** See {@link CoordFlagContext#releaseChannelToMusic}. */
    default void releaseChannelToMusic(SmpsSequencer sequencer,
            SmpsSequencer.TrackType type, int channelId) {
    }

    /**
     * Releases the channels of any inactive SFX track, including those of a
     * sequencer whose every track has now finished.
     *
     * <p>Used by the driver-coordinated SFX slot walk: S1 {@code cfStopTrack}
     * hands a channel back to music from inside the finishing track's own slot
     * service (s1.sounddriver.asm:2489-2563), whether or not the sound has
     * other tracks still playing.
     */
    default void reconcileFinishedSfxSlot(SmpsSequencer sequencer) {
        reconcileInactiveSfxTracks(sequencer);
    }

    byte[] s1SpecialSfxVoiceForBug(int voiceId);

    boolean isContinuousSfxFlagSet();

    void clearContinuousSfxId();

    void clearContinuousSfxFlag();

    boolean decrementContSfxLoopCnt();

    final class NoOp implements SmpsSequencerHost {
        private static final NoOp INSTANCE = new NoOp();

        private NoOp() {
        }

        @Override
        public SmpsDriverServiceObserver.ServiceEvent beginSequencerService(
                SmpsSequencer sequencer,
                SmpsDriverServiceObserver.ServiceKind kind) {
            return null;
        }

        @Override
        public void endSequencerService(
                SmpsDriverServiceObserver.ServiceEvent event) {
        }

        @Override
        public void reconcileInactiveSfxTracks(SmpsSequencer sequencer) {
        }

        @Override
        public byte[] s1SpecialSfxVoiceForBug(int voiceId) {
            return null;
        }

        @Override
        public boolean isContinuousSfxFlagSet() {
            return false;
        }

        @Override
        public void clearContinuousSfxId() {
        }

        @Override
        public void clearContinuousSfxFlag() {
        }

        @Override
        public boolean decrementContSfxLoopCnt() {
            return true;
        }
    }
}
