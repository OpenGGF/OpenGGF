package com.openggf.game.soniccd.level;

import org.junit.jupiter.api.Test;
import java.util.HexFormat;
import java.util.List;
import static com.openggf.game.soniccd.level.CdR31aAssets.Asset.*;
import static org.junit.jupiter.api.Assertions.*;

/** Tests FIRST, Java UNRUN. Actual USA R31A DATA only; no player/helper/Frame/World invocation.
 * Full dc declarations: N1ecc src/r3/player_block_check.asm:171-237,279-295,485-498.
 * Historical labels are source names; actual source operands, not their digits, select these views.
 */
class TestCdR31aSpecialBlockData {
    @Test void periodDirectoryRetainsRawBADCOrderAndRelativeBases() throws Exception {
        var a=TestCdR31aAssets.actual().assets();
        var root=a.view(PLAYER_BLOCKS);assertEquals(8,root.length());
        assertArrayEquals(new int[]{0x2A,8,0x52,0x30},words(root));
        var selected=List.of(PLAYER_BLOCKS_B,PLAYER_BLOCKS_A,PLAYER_BLOCKS_D,PLAYER_BLOCKS_C);
        for(int i=0;i<4;i++)assertEquals(a.range(selected.get(i)).nativeAddress(),
                a.range(PLAYER_BLOCKS).nativeAddress()+(short)root.unsignedWord(i*2));
        assertEquals(0x206858,a.range(PLAYER_BLOCKS).nativeAddress());
    }

    @Test void everyCountedListRetainsAllSourceIdsIncludingInactiveZeros() throws Exception {
        var a=TestCdR31aAssets.actual().assets();
        var ids=List.of(PLAYER_BLOCKS_A,PLAYER_BLOCKS_B,PLAYER_BLOCKS_C,PLAYER_BLOCKS_D);
        int[][] source={
                {15,0x13C,0x146,0x19B,0x1AE,0x83,0x84,0x89,0x8A,0x77,0x76,0x80,0x7F,0x7E,0x7D,0x7C,0x82},
                {1,0x145,0x146},
                {15,0x13C,0x146,0,0,0x83,0x84,0x89,0x8A,0x77,0x76,0x80,0x7F,0x7E,0x7D,0x7C,0x82},
                {15,0x13C,0x146,0x165,0x166,0x83,0x84,0x89,0x8A,0x77,0x76,0x80,0x7F,0x7E,0x7D,0x7C,0x82}};
        for(int i=0;i<ids.size();i++){
            var raw=a.view(ids.get(i));assertArrayEquals(source[i],words(raw));
            assertEquals(2+2*(raw.unsignedWord(0)+1),raw.length());
        }
        assertNotEquals(a.view(PLAYER_BLOCKS_A),a.view(PLAYER_BLOCKS_C));
    }

    @Test void allSixteenSelectorWordsRetainNativeAliasesAndActualTargets() throws Exception {
        var a=TestCdR31aAssets.actual().assets();var root=a.view(PLAYER_BLOCK_ROUTINES);
        int[] relative={0x20,0x20,0xD0,0xD0,0x266,0x122,0x134,0x134,0x242,0x19A,0x19A,0x19A,0x17E,0x122,0x122,0x146};
        int[] targets={0x206942,0x206942,0x2069F2,0x2069F2,0x206B88,0x206A44,0x206A56,0x206A56,
                0x206B64,0x206ABC,0x206ABC,0x206ABC,0x206AA0,0x206A44,0x206A44,0x206A68};
        assertEquals(32,root.length());assertArrayEquals(relative,words(root));
        assertEquals(0x206922,a.range(PLAYER_BLOCK_ROUTINES).nativeAddress());
        for(int i=0;i<16;i++)assertEquals(targets[i],a.range(PLAYER_BLOCK_ROUTINES).nativeAddress()+(short)root.unsignedWord(i*2));
        // Every distinct directory endpoint has its source-declared instruction structure.
        int[] starts={0x6942,0x69F2,0x6B88,0x6A44,0x6A56,0x6B64,0x6ABC,0x6AA0,0x6A68};
        String[] heads={
                "0242fff04a016604064200100243fff00804000b6604064300103203",
                "0242fff050420243fff050433203926800089468000c4eb900200a1a4eb900200782c3fcf900e081e28131410010",
                "32030241000f0c010008640c0804000b6600feaa6000feca0804000b6600fec26000fe9a",
                "303c07004a6800126b0244403140001260d6",
                "303c07004a6800106b0244403140001060c4",
                "32030241000f0c010008640c0804000b6600fee06000ff420804000b6600fec26000ff36",
                "044100126b2c303b106a43fb00660242000f0243000f",
                "32030241000f0c01000864080804000b66a460900804000b668a609a",
                "0242fff050420243fff050433203926800089468000c4eb900200a1a4eb900200782c3fcf900e08131410010"};
        for(int i=0;i<starts.length;i++)assertEquals(heads[i],hex(a,starts[i],heads[i].length()/2));
        // Source Routine4 alone has two ASR.L #8/#1 pairs; Routine1E has one ASR.L #8 per product.
        assertEquals("e081e28131410010",hex(a,0x6A18,8));
        assertEquals("e08131410010",hex(a,0x6A8E,6));
    }

    @Test void allBumperPointerAndProfileBytesAreExactSixteenColumnData() throws Exception {
        var a=TestCdR31aAssets.actual().assets();var root=a.view(PLAYER_BUMPER_COLLIDE);
        assertArrayEquals(new int[]{6,22,38},words(root));assertEquals(6,root.length());
        var ids=List.of(PLAYER_BUMPER_COLLIDE_1,PLAYER_BUMPER_COLLIDE_2,PLAYER_BUMPER_COLLIDE_3);
        String[] source={"01010102020203030304040405050506","06060707070808080909090a0a0a0b0b","0b0c0c0c0d0d0d0e0e0e0f0f0f101010"};
        for(int i=0;i<3;i++){
            assertEquals(16,a.view(ids.get(i)).length());assertArrayEquals(HexFormat.of().parseHex(source[i]),a.view(ids.get(i)).copy());
            assertEquals(a.range(ids.get(i)).nativeAddress(),a.range(PLAYER_BUMPER_COLLIDE).nativeAddress()+(short)root.unsignedWord(i*2));
        }
    }

    @Test void actualPcIndexedConsumerPairsSelectAllThreeExposedRoots() throws Exception {
        var a=TestCdR31aAssets.actual().assets();
        int[] at={0x683A,0x683E,0x68E8,0x68EC,0x6AC2,0x6AC6};
        String[] nativeBytes={"323b101c","43fb1018","303b1038","4ebb0034","303b106a","43fb0066"};
        var roots=List.of(PLAYER_BLOCKS,PLAYER_BLOCKS,PLAYER_BLOCK_ROUTINES,PLAYER_BLOCK_ROUTINES,PLAYER_BUMPER_COLLIDE,PLAYER_BUMPER_COLLIDE);
        for(int i=0;i<at.length;i++){
            assertEquals(nativeBytes[i],hex(a,at[i],4));
            int displacement=(byte)a.originalImage().readFile(at[i]+3,1)[0];
            assertEquals(a.range(roots.get(i)).nativeAddress(),0x200000+at[i]+2+displacement);
        }
        assertEquals("d241323b101c43fb10187c003c197200b0711000677e544151cefff6",hex(a,0x6838,28));
        assertEquals("044100126b2c303b106a43fb00660242000f0243000f",hex(a,0x6ABC,22));
    }

    @Test void equalTerrainHeightBytesDoNotReplaceTheReachedBumperSource() throws Exception {
        var a=TestCdR31aAssets.actual().assets();
        var ids=List.of(PLAYER_BUMPER_COLLIDE_1,PLAYER_BUMPER_COLLIDE_2,PLAYER_BUMPER_COLLIDE_3);
        for(int i=0;i<3;i++){
            assertArrayEquals(a.originalImage().readFile(0x1DF90+16*i,16),a.view(ids.get(i)).copy());
            assertEquals(0x206B34+16*i,a.range(ids.get(i)).nativeAddress());
            assertNotEquals(0x21DF90+16*i,a.range(ids.get(i)).nativeAddress());
        }
        // Finite data extension only: keep previous enum ordinals and forbid a forged fixed range.
        assertEquals(52,TITLE_MAPPING_READ_VIEW.ordinal());assertEquals(53,PLAYER_BLOCKS.ordinal());
        assertEquals(62,PLAYER_BUMPER_COLLIDE_3.ordinal());
        var r=a.range(PLAYER_BLOCKS);
        assertThrows(IllegalArgumentException.class,()->new CdR31aAssets.Range(PLAYER_BLOCKS,0x6860,r.length(),r.sha256()));
        assertThrows(IllegalArgumentException.class,()->new CdR31aAssets.Range(PLAYER_BUMPER_COLLIDE_1,0x1DF90,16,a.range(PLAYER_BUMPER_COLLIDE_1).sha256()));
    }

    private static int[] words(CdAssetBytes raw){
        assertEquals(0,raw.length()&1);int[] out=new int[raw.length()/2];
        for(int i=0;i<out.length;i++)out[i]=raw.unsignedWord(i*2);return out;
    }
    private static String hex(CdR31aAssets a,int offset,int length)throws Exception{
        return HexFormat.of().formatHex(a.originalImage().readFile(offset,length));
    }
}
