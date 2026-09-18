package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.runtime.SozZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;
import java.util.List;

/** SKL $41, Obj_SOZLightSwitch ($40E5E)..sub_40F52. One multisprite hanging switch. */
public final class SozLightSwitchObjectInstance extends AbstractObjectInstance implements SpawnRewindRecreatable {
    private final FbzParticipantStateTable players=new FbzParticipantStateTable(2);
    private int extension;
    public SozLightSwitchObjectInstance(ObjectSpawn spawn){super(spawn,"SOZLightSwitch");}
    @Override public void update(int vIntRunCount,PlayableEntity leader) {
        // $30 is a WORD containing the two capture bytes: either native rider pulls.
        boolean held=false;for(int i=0;i<players.size();i++)held|=players.flag(i,0);
        int limit=(spawn.subtype()&0x7F)<<3;
        if(held && extension!=limit) {
            extension=(extension+2)&0xFFFF;
            if(extension==limit && services().zoneRuntimeState() instanceof SozZoneRuntimeState state)state.lighting().resetLight();
        } else if(!held && extension!=0) extension=(extension-2)&0xFFFF;
        if(leader instanceof AbstractPlayableSprite p)updatePlayer(p);
        for(var participant:services().playerQuery().playersFor(ObjectPlayerParticipationPolicy.MAIN_PLUS_ENGINE_SIDEKICKS_AS_NATIVE_P2_EXTENDED))
            if(participant!=leader && participant instanceof AbstractPlayableSprite p)updatePlayer(p);
    }
    private void updatePlayer(AbstractPlayableSprite p) {
        int slot=players.slot(p);
        if(players.flag(slot,0)) {
            if(!p.isRenderFlagOnScreen() || p.getDead() || p.isHurt()){release(p,slot,60);return;}
            if(p.isLogicalJumpPressActive()) {
                boolean direction=(p.getLogicalInputState()&15)!=0;
                release(p,slot,direction?60:18);
                if(p.isLeftPressed())p.setXSpeed((short)-0x200);
                if(p.isRightPressed())p.setXSpeed((short)0x200);
                p.setYSpeed((short)-0x380);p.setAir(true);p.setJumping(true);
                int x=p.getCentreX(),y=p.getCentreY();p.setRolling(true);
                NativePositionOps.writeXPosPreserveSubpixel(p,x);NativePositionOps.writeYPosPreserveSubpixel(p,y);
                p.applyCustomRadii(7,14);p.setAnimationId(2);p.setRollingJump(false);p.setFlipAngle(0);return;
            }
            if((spawn.subtype()&0x80)!=0) {
                var floor=ObjectTerrainUtils.checkFloorDist(services().levelManager(),services().backgroundPlaneCollisionProvider(),
                        (p.getTopSolidBit()&255)!=0xC,p.getCentreX(),p.getCentreY()+p.getYRadius());
                if(floor!=null && floor.distance()<=0){release(p,slot,60);return;}
            }
            NativePositionOps.writeYPosPreserveSubpixel(p,getY()+extension+0x30);return;
        }
        int cooldown=players.get(slot,1);
        if(cooldown>0){players.set(slot,1,--cooldown);if(cooldown!=0)return;}
        if(((p.getCentreX()-getX()+0x10)&0xFFFF)>=0x20 ||
                ((p.getCentreY()-getY()-extension-0x30)&0xFFFF)>=0x18 ||
                p.isObjectControlled()&&!p.isObjectControlAllowsCpu() ||p.getDead()||p.isHurt()||p.isDebugMode())return;
        p.setXSpeed((short)0);p.setYSpeed((short)0);p.setGSpeed((short)0);
        NativePositionOps.writeXPosPreserveSubpixel(p,getX());NativePositionOps.writeYPosPreserveSubpixel(p,getY()+extension+0x30);
        p.setAnimationId(0x14);ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(p);
        players.flag(slot,0,true);services().playSfx(Sonic3kSfx.SWITCH.id);
    }
    private void release(AbstractPlayableSprite p,int slot,int cooldown){ObjectControlState.none().applyTo(p);players.flag(slot,0,false);players.set(slot,1,cooldown);}
    public boolean isPlayerHeld(PlayableEntity player){return players.flag(players.slot(player),0);}
    public int extension(){return extension;}
    @Override public int getPriorityBucket(){return 1;}
    @Override public int getOnScreenHalfWidth(){return 0x18;}
    @Override public int getOnScreenHalfHeight(){return 0x40;}
    @Override public void appendRenderCommands(List<GLCommand> commands){var r=getRenderer(Sonic3kObjectArtKeys.SOZ_LIGHT_SWITCH);if(r!=null&&r.isReady()){
        r.drawFrameIndex(6,getX(),getY(),false,false);r.drawFrameIndex(extension==0?0:(extension>>3)+1,getX(),getY()+extension,false,false);
    }}
}
