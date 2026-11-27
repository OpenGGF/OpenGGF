package com.openggf.audio;

import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.DacData;

/** Native counterpart to the streamed side of the launch-owned music resolver. */
public interface NativeMusicOverridePort extends StreamedMusicPort {
    record Song(TrackRef track, AbstractSmpsData data, DacData dac) {}

    Song nativeMusicOverride(int musicId);

    static Song find(StreamedMusicPort port, int musicId) {
        return port instanceof NativeMusicOverridePort nativePort
                ? nativePort.nativeMusicOverride(musicId) : null;
    }
}
