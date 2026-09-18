package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectAnimationState;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RomObjectCodePointerProvider;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SolidRoutineProfile;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.objects.SpringBounceHelper;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.ReverseGravity;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.animation.SpriteAnimationEndAction;
import com.openggf.sprites.animation.SpriteAnimationScript;
import com.openggf.sprites.animation.SpriteAnimationSet;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * SKL {@code $5D}, {@code Obj_DEZRetractingSpring} (sonic3k.asm:94098-94185).
 *
 * <p>Thirteen act 2 placements, every one of them subtype {@code $02}; act 1 places none. The
 * S3KL table's {@code $5D} is {@code Obj_CGZTriangleBumpers}, a competition-zone object no
 * zone 0-6 layout places, so the two tables share the number the way {@code $61} does.
 *
 * <p><b>It is a horizontal piston, not a vertical spring.</b> The object never moves in Y. Its
 * placement X is saved once into {@code $44(a0)} (:94105) and every update rewrites
 * {@code x_pos} as {@code $44 ± $34(a0)} (:94156-94158), where {@code $34} is the extension in
 * eight-pixel steps and {@code $32(a0) = $20} (:94106) is its limit. What it launches you with
 * is still the vertical {@code Spring_Powers} word, selected by subtype bit 1 out of
 * {@code word_4808A} (:94093-94095, :94107-94109) — the same {@code -$1000} / {@code -$A00}
 * pair every red and yellow spring in the three games uses.
 *
 * <p><b>The extension is driven by Player 1's height alone, with a dead band either side.</b>
 * {@code d0 = Player_1.y_pos - y_pos} (:94113-94114). The {@code bcs} at :94115 is an unsigned
 * borrow, so it separates "player below" from "player above" rather than testing a sign. Below,
 * the spring extends by 8 only once the player is a full {@code $20} down (:94116-94127);
 * above, it retracts by 8 only once the player is more than {@code $20} up (:94132-94143). In
 * between it holds whatever extension it had, which is what lets a rider stand on an extended
 * spring without it retracting under them.
 *
 * <p><b>Which way it extends is the exclusive or of the two flip bits.</b> {@code btst #0} skips
 * a negate and {@code btst #1} adds one (:94146-94154), so an unflipped spring extends towards
 * −X, one flip extends towards +X, and both flips extend towards −X again. Of the thirteen
 * placements, {@code DEZ2_Sprites} records 4 and 373 are unflipped, records 8, 198, 239, 327,
 * 397, 398 carry one flip and records 206, 332, 355, 356, 414 carry both.
 *
 * <p><b>The launch is the shared spring subroutine.</b> {@code sub_22F98} (:47719-47749) nudges
 * {@code y_pos} by 8 — mirrored to −8 under {@code Reverse_gravity_flag} by a
 * {@code subi.w #2*8} (:47722-47724) — writes the stored velocity, sets {@code Status_InAir},
 * clears {@code Status_OnObj}, {@code jumping} and {@code spin_dash_flag}, puts the player on
 * animation {@code $10} and returns routine 2. It writes no {@code move_lock}: neither
 * {@code Obj_Spring_Up} nor this object suppresses input after a launch, so nothing here sets
 * the engine's springing timer.
 *
 * <p>Subtype bit 7 zeroes {@code x_vel} (:47729-47731) and subtype bit 0 starts the tumbling
 * flip (:47732-47747). No {@code $5D} placement sets either, so both are modelled from the
 * subroutine rather than from a placement; the {@code andi.b #$C} solid-layer swap at
 * :47750-47757 is not modelled because the engine has no per-player solid-layer bits to write.
 */
public final class S3kDezRetractingSpringObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener, SpawnRewindRecreatable,
        RomObjectCodePointerProvider {

    /** {@code moveq #0,d1 / move.b width_pixels(a0),d1} with {@code width_pixels = $10} (:94103, :94159-94160). */
    private static final int SOLID_HALF_WIDTH = 0x10;
    /** {@code moveq #9,d3} (:94161): the only height {@code SolidObjectTop_1P} is given. */
    private static final int SOLID_HALF_HEIGHT = 9;

    /** {@code move.w #$20,$32(a0)} (:94106): the extension limit. */
    private static final int EXTENSION_LIMIT = 0x20;
    /** {@code addq.w #8} / {@code subq.w #8} (:94127, :94143). */
    private static final int EXTENSION_STEP = 8;
    /** {@code cmpi.w #$20,d0} (:94116) and {@code cmpi.w #-$20,d0} (:94132). */
    private static final int DEAD_BAND = 0x20;

    /** {@code move.w #$280,priority(a0)} (:94102). */
    private static final int PRIORITY_WORD = 0x280;

    /** {@code addq.w #8,y_pos(a1)} (:47721). */
    private static final int LAUNCH_Y_NUDGE = 8;

    /** {@code Obj_DEZRetractingSpring} installs {@code loc_480D4} (:94110), ROM {@code $000480D4}. */
    private static final int ROM_CODE_POINTER_HIGH_WORD = 0x0004;

    private static final int ANIM_IDLE = 0;
    private static final int ANIM_BOUNCE = 1;

    /** {@code $34(a0)}: 0, 8, $10, $18 or $20. */
    private int extension;

    private final ObjectAnimationState animationState =
            new ObjectAnimationState(buildAnimationSet(), ANIM_IDLE, 0);

    public S3kDezRetractingSpringObjectInstance(ObjectSpawn spawn) {
        super(spawn, "DEZRetractingSpring");
    }

    @Override
    public int romObjectCodePointerHighWord() {
        return ROM_CODE_POINTER_HIGH_WORD;
    }

    // --- solid contract ---

    @Override
    public SolidObjectParams getSolidParams() {
        return SolidObjectParams.of(SOLID_HALF_WIDTH, SOLID_HALF_HEIGHT, SOLID_HALF_HEIGHT);
    }

    @Override
    public boolean isTopSolidOnly() {
        // jsr (SolidObjectTop_1P).l (:94166): the piston has no sides and no underside.
        return true;
    }

    @Override
    public boolean usesStickyContactBuffer() {
        // SolidObjectTop_1P keeps a rider only inside the exact d1*2 window (:1E2D0-1E2E0).
        return false;
    }

    @Override
    public SolidRoutineProfile getSolidRoutineProfile() {
        return SolidRoutineProfile.topSolid(usesStickyContactBuffer());
    }

    @Override
    public int getOnScreenHalfWidth() {
        // move.b #$10,width_pixels(a0) (:94103): render and balance widths follow the solid one.
        return SOLID_HALF_WIDTH;
    }

    // --- per-update behaviour ---

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        AbstractPlayableSprite playerOne = asSprite(mainPlayer(playerEntity));
        if (playerOne != null) {
            trackPlayerHeight(playerOne);
        }
        // loc_4813C :94156-94158. The Y never changes; only the piston's X does.
        updateDynamicSpawn((baseX() + extensionOffset()) & 0xFFFF, getY());
        // lea (Ani_DEZRetractingSpring).l,a1 / jsr (Animate_Sprite).l (:94181-94182).
        animationState.update();
    }

    /** {@code loc_480D4} :94113-94143. */
    private void trackPlayerHeight(AbstractPlayableSprite playerOne) {
        // move.w (Player_1+y_pos).w,d0 / sub.w y_pos(a0),d0 / bcs (:94113-94115). The branch is
        // an unsigned borrow, so it asks "is Player 1 above the spring", not "is d0 negative".
        int playerY = playerOne.getCentreY() & 0xFFFF;
        int springY = getY() & 0xFFFF;
        if (playerY < springY) {
            int distanceAbove = springY - playerY;
            // cmpi.w #-$20,d0 / bge (:94132-94133): a player exactly $20 above does nothing.
            if (distanceAbove <= DEAD_BAND || extension == 0) {
                return;
            }
            // tst.w $34(a0) / move.w $32(a0),d1 / cmp.w $34(a0),d1 / bne (:94134-94138): the
            // latch sounds only as the spring leaves full extension.
            if (extension == EXTENSION_LIMIT) {
                playLatch();
            }
            extension -= EXTENSION_STEP;
            return;
        }
        int distanceBelow = playerY - springY;
        // cmpi.w #$20,d0 / blt (:94116-94117): a player exactly $20 below does extend it.
        if (distanceBelow < DEAD_BAND || extension == EXTENSION_LIMIT) {
            return;
        }
        // tst.w $34(a0) / bne (:94121-94122): the latch sounds only as it leaves rest.
        if (extension == 0) {
            playLatch();
        }
        extension += EXTENSION_STEP;
    }

    /**
     * {@code loc_48124} :94146-94157. {@code btst #0,status(a0)} skips a negate and
     * {@code btst #1,status(a0)} adds one, so the direction is the exclusive or of the flips.
     */
    private int extensionOffset() {
        boolean xFlip = (spawn.renderFlags() & 1) != 0;
        boolean yFlip = (spawn.renderFlags() & 2) != 0;
        return (xFlip ^ yFlip) ? extension : -extension;
    }

    // --- the launch ---

    @Override
    public void onSolidContact(PlayableEntity playerEntity, SolidContact contact, int frameCounter) {
        // btst #p1_standing_bit,status(a0) / beq (:94167-94168) and the same test for Player 2
        // (:94177-94178): both players launch, and a standing rider launches every frame it is
        // still standing rather than only on the landing frame.
        if (!contact.standing()) {
            return;
        }
        AbstractPlayableSprite player = asSprite(playerEntity);
        if (player == null) {
            return;
        }
        launch(player);
    }

    /** {@code sub_22F98} :47719-47749. */
    private void launch(AbstractPlayableSprite player) {
        boolean reverseGravity = isReverseGravityActive(player);
        // addq.w #8,y_pos(a1) then subi.w #2*8 under the flag (:47721-47724): a net -8.
        NativePositionOps.writeYPosPreserveSubpixel(player, (short) (player.getCentreY()
                + ReverseGravity.mirrorYDelta(reverseGravity, LAUNCH_Y_NUDGE)));
        // move.w $30(a0),y_vel(a1) (:47726): the word the header chose, not a mirrored one.
        player.setYSpeed((short) launchVelocity());
        // bset #1,status(a1) / bclr #3,status(a1) (:47727-47728).
        player.setAir(true);
        player.setOnObject(false);
        // clr.b jumping(a1) / clr.b spin_dash_flag(a1) (:47729-47730).
        player.setJumping(false);
        player.setRollingJump(false);
        // move.b #$10,anim(a1) / move.b #2,routine(a1) (:47731-47732): routine 2 returns even a
        // hurt player to normal control.
        player.setAnimationId(Sonic3kAnimationIds.SPRING.id());
        player.setHurt(false);
        // move.b subtype(a0),d0 / bpl (:47733-47735): only a bit 7 subtype kills x_vel.
        if ((spawn.subtype() & 0x80) != 0) {
            player.setXSpeed((short) 0);
        }
        // btst #0,d0 / beq (:47736-47737): the tumbling launch. No $5D placement sets bit 0.
        if ((spawn.subtype() & 0x01) != 0) {
            applyTumble(player);
        }
        player.recordMgzTopPlatformSpringHandoff(player.getXSpeed(), player.getYSpeed());
        playSpring();
        // The mace of the animation: move.w #1<<8,anim(a0) writes anim = 1, prev_anim = 0
        // (:47720), which restarts the bounce script from its first frame.
        animationState.setAnimId(ANIM_BOUNCE);
        animationState.resetFrameIndex();
    }

    /** {@code loc_22FE0} :47736-47748. */
    private void applyTumble(AbstractPlayableSprite player) {
        player.setGSpeed((short) 1);
        player.setFlipAngle(1);
        player.setAnimationId(0);
        player.setFlipsRemaining(0);
        player.setFlipSpeed(4);
        // btst #1,d0 / bne (:47742-47744): without bit 1 the tumble is a single flip.
        if ((spawn.subtype() & 0x02) == 0) {
            player.setFlipsRemaining(1);
        }
        // btst #0,status(a1) / neg.b flip_angle / neg.w ground_vel (:47745-47748).
        if (player.getDirection() == com.openggf.physics.Direction.LEFT) {
            player.setFlipAngle(-player.getFlipAngle());
            player.setGSpeed((short) -player.getGSpeed());
        }
    }

    /**
     * {@code move.w word_4808A(pc,d0.w),$30(a0)} with {@code d0 = subtype & 2} (:94107-94109).
     * {@code word_4808A} (:94093-94095) is the {@code Spring_Powers} pair.
     */
    public int launchVelocity() {
        return SpringBounceHelper.strengthFromSubtype(spawn.subtype());
    }

    // --- rendering ---

    /**
     * {@code move.l #Map_DEZRetractingSpring,mappings(a0)} and
     * {@code make_art_tile(ArtTile_DEZ2Extra,1,0)} (:94099-94100). Frame 0 is the retracted
     * piston, frame 1 the compressed pose and frame 2 the extended one.
     */
    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.DEZ_RETRACTING_SPRING);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        boolean hFlip = (spawn.renderFlags() & 1) != 0;
        boolean vFlip = (spawn.renderFlags() & 2) != 0;
        renderer.drawFrameIndex(animationState.getMappingFrame(), getX(), getY(), hFlip, vFlip);
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.fromS3kWord(PRIORITY_WORD);
    }

    /**
     * {@code Ani_DEZRetractingSpring} (:94186, ROM {@code $000481A2}).
     * {@code byte_481A6} is {@code $F, 0, $FF}: frame 0 held sixteen updates, then restart.
     * {@code byte_481A9} is {@code 0, 1,0,0,2,2,2,2,2,2, $FD, 0}: nine one-update frames, then
     * {@code $FD} switches back to script 0.
     */
    private static SpriteAnimationSet buildAnimationSet() {
        SpriteAnimationSet set = new SpriteAnimationSet();
        set.addScript(ANIM_IDLE, new SpriteAnimationScript(
                0x0F, List.of(0), SpriteAnimationEndAction.LOOP, 0));
        set.addScript(ANIM_BOUNCE, new SpriteAnimationScript(
                0x00, List.of(1, 0, 0, 2, 2, 2, 2, 2, 2),
                SpriteAnimationEndAction.SWITCH, ANIM_IDLE));
        return set;
    }

    // --- helpers ---

    /** {@code move.w x_pos(a0),$44(a0)} (:94105): written once at init and never changed. */
    private int baseX() {
        return spawn.x() & 0xFFFF;
    }

    /** {@code lea (Player_1).w,a1} (:94163): the extension never answers to the sidekick. */
    private PlayableEntity mainPlayer(PlayableEntity updatePlayer) {
        if (tryServices() == null || services().playerQuery() == null) {
            return updatePlayer;
        }
        PlayableEntity main = services().playerQuery().mainPlayerOrNull();
        return main == null ? updatePlayer : main;
    }

    /** {@code tst.b (Reverse_gravity_flag).w} as {@code sub_22F98} reads it (:47722). */
    private static boolean isReverseGravityActive(AbstractPlayableSprite player) {
        var gameState = player.currentGameStateOrNull();
        return gameState != null && gameState.isReverseGravityActive();
    }

    private void playLatch() {
        ObjectServices objectServices = tryServices();
        if (objectServices != null) {
            objectServices.playSfx(Sonic3kSfx.SPRING_LATCH.id);
        }
    }

    private void playSpring() {
        ObjectServices objectServices = tryServices();
        if (objectServices != null) {
            objectServices.playSfx(Sonic3kSfx.SPRING.id);
        }
    }

    private static AbstractPlayableSprite asSprite(PlayableEntity entity) {
        return entity instanceof AbstractPlayableSprite sprite ? sprite : null;
    }

    /** Test accessors. */
    public int extensionForTest() {
        return extension;
    }

    public int animIdForTest() {
        return animationState.getAnimId();
    }

    public int mappingFrameForTest() {
        return animationState.getMappingFrame();
    }
}
