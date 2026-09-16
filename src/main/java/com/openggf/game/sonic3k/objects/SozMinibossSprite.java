package com.openggf.game.sonic3k.objects;

import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import java.io.IOException;
import java.util.List;

/** ROM-backed sprite/animation primitives local to the SOZ Egg Golem family. */
abstract class SozMinibossSprite extends AbstractObjectInstance {
    protected int x;
    protected int y;
    protected int xSub;
    protected int ySub;
    protected int xVel;
    protected int yVel;
    protected int frame;
    protected int script;
    protected int animCursor;
    protected int animTimer;
    protected boolean flip;
    protected boolean visible = true;
    protected int priority = 5;
    protected String artKey;

    SozMinibossSprite(ObjectSpawn spawn, String name, String artKey) {
        super(spawn, name); x = spawn.x(); y = spawn.y(); this.artKey = artKey;
    }
    protected final int romByte(int address) {
        try { return services().romReader().readU8(address); }
        catch (IOException failure) { throw new IllegalStateException("SOZ miniboss requires ROM data",failure); }
    }
    protected final void move(int gravity) {
        int xp=(x<<8)+xSub+xVel,yp=(y<<8)+ySub+yVel;
        x=(short)(xp>>8);y=(short)(yp>>8);xSub=xp&255;ySub=yp&255;
        yVel=(short)(yVel+gravity);
    }
    /** Animate_RawMultiDelay: the initial pair is skipped; $F4 calls the owner. */
    protected final int animateMulti(boolean flipX) {
        animTimer=(byte)(animTimer-1);if(animTimer>=0)return 0;
        animCursor=(animCursor+2)&255;int value=romByte(script+animCursor);
        if(value==0xF4){animCursor=0;return -1;}
        if(value==0xFC){animCursor=0;value=romByte(script);}
        frame=value;animTimer=romByte(script+animCursor+1);
        if(flipX && (value&0x40)!=0)flip=!flip;
        return 1;
    }
    protected final boolean animateRaw() {
        animTimer=(byte)(animTimer-1);if(animTimer>=0)return false;
        animCursor=(animCursor+1)&255;int value=romByte(script+1+animCursor);
        if(value==0xF4){animCursor=0;return true;}
        if(value==0xFC){animCursor=0;value=romByte(script+1);}
        frame=value;animTimer=romByte(script);return false;
    }
    @Override public int getX(){return x;}
    @Override public int getY(){return y;}
    @Override public int getPriorityBucket(){return x<0x4200?1:priority;}
    @Override public boolean isHighPriority(){return x>=0x4200;}
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if(!visible || isDestroyed())return;
        var renderer=getRenderer(artKey);
        if(renderer!=null && renderer.isReady())renderer.drawFrameIndexForcedPriority(frame,x,y,flip,false,-1,isHighPriority());
    }
}
