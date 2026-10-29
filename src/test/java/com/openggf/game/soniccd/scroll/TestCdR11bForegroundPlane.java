package com.openggf.game.soniccd.scroll;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11aAssetLoader;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.level.CdStageTerrain;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.ReadOnlyBufferException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;

import static com.openggf.game.soniccd.scroll.CdR11bForegroundPlane.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Native source-vector component fixtures, not an executed B World/load or GPU proof.
 * Explicit known caller metadata is a constructed input, never a native-history default.
 * Painted full-image oracles use literal tile quads and fixture geometry independently
 * of production lookup/command helpers. Author Java/discovery remain UNRUN.
 */
class TestCdR11bForegroundPlane {
    private static CdR11bAssets actualPast;
    private static final CallerMetadata CALLER_ZERO = new CallerMetadata(OptionalInt.of(0));
    private static final CallerMetadata UNKNOWN = new CallerMetadata(OptionalInt.empty());
    private static final int[] QUAD = {0xA001, 0x4802, 0x1003, 0xF004};

    @BeforeAll
    static void actualConfiguredOriginalUsaSource() throws Exception {
        String configured = System.getProperty("soniccd.media.root");
        assertNotNull(configured, "Explicit original-USA check requires soniccd.media.root");
        assertFalse(configured.isBlank());
        Path cue;
        try (var entries = Files.list(Path.of(configured).resolve("USA"))) {
            var cues = entries.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); cue = cues.getFirst();
        }
        try (var source = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            // Authenticate both original source capabilities through the ordinary media pipeline.
            CdR11aAssetLoader.load(source, CdReleaseVariant.USA, CdStagePayload.R11A.stage());
            actualPast = CdR11bAssets.fromPrograms(CdTimeWarpPrograms.fromPrograms(source));
        }
    }

    @Test
    void initializerDrawsSixteenFullRowsAndPreservesAllFlags() {
        Fixture f = new Fixture(); var before = state();
        var r = f.owner().initialize(before, camera(16,16), 0xA55A, UNKNOWN);
        byte[] expected = seed();
        for (int y=0;y<256;y+=16) for (int x=16;x<=512;x+=16) paintGrid(expected,x,y);
        assertArrayEquals(expected,r.state().nameTable().copy()); assertEquals(512,r.blocks().size());
        assertEquals(0xA55A,r.flagsWord()); assertEquals(Phase.INITIAL_ROW,r.blocks().getFirst().phase());
        assertEquals(0,r.blocks().getFirst().relativeX()); assertEquals(-16,r.blocks().getFirst().relativeY());
        assertEquals(496,r.blocks().getLast().relativeX()); assertEquals(224,r.blocks().getLast().relativeY());
        assertArrayEquals(seed(),before.nameTable().copy());
    }

    @Test
    void incrementalConsumesBothRowsAndBothColumnsInNativeOrder() {
        Fixture f = new Fixture(); var r=f.owner().incremental(state(),camera(16,16),0xFF5A,UNKNOWN);
        assertEquals(76,r.blocks().size()); assertEquals(0xF05A,r.flagsWord());
        assertEquals(Phase.TOP_ROW,r.blocks().get(0).phase()); assertEquals(Phase.BOTTOM_ROW,r.blocks().get(22).phase());
        assertEquals(Phase.LEFT_COLUMN,r.blocks().get(44).phase()); assertEquals(Phase.RIGHT_COLUMN,r.blocks().get(60).phase());
        byte[] expected=seed();
        for (int x=0;x<=336;x+=16) {paintGrid(expected,x,0);paintGrid(expected,x,240);}
        for (int y=0;y<=240;y+=16) {paintGrid(expected,0,y);paintGrid(expected,336,y);}
        assertArrayEquals(expected,r.state().nameTable().copy());
    }

    @Test
    void eachIndividualFlagHasLiteralFullImageAndNonwrites() {
        for(int bit=0;bit<4;bit++) {
            var r=new Fixture().owner().incremental(state(),camera(16,16),(1<< (bit+8))|0x5A,UNKNOWN);
            byte[] expected=seed();
            if(bit<2) for(int x=0;x<=336;x+=16) paintGrid(expected,x,bit==0?0:240);
            else for(int y=0;y<=240;y+=16) paintGrid(expected,bit==2?0:336,y);
            assertArrayEquals(expected,r.state().nameTable().copy(),"bit="+bit);
            assertEquals(bit<2?22:16,r.blocks().size()); assertEquals(0x5A,r.flagsWord());
        }
    }

    @Test
    void simultaneousTopAndBottomExecuteBothInsteadOfBackgroundPriority() {
        var r=new Fixture().owner().incremental(state(),camera(16,16),0x035A,UNKNOWN);
        assertEquals(44,r.blocks().size()); assertEquals(Phase.BOTTOM_ROW,r.blocks().get(22).phase());
        assertEquals(0x5A,r.flagsWord()); byte[] expected=seed();
        for(int x=0;x<=336;x+=16){paintGrid(expected,x,0);paintGrid(expected,x,240);}
        assertArrayEquals(expected,r.state().nameTable().copy());
    }

    @Test
    void unusedHighAndLowFlagsPerformNoTerrainOrMetadataRead() {
        var owner=tables(new byte[0],new byte[0],new byte[0]); var before=state();
        for(int flags:new int[]{0,0x5A,0xF05A}) {
            var r=owner.incremental(before,camera(0xFFFF,0xFFFF),flags,UNKNOWN);
            assertSame(before,r.state()); assertSame(UNKNOWN,r.metadata()); assertEquals(flags,r.flagsWord());
            assertTrue(r.blocks().isEmpty());
        }
    }

    @Test
    void fixedPointFractionsDoNotEnterWorldIntegerWords() {
        var r=new Fixture().owner().incremental(state(),new CdR11aBackgroundPlane.Camera(0x0010ABCD,0x001055AA),0x0F5A,UNKNOWN);
        byte[] expected=seed(); for(int x=0;x<=336;x+=16){paintGrid(expected,x,0);paintGrid(expected,x,240);}
        for(int y=0;y<=240;y+=16){paintGrid(expected,0,y);paintGrid(expected,336,y);}
        assertArrayEquals(expected,r.state().nameTable().copy()); assertEquals(0,r.blocks().getFirst().worldXWord());
        assertEquals(0,r.blocks().getFirst().worldYWord());
    }

    @Test
    void worldWordWrapAndPlaneRingWrapAreIndependent() {
        var r=new Fixture().owner().initialize(state(),camera(0xFFF0,0xFFF0),0xCAFE,UNKNOWN);
        byte[] expected=seed();
        for(int y=-16;y<=224;y+=16) for(int x=0;x<=496;x+=16) paintGrid(expected,(0xFFF0+x)&0xFFFF,(0xFFF0+y)&0xFFFF);
        assertArrayEquals(expected,r.state().nameTable().copy()); assertEquals(0xFFF0,r.blocks().getFirst().worldXWord());
        assertEquals(0xFFE0,r.blocks().getFirst().worldYWord()); assertEquals(0xCAFE,r.flagsWord());
    }

    @Test
    void unknownInheritedMetadataStopsBlankFirstBeforeAnyBlockRead() {
        var before=state(); var f=assertThrows(InvalidPlane.class,()->tables(new byte[0],new byte[0],new byte[2048])
                .initialize(before,camera(0,16),0xA55A,UNKNOWN));
        assertFault(f,Boundary.METADATA_BYTE,-1,Phase.INITIAL_ROW,0xA55A,0);
        assertSame(before,f.completedPrefix().state()); assertSame(UNKNOWN,f.completedPrefix().metadata());
    }

    @Test
    void nonblankReplacesUnknownMetadataBeforeDrawing() {
        var r=constants(0,8,512).initialize(state(),camera(0,16),0xA55A,UNKNOWN);
        byte[] expected=seed(); for(int y=0;y<256;y+=16)for(int x=0;x<512;x+=16)paintQuad(expected,x,y,QUAD);
        assertArrayEquals(expected,r.state().nameTable().copy()); assertEquals(new ChunkMetadata(510),r.metadata());
        assertEquals(new ChunkMetadata(0),r.blocks().getFirst().metadata()); assertEquals(0,r.blocks().getFirst().metadataHighByte());
    }

    @Test
    void blankLayoutUsesTheActualCallerByteAndBlockZero() {
        byte[] blocks=quadBytes(); var known=new CallerMetadata(OptionalInt.of(0x18));
        var r=tables(blocks,new byte[0],new byte[2048]).incremental(state(),camera(16,16),0x015A,known);
        int[] xy={0xE804,0x0803,0x5002,0xB801};byte[] expected=seed();
        for(int x=0;x<=336;x+=16)paintQuad(expected,x,0,xy);
        assertArrayEquals(expected,r.state().nameTable().copy()); assertSame(known,r.metadata());
        assertTrue(r.blocks().stream().allMatch(b->b.rawLayoutByte()==0&&b.blockByteOffset()==0&&b.metadataHighByte()==0x18));
    }

    @Test
    void blankAfterNonblankKeepsTheRealPriorChunkMetadata() {
        byte[] chunks=new byte[512]; word(chunks,30,0x0800);byte[] layout=ones(2048);layout[1]=0;
        var r=tables(quadBytes(),chunks,layout).incremental(state(),camera(16,16),0x015A,UNKNOWN);
        int[] xflip={0x4002,0xA801,0xF804,0x1803};byte[] expected=seed();
        for(int x=0;x<=336;x+=16)paintQuad(expected,x,0,x<240?QUAD:xflip);
        assertArrayEquals(expected,r.state().nameTable().copy());assertEquals(new ChunkMetadata(30),r.metadata());
        assertEquals(0,r.blocks().get(16).rawLayoutByte()); assertEquals(new ChunkMetadata(30),r.blocks().get(16).metadata());
    }

    @Test
    void initializationCarriesMetadataAcrossLaterBlankRows() {
        byte[] chunks=new byte[512];word(chunks,510,0x1000);byte[] layout=new byte[2048];layout[0]=layout[1]=1;
        var r=tables(quadBytes(),chunks,layout).initialize(state(),camera(0,256),0x1234,UNKNOWN);
        int[] yflip={0x0003,0xE004,0xB001,0x5802};byte[] expected=seed();
        for(int x=0;x<512;x+=16)paintQuad(expected,x,240,(x&255)==240?yflip:QUAD);
        for(int y=256;y<=480;y+=16)for(int x=0;x<512;x+=16)paintQuad(expected,x,y,yflip);
        assertArrayEquals(expected,r.state().nameTable().copy());assertEquals(new ChunkMetadata(510),r.metadata());
    }

    @Test
    void malformedLayoutFailsOnlyAtItsReachedByte() {
        var before=state();var f=assertThrows(InvalidPlane.class,()->tables(new byte[0],new byte[0],new byte[0])
                .incremental(before,camera(16,16),0x0F5A,UNKNOWN));
        assertFault(f,Boundary.LAYOUT_READ,0,Phase.TOP_ROW,0x0E5A,0);
        assertSame(before,f.completedPrefix().state());assertSame(UNKNOWN,f.completedPrefix().metadata());
    }

    @Test
    void raw80IsNotBlankAndRetainsTheReachedChunkAddress() {
        byte[] layout=ones(2048);layout[0]=(byte)0x80;var before=state();
        var f=assertThrows(InvalidPlane.class,()->tables(quadBytes(),new byte[54272],layout)
                .initialize(before,camera(0,16),0xA55A,UNKNOWN));
        assertFault(f,Boundary.CHUNK_READ,65024,Phase.INITIAL_ROW,0xA55A,0);
        assertEquals(new ChunkMetadata(65024),f.completedPrefix().metadata());assertSame(before,f.completedPrefix().state());
    }

    @Test
    void truncatedChunkRetainsItsMetadataAddressBeforeMissingWord() {
        var f=assertThrows(InvalidPlane.class,()->tables(quadBytes(),new byte[1],ones(2048))
                .incremental(state(),camera(16,16),0x015A,UNKNOWN));
        assertFault(f,Boundary.CHUNK_READ,0,Phase.TOP_ROW,0x005A,0);assertEquals(new ChunkMetadata(0),f.completedPrefix().metadata());
    }

    @Test
    void nonblankMissingChunkWinsBeforeUnknownInheritedMetadata() {
        var f=assertThrows(InvalidPlane.class,()->tables(new byte[0],new byte[0],ones(2048))
                .incremental(state(),camera(16,16),0x015A,UNKNOWN));
        assertEquals(Boundary.CHUNK_READ,f.boundary());assertEquals(new ChunkMetadata(0),f.completedPrefix().metadata());
    }

    @Test
    void unflippedMissingBottomKeepsOnlyItsCompletedUpperLong() {
        var before=state();var f=assertThrows(InvalidPlane.class,()->constants(0,4,512)
                .incremental(before,camera(16,16),0x0F5A,UNKNOWN));
        assertFault(f,Boundary.BLOCK_READ,4,Phase.TOP_ROW,0x0E5A,0);byte[] expected=seed();paintUpper(expected,0,0,QUAD[0],QUAD[1]);
        assertArrayEquals(expected,f.completedPrefix().state().nameTable().copy());assertArrayEquals(seed(),before.nameTable().copy());
    }

    @Test
    void horizontalFlipMissingBottomKeepsReflectedUpperLong() {
        var f=assertThrows(InvalidPlane.class,()->constants(0x0800,4,512)
                .incremental(state(),camera(16,16),0x015A,UNKNOWN));
        assertFault(f,Boundary.BLOCK_READ,4,Phase.TOP_ROW,0x005A,0);byte[] expected=seed();paintUpper(expected,0,0,0x4002,0xA801);
        assertArrayEquals(expected,f.completedPrefix().state().nameTable().copy());
    }

    @Test
    void verticalAndBothFlipsReadBothLongsBeforeAnyPlaneWrite() {
        for(int flip:new int[]{0x1000,0x1800}) {
            var before=state();var f=assertThrows(InvalidPlane.class,()->constants(flip,4,512)
                    .incremental(before,camera(16,16),0x015A,UNKNOWN));
            assertFault(f,Boundary.BLOCK_READ,4,Phase.TOP_ROW,0x005A,0);
            assertSame(before,f.completedPrefix().state());assertEquals(new ChunkMetadata(0),f.completedPrefix().metadata());
        }
    }

    @Test
    void laterBlockFailureRetainsTheCompletePriorBlock() {
        byte[] chunks=new byte[512];word(chunks,2,1);var before=state();
        var f=assertThrows(InvalidPlane.class,()->tables(quadBytes(),chunks,ones(2048))
                .incremental(before,camera(16,16),0x015A,UNKNOWN));
        assertFault(f,Boundary.BLOCK_READ,8,Phase.TOP_ROW,0x005A,1);byte[] expected=seed();paintQuad(expected,0,0,QUAD);
        assertArrayEquals(expected,f.completedPrefix().state().nameTable().copy());assertEquals(new ChunkMetadata(2),f.completedPrefix().metadata());
    }

    @Test
    void bottomFailureRetainsTopAndItsAlreadyConsumedFlag() {
        var f=assertThrows(InvalidPlane.class,()->constants(0,8,32)
                .incremental(state(),camera(16,16),0x035A,UNKNOWN));
        assertFault(f,Boundary.CHUNK_READ,480,Phase.BOTTOM_ROW,0x005A,22);byte[] expected=seed();
        for(int x=0;x<=336;x+=16)paintQuad(expected,x,0,QUAD);
        assertArrayEquals(expected,f.completedPrefix().state().nameTable().copy());assertEquals(new ChunkMetadata(480),f.completedPrefix().metadata());
    }

    @Test
    void leftFailureKeepsRightPendingAndEarlierCompletedRows() {
        byte[] chunks=new byte[512];word(chunks,32,1);
        var f=assertThrows(InvalidPlane.class,()->tables(quadBytes(),chunks,ones(2048))
                .incremental(state(),camera(16,16),0x0F5A,UNKNOWN));
        assertFault(f,Boundary.BLOCK_READ,8,Phase.LEFT_COLUMN,0x085A,45);byte[] expected=seed();
        for(int x=0;x<=336;x+=16){paintQuad(expected,x,0,QUAD);paintQuad(expected,x,240,QUAD);}
        assertArrayEquals(expected,f.completedPrefix().state().nameTable().copy());
    }

    @Test
    void rightFailureRetainsEveryEarlierCompletedDraw() {
        byte[] chunks=new byte[512];word(chunks,42,1);
        var f=assertThrows(InvalidPlane.class,()->tables(quadBytes(),chunks,ones(2048))
                .incremental(state(),camera(16,16),0x0F5A,UNKNOWN));
        assertFault(f,Boundary.BLOCK_READ,8,Phase.RIGHT_COLUMN,0x005A,61);byte[] expected=seed();
        for(int x=0;x<=336;x+=16){paintQuad(expected,x,0,QUAD);paintQuad(expected,x,240,QUAD);}
        for(int y=0;y<=240;y+=16)paintQuad(expected,0,y,QUAD);
        assertArrayEquals(expected,f.completedPrefix().state().nameTable().copy());
    }

    @Test
    void allFourReflectionsUseIndependentLiteralQuads() {
        int[][] cases={{0,0xA001,0x4802,0x1003,0xF004},{0x800,0x4002,0xA801,0xF804,0x1803},
                {0x1000,0x0003,0xE004,0xB001,0x5802},{0x1800,0xE804,0x0803,0x5002,0xB801}};
        for(int[] c:cases) {
            var r=constants(c[0],8,512).incremental(state(),camera(16,16),0x015A,UNKNOWN);byte[] expected=seed();
            int[] quad=Arrays.copyOfRange(c,1,5);for(int x=0;x<=336;x+=16)paintQuad(expected,x,0,quad);
            assertArrayEquals(expected,r.state().nameTable().copy());assertEquals(c[0]>>>8,r.blocks().getFirst().metadataHighByte());
        }
    }

    @Test
    void malformedTablesAreLazyWhenFlagsAreClear() {
        var before=state();var owner=tables(new byte[3],new byte[1],new byte[1]);
        var r=owner.incremental(before,camera(0xFFFF,0xFFFF),0xF0FF,new ChunkMetadata(-1));
        assertSame(before,r.state());assertEquals(0xF0FF,r.flagsWord());assertTrue(r.blocks().isEmpty());
        assertEquals(new ChunkMetadata(-1),r.metadata());
    }

    @Test
    void explicitChunkMetadataReadsItsActualCurrentByteOnBlankLayout() {
        byte[] chunks=new byte[2];chunks[1]=0x18;var metadata=new ChunkMetadata(1);
        var r=tables(quadBytes(),chunks,new byte[2048]).incremental(state(),camera(16,16),0x015A,metadata);
        byte[] expected=seed();int[] xy={0xE804,0x0803,0x5002,0xB801};for(int x=0;x<=336;x+=16)paintQuad(expected,x,0,xy);
        assertArrayEquals(expected,r.state().nameTable().copy());assertSame(metadata,r.metadata());
        var f=assertThrows(InvalidPlane.class,()->tables(quadBytes(),new byte[0],new byte[2048])
                .incremental(state(),camera(16,16),0x015A,new ChunkMetadata(1)));
        assertFault(f,Boundary.METADATA_BYTE,1,Phase.TOP_ROW,0x005A,0);
    }

    @Test
    void nullWidthsAndStrictTerrainShapeIntroduceNoDefaults() {
        var owner=new Fixture().owner();
        assertThrows(NullPointerException.class,()->bind(null));assertThrows(NullPointerException.class,()->fromTerrain(null));
        assertThrows(NullPointerException.class,()->fromTables(null,CdAssetBytes.copyOf(new byte[0]),CdAssetBytes.copyOf(new byte[0])));
        assertThrows(NullPointerException.class,()->owner.initialize(null,camera(0,0),0,UNKNOWN));
        assertThrows(NullPointerException.class,()->owner.initialize(state(),null,0,UNKNOWN));
        assertThrows(NullPointerException.class,()->owner.initialize(state(),camera(0,0),0,null));
        for(int bad:new int[]{-1,0x10000})assertThrows(IllegalArgumentException.class,()->owner.incremental(state(),camera(0,0),bad,UNKNOWN));
        assertThrows(NullPointerException.class,()->new CallerMetadata(null));
        for(int bad:new int[]{-1,256})assertThrows(IllegalArgumentException.class,()->new CallerMetadata(OptionalInt.of(bad)));
        byte[] fg=new byte[338],bg=new byte[50];fg[0]=41;fg[1]=7;bg[0]=5;bg[1]=7;
        var present=CdStageTerrain.decode(CdStageTerrain.Shape.PPZ1_PRESENT,new byte[6432],new byte[25600],
                new byte[61952],fg,bg,new byte[900],new byte[256],new byte[4096],new byte[4096]);
        assertThrows(IllegalArgumentException.class,()->fromTerrain(present));
    }

    @Test
    void immutableBorrowsAndCompletedObservationsCannotMutateOrTickTheOwner() {
        byte[] blocks=quadBytes(),chunks=new byte[512],layout=ones(2048);var owner=tables(blocks,chunks,layout);
        Arrays.fill(blocks,(byte)0);Arrays.fill(chunks,(byte)0xFF);Arrays.fill(layout,(byte)0);
        var before=state();var one=owner.incremental(before,camera(16,16),0x015A,UNKNOWN);
        var two=owner.incremental(before,camera(16,16),0x015A,UNKNOWN);
        assertArrayEquals(one.state().nameTable().copy(),two.state().nameTable().copy());assertEquals(one.blocks(),two.blocks());
        assertEquals(one.flagsWord(),two.flagsWord());assertEquals(one.metadata(),two.metadata());assertArrayEquals(seed(),before.nameTable().copy());
        assertThrows(UnsupportedOperationException.class,()->one.blocks().clear());
        assertThrows(ReadOnlyBufferException.class,()->one.state().nameTable().buffer().put(0,(byte)0));
        byte[] copy=one.state().nameTable().copy();Arrays.fill(copy,(byte)0);assertNotEquals(0,one.state().word(0,0));
    }

    @Test
    void actualUsaBindingBorrowsAuthenticatedPreparedTerrainButDoesNotProveLoadReadiness() {
        assertEquals(CdStageTerrain.Shape.PPZ1_PAST,actualPast.terrain().shape());
        var owner=bind(actualPast);var before=state();
        // This explicit component caller-byte0 is not claimed as actual LoadLevelData's metadata.
        var r=owner.initialize(before,camera(0x100,0x100),0xCAFE,CALLER_ZERO);
        assertEquals(512,r.blocks().size());assertEquals(0xCAFE,r.flagsWord());assertEquals(4096,r.state().nameTable().length());
        assertArrayEquals(seed(),before.nameTable().copy());
        var same=fromTerrain(actualPast.terrain()).initialize(before,camera(0x100,0x100),0xCAFE,CALLER_ZERO);
        assertArrayEquals(r.state().nameTable().copy(),same.state().nameTable().copy());assertEquals(r.blocks(),same.blocks());
        assertEquals(0xC000,VRAM_BASE);
    }

    private static void assertFault(InvalidPlane f,Boundary boundary,int offset,Phase phase,int flags,int completeBlocks) {
        assertEquals(boundary,f.boundary());assertEquals(offset,f.byteOffset());assertEquals(phase,f.phase());
        assertEquals(flags,f.completedPrefix().flagsWord());assertEquals(completeBlocks,f.completedPrefix().blocks().size());
    }
    private static CdR11bForegroundPlane tables(byte[] blocks,byte[] chunks,byte[] layout) {
        return fromTables(CdAssetBytes.copyOf(blocks),CdAssetBytes.copyOf(chunks),CdAssetBytes.copyOf(layout));
    }
    private static CdR11bForegroundPlane constants(int metadata,int blockBytes,int chunkBytes) {
        byte[] chunks=new byte[chunkBytes];for(int i=0;i+1<chunks.length;i+=2)word(chunks,i,metadata);
        return tables(Arrays.copyOf(quadBytes(),blockBytes),chunks,ones(2048));
    }
    private static byte[] quadBytes(){byte[] b=new byte[8];for(int i=0;i<4;i++)word(b,i*2,QUAD[i]);return b;}
    private static byte[] ones(int length){byte[] b=new byte[length];Arrays.fill(b,(byte)1);return b;}
    private static CdR11aBackgroundPlane.State state(){return new CdR11aBackgroundPlane.State(CdAssetBytes.copyOf(seed()));}
    private static CdR11aBackgroundPlane.Camera camera(int x,int y){return new CdR11aBackgroundPlane.Camera(x<<16,y<<16);}
    private static byte[] seed(){byte[] b=new byte[4096];for(int i=0;i<2048;i++)word(b,i*2,0x8000+i);return b;}
    private static void word(byte[] bytes,int at,int value){bytes[at]=(byte)(value>>>8);bytes[at+1]=(byte)value;}
    private static void paintQuad(byte[] bytes,int x,int y,int[] quad){
        int tx=(x&0x1FF)/8,ty=(y&0xFF)/8;
        word(bytes,(ty*64+tx)*2,quad[0]);word(bytes,(ty*64+tx+1)*2,quad[1]);
        word(bytes,((ty+1)*64+tx)*2,quad[2]);word(bytes,((ty+1)*64+tx+1)*2,quad[3]);
    }
    private static void paintUpper(byte[] bytes,int x,int y,int a,int b){int tx=(x&0x1FF)/8,ty=(y&0xFF)/8;word(bytes,(ty*64+tx)*2,a);word(bytes,(ty*64+tx+1)*2,b);}
    // Independent fixture-grid painter. No production block-lookup or command helper is called.
    private static void paintGrid(byte[] bytes,int x,int y){
        int bx=(x&255)/16,by=(y&255)/16,flip=(bx&1)*0x800+(by&1)*0x1000;
        int[] attributes={0xA000,0x4800,0x1000,0xF000};int[] quad=new int[4];
        for(int py=0;py<2;py++)for(int px=0;px<2;px++){
            int source=((py^(by&1))*2)+(px^(bx&1));
            quad[py*2+px]=(attributes[source]|(((by*16+bx)%128)*4+source+1))^flip;
        }
        paintQuad(bytes,x,y,quad);
    }
    private static final class Fixture {
        final byte[] blocks=new byte[5280],chunks=new byte[54272],layout=ones(2048);
        Fixture(){
            int[] attributes={0xA000,0x4800,0x1000,0xF000};
            for(int b=0;b<660;b++)for(int p=0;p<4;p++)word(blocks,b*8+p*2,attributes[p]|((b%128)*4+p+1));
            for(int y=0;y<16;y++)for(int x=0;x<16;x++)word(chunks,(y*16+x)*2,0x6000|y*16+x|(x&1)*0x800|(y&1)*0x1000);
        }
        CdR11bForegroundPlane owner(){return tables(blocks,chunks,layout);}
    }
}
