package com.openggf.game.soniccd;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.CdPlayerBody;
import com.openggf.game.soniccd.resources.CdLevelHud;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static com.openggf.game.soniccd.TestSonicCdPastRingWorld.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * One positive controller-only continuation through returned9856, with full/course replay.
 * Native pin1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff Level End/Main.asm:626-650,708-907;
 * Results/Main.asm:61-69; Object Spawner.asm:329-340. Original USA, shipped FixBugs=0.
 * The observed adjacent native images identify Goal/Signpost/allocation edges; controller inputs
 * alone drive the World. No actor/global/object/art/terrain/camera/clock/queue edits, input search,
 * fallback, failed restore, EGL, native execution or new capture owner is used. Input call numbers
 * and the9856 bound belong only to this fixture and never select production behavior.
 * Existing Goal9598 EGL evidence is separate. This ends before the first Results dispatch at9857
 * and makes no >=50 BigRing touch, Results body, programme transition, LoadMMD or act-completion claim.
 */
@Isolated
class TestSonicCdPastActExitRoute {
    private final TestSonicCdPastLoadingWorld fixture = new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup() { fixture.setup(); }
    @AfterEach void cleanup() { fixture.cleanup(); }

    @Test void naturalGoalSignpostAndLowRingBigRingReturn9856WithUntouchedResultsDelayAndReplayBothScopes() throws Exception {
        var p=ready();var h=p.harness();var owners=new Owners(p);var edges=new Edges();
        for(int call=p.returned()+1;call<=9855;call++) advance(p,call,owners,edges);
        assertEquals(9582,edges.goalCall,"Require the observed actual Goal routine2->4 edge");
        assertTrue(edges.signpostMain>edges.goalCall);assertTrue(edges.spinDone>edges.signpostMain);
        assertEquals(0,edges.resultsCall,"The9855 cut must precede native Results allocation");assertTrue(edges.lowRingReturns>0);

        var registry=h.mode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();
        var before9856=captureNative(h);var beforePool=p.placement().capture();var incoming=row(p);
        owners.healthy(p);assertPlacement(beforePool,p);
        advance(p,9856,owners,edges);
        assertEquals(9856,edges.resultsCall);assertEquals(32,edges.child);assertEquals(33,edges.parent);
        assertTrue(edges.child<edges.parent,"Earlier physical child was already visited as ID0 in this pass");
        assertPendingResults(p,incoming,edges.parent);var after9856=captureNative(h);var afterPool=p.placement().capture();

        registry.restore(full);assertNativeCapture(before9856,h);owners.healthy(p);assertPlacement(beforePool,p);
        step(h,controller(9856));owners.healthy(p);assertNativeCapture(after9856,h);assertPlacement(afterPool,p);assertPendingResults(p,incoming,edges.parent);
        registry.restoreCourse(course);assertNativeCapture(before9856,h);owners.healthy(p);assertPlacement(beforePool,p);
        step(h,controller(9856));owners.healthy(p);assertNativeCapture(after9856,h);assertPlacement(afterPool,p);assertPendingResults(p,incoming,edges.parent);
        // Stop at this healthy returned cut. The old observer's failed9857 is never executed/restored.
    }

    private static final class Edges {
        int goalCall,signpostMain,spinDone,resultsCall,parent=-1,child=-1,lowRingReturns;
    }
    private record Row(CdObjectPlacementRuntime.Snapshot placement,CdPlayerBody.Environment environment,
                       SonicCdPlayerPresentation.ScrollCapture scroll,Object globals,int rings) { }
    private static Row row(Prepared p)throws Exception{return new Row(p.placement().capture(),p.frame().environment(),presentation(p).capturePastScroll(),globals(p),p.harness().module().pastLoadState().ring().rings());}
    private static void advance(Prepared p,int call,Owners owners,Edges e)throws Exception {
        var before=row(p);step(p.harness(),controller(call));owners.healthy(p);var after=p.placement().capture();
        for(var prior:before.placement().pool().slots()) {
            var current=p.placement().pool().state(prior.identity().wholeIndex());
            if(prior.id()==0x16 && prior.routine()==2 && current.id()==0x16 && current.routine()==4) {
                assertEquals(0,e.goalCall);e.goalCall=call;assertGoalStores(p,before,prior,current);
            }
            if(prior.id()==0x17 && current.id()==0x17) {
                if(prior.routine()==2 && current.routine()==4) {
                    assertEquals(0,e.signpostMain);e.signpostMain=call;assertSignpostMain(p,before,prior,current);
                } else if(prior.routine()==4) {
                    if(prior.unsignedByte(LEVEL_END_TIMER)==1) {
                        assertEquals(0,e.spinDone);e.spinDone=call;assertEquals(6,current.routine());assertEquals(60,current.unsignedByte(LEVEL_END_TIMER));assertEquals(3,current.unsignedByte(MAP_FRAME));
                    } else {assertEquals(4,current.routine());assertEquals((prior.unsignedByte(LEVEL_END_TIMER)-1)&255,current.unsignedByte(LEVEL_END_TIMER));}
                } else if(prior.routine()==6) {
                    if(prior.unsignedByte(LEVEL_END_TIMER)==1) {
                        assertEquals(0,e.resultsCall);e.resultsCall=call;e.parent=prior.identity().wholeIndex();
                        e.child=assertPendingResults(p,before,e.parent);assertSignpostResults(p,before,prior,current);
                    } else {assertEquals(6,current.routine());assertEquals((prior.unsignedByte(LEVEL_END_TIMER)-1)&255,current.unsignedByte(LEVEL_END_TIMER));}
                }
            }
            if(prior.id()==0x14 && prior.subtype()==0 && current.id()==0x14) {
                assertTrue(before.rings()<50);
                assertTrue(p.harness().module().pastLoadState().ring().rings()<50,"Only the actual low-ring route is witnessed");
                assertEquals(prior,current,"Native low-ring return has no Init, animation, Draw/BCLR7 or touch store");
                assertEquals(0,current.routine());assertEquals(1,count(p,SonicCdGameModule.PastLoadCall.BIG_RING_BODY));
                var completed=completedCpu(p);
                assertFalse(completed.provenance().stream().anyMatch(v->v.slotRef().wholeIndex()==current.identity().wholeIndex()));
                assertTrue(p.harness().module().pastLoadState().sounds().stream().noneMatch(SonicCdGameModule.PastBigRingFm.class::isInstance));e.lowRingReturns++;
            }
        }
        assertEquals(before.placement().pool().slots().size(),after.pool().slots().size());
    }
    private static void assertGoalStores(Prepared p,Row before,CdObjectState old,CdObjectState done)throws Exception {
        assertEquals(236,old.savedFlagsId());assertEquals(9984,old.centreX());assertEquals(740,old.centreY());
        assertEquals(old.moveByte(ROUTINE,4).moveByte(SPRITE_FLAGS,done.spriteFlags()),done);
        assertEquals(old.spriteFlags()&0x7F,done.spriteFlags()&0x7F);
        int camera=before.scroll().state().cameraX();var env=p.frame().environment();
        assertEquals(camera,env.movement().bounds().left());assertEquals(camera,presentation(p).pastForegroundImage().destLeftBound());
        assertEquals(0,env.globals().timeWarpTimer());assertEquals(0,env.movement().gates().timeWarpDirection());
        var oldContact=(CdPlayerBody.ContactGlobals)actualField(before.globals(),"contact");
        assertEquals(new CdPlayerBody.ContactGlobals(0,oldContact.shield(),oldContact.combineRing()),actualField(globals(p),"contact"));
        int dy=((p.frame().state().motion().centreY16_16()>>>16)-old.centreY()+128)&0xFFFF;
        assertTrue((short)dy>=0 && dy<256);assertTrue((p.frame().state().motion().centreX16_16()>>>16)>=old.centreX());
        assertEquals(1,count(p,SonicCdGameModule.PastLoadCall.GOAL_POST_BODY));
        // Existing GoalPostRoute separately checks this cue's exact source/destination tail and EGL pixels.
        var queue=p.harness().module().pastLoadState().section().queue().bytes();var destinations=new ArrayList<Integer>();
        for(int at=0;at+6<=queue.length() && queue.unsignedLong(at)!=0;at+=6)destinations.add(queue.unsignedWord(at+4));
        assertTrue(destinations.size()>=3);assertEquals(List.of(0x8780,0x9100,0x7DE0),destinations.subList(destinations.size()-3,destinations.size()));
    }
    private static void assertSignpostMain(Prepared p,Row before,CdObjectState old,CdObjectState current)throws Exception {
        int camera=before.scroll().state().cameraX();var env=p.frame().environment();
        assertEquals(239,current.savedFlagsId());assertEquals(10552,current.centreX());assertEquals(756,current.centreY());
        assertEquals(120,current.unsignedByte(LEVEL_END_TIMER));assertEquals(0,current.unsignedByte(MAP_FRAME));
        assertEquals(camera,env.movement().bounds().left());assertEquals(camera,presentation(p).pastForegroundImage().destLeftBound());
        assertEquals(0,p.harness().module().pastLoadState().level().updateHudTime());
        assertEquals(0,env.movement().gates().speedShoes());assertEquals(0,env.globals().invincible());
        assertEquals(old.moveByte(ROUTINE,4).moveByte(LEVEL_END_TIMER,120).moveByte(MAP_FRAME,0).moveByte(SPRITE_FLAGS,current.spriteFlags()),current);
        assertEquals(old.spriteFlags()&0x7F,current.spriteFlags()&0x7F);
        assertEquals(List.of(CdSignpost.Sound.FM_SIGNPOST),signpostSounds(p));assertEquals(1,count(p,SonicCdGameModule.PastLoadCall.SIGNPOST_BODY));
    }
    /** Identify the actual new ID from adjacent complete images; never guess that a body executed. */
    private static int assertPendingResults(Prepared p,Row before,int parent)throws Exception {
        int child=-1,firstFree=-1;
        for(var old:before.placement().pool().slots()) {
            if(old.id()==0 && firstFree<0)firstFree=old.identity().wholeIndex();
            var now=p.placement().pool().state(old.identity().wholeIndex());
            if(old.id()==0 && now.id()==0x3A) {
                assertEquals(-1,child,"Only one native Results allocation occurred");child=old.identity().wholeIndex();
                assertEquals(old.moveByte(ID,0x3A).moveByte(RESULTS_TIMER,16),now,"Only ID and high delay BYTE are allocated; retain all dirty nonwrites including low$33");
                assertEquals(0,now.routine());assertEquals(16,now.unsignedByte(RESULTS_TIMER));
            }
        }
        assertTrue(child>=32);assertEquals(firstFree,child);assertTrue(child<parent);
        assertEquals(0,count(p,SonicCdGameModule.PastLoadCall.RESULTS_BODY),"The earlier physical child is unvisited until the next ordinary call");
        var state=p.placement().pool().state(parent);assertEquals(0x17,state.id());assertEquals(8,state.routine());assertEquals(180,state.unsignedByte(LEVEL_END_TIMER));
        assertEquals(List.of(0x3A,0x17,0x14),p.placement().capture().pool().slots().stream().filter(s->s.id()!=0).map(CdObjectState::id).toList());
        assertEquals(0x14,p.placement().pool().state(34).id());assertEquals(0,p.placement().pool().state(34).routine());
        return child;
    }
    private static void assertSignpostResults(Prepared p,Row before,CdObjectState old,CdObjectState current)throws Exception {
        assertEquals(old.moveByte(ROUTINE,8).moveByte(LEVEL_END_TIMER,180).moveByte(SPRITE_FLAGS,current.spriteFlags()),current);
        assertEquals(old.spriteFlags()&0x7F,current.spriteFlags()&0x7F);
        var controls=p.frame().environment().controls();assertEquals(before.environment().controls().controlLocked()|1,controls.controlLocked());
        assertNotEquals(0x502,p.frame().environment().movement().gates().zoneAct());assertEquals(8,controls.logicalHeld());assertEquals(8,controls.logicalTap());
        assertEquals(8,controls.rawHeld());assertEquals(0,controls.rawTap());
        var expected=new ArrayList<CdSignpost.Sound>();if(before.environment().movement().gates().timeZone()==0)expected.add(CdSignpost.Sound.SCMD_FADEPCM);
        expected.add(CdSignpost.Sound.SCMD_RESULTMUS);assertEquals(expected,signpostSounds(p));
        var live=(CdCheckpointLiveAux.State)actualField(globals(p),"live");int minutes=live.knownByte(0xFF1515).orElseThrow(),seconds=live.knownByte(0xFF1516).orElseThrow();
        var bonus=(CdLevelHud.BonusValues)((Optional<?>)actualField(globals(p),"bonus")).orElseThrow();
        int index=Math.min(((minutes*60+seconds)&0xFFFF)/15,20);
        assertEquals(p.assets().view(CdR11bAssets.Asset.LEVEL_END_TIME_BONUSES).bytes().unsignedWord(index*2),bonus.timeBonusWord());
        assertEquals((p.harness().module().pastLoadState().ring().rings()*100)&0xFFFF,bonus.ringBonusWord());assertEquals(1,bonus.updateHudBonusByte());
    }
    private record Owners(Object world,long generation,Object stage,Object visit,Object pool,Object assets,Object programme,Object goal,Object signpost,Object bigRing,Object results) {
        Owners(Prepared p)throws Exception {this(p.harness().mode().getWorldSession(),p.harness().mode().getLevelManager().getCompletedProductionLoadGeneration(),
                p.frame().stageSource(),p.visit(),p.placement().pool(),p.assets(),p.harness().module().levelProgramState(),actualField(p.visit(),"goalPost"),actualField(p.visit(),"signpost"),actualField(p.visit(),"bigRing"),actualField(p.visit(),"results"));}
        void healthy(Prepared p)throws Exception {
            var h=p.harness();assertFalse(p.frame().closed());assertSame(p.frame(),h.module().requirePlayerFrame());assertSame(world,h.mode().getWorldSession());
            assertEquals(generation,h.mode().getLevelManager().getCompletedProductionLoadGeneration());assertSame(stage,p.frame().stageSource());assertSame(visit,actualField(h.module(),"pastLoading"));
            assertSame(programme,h.module().levelProgramState());
            assertSame(pool,p.placement().pool());assertSame(assets,h.module().pastLoadState().assets());assertSame(goal,actualField(visit,"goalPost"));assertSame(signpost,actualField(visit,"signpost"));
            assertSame(bigRing,actualField(visit,"bigRing"));assertSame(results,actualField(visit,"results"));
            for(String owner:List.of("Goal","Signpost","BigRing","Results")){assertNull(actualField(visit,"active"+owner));String lower=Character.toLowerCase(owner.charAt(0))+owner.substring(1);assertFalse((boolean)actualField(visit,lower+"Port"));assertFalse((boolean)actualField(visit,"drawing"+owner));}
            assertNull(actualField(visit,"drawQueue"));assertEquals(SonicCdGameModule.PastLoadCall.ORDINARY_MAIN_RETURN,h.module().pastLoadState().calls().getLast());
        }
    }
    private static void assertPlacement(CdObjectPlacementRuntime.Snapshot expected,Prepared p) {
        p.placement().validateRestore(expected);var now=p.placement().capture();assertEquals(expected.status(),now.status());assertArrayEquals(expected.savedFlags().copy(),now.savedFlags().copy());
        for(var image:expected.pool().slots())assertEquals(image,p.placement().pool().state(image.identity().wholeIndex()),"Full64 at physical"+image.identity().wholeIndex());
    }
    private static List<CdSignpost.Sound> signpostSounds(Prepared p){return p.harness().module().pastLoadState().sounds().stream().filter(SonicCdGameModule.PastSignpostSound.class::isInstance).map(SonicCdGameModule.PastSignpostSound.class::cast).map(SonicCdGameModule.PastSignpostSound::sound).toList();}
    private static long count(Prepared p,SonicCdGameModule.PastLoadCall call){return p.harness().module().pastLoadState().calls().stream().filter(c->c==call).count();}
    private static SonicCdPlayerPresentation presentation(Prepared p)throws Exception{return (SonicCdPlayerPresentation)actualField(p.harness().module(),"playerPresentation");}
    private static Object globals(Prepared p)throws Exception{return actualField(actualField(p.visit(),"values"),"globals");}
    private static CdObjectDrawQueue.CpuSat completedCpu(Prepared p)throws Exception {
        var loop=actualField(actualField(p.visit(),"values"),"loop");
        return assertInstanceOf(CdObjectDrawQueue.CpuSat.class,((Optional<?>)actualField(loop,"completedDraw")).orElseThrow());
    }

    /** Frozen GoalPostRoute controller recipe; this fixture extends only its positive input bound. */
    private static LogicalInputSnapshot controller(int call) {
        if(call<1 || call>9856)throw new IllegalArgumentException("Positive act-exit prefix ends at9856");
        if(call<=8276)return route(call);
        int directions=8,actions=call<=8308?2:0;
        if(call>=9001 && call<=9203)directions=4;
        else if(call>=9206 && call<=9217)directions=0;
        else if(call>=9218 && call<=9263){directions=2;actions=call==9219?2:0;}
        else if(call>=9336 && call<=9367)actions=2;
        return LogicalInputSnapshot.ofPlayers(PlayerInputState.of(directions,0,actions,0,false,false),PlayerInputState.neutral());
    }
}
