package com.openggf.stageplay.runtime;

import com.openggf.physics.RouteTerrainProbe;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayGround;
import com.openggf.stageplay.StageplayGround.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class TestStageplayGroundSurvey {
    private static final Visual OBJECT = new Visual(2,20,14,ObjectPath.PRIMARY);
    private static final Basis BASIS = new EntryLayout(new StageplayDocument.SetSource("s1",0,0,"",""));
    private RouteTerrainProbe terrain(GroundWork work, int... surfaces) {
        return new RouteTerrainProbe() {
            public Floor floor(int x, int y, int path) {
                work.scan();
                return Arrays.stream(surfaces).filter(s -> Math.abs(s-y) <= 16).boxed()
                        .min(Comparator.comparingInt(s -> Math.abs(s-y))).map(s -> new Floor(true,s,0)).orElse(Floor.missing());
            }
            public boolean clear(int l,int t,int r,int b,int path) { work.scan(); return true; }
            public boolean dynamicCollision() { return false; }
        };
    }
    @Test void enumeratesEveryPixelPhaseAndReturnsTopFirstWithNearestTiesAbove() {
        var batch = new Batch(List.of(new Query("floors",100,80,320,OBJECT)),10000);
        var work = new GroundWork(batch,() -> false);
        var seeds = new HashSet<Integer>();
        var exact = new RouteTerrainProbe() {
            public Floor floor(int x,int y,int path) { work.scan(); seeds.add(y); return y==101 || y==300 ? new Floor(true,y,0) : Floor.missing(); }
            public boolean clear(int l,int t,int r,int b,int path) { work.scan(); return true; }
            public boolean dynamicCollision() { return false; }
        };
        var answer = StageplayGroundSurvey.answer(BASIS,batch,work,exact,null,p -> null,(q,p) -> null).answer("floors");
        for (int seed=64;seed<=336;seed++) assertTrue(seeds.contains(seed),"Unvisited integer seed "+seed);
        assertEquals(Outcome.FOUND,answer.outcome());
        assertEquals(List.of(87,286),answer.placements().stream().map(Placement::y).toList());
        assertEquals(87,answer.nearest(186.5).orElseThrow().y());
        assertTrue(answer.refusals().isEmpty());
        assertFalse(answer.truncated());
    }
    @Test void budgetExhaustionAndCancellationNeverClaimAbsenceOrNearest() {
        var batch = new Batch(List.of(new Query("one",100,80,320,OBJECT),new Query("two",200,80,320,OBJECT)),10000,2,0);
        var work = new GroundWork(batch,() -> false);
        var report = StageplayGroundSurvey.answer(BASIS,batch,work,terrain(work,101),null,p -> null,(q,p) -> null);
        assertEquals(StopReason.SCAN_LIMIT,report.work().stopped());
        assertEquals(2,report.work().nativeScans());
        for (var a : report.answers()) { assertEquals(Outcome.INCOMPLETE,a.outcome()); assertTrue(a.nearest(100).isEmpty()); }
        var calls = new AtomicInteger();
        var cancelled = new GroundWork(batch,() -> calls.incrementAndGet() > 4);
        var stop = StageplayGroundSurvey.answer(BASIS,batch,cancelled,terrain(cancelled),null,p -> null,(q,p) -> null);
        assertEquals(StopReason.CANCELLED,stop.work().stopped());
        assertTrue(stop.answers().stream().allMatch(a -> a.outcome() == Outcome.INCOMPLETE));
    }
    @Test void aCompleteEmptyBandDiffersFromIncompleteAndBoundedListsAdvertiseTruncation() {
        var batch = new Batch(List.of(new Query("floor",100,0,800,OBJECT)),10000);
        var work = new GroundWork(batch,() -> false);
        assertEquals(Outcome.NONE_IN_BAND,StageplayGroundSurvey.answer(BASIS,batch,work,terrain(work),null,p -> null,(q,p) -> null).answers().getFirst().outcome());
        var bounded = new GroundWork(batch,() -> false);
        int[] surfaces = java.util.stream.IntStream.range(0,20).map(i -> 31+i*32).toArray();
        var answer = StageplayGroundSurvey.answer(BASIS,batch,bounded,terrain(bounded,surfaces),null,p -> null,(q,p) -> null).answers().getFirst();
        assertEquals(16,answer.placements().size()); assertTrue(answer.truncated()); assertTrue(answer.nearest(700).isEmpty());
    }
    @Test void donorProfilesAreCapturedOncePerDistinctSetupInTheBatch() {
        var player = new Player("s2","sonic",0);
        var batch = new Batch(List.of(new Query("a",100,100,120,player),new Query("b",200,100,120,player)),10000);
        var work = new GroundWork(batch,() -> false);
        var count = new AtomicInteger();
        var profile = org.mockito.Mockito.mock(com.openggf.sprites.playable.PlayablePlacementProfile.class);
        var players = org.mockito.Mockito.mock(com.openggf.physics.NativePlayerTerrainPlacement.class);
        org.mockito.Mockito.when(players.near(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(new com.openggf.physics.NativePlayerTerrainPlacement.Result(null,com.openggf.physics.NativePlayerTerrainPlacement.Reason.NO_GEOMETRIC_SUPPORT,Integer.MIN_VALUE));
        StageplayGroundSurvey.answer(BASIS,batch,work,terrain(work),players,p -> { count.incrementAndGet(); return profile; },(q,p) -> null);
        assertEquals(1,count.get());
    }
    @Test void bothBasesAndTypedEvidenceSurviveStrictJsonRoundTrips() throws Exception {
        var batch = new Batch(List.of(new Query("object",100,80,320,OBJECT),new Query("player",200,80,320,new Player("s3k","knuckles",1))),10000);
        assertEquals(batch,StageplayGround.Json.decodeBatch(StageplayGround.Json.encode(batch),null));
        for (var basis : List.of(BASIS,new SceneFrame(BASIS.set(),240))) {
            var report = new Report(basis,List.of(new Answer("one",Outcome.FOUND,List.of(new Placement(100,87,101,0,new VisualSupport(101,101,101))),List.of(),false),
                    new Answer("two",Outcome.FOUND,List.of(new Placement(100,82,101,0,new PlayerTerrainProposal(Foot.LEFT,0,9,19))),List.of(),false)),new Work(7,3,12,StopReason.NONE));
            assertEquals(report,StageplayGround.Json.decodeReport(StageplayGround.Json.encode(report)));
        }
        var json = StageplayGround.Json.encode(batch);
        assertThrows(Exception.class,() -> StageplayGround.Json.decodeBatch(json.replace("\"maxMillis\":10000","\"typo\":10000"),null));
        assertThrows(Exception.class,() -> StageplayGround.Json.decodeBatch(json.replace("\"maxMillis\":10000","\"maxMillis\":1,\"maxMillis\":10000"),null));
        assertThrows(Exception.class,() -> StageplayGround.Json.decodeBatch(json+" {}",null));
    }
}
