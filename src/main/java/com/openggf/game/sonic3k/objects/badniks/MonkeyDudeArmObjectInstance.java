package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.NativeSstSlotAccess;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.physics.TrigLookupTable;
import java.util.List;

/** Native MonkeyDudeArm_Init/Root/Follower/HeldCoconut; five independent SST dispatch owners. */
public final class MonkeyDudeArmObjectInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable {
    private static final int PLAYER_X_RANGE=0x80; // MonkeyDudeArm_CheckPlayerRange
    private enum ArmRootPhase {
        INITIAL_SWING, RANDOM_SWING, ATTACK_WINDUP, ATTACK_SWING, ATTACK_RETURN, POST_ATTACK_SWING
    }
    // parent3, $3E and $44 are physical SST addresses, not Java object identity:
    // a killed body is overwritten by Obj_Explosion in the same native slot.
    private int predecessorSlot=-1;
    private int secondFollowerSlot=-1;
    private int rootSlot=-1;
    private int xFixed,yFixed;
    private boolean initPending=true;
    private boolean nativeRetirementPending;
    private int mappingFrame=3, priorityBucket=6;
    private int followerRoutine,delayPeriod,delayTimer;
    private int romArmRootAngle,romArmAngleStep=1,armRandomWaitTimer;
    private ArmRootPhase armRootPhase=ArmRootPhase.INITIAL_SWING;
    private boolean armAttackFlag,armReleaseEnabled,coconutThrown;
    private boolean initialFacingLeft;

    /** Pure spawn shell; captured numeric native links restore without allocating children. */
    public MonkeyDudeArmObjectInstance(ObjectSpawn spawn) {
        super(spawn,"MonkeyDudeArm");
        xFixed=spawn.x()<<16;yFixed=spawn.y()<<16;
        initialFacingLeft=(spawn.renderFlags()&1)==0;
    }
    MonkeyDudeArmObjectInstance(ObjectSpawn spawn,int predecessorSlot) {
        this(spawn);this.predecessorSlot=predecessorSlot;
    }

    @Override public void update(int vIntRunCount,PlayableEntity main) {
        if (nativeRetirementPending) {
            ObjectLifetimeOps.deleteNoRespawn(this);
            return;
        }
        if (initPending) {
            initPending=false;
            delayPeriod=(spawn.subtype()*2)&0xFF;
            delayTimer=delayPeriod;
            if (spawn.subtype()==0) {
                xFixed+=((initialFacingLeft?-0xE:0xE)<<16);
                yFixed-=2<<16;
            } else if (spawn.subtype()==8) {
                mappingFrame=6;
                secondFollowerSlot=armAt(predecessorSlot).predecessorSlot;
                rootSlot=armAt(armAt(secondFollowerSlot).predecessorSlot).predecessorSlot;
            }
        } else if (spawn.subtype()==0) {
            Header body=header(predecessorSlot);
            yFixed=((body.yFixed()>>16)-(body.mappingFrame()==0?2:4))<<16|(yFixed&0xFFFF);
            PlayableEntity target=main;
            PlayableEntity second=services().playerQuery().nativeP2OrNull();
            if (second!=null && (target==null
                    || Math.abs((short)((xFixed>>16)-second.getCentreX()))
                    <Math.abs((short)((xFixed>>16)-target.getCentreX()))))target=second;
            updateArmRootRoutine(target);
        } else {
            Header predecessor=header(predecessorSlot);
            priorityBucket=predecessor.priority();
            if (followerRoutine==0 && predecessor.attack()) {
                followerRoutine=2;armAttackFlag=true;
                delayPeriod=(delayPeriod>>>1)&0xFF;delayTimer=1;
            } else if (followerRoutine==2) {
                if (spawn.subtype()==8 && !coconutThrown && header(rootSlot).release()) {
                    int angle=header(secondFollowerSlot).angle();
                    if (initialFacingLeft?angle>=0x7C:angle<0x84)throwCoconut();
                }
                if (!predecessor.attack()) {
                    followerRoutine=4;
                    delayPeriod=(delayPeriod*2)&0xFF;delayTimer=1;
                }
            }
            delayTimer=(delayTimer-1)&0xFF;
            if (delayTimer==0) {
                delayTimer=delayPeriod;
                romArmRootAngle=predecessor.angle();
            }
            // MoveSprite_CircularSimple: GetSineCosine/ASR.L #5 retains all
            // fractional words while adding each predecessor's 16.16 position.
            xFixed=predecessor.xFixed()+(TrigLookupTable.sinHex(romArmRootAngle)<<11);
            yFixed=predecessor.yFixed()+(TrigLookupTable.cosHex(romArmRootAngle)<<11);
        }
        // Child_Draw_Sprite reads the actual preceding physical record's byte2A
        // after the routine. Go_Delete_Sprite marks this record, clearing next pass.
        if (header(predecessorSlot).retiring())nativeRetirementPending=true;
    }

    private void throwCoconut() {
        coconutThrown=true;mappingFrame=4;
        services().playSfx(Sonic3kSfx.MISSILE_THROW.id);
        int vx=initialFacingLeft?-0x200:0x200;
        // CreateChild2_Complex copies the hand's PRE-move word position and
        // allocates after this hand, so a higher child runs later in this pass.
        spawnChild(() -> new S3kBadnikProjectileInstance(spawn,Sonic3kObjectArtKeys.MONKEY_DUDE,
                6,xFixed>>16,yFixed>>16,vx,-0x400,0x20,0x18,5,vx>0));
    }

    private MonkeyDudeArmObjectInstance armAt(int slot) {
        var owner=NativeSstSlotAccess.currentOwner(services().objectManager(),slot);
        if (owner instanceof MonkeyDudeArmObjectInstance arm)return arm;
        throw new IllegalStateException("MonkeyDude native arm link has no verified field owner at SST"+slot);
    }
    private record Header(int xFixed,int yFixed,int mappingFrame,int priority,int angle,
                          boolean attack,boolean release,boolean retiring) { }
    private Header header(int slot) {
        if (slot<0)throw new IllegalStateException("Uninitialized MonkeyDude native SST link");
        var owner=NativeSstSlotAccess.currentOwner(services().objectManager(),slot);
        if (owner==null)return new Header(0,0,0,0,0,false,false,false);
        if (owner instanceof MonkeyDudeArmObjectInstance arm)
            return new Header(arm.xFixed,arm.yFixed,arm.mappingFrame,arm.priorityBucket,
                    arm.romArmRootAngle,arm.armAttackFlag,arm.armReleaseEnabled,arm.nativeRetirementPending);
        if (owner instanceof MonkeyDudeBadnikInstance body)
            return new Header(body.getX()<<16,body.getY()<<16,body.nativeMappingFrame(),
                    body.getPriorityBucket(),0,false,false,body.nativeRetirementPending());
        if (owner instanceof MonkeyDudeExplosionObjectInstance explosion)
            return new Header(explosion.getX()<<16,explosion.getY()<<16,explosion.nativeMappingFrame(),
                    explosion.getPriorityBucket(),0,false,false,true);
        throw new IllegalStateException("Unknown MonkeyDude native field owner at SST"+slot+": "+owner.getClass().getName());
    }

    private void updateArmRootRoutine(PlayableEntity targetEntity) {
        boolean mirrored = !initialFacingLeft;
        switch (armRootPhase) {
            case INITIAL_SWING -> {
                int next = addAngle(romArmRootAngle, mirrored ? 4 : -4);
                if ((mirrored && next >= 0x80) || (!mirrored && next <= 0x80)) {
                    // loc_87316 branches before writing $3C: retain $7C/$84.
                    armRootPhase = ArmRootPhase.RANDOM_SWING;
                    resetRandomArmWait();
                } else {
                    romArmRootAngle = next;
                }
            }
            case RANDOM_SWING -> {
                if (armTargetIsOnNativeSide(targetEntity, mirrored)) {
                    armRootPhase = ArmRootPhase.ATTACK_WINDUP;
                    armAttackFlag = true;
                } else {
                    updateRandomArmSwing(mirrored);
                }
            }
            case ATTACK_WINDUP -> {
                romArmRootAngle = addAngle(romArmRootAngle, mirrored ? 4 : -4);
                if ((mirrored && romArmRootAngle >= 0xC0)
                        || (!mirrored && romArmRootAngle <= 0x40)) {
                    armRootPhase = ArmRootPhase.ATTACK_SWING;
                    armReleaseEnabled = true;
                }
            }
            case ATTACK_SWING -> {
                romArmRootAngle = addAngle(romArmRootAngle, mirrored ? -8 : 8);
                if ((mirrored && romArmRootAngle <= 0x60)
                        || (!mirrored && romArmRootAngle >= 0xA0)) {
                    armRootPhase = ArmRootPhase.ATTACK_RETURN;
                    romArmRootAngle = mirrored ? 0x60 : 0xA0;
                    armAttackFlag = false;
                }
            }
            case ATTACK_RETURN -> {
                romArmRootAngle = addAngle(romArmRootAngle, mirrored ? 2 : -2);
                if ((mirrored && romArmRootAngle >= 0x80)
                        || (!mirrored && romArmRootAngle <= 0x80)) {
                    armRootPhase = ArmRootPhase.POST_ATTACK_SWING;
                    romArmRootAngle = 0x80;
                    resetRandomArmWait();
                }
            }
            case POST_ATTACK_SWING -> updateRandomArmSwing(mirrored);
        }
    }

    private boolean armTargetIsOnNativeSide(PlayableEntity target, boolean mirrored) {
        if (target == null) return false;
        // Find_SonicTails d2 is absolute horizontal distance from the root
        // child, not vertical distance. Equality belongs to the left side.
        int dx = (short) (target.getCentreX() - (xFixed >> 16));
        return Math.abs(dx) < PLAYER_X_RANGE && (mirrored ? dx > 0 : dx <= 0);
    }

    private void updateRandomArmSwing(boolean mirrored) {
        romArmRootAngle = addAngle(romArmRootAngle, romArmAngleStep);
        int rangeRelative = addAngle(romArmRootAngle, mirrored ? -0x20 : -0x80);
        if (rangeRelative >= 0x60) {
            romArmAngleStep = -romArmAngleStep;
            romArmRootAngle = addAngle(romArmRootAngle, romArmAngleStep);
        }
        armRandomWaitTimer--;
        if (armRandomWaitTimer < 0) {
            resetRandomArmWait();
        }
    }

    private void resetRandomArmWait() {
        services().rng().nextRaw();
        // 68k move.w (RNG_seed).w reads the first (high) word of the
        // big-endian longword stored by Random_Number.
        int seedWord = (int) (services().rng().getSeed() >>> 16) & 0xFFFF;
        romArmAngleStep = (seedWord & 1) == 0 ? 1 : -1;
        armRandomWaitTimer = seedWord & 0x3C;
    }

    private static int addAngle(int angle, int delta) {
        return (angle + delta) & 0xFF;
    }


    @Override public int getX() { return xFixed>>16; }
    @Override public int getY() { return yFixed>>16; }
    @Override public int getPriorityBucket() { return RenderPriority.clamp(priorityBucket); }
    @Override public boolean isPersistent() { return true; }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed() || nativeRetirementPending)return;
        var render=services().renderManager();
        if (render==null)return;
        var renderer=render.getRenderer(Sonic3kObjectArtKeys.MONKEY_DUDE);
        if (renderer!=null && renderer.isReady())
            // SetUp_ObjAttributes3 sets bit2 only: $38 bit3 changes arm motion,
            // not the independently cleared render_flags bit0 of this child.
            renderer.drawFrameIndex(mappingFrame,getX(),getY(),false,false);
    }
}
