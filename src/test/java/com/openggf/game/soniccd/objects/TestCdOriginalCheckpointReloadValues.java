package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import org.junit.jupiter.api.Test;
import java.util.*;
import static com.openggf.game.soniccd.objects.CdCheckpointLoadData.*;
import static org.junit.jupiter.api.Assertions.*;

/** Constructed DATA controls only, never an original invocation/World or route witness.
 * No known-zero bank is provided to runtime. ALL Java UNRUN. */
class TestCdOriginalCheckpointReloadValues {
    private static CdCheckpointSaveData.State saved(int spawn,boolean mini,boolean water){
        byte[] bytes=new byte[31],mask=new byte[31];Arrays.fill(mask,(byte)1);
        for(int i=0;i<bytes.length;i++)bytes[i]=(byte)(i*7+3);
        bytes[0]=(byte)spawn;bytes[1]=0x12;bytes[2]=0x34;
        if(!mini)mask[30]=0;
        // savedWaterRoutine aliases the LOW water-height byte: leave it known.
        if(!water){mask[27]=0;mask[29]=0;}
        return CdCheckpointSaveData.State.supplied(CdAssetBytes.copyOf(bytes),CdAssetBytes.copyOf(mask));
    }
    private static final class Values implements Port {
        final CdCheckpointSaveData.State saved;
        final EnumMap<StoreTarget,Integer> live=new EnumMap<>(StoreTarget.class);
        final List<StoreTarget> stores=new ArrayList<>();
        StoreTarget fail;RuntimeException fault;
        Values(CdCheckpointSaveData.State s){saved=s;for(var t:StoreTarget.values())live.put(t,t.width()==1?0xA5:t.width()==2?0xBEEF:0x89ABCDEF);}
        public CdCheckpointSaveData.State currentSaved(){return saved;}
        public OptionalInt read(StoreTarget t){return OptionalInt.of(live.get(t));}
        public void commit(StoreCall c){
            assertEquals(OptionalInt.of(live.get(c.target())),c.expectedValue());
            if(c.target()==fail)throw fault;
            live.put(c.target(),c.value());stores.add(c.target());
        }
    }
    @Test void actualSavedSignSelectsLeftBoundAndUnknownSuppressedMiniWaterAreUnread(){
        var negative=new Values(saved(0x81,false,false));load(1,0,negative.saved,negative);
        assertEquals(20,negative.stores.size());assertEquals(StoreTarget.SPAWN_MODE,negative.stores.getFirst());
        assertEquals(StoreTarget.LEFT_BOUND,negative.stores.getLast());assertEquals(0x1194,negative.live.get(StoreTarget.LEFT_BOUND).intValue());
        assertEquals(0xBEEF,negative.live.get(StoreTarget.WATER_HEIGHT2).intValue());assertEquals(0xA5,negative.live.get(StoreTarget.MINI_SONIC).intValue());
        var positive=new Values(saved(1,false,false));load(0x81,0,positive.saved,positive);
        assertEquals(19,positive.stores.size());assertFalse(positive.stores.contains(StoreTarget.LEFT_BOUND));
    }
    @Test void zoneGatesReachUnknownFieldsOnlyAfterNineteenCompletedStores(){
        for(int zone:new int[]{2,6}){
            var v=new Values(saved(1,false,false));
            assertThrows(CdCheckpointSaveData.UnknownSavedByte.class,()->load(1,zone,v.saved,v));
            assertEquals(19,v.stores.size());assertEquals(StoreTarget.BG3_Y,v.stores.getLast());
        }
        var warp=new Values(CdCheckpointSaveData.State.unknown());
        assertThrows(WarpBoundary.class,()->load(2,0,warp.saved,warp));assertTrue(warp.stores.isEmpty());
    }
    @Test void unknownSpawnFaultsBeforeFirstStoreAndDestinationFaultRetainsCurrentBottom(){
        var unknown=new Values(CdCheckpointSaveData.State.unknown());
        assertThrows(CdCheckpointSaveData.UnknownSavedByte.class,()->load(1,0,unknown.saved,unknown));assertTrue(unknown.stores.isEmpty());
        var v=new Values(saved(1,false,false));v.fail=StoreTarget.DEST_BOTTOM_BOUND;v.fault=new IllegalStateException("native59");
        assertSame(v.fault,assertThrows(IllegalStateException.class,()->load(1,0,v.saved,v)));
        assertEquals(10,v.stores.size());assertEquals(v.saved.read(CdCheckpointSaveData.SavedField.BOTTOM_BOUND),v.live.get(StoreTarget.BOTTOM_BOUND).intValue());
        assertEquals(0xBEEF,v.live.get(StoreTarget.DEST_BOTTOM_BOUND).intValue());assertEquals(0,v.fault.getSuppressed().length);
    }
    @Test void firstAndLaterFaultIdentityRetainOnlyTheirCompletedNativePrefixes(){
        for(var target:List.of(StoreTarget.SPAWN_MODE,StoreTarget.PRIMARY_Y,StoreTarget.BG2_Y)){
            var v=new Values(saved(1,false,false));v.fail=target;v.fault=new IllegalStateException("fault "+target);
            assertSame(v.fault,assertThrows(IllegalStateException.class,()->load(1,0,v.saved,v)));
            assertFalse(v.stores.contains(target));assertEquals(0,v.fault.getSuppressed().length);
            if(target==StoreTarget.SPAWN_MODE)assertTrue(v.stores.isEmpty());
            else assertEquals(1,v.live.get(StoreTarget.SPAWN_MODE).intValue());
        }
    }
}
