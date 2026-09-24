package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.S3kSpriteMaskSupport;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.util.List;

/** SOZ2's placed Obj_SpriteMask $8B/$40: priority-zero mapping frame four SAT markers. */
public final class SozSpriteMaskObjectInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable, RomWorldPositionedObject {
    public SozSpriteMaskObjectInstance(ObjectSpawn spawn){super(spawn,"SOZSpriteMask");}
    @Override public void update(int vIntRunCount,PlayableEntity player){}
    @Override public int getPriorityBucket(){return spawn.subtype()&7;}
    @Override public int getOnScreenHalfWidth(){return 0x20;}
    @Override public int getOnScreenHalfHeight(){return (spawn.subtype()&0xF0)>>2;}
    /**
     * {@code Offset_ObjectsDuringTransition} (sonic3k.asm:104166-104181) subtracts {@code d0}/
     * {@code d1} from the {@code x_pos}/{@code y_pos} of every SST slot whose
     * {@code render_flags} bit 2 is set, and a placed {@code Obj_SpriteMask} holds such a slot.
     */
    @Override public void offsetNativePositionWordsPreserveSubpixel(int offsetX,int offsetY){
        updateDynamicSpawn((getCollisionX()+offsetX)&0xFFFF,(getCollisionY()+offsetY)&0xFFFF);
    }
    @Override public void appendRenderCommands(List<GLCommand> commands){S3kSpriteMaskSupport.submitFrame4(services().graphicsManager(),getX(),getY());}
}
