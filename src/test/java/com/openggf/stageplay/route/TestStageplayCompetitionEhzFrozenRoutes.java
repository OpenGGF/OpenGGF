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
        assertEquals(resource("ehz-zone-results").tape().slice(0,10010),route.tape().slice(0,10010));
        assertTrue(route.tape().rows().stream().anyMatch(row->row.p2()!=0));
        assertEquals(1,route.manifest().nativeFacts().inlets().size());
        var inlet=route.manifest().nativeFacts().inlets().getFirst();
        assertEquals(10009,inlet.engineFromRows());assertEquals(10010,inlet.anchor().engineAnchorRows());
        assertEquals(10364,inlet.anchor().prefixRows());
        assertTrue(route.manifest().qualification().stream().anyMatch(q->q.scope()==ReplayScope.BIZHAWK && q.passed() && !q.coverage().isEmpty()));
        boolean[] seen={false,false};RouteSample[] previous={null};
        var result=new RouteReplay(new StageplayCompetitionRouteHosts(Path.of(System.getProperty("sonic2.rom.path"))))
                .replay(route,Cancellation.NONE,(row,sample)->{
                    if(sample.facts().getOrDefault("competition.phase",-1)==1) {
                        int act=sample.facts().get("competition.act");if(act<2)seen[act]=true;
                    }
                    if(row>=1200 && route.tape().row(row).held(PlayerSlot.P1,PhysicalButton.START))
                        assertEquals(1,previous[0].facts().getOrDefault("input.menu",0),"no authored ACT pause/unpause workaround");
                    previous[0]=sample;
                });
        var passed=assertInstanceOf(ReplayResult.Passed.class,result,result.toString());
        assertTrue(seen[0] && seen[1]);var facts=passed.end().facts();
        boolean special=name.endsWith("special");
        assertEquals(special?5:3,facts.get("competition.phase"));
        assertEquals(special?1:2,facts.get("competition.zoneWins.p1"));
        assertEquals(special?1:0,facts.get("competition.zoneWins.p2"));
        if(special)assertEquals(1,facts.get("competition.specialStage"));
    }
}
