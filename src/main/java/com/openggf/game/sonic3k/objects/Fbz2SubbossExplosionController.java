package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.*;

/** Rewind-safe SST implementation of subtype-zero Obj_CreateBossExplosion. */
final class Fbz2SubbossExplosionController extends AbstractS3kBossExplosionObjectInstance implements RewindRecreatable, RomWorldPositionedObject {
    Fbz2SubbossExplosionController(int x,int y,int familySlot){this(new ObjectSpawn(x,y,0xAB,0,0,false,0));this.familySlot=familySlot;}
    private Fbz2SubbossExplosionController(ObjectSpawn s){super(s,"FBZ2SubbossExplosionControl");}
    @Override public Fbz2SubbossExplosionController recreateForRewind(RewindRecreateContext c){return new Fbz2SubbossExplosionController(c.spawn());}
}
