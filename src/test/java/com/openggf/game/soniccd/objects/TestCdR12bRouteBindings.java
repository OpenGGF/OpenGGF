package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.media.*;
import com.openggf.game.soniccd.player.*;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual USA source bytes and explicitly constructed component ingresses, never World/route authority. Java UNRUN. */
class TestCdR12bRouteBindings {
    static CdR12bTerrainAssets terrain,independent;static CdR12bPlayerAssets player;
    @BeforeAll static synchronized void load()throws Exception{if(terrain!=null)return;String root=System.getProperty("soniccd.media.root");org.junit.jupiter.api.Assumptions.assumeTrue(root!=null&&!root.isBlank(),"Explicit Sonic CD media root");Path cue;try(var files=Files.list(Path.of(root).resolve("USA"))){var cues=files.filter(f->f.toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();}try(var programs=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){terrain=CdR12bTerrainAssets.fromPrograms(programs);independent=CdR12bTerrainAssets.fromPrograms(programs);player=CdR12bPlayerAssets.fromPrograms(programs);}}
    @Test void actualAssetValuesRetainEveryNativeRootAndMixedEndHullWithoutDuplicatingOwner() {
        var o=terrain.objects();assertEquals(0x20937C,CdSpinningDisc.Assets.fromAct2PastAssets(o).mappingAddressBits());assertEquals(0x21D6C4,CdTunnelDoor.Assets.fromAct2PastAssets(o).mappingsAddress());assertEquals(0x21D528,CdSpinTunnelTag.Assets.fromAct2PastAssets(o).mappingsAddress());
        var big=CdBigRing.Assets.fromAct2PastAssets(o);assertSame(o.bigRing(),big.ring());assertSame(o.bigRingFlash(),big.flash());assertEquals(0x21D8F6,big.ringMappingsAddress());assertEquals(0x20D652,big.flashMappingsAddress());assertSame(o.view(CdR12bObjectAssets.View.ANI_BIG_RING),big.ringAnimations());
        assertSame(o.goalSignpost(),CdGoalPost.Assets.fromAct2PastAssets(o).visual());assertSame(o.goalSignpost(),CdSignpost.Assets.fromAct2PastAssets(o).visual());assertEquals(72,CdTunnelDoor.Assets.fromAct2PastAssets(o).objectTileIds().length());
        assertThrows(IllegalArgumentException.class,()->new CdTunnelDoorVertical.Assets(o.tunnelDoorVertical(),0x20CB52,o.view(CdR12bObjectAssets.View.OBJECT_TILE_IDS)));
    }
    @Test void allSevenExactPlacementSourceGatesPrecedeMetadataAndEveryComponentPort() {
        var actual=placement();var before=actual.capture();for(var p:List.of(new CdObjectPlacementRuntime(terrain.placements()),CdObjectPlacementRuntime.fromAssets(independent,bank()))){
            assertThrows(IllegalArgumentException.class,()->CdSpinningDisc.fromAct2PastAssets(p,terrain,null,null,null,null,null,null));assertThrows(IllegalArgumentException.class,()->CdTunnelDoor.fromAct2PastAssets(p,terrain,null,null,null,null,null,null));assertThrows(IllegalArgumentException.class,()->CdSpinTunnelTag.fromAct2PastAssets(p,terrain));assertThrows(IllegalArgumentException.class,()->CdTunnelDoorVertical.fromAct2PastAssets(p,terrain,null,null,null,null));assertThrows(IllegalArgumentException.class,()->CdBigRing.fromAct2PastAssets(p,terrain,null,null,null,null,null,null));assertThrows(IllegalArgumentException.class,()->CdGoalPost.fromAct2PastAssets(p,terrain,null,null,null,null,null));assertThrows(IllegalArgumentException.class,()->CdSignpost.fromAct2PastAssets(p,terrain,null,null,null,null));
        }assertThrows(IllegalArgumentException.class,()->CdSpinTunnelTag.fromAct2PastAssets(actual,independent));nonwrites(before,actual,Set.of());
    }
    @Test void legacyPublicDtoAndConstructorShapesRemainUnchanged() {
        assertEquals(4,CdSpinningDisc.Assets.class.getRecordComponents().length);assertEquals(3,CdTunnelDoorVertical.Assets.class.getRecordComponents().length);assertEquals(6,CdBigRing.Assets.class.getRecordComponents().length);assertEquals(3,CdGoalPost.Assets.class.getRecordComponents().length);assertEquals(4,CdSignpost.Assets.class.getRecordComponents().length);
        for(var c:List.of(CdSpinningDisc.class,CdTunnelDoorVertical.class,CdSpinTunnelTag.class,CdBigRing.class,CdGoalPost.class,CdSignpost.class))assertEquals(1,c.getConstructors().length,c.getName());
    }
    @Test void spinTunnelPureTargetIngressStoresActualRawPowerupRootWithoutOwningPublication() {
        var p=placement();seed(p,0x34);var before=p.capture();var body=CdSpinTunnelTag.fromAct2PastAssets(p,terrain);var motion=frame(2,0,0x7000,0x7000).state().motion();var result=body.step(p.pool().state(33),motion,new CdSpinTunnelTag.Gates(0,0,0,0));assertEquals(0x21D528,result.object().longBits(MAPPINGS));assertEquals(0x544,result.object().word(TILE));assertEquals(2,result.object().routine());assertEquals(motion,result.player().finalMotion());nonwrites(before,p,Set.of());
    }
    @Test void spinTunnelMalformedRoutineFailsBeforeAnyValueOrOwnerWrite() {
        var p=placement();seed(p,0x34);p.pool().write(p.pool().state(33).moveByte(ROUTINE,3));var before=p.capture();var b=CdSpinTunnelTag.fromAct2PastAssets(p,terrain);assertThrows(CdSpinTunnelTag.UnsupportedSpinTunnelTag.class,()->b.step(p.pool().state(33),frame(2,0,0x7000,0x7000).state().motion(),new CdSpinTunnelTag.Gates(0,0,0,0)));nonwrites(before,p,Set.of());
    }
    @Test void bigRingConstructorAndBelowFiftyUsaGateDoNotRunLaterOwnersOrDraw() {
        var p=placement();seed(p,0x14);var before=p.capture();var b=CdBigRing.fromAct2PastAssets(p,terrain,c->switch(c.field()){case RINGS_WORD->49;case CAMERA_X_WORD->0x1000;default->0;},null,null,null,null,c->fail("USA below50 cannot draw"));nonwrites(before,p,Set.of());b.run(33);nonwrites(before,p,Set.of());
    }
    @Test void bigRingActualInitUsesSeparateNativeRingRootAndLiteral2488() {
        var p=placement();seed(p,0x14);var before=p.capture();int[] draws={0};var b=CdBigRing.fromAct2PastAssets(p,terrain,TestCdR12bRouteBindings::bigRead,null,null,null,null,c->{draws[0]++;clearDraw(p,c.expectedSource());});b.run(33);assertEquals(0x21D8F6,p.pool().state(33).longBits(MAPPINGS));assertEquals(0x2488,p.pool().state(33).word(TILE));assertEquals(2,p.pool().state(33).routine());assertEquals(1,draws[0]);nonwrites(before,p,Set.of(33));
    }
    @Test void flashDeleteKeepsItsNativeUnconditionalDrawOnZeroSlotAndDoesNotDispatchParent() {
        var p=placement();seed(p,0x14);p.pool().write(p.pool().state(33).moveByte(SUBTYPE,1).moveByte(ROUTINE,4));var before=p.capture();int[] draws={0};var b=CdBigRing.fromAct2PastAssets(p,terrain,c->{throw new AssertionError("flash4 has no scalar read");},null,null,null,null,c->{draws[0]++;assertArrayEquals(new byte[64],c.expectedSource().copyBytes());});b.run(33);assertEquals(1,draws[0]);assertArrayEquals(new byte[64],p.pool().state(33).copyBytes());nonwrites(before,p,Set.of(33));
    }
    @Test void badBigRingRoutineStopsAfterActuallyReachedRingsReadWithoutNativeStores() {
        var p=placement();seed(p,0x14);p.pool().write(p.pool().state(33).moveByte(ROUTINE,3));var before=p.capture();var b=CdBigRing.fromAct2PastAssets(p,terrain,TestCdR12bRouteBindings::bigRead,null,null,null,null,c->fail("unreached"));assertThrows(CdBigRing.UnsupportedBigRing.class,()->b.run(33));nonwrites(before,p,Set.of());
    }
    @Test void goalActualPastActTwoIndexChooses8381AndSharedFrameFiveWithoutAnyPlc() {
        var p=placement();seed(p,0x16);var before=p.capture();int[] draws={0};var b=CdGoalPost.fromAct2PastAssets(p,terrain,c->0,c->{throw new AssertionError("far player");},c->{throw new AssertionError("unreached PLC");},null,c->{draws[0]++;clearDraw(p,c.expectedSource());});b.run(33,new CdGoalPost.Globals(1,0,0,0,0x1000));var s=p.pool().state(33);assertEquals(0x21D9B4,s.longBits(MAPPINGS));assertEquals(0x8381,s.word(TILE));assertEquals(5,s.unsignedByte(MAP_FRAME));assertEquals(2,s.routine());assertEquals(1,draws[0]);nonwrites(before,p,Set.of(33));
    }
    @Test void goalReachedOwnerFailurePreservesRoutineFourAndStopsBeforePlcOrDraw() {
        var p=placement();seed(p,0x16);p.pool().write(p.pool().state(33).moveByte(ROUTINE,2));var before=p.capture();var fault=new Fault();var b=CdGoalPost.fromAct2PastAssets(p,terrain,c->c.field()==CdGoalPost.PlayerField.X_WORD?0x1001:0x200,c->{throw fault;},c->{throw new AssertionError("after failed goal owner");},null,c->fail("after failed goal owner"));assertSame(fault,assertThrows(Fault.class,()->b.run(33,new CdGoalPost.Globals(1,0,0,0,0x1000))));assertEquals(4,p.pool().state(33).routine());nonwrites(before,p,Set.of(33));
    }
    @Test void signpostActualInitUsesSharedRootAnd843cWithoutEndProgrammeWork() {
        var p=placement();seed(p,0x17);var before=p.capture();int[] draws={0};var b=CdSignpost.fromAct2PastAssets(p,terrain,c->0,null,null,c->{draws[0]++;clearDraw(p,c.expectedSource());});b.run(33);assertEquals(0x21D9B4,p.pool().state(33).longBits(MAPPINGS));assertEquals(0x843C,p.pool().state(33).word(TILE));assertEquals(2,p.pool().state(33).routine());assertEquals(1,draws[0]);nonwrites(before,p,Set.of(33));
    }
    @Test void signpostReachedShoesStoreFailureKeepsSourceTimerAndStopsBeforeSound() {
        var p=placement();seed(p,0x17);p.pool().write(p.pool().state(33).moveByte(ROUTINE,2));var before=p.capture();var fault=new Fault();var order=new ArrayList<CdSignpost.StoreField>();var b=CdSignpost.fromAct2PastAssets(p,terrain,c->switch(c.field()){case PLAYER_X_WORD->0x1001;case PLAYER_Y_WORD->0x200;case CAMERA_X_WORD->0x1000;default->0;},c->{order.add(c.field());if(c.field()==CdSignpost.StoreField.SPEED_SHOES_BYTE)throw fault;},c->{throw new AssertionError("after failed store");},c->fail("after failed store"));assertSame(fault,assertThrows(Fault.class,()->b.run(33)));assertEquals(List.of(CdSignpost.StoreField.LEFT_BOUND_WORD,CdSignpost.StoreField.DEST_LEFT_BOUND_WORD,CdSignpost.StoreField.UPDATE_HUD_TIME_BYTE,CdSignpost.StoreField.SPEED_SHOES_BYTE),order);assertEquals(4,p.pool().state(33).routine());assertEquals(120,p.pool().state(33).unsignedByte(LEVEL_END_TIMER));nonwrites(before,p,Set.of(33));
    }
    @Test void signpostUnknownRoutineRejectsBeforeAnyNativeReadStoreOrDraw() {
        var p=placement();seed(p,0x17);p.pool().write(p.pool().state(33).moveByte(ROUTINE,9));var before=p.capture();var b=CdSignpost.fromAct2PastAssets(p,terrain,c->{throw new AssertionError("unreached");},null,null,c->fail("unreached"));assertThrows(CdSignpost.UnsupportedSignpost.class,()->b.run(33));nonwrites(before,p,Set.of());
    }
    @Test void goalReachedBadNativeBaseIndexStopsAfterHeaderRatherThanNormalizingPeriod() {
        var p=placement();seed(p,0x16);var before=p.capture();var body=CdGoalPost.fromAct2PastAssets(p,terrain,c->{throw new AssertionError("base read precedes player");},c->{throw new AssertionError("unreached");},c->{throw new AssertionError("unreached");},null,c->fail("unreached"));var e=assertThrows(CdGoalPost.UnsupportedGoalPost.class,()->body.run(33,new CdGoalPost.Globals(0xFFFF,0xFF,0,0,0x1000)));assertEquals(CdGoalPost.UnsupportedBoundary.BASE_TILE_READ,e.boundary());assertEquals(2,p.pool().state(33).routine());assertEquals(0x21D9B4,p.pool().state(33).longBits(MAPPINGS));nonwrites(before,p,Set.of(33));
    }
    @Test void bigRingRejectsReachedOverwideRingReadBeforeAnyStoreOrDraw() {
        var p=placement();seed(p,0x14);var before=p.capture();var b=CdBigRing.fromAct2PastAssets(p,terrain,c->0x10000,null,null,null,null,c->fail("unreached"));var e=assertThrows(CdBigRing.UnsupportedBigRing.class,()->b.run(33));assertEquals(CdBigRing.UnsupportedBoundary.READ_VALUE_WIDTH,e.boundary());nonwrites(before,p,Set.of());
    }
    @Test void signpostOverwideZoneReadRetainsOnlyTheActualPrefixBeforePriorityAndMapStore() {
        var p=placement();seed(p,0x17);var before=p.capture();var b=CdSignpost.fromAct2PastAssets(p,terrain,c->256,null,null,c->fail("unreached"));var e=assertThrows(CdSignpost.UnsupportedSignpost.class,()->b.run(33));assertEquals(CdSignpost.UnsupportedBoundary.READ_VALUE_WIDTH,e.boundary());assertEquals(2,p.pool().state(33).routine());assertEquals(0x43C,p.pool().state(33).word(TILE));assertEquals(before.pool().slots().get(1).longBits(MAPPINGS),p.pool().state(33).longBits(MAPPINGS));nonwrites(before,p,Set.of(33));
    }
    static int bigRead(CdBigRing.ReadCall c){return switch(c.field()){case RINGS_WORD->50;case CAMERA_X_WORD->0x1000;case PLAYER_X_RADIUS_BYTE->9;case PLAYER_Y_RADIUS_BYTE->19;default->0;};}
    static CdObjectPlacementRuntime placement(){return CdObjectPlacementRuntime.fromAssets(terrain,bank());}static CdAssetBytes bank(){byte[] a=new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES];for(int i=0;i<a.length;i++)a[i]=(byte)(i*29+0x97);return CdAssetBytes.copyOf(a);}
    static void seed(CdObjectPlacementRuntime p,int id){for(int i=32;i<128;i++)p.pool().write(dirty(p.pool().state(i)).moveByte(ID,0));p.pool().write(p.pool().state(33).moveByte(ID,id).moveByte(ROUTINE,0).moveByte(ROUTINE_2,0).moveByte(SUBTYPE,0).moveByte(SUBTYPE2,0).moveByte(ANIMATION,0).moveByte(PREVIOUS_ANIMATION,0).moveByte(ANIMATION_FRAME,0).moveByte(ANIMATION_TIME,0).moveByte(SPRITE_FLAGS,0x80).moveByte(FLAGS,0).moveByte(SAVED_FLAGS_ID,0).moveWord(X,0x1000).moveWord(Y,0x200));}
    static CdSolidObject solid(){return new CdSolidObject(CdPlayerMovement.fromStageSource(CdStagePlayerSource.fromAct2Past(terrain,player)));}
    static CdPlayerFrameRuntime frame(int flags,int stand,int x,int y){return frame(flags,stand,x,y,0x100,0);}
    static CdPlayerFrameRuntime frame(int flags,int stand,int x,int y,int debug,int push){var motion=new CdPlayerMotionState((x<<16)|0x8000,(y<<16)|0x1234,0x123,0,0,2,0x10,flags,4,9,19,0,0,1,0,0,0,0,0,0);var state=new CdPlayerBody.State(motion,new CdPlayerBody.AnimationCursor(0,0,0),new CdPlayerBody.Header(1,0x230F60,0x780,2,24),new CdPlayerBody.Timers(0,0,0,0),new CdPlayerBody.History(CdAssetBytes.copyOf(new byte[256]),0),0x12,new CdPlayerBody.ObjectLinks(push,stand),0x34);var bounds=terrain.loadPlan().bounds();var env=new CdPlayerBody.Environment(new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0x600,12,0x80),new CdPlayerMovement.Bounds(bounds.left(),bounds.right(),bounds.bottom(),0),new CdPlayerMovement.Gates(1,0,0,0,0,0,0),new CdPlayerMovement.Globals(0,96,0x12,0x34,0,4,0,0,0,0)),new CdPlayerBody.Controls(0,0,0,0,0),new CdPlayerBody.Globals(0,160,0,debug,0,0,0,0),CdPlayerBody.DynamicSlots.UNIMPLEMENTED);return CdPlayerFrameRuntime.fromAct2PastEntry(terrain,player,state,env,new CdPlayerArtRuntime.State(new byte[768],new byte[768],0,false),new CdPlayerFrameRuntime.VIntClock(OptionalLong.empty(),17));}
    static CdSolidObject.Player actor(CdPlayerFrameRuntime f){var s=f.state();var h=s.header();return new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(s.motion(),h.tile(),h.priority(),s.timers().hurt(),s.rotationDistance()),h.slotId(),s.objectLinks().pushObject(),s.objectLinks().standObject());}
    static CdSolidObject.Globals globals(CdPlayerFrameRuntime f){var g=f.environment().movement().globals();return new CdSolidObject.Globals(f.environment().globals().invincible(),g.scoreChain(),g.primaryAngle(),g.secondaryAngle());}
    static CdSolidObject.Gates gates(CdPlayerFrameRuntime f){return new CdSolidObject.Gates(f.environment().globals().debugMode(),f.environment().movement().gates().miniSonic());}
    static void clearDraw(CdObjectPlacementRuntime p,CdObjectState s){p.pool().write(s.moveByte(SPRITE_FLAGS,s.spriteFlags()&127));}
    static void nonwrites(CdObjectPlacementRuntime.Snapshot b,CdObjectPlacementRuntime p,Set<Integer> changed){assertEquals(b.status(),p.status());assertArrayEquals(b.savedFlags().copy(),p.capture().savedFlags().copy());for(var s:b.pool().slots())if(!changed.contains(s.identity().wholeIndex()))assertEquals(s,p.pool().state(s.identity().wholeIndex()));}
    static CdObjectState dirty(CdObjectState s){try{byte[] b=new byte[64];for(int i=0;i<64;i++)b[i]=(byte)(s.identity().wholeIndex()*13+i*7);var f=CdObjectState.class.getDeclaredField("owner");f.setAccessible(true);var c=CdObjectState.class.getDeclaredConstructor(Object.class,CdObjectState.Identity.class,byte[].class);c.setAccessible(true);return c.newInstance(f.get(s),s.identity(),b);}catch(Exception e){throw new AssertionError(e);}}
    static final class Fault extends RuntimeException {private static final long serialVersionUID=1L;}
}
