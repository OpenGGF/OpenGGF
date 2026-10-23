package openggf.racing.server.verifier;

import openggf.racing.server.config.SecretFile;
import com.openggf.net.identity.PlayerIdentity;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** CLI entry point for the operator-supplied-ROM replay verification worker. */
public final class VerifierMain {
    private VerifierMain() { }

    public static void main(String[] args) throws Exception {
        Arguments options = Arguments.parse(args);
        PlayerIdentity identity = PlayerIdentity.loadOrCreate(options.dataDir());
        Map<String, Path> roms = new LinkedHashMap<>();
        for (Path rom : options.roms()) {
            roms.put(AttemptReplayHarness.fingerprintForRom(rom), rom);
        }
        HttpMasterApi api = new HttpMasterApi(options.master(),
                options.registrationToken(), options.trustInsecure());
        VerifierWorker worker = new VerifierWorker(api, (recording, fingerprint) -> {
            Path rom = roms.get(fingerprint);
            return rom == null
                    ? new AttemptReplayHarness.Result(false, -1, -1, -1,
                    "", 0, "fingerprint mismatch")
                    : AttemptReplayHarness.replay(recording, rom);
        }, identity);
        VerifierShutdown shutdown = new VerifierShutdown();
        Thread owner = Thread.currentThread();
        Thread hook = new Thread(() -> shutdown.drain(owner,
                Duration.ofSeconds(options.shutdownGraceSeconds())), "verifier-shutdown");
        Runtime.getRuntime().addShutdownHook(hook);
        try {
            VerifierLoop.Cycle cycle = registeringCycle(
                    () -> api.register(identity.publicKeyEncoded(), roms.keySet()),
                    worker::pollOnce, shutdown::isStopping);
            serve(options.once(), cycle, shutdown::pause, shutdown::isStopping);
        } finally {
            shutdown.finished();
            try {
                Runtime.getRuntime().removeShutdownHook(hook);
            } catch (IllegalStateException shuttingDown) {
                // The running hook is already awaiting finished(), and owns JVM shutdown.
            }
        }
    }

    /** Runs one cycle for {@code --once}, otherwise the surviving poll loop. */
    static void serve(boolean once, VerifierLoop.Cycle cycle, VerifierLoop.Sleeper sleeper) {
        serve(once, cycle, sleeper, () -> false);
    }

    private static void serve(boolean once, VerifierLoop.Cycle cycle,
                              VerifierLoop.Sleeper sleeper, BooleanSupplier stopping) {
        if (once) {
            // One-shot runs keep failing loudly: a failed cycle ends the process.
            if (!stopping.getAsBoolean()) cycle.pollOnce();
            return;
        }
        new VerifierLoop(cycle, sleeper, stopping).run();
    }

    /** Initial registration belongs inside the same retry boundary as later HTTP calls. */
    static VerifierLoop.Cycle registeringCycle(Runnable register, VerifierLoop.Cycle cycle,
                                               BooleanSupplier stopping) {
        return new VerifierLoop.Cycle() {
            private boolean registered;

            @Override
            public boolean pollOnce() {
                if (!registered) {
                    register.run();
                    registered = true;
                }
                // Shutdown may have arrived while registration was in flight.
                return !stopping.getAsBoolean() && cycle.pollOnce();
            }
        };
    }

    record Arguments(String master, String registrationToken,
                             List<Path> roms, Path dataDir,
                             boolean trustInsecure, boolean once, long shutdownGraceSeconds) {
        static Arguments parse(String[] args) throws IOException {
            String master = null;
            String registration = null;
            Path registrationFile = null;
            Path data = Path.of("verifier-data");
            List<Path> roms = new ArrayList<>();
            boolean insecure = false;
            boolean once = false;
            long shutdownGraceSeconds = 30;
            for (int index = 0; index < args.length; index++) {
                switch (args[index]) {
                    case "--master" -> master = value(args, ++index, "--master");
                    case "--registration-token" -> registration = value(
                            args, ++index, "--registration-token");
                    case "--registration-token-file" -> registrationFile = Path.of(value(
                            args, ++index, "--registration-token-file"));
                    case "--rom" -> roms.add(Path.of(value(args, ++index, "--rom")));
                    case "--data" -> data = Path.of(value(args, ++index, "--data"));
                    case "--trust-insecure" -> insecure = true;
                    case "--once" -> once = true;
                    case "--shutdown-grace-seconds" -> shutdownGraceSeconds = Long.parseLong(
                            value(args, ++index, "--shutdown-grace-seconds"));
                    default -> throw new IllegalArgumentException(
                            "unknown argument " + args[index]);
                }
            }
            if (registration != null && registrationFile != null) {
                throw new IllegalArgumentException(
                        "choose --registration-token or --registration-token-file, not both");
            }
            if (registrationFile != null) registration = SecretFile.read(registrationFile);
            if (master == null || master.isBlank() || registration == null
                    || registration.isBlank() || roms.isEmpty()) {
                throw new IllegalArgumentException(
                        "required: --master URL (--registration-token TOKEN or "
                                + "--registration-token-file PATH) --rom PATH");
            }
            if (shutdownGraceSeconds < 1 || shutdownGraceSeconds > 300) {
                throw new IllegalArgumentException("--shutdown-grace-seconds must be 1 to 300");
            }
            SecretFile.requireToken(registration);
            return new Arguments(master.replaceAll("/+$", ""), registration,
                    List.copyOf(roms), data, insecure, once, shutdownGraceSeconds);
        }

        private static String value(String[] args, int index, String option) {
            if (index >= args.length) {
                throw new IllegalArgumentException("missing value for " + option);
            }
            return args[index];
        }
    }
}
