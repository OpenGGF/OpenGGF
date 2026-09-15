package com.openggf.game.sonic3k.objects;
import com.openggf.level.objects.*;
/** Native Child6_CreateBossExplosion retained in an independent SST. */
public final class SozEndBossExplosion extends AbstractS3kBossExplosionObjectInstance implements SpawnRewindRecreatable {
    SozEndBossExplosion(int x,int y,int familySlot){this(new ObjectSpawn(x,y,0x98,0,0,false,0));this.familySlot=familySlot;}
    public SozEndBossExplosion(ObjectSpawn spawn){super(spawn,"SOZEndBossExplosion");}
}
