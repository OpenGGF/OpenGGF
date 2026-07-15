package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;
import java.util.List;

/** First ChildObjDat_703D0 member, loc_6FFDC. */
final class Fbz2SubbossMachineChild extends AbstractFbz2SubbossChild implements RewindRecreatable, RomWorldPositionedObject {
    private boolean detached;
    private int frame=2;
    Fbz2SubbossMachineChild(Fbz2SubbossInstance r){this(new ObjectSpawn(r.getX()+0xCC,r.getY()+0x7C,0xAB,0,0,false,0));root=r;familySlot=r.getSlotIndex();}
    private Fbz2SubbossMachineChild(ObjectSpawn s){super(s,"FBZ2SubbossMachine");}
    @Override public void update(int f,PlayableEntity p){if(!detached&&root!=null&&root.statusBit(Fbz2SubbossInstance.STATUS_CHARACTER_DEFEAT)){detached=true;frame=3;if(tryServices()!=null&&services().objectManager()!=null)spawnChild(()->new Fbz2SubbossExplosionController(x,y,familySlot));}if(detached&&tryServices()!=null&&!isOnScreen())ObjectLifetimeOps.expireDynamic(this);}
    @Override public Fbz2SubbossMachineChild recreateForRewind(RewindRecreateContext c){return new Fbz2SubbossMachineChild(c.spawn());}
    @Override public int getPriorityBucket(){return 5;}
    @Override public void appendRenderCommands(List<GLCommand> c){PatternSpriteRenderer r=getRenderer(Sonic3kObjectArtKeys.FBZ2_SUBBOSS);if(r!=null&&r.isReady())r.drawFrameIndex(frame,x,y,false,false);}
}
