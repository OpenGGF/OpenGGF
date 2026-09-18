package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ExplosionObjectInstance;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RomObjectCodePointerProvider;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SolidRoutineProfile;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * ROM object {@code Obj_Iwamodoki} -- object id {@code $9A} in the {@code SKL} pointer set
 * (sonic3k.asm:188040-188135, ROM {@code $8FAF0}; {@code Map_Iwamodoki} at ROM {@code $8FC90}).
 * Lava Reef places 32 in act 1 and 34 in act 2, the zone's most common badnik.
 *
 * <p>It is not an attackable badnik: {@code ObjDat_Iwamodoki} leaves {@code collision_flags} at
 * {@code 0} (:188106), so nothing in the touch system sees it at all. What it is, is a solid
 * block with a fuse.
 *
 * <ul>
 *   <li>{@code Obj_WaitOffscreen} (:180271-180302) heads the routine, so the object does nothing
 *       and draws nothing until {@code render_flags} bit 7 says it has been on screen.</li>
 *   <li>{@code loc_8FB4C} (:188074-188081): {@code Find_SonicTails} gives the nearer player's
 *       ABSOLUTE horizontal distance in {@code d2}; under {@code $40} pixels the fuse lights --
 *       {@code byte_8FC30} into {@code $30(a0)} and {@code loc_8FB76} into {@code $34(a0)}.</li>
 *   <li>{@code loc_8FB70} (:188084) is {@code Animate_RawMultiDelay} over {@code byte_8FC30}: three
 *       frames of seven, a {@code $2F} hold, and then an accelerating 3/4 flicker that ends on
 *       frame 5 for {@code $1F} frames before the {@code $F4} command calls {@code $34(a0)}.</li>
 *   <li>{@code loc_8FB76} (:188086-188094): with {@code FixBugs = 0} the ROM never awards the 100
 *       points the comment describes, so this class does not either. The slot becomes
 *       {@code Obj_Explosion}, {@code status} bit 7 is set and
 *       {@code CreateChild2_Complex(ChildObjDat_8FBD6)} throws the four fragments.</li>
 *   <li>The tail (:188046-188061) unloads past {@code $280} coarse pixels, stops being solid once
 *       {@code status} bit 7 is set ({@code Displace_PlayerOffObject}), and is otherwise
 *       {@code SolidObjectFull} with {@code d1 = $17}, {@code d2 = $C}, {@code d3 = $B}. Note
 *       {@code d3} is one LESS than {@code d2} here, not one more as most solids have it.</li>
 * </ul>
 *
 * <p>{@code Animate_RawMultiDelay} adds two to {@code anim_frame} BEFORE reading
 * (sonic3k.asm:177563-177566) and {@code anim_frame_timer} starts at zero, so entry 0 of the
 * script is never shown: the fuse opens on {@code byte_8FC30}'s second pair.
 */
public final class IwamodokiBadnikInstance extends AbstractObjectInstance
        implements SolidObjectProvider, RewindRecreatable, RomObjectCodePointerProvider {

    /** {@code ObjDat_Iwamodoki}: {@code dc.w $280} (sonic3k.asm:188105). */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x0280);
    /** {@code dc.b $C,$C,0,0} (:188106). */
    private static final int HALF_SIZE = 0x0C;
    /** {@code moveq #$17,d1 / #$C,d2 / #$B,d3} (:188051-188053). */
    private static final int SOLID_HALF_WIDTH = 0x17;
    private static final int SOLID_HEIGHT_AIR = 0x0C;
    private static final int SOLID_HEIGHT_GROUND = 0x0B;
    /** {@code cmpi.w #$40,d2 / bhs} (:188076). */
    private static final int FUSE_RANGE = 0x40;
    /**
     * {@code byte_8FC30} (sonic3k.asm:188137-188181) as {@code (mapping_frame, delay)} pairs,
     * ending in the {@code $F4} command that calls {@code $34(a0)}.
     */
    private static final int[][] FUSE_SCRIPT = {
            {0, 7}, {1, 7}, {2, 7}, {3, 0x2F},
            {4, 5}, {3, 5}, {4, 4}, {3, 4}, {4, 3}, {3, 3}, {4, 2}, {3, 2},
            {4, 1}, {3, 1},
            {4, 0}, {3, 0}, {4, 0}, {3, 0}, {4, 0}, {3, 0}, {4, 0}, {3, 0},
            {4, 0}, {3, 0}, {4, 0}, {3, 0}, {4, 0}, {3, 0}, {4, 0}, {3, 0},
            {4, 0}, {3, 0}, {4, 0}, {3, 0}, {4, 0}, {3, 0}, {4, 0}, {3, 0},
            {5, 0x1F}};
    /** {@code ChildObjDat_8FBD6} (sonic3k.asm:188117-188135): offset X, Y, x_vel, y_vel. */
    private static final int[][] SHRAPNEL = {
            {-4, 4, -0x400, -0x200},
            {4, 4, 0x400, -0x200},
            {-8, -8, -0x200, -0x400},
            {8, -8, 0x200, -0x400}};

    /** ROM {@code routine(a0)}: 0 init, 2 waiting for a player, 4 burning. */
    private int routine;
    /** ROM {@code anim_frame(a0)}: the byte index into the script, always even. */
    private int animFrame;
    /** ROM {@code anim_frame_timer(a0)}. */
    private int animTimer;
    /** ROM {@code mapping_frame(a0)}. */
    private int mappingFrame;
    /** ROM {@code status(a0)} bit 7: set at detonation, which ends the solid. */
    private boolean detonated;
    /** ROM {@code render_flags(a0)} bit 7 as {@code Obj_WaitOffscreen} reads it. */
    private boolean awake;

    public IwamodokiBadnikInstance(ObjectSpawn spawn) {
        super(spawn, "Iwamodoki");
    }

    /**
     * {@code Obj_Iwamodoki} is installed from the SKL object pointer table at ROM
     * {@code $0008FAF0} (sonic3k.lst); its whole code block lies in one bank, so the high word
     * {@code sub_13EFC} latches into {@code Tails_CPU_interact} is {@code $0008}.
     */
    @Override
    public int romObjectCodePointerHighWord() {
        return 0x0008;
    }

    @Override
    public IwamodokiBadnikInstance recreateForRewind(RewindRecreateContext ctx) {
        return ObjectConstructionContext.construct(ctx.objectServices(),
                () -> new IwamodokiBadnikInstance(ctx.spawn()));
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        if (detonated) {
            return;
        }
        // Obj_WaitOffscreen: the real routine is restored only once the object has been drawn.
        if (!awake) {
            awake = isOnScreen();
            if (!awake) {
                return;
            }
        }
        switch (routine) {
            case 0 -> routine = 2;
            case 2 -> lookForPlayer(playerEntity);
            default -> burn();
        }
    }

    /** {@code loc_8FB4C} (sonic3k.asm:188074-188081). */
    private void lookForPlayer(PlayableEntity playerEntity) {
        if (nearestHorizontalDistance(playerEntity) >= FUSE_RANGE) {
            return;
        }
        routine = 4;
        animFrame = 0;
        animTimer = 0;
        }

    /**
     * {@code Find_SonicTails} (sonic3k.asm:178248-178280) returns the ABSOLUTE distance of
     * whichever player is horizontally nearer, Player 2 included.
     */
    private int nearestHorizontalDistance(PlayableEntity playerEntity) {
        int best = Integer.MAX_VALUE;
        if (playerEntity != null) {
            best = Math.abs((short) (getCentreX() - playerEntity.getCentreX()));
        }
        PlayableEntity p2 = nativeP2OrNull();
        if (p2 != null) {
            best = Math.min(best, Math.abs((short) (getCentreX() - p2.getCentreX())));
        }
        return best;
    }

    /**
     * {@code Animate_RawMultiDelay} (sonic3k.asm:177560-177578). The {@code addq.w #2,d0} runs
     * BEFORE the read, so the first step lands on pair 1; a negative entry -- here the trailing
     * {@code $F4} -- runs {@code loc_84600}, which calls {@code $34(a0)}.
     */
    private void burn() {
        animTimer = (byte) (animTimer - 1);
        if (animTimer >= 0) {
            return;
        }
        animFrame = (byte) (animFrame + 2);
        int pair = (animFrame & 0xFF) / 2;
        if (pair >= FUSE_SCRIPT.length) {
            detonate();
            return;
        }
        mappingFrame = FUSE_SCRIPT[pair][0];
        animTimer = FUSE_SCRIPT[pair][1];
    }

    /** {@code loc_8FB76} (sonic3k.asm:188086-188094). */
    private void detonate() {
        detonated = true;
        final int x = getCentreX();
        final int y = getCentreY();
        for (int[] fragment : SHRAPNEL) {
            final int index = indexOf(fragment);
            final int fx = (x + fragment[0]) & 0xFFFF;
            final int fy = (y + fragment[1]) & 0xFFFF;
            final int xVel = fragment[2];
            final int yVel = fragment[3];
            spawnChild(() -> new IwamodokiShrapnelInstance(fx, fy, index * 2, xVel, yVel));
        }
        try {
            spawnChild(() -> new ExplosionObjectInstance(
                    new ObjectSpawn(x, y, 0, 0, 0, false, 0), services()));
        } catch (Exception ignored) {
            // A probe-constructed badnik has no object manager; the state change still applies.
        }
        ObjectLifetimeOps.destroyLatched(this);
    }

    private static int indexOf(int[] fragment) {
        for (int i = 0; i < SHRAPNEL.length; i++) {
            if (SHRAPNEL[i] == fragment) {
                return i;
            }
        }
        return 0;
    }

    private PlayableEntity nativeP2OrNull() {
        try {
            return services().playerQuery().nativeP2OrNull();
        } catch (Exception e) {
            return null;
        }
    }

    // ===== SolidObjectProvider =====

    @Override
    public SolidObjectParams getSolidParams() {
        return SolidObjectParams.of(SOLID_HALF_WIDTH, SOLID_HEIGHT_AIR, SOLID_HEIGHT_GROUND);
    }

    @Override
    public SolidRoutineProfile getSolidRoutineProfile() {
        return SolidRoutineProfile.fullSolid(false);
    }

    @Override
    public boolean carriesRiderOnHorizontalMove(PlayableEntity player) {
        // d4 = x_pos(a0) and the badnik never moves, so MvSonicOnPtfm's carry is zero either way.
        return false;
    }

    // ===== Accessors =====

    /** ROM {@code routine(a0)}. */
    public int routine() {
        return routine;
    }

    /** ROM {@code anim_frame(a0)}. */
    public int animFrame() {
        return animFrame & 0xFF;
    }

    /** ROM {@code anim_frame_timer(a0)}. */
    public int animTimer() {
        return (byte) animTimer;
    }

    /** ROM {@code mapping_frame(a0)}. */
    public int mappingFrame() {
        return mappingFrame;
    }

    /** ROM {@code status(a0)} bit 7. */
    public boolean detonated() {
        return detonated;
    }

    /** ROM {@code render_flags(a0)} bit 7, as {@code Obj_WaitOffscreen} latches it. */
    public boolean awake() {
        return awake;
    }

    public int getCentreX() {
        return getSpawn().x() & 0xFFFF;
    }

    public int getCentreY() {
        return getSpawn().y() & 0xFFFF;
    }

    @Override
    public int getPriorityBucket() {
        return PRIORITY_BUCKET;
    }

    @Override
    public boolean isHighPriority() {
        // make_art_tile(ArtTile_Iwamodoki,0,0) (sonic3k.asm:188104).
        return false;
    }

    @Override
    public int getOnScreenHalfWidth() {
        return HALF_SIZE;
    }

    @Override
    public int getOnScreenHalfHeight() {
        return HALF_SIZE;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!awake) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.IWAMODOKI);
        if (renderer == null) {
            return;
        }
        renderer.drawFrameIndex(mappingFrame, getX(), getY(), false, false);
    }
}
