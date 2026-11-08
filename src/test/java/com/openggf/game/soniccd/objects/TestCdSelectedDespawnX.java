package com.openggf.game.soniccd.objects;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
class TestCdSelectedDespawnX {
    @Test void anchorIsAnExplicitOperandAndDoesNotManufactureCurrentPosition() {
        var pool=new CdObjectPool();var s=pool.state(32).moveWord(X,0x4000);var owner=new CdSpinTunnelTag(new CdSpinTunnelTag.Assets(0x212300));
        var g=new CdSpinTunnelTag.DespawnGlobals(1,0,0);
        assertTrue(owner.despawn(s,g).deleteObject());assertFalse(owner.despawnAtX(s,0x100,g).deleteObject());assertEquals(0x4000,s.centreX());
    }
    @Test void oldEntryDelegatesForAllWordEdgesAndWarpPeriodControls() {
        var pool=new CdObjectPool();var owner=new CdSpinTunnelTag(new CdSpinTunnelTag.Assets(0x212300));
        for(int x:new int[]{0,0x7f,0x80,0x200,0x280,0x300,0x7fff,0x8000,0xff80,0xffff})
            for(int period:new int[]{0,1,2,0x80,0x81,0x82})for(int dir:new int[]{0,1,255}){
                var s=pool.state(32).moveWord(X,x).moveByte(CdObjectState.ByteField.SAVED_FLAGS_ID,5);var g=new CdSpinTunnelTag.DespawnGlobals(period,dir,0);
                assertEquals(owner.despawn(s,g),owner.despawnAtX(s,x,g));
            }
    }
    @Test void nativeUnsignedEqualityWrapAndWarpLoadedClearHaveLiteralOutcomes() {
        var pool=new CdObjectPool();var owner=new CdSpinTunnelTag(new CdSpinTunnelTag.Assets(0x212300));
        var s=pool.state(32).moveWord(X,0x4000).moveByte(CdObjectState.ByteField.SAVED_FLAGS_ID,5);
        var ordinary=new CdSpinTunnelTag.DespawnGlobals(1,0,0);
        assertFalse(owner.despawnAtX(s,0x200,ordinary).deleteObject()); // distance$280, BLS equality.
        assertTrue(owner.despawnAtX(s,0x280,ordinary).deleteObject());
        assertFalse(owner.despawnAtX(s,0xff80,ordinary).deleteObject()); // WORD wrap distance0.
        var warp=owner.despawnAtX(s,0xff80,new CdSpinTunnelTag.DespawnGlobals(0x81,1,0));
        assertTrue(warp.deleteObject());assertEquals(0,warp.loadedClear().orElseThrow().period());assertEquals(17,warp.loadedClear().orElseThrow().index());
        var reverse=owner.despawnAtX(s,0xff80,new CdSpinTunnelTag.DespawnGlobals(0x81,255,0));
        assertEquals(2,reverse.loadedClear().orElseThrow().period());assertEquals(19,reverse.loadedClear().orElseThrow().index());
    }

}
