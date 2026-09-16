package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.runtime.SozZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.util.List;

/** loc_55F48, loc_55F98 and loc_55FDA: three independently allocated native SSTs. */
public final class SozAct1ArenaController extends AbstractObjectInstance implements SpawnRewindRecreatable {
    private int routine;
    private int timer;
    public SozAct1ArenaController(ObjectSpawn spawn){super(spawn,"SOZ1 arena admission");}
    public void allocateDoorSiblings(){
        var mask=spawnChild(()->new Mask(getSpawn()));
        if(mask!=null && !mask.isDestroyed())spawnChild(()->new Door(getSpawn()));
    }
    @Override public void update(int vIntRunCount,PlayableEntity player){
        var events=((SozZoneRuntimeState)services().zoneRuntimeState()).events();
        if(routine==0){if(events.screenShakeFlag()!=0)return;timer=60;routine=4;}
        if(routine==4){if(--timer!=0)return;events.doorSignal(-1);routine=8;}
        if(events.doorSignal()!=0)return;
        spawnFreeChild(()->new SozMinibossInstance(new ObjectSpawn(0x439D,0x9F7,0x97,0,0,false,0)));
        ObjectLifetimeOps.deleteNoRespawn(this);
    }
    @Override public boolean isPersistent(){return true;}
    @Override public void appendRenderCommands(List<GLCommand> commands){}

    private static com.openggf.data.RomByteReader reader(ObjectServices services){
        try{return services.romReader();}catch(java.io.IOException failure){throw new IllegalStateException("SOZ arena ROM",failure);}
    }

    /** Four ROM marker/companion pairs mask the statue and door below y=$A00. */
    public static final class Mask extends AbstractObjectInstance implements SpawnRewindRecreatable {
        private int x;
        public Mask(ObjectSpawn spawn){super(spawn,"SOZ1 arena sprite mask");}
        @Override public boolean isPersistent(){return true;}
        @Override public int getX(){return x;}
        @Override public int getY(){return 0xA00;}
        @Override public int getPriorityBucket(){return 4;}
        @Override public void update(int vIntRunCount,PlayableEntity player){if(services().currentAct()!=0)ObjectLifetimeOps.deleteNoRespawn(this);else x=services().camera().getX()&65535;}
        @Override public void appendRenderCommands(List<GLCommand> commands){
            var graphics=services().graphicsManager();if(isDestroyed() || !graphics.isSpriteSatCollectionActive())return;
            graphics.requestSpriteMask();var rom=reader(services());
            // Map_SOZ1EndDoor frame0, $56122: read even the control-marker tile words from ROM.
            for(int i=0;i<rom.readU16BE(0x56122);i++){
                int address=0x56124+i*6,size=rom.readU8(address+1);
                graphics.submitSpriteSatControlEntry(x+(short)rom.readU16BE(address+4),0xA00+(byte)rom.readU8(address),
                        (size>>2)+1,(size&3)+1,rom.readU16BE(address+2)&0x7FF);
            }
        }
    }

    public static final class Door extends AbstractObjectInstance implements SpawnRewindRecreatable {
        private int routine;
        private int displacement;
        private int x=0x439C;
        private int y=0x9D4;
        private boolean visible;
        public Door(ObjectSpawn spawn){super(spawn,"SOZ1 arena moving door");}
        @Override public boolean isPersistent(){return true;}
        @Override public int getX(){return x;}
        @Override public int getY(){return y;}
        @Override public int getPriorityBucket(){return 7;}
        @Override public boolean isHighPriority(){return true;}
        @Override public void update(int vIntRunCount,PlayableEntity player){
            var events=((SozZoneRuntimeState)services().zoneRuntimeState()).events();
            visible=false;
            if(routine==2 && services().currentAct()!=0){ObjectLifetimeOps.deleteNoRespawn(this);return;}
            if(routine==0 || routine==2){
                if(events.doorSignal()==0)return;
                services().playSfx(Sonic3kSfx.DOOR_OPEN.id);
                if(routine==0){events.backgroundRowReplacement(0xF6);routine=1;}else routine=3;
            }
            int levelFrameCounter=services().levelManager().getFrameCounter();
            int movement=(levelFrameCounter&3)==0?2:1;
            displacement+=routine==1?movement:-movement;
            y=0x9D4+displacement;
            x=0x439C+(reader(services()).readU8(0x4F438+(displacement&63))&1);
            visible=true;
            if(routine==1 && displacement>=0x58){displacement=0x58;events.doorSignal(0);routine=2;}
            else if(routine==3 && displacement<0){displacement=0;events.backgroundRowReplacement(0xFD);events.doorSignal(0);routine=0;}
            updateDynamicSpawn(x,y);
        }
        @Override public void appendRenderCommands(List<GLCommand> commands){
            if(!visible || isDestroyed())return;
            var renderer=getRenderer(Sonic3kObjectArtKeys.SOZ_ACT1_END_DOOR);
            if(renderer!=null)renderer.drawFrameIndexForcedPriority(1,x,y,false,false,-1,true);
        }
    }
}
