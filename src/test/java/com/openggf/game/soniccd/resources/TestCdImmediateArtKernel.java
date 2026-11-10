package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.media.CdMmdImage;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit constructed malformed-format controls for the extracted package-private algorithm. Java UNRUN. */
class TestCdImmediateArtKernel {
    record Value(CdAssetBytes table,List<CdAssetBytes> tiles,int remaining,CdR11aPlcBuffer.State queue){}
    static final class Fault extends UnsupportedOperationException {
        final CdImmediateArtKernel.Reason reason;final Value state;final CdImmediateArtKernel.Operation operation;final long source;final int destination;final CdAssetBytes ram;
        Fault(CdImmediateArtKernel.Reason r,Value s,CdImmediateArtKernel.Operation o,long p,int d,CdAssetBytes b,Throwable c){super(c);reason=r;state=s;operation=o;source=p;destination=d;ram=b;}
    }
    private static Value fresh(){return new Value(TestCdR13SectionArt.dirty(),List.of(),0,CdR11aPlcBuffer.cleared());}
    private static CdImmediateArtKernel<Value,List<CdAssetBytes>> kernel(byte[] bytes,int failDestination,CdAssetBytes product){
        var b=new CdSectionArtKernel.Binding(new Object(),CdNativeFile.R13C,CdMmdImage.Domain.ORIGINAL_WORD_RAM);var source=new CdSectionArtKernel.Source(b,new Object(),CdNativeFile.R13C,CdMmdImage.Domain.ORIGINAL_WORD_RAM,0x100,bytes.length,0x200100,CdAssetBytes.copyOf(bytes));
        return new CdImmediateArtKernel<>(Map.of(1,List.of(new CdR11aPlcBuffer.Entry(0x200100,0))),Map.of(0x200100L,source),source,()->product,CdAssetBytes.copyOf(new byte[384]),0x232F3C,new CdImmediateArtKernel.States<>(){
            public void checked(Value s){Objects.requireNonNull(s);}public CdAssetBytes codeTable(Value s){return s.table();}public List<CdAssetBytes> residency(Value s){return s.tiles();}public int remainingTilesWord(Value s){return s.remaining();}public CdR11aPlcBuffer.State queue(Value s){return s.queue();}public Value withValues(Value s,CdAssetBytes t,List<CdAssetBytes> r){return new Value(t,r,s.remaining(),s.queue());}
            public RuntimeException failure(CdImmediateArtKernel.Reason r,Value s,CdImmediateArtKernel.Operation o,long p,int d,CdAssetBytes prefix,Throwable c){return new Fault(r,s,o,p,d,prefix,c);}
            public void admitDestination(Value s,int d,CdImmediateArtKernel.Operation o,long p){if(d==failDestination)throw new Fault(CdImmediateArtKernel.Reason.RESIDENCY,s,o,p,d,null,null);}
            public List<CdAssetBytes> commitTile(Value s,int d,CdAssetBytes bytes,CdImmediateArtKernel.Operation o,long p){admitDestination(s,d,o,p);var list=new ArrayList<>(s.tiles());list.add(bytes);return List.copyOf(list);}
        });
    }
    @Test void malformedImmediatePrepareRetainsCompletedTableStoresAndOriginalCauseAndOperation(){
        byte[] bad={0,1,(byte)0x80,1,0,(byte)0x81,1,2};var k=kernel(bad,-1,CdAssetBytes.copyOf(new byte[32]));var before=fresh();var f=assertThrows(Fault.class,()->k.loadImmediateList(before,1));assertEquals(CdImmediateArtKernel.Reason.DECODE_PREPARE,f.reason);assertEquals(CdImmediateArtKernel.Operation.IMMEDIATE_LIST,f.operation);assertInstanceOf(CdNemesisPlcDecoder.DecodeException.class,f.getCause());for(int i=0;i<128;i++)assertEquals(0x100,f.state.table().unsignedWord(i*2));assertArrayEquals(before.table().slice(256,256).copy(),f.state.table().slice(256,256).copy());assertTrue(f.state.tiles().isEmpty());assertEquals(0x5A5A,before.table().unsignedWord(0));
    }
    @Test void immediateZeroHeaderAndRamPrepareFailureKeepTheirDistinctPrefixContract(){
        var zero=kernel(new byte[]{0,0,(byte)255,0,0},-1,CdAssetBytes.copyOf(new byte[0]));var z=assertThrows(Fault.class,()->zero.loadImmediateList(fresh(),1));assertEquals(CdImmediateArtKernel.Reason.IMMEDIATE_HEADER_COUNT,z.reason);assertNull(z.ram);var bad=kernel(new byte[]{0,1,(byte)255,0},-1,CdAssetBytes.copyOf(new byte[32]));var f=assertThrows(Fault.class,()->bad.decodeBlocksToRam(fresh()));assertEquals(CdImmediateArtKernel.Reason.DECODE_PREPARE,f.reason);assertEquals(CdImmediateArtKernel.Operation.BLOCKS_RAM,f.operation);assertEquals(0,f.ram.length());assertEquals(0,f.destination);
    }
    @Test void actualRingPrefixFaultKeepsTilesForGraphicsAndRowsForRam()throws Exception{
        var a=TestCdR13SectionArt.actual().good();var registry=com.openggf.game.soniccd.level.CdR13SectionSources.fromAssets(a);var id=com.openggf.game.soniccd.level.CdR13SectionSources.SourceId.ART_RINGS;var raw=registry.view(id,com.openggf.game.soniccd.level.CdR13SectionSources.ReaderKind.NATIVE);var first=CdNemesisPlcDecoder.decodeTile(CdNemesisPlcDecoder.prepare(raw,0x200100,fresh().table()));var prefix=raw.slice(0,first.state().inputOffset());var k=kernel(prefix.copy(),-1,registry.decoded(id).bytes());var before=fresh();
        var immediate=assertThrows(Fault.class,()->k.loadImmediateList(before,1));assertEquals(CdImmediateArtKernel.Reason.DECODE_TILE,immediate.reason);assertFalse(immediate.state.tiles().isEmpty());assertNull(immediate.ram);assertArrayEquals(first.bytes().copy(),immediate.state.tiles().getFirst().copy());assertTrue(before.tiles().isEmpty());
        var ram=assertThrows(Fault.class,()->k.decodeBlocksToRam(before));assertEquals(CdImmediateArtKernel.Reason.DECODE_TILE,ram.reason);assertEquals(CdImmediateArtKernel.Operation.BLOCKS_RAM,ram.operation);assertTrue(ram.ram.length()>=immediate.state.tiles().size()*32);assertEquals(0,ram.ram.length()%4);assertEquals(ram.ram.length(),ram.destination);assertArrayEquals(registry.decoded(id).bytes().slice(0,ram.ram.length()).copy(),ram.ram.copy());assertTrue(ram.state.tiles().isEmpty());assertInstanceOf(CdNemesisPlcDecoder.DecodeException.class,ram.getCause());
    }
    @Test void completeRuntimeRamLoopReportsMismatchedImmutableProductWithEveryCompletedTile()throws Exception{
        var a=TestCdR13SectionArt.actual().good();var registry=com.openggf.game.soniccd.level.CdR13SectionSources.fromAssets(a);var id=com.openggf.game.soniccd.level.CdR13SectionSources.SourceId.ART_RINGS;var raw=registry.view(id,com.openggf.game.soniccd.level.CdR13SectionSources.ReaderKind.NATIVE);var k=kernel(raw.copy(),-1,com.openggf.game.soniccd.level.CdAssetBytes.copyOf(new byte[32]));var before=fresh();var f=assertThrows(Fault.class,()->k.decodeBlocksToRam(before));assertEquals(CdImmediateArtKernel.Reason.BLOCK_PRODUCT_MISMATCH,f.reason);assertArrayEquals(registry.decoded(id).bytes().copy(),f.ram.copy());assertEquals(448,f.destination);assertEquals(0x200100L+registry.source(id).expectedNativeConsumedBytes(),f.source);assertSame(before.queue(),f.state.queue());assertTrue(f.state.tiles().isEmpty());
    }
    @Test void faultWithinFirstTilePublishesCompletedRowsOnlyAndNoPartialRow() {
        // 1-bit zero code: each symbol emits one 8-pixel row; prefetch leaves
        // eight complete rows possible only with a refill. This finite input
        // stops before the eighth symbol, after seven completed row longs.
        byte[] raw = {0,1,(byte)0x81,0x71,0,(byte)0xFF,0,0};
        var k = kernel(raw,-1,CdAssetBytes.copyOf(new byte[32]));
        var before = fresh();
        var f = assertThrows(Fault.class, () -> k.decodeBlocksToRam(before));
        assertEquals(CdImmediateArtKernel.Reason.DECODE_TILE,f.reason);
        assertEquals(28,f.ram.length());
        assertEquals(28,f.destination);
        for(int i=0;i<28;i++) assertEquals(0x11,f.ram.unsignedByte(i));
        assertTrue(f.state.tiles().isEmpty());
        assertSame(before.queue(),f.state.queue());
    }
    @Test void unfinishedFirstRowPublishesNoPixelsAsRamBytes() {
        byte[] raw={0,1,(byte)0x81,0x01,0,(byte)0xFF,0,0};
        var f=assertThrows(Fault.class,()->kernel(raw,-1,CdAssetBytes.copyOf(new byte[32])).decodeBlocksToRam(fresh()));
        assertEquals(CdImmediateArtKernel.Reason.DECODE_TILE,f.reason);
        assertEquals(0,f.ram.length());assertEquals(0,f.destination);
        var cause=assertInstanceOf(CdNemesisPlcDecoder.DecodeException.class,f.getCause());
        assertEquals(7,cause.diagnostic().pixelsInRow());
    }
    @Test void xorRowsUseActualDecoderOutputInsteadOfRepeatingRawRows() {
        byte[] raw={(byte)0x80,1,(byte)0x81,0x71,0,(byte)0xFF,0,0,0};
        byte[] product=new byte[32];
        for(int row=0;row<8;row+=2)Arrays.fill(product,row*4,row*4+4,(byte)0x11);
        var result=kernel(raw,-1,CdAssetBytes.copyOf(product)).decodeBlocksToRam(fresh());
        for(int at=0;at<32;at++)assertEquals(Byte.toUnsignedInt(product[at]),result.blockDefinitions().unsignedByte(at));
    }
    @Test void ramResultCarriesKnownRowsWithoutInventingTheRestOfTheBank() {
        byte[] raw = {0,1,(byte)0x81,0x71,0,(byte)0xFF,0,0,0};
        byte[] product = new byte[32]; Arrays.fill(product,(byte)0x11);
        var k = kernel(raw,-1,CdAssetBytes.copyOf(product));
        var result = k.decodeBlocksToRam(fresh());
        assertEquals(0x1111,result.blockDefinitions().unsignedWord(30));
        assertEquals(32,assertThrows(CdBlockDefinitionStaging.UnknownRead.class,
                () -> result.blockDefinitions().unsignedWord(32)).byteOffset());
        var original = CdBlockDefinitionStaging.afterFileBufferClear()
                .withCompletedPrefix(result.bytes());
        assertEquals(0,original.blockWord(1023,3));
    }
    @Test void lifeWriterRetainsOneWholeTileOnDestinationFailureAndActiveQueueIsANonwrite(){
        var k=kernel(new byte[]{0,1,(byte)255,0,0},0xB440,CdAssetBytes.copyOf(new byte[32]));var before=new Value(fresh().table(),List.of(),17,CdR11aPlcBuffer.cleared());var f=assertThrows(Fault.class,()->k.loadLifeIcon(before,2));assertEquals(CdImmediateArtKernel.Operation.LIFE_ICON,f.operation);assertEquals(1,f.state.tiles().size());assertSame(before.queue(),f.state.queue());assertSame(before.table(),f.state.table());assertEquals(17,f.state.remaining());assertTrue(before.tiles().isEmpty());assertEquals(0xB440,f.destination);assertEquals(0x23305CL,f.source);
        var active=assertThrows(Fault.class,()->k.loadImmediateList(before,1));assertEquals(CdImmediateArtKernel.Reason.ACTIVE_QUEUED_DECODE,active.reason);assertSame(before,active.state);
    }
}
