package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.player.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.soniccd.objects.CdR4SolidMover.*;

/** Literal pinned native vectors plus labelled constructed component controls.
 * No World, Frame, dispatcher, route or rendering proof. Fixtures are not runtime fallback assets.
 * Source: src/objects/r4/*.asm, common/object_spawn.asm:279-312, common/math.asm:3-57
 * at 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff.
 */
class TestCdR4SolidMovers {
    static CdAssetBytes bytes(int... values) {byte[] b=new byte[values.length];for(int i=0;i<b.length;i++)b[i]=(byte)values[i];return CdAssetBytes.copyOf(b);}
    static CdAssetBytes words(int... values) {byte[] b=new byte[values.length*2];for(int i=0;i<values.length;i++){b[i*2]=(byte)(values[i]>>8);b[i*2+1]=(byte)values[i];}return CdAssetBytes.copyOf(b);}
    static CdAssetBytes longs(int... values) {byte[] b=new byte[values.length*4];for(int i=0;i<values.length;i++)for(int j=0;j<4;j++)b[i*4+j]=(byte)(values[i]>>((3-j)*8));return CdAssetBytes.copyOf(b);}
    static final class Data implements CdR4SolidMover.Data {
        final Map<Table,CdAssetBytes> tables=new EnumMap<>(Table.class);Table fault; Mapping mapFault;int reads;
        Data() {
            tables.put(Table.BLOCK_SIZES,bytes(16,16,16,16,32,16,16,32,32,32,64,16,16,64));
            tables.put(Table.TWIN_MOTION,bytes(128,252,2,0,128,252,0,0,128,4,254,0,128,4,0,0));
            tables.put(Table.BREAK_OFFSETS,words(-8,-8,16,0,0,16,16,16));
            tables.put(Table.BREAK_DELTAS,longs(-0x148b0,-0x60000,0x148b0,-0x60000,-0x2e38e,-0x40000,0x2e38e,-0x40000,
                -0x3da12,-0x148b0,-0x2e38e,-0x2e38e,-0x3da12,0x148b0,-0x2e38e,0x2e38e,
                0x3da12,-0x148b0,0x2e38e,-0x2e38e,0x3da12,0x148b0,0x2e38e,0x2e38e));
            tables.put(Table.CRUSH_SHAPES,bytes(2,1,1,2,2,1,1,2,0,3,1,2,2,1,3,0,3,0,0,3,3,0,0,3,
                1,2,0,0,0,0,2,1,2,1,1,2,1,2,2,1,0,3,2,0,2,0,0,3,3,0,0,0,0,0,0,3,2,1,1,2,3,0,0,3,1,2,1,1,1,1,2,1));
            tables.put(Table.CRUSH_MOTION,longs(60,0,48,0x20000,60,0,192,-0x8000));
            tables.put(Table.ROTATE_FORWARD,bytes(0,4,1,255,1,5,2,255,2,5,1,255,3,6,2,255));
            tables.put(Table.ROTATE_BACK,bytes(0,7,3,255,1,4,2,255,2,4,1,255,3,7,0,255));
            tables.put(Table.ROTATE_SIDE,bytes(-16,0,-16,0,16,0,16,0));tables.put(Table.ROTATE_TOP,bytes(0,-32,0,32,0,32,0,-32));
            tables.put(Table.H3_OFFSETS,words(0,0,-128,-144,0,-128,144,0,-128,0,64,128,-144,64,128,144,64,128));
            tables.put(Table.H5_START,bytes(0,0,64,1,128,2,192,3,128,4,64,5,0,0));
            tables.put(Table.H5_MOTION,bytes(128,0,0,128,128,0,0,128,128,0,0,128,128,0,255,128,128,0,255,128,128,0,255,128));
            tables.put(Table.V1_START,bytes(192,0,168,224,144,192,120,160,216,32,240,64,8,96));
            tables.put(Table.V6_START,bytes(0,0,32,224,64,192,96,160,32,32,64,64,96,96));
            tables.put(Table.V10_START,bytes(0,0,224,224,192,192,160,160,224,32,192,64,160,96));
            tables.put(Table.V11_START,bytes(0,0,0,128,0,128,0,128,176,32,255,0,176,160,255,0,80,32,255,0,80,160,255,0));
        }
        public int mapping(Mapping mapping) {reads++;if(mapping==mapFault)throw new IllegalStateException("mapping read");return 0x20abcd;}
        public CdAssetBytes table(Table table) {reads++;if(table==fault)throw new IllegalStateException("table read");return Objects.requireNonNull(tables.get(table));}
        public int address(Address address) {return switch(address){case SAVED_FLAGS->0xffb000;case WOOD_LEFT_PROBE->0x200222;case WOOD_RIGHT_PROBE->0x200444;};}
    }
    static CdSolidObject solid() {
        // Constructed open-air terrain and format-valid zero tables, same shared kernel as production.
        byte[] fg=new byte[338],bg=new byte[50];fg[0]=41;fg[1]=7;bg[0]=5;bg[1]=7;Arrays.fill(fg,2,fg.length,(byte)2);
        var terrain=CdR11aTerrain.decode(new byte[6432],new byte[25600],new byte[61952],fg,bg,new byte[900],new byte[256],new byte[4096],new byte[4096]);
        byte[] ani=new byte[578];for(int i=0;i<60;i++){ani[i*2+1]=120;}ani[122]=(byte)255;
        byte[] map=new byte[398];for(int i=0;i<198;i++){map[i*2]=1;map[i*2+1]=(byte)140;}
        var visual=CdSonicVisualAssets.decode(map,map,new byte[69472]);
        byte[] atan=new byte[257];for(int i=0;i<=256;i++)atan[i]=(byte)(i/8);
        var assets=CdR11aPlayerAssets.decode(ani,new byte[60],new byte[]{6,7,8,0x49,0x4c,(byte)255},new byte[4],new byte[640],atan,visual);
        return new CdSolidObject(new CdPlayerMovement(terrain,assets));
    }
    static CdSolidObject.Player player(int id,int x,int y,int xv,int yv,int flags,int stand) {
        var motion=new CdPlayerMotionState(x,y,xv,yv,0,2,0,flags,0,9,19,0,0,0,0,0,0,0,0,0);
        return new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(motion,0,0,0,0),id,0,stand);
    }
    static final class Scene {
        CdObjectPool pool=new CdObjectPool();Data data=new Data();CdSolidObject kernel=solid();
        CdSolidObject.Player primary=player(0,0x1001234,0x1005678,0,0,0,0),secondary=player(2,0x2222345,0x3333456,-13,17,2,0);
        Sine sine=a->new CdPlayerMath.SineCosine(0,256);
        Terrain terrain=new Terrain(){public Probe floor(int x,int y){return new Probe(16,OptionalInt.empty(),OptionalInt.empty());}public Probe wall(boolean right,int x,int y){return new Probe(-1,OptionalInt.empty(),OptionalInt.empty());}};
        Water water=new Water(0x780,0x780,0x780,0x1234);
        Scene(){set(0,1,0x34);set(8,4,0x100beef);set(12,4,0x200abcd);}
        void set(int at,int size,int value){pool.write(CdR4SolidMover.put(pool.state(40),at,size,value));}
        void slot(int n,int at,int size,int value){pool.write(CdR4SolidMover.put(pool.state(n),at,size,value));}
        Inputs in(){return new Inputs(pool.capture(),40,primary,secondary,new CdSolidObject.Gates(0,0),new CdSolidObject.Globals(0,0,0x12,0x34),kernel,data,sine,terrain,CdAssetBytes.copyOf(new byte[766]),1,water,new ActorWidths(16,24));}
        void fill(){for(int n=32;n<128;n++)slot(n,0,1,99);}
    }
    static CdObjectState object(Outcome o){return o.prefix().pool().slots().get(8);}
    static CdObjectState at(Outcome o,int n){return o.prefix().pool().slots().get(n-32);}
    static CdObjectState beforeClear(Outcome o) {return o.prefix().observations().stream().filter(e->e instanceof Cleared).map(e->((Cleared)e).before().pool().slots().get(8)).findFirst().orElseThrow();}
    static void ok(Outcome o){assertTrue(o.stopped().isEmpty(),()->o.stopped().toString());}
    static int b(CdObjectState s,int n){return CdR4SolidMover.u8(s,n);}static int w(CdObjectState s,int n){return CdR4SolidMover.u16(s,n);}static int l(CdObjectState s,int n){return CdR4SolidMover.i32(s,n);}

    @ParameterizedTest @ValueSource(ints={0,1,2,3,4,5,6}) void blockSizesAreLiteralNativePairs(int subtype) {
        Scene s=new Scene();s.set(0x28,1,subtype);var before=s.pool.capture();var o=CdR4Block.run(s.in());ok(o);
        int[] widths={16,16,32,16,32,64,16},heights={16,16,16,32,32,16,64};
        assertEquals(widths[subtype],b(object(o),0x19));assertEquals(heights[subtype],b(object(o),0x16));
        assertEquals(0xbeef,w(object(o),10));assertEquals(0xabcd,w(object(o),14));assertEquals(s.secondary,o.prefix().secondary());
        assertEquals(before.slots(),s.pool.capture().slots());assertEquals(Tail.DRAW_DESPAWN_X,o.tail());
    }
    @Test void realSharedSolidReleasesOldAttachmentAcrossTheCompletePool() {
        Scene s=new Scene();s.set(1,1,128);s.slot(41,0,1,0x34);s.slot(41,0x22,1,8);
        s.primary=player(1,0x1001234,0x1e05678,0x123,0x100,10,41);
        var o=CdR4Block.run(s.in());ok(o);assertEquals(40,o.prefix().primary().standObject());
        assertEquals(0,b(at(o,41),0x22));assertEquals(0x1dd5678,o.prefix().primary().actor().motion().centreY16_16());
        assertEquals(s.secondary,o.prefix().secondary());assertEquals(8,b(s.pool.state(41),0x22));
    }
    @Test void mappingFaultRetainsOnlyReachedInitStores() {
        Scene s=new Scene();s.data.mapFault=Mapping.BLOCK;var o=CdR4Block.run(s.in());assertTrue(o.stopped().isPresent());
        assertEquals(2,b(object(o),0x24));assertEquals(0x446a,w(object(o),2));assertEquals(0,b(object(o),0x19));assertEquals(0,l(object(o),4));
    }
    @Test void sizeSecondReadFaultOccursBeforeEitherDimensionWrite() {
        Scene s=new Scene();s.data.tables.put(Table.BLOCK_SIZES,bytes(31));var o=CdR4Block.run(s.in());
        assertTrue(o.stopped().isPresent());assertEquals(0,b(object(o),0x17));assertEquals(0,b(object(o),0x16));assertEquals(0x20abcd,l(object(o),4));
    }
    @Test void twinInitDoesNotRunMotionAndPreservesDirtyChildFields() {
        Scene s=new Scene();s.slot(32,0x3a,1,0xa7);s.slot(32,10,2,0x1234);s.set(0x12,2,0x8899);
        var o=CdR4TwinMoveBlock.run(s.in());ok(o);assertEquals(0xa7,b(at(o,32),0x3a));assertEquals(0x1234,w(at(o,32),10));
        assertEquals(0x120,w(at(o,32),8));assertEquals(0xda00,w(at(o,32),0x34));assertEquals(0x8899,w(object(o),0x12));assertEquals(0,b(object(o),0x19)-16);
    }
    @Test void twinSegmentLoadNegatesChildByteBeforeSignExtension() {
        Scene s=new Scene();s.set(0x24,1,2);s.set(0x3c,1,1);var o=CdR4TwinMoveBlock.run(s.in());ok(o);
        assertEquals(128,b(object(o),0x3a));assertEquals(4,w(object(o),0x3e));assertEquals(0xfe00,w(object(o),0x12));assertEquals(0x200abcd,l(object(o),12));
    }
    @Test void twinLongMovementUsesOldSpeedThenWordAccelerationAndWraps() {
        Scene s=new Scene();s.set(0x24,1,2);s.set(12,4,0xfffffff0);s.set(0x12,2,0x7fff);s.set(0x3e,2,4);s.set(0x3a,1,1);s.set(0x3b,1,3);
        var o=CdR4TwinMoveBlock.run(s.in());ok(o);assertEquals(0x007ffef0,l(object(o),12));assertEquals(0x8003,w(object(o),0x12));assertEquals(0,b(object(o),0x3b));
    }
    @Test void crushPartialAllocationKeepsFirstChildAndRequestsParentDelete() {
        Scene s=new Scene();s.fill();s.slot(41,0,1,0);s.set(0x28,1,0);var o=CdR4CrushBlocks.run(s.in());
        assertTrue(o.stopped().isPresent());assertTrue(o.stopped().get().getMessage().contains("c000"));
        assertEquals(0,object(o).id());assertEquals(99,b(at(o,41),0));
    }
    @Test void crushChildrenAreStrictlyLaterPhysicalSlotsAndParentDoesNotDraw() {
        Scene s=new Scene();s.set(0,1,0x33);s.set(0x28,1,0);var o=CdR4CrushBlocks.run(s.in());ok(o);
        for(int n=41;n<49;n++)assertEquals(0x33,b(at(o,n),0));assertEquals(0,b(at(o,32),0));assertEquals(Tail.DESPAWN_ANCHOR_30,o.tail());
        assertEquals(0xbf,w(object(o),0x2a));assertEquals(0x1208000,l(at(o,41),8));
    }
    @Test void crushChildAcceptsReusedRawParentByIdAndRejectsWrongId() {
        Scene s=new Scene();s.set(0x28,1,255);s.set(0x32,2,0xda00);s.set(0,1,0x33);var o=CdR4CrushBlocks.run(s.in());ok(o);assertEquals(Tail.DRAW,o.tail());
        s.set(0,1,0x34);var deleted=CdR4CrushBlocks.run(s.in());ok(deleted);assertEquals(Tail.NONE,deleted.tail());assertEquals(0,object(deleted).id());
    }
    @Test void collapseSplitCopiesOnlyFirst16BytesAndSecondDelayStaysDirty() {
        Scene s=new Scene();s.set(0x24,1,6);s.set(0x28,1,2);s.set(0x2c,2,8);s.slot(33,0x2a,2,0x9abc);s.slot(32,0x2c,4,0x12345678);
        var o=CdR4CollapseFloor.run(s.in());ok(o);assertEquals(4,w(at(o,32),0x2a));assertEquals(0x9abc,w(at(o,33),0x2a));
        assertEquals(0x12345678,l(at(o,32),0x2c));assertEquals(0,b(at(o,32),0x24));assertEquals(255,b(at(o,32),0x29));assertEquals(0xf8,w(at(o,32),8));assertEquals(0x1f8,w(at(o,32),12));
    }
    @Test void collapseFullPoolStillShrinksAndDoesNotInventChildren() {
        Scene s=new Scene();s.fill();s.set(0x24,1,6);s.set(0x28,1,1);var o=CdR4CollapseFloor.run(s.in());ok(o);
        assertEquals(Tail.NONE,o.tail());assertEquals(95,o.prefix().pool().slots().stream().filter(a->a.id()!=0).count());assertEquals(0,object(o).id());
    }
    @Test void fallingPieceCapsSignedLongAndRetainsFraction() {
        Scene s=new Scene();s.set(0x29,1,255);s.set(0x24,1,4);s.set(1,1,128);s.set(0x2c,4,0x15f000);var o=CdR4CollapseFloor.run(s.in());ok(o);
        assertEquals(0x160000,l(object(o),0x2c));assertEquals(0x216abcd,l(object(o),12));
    }
    @Test void pieceInitDoesNotClearDirtyDeltaOrDelay() {
        Scene s=new Scene();s.set(0x29,1,255);s.set(0x2c,4,0x12345678);s.set(0x2a,2,0x9abc);var o=CdR4CollapseFloor.run(s.in());ok(o);
        assertEquals(0x12345678,l(object(o),0x2c));assertEquals(0x9abc,w(object(o),0x2a));assertEquals(0x08080308,l(object(o),0x16));
    }
    @Test void breakFailureAfterFirstDeltaRetainsExactPartialOriginalAndStoppedSound() {
        Scene s=new Scene();s.set(0x24,1,2);s.set(0x21,1,1);s.primary=player(0,0x1001234,0x1005678,0,0x200,0,0);s.data.tables.put(Table.BREAK_DELTAS,longs(-0x148b0));
        var o=CdR4BreakBlock.run(s.in());assertTrue(o.stopped().isPresent());assertEquals(-0x148b0,l(object(o),0x2a));assertEquals(4,b(object(o),0x24));
        assertEquals(0xf8,w(object(o),8));assertEquals(0x1f8,w(object(o),12));assertEquals(1,o.prefix().observations().stream().filter(a->a instanceof Sound).count());
    }
    @Test void breakChildrenUseAlreadyOffsetOriginalAndOwnLongFractions() {
        Scene s=new Scene();s.primary=player(0,0x1001234,0x2005678,0,0,0,0);s.set(0,1,0x50);s.set(0x24,1,2);s.set(0x21,1,1);var o=CdR4BreakBlock.run(s.in());ok(o);
        assertEquals(0x108,w(at(o,32),8));assertEquals(0x1f8,w(at(o,32),12));assertEquals(0,l(at(o,32),8)&65535);
        assertEquals(0x00f4e4dd,l(object(o),8));assertEquals(0x01f7a31d,l(object(o),12));
    }
    @Test void wallSavedBitIsSetOnceAndDirtyFractionSurvives() {
        Scene s=new Scene();s.set(0x23,1,2);var o=CdR4MoveWall.run(s.in());ok(o);assertEquals(0xffb009,l(object(o),0x2c));
        assertEquals(1,o.prefix().savedFlags().unsignedByte(9));assertEquals(60,w(object(o),0x2a));assertEquals(0x100beef,l(object(o),8));
        var in=s.in();var saved=new byte[766];saved[9]=1;var other=new Inputs(in.pool(),40,in.primary(),in.secondary(),in.gates(),in.globals(),in.solid(),in.data(),in.sine(),in.terrain(),CdAssetBytes.copyOf(saved),1,in.water(),in.widths());
        var r=CdR4MoveWall.run(other);ok(r);assertEquals(6,b(object(r),0x24));assertEquals(0x1c0beef,l(object(r),8));
    }
    @Test void wallMotionMovesAfterSolidAndCountsWordTimer() {
        Scene s=new Scene();s.set(0x24,1,4);s.set(0x2a,2,1);var o=CdR4MoveWall.run(s.in());ok(o);
        assertEquals(0x1013eef,l(object(o),8));assertEquals(6,b(object(o),0x24));assertEquals(Tail.DRAW_WALL_WINDOW,o.tail());
    }
    @Test void woodInitDeletesAtOtherWaterTargetsWithoutSolid() {
        Scene s=new Scene();s.water=new Water(0,0,1,0);var o=CdR4WoodBlock.run(s.in());ok(o);assertEquals(Tail.DRAW_DESPAWN_ANCHOR_36,o.tail());assertEquals(0,object(o).id());assertEquals(0x100,w(beforeClear(o),0x36));
    }
    @Test void woodUsesHighSpeedByteAndFaultRetainsProbePreparation() {
        Scene s=new Scene();s.set(0x24,1,8);s.primary=player(1,0x1050000,0x7800000,0x200,0,0,0);
        s.terrain=new Terrain(){public Probe floor(int x,int y){throw new AssertionError();}public Probe wall(boolean r,int x,int y){assertTrue(r);assertEquals(0x787,y);throw new TerrainStopped(new Probe(0,OptionalInt.of(0x44),OptionalInt.empty()),new IllegalStateException("read"));}};
        var o=CdR4WoodBlock.run(s.in());assertTrue(o.stopped().isPresent());assertEquals(7,w(object(o),0x32));assertEquals(0x200444,l(object(o),0x2a));assertEquals(0x44,o.prefix().globals().primaryAngle());
    }
    @Test void swingUsesHighPhaseByteAndRetainsDirtyLowByte() {
        Scene s=new Scene();s.set(0x24,1,2);s.set(0x3c,1,6);s.set(0x3a,2,0xc055);s.set(0x32,2,0x200);s.set(0x36,2,0x100);
        s.sine=a->{assertEquals(0xbf,a);return new CdPlayerMath.SineCosine(-256,0);};var o=CdR4Swing.run(s.in());ok(o);
        assertEquals(0xbfd5,w(object(o),0x3a));assertEquals(0x1a0,w(object(o),12));assertEquals(0xabcd,w(object(o),14));
    }
    @Test void swingInitAllocatesParts6To1WithoutGivingChildrenNewCoordinates() {
        Scene s=new Scene();s.slot(32,8,4,0x12345678);var o=CdR4Swing.run(s.in());ok(o);
        for(int n=32;n<38;n++)assertEquals(38-n,b(at(o,n),0x3c));assertEquals(0x12345678,l(at(o,32),8));
    }
    @Test void rotatePartialInitRetainsFirstChildAndRequestsDespawn() {
        Scene s=new Scene();s.fill();s.slot(32,0,1,0);var o=CdR4RotateBlock.run(s.in());ok(o);
        assertEquals(Tail.DESPAWN_THEN_DRAW_DESPAWN_X,o.tail());assertEquals(0x3d,b(at(o,32),0));assertEquals(0xd800,w(object(o),0x30));
    }
    @Test void rotateChildReadsReusedParentWithoutIdCheckAndKeepsDirtyLatch() {
        Scene s=new Scene();s.set(0x3c,1,255);s.set(0x34,2,0xd800);s.set(0x3d,1,1);s.set(0x3e,2,0x89ab);s.slot(32,0,1,99);s.slot(32,0x1a,1,4);
        var o=CdR4RotateBlock.run(s.in());ok(o);assertEquals(0x89ab,w(object(o),0x3e));assertEquals(Tail.DRAW_DESPAWN_ANCHOR_36,o.tail());
    }
    @Test void rotateForwardSoundOccursOnlyOnExpiredTimer() {
        Scene s=new Scene();s.set(0x24,1,4);s.set(0x3a,1,1);var o=CdR4RotateBlock.run(s.in());ok(o);
        assertEquals(4,b(object(o),0x3a));assertEquals(1,b(object(o),0x3b));assertEquals(0xbf,assertInstanceOf(Sound.class,o.prefix().observations().getFirst()).nativeId());
    }
    @Test void rotateRawUnownedLinkStopsBeforeDrawingAndRetainsInitPrefix() {
        Scene s=new Scene();s.set(0x3c,1,255);s.set(0x34,2,0xd000);var o=CdR4RotateBlock.run(s.in());assertTrue(o.stopped().isPresent());assertEquals(8,b(object(o),0x1a));assertEquals(Tail.NONE,o.tail());
    }
    @ParameterizedTest @ValueSource(ints={0,1,2,3,4,5,6,7,8}) void horizontalTargetsHaveFiniteInitBodies(int type) {
        Scene s=new Scene();s.set(0x28,1,type);var o=CdR4HMoveBlock.run(s.in());ok(o);assertEquals(Tail.DRAW_H_WINDOW,o.tail());assertEquals(2,b(object(o),0x24));
        int[] children={6,6,1,5,1,4,3,6,2},width={48,48,32,32,64,32,16,48,64},height={16,16,16,32,64,32,16,16,16};
        int[] x={0x100,0x100,0xe0,0x100,0x80,0x100,0x110,0x100,0x180},velocity={0,0,0xe000,0xff80,0xfe00,0,0x1000,0,0x200},phase={1,1,1,0,0,0,0x81,1,0};
        assertEquals(children[type],o.prefix().pool().slots().stream().filter(a->a.id()!=0).count()-1);
        assertEquals(width[type],b(object(o),0x19));assertEquals(height[type],b(object(o),0x16));assertEquals(x[type],w(object(o),8));
        assertEquals(velocity[type],w(object(o),0x10));assertEquals(phase[type],b(object(o),0x3a));
    }
    @ParameterizedTest @ValueSource(ints={0,1,2,3,4,5,6,7,8,9,10,11}) void verticalTargetsHaveFiniteInitBodies(int type) {
        Scene s=new Scene();s.set(0x28,1,type);var before=s.pool.capture();var o=CdR4VMoveBlock.run(s.in());ok(o);
        if(type==2) {assertEquals(before.slots(),o.prefix().pool().slots());assertEquals(0,s.data.reads);assertEquals(Tail.NONE,o.tail());}
        else assertEquals(2,b(object(o),0x24));
        int[] children={6,6,0,0,3,3,7,3,3,1,6,5},width={16,16,0,32,18,20,0,16,32,16,16,16},height={64,64,0,96,48,65,0,64,48,32,16,32};
        int[] y={0x240,0x240,0x200,0x1b0,0x230,0x240,0x200,0x240,0x1e0,0x200,0x200,0x200};
        int[] velocity={0x3f54,0x3f54,0,0xaf54,0x3000,0x3f54,0,0x3f54,0xe000,0,0xff54,0x80},phase={0xc1,0xc1,0,0x41,0xc1,1,0,0xc1,0,0,1,0};
        assertEquals(children[type],o.prefix().pool().slots().stream().filter(a->a.id()!=0).count()-1);
        assertEquals(width[type],b(object(o),0x19));assertEquals(height[type],b(object(o),0x16));assertEquals(y[type],w(object(o),12));
        assertEquals(velocity[type],w(object(o),0x12));assertEquals(phase[type],b(object(o),0x3a));
    }
    @Test void horizontalWrapRequiresEqualityAndKeepsFraction() {
        Scene s=new Scene();s.set(0x28,1,4);s.set(0x24,1,2);s.set(0x36,2,0x200);s.set(8,4,0x102beef);s.set(0x10,2,-0x200);var o=CdR4HMoveBlock.run(s.in());ok(o);
        assertEquals(0x300beef,l(object(o),8));s.set(8,4,0x101beef);var missed=CdR4HMoveBlock.run(s.in());ok(missed);assertEquals(0x0ffbeef,l(object(missed),8));
    }
    @Test void horizontalType5ReloadsNewSegmentOnExpiryWithoutMovingTwice() {
        Scene s=new Scene();s.set(0x28,1,5);s.set(0x24,1,2);s.set(0x3a,1,1);s.set(0x3b,1,2);s.set(0x30,2,0x80);var o=CdR4HMoveBlock.run(s.in());ok(o);
        assertEquals(0x1013eef,l(object(o),8));assertEquals(0xff80,w(object(o),0x30));assertEquals(128,b(object(o),0x3a));assertEquals(0x80,w(object(o),0x10));
    }
    @Test void verticalSineVelocitySubtractsOldFractionButRetainsPositionFraction() {
        Scene s=new Scene();s.set(0x28,1,9);s.set(0x24,1,2);s.set(0x32,2,0x200);var o=CdR4VMoveBlock.run(s.in());ok(o);
        assertEquals(0x200abcd,l(object(o),12));assertEquals(0xff54,w(object(o),0x12));
    }
    @Test void vertical8UpdatesPhaseWordIncludingDirtyLowByte() {
        Scene s=new Scene();s.set(0x28,1,8);s.set(0x24,1,2);s.set(0x3a,2,0x40ff);s.set(0x32,2,0x200);var o=CdR4VMoveBlock.run(s.in());ok(o);assertEquals(0x417f,w(object(o),0x3a));
    }
    @Test void completePairAndAll96AreRetainedOnSineCallbackFault() {
        Scene s=new Scene();s.set(0x28,1,0);s.sine=a->{throw new IllegalStateException("read-only math failed");};var before=s.pool.capture();var o=CdR4VMoveBlock.run(s.in());
        assertTrue(o.stopped().isPresent());assertEquals(96,o.prefix().pool().slots().size());assertEquals(s.secondary,o.prefix().secondary());assertEquals(before.slots(),s.pool.capture().slots());
        assertEquals(0xc0,b(object(o),0x3a));assertEquals(0x23,b(at(o,32),0));assertEquals(0x240,w(object(o),0x32));
    }
    @Test void woodSignedSubqBleRetainsOverflowPrefixAndStopsStepping() {
        Scene s=new Scene();s.set(0x24,1,8);s.primary=player(0,0x81000000,0,0,0,0,0);int[] calls={0};
        s.terrain=new Terrain(){public Probe floor(int x,int y){throw new AssertionError();}public Probe wall(boolean right,int x,int y){assertFalse(right);calls[0]++;return new Probe(0,OptionalInt.empty(),OptionalInt.empty());}};
        var o=CdR4WoodBlock.run(s.in());ok(o);assertEquals(1,calls[0]);assertEquals(0x7fff,w(object(o),0x32));assertEquals(0x100,w(object(o),8));
    }
    @Test void collapseSignedSubqBleDeletesOn80To7fOverflow() {
        Scene s=new Scene();s.fill();s.set(0x24,1,6);s.set(0x28,1,0x80);var o=CdR4CollapseFloor.run(s.in());ok(o);
        assertEquals(Tail.NONE,o.tail());assertEquals(0,object(o).id());assertEquals(0x7f,b(beforeClear(o),0x28));assertEquals(6,b(beforeClear(o),0x24));
    }
    @Test void topSharedKernelReleasesActualSecondaryFlagsWithoutChangingItsOtherValues() {
        Scene s=new Scene();s.set(0x24,1,2);s.set(1,1,128);s.set(0x16,1,17);s.set(0x19,1,16);
        s.primary=player(1,0x1001234,0x1e05678,0x123,0x100,10,1);s.secondary=withFlags(s.secondary,10);
        var o=CdR4CollapseFloor.run(s.in());ok(o);assertEquals(40,o.prefix().primary().standObject());
        assertEquals(withFlags(s.secondary,2),o.prefix().secondary());assertEquals(10,s.secondary.actor().motion().flags());
    }
    @Test void solidSecondaryReleaseRemainsAnExplicitMissingSharedOwnerCut() {
        Scene s=new Scene();s.set(1,1,128);s.primary=player(1,0x1001234,0x1e05678,0x123,0x100,10,1);s.secondary=withFlags(s.secondary,10);
        var o=CdR4Block.run(s.in());assertTrue(o.stopped().isPresent());assertEquals(1,o.prefix().primary().standObject());
        assertEquals(s.secondary,o.prefix().secondary());assertEquals(8,b(object(o),0x22));
        var call=assertInstanceOf(Solid.class,o.prefix().observations().getFirst());
        assertEquals(CdSolidObject.StopSite.STAND_RELEASE,assertInstanceOf(CdSolidObject.NativeStopped.class,call.outcome()).stop().site());
    }
    @Test void soundMetadataRetainsTheRealBeforeCutWithoutAnyPublicationApi() {
        Scene s=new Scene();s.set(0x24,1,4);s.set(0x3a,1,1);var o=CdR4RotateBlock.run(s.in());ok(o);
        var sound=assertInstanceOf(Sound.class,o.prefix().observations().getFirst());
        assertEquals(0,b(sound.before().pool().slots().get(8),0x3a));assertEquals(0,b(sound.before().pool().slots().get(8),0x3b));
        assertEquals(4,b(object(o),0x3a));assertEquals(1,b(object(o),0x3b));assertEquals(s.secondary,sound.before().secondary());
    }
    @ParameterizedTest @ValueSource(ints={0,1,2,3,4,5,6}) void horizontal5StartOffsetsAreUnsignedBytes(int part) {
        Scene s=new Scene();s.set(0x28,1,5);s.set(0x3c,1,part);var o=CdR4HMoveBlock.run(s.in());ok(o);
        int[] x={0x100,0x140,0x180,0x1c0,0x180,0x140,0x100},phase={0,1,2,3,4,5,0};
        assertEquals(x[part],w(object(o),8));assertEquals(phase[part],b(object(o),0x3b));assertEquals(0xbeef,w(object(o),10));
    }
    @ParameterizedTest @ValueSource(ints={0,1,2,3,4,5}) void vertical11PartsSignExtendOffsetsAndKeepFractions(int part) {
        Scene s=new Scene();s.set(0x28,1,11);s.set(0x3c,1,part);s.set(0x36,2,0x100);s.set(0x32,2,0x200);var o=CdR4VMoveBlock.run(s.in());ok(o);
        int[] x={0x100,0x100,0xb0,0xb0,0x150,0x150},y={0x200,0x180,0x220,0x1a0,0x220,0x1a0},velocity={0x80,0x80,0xff00,0xff00,0xff00,0xff00};
        assertEquals(x[part],w(object(o),8));assertEquals(y[part],w(object(o),12));assertEquals(velocity[part],w(object(o),0x12));assertEquals(0xabcd,w(object(o),14));
    }
    @ParameterizedTest @ValueSource(ints={0,1,2,3,4,5,6,7,8}) void crushAllNineNativeShapeRowsReadBackwardsInPhysicalOrder(int subtype) {
        Scene s=new Scene();s.set(0,1,0x33);s.set(0x28,1,subtype);var o=CdR4CrushBlocks.run(s.in());ok(o);
        int[][] frames={{2,1,1,2,2,1,1,2},{0,3,1,2,2,1,3,0},{3,0,0,3,3,0,0,3},{1,2,0,0,0,0,2,1},{2,1,1,2,1,2,2,1},
            {0,3,2,0,2,0,0,3},{3,0,0,0,0,0,0,3},{2,1,1,2,3,0,0,3},{1,2,1,1,1,1,2,1}};
        for(int n=0;n<8;n++) {
            int frame=frames[subtype][7-n];var child=at(o,41+n);assertEquals(frame,b(child,0x1a));assertEquals((frame+1)*16,b(child,0x19));
            int x=(n%2==0?0x140-frame*16:0xc0+frame*16);assertEquals((x<<16)+(n%2==0?0x8000:-0x8000),l(child,8));
            assertEquals(0x230-(n/2)*0x20,w(child,12));assertEquals(n%2==0?255:254,b(child,0x28));
        }
    }
    @Test void rotateApproachReadsLiveDisplayWidth19RatherThanCollisionRadius17() {
        Scene s=new Scene();s.set(0x24,1,2);s.set(8,2,0x110);s.set(0x30,2,0xd800);s.slot(32,0x3e,1,255);
        s.terrain=new Terrain(){public Probe floor(int x,int y){throw new AssertionError("native width19 compare must return before probe");}public Probe wall(boolean r,int x,int y){throw new AssertionError();}};
        var o=CdR4RotateBlock.run(s.in());ok(o);assertEquals(2,b(object(o),0x24));assertEquals(0,s.data.reads);
    }
    @ParameterizedTest @ValueSource(ints={0,1,4,5}) void h3FailedLastSearchReadsBorrowedMinus100SpanWithoutHealthyRowSubstitution(int freeSlots) {
        Scene s=new Scene();s.fill();s.set(0x28,1,3);for(int n=0;n<freeSlots;n++)s.slot(32+n,0,1,0);
        // Constructed span DATA, deliberately unlike the healthy row0: not claimed as retail code bytes.
        s.data.tables.put(Table.H3_FAILED_ALLOCATION_OFFSETS,words(-3,5,0x102));
        if(freeSlots==5)s.data.fault=Table.H3_FAILED_ALLOCATION_OFFSETS;
        var o=CdR4HMoveBlock.run(s.in());ok(o);assertEquals(Tail.DRAW_H_WINDOW,o.tail());
        assertEquals(freeSlots<5?0xfd:0x100,w(object(o),8));assertEquals(freeSlots<5?0x205:0x200,w(object(o),12));
        assertEquals(freeSlots<5?0x102:0xff80,w(object(o),0x10));assertEquals(0xbeef,w(object(o),10));assertEquals(0xabcd,w(object(o),14));
        assertEquals(5,o.prefix().observations().stream().filter(e->e instanceof Search).count());
        for(int n=0;n<freeSlots;n++)assertEquals(0x24,b(at(o,32+n),0));
    }
    @Test void h3MissingFailedAllocationSpanStopsBeforeAnyOffsetOrSpeedStore() {
        Scene s=new Scene();s.fill();s.set(0x28,1,3);s.set(0x10,2,0x77aa);var o=CdR4HMoveBlock.run(s.in());
        assertTrue(o.stopped().isPresent());assertEquals(Tail.NONE,o.tail());assertEquals(2,b(object(o),0x24));
        assertEquals(0x100beef,l(object(o),8));assertEquals(0x200abcd,l(object(o),12));assertEquals(0x77aa,w(object(o),0x10));
        assertEquals(32,b(object(o),0x19));assertEquals(32,b(object(o),0x16));
        assertEquals(5,o.prefix().observations().stream().filter(e->e instanceof Search).count());
    }
    @Test void actualUsaTenProgrammesAuthenticateBeforeBorrowingBlockTable() throws Exception {
        String root=System.getProperty("soniccd.media.root");Assumptions.assumeTrue(root!=null&&!root.isBlank(),"requires user media");
        Path cue;try(var paths=Files.list(Path.of(root,"USA"))){var cues=paths.filter(f->f.toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();}
        CdNativeFile[] files={CdNativeFile.R41A,CdNativeFile.R41B,CdNativeFile.R41C,CdNativeFile.R41D,CdNativeFile.R42A,CdNativeFile.R42B,CdNativeFile.R42C,CdNativeFile.R42D,CdNativeFile.R43C,CdNativeFile.R43D};
        String[] pins={"6ddd5b53e1530c15099948053280809271319d35a3c0424b424393ce513054ca","4d9698d1bb3ef967171fcb65e31ae6bb74fbadce706a2016385fba091ec0d4f2","da73414c78fd8108816f39633c9b39fc8d9e8761296b8dfb5bbf457b1ef93e90","101a602503dd1db59bdd52125c450690a816e303e97f2c08907c72f3cc89da11","5ccaa4f12da09f0aaaf9308be81f8345b02c68e11f78173693c347dec1784024","c3c987ae4113d94d0ae03e893dd44c66988ada3baa2f657b26391e654bab6355","e478ce24bf9fdd5216102b42b08c908ee383f6bdb02258ef53802b00b5b27dc8","eb1f5c2d3cb11735438f74b799e0f8626426ce4beb66cbf577359d2ed7454496","c27a455859c4820182c8119d0de3618dde55dd47d34d32c396963e677ac1994c","a11d1d547841697d87d2a1b5fd5c3fe3ebab9aeaf5e8f1d3964f165a521b4789"};
        byte[] pattern=bytes(16,16,16,16,32,16,16,32,32,32,64,16,16,64).copy();
        try(var source=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())) {
            for(int n=0;n<files.length;n++) {
                var mmd=source.mmd(files[n]);byte[] actual=mmd.readFile(0,mmd.fileBytes());assertEquals(262144,actual.length);
                assertEquals(pins[n],HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(actual)));
                int found=-1,count=0;for(int at=0;at<=actual.length-pattern.length;at++)if(Arrays.equals(actual,at,at+pattern.length,pattern,0,pattern.length)){found=at;count++;}
                assertTrue(count>=1,"native Block size bytes in "+files[n]); // retained historical copies can repeat; no active-operand authority claimed
                Scene s=new Scene();s.data.tables.put(Table.BLOCK_SIZES,CdAssetBytes.copyOf(mmd.readFile(found,14)));s.set(0x28,1,6);
                var o=CdR4Block.run(s.in());ok(o);assertEquals(16,b(object(o),0x19));assertEquals(64,b(object(o),0x16));
            }
        }
    }
}
