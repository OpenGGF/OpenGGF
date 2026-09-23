package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.*;

/** loc_7EAB6 / 7EACC / 7EB0E: spark, delayed cover and final body pieces. */
final class DezMinibossDebris extends DezMinibossSprite implements RewindRecreatable {
    static final int SPARK=0, COVER=1, BODY=2;
    private int role;
    private boolean initialized;
    private int coverTimer;
    private boolean waiting;
    private DezMinibossDebris(ObjectSpawn spawn) { super(spawn,"DEZMinibossDebris"); }
    DezMinibossDebris(DezMinibossSprite source,int role,int subtype,int dx,int dy) {
        this(new ObjectSpawn((source.getX()+dx)&0xFFFF,(source.getY()+dy)&0xFFFF,0,subtype,0,false,0));
        this.role=role;
    }
    @Override public DezMinibossDebris recreateForRewind(RewindRecreateContext context) {
        return new DezMinibossDebris(context.spawn());
    }
    @Override public void update(int clock,PlayableEntity player) {
        visible=false;
        if(pendingDelete) { ObjectLifetimeOps.deleteNoRespawn(this); return; }
        if(!initialized) {
            initialized=true;
            switch(role) {
                case SPARK -> {
                    priority=1; halfWidth=halfHeight=8; frame=0x12;
                    int random=services().rng().nextRaw();
                    int index=random&0xC;
                    xVelocity=(short)romWord(0x7ED40+index); yVelocity=(short)romWord(0x7ED42+index);
                    int dx=random&0x1F;
                    if((dx&1)!=0) { dx=-dx; xVelocity=-xVelocity; flipX=true; }
                    writeX(getX()+dx);
                    int high=random>>>16; frame=romByte(0x7ED50+(high&3));
                    int dy=high&0x1F; if((dy&1)!=0) dy=-dy; writeY(getY()+dy);
                }
                case COVER -> {
                    priority=3; halfWidth=0x10; halfHeight=0x18; frame=0x11;
                    flipX=spawn.subtype()!=0; xVelocity=flipX?0x200:-0x200; yVelocity=-0x200;
                    coverTimer=0x3F; waiting=true;
                }
                case BODY -> {
                    priority=2; halfWidth=0x10; halfHeight=0x14; frame=0x23+(spawn.subtype()>>>1);
                    xVelocity=(short)romWord(0x852FC+spawn.subtype()*2);
                    yVelocity=(short)romWord(0x852FE+spawn.subtype()*2);
                    return; // init tail only Set_IndexedVelocity, not Draw_Sprite
                }
                default -> throw new IllegalStateException("Unknown DEZ debris role "+role);
            }
        }
        if(waiting) {
            if(--coverTimer<0) waiting=false;
            visible=true; return; // expiry also draws stationary; first move is next entry
        }
        move(0x38);
        if(isCoarseXOutOfRange(getX(),cameraLeft(),coarseXCullRange())
                || ((getY()-cameraTop()+0x80)&0xFFFF)>0x200) {
            status|=0x80; control|=0x10; pendingDelete=true; return;
        }
        visible=(control&0x40)!=0; control^=0x40; updateDynamicSpawn(getX(),getY());
    }
}
