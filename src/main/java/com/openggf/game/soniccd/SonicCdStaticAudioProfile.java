package com.openggf.game.soniccd;

import com.openggf.audio.AudioManager;
import com.openggf.audio.GameAudioProfile;
import com.openggf.audio.GameMusic;
import com.openggf.audio.GameSound;
import com.openggf.audio.smps.SmpsLoader;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.data.Rom;

import java.util.Map;

/** Explicitly silent static entry. Neither cartridge SMPS nor the CD devices are installed here. */
final class SonicCdStaticAudioProfile implements GameAudioProfile {
    /** Retire prior base, donor, streamed and backend playback ownership before installing silence. */
    void retirePriorAudio(AudioManager audio) { audio.resetState(); }

    @Override public String presentationGameId() { return "scd"; }
    @Override public SmpsLoader createSmpsLoader(Rom rom) { return null; }
    @Override public SmpsSequencerConfig getSequencerConfig() { return null; }
    @Override public int getSpeedShoesOnCommandId() { return -1; }
    @Override public int getSpeedShoesOffCommandId() { return -1; }
    @Override public int getInvincibilityMusicId() { return -1; }
    @Override public int getExtraLifeMusicId() { return -1; }
    @Override public int getDrowningMusicId() { return -1; }
    @Override public Map<GameSound, Integer> getSoundMap() { return Map.of(); }
    @Override public Map<GameMusic, Integer> getMusicMap() { return Map.of(); }
    @Override public void fadeOutMusic(AudioManager audio) { audio.stopMusic(); }
}
