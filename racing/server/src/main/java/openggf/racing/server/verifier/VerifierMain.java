package openggf.racing.server.verifier;

import openggf.racing.server.verifier.AttemptReplayHarness;
import com.openggf.net.identity.PlayerIdentity;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        api.register(identity.publicKeyEncoded(), roms.keySet());
        VerifierWorker worker = new VerifierWorker(api, (recording, fingerprint) -> {
            Path rom = roms.get(fingerprint);
            return rom == null
                    ? new AttemptReplayHarness.Result(false, -1, -1, -1,
                    "", 0, "fingerprint mismatch")
                    : AttemptReplayHarness.replay(recording, rom);
        }, identity);
        serve(options.once(), worker::pollOnce, Thread::sleep);
    }

    /** Runs one cycle for {@code --once}, otherwise the surviving poll loop. */
    static void serve(boolean once, VerifierLoop.Cycle cycle, VerifierLoop.Sleeper sleeper) {
        if (once) {
            // One-shot runs keep failing loudly: a failed cycle ends the process.
            cycle.pollOnce();
            return;
        }
        new VerifierLoop(cycle, sleeper).run();
    }

    private record Arguments(String master, String registrationToken,
                             List<Path> roms, Path dataDir,
                             boolean trustInsecure, boolean once) {
        private static Arguments parse(String[] args) {
            String master = null;
            String registration = null;
            Path data = Path.of("verifier-data");
            List<Path> roms = new ArrayList<>();
            boolean insecure = false;
            boolean once = false;
            for (int index = 0; index < args.length; index++) {
                switch (args[index]) {
                    case "--master" -> master = value(args, ++index, "--master");
                    case "--registration-token" -> registration = value(
                            args, ++index, "--registration-token");
                    case "--rom" -> roms.add(Path.of(value(args, ++index, "--rom")));
                    case "--data" -> data = Path.of(value(args, ++index, "--data"));
                    case "--trust-insecure" -> insecure = true;
                    case "--once" -> once = true;
                    default -> throw new IllegalArgumentException(
                            "unknown argument " + args[index]);
                }
            }
            if (master == null || registration == null || roms.isEmpty()) {
                throw new IllegalArgumentException(
                        "required: --master URL --registration-token TOKEN --rom PATH");
            }
            return new Arguments(master.replaceAll("/+$", ""), registration,
                    List.copyOf(roms), data, insecure, once);
        }

        private static String value(String[] args, int index, String option) {
            if (index >= args.length) {
                throw new IllegalArgumentException("missing value for " + option);
            }
            return args[index];
        }
    }
}
