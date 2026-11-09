package com.openggf.stageplay.route;
import com.openggf.tools.StageplayCompetitionRouteHosts;

import com.openggf.route.*;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Acceptance consumes frozen rows only: both acts, results, and the actual tiebreaker inlet. */
@Isolated @RequiresRom(SonicGame.SONIC_2)
class TestStageplayCompetitionEhzFrozenRoutes {
    @BeforeEach void retireAnnotationFixture() { com.openggf.tests.TestEnvironment.resetEngineOnly(); }
    static RouteArtifact resource(String name)throws Exception {
        return RouteFiles.read(Path.of(Objects.requireNonNull(TestStageplayCompetitionEhzFrozenRoutes.class.getResource(
                "/routes/s2/competition/"+name+".route.json")).toURI()));
    }
    @ParameterizedTest @ValueSource(strings={"ehz-zone-results","ehz-tied-act-wins-special"})
    void frozenTwoHumanProgrammesReachTheirDeclaredFlow(String name)throws Exception {
        var route=resource(name);var retained=resource("ehz1-p1-finish-prefix");
        assertEquals(retained.tape(),route.tape().slice(0,retained.tape().size()));
        int common=resource("ehz-zone-results").manifest().nativeFacts().inlets().getFirst().anchor().engineAnchorRows();
        assertEquals(resource("ehz-zone-results").tape().slice(0,common),route.tape().slice(0,common));
        assertTrue(route.tape().rows().stream().anyMatch(row->row.p2()!=0));
        assertEquals(1,route.manifest().nativeFacts().inlets().size());
        var inlet=route.manifest().nativeFacts().inlets().getFirst();
        assertTrue(inlet.anchor().engineAnchorRows()-inlet.engineFromRows()>22+25,
                "the engine now represents fade, arrival, upload and leave waits");
        var first=route.manifest().nativeFacts().entry().orElseThrow();
        int residue=inlet.anchor().prefixRows()-inlet.anchor().engineAnchorRows()
                -(first.prefixRows()-first.engineAnchorRows());
        assertTrue(residue>=0 && residue<inlet.anchor().engineAnchorRows()-inlet.engineFromRows(),
                "the witnessed inlet adds only residual physical timing");
        assertEquals(10364,inlet.anchor().prefixRows());
        if (name.endsWith("special")) {
            // The lag-pacing repair appends planner-authored neutral rows. The
            // existing native movie qualified this exact prefix; a new engine
            // stamp covers the longer tape, without claiming native tail proof.
            assertEquals("10f00229ecc4bf98f43915cce8e4272deddbad33ce77b82e1c97b7c1785be265",
                    route.tape().slice(0, 18944).hash().sha256());
            assertTrue(route.tape().slice(18944, route.tape().size()).rows().stream().allMatch(PadRow.NEUTRAL::equals));
        } else assertTrue(route.manifest().qualification().stream().anyMatch(q->q.scope()==ReplayScope.BIZHAWK && q.passed() && !q.coverage().isEmpty()));
        replayFlow(name, route);
    }

    @org.junit.jupiter.api.Test
    void simultaneousTieHasOverlappingRunsAndReadableResults() throws Exception {
        replayFlow("ehz-simultaneous-tie-special", resource("ehz-simultaneous-tie-special"));
    }

    private static void replayFlow(String name, RouteArtifact route) throws Exception {
        boolean simultaneous=name.equals("ehz-simultaneous-tie-special");
        int[][] starts={{-1,-1},{-1,-1}}, finishes={{-1,-1},{-1,-1}};
        int[] neutralBoardRows={0,0};
        boolean[] seen={false,false}, titleSeen={false,false};RouteSample[] previous={null};
        var result=new RouteReplay(new StageplayCompetitionRouteHosts(Path.of(System.getProperty("sonic2.rom.path"))))
                .replay(route,Cancellation.NONE,(row,sample)->{
                    if(sample.facts().getOrDefault("competition.phase",-1)==1) {
                        int act=sample.facts().get("competition.act");
                        if(act<2 && sample.facts().getOrDefault("competition.titleCard",0)==1) {
                            titleSeen[act]=true;
                            assertEquals(0,sample.clocks().levelFrameCounter());
                        } else if(act<2) seen[act]=true;
                    }
                    if(simultaneous) {
                        int act=sample.facts().getOrDefault("competition.act",-1);
                        int phase=sample.facts().getOrDefault("competition.phase",-1);
                        if(act>=0 && act<2 && phase==1 && sample.facts().getOrDefault("competition.titleCard",1)==0) {
                            for(int lane=0;lane<2;lane++) {
                                var slot=lane==0?PlayerSlot.P1:PlayerSlot.P2;
                                if(starts[act][lane]<0 && route.tape().row(row).lane(slot)!=0) starts[act][lane]=row;
                                if(finishes[act][lane]<0 && sample.facts().getOrDefault("competition.p"+(lane+1)+".timerPaused",0)==1)
                                    finishes[act][lane]=row;
                            }
                        }
                        if(act>=0 && act<2 && phase==2) {
                            assertEquals(1,sample.facts().get("competition.zoneWins.p1"));
                            assertEquals(act,sample.facts().get("competition.zoneWins.p2"));
                            if(route.tape().row(row).equals(PadRow.NEUTRAL)) neutralBoardRows[act]++;
                            else if(route.tape().row(row).held(PlayerSlot.P1,PhysicalButton.START))
                                assertTrue(neutralBoardRows[act]>=180,"three readable seconds before confirm");
                        }
                    }
                    if(row>=1200 && route.tape().row(row).held(PlayerSlot.P1,PhysicalButton.START))
                        assertEquals(1,previous[0].facts().getOrDefault("input.menu",0),"no authored ACT pause/unpause workaround");
                    if(previous[0]!=null && previous[0].facts().getOrDefault("input.locked",0)==1)
                        assertEquals(PadRow.NEUTRAL,route.tape().row(row),"planner waits preserve native button edges");
                    previous[0]=sample;
                });
        var passed=assertInstanceOf(ReplayResult.Passed.class,result,result.toString());
        assertTrue(seen[0] && seen[1]);assertTrue(titleSeen[0] && titleSeen[1]);var facts=passed.end().facts();
        boolean special=name.endsWith("special");
        assertEquals(special?5:3,facts.get("competition.phase"));
        assertEquals(special?1:2,facts.get("competition.zoneWins.p1"));
        assertEquals(special?1:0,facts.get("competition.zoneWins.p2"));
        if(special)assertEquals(1,facts.get("competition.specialStage"));
        if(simultaneous) for(int act=0;act<2;act++) {
            assertTrue(starts[act][0]>=0 && starts[act][1]>=0);
            assertTrue(Math.max(starts[act][0],starts[act][1])<Math.min(finishes[act][0],finishes[act][1]),
                    "both humans run before either finishes act "+act);
            assertTrue(neutralBoardRows[act]>=180,"readable results for act "+act);
        }
    }
    @ParameterizedTest @ValueSource(strings={"sonic-1up","tails-1up","robotnik","super-ring",
            "speed-shoes","shield","invincibility","teleport"})
    void frozenQuestionMarkOutcomesAreAwardedAndHeld(String name) throws Exception {
        int content=List.of("sonic-1up","tails-1up","robotnik","super-ring","speed-shoes","shield",
                "invincibility","teleport").indexOf(name)+1;
        var route=resource("ehz-qmark-"+name);
        assertEquals(resource("ehz-qmark-sonic-1up").tape().slice(0,1512),route.tape().slice(0,1512));
        assertTrue(route.tape().slice(route.tape().size()-360,route.tape().size()).rows().stream()
                .allMatch(PadRow.NEUTRAL::equals),"six-second neutral display hold");
        assertTrue(route.manifest().qualification().stream().anyMatch(q->q.scope()==ReplayScope.BIZHAWK
                && q.passed() && q.coverage().contains("qmark-content-"+content+"-award")));
        int[] breakRow={-1},initRow={-1},lfc={-1};
        var result=new RouteReplay(new StageplayCompetitionRouteHosts(Path.of(System.getProperty("sonic2.rom.path"))))
                .replay(route,Cancellation.NONE,(row,sample)->{
                    var f=sample.facts();
                    if(breakRow[0]<0 && f.getOrDefault("competition.monitor.spawned",0)==1)breakRow[0]=row;
                    if(initRow[0]<0 && f.containsKey("competition.monitor.content")) {
                        initRow[0]=row;lfc[0]=f.get("competition.monitor.initFrame");
                        assertEquals(content,f.get("competition.monitor.content"));assertEquals(content,(lfc[0]&7)+1);
                        assertEquals(1,f.get("competition.monitor.breaker"));
                    }
                });
        var passed=assertInstanceOf(ReplayResult.Passed.class,result,result.toString());
        assertTrue(breakRow[0]>1512 && initRow[0]>breakRow[0]);
        var facts=passed.end().facts();assertEquals(1,facts.get("competition.phase"));
        assertEquals(0,facts.get("competition.p1.timerPaused"));assertEquals(0,facts.get("competition.p2.timerPaused"));
        assertEquals(1,facts.get("competition."+(content==2?"p2":"p1")+".monitors"));
        if(content==1)assertEquals(4,facts.get("competition.p1.lives"));
        if(content==2)assertEquals(4,facts.get("competition.p2.lives"));
        if(content==8)assertTrue(passed.end().p2().orElseThrow().centreX()-passed.end().p1().centreX()>500);
        System.out.printf("QMARK %s breakRow=%d initRow=%d initLfc=%d rows=%d%n",name,breakRow[0],initRow[0],lfc[0],route.tape().size());
    }
}
