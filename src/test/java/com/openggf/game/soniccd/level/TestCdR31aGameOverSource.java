package com.openggf.game.soniccd.level;

import org.junit.jupiter.api.Test;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual authenticated original programme fixture; no new source accessor or decoder. */
class TestCdR31aGameOverSource {
    @Test void selectedOriginalCodeCommandsAndDistinctMappingRootsMatchNativeOperands()throws Exception{
        var f=TestCdR31aAssets.actual();var a=f.assets();var data=CdR31aGameOverSource.fromAssets(a).data();var code=CdAssetBytes.copyOf(f.image().readFile(0xF17E,0xE4));
        assertEquals(0x20F17E,a.view(CdR31aAssets.Asset.OBJECT_INDEX).unsignedLong((0x3B-1)*4));
        assertEquals(4,code.unsignedWord(14));assertEquals(0xBC,code.unsignedWord(16));
        assertEquals("303c0082",HexFormat.of().formatHex(code.slice(0x12,4).copy()));
        assertEquals("217c0020f2620004",HexFormat.of().formatHex(code.slice(0x38,8).copy()));
        assertEquals("217c0020f2660004",HexFormat.of().formatHex(code.slice(0x5A,8).copy()));
        assertEquals("303c006e4ef9",HexFormat.of().formatHex(code.slice(0xC0,6).copy()));assertEquals(0x7008,code.unsignedWord(0xCA));
        assertArrayEquals(f.image().readFile(0xF262,56),data.mappings().copy());assertEquals("00080014001c0028",HexFormat.of().formatHex(data.mappings().slice(0,8).copy()));
        for(int n:new int[]{19,31,43,55})assertEquals(0,data.mappings().unsignedByte(n));
        System.out.println("R31_GAME_OVER_SOURCE code="+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(code.copy()))+" mappings="+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data.mappings().copy())));
    }
    @Test void contradictoryPublicDispatchProjectionRejectsBeforeItCanSupplyGameOverData()throws Exception{
        var a=TestCdR31aAssets.actual().assets();var wrong=spy(a);byte[] index=a.view(CdR31aAssets.Asset.OBJECT_INDEX).copy();index[(0x3B-1)*4+3]^=2;doReturn(CdAssetBytes.copyOf(index)).when(wrong).view(CdR31aAssets.Asset.OBJECT_INDEX);assertThrows(IllegalArgumentException.class,()->CdR31aGameOverSource.fromAssets(wrong));
        var first=CdR31aGameOverSource.fromAssets(a);var copy=first.data().mappings().copy();Arrays.fill(copy,(byte)0xA5);assertArrayEquals(TestCdR31aAssets.actual().image().readFile(0xF262,56),first.data().mappings().copy());assertThrows(java.nio.ReadOnlyBufferException.class,()->first.data().mappings().buffer().put(0,(byte)1));
    }
}
