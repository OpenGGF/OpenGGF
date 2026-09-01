package com.openggf.game.sonic3k.audio;

import com.openggf.audio.AudioManager;
import com.openggf.audio.NullAudioBackend;
import com.openggf.audio.rewind.AudioCommand;
import com.openggf.audio.rewind.AudioCommandTimeline;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSonic3kSpeedShoesCommandSemantics {
    @AfterEach
    void tearDown() {
        AudioManager.getInstance().resetState();
        AudioManager.getInstance().setBackend(new NullAudioBackend());
    }

    @Test
    void shippedCommandIdsResolveFromEitherMailboxExactlyOnce() {
        AudioManager audio = AudioManager.getInstance();
        audio.setBackend(new NullAudioBackend());
        audio.setAudioProfile(new Sonic3kAudioProfile());
        AudioCommandTimeline timeline = audio.commandTimeline();

        int before = timeline.entryCount();
        audio.playMusic(Sonic3kSmpsConstants.CMD_STOP);
        assertEquals(before + 1, timeline.entryCount());
        AudioCommand.RetainGlobalStop e0 = assertInstanceOf(
                AudioCommand.RetainGlobalStop.class,
                timeline.entryAt(before).command());
        assertEquals(0xE0, e0.sourceCommandId());

        before = timeline.entryCount();
        assertTrue(audio.playSfx(Sonic3kSmpsConstants.CMD_STOP_ALL));
        assertEquals(before + 1, timeline.entryCount());
        AudioCommand.RetainGlobalStop e2 = assertInstanceOf(
                AudioCommand.RetainGlobalStop.class,
                timeline.entryAt(before).command());
        assertEquals(0xE2, e2.sourceCommandId());

        before = timeline.entryCount();
        audio.playMusic(Sonic3kSmpsConstants.CMD_FADE_OUT);
        assertEquals(before + 1, timeline.entryCount());
        assertInstanceOf(AudioCommand.FadeOutMusic.class,
                timeline.entryAt(before).command());

        before = timeline.entryCount();
        assertTrue(audio.playSfx(Sonic3kSmpsConstants.CMD_FADE_OUT_ALT));
        assertEquals(before + 1, timeline.entryCount());
        assertInstanceOf(AudioCommand.FadeOutMusic.class,
                timeline.entryAt(before).command());

        before = timeline.entryCount();
        audio.playMusic(Sonic3kSmpsConstants.CMD_STOP_SFX);
        assertEquals(before + 1, timeline.entryCount());
        assertInstanceOf(AudioCommand.StopSmpsSfx.class,
                timeline.entryAt(before).command());
    }

    @Test
    void e3IsAnExplicitReferenceLimitationAndNeverSpeedControl() {
        AudioManager audio = AudioManager.getInstance();
        audio.setBackend(new NullAudioBackend());
        audio.setAudioProfile(new Sonic3kAudioProfile());
        int before = audio.commandTimeline().entryCount();

        assertTrue(audio.playSfx(Sonic3kSmpsConstants.CMD_PSG_SILENCE));

        assertEquals(before + 1, audio.commandTimeline().entryCount());
        AudioCommand.ReferenceLimitation limitation = assertInstanceOf(
                AudioCommand.ReferenceLimitation.class,
                audio.commandTimeline().entryAt(before).command());
        assertEquals(0xE3, limitation.sourceCommandId());
        assertEquals(1, audio.captureLogicalSnapshot().presentation()
                .speedMultiplier());
    }

    @Test
    void speedShoesUseSemanticEightAndOneWithoutSystemCommands() {
        AudioManager audio = AudioManager.getInstance();
        audio.setBackend(new NullAudioBackend());
        audio.setAudioProfile(new Sonic3kAudioProfile());

        audio.setSpeedMultiplier(
                Sonic3kSmpsConstants.SPEED_MULTIPLIER_ON);
        audio.setSpeedMultiplier(
                Sonic3kSmpsConstants.SPEED_MULTIPLIER_OFF);

        int count = audio.commandTimeline().entryCount();
        AudioCommand.SetSpeedMultiplier on = assertInstanceOf(
                AudioCommand.SetSpeedMultiplier.class,
                audio.commandTimeline().entryAt(count - 2).command());
        AudioCommand.SetSpeedMultiplier off = assertInstanceOf(
                AudioCommand.SetSpeedMultiplier.class,
                audio.commandTimeline().entryAt(count - 1).command());
        assertEquals(8, on.multiplier());
        assertEquals(1, off.multiplier());
    }
}
