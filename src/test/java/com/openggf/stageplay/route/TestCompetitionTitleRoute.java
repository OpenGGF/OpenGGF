package com.openggf.stageplay.route;
import com.openggf.tools.StageplayCompetitionRouteHosts;

import com.openggf.route.*;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

@Isolated @RequiresRom(SonicGame.SONIC_2)
class TestCompetitionTitleRoute {
    @org.junit.jupiter.api.BeforeEach
    void retireAnnotationFixtureBeforeColdBoot() {
        // @RequiresRom opens a gameplay session; the cold host requires an empty worker.
        com.openggf.tests.TestEnvironment.resetEngineOnly();
    }
    @Test void coldPrefixLaunchesTheProductionCasinonightMenuEntry() throws Exception {
        var launch=new LaunchContract(RomIdentity.standard("s2"),new Inlet.Title(),new Roster("sonic","tails"),
                320,"off",new InitialState(0,Seeds.NONE));
        var frozen=TestStageplayCompetitionFrozenRoute.resource("cnz1-slot-flip");
        int end=frozen.manifest().nativeFacts().entry().orElseThrow().engineAnchorRows()+1;
        var tape=frozen.tape().slice(0,end);
        var condition=new SamplePredicate.All(java.util.List.of(
                new SamplePredicate.FactEquals("competition.selection",2),
                new SamplePredicate.FactEquals("competition.phase",1),
                new SamplePredicate.PlayerIn(PlayerSlot.P2,new Region(0,0,65535,65535))));
        var goals=new GoalProgram(Guard.NONE,java.util.List.of(Leg.of("cnz-entry",new RouteGoal.Achieve(condition),1)),tape.size()-1);
        var route=RouteArtifact.of(launch,goals,tape,new Provenance("test","test","test",java.util.List.of()));
        var hosts=new StageplayCompetitionRouteHosts(Path.of(System.getProperty("sonic2.rom.path")));
        var result=new RouteReplay(hosts).replay(route,Cancellation.NONE,(row,sample)->{
            if(row==899 || row==900 || row==949 || row==950 || row==1050 || row==1150)
                System.out.println("TITLE EDGE row="+row+" "+sample);
        });
        var passed=assertInstanceOf(ReplayResult.Passed.class,result,result.toString());
        System.out.println("TITLE PREFIX END "+passed.end());
        assertEquals(2,passed.end().facts().get("competition.selection"));
        assertTrue(passed.end().p2().isPresent());
        int anchor=passed.end().facts().get("input.gameplayAnchorRows");
        assertTrue(anchor>0 && anchor<tape.size());
        var entry=new NativeFacts.NativeEntry("unused-in-engine-replay",1337,tape.hash(),anchor,"first-ordinary-level-lfc-zero");
        var anchored=new RouteArtifact(route.manifest().withNativeFacts(new NativeFacts(java.util.Optional.of(entry),java.util.List.of())),tape);
        assertInstanceOf(ReplayResult.Passed.class,new RouteReplay(hosts).replay(anchored,Cancellation.NONE));
        var wrong=new NativeFacts.NativeEntry(entry.movie(),entry.prefixRows(),entry.inputHash(),anchor+1,entry.anchorBoundary());
        var wrongRoute=new RouteArtifact(route.manifest().withNativeFacts(new NativeFacts(java.util.Optional.of(wrong),java.util.List.of())),tape);
        assertInstanceOf(ReplayResult.Failed.class,new RouteReplay(hosts).replay(wrongRoute,Cancellation.NONE),
                "negative control: replay must observe the declared engine boundary, not accept a fitted row");
        var changed=new java.util.ArrayList<>(tape.rows());changed.set(1050,PadRow.NEUTRAL);
        var broken=RouteArtifact.of(launch,goals,InputTape.of(changed),route.manifest().provenance());
        assertInstanceOf(ReplayResult.Failed.class,new RouteReplay(hosts).replay(broken,Cancellation.NONE),
                "negative control: removing the grid edge must fail the same CNZ launch goal");
    }
    @Test void plannerCanRestoreALeaveCheckpointAfterLookingPastTheGameplayAnchor() throws Exception {
        var hosts=new StageplayCompetitionRouteHosts(Path.of(System.getProperty("sonic2.rom.path")));
        var goals=new GoalProgram(Guard.NONE,java.util.List.of(new Leg("ordinary-after-title",
                new RouteGoal.Achieve(new SamplePredicate.FactEquals("competition.p1.time",200)),
                Guard.NONE.holding(new SamplePredicate.FactEquals("competition.phase",1)),400)),1200);
        var result=com.openggf.stageplay.route.StageplayRoutePlanner.planNative(hosts,
                StageplayCompetitionRouteHosts.launch(),StageplayCompetitionRouteHosts.menuPrefix(2),goals,
                new SearchBudget(100,100000,32,16),()->false,PlanProgress.Listener.NONE);
        var solved=assertInstanceOf(PlanResult.Solved.class,result,result.toString());
        assertEquals(200,solved.replayEnd().facts().get("competition.p1.time"));
        assertTrue(solved.replayEnd().facts().get("input.gameplayAnchorRows")>1200);
    }

}
