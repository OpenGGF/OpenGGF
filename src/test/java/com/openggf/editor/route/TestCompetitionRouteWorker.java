package com.openggf.editor.route;

import com.openggf.route.*;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real subprocesses: the editor retains its own session; title programmes own isolated EGL. */
@RequiresRom(SonicGame.SONIC_2)
class TestCompetitionRouteWorker {
    @TempDir Path dir;
    private RouteTapeJob job() throws Exception {
        Path rom=RomTestUtils.ensureSonic2RomAvailable().toPath();
        return new RouteTapeJob(()->Map.of("s2",rom));
    }
    private static RouteArtifact fixture(String name) throws Exception {
        return RouteFiles.read(Path.of("src/test/resources/routes/s2/competition",name+".route.json"));
    }
    private static RouteTapeJob.Update await(RouteTapeJob job) throws Exception {
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(3);
        while(job.poll().running() && System.nanoTime()<deadline) Thread.sleep(50);
        assertFalse(job.poll().running(),"Isolated worker timeout");
        assertNotEquals(RouteTapeJob.Phase.ERROR,job.poll().phase(),job.poll().message());
        return job.poll();
    }
    @Test void replayAndUnchangedSavePreserveFrozenBytes() throws Exception {
        for(String name:List.of("cnz1-slot-prefix","cnz1-slot-flip")) {
            Path source=Path.of("src/test/resources/routes/s2/competition",name);
            Path stem=dir.resolve(name), manifest=Path.of(stem+".route.json"), script=Path.of(stem+".script");
            Files.copy(Path.of(source+".route.json"),manifest);Files.copy(Path.of(source+".script"),script);
            byte[] m=Files.readAllBytes(manifest),t=Files.readAllBytes(script);
            try(var job=job()) {
                var route=RouteFiles.read(manifest);
                job.start(new RouteTapeJob.Request(RouteTapeJob.Kind.REPLAY,route,null,false));
                var replay=await(job); assertEquals(RouteTapeJob.Phase.PASSED,replay.phase(),replay.message());
                assertFalse(replay.path().path(PlayerSlot.P2).isEmpty());
                assertTrue(replay.facts().containsKey("competition.phase"));
                job.start(new RouteTapeJob.Request(RouteTapeJob.Kind.SAVE,route,stem,true));
                var saved=await(job);assertEquals(RouteTapeJob.Phase.PASSED,saved.phase(),saved.message());
            }
            assertArrayEquals(m,Files.readAllBytes(manifest));assertArrayEquals(t,Files.readAllBytes(script));
        }
    }
    @Test void planContinuesPrefixAndRepairRetainsNativeMetadata() throws Exception {
        var original=fixture("cnz1-slot-prefix");
        var goals=new GoalProgram(Guard.NONE,List.of(Leg.of("slot-still-active",new RouteGoal.Achieve(
                new SamplePredicate.FactEquals("competition.slot.inUse",1)),30)),original.tape().size());
        var m=original.manifest();
        var route=new RouteArtifact(new RouteManifest(m.launch(),m.tapeHash(),m.rowCount(),goals,true,m.provenance(),List.of(),m.nativeFacts()),original.tape());
        try(var job=job()) {
            job.start(RouteTapeJob.Request.plan(route,route.tape().size()));
            var plan=await(job);assertEquals(RouteTapeJob.Phase.SOLVED,plan.phase(),plan.message());
            assertEquals(route.tape(),plan.planned().tape().slice(0,route.tape().size()));
            assertEquals(goals,plan.planned().checks());
            assertEquals(m.nativeFacts(),plan.planned().manifest().nativeFacts());
            job.start(RouteTapeJob.Request.plan(original,original.tape().size()));
            var completed=await(job); assertEquals(RouteTapeJob.Phase.SOLVED,completed.phase(),completed.message());
            assertEquals(original.checks(),completed.planned().checks(),"Completed prefix legs retain their original cursor");
            assertEquals(original.tape(),completed.planned().tape(),"No continuation is invented after completed goals");
            var bounds=new RouteTapeJob.RepairBounds(original.tape().size(),30,1,original.tape().size()-1,original.tape().size()-1);
            job.start(RouteTapeJob.Request.repair(original,original,bounds));
            var repaired=await(job);assertEquals(RouteTapeJob.Phase.SOLVED,repaired.phase(),repaired.message());
            assertEquals(original.tape(),repaired.repair().rows());
        }
    }
}
