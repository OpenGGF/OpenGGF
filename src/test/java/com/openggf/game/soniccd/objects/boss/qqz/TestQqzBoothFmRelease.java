package com.openggf.game.soniccd.objects.boss.qqz;

import com.openggf.audio.smps.SmpsCoordFlagHandlerOwner;
import com.openggf.audio.smps.SmpsCoordFlagRuntimeState;
import com.openggf.audio.smps.SmpsLogicalWriteTarget;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.game.GameModule;
import com.openggf.game.solid.ObjectSolidExecutionContext;
import com.openggf.game.soniccd.CdDiscDataSource;
import com.openggf.game.soniccd.CdTestMedia;
import com.openggf.game.soniccd.audio.CdFmSequencerConfig;
import com.openggf.game.soniccd.audio.SonicCdAudioProfile;
import com.openggf.level.objects.ObjectServices;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestQqzBoothFmRelease {
    @Test
    void boothDefeatReleasesTheHeldDiscEffectIntoItsFadeTail() throws Exception {
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            var profile = new SonicCdAudioProfile(() -> disc);
            var handlers = new SmpsCoordFlagHandlerOwner(new SmpsCoordFlagRuntimeState());
            profile.configurePresentationCoordFlagHandlers(handlers);
            var config = CdFmSequencerConfig.create(handlers.handlerFor(profile.presentationGameId()));
            var loader = profile.createRomlessSmpsLoader();
            var sequencer = new SmpsSequencer(loader.loadSfx(QqzBossBooth.FM_STOP), loader.loadDacData(),
                    mock(SmpsLogicalWriteTarget.class), () -> { }, config);
            var track = sequencer.getTracks().getFirst();

            for (int tick = 0; tick < 16; tick++) sequencer.serviceOuterFrame();
            assertTrue(track.active, "C4 holds while the booth is on the belt");
            int heldVolume = track.volumeOffset;

            var module = mock(GameModule.class);
            when(module.getAudioProfile()).thenReturn(profile);
            var services = mock(ObjectServices.class);
            when(services.gameModule()).thenReturn(module);
            when(services.solidExecution()).thenReturn(ObjectSolidExecutionContext.inert());
            var booth = new QqzBossBooth(QqzBossBooth.BOOTH_X, 0, null);
            booth.setServices(services);
            booth.setRoutine(0xA);
            booth.timer = 0x3B;
            booth.update(0, null);
            assertEquals(0xC, booth.routine());

            // SonicCD_C4_Jump1 (Sound Drivers/FM/SFX/C4.asm:12-22) falls into 34 volume steps and a stop.
            for (int tick = 0; tick < 40; tick++) sequencer.serviceOuterFrame();
            assertEquals(heldVolume + 0x22, track.volumeOffset, "C4 plays each fade step");
            assertFalse(track.active, "C4 stops after the fade");
        }
    }
}
