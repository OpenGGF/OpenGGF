package com.openggf.game.soniccd.objects;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
/** Pure native WORD arithmetic; no publication, source or World authority. */
class TestCdSharedCameraWindow {
 @Test void nativeUnsignedBoundaryKeepsEqualityAndDeletesTheNextChunk(){assertFalse(CdSpinTunnelTag.outsideCameraWindow(0x200,0));assertTrue(CdSpinTunnelTag.outsideCameraWindow(0x280,0));}
 @Test void nativeWrappedWordsAndLowSevenBitsRemainExact(){for(int camera:new int[]{0,1,0x7F,0x80,0xFF80,0xFFFF})for(int x:new int[]{0,0x7F,0x80,0x200,0x280,0xFF80,0xFFFF}){int d=((x&0xFF80)-(((camera-0x80)&65535)&0xFF80))&65535;assertEquals(d>0x280,CdSpinTunnelTag.outsideCameraWindow(x,camera));}}
 @Test void invalidWordsRejectInsteadOfMaskingMissingOwners(){assertThrows(IllegalArgumentException.class,()->CdSpinTunnelTag.outsideCameraWindow(-1,0));assertThrows(IllegalArgumentException.class,()->CdSpinTunnelTag.outsideCameraWindow(0,65536));}
}
