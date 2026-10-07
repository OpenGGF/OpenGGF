package com.openggf.tools.challenge;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestChallengeWorker {
    private static final long GENERATION = 7;

    @Test
    void preparationAndStartDoNotAdvanceAndEachHeldSampleHasOneStrictStep() throws Exception {
        Playback session = new Playback();
        ByteArrayOutputStream output = serve(session,
                command(ChallengeProtocol.START, 0, 0),
                command(ChallengeProtocol.STEP, 1, 0x28),
                command(ChallengeProtocol.STEP, 2, 0x28),
                command(ChallengeProtocol.CLOSE, 3, 0));
        assertEquals(List.of(0x28, 0x28), session.steps);
        assertEquals(2, session.initials);
        DataInputStream packets = new DataInputStream(new ByteArrayInputStream(output.toByteArray()));
        assertEquals(0, ChallengeProtocol.readFrame(packets).sequence());
        assertEquals(0, ChallengeProtocol.readFrame(packets).sequence());
        assertEquals(1, ChallengeProtocol.readFrame(packets).sequence());
        assertEquals(2, ChallengeProtocol.readFrame(packets).sequence());
        assertEquals(0, packets.available());
    }

    @Test
    void staleGenerationAndStepBeforeStartNeverReachProduction() {
        Playback session = new Playback();
        assertThrows(IOException.class, () -> serve(session,
                new ChallengeProtocol.Command(ChallengeProtocol.START, GENERATION + 1, 0, 0)));
        assertThrows(IOException.class, () -> serve(session, command(ChallengeProtocol.STEP, 1, 8)));
        assertTrue(session.steps.isEmpty());
    }

    @Test
    void duplicateOrdinalStopsBeforeTheSecondMutation() {
        Playback session = new Playback();
        assertThrows(IOException.class, () -> serve(session,
                command(ChallengeProtocol.START, 0, 0),
                command(ChallengeProtocol.STEP, 1, 8),
                command(ChallengeProtocol.STEP, 1, 0x20)));
        assertEquals(List.of(8), session.steps);
    }

    @Test
    void restartCommandAndSkippedOrdinalAreRejected() {
        Playback session = new Playback();
        assertThrows(IOException.class, () -> serve(session,
                command(ChallengeProtocol.START, 0, 0), command(ChallengeProtocol.START, 0, 0)));
        assertThrows(IOException.class, () -> serve(session,
                command(ChallengeProtocol.START, 0, 0), command(ChallengeProtocol.STEP, 2, 8)));
        assertTrue(session.steps.isEmpty());
    }

    @Test
    void closeBeforeStartAndParentEofLeavePreparationUnadvanced() throws Exception {
        Playback session = new Playback();
        serve(session, command(ChallengeProtocol.CLOSE, 1, 0));
        serve(session);
        assertTrue(session.steps.isEmpty());
    }

    @Test
    void productionFaultReportsItsOrdinalAndDoesNotAdmitTheNextSample() throws Exception {
        ByteArrayOutputStream commands = new ByteArrayOutputStream();
        DataOutputStream input = new DataOutputStream(commands);
        ChallengeProtocol.writeCommand(input, command(ChallengeProtocol.START, 0, 0));
        ChallengeProtocol.writeCommand(input, command(ChallengeProtocol.STEP, 1, 8));
        ChallengeProtocol.writeCommand(input, command(ChallengeProtocol.STEP, 2, 0x20));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Playback session = new Playback() {
            @Override public ChallengeProtocol.Frame step(long generation, long sequence, int heldMask) {
                steps.add(heldMask);
                throw new IllegalStateException("Planned production fault");
            }
        };
        assertThrows(IOException.class, () -> ChallengeWorker.serve(
                new DataInputStream(new ByteArrayInputStream(commands.toByteArray())),
                new DataOutputStream(bytes), GENERATION, session));
        DataInputStream output = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        ChallengeProtocol.readFrame(output);
        ChallengeProtocol.readFrame(output);
        IOException error = assertThrows(IOException.class, () -> ChallengeProtocol.readFrame(output));
        assertTrue(error.getMessage().contains("7:1"));
        assertTrue(error.getMessage().contains("Planned production fault"));
        assertEquals(List.of(8), session.steps);
        assertEquals(0, output.available());
    }

    @Test
    void workerLaunchAcceptsOnlyWhitelistedAbsoluteInputs() {
        var options = ChallengeWorker.Options.parse(new String[] {
                "--game", "s3k", "--rom", "/tmp/user-supplied.gen", "--generation", "7"});
        assertEquals(GENERATION, options.generation());
        assertEquals("s3k", options.game());
        assertThrows(IllegalArgumentException.class, () -> ChallengeWorker.Options.parse(new String[] {
                "--game", "s1", "--rom", "relative.gen", "--generation", "1"}));
        assertThrows(IllegalArgumentException.class, () -> ChallengeWorker.Options.parse(new String[] {
                "--game", "s1", "--rom", "/tmp/a.gen", "--generation", "0"}));
        assertThrows(IllegalArgumentException.class, () -> ChallengeWorker.Options.parse(new String[] {
                "--game", "s1", "--game", "s2", "--generation", "1"}));
        assertThrows(IllegalArgumentException.class, () -> ChallengeWorker.Options.parse(new String[] {
                "--game", "custom", "--rom", "/tmp/a.gen", "--generation", "1"}));
        assertThrows(IllegalArgumentException.class, () -> ChallengeWorker.Options.parse(new String[] {
                "--game", "s1", "--rom", "/tmp/a.gen", "--exec", "anything"}));
    }

    private static ChallengeProtocol.Command command(byte kind, long sequence, int held) {
        return new ChallengeProtocol.Command(kind, GENERATION, sequence, held);
    }

    private static ByteArrayOutputStream serve(Playback session, ChallengeProtocol.Command... commands)
            throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream input = new DataOutputStream(bytes);
        for (var command : commands) ChallengeProtocol.writeCommand(input, command);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ChallengeWorker.serve(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())),
                new DataOutputStream(output), GENERATION, session);
        return output;
    }

    private static class Playback implements ChallengeWorker.Playback {
        final List<Integer> steps = new ArrayList<>();
        int initials;
        @Override public ChallengeProtocol.Frame initial(long generation, long sequence) {
            initials++;
            return frame(generation, sequence);
        }
        @Override public ChallengeProtocol.Frame step(long generation, long sequence, int heldMask) {
            steps.add(heldMask);
            return frame(generation, sequence);
        }
        private ChallengeProtocol.Frame frame(long generation, long sequence) {
            return new ChallengeProtocol.Frame(generation, sequence,
                    new byte[ChallengeProtocol.RGBA_BYTES], new short[0], "LEVEL", 0, 0, 0, 0, 0, -1);
        }
        @Override public void close() { }
    }
}
