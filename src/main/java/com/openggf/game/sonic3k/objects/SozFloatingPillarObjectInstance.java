package com.openggf.game.sonic3k.objects;

import com.openggf.game.OscillationManager;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.solid.ContactKind;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.game.DamageCause;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.IdentityHashMap;
import java.util.List;

/** Obj_SOZFloatingPillar ($41176), including the top/bottom spike variants. */
public final class SozFloatingPillarObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SpawnRewindRecreatable, RomObjectCodePointerProvider {
    private boolean initialized;
    private int x,y,width,height,mappingFrame,hurtMask;
    public SozFloatingPillarObjectInstance(ObjectSpawn spawn) { super(spawn,"SOZFloatingPillar"); x=spawn.x(); y=spawn.y(); }
    @Override public void update(int vIntRunCount,PlayableEntity leader) {
        if(!initialized) {
            try {
                byte[] info=services().rom().readBytes(Sonic3kConstants.SOZ_FLOATING_PILLAR_SHAPES_ADDR+((spawn.subtype()>>>2)&0x1C),4);
                width=info[0]&255; height=info[1]&255; mappingFrame=info[2]&255; hurtMask=info[3]&255;
            } catch(IOException e) { throw new UncheckedIOException("Cannot load SOZ pillar size from ROM",e); }
            initialized=true;
        }
        int mode=spawn.subtype()&15;
        if(mode!=0) {
            // OscillationManager excludes the table's initial control WORD.
            int offset=switch(mode) { case 1,4 -> 8; case 2,5 -> 0x1C; case 3,6 -> 0x38;
                default -> throw new IllegalStateException("Invalid SOZ pillar movement index "+mode); };
            int delta=OscillationManager.getByte(offset);
            delta=mode==3 || mode==6 ? delta*2-0x80 : delta-(mode==1 || mode==4 ? 0x20 : 0x40);
            if((spawn.renderFlags()&1)!=0) delta=-delta;
            if(mode<=3) x=(spawn.x()+delta)&0xFFFF; else y=(spawn.y()+delta)&0xFFFF;
        }
        var previouslyStanding=new IdentityHashMap<PlayableEntity,Boolean>();
        var manager=services().objectManager();
        if(manager!=null) for(var player:services().playerQuery().playersFor(
                ObjectPlayerParticipationPolicy.MAIN_PLUS_ENGINE_SIDEKICKS_AS_NATIVE_P2_EXTENDED))
            previouslyStanding.put(player,manager.hasObjectStandingBit(player,this));
        var contacts=checkpointAll();
        if(contacts!=null && hurtMask!=0) contacts.perPlayer().forEach((player,contact) -> {
            boolean hit=mappingFrame==1 ? contact.kind()==ContactKind.TOP
                    && !Boolean.TRUE.equals(previouslyStanding.get(player))
                    : contact.kind()==ContactKind.BOTTOM || contact.kind()==ContactKind.CRUSH;
            if(hit) hurt(player,vIntRunCount);
        });
    }
    private void hurt(PlayableEntity player,int vIntRunCount) {
        if(player.getDead() || player.getInvulnerable()) return;
        // sub_24280 subtracts the current velocity after the solid helper, then HurtCharacter.
        if(player instanceof AbstractPlayableSprite p) {
            int position=((p.getCentreY()<<16)|p.getYSubpixelRaw())-(p.getYSpeed()<<8);
            NativePositionOps.writeYPosPreserveSubpixel(p,position>>16);
            p.setSubpixelRaw(p.getXSubpixelRaw(),position&0xFFFF);
        }
        if(player.isCpuControlled()) { player.applyHurt(x,DamageCause.NORMAL); return; }
        boolean rings=player.getRingCount()>0;
        if(rings && !player.hasShield()) services().spawnLostRings(player,vIntRunCount);
        player.applyHurtOrDeath(x,DamageCause.NORMAL,rings);
    }
    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public SolidObjectParams getSolidParams() { return new SolidObjectParams(width+11,height,height+1); }
    // SolidObject_cont loc_1E042 branches on x_vel < 0, so zero velocity
    // still clears ground_vel on left-side penetration (including wall running).
    @Override public boolean zeroXSpeedStopsOnLeftSideContact() { return true; }
    @Override public SolidExecutionMode solidExecutionMode() { return SolidExecutionMode.MANUAL_CHECKPOINT; }
    // SolidObjectFull_1P loc_1DC98 consumes an airborne stale standing bit
    // at this object's own checkpoint and returns without a new contact.
    @Override public boolean airborneStaleStandingBitReturnsNoContact(PlayableEntity player) { return true; }
    @Override public boolean airborneRiderUnseatRequiresOwnCheckpoint(PlayableEntity player) { return true; }
    @Override public boolean usesInstanceSolidStateLatchKey() { return true; }
    @Override public int romObjectCodePointerHighWord() { return 4; }
    @Override public int getPriorityBucket() { return 5; }
    @Override public int getOnScreenHalfWidth() { return width; }
    @Override public int getOnScreenHalfHeight() { return height; }
    @Override public boolean checksOutOfRangeAfterRoutine() { return true; }
    @Override public boolean usesCustomOutOfRangeCheck() { return true; }
    @Override public boolean isCustomOutOfRange(int cameraX) { return isCoarseXOutOfRange(spawn.x(),cameraX,coarseXCullRange()); }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        var r=getRenderer(Sonic3kObjectArtKeys.SOZ_FLOATING_PILLAR);
        if(r!=null && r.isReady()) r.drawFrameIndex(mappingFrame,x,y,(spawn.renderFlags()&1)!=0,(spawn.renderFlags()&2)!=0);
    }
}
