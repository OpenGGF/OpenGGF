package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.media.*;
import com.openggf.game.soniccd.player.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Function;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Genuine retained original R12B metadata with explicit component pool/player inputs. All Java UNRUN. */
class TestCdR12bRemainingInitialBindings {
    static final int SLOT=40, SAVED=7;
    static CdR12bTerrainAssets terrain, equalTerrain;
    static CdR12bPlayerAssets playerAssets;
    static CdR12aTerrainAssets present;
    static CdR11bAssets past;
    @BeforeAll static void originalUsa() throws Exception {
        String root=System.getProperty("soniccd.media.root");assertNotNull(root,"Requires original-USA soniccd.media.root");assertFalse(root.isBlank());
        Path cue;try(var files=Files.list(Path.of(root).resolve("USA"))){var cues=files.filter(p->p.toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();}
        try(var programs=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){
            terrain=CdR12bTerrainAssets.fromPrograms(programs);equalTerrain=CdR12bTerrainAssets.fromPrograms(programs);playerAssets=CdR12bPlayerAssets.fromPrograms(programs);
            present=CdR12aTerrainAssets.fromPrograms(programs);past=CdR11bAssets.fromPrograms(CdTimeWarpPrograms.fromPrograms(programs));
        }
    }
    @Test void additiveFactoriesKeepAllOriginalConstructorsAndAssetRecordShapes() throws Exception {
        Class<?>[] types={CdMonitor.class,CdTimePost.class,CdCollapsePlatform.class,CdAnimal.class,CdRobotGenerator.class,CdPlatform.class};
        int[] ctors={8,5,6,1,8,8},arities={8,5,7,2,10,8};
        for(int i=0;i<types.length;i++){
            assertEquals(1,types[i].getConstructors().length);assertEquals(ctors[i],types[i].getConstructors()[0].getParameterCount());
            var methods=Arrays.stream(types[i].getDeclaredMethods()).filter(m->m.getName().equals("fromAct2PastAssets")).toList();
            // Animal retains its legacy2 factory beside the source-bound active9 closure.
            assertEquals(i==3?List.of(2,9):List.of(arities[i]),methods.stream().map(m->m.getParameterCount()).sorted().toList());
            for(var method:methods){assertTrue(java.lang.reflect.Modifier.isPublic(method.getModifiers()));assertTrue(java.lang.reflect.Modifier.isStatic(method.getModifiers()));}
        }
        assertEquals(CdAnimal.class,CdAnimal.class.getMethod("fromAct2PastAssets",CdObjectPlacementRuntime.class,CdR12bTerrainAssets.class).getReturnType());
        assertEquals(CdAnimal.class,CdAnimal.class.getMethod("fromAct2PastAssets",CdObjectPlacementRuntime.class,CdR12bTerrainAssets.class,CdR12bPlayerAssets.class,CdPlayerMath.class,CdPlayerTerrain.class,CdActiveAnimal.PrimaryAngleCommit.class,CdActiveAnimal.DrawObjectCall.class,CdActiveAnimal.DispatchObserver.class,CdActiveAnimal.ReceiptGuard.class).getReturnType());
        assertEquals(2,CdMonitor.Assets.class.getRecordComponents().length);assertEquals(10,CdCollapsePlatform.Assets.class.getRecordComponents().length);
        assertEquals(5,CdRobotGenerator.Assets.class.getRecordComponents().length);assertEquals(4,CdPlatform.Assets.class.getRecordComponents().length);
    }
    @Test void genuineMetadataBindingsBorrowCompleteNativeR12bRootsAndCachedProducts() {
        var o=terrain.objects();var m=CdMonitor.Assets.fromAct2PastAssets(o);var c=CdCollapsePlatform.Assets.fromAct2PastAssets(o);
        var r=CdRobotGenerator.Assets.fromAct2PastAssets(o);var p=CdPlatform.Assets.fromAct2PastAssets(o);
        assertSame(o.monitorTime(),m.visual());assertEquals(0x20A804,m.mappingsAddress());assertEquals(450,m.visual().mappings().length());assertEquals(136,m.visual().animationTable().length());
        assertSame(o.collapsePlatform1(),c.collapsePlatform1());assertSame(o.collapsePlatform2(),c.collapsePlatform2());assertSame(o.collapsePlatform3(),c.collapsePlatform3());assertSame(o.collapsePlatform4(),c.collapsePlatform4());
        assertEquals(List.of(0x20C2D6,0x20C3A2,0x20C364,0x20C4D8),List.of(c.mappings1(),c.mappings2(),c.mappings3(),c.mappings4()));
        assertSame(o.view(CdR12bObjectAssets.View.COLLAPSE_PLATFORM_SIZES_1),c.sizes1());assertSame(o.view(CdR12bObjectAssets.View.COLLAPSE_PLATFORM_SIZES_2),c.sizes2());
        assertEquals(24,c.sizes1().length());assertEquals(24,c.sizes2().length());
        assertSame(o.robotGenerator(),r.visual());assertSame(o.view(CdR12bObjectAssets.View.ROBOT_GENERATOR_BASE_TILES),r.baseTiles());
        assertSame(o.view(CdR12bObjectAssets.View.ROBOT_GENERATOR_EXPLOSION_SCHEDULE),r.explosionSchedule());assertEquals(0x20E482,r.mappingsAddress());assertEquals(0x20E6C0,r.explosionScheduleAddress());
        assertSame(o.platform(),p.visual());assertSame(o.view(CdR12bObjectAssets.View.PLATFORM_WIDTHS),p.widths());assertSame(o.view(CdR12bObjectAssets.View.PLATFORM_RANGES),p.ranges());assertEquals(0x20C980,p.mappingsAddress());
        assertEquals(72,o.view(CdR12bObjectAssets.View.OBJECT_TILE_IDS).length());
    }
    @Test void collapseRetainsTheExactFiveHundredFiftySixByteNativeAct2PastDataOrder() throws Exception {
        var o=terrain.objects();var digest=MessageDigest.getInstance("SHA-256");
        for(var v:List.of(CdR12bObjectAssets.View.MAP_SPR_COLLAPSE_PLATFORM_1,CdR12bObjectAssets.View.COLLAPSE_PLATFORM_SIZES_1,
                CdR12bObjectAssets.View.MAP_SPR_COLLAPSE_PLATFORM_3,CdR12bObjectAssets.View.MAP_SPR_COLLAPSE_PLATFORM_2,
                CdR12bObjectAssets.View.COLLAPSE_PLATFORM_SIZES_2,CdR12bObjectAssets.View.MAP_SPR_COLLAPSE_PLATFORM_4))digest.update(o.view(v).copy());
        assertEquals("2c75d993368d72994b799b1066ab51d710c3b5655ef17cdbb9ea61a6694d4425",HexFormat.of().formatHex(digest.digest()));
    }
    @Test void everyMetadataFactoryRejectsForeignIdentityBeforeAnyVisualOrRawRead() {
        List<Function<CdR12bObjectAssets,Object>> factories=List.of(CdMonitor.Assets::fromAct2PastAssets,CdCollapsePlatform.Assets::fromAct2PastAssets,CdRobotGenerator.Assets::fromAct2PastAssets,CdPlatform.Assets::fromAct2PastAssets);
        var identity=terrain.identity();
        for(var factory:factories){var bad=mock(CdR12bObjectAssets.class);var foreign=mock(CdR12bTerrainAssets.Identity.class);
            when(foreign.variant()).thenReturn(identity.variant());when(foreign.stage()).thenReturn(CdStagePayload.R12A.stage());when(foreign.file()).thenReturn(CdNativeFile.R12A);when(foreign.domain()).thenReturn(identity.domain());when(bad.identity()).thenReturn(foreign);
            assertThrows(IllegalArgumentException.class,()->factory.apply(bad));verify(bad).identity();verifyNoMoreInteractions(bad);assertThrows(NullPointerException.class,()->factory.apply(null));}
    }
    @Test void sixNamedBodyFactoriesArePureIngressOverTheSameActualPlacement() {
        var h=new H();var before=h.placement.capture();
        for(int family=0;family<6;family++)assertNotNull(binding(family,h.placement,terrain,h));
        assertPlacement(before,h.placement);assertEquals(0,h.draws);assertTrue(h.order.isEmpty());assertTrue(h.publications.isEmpty());assertNull(h.pending);
    }
    @Test void exactSourceGateRejectsPureEqualIdentityAndAllForeignFamiliesBeforeMetadataOrHelpers() {
        var h=new H();assertEquals(terrain.identity(),equalTerrain.identity());assertNotSame(terrain.placements(),equalTerrain.placements());
        assertNull(field(equalTerrain,"objects"));
        var pure=new CdObjectPlacementRuntime(terrain.placements());
        var oldA=CdObjectPlacementRuntime.fromAssets(present,bank());var oldB=CdObjectPlacementRuntime.fromAssets(past,bank());
        for(int family=0;family<6;family++){
            int selected=family;var before=h.placement.capture();
            assertThrows(IllegalArgumentException.class,()->binding(selected,h.placement,equalTerrain,null));
            assertThrows(IllegalArgumentException.class,()->binding(selected,pure,terrain,null));
            assertThrows(IllegalArgumentException.class,()->binding(selected,oldA,terrain,null));
            assertThrows(IllegalArgumentException.class,()->binding(selected,oldB,terrain,null));
            assertPlacement(before,h.placement);assertNull(field(equalTerrain,"objects"));
        }
    }
    @Test void everyNamedBodyFactoryRetainsNullGatesWithoutNativeWork() {
        var h=new H();var before=h.placement.capture();
        for(int family=0;family<6;family++){int f=family;assertThrows(NullPointerException.class,()->binding(f,null,terrain,h));assertThrows(NullPointerException.class,()->binding(f,h.placement,null,h));}
        assertPlacement(before,h.placement);assertEquals(0,h.draws);
    }
    @Test void monitorInitUsesActualRootAndChangesOnlyReachedNativeBytesAndOneSavedByte() {
        var h=new H();h.seed(0x19,0,2);var before=h.placement.capture();var b=h.state();h.monitor();
        var result=h.monitor.runPast(SLOT,h.monitorReads(),h.monitorInputs());
        var expected=b.addByte(ROUTINE,2).moveByte(Y_RADIUS,14).moveByte(X_RADIUS,14).moveLong(MAPPINGS,0x20A804).moveWord(TILE,0x5A8)
                .moveByte(PRIORITY,3).moveByte(SPRITE_FLAGS,4).moveByte(WIDTH,15).moveByte(COLLISION_TYPE,0x46).moveByte(ANIMATION,2);
        assertEquals(expected,result.state());assertEquals(List.of("DRAW"),h.order);assertEquals(0,h.solidCalls);assertOtherSlots(before,h.placement,Set.of(SLOT));assertOnlySaved(before,h.placement,0);
    }
    @Test void monitorInitPublishesPrefixBeforeReachedZoneFailure() {
        var h=new H();h.seed(0x19,0,2);var before=h.placement.capture();var b=h.state();h.failRead="ZONE";h.monitor();
        assertSame(h.failure,assertThrows(IllegalStateException.class,()->h.monitor.runPast(SLOT,h.monitorReads(),h.monitorInputs())));
        assertEquals(b.addByte(ROUTINE,2).moveByte(Y_RADIUS,14).moveByte(X_RADIUS,14).moveLong(MAPPINGS,0x20A804).moveWord(TILE,0x5A8).moveByte(PRIORITY,3),h.state());
        assertArrayEquals(before.savedFlags().copy(),h.placement.capture().savedFlags().copy());assertOtherSlots(before,h.placement,Set.of(SLOT));assertEquals(0,h.draws);
    }
    @Test void monitorUsesTheExistingGenuinePrefixSolidAndPreventsCallbackReentry() {
        var h=new H();h.seed(0x19,2,2);h.put(h.state().moveByte(SPRITE_FLAGS,0x84).moveByte(ROUTINE_2,0));h.stop=1;h.monitor();
        h.drawHook=()->assertThrows(IllegalStateException.class,()->h.monitor.runPast(SLOT,h.monitorReads(),h.monitorInputs()));
        h.monitor.runPast(SLOT,h.monitorReads(),h.monitorInputs());assertEquals(1,h.solidCalls);assertEquals(List.of("PREFLIGHT","SOLID","DRAW"),h.order);
        assertInstanceOf(CdSolidObject.Completed.class,h.lastSolid);assertEquals(CdSolidObject.GetOffReason.PLAYER_DEAD,((CdSolidObject.Completed)h.lastSolid).completedResult().getOffReason().orElseThrow());
    }
    @Test void timePostActualPastContactCommitsDirectionThenSoundThenDrawWithOneBankByte() {
        var h=new H();h.seed(0x19,0,8);h.put(h.state().moveByte(COLLISION_STATUS,1));var before=h.placement.capture();h.post();
        var r=h.post.runPast(SLOT,h.postReads());assertEquals(0x20A804,r.state().longBits(MAPPINGS));assertEquals(4,r.state().routine());assertEquals(60,r.state().unsignedByte(TIME_POST_SPIN));
        assertEquals(0,h.state().unsignedByte(COLLISION_STATUS));assertEquals(255,h.direction);assertEquals(List.of("DIR:0>255","VOICE:SCMD_PASTSFX","DRAW"),h.order);
        assertOnlySaved(before,h.placement,1);assertOtherSlots(before,h.placement,Set.of(SLOT));
    }
    @Test void timePostInitRetainsItsRealPrefixBeforeALazyReadFailure() {
        var h=new H();h.seed(0x19,0,9);var before=h.placement.capture();var b=h.state();h.failRead="ZONE";h.post();
        assertSame(h.failure,assertThrows(IllegalStateException.class,()->h.post.runPast(SLOT,h.postReads())));
        assertEquals(b.addByte(ROUTINE,2).moveByte(Y_RADIUS,32).moveByte(X_RADIUS,14).moveLong(MAPPINGS,0x20A804).moveWord(TILE,0x5A8).moveByte(SPRITE_FLAGS,4).moveByte(PRIORITY,3),h.state());
        assertArrayEquals(before.savedFlags().copy(),h.placement.capture().savedFlags().copy());assertEquals(0,h.draws);
    }
    @Test void dynamicTimeIconKeepsItsExistingReservedOwnerBoundary() {
        var h=new H();h.seed(0x19,0,10);var before=h.placement.capture();h.post();
        assertEquals(CdTimePost.UnsupportedBoundary.TIME_ICON_RESERVED_SLOT,assertThrows(CdTimePost.UnsupportedTimePost.class,()->h.post.runPast(SLOT,h.postReads())).boundary());
        assertPlacement(before,h.placement);assertEquals(0,h.draws);
    }
    @Test void collapseUsesActualMultiAndSingleRootsSizesAndGenuineTopSolid() {
        for(int subtype:new int[]{0,0x80}){
            var h=new H();h.seed(0x20,0,subtype);var before=h.placement.capture();var b=h.state();h.collapse();
            var result=h.collapse.run(SLOT,new CdCollapsePlatform.DespawnGlobals(0,0,0x1200),new CdCollapsePlatform.Inputs(h.player,h.gates,h.globals));
            var expected=b.addByte(ROUTINE,2).moveByte(SPRITE_FLAGS,b.spriteFlags()|4).moveByte(PRIORITY,3).moveWord(TILE,0x44BE)
                    .moveLong(MAPPINGS,subtype==0?0x20C2D6:0x20C3A2).moveByte(MAP_FRAME,0).moveByte(X_RADIUS,subtype==0?40:64).moveByte(WIDTH,subtype==0?40:64).moveByte(Y_RADIUS,subtype==0?34:26);
            assertEquals(expected,h.beforeSolid);assertEquals(1,h.solidCalls);assertEquals(1,h.draws);assertInstanceOf(CdSolidObject.Completed.class,h.lastSolid);
            assertEquals(h.state(),result.state());assertOtherSlots(before,h.placement,Set.of(SLOT));assertArrayEquals(before.savedFlags().copy(),h.placement.capture().savedFlags().copy());
        }
    }
    @Test void collapseBadSizeSelectorRetainsItsNativeHeaderPrefixBeforeFailure() {
        var h=new H();h.seed(0x20,0,15);var b=h.state();var before=h.placement.capture();h.collapse();
        var error=assertThrows(CdCollapsePlatform.UnsupportedCollapsePlatform.class,()->h.collapse.run(SLOT,null,null));assertEquals(CdCollapsePlatform.UnsupportedBoundary.SIZE_DIRECTORY_READ,error.boundary());
        assertEquals(b.addByte(ROUTINE,2).moveByte(SPRITE_FLAGS,b.spriteFlags()|4).moveByte(PRIORITY,3).moveWord(TILE,0x44BE).moveLong(MAPPINGS,0x20C2D6).moveByte(MAP_FRAME,15),h.state());
        assertEquals(0,h.solidCalls);assertEquals(0,h.draws);assertArrayEquals(before.savedFlags().copy(),h.placement.capture().savedFlags().copy());assertOtherSlots(before,h.placement,Set.of(SLOT));
    }
    @Test void robotInitUsesActualActOnePastTableRootsAndRetainsEveryUnwrittenByte() {
        var h=new H();h.seed(0x2D,0,0);var b=h.state();var before=h.placement.capture();h.robot();
        var result=h.robot.run(SLOT,new CdRobotGenerator.Globals(1,0,1,0,0x1200),null);
        var expected=b.addByte(ROUTINE,2).moveByte(SPRITE_FLAGS,(b.spriteFlags()|4)&0x7F).moveByte(PRIORITY,4).moveByte(X_RADIUS,0x22).moveByte(WIDTH,0x22).moveByte(Y_RADIUS,0x20)
                .moveWord(TILE,0x454).moveLong(MAPPINGS,0x20E482).moveLong(ROBOT_GENERATOR_SCHEDULE,0x20E6C0).moveWord(ROBOT_GENERATOR_BASE_Y,b.centreY())
                .moveWord(ROBOT_GENERATOR_TIMER,4).moveWord(ROBOT_GENERATOR_FLOAT_STEP,1).moveByte(MAP_FRAME,1);
        assertEquals(expected,result.state());assertEquals(3,h.publications.size());assertEquals(4,h.completed);assertNull(h.pending);assertEquals(0,h.solidCalls);assertEquals(1,h.draws);
        assertOtherSlots(before,h.placement,Set.of(SLOT));assertArrayEquals(before.savedFlags().copy(),h.placement.capture().savedFlags().copy());
    }
    @Test void robotActivePastUsesActualSharedSolidAndNativeAnimationWithoutNewOwners() {
        var h=new H();h.seed(0x2D,0,0);h.put(h.state().moveByte(ANIMATION,0).moveByte(PREVIOUS_ANIMATION,0).moveByte(ANIMATION_FRAME,0).moveByte(ANIMATION_TIME,0));h.robot();
        h.robot.run(SLOT,new CdRobotGenerator.Globals(0,0,1,0,0x1200),new CdRobotGenerator.Inputs(h.player,h.gates,h.globals));
        assertEquals(1,h.solidCalls);assertInstanceOf(CdSolidObject.Completed.class,h.lastSolid);assertEquals(0x20E482,h.state().longBits(MAPPINGS));
        assertEquals(0x20E6C0,h.state().longBits(ROBOT_GENERATOR_SCHEDULE));assertEquals(0x454,h.state().word(TILE));assertEquals(1,h.draws);assertNull(h.pending);
    }
    @Test void robotUnownedRawBaseReadLeavesOnlyItsActualInitialHeaderPrefix() {
        var h=new H();h.seed(0x2D,0,0);var b=h.state();h.robot();
        var e=assertThrows(CdRobotGenerator.UnsupportedRobotGenerator.class,()->h.robot.run(SLOT,new CdRobotGenerator.Globals(0,0,255,0,0x1200),null));
        assertEquals(CdRobotGenerator.UnsupportedBoundary.BASE_TILE_READ,e.boundary());
        assertEquals(b.addByte(ROUTINE,2).moveByte(SPRITE_FLAGS,b.spriteFlags()|4).moveByte(PRIORITY,4).moveByte(X_RADIUS,0x22).moveByte(WIDTH,0x22).moveByte(Y_RADIUS,0x20),h.state());
        assertEquals(1,h.publications.size());assertEquals(0,h.draws);assertNull(h.pending);
    }
    @Test void platformInitUsesActualNativeRootAndInitialOnlyStoresWithoutMainFallthrough() {
        var h=new H();h.seed(0x21,0,0);var b=h.state();var before=h.placement.capture();h.platform();
        var result=h.platform.run(SLOT,new CdPlatform.Globals(1,0,0x1200,0,0),null);
        var expected=b.moveByte(SPRITE_FLAGS,(b.spriteFlags()|4)&0x7F).moveWord(TILE,0x44BE).moveByte(PRIORITY,2).moveWord(PLATFORM_ANCHOR_X,b.centreX())
                .moveWord(PLATFORM_ANCHOR_Y,b.centreY()).moveWord(PLATFORM_INITIAL_Y,b.centreY()).moveLong(MAPPINGS,0x20C980).moveByte(MAP_FRAME,0)
                .moveByte(WIDTH,16).moveByte(Y_RADIUS,8).moveByte(PLATFORM_RANGE,2).addByte(ROUTINE,2);
        assertEquals(expected,result.state());assertEquals(0,h.solidCalls);assertEquals(1,h.draws);assertOtherSlots(before,h.placement,Set.of(SLOT));assertArrayEquals(before.savedFlags().copy(),h.placement.capture().savedFlags().copy());
    }
    @Test void platformChildStoresKeepTheAllocatedSlotsDirtyFractionsAndAllOtherBytes() {
        var h=new H();h.seed(0x21,0,0);h.put(h.state().moveByte(SUBTYPE2,0xFA));var before=h.placement.capture();var child=h.placement.pool().state(32);var parent=h.state();h.platform();
        h.platform.run(SLOT,new CdPlatform.Globals(1,0,0x1200,0,0),null);
        var expected=child.moveByte(ID,0x0A).moveWord(X,parent.centreX()).moveWord(Y,parent.centreY()).addWord(Y,-0x10)
                .moveByte(SPRING_PARENT_Y_OFFSET,0xF0).moveWord(SPRING_PARENT,parent.identity().nativeAddress24()).moveByte(SUBTYPE,2)
                .moveByte(SPRING_PARENT_X_OFFSET,0xF8).addWord(X,0xF8);
        assertEquals(expected,h.placement.pool().state(32));assertOtherSlots(before,h.placement,Set.of(SLOT,32));assertArrayEquals(before.savedFlags().copy(),h.placement.capture().savedFlags().copy());assertEquals(0,h.solidCalls);
    }
    @Test void animalFactoryAdmitsOnlyTheExistingPresencePrefixAndKeepsAllDataUntouched() {
        var h=new H();h.seed(0x24,0x5C,1);h.put(h.state().moveByte(SPRITE_FLAGS,0));var before=h.placement.capture();var reads=new ArrayList<String>();
        var body=CdAnimal.fromAct2PastAssets(h.placement,terrain);
        body.runWithReads(SLOT,new CdAnimal.Reads(){public int timeZone(){reads.add("TIME");return 0;}public int goodFuture(){fail("Past prefix does not read goodFuture");return 0;}public int projDestroyed(){reads.add("PROJECTOR");return 0;}public int timeWarpDir(){reads.add("DIRECTION");return 0;}public int cameraXWord(){reads.add("CAMERA");return 0x1200;}});
        assertPlacement(before,h.placement);assertTrue(reads.contains("TIME"));assertTrue(reads.contains("PROJECTOR"));assertTrue(reads.contains("CAMERA"));assertEquals(0,h.draws);
    }
    @Test void animalFreedBranchStillStopsWithoutInventingAMetadataBackedBody() {
        var h=new H();h.seed(0x24,0,0);var before=h.placement.capture();var body=CdAnimal.fromAct2PastAssets(h.placement,terrain);
        assertEquals(CdAnimal.UnsupportedBoundary.PROJECTOR_DESTROYED_BODY,assertThrows(CdAnimal.UnsupportedAnimal.class,()->body.run(SLOT,new CdAnimal.Globals(0,0,1,0,0x1200))).boundary());
        assertPlacement(before,h.placement);
    }

    private static Object binding(int family,CdObjectPlacementRuntime p,CdR12bTerrainAssets t,H h){
        return switch(family){
            case 0->CdMonitor.fromAct2PastAssets(p,t,h==null?null:h.solid,h==null?null:h.terrain,h,h==null?null:h::angle,h==null?null:h::draw,h==null?null:h::fm);
            case 1->CdTimePost.fromAct2PastAssets(p,t,h==null?null:h::direction,h==null?null:h::voice,h==null?null:h::draw);
            case 2->CdCollapsePlatform.fromAct2PastAssets(p,t,h==null?null:h.solid,h==null?null:h::collapseSolid,h==null?null:h::draw,h==null?null:c->{h.order.add("FM");},h==null?null:c->{});
            case 3->CdAnimal.fromAct2PastAssets(p,t);
            case 4->CdRobotGenerator.fromAct2PastAssets(p,t,h==null?null:h.solid,h==null?null:h::robotSolid,h==null?null:c->{},h==null?null:c->{},h==null?null:c->{},h==null?null:h::robotDraw,h==null?null:h.observer(),h==null?null:h.guard());
            case 5->CdPlatform.fromAct2PastAssets(p,t,h==null?null:h.math,h==null?null:h.terrain,h==null?null:h.solid,h==null?null:c->{},h==null?null:c->{},h==null?null:h::draw);
            default->throw new AssertionError(family);
        };
    }
    private static final class H implements CdMonitor.PrefixSolidOwner {
        final CdObjectPlacementRuntime placement=CdObjectPlacementRuntime.fromAssets(TestCdR12bRemainingInitialBindings.terrain,bank());
        final CdPlayerMath math=new CdPlayerMath(playerAssets.player());
        final CdPlayerTerrain terrain=CdPlayerTerrain.fromStageTerrain(TestCdR12bRemainingInitialBindings.terrain.terrain());
        final CdSolidObject solid=new CdSolidObject(CdPlayerMovement.fromStageSource(CdStagePlayerSource.fromAct2Past(TestCdR12bRemainingInitialBindings.terrain,playerAssets)));
        CdSolidObject.Player player=deadPlayer();final CdSolidObject.Gates gates=new CdSolidObject.Gates(0,0);CdSolidObject.Globals globals=new CdSolidObject.Globals(0,4,0x12,0x34);
        CdMonitor monitor;CdTimePost post;CdCollapsePlatform collapse;CdRobotGenerator robot;CdPlatform platform;
        final List<String> order=new ArrayList<>();final List<CdRobotGenerator.Publication> publications=new ArrayList<>();
        CdSolidObject.Outcome lastSolid;CdObjectState beforeSolid;int draws,solidCalls,direction,stop,completed;Object pending;
        String failRead="";final IllegalStateException failure=new IllegalStateException("actual source read failed");Runnable drawHook=()->{};
        H(){for(int whole=32;whole<128;whole++)placement.pool().write(dirty(placement.pool().state(whole)).moveByte(ID,0));}
        CdObjectState state(){return placement.pool().state(SLOT);}void put(CdObjectState s){placement.pool().write(s);}
        void seed(int id,int routine,int subtype){put(state().moveByte(ID,id).moveByte(ROUTINE,routine).moveByte(ROUTINE_2,0).moveByte(SUBTYPE,subtype).moveByte(SUBTYPE2,0)
                .moveByte(SAVED_FLAGS_ID,SAVED).moveByte(SPRITE_FLAGS,0x81).moveByte(COLLISION_STATUS,0).moveWord(X,0x1200).moveWord(Y,0x300));}
        void monitor(){monitor=CdMonitor.fromAct2PastAssets(placement,TestCdR12bRemainingInitialBindings.terrain,solid,terrain,this,this::angle,this::draw,this::fm);}
        void post(){post=CdTimePost.fromAct2PastAssets(placement,TestCdR12bRemainingInitialBindings.terrain,this::direction,this::voice,this::draw);}
        void collapse(){collapse=CdCollapsePlatform.fromAct2PastAssets(placement,TestCdR12bRemainingInitialBindings.terrain,solid,this::collapseSolid,this::draw,c->{order.add("FM");},c->{});}
        void robot(){robot=CdRobotGenerator.fromAct2PastAssets(placement,TestCdR12bRemainingInitialBindings.terrain,solid,this::robotSolid,c->{order.add("GOOD");},c->{order.add("POINTS");},c->{order.add("FM");},this::robotDraw,observer(),guard());}
        void platform(){platform=CdPlatform.fromAct2PastAssets(placement,TestCdR12bRemainingInitialBindings.terrain,math,terrain,solid,c->{adopt(c.outcome());},c->{globals=new CdSolidObject.Globals(globals.invincible(),globals.scoreChain(),c.value(),globals.secondaryAngle());},this::draw);}
        CdMonitor.SolidInputs monitorInputs(){return new CdMonitor.SolidInputs(player,gates,globals);}
        int read(String key,int value){if(failRead.equals(key))throw failure;return value;}
        CdMonitor.PastGlobals monitorReads(){return new CdMonitor.PastGlobals(){public int rawTimeZone(){return read("TIME",0);}public int rawTimeWarpDirection(){return read("DIRECTION",direction);}public int cameraXWord(){return read("CAMERA",0x1200);}public int timeAttackMode(){return read("ATTACK",0);}public int zone(){return read("ZONE",0);}public int layer(){return read("LAYER",0);}public int timeStopTimer(){return read("STOP",stop);}};}
        CdTimePost.PastGlobals postReads(){return new CdTimePost.PastGlobals(){public int rawTimeZone(){return read("TIME",0);}public int rawTimeWarpDirection(){return read("DIRECTION",direction);}public int cameraXWord(){return read("CAMERA",0x1200);}public int timeAttackMode(){return read("ATTACK",0);}public int zone(){return read("ZONE",0);}public OptionalInt layer(){return OptionalInt.of(read("LAYER",0));}};}
        public CdMonitor.LiveSolid preflight(CdMonitor.PrefixSolidCut c){order.add("PREFLIGHT");assertEquals(player,c.expectedPlayer());assertEquals(globals,c.expectedGlobals());return new CdMonitor.LiveSolid(player,gates,globals);}
        public CdMonitor.LiveSolid commit(CdMonitor.PrefixSolidCall c){order.add("SOLID");adopt(c.outcome());return new CdMonitor.LiveSolid(player,gates,globals);}
        void collapseSolid(CdCollapsePlatform.SolidCall c){beforeSolid=state();adopt(c.outcome());}
        void robotSolid(CdRobotGenerator.SolidCall c){adopt(c.outcome());}
        void adopt(CdSolidObject.Outcome outcome){lastSolid=outcome;solidCalls++;if(outcome instanceof CdSolidObject.Completed d){var r=d.completedResult();placement.pool().write(r.object());r.otherSlots().forEach(placement.pool()::write);player=r.player();globals=r.globals();}else{var r=((CdSolidObject.NativeStopped)outcome).prefix();placement.pool().write(r.object());r.otherSlots().forEach(placement.pool()::write);player=r.player();globals=r.globals();}}
        void angle(int expected,int value){assertEquals(globals.primaryAngle(),expected);globals=new CdSolidObject.Globals(globals.invincible(),globals.scoreChain(),value,globals.secondaryAngle());}
        void draw(CdObjectPool actual,CdObjectState.Identity id){assertSame(placement.pool(),actual);assertEquals(SLOT,id.wholeIndex());order.add("DRAW");draws++;put(state().moveByte(SPRITE_FLAGS,state().spriteFlags()&0x7F));drawHook.run();}
        void robotDraw(CdRobotGenerator.DrawCall c){draw(placement.pool(),c.expectedSource().identity());}
        void fm(CdMonitor.FmSound sound){order.add("FM");}
        void direction(CdTimePost.DirectionCall c){assertEquals(direction,c.expectedRawDirection());order.add("DIR:"+direction+">"+c.rawDirection());direction=c.rawDirection();}
        void voice(CdTimePost.SubCpuCommand c){order.add("VOICE:"+c.name());}
        CdRobotGenerator.DispatchObserver observer(){return new CdRobotGenerator.DispatchObserver(){public void beforePublication(CdRobotGenerator.Publication c){}public void afterPublication(CdRobotGenerator.Publication c){publications.add(c);}public void beforeCall(Object c){}public void afterCall(Object c){}};}
        CdRobotGenerator.ReceiptGuard guard(){return new CdRobotGenerator.ReceiptGuard(){public void prepared(Object c){assertNull(pending);assertTrue(robot.isCurrentCall(c));pending=c;}public void completed(Object c){assertSame(pending,c);completed++;pending=null;}};}
    }
    private static CdSolidObject.Player deadPlayer(){return new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(new CdPlayerMotionState(0x01001234,0x02005678,0,0,0,6,0,0,4,9,19,0,0,0,0,0,0,0,0,0),0x780,2,0,0),1,0,0);}
    private static CdAssetBytes bank(){byte[] b=new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES];for(int i=0;i<b.length;i++)b[i]=(byte)(i*17+3);b[2+3*SAVED]=(byte)0x80;return CdAssetBytes.copyOf(b);}
    private static CdObjectState dirty(CdObjectState base){try{byte[] b=new byte[64];for(int i=0;i<64;i++)b[i]=(byte)(0x31+i*17+base.identity().wholeIndex());var c=CdObjectState.class.getDeclaredConstructor(Object.class,CdObjectState.Identity.class,byte[].class);c.setAccessible(true);return c.newInstance(field(base,"owner"),base.identity(),b);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private static Object field(Object owner,String name){try{var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private static void assertOtherSlots(CdObjectPlacementRuntime.Snapshot before,CdObjectPlacementRuntime after,Set<Integer> changed){assertEquals(before.status(),after.status());for(var s:before.pool().slots())if(!changed.contains(s.identity().wholeIndex()))assertEquals(s,after.pool().state(s.identity().wholeIndex()));}
    private static void assertPlacement(CdObjectPlacementRuntime.Snapshot before,CdObjectPlacementRuntime after){assertOtherSlots(before,after,Set.of());assertArrayEquals(before.savedFlags().copy(),after.capture().savedFlags().copy());}
    private static void assertOnlySaved(CdObjectPlacementRuntime.Snapshot before,CdObjectPlacementRuntime after,int value){byte[] expected=before.savedFlags().copy();expected[2+3*SAVED]=(byte)value;assertArrayEquals(expected,after.capture().savedFlags().copy());}
}
