package com.openggf.game.sonic3k.ending;

import com.openggf.game.PlayableEntity;
import com.openggf.game.palette.PaletteWriteSupport;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.util.List;

/** loc_5FC1E: three white steps, a twenty-pass hold, then three target steps. */
public final class EndingKnucklesPaletteFlashObjectInstance extends AbstractObjectInstance implements SpawnRewindRecreatable, NativeSstStateSource {
    private int phase,timer,steps=2;
    private boolean deletePending;
    public EndingKnucklesPaletteFlashObjectInstance(){this(new ObjectSpawn(0,0,0,0,0,false,0));}
    public EndingKnucklesPaletteFlashObjectInstance(ObjectSpawn spawn){super(spawn,"KnucklesEndingPaletteFlash");}
    @Override public void update(int vIntRunCount,PlayableEntity player){
        if(deletePending){ObjectLifetimeOps.deleteNoRespawn(this);return;}
        if((timer=(short)(timer-1))>=0)return;
        timer=phase==0?5:3;
        var registry=services().paletteOwnershipRegistryOrNull();
        byte[] targets=registry.targetSegaData(2,1,15),bytes=new byte[30];
        for(int color=1;color<16;color++){
            int word=PaletteWriteSupport.segaWordFromColor(services().currentLevel().getPalette(2).getColor(color));
            int target=(targets[(color-1)*2]&255)<<8|(targets[(color-1)*2+1]&255);
            for(int shift=0;shift<=8;shift+=4){
                int component=(word>>>shift)&14;
                if(phase==0&&component<14)word+=2<<shift;
                else if(phase!=0&&component>((target>>>shift)&14))word-=2<<shift;
            }
            bytes[(color-1)*2]=(byte)(word>>>8);bytes[(color-1)*2+1]=(byte)word;
        }
        S3kPaletteWriteSupport.applyContiguousPatch(registry,services().currentLevel(),services().graphicsManager(),
                "s3k-ending-knuckles-flash",S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE,2,1,bytes);
        S3kPaletteWriteSupport.resolvePendingWritesNow(registry,services().currentLevel(),services().graphicsManager());
        if((steps=(byte)(steps-1))<0){
            if(phase==0){phase=1;steps=2;timer=0x13;}
            // loc_5FC78 jumps to Go_Delete_Sprite: code/status change now,
            // Delete_Current_Sprite clears and releases the SST on the next dispatch.
            else deletePending=true;
        }
    }
    // loc5FC1E..5FC78 writes timer2E/steps39; Go_Delete_Sprite then retains status7.
    @Override public NativeSstState nativeSstState(){
        return deletePending?new NativeSstState(0,0,0,0,0,0,0,0x80,0,0):NativeSstState.CLEARED;
    }
    @Override public boolean isPersistent(){return true;}
    @Override public void appendRenderCommands(List<GLCommand> commands){ }
}
