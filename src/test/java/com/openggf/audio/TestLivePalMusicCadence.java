package com.openggf.audio;

import com.openggf.audio.driver.SmpsDriverServiceObserver;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.smps.SmpsSequencer.Region;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.data.PhysicalImage;
import com.openggf.data.Rom;
import com.openggf.data.RomIdentity;
import com.openggf.data.RomImageCatalogue;
import com.openggf.game.GameServices;
import com.openggf.game.sonic1.audio.Sonic1AudioProfile;
import com.openggf.game.sonic1.audio.Sonic1Music;
import com.openggf.game.sonic2.audio.Sonic2AudioProfile;
import com.openggf.game.sonic2.audio.Sonic2Music;
import com.openggf.game.sonic3k.audio.Sonic3kAudioProfile;
import com.openggf.game.sonic3k.audio.Sonic3kAudioResourceProfile;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestLivePalMusicCadence {
    private AudioManager audio;
    private Rom rom;
    private final MusicPassObserver observer = new MusicPassObserver();

    @BeforeEach
    void reset() {
        TestEnvironment.resetAll();
        audio = AudioManager.getInstance();
        audio.resetState();
    }

    @AfterEach
    void close() {
        audio.setDriverServiceObserver(null);
        audio.resetState();
        GameServices.configuration().clearSessionOverrides();
        if (rom != null) rom.close();
    }

    @ParameterizedTest
    @EnumSource(Region.class)
    @RequiresRom(SonicGame.SONIC_1)
    void sonic1RunsOneMusicPassPerService(Region region) throws Exception {
        // UpdateMusic (s1.sounddriver.asm:147-176) has no PAL repeat.
        rom = new Rom();
        rom.open(RomTestUtils.ensureSonic1RomAvailable().getAbsolutePath());
        start(region, new Sonic1AudioProfile(), Sonic1Music.GHZ.id);
        assertMusicRepeats(List.of());
    }

    @ParameterizedTest
    @CsvSource({"PAL, EMERALD_HILL", "NTSC, EMERALD_HILL", "PAL, UNDERWATER", "NTSC, UNDERWATER"})
    @RequiresRom(SonicGame.SONIC_2)
    void sonic2HonorsTheSongPalFlag(Region region, Sonic2Music song) throws Exception {
        // .pal_timer (s2.sounddriver.asm:438-452) reloads 5 after decrementing to zero.
        // zMusIDPtr_Countdown (s2.sounddriver.asm:3854) sets MusFlag_SlowerOnPAL.
        rom = new Rom();
        rom.open(RomTestUtils.ensureSonic2RomAvailable().getAbsolutePath());
        start(region, new Sonic2AudioProfile(), song.id);
        assertMusicRepeats(region == Region.PAL && song == Sonic2Music.EMERALD_HILL
                ? List.of(5, 10, 15) : List.of());
    }

    @ParameterizedTest
    @CsvSource({"PAL, S3", "NTSC, S3", "PAL, SK", "NTSC, SK", "PAL, S3K", "NTSC, S3K"})
    @RequiresRom(SonicGame.SONIC_3K)
    void sonic3CartridgesUseTheirRetailReload(Region region, RomIdentity identity) throws Exception {
        // zVInt (Sound/Z80 Sound Driver.asm:470-499) tests before decrementing;
        // zInitAudioDriver (Sound/Z80 Sound Driver.asm:548-549) seeds 5.
        // The shipped v3 driver reloads 6. S&K and locked-on use the v4 reload of 5.
        var image = PhysicalImage.probe(RomTestUtils.ensureSonic3kRomAvailable().toPath());
        var catalogue = new RomImageCatalogue(List.of(image), Map.of(), false);
        rom = Rom.fromReader(catalogue.open(identity), identity.toString());
        var resources = Sonic3kAudioResourceProfile.fromLogicalRom(identity);
        int nativeSong = identity == RomIdentity.S3 ? 0x02 : 0x0F;
        start(region, new Sonic3kAudioProfile(resources), resources.musicId(nativeSong));
        List<Integer> palRepeats = identity == RomIdentity.S3 ? List.of(6, 12, 18) : List.of(6, 11, 16);
        assertMusicRepeats(region == Region.PAL ? palRepeats : List.of());
    }

    private void start(Region region, GameAudioProfile profile, int song) {
        var config = GameServices.configuration();
        config.setSessionOverride(SonicConfiguration.REGION, region.name());
        config.setSessionOverride(SonicConfiguration.AUDIO_ENABLED, true);
        config.setSessionOverride(SonicConfiguration.AUDIO_MUSIC_VOLUME, 100);
        audio.setBackend(new HeadlessSmpsAudioBackend(config, GameServices.profiler(), audio, audio));
        audio.installMatchedSource(rom, profile);
        audio.setSoundMap(profile.getSoundMap());
        audio.setDriverServiceObserver(observer);
        audio.playMusic(song);
    }

    private void assertMusicRepeats(List<Integer> expected) {
        // The first frame with a music pass is service 1. ROM loading can delay it.
        for (int frame = 0; frame < 30 && observer.passes == 0; frame++) {
            audio.presentFrame(PresentationMode.FORWARD);
        }
        assertEquals(1, observer.passes, "first music pass after ROM-backed song admission");
        List<Integer> repeats = new ArrayList<>();
        for (int service = 2; service <= 18; service++) {
            observer.passes = 0;
            audio.presentFrame(PresentationMode.FORWARD);
            assertTrue(observer.passes == 1 || observer.passes == 2,
                    "expected one or two music passes on service " + service + ", got " + observer.passes);
            if (observer.passes == 2) repeats.add(service);
        }
        assertEquals(expected, repeats, "services with a second music pass");
    }

    private static final class MusicPassObserver implements SmpsDriverServiceObserver {
        private int passes;

        @Override
        public void onServiceBegin(ServiceEvent event) {
            if (event.kind() == ServiceKind.SEQUENCER_TICK && !event.sequencer().sfx()) passes++;
        }
    }
}
