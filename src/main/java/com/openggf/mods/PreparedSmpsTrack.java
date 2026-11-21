package com.openggf.mods;

import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.DacData;

/** Native song and its immutable launch-scoped dependencies. */
public record PreparedSmpsTrack(TrackKey key, AbstractSmpsData data, DacData dac) {}
