package com.openggf.game.sonic3k.objects;

import com.openggf.game.DamageCause;
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
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SolidRoutineProfile;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * ROM object {@code Obj_LRZSmashingSpikePlatform} -- object id {@code $21} in the {@code SKL}
 * pointer set (sonic3k.asm:88538-88651, ROM {@code $433E8};
 * {@code Map_LRZSmashingSpikePlatform} at ROM {@code $4324A}). The {@code S3KL} set spends the
 * same id on {@code Obj_LBZGateLaser}.
 *
 * <p><b>Shape.</b> A wide solid block that accelerates straight down, slams, holds for thirty
 * frames while a twenty-entry squash animation plays, and then climbs back one pixel a frame to
 * its placed Y before falling again.
 *
 * <ul>
 *   <li>Init {@code :88539-88550}: {@code $46(a0)} latches the placed {@code y_pos} and
 *       {@code $38(a0)} is {@code subtype << 3}, the fall distance in whole pixels. Lava Reef's
 *       ten subtypes run {@code $09} to {@code $1D}, so the drop is 72 to 232 pixels.</li>
 *   <li>Fall {@code loc_43128} {@code :88553-88566}: {@code d0} takes the CURRENT {@code y_vel},
 *       then {@code y_vel += $40}; {@code d0} is sign-extended, shifted left eight and added to
 *       the 16.16 long at {@code $34(a0)}. The engine keeps that whole long, because the landing
 *       write {@code move.w $38(a0),$34(a0)} (:88569) replaces only the high word and leaves the
 *       fraction standing for the next fall.</li>
 *   <li>Land {@code :88567-88576}: {@code y_vel} cleared, {@code $32(a0)} set, {@code $3A(a0)} =
 *       30, {@code anim_frame} = 0, and {@code sfx_Crash} only when {@code render_flags} bit 7
 *       says the block was drawn last frame -- "was drawn", not "is on screen", the reading
 *       {@link LrzFireballLauncherObjectInstance} records for {@code $1B}.</li>
 *   <li>Squash {@code loc_43178} {@code :88579-88586} steps {@code RawAni_43196} while
 *       {@code $3A} counts down; the {@code beq} after the {@code move.b} stops
 *       {@code anim_frame} advancing on the table's trailing zero, so the block sits on frame 0
 *       for the rest of the hold.</li>
 *   <li>Rise {@code loc_431AA} {@code :88592-88603}: one pixel a frame off the high word of
 *       {@code $34}, {@code mapping_frame} flickering between {@code 8} and {@code 0}
 *       ({@code addq.b #8} then {@code andi.b #8}), and {@code sfx_Blast} every time
 *       {@code Level_frame_counter+1 & $1F} is zero. At zero {@code loc_431D4} clears
 *       {@code $32} and the frame, and the next update starts a new fall.</li>
 *   <li>Solid and crush {@code loc_431E0} {@code :88609-88628}: {@code d1 = width_pixels + $B}
 *       ({@code $4B}), {@code d2 = height_pixels - (mapping_frame & 7)} and {@code d3 = d2 + 1},
 *       so the squash frames shrink the box with the art. {@code SolidObjectFull} then leaves
 *       bits {@code 4|8} of the swapped {@code d6} set for a player it drove downward
 *       ({@code loc_1E10E}, {@code d4 = d6 + $F} with {@code p1_standing_bit = 3}, :41578-41583),
 *       and each such player is passed to {@code sub_24280} (:49205-49222).</li>
 * </ul>
 *
 * <p>The routine's only tail is {@code Sprite_OnScreen_Test} (:88631, :88644): no
 * {@code out_of_range}, {@code MarkObjGone} or {@code Delete_Sprite_If_Not_In_Range} anywhere, so
 * the shared camera unload must not apply -- the same audit {@link LrzSinkingRockObjectInstance}
 * records for {@code $17}.
 */
public final class LrzSmashingSpikePlatformObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener, RewindRecreatable,
        RomObjectCodePointerProvider {

    /** {@code move.w #$280,priority(a0)} (sonic3k.asm:88544). */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x0280);
    /** {@code move.b #$40,width_pixels(a0)} (:88542). */
    private static final int WIDTH_PIXELS = 0x40;
    /** {@code move.b #$20,height_pixels(a0)} (:88543). */
    private static final int HEIGHT_PIXELS = 0x20;
    /** {@code addi.w #$B,d1} (:88613). */
    private static final int SOLID_SIDE_PADDING = 0x0B;
    /** {@code addi.w #$40,y_vel(a0)} (:88557). */
    private static final int GRAVITY = 0x40;
    /** {@code move.w #30,$3A(a0)} (:88571). */
    private static final int SMASH_HOLD_FRAMES = 30;
    /** {@code RawAni_43196} (sonic3k.asm:88589). */
    private static final int[] SQUASH_FRAMES =
            {2, 4, 6, 7, 7, 7, 7, 6, 6, 5, 5, 4, 4, 3, 3, 2, 2, 1, 1, 0};

    /** ROM {@code $46(a0)}: the placed {@code y_pos}, the top of the fall. */
    private int baseY;
    /** ROM {@code $38(a0)}: {@code subtype << 3}, the fall distance in pixels. */
    private int fallDistance;
    /** ROM {@code $34(a0)}: a 16.16 displacement below {@code $46(a0)}. */
    private int offset;
    /** ROM {@code y_vel(a0)}, 8.8. */
    private int yVel;
    /** ROM {@code $32(a0)}: zero while falling, non-zero from the slam until the rise ends. */
    private boolean smashed;
    /** ROM {@code $3A(a0)}. */
    private int holdTimer;
    /** ROM {@code anim_frame(a0)}: the index into {@code RawAni_43196}. */
    private int animFrame;
    /** ROM {@code mapping_frame(a0)}. */
    private int mappingFrame;
    /**
     * ROM {@code render_flags(a0)} bit 7 as the previous frame's render pass left it. Only
     * {@code Sprite_OnScreen_Test} sets it, so a block created as the camera reaches it reads
     * clear on its creation frame -- the latch {@link LrzFireballLauncherObjectInstance} records.
     */
    private boolean renderedLastFrame;

    public LrzSmashingSpikePlatformObjectInstance(ObjectSpawn spawn) {
        super(spawn, "LRZSmashingSpikePlatform");
        this.baseY = spawn.y() & 0xFFFF;
        // moveq #0,d0 / move.b subtype(a0),d0 / lsl.w #3,d0 (sonic3k.asm:88546-88549).
        this.fallDistance = ((spawn.subtype() & 0xFF) << 3) & 0xFFFF;
    }

    /**
     * {@code Obj_LRZSmashingSpikePlatform} is installed from the SKL object pointer table at ROM
     * {@code $000433E8} (sonic3k.lst); its whole code block lies in one bank, so the high word
     * {@code sub_13EFC} latches into {@code Tails_CPU_interact} is {@code $0004}.
     */
    @Override
    public int romObjectCodePointerHighWord() {
        return 0x0004;
    }

    @Override
    public LrzSmashingSpikePlatformObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return ObjectConstructionContext.construct(ctx.objectServices(),
                () -> new LrzSmashingSpikePlatformObjectInstance(ctx.spawn()));
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        boolean wasRendered = renderedLastFrame;
        renderedLastFrame = isOnScreen();
        if (!smashed) {
            fall(wasRendered);
        } else if (holdTimer != 0) {
            squash();
        } else {
            rise(vIntRunCount);
        }
        // loc_431E0 (:88609-88611): y_pos = $46(a0) + $34(a0).w, a word add.
        updateDynamicSpawn(getCentreX(), getCentreY());
    }

    /** {@code loc_43128} (sonic3k.asm:88553-88577). */
    private void fall(boolean wasRendered) {
        int velocityThisFrame = (short) yVel;
        yVel = (short) (yVel + GRAVITY);
        // ext.l d0 / lsl.l #8,d0 / add.l d0,$34(a0): an 8.8 velocity onto a 16.16 displacement,
        // the MoveSprite2 scaling (sonic3k.asm:36054-36061).
        offset += velocityThisFrame << 8;
        // cmp.w $38(a0),d2 / blo: an UNSIGNED compare of the whole-pixel part.
        int wholePixels = (offset >>> 16) & 0xFFFF;
        if (wholePixels < fallDistance) {
            return;
        }
        yVel = 0;
        // move.w $38(a0),$34(a0) writes the HIGH word only; the fraction survives.
        offset = (fallDistance << 16) | (offset & 0xFFFF);
        smashed = true;
        holdTimer = SMASH_HOLD_FRAMES;
        animFrame = 0;
        // tst.b render_flags(a0) / bpl: bit 7 is "was drawn last frame".
        if (wasRendered) {
            playSfx(Sonic3kSfx.CRASH.id);
        }
    }

    /** {@code loc_43178} (sonic3k.asm:88579-88586). */
    private void squash() {
        holdTimer--;
        mappingFrame = SQUASH_FRAMES[animFrame];
        // beq.s loc_431E0 after the move.b: the trailing zero entry freezes anim_frame.
        if (mappingFrame != 0) {
            animFrame++;
        }
    }

    /** {@code loc_431AA} (sonic3k.asm:88592-88607). */
    private void rise(int vIntRunCount) {
        int wholePixels = (offset >>> 16) & 0xFFFF;
        if (wholePixels == 0) {
            // loc_431D4 (:88609-88610).
            smashed = false;
            mappingFrame = 0;
            return;
        }
        offset = ((wholePixels - 1) << 16) | (offset & 0xFFFF);
        // addq.b #8 / andi.b #8: a two-frame flicker between frames 8 and 0.
        mappingFrame = (mappingFrame + 8) & 8;
        if ((levelFrameCounterLowByte(vIntRunCount) & 0x1F) == 0) {
            playSfx(Sonic3kSfx.BLAST.id);
        }
    }

    /**
     * {@code move.b (Level_frame_counter+1).w,d0} (:88600): the LOW byte of the level clock, which
     * is independent of the object-visible {@code V_int_run_count} this method is handed.
     */
    private int levelFrameCounterLowByte(int fallback) {
        return (services().levelManager() != null
                ? services().levelManager().getFrameCounter()
                : fallback) & 0xFF;
    }

    private void playSfx(int id) {
        try {
            services().playSfx(id);
        } catch (Exception e) {
            // Headless unit contexts have no audio service; the routine's behaviour is the
            // motion, not the cue.
        }
    }

    // ===== SolidObjectProvider =====

    @Override
    public SolidObjectParams getSolidParams() {
        // d1 = width_pixels + $B; d2 = height_pixels - (mapping_frame & 7); d3 = d2 + 1
        // (sonic3k.asm:88612-88621).
        int air = HEIGHT_PIXELS - (mappingFrame & 7);
        return SolidObjectParams.of(WIDTH_PIXELS + SOLID_SIDE_PADDING, air, air + 1);
    }

    @Override
    public SolidRoutineProfile getSolidRoutineProfile() {
        return SolidRoutineProfile.fullSolid(false);
    }

    @Override
    public int getTopLandingHalfWidth(PlayableEntity player, int collisionHalfWidth) {
        // d4 = x_pos(a0) (:88622) with the unpadded width_pixels as the retention surface.
        return WIDTH_PIXELS;
    }

    @Override
    public boolean carriesRiderOnHorizontalMove(PlayableEntity player) {
        // d4 is x_pos(a0) and the block only ever moves vertically, so MvSonicOnPtfm's
        // d4 - x_pos(a0) carry is zero either way.
        return false;
    }

    // ===== SolidObjectListener =====

    @Override
    public void onSolidContact(PlayableEntity player, SolidContact contact, int frameCounter) {
        // swap d6 / andi.w #4|8,d6 (:88626-88628). Bits 18 and 19 of d6 are set by loc_1E10E's
        // "d4 = d6 + $F" (sonic3k.asm:41578-41583) with p1_standing_bit = 3
        // (sonic3k.constants.asm:133), which is the branch that drives an overlapping player
        // DOWNWARD -- the block coming down on their head, not a side push or a landing.
        if (player == null || !contact.touchBottom()) {
            return;
        }
        applySub24280(player, frameCounter);
    }

    /** {@code sub_24280} (sonic3k.asm:49205-49222). */
    private void applySub24280(PlayableEntity player, int frameCounter) {
        if (player.getInvulnerable()) {
            return;
        }
        // move.l y_pos(a1),d3 / move.w y_vel(a1),d0 / ext.l / asl.l #8 / sub.l d0,d3: the full
        // 16.16 y_pos is rewound by this frame's 8.8 y_vel before HurtCharacter runs.
        if (player instanceof AbstractPlayableSprite sprite) {
            sprite.move((short) 0, (short) -sprite.getYSpeed());
        }
        int sourceX = getCentreX();
        if (player.isCpuControlled()) {
            player.applyHurt(sourceX);
            return;
        }
        boolean hadRings = player.getRingCount() > 0;
        if (hadRings && !player.hasShield()) {
            services().spawnLostRings(player, frameCounter);
        }
        player.applyHurtOrDeath(sourceX, DamageCause.NORMAL, hadRings);
    }

    // ===== Lifetime =====

    @Override
    public boolean usesCustomOutOfRangeCheck() {
        return true;
    }

    @Override
    public boolean isCustomOutOfRange(int cameraX) {
        return false;
    }

    // ===== Position and accessors =====

    /** ROM x_pos/y_pos are object centres. */
    public int getCentreX() {
        return getSpawn().x() & 0xFFFF;
    }

    /** {@code move.w $46(a0),d0 / add.w $34(a0),d0 / move.w d0,y_pos(a0)} (:88609-88611). */
    public int getCentreY() {
        return (baseY + ((offset >>> 16) & 0xFFFF)) & 0xFFFF;
    }

    /** ROM {@code $46(a0)}. */
    public int baseY() {
        return baseY;
    }

    /** ROM {@code $38(a0)}. */
    public int fallDistance() {
        return fallDistance;
    }

    /** ROM {@code $34(a0)} whole-pixel part. */
    public int offsetPixels() {
        return (offset >>> 16) & 0xFFFF;
    }

    /** ROM {@code y_vel(a0)}. */
    public int yVel() {
        return (short) yVel;
    }

    /** ROM {@code $32(a0)} as a flag. */
    public boolean smashed() {
        return smashed;
    }

    /** ROM {@code $3A(a0)}. */
    public int holdTimer() {
        return holdTimer;
    }

    /** ROM {@code mapping_frame(a0)}. */
    public int mappingFrame() {
        return mappingFrame;
    }

    // ===== Rendering =====

    @Override
    public int getPriorityBucket() {
        return PRIORITY_BUCKET;
    }

    @Override
    public boolean isHighPriority() {
        // make_art_tile(ArtTile_LRZMisc,2,0) (sonic3k.asm:88540) leaves the priority bit clear.
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
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.LRZ_SMASHING_SPIKE_PLATFORM);
        if (renderer == null) {
            return;
        }
        renderer.drawFrameIndex(mappingFrame, getX(), getY(), false, false);
    }
}
