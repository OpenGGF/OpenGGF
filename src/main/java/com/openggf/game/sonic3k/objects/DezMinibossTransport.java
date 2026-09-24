package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.CharacterKey;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.runtime.S3kDezZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.physics.Direction;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/** loc_7E25C: independent SST surviving results and the DEZ1 -> DEZ2 load. */
final class DezMinibossTransport extends AbstractObjectInstance implements SpawnRewindRecreatable {
    private int state;
    private int timer;
    private boolean fromRight;
    private int angularSpeed;
    private int angle;
    private int posX;
    private int posY;
    private int yVelocity;

    DezMinibossTransport(ObjectSpawn spawn) { super(spawn,"DEZMinibossTransport"); }
    DezMinibossTransport() { this(new ObjectSpawn(0,0,0,0,0,false,0)); }
    @Override public boolean isPersistent() { return true; }
    @Override public boolean participatesInRomWorldTransitionOffset() { return false; }
    @Override public int getX() { return posX>>>16; }
    @Override public int getY() { return posY>>>16; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
    @Override public void update(int vIntRunCount,PlayableEntity ignored) {
        if(!(services().playerQuery().mainPlayerOrNull() instanceof AbstractPlayableSprite player)) return;
        switch(state) {
            case 0 -> {
                if(!services().gameState().isEndOfLevelActive()) return;
                state=1;
                // loc_7E25C falls through, but the just-tested FAA8 still holds us.
            }
            case 1 -> {
                if(services().gameState().isEndOfLevelActive()) return;
                state=2; player.setControlLocked(true);
                spawnFreeChild(()->new P2Hold(new ObjectSpawn(0,0,0,0,0,false,0)));
                fromRight=(player.getCentreX()&0xFFFF)>=0x140;
                walk(player);
            }
            case 2 -> walk(player);
            case 3 -> {
                if(--timer>=0) return;
                state=4; runtime().setEventsFg4(0xFF);
                runtime().setCameraStoredMinY(0);
                spawnFreeChild(()->new Bounds(true));
                runtime().setCameraStoredMaxY(0x2000);
                spawnFreeChild(()->new Bounds(false));
                services().playSfx(Sonic3kSfx.BIG_RUMBLE.id);
            }
            case 4 -> {
                if((player.getCentreY()&0xFFFF)<0x360 || player.getAir()) return;
                state=5; ObjectControlState.nativeBit7FullControl().applyTo(player);
                player.setAnimationId(0); stop(player); player.setPushing(false); player.setAir(true);
                player.setDirection(Direction.RIGHT); player.setRenderFlips(false,false);
            }
            case 5 -> {
                angularSpeed=(angularSpeed+8)&0xFFFF;
                if(angularSpeed==0x300) { state=6; timer=0x24; player.setHighPriority(false); }
                spin(player);
            }
            case 6 -> {
                if(--timer<0) {
                    state=7; yVelocity=-0x1000;
                    posX=(player.getCentreX()&0xFFFF)<<16; posY=(player.getCentreY()&0xFFFF)<<16;
                    services().playSfx(Sonic3kSfx.SUPER_TRANSFORM.id);
                }
                NativePositionOps.writeYPosPreserveSubpixel(player,(player.getCentreY()-0x10)&0xFFFF);
                spin(player);
            }
            case 7 -> {
                spin(player);
                if(yVelocity>=0) {
                    state=8;
                    int frame=CharacterKey.TAILS.equals(player.characterKey())?0xAD:
                            CharacterKey.KNUCKLES.equals(player.characterKey())?0x56:0xBA;
                    player.setMappingFrame(frame); player.setDirection(Direction.RIGHT); player.setRenderFlips(false,false);
                    player.setAnimationId(5); player.getAnimationManager().publishPreviousAnimationId(0);
                }
                fly(player);
            }
            case 8 -> fly(player);
            case 9 -> {
                if(--timer>=0) return;
                try {
                    S3kPaletteWriteSupport.applyLine(services().paletteOwnershipRegistryOrNull(),services().currentLevel(),
                            services().graphicsManager(),S3kPaletteOwners.DEZ_MINIBOSS,S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE,
                            1,services().rom().readBytes(0x7F01C,32));
                } catch(IOException failure) { throw new UncheckedIOException(failure); }
                ObjectControlState.none().applyTo(player); player.setObjectMappingFrameControl(false); player.setControlLocked(false);
                if(services().playerQuery().nativeP2OrNull() instanceof AbstractPlayableSprite p2) p2.setControlLocked(false);
                ObjectLifetimeOps.deleteNoRespawn(this);
            }
            default -> throw new IllegalStateException("Unknown DEZ transport routine "+state);
        }
    }
    private void walk(AbstractPlayableSprite player) {
        int x=player.getCentreX()&0xFFFF;
        if(fromRight?x>0x140:x<0x140) {
            boolean jump=player.getPushing();
            // Ctrl_1_locked suppresses hardware copies; loc_7E2C0/7E2DE owns
            // Ctrl_1_logical. The forced mask admits that scripted input to movement.
            player.setForcedInputMask((fromRight ? AbstractPlayableSprite.INPUT_LEFT : AbstractPlayableSprite.INPUT_RIGHT)
                    | (jump ? AbstractPlayableSprite.INPUT_JUMP : 0));
            player.setLogicalInputState(false,false,fromRight,!fromRight,jump,jump);
            return;
        }
        NativePositionOps.writeXPosPreserveSubpixel(player,0x140); stop(player); player.clearForcedInputMask(); player.clearLogicalInputState();
        state=3; timer=0x1F;
        spawnFreeChild(()->new DezMinibossExplosionController(0x100,0x760,0x14));
        spawnFreeChild(()->new DezMinibossExplosionController(0x180,0x760,0x14));
    }
    private void spin(AbstractPlayableSprite player) {
        angle=(angle+angularSpeed)&0xFFFF; if(angle>=0xC00) angle-=0xC00;
        try {
            int index=angle>>>8; int flags=services().romReader().readU8(0x7EF3A+index);
            player.setObjectMappingFrameControl(true); player.setMappingFrame(services().romReader().readU8(0x7EF2E+index));
            player.setRenderFlips((flags&1)!=0,(flags&2)!=0);
        } catch(IOException failure) { throw new UncheckedIOException(failure); }
    }
    private void fly(AbstractPlayableSprite player) {
        posY+=(short)yVelocity<<8; yVelocity=(short)(yVelocity+0x38);
        int floor=CharacterKey.TAILS.equals(player.characterKey())?0x3B0:0x3AC;
        if(yVelocity>=0 && getY()>=floor) {
            posY=(floor<<16)|(posY&0xFFFF); state=9; timer=119;
            spawnFreeChild(()->S3kTitleCardOwnerSlotObjectInstance.inLevel(11,1));
        }
        NativePositionOps.writeXPosPreserveSubpixel(player,getX()); NativePositionOps.writeYPosPreserveSubpixel(player,getY());
    }
    private static void stop(AbstractPlayableSprite player) {
        player.setXSpeed((short)0); player.setYSpeed((short)0); player.setGSpeed((short)0);
    }
    private S3kDezZoneRuntimeState runtime() { return (S3kDezZoneRuntimeState)services().zoneRuntimeState(); }
    int stateForTest() { return state; }

    /** loc_863C0: a positive lock suppresses manual P2 input, not native CPU following. */
    static final class P2Hold extends AbstractObjectInstance implements SpawnRewindRecreatable {
        private boolean initialized;
        P2Hold(ObjectSpawn spawn) { super(spawn,"DEZTransportP2Hold"); }
        @Override public boolean isPersistent() { return true; }
        @Override public boolean participatesInRomWorldTransitionOffset() { return false; }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
        @Override public void update(int vIntRunCount,PlayableEntity ignored) {
            if(!(services().playerQuery().nativeP2OrNull() instanceof AbstractPlayableSprite p2)) {
                ObjectLifetimeOps.deleteNoRespawn(this); return;
            }
            var cpu=p2.getCpuController();
            if(!initialized) { initialized=true; p2.setControlLocked(true); if(cpu!=null) cpu.clearManualControlTimer(); }
            if(!p2.isControlLocked()) {
                if(cpu!=null) cpu.setController2Input(0,0);
                ObjectLifetimeOps.deleteNoRespawn(this); return;
            }
            if(cpu!=null) cpu.clearController2LogicalLatch();
        }
    }
    /** Obj_DecLevStartYGradual / Obj_IncLevEndYGradual independently read live stored bounds. */
    static final class Bounds extends AbstractObjectInstance implements RewindRecreatable {
        private boolean minimum;
        private int acceleration;
        private Bounds(ObjectSpawn spawn) { super(spawn,"DEZTransportCameraY"); }
        Bounds(boolean minimum) { this(new ObjectSpawn(0,0,0,0,0,false,0)); this.minimum=minimum; }
        @Override public Bounds recreateForRewind(RewindRecreateContext context) { return new Bounds(context.spawn()); }
        @Override public boolean isPersistent() { return true; }
        @Override public boolean participatesInRomWorldTransitionOffset() { return false; }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
        @Override public void update(int vIntRunCount,PlayableEntity ignored) {
            var state=(S3kDezZoneRuntimeState)services().zoneRuntimeState(); var camera=services().camera();
            acceleration+=minimum?0x4000:0x8000;
            int next=(short)((minimum?camera.getMinY():camera.getMaxY())+(minimum?-1:1)*(acceleration>>>16));
            int target=minimum?state.cameraStoredMinY():state.cameraStoredMaxY();
            boolean done=minimum?next<=target:next>target;
            if(minimum) camera.setMinY((short)(done?target:next)); else camera.setMaxYCurrent((short)(done?target:next));
            if(done) ObjectLifetimeOps.deleteNoRespawn(this);
        }
    }
}
