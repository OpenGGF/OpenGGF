package com.openggf.audio.smps;

import com.openggf.audio.AudioManager;
import com.openggf.audio.AudioTestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class TestSmpsMusicTempoPercent {
    @Test
    void rejectsUnsupportedRatesWithoutChangingState() {
        var music = sequencer(SmpsSequencerConfig.TempoMode.OVERFLOW, false);
        var before = music.captureSnapshot();
        for (int percent : new int[] {0, 24, 101, Integer.MAX_VALUE})
            assertThrows(IllegalArgumentException.class, () -> music.setMusicTempoPercent(percent));
        assertEquals(before.musicTempoPercent(), music.captureSnapshot().musicTempoPercent());
        assertEquals(before.musicTempoPhase(), music.captureSnapshot().musicTempoPhase());
    }

    @ParameterizedTest
    @CsvSource({"TIMEOUT,65", "OVERFLOW2,65", "OVERFLOW,65",
            "TIMEOUT,85", "OVERFLOW2,85", "OVERFLOW,85", "TIMEOUT,100", "OVERFLOW2,100", "OVERFLOW,100"})
    void slowsOnlyAdmittedMusicDurationTicks(SmpsSequencerConfig.TempoMode mode, int percent) {
        SmpsSequencer normal = sequencer(mode, false);
        SmpsSequencer gentle = sequencer(mode, false);
        setPercent(gentle, percent);
        int normalStart = normal.getTracks().getFirst().duration;
        int gentleStart = gentle.getTracks().getFirst().duration;
        for (int i = 0; i < 100; i++) {
            normal.serviceOuterFrame();
            gentle.serviceOuterFrame();
        }
        int normalTicks = normalStart - normal.getTracks().getFirst().duration;
        assertEquals(50, normalTicks, "fixture admits half of the ROM music ticks");
        assertEquals(normalTicks * percent / 100,
                gentleStart - gentle.getTracks().getFirst().duration);
        assertEquals(normal.getTracks().getFirst().baseFnum,
                gentle.getTracks().getFirst().baseFnum, "note frequency is unchanged");
        assertEquals(normal.captureSnapshot().pitch(), gentle.captureSnapshot().pitch());
    }

    @ParameterizedTest
    @EnumSource(SmpsSequencerConfig.TempoMode.class)
    void soundEffectsKeepTheirOriginalClock(SmpsSequencerConfig.TempoMode mode) {
        SmpsSequencer sfx = sequencer(mode, true);
        setPercent(sfx, 25);
        int start = sfx.getTracks().getFirst().duration;
        for (int i = 0; i < 100; i++) sfx.serviceOuterFrame();
        assertEquals(100, start - sfx.getTracks().getFirst().duration);
    }

    @Test
    void rewindRestoresFractionalPhaseAndRate() {
        SmpsSequencer music = sequencer(SmpsSequencerConfig.TempoMode.OVERFLOW, false);
        setPercent(music, 85);
        for (int i = 0; i < 7; i++) music.serviceOuterFrame();
        var snapshot = music.captureSnapshot();
        for (int i = 0; i < 33; i++) music.serviceOuterFrame();
        int expectedDuration = music.getTracks().getFirst().duration;
        setPercent(music, 100);
        music.restoreSnapshot(snapshot);
        for (int i = 0; i < 33; i++) music.serviceOuterFrame();
        assertEquals(expectedDuration, music.getTracks().getFirst().duration);
    }

    private static SmpsSequencer sequencer(SmpsSequencerConfig.TempoMode mode, boolean sfx) {
        SmpsSequencer result = new SmpsSequencer(new AudioTestFixtures.StubSmpsData("tempo"),
                AudioTestFixtures.EMPTY_DAC, AudioManager.getInstance(),
                new SmpsSequencerConfig.Builder().tempoMode(mode).tempoModBase(256).build());
        result.setNormalTempo(mode == SmpsSequencerConfig.TempoMode.TIMEOUT ? 2 : 128);
        result.recalculateTempo();
        result.setSfxMode(sfx);
        result.serviceOuterFrame(); // Prime the ROM's first music service before measuring.
        SmpsSequencer.Track track = new SmpsSequencer.Track(0, SmpsSequencer.TrackType.FM, 0);
        track.active = true;
        track.duration = 1000;
        track.baseFnum = 0x321;
        result.addTrack(track);
        return result;
    }

    private static void setPercent(SmpsSequencer sequencer, int percent) {
        sequencer.setMusicTempoPercent(percent);
    }
}
