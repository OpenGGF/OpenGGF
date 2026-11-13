package com.openggf.tools;
import com.openggf.tools.StageplayCompetitionRouteHosts;

import com.openggf.route.*;

import com.openggf.stageplay.route.StageplayRoutePlanner;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TestTwoPlayerRouteProgramme {
    private static PlayerView player(int x) {
        return new PlayerView(x,100,0,0,0,0,0,0,false,false,false,false,false,false,0,false,false,false,PlayerView.Motion.NONE);
    }
    private static final class World implements RouteWorld {
        int p1,p2,rows;
        @Override public RouteSample observe() { return new RouteSample(rows,0,new RouteSample.Level(0,0,0,"ACT"),
                new RouteSample.Clocks(rows,rows),player(p1),Optional.of(player(p2)),0,0,0); }
        @Override public RecordedStep step(PadRow row) {
            p1+=delta(row.p1());p2+=delta(row.p2());rows++;return new RecordedStep(row,observe());
        }
        int delta(int b) { return ((b&PhysicalButton.RIGHT.bit())!=0?1:0)-((b&PhysicalButton.LEFT.bit())!=0?1:0); }
        record Saved(int p1,int p2,int rows) implements WorldCheckpoint { }
        @Override public WorldCheckpoint capture() { return new Saved(p1,p2,rows); }
        @Override public void restore(WorldCheckpoint c) { var s=(Saved)c;p1=s.p1;p2=s.p2;rows=s.rows; }
        @Override public int rowsOffered() { return rows; }
        @Override public void close() { }
    }
    @Test void bothPlayersSearchAndLinearQualificationKeepTheirOwnPad() {
        var factory=new RouteWorldFactory() {
            public RouteWorld openSearch(LaunchContract l) { return new World(); }
            public ReplayWorld openColdReplay(LaunchContract l) { return new World(); }
            public String environment() { return "two-human-test"; }
        };
        var both=new SamplePredicate.All(List.of(new SamplePredicate.PlayerIn(PlayerSlot.P1,new Region(20,90,24,110)),
                new SamplePredicate.PlayerIn(PlayerSlot.P2,new Region(-24,90,-20,110))));
        var goals=GoalProgram.of(Guard.safe(),new Leg("both",new RouteGoal.Achieve(both),Guard.NONE,100));
        var result=StageplayRoutePlanner.planNative(factory,StageplayCompetitionRouteHosts.launch(),InputTape.empty(),goals,
                new SearchBudget(100,10000,10,0),()->false,PlanProgress.Listener.NONE);
        var solved=assertInstanceOf(PlanResult.Solved.class,result,result.toString());
        assertTrue(both.test(solved.replayEnd()));
        var broken=InputTape.of(solved.route().tape().rows().stream().map(r->new PadRow(r.p1(),0)).toList());
        var brokenRoute=RouteArtifact.of(solved.route().launch(),goals,broken,solved.route().manifest().provenance());
        assertInstanceOf(ReplayResult.Failed.class,new RouteReplay(factory).replay(brokenRoute,Cancellation.NONE),
                "negative control: removing P2 rows must fail the same goal programme");
        assertTrue(solved.route().tape().rows().stream().anyMatch(r -> r.p1()!=0 && r.p2()!=0));
        assertFalse(both.test(new World().observe()),"negative control: neither actor has reached the goal");
    }
    @Test void jointSearchCanChargeOneLaneWhileTheOtherKeepsRunning() {
        class Asymmetric implements RouteWorld {
            int p1, p2, rows;
            public RouteSample observe() { return new RouteSample(rows,0,new RouteSample.Level(0,0,0,"ACT"),
                    new RouteSample.Clocks(rows,rows),player(p1),Optional.of(player(p2)),0,0,0); }
            public RecordedStep step(PadRow row) {
                boolean together = row.held(PlayerSlot.P1,PhysicalButton.DOWN)
                        && row.held(PlayerSlot.P2,PhysicalButton.RIGHT) && !row.held(PlayerSlot.P2,PhysicalButton.DOWN);
                // Serial charge-then-run cannot advance either actor in this coupled world.
                if (together) { p1++; p2++; }
                rows++; return new RecordedStep(row,observe());
            }
            record Saved(int p1,int p2,int rows) implements WorldCheckpoint { }
            public WorldCheckpoint capture() { return new Saved(p1,p2,rows); }
            public void restore(WorldCheckpoint checkpoint) { var c=(Saved)checkpoint;p1=c.p1();p2=c.p2();rows=c.rows(); }
            public int rowsOffered() { return rows; }
            public void close() { }
        }
        var factory=new RouteWorldFactory() {
            public RouteWorld openSearch(LaunchContract launch) { return new Asymmetric(); }
            public ReplayWorld openColdReplay(LaunchContract launch) { return new Asymmetric(); }
            public String environment() { return "asymmetric-lanes"; }
        };
        var both=new SamplePredicate.All(List.of(
                new SamplePredicate.PlayerIn(PlayerSlot.P1,new Region(20,90,1000,110)),
                new SamplePredicate.PlayerIn(PlayerSlot.P2,new Region(20,90,1000,110))));
        var goals=GoalProgram.of(Guard.NONE,Leg.of("charge-and-run",new RouteGoal.Achieve(both),100));
        var result=new RoutePlanner(factory).plan(new RouteTask.Author(StageplayCompetitionRouteHosts.launch(),
                InputTape.empty(),goals,new SearchBudget(50,30000,10,0)),Cancellation.NONE);
        var solved=assertInstanceOf(PlanResult.Solved.class,result,result.toString());
        assertTrue(solved.route().tape().rows().stream().anyMatch(r ->
                r.held(PlayerSlot.P1,PhysicalButton.DOWN) && r.held(PlayerSlot.P2,PhysicalButton.RIGHT)
                        && !r.held(PlayerSlot.P2,PhysicalButton.DOWN)));
        assertTrue(both.test(solved.replayEnd()));
    }

    @Test void titleInletAndExportPreservePrefixAndBothLanes(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        var prefix=com.openggf.tools.StageplayCompetitionRouteHosts.menuPrefix(2);
        var launch=new LaunchContract(RomIdentity.standard("s2"),new Inlet.Title(),new Roster("sonic","tails"),320,"off",new InitialState(0,Seeds.NONE));
        var tape=prefix.append(InputTape.of(List.of(new PadRow(PhysicalButton.RIGHT.bit(),PhysicalButton.LEFT.bit()))));
        var route=RouteArtifact.of(launch,GoalProgram.NONE,tape,new Provenance("test","test","test",List.of()));
        var written=RouteFiles.writeDraft(dir.resolve("title"),route);
        assertEquals(launch,RouteFiles.readDraft(written.manifest()).launch());
        assertThrows(java.io.IOException.class,()->RouteFiles.exportBk2(route,dir.resolve("unsafe.bk2")));
        var nativePrefix=prefix.append(InputTape.of(List.of(PadRow.NEUTRAL,PadRow.NEUTRAL,PadRow.NEUTRAL)));
        var witness=dir.resolve("native.script");java.nio.file.Files.writeString(witness,RouteFiles.scriptText(nativePrefix));
        var entry=new NativeFacts.NativeEntry(witness.toString(),nativePrefix.size(),nativePrefix.hash(),
                prefix.size(),"first-ordinary-level-lfc-zero");
        var anchored=new RouteArtifact(route.manifest().withNativeFacts(new NativeFacts(Optional.of(entry),List.of())),tape);
        var movie=RouteFiles.exportBk2(anchored,dir.resolve("title.bk2"));
        var expected=nativePrefix.append(tape.slice(prefix.size(),tape.size()));
        assertEquals(expected,RouteFiles.tapeFromMovie(movie,expected.size()));
        assertEquals(PadRow.of(PlayerSlot.P1,PhysicalButton.DOWN),tape.row(900));
        assertEquals(PadRow.NEUTRAL,tape.row(901));
        assertEquals(PadRow.of(PlayerSlot.P1,PhysicalButton.DOWN),tape.row(1050));
        assertNotEquals(tape.row(900),tape.row(899),"negative control: selection is a fresh edge");
    }

    @Test void hostFactsRoundTripAndAbsentFactFails() throws Exception {
        var fact=new SamplePredicate.FactEquals("competition.slot.inUse",1);
        var goals=GoalProgram.of(Guard.NONE,new Leg("spin",new RouteGoal.Achieve(fact),Guard.NONE,100));
        assertEquals(goals,RouteCodec.parseGoals(RouteCodec.formatGoals(goals)));
        var empty=new World().observe();
        assertFalse(fact.test(empty));
        var active=new RouteSample(0,0,empty.level(),empty.clocks(),empty.p1(),empty.p2(),0,0,0,List.of(),List.of(),Map.of(fact.key(),1));
        assertTrue(fact.test(active));
        assertFalse(new SamplePredicate.FactEquals(fact.key(),2).test(active),"negative control: wrong owner value");
    }
    @Test void tiesCompareCopiedOwnerFactsAndRequireBothKeys() throws Exception {
        var equal=new SamplePredicate.FactsEqual("p1.total","p2.total");
        var goals=GoalProgram.of(Guard.NONE,Leg.of("tie",new RouteGoal.Achieve(equal),10));
        assertEquals(goals,RouteCodec.parseGoals(RouteCodec.formatGoals(goals)));
        var empty=new World().observe();
        assertFalse(equal.test(empty));
        var facts=new java.util.HashMap<String,Integer>();facts.put("p1.total",3);facts.put("p2.total",3);
        var sample=new RouteSample(0,0,empty.level(),empty.clocks(),empty.p1(),empty.p2(),0,0,0,List.of(),List.of(),facts);
        assertTrue(equal.test(sample));
        facts.put("p2.total",4);
        assertTrue(equal.test(sample),"the observation retains its copied values");
        var unequal=new RouteSample(0,0,empty.level(),empty.clocks(),empty.p1(),empty.p2(),0,0,0,List.of(),List.of(),facts);
        assertFalse(equal.test(unequal),"negative control: a one-point difference must fail the tie");
    }

    @Test void residentArtAndFlatPosesCannotProduceFalseFlipWitnesses() {
        for(int flat:new int[]{0,4,8,12}) assertFalse(Sonic2CompetitionRouteWitness.midFlipFrame(flat));
        assertTrue(Sonic2CompetitionRouteWitness.midFlipFrame(13));
        assertFalse(Sonic2CompetitionRouteWitness.midFlipFrame(-1));
        byte[][] frames={new byte[]{1,2},new byte[]{3,4}};
        assertEquals(1,Sonic2CompetitionRouteWitness.residentFrame(new byte[]{3,4},frames));
        assertEquals(-1,Sonic2CompetitionRouteWitness.residentFrame(new byte[]{3,5},frames),
                "negative control: mixed or changed resident art is not a known ROM pose");
    }

}
