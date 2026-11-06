package com.openggf.game.soniccd.level;
import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.*;
import org.junit.jupiter.api.*;
import java.nio.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;
/** Actual original C/D data tests authored first, all Java UNRUN. No art service/World/Frame. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCdR13CapsuleAssets {
    private List<CdR13FutureAssets> originals;
    @Test void bothOriginalProfilesAuthenticateAllTwelveBoundedViewsAndSixRoutineTargets()throws Exception{
        for(var a:actual()){
            var c=CdR13CapsuleAssets.bind(a);
            assertSame(a.identity(),c.identity());
            assertTrue(c.ownsAssets(a));
            assertEquals(12,c.ranges().size());
            for(var r:c.ranges()){
                assertEquals(a.identity().file(),r.file());
                assertEquals(r.sha256(),hash(c.view(r.view()).copy()));
                assertArrayEquals(a.originalImage().readFile(r.fileOffset(),r.length()),c.view(r.view()).copy());
                assertSame(c.view(r.view()),c.view(r.view()));
            }
            int[] rel={
                12,60,160,1516,1714,406
            };
            assertEquals(6,c.routineTargets().size());
            for(int n=0;n<6;n++)assertEquals(c.range(CdR13CapsuleAssets.View.ROUTINE_DIRECTORY).nativeAddress()+rel[n],c.routineTargets().get(n));
            assertEquals(c.range(CdR13CapsuleAssets.View.CAPSULE_CODE).nativeAddress(),c.view(CdR13CapsuleAssets.View.OBJECT_INDEX).unsignedLong(20*4));
        }
    }
    @Test void actualConsumerOperandsSelectFarResidentMappingsScriptsAndSignedPcHelperTables()throws Exception{
        for(var a:actual()){
            var c=CdR13CapsuleAssets.bind(a);
            int delta=a.identity().file()==CdNativeFile.R13C?0:24;
            assertEquals(c.mappingAddress(),u32(a.originalImage().readFile(0x20AF60+delta-0x200000,4)));
            assertEquals(c.mappingAddress(),u32(a.originalImage().readFile(0x20B09C+delta-0x200000,4)));
            assertEquals(c.range(CdR13CapsuleAssets.View.ANIMATIONS).nativeAddress(),u32(a.originalImage().readFile(0x20AF80+delta-0x200000,4)));
            assertEquals(c.range(CdR13CapsuleAssets.View.ANIMATIONS).nativeAddress(),u32(a.originalImage().readFile(0x20B0DA+delta-0x200000,4)));
            assertEquals(0x2481,c.tileWord());
            var code=c.view(CdR13CapsuleAssets.View.CAPSULE_CODE);
            assertEquals(0x45FB,code.unsignedWord(0xAFFC-0xAF16));
            assertEquals(c.range(CdR13CapsuleAssets.View.EXPLOSION_POSITIONS).nativeAddress(),0x20AFFE+delta+(byte)code.unsignedByte(0xAFFF-0xAF16));
        }
    }
    @Test void originalCountedVisualUsesNineFramesThirtySixPiecesAndAll104TilesWithoutPadding()throws Exception{
        for(var a:actual()){
            var c=CdR13CapsuleAssets.bind(a);
            assertEquals(210,c.visual().mappings().length());
            assertEquals(9,c.visual().mappingFrames().size());
            assertEquals(36,c.visual().mappingFrames().stream().mapToInt(f->f.pieces().size()).sum());
            assertEquals(104,c.visual().mappingFrames().stream().flatMap(f->f.pieces().stream()).mapToInt(p->p.tileOffset()+p.tileCount()).max().orElseThrow());
            assertEquals(2,c.visual().animations().size());
            assertEquals(List.of(3,4,5,6,7,8),c.visual().animations().get(1).frameBytes());
            assertEquals(52,c.view(CdR13CapsuleAssets.View.ANIMATIONS).length());
            assertSame(c.visual(),c.visual());
            assertEquals(104,CdR13SectionSources.fromAssets(a).source(CdR13SectionSources.SourceId.ART_FLOWER_CAPSULE).tileCount());
        }
    }
    @Test void completeElevenVelocityWordsKeepSevenReachedAndFourLatentEntries()throws Exception{
        int[] v={
            0,-128,128,-256,256,-384,384,-512,512,-640,640
        };
        for(var a:actual()){
            var c=CdR13CapsuleAssets.bind(a);
            var b=c.view(CdR13CapsuleAssets.View.SEED_VELOCITIES);
            assertEquals(22,b.length());
            for(int n=0;n<11;n++)assertEquals(v[n],(short)b.unsignedWord(n*2));
            assertThrows(IndexOutOfBoundsException.class,()->b.unsignedWord(21));
        }
    }
    @Test void allEightExplosionPairsAndTwentyOneTimeBonusesAreOriginalImmutableBytes()throws Exception{
        for(var a:actual()){
            var c=CdR13CapsuleAssets.bind(a);
            assertEquals("000020f8e000e8f81808f0081008f8f8",HexFormat.of().formatHex(c.view(CdR13CapsuleAssets.View.EXPLOSION_POSITIONS).copy()));
            var b=c.view(CdR13CapsuleAssets.View.TIME_BONUSES);
            assertEquals(42,b.length());
            assertEquals(50000,b.unsignedWord(0));
            assertEquals(0,b.unsignedWord(40));
            assertThrows(IndexOutOfBoundsException.class,()->b.unsignedWord(41));
        }
    }
    @Test void capsule32PaletteAndRestoredLevel96PaletteRemainDistinctActualBindings()throws Exception{
        for(var a:actual()){
            var c=CdR13CapsuleAssets.bind(a);
            var cap=c.capsulePalette();
            assertEquals(0xFB20,cap.destinationWord());
            assertEquals(7,cap.longwordCounter());
            assertEquals(32,cap.bytes().length());
            assertEquals("d190639ece42d9fb7cfb15093f8c512e483155efd04a382dc5028cb975f80e9d",hash(cap.bytes().copy()));
            var restore=c.restoredPalette();
            assertEquals(4,restore.nativeId());
            assertEquals(23,restore.longwordCounter());
            assertEquals(0xFB20,restore.destinationWord());
            assertEquals(96,restore.bytes().length());
            assertArrayEquals(a.view(CdR13FutureAssets.Asset.PAL_LEVEL).copy(),restore.bytes().copy());
            assertEquals(4,a.view(CdR13FutureAssets.Asset.LEVEL_DATA_INDEX).unsignedByte(14));
        }
    }
    @Test void rawViewsListsAndDecodedPiecesCannotBeMutatedOrReadBeyondTheirActualExtents()throws Exception{
        var c=CdR13CapsuleAssets.bind(actual().getFirst());
        for(var v:CdR13CapsuleAssets.View.values()){
            var b=c.view(v);
            assertThrows(ReadOnlyBufferException.class,()->b.buffer().put(0,(byte)0));
            assertThrows(IndexOutOfBoundsException.class,()->b.unsignedByte(b.length()));
        }
        assertThrows(UnsupportedOperationException.class,()->c.ranges().clear());
        assertThrows(UnsupportedOperationException.class,()->c.routineTargets().clear());
        assertThrows(UnsupportedOperationException.class,()->c.visual().mappingFrames().clear());
    }
    @Test void publicMatchingIdentityCannotReplaceActualSourceAndForeignWholeImageIsRejected()throws Exception{
        var a=actual().getFirst();
        var fake=mock(CdR13FutureAssets.class);
        when(fake.identity()).thenReturn(a.identity());
        when(fake.originalImage()).thenReturn(actual().getLast().originalImage());
        assertThrows(IllegalArgumentException.class,()->CdR13CapsuleAssets.bind(fake));
        assertThrows(NullPointerException.class,()->CdR13CapsuleAssets.bind(null));
        for(int bad=0;bad<3;bad++){
            var other=mock(CdR13FutureAssets.class);
            var id=mock(CdR13FutureAssets.Identity.class);
            when(other.identity()).thenReturn(id);
            when(other.originalImage()).thenReturn(a.originalImage());
            when(id.variant()).thenReturn(bad==0?CdReleaseVariant.JAPAN:CdReleaseVariant.USA);
            when(id.file()).thenReturn(bad==1?CdNativeFile.R12B:CdNativeFile.R13C);
            when(id.stage()).thenReturn(a.identity().stage());
            when(id.domain()).thenReturn(bad==2?CdMmdImage.Domain.RELOCATED_COPY:CdMmdImage.Domain.ORIGINAL_WORD_RAM);
            when(id.mmdSha256()).thenReturn(a.identity().mmdSha256());
            assertThrows(IllegalArgumentException.class,()->CdR13CapsuleAssets.bind(other));
        }
        var paletteFake=mock(CdR13FutureAssets.class);
        when(paletteFake.identity()).thenReturn(a.identity());
        when(paletteFake.originalImage()).thenReturn(a.originalImage());
        when(paletteFake.range(CdR13FutureAssets.Asset.LEVEL_DATA_INDEX)).thenReturn(a.range(CdR13FutureAssets.Asset.LEVEL_DATA_INDEX));
        when(paletteFake.view(CdR13FutureAssets.Asset.LEVEL_DATA_INDEX)).thenReturn(a.view(CdR13FutureAssets.Asset.LEVEL_DATA_INDEX));
        var binding=a.paletteBindings().getLast();
        byte[] changed=binding.bytes().copy();
        changed[0]^=2;
        when(paletteFake.paletteBindings()).thenReturn(List.of(new CdR13FutureAssets.PaletteBinding(4,binding.destinationWord(),binding.longwordCounter(),binding.source(),CdAssetBytes.copyOf(changed))));
        assertThrows(IllegalArgumentException.class,()->CdR13CapsuleAssets.bind(paletteFake));
        assertFalse(CdR13CapsuleAssets.bind(a).ownsAssets(actual().getLast()));
    }
    private List<CdR13FutureAssets> actual()throws Exception{
        if(originals!=null)return originals;
        String root=System.getProperty("soniccd.media.root");
        assumeTrue(root!=null&&!root.isBlank(),"Optional original USA media");
        Path cue;
        try(var paths=Files.list(Path.of(root).resolve("USA"))){
            var cues=paths.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1,cues.size());
            cue=cues.getFirst();
        }
        try(var p=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){
            originals=List.of(CdR13FutureAssets.fromPrograms(p,CdStagePayload.R13C.stage()),CdR13FutureAssets.fromPrograms(p,CdStagePayload.R13D.stage()));
        }
        return originals;
    }
    private static int u32(byte[] b){
        return(b[0]&255)<<24|(b[1]&255)<<16|(b[2]&255)<<8|(b[3]&255);
    }
    private static String hash(byte[] b)throws Exception{
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
    }
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
