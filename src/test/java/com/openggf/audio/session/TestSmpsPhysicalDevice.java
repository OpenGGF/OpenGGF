package com.openggf.audio.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.audio.driver.SmpsDriverServiceObserver;
import com.openggf.audio.rewind.SmpsSourceDescriptor;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.synth.ChipWriteObserver;
import com.openggf.audio.synth.VirtualSynthesizer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSmpsPhysicalDevice {
    @Test
    void deferredConstructionAppliesSettingsWithoutChipWrites() {
        SmpsSessionTestFixtures.RecordingObserver observer =
                new SmpsSessionTestFixtures.RecordingObserver();
        SmpsPhysicalDevice.Settings settings =
                new SmpsPhysicalDevice.Settings(48_000, true, true);

        SmpsPhysicalDevice device =
                new SmpsPhysicalDevice(settings, observer);
        SmpsPhysicalDevice.Snapshot snapshot = device.captureSnapshot();

        assertTrue(observer.events().isEmpty());
        assertEquals(settings, snapshot.settings());
        assertEquals(48_000, snapshot.synth().outputSampleRate());
        assertTrue(snapshot.synth().ym().dacInterpolate());
        assertTrue(snapshot.synth().psg().noiseShiftOnEveryToggle());

        device.close();
        assertTrue(observer.events().isEmpty());
    }

    @Test
    void legacyConstructorsRetainSilenceWhileDeferredConstructionWritesNothing() {
        SmpsSessionTestFixtures.RecordingObserver legacy =
                new SmpsSessionTestFixtures.RecordingObserver();
        SmpsSessionTestFixtures.RecordingObserver deferred =
                new SmpsSessionTestFixtures.RecordingObserver();

        new VirtualSynthesizer(44_100, legacy);
        new VirtualSynthesizer(44_100, deferred,
                VirtualSynthesizer.Initialization.DEFERRED);

        assertEquals(202, legacy.events().size());
        assertTrue(deferred.events().isEmpty());
    }

    @Test
    void immutableWriteProgramIsAppliedInExactOrder() {
        SmpsSessionTestFixtures.RecordingObserver observer =
                new SmpsSessionTestFixtures.RecordingObserver();
        SmpsPhysicalDevice device = new SmpsPhysicalDevice(
                SmpsSessionTestFixtures.settings(), observer);
        List<SmpsChipWrite> source = new ArrayList<>(List.of(
                new SmpsChipWrite.Ym2612(1, 0xA4, 0x21),
                new SmpsChipWrite.Psg(0x9F),
                new SmpsChipWrite.Ym2612(0, 0x28, 0x04)));
        SmpsWriteProgram program = new SmpsWriteProgram(source);
        source.clear();

        device.apply(program);

        assertEquals(List.of(
                "YM:1:A4:21", "PSG:9F", "YM:0:28:04"),
                observer.events());
        assertThrows(UnsupportedOperationException.class,
                () -> program.writes().clear());
    }

    @Test
    void directRollbackRestoresStateWithoutDiagnosticWrites() {
        SmpsSessionTestFixtures.RecordingObserver observer =
                new SmpsSessionTestFixtures.RecordingObserver();
        SmpsPhysicalDevice device = new SmpsPhysicalDevice(
                SmpsSessionTestFixtures.settings(), observer);
        device.apply(new SmpsWriteProgram(List.of(
                new SmpsChipWrite.Ym2612(0, 0x22, 0x08),
                new SmpsChipWrite.Psg(0x84))));
        SmpsPhysicalDevice.LiveMutationToken token =
                device.captureLiveMutation();
        JsonNode expected = SmpsSessionTestFixtures.json(
                device.captureSnapshot());

        device.apply(new SmpsWriteProgram(List.of(
                new SmpsChipWrite.Ym2612(0, 0x28, 0xF0),
                new SmpsChipWrite.Psg(0x92))));
        observer.clear();
        device.rollbackLiveMutation(token);

        assertTrue(observer.events().isEmpty());
        assertEquals(expected, SmpsSessionTestFixtures.json(
                device.captureSnapshot()));
        assertThrows(IllegalStateException.class,
                () -> device.rollbackLiveMutation(token));

        SmpsPhysicalDevice other = new SmpsPhysicalDevice(
                SmpsSessionTestFixtures.settings(), ChipWriteObserver.NONE);
        SmpsPhysicalDevice.LiveMutationToken otherToken =
                device.captureLiveMutation();
        assertThrows(IllegalArgumentException.class,
                () -> other.rollbackLiveMutation(otherToken));
    }

    @Test
    void controlsAndOutputSettingsApplyOncePerSession() {
        SmpsDriverSession session = SmpsSessionTestFixtures.session(
                new SmpsSessionTestFixtures.RecordingObserver());
        session.install();
        Object physical = session.physicalIdentityForTesting();
        SmpsPhysicalDevice.Settings settings =
                session.captureSnapshot().physical().settings();
        session.applyChannelMasks(0x15, 0x05);
        SmpsDriverSessionSnapshot controlled = session.captureSnapshot();

        session.applyCommand(new SmpsSessionCommand.SetSpeedShoes(true));
        session.applyCommand(new SmpsSessionCommand.SetSpeedMultiplier(2));
        session.applyCommand(new SmpsSessionCommand.ResetRingAlternation(false));

        assertSame(physical, session.physicalIdentityForTesting());
        assertEquals(settings,
                session.captureSnapshot().physical().settings());
        assertArrayEquals(controlled.physical().synth().ym().mutes(),
                session.captureSnapshot().physical().synth().ym().mutes());
        assertArrayEquals(controlled.physical().synth().psg().mutes(),
                session.captureSnapshot().physical().synth().psg().mutes());
    }
}

final class SmpsSessionTestFixtures {
    private static final ObjectMapper JSON = new ObjectMapper();

    private SmpsSessionTestFixtures() {
    }

    static SmpsPhysicalDevice.Settings settings() {
        return new SmpsPhysicalDevice.Settings(44_100, false, false);
    }

    static SmpsDriverSession session(ChipWriteObserver observer) {
        SmpsPhysicalPolicy policy =
                LegacyCompatibilitySmpsPhysicalPolicy.INSTANCE;
        SmpsPhysicalDevice.Settings settings = settings();
        return new SmpsDriverSession(settings, policy, observer,
                new SmpsSessionProfileFingerprint(
                        "test", 7, policy.identity(), settings),
                SmpsDriverSessionConfiguration.DEFAULT);
    }

    static SmpsDriverServiceObserver.DriverIdentity owner(long ordinal) {
        return new SmpsDriverServiceObserver.DriverIdentity(ordinal,
                SmpsDriverServiceObserver.DriverAdmissionOrigin.unspecified());
    }

    static SmpsSourceDescriptor source(int id) {
        return new SmpsSourceDescriptor(
                SmpsSourceDescriptor.Kind.BASE_MUSIC,
                id, null, null, 0x8000 + id, 4,
                id * 31, false, 7);
    }

    static DacData dac() {
        return new DacData(
                Map.of(1, new byte[] {0, 32, 64, 96, 127}),
                Map.of(0x81, new DacData.DacEntry(1, 4)),
                295);
    }

    static JsonNode json(Object value) {
        return JSON.valueToTree(value);
    }

    static final class RecordingObserver implements ChipWriteObserver {
        private final List<String> events = new ArrayList<>();

        @Override
        public void onYm2612Write(int port, int register, int value) {
            events.add("YM:%d:%02X:%02X".formatted(
                    port, register, value));
        }

        @Override
        public void onPsgWrite(int value) {
            events.add("PSG:%02X".formatted(value));
        }

        List<String> events() {
            return List.copyOf(events);
        }

        void clear() {
            events.clear();
        }
    }
}
