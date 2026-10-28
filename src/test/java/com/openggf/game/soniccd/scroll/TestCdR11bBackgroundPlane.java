package com.openggf.game.soniccd.scroll;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11aTerrain;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.level.CdStageTerrain;
import org.junit.jupiter.api.Test;
import java.nio.ReadOnlyBufferException;
import java.util.Arrays;
import java.util.HexFormat;
import static com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Source-only tests-first freeze; ALL JAVA UNRUN. Constructed immutable format fixtures,
 * not media authentication, hardware emulation, current-art residency or a live Past frame.
 * N1ecc5f3a Past Scroll:InitLevelDrawBG899-984/DrawLevelBG1700-803;
 * Block Draw.asm:DrawBlock117-179/GetBlockDataAbsXY202-235/GetBlockVDPCmdAbsXY382-392.
 * Complete expected images use an independent fixed world-space tile mosaic or literal quads;
 * no production Plane, scroll, renderer or native helper generates expected name-table bytes.
 */
class TestCdR11bBackgroundPlane {
    private static final FlagWords FLAGS = new FlagWords(0x005A, 0xA55A, 0x5AA5);

    @Test
    void staticInitializationUsesExistingStateAndAll4096NativeBytes() {
        Fixture f = new Fixture(); State before = state();
        var r = f.owner().initialize(before, live(0x1234, 16), FLAGS);
        byte[] expected = new byte[4096];
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 64; x++) { word(expected, (y * 64 + x) * 2, gridTile(x * 8, y * 8)); }
        }
        assertArrayEquals(expected, r.state().nameTable().copy()); assertArrayEquals(seed(), before.nameTable().copy());
        assertEquals(16, r.sections().size()); assertEquals(512, r.blocks().size()); assertEquals(FLAGS, r.flags());
        assertEquals(1, r.sections().getFirst().tableIndex()); assertEquals(16, r.sections().getLast().tableIndex());
        assertEquals(CameraSource.LIVE_BG1, r.blocks().getFirst().camera());
        assertEquals(0, r.blocks().getFirst().relativeX());
        assertEquals(0x60000003, r.blocks().getFirst().upperVdpCommand());
        assertEquals(0x60800003, r.blocks().getFirst().lowerVdpCommand());
    }

    @Test
    void nativeBandsUseFiveZerosFiveSixesTwentyThreeFoursAndTailZero() {
        Fixture f = new Fixture(); f.ids = nativeIds();
        var r = f.owner().initialize(state(), new LiveCameras(camera(0x1234,16),camera(48,16),camera(80,16)), FLAGS);
        assertEquals(392, r.blocks().size()); // Four static32 + twelve dynamic22.
        byte[] expected = seed();
        for (int row = 0; row < 16; row++) {
            int id = row < 4 ? 0 : row < 9 ? 6 : 4, x = id == 0 ? 0 : id == 6 ? 64 : 32;
            paintGridRow(expected, x, row * 16, id == 0 ? 32 : 22);
            assertEquals(new SectionRead(Phase.INITIAL_ROW,row+1,id,-16+row*16,true), r.sections().get(row));
        }
        assertArrayEquals(expected,r.state().nameTable().copy()); assertEquals(FLAGS,r.flags());
        assertEquals(CameraSource.LIVE_BG3,r.blocks().get(128).camera()); assertEquals(64,r.blocks().get(128).worldXWord());
        assertEquals(CameraSource.LIVE_BG2,r.blocks().get(238).camera()); assertEquals(32,r.blocks().get(238).worldXWord());
    }

    @Test
    void initialTopWrapUsesPastIndex32RatherThanPresent128() {
        Fixture f = new Fixture(); f.ids[32] = 6;
        var r = f.owner().initialize(state(),live(16,0),FLAGS);
        assertEquals(32,r.sections().getFirst().tableIndex()); assertEquals(6,r.sections().getFirst().selector());
        assertEquals(CameraSource.LIVE_BG3,r.blocks().getFirst().camera());
        assertEquals(1,r.sections().get(1).tableIndex()); assertEquals(15,r.sections().getLast().tableIndex());
        assertEquals(502,r.blocks().size());
    }

    @Test
    void initialSelectionMasks512ButNeverReadsTheSuppliedTail33() {
        Fixture f = new Fixture(); f.ids[33] = (byte)0xFF; var owner = f.owner();
        var low = owner.initialize(state(),live(0,0),FLAGS);
        var high = owner.initialize(state(),live(0,0x200),FLAGS);
        assertEquals(low.sections(),high.sections()); assertEquals(512,high.blocks().size());
        assertTrue(high.sections().stream().allMatch(r -> r.tableIndex() >= 1 && r.tableIndex() <= 32));
    }

    @Test
    void incrementalOffscreenByteZeroSelectsCopiedBg2WithoutInitialMask() {
        Fixture f = new Fixture(); f.ids[0] = 4;
        var r = f.owner().incremental(state(),live(500,0),copied(16,0,48,32,80,64),flags(1));
        assertEquals(new SectionRead(Phase.INCREMENTAL_ROW,0,4,-16,true),r.sections().getFirst());
        assertEquals(22,r.blocks().size()); assertEquals(CameraSource.COPIED_BG2,r.blocks().getFirst().camera());
        assertEquals(32,r.blocks().getFirst().worldXWord()); assertEquals(16,r.blocks().getFirst().worldYWord());
        assertEquals(0x61080003,r.blocks().getFirst().upperVdpCommand());
        byte[] expected = seed(); paintGridRow(expected,32,16,22); assertArrayEquals(expected,r.state().nameTable().copy());
    }

    @Test
    void incrementalReadsLiveSelectionYButCopiedCoordinatesAndIgnoresFractions() {
        Fixture f = new Fixture(); f.ids[22] = 4;
        var l = new LiveCameras(new Camera(0x7FFF1234,0x0160ABCD),camera(0,0),camera(0,0));
        var c = new CopiedCameras(camera(32,48),new Camera(0x0200FFFF,0x01100001),camera(0,0));
        var r = f.owner().incremental(state(),l,c,flags(1));
        assertEquals(22,r.sections().getFirst().tableIndex()); assertEquals(496,r.blocks().getFirst().worldXWord());
        assertEquals(256,r.blocks().getFirst().worldYWord()); assertEquals(CameraSource.COPIED_BG2,r.blocks().getFirst().camera());
        assertEquals(0x607C0003,r.blocks().getFirst().upperVdpCommand());
        assertEquals(0x60800003,r.blocks().get(1).lowerVdpCommand());
        byte[] expected = seed(); paintGridRow(expected,496,256,22); assertArrayEquals(expected,r.state().nameTable().copy());
    }

    @Test
    void finiteTail33IsReadBut34FailsWithoutPaddingOrPartialResult() {
        Fixture f = new Fixture(); var owner = f.owner();
        var tail = owner.incremental(state(),live(0,0x120),copied(0,16,0,0,0,0),flags(2));
        assertEquals(33,tail.sections().getFirst().tableIndex()); State before = state();
        assertBoundary(Boundary.SECTION_READ,34,() -> owner.incremental(before,live(0,0x130),copied(0,0,0,0,0,0),flags(2)));
        assertBoundary(Boundary.SECTION_READ,34,() -> owner.incremental(before,live(0,0x130),copied(0,0,0,0,0,0),flags(4)));
        assertArrayEquals(seed(),before.nameTable().copy());
    }

    @Test
    void signedNegativeIncrementalIndicesStopWhileInitialLookupWraps() {
        var owner = new Fixture().owner();
        assertBoundary(Boundary.SECTION_READ,-1,() -> owner.incremental(state(),live(0,0xFFF0),copied(0,0,0,0,0,0),flags(1)));
        assertBoundary(Boundary.SECTION_READ,-1,() -> owner.incremental(state(),live(0,0xFFF0),copied(0,0,0,0,0,0),flags(4)));
        assertEquals(31,owner.initialize(state(),live(0,0xFFF0),FLAGS).sections().getFirst().tableIndex());
    }

    @Test
    void sameFfSelectorUsesUnsignedInitialOffset510AndSignedRowOffsetMinus2() {
        Fixture f = new Fixture(); f.ids[1] = (byte)0xFF; var owner = f.owner(); State before = state();
        assertBoundary(Boundary.CAMERA_POINTER_READ,510,() -> owner.initialize(before,live(0,16),FLAGS));
        assertBoundary(Boundary.CAMERA_POINTER_READ,-2,() -> owner.incremental(before,live(0,16),copied(0,16,0,0,0,0),flags(1)));
        assertArrayEquals(seed(),before.nameTable().copy());
    }

    @Test
    void reachedNullNativePointerIsAnAddressFaultNotAStaticRow() {
        Fixture f = new Fixture(); Arrays.fill(f.pointers,0,4,(byte)0);
        assertBoundary(Boundary.CAMERA_ADDRESS,0,() -> f.owner().initialize(state(),live(0,16),FLAGS));
        var r = f.owner().incremental(state(),live(0,16),copied(0,16,0,0,0,0),flags(1));
        assertEquals(CameraSource.COPIED_BG1,r.blocks().getFirst().camera());
    }

    @Test
    void skippedBadColumnSelectorsReadIdsButNeverReadCameraPointers() {
        Fixture f = new Fixture(); Arrays.fill(f.ids,(byte)0xFF); Arrays.fill(f.pointers,(byte)0); State before = state();
        var r = f.owner().incremental(before,live(0,0),copied(0,0,0,0,0,0),flags(4));
        assertSame(before,r.state()); assertEquals(16,r.sections().size()); assertTrue(r.blocks().isEmpty());
        assertTrue(r.sections().stream().noneMatch(SectionRead::drawn)); assertEquals(FLAGS,r.flags());
    }

    @Test
    void selectedColumnSelectorRemainsUnsignedAtItsActualPointerBoundary() {
        Fixture f = new Fixture(); f.ids[0] = (byte)0x84;
        assertBoundary(Boundary.CAMERA_POINTER_READ,264,() -> f.owner().incremental(state(),live(0,0),copied(0,0,0,0,0,0),flags(0x10)));
    }

    @Test
    void zeroHighFlagsPreserveSameStateAndDoNotSampleBadData() {
        Fixture f = new Fixture(); Arrays.fill(f.ids,(byte)0xFF); Arrays.fill(f.pointers,(byte)0); State before = state();
        var r = f.owner().incremental(before,live(0,0xFFFF),copied(0,0,0,0,0,0),FLAGS);
        assertSame(before,r.state()); assertEquals(FLAGS,r.flags()); assertTrue(r.sections().isEmpty()); assertTrue(r.blocks().isEmpty());
    }

    @Test
    void simultaneousTopAndBottomDrawTopThenKeepRealColumnScan() {
        var r = new Fixture().owner().incremental(state(),live(0,16),copied(0,16,0,0,0,0),flags(3));
        assertEquals(17,r.sections().size()); assertEquals(32,r.blocks().size()); assertEquals(-16,r.sections().getFirst().relativeY());
        assertEquals(Phase.INCREMENTAL_COLUMN,r.sections().getLast().phase()); assertEquals(FLAGS,r.flags());
        byte[] expected = seed(); paintGridRow(expected,0,0,32); assertArrayEquals(expected,r.state().nameTable().copy());
    }

    @Test
    void rightColumnDropsUnrelatedLeftBitsAndUsesCopiedBg2() {
        Fixture f = new Fixture(); Arrays.fill(f.ids,(byte)4);
        var r = f.owner().incremental(state(),live(0,0),copied(16,16,48,16,80,16),flags(0x60));
        assertEquals(16,r.blocks().size()); assertEquals(320,r.blocks().getFirst().relativeX());
        assertEquals(368,r.blocks().getFirst().worldXWord()); assertEquals(CameraSource.COPIED_BG2,r.blocks().getFirst().camera());
        byte[] expected = seed(); for(int y=0;y<=240;y+=16) { paintGridBlock(expected,368,y); }
        assertArrayEquals(expected,r.state().nameTable().copy()); Arrays.fill(f.ids,(byte)6);
        assertTrue(f.owner().incremental(state(),live(0,0),copied(16,16,48,16,80,16),flags(0x60)).blocks().isEmpty());
    }

    @Test
    void rowThenColumnIntersectionUsesNativeOrderAndWordWrap() {
        Fixture f = new Fixture(); Arrays.fill(f.ids,(byte)4);
        var r = f.owner().incremental(state(),live(0,0),copied(16,0,0x1F0,0x100,80,0),flags(0x21));
        assertEquals(17,r.sections().size()); assertEquals(38,r.blocks().size()); byte[] expected = seed();
        paintGridRow(expected,0x1E0,0xF0,22); for(int y=0xF0;y<=0x1E0;y+=16) { paintGridBlock(expected,0x330,y); }
        assertArrayEquals(expected,r.state().nameTable().copy());
        assertEquals(Phase.INCREMENTAL_ROW,r.sections().getFirst().phase());
        assertEquals(Phase.INCREMENTAL_COLUMN,r.sections().getLast().phase());
    }

    @Test
    void onlyBg1HighByteIsConsumedAndBothOtherFlagWordsSurvive() {
        Fixture f = new Fixture(); Arrays.fill(f.ids,(byte)4);
        var r = f.owner().incremental(state(),live(0,0),copied(0,16,48,16,80,16),flags(0xFF));
        assertEquals(0x005A,r.flags().bg1()); assertEquals(0xA55A,r.flags().bg2()); assertEquals(0x5AA5,r.flags().bg3());
        assertEquals(17,r.sections().size()); assertEquals(38,r.blocks().size());
    }

    @Test
    void allFourBlockReflectionsUseLiteralQuadsAcrossTheCompleteImage() {
        int[][] cases = {{0,0xA001,0x4802,0x1003,0xF004},{0x800,0x4002,0xA801,0xF804,0x1803},
                {0x1000,0x0003,0xE004,0xB001,0x5802},{0x1800,0xE804,0x0803,0x5002,0xB801}};
        for(int[] c:cases) {
            Fixture f = new Fixture(); Arrays.fill(f.chunks,(byte)0); word(f.chunks,0,c[0]);
            var r = f.owner().incremental(state(),live(0,0),copied(0,16,0,0,0,0),flags(1)); byte[] expected = seed();
            for(int x=0;x<512;x+=16) {
                if(x==0 || x==256) { paintQuad(expected,x,0,c[1],c[2],c[3],c[4]); }
                else { paintQuad(expected,x,0,0xA001,0x4802,0x1003,0xF004); }
            }
            assertArrayEquals(expected,r.state().nameTable().copy()); assertEquals(c[0]>>>8,r.blocks().getFirst().metadata().highByte());
        }
    }

    @Test
    void allBlankRowUsesActualSectionMetadataAndBlockZeroInsteadOfZeroTiles() {
        Fixture f = new Fixture(); Arrays.fill(f.bg,2,f.bg.length,(byte)0); f.ids[1] = 0x18;
        var r = f.owner().incremental(state(),live(0,0),copied(0,16,0,0,0,0),flags(1)); byte[] expected = seed();
        for(int x=0;x<512;x+=16) { paintQuad(expected,x,0,0xE804,0x0803,0x5002,0xB801); }
        assertArrayEquals(expected,r.state().nameTable().copy());
        assertTrue(r.blocks().stream().allMatch(b -> b.layoutRawByte()==0 && b.blockByteOffset()==0
                && b.metadata().equals(new MetadataRead(MetadataSource.SECTION_IDS,1,0x18))));
    }

    @Test
    void blankAfterLoadedChunkKeepsTheActualPriorChunkMetadataCursor() {
        Fixture f = new Fixture(); f.bg[3] = 0;
        var r = f.owner().incremental(state(),live(0,0),copied(0,16,0,0,0,0),flags(1)); byte[] expected = seed();
        paintGridRow(expected,0,0,16); for(int x=256;x<512;x+=16) { paintQuad(expected,x,0,0x4002,0xA801,0xF804,0x1803); }
        assertArrayEquals(expected,r.state().nameTable().copy());
        assertEquals(new MetadataRead(MetadataSource.CHUNKS,30,0x68),r.blocks().get(16).metadata());
        assertEquals(0,r.blocks().get(16).blockByteOffset());
    }

    @Test
    void blankColumnReadsNextSelectorByteAndFaultsAt34AfterTail33() {
        Fixture f = new Fixture(); Arrays.fill(f.bg,2,f.bg.length,(byte)0); f.ids[0]=4; f.ids[1]=0x18;
        var r = f.owner().incremental(state(),live(0,0),copied(0,0,16,16,0,0),flags(0x10));
        assertEquals(1,r.blocks().size()); byte[] expected=seed(); paintQuad(expected,0,0,0xE804,0x0803,0x5002,0xB801);
        assertArrayEquals(expected,r.state().nameTable().copy());
        assertEquals(new MetadataRead(MetadataSource.SECTION_IDS,1,0x18),r.blocks().getFirst().metadata());
        Arrays.fill(f.ids,(byte)0); f.ids[33]=4; State before=state();
        assertBoundary(Boundary.SECTION_READ,34,() -> f.owner().incremental(before,live(0,0x120),copied(0,0,16,16,0,0),flags(0x10)));
        assertArrayEquals(seed(),before.nameTable().copy());
    }

    @Test
    void nativeLayoutColumn64CanReachTheNextRetainedForegroundHalf() {
        Fixture f = new Fixture(); f.fg[2+42]=2; Arrays.fill(f.ids,(byte)4);
        var r = f.owner().incremental(state(),live(0,0),copied(0,0,0x4010,16,0,0),flags(0x10));
        var b = r.blocks().getFirst(); assertEquals(0x80,b.layoutByteOffset()); assertEquals(2,b.layoutRawByte());
        assertEquals(512,b.metadata().byteOffset()); byte[] expected=seed();
        for(int y=0;y<256;y+=16) { paintQuad(expected,0x4000,y,0xA001,0x4802,0x1003,0xF004); }
        assertArrayEquals(expected,r.state().nameTable().copy());
    }

    @Test
    void rawLayout80IsNotBlankAndStopsAtTheRealUnloadedChunk128() {
        Fixture f = new Fixture(); f.bg[2]=(byte)0x80;
        assertBoundary(Boundary.CHUNK_READ,127*512,() -> f.owner().incremental(state(),live(0,0),copied(0,16,0,0,0,0),flags(1)));
    }

    @Test
    void pastTableShapesRejectPresentPaddingAndKeepExactOffsets() {
        Fixture f = new Fixture(); var terrain=f.terrain();
        for(int count:new int[]{33,35,130}) {
            assertBoundary(Boundary.TABLE_SHAPE,count,() -> CdR11bBackgroundPlane.fromTables(terrain,
                    CdAssetBytes.copyOf(new byte[count]),CdAssetBytes.copyOf(f.pointers)));
        }
        for(int count:new int[]{15,17}) {
            assertBoundary(Boundary.TABLE_SHAPE,count,() -> CdR11bBackgroundPlane.fromTables(terrain,
                    CdAssetBytes.copyOf(f.ids),CdAssetBytes.copyOf(new byte[count])));
        }
    }

    @Test
    void namedViewsRequireBothPastSelectorsAndBothExactExtents() {
        Fixture f = new Fixture(); var terrain=f.terrain();
        var ids=view(CdR11bAssets.Asset.BG_CAMERA_SECT_IDS,f.ids);
        var pointers=view(CdR11bAssets.Asset.BG_CAMERA_SECTS,f.pointers);
        assertEquals(512,CdR11bBackgroundPlane.fromViews(terrain,ids,pointers).initialize(state(),live(0,16),FLAGS).blocks().size());
        assertBoundary(Boundary.VIEW_BINDING,0,() -> CdR11bBackgroundPlane.fromViews(terrain,pointers,ids));
        assertBoundary(Boundary.VIEW_BINDING,0,() -> CdR11bBackgroundPlane.fromViews(terrain,
                view(CdR11bAssets.Asset.BG_CAMERA_SECT_IDS,new byte[33]),pointers));
        assertBoundary(Boundary.VIEW_BINDING,0,() -> CdR11bBackgroundPlane.fromViews(terrain,
                ids,view(CdR11bAssets.Asset.BG_CAMERA_SECTS,new byte[15])));
        assertBoundary(Boundary.VIEW_BINDING,0,() -> CdR11bBackgroundPlane.fromViews(null,pointers,ids));
    }

    @Test
    void sourceShapeCannotRelabelPresentAndLegacyStillRejectsPastTableExtent() {
        Fixture f = new Fixture(); byte[] fg=new byte[338],bg=new byte[50]; fg[0]=41;fg[1]=7;bg[0]=5;bg[1]=7;
        var present=CdStageTerrain.decode(CdStageTerrain.Shape.PPZ1_PRESENT,new byte[6432],new byte[25600],
                new byte[61952],fg,bg,new byte[900],new byte[256],new byte[4096],new byte[4096]);
        assertThrows(IllegalArgumentException.class,() -> CdR11bBackgroundPlane.fromTables(present,
                CdAssetBytes.copyOf(f.ids),CdAssetBytes.copyOf(f.pointers)));
        var legacy=CdR11aTerrain.decode(new byte[6432],new byte[25600],new byte[61952],fg,bg,
                new byte[900],new byte[256],new byte[4096],new byte[4096]);
        assertBoundary(Boundary.TABLE_SHAPE,34,() -> CdR11aBackgroundPlane.fromTables(legacy,
                CdAssetBytes.copyOf(f.ids),CdAssetBytes.copyOf(f.pointers)));
    }

    @Test
    void immutableBorrowsAndCompletedObservationsCannotMutateOrTickTheOwner() {
        Fixture f = new Fixture(); var owner=f.owner(); State before=state();
        Arrays.fill(f.ids,(byte)0xFF);Arrays.fill(f.pointers,(byte)0);Arrays.fill(f.blocks,(byte)0);
        Arrays.fill(f.chunks,(byte)0);Arrays.fill(f.bg,(byte)0);
        var one=owner.initialize(before,live(0,16),FLAGS);var two=owner.initialize(before,live(0,16),FLAGS);
        assertArrayEquals(one.state().nameTable().copy(),two.state().nameTable().copy());
        assertEquals(one.blocks(),two.blocks());assertEquals(one.sections(),two.sections());assertArrayEquals(seed(),before.nameTable().copy());
        byte[] copy=one.state().nameTable().copy();Arrays.fill(copy,(byte)0);assertNotEquals(0,one.state().word(0,0));
        assertThrows(ReadOnlyBufferException.class,() -> one.state().nameTable().buffer().put(0,(byte)0));
        assertThrows(UnsupportedOperationException.class,() -> one.blocks().clear());
        assertThrows(UnsupportedOperationException.class,() -> one.sections().clear());
    }

    @Test
    void nullGuardsAndFiniteSourceDescriptorsIntroduceNoDefaultTerrainOrState() {
        Fixture f = new Fixture();var owner=f.owner();
        assertThrows(NullPointerException.class,() -> CdR11bBackgroundPlane.bind(null));
        assertThrows(NullPointerException.class,() -> CdR11bBackgroundPlane.fromTables(null,CdAssetBytes.copyOf(f.ids),CdAssetBytes.copyOf(f.pointers)));
        assertThrows(NullPointerException.class,() -> CdR11bBackgroundPlane.fromViews(f.terrain(),null,view(CdR11bAssets.Asset.BG_CAMERA_SECTS,f.pointers)));
        assertThrows(NullPointerException.class,() -> owner.initialize(null,live(0,0),FLAGS));
        assertThrows(NullPointerException.class,() -> owner.incremental(state(),live(0,0),null,FLAGS));
        assertEquals(0x3398,CdR11bAssets.Asset.BG_CAMERA_SECT_IDS.fileOffset());assertEquals(34,CdR11bAssets.Asset.BG_CAMERA_SECT_IDS.length());
        assertEquals(0x33BA,CdR11bAssets.Asset.BG_CAMERA_SECTS.fileOffset());assertEquals(16,CdR11bAssets.Asset.BG_CAMERA_SECTS.length());
    }

    private static void assertBoundary(Boundary expected,int offset,org.junit.jupiter.api.function.Executable action) {
        InvalidPlane f=assertThrows(InvalidPlane.class,action);assertEquals(expected,f.boundary());assertEquals(offset,f.byteOffset());
    }
    private static CdR11bAssets.View view(CdR11bAssets.Asset asset,byte[] bytes) {
        return new CdR11bAssets.View(asset,new CdR11bAssets.FileRange(asset.range().file(),asset.range().domain(),
                asset.fileOffset(),bytes.length),CdAssetBytes.copyOf(bytes));
    }
    private static FlagWords flags(int high) { return new FlagWords((high<<8)|0x5A,FLAGS.bg2(),FLAGS.bg3()); }
    private static State state() { return new State(CdAssetBytes.copyOf(seed())); }
    private static Camera camera(int x,int y) { return new Camera(x<<16,y<<16); }
    private static LiveCameras live(int x,int y) { return new LiveCameras(camera(x,y),camera(48,y),camera(80,y)); }
    private static CopiedCameras copied(int x1,int y1,int x2,int y2,int x3,int y3) {
        return new CopiedCameras(camera(x1,y1),camera(x2,y2),camera(x3,y3));
    }
    private static byte[] seed() {
        byte[] b=new byte[4096];for(int i=0;i<2048;i++) { word(b,i*2,0x8000+i); }return b;
    }
    private static void word(byte[] bytes,int at,int value) { bytes[at]=(byte)(value>>>8);bytes[at+1]=(byte)value; }
    // Expected image coordinates, never a native command decoder/production block lookup.
    private static int gridTile(int worldX,int worldY) {
        int bx=(worldX&255)/16,by=(worldY&255)/16;
        int px=((worldX&15)/8)^(bx&1),py=((worldY&15)/8)^(by&1),i=py*2+px;
        int[] attributes={0xA000,0x4800,0x1000,0xF000};
        return (attributes[i]|(((by*16+bx)%128)*4+i+1))^((bx&1)*0x800)^((by&1)*0x1000);
    }
    private static void paintGridBlock(byte[] ram,int x,int y) {
        x&=0xFFF0;y&=0xFFF0;
        for(int dy=0;dy<16;dy+=8) { for(int dx=0;dx<16;dx+=8) {
            word(ram,((((y+dy)/8)%32)*64+((x+dx)/8)%64)*2,gridTile(x+dx,y+dy));
        } }
    }
    private static void paintGridRow(byte[] ram,int x,int y,int count) {
        for(int i=0;i<count;i++) { paintGridBlock(ram,(x+i*16)&0xFFFF,y); }
    }
    private static void paintQuad(byte[] ram,int x,int y,int a,int b,int c,int d) {
        int tx=(x&0x1FF)/8,ty=(y&0xFF)/8;
        word(ram,(ty*64+tx)*2,a);word(ram,(ty*64+tx+1)*2,b);
        word(ram,((ty+1)*64+tx)*2,c);word(ram,((ty+1)*64+tx+1)*2,d);
    }
    private static byte[] nativeIds() {
        return HexFormat.of().parseHex("00000000000606060606040404040404040404040404040404040404040404040400");
    }
    private static final class Fixture {
        byte[] ids=new byte[34];
        final byte[] pointers=HexFormat.of().parseHex("00fff70800fff70800fff71000fff718");
        final byte[] blocks=new byte[5280],patterns=new byte[23872],chunks=new byte[54272];
        final byte[] fg=new byte[170],bg=new byte[22];
        Fixture() {
            fg[0]=41;fg[1]=3;bg[0]=9;bg[1]=1;Arrays.fill(fg,2,fg.length,(byte)1);Arrays.fill(bg,2,bg.length,(byte)1);
            int[] attributes={0xA000,0x4800,0x1000,0xF000};
            for(int b=0;b<660;b++) { for(int p=0;p<4;p++) { word(blocks,b*8+p*2,attributes[p]|((b%128)*4+p+1)); } }
            for(int y=0;y<16;y++) { for(int x=0;x<16;x++) {
                word(chunks,(y*16+x)*2,0x6000|y*16+x|(x&1)*0x800|(y&1)*0x1000);
            } }
        }
        CdStageTerrain terrain() {
            return CdStageTerrain.decode(CdStageTerrain.Shape.PPZ1_PAST,blocks,patterns,chunks,fg,bg,
                    new byte[800],new byte[256],new byte[4096],new byte[4096]);
        }
        CdR11bBackgroundPlane owner() {
            return CdR11bBackgroundPlane.fromTables(terrain(),CdAssetBytes.copyOf(ids),CdAssetBytes.copyOf(pointers));
        }
    }
}
