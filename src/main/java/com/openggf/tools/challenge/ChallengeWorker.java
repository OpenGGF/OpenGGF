package com.openggf.tools.challenge;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Internal child-process entry point; the host owns process creation and deadlines. */
public final class ChallengeWorker {
    private ChallengeWorker() { }

    public static void main(String[] args) {
        OutputStream transport = System.out;
        // Save the protocol pipe before any engine boot can print diagnostics.
        System.setOut(System.err);
        Options options;
        try {
            options = Options.parse(args);
        } catch (RuntimeException badArguments) {
            System.err.println("Challenge worker: " + badArguments.getMessage());
            System.exit(2);
            return;
        }
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(transport));
        try (WorkerGameSession session = WorkerGameSession.open(options.game(), options.rom())) {
            serve(new DataInputStream(new BufferedInputStream(System.in)), out,
                    options.generation(), session);
        } catch (Exception | LinkageError failure) {
            failure.printStackTrace(System.err);
            try {
                if (!(failure instanceof ReportedFailure)) {
                    ChallengeProtocol.writeError(out, options.generation(), 0, failure.getMessage());
                }
            }
            catch (IOException transportClosed) { /* The supervisor already closed the pipe. */ }
            System.exit(1);
        }
    }

    /** No worker tick is admitted before START; every accepted STEP has one strict ordinal. */
    static void serve(DataInputStream in, DataOutputStream out, long generation,
                      Playback session) throws IOException {
        ChallengeProtocol.writeFrame(out, session.initial(generation, 0));
        boolean started = false;
        long nextSequence = 1;
        while (true) {
            ChallengeProtocol.Command command;
            try { command = ChallengeProtocol.readCommand(in); }
            catch (EOFException disconnected) { return; }
            try {
                if (command.generation() != generation) throw new IOException("Stale worker generation");
                if (command.kind() == ChallengeProtocol.CLOSE) {
                    if (command.sequence() != nextSequence) throw new IOException("Out-of-order close");
                    return;
                }
                if (command.kind() == ChallengeProtocol.START) {
                    if (started || command.sequence() != 0) throw new IOException("Duplicate or out-of-order start");
                    started = true;
                    ChallengeProtocol.writeFrame(out, session.initial(generation, 0));
                } else {
                    if (!started) throw new IOException("Step arrived before the all-worker start barrier");
                    if (command.sequence() != nextSequence) throw new IOException("Duplicate or out-of-order step");
                    if (nextSequence == Long.MAX_VALUE) throw new IOException("Worker sequence exhausted");
                    ChallengeProtocol.writeFrame(out,
                            session.step(generation, nextSequence, command.heldMask()));
                    nextSequence++;
                }
            } catch (IOException | RuntimeException | LinkageError failure) {
                ChallengeProtocol.writeError(out, generation, command.sequence(), failure.getMessage());
                throw new ReportedFailure(failure);
            }
        }
    }

    interface Playback extends AutoCloseable {
        ChallengeProtocol.Frame initial(long generation, long sequence);
        ChallengeProtocol.Frame step(long generation, long sequence, int heldMask);
        @Override void close();
    }

    private static final class ReportedFailure extends IOException {
        private ReportedFailure(Throwable cause) { super(cause.getMessage(), cause); }
    }

    record Options(String game, Path rom, long generation) {
        static Options parse(String[] args) {
            if (args.length != 6) throw new IllegalArgumentException("Expected --game, --rom and --generation only");
            Map<String, String> values = new HashMap<>();
            for (int i = 0; i < args.length; i += 2) {
                if (!args[i].equals("--game") && !args[i].equals("--rom") && !args[i].equals("--generation")) {
                    throw new IllegalArgumentException("Unknown worker option");
                }
                if (values.putIfAbsent(args[i], args[i + 1]) != null) {
                    throw new IllegalArgumentException("Duplicate worker option");
                }
            }
            String game = values.get("--game");
            if (!"s1".equals(game) && !"s2".equals(game) && !"s3k".equals(game)) {
                throw new IllegalArgumentException("Game must be s1, s2 or s3k");
            }
            Path rom = Path.of(values.get("--rom"));
            if (!rom.isAbsolute()) throw new IllegalArgumentException("ROM path must be absolute");
            long generation = Long.parseLong(values.get("--generation"));
            if (generation <= 0) throw new IllegalArgumentException("Generation must be positive");
            return new Options(game, rom, generation);
        }
    }
}
