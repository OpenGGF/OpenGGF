package com.openggf.game.soniccd.objects.wwz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.physics.Direction;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Launcher, object $04 ({@code ObjLauncher}, Level/Wacky Workbench/Objects/Launcher/Main.asm:12-144): a
 * top-solid $1C-wide car that takes the player over when stood on, rides $390 pixels right at
 * $C00 with the player captured, lets go on a jump press (or at the end of the run) and
 * returns at four pixels a frame. Init allocates a second object with the same ID
 * ({@code FindNextObjSlot}) that is the car's solid side: a $0-wide block 24 left and 16 above
 * the car while the car is still waiting (routine 8, :131-144). Act 3's index leaves $04 to
 * {@code NullObject} (r6/object_index_3.asm:7).
 *
 * <p>The player's captured state is {@code oPlayerCtrl} bit 0 (object control); the launch
 * animation is $3A. Positions are 16.16: {@code oX} moves by {@code oXVel << 8}.
 */
public final class ObjLauncher extends WwzObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    /** Routines, as {@code oRoutine} (Launcher/Main.asm:22-27). */
    private static final int INIT = 0, MAIN = 2, LAUNCH = 4, REVERT = 6, SOLID_SIDE = 8;
    /** {@code move.w #$C00,oXVel(a0)} (Launcher/Main.asm:63). */
    private static final int LAUNCH_SPEED = 0xC00;
    /** {@code addi.w #912,d0}: the run's length (Launcher/Main.asm:98-99). */
    private static final int RUN_LENGTH = 912;
    /** {@code move.b #$3A,oAnim(a1)} (Launcher/Main.asm:61). */
    private static final int LAUNCH_ANIMATION = 0x3A;
    /** {@code move.w #-$680,oYVel(a1)} (Launcher/Main.asm:86). */
    private static final int JUMP_SPEED = -0x680;
    /** {@code move.b #2,oAnim(a1)}: SonAni_Roll (Launcher/Main.asm:93). */
    private static final int ROLL_ANIMATION = 2;

    private int xVel;
    /** {@code oPlayerCtrl} bit 0 set by this car and not yet cleared. */
    private boolean holdsPlayer;
    private int launchX;
    /** {@code oLaunchParent}: found again by the car's layout spawn, since the car may range-unload before its side. */
    @RewindTransient(reason = "Structural parent link; relinked from the parent's spawn by parent()")
    private ObjLauncher parent;
    private int parentSpawnX;
    private int parentSpawnY;

    public ObjLauncher(ObjectSpawn spawn) {
        super(spawn, "Launcher");
    }

    /** The solid side {@code ObjLauncher_Init} allocates (Launcher/Main.asm:41-50). */
    private ObjLauncher(ObjectSpawn spawn, ObjLauncher parent) {
        super(spawn, "LauncherSide");
        this.parent = parent;
        this.parentSpawnX = parent.getSpawn().x();
        this.parentSpawnY = parent.getSpawn().y();
        routine = SOLID_SIDE;
        spriteFlip = 0;                          // ori.b #4,oSprFlags(a1): a cleared slot has no flips
        mapFrame = 1;
        yRadius = 12;
        // oWidth is never written for the side: the slot was cleared (Launcher/Main.asm:41-50).
        width = 0;
        priority = 0;
        tileWord = WwzObjectArt.LAUNCHER_TILE;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (WwzStage.nullObject(services(), 0x04)) {        // r6/object_index_3.asm:7
            setDestroyed(true);
            return;
        }
        AbstractPlayableSprite sprite = sprite(player);
        switch (routine) {
            case INIT -> {
                init();
                main(sprite);
            }
            case MAIN -> main(sprite);
            case LAUNCH -> launch(sprite);
            case REVERT -> revert();
            case SOLID_SIDE -> solidSide();
            default -> { }
        }
    }

    /** ObjLauncher_Init (Launcher/Main.asm:31-50); it falls through into ObjLauncher_Main. */
    private void init() {
        tileWord = WwzObjectArt.LAUNCHER_TILE;
        launchX = x;
        yRadius = 4;
        width = 28;
        routine = MAIN;
        var objects = services().objectManager();
        if (objects != null) {
            ObjLauncher side = new ObjLauncher(new ObjectSpawn(x, y, 0x04, 0, 0, false, 0), this);
            objects.addDynamicObjectAfterCurrent(side);
        }
    }

    /** ObjLauncher_Main (Launcher/Main.asm:54-66). */
    private void main(AbstractPlayableSprite player) {
        if (player == null) return;
        if (!solidObject(Entry.TOP)) return;                 // lea objPlayerSlot,a1 / jsr TopSolidObject / beq.s .End
        player.setObjectControlled(true);                    // bset #0,oPlayerCtrl(a1)
        player.setObjectControlAllowsCpu(true);              // bit 0 only: oPlayerCtrl's sign stays clear
        holdsPlayer = true;
        NativePositionOps.writeXPosPreserveSubpixel(player, x);   // move.w oX(a0),oX(a1)
        player.setDirection(Direction.RIGHT);                // bclr #0,oFlags(a1)
        player.setAnimationId(LAUNCH_ANIMATION);             // move.b #$3A,oAnim(a1)
        routine = LAUNCH;
        xVel = LAUNCH_SPEED;
    }

    /** ObjLauncher_Launch (Launcher/Main.asm:70-114). */
    private void launch(AbstractPlayableSprite player) {
        addToX(xVel << 8);                                   // ext.l / asl.l #8 / add.l d0,oX
        if (player == null) return;
        solidObject(Entry.TOP);
        if (player.isJumpJustPressed()) {                    // andi.b #$70,d0 on p1CtrlData's tap byte
            player.setObjectControlled(false);               // bclr #0,oPlayerCtrl(a1)
            holdsPlayer = false;
            player.setYSpeed((short) JUMP_SPEED);
            player.setXSpeed((short) xVel);
            startRollingJump(player);
            services().playSfx(WwzSounds.JUMP);              // FM_JUMP
        }
        int end = (launchX + RUN_LENGTH) & 0xFFFF;
        if (end >= (x & 0xFFFF)) return;                     // cmp.w oX(a0),d0 / bcc.s .End
        x = end;                                             // move.w d0,oX(a0)
        routine = REVERT;
        if (!stoodOn(player)) return;                        // btst #3,oFlags(a0) / beq.s .End
        player.setObjectControlled(false);                   // bclr #0,oPlayerCtrl(a1)
        holdsPlayer = false;
        player.setXSpeed((short) xVel);
        player.setAnimationId(0);                            // move.b #0,oAnim(a1)
        player.setAir(true);                                 // bset #1,oFlags(a1)
        player.setOnObject(false);                           // bclr #3,oFlags(a1)
    }

    /**
     * {@code move.b #$E,oYRadius(a1) / move.b #7,oXRadius(a1) / addq.w #5,oY(a1) / bset #2,oFlags(a1) /
     * bclr #5,oFlags(a1) / move.b #2,oAnim(a1)} (Launcher/Main.asm:88-93); the radii and the five
     * pixels are written whether or not the player was already rolling.
     */
    static void startRollingJump(AbstractPlayableSprite player) {
        short centreX = player.getCentreX();
        short centreY = player.getCentreY();
        player.setRolling(true);
        player.applyRollingRadii(false);
        NativePositionOps.writeXPosPreserveSubpixel(player, centreX);
        NativePositionOps.writeYPosPreserveSubpixel(player, centreY + 5);
        player.setPushing(false);
        player.setAnimationId(ROLL_ANIMATION);
    }

    /** ObjLauncher_Revert (Launcher/Main.asm:118-127). */
    private void revert() {
        x = (x - 4) & 0xFFFF;                                // subq.w #4,oX(a0)
        if (launchX < (x & 0xFFFF)) return;                  // cmp.w oX(a0),d0 / bcs.s .End
        x = launchX;
        routine = MAIN;
    }

    /** ObjLauncher_SolidSide (Launcher/Main.asm:131-144). */
    private void solidSide() {
        ObjLauncher car = parent();
        if (car == null) {                                   // the car is gone: its slot no longer holds the launcher
            setDestroyed(true);
            return;
        }
        if (car.routine >= LAUNCH) return;                   // cmpi.b #4,oRoutine(a1) / bcc.s .End
        x = (car.x - 24) & 0xFFFF;
        y = (car.y - 16) & 0xFFFF;
        solidObject(Entry.SOLID);
    }

    private ObjLauncher parent() {
        if (parent != null && !parent.isDestroyed()) return parent;
        parent = null;
        var objects = services().objectManager();
        if (objects == null) return null;
        for (ObjLauncher car : objects.activeObjectsOfType(ObjLauncher.class)) {
            if (car.routine != SOLID_SIDE && !car.isDestroyed() && car.getSpawn().x() == parentSpawnX
                    && car.getSpawn().y() == parentSpawnY) {
                parent = car;
                break;
            }
        }
        return parent;
    }

    /** Clears the control this object still holds (the player-only half of its own release). */
    @Override
    public void releasePlayersForVisualDirection() {
        if (!holdsPlayer) return;
        holdsPlayer = false;
        if (services().camera().getFocusedSprite() instanceof AbstractPlayableSprite sprite) sprite.setObjectControlled(false);
    }

    @Override public int solidXVelocity() { return routine == SOLID_SIDE ? 0 : xVel; }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override
    protected String sheetKey() {
        return WwzObjectArt.key(WwzObjectArt.LAUNCHER);
    }

    public int xVelocity() { return xVel; }
}
