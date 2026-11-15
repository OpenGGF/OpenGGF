package com.openggf.game.sonic2;

import com.openggf.audio.rewind.AudioCommand;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.constants.Sonic2AudioConstants;
import com.openggf.sprites.playable.SuperState;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Sonic 2's Super Sonic donated into Sonic 1 keeps its transformation sound.
 *
 * <p>Sonic_CheckGoSuper requests SndID_SuperTransform and then the Super music in one call. The
 * Sonic 2 driver loads the song first and admits the sound on its next pass (sndDriverInput,
 * s2.asm:1270-1332; zCycleQueue, s2.sounddriver.asm:1496-1550), so the sound is heard over the
 * song. A donor has no mailbox on the host, and its music load stops every running SFX: the
 * donated requests must reach the presentation song first, or the sound is cut on the frame it
 * starts.
 */
@RequiresRom(SonicGame.SONIC_1)
class TestSonic2DonatedSuperTransformSound {
    @AfterEach
    void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
    }

    @Test
    void donatedTransformationSoundIsRequestedAfterTheSongThatWouldStopIt() {
        var donorRom = RomTestUtils.ensureSonic2RomAvailable();
        assumeTrue(donorRom != null, "Sonic 2 donor ROM is not available in this environment");
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.SONIC_2_ROM, donorRom.getAbsolutePath());
        var fixture = HeadlessTestFixture.builder().withCrossGameDonation("s2").withZoneAndAct(1, 0)
                .withFreshLevelStartLifecycle().build();
        var player = fixture.sprite();
        var controller = assertInstanceOf(Sonic2SuperStateController.class, player.getSuperStateController());
        GameServices.gameState().configureSpecialStageProgress(6, 6);
        for (int emerald = 0; emerald < 6; emerald++) GameServices.gameState().markEmeraldCollected(emerald);
        player.setRingCount(60);
        player.setAir(true);
        player.setJumping(true);
        player.setYSpeed((short) 0x28);

        var audio = GameServices.audio();
        int before = audio.commandTimeline().entries().size();
        controller.checkTransformationBeforeMove();
        assertEquals(SuperState.TRANSFORMING, controller.getState(), "the donated jump gate starts the transformation");

        List<AudioCommand> started = audio.commandTimeline().entries().stream().skip(before)
                .map(entry -> entry.command()).toList();
        int song = -1;
        int sound = -1;
        for (int i = 0; i < started.size(); i++) {
            if (started.get(i) instanceof AudioCommand.PlayMusic music
                    && music.route() == AudioCommand.MusicRoute.DONOR_SMPS && "s2".equals(music.donorGameId())) {
                song = i;
            } else if (started.get(i) instanceof AudioCommand.PlaySfx sfx
                    && sfx.route() == AudioCommand.SfxRoute.DONOR_SMPS && "s2".equals(sfx.donorGameId())
                    && sfx.sfxId() == Sonic2AudioConstants.SFX_SUPER_TRANSFORM) {
                sound = i;
            }
        }
        assertTrue(song >= 0, "the donor's Super music is requested: " + started);
        assertTrue(sound >= 0, "the donor's SndID_SuperTransform ($DF) is requested: " + started);
        assertTrue(sound > song, "the sound follows the song, whose load stops every running SFX: " + started);
    }
}
