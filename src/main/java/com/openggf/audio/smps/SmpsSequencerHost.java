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
