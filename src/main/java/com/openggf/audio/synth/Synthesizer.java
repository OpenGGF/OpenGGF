package com.openggf.audio.synth;

import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.YmServiceTimingProfile;

public interface Synthesizer {
    interface YmTimingScope extends AutoCloseable {
        void consumeSuppressedHardwareAttempt();

        @Override
        void close();

        static YmTimingScope immediate() {
            return ImmediateScope.INSTANCE;
        }
    }

    enum ImmediateScope implements YmTimingScope {
        INSTANCE;

        @Override
        public void consumeSuppressedHardwareAttempt() {
        }

        @Override
        public void close() {
        }
    }

    default YmTimingScope beginYmTiming(
            Object source,
            YmServiceTimingProfile.SegmentKind kind,
            YmServiceTimingProfile.Variant variant) {
        return YmTimingScope.immediate();
    }

    void writeFm(Object source, int port, int reg, int val);
    void writePsg(Object source, int val);
    void setInstrument(Object source, int channelId, byte[] voice);
    void playDac(Object source, int note);
    void stopDac(Object source);
    void setDacData(DacData data);
    void setFmMute(int channel, boolean mute);
    void setPsgMute(int channel, boolean mute);
    void setDacInterpolate(boolean interpolate);
    void silenceAll();
}
