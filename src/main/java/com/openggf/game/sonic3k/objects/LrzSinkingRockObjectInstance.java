package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RomObjectCodePointerProvider;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SolidRoutineProfile;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;

import java.util.List;

/**
 * ROM object {@code Obj_LRZSinkingRock} -- object id {@code $17} in the {@code SKL} pointer set
 * (sonic3k.asm:87898-87941, ROM {@code $4279E}; {@code Map_LRZSinkingRock} at ROM {@code $42834}).
 * The {@code S3KL} set spends the same id on {@code Obj_LBZRideGrapple}.
 *
 * <p><b>Shape.</b> A full-solid block that rides a sine curve instead of a velocity. {@code $2E(a0)}
 * is an angle byte that counts <em>up</em> by one each frame a player is standing on the block and
 * back <em>down</em> by one each frame nobody is, clamped to {@code $40} at the top and {@code 0} at
 * the bottom. The block's {@code y_pos} is then {@code $46(a0) + (sin($2E) >> 3)}, so it sinks
 * smoothly to {@code sin($40) / 8 = $100 / 8 = 32} pixels below its placed Y and rises back.
 *
 * <p>ROM references, all in {@code docs/skdisasm/sonic3k.asm}:
 * <ul>
 *   <li>Init {@code Obj_LRZSinkingRock} {@code :87898-87911}; the act branch at {@code :87907-87910}
 *       gives act 2 {@code mapping_frame} 1 and the {@code $090} tile base</li>
 *   <li>Routine install {@code loc_427DC} {@code :87913-87914}, which falls straight through into
 *       the main routine on the same frame</li>
 *   <li>Main {@code loc_427E2} {@code :87916-87922}, rise branch {@code loc_427F8}
 *       {@code :87924-87927}, position and collision {@code loc_42804} {@code :87929-87940}</li>
 * </ul>
 *
 * <p>The {@code Sprite_OnScreen_Test} tail (:87940) draws only in range;
 * {@code loc_1B5A0} releases the respawn entry and deletes it outside the
 * coarse X window. The shared post-routine unload models that path.
 *
 * <p>No zone or act gate lives in this class: it reaches Lava Reef only because
 * {@code Sonic3kObjectRegistry} resolves id {@code $17} through
 * {@link com.openggf.game.sonic3k.objects.S3kZoneSet}.
 */
public final class LrzSinkingRockObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener, RewindRecreatable,
        RomObjectCodePointerProvider {

    /** {@code move.w #$280,priority(a0)} (sonic3k.asm:87903). */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x0280);
    /** {@code move.b #$10,width_pixels(a0)} / {@code height_pixels(a0)} (:87901-87902). */
    private static final int WIDTH_PIXELS = 0x10;
    private static final int HEIGHT_PIXELS = 0x10;
    /**
     * {@code move.w #$1B,d1 / #$10,d2 / #$11,d3} before {@code SolidObjectFull} (:87934-87937).
     * The solid box is wider than the drawn block and one pixel deeper on the standing side than on
     * the approach side, which is why the two vertical parameters are not equal.
     */
    private static final int SOLID_HALF_WIDTH = 0x1B;
    private static final int SOLID_HEIGHT_AIR = 0x10;
    private static final int SOLID_HEIGHT_GROUND = 0x11;
    /** {@code cmpi.b #$40,$2E(a0)} (:87925): the angle stops at a quarter turn. */
    private static final int MAX_ANGLE = 0x40;

    /** ROM {@code $46(a0)}: {@code y_pos} as placed, the curve's origin (:87904). */
    private int baseY;
    /** ROM {@code mapping_frame(a0)}: 0 in act 1, 1 in act 2 (:87908). */
    private int mappingFrame;
    /** ROM {@code $2E(a0)}: the angle byte driving the sine. */
    private int angle;

    private final String artKey;

    private boolean p1Standing;
    private boolean p2Standing;
    private boolean p1StandingLatched;
    private boolean p2StandingLatched;

    public LrzSinkingRockObjectInstance(ObjectSpawn spawn) {
        super(spawn, "LRZSinkingRock");
        this.baseY = spawn.y() & 0xFFFF;
        boolean act2 = actIndexOrZero() != 0;
        this.mappingFrame = act2 ? 1 : 0;
        this.artKey = act2
                ? Sonic3kObjectArtKeys.LRZ2_SINKING_ROCK
                : Sonic3kObjectArtKeys.LRZ_SINKING_ROCK;
    }

    private int actIndexOrZero() {
        try {
            return services().currentAct();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * {@code Obj_LRZSinkingRock} is installed from the SKL object pointer table at ROM
     * {@code $0004279E} (sonic3k.lst); its whole code block lies in one bank, so the high word
     * {@code sub_13EFC} latches into {@code Tails_CPU_interact} is {@code $0004}.
     */
    @Override
    public int romObjectCodePointerHighWord() {
        return 0x0004;
    }

    @Override
    public LrzSinkingRockObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return ObjectConstructionContext.construct(ctx.objectServices(),
                () -> new LrzSinkingRockObjectInstance(ctx.spawn()));
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        // SolidObjectFull writes status(a0)'s standing bits at the tail of the ROM routine
        // (:87938), so loc_427E2 at the head of the next frame reads the bits the PREVIOUS frame
        // left. The engine runs its solid checkpoint after this method, so the latch reproduces
        // that one-frame relationship -- the same shape LrzDashElevatorObjectInstance documents.
        p1StandingLatched = p1Standing;
        p2StandingLatched = p2Standing;
        p1Standing = false;
        p2Standing = false;

        // loc_427E2 (:87916-87922) / loc_427F8 (:87924-87927).
        if (p1StandingLatched || p2StandingLatched) {
            if (angle != MAX_ANGLE) {
                angle++;
            }
        } else if (angle != 0) {
            angle--;
        }

        // loc_42804 (:87929-87933): GetSineCosine returns the sine in d0 scaled by $100; asr.w #3
        // is a signed shift, and the result is a word add onto $46(a0).
        updateDynamicSpawn(getCentreX(), getCentreY());
    }

    @Override
    public void onSolidContact(PlayableEntity player, SolidContact contact, int frameCounter) {
        if (!contact.standing()) {
            return;
        }
        if (player == nativeP2OrNull()) {
            p2Standing = true;
        } else {
            p1Standing = true;
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
    public SolidObjectParams getSolidParams() {
        return SolidObjectParams.of(SOLID_HALF_WIDTH, SOLID_HEIGHT_AIR, SOLID_HEIGHT_GROUND);
    }

    @Override
    public SolidRoutineProfile getSolidRoutineProfile() {
        return SolidRoutineProfile.fullSolid(false);
    }

    @Override
    public boolean airborneRiderUnseatRequiresOwnCheckpoint(PlayableEntity player) {
        // The a0.d6 standing bit SolidObjectFull_1P reads is per object
        // (sonic3k.asm:41021-41034): loc_1DC98's bclr names THIS block's status byte, and another
        // solid's SolidObjectFull clears only its own. The block's stale-rider branch must
        // therefore still be available when the block's own checkpoint runs, even if an earlier
        // slot's checkpoint already saw the rider airborne. Same contract as
        // Obj_MGZMovingSpikePlatform.
        return true;
    }

    @Override
    public boolean airborneStaleStandingBitReturnsNoContact(PlayableEntity player) {
        // loc_1DC98 (sonic3k.asm:41028-41034) clears Status_OnObj / d6 and returns d4 = 0 without
        // falling through to SolidObject_cont, so neither MvSonicOnPtfm nor loc_1E154's
        // upward-velocity lift (:41608-41637) runs on the frame the rider jumps off. Without this
        // the block's sink is applied on top of Sonic_Jump's own y_pos change.
        return true;
    }

    @Override
    public boolean carriesRiderOnHorizontalMove(PlayableEntity player) {
        // d4 = x_pos(a0) (:87937) and the block only ever moves vertically, so MvSonicOnPtfm's
        // d4 - x_pos(a0) carry is zero either way.
        return false;
    }

    // The routine tail calls Sprite_OnScreen_Test (loc_1B5A0): outside
    // the coarse X window it clears respawn bit7 and deletes the object.
    // Use the shared post-routine unload in every movement phase.

    /** ROM x_pos/y_pos are object centres. */
    public int getCentreX() {
        return getSpawn().x() & 0xFFFF;
    }

    /** ROM {@code move.w d0,y_pos(a0)} (:87933): {@code $46(a0) + (sin($2E) >> 3)}, a word. */
    public int getCentreY() {
        return (baseY + (TrigLookupTable.sinHex(angle) >> 3)) & 0xFFFF;
    }

    /** ROM {@code $2E(a0)}. */
    public int angle() {
        return angle;
    }

    /** ROM {@code $46(a0)}. */
    public int baseY() {
        return baseY;
    }

    public int mappingFrame() {
        return mappingFrame;
    }

    @Override
    public int getPriorityBucket() {
        return PRIORITY_BUCKET;
    }

    @Override
    public boolean isHighPriority() {
        // make_art_tile($0D3,2,0) and make_art_tile($090,2,0) both leave the priority bit clear
        // (sonic3k.asm:87900, :87910).
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
        PatternSpriteRenderer renderer = getRenderer(artKey);
        if (renderer == null) {
            return;
        }
        renderer.drawFrameIndex(mappingFrame, getX(), getY(), false, false);
    }
}
