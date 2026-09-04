package com.openggf.audio;

import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.rewind.SmpsDriverSnapshot;
import com.openggf.audio.rewind.SmpsTrackSnapshot;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.synth.ChipWriteObserver;
import com.openggf.data.Rom;
import com.openggf.game.sonic3k.audio.Sonic3kAudioProfile;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The extra-life jingle's restore, driven through the live presentation path.
 *
 * <p>{@code cfFadeInToPrevious} stores {@code zFadeToPrevFlag} and the driver's
 * main loop acts on it (skdisasm Sound/Z80 Sound Driver.asm:3079-3082, with the
 * flag read at :659-666), so the whole restore belongs inside the driver's own
 * service. The engine splits it: the sequencer's coordination flag asks for the
 * restore, and the presentation layer brings the backed-up song back.
 *
 * <p>That ask has to go through the sequencer's injected restore sink. A
 * coordination flag runs inside the service, which for this path runs inside an
 * active presentation command batch, and the batch refuses a command submitted
 * into it. The refusal is logged rather than raised, so a handler that reaches
 * the global {@code AudioManager} instead loses the restore silently and the
 * music after the jingle is wrong. Sonic 1 and 2 reach it through the injected
 * sink already, in {@code SmpsSequencer.handleFadeIn}.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kOneUpRestoreRom {
    private Rom rom;

    @AfterEach
    void tearDown() {
        AudioManager.getInstance().resetState();
        AudioManager.getInstance().setBackend(new NullAudioBackend());
        if (rom != null) {
            rom.close();
        }
    }

    @Test
    void theExtraLifeJingleRestoresTheLevelMusicWithoutARejectedCommand() {
        AudioManager audio = install();
        List<LogRecord> warnings = captureAudioManagerWarnings();

        audio.playMusic(Sonic3kMusic.AIZ1.id);
        for (int frame = 0; frame < 4; frame++) {
            audio.presentFrame(PresentationMode.FORWARD);
        }
        assertEquals(Sonic3kMusic.AIZ1.id, playingMusicId(audio),
                "the level music must be playing before the jingle");

        audio.playMusic(Sonic3kMusic.EXTRA_LIFE.id);
        boolean restored = false;
        for (int frame = 0; frame < 900 && !restored; frame++) {
            audio.presentFrame(PresentationMode.FORWARD);
            restored = playingMusicId(audio) == Sonic3kMusic.AIZ1.id;
        }

        assertTrue(restored,
                "the level music must come back when the jingle ends");
        assertFalse(warnings.stream().anyMatch(record ->
                        String.valueOf(record.getMessage())
                                .contains("shadow command mirror failed")),
                "the restore must not be lost to a rejected batch command: "
                        + warnings.stream().map(LogRecord::getMessage).toList());
        assertEquals(1, musicSequencerCount(audio),
                "the jingle's own sequencer must be gone once it has restored");
    }

    /**
     * {@code zFadeInToPrevious} does not merely bring the song back. Per FM
     * track it clears the overriding bit, lowers the volume by 40h and resends
     * the instrument, and it then arms a fade in of 40h steps
     * (skdisasm Sound/Z80 Sound Driver.asm:2744-2789). The PSG tracks keep the
     * overriding bit, which is what mutes them through the fade. So the level
     * music must come back attenuated and climb, not appear at full volume.
     */
    @Test
    void theRestoredMusicComesBackAttenuatedAndFadesIn() {
        AudioManager audio = install();
        List<String> writes = new ArrayList<>();
        audio.setChipWriteObserver(new ChipWriteObserver() {
            @Override public void onYm2612Write(int port, int register, int value) {
                writes.add(String.format("ym%d[%02X]=%02X", port, register, value));
            }

            @Override public void onPsgWrite(int value) {
                writes.add(String.format("psg[%02X]", value));
            }
        });

        audio.playMusic(Sonic3kMusic.AIZ1.id);
        for (int frame = 0; frame < 8; frame++) {
            audio.presentFrame(PresentationMode.FORWARD);
        }
        int settledVolume = fmVolumeOffset(audio);

        audio.playMusic(Sonic3kMusic.EXTRA_LIFE.id);
        boolean restored = false;
        for (int frame = 0; frame < 900 && !restored; frame++) {
            writes.clear();
            audio.presentFrame(PresentationMode.FORWARD);
            restored = playingMusicId(audio) == Sonic3kMusic.AIZ1.id;
        }
        assertTrue(restored, "the level music must come back");

        int atRestore = fmVolumeOffset(audio);
        assertTrue(atRestore > settledVolume,
                "the restored song must come back attenuated, was " + atRestore
                        + " against a settled " + settledVolume);
        assertTrue(writes.stream().anyMatch(write -> write.startsWith("ym")),
                "the restore must resend the FM voices: " + writes);
        assertTrue(psgTrackOverridden(audio),
                "the PSG tracks stay overridden through the fade");

        int previous = atRestore;
        boolean climbed = false;
        for (int frame = 0; frame < 400 && !climbed; frame++) {
            audio.presentFrame(PresentationMode.FORWARD);
            climbed = fmVolumeOffset(audio) < previous;
        }
        assertTrue(climbed,
                "the fade in must step the attenuation back down from "
                        + previous);
    }

    private static int fmVolumeOffset(AudioManager audio) {
        for (SmpsDriverSnapshot.SequencerEntry entry
                : audio.shadowSmpsDriverSnapshotForTesting().sequencers()) {
            if (entry.sfx()) {
                continue;
            }
            for (SmpsTrackSnapshot track : entry.snapshot().tracks()) {
                if (track.type() == SmpsSequencer.TrackType.FM
                        && track.active()) {
                    return track.volumeOffset();
                }
            }
        }
        return -1;
    }

    private static boolean psgTrackOverridden(AudioManager audio) {
        for (SmpsDriverSnapshot.SequencerEntry entry
                : audio.shadowSmpsDriverSnapshotForTesting().sequencers()) {
            if (entry.sfx()) {
                continue;
            }
            for (SmpsTrackSnapshot track : entry.snapshot().tracks()) {
                if (track.type() == SmpsSequencer.TrackType.PSG) {
                    return track.overridden();
                }
            }
        }
        return false;
    }

    private static int musicSequencerCount(AudioManager audio) {
        return (int) audio.shadowSmpsDriverSnapshotForTesting().sequencers()
                .stream().filter(entry -> !entry.sfx()).count();
    }

    private static int playingMusicId(AudioManager audio) {
        for (SmpsDriverSnapshot.SequencerEntry entry
                : audio.shadowSmpsDriverSnapshotForTesting().sequencers()) {
            if (!entry.sfx()) {
                return entry.smpsData().getId();
            }
        }
        return -1;
    }

    private static List<LogRecord> captureAudioManagerWarnings() {
        List<LogRecord> records = new ArrayList<>();
        Logger logger = Logger.getLogger(AudioManager.class.getName());
        logger.addHandler(new Handler() {
            @Override public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                    records.add(record);
                }
            }

            @Override public void flush() { }

            @Override public void close() { }
        });
        return records;
    }

    private AudioManager install() {
        File file = RomTestUtils.ensureSonic3kRomAvailable();
        rom = new Rom();
        assertTrue(rom.open(file.getAbsolutePath()));
        Sonic3kAudioProfile profile = new Sonic3kAudioProfile();
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        audio.setRom(rom);
        audio.setAudioProfile(profile);
        audio.setSoundMap(profile.getSoundMap());
        return audio;
    }
}
