package com.openggf.audio;

import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.rewind.AudioCommand;
import com.openggf.audio.rewind.AudioLogicalSnapshot;
import com.openggf.game.sonic2.audio.Sonic2AudioProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSonic2RequestProductionWiring {

    @AfterEach
    void tearDown() {
        AudioManager.getInstance().resetState();
        AudioManager.getInstance().setBackend(new NullAudioBackend());
    }

    @Test
    void baseS2IngressDefersUntilForwardPresentationAndRingResolvesOnce() {
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        audio.setAudioProfile(new Sonic2AudioProfile());
        audio.setSoundMap(new Sonic2AudioProfile().getSoundMap());

        assertTrue(audio.playSfx(0xB5));
        assertEquals(0, audio.commandTimeline().entryCount(),
                "S2 ingress must write the source mailbox without immediate playback");

        audio.presentFrame(PresentationMode.SILENT);
        assertEquals(0, audio.commandTimeline().entryCount(),
                "silent presentation must not consume S2 mailbox work");

        audio.presentFrame(PresentationMode.FORWARD);
        List<AudioCommand> commands = audio.commandTimeline().entries().stream()
                .map(entry -> entry.command()).toList();
        assertEquals(1, commands.size());
        assertEquals(0xCE, ((AudioCommand.PlaySfx) commands.getFirst()).sfxId(),
                "raw B5 is resolved only by the S2 pipeline");

        audio.presentFrame(PresentationMode.FORWARD);
        assertEquals(1, audio.commandTimeline().entryCount(),
                "one outer boundary cannot service the same mailbox twice");
    }

    @Test
    void nonS2ProfileRetainsImmediateIngress() {
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        audio.setAudioProfile(new AudioTestFixtures.StubAudioProfile(
                new AudioTestFixtures.StubSmpsLoader()));

        audio.playMusic(0x82);

        assertEquals(1, audio.commandTimeline().entryCount());
    }

    @Test
    void logicalSnapshotRestoresPendingMailboxWork() {
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        audio.setAudioProfile(new Sonic2AudioProfile());
        audio.setSoundMap(new Sonic2AudioProfile().getSoundMap());

        assertTrue(audio.playSfx(0xB5));
        AudioLogicalSnapshot pending = audio.captureLogicalSnapshot();

        audio.presentFrame(PresentationMode.FORWARD);
        assertEquals(1, audio.commandTimeline().entryCount());

        audio.restoreLogicalSnapshot(pending);
        audio.presentFrame(PresentationMode.FORWARD);

        assertEquals(2, audio.commandTimeline().entryCount(),
                "restored request work must replay once after the historical command");
        assertEquals(0xCE, ((AudioCommand.PlaySfx) audio.commandTimeline()
                .entries().getLast().command()).sfxId());
        audio.presentFrame(PresentationMode.FORWARD);
        assertEquals(2, audio.commandTimeline().entryCount(),
                "restored request work must not replay twice");
    }

    @Test
    void reverseDoesNotConsumeAndSameFrameSoundWritesOverwrite() {
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        Sonic2AudioProfile profile = new Sonic2AudioProfile();
        audio.setAudioProfile(profile);
        audio.setSoundMap(profile.getSoundMap());

        assertTrue(audio.playSfx(0xA0));
        assertTrue(audio.playSfx(0xB5));
        audio.presentFrame(PresentationMode.REVERSE);
        assertEquals(0, audio.commandTimeline().entryCount(),
                "reverse presentation must not consume the mailbox");

        audio.presentFrame(PresentationMode.FORWARD);
        List<AudioCommand> commands = audio.commandTimeline().entries().stream()
                .map(entry -> entry.command()).toList();
        assertEquals(1, commands.size());
        assertEquals(0xCE, ((AudioCommand.PlaySfx) commands.getFirst()).sfxId(),
                "the later raw write must overwrite SFX0 before the bridge runs");
    }

    @Test
    void unmappedS2GameSoundRetainsImmediateFallbackRoute() {
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        Sonic2AudioProfile profile = new Sonic2AudioProfile();
        audio.setAudioProfile(profile);
        audio.setSoundMap(profile.getSoundMap());

        audio.playSfx(GameSound.FIRE_SHIELD);

        assertEquals(1, audio.commandTimeline().entryCount());
        AudioCommand.PlaySfx command = (AudioCommand.PlaySfx) audio
                .commandTimeline().entries().getFirst().command();
        assertEquals(AudioCommand.SfxRoute.FALLBACK_NAME, command.route());
    }

    @Test
    void standaloneNamedSfxRetainsImmediateRoute() {
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        Sonic2AudioProfile profile = new Sonic2AudioProfile();
        audio.setAudioProfile(profile);

        audio.playSfx("standalone-cue");

        assertEquals(1, audio.commandTimeline().entryCount());
        assertEquals(AudioCommand.SfxRoute.FALLBACK_NAME,
                ((AudioCommand.PlaySfx) audio.commandTimeline().entries()
                        .getFirst().command()).route());
    }
}
