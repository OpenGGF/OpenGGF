package com.openggf.tools.challenge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

/** Owns the fixed, one-world checkpoint diagnostic JVM; no checkpoint transport is exposed. */
final class ChallengeDiagnosticProcess implements AutoCloseable {
    private final Path directory;
    private final Process process;
    private final Thread shutdownHook;
    private boolean closed;

    ChallengeDiagnosticProcess(Path rom, Path output) throws IOException {
        ChallengeRoms.validate("s1", rom);
        Path parent = Path.of("target", "challenge-workers").toAbsolutePath();
        Files.createDirectories(parent);
        directory = Files.createTempDirectory(parent, "rewind-");
        ProcessBuilder builder = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-Xmx512m",
                "-cp", ProcessGameEndpoint.workerClasspath(),
                "com.openggf.tools.challenge.WorkerReplayDiagnostic", "--game", "s1",
                "--rom", rom.toAbsolutePath().toString(), "--output", output.toAbsolutePath().toString());
        for (String key : new String[] {"JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS"})
            builder.environment().remove(key);
        builder.directory(directory.toFile()).inheritIO();
        try {
            process = builder.start();
        } catch (IOException failure) {
            Files.deleteIfExists(directory);
            throw failure;
        }
        shutdownHook = new Thread(this::close, "challenge-rewind-close-" + process.pid());
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    boolean alive() { return process.isAlive(); }

    void requireSuccess() throws IOException {
        if (process.isAlive() || process.exitValue() != 0)
            throw new IOException("Native checkpoint diagnostic did not complete successfully");
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        if (Thread.currentThread() != shutdownHook) {
            try { Runtime.getRuntime().removeShutdownHook(shutdownHook); }
            catch (IllegalStateException shuttingDown) { }
        }
        boolean interrupted = Thread.interrupted();
        try {
            if (process.isAlive()) process.destroy();
            if (!process.waitFor(1500, TimeUnit.MILLISECONDS)) process.destroyForcibly();
            process.waitFor(1500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException failure) {
            interrupted = true;
            process.destroyForcibly();
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                try { process.waitFor(1500, TimeUnit.MILLISECONDS); }
                catch (InterruptedException failure) { interrupted = true; }
            }
            if (interrupted) Thread.currentThread().interrupt();
        }
        if (process.isAlive())
            throw new IllegalStateException("Diagnostic teardown pending: " + process.pid());
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        } catch (IOException failure) {
            throw new IllegalStateException("Diagnostic directory cleanup pending: " + directory, failure);
        }
    }
}
