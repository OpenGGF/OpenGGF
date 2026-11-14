package com.openggf.game.soniccd.objects.shared;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * What Quartz Quadrant's and Wacky Workbench's tunnel paths ({@code TunnelPathObject}, objects/r5/tunnel_path.asm and
 * objects/r6/tunnel_path.asm) share: an invisible marker that captures a player within a small box, then carries it along
 * a list of points read from the stage's {@code TunnelPaths} directory. Each segment is a straight line: the
 * velocity toward the next point is worked out once with {@code divs.w}, and the player's 16.16 position advances by it
 * until the whole-frame count in the high byte of {@code obj.var_2e} runs out; the player is then snapped to the point.
 *
 * <p>The two routines differ in the capture box, the ground speed, the record layout and the way the ride ends; each
 * subclass states its own. The routine, its index and the segment counters ({@code obj.var_3a}, {@code var_3b},
 * {@code var_2e}) are kept here as plain fields, never as object bytes.
 *
 * <p>Not modelled: the {@code btst #7,time_zone} block that clears the object-state flag while a time warp runs
 * (objects/r5/tunnel_path.asm:4-33); the object manager owns respawn state.
 */
abstract class TunnelPathBase extends CdObject implements ObjectVisualFacing.NonDirectional {
    /** {@code move.w #$91,d0; jsr PlayFmSound} (objects/r5/tunnel_path.asm:129, 140). */
    private static final int SFX_TUNNEL = 0x91;
    /** {@code cmpi.b #$2B,obj.anim_id(a6)}: the give-up animation stops the object (objects/r5/tunnel_path.asm:51-54). */
    private static final int ANIM_GIVE_UP = 0x2B;
    /** {@code move.b #2,obj.anim_id(a6)}: the roll animation. */
    private static final int ANIM_ROLL = 2;
    /** {@code move.w #$78,obj.var_30(a6)}: the hurt timer after a hurt player is carried (objects/r5/tunnel_path.asm:112-113). */
    private static final int HURT_TIMER = 0x78;

    /** {@code obj.routine}: 0 init, 2 waiting for the player, 4 first velocity, 6 carrying. */
    protected int routine;
    /** {@code obj.var_2e}: the frame count of the current segment as 8.8 (its high byte counts down). */
    protected int steps;
    /** {@code obj.var_3a}: the byte offset of the current record. */
    protected int pathIndex;
    /** {@code obj.var_3b}: the byte offset at which the path ends. */
    protected int pathLimit;
    /** {@code obj.var_3c}: the stage-file offset of the first record. */
    protected int pathData;
    /** {@code obj.var_36} and {@code var_38}: the current target as read from the record. */
    protected int targetX;
    protected int targetY;
    /** The player this object holds ({@code obj.var_2c(a6)} bit 0 set by it and not yet cleared). */
    protected boolean holdsPlayer;
    /** Whether this frame ended in {@code CheckObjectDespawn}. */
    private boolean checksDespawn;

    protected TunnelPathBase(ObjectSpawn spawn) {
        super(spawn, "Tunnel path");
    }

    // ---- The two variants' differences ------------------------------------------------------

    /** The catalogue column of the path directory ({@code TunnelPaths}). */
    protected abstract String pathsColumn();

    /** {@code obj.ground_speed(a6)} set on capture: $A00 in Quartz Quadrant, $800 in Wacky Workbench. */
    protected abstract int captureGroundSpeed();

    /** The box half-extents of the capture test (its x and y windows are twice these). */
    protected abstract int boxX();

    protected abstract int boxY();

    /** True when the object runs its routine this frame (Quartz Quadrant gates the reversed forms on the conveyor flag). */
    protected boolean runsRoutine() {
        return true;
    }

    /** {@code obj.var_2c(a6)} as the variant reads it before capturing. */
    protected abstract int playerControl(AbstractPlayableSprite player);

    /** The variant's capture stores beyond the common ones. */
    protected void onCapture(AbstractPlayableSprite player) { }

    /** Releases the player's control bits at the end of the path ({@code clr.b obj.var_2c(a6)}). */
    protected abstract void releaseControl(AbstractPlayableSprite player);

    /** The x added to the record's x ({@code var_36}) when the target is used: 0 in r5, the object's x in r6. */
    protected int recordOriginX() {
        return 0;
    }

    protected int recordOriginY() {
        return 0;
    }

    /** The record size: 4 bytes (x, y) in r5; 6 (x, y, next-index) in r6. */
    protected abstract int recordSize();

    /** Whether Routine 6 and Routine 4 set the roll animation again each frame (r6 does; r5 does not). */
    protected boolean rollsEachFrame() {
        return false;
    }

    /**
     * Routine 6 after a step's count has run out and the player was snapped to the target: advance to the next record, or
     * finish. The variant decides on its joypad branch (r6); the default is the plain next record.
     */
    protected int nextRecordIndex(AbstractPlayableSprite player, int current) {
        int next = (current + recordSize()) & 0xFF;
        return Integer.compareUnsigned(next, pathLimit) < 0 ? next : -1;
    }

    /**
     * Runs the object's routine for this frame in place of the standard ones (Metallic Madness's free-path form, subtype $80,
     * {@code loc_208154}); true when it did. Such a form always ends in {@code CheckObjectDespawn}.
     */
    protected boolean runsAlternativeRoutine(AbstractPlayableSprite player) {
        return false;
    }

    /** True when {@code TunnelPathObject_0_Routine6} skips the despawn test for the frame ({@code addq.l #4,sp} in r6). */
    protected boolean carryingSkipsDespawn() {
        return false;
    }

    // ---- The object --------------------------------------------------------------------------

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        checksDespawn = false;
        if (!(player instanceof AbstractPlayableSprite sprite)) return;
        if (!runsRoutine()) {
            checksDespawn = true;                                  // loc_208A90: jmp CheckObjectDespawn
            return;
        }
        // cmpi.b #$2B,oAnim(a6) / cmpi.b #6,oRoutine(a6): the give-up animation and the dead player stop the object.
        if (sprite.getAnimationId() == ANIM_GIVE_UP || sprite.getDead()) return;
        if (runsAlternativeRoutine(sprite)) {
            checksDespawn = true;
            return;
        }
        switch (routine) {
            case 0 -> {
                init();
                waiting(sprite);                                   // Routine0 falls through into Routine2
            }
            case 2 -> waiting(sprite);
            case 4 -> firstVelocity(sprite);
            default -> {
                carrying(sprite);
                if (carryingSkipsDespawn()) return;                // addq.l #4,sp: back to RunObjects, past the test below
            }
        }
        // cmpi.b #4,obj.routine(a0) / bcc: only an object still waiting reaches CheckObjectDespawn.
        if (routine < 4) checksDespawn = true;
    }

    /** {@code TunnelPathObject_0_Routine0} (objects/r5/tunnel_path.asm:82-95): the path directory entry for the subtype. */
    private void init() {
        priority = 1;
        width = 0x10;
        routine += 2;
        var provider = artProvider();
        var level = cdLevel();
        if (provider == null || level == null) return;
        var image = provider.image();
        int subtype = getSpawn().subtype() & 0x7F;                 // add.w d0,d0; andi.w #$FE,d0
        int directory = SharedObjectCatalogue.offset(level.payload(), pathsColumn());
        int path = directory + (short) image.u16(directory + subtype * 2);
        int header = image.u16(path);
        pathIndex = (header >> 8) & 0xFF;                          // move.w (a2)+,obj.var_3a: index byte, then limit byte
        pathLimit = header & 0xFF;
        pathData = path + 2;
        targetX = image.u16(pathData);
        targetY = image.u16(pathData + 2);
    }

    /** {@code TunnelPathObject_0_Routine2} (objects/r5/tunnel_path.asm:97-130): wait for a free player inside the box, then capture it. */
    protected final void waiting(AbstractPlayableSprite player) {
        int dx = ((player.getCentreX() & 0xFFFF) - (x & 0xFFFF) + boxX()) & 0xFFFF;
        if (dx >= 2 * boxX()) return;
        int dy = ((player.getCentreY() & 0xFFFF) - (y & 0xFFFF) + boxY()) & 0xFFFF;
        if (dy >= 2 * boxY()) return;
        if (playerControl(player) != 0) return;                    // tst.b obj.var_2c(a6); bne
        if (player.isHurt()) {                                     // cmpi.b #4,oRoutine(a6): subq.b #2; var_30 = $78
            player.setHurt(false);
            player.setInvulnerableFrames(HURT_TIMER);
        }
        routine += 2;
        player.setObjectControlled(true);                          // move.b #$81,obj.var_2c(a6): bit 0 (bit 7 is not modelled)
        holdsPlayer = true;
        onCapture(player);
        player.setAnimationId(ANIM_ROLL);
        player.setGSpeed((short) captureGroundSpeed());
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setPushing(false);                                  // bclr #5,obj.flags(a6)
        player.setAir(true);                                       // bset #1,obj.flags(a6)
        NativePositionOps.writeXPosPreserveSubpixel(player, x);
        NativePositionOps.writeYPosPreserveSubpixel(player, y);
        services().playSfx(SFX_TUNNEL);
    }

    /** {@code TunnelPathObject_0_Routine4} (objects/r5/tunnel_path.asm:136-141): the first segment's velocity. */
    private void firstVelocity(AbstractPlayableSprite player) {
        if (rollsEachFrame()) player.setAnimationId(ANIM_ROLL);
        computeVelocity(player);
        routine += 2;
        services().playSfx(SFX_TUNNEL);
    }

    /** {@code TunnelPathObject_0_Routine6} (objects/r5/tunnel_path.asm:145-185): carry the player along the segment, snap at its end. */
    protected final void carrying(AbstractPlayableSprite player) {
        if (rollsEachFrame()) player.setAnimationId(ANIM_ROLL);
        int count = (((steps >> 8) - 1) & 0xFF);                   // subq.b #1,obj.var_2e(a0): the high byte
        steps = (count << 8) | (steps & 0xFF);
        if (count < 0x80) {                                        // bpl: still moving
            NativePositionOps.addXPos16_16(player, player.getXSpeed() << 8);
            NativePositionOps.addYPos16_16(player, player.getYSpeed() << 8);
            afterMove(player);
            return;
        }
        NativePositionOps.writeXPosPreserveSubpixel(player, (targetX + recordOriginX()) & 0xFFFF);
        NativePositionOps.writeYPosPreserveSubpixel(player, (targetY + recordOriginY()) & 0xFFFF);
        loadNext(player, nextRecordIndex(player, pathIndex));
    }

    /** The variant's per-frame work after a plain step (r6 tests the joypad for a branch there). */
    protected void afterMove(AbstractPlayableSprite player) { }

    /** Loads the record at {@code index} and works out the velocity toward it, or ends the ride when {@code index} is -1. */
    protected final void loadNext(AbstractPlayableSprite player, int index) {
        if (index < 0) {
            finish(player);
            return;
        }
        pathIndex = index;
        var image = artProvider().image();
        targetX = image.u16(pathData + index);
        targetY = image.u16(pathData + index + 2);
        computeVelocity(player);
    }

    /** {@code loc_208BDE} (objects/r5/tunnel_path.asm:186-192): wrap the Y, free the player, and go back to the initial routine. */
    private void finish(AbstractPlayableSprite player) {
        NativePositionOps.writeYPosPreserveSubpixel(player, player.getCentreY() & 0x7FF);   // andi.w #$7FF,obj.y(a6)
        // clr.b obj.routine(a0); clr.b obj.var_2c(a6); move.w #2,obj.routine(a0): the word store leaves the routine byte 0
        // (the 2 lands in the next byte), so the object initialises again and waits for the player.
        routine = 0;
        holdsPlayer = false;
        player.setObjectControlled(false);
        releaseControl(player);
    }

    // ---- sub_208BF4: the velocity toward the target ---------------------------------------------------------

    /**
     * {@code sub_208BF4} (objects/r5/tunnel_path.asm:195-265) / {@code TunnelPathComputePlayerVelocity} (objects/r6/tunnel_path.asm:232-308): the speed is {@code ground_speed} along the longer axis, the
     * other axis gets the quotient, {@code obj.var_2e} the frame count as 8.8. Division is {@code divs.w}: a quotient that does not fit
     * in 16 bits leaves the dividend in place.
     */
    protected final void computeVelocity(AbstractPlayableSprite player) {
        int speed = (short) player.getGSpeed();
        int d2 = speed;
        int d3 = speed;
        int px = player.getCentreX() & 0xFFFF;
        int py = player.getCentreY() & 0xFFFF;
        int tx = (targetX + recordOriginX()) & 0xFFFF;
        int ty = (targetY + recordOriginY()) & 0xFFFF;
        int d0 = (tx - px) & 0xFFFF;
        if (!greaterOrEqualAfterSub(tx, px)) {                     // sub.w; bge.s
            d0 = (-d0) & 0xFFFF;
            d2 = (short) -d2;
        }
        int d1 = (ty - py) & 0xFFFF;
        if (!greaterOrEqualAfterSub(ty, py)) {
            d1 = (-d1) & 0xFFFF;
            d3 = (short) -d3;
        }
        if (d1 >= d0) {                                            // cmp.w d0,d1; bcs.s (not taken): the vertical axis is longer
            int quotient = divs((short) ((ty - py) & 0xFFFF) << 16, d3);
            int dx = (tx - px) & 0xFFFF;
            int xSpeed = 0;                                        // beq.s: d0 stays 0
            if (dx != 0) xSpeed = divs((short) dx << 16, (short) quotient);
            player.setXSpeed((short) xSpeed);
            player.setYSpeed((short) d3);
            if ((short) quotient < 0) quotient = (-quotient) & 0xFFFF;   // tst.w d1; bpl.s; neg.w d1
            steps = quotient & 0xFFFF;
        } else {
            int horizontal = divs((short) ((tx - px) & 0xFFFF) << 16, d2);
            int dy = (ty - py) & 0xFFFF;
            int ySpeed = 0;
            if (dy != 0) ySpeed = divs((short) dy << 16, (short) horizontal);
            player.setYSpeed((short) ySpeed);
            player.setXSpeed((short) d2);
            if ((short) horizontal < 0) horizontal = (-horizontal) & 0xFFFF;
            steps = horizontal & 0xFFFF;
        }
    }

    /** {@code bge} after {@code sub.w src,Dn}: N equals V for {@code dn - src}. */
    private static boolean greaterOrEqualAfterSub(int dn, int src) {
        short a = (short) dn;
        short b = (short) src;
        short result = (short) (a - b);
        boolean negative = result < 0;
        boolean overflow = ((a ^ b) & (a ^ result) & 0x8000) != 0;
        return negative == overflow;
    }

    /**
     * {@code divs.w divisor,Dn}: the low word of the result is the signed quotient (truncated toward zero), the high word the remainder. A
     * quotient outside -32768..32767 sets V and leaves the register unchanged.
     */
    private static int divs(int dividend, int divisor) {
        short word = (short) divisor;
        if (word == 0) throw new ArithmeticException("divs.w by zero in the tunnel path velocity");
        int quotient = dividend / word;
        if (quotient < Short.MIN_VALUE || quotient > Short.MAX_VALUE) return dividend;
        int remainder = dividend % word;
        return (remainder << 16) | (quotient & 0xFFFF);
    }

    // ---- Lifetime --------------------------------------------------------------------------------------

    @Override protected boolean despawnsByCheckObjDespawn() { return checksDespawn; }

    /** Clears the control this object still holds (the player-only half of its own release). */
    @Override
    public void releasePlayersForVisualDirection() {
        if (!holdsPlayer) return;
        holdsPlayer = false;
        if (services().camera().getFocusedSprite() instanceof AbstractPlayableSprite sprite) {
            sprite.setObjectControlled(false);
            releaseControl(sprite);
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // No DrawObject call: the marker is never drawn (objects/r5/tunnel_path.asm:51-70).
    }
}
