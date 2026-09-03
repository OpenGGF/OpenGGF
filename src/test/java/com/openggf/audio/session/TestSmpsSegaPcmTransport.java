package com.openggf.audio.session;

import com.openggf.audio.synth.ChipWriteObserver;
import com.openggf.game.sonic1.audio.Sonic1SmpsCompatibilityPolicy;
import com.openggf.game.sonic2.audio.Sonic2SmpsCompatibilityPolicy;
import com.openggf.game.sonic3k.audio.Sonic3kSmpsConstants;
import com.openggf.game.sonic3k.audio.Sonic3kSmpsPhysicalPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The driver-owned SEGA PCM transport: the ROM's loop arithmetic, the write
 * program it produces, and the session behaviour of running it.
 */
class TestSmpsSegaPcmTransport {

    private static final double OUTPUT_RATE = 44_100.0;

    @Test
    void s3kTransportCarriesTheRomsOwnLoopArithmetic() {
        SmpsSegaPcmTransport transport = s3kTransport();

        // pcmLoopCounterBase(sampleRate, 105) with Z80_Clock = 3579545
        // (sonic3k.macros.asm:270-271, sonic3k.constants.asm:202-204):
        // 1 + (3579545/14434 - 105 + 6)/13 = 12, and the loop then costs
        // 105 + 13 * 11 = 248 Z80 cycles per sample byte.
        assertEquals(3_579_545, SmpsSegaPcmTransport.Z80_CLOCK_HZ);
        assertEquals(Sonic3kSmpsConstants.SEGA_SOUND_SAMPLE_RATE,
                transport.sampleRate());
        assertEquals(12, transport.loopCounter());
        assertEquals(248, transport.z80CyclesPerByte());
    }

    @Test
    void s3kProgramIsTheDacEnableThenOneWritePerByteThenTheDacDisable() {
        SmpsSegaPcmTransport transport = s3kTransport();
        byte[] pcm = {0x7F, (byte) 0x80, 0x00};

        List<SmpsChipWrite> writes = transport.program(pcm).writes();

        assertEquals(List.of(
                new SmpsChipWrite.Ym2612(0, 0x2B, 0x80),
                new SmpsChipWrite.Ym2612(0, 0x2A, 0x7F),
                new SmpsChipWrite.Ym2612(0, 0x2A, 0x80),
                new SmpsChipWrite.Ym2612(0, 0x2A, 0x00),
                new SmpsChipWrite.Ym2612(0, 0x2B, 0x00)),
                writes);
    }

    @Test
    void s1AndS2KeepTheirOwnSegaMechanism() {
        // S2's zPlaySegaSound (s2.sounddriver.asm:1603-1652) and S1's
        // zPlay_SegaPCM (sound/z80.asm:187-206) are the same shape, but
        // neither policy claims the transport yet, so their SEGA screens are
        // untouched by this vocabulary.
        assertEquals(Optional.empty(),
                Sonic1SmpsCompatibilityPolicy.INSTANCE.segaPcmTransport());
        assertEquals(Optional.empty(),
                Sonic2SmpsCompatibilityPolicy.INSTANCE.segaPcmTransport());
        assertEquals(Optional.empty(),
                LegacyCompatibilitySmpsPhysicalPolicy.INSTANCE
                        .segaPcmTransport());
    }

    @Test
    void aProfileWithoutATransportRefusesToRunOne() {
        SmpsDriverSession session = SmpsSessionTestSupport.installed(
                OUTPUT_RATE);

        assertFalse(session.ownsSegaPcmTransport());
        assertThrows(IllegalStateException.class,
                () -> session.beginSegaPcmTransport(new byte[] {1, 2}));
    }

    @Test
    void theTransportMasksEveryServiceItSpans() {
        RecordingSession recording = s3kSession();
        recording.session.install();
        recording.writes.clear();

        recording.session.beginSegaPcmTransport(new byte[] {0x10, 0x20});

        assertTrue(recording.session.segaPcmTransportActive());
        assertEquals(List.of("YM:0:2B:80"), recording.writes.events());
        assertEquals(SmpsServiceOutcome.SEGA_PCM_TRANSPORT,
                recording.session.serviceForward());
        assertEquals(SmpsServiceOutcome.SEGA_PCM_TRANSPORT,
                recording.session.serviceForward());
    }

    @Test
    void renderingSendsOneByteEveryRomLoopAndLeavesThroughTheDacDisable() {
        RecordingSession recording = s3kSession();
        recording.session.install();
        byte[] pcm = {0x11, 0x22, 0x33};
        recording.session.beginSegaPcmTransport(pcm);
        recording.writes.clear();

        // 248 Z80 cycles per byte is a little over three output frames at
        // 44.1 kHz, so a hundred frames is far more than the sample needs.
        short[] target = new short[200];
        assertEquals(100, recording.session.renderFrames(target, 0, 100));

        assertFalse(recording.session.segaPcmTransportActive());
        assertEquals(List.of("YM:0:2A:11", "YM:0:2A:22", "YM:0:2A:33",
                "YM:0:2B:00"), recording.writes.events());
    }

    @Test
    void bytesArriveAtTheRomsCadenceRatherThanAllAtOnce() {
        RecordingSession recording = s3kSession();
        recording.session.install();
        byte[] pcm = new byte[64];
        recording.session.beginSegaPcmTransport(pcm);
        recording.writes.clear();

        short[] target = new short[64];
        recording.session.renderFrames(target, 0, 32);

        // The loop sends its first byte on entry, then one every 248 Z80
        // cycles; 32 output frames is 32 * 3579545 / 44100 = 2597 cycles,
        // which covers ten more.
        long sampleWrites = recording.writes.events().stream()
                .filter(event -> event.startsWith("YM:0:2A:"))
                .count();
        assertEquals(11, sampleWrites);
        assertTrue(recording.session.segaPcmTransportActive());
    }

    @Test
    void aStopRequestEndsTheLoopAtTheNextByteBoundary() {
        RecordingSession recording = s3kSession();
        recording.session.install();
        recording.session.beginSegaPcmTransport(new byte[64]);
        short[] target = new short[64];
        recording.session.renderFrames(target, 0, 32);
        recording.writes.clear();

        recording.session.requestSegaPcmTransportStop();
        recording.session.renderFrames(target, 0, 32);

        assertFalse(recording.session.segaPcmTransportActive());
        assertEquals(List.of("YM:0:2B:00"), recording.writes.events());
    }

    @Test
    void anInFlightTransportSurvivesSnapshotAndRestore() {
        RecordingSession recording = s3kSession();
        recording.session.install();
        byte[] pcm = {0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48};
        recording.session.beginSegaPcmTransport(pcm);
        short[] target = new short[64];
        recording.session.renderFrames(target, 0, 8);
        SmpsDriverSessionSnapshot snapshot =
                recording.session.captureSnapshot();
        SmpsSegaPcmTransportSnapshot transport = snapshot.segaPcmTransport();

        assertEquals(3, transport.cursor());
        assertFalse(transport.stopRequested());
        assertArrayEqualsBytes(pcm, transport.pcm());

        recording.session.commitRestore(recording.session.prepareRestore(
                snapshot, recording.session.captureLogicalSnapshot(),
                ignored -> null));
        recording.writes.clear();
        recording.session.renderFrames(target, 0, 32);

        assertFalse(recording.session.segaPcmTransportActive());
        assertEquals(List.of("YM:0:2A:44", "YM:0:2A:45", "YM:0:2A:46",
                "YM:0:2A:47", "YM:0:2A:48", "YM:0:2B:00"),
                recording.writes.events());
    }

    @Test
    void rollingBackALiveMutationRestoresTheLoopPosition() {
        RecordingSession recording = s3kSession();
        recording.session.install();
        recording.session.beginSegaPcmTransport(new byte[] {1, 2, 3, 4, 5, 6});
        short[] target = new short[64];
        recording.session.renderFrames(target, 0, 8);

        SmpsDriverSession.LiveMutationToken token =
                recording.session.captureLiveMutation();
        recording.session.renderFrames(target, 0, 8);
        recording.session.rollbackLiveMutation(token);

        assertTrue(recording.session.segaPcmTransportActive());
        assertEquals(3, recording.session.captureSnapshot()
                .segaPcmTransport().cursor());
    }

    private static void assertArrayEqualsBytes(byte[] expected, byte[] actual) {
        assertEquals(expected.length, actual.length);
        for (int index = 0; index < expected.length; index++) {
            assertEquals(expected[index], actual[index],
                    "byte " + index);
        }
    }

    private static SmpsSegaPcmTransport s3kTransport() {
        return Sonic3kSmpsPhysicalPolicy.INSTANCE.segaPcmTransport()
                .orElseThrow();
    }

    private record RecordingSession(
            SmpsDriverSession session,
            SmpsSessionTestFixtures.RecordingObserver writes) {
    }

    private static RecordingSession s3kSession() {
        SmpsSessionTestFixtures.RecordingObserver writes =
                new SmpsSessionTestFixtures.RecordingObserver();
        SmpsPhysicalPolicy policy = Sonic3kSmpsPhysicalPolicy.INSTANCE;
        SmpsPhysicalDevice.Settings settings =
                new SmpsPhysicalDevice.Settings(OUTPUT_RATE, false, false);
        return new RecordingSession(new SmpsDriverSession(
                settings, policy, (ChipWriteObserver) writes,
                new SmpsSessionProfileFingerprint(
                        "s3k-transport-test", 1, policy.identity(), settings),
                SmpsDriverSessionConfiguration.DEFAULT), writes);
    }
}
