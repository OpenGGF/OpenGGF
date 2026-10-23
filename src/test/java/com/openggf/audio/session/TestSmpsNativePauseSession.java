package com.openggf.audio.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.openggf.audio.MusicRestoreSink;
import com.openggf.audio.driver.SmpsDriver;
import com.openggf.audio.rewind.SmpsDriverSnapshot;
import com.openggf.audio.rewind.SmpsSequencerSnapshot;
import com.openggf.audio.rewind.SmpsSourceDescriptor;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.synth.Ym2612Chip;
import com.openggf.game.sonic2.audio.Sonic2SmpsCompatibilityPolicy;
import com.openggf.game.sonic2.audio.Sonic2SmpsSequencerConfig;
import com.openggf.game.sonic2.audio.Sonic2StatefulCommandPolicy;
import com.openggf.game.sonic2.audio.smps.Sonic2SmpsData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Real-session native pause regressions, using ROM-free S2 SMPS bytecode. */
class TestSmpsNativePauseSession {
    private static final byte[] VOICE = {
            0x2D, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12,
            13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24
    };

    @Test
    void nativePauseMutesOnceAndHoldsAllLogicalServiceUntilTheTickAfterResume() {
        try (Fixture fixture = ready()) {
            var session = fixture.session();
            JsonNode logical = logicalState(session);
            fixture.writes().clear();

            session.applyCommand(new SmpsSessionCommand.NativePause(true));
            assertEquals(new SmpsNativePauseState(false, true), session.captureSnapshot().nativePause());
            assertTrue(fixture.writes().events().isEmpty(), "the request itself must not write chips");
            assertEquals(SmpsServiceOutcome.ORDINARY, session.serviceForward());
            assertEquals(pauseWrites(), fixture.writes().events());
            assertEquals(logical, logicalState(session));
            assertEquals(new SmpsNativePauseState(true, null), session.captureSnapshot().nativePause());
            assertEquals(Boolean.TRUE, session.consumeNativePauseReceipt());
            assertNull(session.consumeNativePauseReceipt());

            fixture.writes().clear();
            session.serviceForward();
            session.serviceForward();
            session.applyCommand(new SmpsSessionCommand.NativePause(true));
            session.serviceForward();
            assertTrue(fixture.writes().events().isEmpty(), "held and repeated pause must not re-mute");
            assertEquals(logical, logicalState(session));
            assertEquals(Boolean.TRUE, session.consumeNativePauseReceipt());

            session.applyCommand(new SmpsSessionCommand.NativePause(false));
            session.serviceForward();
            assertEquals(resumeWrites(), fixture.writes().events());
            assertEquals(54, fixture.writes().events().size(), "27 writes each for DAC/FM1 native slots");
            assertEquals(logical, logicalState(session), "S2 resume also returns straight to the DAC loop");
            assertEquals(SmpsNativePauseState.RUNNING, session.captureSnapshot().nativePause());
            assertEquals(Boolean.FALSE, session.consumeNativePauseReceipt());

            int duration = fmDuration(session);
            fixture.writes().clear();
            session.serviceForward();
            assertEquals(duration - 1, fmDuration(session), "only the following V-int advances the note");
        }
    }

    @Test
    void resumeMailboxWithoutAPriorPauseStillRestoresVoicesAndSkipsThatService() {
        try (Fixture fixture = ready()) {
            var session = fixture.session();
            JsonNode before = logicalState(session);
            fixture.writes().clear();
            session.applyCommand(new SmpsSessionCommand.NativePause(false));
            session.serviceForward();

            assertEquals(resumeWrites(), fixture.writes().events());
            assertEquals(before, logicalState(session));
            assertEquals(Boolean.FALSE, session.consumeNativePauseReceipt());
            int duration = fmDuration(session);
            session.serviceForward();
            assertEquals(duration - 1, fmDuration(session));
        }
    }

    @Test
    void heldNativePauseContinuesDacClockAndProducesPcmWithoutAdvancingTracks() {
        try (Fixture fixture = ready()) {
            var session = fixture.session();
            session.applyCommand(new SmpsSessionCommand.NativePause(true));
            session.serviceForward();
            JsonNode logical = logicalState(session);
            Ym2612Chip.Snapshot before = ym(session);
            long rendered = session.renderedStereoFramesForTesting();

            short[] pcm = new short[512];
            boolean audible = false;
            int chunks = 0;
            // Applying queued YM strobes already clocks the core and fills its
            // output cache. Consume that finite rendered prefix, then demand
            // fresh DAC progress while the native logical service stays held.
            do {
                assertEquals(256, session.renderFrames(pcm, 0, 256));
                audible |= Arrays.stream(toInts(pcm)).anyMatch(value -> value != 0);
                chunks++;
            } while (ym(session).dacIndex() <= before.dacIndex() && chunks < 32);
            session.serviceForward();

            Ym2612Chip.Snapshot after = ym(session);
            assertEquals(1, before.currentDacSampleId());
            assertEquals(1, after.currentDacSampleId());
            assertTrue(after.dacIndex() > before.dacIndex(), "native pause never stops the DAC sample loop");
            assertTrue(audible,
                    "rendering must preserve audible DAC output rather than host-muting the session");
            assertEquals(rendered + chunks * 256L, session.renderedStereoFramesForTesting());
            assertEquals(logical, logicalState(session));
            assertFalse(session.captureSnapshot().physical().outputSilenced());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rewindRestoresPendingOrConsumedPauseAndReplaysIdenticalWritesAndPcm(boolean consumed) {
        try (Fixture fixture = ready()) {
            var session = fixture.session();
            session.applyCommand(new SmpsSessionCommand.NativePause(true));
            if (consumed) session.serviceForward();
            var saved = session.captureSnapshot();
            var logical = session.captureLogicalSnapshot();
            fixture.writes().clear();
            short[] expected = renderPauseResumeSequence(session);
            var expectedWrites = fixture.writes().events();
            JsonNode expectedLogical = logicalState(session);
            var expectedPhysical = SmpsSessionTestFixtures.json(session.captureSnapshot());

            session.commitRestore(session.prepareRestore(saved, logical, ignored -> fixture.dac()));
            assertEquals(saved.nativePause(), session.captureSnapshot().nativePause());
            assertNull(session.consumeNativePauseReceipt(), "restore must discard an ephemeral consumed receipt");
            fixture.writes().clear();
            short[] actual = renderPauseResumeSequence(session);

            assertArrayEquals(expected, actual);
            assertEquals(expectedWrites, fixture.writes().events());
            assertEquals(expectedLogical, logicalState(session));
            assertEquals(expectedPhysical, SmpsSessionTestFixtures.json(session.captureSnapshot()));
        }
    }

    @Test
    void liveMutationRollbackRestoresPausedChipsAndReplaysTheSamePcm() {
        try (Fixture fixture = ready()) {
            var session = fixture.session();
            session.applyCommand(new SmpsSessionCommand.NativePause(true));
            session.serviceForward();
            session.renderFrames(new short[64], 0, 32);
            var saved = session.captureSnapshot();
            var logical = session.captureLogicalSnapshot();
            fixture.writes().clear();

            var token = session.captureLiveMutation();
            short[] aborted = renderPauseResumeSequence(session);
            session.rollbackLiveMutation(token);

            assertEquals(SmpsSessionTestFixtures.json(saved), SmpsSessionTestFixtures.json(session.captureSnapshot()));
            assertEquals(logicalState(logical), logicalState(session));
            assertTrue(fixture.writes().events().isEmpty(), "aborted chip observations stay private");
            assertNull(session.consumeNativePauseReceipt());
            short[] replay = renderPauseResumeSequence(session);
            assertArrayEquals(aborted, replay);

            // The durable snapshot path must agree with the live-mutation path.
            session.commitRestore(session.prepareRestore(saved, logical, ignored -> fixture.dac()));
            assertArrayEquals(replay, renderPauseResumeSequence(session));
        }
    }

    @Test
    void malformedResumeVoiceFailsBeforePhysicalWritesAndCanRollBackTheQueuedEdge() {
        try (Fixture fixture = ready()) {
            var session = fixture.session();
            session.applyCommand(new SmpsSessionCommand.NativePause(true));
            session.serviceForward();
            // A malformed live logical owner, not an injected chip/register state.
            session.logicalDriverForTesting().sequencersForTesting().getFirst()
                    .getTracks().get(1).voiceData = new byte[24];
            var saved = SmpsSessionTestFixtures.json(session.captureSnapshot());
            var logical = logicalState(session);
            fixture.writes().clear();
            var token = session.captureLiveMutation();
            try {
                session.applyCommand(new SmpsSessionCommand.NativePause(false));
                assertThrows(IllegalStateException.class, session::serviceForward);
            } finally {
                session.rollbackLiveMutation(token);
            }

            assertTrue(fixture.writes().events().isEmpty());
            assertEquals(saved, SmpsSessionTestFixtures.json(session.captureSnapshot()));
            assertEquals(logical, logicalState(session));
            assertNull(session.consumeNativePauseReceipt());
            assertEquals(new SmpsNativePauseState(true, null), session.captureSnapshot().nativePause());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void segaTransportRetainsPendingPauseOrResumeUntilItsNaturalExit(boolean initiallyPaused) {
        try (Fixture fixture = ready()) {
            var session = fixture.session();
            if (initiallyPaused) {
                session.applyCommand(new SmpsSessionCommand.NativePause(true));
                session.serviceForward();
                session.consumeNativePauseReceipt();
            }
            JsonNode logical = logicalState(session);
            session.beginSegaPcmTransport(new byte[] {0x10, 0x20, 0x30, 0x40});
            fixture.writes().clear();
            session.applyCommand(new SmpsSessionCommand.NativePause(!initiallyPaused));

            assertTrue(session.segaPcmTransportActive());
            assertFalse(session.captureSnapshot().segaPcmTransport().stopRequested());
            assertEquals(new SmpsNativePauseState(initiallyPaused, !initiallyPaused),
                    session.captureSnapshot().nativePause());
            assertEquals(SmpsServiceOutcome.SEGA_PCM_TRANSPORT, session.serviceForward());
            assertEquals(SmpsServiceOutcome.SEGA_PCM_TRANSPORT, session.serviceForward());
            assertNull(session.consumeNativePauseReceipt());
            assertTrue(fixture.writes().events().isEmpty());
            assertEquals(logical, logicalState(session));

            session.renderFrames(new short[256], 0, 128);
            assertFalse(session.segaPcmTransportActive());
            assertEquals(new SmpsNativePauseState(initiallyPaused, !initiallyPaused),
                    session.captureSnapshot().nativePause());
            fixture.writes().clear();
            session.serviceForward();
            assertEquals(initiallyPaused ? resumeWrites() : pauseWrites(), fixture.writes().events());
            assertEquals(new SmpsNativePauseState(!initiallyPaused, null), session.captureSnapshot().nativePause());
            assertEquals(Boolean.valueOf(!initiallyPaused), session.consumeNativePauseReceipt());
            assertEquals(logical, logicalState(session));
        }
    }

    @Test
    void unsupportedProfileRejectsNativePauseWithoutChangingSessionState() {
        var session = SmpsSessionTestFixtures.session(new SmpsSessionTestFixtures.RecordingObserver());
        try (session) {
            session.install();
            var before = SmpsSessionTestFixtures.json(session.captureSnapshot());
            assertThrows(UnsupportedOperationException.class, session::requireNativePauseSupported);
            assertThrows(UnsupportedOperationException.class,
                    () -> session.applyCommand(new SmpsSessionCommand.NativePause(true)));
            assertEquals(before, SmpsSessionTestFixtures.json(session.captureSnapshot()));
        }
    }

    private static short[] renderPauseResumeSequence(SmpsDriverSession session) {
        short[] pcm = new short[768];
        session.serviceForward();
        session.renderFrames(pcm, 0, 128);
        session.applyCommand(new SmpsSessionCommand.NativePause(false));
        session.serviceForward();
        session.renderFrames(pcm, 256, 128);
        session.serviceForward();
        session.renderFrames(pcm, 512, 128);
        return pcm;
    }

    private static int fmDuration(SmpsDriverSession session) {
        return session.captureLogicalSnapshot().sequencers().getFirst().snapshot().tracks().get(1).duration();
    }

    private static JsonNode logicalState(SmpsDriverSession session) {
        return logicalState(session.captureLogicalSnapshot());
    }

    private static JsonNode logicalState(SmpsDriverSnapshot snapshot) {
        // Compare driver RAM and all track/tempo/fade state without serializing injected services.
        return SmpsSessionTestFixtures.json(new LogicalState(snapshot.region(), snapshot.readMode(),
                snapshot.continuousSfxId(), snapshot.continuousSfxFlag(), snapshot.contSfxLoopCnt(),
                snapshot.palUpdateCounter(), snapshot.sequencers().stream().map(e -> e.snapshot()).toList(),
                snapshot.fmLockSequencerIds(), snapshot.psgLockSequencerIds(), snapshot.fadeDelay(),
                snapshot.fadeDelayTimeout(), snapshot.fadeOutTimeout(), snapshot.fadeInTimeout(),
                snapshot.driverOwnedFade()));
    }

    private record LogicalState(SmpsSequencer.Region region, SmpsDriver.ReadMode readMode,
                                int continuousSfxId, boolean continuousSfxFlag, int contSfxLoopCnt,
                                int palUpdateCounter, List<SmpsSequencerSnapshot> sequencers,
                                int[] fmLocks, int[] psgLocks, int fadeDelay, int fadeDelayTimeout,
                                int fadeOutTimeout, int fadeInTimeout, boolean driverOwnedFade) { }

    private static Ym2612Chip.Snapshot ym(SmpsDriverSession session) {
        return assertInstanceOf(Ym2612Chip.Snapshot.class, session.captureSnapshot().physical().synth().ym());
    }

    private static int[] toInts(short[] values) {
        int[] result = new int[values.length];
        for (int index = 0; index < values.length; index++) result[index] = values[index];
        return result;
    }

    private static List<String> pauseWrites() {
        List<String> writes = new ArrayList<>();
        // Independent native countdown order, not the policy under test's program.
        for (int channel : new int[] {2, 6, 1, 5, 0, 4}) writes.add(ymWrite(0, 0x28, channel));
        for (int register = 0x30; register <= 0x8F; register++) {
            writes.add(ymWrite(0, register, 0xFF));
            writes.add(ymWrite(1, register, 0xFF));
        }
        writes.addAll(List.of("PSG:9F", "PSG:BF", "PSG:DF", "PSG:FF"));
        return List.copyOf(writes);
    }

    private static List<String> resumeWrites() {
        List<String> writes = new ArrayList<>();
        // zResumeTrack walks the music DAC slot first (FM6), then music FM1.
        appendVoice(writes, 1, 2, 0);
        appendVoice(writes, 0, 0, 2);
        return List.copyOf(writes);
    }

    private static void appendVoice(List<String> writes, int port, int channel, int volume) {
        writes.add(ymWrite(port, 0xB4 + channel, 0xC0));
        writes.add(ymWrite(port, 0xB0 + channel, 0x2D));
        int index = 1;
        for (int group : new int[] {0x30, 0x50, 0x60, 0x70, 0x80}) {
            for (int slot = 0; slot < 4; slot++) writes.add(ymWrite(port, group + channel + slot * 4, index++));
        }
        writes.add(ymWrite(port, 0xB4 + channel, 0xC0));
        writes.add(ymWrite(port, 0x40 + channel, 21));
        writes.add(ymWrite(port, 0x44 + channel, 22 + volume));
        writes.add(ymWrite(port, 0x48 + channel, 23 + volume));
        writes.add(ymWrite(port, 0x4C + channel, 24 + volume));
    }

    private static String ymWrite(int port, int register, int value) {
        return "YM:%d:%02X:%02X".formatted(port, register, value);
    }

    private static Fixture ready() {
        var physical = Sonic2SmpsCompatibilityPolicy.INSTANCE;
        var stateful = Sonic2StatefulCommandPolicy.INSTANCE;
        var settings = SmpsSessionTestFixtures.settings();
        var writes = new SmpsSessionTestFixtures.RecordingObserver();
        var session = new SmpsDriverSession(settings, physical, writes,
                new SmpsSessionProfileFingerprint("sonic2", 7, physical.identity(), settings, stateful.identity()),
                new SmpsDriverSessionConfiguration(stateful));
        byte[] sample = new byte[8192];
        for (int index = 0; index < sample.length; index++) sample[index] = (byte) (index * 37);
        DacData dac = new DacData(Map.of(1, sample), Map.of(0x81, new DacData.DacEntry(1, 4)), 295);

        // S2 little-endian header, DAC+FM1, divider 1 and overflow tempo FF.
        // Real stream units select voice zero and sustain notes for 64 ticks.
        byte[] program = new byte[0x140];
        program[0] = 0;
        program[1] = 1; // voice table 0100h
        program[2] = 2;
        program[4] = 1;
        program[5] = (byte) 0xFF;
        program[6] = 0x70;
        program[10] = (byte) 0x80;
        program[13] = 2; // FM1 attenuation
        program[0x70] = (byte) 0x81;
        program[0x71] = 0x40;
        program[0x72] = (byte) 0xF2;
        program[0x80] = (byte) 0xEF;
        program[0x81] = 0;
        program[0x82] = (byte) 0xA0;
        program[0x83] = 0x40;
        program[0x84] = (byte) 0xF2;
        System.arraycopy(VOICE, 0, program, 0x100, VOICE.length);
        var data = new Sonic2SmpsData(program, 0);
        data.setId(0x31);
        var source = SmpsSourceDescriptor.baseMusic(7, data, program.length, Arrays.hashCode(program));
        var config = Sonic2SmpsSequencerConfig.CONFIG;
        SmpsDriver detached = new SmpsDriver();
        MusicRestoreSink restore = () -> { };
        var sequencer = new SmpsSequencer(data, dac, detached, detached, restore, config, source,
                SmpsSequencer.SourceDescriptorTrust.PRECOMPUTED_IMMUTABLE);
        var entry = new SmpsDriverSnapshot.SequencerEntry(false, source,
                SmpsSequencer.SourceDescriptorTrust.PRECOMPUTED_IMMUTABLE,
                null, data, dac, restore, config, sequencer.captureSnapshot());
        session.install();
        session.requireNativePauseSupported();
        session.queueActivation(new PreparedSmpsMusicActivation(new SmpsMusicActivation(source, 2), entry,
                SmpsLogicalTransitionPolicies.forConfig(config), new SmpsDacSelection(source, dac)));
        session.serviceForward();
        session.renderFrames(new short[128], 0, 64);
        assertEquals(0x40, fmDuration(session));
        return new Fixture(session, writes, dac);
    }

    private record Fixture(SmpsDriverSession session, SmpsSessionTestFixtures.RecordingObserver writes,
                           DacData dac) implements AutoCloseable {
        @Override
        public void close() {
            session.close();
        }
    }
}
