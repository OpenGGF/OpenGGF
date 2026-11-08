package com.openggf.stageplay.route;

import com.openggf.route.*;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Acceptance replays committed rows only; it never invokes a planner. */
@Isolated @RequiresRom(SonicGame.SONIC_2)
class TestStageplayCompetitionFrozenRoute {
    @org.junit.jupiter.api.BeforeEach
    void retireAnnotationFixtureBeforeColdBoot() {
        // @RequiresRom opens a gameplay session; the cold host requires an empty worker.
        com.openggf.tests.TestEnvironment.resetEngineOnly();
    }
    static RouteArtifact resource(String name) throws Exception {
        return RouteFiles.read(Path.of(Objects.requireNonNull(TestStageplayCompetitionFrozenRoute.class.getResource(
                "/routes/s2/competition/"+name+".route.json")).toURI()));
    }
    @Test void frozenTwoHumanRowsShowAnOwnedSlotAndAnActualIntermediateFlip() throws Exception {
        var prefix=resource("cnz1-slot-prefix");var route=resource("cnz1-slot-flip");
        assertEquals(prefix.tape(),route.tape().slice(0,prefix.tape().size()),"retain every qualified prefix row");
        assertEquals(StageplayCompetitionRouteHosts.menuPrefix(2),route.tape().slice(0,1200));
        assertTrue(route.tape().rows().stream().anyMatch(row->row.p2()!=0));
        var hosts=new StageplayCompetitionRouteHosts(Path.of(System.getProperty("sonic2.rom.path")));
        boolean[] witnessed={false,false};
        var result=new RouteReplay(hosts).replay(route,Cancellation.NONE,(row,sample)->{
            if(!witnessed[0] && sample.facts().getOrDefault("competition.slot.inUse",0)==1) {
                assertTrue(sample.p1().objectControlled());
                assertEquals(64,sample.p2().orElseThrow().centreX(),"P2 stays human/idle until its authored lane begins");
                var cage=sample.objects().stream().filter(o->o.placement().map(p->p.objectId()==0xd6 && p.subtype()==1).orElse(false)).findFirst().orElseThrow();
                assertTrue(cage.centreX()-sample.cameraX()>=0 && cage.centreX()-sample.cameraX()<320);
                assertTrue(cage.centreY()-sample.cameraY()>=0 && cage.centreY()-sample.cameraY()<216);
                assertTrue(sample.interactions().stream().anyMatch(i->i.participant()==PlayerSlot.P1 && i.owner().equals(cage.instance())));
                witnessed[0]=true;
            }
            if(!witnessed[1] && sample.facts().getOrDefault("competition.flip.visible",0)==1) {
                int pose=sample.facts().get("competition.flip.frame");assertNotEquals(0,pose&3);
                int x=sample.facts().get("competition.flip.screenX"),y=sample.facts().get("competition.flip.screenY");
                int expected=sample.facts().get("competition.flip.rgb");
                int actual=hosts.renderCurrent().argb(x,y)&0xffffff;
                assertEquals(expected,actual);
                assertNotEquals(expected^0x00ff00,actual,"negative control: wrong source colour must fail");
                witnessed[1]=true;
            }
        });
        assertInstanceOf(ReplayResult.Passed.class,result,result.toString());
        assertTrue(witnessed[0],"owned slot in view");assertTrue(witnessed[1],"visible non-flat resident pose");
        var broken=InputTape.of(route.tape().rows().stream().map(r->new PadRow(r.p1(),0)).toList());
        var corrupt=RouteArtifact.of(route.launch(),route.checks(),broken,route.manifest().provenance());
        assertInstanceOf(ReplayResult.Failed.class,new RouteReplay(hosts).replay(corrupt,Cancellation.NONE),
                "negative control: removing the human P2 lane must fail the frozen goal programme");
    }
}
