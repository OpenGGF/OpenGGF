package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayerCharacter;
import com.openggf.level.objects.*;
import java.util.ArrayList;
import java.util.List;

/** Stable-role graph relinker; missing native allocation prefixes remain missing. */
final class Fbz2SubbossRewindLinks {
    private Fbz2SubbossRewindLinks(){}
    static Fbz2SubbossInstance root(ObjectManager manager,int familySlot){if(manager==null)return null;for(ObjectInstance o:manager.getActiveObjects())if(o instanceof Fbz2SubbossInstance r&&(familySlot<0||r.getSlotIndex()==familySlot))return r;return null;}
    static void settle(ObjectManager manager,int familySlot){Fbz2SubbossInstance root=root(manager,familySlot);if(root==null)return;List<Object> family=new ArrayList<>();for(ObjectInstance o:manager.getActiveObjects())if(o instanceof AbstractFbz2SubbossChild c&&c.familySlot()==familySlot)family.add(c);settleForTest(root,family.toArray());}
    static void settleForTest(Fbz2SubbossInstance root,Object[] objects){Fbz2SubbossCornerChild left=null,right=null;for(Object o:objects)if(o instanceof Fbz2SubbossCornerChild c){c.attach(root);if(c.nativeSubtype()==0)left=c;else if(c.nativeSubtype()==2)right=c;}root.setUpperLeft(left);root.setUpperRight(right);for(Object o:objects){if(o instanceof AbstractFbz2SubbossChild c)c.attach(root);if(o instanceof Fbz2SubbossSolidSideChild s)s.setCorner(s.nativeSubtype()==0?left:right);}}
    static List<AbstractObjectInstance> childrenForTest(Fbz2SubbossInstance root){return List.of(new Fbz2SubbossCornerChild(root,0),new Fbz2SubbossSolidSideChild(root,0),new Fbz2SubbossMachineChild(root),new Fbz2SubbossCharacterChild(root,PlayerCharacter.SONIC_ALONE),new Fbz2SubbossSpriteMaskChild(root),new Fbz2SubbossLaserChild(root),new Fbz2SubbossExplosionController(root.getX(),root.getY(),root.getSlotIndex()),new Fbz2SubbossRumbleController());}
}
