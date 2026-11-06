package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import com.openggf.game.soniccd.player.CdPlayerMath;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.openggf.game.soniccd.level.CdR12bObjectAssets.View.*;
import static com.openggf.game.soniccd.objects.CdObjectDrawQueue.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests FIRST, all Java UNRUN by author. Actual retained original R12B aggregate and explicit
 * component ingress; constructed dirty slots/ports are not a live Frame or an ordinary World walk.
 * N1ecc5f3a Ring/Main20-35,150-174,214-279; Spring/Main186-244; HUD/Main67-140;
 * Monitor/Main7-71,269-280. Expected full64 images are literal native stores.
 * Root owns mapping catalogue/current art, real World ports and the first unpaused physical walk.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCdR12bInitialBodyBindings {
    @Test void factoriesBindActualR12bValuesWithoutMutatingPoolBankOrInvokingPorts() throws Exception {
        var a=retail().act2Past();var p=placement(a);dirtyPool(p);var before=p.capture();var h=new Ports(p);
        var ring=CdRing.fromAct2PastAssets(p,a);var spring=spring(p,a,h);var hud=CdReservedHud.fromAct2PastAssets(p,a,h,h::drawReserved);
        assertNotNull(spring);assertNotNull(hud);assertCut(before,p);assertEquals(0,h.reservedReads);assertEquals(0,h.reservedWrites);
        assertTrue(h.events.isEmpty());assertEquals(0,h.mappingReads);assertEquals(0x209EB0,ring.sourceData().mappingAddress());
        assertEquals(0xA7AE,ring.sourceData().baseTileWord());assertSame(a.objects().rings(),ring.sourceData().visuals());
        assertEquals(a.objects().ringDeltas(),ring.sourceData().deltas());assertTrue(ring.sourceIdentity().isEmpty());assertTrue(ring.artBinding().isEmpty());
        var ha=CdReservedHud.Assets.fromAct2PastAssets(a);assertEquals(0x20AB1C,ha.hudMappingAddressBits());assertEquals(0x20A804,ha.monitorMappingAddressBits());
        assertSame(a.directArtSources().hudAssets(),a.objects().hudAssets(),"Existing raw HUD owner remains the sole HUD metadata product");
    }

    @Test void allThreeBodyFactoriesRejectForeignAndConstructedPlacementsBeforeAnyPort() throws Exception {
        var f=retail();var a=f.act2Past();
        var candidates=List.of(CdObjectPlacementRuntime.fromAssets(f.opening()),CdObjectPlacementRuntime.fromAssets(f.past(),bank()),
                CdObjectPlacementRuntime.fromAssets(f.act2(),bank()),new CdObjectPlacementRuntime(a.placements()),placement(f.independentAct2Past()));
        assertEquals(a.identity(),f.independentAct2Past().identity());assertNotSame(a.placements(),f.independentAct2Past().placements());
        for(var p:candidates){dirtyPool(p);var before=p.capture();var h=new Ports(p);assertFalse(p.sameAct2PastSource(a));
            assertThrows(IllegalArgumentException.class,()->CdRing.fromAct2PastAssets(p,a));
            assertThrows(IllegalArgumentException.class,()->spring(p,a,h));
            assertThrows(IllegalArgumentException.class,()->CdReservedHud.fromAct2PastAssets(p,a,h,h::drawReserved));
            assertCut(before,p);assertEquals(0,h.reservedReads);assertEquals(0,h.reservedWrites);assertTrue(h.events.isEmpty());assertEquals(0,h.mappingReads);
        }
    }

    @Test void namedIngressRejectsNullsWithoutCallingSlotsOrDraw() throws Exception {
        var a=retail().act2Past();var p=placement(a);var h=new Ports(p);var before=p.capture();
        assertThrows(NullPointerException.class,()->CdRing.fromAct2PastAssets(null,a));
        assertThrows(NullPointerException.class,()->CdRing.fromAct2PastAssets(p,null));
        assertThrows(NullPointerException.class,()->CdSpring.Assets.fromAct2PastAssets(null));
        assertThrows(NullPointerException.class,()->spring(null,a,h));
        assertThrows(NullPointerException.class,()->spring(p,null,h));
        assertThrows(NullPointerException.class,()->CdReservedHud.Assets.fromAct2PastAssets(null));
        assertThrows(NullPointerException.class,()->CdReservedHud.fromAct2PastAssets(null,a,h,h::drawReserved));
        assertThrows(NullPointerException.class,()->CdReservedHud.fromAct2PastAssets(p,null,h,h::drawReserved));
        assertThrows(NullPointerException.class,()->CdReservedHud.fromAct2PastAssets(p,a,null,h::drawReserved));
        assertThrows(NullPointerException.class,()->CdReservedHud.fromAct2PastAssets(p,a,h,null));
        assertCut(before,p);assertEquals(0,h.reservedReads);assertEquals(0,h.reservedWrites);assertTrue(h.events.isEmpty());
    }

    @Test void boundRingProtectsStageVariantZonePeriodAndRawInputsCannotBorrowR12b() throws Exception {
        var a=retail().act2Past();var p=placement(a);dirtyPool(p);var before=p.capture();var h=new Ports(p);var ring=CdRing.fromAct2PastAssets(p,a);
        for(String stage:List.of("R11A","R11B"))assertThrows(IllegalArgumentException.class,()->ring.executeSelected(32,input(CdReleaseVariant.USA,stage,0,0),h,h));
        for(var in:List.of(input(CdReleaseVariant.JAPAN,"R12B",0,0),input(CdReleaseVariant.USA,"R12A",0,0),
                input(CdReleaseVariant.USA,"R12B",6,0),input(CdReleaseVariant.USA,"R12B",0,3)))
            assertThrows(UnsupportedOperationException.class,()->ring.executeSelected(32,in,h,h));
        assertCut(before,p);assertTrue(h.events.isEmpty());assertEquals(0,h.mappingReads);
        assertThrows(IllegalArgumentException.class,()->new CdRing(p,TestCdRing.data()));
        var raw=new CdObjectPlacementRuntime(a.placements());var rh=new Ports(raw);var rawBefore=raw.capture();var rawRing=new CdRing(raw,TestCdRing.data());
        assertThrows(UnsupportedOperationException.class,()->rawRing.executeSelected(32,input(),rh,rh));assertCut(rawBefore,raw);assertTrue(rh.events.isEmpty());
    }

    @Test void legacyRingFactoriesKeepTheirSourceAndCannotBorrowNewR12bInputs() throws Exception {
        var f=retail();var a=CdObjectPlacementRuntime.fromAssets(f.opening());var b=CdObjectPlacementRuntime.fromAssets(f.past(),bank());var c=CdObjectPlacementRuntime.fromAssets(f.act2(),bank());
        var runtimes=List.of(a,b,c);var rings=List.of(CdRing.fromAssets(a,f.opening().openingObjects()),CdRing.fromAssets(b,f.past()),CdRing.fromAssets(c,f.act2().objects()));
        for(int i=0;i<runtimes.size();i++){var p=runtimes.get(i);var before=p.capture();var h=new Ports(p);var ring=rings.get(i);
            assertThrows(UnsupportedOperationException.class,()->ring.executeSelected(32,input(),h,h));assertCut(before,p);assertTrue(h.events.isEmpty());
        }
        assertNotNull(CdSpring.Assets.fromPastAssets(f.past()));assertNotNull(CdSpring.Assets.fromObjectAssets(f.opening().openingObjects()));
        assertEquals(0x20ABBE,CdReservedHud.Assets.fromAssets(f.past()).hudMappingAddressBits());
    }

    @Test void ringInitAndLaterPhysicalChildrenUseR12bOperandsAndKeepAllOtherSlotBytes() throws Exception {
        var a=retail().act2Past();var p=placement(a);dirtyPool(p);var seed=p.pool().state(32).moveByte(ID,0x10).moveByte(ROUTINE,0)
                .moveWord(X,0x100).moveWord(Y,0x200).moveByte(SUBTYPE,0x12).moveByte(SAVED_FLAGS_ID,1);p.pool().write(seed);
        var before=p.capture();var h=new Ports(p);var globals=h.value;var ring=CdRing.fromAct2PastAssets(p,a);
        // Explicit constructed component walk: Ring parent32 creates later physical33/34, each selected once.
        for(int whole=32;whole<128;whole++)if(p.pool().state(whole).id()==0x10)ring.executeSelected(whole,input(),h,h);
        assertEquals(List.of("draw:32","draw:33","draw:34"),h.events);assertEquals(0,h.mappingReads);
        for(int whole=32;whole<128;whole++){var expected=before.pool().slots().get(whole-32);
            if(whole<=34)expected=expected.moveByte(ID,0x10).moveByte(ROUTINE,2).moveWord(X,0x100+(whole-32)*24).moveWord(RING_ANCHOR_X,0x100).moveWord(Y,0x200)
                    .moveLong(MAPPINGS,0x209EB0).moveWord(TILE,0xA7AE).moveByte(PRIORITY,2).moveByte(SPRITE_FLAGS,4).moveByte(COLLISION_TYPE,0x47)
                    .moveByte(WIDTH,8).moveByte(Y_RADIUS,8).moveByte(SAVED_FLAGS_ID,1).moveByte(RING_MEMBER,whole-31).moveByte(MAP_FRAME,3);
            assertEquals(expected,p.pool().state(whole),"full64 physical "+whole);
        }
        byte[] expectedBank=before.savedFlags().copy();expectedBank[5]&=0x7F;assertArrayEquals(expectedBank,p.capture().savedFlags().copy());
        assertEquals(before.status(),p.status());assertEquals(globals,h.value);assertEquals(0,h.reservedReads);assertEquals(0,h.reservedWrites);
    }

    @Test void ringCollectUsesNativeRewardThenSavedBitThenActualSparkleScriptWithoutArtAdmission() throws Exception {
        var a=retail().act2Past();var p=placement(a);dirtyPool(p);p.pool().write(p.pool().state(32).moveByte(ID,0x10).moveByte(ROUTINE,4)
                .moveWord(X,0x100).moveWord(Y,0x200).moveLong(MAPPINGS,0x209EB0).moveWord(TILE,0xA7AE).moveByte(SPRITE_FLAGS,4).moveByte(FLAGS,0)
                .moveByte(SAVED_FLAGS_ID,1).moveByte(RING_MEMBER,2).moveByte(ANIMATION,0).moveByte(PREVIOUS_ANIMATION,0xFF).moveByte(ANIMATION_FRAME,0).moveByte(ANIMATION_TIME,0));
        var before=p.capture();var h=new Ports(p);h.value=new CdRingGlobals(99,3,0x20,0x80,0xFF,0,7,3);
        h.onSound=()->{assertEquals(0x80,p.savedFlag(1,0),"native sound precedes collected saved bit");assertEquals(6,p.pool().state(32).routine());assertEquals(100,h.value.rings());};
        CdRing.fromAct2PastAssets(p,a).executeSelected(32,input(),h,h);
        var expected=before.pool().slots().getFirst().moveByte(ROUTINE,6).moveByte(COLLISION_TYPE,0).moveByte(PRIORITY,1)
                .moveByte(PREVIOUS_ANIMATION,0).moveByte(ANIMATION_FRAME,1).moveByte(ANIMATION_TIME,5).moveByte(MAP_FRAME,4);
        assertEquals(expected,p.pool().state(32));for(int whole=33;whole<128;whole++)assertEquals(before.pool().slots().get(whole-32),p.pool().state(whole));
        var bytes=before.savedFlags().copy();bytes[5]|=2;assertArrayEquals(bytes,p.capture().savedFlags().copy());
        assertEquals(new CdRingGlobals(100,4,0x22,0x81,0,0,7,3),h.value);assertEquals(List.of("globals","SCMD_YESSFX","draw:32"),h.events);assertEquals(0,h.mappingReads);
    }

    @Test void springAssetsKeepRealSeventyTwoByteTableAndIndependentR12bRoots() throws Exception {
        var a=retail().act2Past();var o=a.objects();var s=CdSpring.Assets.fromAct2PastAssets(a);
        assertSame(o.springVertical(),s.vertical());assertSame(o.springHorizontal(),s.horizontal());assertSame(o.springDiagonal(),s.diagonal());
        assertSame(o.view(SPRING_SPEEDS),s.speedWords());assertSame(o.view(OBJECT_TILE_IDS),s.objectTileIds());assertEquals(72,s.objectTileIds().length());assertEquals(0x209956,s.mappingsAddress());
        assertEquals(List.of(0,6,80),List.of(s.vertical().mappingTableOffset(),s.horizontal().mappingTableOffset(),s.diagonal().mappingTableOffset()));
        assertEquals(200,s.diagonal().mappings().length());assertEquals(199,MAP_SPRING.canonicalRange().length());assertEquals(0x002E,o.view(DIAGONAL_SPRING_HEADER).unsignedWord(0));
        for(int subtype=0;subtype<256;subtype++)assertEquals((subtype&2)==0?-4096:-2560,s.speedWord(subtype));
        assertEquals(0x490,s.objectTileIds().unsignedWord(66));assertThrows(IndexOutOfBoundsException.class,()->s.objectTileIds().unsignedWord(72));
    }

    @Test void springInitOrientationMatrixUsesActualRootsAndKeepsEveryFull64Nonwrite() throws Exception {
        int[][] vectors={{0,0,2,16,8,0,0x0520,-4096,0x15},{2,0,2,16,8,0,0x2520,-2560,0x15},
                {4,2,8,8,16,6,0x0520,-4096,0x15},{0,2,14,16,8,0,0x0520,-4096,0x17},
                {8,0,20,24,12,80,0x0490,-4096,0x15},{10,2,20,24,12,80,0x2490,-2560,0x15},{12,2,8,8,16,6,0x0520,-4096,0x15}};
        var a=retail().act2Past();
        for(var v:vectors){var p=placement(a);dirtyPool(p);seedSpring(p,v[0],v[1],0);var before=p.capture();var h=new Ports(p);
            var math=mock(CdPlayerMath.class);var solid=mock(CdSolidObject.class);var body=spring(p,a,h,math,solid);
            var out=body.run(40,new CdSpring.Globals(0x100),solidInputs());
            var expected=springPrefix(before.pool().slots().get(8),v[2],v[3],v[4],v[5],v[1]|4).moveWord(TILE,v[6]).moveWord(SPRING_SPEED,v[7]).moveByte(FLAGS,v[8]);
            assertEquals(expected,out.state());assertEquals(expected,p.pool().state(40));assertOtherSlots(before,p,40);assertArrayEquals(before.savedFlags().copy(),p.capture().savedFlags().copy());
            assertEquals(before.status(),p.status());assertEquals(List.of("draw:40"),h.events);assertEquals(0,h.mappingReads);verifyNoInteractions(math,solid);
            assertEquals(List.of(new CdSpring.RoutineEntered(CdSpring.NativeRoutine.INIT),new CdSpring.CallReturned(CdSpring.NativeCall.DRAW_OBJECT)),out.effects());
            assertEquals(solidInputs().player(),out.finalPlayer());assertEquals(solidInputs().globals(),out.finalGlobals());
        }
    }

    @Test void springUnownedSubtypeTwoStillStopsAtItsExactPublishedSelectorPrefix() throws Exception {
        var a=retail().act2Past();
        for(int subtype2:new int[]{1,0x7F,0xFF}){var p=placement(a);dirtyPool(p);seedSpring(p,10,0x12,subtype2);var before=p.capture();var h=new Ports(p);
            var body=spring(p,a,h);var error=assertThrows(CdSpring.UnsupportedSpring.class,()->body.run(40,new CdSpring.Globals(0x100),solidInputs()));
            assertEquals(CdSpring.UnsupportedBoundary.DIAGONAL_TILE_SELECTOR,error.boundary());assertEquals(40,error.wholeIndex());assertEquals(15,error.value());assertTrue(error.readAddress24().isEmpty());
            assertEquals(springPrefix(before.pool().slots().get(8),20,24,12,80,0x16),p.pool().state(40));
            assertOtherSlots(before,p,40);assertArrayEquals(before.savedFlags().copy(),p.capture().savedFlags().copy());assertTrue(h.events.isEmpty());assertEquals(0,h.mappingReads);
        }
    }

    @Test void hudScoreLivesAndRingsInitUseActualPointersScreenYAndOnlyNativeFull64Stores() throws Exception {
        var a=retail().act2Past();var p=placement(a);dirtyPool(p);var cut=p.capture();var h=new Ports(p);
        for(int whole:new int[]{2,3,5})h.put(whole,reservedImage(0x1C,0,whole==3?1:0,whole==5?1:0,0x80));
        var all=h.reservedCopy();var body=CdReservedHud.fromAct2PastAssets(p,a,h,h::drawReserved);
        for(int whole:new int[]{2,3,5}){var out=body.run(whole,hudGlobals(0,0,0));byte[] expected=hudPrefix(all[whole-1]);
            if(whole==3){word(expected,10,0x148);expected[0x1A]=1;}else expected[0x1A]=(byte)(whole==5?3:0);expected[1]&=0x7F;
            assertArrayEquals(expected,out.image().copy());assertArrayEquals(expected,h.get(whole).copy());assertEquals(all[whole-1][12],expected[12],"world Y is not screen Y");
        }
        for(int whole=1;whole<32;whole++)if(whole!=2&&whole!=3&&whole!=5)assertArrayEquals(all[whole-1],h.get(whole).copy());
        assertCut(cut,p);assertEquals(List.of("draw:2","draw:3","draw:5"),h.events);assertEquals(0,h.mappingReads);
    }

    @Test void timeIconKeepsRawDirectionTimerSuppressionAndActualMonitorRootAtInit() throws Exception {
        var a=retail().act2Past();
        int[][] vectors={{0,0,1,19,0},{1,89,1,19,1},{0xFF,89,1,18,1},{0xFF,90,1,18,0},{1,90,2,19,1}};
        for(var v:vectors){var p=placement(a);var cut=p.capture();var h=new Ports(p);byte[] seed=reservedImage(0x19,0,10,0xD7,0x80);h.put(31,seed);
            var all=h.reservedCopy();var out=CdReservedHud.fromAct2PastAssets(p,a,h,h::drawReserved).run(31,hudGlobals(v[0],v[1],v[2]));
            var expected=iconPrefix(seed);expected[0x1A]=(byte)v[3];if(v[4]!=0)expected[1]&=0x7F;
            assertArrayEquals(expected,out.image().copy());for(int whole=1;whole<31;whole++)assertArrayEquals(all[whole-1],h.get(whole).copy());
            assertEquals(v[4],h.events.size());assertEquals(0,h.mappingReads);assertCut(cut,p);
        }
    }

    @Test void reservedFactoryDoesNotAdmitOtherMonitorTimePostOrPointsBodies() throws Exception {
        var a=retail().act2Past();int[][] cases={{0x1C,0,0x80,0},{0x19,0,0,1},{0x19,0,8,2},{0x19,1,10,3}};
        var boundaries=List.of(CdReservedHud.UnsupportedBoundary.POINTS_BRANCH,CdReservedHud.UnsupportedBoundary.MONITOR_BRANCH,CdReservedHud.UnsupportedBoundary.TIME_POST_BRANCH,CdReservedHud.UnsupportedBoundary.TIME_ICON_ROUTINE);
        for(var c:cases){var p=placement(a);var before=p.capture();var h=new Ports(p);var image=reservedImage(c[0],c[1],c[2],0,0x80);h.put(31,image);var all=h.reservedCopy();
            var body=CdReservedHud.fromAct2PastAssets(p,a,h,h::drawReserved);var error=assertThrows(CdReservedHud.UnsupportedReservedHud.class,()->body.run(31,hudGlobals(0,0,0)));
            assertEquals(boundaries.get(c[3]),error.boundary());for(int whole=1;whole<32;whole++)assertArrayEquals(all[whole-1],h.get(whole).copy());
            assertCut(before,p);assertTrue(h.events.isEmpty());assertEquals(0,h.reservedWrites);assertEquals(0,h.mappingReads);
        }
    }

    @Test void genuineQueueStillDefersTheUnboundMappingCatalogueAndConfersNoCurrentArt() throws Exception {
        var a=retail().act2Past();var p=placement(a);var h=new Ports(p);h.put(2,reservedImage(0x1C,0,0,0,0x80));
        var body=CdReservedHud.fromAct2PastAssets(p,a,h,h::drawReserved);body.run(2,hudGlobals(0,0,0));assertEquals(0,h.mappingReads);
        var cut=p.capture();var selected=h.get(2).copy();assertEquals(0x20AB1C,h.get(2).unsignedLong(4));
        assertSame(h.mappingGap,assertThrows(UnsupportedOperationException.class,()->h.queue.finishAfterCamera(cameras())).getCause());
        assertEquals(1,h.mappingReads);assertEquals(0x20AB1C,h.mappingAddress);assertArrayEquals(selected,h.get(2).copy());assertCut(cut,p);
        assertEquals(Phase.FAILED,h.queue.phase());assertThrows(IllegalStateException.class,h.queue::beginPass);
        // This is the honest external mapping gap. No art decode/current residency/render product is invented.
    }

    private static CdSpring spring(CdObjectPlacementRuntime p,CdR12bTerrainAssets a,Ports h){return spring(p,a,h,mock(CdPlayerMath.class),mock(CdSolidObject.class));}
    private static CdSpring spring(CdObjectPlacementRuntime p,CdR12bTerrainAssets a,Ports h,CdPlayerMath math,CdSolidObject solid){
        // Explicit Init-only component collaborators. Native Init must not reach math/Solid or action ports.
        return CdSpring.fromAct2PastAssets(p,a,math,solid,c->fail("Init reached SolidCommit"),c->fail("Init reached LaunchCommit"),(pool,id)->h.drawDynamic(pool,id.wholeIndex()),s->fail("Init reached FM"));
    }
    private static void seedSpring(CdObjectPlacementRuntime p,int subtype,int sprite,int subtype2){
        p.pool().write(p.pool().state(40).moveByte(ID,10).moveByte(ROUTINE,0).moveByte(ROUTINE_2,0).moveWord(X,0x100).moveWord(Y,0x200)
                .moveWord(SPRING_PARENT,0).moveByte(SPRITE_FLAGS,sprite).moveByte(SUBTYPE,subtype).moveByte(SUBTYPE2,subtype2).moveByte(FLAGS,0x15));
    }
    private static CdObjectState springPrefix(CdObjectState b,int routine,int width,int radius,int mapOffset,int sprite){
        return b.moveByte(ROUTINE,routine).moveLong(MAPPINGS,0x209956+mapOffset).moveWord(TILE,0x520).moveByte(SPRITE_FLAGS,sprite)
                .moveByte(WIDTH,width).moveByte(Y_RADIUS,radius).moveWord(SPRING_ANCHOR_X,0x100).moveByte(PRIORITY,4);
    }
    private static CdSpring.SolidInputs solidInputs(){return new CdSpring.SolidInputs(new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(
            new CdPlayerMotionState(0x01008000,0x00671234,0x123,0x400,0,2,0x10,2,4,9,19,0,0,1,0,0,0,0,0x56,0x78),0x780,2,0,0),1,0,0),new CdSolidObject.Gates(0,0),new CdSolidObject.Globals(0,4,0x12,0x34));}
    private static CdRing.Inputs input(){return input(CdReleaseVariant.USA,"R12B",0,0);}
    private static CdRing.Inputs input(CdReleaseVariant variant,String stage,int zone,int period){return new CdRing.Inputs(variant,CdStageKey.parse(stage),zone,0x100,period,0);}
    private static CdReservedHud.Globals hudGlobals(int direction,int timer,int frames){return new CdReservedHud.Globals(0,5,45,0,direction,timer,frames);}
    private static CdAssetBytes bank(){byte[] b=new byte[766];for(int i=0;i<b.length;i++)b[i]=(byte)(i*53+0x5B);b[0]=(byte)0xC7;b[1]=(byte)0x9E;b[5]=(byte)0x80;return CdAssetBytes.copyOf(b);}
    private static CdObjectPlacementRuntime placement(CdR12bTerrainAssets a){return CdObjectPlacementRuntime.fromAssets(a,bank());}
    private static void dirtyPool(CdObjectPlacementRuntime p){for(int whole=32;whole<128;whole++){var s=p.pool().state(whole);
        for(var f:CdObjectState.ByteField.values())s=s.moveByte(f,whole*17+f.byteOffset()*13);
        for(var f:CdObjectState.WordField.values())s=s.moveWord(f,whole*257+f.byteOffset()*113);
        for(var f:CdObjectState.LongField.values())s=s.moveLong(f,0x5A397100+whole*257+f.byteOffset()*113);
        p.pool().write(s.moveByte(ID,0));}}
    private static void assertCut(CdObjectPlacementRuntime.Snapshot before,CdObjectPlacementRuntime p){assertEquals(before.status(),p.status());assertEquals(before.pool().slots(),p.pool().capture().slots());assertArrayEquals(before.savedFlags().copy(),p.capture().savedFlags().copy());}
    private static void assertOtherSlots(CdObjectPlacementRuntime.Snapshot before,CdObjectPlacementRuntime p,int selected){for(int whole=32;whole<128;whole++)if(whole!=selected)assertEquals(before.pool().slots().get(whole-32),p.pool().state(whole),"full64 physical "+whole);}
    private static byte[] reservedImage(int id,int routine,int subtype,int subtype2,int flags){byte[] b=new byte[64];for(int i=0;i<b.length;i++)b[i]=(byte)(0x43+i*13);b[0]=(byte)id;b[1]=(byte)flags;b[0x24]=(byte)routine;b[0x28]=(byte)subtype;b[0x29]=(byte)subtype2;return b;}
    private static byte[] hudPrefix(byte[] in){var b=in.clone();b[0x24]=2;longWord(b,4,0x20AB1C);word(b,2,0x8568);word(b,8,0x90);word(b,10,0x88);return b;}
    private static byte[] iconPrefix(byte[] in){var b=in.clone();b[0x24]=2;longWord(b,4,0x20A804);word(b,2,0x85A8);word(b,8,0xC4);word(b,10,0x152);return b;}
    private static void word(byte[] b,int at,int v){b[at]=(byte)(v>>>8);b[at+1]=(byte)v;}
    private static void longWord(byte[] b,int at,int v){word(b,at,v>>>16);word(b,at+2,v);}
    private static DrawCameras cameras(){return new DrawCameras(new CameraWords(0x100,0x180),new CameraWords(0,0),new CameraWords(0,0));}

    /** Test-owned selected-image and existing-queue adapter, never a production Frame/current/art owner. */
    private static final class Ports implements CdRing.GlobalsPort,CdRing.SourceCalls,CdReservedHud.Slots,CdDrawSlots {
        final CdObjectPlacementRuntime p;final CdAssetBytes[] reserved=new CdAssetBytes[31];final List<String> events=new ArrayList<>();final CdObjectDrawQueue queue;
        final UnsupportedOperationException mappingGap=new UnsupportedOperationException("Test has no Root-owned R12B mapping catalogue");
        int reservedReads,reservedWrites,mappingReads,mappingAddress;Runnable onSound=()->{};CdRingGlobals value=new CdRingGlobals(17,6,0xA0,0xF0,9,0,7,3);
        Ports(CdObjectPlacementRuntime p){this.p=p;for(int i=0;i<31;i++)reserved[i]=CdAssetBytes.copyOf(reservedImage(0,0,0,0,0));
            queue=new CdObjectDrawQueue(this,(address,mode,frame)->{mappingReads++;mappingAddress=address;throw mappingGap;},new byte[640],List.of(new CdSlotRef(2),new CdSlotRef(3),new CdSlotRef(5),new CdSlotRef(31)));queue.beginPass();}
        void put(int whole,byte[] bytes){reserved[whole-1]=CdAssetBytes.copyOf(bytes);}CdAssetBytes get(int whole){return reserved[whole-1];}
        byte[][] reservedCopy(){byte[][] all=new byte[31][];for(int i=0;i<31;i++)all[i]=reserved[i].copy();return all;}
        @Override public CdRingGlobals read(){return value;}@Override public void write(CdRingGlobals next){value=next;events.add("globals");}
        @Override public void playFmSound(CdRing.FmSound sound){events.add(sound.name());onSound.run();}
        @Override public void subCpuCommand(CdRing.SubCpuCommand sound){events.add(sound.name());onSound.run();}
        @Override public void drawObject(CdObjectPool pool,int whole){drawDynamic(pool,whole);}
        void drawDynamic(CdObjectPool pool,int whole){assertSame(p.pool(),pool);var b=pool.state(whole);queue.drawObject(new DrawCallCut(new CdSlotRef(whole),b.spriteFlags(),b.centreX(),b.centreY(),b.unsignedByte(Y_RADIUS),b.unsignedByte(WIDTH),b.unsignedByte(PRIORITY)),new CameraWords(0x100,0x180));events.add("draw:"+whole);}
        void drawReserved(int whole){var b=get(whole);queue.drawObject(new DrawCallCut(new CdSlotRef(whole),b.unsignedByte(1),b.unsignedWord(8),b.unsignedWord(12),b.unsignedByte(0x16),b.unsignedByte(0x19),b.unsignedByte(0x18)),new CameraWords(0x100,0x180));events.add("draw:"+whole);}
        @Override public CdAssetBytes read(int whole){reservedReads++;return get(whole);}
        @Override public void write(int whole,CdAssetBytes expected,CdAssetBytes next){assertArrayEquals(get(whole).copy(),expected.copy());put(whole,next.copy());reservedWrites++;}
        @Override public LiveDrawSlot read(CdSlotRef ref){int whole=ref.wholeIndex();if(whole>=32){var b=p.pool().state(whole);return new LiveDrawSlot(b.id(),b.spriteFlags(),b.centreX(),b.centreY(),b.word(X_FRACTION),b.longBits(MAPPINGS),b.unsignedByte(MAP_FRAME),b.word(TILE));}
            var b=get(whole);return new LiveDrawSlot(b.unsignedByte(0),b.unsignedByte(1),b.unsignedWord(8),b.unsignedWord(12),b.unsignedWord(10),(int)b.unsignedLong(4),b.unsignedByte(0x1A),b.unsignedWord(2));}
        @Override public void writeOnScreenBit(CdSlotRef ref,boolean value){int whole=ref.wholeIndex();if(whole>=32){var b=p.pool().state(whole);p.pool().write(b.moveByte(SPRITE_FLAGS,value?b.spriteFlags()|0x80:b.spriteFlags()&0x7F));}else{var b=get(whole).copy();b[1]=(byte)(value?b[1]|0x80:b[1]&0x7F);put(whole,b);}}
    }

    private record Retail(CdR12bTerrainAssets act2Past,CdR12bTerrainAssets independentAct2Past,CdR12aTerrainAssets act2,CdR11aAssets opening,CdR11bAssets past){}
    private Retail retained;
    private Retail retail()throws Exception{if(retained!=null)return retained;String root=System.getProperty("soniccd.media.root");assertNotNull(root,"Configure original USA CD media root");assertFalse(root.isBlank());Path cue;
        try(var files=Files.list(Path.of(root).resolve("USA"))){var cues=files.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();}
        try(var source=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){retained=new Retail(CdR12bTerrainAssets.fromPrograms(source),CdR12bTerrainAssets.fromPrograms(source),CdR12aTerrainAssets.fromPrograms(source),
                CdR11aAssetLoader.load(source,CdReleaseVariant.USA,CdStageKey.parse("R11A")),CdR11bAssets.fromPrograms(CdTimeWarpPrograms.fromPrograms(source)));}
        return retained; // Source is closed; only actual authenticated immutable aggregates remain.
    }
}
