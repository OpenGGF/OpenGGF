package com.openggf.audio;

import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.rewind.AudioLogicalSnapshot;
import com.openggf.audio.synth.NativeCompatiblePcmOutput;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.data.PhysicalImage;
import com.openggf.data.Rom;
import com.openggf.data.RomIdentity;
import com.openggf.data.RomImageCatalogue;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.audio.Sonic3kAudioProfile;
import com.openggf.game.sonic3k.audio.Sonic3kAudioResourceProfile;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.openggf.game.sonic3k.audio.Sonic3kAudioResourceProfile.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual production no-device synthesis; audible output is required, waveform parity is not claimed. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSonic3kCartridgeAudio {
    private AudioManager audio;
    private final Map<Sonic3kAudioResourceProfile, Rom> roms = new EnumMap<>(Sonic3kAudioResourceProfile.class);
    private final Map<Sonic3kAudioResourceProfile, Sonic3kAudioProfile> profiles =
            new EnumMap<>(Sonic3kAudioResourceProfile.class);

    @BeforeEach
    void installNoDeviceCapture() throws Exception {
        TestEnvironment.resetAll();
        GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_FM_CORE, "accurate");
        var image = PhysicalImage.probe(RomTestUtils.ensureSonic3kRomAvailable().toPath());
        var catalogue = new RomImageCatalogue(List.of(image), Map.of(), false);
        for (var identity : List.of(RomIdentity.S3, RomIdentity.SK, RomIdentity.S3K)) {
            var resource = fromLogicalRom(identity);
            Rom rom = Rom.fromReader(catalogue.open(identity), "original " + identity + " view");
            assertSame(resource, fromRom(rom));
            roms.put(resource, rom);
            profiles.put(resource, new Sonic3kAudioProfile(resource));
        }
        audio = AudioManager.getInstance();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        audio.setNativeCompatiblePcmCapture(NativeCompatiblePcmOutput.Config.withFilter(
                NativeCompatiblePcmOutput.Filter.THREE_BAND));
    }

    @AfterEach
    void close() {
        if (audio != null) {
            audio.resetState();
            audio.setNativeCompatiblePcmCapture(null);
        }
        GameServices.configuration().clearSessionOverrides();
        roms.values().forEach(Rom::close);
    }

    static Stream<Arguments> songs() {
        return Stream.of(
                Arguments.of(SONIC_3, 0x25), Arguments.of(SONIC_3, 0x2D),
                Arguments.of(SONIC_3, 0x20), Arguments.of(SONIC_3, 0x21),
                Arguments.of(SONIC_3, 0x22), Arguments.of(SONIC_3, 0x23), Arguments.of(SONIC_3, 0x24),
                Arguments.of(SONIC_KNUCKLES, 0x25), Arguments.of(SONIC_KNUCKLES, 0x1F),
                Arguments.of(SONIC_KNUCKLES, 0x0F), Arguments.of(SONIC_KNUCKLES, 0x10),
                Arguments.of(LOCKED_ON, 0x01));
    }

    @ParameterizedTest(name = "{0} native song {1}")
    @MethodSource("songs")
    void requiredSongsProduceSoundThroughTheProductionLoaderAndSynthesizer(
            Sonic3kAudioResourceProfile resources, int nativeId) {
        install(resources);
        int id = resources.musicId(nativeId);
        if (resources == SONIC_3 && nativeId == 0x25) audio.playMusicMailboxNativeRequest(nativeId);
        else audio.playMusic(id);
        present(36);
        assertEquals(id, audio.captureLogicalSnapshot().presentation().activeMusic().musicId());
        assertSounds(drain(), resources + " music " + Integer.toHexString(nativeId));
        assertEquals(1, audio.nativeCompatiblePcmSessions(), "one real synthesis session served the sound");
    }

    static Stream<Sonic3kSfx> competitionEffects() {
        return Stream.of(Sonic3kSfx.JUMP, Sonic3kSfx.RING_RIGHT, Sonic3kSfx.SKID,
                Sonic3kSfx.SPRING, Sonic3kSfx.RING_LOSS, Sonic3kSfx.SMALL_BUMPERS,
                Sonic3kSfx.STARPOST, Sonic3kSfx.GOAL, Sonic3kSfx.LAUNCH_READY,
                Sonic3kSfx.LAUNCH_GO, Sonic3kSfx.SWITCH, Sonic3kSfx.ERROR);
    }

    @ParameterizedTest
    @MethodSource("competitionEffects")
    void ordinaryCompetitionPlayerItemMenuAndResultsEffectsProduceSound(Sonic3kSfx effect) {
        install(SONIC_3);
        // zPlaySound_CheckRing (Sound/Z80 Sound Driver.asm:1919-1929)
        // alternates raw $33 requests; the reset speaker selects $34 first.
        int admittedId = effect == Sonic3kSfx.RING_RIGHT ? Sonic3kSfx.RING_LEFT.id : effect.id;
        assertTrue(audio.captureLogicalSnapshot().ringLeft());
        assertTrue(audio.playSfx(effect.id));
        present(1);
        assertTrue(audio.shadowSmpsDriverSnapshotForTesting().sequencers().stream()
                .anyMatch(entry -> entry.sfx() && entry.source().id() == admittedId),
                "the real original-S3 SFX program was admitted");
        assertEquals(effect != Sonic3kSfx.RING_RIGHT, audio.captureLogicalSnapshot().ringLeft());
        present(29);
        assertSounds(drain(), "original S3 " + effect);
    }

    @ParameterizedTest
    @EnumSource(Sonic3kAudioResourceProfile.class)
    void sameSourceReinstallationAndRestoreKeepAudibleForwardReplayExact(
            Sonic3kAudioResourceProfile resources) {
        install(resources);
        audio.playMusic(resources.musicId(resources == SONIC_3 ? 0x20 : 0x0F));
        present(20);
        drain();
        AudioLogicalSnapshot before = audio.captureLogicalSnapshot();
        audio.setRom(roms.get(resources));
        audio.setAudioProfile(profiles.get(resources));
        present(16);
        short[] expected = drain();
        assertSounds(expected, "restore reference " + resources);
        audio.restoreLogicalSnapshot(before);
        present(16);
        assertArrayEquals(expected, drain(), "resource and chip state survive same-source entry/restore");
        audio.restoreLogicalSnapshot(before);
        present(1);
        drain();
        present(16);
        assertFalse(Arrays.equals(expected, drain()), "one-frame-shifted output rejects a vacuous equality");
        assertEquals(1, audio.nativeCompatiblePcmSessions(), "the retained source kept its synthesis owner");
    }

    @Test
    void originalS3ExtraLifeUsesTheNamespacedOverrideAndRestoresItsArenaMusic() {
        install(SONIC_3);
        audio.playMusic(0x120);
        present(20);
        drain();
        audio.playMusic(profiles.get(SONIC_3).getExtraLifeMusicId());
        present(1);
        var overridden = audio.captureLogicalSnapshot().presentation();
        assertEquals(0x12A, overridden.activeMusic().musicId());
        assertEquals(0x120, overridden.overrideStack().getFirst().musicId());
        present(30);
        assertSounds(drain(), "original S3 one-up override");
        int remaining = 240;
        while (audio.captureLogicalSnapshot().presentation().activeMusic().musicId() == 0x12A
                && remaining-- > 0) {
            present(1);
            drain();
        }
        assertTrue(remaining > 0, "the native one-up program must complete");
        assertEquals(0x120, audio.captureLogicalSnapshot().presentation().activeMusic().musicId());
        assertTrue(audio.captureLogicalSnapshot().presentation().overrideStack().isEmpty());
    }

    @Test
    void originalSkThenS3ThenSkBootsInOneJvmCannotReuseTheWrongCartridge() {
        short[][] output = new short[3][];
        Sonic3kAudioResourceProfile[] visits = {SONIC_KNUCKLES, SONIC_3, SONIC_KNUCKLES};
        for (int i = 0; i < visits.length; i++) {
            install(visits[i]);
            audio.playMusic(visits[i].musicId(0x25));
            present(36);
            output[i] = drain();
            assertSounds(output[i], "reused boot " + visits[i]);
            assertSame(profiles.get(visits[i]), audio.getAudioProfile());
            assertEquals(visits[i].musicId(0x25),
                    audio.captureLogicalSnapshot().presentation().activeMusic().musicId());
        }
        assertFalse(Arrays.equals(output[0], output[1]), "the cartridges have distinct title arrangements");
        assertArrayEquals(output[0], output[2], "returning to S&K reloads its original resources and fresh device");
    }

    private void install(Sonic3kAudioResourceProfile resources) {
        audio.installMatchedSource(roms.get(resources), profiles.get(resources));
        audio.setSoundMap(profiles.get(resources).getSoundMap());
    }

    private void present(int frames) {
        for (int frame = 0; frame < frames; frame++) audio.presentFrame(PresentationMode.FORWARD);
    }

    private short[] drain() {
        short[] buffer = new short[2 * 65_536];
        int frames = audio.drainNativeCompatiblePcm(buffer, 0, buffer.length / 2);
        assertTrue(frames < buffer.length / 2, "the bounded buffer drained the entire produced window");
        assertTrue(frames > 0, "the no-device path really rendered PCM");
        return Arrays.copyOf(buffer, frames * 2);
    }

    private static void assertSounds(short[] pcm, String source) {
        for (short sample : pcm) if (sample != 0) return;
        fail(source + " produced only silence");
    }
}
