package com.openggf.game.sonic3k.ending;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/** Native 601BA/601B2/601C2/601CA/601D0/601D8/6022E flight children. */
public final class EndingKnucklesFlightChildObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable, EndingFlightChildHost {
    static final int TORNADO=0,PILOT=1,MASTER=2,PASSENGER=3,FLAME=4,PROPELLER=5,SMALL_PROPELLER=6;
    private EndingFlightChildHost parent;
    private int role,subtype,dx,dy,x,y,frame,noseCursor,nativeVInt;
    private boolean initialized,flip,high=true,draw=true;
    EndingKnucklesFlightChildObjectInstance(EndingFlightChildHost parent,int role,int subtype,int dx,int dy){
        this(new ObjectSpawn((parent.getX()+dx)&0xFFFF,(parent.getY()+dy)&0xFFFF,0,role,0,false,0));
        this.parent=parent;this.subtype=subtype;this.dx=dx;this.dy=dy;high=parent.isHighPriority();
    }
    private EndingKnucklesFlightChildObjectInstance(ObjectSpawn spawn){
        super(spawn,"KnucklesFlightChild");role=spawn.subtype();x=spawn.x();y=spawn.y();
    }
    @Override public EndingKnucklesFlightChildObjectInstance recreateForRewind(RewindRecreateContext c){
        return new EndingKnucklesFlightChildObjectInstance(c.spawn());
    }
    private record Extra(ObjectRefId parent,int role,int subtype,int dx,int dy,int x,int y,int frame,
                         int noseCursor,int nativeVInt,boolean initialized,boolean flip,boolean high,boolean draw)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra { }
    @Override public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext c){
        var id=c.identityTable().map(t->t.encodeObject(parent)).orElse(null);
        return super.captureRewindState(c).withObjectSubclassExtra(new Extra(id,role,subtype,dx,dy,x,y,frame,
                noseCursor,nativeVInt,initialized,flip,high,draw));
    }
    @Override public void restoreRewindState(PerObjectRewindSnapshot s,RewindCaptureContext c){
        super.restoreRewindState(s,c);
        if(s.objectSubclassExtra() instanceof Extra e){
            parent=e.parent()==null?null:(EndingFlightChildHost)c.requireIdentityTable().resolveObject(e.parent(),true);
            role=e.role();subtype=e.subtype();dx=e.dx();dy=e.dy();x=e.x();y=e.y();frame=e.frame();
            noseCursor=e.noseCursor();nativeVInt=e.nativeVInt();initialized=e.initialized();flip=e.flip();
            high=e.high();draw=e.draw();
        }
    }
    @Override public void update(int vIntRunCount,PlayableEntity player){
        nativeVInt=vIntRunCount;draw=true;
        if(parent==null||parent.isDestroyed()){ObjectLifetimeOps.deleteNoRespawn(this);return;}
        if(!initialized){
            initialized=true;
            switch(role){
                case TORNADO -> {frame=1;spawnChild(()->new EndingKnucklesFlightChildObjectInstance(this,PILOT,0,0,-0x10));}
                case PILOT -> frame=0x12;
                case MASTER -> {frame=2;high=true;}
                case PASSENGER,SMALL_PROPELLER -> frame=0x16;
                case PROPELLER -> frame=4;
                case FLAME -> frame=romByte(0x5ED0E+(parent.flightPowered()?8:6)+(subtype==0?0:1));
                default -> throw new IllegalStateException("Ending flight child "+role);
            }
            // Obj_5EBC6 falls through to Refresh_ChildPosition in its init dispatch.
            if(role==MASTER)refresh(false);
        }else switch(role){
            case TORNADO,PILOT -> {high=parent.isHighPriority();refresh(true);}
            case MASTER -> refresh(false);
            case PASSENGER -> {
                if(parent.flightChildSmall()){ObjectLifetimeOps.deleteNoRespawn(this);return;}
                readOffset(0x5EC8E+(parent.flightFrame()-1)*2);refresh(true);
                frame=(vIntRunCount&2)==0?0x16:0x17;
            }
            case FLAME -> {
                if(parent.flightChildSmall()){ObjectLifetimeOps.deleteNoRespawn(this);return;}
                try{
                    int table=0x600B0;
                    int row=table+(short)services().romReader().readU16BE(table+(parent.flightFrame()-1)*2);
                    readOffset(row+(subtype==0?0:2));
                }catch(IOException failure){throw new UncheckedIOException(failure);}
                refresh(true);
            }
            case PROPELLER -> {
                refresh(true);
                if(parent.flightChildSmall()){ObjectLifetimeOps.deleteNoRespawn(this);return;}
                draw=(vIntRunCount&2)==0;
            }
            case SMALL_PROPELLER -> {
                refresh(true);
                if(parent.flightChildHidden()){ObjectLifetimeOps.deleteNoRespawn(this);return;}
                frame=(vIntRunCount&2)==0?0x18:0x19;
            }
            default -> throw new IllegalStateException("Ending flight child "+role);
        }
        if(role==TORNADO)noseCursor=(noseCursor+1)%6; // sub_60038, including init dispatch
        // Only Child_Draw_Sprite owners consume parent status bit7.
        if((role==TORNADO||role==PILOT||role==MASTER)&&parent.flightChildRetired())
            ObjectLifetimeOps.deleteNoRespawn(this);
    }
    private void readOffset(int address){
        try{dx=(byte)services().romReader().readU8(address);dy=(byte)services().romReader().readU8(address+1);}
        catch(IOException failure){throw new UncheckedIOException(failure);}
    }
    private int romByte(int address){
        try{return services().romReader().readU8(address);}
        catch(IOException failure){throw new UncheckedIOException(failure);}
    }
    private void refresh(boolean adjusted){
        if(adjusted)flip=parent.flightFlipX();
        x=(parent.getX()+(adjusted&&flip?-dx:dx))&0xFFFF;y=(parent.getY()+dy)&0xFFFF;
    }
    @Override public int getX(){return x;}
    @Override public int getY(){return y;}
    @Override public boolean isPersistent(){return true;}
    @Override public boolean isHighPriority(){return high;}
    @Override public int getPriorityBucket(){return role==MASTER?6:role==PASSENGER||role==SMALL_PROPELLER?3:4;}
    int role(){return role;}
    int subtype(){return subtype;}
    int mappingFrame(){return frame;}
    @Override public boolean flightFlipX(){return flip;}
    @Override public boolean flightChildRetired(){return isDestroyed();}
    @Override public boolean flightChildSmall(){return false;}
    @Override public boolean flightChildHidden(){return false;}
    @Override public int flightFrame(){return frame;}
    @Override public boolean flightPowered(){return parent!=null&&parent.flightPowered();}
    @Override public void appendRenderCommands(List<GLCommand> commands){
        if(isDestroyed()||!initialized||!draw)return;
        int map=role==TORNADO?S3kEndingResources.MAP_ENDING_PLANE:role==MASTER?
                S3kEndingResources.MAP_MASTER_EMERALD:S3kEndingResources.MAP_ENDING_PLANE_EXTRA;
        int tile=role==TORNADO?0x1E3:role==MASTER?0x52E:0x26E;
        int palette=role==MASTER?2:role==SMALL_PROPELLER?1:0;
        draw(map,tile,palette,frame,x,y);
        if(role==TORNADO){
            // These two pieces are native mainspr_childsprites within this SST.
            int sign=flip?-1:1;
            draw(map,tile,0,romByte(0x60072+noseCursor),x+sign*(byte)romByte(0x6006E),y+(byte)romByte(0x6006F));
            draw(map,tile,0,6+(nativeVInt&1),x+sign*(byte)romByte(0x60070),y+(byte)romByte(0x60071));
        }
    }
    private void draw(int map,int tile,int palette,int mapping,int drawX,int drawY){
        EndingSpriteRenderSupport.draw(services(),"knuckles-ending-child-"+role,map,tile,palette,mapping,
                drawX,drawY,flip,high,getPriorityBucket());
    }
}
