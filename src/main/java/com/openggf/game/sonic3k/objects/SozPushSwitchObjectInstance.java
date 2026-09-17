package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.runtime.SozZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.IdentityHashMap;
import java.util.List;

/** Obj_SOZPushSwitch ($4187C): analog push displacement in Level_trigger_array. */
public final class SozPushSwitchObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SpawnRewindRecreatable, RomObjectCodePointerProvider {
    private final FbzParticipantStateTable participants=new FbzParticipantStateTable(1);
    private int x;
    private int charge;
    private int decayTimer;
    private int soundTimer;
    private int previousRockX;
    private boolean retained;
    public SozPushSwitchObjectInstance(ObjectSpawn spawn) { super(spawn,"SOZPushSwitch"); x=spawn.x(); }

    @Override public void update(int vIntRunCount, PlayableEntity leader) {
        int index=spawn.subtype() & 15;
        if(retained) {
            // loc_41984: a newly loaded placement supersedes this invisible decay owner.
            for(var object:services().objectManager().getActiveObjects()) {
                if(object!=this && spawn.equals(object.getSpawn()) && !object.isDestroyed()) {
                    ObjectLifetimeOps.expireDynamic(this); return;
                }
            }
            charge=SozZoneRuntimeState.trigger(index);
            decay(); SozZoneRuntimeState.writeTrigger(index,charge);
            if(charge==0) ObjectLifetimeOps.expireDynamic(this);
            return;
        }
        charge=SozZoneRuntimeState.trigger(index);
        var players=services().playerQuery().playersFor(
                ObjectPlayerParticipationPolicy.MAIN_PLUS_ENGINE_SIDEKICKS_AS_NATIVE_P2_EXTENDED);
        var wasPushing=new IdentityHashMap<PlayableEntity,Boolean>();
        // Establish stable participant order before collision callbacks can bind P2 first.
        for(var p:players) {
            participants.slot(p);
            wasPushing.put(p,p instanceof AbstractPlayableSprite sprite && sprite.getPushing());
        }
        checkpointAll();
        boolean pushed=false;
        for(var p:players) {
            if(!participants.flag(participants.slot(p),0) || !Boolean.TRUE.equals(wasPushing.get(p))) continue;
            boolean playerOnLeft=(x & 0xFFFF)>=(p.getCentreX() & 0xFFFF);
            if(playerOnLeft == flipped()) continue;
            if(charge!=0x80) {
                charge=(charge+1)&0xFFFF;
                if((charge&3)==0) {
                    NativePositionOps.writeXPosPreserveSubpixel(p,p.getCentreX()+(flipped()?-1:1));
                    services().playSfx(Sonic3kSfx.PUSH_BLOCK.id);
                }
            }
            pushed=true; break; // Either eligible native player blocks later players, even at saturation.
        }
        boolean decayed=!pushed && decay();
        positionFromCharge();
        if((spawn.subtype()&0x80)!=0) coupleRock(decayed);
        SozZoneRuntimeState.writeTrigger(index,charge);
        if(services().camera()!=null && isCoarseXOutOfRange(spawn.x(),services().camera().getX(),coarseXCullRange())) {
            if(charge==0) setDestroyedByOffscreen();
            else {
                retained=true;
                // Clear only respawn bit 7 and retain this SST as loc_41984.
                services().objectManager().releaseSpawnForRespawn(this,spawn);
                // loc_4197E falls through into the retained routine this pass.
                decay(); SozZoneRuntimeState.writeTrigger(index,charge);
                if(charge==0) ObjectLifetimeOps.expireDynamic(this);
            }
        }
    }
    private boolean decay() {
        if((spawn.subtype()&0x70)==0) {
            decayTimer=(short)(decayTimer-1);
            if(decayTimer>=0) return false;
            decayTimer=9;
        }
        if(charge==0) return false;
        charge=(charge-1)&0xFFFF;
        if(charge==0) services().playSfx(Sonic3kSfx.DOOR_CLOSE.id);
        else {
            soundTimer=(byte)(soundTimer-1);
            if(soundTimer<0) { soundTimer=3; services().playSfx(Sonic3kSfx.DOOR_MOVE.id); }
        }
        return true;
    }
    private void positionFromCharge() { x=(spawn.x()+(flipped()?-(charge>>>2):(charge>>>2)))&0xFFFF; }
    private boolean flipped() { return (spawn.renderFlags()&1)!=0; }
    private void coupleRock(boolean decayed) {
        if(!(services().zoneRuntimeState() instanceof SozZoneRuntimeState state) || state.pushableRockSlot()<0) return;
        SozPushableRockObjectInstance rock=null;
        for(var object:services().objectManager().getActiveObjects()) {
            if(object instanceof AbstractObjectInstance a && a.getSlotIndex()==state.pushableRockSlot()) {
                if(object instanceof SozPushableRockObjectInstance r && r.isLinkedSwitchRock()) rock=r;
                break;
            }
        }
        // sub_41AA8 rejects a reused slot or a rock which entered its fall routine.
        if(rock==null) { state.publishPushableRockSlot(-1); return; }
        int dx=(rock.getX()-x+0x1B)&0xFFFF;
        int dy=(rock.getY()-spawn.y()+0xC)&0xFFFF;
        if(dx<0x37 && dy<0x18) {
            boolean rightSide=dx>=0x1C;
            if(flipped()==rightSide && charge!=0x80) {
                int increment=previousRockX==rock.getX()?1:4+(decayed?1:0);
                charge=Math.min(0x80,charge+increment);
            }
            positionFromCharge();
            rock.moveBySwitch((x+(rightSide?0x1C:-0x1C))&0xFFFF);
        }
        previousRockX=rock.getX();
    }
    @Override public void setPlayerPushing(PlayableEntity player,boolean pushing) { participants.flag(participants.slot(player),0,pushing); }
    @Override public int getX() { return x; }
    @Override public SolidObjectParams getSolidParams() { return new SolidObjectParams(23,16,17); }
    // loc_1E154 re-reads width_pixels=$30 (Obj_SOZPushSwitch), not the caller's d1=$17-$B.
    @Override public int getTopLandingHalfWidth(PlayableEntity player,int collisionHalfWidth) { return 0x30; }
    @Override public boolean isSolidFor(PlayableEntity player) { return !retained; }
    @Override public boolean carriesRiderOnHorizontalMove(PlayableEntity player) { return false; }
    @Override public SolidExecutionMode solidExecutionMode() { return SolidExecutionMode.MANUAL_CHECKPOINT; }
    @Override public boolean usesInstanceSolidStateLatchKey() { return true; }
    @Override public int romObjectCodePointerHighWord() { return 4; }
    @Override public int getPriorityBucket() { return 6; }
    @Override public int getOnScreenHalfWidth() { return 48; }
    @Override public int getOnScreenHalfHeight() { return 16; }
    @Override public boolean usesCustomOutOfRangeCheck() { return true; }
    @Override public boolean isCustomOutOfRange(int cameraX) { return false; }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if(retained) return;
        var r=getRenderer(Sonic3kObjectArtKeys.SOZ_PUSH_SWITCH);
        if(r!=null && r.isReady()) {
            // Native main sprite precedes its child in SAT priority; paint the track first.
            r.drawFrameIndex(0,spawn.x()+(flipped()?-16:16),spawn.y(),false,false);
            r.drawFrameIndex(1,x,spawn.y(),flipped(),(spawn.renderFlags()&2)!=0);
        }
    }
}
