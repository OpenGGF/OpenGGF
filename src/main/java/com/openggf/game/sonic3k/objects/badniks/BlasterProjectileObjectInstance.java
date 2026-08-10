package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.GenericFieldCapturer;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/** Concrete independent {@code ChildObjDat_8972E/89746} projectile slots. */
public final class BlasterProjectileObjectInstance extends AbstractObjectInstance
        implements TouchResponseProvider, RewindRecreatable {
    private static final int[] PRIMARY = {1, 5, 6, 0xFC};
    private static final int[] SECONDARY = {2, 7, 8, 9, 0xA, 0xFC};
    private static final int[] PRIMARY_FRAMES = {5, 6};
    private static final int[] SECONDARY_FRAMES = {7, 8, 9, 0xA};
    private static final int SHIELD_REACTION = 1 << 3;
    private static final TouchResponseProfile TOUCH = new TouchResponseProfile(
            TouchCategoryDecodeMode.NORMAL, false, true, false,
            TouchShieldDeflectCapability.SHIELD_DEFLECT, SHIELD_REACTION,
            TouchAttackBouncePolicy.STANDARD_ENEMY_KILL,
            TouchActorContextPolicy.MAIN_FULL_SIDEKICK_HURT_ONLY,
            TouchOverlapStopPolicy.STOP_AFTER_FIRST_OVERLAP_FOR_ALL_ACTORS);

    private BlasterBadnikInstance owner;
    private int ownerSlot;
    private boolean primary;
    private int currentX;
    private int currentY;
    private int xFixed;
    private int yFixed;
    private int xVelocity;
    private int yVelocity;
    private int mappingFrame;
    private int animationTimer;
    private int animationIndex;
    private boolean initialized;
    private boolean facingRight;
    private boolean collisionEnabled;

    private BlasterProjectileObjectInstance(ObjectSpawn spawn, BlasterBadnikInstance owner,
            boolean primary, int x, int y, int xVelocity, int yVelocity) {
        super(spawn, primary ? "BlasterPrimaryProjectile" : "BlasterSecondaryProjectile");
        this.owner = owner;
        ownerSlot = owner == null ? -1 : owner.getSlotIndex();
        this.primary = primary;
        currentX = x;
        currentY = y;
        xFixed = x << 16;
        yFixed = y << 16;
        this.xVelocity = xVelocity;
        this.yVelocity = yVelocity;
        mappingFrame = primary ? 5 : 7;
        animationTimer = 0; // same-frame fall-through advances 5->6 / 7->8.
        collisionEnabled = primary;
        facingRight = owner != null && !owner.badnikFacingLeft();
    }

    /** Minimal probe shape used by the generic dynamic rewind codec. */
    private BlasterProjectileObjectInstance(ObjectSpawn spawn) {
        this(spawn, null, true, spawn.x(), spawn.y(), 0, 0);
    }

    static BlasterProjectileObjectInstance primary(ObjectSpawn spawn, BlasterBadnikInstance owner) {
        boolean right = owner != null && !owner.badnikFacingLeft();
        int x = (owner == null ? spawn.x() : owner.getX()) + (right ? 0x20 : -0x20);
        int y = (owner == null ? spawn.y() : owner.getY()) - 0x20;
        return new BlasterProjectileObjectInstance(spawn, owner, true, x, y, right ? 0x200 : -0x200, -0x400);
    }

    static BlasterProjectileObjectInstance secondary(ObjectSpawn spawn, BlasterBadnikInstance owner) {
        boolean right = owner != null && !owner.badnikFacingLeft();
        int x = (owner == null ? spawn.x() : owner.getX()) + (right ? 0x0C : -0x0C);
        int y = (owner == null ? spawn.y() : owner.getY()) - 4;
        return new BlasterProjectileObjectInstance(spawn, owner, false, x, y, right ? -0x100 : 0x100, -0x200);
    }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        if (!initialized) initialized = true;
        xFixed += xVelocity << 8;
        yFixed += yVelocity << 8;
        currentX = xFixed >> 16;
        currentY = yFixed >> 16;
        yVelocity += 0x38;
        if (--animationTimer < 0) {
            int[] frames = primary ? PRIMARY_FRAMES : SECONDARY_FRAMES;
            animationIndex = (animationIndex + 1) % frames.length;
            mappingFrame = frames[animationIndex];
            animationTimer = primary ? 1 : 2;
        }
        updateDynamicSpawn(currentX, currentY);
        if (tryServices() != null && services().camera() != null) {
            int cameraX = services().camera().getX();
            int cameraY = services().camera().getY();
            if (outsideDeleteBounds(currentX, currentY, cameraX, cameraY)) {
                ObjectLifetimeOps.expireDynamic(this);
            }
        }
    }

    static int[] primaryAnimation() { return PRIMARY.clone(); }
    static int[] secondaryAnimation() { return SECONDARY.clone(); }
    static boolean outsideDeleteBounds(int x, int y, int cameraX, int cameraY) {
        return isCoarseXOutOfRange(x, cameraX, 0x280)
                || ((((y - cameraY) + 0x80) & 0xFFFF) > 0x200);
    }
    BlasterBadnikInstance parentMember() { return owner; }
    int familySlot() { return ownerSlot; }
    boolean primaryKind() { return primary; }
    int mappingFrame() { return mappingFrame; }
    @Override public int getX() { return currentX; }
    @Override public int getY() { return currentY; }
    @Override public int getPriorityBucket() { return primary ? 5 : 4; }
    @Override public int getCollisionFlags() { return primary && collisionEnabled ? 0x98 : 0; }
    @Override public int getCollisionProperty() { return 0; }
    @Override public int getShieldReactionFlags() { return primary ? SHIELD_REACTION : 0; }
    @Override public TouchResponseProfile getTouchResponseProfile() { return TOUCH; }
    @Override public TouchResponseProfile getTouchResponseProfile(boolean multi) { return TOUCH; }
    @Override public boolean usesCurrentTouchResponseState() { return true; }

    @Override public boolean onShieldDeflect(PlayableEntity entity) {
        if (!primary || !(entity instanceof AbstractPlayableSprite player)) return false;
        int angle = TrigLookupTable.calcAngle((short) (player.getCentreX() - currentX),
                (short) (player.getCentreY() - currentY));
        xVelocity = -((TrigLookupTable.cosHex(angle) * 0x800) >> 8);
        yVelocity = -((TrigLookupTable.sinHex(angle) * 0x800) >> 8);
        collisionEnabled = false;
        return true;
    }

    @Override public BlasterProjectileObjectInstance recreateForRewind(RewindRecreateContext context) {
        var restored = new BlasterProjectileObjectInstance(context.spawn(), null,
                true, context.spawn().x(), context.spawn().y(), 0, 0);
        if (context.state() != null && context.state().compactGenericState() != null) {
            GenericFieldCapturer.restoreObjectSubclassScalarsCompact(restored, context.state().compactGenericState());
        }
        return restored;
    }

    @Override protected void afterRewindRestoreSettled() {
        if (owner != null || tryServices() == null || services().objectManager() == null) return;
        for (ObjectInstance candidate : services().objectManager().getActiveObjects()) {
            if (candidate instanceof BlasterBadnikInstance blaster && blaster.getSlotIndex() == ownerSlot) {
                owner = blaster;
                return;
            }
        }
    }

    @Override public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.FBZ_BLASTER);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndexForcedPriority(mappingFrame, currentX, currentY,
                    facingRight, false, -1, true);
        }
    }
}
