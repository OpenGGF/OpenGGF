package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.S3kSpriteMaskSupport;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.util.List;

/** Generic Obj_SpriteMask subtype $49 from ChildObjDat_703DE. */
final class Fbz2SubbossSpriteMaskChild extends AbstractFbz2SubbossChild implements RewindRecreatable, RomWorldPositionedObject {
    Fbz2SubbossSpriteMaskChild(Fbz2SubbossInstance r){this(new ObjectSpawn(r.getX(),r.getY(),0xAB,0x49,0,false,0));root=r;familySlot=r.getSlotIndex();}
    private Fbz2SubbossSpriteMaskChild(ObjectSpawn s){super(s,"FBZ2SubbossSpriteMask");}
    static int nativeSubtype(){return 0x49;} static int mappingFrame(){return 4;} static int nativePriority(){return 0x80;}
    @Override public void update(int vIntRunCount,PlayableEntity p){if(root!=null&&root.controlBit(Fbz2SubbossInstance.CONTROL_DELETE_MASK))ObjectLifetimeOps.deleteNoRespawn(this);}
    @Override public Fbz2SubbossSpriteMaskChild recreateForRewind(RewindRecreateContext c){return new Fbz2SubbossSpriteMaskChild(c.spawn());}
    @Override public int getPriorityBucket(){return 1;}
    @Override public void appendRenderCommands(List<GLCommand> c){if(tryServices()!=null)S3kSpriteMaskSupport.submitFrame4(services().graphicsManager(),x,y);}
}
