package com.openggf.game.sonic3k.ending;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.objects.S3kRawAnimation;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/** loc_5E504 / loc_5E612: the five flickies and two rabbits in Obj_Ending. */
public final class EndingAnimalObjectInstance extends AbstractObjectInstance implements RewindRecreatable {
    private int routine,index,xFixed,yFixed,xVelocity,yVelocity,baseX,timer,mappingFrame;
    private int animFrame,animTimer;
    private boolean rabbit,swingReverse;
    private transient S3kRawAnimation scripts;
    public EndingAnimalObjectInstance(boolean rabbit,int index){
        this(new ObjectSpawn(0,0,0,(rabbit?0x80:0)|index,0,false,0));
    }
    public EndingAnimalObjectInstance(boolean rabbit,int index,int parentX,int parentY){
        this(new ObjectSpawn(parentX,parentY,0,(rabbit?0x80:0)|index,0,false,0));
    }
    private EndingAnimalObjectInstance(ObjectSpawn spawn){
        super(spawn,"EndingAnimal");rabbit=(spawn.subtype()&0x80)!=0;index=spawn.subtype()&0x7F;
        setX(spawn.x());setY(spawn.y());
    }
    @Override public EndingAnimalObjectInstance recreateForRewind(RewindRecreateContext c){return new EndingAnimalObjectInstance(c.spawn());}
    private S3kEndingState ending(){return S3kEndingCoordinator.state(services());}
    @Override public int getX(){return xFixed>>>16;}
    @Override public int getY(){return yFixed>>>16;}
    @Override public boolean isPersistent(){return true;}
    @Override public int getPriorityBucket(){return rabbit?5:7;}
    public int routine(){return routine;}
    @Override public void update(int vInt,PlayableEntity player){
        if(ending().flag(3)){ObjectLifetimeOps.deleteNoRespawn(this);return;}
        if(routine==0){initialize();return;}
        if(rabbit)rabbit();else bird();
    }
    private void initialize(){
        routine=2;
        if(rabbit)timer=index*16+0x60;
        else{
            try{var reader=services().romReader();setX(reader.readU16BE(0x5E554+index*4));
                setY(reader.readU16BE(0x5E556+index*4));}
            catch(IOException failure){throw new UncheckedIOException(failure);}
            baseX=getX();timer=index*16;yVelocity=0x80;mappingFrame=2;
        }
    }
    private void bird(){
        if(routine==2){animate();if((timer=(short)(timer-1))<0){routine=4;xVelocity=-0x40;}return;}
        swing();move();
        if(routine==4){animate();if((short)(baseX-getX())>=0x30){routine=6;xVelocity=0x80;mappingFrame=2;}}
        else if(getX()>baseX){routine=4;xVelocity=-0x40;setX(baseX);}
    }
    private void rabbit(){
        if(routine==2){if((timer=(short)(timer-1))<0){routine=4;
            setX((services().rng().nextRaw()&0x3F)+0x180);setY(0x180);xVelocity=-0x300;yVelocity=-0x600;}
            return;}
        move();yVelocity=(short)(yVelocity+0x38);mappingFrame=yVelocity<0?0:1;
        if(yVelocity>=0&&getY()>=0x180){routine=2;timer=59;}
    }
    private void animate(){
        if(scripts==null)try{scripts=S3kRawAnimation.load(services().romReader(),0x602A1,6);}
        catch(IOException failure){throw new UncheckedIOException(failure);}
        var a=new S3kRawAnimation.State();a.script=0x602A1;a.animFrame=animFrame;a.animFrameTimer=animTimer;
        a.mappingFrame=mappingFrame;scripts.animateNoSst(a,0x602A1,()->{});
        animFrame=a.animFrame;animTimer=a.animFrameTimer;mappingFrame=a.mappingFrame;
    }
    private void swing(){
        int acceleration=8,maximum=0x80;
        if(!swingReverse){acceleration=-acceleration;yVelocity=(short)(yVelocity+acceleration);maximum=-maximum;
            if(yVelocity>maximum)return;swingReverse=true;acceleration=-acceleration;maximum=-maximum;}
        yVelocity=(short)(yVelocity+acceleration);if(yVelocity<maximum)return;
        swingReverse=false;yVelocity=(short)(yVelocity-acceleration);
    }
    private void move(){xFixed+=xVelocity<<8;yFixed+=yVelocity<<8;}
    private void setX(int x){xFixed=(x&0xFFFF)<<16|(xFixed&0xFFFF);}
    private void setY(int y){yFixed=(y&0xFFFF)<<16|(yFixed&0xFFFF);}
    @Override public void appendRenderCommands(List<GLCommand> commands){
        if(isDestroyed()||(rabbit&&routine!=4))return;
        var camera=services().camera();
        EndingSpriteRenderSupport.draw(services(),"ending-animals",0x60796,0x2FF,rabbit?3:0,mappingFrame,
                EndingScreenProjection.worldX(camera,getX()),EndingScreenProjection.worldY(camera,getY()),
                false,false,getPriorityBucket());
    }
}
