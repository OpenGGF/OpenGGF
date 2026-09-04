package com.openggf.audio;

import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.rewind.SmpsDriverSnapshot;
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
