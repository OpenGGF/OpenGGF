package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RomObjectCodePointerProvider;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SolidRoutineProfile;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;

import java.util.List;

/**
 * ROM object {@code Obj_LRZFallingSpike} -- object id {@code $18} in the {@code SKL} pointer set
 * (sonic3k.asm:87946-88009, ROM {@code $4284C}; {@code Map_LRZFallingSpike} at ROM {@code $42920}).
 * The {@code S3KL} set spends the same id on {@code Obj_LBZCupElevator}.
 *
 * <p>Three routines: it hangs harmful and waiting, drops under gravity when a player comes close
 * enough, and once it hits the floor it stops hurting and becomes an ordinary full-solid block.
 *
 * <p><b>The trigger distance is the subtype in pixels, not a scaled value.</b> Init does
 * {@code move.b subtype(a0),$2F(a0)} (:87955) and the waiting routine does
 * {@code cmp.w $2E(a0),d0} (:87977), and {@code $2F} is the <em>low byte of the word at
 * {@code $2E}</em>. A freshly allocated slot is zeroed -- {@code Delete_Referenced_Sprite}
 * (:36115-36124) clears the whole SST and {@code AllocateObject} (:37911) only looks for a slot
 * whose first long is zero -- and this object never writes {@code $2E}'s high byte, so the compared
 * word is the subtype itself. Lava Reef's fifteen placements carry subtypes 1 to 5, so each spike
 * drops only while a player's {@code x_pos} is within one to five pixels of its own. Byte-verified
 * in the user-supplied ROM at {@code $4288C} ({@code 11 68 00 2C 00 2F}) and {@code $428BE}
 * ({@code B0 68 00 2E}), because a value that narrow reads like a transcription slip. Writing the
 * byte to {@code $2E} instead would give {@code subtype << 8}, a 256-to-1280 pixel trigger.
 *
 * <p>The distance is the smaller of the two players' horizontal separations (:87958-87976), taken
 * with word arithmetic and a {@code neg.w}, and the comparison is unsigned ({@code bhs}).
 *
 * <p>The waiting and falling routines end in {@code Sprite_CheckDeleteTouch3}, so the shared camera
 * unload applies to them. The landed routine's {@code Sprite_OnScreen_Test}
 * also releases its respawn entry and deletes outside the coarse X window;
 * landing does not make the spike permanent.
 */
public final class LrzFallingSpikeObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, TouchResponseProvider, RewindRecreatable,
        RomObjectCodePointerProvider {

    /** {@code move.w #$280,priority(a0)} (sonic3k.asm:87953). */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x0280);
    /** {@code move.b #$10,width_pixels(a0)} / {@code height_pixels(a0)} (:87951-87952). */
    private static final int WIDTH_PIXELS = 0x10;
    private static final int HEIGHT_PIXELS = 0x10;
    /** {@code move.b #$C,y_radius(a0)} (:87954): the radius {@code ObjCheckFloorDist} uses. */
    private static final int Y_RADIUS = 0x0C;
    /** {@code move.b #$82,collision_flags(a0)} (:87954). */
    private static final int COLLISION_FLAGS_HARMFUL = 0x82;
    /** {@code move.w #$13,d1 / #$10,d2 / #$11,d3} before {@code SolidObjectFull} (:88004-88006). */
    private static final int SOLID_HALF_WIDTH = 0x13;
    private static final int SOLID_HEIGHT_AIR = 0x10;
    private static final int SOLID_HEIGHT_GROUND = 0x11;
    /** {@code addi.w #$38,y_vel(a0)} inside {@code MoveSprite} (sonic3k.asm:36037). */
    private static final int GRAVITY = 0x38;

    private enum Phase { WAITING, FALLING, LANDED }

    /** ROM {@code $2E(a0)} as the word the subtype byte lands in. */
    private int triggerDistance;
    private Phase phase = Phase.WAITING;
    private final SubpixelMotion.State motion;

    public LrzFallingSpikeObjectInstance(ObjectSpawn spawn) {
        super(spawn, "LRZFallingSpike");
        this.triggerDistance = spawn.subtype() & 0xFF;
        this.motion = new SubpixelMotion.State(
                spawn.x() & 0xFFFF, spawn.y() & 0xFFFF, 0, 0, 0, 0);
    }

    /**
     * {@code Obj_LRZFallingSpike} is installed from the SKL object pointer table at ROM
     * {@code $0004284C} (sonic3k.lst); its whole code block lies in one bank, so the high word
     * {@code sub_13EFC} latches into {@code Tails_CPU_interact} is {@code $0004}.
     */
    @Override
    public int romObjectCodePointerHighWord() {
        return 0x0004;
    }

    @Override
    public LrzFallingSpikeObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return ObjectConstructionContext.construct(ctx.objectServices(),
                () -> new LrzFallingSpikeObjectInstance(ctx.spawn()));
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        switch (phase) {
            case WAITING -> updateWaiting(playerEntity);
            case FALLING -> updateFalling();
            case LANDED -> {
                // loc_42904 (sonic3k.asm:88003-88008) is the SolidObjectFull call the engine's
                // solid checkpoint makes from getSolidParams(), then a draw test.
            }
        }
    }

    /** {@code loc_42898} (sonic3k.asm:87958-87981). */
    private void updateWaiting(PlayableEntity playerEntity) {
        int distance = Math.min(
                horizontalSeparation(playerEntity),
                horizontalSeparation(nativeP2OrNull()));
        // cmp.w $2E(a0),d0 / bhs: an unsigned compare, and d0 is a non-negative word here.
        if (distance < triggerDistance) {
            phase = Phase.FALLING;
        }
    }

    /**
     * {@code move.w x_pos(a0),d0 / sub.w x_pos(a1),d0 / bpl / neg.w d0} (:87959-87963), word
     * arithmetic throughout. The ROM reads the {@code Player_2} slot unconditionally, so an empty
     * slot contributes {@code |x_pos(a0) - 0|}, the object's own X -- larger than any Lava Reef
     * trigger and therefore never the smaller of the two.
     */
    private int horizontalSeparation(PlayableEntity entity) {
        if (entity == null) {
            return Math.abs((short) getCentreX()) & 0xFFFF;
        }
        int delta = (short) ((getCentreX() - entity.getCentreX()) & 0xFFFF);
        return Math.abs(delta) & 0xFFFF;
    }

    /** {@code loc_428D6} (sonic3k.asm:87985-87998). */
    private void updateFalling() {
        SubpixelMotion.moveSprite(motion, GRAVITY);
        updateDynamicSpawn(motion.x, motion.y);

        // jsr (ObjCheckFloorDist) / tst.w d1 / bpl: land only on a strictly negative distance.
        // Unlike the AIZ emerald scatter, this routine has no "skip while rising" test.
        TerrainCheckResult floor = ObjectTerrainUtils.checkFloorDist(motion.x, motion.y, Y_RADIUS);
        if (!floor.foundSurface() || floor.distance() >= 0) {
            return;
        }
        // add.w d1,y_pos(a0): d1 is negative, so this lifts the spike out of the floor.
        motion.y = (motion.y + floor.distance()) & 0xFFFF;
        updateDynamicSpawn(motion.x, motion.y);
        phase = Phase.LANDED;
        try {
            services().playSfx(Sonic3kSfx.BOSS_PROJECTILE.id);
        } catch (Exception ignored) {
            // Headless replays can omit the audio backend.
        }
    }

    private PlayableEntity nativeP2OrNull() {
        try {
            return services().playerQuery().nativeP2OrNull();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public int getCollisionFlags() {
        // move.b #0,collision_flags(a0) on landing (sonic3k.asm:87992): a landed spike is a plain
        // platform and stops hurting.
        return phase == Phase.LANDED ? 0 : COLLISION_FLAGS_HARMFUL;
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return SolidObjectParams.of(SOLID_HALF_WIDTH, SOLID_HEIGHT_AIR, SOLID_HEIGHT_GROUND);
    }

    @Override
    public boolean isSolidFor(PlayableEntity player) {
        // Only loc_42904 calls SolidObjectFull; the waiting and falling routines never do.
        return phase == Phase.LANDED;
    }

    @Override
    public SolidRoutineProfile getSolidRoutineProfile() {
        return SolidRoutineProfile.fullSolid(false);
    }

    @Override
    public boolean carriesRiderOnHorizontalMove(PlayableEntity player) {
        // d4 = x_pos(a0) (sonic3k.asm:88007); a landed spike never moves again.
        return false;
    }

    // The routine tail calls Sprite_OnScreen_Test (loc_1B5A0): outside
    // the coarse X window it clears respawn bit7 and deletes the object.
    // Use the shared post-routine unload, including after a spike has landed.

    /** ROM x_pos/y_pos are object centres. */
    public int getCentreX() {
        return motion.x & 0xFFFF;
    }

    public int getCentreY() {
        return motion.y & 0xFFFF;
    }

    /** ROM {@code $2E(a0)} as the subtype byte lands in it. */
    public int triggerDistance() {
        return triggerDistance;
    }

    public boolean isWaiting() {
        return phase == Phase.WAITING;
    }

    public boolean isFalling() {
        return phase == Phase.FALLING;
    }

    public boolean isLanded() {
        return phase == Phase.LANDED;
    }

    @Override
    public int getPriorityBucket() {
        return PRIORITY_BUCKET;
    }

    @Override
    public boolean isHighPriority() {
        // make_art_tile(ArtTile_LRZMisc,2,0) (sonic3k.asm:87948) leaves the priority bit clear.
        return false;
    }

    @Override
    public int getOnScreenHalfWidth() {
        return WIDTH_PIXELS;
    }

    @Override
    public int getOnScreenHalfHeight() {
        return HEIGHT_PIXELS;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.LRZ_FALLING_SPIKE);
        if (renderer == null) {
            return;
        }
        // Init leaves mapping_frame at the zero the cleared slot holds.
        renderer.drawFrameIndex(0, getX(), getY(), false, false);
    }
}
