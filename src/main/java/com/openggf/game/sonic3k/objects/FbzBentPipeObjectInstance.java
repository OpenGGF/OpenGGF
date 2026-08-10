package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/** Locked-on {@code Obj_FBZBentPipe} ($76), a static full-solid. */
public final class FbzBentPipeObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SpawnRewindRecreatable {
    private static final int[][] SIZE={{0x18,0x10},{0x10,0x08},{0x18,0x10}};
    private final int frame;
    private final int sizeIndex;
    public FbzBentPipeObjectInstance(ObjectSpawn spawn){super(spawn,"FBZBentPipe");frame=spawn.subtype()&0xFF;sizeIndex=spawn.subtype()&3;if(sizeIndex==3)throw new IllegalArgumentException("FBZ bent-pipe subtype has undefined ROM size-table index 3: $"+Integer.toHexString(frame));}
    int mappingFrame(){return frame;}
    @Override public void update(int vIntRunCount,PlayableEntity player){if(!isOnScreen(0x180))setDestroyedByOffscreen();}
    @Override public int getPriorityBucket(){return 4;}@Override public SolidObjectParams getSolidParams(){return new SolidObjectParams(SIZE[sizeIndex][0]+0x0B,SIZE[sizeIndex][1],SIZE[sizeIndex][1]+1);}
    @Override public int getBalanceWidthPixels(){
        // Sonic_Balance reads width_pixels(a1) from byte_3B6D8. The +$B
        // extension belongs only to loc_3B718's SolidObjectFull d1.
        return SIZE[sizeIndex][0];
    }
    @Override public boolean usesCustomOutOfRangeCheck(){return true;}@Override public boolean isCustomOutOfRange(int cameraX){int object=spawn.x()&0xFF80;int back=(cameraX-0x80)&0xFF80;return ((object-back)&0xFFFF)>0x280;}
    @Override public SolidRoutineProfile getSolidRoutineProfile(){
        // loc_3B718 reaches SolidObject_cont, whose unsigned BHI comparison
        // retains the exact right boundary of the expanded d1 span.
        return SolidRoutineProfile.fullSolid(false,true,false);
    }
    @Override public void appendRenderCommands(List<GLCommand> commands){PatternSpriteRenderer r=getRenderer(Sonic3kObjectArtKeys.FBZ_BENT_PIPE);if(r!=null&&r.isReady())r.drawFrameIndex(frame,spawn.x(),spawn.y(),false,false);}
}
