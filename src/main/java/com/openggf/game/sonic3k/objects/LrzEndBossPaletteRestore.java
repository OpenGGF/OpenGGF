package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/** loc_7A100/7A12A: word_78EAA rotation, started by the capsule's FACD publication. */
public final class LrzEndBossPaletteRestore extends AbstractObjectInstance implements ZeroArgRewindRecreatable {
    private boolean initialized,finished;
    private int cursor,header,delay,iteration;
    public LrzEndBossPaletteRestore() { super(new ObjectSpawn(0,0,0,0,0,false,0),"LRZEndBossPaletteRestore"); }
    @Override public void update(int vIntRunCount,PlayableEntity player) {
        var state=S3kRuntimeStates.currentLrz(services().zoneRuntimeRegistry()).orElseThrow().bossAct();
        if(!initialized && !state.capsuleOpened()) return;
        try {
            var rom=services().rom();
            if(!initialized) {
                initialized=true; header=rom.read32BitAddr(0x78EAE);
                cursor=header+rom.read16BitAddr(0x78EAA);
                delay=rom.readBytes(0x78EAC,1)[0]&255;
            }
            // Run_PalRotationScript returns before decrementing the script delay while
            // another fade owns Palette_rotation_disable. The caller still writes $7FFF.
            var registry=services().paletteOwnershipRegistryOrNull();
            if(!finished && (registry==null || !registry.isPaletteRotationDisabled())) {
                delay=(byte)(delay-1);
                if(delay<0) {
                    if((short)rom.read16BitAddr(cursor)<0) {
                        iteration=(iteration+1)&255;
                        int repeats=rom.readBytes(header+3,1)[0]&255;
                        if(iteration>=repeats) finished=true;
                        else cursor=header+4;
                    }
                    if(!finished) {
                        int count=(rom.readBytes(header+2,1)[0]&255)+1;
                        int destination=rom.read16BitAddr(header);
                        // This native script targets Normal_palette_line_3+$02.
                        int first=(destination-0xFC00)/2;
                        byte[] colors=rom.readBytes(cursor,count*2);
                        S3kPaletteWriteSupport.applyContiguousPatch(services().paletteOwnershipRegistryOrNull(),
                                services().currentLevel(),services().graphicsManager(),"lrz.endboss.restore",190,
                                first/16,first%16,colors);
                        cursor+=count*2; delay=rom.read16BitAddr(cursor)&255; cursor+=2;
                    }
                }
            }
            // loc_7A130 keeps this word at $7FFF even after the custom callback.
            state.writePrimaryPaletteTimer(0x7FFF);
        } catch(IOException failure) { throw new UncheckedIOException(failure); }
    }
    // These ROM routines own their deletion; none calls placement-range unloading.
    @Override public boolean isPersistent() { return true; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}
