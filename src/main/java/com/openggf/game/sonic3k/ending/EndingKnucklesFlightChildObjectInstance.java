package com.openggf.game.sonic3k.ending;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/** Native 601B2–601E0/6020C/6022E children; parent3 is a physical SST address. */
public final class EndingKnucklesFlightChildObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable, EndingFlightChildHost {
    static final int TORNADO=0, PILOT=1, MASTER=2, PASSENGER=3, FLAME=4, PROPELLER=5,
            SMALL_PROPELLER=6, MASTER_PASSENGER=7, SMALL_MASTER=8;
    private int parentSlot, role, subtype, dx, dy, x, y, frame, noseCursor, nativeVInt, nativeMode;
    private int renderFlags, priority, artTile, status, flags38, xFraction, yFraction, animationFrame;
    private boolean initialized, draw, deletePending, powered;

    EndingKnucklesFlightChildObjectInstance(EndingFlightChildHost parent, int role, int subtype, int dx, int dy) {
        this(new ObjectSpawn((parent.getX()+dx)&0xFFFF, (parent.getY()+dy)&0xFFFF, 0, role, 0, false, 0));
        parentSlot=((AbstractObjectInstance)parent).getSlotIndex();
        this.subtype=subtype;this.dx=dx;this.dy=dy;
        nativeMode=parent.flightPlayerMode();powered=parent.flightPowered();
        // CreateChild1_Normal/CreateChild6_Simple copy art_tile, not priority/render_flags.
        artTile=parent.nativeSstState().artTile();
    }
    private EndingKnucklesFlightChildObjectInstance(ObjectSpawn spawn) {
        super(spawn,"EndingFlightChild");role=spawn.subtype();x=spawn.x();y=spawn.y();
    }
    @Override public EndingKnucklesFlightChildObjectInstance recreateForRewind(RewindRecreateContext c) {
        return new EndingKnucklesFlightChildObjectInstance(c.spawn());
    }
    // All retained fields are scalars captured by the generic rewind schema.
    // Resolving parentSlot on every dispatch follows a new code owner after a native partial write.
    private AbstractObjectInstance parent() {
        return NativeSstSlotAccess.currentOwner(services().objectManager(),parentSlot);
    }
    private NativeSstState header(AbstractObjectInstance parent) {
        // Delete_Referenced_Sprite clears the entire SST.
        if(parent==null)return NativeSstState.CLEARED;
        if(!(parent instanceof NativeSstStateSource source))
            throw new IllegalStateException("Ending child parent lacks live SST projection: "+parent.getClass().getName());
        return source.nativeSstState();
    }
    @Override public NativeSstState nativeSstState() {
        return new NativeSstState(renderFlags,xFraction,yFraction,frame,animationFrame,0,flags38,
                status,priority,artTile);
    }
    @Override public void update(int vIntRunCount,PlayableEntity player) {
        nativeVInt=vIntRunCount;draw=false;
        if(deletePending){ObjectLifetimeOps.deleteNoRespawn(this);return;}
        var parent=parent();var state=header(parent);
        if(!initialized) {
            initialized=true;
            switch(role) {
                case TORNADO -> {
                    artTile=0x81E3;priority=0x200;renderFlags|=0x44;frame=1;yFraction=2;
                    spawnChild(()->new EndingKnucklesFlightChildObjectInstance(this,PILOT,0,0,-0x10));
                    draw=true; // Obj_5EA52 calls Child_Draw_Sprite even on its init pass.
                }
                case PILOT -> {
                    priority=state.priority();renderFlags|=state.renderFlags()&4;
                    artTile=nativeMode<=1?0x81E3:0x826E;frame=nativeMode<=1?0xC:nativeMode==2?0xB:0x12;
                }
                case MASTER -> {
                    artTile=0xC52E;priority=0x300;renderFlags|=4;frame=2;
                    refresh(parent,state,false);draw=true; // Obj_5EBC6 falls through.
                }
                case PASSENGER,SMALL_PROPELLER -> initializeAttributes(state,0x180,0x16);
                case PROPELLER -> initializeAttributes(state,0x200,4);
                case MASTER_PASSENGER -> initializeAttributes(state,0x200,9);
                case SMALL_MASTER -> {
                    artTile=0x426E;initializeAttributes(state,0x200,0x11);
                }
                case FLAME -> {
                    artTile=0x826E;priority=0x200;renderFlags|=state.renderFlags()&4;
                    artTile=(artTile&0x7FFF)|(state.artTile()&0x8000);
                    int mode=nativeMode==3&&powered?4:nativeMode;
                    frame=romByte(0x5ED0E+mode*2+(subtype==0?0:1));
                }
                default -> throw new IllegalStateException("Ending flight child "+role);
            }
        } else switch(role) {
            case TORNADO,PILOT -> {copyPriority(state);refresh(parent,state,true);draw=true;}
            case MASTER -> {refresh(parent,state,false);draw=true;}
            case PASSENGER -> {
                readOffset(0x5EC8E+(state.mappingFrame()-1)*2);refresh(parent,state,true);
                if(retireImmediately(state,0x10))return;
                frame=(vIntRunCount&2)==0?0x16:0x17;draw=true;
            }
            case FLAME -> {
                if(retireImmediately(state,0x10))return;
                try {
                    int table=0x600B0;
                    int row=table+(short)services().romReader().readU16BE(table+(state.mappingFrame()-1)*2);
                    readOffset(row+(subtype==0?0:2));
                } catch(IOException failure){throw new UncheckedIOException(failure);}
                refresh(parent,state,true);draw=true;
            }
            case PROPELLER -> {
                refresh(parent,state,true);if(retireImmediately(state,0x10))return;draw=(vIntRunCount&2)==0;
            }
            case MASTER_PASSENGER -> {
                refresh(parent,state,true);draw=true;
                if((state.flags38()&0x10)!=0){deletePending=true;flags38|=0x10;draw=false;}
            }
            case SMALL_MASTER -> {
                copyPriority(state);refresh(parent,state,true);if(retireImmediately(state,0x20))return;draw=true;
            }
            case SMALL_PROPELLER -> {
                refresh(parent,state,true);if(retireImmediately(state,2))return;
                frame=(vIntRunCount&2)==0?0x18:0x19;draw=true;
            }
            default -> throw new IllegalStateException("Ending flight child "+role);
        }
        if(role==TORNADO){noseCursor=(noseCursor+1)%6;animationFrame=6+(nativeVInt&1);}
        // Go_Delete_Sprite installs Delete_Current_Sprite for the following dispatch.
        if(draw&&(role==TORNADO||role==PILOT||role==MASTER)&&(state.status()&0x80)!=0) {
            deletePending=true;status|=0x80;draw=false;
        }
    }
    private void initializeAttributes(NativeSstState state,int priority,int frame) {
        this.priority=priority;this.frame=frame;renderFlags|=state.renderFlags()&4;
    }
    private boolean retireImmediately(NativeSstState state,int bit) {
        if((state.flags38()&bit)==0)return false;
        ObjectLifetimeOps.deleteNoRespawn(this);return true;
    }
    private void copyPriority(NativeSstState state) {
        priority=state.priority();artTile=(artTile&0x7FFF)|(state.artTile()&0x8000);
    }
    private void readOffset(int address) {
        try{dx=(byte)services().romReader().readU8(address);dy=(byte)services().romReader().readU8(address+1);}
        catch(IOException failure){throw new UncheckedIOException(failure);}
    }
    private int romByte(int address) {
        try{return services().romReader().readU8(address);}
        catch(IOException failure){throw new UncheckedIOException(failure);}
    }
    private void refresh(AbstractObjectInstance parent,NativeSstState state,boolean adjusted) {
        if(adjusted)renderFlags=(renderFlags&~3)|(state.renderFlags()&3);
        int offsetX=adjusted&&(renderFlags&1)!=0?-dx:dx;
        int offsetY=adjusted&&(renderFlags&2)!=0?-dy:dy;
        x=((parent==null?0:parent.getX())+offsetX)&0xFFFF;
        y=((parent==null?0:parent.getY())+offsetY)&0xFFFF;
    }
    @Override public int getX(){return x;}
    @Override public int getY(){return y;}
    @Override public boolean isPersistent(){return true;}
    @Override public boolean isHighPriority(){return (artTile&0x8000)!=0;}
    @Override public int getPriorityBucket(){return priority>>>7;}
    int role(){return role;}
    int subtype(){return subtype;}
    int mappingFrame(){return frame;}
    int parentSlot(){return parentSlot;}
    boolean submittedForDraw(){return draw;}
    @Override public int flightPlayerMode(){return nativeMode;}
    @Override public boolean flightFlipX(){return (renderFlags&1)!=0;}
    @Override public boolean flightChildRetired(){return (status&0x80)!=0;}
    @Override public boolean flightChildSmall(){return (flags38&0x10)!=0;}
    @Override public boolean flightChildHidden(){return (flags38&2)!=0;}
    @Override public int flightFrame(){return frame;}
    @Override public boolean flightPowered(){return powered;}
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if(isDestroyed()||!initialized||!draw)return;
        int map=role==TORNADO||role==PILOT&&nativeMode<=1?S3kEndingResources.MAP_ENDING_PLANE:
                role==MASTER?S3kEndingResources.MAP_MASTER_EMERALD:S3kEndingResources.MAP_ENDING_PLANE_EXTRA;
        draw(map,frame,x,y);
        if(role==TORNADO) {
            // These two pieces are native mainspr_childsprites within this one SST.
            int sign=(renderFlags&1)!=0?-1:1;
            draw(map,romByte(0x60072+noseCursor),x+sign*(byte)romByte(0x6006E),y+(byte)romByte(0x6006F));
            draw(map,animationFrame,x+sign*(byte)romByte(0x60070),y+(byte)romByte(0x60071));
        }
    }
    private void draw(int map,int mapping,int drawX,int drawY) {
        if((renderFlags&4)==0){drawX=EndingScreenProjection.worldX(services().camera(),drawX);
            drawY=EndingScreenProjection.worldY(services().camera(),drawY);}
        EndingSpriteRenderSupport.draw(services(),"ending-flight-child-"+role,map,artTile&0x7FF,
                (artTile>>>13)&3,mapping,drawX,drawY,(renderFlags&1)!=0,(renderFlags&2)!=0,
                isHighPriority(),getPriorityBucket());
    }
}
