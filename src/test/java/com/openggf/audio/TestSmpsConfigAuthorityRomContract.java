package com.openggf.audio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.audio.driver.SmpsDriver;
import com.openggf.audio.driver.SmpsDriverServiceObserver;
import com.openggf.audio.presentation.AudioPresentationSourceFactory;
import com.openggf.audio.presentation.DecodedPcmCache;
import com.openggf.audio.rewind.SmpsDriverSnapshot;
import com.openggf.audio.rewind.SmpsSourceDescriptor;
import com.openggf.audio.session.OwnedSmpsAudioStream;
import com.openggf.audio.session.SmpsDriverSessionConfiguration;
import com.openggf.audio.session.SmpsPhysicalDevice;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsCoordFlagHandlerOwner;
import com.openggf.audio.smps.SmpsCoordFlagRuntimeState;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.synth.ChipWriteObserver;
import com.openggf.audio.synth.FmCoreSelection;
import com.openggf.data.Rom;
import com.openggf.game.sonic3k.audio.Sonic3kAudioProfile;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.audio.smps.Sonic3kCoordFlagHandler;
import com.openggf.game.sonic3k.audio.smps.Sonic3kSmpsLoader;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static com.openggf.audio.TestSmpsConfigAuthorityContract.*;
import static org.junit.jupiter.api.Assertions.*;

/** Short engine-preservation oracle. No native-emulator parity is claimed. */
class TestSmpsConfigAuthorityRomContract {
    @ParameterizedTest
    @ValueSource(strings = {"music", "mixed", "standalone"})
    @RequiresRom(SonicGame.SONIC_3K)
    void authenticS3kProgramsMatchIndependentFactoryAndDirectSessionBaseline(String route) throws Exception {
        try (Rom rom = new Rom()) {
            assertTrue(rom.open(RomTestUtils.ensureSonic3kRomAvailable().getAbsolutePath()));
            // Each arm loads fresh ROM programs and owns fresh handler/chip state.
            Observation control = observe(rom, route, true);
            Observation legacy = observe(rom, route, false);
            assertEquals(control, legacy, route + " legacy binding vs independent supplied-owner session");
            assertEquals(expected(route), legacy, "pinned pre-injection engine observation");
            assertTrue(legacy.nonzeroSamples > 0, "silence cannot establish PCM preservation");
            assertTrue(legacy.writes > 0);
            assertTrue(legacy.services > 0);
            assertEquals(18, legacy.frames);
            System.out.println("SMPS authority baseline " + route + " " + legacy);
        }
    }

    private Observation observe(Rom rom, String route, boolean direct) throws Exception {
        try (var scope = new Scope()) {
            var loader = new Sonic3kSmpsLoader(rom); // Separate loader caches for the two arms.
            var profile = new RecordingProfile();
            var s = scope.manager(profile);
            scope.singleton(s);
            scope.configureRoot(scope.manager(new Sonic3kAudioProfile()));
            var owner = new SmpsCoordFlagHandlerOwner(new SmpsCoordFlagRuntimeState());
            owner.register("s3k", Sonic3kCoordFlagHandler::new);
            var settings = new AudioPresentationSourceFactory.Settings(48_000,
                    SmpsSequencer.Region.NTSC, false, false, false, 1, () -> {},
                    new DecodedPcmCache(), ignored -> null);
            var factory = new AudioPresentationSourceFactory(() -> true, owner, settings);
            var observer = new Oracle();
            AbstractSmpsData music = loader.loadMusic(Sonic3kMusic.AIZ1.id);
            DacData dac = loader.loadDacData();
            assertNotNull(music);
            assertNotNull(dac);
            var backend = scope.backend(() -> {});
            backend.setChipWriteObserver(observer);
            backend.setDriverServiceObserver(observer);
            try (var stream = direct ? new OwnedSmpsAudioStream("s3k", 0,
                    new SmpsPhysicalDevice.Settings(48_000, false, FmCoreSelection.ACCURATE),
                    profile.smpsPhysicalPolicy(), observer,
                    new SmpsDriverSessionConfiguration(profile.smpsStatefulCommandPolicy())) : null) {
                SmpsDriver driver = direct ? stream.logicalDriver() : null;
                if (direct) driver.setServiceObserver(observer);
                if (!route.equals("standalone")) {
                    if (direct) add(driver, music, dac, factory, false, null, profile);
                    else backend.playSmps(music, dac, S3K, false);
                }
                for (int frame = 0; frame < 18; frame++) {
                    if (!route.equals("music") && (frame == 0 || frame == 3 || frame == 6 || frame == 9)) {
                        int id = frame == 6 ? Sonic3kSfx.RING_LEFT.id : Sonic3kSfx.SPINDASH.id;
                        AbstractSmpsData data = loader.loadSfx(id);
                        assertNotNull(data);
                        if (direct) add(driver, data, dac, factory, true,
                                route.equals("mixed") ? music : null, profile);
                        else backend.playSfxSmps(data, dac, 1, S3K);
                        if (frame == 6) assertEquals(0, state(direct, owner, profile).spindashRevCounter(),
                                "authentic ordinary SFX resets before rendering");
                    }
                    AudioStream selected = direct ? stream : route.equals("standalone")
                            ? backend.stateForTesting().sfxStream() : backend.stateForTesting().currentStream();
                    assertNotNull(selected);
                    observer.frame(frame);
                    short[] pcm = new short[1_600];
                    assertEquals(pcm.length, selected.read(pcm, pcm.length));
                    observer.pcm(pcm);
                    if (!direct) backend.update();
                    SmpsDriverSnapshot snapshot = direct ? driver.captureSnapshot() : route.equals("standalone")
                            ? backend.stateForTesting().standaloneSfxDriverSnapshot()
                            : backend.stateForTesting().musicDriverSnapshot();
                    observer.logical(snapshot, state(direct, owner, profile).snapshot());
                    if (!route.equals("music") && (frame == 0 || frame == 3 || frame == 6 || frame == 9)) {
                        int expected = frame == 0 || frame == 9 ? 1 : frame == 3 ? 2 : 0;
                        assertEquals(expected, state(direct, owner, profile).spindashRevCounter(),
                                "authentic E9/reset at frame " + frame);
                    }
                }
                return observer.finish();
            }
        }
    }

    private static Observation expected(String route) {
        // Established on unchanged production 4eb8e8e1, independently compared before pinning.
        return switch (route) {
            case "music" -> new Observation(
                    "137bd022f6603d640d15aa8980817604cf9a169f4aaf1002d4a918e8af28b91d",
                    "567bd389a188ef7b850ad94a364c0f4d84c29b0624175c057b01a8e0a86f7126",
                    "69218fad4e05e4c45dd7f165c38e7cc62271a3c4f35763385c43931b0b6e50bf",
                    2_210, 18, 27_187, 18);
            case "mixed" -> new Observation(
                    "0efd250bdeb836c695348f6e787cb76dc3f9f987d18e4a490415fa3eaf81f0d5",
                    "0005c45fd2bd9272d3b1f77a3f1668361f62b7a437d4aa4b84228fdc7f926c82",
                    "fef60de048faa7cccfae15c1b31a8b7cca07354ce3eacf24d2de8f4dd8726925",
                    2_351, 36, 28_798, 18);
            case "standalone" -> new Observation(
                    "28654cf7dc78fa0de55449db0ed5fbda7cc0cf20e68bfc5803d3f3ff717e93e9",
                    "51194222f497282fedb4d2b5a88d4cdf7ce0191c284dee2d959c4138e13eb600",
                    "e773e14bb55208fede13a5cef6046e4e8c75b82bbddc96f52f42a1a1cb6be23f",
                    270, 18, 28_798, 18);
            default -> throw new AssertionError(route);
        };
    }

    private static SmpsCoordFlagRuntimeState state(boolean direct, SmpsCoordFlagHandlerOwner owner,
            RecordingProfile profile) {
        return direct ? owner.state() : profile.owner.state();
    }

    private static void add(SmpsDriver driver, AbstractSmpsData data, DacData dac,
            AudioPresentationSourceFactory factory, boolean sfx, AbstractSmpsData fallback,
            Sonic3kAudioProfile profile) {
        var config = factory.legacySequencerConfig("s3k", S3K);
        var seq = new SmpsSequencer(data, dac, driver, () -> {}, config);
        seq.setSourceDescriptor(sfx ? SmpsSourceDescriptor.baseSfx(data) : SmpsSourceDescriptor.baseMusic(data));
        seq.setSampleRate(48_000);
        seq.setFm6DacOff(false);
        if (sfx) {
            seq.setSfxMode(true);
            seq.setPitch(1);
            seq.setSfxPriority(profile.getSfxPriority(data.getId()));
            seq.setSpecialSfx(profile.isSpecialSfx(data.getId()));
            if (fallback != null) seq.setFallbackVoiceData(fallback);
        } else seq.setFallbackVoiceData(data);
        driver.addSequencer(seq, sfx);
    }

    record Observation(String pcm, String orderedWrites, String servicesAndLogical,
            long writes, long services, long nonzeroSamples, int frames) {}

    @JsonIgnoreProperties({"smpsData", "dacData", "audioManager", "config"})
    private abstract static class RetainedDependencies {}

    private static final class Oracle implements ChipWriteObserver, SmpsDriverServiceObserver {
        private final ObjectMapper json = new ObjectMapper().addMixIn(
                SmpsDriverSnapshot.SequencerEntry.class, RetainedDependencies.class);
        private final MessageDigest pcm = digest();
        private final MessageDigest writes = digest();
        private final MessageDigest state = digest();
        private long writeCount;
        private long services;
        private long nonzeroSamples;
        private int frames;

        void frame(int frame) { frames++; text(writes, "frame:" + frame); text(state, "frame:" + frame); }
        void logical(SmpsDriverSnapshot snapshot, SmpsCoordFlagRuntimeState.Snapshot coord) {
            text(state, json.valueToTree(snapshot).toString());
            text(state, coord.toString());
        }
        void pcm(short[] samples) {
            var bytes = ByteBuffer.allocate(samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
            for (short sample : samples) {
                bytes.putShort(sample);
                if (sample != 0) nonzeroSamples++;
            }
            pcm.update(bytes.array());
        }
        @Override public void onYm2612Write(int port, int reg, int value) {
            writeCount++; text(writes, "ym:" + port + ":" + reg + ":" + value);
        }
        @Override public void onPsgWrite(int value) { writeCount++; text(writes, "psg:" + value); }
        @Override public void onServiceBegin(ServiceEvent event) {
            services++; text(state, "begin:" + event.ordinal() + ":" + event.kind());
        }
        @Override public void onServiceEnd(ServiceEvent event, SmpsDriverSnapshot snapshot) {
            text(state, "end:" + event.ordinal() + ":" + event.kind());
            text(state, json.valueToTree(snapshot).toString());
        }
        Observation finish() {
            return new Observation(hex(pcm), hex(writes), hex(state), writeCount, services, nonzeroSamples, frames);
        }
        private static void text(MessageDigest target, String value) {
            target.update((value + "\n").getBytes(StandardCharsets.UTF_8));
        }
        private static String hex(MessageDigest digest) { return HexFormat.of().formatHex(digest.digest()); }
        private static MessageDigest digest() {
            try { return MessageDigest.getInstance("SHA-256"); }
            catch (java.security.NoSuchAlgorithmException failure) { throw new AssertionError(failure); }
        }
    }
}
