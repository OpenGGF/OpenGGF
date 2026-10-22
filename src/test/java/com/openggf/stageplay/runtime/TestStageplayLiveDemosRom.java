package com.openggf.stageplay.runtime;

import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.mods.scene.host.SceneRomLibrary;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayTimeline;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.StageplayScriptCompiler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Reusable screenplay sources are executable route obligations, not pictures of intended interaction. */
@RequiresRom(SonicGame.SONIC_3K)
class TestStageplayLiveDemosRom {
    @BeforeEach void logicGraphics() { GameServices.graphics().initHeadless(); }
    @Test void relayBoardsRidesAndCompletesTwoConsecutiveNativeTransfers() throws Exception {
        try(var probe=new Probe("live-platform-relay")) {
            var rows=probe.frames();
            assertTrue(rows.get(35).world().actors().stream().allMatch(a->a.ridingLayoutIndex()==179));
            assertTrue(rows.get(190).world().actors().stream().allMatch(a->a.ridingLayoutIndex()==166));
            assertTrue(rows.get(400).world().actors().stream().allMatch(a->a.ridingLayoutIndex()==179));
            assertTrue(rows.stream().flatMap(s->s.world().events().stream()).anyMatch(e->e.kind().equals("LEFT_OBJECT")));
            probe.seek(90); assertEquals(rows.get(400),probe.seek(400));
            assertClean(rows);
        }
    }
    @Test void airTrafficUsesNativeFlightHoverGlideReleaseAndLandingIncludingAccessories() throws Exception {
        try(var probe=new Probe("live-air-traffic")) {
            var rows=probe.frames();
            for(int frame:new int[]{0,95,115,300,440}) for(var actor:rows.get(frame).actors()) {
                var camera=rows.get(frame).camera();
                assertTrue(actor.x()-camera.x()>=20 && actor.x()-camera.x()<300);
                assertTrue(actor.y()-camera.y()>=20 && actor.y()-camera.y()<204,
                        "Native camera bounds are origins, not terrain dimensions: "+frame);
            }
            assertTrue(rows.get(95).puppets().getFirst().airborne());
            assertFalse(rows.get(95).puppets().get(1).airborne(),"Independent controller edges must not jump both actors");
            assertTrue(rows.get(115).world().actors().stream().allMatch(a->a.abilityState()!=0));
            assertTrue(rows.get(300).puppets().stream().filter(p->p.actorId().equals("tails")).allMatch(p->p.accessory()!=null));
            double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
            for(int f=275;f<350;f++) {
                double y=rows.get(f).actors().getFirst().y(); min=Math.min(min,y); max=Math.max(max,y);
                assertTrue(rows.get(f).world().actors().getFirst().abilityTimer()>0);
            }
            assertTrue(max-min<40,"Bounded hovering must stay near its actual entry altitude: "+min+".."+max);
            assertTrue(rows.get(440).puppets().stream().noneMatch(StageplayTimeline.PuppetSample::airborne));
            assertTrue(rows.get(440).world().actors().stream().allMatch(a->a.abilityState()==0));
            assertTrue(rows.get(300).world().actors().getFirst().abilityTimer()<rows.get(115).world().actors().getFirst().abilityTimer());
            probe.seek(60); assertEquals(rows.get(500),probe.seek(500)); assertClean(rows);
        }
    }
    @Test void ringsDisappearOnceAndPlacedHazardCausesNativeSpillKnockbackAndRecovery() throws Exception {
        try(var probe=new Probe("live-ring-run")) {
            var rows=probe.frames();
            assertEquals(3,rows.get(20).world().rings().stream().filter(StageplayTimeline.LiveRing::collected).count());
            assertTrue(rows.get(20).world().actors().stream().allMatch(a->a.rings()==3),"Shared native total, not summed actor credits");
            assertTrue(rows.get(29).world().actors().stream().allMatch(StageplayTimeline.LiveActor::hurt));
            assertTrue(rows.get(30).world().actors().stream().allMatch(a->a.rings()==0));
            assertTrue(rows.get(30).puppets().stream().anyMatch(p->p.velocityX()!=0 && p.velocityY()!=0));
            assertEquals(3,rows.get(31).world().objects().stream().filter(o->o.type().equals("LostRingObjectInstance")).count());
            assertTrue(rows.get(120).world().actors().stream().noneMatch(StageplayTimeline.LiveActor::hurt));
            probe.seek(20); assertEquals(rows.get(200),probe.seek(200)); assertClean(rows);
        }
    }
    @Test void completeCutsceneKeepsItsSixtySecondContractAndReplaysAcrossActAndRosterChanges() throws Exception {
        try(var probe=new Probe("one-more-take")) {
            assertEquals(3600,probe.document.durationFrames());
            var closing=probe.seek(3500);
            probe.seek(2000);
            assertEquals(closing,probe.seek(3500));
            assertEquals(3,closing.world().actors().size());
        }
    }
    private static void assertClean(List<StageplayTimeline.Frame> rows) {
        assertTrue(rows.stream().flatMap(s->s.world().events().stream()).noneMatch(e->e.kind().contains("TIMED_OUT")));
        assertTrue(rows.stream().flatMap(s->s.world().actors().stream()).noneMatch(StageplayTimeline.LiveActor::dead));
    }
    private static final class Probe implements AutoCloseable {
        final StageplayDocument document; final SceneRomLibrary library; final StageplayLiveWorld world;
        Probe(String name) throws Exception {
            document=StageplayScriptCompiler.compile(Files.readAllBytes(Path.of("examples/stageplay-demo/src/main/resources/scenes/"+name+".screenplay.json")));
            library=SceneRomLibrary.open(Map.of("s3k",Path.of(System.getProperty("s3k.rom.path"))));
            world=StageplayLiveWorld.adoptOffline(library,SessionManager.getCurrentGameplayMode());
        }
        StageplayTimeline.Frame seek(int frame) { return world.resolve(document,StageplayTimeline.sample(document,frame)); }
        List<StageplayTimeline.Frame> frames() {
            var rows=new ArrayList<StageplayTimeline.Frame>(); for(int f=0;f<document.durationFrames();f++) rows.add(seek(f)); return rows;
        }
        @Override public void close() { world.close(); library.close(); }
    }
}
