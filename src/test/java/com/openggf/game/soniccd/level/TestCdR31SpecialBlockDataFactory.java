package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.player.CdR31SpecialBlockCollision.Data;
import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.openggf.game.soniccd.level.CdR31aAssets.Asset.*;
import static org.junit.jupiter.api.Assertions.*;

/** Tests FIRST; Java UNRUN. Actual original-USA source DATA, with explicitly constructed
 * partial/malformed format controls. No actor/component run, Source, Frame or World admission.
 */
class TestCdR31SpecialBlockDataFactory {
    @Test void actualFactoryBindsAllCompleteCountedListsInOriginalBADCOrder() throws Exception {
        var a=TestCdR31aAssets.actual().assets();var data=Data.fromAssets(a);
        var ids=List.of(PLAYER_BLOCKS_B,PLAYER_BLOCKS_A,PLAYER_BLOCKS_D,PLAYER_BLOCKS_C);
        int[][] words={
                {1,0x145,0x146},
                {15,0x13C,0x146,0x19B,0x1AE,0x83,0x84,0x89,0x8A,0x77,0x76,0x80,0x7F,0x7E,0x7D,0x7C,0x82},
                {15,0x13C,0x146,0x165,0x166,0x83,0x84,0x89,0x8A,0x77,0x76,0x80,0x7F,0x7E,0x7D,0x7C,0x82},
                {15,0x13C,0x146,0,0,0x83,0x84,0x89,0x8A,0x77,0x76,0x80,0x7F,0x7E,0x7D,0x7C,0x82}};
        assertEquals(4,data.blockLists().size());
        for(int i=0;i<4;i++){
            var raw=data.blockLists().get(i).orElseThrow();assertSame(a.view(ids.get(i)),raw);
            assertArrayEquals(words[i],words(raw));assertEquals(2+2*(words[i][0]+1),raw.length());
        }
    }

    @Test void actualFactoryBindsExactlyThreeOriginalSixteenColumnBumperProfiles() throws Exception {
        var a=TestCdR31aAssets.actual().assets();var data=Data.fromAssets(a);
        var ids=List.of(PLAYER_BUMPER_COLLIDE_1,PLAYER_BUMPER_COLLIDE_2,PLAYER_BUMPER_COLLIDE_3);
        String[] expected={"01010102020203030304040405050506","06060707070808080909090a0a0a0b0b","0b0c0c0c0d0d0d0e0e0e0f0f0f101010"};
        assertEquals(3,data.bumperShapes().size());
        for(int i=0;i<3;i++){
            var raw=data.bumperShapes().get(i).orElseThrow();assertSame(a.view(ids.get(i)),raw);
            assertEquals(16,raw.length());assertArrayEquals(HexFormat.of().parseHex(expected[i]),raw.copy());
            assertEquals(0x206B34+16*i,a.range(ids.get(i)).nativeAddress());
        }
    }

    @Test void completeDirectoryOperandsAndOriginalPcRootsRetainTheirSourceMeaning() throws Exception {
        var a=TestCdR31aAssets.actual().assets();Data.fromAssets(a);
        assertArrayEquals(new int[]{0x2A,8,0x52,0x30},words(a.view(PLAYER_BLOCKS)));
        assertArrayEquals(new int[]{6,22,38},words(a.view(PLAYER_BUMPER_COLLIDE)));
        assertArrayEquals(new int[]{0x20,0x20,0xD0,0xD0,0x266,0x122,0x134,0x134,0x242,0x19A,0x19A,0x19A,0x17E,0x122,0x122,0x146},words(a.view(PLAYER_BLOCK_ROUTINES)));
        int[] at={0x683A,0x683E,0x68E8,0x68EC,0x6AC2,0x6AC6};
        String[] code={"323b101c","43fb1018","303b1038","4ebb0034","303b106a","43fb0066"};
        int[] roots={0x206858,0x206858,0x206922,0x206922,0x206B2E,0x206B2E};
        for(int i=0;i<at.length;i++){
            byte[] raw=a.originalImage().readFile(at[i],4);assertEquals(code[i],HexFormat.of().formatHex(raw));
            assertEquals(roots[i],0x200000+at[i]+2+raw[3]);
        }
    }

    @Test void fullOriginalMathViewsRetainKnownValuesAndGenuineUnreadEvenTail() throws Exception {
        var a=TestCdR31aAssets.actual().assets();var data=Data.fromAssets(a);
        var sine=data.sineWords().orElseThrow();var atan=data.arcTanBytes().orElseThrow();
        assertSame(a.view(SINE_TABLE),sine);assertSame(a.view(ARC_TAN_TABLE),atan);
        assertEquals(640,sine.length());assertEquals(258,atan.length());
        assertEquals("bbde7b84eb90b56a2425842a67a0ff0316b3f3b0ff49b956f9b86c1267d75bb9",TestCdR31aAssets.hash(sine.copy()));
        assertEquals("7d0ff986365e0596080368cab93935822e1f39220f50071db5dd075c0cabe66f",TestCdR31aAssets.hash(atan.copy()));
        int[] si={0,1,32,64,128,192,255,256,257,319},sv={0,6,181,256,0,-256,-6,0,6,255};
        for(int i=0;i<si.length;i++)assertEquals(sv[i],(short)sine.unsignedWord(si[i]*2));
        int[] ai={0,1,32,64,128,192,255,256,257},av={0,0,5,10,19,26,32,32,0};
        for(int i=0;i<ai.length;i++)assertEquals(av[i],atan.unsignedByte(ai[i]));
        assertArrayEquals(a.originalImage().readFile(0x79A,640),sine.copy());
        assertArrayEquals(a.originalImage().readFile(0xA80,258),atan.copy());
        // Atan's byte257 is retained actual EVEN data; the existing algorithm reaches at most256.
        assertEquals(0,atan.unsignedByte(257));assertThrows(IndexOutOfBoundsException.class,()->atan.unsignedByte(258));
    }

    @Test void nullAndPubliclyReachableForeignOrShortInputsCannotBecomeFactoryData() throws Exception {
        assertThrows(NullPointerException.class,()->Data.fromAssets(null));
        var f=TestCdR31aAssets.actual();
        assertThrows(IOException.class,()->Data.fromAssets(CdR31aAssets.fromPrograms(TestCdR31aAssets.source(f,f.foreign()),CdStagePayload.R31A.stage())));
        var shortImage=CdMmdImage.parse(f.image().readFile(0,262143));
        assertThrows(IOException.class,()->Data.fromAssets(CdR31aAssets.fromPrograms(TestCdR31aAssets.source(f,shortImage),CdStagePayload.R31A.stage())));
        assertThrows(UnsupportedOperationException.class,()->Data.fromAssets(CdR31aAssets.fromPrograms(TestCdR31aAssets.source(f,f.image()),CdStagePayload.R13D.stage())));
        var range=f.assets().range(PLAYER_BLOCKS);
        assertThrows(IllegalArgumentException.class,()->new CdR31aAssets.Range(PLAYER_BLOCKS,range.fileOffset(),6,range.sha256()));
    }

    @Test void partialConstructorStillPreservesMissingOddAndTruncatedReachedViews() {
        // CONSTRUCTED FORMAT CONTROLS. These values authenticate no source or invocation.
        var missing=new Data(absent(4),absent(3),Optional.empty(),Optional.empty());
        assertTrue(missing.blockLists().stream().allMatch(Optional::isEmpty));
        assertTrue(missing.bumperShapes().stream().allMatch(Optional::isEmpty));
        assertTrue(missing.sineWords().isEmpty());assertTrue(missing.arcTanBytes().isEmpty());
        var odd=CdAssetBytes.copyOf(new byte[]{0,15,1});var sine=CdAssetBytes.copyOf(new byte[]{1});
        var live=CdAssetBytes.copyOf(new byte[257]);var data=new Data(first(odd),absent(3),Optional.of(sine),Optional.of(live));
        assertSame(odd,data.blockLists().getFirst().orElseThrow());assertEquals(3,odd.length());
        assertSame(sine,data.sineWords().orElseThrow());assertSame(live,data.arcTanBytes().orElseThrow());
        assertTrue(Data.unowned().blockLists().stream().allMatch(Optional::isEmpty));
    }

    @Test void constructedExtentAndNullControlsRetainAllFiniteLimitsWithAtan258Cap() {
        // The sole extent delta is the authenticated raw include's 257 live bytes plus actual EVEN.
        assertEquals(258,new Data(absent(4),absent(3),Optional.empty(),Optional.of(bytes(258))).arcTanBytes().orElseThrow().length());
        assertThrows(IllegalArgumentException.class,()->new Data(first(bytes(35)),absent(3),Optional.empty(),Optional.empty()));
        assertThrows(IllegalArgumentException.class,()->new Data(absent(4),firstShape(bytes(17)),Optional.empty(),Optional.empty()));
        assertThrows(IllegalArgumentException.class,()->new Data(absent(4),absent(3),Optional.of(bytes(641)),Optional.empty()));
        assertThrows(IllegalArgumentException.class,()->new Data(absent(4),absent(3),Optional.empty(),Optional.of(bytes(259))));
        assertThrows(IllegalArgumentException.class,()->new Data(absent(3),absent(3),Optional.empty(),Optional.empty()));
        assertThrows(IllegalArgumentException.class,()->new Data(absent(4),absent(2),Optional.empty(),Optional.empty()));
        assertThrows(NullPointerException.class,()->new Data(null,absent(3),Optional.empty(),Optional.empty()));
        assertThrows(NullPointerException.class,()->new Data(absent(4),absent(3),null,Optional.empty()));
        var nullEntry=new ArrayList<>(absent(4));nullEntry.set(1,null);
        assertThrows(NullPointerException.class,()->new Data(nullEntry,absent(3),Optional.empty(),Optional.empty()));
    }

    @Test void returnedDataRetainsOnlyImmutableViewsAndNoAssetsOrAuthorityField() throws Exception {
        var a=TestCdR31aAssets.actual().assets();var data=Data.fromAssets(a);
        assertThrows(UnsupportedOperationException.class,()->data.blockLists().clear());
        assertThrows(UnsupportedOperationException.class,()->data.bumperShapes().clear());
        var original=data.blockLists().get(1).orElseThrow();byte[] copy=original.copy();copy[0]^=1;
        assertEquals(15,original.unsignedWord(0));
        assertThrows(java.nio.ReadOnlyBufferException.class,()->original.buffer().put(0,(byte)1));
        assertEquals(List.of("blockLists","bumperShapes","sineWords","arcTanBytes"),Arrays.stream(Data.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toList());
        assertTrue(Arrays.stream(Data.class.getDeclaredFields()).noneMatch(x->x.getType()==CdR31aAssets.class));
        assertFalse(Arrays.stream(Data.class.getDeclaredMethods()).anyMatch(x->Set.of("source","world","frame","owner").contains(x.getName())));
    }

    private static int[] words(CdAssetBytes raw){int[] out=new int[raw.length()/2];for(int i=0;i<out.length;i++)out[i]=raw.unsignedWord(i*2);return out;}
    private static CdAssetBytes bytes(int size){return CdAssetBytes.copyOf(new byte[size]);}
    private static List<Optional<CdAssetBytes>> absent(int size){return Collections.nCopies(size,Optional.empty());}
    private static List<Optional<CdAssetBytes>> first(CdAssetBytes raw){return List.of(Optional.of(raw),Optional.empty(),Optional.empty(),Optional.empty());}
    private static List<Optional<CdAssetBytes>> firstShape(CdAssetBytes raw){return List.of(Optional.of(raw),Optional.empty(),Optional.empty());}
}
