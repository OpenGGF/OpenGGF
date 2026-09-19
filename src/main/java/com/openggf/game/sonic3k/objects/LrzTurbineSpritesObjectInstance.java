package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RomObjectCodePointerProvider;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;

import java.util.List;

/**
 * {@code Obj_LRZTurbineSprites} (sonic3k.asm:89611-89846), id {@code $32} in the locked-on set
 * and eighteen of Lava Reef act 2's placements. The {@code S3KL} set spends the id on
 * {@code Obj_AIZDrawBridge}, which is the name the id constant carries.
 *
 * <p><b>One id, two objects.</b> {@code tst.b subtype(a0) / beq.s loc_442D8} (:89615-89616)
 * splits them. A non-zero subtype is the thin one: {@code Map_LRZTurbineSprites2},
 * {@code width_pixels} {@code 4}, {@code collision_flags} {@code $A0}, and a routine
 * ({@code loc_44592}, :89838-89843) that does nothing but spin its four frames on the level clock.
 * Eleven placements. Subtype {@code 0} is the turbine proper: {@code width_pixels} {@code $10} and
 * the whole capture machine below. Seven placements.
 *
 * <p><b>The state is two interleaved triples.</b> {@code lea $30(a0),a2} then {@code addq.w #1,a2}
 * (:89631, :89634) gives player 1 {@code $30}/{@code $32}/{@code $34} and player 2
 * {@code $31}/{@code $33}/{@code $35}: a captured flag, a re-capture cooldown, and the ride angle.
 *
 * <p><b>The capture bands are three, and they are not symmetrical</b> ({@code loc_44448},
 * :89747-89774). Horizontally a player must be within {@code $10} of the turbine's own
 * {@code x_pos}. Vertically the ROM tests {@code y - y_pos + $50 < $18} first -- the band from
 * {@code -$50} to {@code -$39}, entered with {@code d1 = $70} -- then the same window shifted by
 * {@code $40} ({@code -$10} to {@code +7}, {@code d1 = $30}) and once more ({@code +$30} to
 * {@code +$47}, {@code d1 = -$10}). {@code d1} is the ride angle the capture starts from, so
 * which band a player enters by decides where on the loop they join it.
 *
 * <p><b>The middle band is for high-priority players only</b> ({@code cmpi.b #$30,d1 /
 * tst.w art_tile(a1) / bpl}, :89781-89784): a player drawn behind the level tiles -- which is what
 * this object itself puts them behind on the far half of the loop -- is the only one the middle
 * band accepts. That is how the turbine hands a player round its own back without catching them
 * again on the way past.
 *
 * <p><b>The entry angle carries the level clock in it</b> ({@code (Level_frame_counter+1) & 7},
 * minus {@code 4}, times {@code 4}, added to {@code d1}, :89791-89796), so two captures from the
 * same band a frame apart start four steps of the loop apart. The ride itself
 * ({@code sub_4450A}, :89814-89836) reads {@code RawAni_44552} for the player's
 * {@code mapping_frame} and {@code byte_44572} for a signed y offset from the turbine, flips the
 * player behind the tiles for the negative half of the angle byte, and steps the angle by
 * {@code 4} -- sixty-four frames a loop.
 */
public final class LrzTurbineSpritesObjectInstance extends AbstractObjectInstance
        implements TouchResponseProvider, RewindRecreatable, RomObjectCodePointerProvider {

    /** {@code move.w #$200,priority(a0)} (sonic3k.asm:89613). */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x0200);
    /** {@code move.b #$10,width_pixels(a0)} for the turbine (:89626). */
    private static final int TURBINE_HALF_WIDTH = 0x10;
    /** {@code move.b #4,width_pixels(a0)} for the thin variant (:89618). */
    private static final int THIN_HALF_WIDTH = 4;
    /** {@code move.b #$30,height_pixels(a0)}, the same for both (:89619, :89627). */
    private static final int HALF_HEIGHT = 0x30;
    /** {@code move.b #$A0,collision_flags(a0)} on the thin variant only (:89620). */
    private static final int THIN_COLLISION_FLAGS = 0xA0;
    /** {@code move.b #30,2(a2)} on a plain release (:89664). */
    private static final int RELEASE_COOLDOWN = 30;
    /** {@code move.b #20,2(a2)} on a jump release (:89713). */
    private static final int JUMP_COOLDOWN = 20;
    /** {@code muls.w #$C,d0 / neg.w d0} (:89697-89698). */
    private static final int JUMP_VELOCITY_SCALE = 0xC;
    /** {@code addq.b #4,d0} on the angle (:89834). */
    private static final int ANGLE_STEP = 4;
    /** {@code move.w #$100,priority(a1)} (:89666, :89828). */
    private static final int PLAYER_PRIORITY_NEAR = RenderPriority.fromS3kWord(0x0100);
    /** {@code move.w #$280,priority(a1)} behind the tiles (:89832). */
    private static final int PLAYER_PRIORITY_FAR = RenderPriority.fromS3kWord(0x0280);
    /** {@code move.b #$E,y_radius(a1)} / {@code #7,x_radius(a1)} (:89668-89669). */

    /** {@code byte_443B4} (sonic3k.asm:89680-89681): the high nibble's angle base. */
    private static final int[] JUMP_ANGLE_BASE = {
        0x20, 0x20, 0x20, 0x30, 0x40, 0x50, 0x60, 0x60,
        0x60, 0xA0, 0xA0, 0xB0, 0xC0, 0xD0, 0xE0, 0xE0
    };

    /** {@code RawAni_44552} (sonic3k.asm:89838-89840): the rider's {@code mapping_frame}. */
    private static final int[] RIDE_FRAMES = {
        0x95, 0x95, 0x63, 0x63, 0x64, 0x64, 0x64, 0x64,
        0x64, 0x65, 0x65, 0x65, 0x65, 0x66, 0x66, 0x66,
        0x66, 0x66, 0x66, 0x66, 0x67, 0x67, 0x67, 0x67,
        0x68, 0x68, 0x68, 0x68, 0x95, 0x95, 0x95, 0x95
    };

    /** {@code byte_44572} (sonic3k.asm:89841-89843): the rider's signed y offset. */
    private static final int[] RIDE_Y_OFFSETS = {
        0x43, 0x40, 0x38, 0x32, 0x24, 0x1E, 0x17, 0x12,
        0x0E, -0x16, -0x1C, -0x24, -0x29, -0x32, -0x3A, -0x40,
        -0x43, -0x40, -0x3A, -0x32, -0x29, -0x24, -0x1C, -0x10,
        0x00, 0x10, 0x17, 0x1E, 0x24, 0x32, 0x38, 0x40
    };

    /** The three vertical bands of {@code loc_44448}: the offset added before the window test. */
    private static final int[] BAND_Y_BIAS = { 0x50, 0x10, -0x30 };
    /** The entry angle each band starts the ride from ({@code d1}). */
    private static final int[] BAND_ANGLE = { 0x70, 0x30, -0x10 };
    /** {@code cmpi.w #$18,d0 / blo} (:89753). */
    private static final int BAND_HEIGHT = 0x18;
    /** {@code addi.w #$10,d0 / cmpi.w #$20,d0 / bhs} (:89749-89751). */
    private static final int CAPTURE_HALF_WIDTH = 0x10;

    /** {@code tst.b subtype(a0)}: the thin variant is everything but subtype 0. */
    private boolean thinVariant;
    /** ROM {@code mapping_frame(a0)}: {@code ((Level_frame_counter+1) >> 1) & 3} for both. */
    private int mappingFrame;
    /** ROM {@code $30(a0)} and {@code $31(a0)}. */
    private final boolean[] captured = new boolean[2];
    /** ROM {@code $32(a0)} and {@code $33(a0)}. */
    private final int[] cooldown = new int[2];
    /** ROM {@code $34(a0)} and {@code $35(a0)}. */
    private final int[] rideAngle = new int[2];

    private record RewindExtra(boolean thinVariant, int mappingFrame, boolean[] captured,
                               int[] cooldown, int[] rideAngle)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    public LrzTurbineSpritesObjectInstance(ObjectSpawn spawn) {
        super(spawn, "LRZTurbineSprites");
        this.thinVariant = spawn != null && (spawn.subtype() & 0xFF) != 0;
        this.mappingFrame = 0;
    }

    /**
     * {@code Obj_LRZTurbineSprites} sits at ROM {@code $0004429C} (sonic3k.lst); its whole code
     * block lies in one bank, so the high word {@code sub_13EFC} latches is {@code $0004}.
     */
    @Override
    public int romObjectCodePointerHighWord() {
        return 0x0004;
    }

    @Override
    public LrzTurbineSpritesObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return ObjectConstructionContext.construct(ctx.objectServices(),
                () -> new LrzTurbineSpritesObjectInstance(ctx.spawn()));
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        int levelFrame = levelFrameCounterOrFallback(vIntRunCount) & 0xFF;
        if (thinVariant) {
            // loc_44592 (sonic3k.asm:89838-89843): four frames, nothing else.
            mappingFrame = (levelFrame >> 1) & 3;
            return;
        }
        // loc_442F2 (:89631-89636): player 1 from $30, then player 2 from $31.
        processPlayer(0, asSprite(playerEntity), levelFrame);
        processPlayer(1, asSprite(nativeP2OrNull()), levelFrame);

        mappingFrame = (levelFrame >> 1) & 3;
        // tst.b render_flags(a0) / bpl, then andi.b #$F on the clock (:89641-89647).
        if (isWithinSolidContactBounds() && (levelFrame & 0x0F) == 0) {
            playSfx(com.openggf.game.sonic3k.audio.Sonic3kSfx.FAN_BIG.id);
        }
    }

    /** {@code sub_44338} (sonic3k.asm:89650-89812) for one player. */
    private void processPlayer(int slot, AbstractPlayableSprite player, int levelFrame) {
        if (!captured[slot]) {
            // loc_44434 (:89736-89745): the cooldown first, then the capture test.
            if (cooldown[slot] != 0) {
                cooldown[slot]--;
                if (cooldown[slot] == 0 && player != null) {
                    player.setPriorityBucket(PLAYER_PRIORITY_NEAR);
                }
                return;
            }
            tryCapture(slot, player, levelFrame);
            return;
        }
        // The held branch (:89651-89663).
        if (player == null || player.isDebugMode() || player.isHurt() || player.getDead()) {
            releasePlain(slot, player);
            return;
        }
        if (player.isJumpPressed()) {
            // andi.w #button_A_mask|button_B_mask|button_C_mask,d1 (:89657): the low byte of
            // Ctrl_n_logical, so a HELD button, not a press edge.
            releaseByJump(slot, player);
            return;
        }
        advanceRide(slot, player);
    }

    /**
     * {@code loc_4436E} (sonic3k.asm:89665-89678): the drop. No velocity is written at all, so
     * the player simply falls out of the loop rolling.
     */
    private void releasePlain(int slot, AbstractPlayableSprite player) {
        captured[slot] = false;
        cooldown[slot] = RELEASE_COOLDOWN;
        if (player == null) {
            return;
        }
        ObjectControlState.none().applyTo(player);
        player.setObjectMappingFrameControl(false);
        player.setPriorityBucket(PLAYER_PRIORITY_NEAR);
        player.setAir(true);
        player.setJumping(true);
        // move.b #$E,y_radius(a1) / #7,x_radius(a1) is the rolling pair (:89668-89669).
        player.applyRollingRadii(false);
        player.setAnimationId(2);
        player.setRolling(true);
        player.setRollingJump(false);
        player.setFlipAngle(0);
    }

    /**
     * {@code loc_443C4} (sonic3k.asm:89683-89734): the jump out. The launch angle is the ride
     * angle's low nibble on top of {@code byte_443B4}'s base for its high nibble, and the
     * velocity is the sine of that, scaled by {@code $C} and negated -- so the top of the loop
     * throws the player up and the bottom throws them down.
     */
    private void releaseByJump(int slot, AbstractPlayableSprite player) {
        int angle = rideAngle[slot] & 0xFF;
        int launchAngle = (JUMP_ANGLE_BASE[(angle >> 4) & 0x0F] | (angle & 0x0F)) & 0xFF;
        int velocity = (short) -(TrigLookupTable.sinHex(launchAngle) * JUMP_VELOCITY_SCALE);
        player.setYSpeed((short) velocity);
        // move.w #0,anim(a1) writes anim and prev_anim together (:89700).
        player.setAnimationId(0);
        player.publishRunAsPreviousAnimation();
        if (velocity < 0) {
            // move.w #$10<<8,anim(a1) / ori.w #high_priority,art_tile(a1) (:89703-89704): rising
            // out of the turbine uses the dedicated animation and draws in front.
            player.setAnimationId(0x10);
            player.setHighPriority(true);
        }
        captured[slot] = false;
        cooldown[slot] = JUMP_COOLDOWN;
        ObjectControlState.none().applyTo(player);
        player.setObjectMappingFrameControl(false);
        player.setAir(true);
        player.setJumping(false);
        player.setSpindash(false);
        // move.b #2,routine(a1) (:89728) restores the ordinary playing routine, which is the one
        // the player is already in: every path that reaches here has been through cmpi.b #4 /
        // bhs, so the routine is 0 or 2 and the write only normalises it.
        player.setRollingJump(false);
        player.setDoubleJumpFlag(0);
        player.setFlipAngle(0);
    }

    /** {@code loc_44448} (sonic3k.asm:89747-89810): the three bands and the entry angle. */
    private void tryCapture(int slot, AbstractPlayableSprite player, int levelFrame) {
        if (player == null) {
            return;
        }
        int dx = (player.getCentreX() - getCentreX() + CAPTURE_HALF_WIDTH) & 0xFFFF;
        if (Integer.compareUnsigned(dx, CAPTURE_HALF_WIDTH * 2) >= 0) {
            return;
        }
        int dy = (player.getCentreY() - getCentreY()) & 0xFFFF;
        int band = -1;
        for (int i = 0; i < BAND_Y_BIAS.length; i++) {
            int window = (dy + BAND_Y_BIAS[i]) & 0xFFFF;
            if (Integer.compareUnsigned(window, BAND_HEIGHT) < 0) {
                band = i;
                break;
            }
        }
        if (band < 0) {
            return;
        }
        if (player.isDebugMode() || player.isHurt() || player.getDead()
                || player.isObjectControlled()) {
            return;
        }
        if (BAND_ANGLE[band] == 0x30 && !player.isHighPriority()) {
            // cmpi.b #$30,d1 / tst.w art_tile(a1) / bpl (:89781-89784): the middle band only
            // takes a player the turbine has already put behind the tiles.
            return;
        }
        // loc_444B6 (:89786-89806).
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0);
        player.setRenderFlips(false, false);
        NativePositionOps.writeXPosPreserveSubpixel(player, getCentreX());
        // move.b (Level_frame_counter+1).w,d0 / andi.b #7 / subi.b #4 / add.b d0,d0 twice.
        int clockOffset = (((levelFrame & 7) - 4) * 4);
        rideAngle[slot] = (BAND_ANGLE[band] + clockOffset) & 0xFF;
        player.setAnimationId(0);
        ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(player);
        player.setObjectMappingFrameControl(true);
        captured[slot] = true;
        advanceRide(slot, player);
    }

    /** {@code sub_4450A} (sonic3k.asm:89814-89836). */
    private void advanceRide(int slot, AbstractPlayableSprite player) {
        int angle = rideAngle[slot] & 0xFF;
        int index = (angle >> 3) & 0x1F;
        player.setMappingFrame(RIDE_FRAMES[index]);
        NativePositionOps.writeYPosPreserveSubpixel(player,
                (getCentreY() + RIDE_Y_OFFSETS[index]) & 0xFFFF);
        player.setPriorityBucket(PLAYER_PRIORITY_NEAR);
        player.setHighPriority(true);
        if (angle >= 0x80) {
            // tst.b 4(a2) / bpl (:89830-89833): the far half of the loop goes behind the tiles.
            player.setPriorityBucket(PLAYER_PRIORITY_FAR);
            player.setHighPriority(false);
        }
        rideAngle[slot] = (angle + ANGLE_STEP) & 0xFF;
    }

    private static AbstractPlayableSprite asSprite(PlayableEntity entity) {
        return entity instanceof AbstractPlayableSprite sprite ? sprite : null;
    }

    private PlayableEntity nativeP2OrNull() {
        try {
            return services().playerQuery().nativeP2OrNull();
        } catch (Exception e) {
            return null;
        }
    }

    private int levelFrameCounterOrFallback(int fallback) {
        try {
            return services().levelManager() != null
                    ? services().levelManager().getFrameCounter()
                    : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private void playSfx(int id) {
        try {
            services().playSfx(id);
        } catch (Exception ignored) {
            // A headless fixture without an audio service still runs the cycle.
        }
    }

    /** {@code tst.b subtype(a0)}. */
    public boolean isThinVariant() {
        return thinVariant;
    }

    /** ROM {@code $30(a0)} / {@code $31(a0)}. */
    public boolean isCaptured(int slot) {
        return captured[slot];
    }

    /** ROM {@code $32(a0)} / {@code $33(a0)}. */
    public int cooldown(int slot) {
        return cooldown[slot];
    }

    /** ROM {@code $34(a0)} / {@code $35(a0)}. */
    public int rideAngle(int slot) {
        return rideAngle[slot];
    }

    /** ROM {@code mapping_frame(a0)}. */
    public int mappingFrame() {
        return mappingFrame;
    }

    public int getCentreX() {
        return getSpawn().x() & 0xFFFF;
    }

    public int getCentreY() {
        return getSpawn().y() & 0xFFFF;
    }

    @Override
    public int getCollisionFlags() {
        // Only the thin variant has any: the turbine itself is never a touch object (:89620).
        return thinVariant ? THIN_COLLISION_FLAGS : 0;
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public int getPriorityBucket() {
        return PRIORITY_BUCKET;
    }

    @Override
    public boolean isHighPriority() {
        // make_art_tile(ArtTile_LRZ2Drum,1,1) (sonic3k.asm:89612) sets the priority bit.
        return true;
    }

    @Override
    public int getOnScreenHalfWidth() {
        return thinVariant ? THIN_HALF_WIDTH : TURBINE_HALF_WIDTH;
    }

    @Override
    public int getOnScreenHalfHeight() {
        return HALF_HEIGHT;
    }

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        return super.captureRewindState(context).withObjectSubclassExtra(new RewindExtra(
                thinVariant, mappingFrame, captured.clone(), cooldown.clone(), rideAngle.clone()));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra) {
            thinVariant = extra.thinVariant();
            mappingFrame = extra.mappingFrame();
            System.arraycopy(extra.captured(), 0, captured, 0, captured.length);
            System.arraycopy(extra.cooldown(), 0, cooldown, 0, cooldown.length);
            System.arraycopy(extra.rideAngle(), 0, rideAngle, 0, rideAngle.length);
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(thinVariant
                ? Sonic3kObjectArtKeys.LRZ2_TURBINE_SPRITES_THIN
                : Sonic3kObjectArtKeys.LRZ2_TURBINE_SPRITES);
        if (renderer == null) {
            return;
        }
        renderer.drawFrameIndex(mappingFrame, getX(), getY(), false, false);
    }
}
