package com.openggf.game.sonic3k.audio;

import com.openggf.audio.GameMusic;
import com.openggf.audio.GameSound;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.game.ObjectArtProvider;
import com.openggf.game.ScrollHandlerProvider;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.competition.S3CompetitionGame;
import com.openggf.game.sonic3k.competition.S3kCompetitionLevelInitProfile;
import com.openggf.game.sonic3k.competition.S3kCompetitionResources;
import com.openggf.game.sonic3k.competition.S3kCompetitionRuntimeModule;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Receiving checks for stock composition; cartridge PCM/resource parsing has separate coverage. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSonic3kAudioModuleComposition {
    @Test
    void reusedStockModuleResolvesActualCartridgesWithoutRetainingThePreviousWorld() throws Exception {
        try (var locked = new Rom()) {
            assertTrue(locked.open(RomTestUtils.ensureSonic3kRomAvailable().getAbsolutePath()));
            TestEnvironment.configureRomFixture(locked);
            var bytes = RomByteReader.fromRom(locked);
            try (var sk = Rom.fromReader(bytes.window(0, 0x200000), "original SK audio receiving");
                 var s3 = Rom.fromReader(bytes.window(0x200000, 0x200000), "original S3 audio receiving")) {
                var module = new Sonic3kGameModule();
                var cartridges = new Rom[]{locked, sk, s3};
                var expected = new Sonic3kAudioResourceProfile[]{Sonic3kAudioResourceProfile.LOCKED_ON,
                        Sonic3kAudioResourceProfile.SONIC_KNUCKLES, Sonic3kAudioResourceProfile.SONIC_3};
                var retained = new Sonic3kAudioProfile[3];
                for (int pass = 0; pass < 2; pass++) {
                    for (int i = 0; i < cartridges.length; i++) {
                        GameServices.rom().setRom(cartridges[i]);
                        var profile = assertInstanceOf(Sonic3kAudioProfile.class, module.getAudioProfile());
                        assertEquals(expected[i], profile.resourceProfile());
                        assertEquals(i == 2 ? 0x129 : 0x29, profile.getMusicMap().get(GameMusic.ACT_CLEAR));
                        if (pass == 0) retained[i] = profile;
                        else assertSame(retained[i], profile, "only immutable profiles are reused");
                    }
                }
                assertNotSame(retained[0], retained[1]);
                assertNotSame(retained[1], retained[2]);
            } finally {
                GameServices.rom().setRom(locked);
            }
        }
    }

    @Test
    void originalCompetitionOwnsAudioEvenWhenTheAmbientRootIsLockedOn() throws Exception {
        try (var locked = new Rom()) {
            assertTrue(locked.open(RomTestUtils.ensureSonic3kRomAvailable().getAbsolutePath()));
            TestEnvironment.configureRomFixture(locked);
            var base = new Sonic3kGameModule();
            assertEquals(Sonic3kAudioResourceProfile.LOCKED_ON,
                    ((Sonic3kAudioProfile) base.getAudioProfile()).resourceProfile());
            var init = mock(S3kCompetitionLevelInitProfile.class);
            when(init.resources()).thenReturn(S3kCompetitionResources.ORIGINAL_S3);
            var runtime = new S3kCompetitionRuntimeModule(base, init,
                    mock(ObjectArtProvider.class), mock(ScrollHandlerProvider.class));
            var audio = assertInstanceOf(Sonic3kAudioProfile.class, runtime.getAudioProfile());
            assertEquals(Sonic3kAudioResourceProfile.SONIC_3, audio.resourceProfile());
            assertEquals(0x129, audio.getMusicMap().get(GameMusic.ACT_CLEAR));
            try (var s3 = Rom.fromReader(RomByteReader.fromRom(locked).window(0x200000, 0x200000),
                    "original S3 competition sound-map receiving")) {
                var game = new S3CompetitionGame(s3);
                assertFalse(game.getSoundMap().isEmpty(), "level audio configuration must retain native SFX");
                assertEquals(audio.getSoundMap().get(GameSound.RING_RIGHT),
                        game.getSoundMap().get(GameSound.RING_RIGHT));
            }
        }
    }
}
