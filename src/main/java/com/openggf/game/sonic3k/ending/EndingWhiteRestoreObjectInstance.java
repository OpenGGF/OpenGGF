package com.openggf.game.sonic3k.ending;

import com.openggf.game.PlayableEntity;
import com.openggf.game.palette.PaletteWriteSupport;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.util.List;

/** loc_85EE6/85EF8: eight normal-to-target steps every four object passes. */
public final class EndingWhiteRestoreObjectInstance extends AbstractObjectInstance implements SpawnRewindRecreatable, NativeSstStateSource {
    private int wait=3;
    private int steps=7;
    public EndingWhiteRestoreObjectInstance(){this(new ObjectSpawn(0,0,0,0,0,false,0));}
    public EndingWhiteRestoreObjectInstance(ObjectSpawn spawn){super(spawn,"EndingWhiteRestore");}
    // loc_85EE6/85EF8 writes only palette pointers, the2E timer and39 step byte.
    @Override public NativeSstState nativeSstState() { return NativeSstState.CLEARED; }
    @Override public void update(int vIntRunCount,PlayableEntity player){
        if((wait=(short)(wait-1))>=0)return;
        wait=3;var registry=services().paletteOwnershipRegistryOrNull();
        for(int line=0;line<4;line++){
            byte[] bytes=new byte[32];byte[] targets=registry==null?new byte[32]:registry.targetSegaData(line,0,16);
            for(int color=0;color<16;color++){
                int word=PaletteWriteSupport.segaWordFromColor(services().currentLevel().getPalette(line).getColor(color));
                int target=((targets[color*2]&255)<<8)|(targets[color*2+1]&255);
                for(int shift=0;shift<=8;shift+=4)
                    if(((word>>>shift)&14)>((target>>>shift)&14))word-=2<<shift;
                bytes[color*2]=(byte)(word>>>8);bytes[color*2+1]=(byte)word;
            }
            S3kPaletteWriteSupport.applyLine(registry,services().currentLevel(),services().graphicsManager(),
                    "s3k-ending-white-restore",S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE,line,bytes,true);
        }
        if(--steps<0){if(registry!=null)registry.setPaletteRotationDisabled(false);ObjectLifetimeOps.deleteNoRespawn(this);}
    }
    @Override public boolean isPersistent(){return true;}
    @Override public void appendRenderCommands(List<GLCommand> commands){}
}
