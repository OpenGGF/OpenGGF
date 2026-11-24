package com.openggf.audio;

import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.session.SmpsChipWrite;
import com.openggf.audio.synth.ChipWriteObserver;
import com.openggf.game.sonic1.audio.Sonic1AudioProfile;
import com.openggf.game.sonic1.audio.Sonic1SmpsCompatibilityPolicy;
import com.openggf.game.sonic1.audio.Sonic1Music;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sonic 1's extra-life restore on the live path; see {@link OneUpRestoreLivePathSupport}. */
@RequiresRom(SonicGame.SONIC_1)
class TestS1OneUpRestoreRom extends OneUpRestoreLivePathSupport {
    @Override
    protected File romFile() {
        return RomTestUtils.ensureSonic1RomAvailable();
    }

    @Override
    protected GameAudioProfile profile() {
        return new Sonic1AudioProfile();
    }

    @Override
    protected int levelMusicId() {
        return Sonic1Music.GHZ.id;
    }

    @Override
    protected int extraLifeMusicId() {
        return Sonic1Music.EXTRA_LIFE.id;
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void fadeTerminalServiceRunsStopAllSoundBeforeTheTrackWalk(boolean withJingle) {
        AudioManager audio = install();
        audio.playMusic(levelMusicId());
        audio.presentFrame(PresentationMode.FORWARD);
        if (withJingle) {
            audio.playMusic(extraLifeMusicId());
            audio.presentFrame(PresentationMode.FORWARD);
        }
        assertEquals(withJingle, sfxBlocked(audio));
        List<SmpsChipWrite> writes = observeWrites(audio);
        audio.fadeOutMusic(2, 1);
        audio.presentFrame(PresentationMode.FORWARD);
        // FadeOutMusic loads the delay after this service's DoFadeOut.
        // DoFadeOut counts down on service 1, attenuates on service 2,
        // counts down on service 3 and reaches StopAllSound on service 4.
        for (int service = 1; service <= 3; service++) {
            writes.clear();
            audio.presentFrame(PresentationMode.FORWARD);
            assertFalse(writes.contains(new SmpsChipWrite.Ym2612(0, 0x27, 0)),
                    "no mode reset before the terminal service");
            assertEquals(1, musicSequencerCount(audio));
            assertEquals(withJingle, sfxBlocked(audio));
        }
        var beforeTerminal = audio.captureLogicalSnapshot();
        assertTerminalStop(audio, writes);
        audio.restoreLogicalSnapshot(beforeTerminal);
        assertTerminalStop(audio, writes);
    }

    @Test
    void explicitGlobalStopUsesTheSameBurstAndReleasesTheJingleBlock() {
        AudioManager audio = install();
        audio.playMusic(extraLifeMusicId());
        audio.presentFrame(PresentationMode.FORWARD);
        assertTrue(sfxBlocked(audio));
        List<SmpsChipWrite> writes = observeWrites(audio);
        audio.retainGlobalStop(0xE4);
        audio.presentFrame(PresentationMode.FORWARD);
        assertStopped(audio, writes);
    }

    private static void assertTerminalStop(AudioManager audio, List<SmpsChipWrite> writes) {
        writes.clear();
        // StopAllSound (s1.sounddriver.asm:1461-1482) clears the retail
        // queue before the same driver's request phase.
        audio.playSfx(GameSound.RING);
        audio.presentFrame(PresentationMode.FORWARD);
        assertStopped(audio, writes);
    }

    private static void assertStopped(AudioManager audio, List<SmpsChipWrite> writes) {
        assertEquals(Sonic1SmpsCompatibilityPolicy.INSTANCE.stopAll().writes(), writes,
                "StopAllSound writes DAC enable, FM mode reset, then FM and PSG silence");
        assertEquals(0, musicSequencerCount(audio));
        assertEquals(0, sfxSequencerCount(audio));
        assertFalse(sfxBlocked(audio));

        audio.playSfx(GameSound.RING);
        audio.presentFrame(PresentationMode.FORWARD);
        assertEquals(1, sfxSequencerCount(audio), "a new request after the global stop plays");
    }

    private static List<SmpsChipWrite> observeWrites(AudioManager audio) {
        List<SmpsChipWrite> writes = new ArrayList<>();
        audio.setChipWriteObserver(new ChipWriteObserver() {
            @Override
            public void onYm2612Write(int port, int register, int value) {
                // The separate PCM clock can still emit DAC data after the stop.
                if (port != 0 || register != 0x2A) {
                    writes.add(new SmpsChipWrite.Ym2612(port, register, value));
                }
            }

            @Override
            public void onPsgWrite(int value) {
                writes.add(new SmpsChipWrite.Psg(value));
            }
        });
        return writes;
    }
}
