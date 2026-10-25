package com.openggf.stageplay.editor;

import com.openggf.stageplay.StageplayDocument.Facing;

import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayStore;
import com.openggf.stageplay.route.*;
import com.openggf.stageplay.runtime.StageplayProcessInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;

import static com.openggf.stageplay.StageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real disposable subprocesses exercise the editor job protocol; the child is a controlled planner stand-in. */
@Timeout(25)
class TestStageplayRouteJob {
    private static StageplayDocument source() {
        return StageplayDocument.empty("s2",0,0,0,0,320,224).withDurationFrames(120)
                .withActors(List.of(new Actor("hero","Hero","s2","sonic",List.of(new ActorKey(0,100,200,0,-1, Facing.RIGHT,true,1,Interpolation.HOLD)))))
                .withPuppets(List.of(new PuppetTrack("hero",false,0,List.of())));
    }
    @Test void shippedWorkerCommandUsesTheHostClasspathAndTheLauncherOwnsTestOverrides() throws Exception {
        var source = source();
        var request = new StageplayRouteRequest("hero", 0, 150, 200, 30);
        var capture = (StageplayRouteJob.Capture) (document, value, root) ->
                new StageplayRouteInputs.Snapshot(document, value, root, "{}", "{}", "0".repeat(64));
        var command = new AtomicReference<List<String>>();
        try (var job = new StageplayRouteJob(capture, args -> {
            command.set(args);
            return child("success"); // This test-only seam supplies Surefire's classpath.
        })) {
            job.start(source, Path.of("/").toAbsolutePath(), request);
            await(() -> job.poll().phase() == StageplayRouteJob.Phase.READY);
            int classpathIndex = command.get().indexOf("-cp") + 1;
            assertTrue(classpathIndex > 0);
            assertEquals(System.getProperty("java.class.path"), command.get().get(classpathIndex));
        }
    }

    @Test void resultRequiresOffThreadFreshnessAndLateContentChangesCannotApply() throws Exception {
        var calls=new AtomicInteger();var changed=new AtomicBoolean();var threadNames=new ArrayList<String>();
        var request=new StageplayRouteRequest("hero",0,150,200,30);var source=source();
        var capture=(StageplayRouteJob.Capture)(document,value,root)->{
            synchronized(threadNames) {threadNames.add(Thread.currentThread().getName());}
            calls.incrementAndGet();return new StageplayRouteInputs.Snapshot(document,value,root,"{}","{}",(changed.get()?"1":"0").repeat(64));
        };
        var parent=com.openggf.game.session.SessionManager.getCurrentWorldSession();
        try(var job=new StageplayRouteJob(capture,args->child("success"))) {
            job.start(source,Path.of("/").toAbsolutePath(),request);
            await(()->job.poll().phase()==StageplayRouteJob.Phase.READY);
            assertEquals(1,calls.get());assertTrue(job.poll().result().success());
            changed.set(true);job.requestApply(source,Path.of("/").toAbsolutePath());
            await(()->job.poll().phase()==StageplayRouteJob.Phase.FAILED);
            assertTrue(job.poll().message().contains("Stale route"));assertEquals(2,calls.get());
            synchronized(threadNames) {assertTrue(threadNames.stream().allMatch(n->n.startsWith("stageplay-route-")));}
            assertSame(parent,com.openggf.game.session.SessionManager.getCurrentWorldSession(),"The parent world is never replaced by job IO");
        }
    }
    @Test void successfulApplyCheckKeepsCandidateAndCancellationRetiresRealOwnedWorker() throws Exception {
        var launched=new AtomicReference<Process>();var request=new StageplayRouteRequest("hero",0,150,200,30);var source=source();
        var capture=(StageplayRouteJob.Capture)(document,value,root)->new StageplayRouteInputs.Snapshot(document,value,root,"{}","{}","0".repeat(64));
        try(var job=new StageplayRouteJob(capture,args->{var child=child("success");launched.set(child);return child;})) {
            job.start(source,Path.of("/").toAbsolutePath(),request);await(()->job.poll().phase()==StageplayRouteJob.Phase.READY);
            var candidate=job.poll().result().candidate();job.requestApply(source,Path.of("/").toAbsolutePath());
            await(()->job.poll().phase()==StageplayRouteJob.Phase.APPLY_READY);assertEquals(candidate,job.poll().result().candidate());
            assertFalse(launched.get().isAlive());
        }
        try(var job=new StageplayRouteJob(capture,args->{var child=child("wait");launched.set(child);return child;})) {
            launched.set(null);job.start(source,Path.of("/").toAbsolutePath(),request);await(()->launched.get()!=null && launched.get().isAlive());
            long generation=job.poll().generation();job.cancel();
            assertEquals(StageplayRouteJob.Phase.CANCELLED,job.poll().phase());assertTrue(job.poll().generation()>generation);
            job.close();assertFalse(launched.get().isAlive());assertNull(job.poll().result());
        }
    }
    @Test void workerProtocolFailureAndLaunchFailureAreActionableAndCannotApply() throws Exception {
        var source=source();var request=new StageplayRouteRequest("hero",0,150,200,30);
        var capture=(StageplayRouteJob.Capture)(document,value,root)->new StageplayRouteInputs.Snapshot(document,value,root,"{}","{}","0".repeat(64));
        for(String mode:List.of("trailing","oversized")) try(var job=new StageplayRouteJob(capture,args->child(mode))) {
            job.start(source,Path.of("/").toAbsolutePath(),request);await(()->job.poll().phase()==StageplayRouteJob.Phase.FAILED);
            assertNull(job.poll().result());assertThrows(java.io.IOException.class,()->job.requestApply(source,Path.of("/")));
        }
        try(var job=new StageplayRouteJob(capture,args->{throw new java.io.IOException("Cannot launch owned worker");})) {
            job.start(source,Path.of("/").toAbsolutePath(),request);await(()->job.poll().phase()==StageplayRouteJob.Phase.FAILED);
            assertTrue(job.poll().message().contains("Cannot launch owned worker"));
        }
    }
    private static Process child(String mode) throws java.io.IOException {
        return new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-Xmx96m","-cp",
                System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),Child.class.getName(),mode).start();
    }
    private static void await(BooleanSupplier condition) throws Exception {
        long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while(!condition.getAsBoolean()) {if(System.nanoTime()>end) fail("Owned route job did not reach expected state");Thread.sleep(10);}
    }
    public static final class Child {
        public static void main(String[] args) throws Exception {
            try(var input=new StageplayProcessInput(new InputStreamReader(System.in,StandardCharsets.UTF_8))) {
                input.readLine(1024);var source=StageplayStore.decode(Base64.getDecoder().decode(input.readLine(StageplayStore.MAX_BYTES*2)));
                input.readLine(1024);var envelope=StageplayRouteProtocol.decodeEnvelope(input.readLine(StageplayRouteProtocol.MAX_REQUEST_CHARS));
                if(args[0].equals("wait")) {Thread.sleep(60000);return;}
                if(args[0].equals("oversized")) {System.out.print("x".repeat(StageplayRouteProtocol.MAX_RESULT_CHARS+1));return;}
                var commands=List.of(new PuppetCommand("route.go",0,PuppetCommandKind.MOVE_TO,150,200,1,0),new PuppetCommand("route.stop",29,PuppetCommandKind.STOP,0,0,0,0));
                var candidate=source.withPuppets(List.of(new PuppetTrack("hero",false,0,commands)));
                var result=new StageplayRouteResult(StageplayRouteResult.Status.VERIFIED,"fixture worker",envelope.fingerprint(),envelope.request(),candidate,commands,List.of(),List.of(new StageplayRoutePoint(29,150,200,true)),1,30,true);
                System.out.println(StageplayRouteProtocol.encodeResult(result));
                if(args[0].equals("trailing")) System.out.println("{}");
            }
        }
    }
}
