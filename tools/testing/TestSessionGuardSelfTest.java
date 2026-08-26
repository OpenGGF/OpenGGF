import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/** Standalone contract checks for the Maven lifecycle session guard. */
public final class TestSessionGuardSelfTest {
    private static final String RUN_ID = "20260823T120000Z-p4242-a1b2c3";
    private static final String COMMAND_HASH =
            "84dd70de6e6483ef1e7b22c39c49846d53957e1e8a40eae56a870f04e7190487";

    private TestSessionGuardSelfTest() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("usage: TestSessionGuardSelfTest <test-root>");
        }
        Path root = Files.createDirectories(Path.of(args[0]).toAbsolutePath().normalize());
        assertReservedPropertyRejected(root);
        Path worktree = Path.of(System.getProperty("user.dir")).toRealPath();
        Path lease = Files.writeString(root.resolve("lease.lock"), "lease\n",
                StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        Path capability = root.resolve("capability");
        writeCapability(capability, worktree, lease, COMMAND_HASH);

        Path runningManifest = root.resolve("running-manifest.json");
        writeManifest(runningManifest, capability, "RUNNING", worktree, lease, COMMAND_HASH);

        assertRejected("missing capability", runningManifest, null, worktree, lease,
                COMMAND_HASH, "validate");
        assertRejected("wrong worktree", runningManifest, capability,
                root.resolve("different-worktree"), lease, COMMAND_HASH, "validate");
        assertRejected("wrong command hash", runningManifest, capability, worktree, lease,
                "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff", "validate");

        Path completedManifest = root.resolve("completed-manifest.json");
        writeManifest(completedManifest, capability, "PASSED", worktree, lease, COMMAND_HASH);
        assertRejected("non-running state", completedManifest, capability, worktree, lease,
                COMMAND_HASH, "validate");

        assertAccepted("valid pre-clean", runningManifest, capability, worktree, lease,
                COMMAND_HASH, "pre-clean");
        assertAccepted("valid validate", runningManifest, capability, worktree, lease,
                COMMAND_HASH, "validate");
        System.out.println("TestSessionGuardSelfTest PASS");
    }

    private static void assertReservedPropertyRejected(Path root) throws Exception {
        Path outputRoot = root.resolve("reserved-output");
        Path lockRoot = root.resolve("reserved-lock");
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add("TestSessionCoordinator");
        command.add("--lock-root");
        command.add(lockRoot.toString());
        command.add("--");
        command.add("java");
        command.add("-Dopenggf.build.directory=/shared/accidental-output");
        command.add("-version");
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().put("OPENGGF_TEST_ROOT", outputRoot.toString());
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        if (exitCode == 0 || !output.contains("reserved session property")) {
            throw new AssertionError("coordinator accepted a caller-owned session path:\n" + output);
        }
    }

    private static void assertRejected(String name, Path manifest, Path capability,
                                       Path worktree, Path lease, String commandHash,
                                       String phase) throws Exception {
        Result result = runGuard(manifest, capability, worktree, lease, commandHash, phase);
        if (result.exitCode == 0) {
            throw new AssertionError(name + " unexpectedly accepted:\n" + result.output);
        }
        if (!result.output.contains("session guard rejected:")) {
            throw new AssertionError(name + " lacked an actionable rejection:\n" + result.output);
        }
    }

    private static void assertAccepted(String name, Path manifest, Path capability,
                                       Path worktree, Path lease, String commandHash,
                                       String phase) throws Exception {
        Result result = runGuard(manifest, capability, worktree, lease, commandHash, phase);
        if (result.exitCode != 0) {
            throw new AssertionError(name + " rejected with " + result.exitCode + ":\n"
                    + result.output);
        }
    }

    private static Result runGuard(Path manifest, Path capability, Path worktree, Path lease,
                                   String commandHash, String phase) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add("-Dopenggf.session.manifest=" + manifest);
        if (capability != null) {
            command.add("-Dopenggf.session.capability=" + capability);
        }
        command.add("-Dopenggf.session.run-id=" + RUN_ID);
        command.add("-Dopenggf.session.command-hash=" + commandHash);
        command.add("-Dopenggf.session.worktree=" + worktree.toAbsolutePath().normalize());
        command.add("-Dopenggf.session.lease-path=" + lease.toAbsolutePath().normalize());
        command.add("-Dopenggf.session.allowed-phases=pre-clean,validate");
        command.add("TestSessionCoordinator");
        command.add("--guard");
        command.add(phase);
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new Result(process.waitFor(), output);
    }

    private static void writeCapability(Path path, Path worktree, Path lease,
                                        String commandHash) throws IOException {
        Files.writeString(path,
                "run_id=" + RUN_ID + "\n"
                        + "command_hash=" + commandHash + "\n"
                        + "worktree=" + worktree + "\n"
                        + "lease_path=" + lease.toAbsolutePath().normalize() + "\n"
                        + "allowed_phases=pre-clean,validate\n",
                StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }

    private static void writeManifest(Path path, Path capability, String state, Path worktree,
                                      Path lease, String commandHash) throws IOException {
        Files.writeString(path, "{\n"
                        + "  \"run_id\": \"" + RUN_ID + "\",\n"
                        + "  \"state\": \"" + state + "\",\n"
                        + "  \"manifest\": \"" + json(path.toAbsolutePath().normalize().toString()) + "\",\n"
                        + "  \"capability\": \"" + json(capability.toAbsolutePath().normalize().toString()) + "\",\n"
                        + "  \"worktree\": \"" + json(worktree.toString()) + "\",\n"
                        + "  \"lease_path\": \"" + json(lease.toAbsolutePath().normalize().toString()) + "\",\n"
                        + "  \"command_hash\": \"" + commandHash + "\",\n"
                        + "  \"allowed_phases\": \"pre-clean,validate\"\n"
                        + "}\n",
                StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record Result(int exitCode, String output) {
    }
}
