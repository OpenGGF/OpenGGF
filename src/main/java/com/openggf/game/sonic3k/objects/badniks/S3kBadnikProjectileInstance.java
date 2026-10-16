package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.TouchResponseProfile;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.objects.ShieldProjectileMotion;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.game.PlayableEntity;

import java.util.List;

/**
 * Shared projectile object for S3K badniks that use Map_Bloominator / Map_MonkeyDude
 * projectile frames and hurt-category touch flags.
 */
public final class S3kBadnikProjectileInstance
        extends AbstractObjectInstance
        implements TouchResponseProvider, RewindRecreatable, com.openggf.game.mutators.NativeObjectFieldTarget {
    private static final int SHIELD_REACTION_BOUNCE = 1 << 3;
    private static final TouchResponseProfile TOUCH_RESPONSE_PROFILE = TouchResponseProfile.fromCanonical(
            new com.openggf.game.profiles.touchresponse.TouchResponseProfile(
                    com.openggf.game.profiles.touchresponse.TouchCategoryDecodeMode.NORMAL,
                    false,
                    true,
                    false,
                    com.openggf.game.profiles.touchresponse.TouchShieldDeflectCapability.SHIELD_DEFLECT,
                    SHIELD_REACTION_BOUNCE,
                    com.openggf.game.profiles.touchresponse.TouchAttackBouncePolicy.STANDARD_ENEMY_KILL,
                    com.openggf.game.profiles.touchresponse.TouchActorContextPolicy.MAIN_FULL_SIDEKICK_HURT_ONLY,
                    com.openggf.game.profiles.touchresponse.TouchOverlapStopPolicy
                            .STOP_AFTER_FIRST_OVERLAP_FOR_ALL_ACTORS));

    // These fields are made non-final (not derivable from the captured
    // ObjectSpawn, which only carries position + the parent badnik's
    // id/subtype/render flags) so GenericFieldCapturer reapplies their exact
    // values after a rewind recreate from forRewindRecreate(...) placeholders.
    private String rendererKey;
    private int mappingFrame;
    private int collisionSizeIndex;
    private int priorityBucket;
    private boolean hFlip;
    private int gravity;

    private int currentX;
    private int currentY;
    private int xVelocity;
    private int yVelocity;
    private int xSubpixel;
    private int ySubpixel;
    private boolean collisionEnabled = true;
    private boolean initPending = true;
    private boolean nativeRetirementPending;

    S3kBadnikProjectileInstance(ObjectSpawn ownerSpawn,
            String rendererKey,
            int mappingFrame,
            int x,
            int y,
            int xVelocity,
            int yVelocity,
            int gravity,
            int collisionSizeIndex,
            int priorityBucket,
            boolean hFlip) {
        super(ownerSpawn, "S3kBadnikProjectile");
        this.rendererKey = rendererKey;
        this.mappingFrame = mappingFrame;
        this.currentX = x;
        this.currentY = y;
        this.xVelocity = xVelocity;
        this.yVelocity = yVelocity;
        this.gravity = gravity;
        this.collisionSizeIndex = collisionSizeIndex;
        this.priorityBucket = priorityBucket;
        this.hFlip = hFlip;
    }

    private S3kBadnikProjectileInstance(ObjectSpawn spawn) {
        this(spawn, "", 0, spawn.x(), spawn.y(), 0, 0, 0, 0, 0, false);
    }

    /**
     * Rewind recreate factory. Generic recreate only needs a structurally-valid
     * instance positioned at the captured spawn; the non-final differentiator
     * fields (rendererKey, mappingFrame, gravity, collisionSizeIndex,
     * priorityBucket, hFlip) and the in-flight motion scalars are reapplied by
     * the generic field capturer immediately after recreate, so placeholders
     * are passed here. {@code appendRenderCommands} tolerates the empty
     * rendererKey for the single pre-reapply frame (getRenderer returns null).
     */
    public static S3kBadnikProjectileInstance forRewindRecreate(ObjectSpawn spawn) {
        return new S3kBadnikProjectileInstance(spawn, "", 0, spawn.x(), spawn.y(), 0, 0, 0, 0, 0, false);
    }

    @Override
    public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        ObjectSpawn spawn = ctx.spawn() != null
                ? ctx.spawn()
                : new ObjectSpawn(0, 0, 0, 0, 0, false, 0);
        return forRewindRecreate(spawn);
    }

    @Override public Kind fieldKind() { return Kind.PROJECTILE; }
    @Override public void displaceForField(int dx, int dy) {
        currentX = (currentX + dx) & 0xFFFF;
        currentY = (currentY + dy) & 0xFFFF;
        updateDynamicSpawn(currentX, currentY);
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        if (nativeRetirementPending) {
            // Go_Delete_Sprite wrote Delete_Current_Sprite/status7 last pass;
            // this dispatch clears the physical record without another move.
            setDestroyed(true);
            return;
        }
        // S3KBadnikProjectile_Init reads ObjDat, enables collision and shield
        // reaction, then falls through Main/motion in this same dispatch.
        initPending = false;
        // MoveSprite / MoveSprite_LightGravity order:
        // use old y_vel for movement this frame, then apply gravity.
        int oldYVel = yVelocity;
        yVelocity += gravity;

        int xPos24 = (currentX << 8) | (xSubpixel & 0xFF);
        int yPos24 = (currentY << 8) | (ySubpixel & 0xFF);
        xPos24 += xVelocity;
        yPos24 += oldYVel;
        currentX = xPos24 >> 8;
        currentY = yPos24 >> 8;
        xSubpixel = xPos24 & 0xFF;
        ySubpixel = yPos24 & 0xFF;

        // Sprite_CheckDeleteTouchXY: unsigned coarse X <= $280 (native
        // width), then unsigned (y_pos - Camera_Y_pos + $80) <= $200.
        // Preserve the existing declared viewport extension for coarse X;
        // the Y window is the native -128..384, independent of screen height.
        if (isCoarseXOutOfRange(currentX, cameraLeft(), coarseXCullRange())
                || ((currentY - cameraTop() + 0x80) & 0xFFFF) > 0x200) {
            nativeRetirementPending = true;
        }
    }

    @Override
    public boolean usesCustomOutOfRangeCheck() {
        // Sprite_CheckDeleteTouchXY owns the exact XY tail. A generic
        // second coarse-X check would erase Go_Delete_Sprite's marked SST
        // before Delete_Current_Sprite can run on its following dispatch.
        return true;
    }

    @Override
    public boolean isCustomOutOfRange(int cameraX) {
        return false;
    }

    @Override
    public int getCollisionFlags() {
        if (initPending || nativeRetirementPending || !collisionEnabled) {
            return 0;
        }
        // HURT category + collision size index (ObjDat3 flags: $98).
        return 0x80 | (collisionSizeIndex & 0x3F);
    }

    @Override
    public boolean publishesTouchResponseListEntryThisFrame() {
        // The XY tail calls Add_SpriteToCollisionResponseList and Draw_Sprite
        // only on the admitted path, even when shield deflection cleared hurt.
        return !initPending && !nativeRetirementPending;
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public boolean usesCurrentTouchResponseState() {
        // loc_86D5E runs the child motion callback before
        // Sprite_CheckDeleteTouchXY adds the pointer to the collision-response
        // list. The next player slot therefore observes that post-movement
        // position, as with Obj37 bouncing rings.
        return true;
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
    public ObjectSpawn getSpawn() {
        return buildSpawnAt(currentX, currentY);
    }

    @Override
    public int getX() {
        return currentX;
    }

    @Override
    public int getY() {
        return currentY;
    }

    @Override
    public int getPriorityBucket() {
        return priorityBucket;
    }

    @Override
    public int getShieldReactionFlags() {
        return SHIELD_REACTION_BOUNCE;
    }

    @Override
    public boolean onShieldDeflect(PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        if (player == null) {
            return false;
        }

        // ROM: Touch_ChkHurt_Bounce_Projectile / ShieldTouchResponse.
        var reflected = ShieldProjectileMotion.awayFrom(currentX, currentY,
                player.getCentreX(), player.getCentreY());
        xVelocity = reflected.x();
        yVelocity = reflected.y();
        collisionEnabled = false;
        return true;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (initPending || nativeRetirementPending) {
            return;
        }
        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager == null) {
            return;
        }
        PatternSpriteRenderer renderer = renderManager.getRenderer(rendererKey);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        renderer.drawFrameIndex(mappingFrame, currentX, currentY, hFlip, false);
    }

}
