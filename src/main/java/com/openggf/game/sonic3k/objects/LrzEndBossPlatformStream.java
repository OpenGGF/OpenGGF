package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/** loc_79A54: first-free-slot stream, with native $44 slot-address reuse semantics. */
public final class LrzEndBossPlatformStream extends AbstractObjectInstance implements ZeroArgRewindRecreatable {
    private boolean initialized;
    private int x, lastSlot = -1;
    public LrzEndBossPlatformStream() { super(new ObjectSpawn(0,0,0,0,0,false,0),"LRZPlatformStream"); }
    @Override public void update(int vIntRunCount,PlayableEntity player) {
        int direction=S3kRuntimeStates.currentLrz(services().zoneRuntimeRegistry()).orElseThrow().bossAct().streamDirection();
        if(!initialized) {
            initialized=true; x=(byte)direction<0?0x9E0:0xB60;
            allocate(); // Native init falls through even if this allocation failed.
        }
        if((direction&255)==0) { ObjectLifetimeOps.expireDynamic(this); return; }
        int lastX=0;
        if(lastSlot<0) {
            // Failed first allocation leaves $44=0; x_pos(a1) reads the ROM reset-vector area.
            try { lastX=services().rom().read16BitAddr(0x10); }
            catch(IOException failure) { throw new UncheckedIOException(failure); }
        } else {
            for(var object:services().objectManager().getActiveObjects())
                if(object instanceof AbstractObjectInstance a && a.getSlotIndex()==lastSlot && !a.isDestroyed()) {
                    lastX=a.getX(); break;
                }
            // Delete_Current_Sprite clears the SST; a later occupant supplies its own X.
        }
        int distance=(short)(lastX-x);
        if(distance<0) distance=(short)-distance;
        if((distance&65535)>=0x80) allocate();
    }
    private void allocate() {
        var child=spawnFreeChild(()->LrzBossPlatformObjectInstance.floating(x));
        if(child!=null && child.getSlotIndex()>=0 && !child.isDestroyed()) lastSlot=child.getSlotIndex();
    }
    @Override public int getX() { return x; }
    // These ROM routines own their deletion; none calls placement-range unloading.
    @Override public boolean isPersistent() { return true; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}
