package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/** One element of ChildObjDat_70F04's nine-node flame stack. */
public final class FbzEndBossFlameChild extends AbstractFbzEndBossChild implements TouchResponseProvider {
    public static final int ACTIVE_COLLISION_FLAGS = 0x8B;
    public static final int FIRE_SHIELD_REACTION = 0x10;
    public static final int CONTINUOUS_SFX_ID = 0x4F;
    private static final TouchResponseProfile TOUCH_RESPONSE_PROFILE = new TouchResponseProfile(
            TouchCategoryDecodeMode.NORMAL, false, true, false,
            TouchShieldDeflectCapability.NONE, FIRE_SHIELD_REACTION,
            TouchAttackBouncePolicy.STANDARD_ENEMY_KILL,
            TouchActorContextPolicy.MAIN_FULL_SIDEKICK_HURT_ONLY,
            TouchOverlapStopPolicy.STOP_AFTER_FIRST_OVERLAP_FOR_ALL_ACTORS);
    private static final int[] TIMERS = {0x50,0x4D,0x4A,0x47,0x44,0x41,0x3E,0x3B,0};
    private static final int[] X_OFFSETS = {0,8,-8,0,0,0,0,0,0};
    private static final int[] Y_OFFSETS = {-0x68,-0x5C,-0x5C,-0x4C,-0x3C,-0x2C,-0x1C,-0x0C,-0x10};
    /** byte_70F70: the leading duplicate pair is skipped by Animate_RawMultiDelay's initial +2. */
    private static final int[] FLAME_FRAMES = {0, 1, 2, 3, 4};
    private static final int[] FLAME_DELAYS = {2, 3, 4, 5, 6};
    /** byte_70F61. */
    private static final int[] EXPLOSION_FRAMES = {0, 1, 2, 3, 4, 5};
    private static final int[] EXPLOSION_DELAYS = {2, 2, 3, 4, 5, 6};
    /** byte_70F3E. */
    private static final int[] BODY_FRAMES = {4, 5, 6, 7, 4, 5, 6, 7, 4, 5, 6, 7, 4, 5, 6, 7};
    private static final int[] BODY_DELAYS = {3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3};
    private FbzEndBossWeaponChild weapon;
    private int flameIndex;
    private boolean initialized;
    private int waitTimer;
    private boolean active;
    private boolean visibleAndTouching;
    private int rawAnimationIndex;
    private int animationFrameTimer;
    private int mappingFrame;

    FbzEndBossFlameChild(FbzEndBossInstance boss, FbzEndBossWeaponChild weapon, int flameIndex) {
        super(boss, "flame:" + flameIndex, "FBZEndBossFlame", flameIndex);
        this.weapon = weapon;
        this.flameIndex = flameIndex;
        this.waitTimer = TIMERS[flameIndex];
        this.x = weapon.getX() + X_OFFSETS[flameIndex];
        this.y = weapon.getY() + Y_OFFSETS[flameIndex];
        this.mappingFrame = initialMappingFrame(flameIndex);
    }
    public FbzEndBossFlameChild(com.openggf.level.objects.ObjectSpawn spawn) {
        super(spawn, "flame:0", "FBZEndBossFlame");
    }
    public static int nativeVolleyCount() { return 9; }
    public static int[] nativeTimers() { return TIMERS.clone(); }
    public static int[] nativeYOffsets() { return Y_OFFSETS.clone(); }
    public int getCollisionFlags() { return visibleAndTouching && !isDestroyed() ? ACTIVE_COLLISION_FLAGS : 0; }
    @Override public int getCollisionProperty() { return 0; }
    @Override public int getShieldReactionFlags() { return FIRE_SHIELD_REACTION; }
    public int getShieldReaction() { return FIRE_SHIELD_REACTION; }
    @Override public TouchResponseProfile getTouchResponseProfile() { return TOUCH_RESPONSE_PROFILE; }
    @Override public TouchResponseProfile getTouchResponseProfile(boolean multiRegionSource) {
        return TOUCH_RESPONSE_PROFILE;
    }
    @Override public void update(int frameCounter, PlayableEntity player) {
        if (weapon == null || weapon.isDestroyed()) { ObjectLifetimeOps.expireDynamic(this); return; }
        if (!initialized) { initialized = true; return; }
        if (!active) {
            if (--waitTimer < 0) active = true;
            return;
        }
        services().playSfx(Sonic3kSfx.FLAMETHROWER_QUIET.id);
        x = weapon.getX() + X_OFFSETS[flameIndex];
        y = weapon.getY() + Y_OFFSETS[flameIndex];
        if (--animationFrameTimer >= 0) return;

        int[] frames = frames(flameIndex);
        if (rawAnimationIndex >= frames.length) {
            ObjectLifetimeOps.expireDynamic(this);
            return;
        }
        mappingFrame = frames[rawAnimationIndex];
        animationFrameTimer = delays(flameIndex)[rawAnimationIndex];
        rawAnimationIndex++;
        visibleAndTouching = true;
    }
    @Override public boolean isHighPriority() { return true; }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (!visibleAndTouching || isDestroyed()) return;
        PatternSpriteRenderer renderer;
        if (flameIndex <= 2) {
            renderer = services().renderManager().getRenderer(Sonic3kObjectArtKeys.FBZ_END_BOSS_FLAME);
        } else if (flameIndex <= 7) {
            renderer = services().renderManager().getBossExplosionRenderer();
        } else {
            renderer = services().renderManager().getRenderer(Sonic3kObjectArtKeys.FBZ_END_BOSS);
        }
        if (renderer != null && renderer.isReady()) renderer.drawFrameIndex(mappingFrame, x, y, false, false);
    }

    int mappingFrameForTest() { return mappingFrame; }

    private static int initialMappingFrame(int index) { return index == 8 ? 4 : 0; }
    private static int[] frames(int index) {
        if (index <= 2) return FLAME_FRAMES;
        if (index <= 7) return EXPLOSION_FRAMES;
        return BODY_FRAMES;
    }
    private static int[] delays(int index) {
        if (index <= 2) return FLAME_DELAYS;
        if (index <= 7) return EXPLOSION_DELAYS;
        return BODY_DELAYS;
    }
    @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new FbzEndBossFlameChild(ctx.spawn());
    }
}
