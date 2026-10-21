package com.openggf.stageplay.editor;

import com.openggf.data.RomIdentity;
import com.openggf.data.RomManager;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;

/** One independent offline render. A bounded diagnostic tail is consumed on completion. */
final class StageplayExportJob implements AutoCloseable {
    private Process process;
    private Thread reader;
    private Path snapshot;
    private Path output;
    private Path staging;
    private final ArrayDeque<String> tail = new ArrayDeque<>();
    private final Launcher launcher;

    @FunctionalInterface interface Launcher { Process start(java.util.List<String> command) throws IOException; }
    StageplayExportJob() { this(command -> new ProcessBuilder(command).redirectErrorStream(true).start()); }
    StageplayExportJob(Launcher launcher) { this.launcher = java.util.Objects.requireNonNull(launcher); }

    void start(StageplayDocument document, Path source, Path destination, RomManager roms) throws IOException {
        if (process != null) throw new IOException("Wait for the current film export to finish");
        // A prior cleanup error retains ownership; never overwrite its only tracked paths.
        cleanupOwnedFiles();
        destination = destination.toAbsolutePath().normalize();
        if (Files.exists(destination)) throw new IOException("Choose a new output file; film export does not overwrite existing media");
        if (source == null) throw new IOException("Save the scene before exporting");
        snapshot = Files.createTempFile(source.toAbsolutePath().getParent(),".stageplay-export-",".stageplay");
        try {
            Files.createDirectories(destination.getParent());
            staging = Files.createTempDirectory(destination.getParent(),".stageplay-export-");
            StageplayStore.write(snapshot,document);
            var command = new ArrayList<String>();
            command.add(Path.of(System.getProperty("java.home"),"bin","java").toString());
            String gl = System.getProperty("openggf.headless.gl");
            if (gl != null) command.add("-Dopenggf.headless.gl="+gl);
            command.add("-cp"); command.add(System.getProperty("java.class.path"));
            command.add("com.openggf.tools.StageplayCaptureTool");
            command.add("--scene"); command.add(snapshot.toString());
            command.add("--out"); command.add(destination.toString());
            command.add("--staging-dir"); command.add(staging.toString());
            command.add("--game"); command.add(document.set().gameId());
            for (var game : new String[]{"s1","s2","s3k"}) {
                var identity = switch(game) { case "s1" -> RomIdentity.S1; case "s2" -> RomIdentity.S2; default -> RomIdentity.S3K; };
                roms.resolveLogicalRom(identity).flatMap(value -> value.wholeImagePath()).ifPresent(path -> {
                    command.add("--"+game+"-rom"); command.add(path.toString());
                });
            }
            process = launcher.start(java.util.List.copyOf(command)); output = destination;
            Process owned = process;
            reader = Thread.ofVirtual().name("stageplay-export-output").start(() -> {
                try (var input = new java.io.BufferedReader(new java.io.InputStreamReader(owned.getInputStream(),StandardCharsets.UTF_8))) {
                    String line;
                    while ((line=input.readLine())!=null) synchronized(tail) {
                        if(tail.size()==12) tail.removeFirst();
                        tail.addLast(line.length()>1024?line.substring(0,1024):line);
                    }
                } catch(IOException failure) { synchronized(tail) { tail.addLast(failure.toString()); } }
            });
        } catch(IOException | RuntimeException failure) {
            try { close(); } catch(RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    String poll() {
        if (process==null || process.isAlive() || reader.isAlive()) return null;
        int code=process.exitValue(); cleanupOwnedFiles(); process=null; reader=null;
        String message;
        synchronized(tail) { message=String.join(" | ",tail); tail.clear(); }
        return code==0 ? "Exported film: "+output : "Film export failed (exit "+code+"): "+message;
    }

    private void cleanupOwnedFiles() {
        IOException failure = null;
        if (snapshot != null) {
            try { Files.deleteIfExists(snapshot); snapshot = null; }
            catch (IOException error) { failure = error; }
        }
        if (staging != null) {
            try {
                if (Files.exists(staging)) try (var files = Files.walk(staging)) {
                    for (var file : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
                }
                staging = null;
            } catch (IOException error) {
                if (failure == null) failure = error; else failure.addSuppressed(error);
            }
        }
        if (failure != null) throw new java.io.UncheckedIOException("Cannot remove owned Stageplay export files", failure);
    }

    @Override public void close() {
        boolean interrupted = Thread.interrupted();
        try {
            if (process != null) {
                // Retain these handles before the parent exits: orphaned encoder children are
                // no longer discoverable through Process.descendants() after SIGTERM.
                var children = process.descendants().toList();
                children.forEach(ProcessHandle::destroy); process.destroy();
                try { process.waitFor(5,java.util.concurrent.TimeUnit.SECONDS); }
                catch (InterruptedException error) { interrupted = true; }
                children.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
                if (process.isAlive()) process.destroyForcibly();
                var remaining = new ArrayList<>(children); remaining.add(process.toHandle());
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
                for (var child : remaining) while (child.isAlive()) {
                    long nanos = deadline - System.nanoTime();
                    if (nanos <= 0) throw new IllegalStateException("Owned Stageplay export process did not stop: " + child.pid());
                    try { child.onExit().get(nanos,java.util.concurrent.TimeUnit.NANOSECONDS); }
                    catch (InterruptedException error) { interrupted = true; }
                    catch (java.util.concurrent.TimeoutException | java.util.concurrent.ExecutionException error) {
                        throw new IllegalStateException("Cannot await owned Stageplay export process " + child.pid(),error);
                    }
                }
                process = null;
            }
            if (reader != null) {
                try { reader.join(1000); } catch (InterruptedException error) { interrupted = true; }
                reader = null;
            }
            cleanupOwnedFiles();
            synchronized (tail) { tail.clear(); }
        } finally { if (interrupted) Thread.currentThread().interrupt(); }
    }
}
