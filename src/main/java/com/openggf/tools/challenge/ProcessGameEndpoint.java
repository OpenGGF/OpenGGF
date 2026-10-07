package com.openggf.tools.challenge;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** One managed JVM, one production game, one request in flight; never a shell command. */
public final class ProcessGameEndpoint implements AutoCloseable {
    private final Process process;
    private final DataInputStream input;
    private final DataOutputStream output;
    private final ExecutorService io;
    private final long generation;
    private final Path directory;
    private final Thread shutdownHook;
    private final AtomicBoolean closed = new AtomicBoolean();
    private long sequence;
    private boolean started;

    public ProcessGameEndpoint(String game, Path rom, long generation) throws IOException {
        this(game, rom, generation, Runtime.getRuntime()::addShutdownHook);
    }
    ProcessGameEndpoint(String game, Path rom, long generation,
            java.util.function.Consumer<Thread> registerHook) throws IOException {
        if (!List.of("s1", "s2", "s3k").contains(game) || generation <= 0)
            throw new IllegalArgumentException("Invalid endpoint identity");
        this.generation = generation;
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = workerClasspath();
        Path parent = Path.of("target", "challenge-workers").toAbsolutePath();
        Files.createDirectories(parent);
        directory = Files.createTempDirectory(parent, "world-");
        ProcessBuilder builder = new ProcessBuilder(java, "-Xmx512m", "-cp", classpath,
                "com.openggf.tools.challenge.ChallengeWorker", "--game", game, "--rom",
                rom.toAbsolutePath().toString(), "--generation", Long.toString(generation));
        // Engine debug/config environment is not an alternate launch surface.
        builder.environment().remove("JAVA_TOOL_OPTIONS");
        builder.environment().remove("JDK_JAVA_OPTIONS");
        builder.environment().remove("_JAVA_OPTIONS");
        builder.directory(directory.toFile());
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        try {
            process = builder.start();
        } catch (IOException failure) {
            Files.deleteIfExists(directory);
            throw failure;
        }
        ExecutorService acquiredExecutor = null;
        Thread acquiredHook = null;
        try {
            input = new DataInputStream(new BufferedInputStream(process.getInputStream(), 1 << 19));
            output = new DataOutputStream(new BufferedOutputStream(process.getOutputStream()));
            io = acquiredExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "challenge-" + game + "-" + process.pid());
                t.setDaemon(true);
                return t;
            });
            shutdownHook = new Thread(this::close, "challenge-close-" + process.pid());
            acquiredHook = shutdownHook;
            registerHook.accept(shutdownHook);
        } catch (RuntimeException | Error failure) {
            ChallengeProcessAcquisition.unwind(failure, process, directory, acquiredHook,
                    acquiredExecutor, process.getInputStream(), process.getOutputStream(), process.getErrorStream());
            throw failure;
        }
    }

    static String workerClasspath() {
        return Arrays.stream(System.getProperty("java.class.path").split(Pattern.quote(File.pathSeparator)))
                .map(entry -> Path.of(entry.isEmpty() ? "." : entry).toAbsolutePath().toString())
                .collect(Collectors.joining(File.pathSeparator));
    }

    public CompletableFuture<ChallengeProtocol.Frame> prepare() {
        return submit(() -> checked(ChallengeProtocol.readFrame(input), 0));
    }
    public CompletableFuture<ChallengeProtocol.Frame> start() {
        return submit(() -> {
            if (started)
                throw new IOException("Worker already started");
            ChallengeProtocol.writeCommand(
                    output, new ChallengeProtocol.Command(ChallengeProtocol.START, generation, 0, 0));
            var frame = checked(ChallengeProtocol.readFrame(input), 0);
            started = true;
            return frame;
        });
    }
    public CompletableFuture<ChallengeProtocol.Frame> step(int heldMask) {
        return submit(() -> {
            if (!started)
                throw new IOException("Worker has not joined the start barrier");
            long next = sequence + 1;
            ChallengeProtocol.writeCommand(output,
                    new ChallengeProtocol.Command(ChallengeProtocol.STEP, generation, next, heldMask));
            var frame = checked(ChallengeProtocol.readFrame(input), next);
            sequence = next;
            return frame;
        });
    }
    private ChallengeProtocol.Frame checked(ChallengeProtocol.Frame frame, long expectedSequence)
            throws IOException {
        requireIdentity(frame, generation, expectedSequence);
        return frame;
    }
    static void requireIdentity(ChallengeProtocol.Frame frame, long generation, long sequence)
            throws IOException {
        if (frame.generation() != generation || frame.sequence() != sequence)
            throw new IOException("Stale or out-of-order worker media");
    }
    private synchronized<T> CompletableFuture<T> submit(Callable<T> action) {
        if (closed.get())
            return CompletableFuture.failedFuture(new IOException("Worker closed"));
        // Caller owns admission: a queued extra step is rejected, never buffered for catch-up.
        if (inFlight != null && !inFlight.isDone())
            return CompletableFuture.failedFuture(new IOException("Worker request already in flight"));
        CompletableFuture<T> future = new CompletableFuture<>();
        inFlight = future;
        io.execute(() -> {
            try {
                future.complete(action.call());
            } catch (Throwable failure) {
                future.completeExceptionally(failure);
            }
        });
        return future;
    }
    private CompletableFuture<?> inFlight;
    public long pid() {
        return process.pid();
    }
    public boolean alive() {
        return process.isAlive();
    }
    void crashForDiagnostic() { process.destroyForcibly(); }
    public long generation() {
        return generation;
    }

    public static <T> T await(CompletableFuture<T> future, Duration timeout) throws IOException {
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted worker request", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IOException("Worker request failed: "
                            + (e.getCause() == null ? e.getMessage() : e.getCause().getMessage()),
                    e);
        }
    }
    @Override
    public synchronized void close() {
        if (!closed.compareAndSet(false, true))
            return;
        if (Thread.currentThread()!=shutdownHook) {
            try { Runtime.getRuntime().removeShutdownHook(shutdownHook); } catch (IllegalStateException shutdownInProgress) { }
        }
        boolean interrupted = Thread.interrupted();
        try {
            if (inFlight == null || inFlight.isDone()) {
                ChallengeProtocol.writeCommand(output,
                        new ChallengeProtocol.Command(ChallengeProtocol.CLOSE, generation, sequence + 1, 0));
                if (!process.waitFor(750, TimeUnit.MILLISECONDS))
                    process.destroy();
            } else
                process.destroy();
            if (!process.waitFor(1500, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor(1500, TimeUnit.MILLISECONDS);
            }
        } catch (IOException e) {
            process.destroyForcibly();
        } catch (InterruptedException e) {
            process.destroyForcibly();
            interrupted = true;
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(1500);
                while (process.isAlive() && System.nanoTime() < deadline) {
                    try {
                        process.waitFor(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                    } catch (InterruptedException e) {
                        interrupted = true;
                    }
                }
            }
            io.shutdownNow();
            if (inFlight != null && !inFlight.isDone())
                inFlight.completeExceptionally(new IOException("Worker closed"));
            try {
                input.close();
            } catch (IOException ignored) {
            }
            if (interrupted)
                Thread.currentThread().interrupt();
            try {
                output.close();
            } catch (IOException ignored) {
            }
        }
        if (process.isAlive())
            throw new IllegalStateException("Worker teardown did not complete: " + process.pid());
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        } catch (IOException failure) {
            throw new IllegalStateException("Worker directory cleanup pending: " + directory, failure);
        }
    }
}
