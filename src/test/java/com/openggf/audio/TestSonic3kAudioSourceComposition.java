package com.openggf.audio;

import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.rewind.AudioSourceDescriptor;
import com.openggf.audio.synth.NativeCompatiblePcmOutput;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.game.RomDataSource;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.audio.Sonic3kAudioProfile;
import com.openggf.game.sonic3k.audio.Sonic3kAudioResourceProfile;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** Current source ownership, actual no-device PCM and restore across foreign ambient ROM changes. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSonic3kAudioSourceComposition {
    private Rom locked;
    private Rom sk;
    private Rom s3;
    private Rom s1;
    private Rom s2;
    private AudioManager audio;

    @BeforeEach
    void openOriginalSources() throws Exception {
        locked = open(RomTestUtils.ensureSonic3kRomAvailable().getAbsolutePath());
        s1 = open(RomTestUtils.ensureSonic1RomAvailable().getAbsolutePath());
        s2 = open(RomTestUtils.ensureSonic2RomAvailable().getAbsolutePath());
        var bytes = RomByteReader.fromRom(locked);
        sk = Rom.fromReader(bytes.window(0, 0x200000), "owned original SK source");
        s3 = Rom.fromReader(bytes.window(0x200000, 0x200000), "owned original S3 source");
    }

    @AfterEach
    void closeSources() {
        if (audio != null) {
            audio.resetState();
            audio.setNativeCompatiblePcmCapture(null);
        }
        TestEnvironment.resetAll();
        for (Rom rom : new Rom[]{locked, sk, s3, s1, s2}) if (rom != null) rom.close();
    }

    @ParameterizedTest
    @EnumSource(Sonic3kAudioResourceProfile.class)
    void actualRootUsesItsPinnedCartridgeAndRestoresPcmDespiteForeignAmbientRoms(
            Sonic3kAudioResourceProfile resources) {
        var module = new Sonic3kGameModule();
        TestEnvironment.configureGameModuleFixture(module);
        Rom source = switch (resources) {
            case LOCKED_ON -> locked;
            case SONIC_KNUCKLES -> sk;
            case SONIC_3 -> s3;
        };
        SessionManager.openGameplaySession(module, module, new RomDataSource(source, "owned audio world"), null);
        GameServices.rom().setRom(s1);
        var profile = assertInstanceOf(Sonic3kAudioProfile.class, module.getAudioProfile());
        assertEquals(resources, profile.resourceProfile());
        install(source, profile);
        int song = resources.musicId(0x25);
        audio.playMusic(song);
        present(20);
        assertSounds(drain());
        var selected = audio.captureLogicalSnapshot();
        GameServices.rom().setRom(s2);
        assertSame(profile, module.getAudioProfile(), "the ambient ROM cannot replace the world's source");
        assertSame(source, SessionManager.getCurrentWorldSession().getDataSource().rom().orElseThrow());
        present(16);
        short[] expected = drain();
        assertSounds(expected);
        audio.restoreLogicalSnapshot(selected);
        present(16);
        assertArrayEquals(expected, drain(), "the owning original cartridge and synthesis state restore exactly");
        assertEquals(song, audio.captureLogicalSnapshot().presentation().activeMusic().musicId());
        assertEquals(1, audio.nativeCompatiblePcmSessions());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void detachedLockedOnDonorProducesAndRestoresItsOwnPcmInS1AndS2Worlds(boolean sonic2) {
        Rom ambient = sonic2 ? s2 : s1;
        TestEnvironment.configureRomFixture(ambient);
        var base = GameServices.module().getAudioProfile();
        install(ambient, base);
        var donorModule = new Sonic3kGameModule();
        var donor = assertInstanceOf(Sonic3kAudioProfile.class, donorModule.getAudioProfile());
        assertEquals(Sonic3kAudioResourceProfile.LOCKED_ON, donor.resourceProfile());
        var previous = audio.captureLogicalSnapshot();
        var loader = donor.createSmpsLoader(locked);
        try (var scope = ScopedDonorAudio.register(audio, "s3k", loader,
                loader.loadDacData(), donor.getSequencerConfig(), donor)) {
            audio.playDonorMusic("s3k", 0x25);
            present(20);
            assertSounds(drain());
            var selected = audio.captureLogicalSnapshot();
            var descriptor = selected.presentation().activeMusic().sourceDescriptor();
            assertEquals(AudioSourceDescriptor.Route.DONOR_MUSIC_ID, descriptor.route());
            assertEquals("s3k", descriptor.donorGameId());
            GameServices.rom().setRom(sonic2 ? s1 : s2);
            assertSame(donor, donorModule.getAudioProfile());
            present(16);
            short[] expected = drain();
            assertSounds(expected);
            audio.restoreLogicalSnapshot(selected);
            present(16);
            assertArrayEquals(expected, drain(), "donor restore keeps its declared locked-on source");
            assertSame(base, audio.getAudioProfile(), "the foreign world's installed base is retained");
        }
        assertEquals(previous.donorGameIds(), audio.captureLogicalSnapshot().donorGameIds());
        assertEquals(previous.donorBindings(), audio.captureLogicalSnapshot().donorBindings());
    }

    private void install(Rom rom, GameAudioProfile profile) {
        GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_FM_CORE, "accurate");
        audio = AudioManager.getInstance();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        audio.setNativeCompatiblePcmCapture(NativeCompatiblePcmOutput.Config.withFilter(
                NativeCompatiblePcmOutput.Filter.THREE_BAND));
        audio.installMatchedSource(rom, profile);
        audio.setSoundMap(profile.getSoundMap());
    }

    private void present(int frames) {
        for (int frame = 0; frame < frames; frame++) audio.presentFrame(PresentationMode.FORWARD);
    }

    private short[] drain() {
        short[] buffer = new short[2 * 65_536];
        int frames = audio.drainNativeCompatiblePcm(buffer, 0, buffer.length / 2);
        assertTrue(frames > 0 && frames < buffer.length / 2);
        return Arrays.copyOf(buffer, 2 * frames);
    }

    private static void assertSounds(short[] samples) {
        for (short sample : samples) if (sample != 0) return;
        fail("the actual native-compatible PCM window must sound");
    }

    private static Rom open(String path) {
        var rom = new Rom();
        assertTrue(rom.open(path));
        return rom;
    }
}
