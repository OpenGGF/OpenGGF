package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseProfile;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * A shot from the Lava Reef miniboss's hand ({@code loc_78A02}, sonic3k.asm:160455-160465,
 * created through {@code ChildObjDat_78D90} by {@code CreateChild10_NormalAdjusted}).
 *
 * <p>{@code sub_78BAA} (sonic3k.asm:160606-160619) picks the velocity from {@code word_78BCA} by
 * the subtype the hand stamped on it -- its shot counter -- reading
 * {@code word_78BCA-4(pc,d0.w)} with {@code d0 = subtype << 2}, so shot 1 takes the first pair,
 * shot 2 the second and shot 3 the third. The X component is negated when the <b>firing hand's</b>
 * {@code render_flags} bit 0 is set, which is how the mirrored ring shoots the other way.
 *
 * <p>{@code word_78D72} gives it a {@code $04 $04} size on mapping frame 9 with
 * {@code collision_flags $98}. {@code bset #3,$2B(a0)} sets shield_reaction's deflection
 * bit; it is independent of the offscreen retirement in {@code Sprite_CheckDeleteTouchXY}.
 */
final class LrzMinibossProjectileChild extends AbstractObjectInstance
        implements RewindRecreatable, TouchResponseProvider {

    /** {@code word_78BCA}: three (x, y) pairs, one per shot of the volley. */
    private static final int[][] SHOT_VELOCITIES = {
            {0x200, 0x300},
            {0x200, 0x200},
            {0x200, 0x100},
    };
    /** {@code ChildObjDat_78D90}: {@code dc.b 8,0} positional offset. */
    private static final int SPAWN_X_OFFSET = 8;
    private static final int SPAWN_Y_OFFSET = 0;
    /** {@code word_78D72}: mapping frame 9, collision flags {@code $98}, priority 0. */
    private static final int MAPPING_FRAME = 9;
    private static final int COLLISION_FLAGS = 0x98;
    private static final int PRIORITY_BUCKET = 0;
    private static final int SUBPIXEL_SHIFT = 8;

    // Not final: see LrzMinibossOrbiterChild - a final scalar is uncapturable for rewind.
    private boolean mirrored;
    private int xFixed;
    private int yFixed;
    private int xVelocity;
    private int yVelocity;
    /** Touch_ChkHurt_Bounce_Projectile clears collision_flags permanently. */
    private boolean collisionEnabled = true;
    private static final TouchResponseProfile TOUCH_PROFILE = TouchResponseProfile.fromCanonical(
            com.openggf.game.profiles.touchresponse.TouchResponseProfile.singleRegionShieldDeflect());

    LrzMinibossProjectileChild(int handX, int handY, int shotNumber, boolean mirrored) {
        this(new ObjectSpawn(handX + (mirrored ? -SPAWN_X_OFFSET : SPAWN_X_OFFSET),
                handY + SPAWN_Y_OFFSET, 0x9D, shotNumber, 0, false, 0), mirrored);
    }

    private LrzMinibossProjectileChild(ObjectSpawn spawn, boolean mirrored) {
        super(spawn, "LRZMinibossShot");
        this.mirrored = mirrored;
        this.xFixed = spawn.x() << SUBPIXEL_SHIFT;
        this.yFixed = spawn.y() << SUBPIXEL_SHIFT;
        // sub_78BAA: word_78BCA-4(pc, subtype<<2), so shot 1 is index 0.
        int index = Math.max(0, Math.min(SHOT_VELOCITIES.length - 1, spawn.subtype() - 1));
        this.xVelocity = mirrored ? -SHOT_VELOCITIES[index][0] : SHOT_VELOCITIES[index][0];
        this.yVelocity = SHOT_VELOCITIES[index][1];
    }

    @Override
    public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new LrzMinibossProjectileChild(ctx.spawn(), mirrored);
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        // loc_78A1C: MoveSprite2 then Sprite_CheckDeleteTouchXY. No gravity.
        xFixed += xVelocity;
        yFixed += yVelocity;
        if (!spriteCheckDeleteTouchXYKeepsAlive()) {
            ObjectLifetimeOps.expireDynamic(this);
            return;
        }
        updateDynamicSpawn(getCentreX(), getCentreY());
    }

    /**
     * {@code Sprite_CheckDeleteTouchXY} (sonic3k.asm:179032-179043), literally: the X test is on
     * the <b>coarse</b> position against {@code Camera_X_pos_coarse_back} with a {@code $280}
     * window, and the Y test is {@code y_pos - Camera_Y_pos + $80} against {@code $200}. Both are
     * {@code bhi}, i.e. unsigned and exclusive, so a value exactly on the bound survives.
     * {@code Camera_X_pos_coarse_back} is refreshed by {@code Load_Sprites} as
     * {@code (Camera_X_pos - $80) & $FF80} (sonic3k.asm:37545-37553).
     *
     * <p>The window is deliberately not symmetric and deliberately coarse: a shot can sit up to
     * {@code $7F} pixels further left than an eyeballed box would allow, and the Y half reaches
     * {@code $180} below the camera rather than {@code $140}.
     */
    private boolean spriteCheckDeleteTouchXYKeepsAlive() {
        var services = tryServices();
        if (services == null || services.camera() == null) {
            return true;
        }
        // ROM reads the 320px camera word; retain its spawn/cull window when
        // the viewport is centered, rather than shifting gameplay with the view.
        int cameraX = LrzMinibossInstance.nativeFramedCameraX(services.camera());
        int cameraY = Short.toUnsignedInt(services.camera().getY());
        int coarseBack = (cameraX - 0x80) & 0xFF80;
        int xDistance = ((getCentreX() & 0xFF80) - coarseBack) & 0xFFFF;
        if (xDistance > 0x280) {
            return false;
        }
        int yDistance = (getCentreY() - cameraY + 0x80) & 0xFFFF;
        return yDistance <= 0x200;
    }

    int getCentreX() { return xFixed >> SUBPIXEL_SHIFT; }
    int getCentreY() { return yFixed >> SUBPIXEL_SHIFT; }

    @Override public int getX() { return getCentreX() - 4; }
    @Override public int getY() { return getCentreY() - 4; }
    @Override public int getCollisionFlags() { return collisionEnabled ? COLLISION_FLAGS : 0; }

    @Override
    public int getShieldReactionFlags() {
        // loc_78A02: bset #3,$2B(a0). sonic3k.constants.asm names $2B shield_reaction.
        return 1 << 3;
    }

    @Override
    public TouchResponseProfile getTouchResponseProfile() {
        return TOUCH_PROFILE;
    }

    @Override
    public TouchResponseProfile getTouchResponseProfile(boolean multiRegionSource) {
        return TOUCH_PROFILE;
    }

    @Override
    public boolean onShieldDeflect(PlayableEntity entity) {
        if (!(entity instanceof AbstractPlayableSprite player)) return false;
        // Touch_ChkHurt_Bounce_Projectile: signed word delta -> GetArcTan ->
        // GetSineCosine -> muls #-$800/asr.l #8, then clr.b collision_flags.
        // loc_78A1C continues MoveSprite2 with the new velocity and retained fractions.
        int angle = TrigLookupTable.calcAngle((short) (player.getCentreX() - getCentreX()),
                (short) (player.getCentreY() - getCentreY()));
        xVelocity = (TrigLookupTable.cosHex(angle) * -0x800) >> 8;
        yVelocity = (TrigLookupTable.sinHex(angle) * -0x800) >> 8;
        collisionEnabled = false;
        return true;
    }

    /** A shot has no hit count of its own; it hurts and is never destroyed by an attack. */
    @Override public int getCollisionProperty() { return 0; }
    @Override public boolean isPersistent() { return true; }
    @Override public int getPriorityBucket() { return PRIORITY_BUCKET; }
    @Override public boolean isHighPriority() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.LRZ_MINIBOSS);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        renderer.drawFrameIndex(MAPPING_FRAME, getCentreX(), getCentreY(), mirrored, false);
    }
}
