package com.openggf.audio;

import com.openggf.audio.smps.SmpsSequencerConfig;
import java.util.Map;

/** Stateless no-ROM native audio defaults for standalone games. */
final class SilentNativeAudioProfile implements GameAudioProfile {
    @Override public SmpsSequencerConfig getSequencerConfig() { return null; }
    @Override public int getSpeedShoesOnCommandId() { return -1; }
    @Override public int getSpeedShoesOffCommandId() { return -1; }
    @Override public int getInvincibilityMusicId() { return -1; }
    @Override public int getExtraLifeMusicId() { return -1; }
    @Override public int getDrowningMusicId() { return -1; }
    @Override public Map<GameSound,Integer> getSoundMap() { return Map.of(); }
    @Override public Map<GameMusic,Integer> getMusicMap() { return Map.of(); }
}
