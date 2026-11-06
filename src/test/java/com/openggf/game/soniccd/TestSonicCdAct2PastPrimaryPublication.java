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

/** Actual-source Frame/Body component products; synthetic metadata controls are explicitly named. Java UNRUN. */
class TestSonicCdAct2PastPrimaryPublication {
    static CdR12bTerrainAssets terrain;
    static CdR12bPlayerAssets player;
    @BeforeAll static void actualOriginalUsa() throws Exception {
        String root=System.getProperty("soniccd.media.root");assertNotNull(root);assertFalse(root.isBlank());
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
