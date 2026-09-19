package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kLevelTriggerManager;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
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
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TouchResponseProfile;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * ROM object {@code Obj_LRZShootingTrigger} - object id {@code $1D} in the {@code SKL} pointer set
 * (sonic3k.asm:88279-88348 plus {@code sub_42EC0} at :88349-88361; {@code Obj_LRZShootingTrigger}
 * at ROM {@code $00042DBE}, {@code Map_LRZShootingTrigger} at {@code $42F06}).
 *
 * <p>Two independent behaviours share one object.
 *
 * <p><b>The gun.</b> {@code $30(a0) = (subtype & $F0) >> 2} (:88290-88294) is the reload period, so
 * the high nibble times four is the number of frames between shots. {@code loc_42E00} decrements
 * the word {@code $2E(a0)} and fires when it goes negative, but only while the previous render pass
 * left the object on-screen ({@code tst.b render_flags(a0) / bpl}, :88300-88301). The shot comes
 * from {@code AllocateObjectAfterCurrent}, so it runs in the same frame it is created.
 *
 * <p><b>The target.</b> {@code collision_flags} is {@code $C6}: the {@code $C0} category routes to
 * {@code Touch_Special}, whose size-index list includes 6, and the handler adds 1 to
 * {@code collision_property} for Player 1 and 2 for Player 2 ({@code loc_103FA}, :21185-21193).
 * {@code loc_42E84} then consumes those two bits with {@code bclr}, calling {@code sub_42EC0} once
 * per toucher. The subroutine does nothing unless that player's {@code anim} is 2 - the roll - so
 * running into the trigger on foot only bounces off the solid-less hitbox. When it does fire it
 * negates both of the player's velocities, sets bit 0 of
 * {@code Level_trigger_array[subtype & $F]} ({@code d3} is still the {@code moveq #0,d3} of
 * :88289), and turns this slot into {@code Obj_Explosion} with its collision cleared.
 *
 * <p>The trigger index is the low nibble and the shot period the high one, from the same byte:
 * Lava Reef's two placements, {@code $A0} and {@code $C2}, are therefore trigger 0 at a
 * 40-frame period and trigger 2 at a 48-frame period.
 */
public final class LrzShootingTriggerObjectInstance extends AbstractObjectInstance
        implements TouchResponseProvider, TouchResponseListener, RewindRecreatable,
        RomObjectCodePointerProvider {

    /** {@code move.w #$280,priority(a0)} (sonic3k.asm:88285). */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x0280);
    /** {@code move.b #$10,width_pixels(a0)} / {@code height_pixels(a0)} (:88283-88284). */
    private static final int HALF_SIZE = 0x10;
    /** {@code move.b #$C6,collision_flags(a0)} (:88286). */
    private static final int COLLISION_FLAGS = 0xC6;
    /** {@code cmpi.b #2,anim(a1)} in {@code sub_42EC0} (:88350). */
    private static final int ROLL_ANIMATION = Sonic3kAnimationIds.ROLL.id();

    /**
     * {@code Touch_ChkValue} routes the {@code $C0} category to {@code Touch_Special}
     * (sonic3k.asm:20773-20778), which for this object only increments {@code collision_property}:
     * it never hurts, never deflects and never gives points, and both players are polled every
     * frame, since {@code loc_42E84} consumes one bit per player.
     */
    private static final TouchResponseProfile TOUCH_RESPONSE_PROFILE = TouchResponseProfile.fromCanonical(
            new com.openggf.game.profiles.touchresponse.TouchResponseProfile(
                    com.openggf.game.profiles.touchresponse.TouchCategoryDecodeMode.S3K_SPECIAL_PROPERTY,
                    true,
                    true,
                    true,
                    com.openggf.game.profiles.touchresponse.TouchShieldDeflectCapability.NONE,
                    0,
                    com.openggf.game.profiles.touchresponse.TouchAttackBouncePolicy.STANDARD_ENEMY_KILL,
                    com.openggf.game.profiles.touchresponse.TouchActorContextPolicy.MAIN_FULL_SIDEKICK_HURT_ONLY,
                    com.openggf.game.profiles.touchresponse.TouchOverlapStopPolicy.STOP_AFTER_FIRST_OVERLAP_FOR_MAIN_ONLY));

    // All three are decoded from the spawn and left non-final so the rewind coverage guard sees
    // them as restorable state; recreateForRewind replays the same spawn.
    /** {@code subtype(a0) & $F}: the {@code Level_trigger_array} index. */
    private int triggerIndex;
    /** {@code $30(a0) = (subtype & $F0) >> 2}: frames between shots. */
    private int shotPeriod;
    /** {@code status(a0)} bit 0, the placement's flip flag; it mirrors the shot's X velocity. */
    private boolean flipped;

    /** {@code $2E(a0)}, a word that counts down to the next shot. */
    private int reloadTimer;
    /** {@code collision_property(a0)}: bit 0 Player 1, bit 1 Player 2, set by Touch_Special. */
    private int collisionProperty;
    /** True once {@code sub_42EC0} has rewritten {@code (a0)} to {@code Obj_Explosion}. */
    private boolean triggered;

    public LrzShootingTriggerObjectInstance(ObjectSpawn spawn) {
        super(spawn, "LRZShootingTrigger");
        int subtype = spawn.subtype() & 0xFF;
        this.triggerIndex = subtype & 0x0F;
        // moveq #0,d0 / move.b subtype(a0),d0 / andi.w #$F0,d0 / lsr.w #2,d0 (:88290-88293).
        this.shotPeriod = (subtype & 0xF0) >> 2;
        this.flipped = (spawn.renderFlags() & 0x1) != 0;
    }

    /**
     * {@code Obj_LRZShootingTrigger} sits at ROM {@code $00042DBE} (sonic3k.lst); its whole code
     * block lies in one bank, so the high word {@code sub_13EFC} latches is {@code $0004}.
     */
    @Override
    public int romObjectCodePointerHighWord() {
        return 0x0004;
    }

    @Override
    public LrzShootingTriggerObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return ObjectConstructionContext.construct(ctx.objectServices(),
                () -> new LrzShootingTriggerObjectInstance(ctx.spawn()));
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        if (triggered) {
            return;
        }
        advanceGun();
        consumeTouches(playerEntity);
    }

    /** {@code loc_42E00} (sonic3k.asm:88296-88334). */
    private void advanceGun() {
        // subq.w #1,$2E(a0) / bpl.s loc_42E84: a word decrement, so the fire frame is the one the
        // counter first goes negative on, not the one it reaches zero on.
        reloadTimer = (short) (reloadTimer - 1);
        if (reloadTimer >= 0) {
            return;
        }
        reloadTimer = shotPeriod;
        // tst.b render_flags(a0) / bpl.s loc_42E84 (:88299-88301).
        if (!onScreen()) {
            return;
        }
        int x = getCentreX();
        int y = getCentreY();
        boolean shotFlipped = flipped;
        spawnChild(() -> new LrzShootingTriggerProjectileInstance(buildSpawnAt(x, y), shotFlipped));
        playSfx(Sonic3kSfx.PROJECTILE.id);
    }

    /** {@code loc_42E84} (sonic3k.asm:88335-88348). */
    private void consumeTouches(PlayableEntity playerEntity) {
        if (collisionProperty == 0) {
            return;
        }
        // bclr #0,collision_property(a0) / bsr.s sub_42EC0, then the same for bit 1 and Player_2.
        if ((collisionProperty & 0x1) != 0) {
            collisionProperty &= ~0x1;
            applyRollingHit(playerEntity);
        }
        if (!triggered && (collisionProperty & 0x2) != 0) {
            collisionProperty &= ~0x2;
            applyRollingHit(nativeP2OrNull());
        }
    }

    /** {@code sub_42EC0} (sonic3k.asm:88349-88361). */
    private void applyRollingHit(PlayableEntity entity) {
        if (!(entity instanceof AbstractPlayableSprite player)) {
            return;
        }
        // cmpi.b #2,anim(a1) / bne.s locret_42EE6: only a rolling player arms the trigger.
        if (player.getAnimationId() != ROLL_ANIMATION) {
            return;
        }
        player.setXSpeed((short) -player.getXSpeed());
        player.setYSpeed((short) -player.getYSpeed());
        // bset d3,(a3) with d3 still the moveq #0,d3 of loc_42E84.
        Sonic3kLevelTriggerManager.setBit(triggerIndex, 0);
        becomeExplosion();
    }

    /**
     * {@code move.l #Obj_Explosion,(a0) / move.b #2,routine(a0)} with both collision bytes cleared
     * (sonic3k.asm:88355-88359). The engine has no in-place routine rewrite, so the slot is
     * destroyed and an explosion allocated at the same position; routine 2 is the plain explosion,
     * without the {@code Obj_Animal} that routine 0 would create.
     */
    private void becomeExplosion() {
        triggered = true;
        int x = getCentreX();
        int y = getCentreY();
        try {
            spawnChild(() -> new ExplosionObjectInstance(buildSpawnAt(x, y), services()));
        } catch (Exception ignored) {
            // A probe-constructed trigger has no object manager; the state change still applies.
        }
        ObjectLifetimeOps.destroyLatched(this);
    }

    /** The engine's {@code render_flags} bit 7 equivalent; a probe build has no camera bounds. */
    private boolean onScreen() {
        try {
            return isWithinSolidContactBounds();
        } catch (Exception e) {
            return false;
        }
    }

    private void playSfx(int id) {
        try {
            services().playSfx(id);
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
    public void onTouchResponse(PlayableEntity player, TouchResponseResult result, int frameCounter) {
        if (result.category() != TouchCategory.SPECIAL
                || !(player instanceof AbstractPlayableSprite sprite)) {
            return;
        }
        // loc_103FA (sonic3k.asm:21185-21193): +1 when the main character touched, +2 when the
        // sidekick did, so the byte says which player(s) overlapped this frame.
        collisionProperty = (collisionProperty + (sprite.isCpuControlled() ? 2 : 1)) & 0xFF;
    }

    @Override
    public int getCollisionFlags() {
        return triggered ? 0 : COLLISION_FLAGS;
    }

    @Override
    public int getCollisionProperty() {
        return collisionProperty;
    }

    @Override
    public TouchResponseProfile getTouchResponseProfile() {
        return TOUCH_RESPONSE_PROFILE;
    }

    @Override
    public TouchResponseProfile getTouchResponseProfile(boolean multiRegionSource) {
        return TOUCH_RESPONSE_PROFILE;
    }

    @Override
    public boolean requiresContinuousTouchCallbacks() {
        return true;
    }

    @Override
    public boolean usesS3kTouchSpecialPropertyResponse() {
        // Touch_ChkValue (sonic3k.asm:20773-20778) routes the $C0 category to Touch_Special;
        // without this hook the engine decoder maps $C0 to BOSS and never dispatches SPECIAL.
        return true;
    }

    /** ROM {@code $2E(a0)}. */
    public int reloadTimer() {
        return reloadTimer;
    }

    /** ROM {@code $30(a0)}. */
    public int shotPeriod() {
        return shotPeriod;
    }

    /** ROM {@code subtype(a0) & $F}. */
    public int triggerIndex() {
        return triggerIndex;
    }

    public boolean hasTriggered() {
        return triggered;
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
        // make_art_tile(ArtTile_LRZMisc,3,0) (sonic3k.asm:88281) leaves the priority bit clear.
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
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.LRZ_SHOOTING_TRIGGER);
        if (renderer == null) {
            return;
        }
        renderer.drawFrameIndex(0, getX(), getY(), false, false);
    }
}
