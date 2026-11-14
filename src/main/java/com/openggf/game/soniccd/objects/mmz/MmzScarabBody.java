package com.openggf.game.soniccd.objects.mmz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.timetravel.CdTimeTravel;
import com.openggf.game.soniccd.zones.mmz.MmzStageSignals;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.CameraRelativeDraw;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.physics.Direction;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * The scarab beetle ({@code ScarabObject} with {@code oSubtype} 0 or 1) at loc_20DE66 (objects/r8/scarab.asm:195-340
 * and ScarabObject_2_Routine0..4, :349-473): it walks $40 pixels either way of where it started along the floor (a
 * speed of $6000 for kind 0, $3000 for kind 1), carrying the time monitor on its back, for 60 calls and pauses for 30
 * ({@code oVar34}). Touched from the side it faces ({@code oColType} $F0, which the player's touch response sends to
 * loc_206CD8 in r8/player_object_collide.asm) it grabs the player for 120 calls, carrying them, then throws them out
 * ahead ($A00 or $500) and rests 30 calls; touched from behind it is an ordinary enemy (a rolling or invincible player
 * destroys it, anyone else is hurt). The player's control hold is {@link MmzStageSignals#revolveHold()} (the
 * {@code oVar2C} bit 0 the Revolve Door also uses).
 */
@CameraRelativeDraw(CameraRelativeDraw.Use.CULL_ONLY)
public final class MmzScarabBody extends MmzBadnik implements TouchResponseListener {
    private final MmzScarab spawner;             // oVar2A
    /** The slot of {@code spawner} the native child stores and tests each turn ({@code cmpi.b #$2F,obj.id(a1)}). */
    private int spawnerSlot = -1;
    private final int dir;                       // oSubtype2: 0, or $FF for a beetle that faces and throws right
    private final int kind;                      // oSubtype
    private MmzScarabShell shell;                // oVar3C
    private int anchorX;                         // oVar2C
    private int speed;                           // oVar2E, 16.16
    private int pause;                           // oVar34
    private int holdTimer;                       // oVar36
    private int phase;                           // oRoutine2
    private int collideStatus;                   // oColStatus
    private boolean grabbed;                     // oVar32
    private boolean killed;

    MmzScarabBody(ObjectSpawn spawn, MmzScarab spawner, int dir) {
        super(spawn, "ScarabBody");
        this.spawner = spawner;
        this.spawnerSlot = spawner == null ? -1 : spawner.getSlotIndex();
        this.dir = dir;
        this.kind = spawn.subtype() & 1;
    }

    @Override
    protected void run(PlayableEntity player) {
        if (!slotHoldsId(spawnerSlot, 0x2F)) {                                 // cmpi.b #$2F,oID(a1); bne DeleteObject
            setDestroyed(true);
            return;
        }
        if (routine == 0) init();
        step(player);
    }

    /** ScarabObject_1_Routine0 (objects/r8/scarab.asm:225-271). */
    private void init() {
        routine = 2;
        initialised = true;
        width = 0x20;                                                // oWidth $20 (and oXRadius $20)
        yRadius = 0xB;
        colType = 0xF0;
        priority = 1;
        tileWord = MmzObjectArt.SCARAB_TILE;
        anchorX = x;
        pause = 0x3C;
        y = (y + floorDistance()) & 0xFFFF;                          // jsr CheckBlockDown; add.w d1,oY
        var objects = services().objectManager();
        if (objects != null) {
            ObjectSpawn s = new ObjectSpawn(x, y, spawn.objectId(), 0, 0, false, 0);
            shell = new MmzScarabShell(s, this, dir != 0 ? 1 : 0);
            objects.addDynamicObjectAfterCurrent(shell);              // jsr SpawnObjectAfter
        }
        if (dir != 0) {                                              // bset #0,oSprFlags and oFlags on both
            statusFlip |= 1;
            spriteFlip |= 1;
        }
        animator = mmzAnimator(kind == 0 ? "scarabAnimations1" : "scarabAnimations2", 0);
        speed = kind == 0 ? 0x6000 : 0x3000;
    }

    /** ScarabObject_1_Routine2 (objects/r8/scarab.asm:272-340). */
    private void step(PlayableEntity player) {
        int oldX = x;
        int oldY = y;
        if ((short) pause >= 0) {                                    // tst.w oVar34; bmi
            addToX(speed);
            int d0 = (short) (x - anchorX);
            if (Math.abs(d0) >= 0x40) reverse();                     // cmpi.w #$40,d0; blt
            int d1 = floorDistance();
            if (((d1 + 7) & 0xFFFF) < 0xE) {                         // addq.w #7; subi.w #$E; bcs loc_20DFCA
                y = (y + d1) & 0xFFFF;
            } else {
                reverse();
            }
        }
        int d6 = (short) (oldX - x);                                 // loc_20DFCE
        int d5 = (short) (oldY - y);
        MmzScarabItem item = spawner.item();
        if (item != null) item.carry(-d6, -d5);                      // sub.w d6,oX(a1); sub.w d5,oY(a1); oXVel = -d6 << 8
        if (grabbed && player instanceof AbstractPlayableSprite sprite) {
            NativePositionOps.addXPosPreserveSubpixel(sprite, -d6);  // sub.w d6,oX; sub.w d5,oY of the player
            NativePositionOps.addYPosPreserveSubpixel(sprite, -d5);
        }
        switch (phase) {
            case 0 -> waiting(player);
            case 2 -> holding(player);
            case 4 -> resting();
            default -> { }
        }
        if (shell != null) shell.follow(x, y, mapFrame);             // move.w oX/oY, oMapFrame + 3 to the shell
    }

    /** sub_20E032 (objects/r8/scarab.asm:341-347). */
    private void reverse() {
        addToX(-speed);
        speed = -speed;
    }

    /** ScarabObject_2_Routine0 (objects/r8/scarab.asm:356-392). */
    private void waiting(PlayableEntity player) {
        if (collideStatus != 0) {
            AbstractPlayableSprite sprite = player instanceof AbstractPlayableSprite p ? p : null;
            boolean refuse = sprite == null || sprite.isHurt() || sprite.getDead() || sprite.getInvulnerableFrames() > 0
                    || spawner.item() != null;                       // routine 4/6, oVar30 of the player, oVar30 of the spawner
            if (!refuse) {
                grab(sprite);
                return;
            }
            collideStatus = 0;                                       // loc_20E072
        }
        pauseStep();
    }

    /** loc_20E076..loc_20E08C (objects/r8/scarab.asm:376-392): 60 calls of walking and animation, 30 of rest. */
    private void pauseStep() {
        pause = (pause - 1) & 0xFFFF;
        if ((short) pause >= 0) {
            animate();
            return;
        }
        if ((short) pause <= -30) pause = 60;
    }

    /** loc_20E096..loc_20E0E2 (objects/r8/scarab.asm:394-420). */
    private void grab(AbstractPlayableSprite sprite) {
        grabbed = true;
        MmzStageSignals signals = signals();
        if (signals != null) signals.setRevolveHold(true);           // bset #0,oVar2C(a1)
        sprite.setObjectControlled(true);                            // the object-control bit (objects/player.asm:290)
        sprite.setObjectControlAllowsCpu(true);                      // bit 0 only: oPlayerCtrl's sign stays clear
        sprite.setRolling(true);                                     // bset #2,oFlags(a1); oYRadius $E, oXRadius 7
        sprite.setAnimationId(2);
        NativePositionOps.writeYPosPreserveSubpixel(sprite, y - 3);
        NativePositionOps.writeXPosPreserveSubpixel(sprite, x + (dir != 0 ? 0x18 : -0x18));
        phase += 2;
        holdTimer = 120;
        arm();
    }

    /** loc_20E0E2: {@code move.w #$F000,oColType(a0)}: type $F0, status 0. */
    private void arm() {
        colType = 0xF0;
        collideStatus = 0;
    }

    /** ScarabObject_2_Routine2 (objects/r8/scarab.asm:422-459). */
    private void holding(PlayableEntity player) {
        holdTimer--;
        if (holdTimer != 0) {
            arm();
            pauseStep();
            return;
        }
        if (player instanceof AbstractPlayableSprite sprite) release(sprite);
        phase += 2;
        colType = 0;                                                 // move.w #0,oColType(a0)
        collideStatus = 0;
        mapFrame = 2;
        holdTimer = 30;
    }

    /** loc_20E0F4..loc_20E130 (objects/r8/scarab.asm:430-458): throw the player out ahead. */
    private void release(AbstractPlayableSprite sprite) {
        releaseGrip();
        sprite.setYSpeed((short) 0);
        int xSpeed = kind == 0 ? -0xA00 : -0x500;
        sprite.setDirection(Direction.LEFT);     // bset #0,oFlags(a1)
        if (dir != 0) {
            xSpeed = -xSpeed;
            sprite.setDirection(Direction.RIGHT); // bclr #0,oFlags(a1)
        }
        sprite.setXSpeed((short) xSpeed);
        sprite.setMoveLockTimer(0xF);                                // move.w #$F,oVar3E(a1)
        sprite.setGSpeed((short) xSpeed);
        sprite.setAngle((byte) 0);
        sprite.setPushing(false);                                    // bclr #5,oFlags(a1)
    }

    /** ScarabObject_2_Routine4 (objects/r8/scarab.asm:461-473). */
    private void resting() {
        holdTimer--;
        if (holdTimer != 0) return;
        phase = 0;
        arm();
        animator = mmzAnimator(kind == 0 ? "scarabAnimations1" : "scarabAnimations2", 0);   // move.b #$FF,oPrevAnim
    }

    // ---- the grip, for the spawner and the touch ------------------------------------------------------------

    /** {@code move.b #1,oColStatus(a6)} by the monitor that broke (objects/r8/scarab.asm:567-575). */
    void touchedByMonitor() { collideStatus = 1; }

    public int pauseTimer() { return (short) pause; }
    public int phase() { return phase; }

    boolean grabbed() { return grabbed; }
    boolean killed() { return killed; }

    /** {@code clr.w oVar32(a0); bclr #0,oVar2C(a1)}: the player is let go. */
    void releaseGrip() {
        grabbed = false;
        MmzStageSignals signals = signals();
        if (signals != null) signals.setRevolveHold(false);
        if (services().camera().getFocusedSprite() instanceof AbstractPlayableSprite sprite) sprite.setObjectControlled(false);
    }

    /** Clears the grip this beetle still holds (the player-only half of its release). */
    @Override
    public void releasePlayersForVisualDirection() {
        if (grabbed) releaseGrip();
    }

    /**
     * loc_20DE66 ends every turn with DrawObject then CheckObjectDespawn on the beetle's own X (objects/r8/scarab.asm:
     * 204-213); the check reads the on-screen bit DrawObject has just cleared, so the beetle leaves by range alone and
     * clears the spawner's {@code var_2e} ({@code clr.w obj.var_2e(a1)}).
     */
    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override protected boolean drawsBeforeDespawnCheck() { return true; }

    private boolean despawned;

    /** True once the beetle despawned by its range check (the spawner's pointer to it is clear). */
    boolean despawned() { return despawned; }

    @Override
    public void onUnload() {
        super.onUnload();
        despawned = true;
    }

    /** {@code jsr DeleteOtherObject}. */
    void deleteOther() {
        setDestroyed(true);
    }

    @Override
    protected void becomeExplosion(int pointsIndex, int layer, int explosionSubtype, boolean fromBadnik) {
        killed = true;                                               // the slot's ID is no longer $2F
        super.becomeExplosion(pointsIndex, layer, explosionSubtype, fromBadnik);
    }

    // ---- touch: loc_206CD8 (r8/player_object_collide.asm:loc_206CD8) ---------------------------------------------

    /** Collision $F0 is a $C0 type whose size index $30 the player's touch response sends to loc_206CD8. */
    @Override public boolean usesS3kTouchSpecialPropertyResponse() { return (colType & 0xC0) == 0xC0; }

    @Override
    public void onTouchResponse(PlayableEntity player, TouchResponseResult result, int frameCounter) {
        if (!(player instanceof AbstractPlayableSprite sprite) || isDestroyed()) return;
        int side = (sprite.getCentreX() - x) < 0 ? 0xFF : 0;         // smi.b d0
        if ((side ^ dir) == 0) {                                     // eor.b d0,d1; beq sub_2069E8
            enemy(sprite);
        } else {
            collideStatus = (collideStatus + 1) & 0xFF;              // loc_206C76: addq.b #1,oColStatus(a1)
        }
    }

    /** sub_2069E8 (r8/player_object_collide.asm:sub_2069E8..loc_206A2C): the ordinary enemy touch. */
    private void enemy(AbstractPlayableSprite sprite) {
        var tt = CdTimeTravel.of(services().gameModule());
        boolean safe = (tt != null && tt.warping()) || sprite.getInvincibleFrames() > 0 || sprite.getAnimationId() == 2;
        if (!safe) {                                                 // sub_206AB2: HurtPlayer
            if (sprite.isHurt() || sprite.getDead() || sprite.getInvulnerableFrames() > 0) return;
            boolean hadRings = sprite.getRingCount() > 0;
            if (hadRings && !sprite.hasShield()) services().spawnLostRings(sprite, 0);
            sprite.applyHurtOrDeath(x, true, hadRings);
            return;
        }
        if (collideStatus != 0) {                                    // loc_20A02: bounce off a beetle that has been touched
            sprite.setXSpeed((short) (-sprite.getXSpeed() >> 1));
            sprite.setYSpeed((short) (-sprite.getYSpeed() >> 1));
            colType = 0;
            collideStatus = (collideStatus - 1) & 0xFF;
            return;
        }
        killAsEnemy(sprite, 0, true);
    }

    @Override public int getCollisionFlags() { return initialised ? colType : 0; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!initialised) return;
        drawSheet(MmzObjectArt.SCARAB, mapFrame, (spriteFlip & 1) != 0, false);
    }
}
