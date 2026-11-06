package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;

import static com.openggf.game.soniccd.SonicCdAct2PastPrimaryPublication.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Actual-source Frame/Body component products; synthetic metadata controls are explicitly named. Java UNRUN. */
class TestSonicCdAct2PastPrimaryPublication {
    static CdR12bTerrainAssets terrain;
    static CdR12bPlayerAssets player;
    @BeforeAll static void actualOriginalUsa() throws Exception {
        String root=System.getProperty("soniccd.media.root");assumeTrue(root!=null&&!root.isBlank(),"Set soniccd.media.root to original USA media");
        Path cue;try(var files=Files.list(Path.of(root).resolve("USA"))){var cues=files.filter(p->p.toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();}
        try(var programs=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){
            terrain=CdR12bTerrainAssets.fromPrograms(programs);player=CdR12bPlayerAssets.fromPrograms(programs);
        }
    }
    @Test void purePackageHelperHasNoOwnersOrRetainedFieldsAndRequiresCompleteIngress() {
        assertTrue(Modifier.isFinal(SonicCdAct2PastPrimaryPublication.class.getModifiers()));assertFalse(Modifier.isPublic(SonicCdAct2PastPrimaryPublication.class.getModifiers()));
        assertEquals(0,SonicCdAct2PastPrimaryPublication.class.getDeclaredFields().length);assertEquals(6,Plan.class.getRecordComponents().length);
        var f=returned(0x81,false,false,false);var before=f.placement.capture();
        assertThrows(NullPointerException.class,()->prepare(null,f.base.pool(),f.globals,f.level,f.reserved));
        assertThrows(NullPointerException.class,()->prepare(f.publication,null,f.globals,f.level,f.reserved));
        assertThrows(NullPointerException.class,()->prepare(f.publication,f.base.pool(),null,f.level,f.reserved));
        assertThrows(NullPointerException.class,()->prepare(f.publication,f.base.pool(),f.globals,null,f.reserved));
        assertThrows(NullPointerException.class,()->prepare(f.publication,f.base.pool(),f.globals,f.level,null));
        for(int n:new int[]{0,1983,1985})assertThrows(IllegalArgumentException.class,()->prepare(f.publication,f.base.pool(),f.globals,f.level,bytes(n,0)));
        unchanged(f,before);
    }
    @Test void genuineOrdinaryMainStagesExternalValuesWithoutReadoptingItsFrameActor() {
        var f=returned(0,false,false,false);assertEquals(CdPlayerBody.Boundary.MAIN_RETURN,f.publication.result().boundary());
        var before=f.placement.capture();var actor=f.frame.captureActor();var shared=f.frame.captureShared();var plan=plan(f);
        assertSame(f.publication.result().state(),f.frame.state());assertSame(f.publication.result().environment(),f.frame.environment());
        assertSame(display(f.publication),plan.display());assertSame(f.base.pool(),plan.pool());assertSame(f.globals,plan.globals());
        assertSame(f.level,plan.level());assertSame(f.reserved,plan.reserved());assertEquals(actor,f.frame.captureActor());assertEquals(shared,f.frame.captureShared());
        assertSounds(f.publication,plan);unchanged(f,before);
    }
    @Test void genuineControlledMainKeepsTheOriginalEligibleAndFalseDisplayCuts() {
        for(int control:new int[]{0x81,0xC1}){
            var f=returned(control,false,false,false);assertEquals(CdPlayerBody.Boundary.CONTROLLED_MAIN_RETURN,f.publication.result().boundary());
            var before=f.placement.capture();var plan=plan(f);assertSame(display(f.publication),plan.display());assertEquals(control==0x81,plan.display().eligible());
            assertSame(f.base.pool(),plan.pool());assertSame(f.globals,plan.globals());assertSame(f.level,plan.level());assertSame(f.reserved,plan.reserved());
            var warp=warp(f.publication);assertTrue(warp.result().stores().isEmpty());assertTrue(warp.result().reservedPatches().isEmpty());assertTrue(warp.result().saveResult().isEmpty());
            assertEquals(warp.result().state().timeWarp(),plan.globals().contact().timeWarp());unchanged(f,before);
        }
    }
    @Test void genuineModeFlipperPatchThenItemTouchPublishesOneFinalSameOwnerImage() {
        var f=returned(0,true,false,false);var before=f.placement.capture();var patches=patches(f.publication);assertEquals(1,patches.size());
        var touch=f.publication.result().effects().stream().filter(CdPlayerBody.ObjectTouchResult.class::isInstance).map(CdPlayerBody.ObjectTouchResult.class::cast).findFirst().orElseThrow();
        assertEquals(CdPlayerObjectTouch.Kind.ITEM,touch.result().kind());var finalImage=touch.result().changedSlot().orElseThrow();
        assertEquals(1,finalImage.unsignedByte(ANIMATION));assertEquals(patches.getFirst().patch().before().routine()+2,finalImage.routine());
        var plan=plan(f);assertEquals(finalImage,plan.pool().slots().get(48-32));f.placement.pool().validateRestore(plan.pool());
        assertOther(before.pool(),plan.pool(),Set.of(48));assertSame(f.globals,plan.globals());assertSounds(f.publication,plan);unchanged(f,before);
    }
    @Test void genuineTouchResponseProjectsOnlyItsFinalRingScoreContactAndOwnedSlotValues() {
        var f=returned(1,false,true,false);var before=f.placement.capture();var effect=f.publication.result().effects().stream().filter(CdPlayerBody.ObjectTouchResponse.class::isInstance).map(CdPlayerBody.ObjectTouchResponse.class::cast).findFirst().orElseThrow();
        var response=effect.result();assertFalse(response.changedSlots().isEmpty());assertNotEquals(f.globals.score().score(),response.world().score());
        var plan=plan(f);var old=f.globals;var g=plan.globals();var r=old.ring();var w=response.world();
        assertEquals(new CdRingGlobals(w.rings(),w.lives(),r.livesFlags(),r.updateHudRings(),w.updateHudLives(),r.timeStopTimer(),r.ringAnimationTimer(),r.ringAnimationFrame()),g.ring());
        assertEquals(new CdOpeningObjectRuntime.ScoreGlobals(w.score(),w.nextLifeScore(),w.updateHudScore()),g.score());assertEquals(effect.contact(),g.contact());
        assertUnrelated(old,g);assertSame(f.level,plan.level());assertSame(f.reserved,plan.reserved());var changed=new HashSet<Integer>();
        for(var image:response.changedSlots()){assertEquals(image,plan.pool().slots().get(image.identity().dynamicIndex()));changed.add(image.identity().wholeIndex());}
        assertOther(before.pool(),plan.pool(),changed);assertSounds(f.publication,plan);unchanged(f,before);
        var hurt=returned(1,false,false,false,true);assertEquals(4,hurt.publication.result().state().motion().routine());
        var hurtBefore=hurt.placement.capture();var hurtPlan=plan(hurt);var hurtResponse=hurt.publication.result().effects().stream().filter(CdPlayerBody.ObjectTouchResponse.class::isInstance).map(CdPlayerBody.ObjectTouchResponse.class::cast).findFirst().orElseThrow();
        assertEquals(hurtResponse.contact(),hurtPlan.globals().contact());assertSame(display(hurt.publication),hurtPlan.display());assertSame(hurt.publication.result().state(),hurt.frame.state());unchanged(hurt,hurtBefore);
    }
    @Test void constructedOrderedModeControlsRejectStaleAndForeignCompletePreimages() {
        var f=returned(0,false,false,false);var before=f.placement.capture();var a=f.base.pool().slots().get(48-32);var b=a.moveByte(ANIMATION,1);var c=b.moveByte(ANIMATION,2);
        var p=synthetic(f,List.of(mode(a,b),mode(b,c),display(f.publication)));var result=prepare(p,f.base.pool(),f.globals,f.level,f.reserved);
        assertEquals(c,result.pool().slots().get(48-32));assertOther(f.base.pool(),result.pool(),Set.of(48));
        assertThrows(IllegalArgumentException.class,()->prepare(synthetic(f,List.of(mode(a,b),mode(a,a.moveByte(ANIMATION,2)),display(f.publication))),f.base.pool(),f.globals,f.level,f.reserved));
        var foreign=new CdObjectPool().state(48);assertThrows(IllegalArgumentException.class,()->prepare(synthetic(f,List.of(mode(foreign,foreign.moveByte(ANIMATION,1)),display(f.publication))),f.base.pool(),f.globals,f.level,f.reserved));
        unchanged(f,before);
    }
    @Test void constructedTouchControlsRejectDuplicatesForeignImagesAndLateModeStores() {
        var f=returned(0,true,false,false);var before=f.placement.capture();var effects=f.publication.result().effects();
        var item=effects.stream().filter(CdPlayerBody.ObjectTouchResult.class::isInstance).map(CdPlayerBody.ObjectTouchResult.class::cast).findFirst().orElseThrow();
        var duplicate=new ArrayList<CdPlayerBody.Effect>(effects);duplicate.add(item);assertThrows(IllegalArgumentException.class,()->prepare(synthetic(f,duplicate),f.base.pool(),f.globals,f.level,f.reserved));
        var late=new ArrayList<CdPlayerBody.Effect>(effects);late.add(new CdPlayerBody.ModeEffect(patches(f.publication).getFirst()));assertThrows(IllegalArgumentException.class,()->prepare(synthetic(f,late),f.base.pool(),f.globals,f.level,f.reserved));
        var foreign=returned(0,true,false,false);var foreignItem=foreign.publication.result().effects().stream().filter(CdPlayerBody.ObjectTouchResult.class::isInstance).findFirst().orElseThrow();
        assertThrows(IllegalArgumentException.class,()->prepare(synthetic(f,List.of(foreignItem,display(f.publication))),f.base.pool(),f.globals,f.level,f.reserved));unchanged(f,before);
    }
    @Test void constructedDisplayControlsRequireExactlyOneOriginalConsistentCut() {
        var f=returned(0x81,false,false,false);var before=f.placement.capture();var effects=new ArrayList<CdPlayerBody.Effect>(f.publication.result().effects());effects.removeIf(CdPlayerBody.DisplayEligibility.class::isInstance);
        assertThrows(IllegalArgumentException.class,()->prepare(synthetic(f,effects),f.base.pool(),f.globals,f.level,f.reserved));
        effects.add(display(f.publication));effects.add(display(f.publication));assertThrows(IllegalArgumentException.class,()->prepare(synthetic(f,effects),f.base.pool(),f.globals,f.level,f.reserved));
        var d=display(f.publication);var falseCut=new CdPlayerBody.DisplayEligibility(!d.eligible(),d.centreXWord(),d.centreYWord(),d.yRadius(),d.spriteFlags(),d.width(),d.priority());
        assertThrows(IllegalArgumentException.class,()->prepare(synthetic(f,List.of(falseCut)),f.base.pool(),f.globals,f.level,f.reserved));unchanged(f,before);
    }
    @Test void constructedWrittenWarpProductsStopAtPreciseComponentPublicationBoundaries() throws Exception {
        var f=returned(0x81,false,false,false);var before=f.placement.capture();var state=warp(f.publication).result().state();
        var saved=CdTimeWarpSaveData.State.supplied(bytes(42,0),bytes(42,0));var save=new CdPlayerTimeWarp.SaveResult(0,0,0,0,0,0,saved,saved,List.of());
        assertWarpBoundary(f,new CdPlayerTimeWarp.Result(state,List.of(),List.of(),Optional.of(save)),Boundary.TIME_WARP_SAVE_PUBLICATION);
        assertWarpBoundary(f,new CdPlayerTimeWarp.Result(state,List.of(new CdPlayerTimeWarp.ReservedWrite(12,CdPlayerTimeWarp.ReservedField.ID,0,3)),List.of()),Boundary.TIME_WARP_RESERVED_PUBLICATION);
        var ctor=CdReservedObjectBank.class.getDeclaredConstructor();ctor.setAccessible(true);var bank=ctor.newInstance();var star=bank.state(12);
        var patch=new CdPlayerTimeWarp.ReservedPatch(star,star.moveByteAt(0,3).moveByteAt(0x1C,5));
        assertWarpBoundary(f,new CdPlayerTimeWarp.Result(state,List.of(),List.of(patch)),Boundary.TIME_WARP_RESERVED_PUBLICATION);
        assertWarpBoundary(f,new CdPlayerTimeWarp.Result(state,List.of(),List.of(),Optional.empty(),Optional.of(new CdPlayerTimeWarp.RestartResult(0,0x100))),Boundary.TIME_WARP_RESTART_PUBLICATION);
        assertWarpBoundary(f,new CdPlayerTimeWarp.Result(state,List.of(new CdPlayerTimeWarp.GlobalWrite(CdPlayerTimeWarp.GlobalField.TIMER_WORD,0,1)),List.of()),Boundary.TIME_WARP_SCALAR_PUBLICATION);unchanged(f,before);
    }
    @Test void actualInitAndConstructedLifecycleProductsNeverGainPrimaryMainPublication() {
        var init=returned(0,false,false,true);assertEquals(Boundary.NON_MAIN_RESULT,assertThrows(UnsupportedPrimaryPublication.class,()->plan(init)).boundary());
        var f=returned(0x81,false,false,false);var before=f.placement.capture();
        for(var effect:List.<CdPlayerBody.Effect>of(new CdPlayerBody.LevelWrite(CdPlayerBody.LevelField.LEVEL_RESTART,0x100),new CdPlayerBody.ResetSavedObjectFlagsRequest(),new CdPlayerBody.UncheckedAliasWrite(0xFF1200,0),new CdPlayerBody.Z80RamWrite(0x1C3E,1))){
            var pub=synthetic(f,List.of(effect,display(f.publication)));assertEquals(Boundary.PRIMARY_LIFECYCLE_PUBLICATION,assertThrows(UnsupportedPrimaryPublication.class,()->prepare(pub,f.base.pool(),f.globals,f.level,f.reserved)).boundary());
        }
        unchanged(f,before);
    }
    @Test void originalFrameSoundOrderIsWrappedOnceAndPurePreparationNeverRearmsBodyOrMovesItsActor() {
        var f=returned(0,true,false,false);assertTrue(f.publication.sounds().size()>=2);var before=f.placement.capture();var actor=f.frame.captureActor();var shared=f.frame.captureShared();
        var first=plan(f);var again=plan(f);assertSounds(f.publication,first);assertEquals(first.sounds(),again.sounds());assertEquals(first.pool().slots(),again.pool().slots());
        assertThrows(UnsupportedOperationException.class,()->first.sounds().add(new SonicCdGameModule.PastFrameSound(f.publication.sounds().getFirst())));
        assertSame(f.publication.result().state(),f.frame.state());assertSame(f.publication.result().environment(),f.frame.environment());assertEquals(actor,f.frame.captureActor());assertEquals(shared,f.frame.captureShared());
        assertThrows(IllegalStateException.class,()->f.frame.runBody(2));assertFalse(f.frame.closed());unchanged(f,before);
    }
    @Test void noWriteWarpUsesReturnedContactScalarAndDuplicateCallsAreMalformedControls() {
        var f=returned(0x81,false,false,false);var before=f.placement.capture();var warp=warp(f.publication);assertTrue(warp.result().stores().isEmpty());
        var g=f.globals;var different=new SonicCdAct2PastWorldHost.Globals(g.ring(),g.live(),new CdPlayerBody.ContactGlobals(0xA5,g.contact().shield(),g.contact().combineRing()),g.score(),g.projectorByte(),g.checkpointSaved(),g.drownTimerWord(),g.debugBlockWord(),g.pauseMusicBit(),g.bonus(),g.tail(),g.animations(),g.paletteSteps7(),g.paletteTimers7());
        var plan=prepare(f.publication,f.base.pool(),different,f.level,f.reserved);assertEquals(warp.result().state().timeWarp(),plan.globals().contact().timeWarp());
        assertEquals(g.contact().shield(),plan.globals().contact().shield());assertEquals(g.contact().combineRing(),plan.globals().contact().combineRing());
        assertThrows(IllegalArgumentException.class,()->prepare(synthetic(f,List.of(warp,warp,display(f.publication))),f.base.pool(),g,f.level,f.reserved));
        assertThrows(IllegalArgumentException.class,()->prepare(synthetic(f,List.of(new CdPlayerBody.ObjectTouchResult(CdPlayerObjectTouch.NoTouch.INSTANCE),warp,display(f.publication))),f.base.pool(),g,f.level,f.reserved));unchanged(f,before);
    }

    /**
     * Real R12B-bound Frame execution from declared component ingress, not a replay of Root's controller.
     * N1ecc5f3a ObjSonic_Hurt (Level/_Objects/Sonic/Main.asm:2351-2366),
     * ObjSonic_HurtChkLand (Main.asm:2372-2391); ObjSonic_SpecialCol
     * (Level/Palmtree Panic/Player Special Collision.asm:7-8) is the actual RTS.
     */
    @Test void genuineHurtRunsTheNativeTailWithoutModeTimeWarpOrMainTouchAndAlwaysDraws() {
        for(int flags:new int[]{2,0x42})for(int control:new int[]{0,0xC1}){
            var f=returnedRoutine(4,flags,control,false);var result=f.publication.result();
            assertEquals(CdPlayerBody.Boundary.HURT_RETURN,result.boundary());assertTrue(result.modeBoundary().isEmpty());
            assertEquals(List.of(CdPlayerBody.NativeCall.CHARGE_BEFORE_DISPATCH,CdPlayerBody.NativeCall.OBJ_MOVE,
                    CdPlayerBody.NativeCall.PP_SPECIAL_COLLISION,CdPlayerBody.NativeCall.LEVEL_COLLISION_IN_AIR,
                    CdPlayerBody.NativeCall.HURT_CHECK_LAND,CdPlayerBody.NativeCall.LEVEL_BOUND,
                    CdPlayerBody.NativeCall.RECORD_POSITION,CdPlayerBody.NativeCall.ANIMATE,CdPlayerBody.NativeCall.DRAW_OBJECT),calls(f.publication));
            assertFalse(result.effects().stream().anyMatch(e->e instanceof CdPlayerBody.ModeEffect||e instanceof CdPlayerBody.TimeWarpCall
                    ||e instanceof CdPlayerBody.ObjectTouchResult||e instanceof CdPlayerBody.ObjectTouchResponse||e instanceof CdPlayerBody.TouchScanCompleted));
            var gravity=result.effects().stream().filter(CdPlayerBody.ActorWrite.class::isInstance).map(CdPlayerBody.ActorWrite.class::cast)
                    .filter(w->w.field()==CdPlayerBody.ActorField.Y_VELOCITY).map(CdPlayerBody.ActorWrite::value).toList();
            assertEquals(flags==2?List.of(-1024+0x30):List.of(-1024+0x30,-1024+0x10),gravity);
            var centreWrites=result.effects().stream().filter(CdPlayerBody.ActorWrite.class::isInstance).map(CdPlayerBody.ActorWrite.class::cast)
                    .filter(w->w.field()==CdPlayerBody.ActorField.CENTRE_X_LONG||w.field()==CdPlayerBody.ActorField.CENTRE_Y_LONG).toList();
            assertEquals(List.of(new CdPlayerBody.ActorWrite(CdPlayerBody.ActorField.CENTRE_X_LONG,513268480+(-512<<8)),
                    new CdPlayerBody.ActorWrite(CdPlayerBody.ActorField.CENTRE_Y_LONG,34384640+(-1024<<8))),centreWrites);
            assertEquals(new CdPlayerBody.Timers(1,1,1,0x3456),result.state().timers(),"Hurt never calls Main Display's timer service");
            assertEquals(0x12,result.environment().controls().logicalHeld());assertEquals(0x34,result.environment().controls().logicalTap());
            assertEquals(1,result.environment().globals().invincible());assertEquals(1,result.environment().movement().gates().speedShoes());
            var cut=display(f.publication);var pose=result.display().orElseThrow();assertTrue(cut.eligible());assertTrue(pose.queued());
            assertEquals(result.state().motion().centreX16_16()>>>16,cut.centreXWord());assertEquals(result.state().motion().centreY16_16()>>>16,cut.centreYWord());
            assertEquals(result.state().motion().spriteFlags(),cut.spriteFlags());assertEquals(pose.spriteFlags(),cut.spriteFlags());
            assertEquals(result.state().motion().yRadius(),cut.yRadius());assertEquals(result.state().header().width(),cut.width());assertEquals(result.state().header().priority(),cut.priority());
            assertSame(cut,plan(f).display());assertSame(result.state(),f.frame.state());assertSame(result.environment(),f.frame.environment());
        }
    }
    @Test void genuineHurtPreparationKeepsEveryExternalImageScalarSoundAndOriginalFrameCut() {
        var f=returnedRoutine(4,2,0,false);var before=f.placement.capture();var actor=f.frame.captureActor();var shared=f.frame.captureShared();
        var source=f.frame.stageSource();var cut=display(f.publication);var pose=f.frame.display();var sounds=f.publication.sounds();
        var originalImages=f.base.pool().slots().stream().map(CdObjectState::copyBytes).toList();var reserved=f.reserved.copy();
        var first=plan(f);var again=plan(f);
        assertSame(f.base.pool(),first.pool());assertSame(f.globals,first.globals());assertSame(f.level,first.level());assertSame(f.reserved,first.reserved());
        assertSame(cut,first.display());assertEquals(first,again);assertSounds(f.publication,first);assertEquals(sounds,f.publication.sounds());
        for(int i=0;i<96;i++)assertArrayEquals(originalImages.get(i),first.pool().slots().get(i).copyBytes());
        assertArrayEquals(reserved,first.reserved().copy());assertSame(source,f.frame.stageSource());assertEquals(actor,f.frame.captureActor());
        assertEquals(shared,f.frame.captureShared());assertSame(pose,f.frame.display());assertSame(f.publication.result().state(),f.frame.state());
        assertSame(f.publication.result().environment(),f.frame.environment());assertThrows(IllegalStateException.class,()->f.frame.runBody(2));unchanged(f,before);
        // Later legitimate owners cannot mutate this old source image or the already selected DrawObject cut.
        var oldImage=f.base.pool().slots().get(16);var oldBytes=oldImage.copyBytes();
        f.placement.pool().write(oldImage.moveByte(ANIMATION,oldImage.unsignedByte(ANIMATION)+1));f.frame.applyObjectYVelocity(0x123);
        assertArrayEquals(oldBytes,oldImage.copyBytes());assertSame(oldImage,first.pool().slots().get(16));assertSame(cut,first.display());
        assertSame(actor.state(),f.publication.result().state());assertSame(pose.orElseThrow(),f.publication.result().display().orElseThrow());
        assertEquals(first,plan(f),"Pure preparation reads its original immutable ingress, never the later live owners");
    }
    @Test void genuineHurtMayReturnAnOutgoingDeadRoutineWithoutAdmittingAnIncomingDeadOrRestartCall() {
        var f=returnedRoutine(4,2,0,true);var result=f.publication.result();var before=f.placement.capture();
        assertEquals(CdPlayerBody.Boundary.HURT_RETURN,result.boundary());assertEquals(6,result.state().motion().routine());
        assertTrue(result.effects().stream().anyMatch(CdPlayerBody.KillPlayerCall.class::isInstance));
        assertFalse(calls(f.publication).contains(CdPlayerBody.NativeCall.DEAD_CHECK_GONE));assertTrue(display(f.publication).eligible());
        assertFalse(f.publication.sounds().isEmpty());var actor=f.frame.captureActor();var shared=f.frame.captureShared();var plan=plan(f);
        assertSame(f.base.pool(),plan.pool());assertSame(f.globals,plan.globals());assertSame(f.level,plan.level());assertSame(f.reserved,plan.reserved());
        assertSame(display(f.publication),plan.display());assertSounds(f.publication,plan);assertEquals(actor,f.frame.captureActor());assertEquals(shared,f.frame.captureShared());unchanged(f,before);
        for(int routine:new int[]{6,8}){
            var incoming=returnedRoutine(routine,2,0,false);var old=incoming.placement.capture();
            assertEquals(routine==6?CdPlayerBody.Boundary.DEAD_RETURN:CdPlayerBody.Boundary.RESTART_RETURN,incoming.publication.result().boundary());
            assertEquals(Boundary.NON_MAIN_RESULT,assertThrows(UnsupportedPrimaryPublication.class,()->plan(incoming)).boundary());unchanged(incoming,old);
        }
    }
    @Test void constructedHurtDisplayControlsRejectMissingDuplicateAndInconsistentCuts() {
        var f=returnedRoutine(4,2,0,false);var before=f.placement.capture();var original=f.publication.result().effects();
        var missing=new ArrayList<CdPlayerBody.Effect>(original);missing.removeIf(CdPlayerBody.DisplayEligibility.class::isInstance);
        assertThrows(IllegalArgumentException.class,()->prepare(synthetic(f,missing),f.base.pool(),f.globals,f.level,f.reserved));
        var duplicate=new ArrayList<CdPlayerBody.Effect>(original);duplicate.add(display(f.publication));
        assertThrows(IllegalArgumentException.class,()->prepare(synthetic(f,duplicate),f.base.pool(),f.globals,f.level,f.reserved));
        var d=display(f.publication);missing.add(new CdPlayerBody.DisplayEligibility(false,d.centreXWord(),d.centreYWord(),d.yRadius(),d.spriteFlags(),d.width(),d.priority()));
        assertThrows(IllegalArgumentException.class,()->prepare(synthetic(f,missing),f.base.pool(),f.globals,f.level,f.reserved));unchanged(f,before);
    }
    @Test void constructedHurtLifecycleAndWrittenWarpControlsKeepTheExistingPublicationFences() {
        var f=returnedRoutine(4,2,0,false);var before=f.placement.capture();
        for(var effect:List.<CdPlayerBody.Effect>of(new CdPlayerBody.LevelWrite(CdPlayerBody.LevelField.LEVEL_RESTART,1),
                new CdPlayerBody.DeadCheckGone(3,4,5,Optional.empty(),Optional.empty()),new CdPlayerBody.RestartCut(f.level,true),
                new CdPlayerBody.ResetSavedObjectFlagsRequest(),new CdPlayerBody.UncheckedAliasWrite(0xFFF000,0x3B),new CdPlayerBody.Z80RamWrite(0x1C3E,1))){
            assertEquals(Boundary.PRIMARY_LIFECYCLE_PUBLICATION,assertThrows(UnsupportedPrimaryPublication.class,
                    ()->prepare(synthetic(f,List.of(effect,display(f.publication))),f.base.pool(),f.globals,f.level,f.reserved)).boundary());
        }
        var warp=warp(returned(0x81,false,false,false).publication).result().state();
        var saved=CdTimeWarpSaveData.State.supplied(bytes(42,0),bytes(42,0));var save=new CdPlayerTimeWarp.SaveResult(0,0,0,0,0,0,saved,saved,List.of());
        assertWarpBoundary(f,new CdPlayerTimeWarp.Result(warp,List.of(),List.of(),Optional.of(save)),Boundary.TIME_WARP_SAVE_PUBLICATION);
        assertWarpBoundary(f,new CdPlayerTimeWarp.Result(warp,List.of(new CdPlayerTimeWarp.ReservedWrite(12,CdPlayerTimeWarp.ReservedField.ID,0,3)),List.of()),Boundary.TIME_WARP_RESERVED_PUBLICATION);
        assertWarpBoundary(f,new CdPlayerTimeWarp.Result(warp,List.of(),List.of(),Optional.empty(),Optional.of(new CdPlayerTimeWarp.RestartResult(0,0x100))),Boundary.TIME_WARP_RESTART_PUBLICATION);
        assertWarpBoundary(f,new CdPlayerTimeWarp.Result(warp,List.of(new CdPlayerTimeWarp.GlobalWrite(CdPlayerTimeWarp.GlobalField.TIMER_WORD,0,1)),List.of()),Boundary.TIME_WARP_SCALAR_PUBLICATION);unchanged(f,before);
    }

    /** Only explicit actor/environment ingress is constructed; every successful product below is the original shared Frame/Body result. */
    private static Fixture returnedRoutine(int routine,int flags,int control,boolean beyondBottom) {
        var placement=CdObjectPlacementRuntime.fromAssets(terrain,bytes(CdObjectPlacementRuntime.SAVED_BANK_BYTES,0xA6));
        for(int whole=32;whole<128;whole++)placement.pool().write(dirty(placement.pool().state(whole)).moveByte(ID,0).moveByte(SPRITE_FLAGS,0).moveByte(COLLISION_TYPE,0));
        var base=placement.capture();var g=globals();var bounds=terrain.loadPlan().bounds();
        int y=beyondBottom?((bounds.bottom()+225)&0xFFFF)<<16:34384640;
        var motion=new CdPlayerMotionState(513268480,y,beyondBottom?0:-512,beyondBottom?0:-1024,0,routine,0,flags,132,9,19,0x1A,0x1A,0,0,0,0,control,0,0);
        var state=new CdPlayerBody.State(motion,new CdPlayerBody.AnimationCursor(0,0,0),new CdPlayerBody.Header(1,0x230F60,0x780,2,24),
                new CdPlayerBody.Timers(1,1,1,0x3456),new CdPlayerBody.History(bytes(256,0x34),0),0,new CdPlayerBody.ObjectLinks(0,0),0);
        var environment=new CdPlayerBody.Environment(new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0x600,0xC,0x80),
                new CdPlayerMovement.Bounds(bounds.left(),bounds.right(),bounds.bottom(),0),new CdPlayerMovement.Gates(1,0,0,0,0,1,0),
                new CdPlayerMovement.Globals(0,96,0,0,0,0,0,0,0,0)),new CdPlayerBody.Controls(0,0,0x12,0x34,0),
                new CdPlayerBody.Globals(0,160,0,0,0,210,1,0),CdPlayerBody.DynamicSlots.UNIMPLEMENTED);
        var level=new CdLevelGlobals(0,0x23,0x34,2,0x45,0x123456,0x56,0,0);
        var frame=CdPlayerFrameRuntime.fromAct2PastEntry(terrain,player,state,environment,new CdPlayerArtRuntime.State(new byte[768],new byte[768],0,false),new CdPlayerFrameRuntime.VIntClock(OptionalLong.empty(),17));
        frame.bindArtPublication((request,image)->fail("initial mapFrame0 already compared; no DPLC cue may be invented"));
        var sample=frame.prepareInput(0x10);frame.serviceVBlank(sample.phase());
        var publication=frame.runBody(1,Optional.empty(),Optional.of(new CdPlayerBody.LevelContext(base.pool(),g.ring(),level)),Optional.empty(),Optional.empty());
        assertTrue(frame.stageSource().ownsAct2Past(terrain,player));assertSame(publication.result().state(),frame.state());assertSame(publication.result().environment(),frame.environment());
        return new Fixture(frame,placement,base,g,level,bytes(1984,0x67),publication);
    }
    private static List<CdPlayerBody.NativeCall> calls(CdPlayerFrameRuntime.BodyPublication p){return p.result().effects().stream().filter(CdPlayerBody.CallReturned.class::isInstance).map(CdPlayerBody.CallReturned.class::cast).map(CdPlayerBody.CallReturned::call).toList();}

    record Fixture(CdPlayerFrameRuntime frame,CdObjectPlacementRuntime placement,CdObjectPlacementRuntime.Snapshot base,
                   SonicCdAct2PastWorldHost.Globals globals,CdLevelGlobals level,CdAssetBytes reserved,CdPlayerFrameRuntime.BodyPublication publication) { }
    private static Fixture returned(int control,boolean flipper,boolean enemy,boolean init) { return returned(control,flipper,enemy,init,false); }
    private static Fixture returned(int control,boolean flipper,boolean enemy,boolean init,boolean hazard) {
        var placement=CdObjectPlacementRuntime.fromAssets(terrain,bytes(CdObjectPlacementRuntime.SAVED_BANK_BYTES,0xA6));
        for(int whole=32;whole<128;whole++)placement.pool().write(dirty(placement.pool().state(whole)).moveByte(ID,0).moveByte(SPRITE_FLAGS,0).moveByte(COLLISION_TYPE,0));
        if(flipper||enemy||hazard)placement.pool().write(placement.pool().state(48).moveByte(ID,flipper?0x1E:0x15).moveByte(SPRITE_FLAGS,0x84)
                .moveByte(COLLISION_TYPE,flipper?0x41:hazard?0x86:6).moveByte(ROUTINE,0).moveByte(ANIMATION,0).moveByte(COLLISION_STATUS,0).moveByte(WIDTH,32).moveWord(X,0x200).moveWord(Y,0x200));
        var g=globals();if(hazard)g=new SonicCdAct2PastWorldHost.Globals(g.ring(),g.live(),new CdPlayerBody.ContactGlobals(0,1,g.contact().combineRing()),g.score(),g.projectorByte(),g.checkpointSaved(),g.drownTimerWord(),g.debugBlockWord(),g.pauseMusicBit(),g.bonus(),g.tail(),g.animations(),g.paletteSteps7(),g.paletteTimers7());var base=placement.capture();var bounds=terrain.loadPlan().bounds();int animation=enemy?2:0;
        var motion=new CdPlayerMotionState(0x02001234,0x02005678,0,0,0,init?0:2,0,flipper?8:enemy?4:2,4,9,19,animation,animation,0,0,0,0,control,0,0);
        var state=new CdPlayerBody.State(motion,new CdPlayerBody.AnimationCursor(0,0,0),new CdPlayerBody.Header(1,0x230F60,0x780,2,24),
                new CdPlayerBody.Timers(0,0,0,0x3456),new CdPlayerBody.History(bytes(256,0x34),0),0,new CdPlayerBody.ObjectLinks(0,flipper?48:0),0);
        var environment=new CdPlayerBody.Environment(new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0x600,0xC,0x80),
                new CdPlayerMovement.Bounds(bounds.left(),bounds.right(),bounds.bottom(),0),new CdPlayerMovement.Gates(1,0,0,0,0,0,0),new CdPlayerMovement.Globals(0,96,0,0,0,0,0,0,0,0)),
                new CdPlayerBody.Controls(0,0,0,0,0),new CdPlayerBody.Globals(0,160,0,0,0,0,0,0),CdPlayerBody.DynamicSlots.UNIMPLEMENTED);
        var frame=CdPlayerFrameRuntime.fromAct2PastEntry(terrain,player,state,environment,new CdPlayerArtRuntime.State(new byte[768],new byte[768],0,false),new CdPlayerFrameRuntime.VIntClock(OptionalLong.empty(),17));
        frame.bindArtPublication((request,image)->fail("initial mapFrame0 already compared; no DPLC cue may be invented"));
        var scanner=new CdPlayerObjectTouch(player.touchDimensions());var response=new CdPlayerTouchResponse(player.view(CdR12bPlayerAssets.Asset.ENEMY_POINTS));var r=g.ring();var score=g.score();
        var touch=new CdPlayerBody.TouchContext(scanner,base.pool(),new CdPlayerBody.ResponseContext(response,g.contact(),new CdPlayerTouchResponse.WorldGlobals(r.rings(),r.lives(),r.updateHudLives(),score.score(),score.nextLifeScore(),score.updateHudScore())));
        var objects=new CdPlayerMovement.ObjectContext(base.pool(),flipper?48:0,1,24);
        var warp=new CdPlayerBody.TimeWarpContext(new CdPlayerTimeWarp.Context(OptionalInt.empty(),Optional.empty()),g.contact());
        var sample=frame.prepareInput(flipper?0x10:0);frame.serviceVBlank(sample.phase());
        var publication=frame.runBody(1,Optional.of(touch),Optional.empty(),Optional.of(objects),Optional.of(warp));
        assertTrue(frame.stageSource().ownsAct2Past(terrain,player));assertSame(publication.result().state(),frame.state());assertSame(publication.result().environment(),frame.environment());
        return new Fixture(frame,placement,base,g,new CdLevelGlobals(0,0x23,0x34,2,0x45,0x123456,0x56,0,0),bytes(1984,0x67),publication);
    }
    private static SonicCdAct2PastWorldHost.Globals globals(){return new SonicCdAct2PastWorldHost.Globals(new CdRingGlobals(99,3,0x56,0x23,0x34,0x5678,0x45,0x56),CdCheckpointLiveAux.unknown(),
            new CdPlayerBody.ContactGlobals(0x23,0x34,0x45),new CdOpeningObjectRuntime.ScoreGlobals(999,1000,0x67),0x45,CdCheckpointSaveData.State.unknown(),
            OptionalInt.empty(),OptionalInt.empty(),OptionalInt.empty(),Optional.empty(),Optional.empty(),Optional.empty(),bytes(7,0x34),bytes(7,0x45));}
    private static Plan plan(Fixture f){return prepare(f.publication,f.base.pool(),f.globals,f.level,f.reserved);}
    private static CdPlayerBody.DisplayEligibility display(CdPlayerFrameRuntime.BodyPublication p){return p.result().effects().stream().filter(CdPlayerBody.DisplayEligibility.class::isInstance).map(CdPlayerBody.DisplayEligibility.class::cast).findFirst().orElseThrow();}
    private static CdPlayerBody.TimeWarpCall warp(CdPlayerFrameRuntime.BodyPublication p){return p.result().effects().stream().filter(CdPlayerBody.TimeWarpCall.class::isInstance).map(CdPlayerBody.TimeWarpCall.class::cast).findFirst().orElseThrow();}
    private static List<CdPlayerMovement.ObjectPatch> patches(CdPlayerFrameRuntime.BodyPublication p){return p.result().effects().stream().filter(CdPlayerBody.ModeEffect.class::isInstance).map(CdPlayerBody.ModeEffect.class::cast).map(CdPlayerBody.ModeEffect::effect).filter(CdPlayerMovement.ObjectPatch.class::isInstance).map(CdPlayerMovement.ObjectPatch.class::cast).toList();}
    private static CdPlayerBody.ModeEffect mode(CdObjectState a,CdObjectState b){return new CdPlayerBody.ModeEffect(new CdPlayerMovement.ObjectPatch(new CdPlayerMovement.SlotPatch(a,b)));}
    /** Constructed metadata control only; never the original producer/call identity required by Root. */
    private static CdPlayerFrameRuntime.BodyPublication synthetic(Fixture f,List<CdPlayerBody.Effect> effects){var r=f.publication.result();return new CdPlayerFrameRuntime.BodyPublication(new CdPlayerBody.Result(r.state(),r.environment(),effects,r.display(),r.boundary(),r.modeBoundary()),f.publication.sounds());}
    private static void assertWarpBoundary(Fixture f,CdPlayerTimeWarp.Result result,Boundary boundary){var p=synthetic(f,List.of(new CdPlayerBody.TimeWarpCall(result),display(f.publication)));assertEquals(boundary,assertThrows(UnsupportedPrimaryPublication.class,()->prepare(p,f.base.pool(),f.globals,f.level,f.reserved)).boundary());}
    private static void assertSounds(CdPlayerFrameRuntime.BodyPublication publication,Plan plan){assertEquals(publication.sounds().size(),plan.sounds().size());for(int i=0;i<plan.sounds().size();i++)assertSame(publication.sounds().get(i),assertInstanceOf(SonicCdGameModule.PastFrameSound.class,plan.sounds().get(i)).sound());}
    private static void assertOther(CdObjectPool.Snapshot old,CdObjectPool.Snapshot next,Set<Integer> changed){assertEquals(96,next.slots().size());for(var image:old.slots())if(!changed.contains(image.identity().wholeIndex()))assertEquals(image,next.slots().get(image.identity().dynamicIndex()));}
    private static void assertUnrelated(SonicCdAct2PastWorldHost.Globals a,SonicCdAct2PastWorldHost.Globals b){assertSame(a.live(),b.live());assertEquals(a.projectorByte(),b.projectorByte());assertSame(a.checkpointSaved(),b.checkpointSaved());assertSame(a.drownTimerWord(),b.drownTimerWord());assertSame(a.debugBlockWord(),b.debugBlockWord());assertSame(a.pauseMusicBit(),b.pauseMusicBit());assertSame(a.bonus(),b.bonus());assertSame(a.tail(),b.tail());assertSame(a.animations(),b.animations());assertSame(a.paletteSteps7(),b.paletteSteps7());assertSame(a.paletteTimers7(),b.paletteTimers7());}
    private static void unchanged(Fixture f,CdObjectPlacementRuntime.Snapshot before){var after=f.placement.capture();assertEquals(before.status(),after.status());assertEquals(before.pool().slots(),after.pool().slots());assertArrayEquals(before.savedFlags().copy(),after.savedFlags().copy());assertFalse(f.frame.closed());}
    private static CdAssetBytes bytes(int length,int value){byte[] b=new byte[length];Arrays.fill(b,(byte)value);return CdAssetBytes.copyOf(b);}
    private static CdObjectState dirty(CdObjectState base){try{byte[] b=new byte[64];for(int i=0;i<64;i++)b[i]=(byte)(base.identity().wholeIndex()*13+i*7);var owner=CdObjectState.class.getDeclaredField("owner");owner.setAccessible(true);var ctor=CdObjectState.class.getDeclaredConstructor(Object.class,CdObjectState.Identity.class,byte[].class);ctor.setAccessible(true);return ctor.newInstance(owner.get(base),base.identity(),b);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
}
