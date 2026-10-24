package com.openggf.stageplay.editor;

import com.openggf.data.RomManager;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayStore;
import com.openggf.stageplay.route.*;
import com.openggf.stageplay.runtime.StageplayProcessInput;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** One independent logic-only attempt. Planning and freshness IO never run on the editor thread. */
public final class StageplayRouteJob implements AutoCloseable {
    public enum Phase { IDLE, PLANNING, READY, CHECKING, APPLY_READY, FAILED, CANCELLED }
    public record Update(long generation, Phase phase, String message, StageplayRouteResult result) { }
    @FunctionalInterface interface Capture { StageplayRouteInputs.Snapshot capture(StageplayDocument document, StageplayRouteRequest request, Path root) throws IOException; }
    @FunctionalInterface interface Launcher { Process start(List<String> command) throws IOException; }
    private final Capture capture;
    private final Launcher launcher;
    private volatile Update update = new Update(0,Phase.IDLE,"",null);
    private long generation;
    private Thread task;
    private Process process;
    private List<ProcessHandle> ownedChildren=List.of();
    private StageplayRouteInputs.Snapshot snapshot;

    public StageplayRouteJob(RomManager roms) {
        this((document,request,root) -> StageplayRouteInputs.capture(document,request,root,roms), command -> {
            var builder = new ProcessBuilder(command);
            builder.environment().remove("DISPLAY"); builder.environment().remove("WAYLAND_DISPLAY");
            return builder.start();
        });
    }
    StageplayRouteJob(Capture capture, Launcher launcher) { this.capture=Objects.requireNonNull(capture); this.launcher=Objects.requireNonNull(launcher); }

    public synchronized void start(StageplayDocument document, Path root, StageplayRouteRequest request) throws IOException {
        if(task!=null && task.isAlive()) throw new IOException("Cancel the current route attempt before starting another");
        if(process!=null && process.isAlive()) throw new IOException("Previous route worker is still stopping");
        long token=++generation;
        update=new Update(token,Phase.PLANNING,"Planning in a separate logic worker",null); snapshot=null;
        task=Thread.ofVirtual().name("stageplay-route-job").start(() -> {
            try {
                var inputs=capture.capture(document,request,root);
                if(cancelled(token)) return;
                var result=runWorker(inputs,token);
                StageplayRouteProtocol.validatePatch(document,request,inputs.fingerprint(),result);
                synchronized(this) {
                    if(cancelled(token)) return;
                    snapshot=inputs;
                    update=new Update(token,result.success()?Phase.READY:Phase.FAILED,result.status()+": "+result.message(),result);
                }
            } catch(Exception failure) { fail(token,failure); }
        });
    }
    public Update poll() { return update; }

    /** Explicit Apply begins an off-thread fresh-content check, not an immediate document mutation. */
    public synchronized void requestApply(StageplayDocument current, Path root) throws IOException {
        if(update.phase()!=Phase.READY || snapshot==null) throw new IOException("No successful route is ready to apply");
        if(!snapshot.document().equals(current) || !snapshot.root().equals(root.toAbsolutePath().normalize())) {
            update=new Update(generation,Phase.FAILED,"Stale route: scene or asset root changed; plan again",null); return;
        }
        long token=generation; var original=snapshot; var result=update.result();
        update=new Update(token,Phase.CHECKING,"Checking current ROM and project contents before Apply",result);
        task=Thread.ofVirtual().name("stageplay-route-freshness").start(() -> {
            try {
                var fresh=capture.capture(current,original.request(),root);
                if(!fresh.fingerprint().equals(original.fingerprint())) throw new IOException("Stale route: ROM or project contents changed; plan again");
                StageplayRouteProtocol.validatePatch(current,original.request(),original.fingerprint(),result);
                synchronized(this) { if(!cancelled(token)) update=new Update(token,Phase.APPLY_READY,"Route inputs verified for Apply",result); }
            } catch(Exception failure) {
                synchronized(this) {
                    if(!cancelled(token)) {
                        var stale=StageplayRouteResult.failure(StageplayRouteResult.Status.STALE_INPUT,
                                Objects.toString(failure.getMessage(),"Route inputs changed"),original.fingerprint(),original.request(),result.expandedNodes(),result.simulatedTicks());
                        update=new Update(token,Phase.FAILED,stale.message(),stale);
                    }
                }
            }
        });
    }
    private synchronized boolean cancelled(long token) { return token!=generation || Thread.currentThread().isInterrupted(); }
    private synchronized void fail(long token,Exception failure) {
        if(token==generation) update=new Update(token,Phase.FAILED,Objects.toString(failure.getMessage(),failure.getClass().getSimpleName()),null);
    }
    private StageplayRouteResult runWorker(StageplayRouteInputs.Snapshot inputs,long token) throws Exception {
        var command=new ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"),"bin","java").toString());
        command.add("--enable-native-access=ALL-UNNAMED"); command.add("-Xmx1g");
        command.add("-Djava.awt.headless=true"); command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add("com.openggf.tools.StageplayRouteTool"); command.add("--worker");
        Process child=launcher.start(List.copyOf(command));
        synchronized(this) {
            process=child; ownedChildren=List.of();
        }
        if(cancelled(token)) {
            retireWorker(child);
            throw new InterruptedException("Route attempt cancelled before worker admission");
        }
        var tail=new ArrayDeque<String>();
        var readerFailure=new java.util.concurrent.atomic.AtomicReference<Exception>();
        Thread stderr=Thread.ofVirtual().name("stageplay-route-diagnostics").start(() -> {
            try(var input=new StageplayProcessInput(new InputStreamReader(child.getErrorStream(),StandardCharsets.UTF_8))) {
                String line; while((line=input.readLine(4096))!=null) synchronized(tail) {
                    if(tail.size()==12) tail.removeFirst(); tail.addLast(line.substring(0,Math.min(512,line.length())));
                }
            } catch(Exception failure) { readerFailure.set(failure); child.destroyForcibly(); }
        });
        var response=new java.util.concurrent.CompletableFuture<String>();
        Thread stdout=Thread.ofVirtual().name("stageplay-route-response").start(() -> {
            try(var input=new StageplayProcessInput(new InputStreamReader(child.getInputStream(),StandardCharsets.UTF_8))) {
                String line=input.readLine(StageplayRouteProtocol.MAX_RESULT_CHARS);
                if(input.readLine(0)!=null) throw new IOException("Trailing route response records");
                response.complete(line);
            } catch(Exception failure) { response.completeExceptionally(failure); child.destroyForcibly(); }
        });
        try {
            try(var output=new BufferedWriter(new OutputStreamWriter(child.getOutputStream(),StandardCharsets.UTF_8))) {
                output.write(inputs.romDescriptor()); output.newLine();
                output.write(Base64.getEncoder().encodeToString(StageplayStore.encode(inputs.document()))); output.newLine();
                output.write(inputs.assetDescriptor()); output.newLine();
                output.write(StageplayRouteProtocol.envelope(inputs)); output.newLine();
            }
            if(!child.waitFor(inputs.request().maxMillis()+60_000L,TimeUnit.MILLISECONDS)) throw new IOException("Route worker exceeded its wall-clock limit");
            stdout.join(2000); stderr.join(2000);
            if(stdout.isAlive() || stderr.isAlive()) throw new IOException("Route worker streams did not finish");
            if(readerFailure.get()!=null) throw new IOException("Route diagnostic stream exceeded its bound",readerFailure.get());
            if(child.exitValue()!=0) { synchronized(tail) { throw new IOException("Route worker failed (exit "+child.exitValue()+"): "+String.join(" | ",tail)); } }
            return StageplayRouteProtocol.decodeResult(response.get());
        } finally {
            retireWorker(child);
            stdout.join(2000); stderr.join(2000);
        }
    }
    public synchronized void cancel() {
        ++generation; if(task!=null) task.interrupt();
        if(process!=null) {
            ownedChildren=process.descendants().toList();
            ownedChildren.forEach(ProcessHandle::destroy); process.destroy();
        }
        snapshot=null; update=new Update(generation,Phase.CANCELLED,"Route attempt cancelled",null);
    }
    private void retireWorker(Process child) throws InterruptedException {
        List<ProcessHandle> children;
        synchronized(this) {
            var all=new LinkedHashMap<Long,ProcessHandle>();
            if(process==child) ownedChildren.forEach(p->all.put(p.pid(),p));
            child.descendants().forEach(p->all.put(p.pid(),p)); children=List.copyOf(all.values());
        }
        try {
            children.forEach(ProcessHandle::destroy); if(child.isAlive()) child.destroy();
            if(!child.waitFor(2,TimeUnit.SECONDS)) child.destroyForcibly();
            children.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
            if(!child.waitFor(2,TimeUnit.SECONDS)) throw new IllegalStateException("Owned route worker did not stop: "+child.pid());
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
            for(var handle:children) if(handle.isAlive()) {
                long remaining=deadline-System.nanoTime();
                if(remaining<=0) throw new IllegalStateException("Owned route child did not stop: "+handle.pid());
                try { handle.onExit().get(remaining,TimeUnit.NANOSECONDS); }
                catch(java.util.concurrent.ExecutionException|java.util.concurrent.TimeoutException failure) {
                    throw new IllegalStateException("Owned route child did not stop: "+handle.pid(),failure);
                }
            }
        } finally {
            synchronized(this) { if(process==child && !child.isAlive() && children.stream().noneMatch(ProcessHandle::isAlive)) { process=null; ownedChildren=List.of(); } }
        }
    }
    @Override public void close() {
        Thread running; Process child;
        synchronized(this) { running=task; child=process; cancel(); }
        boolean interrupted=Thread.interrupted();
        try {
            if(child!=null) retireWorker(child);
            if(running!=null) { running.join(5000); if(running.isAlive()) throw new IllegalStateException("Owned route job did not stop"); }
        } catch(InterruptedException failure) { interrupted=true; throw new IllegalStateException("Interrupted closing route worker",failure); }
        finally { if(interrupted) Thread.currentThread().interrupt(); }
    }
}
