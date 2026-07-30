package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.PowerUpObject;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.Sonic3kObjectArtProvider;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PoweredScreenAttackSpecial;
import com.openggf.level.objects.PoweredScreenAttackable;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.objects.TouchResponseAttackable;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * ROM {@code Obj_SuperTailsBirds}: the four Super Tails attack Flickies.
 *
 * <p>The ROM allocates four consecutive objects. Keeping the four slots in one
 * auxiliary effect preserves their shared round-robin target cursor and makes
 * their reservation graph atomic under rewind.
 */
public final class SuperTailsFlickyFlockObjectInstance extends AbstractObjectInstance
        implements PowerUpObject, RewindRecreatable {
    private static final int COUNT = 4;
    private static final int SUBPIXEL_SHIFT = 8;
    private static final int SEARCH_DELAY = 120;
    private static final int HIT_BOX_BIAS = 0x0C;
    private static final int HIT_BOX_SIZE = 0x18;
    private static final int OFFSCREEN_MARGIN = 0x20;

    private AbstractPlayableSprite owner;
    private boolean visible = true;
    private boolean flyingAway;
    /** ROM {@code _unkF66C}, stored here as a collision-list entry index. */
    private int targetCursor;

    private int x0, y0, xv0, yv0, angle0, delay0, anim0, animTimer0 = 1;
    private int x1, y1, xv1, yv1, angle1 = 0x40, delay1, anim1, animTimer1 = 1;
    private int x2, y2, xv2, yv2, angle2 = 0x80, delay2, anim2, animTimer2 = 1;
    private int x3, y3, xv3, yv3, angle3 = 0xC0, delay3, anim3, animTimer3 = 1;
    private ObjectRefId target0, target1, target2, target3;

    public SuperTailsFlickyFlockObjectInstance(AbstractPlayableSprite owner) {
        this(new ObjectSpawn(owner == null ? 0 : owner.getCentreX() - 0xC0,
                owner == null ? 0 : owner.getCentreY() - 0xC0,
                0, 0, 0, false, 0), owner);
    }

    private SuperTailsFlickyFlockObjectInstance(ObjectSpawn spawn, AbstractPlayableSprite owner) {
        super(spawn, "SuperTailsFlickies");
        this.owner = owner;
        int fx = spawn.x() << SUBPIXEL_SHIFT;
        int fy = spawn.y() << SUBPIXEL_SHIFT;
        x0 = x1 = x2 = x3 = fx;
        y0 = y1 = y2 = y3 = fy;
    }

    @Override
    public SuperTailsFlickyFlockObjectInstance recreateForRewind(RewindRecreateContext context) {
        return new SuperTailsFlickyFlockObjectInstance(context.spawn(), null);
    }

    public boolean isBoundTo(AbstractPlayableSprite player) {
        return owner == player;
    }

    @Override
    public void update(int frameCounter, PlayableEntity ignored) {
        if (owner == null) {
            ObjectLifetimeOps.expireDynamic(this);
            return;
        }
        if (!flyingAway && !isSuperTailsActive()) {
            releaseAllTargets();
            flyingAway = true;
        }

        ensureRomArtLoaded();
        ObjectManager manager = services().objectManager();
        var identities = manager.captureIdentityContext().requireIdentityTable();
        List<ObjectInstance> frozen = manager.poweredAttacks().targetReadView();
        boolean reverseGravity = services().gameState() != null
                && services().gameState().isReverseGravityActive();

        boolean anyOnScreen = false;
        for (int bird = 0; bird < COUNT; bird++) {
            ObjectInstance target = identities.resolve(target(bird));
            boolean releasedMissingTarget = false;
            if (!flyingAway && target(bird) != null
                    && (target == null || target.isDestroyed() || !isTargetEligible(target))) {
                releaseTarget(bird);
                target = null;
                releasedMissingTarget = true;
            }
            if (!flyingAway && target == null && !releasedMissingTarget
                    && decrementSearchDelay(bird)) {
                target = reserveNextTarget(bird, frozen, identities);
            }

            int destinationX;
            int destinationY;
            if (flyingAway) {
                destinationX = flyAwayDestinationXForTest(owner);
                destinationY = owner.getCentreY() - 0xC0;
            } else if (target != null) {
                destinationX = target.getX();
                destinationY = target.getY();
            } else {
                int angle = angle(bird);
                destinationX = owner.getCentreX() + (TrigLookupTable.sinHex(angle) >> 3);
                destinationY = orbitDestinationYForTest(
                        owner.getCentreY(), angle, reverseGravity);
            }

            moveToward(bird, destinationX, destinationY);
            setAngle(bird, (angle(bird) + 2) & 0xFF);
            tickAnimation(bird);

            if (!flyingAway && target != null && targetIsOnScreen(target)
                    && overlapsTarget(bird, target)) {
                hitTarget(bird, target);
                releaseTarget(bird);
            }
            anyOnScreen |= birdIsOnScreen(bird);
        }
        if (flyingAway && !anyOnScreen) {
            ObjectLifetimeOps.expireDynamic(this);
        }
    }

    private boolean isSuperTailsActive() {
        return owner.getSuperStateController() != null
                && owner.getSuperStateController().isSuperTailsFormActive();
    }

    private ObjectInstance reserveNextTarget(int bird, List<ObjectInstance> frozen,
            com.openggf.game.rewind.identity.RewindIdentityTable identities) {
        if (frozen == null || frozen.isEmpty()) return null;
        targetCursor++;
        if (targetCursor >= frozen.size()) {
            targetCursor = 0;
        }
        for (int index = targetCursor; index < frozen.size(); index++) {
            ObjectInstance candidate = frozen.get(index);
            ObjectRefId id = identities.encodeObject(candidate);
            if (id != null && isTargetEligible(candidate) && !isReserved(id)) {
                setTarget(bird, id);
                return candidate;
            }
        }
        return null;
    }

    private boolean isTargetEligible(ObjectInstance candidate) {
        if (candidate == null || candidate.isDestroyed() || !targetIsOnScreen(candidate)
                || !(candidate instanceof TouchResponseProvider provider)) {
            return false;
        }
        int flags = provider.getCollisionFlags() & 0xFF;
        if (flags == 0) return false;
        int category = flags & 0xC0;
        return category == 0 || category == 0xC0;
    }

    private static boolean targetIsOnScreen(ObjectInstance target) {
        return !(target instanceof AbstractObjectInstance object) || object.isOnScreenForTouch();
    }

    private void hitTarget(int bird, ObjectInstance target) {
        if (!(target instanceof TouchResponseProvider provider)) return;
        int flags = provider.getCollisionFlags() & 0xFF;
        int category = flags & 0xC0;
        int property = provider.getCollisionProperty() & 0xFF;
        PlayableEntity p2 = services().playerQuery().nativeP2OrNull();
        PlayableEntity attacker = p2 != null ? p2 : owner;

        if (category == 0) {
            if (property == 0 && target instanceof PoweredScreenAttackable attackable) {
                attackable.onPoweredScreenAttack(attacker);
            } else if (property != 0 && target instanceof TouchResponseAttackable attackable) {
                attackable.onPlayerAttack(attacker,
                        new TouchResponseResult(flags & 0x3F, HIT_BOX_SIZE, HIT_BOX_SIZE,
                                TouchCategory.BOSS));
            }
        } else if (category == 0xC0) {
            if (provider.getTouchResponseProfile().categoryDecodeMode()
                    == com.openggf.level.objects.TouchCategoryDecodeMode.S3K_SPECIAL_PROPERTY) {
                if (target instanceof PoweredScreenAttackSpecial special) {
                    special.orCollisionProperty(2);
                }
            } else if (target instanceof TouchResponseAttackable attackable) {
                attackable.onPlayerAttack(attacker,
                        new TouchResponseResult(flags & 0x3F, HIT_BOX_SIZE, HIT_BOX_SIZE,
                                TouchCategory.BOSS));
            }
        }

        if (p2 != null) {
            NativePositionOps.writeXPosPreserveSubpixel(p2, x(bird));
            NativePositionOps.writeYPosPreserveSubpixel(p2, y(bird));
            p2.setAir(true);
            if (p2 instanceof AbstractPlayableSprite sprite) sprite.setAnimationId(2);
        }
    }

    private boolean overlapsTarget(int bird, ObjectInstance target) {
        int dx = x(bird) - target.getX() + HIT_BOX_BIAS;
        int dy = y(bird) - target.getY() + HIT_BOX_BIAS;
        return Integer.compareUnsigned(dx, HIT_BOX_SIZE) < 0
                && Integer.compareUnsigned(dy, HIT_BOX_SIZE) < 0;
    }

    private void moveToward(int bird, int destinationX, int destinationY) {
        int xVelocity = xv(bird);
        int dx = destinationX - x(bird);
        int xAcceleration = dx < 0 ? -0x20 : 0x20;
        if ((dx < 0 && xVelocity > 0) || (dx >= 0 && xVelocity < 0)) xAcceleration *= 4;
        xVelocity += xAcceleration;

        int yVelocity = yv(bird);
        int destinationWord = destinationY & 0xFFFF;
        int currentWord = y(bird) & 0xFFFF;
        int dy = (short) (destinationWord - currentWord);
        boolean subtractionBorrowed = Integer.compareUnsigned(destinationWord, currentWord) < 0;
        int yAcceleration = verticalAcceleration(dy, subtractionBorrowed, yVelocity);
        yVelocity += yAcceleration;

        setXv(bird, xVelocity);
        setYv(bird, yVelocity);
        int repeatOffset = services().zoneRuntimeState() == null
                ? 0 : services().zoneRuntimeState().levelRepeatOffset();
        int screenYWrap = services().camera() == null
                ? 0xFFFF : services().camera().screenYWrapValue();
        int nextX = ((xFixed(bird) + xVelocity) >> SUBPIXEL_SHIFT) - repeatOffset;
        int nextY = ((yFixed(bird) + yVelocity) >> SUBPIXEL_SHIFT) & screenYWrap;
        setXFixed(bird, (nextX << SUBPIXEL_SHIFT)
                | ((xFixed(bird) + xVelocity) & 0xFF));
        setYFixed(bird, (nextY << SUBPIXEL_SHIFT)
                | ((yFixed(bird) + yVelocity) & 0xFF));
    }

    /** Exact branch graph of ROM {@code Obj_SuperTailsBirds_Move}. */
    static int verticalAcceleration(int delta, boolean subtractionBorrowed, int velocity) {
        boolean farWrapDirection = subtractionBorrowed ? delta > -0x500 : delta >= 0x500;
        if (farWrapDirection) {
            if (velocity <= -0x1000) {
                return velocity < 0 ? 0x80 : 0x20;
            }
            int acceleration = -0x20;
            return velocity >= 0 ? acceleration * 4 : acceleration;
        }
        if (velocity >= 0x1000) {
            return -0x20;
        }
        return velocity < 0 ? 0x80 : 0x20;
    }

    static int orbitDestinationYForTest(int ownerY, int angle, boolean reverseGravity) {
        int yOffset = TrigLookupTable.cosHex(angle) >> 4;
        return ownerY + (reverseGravity ? 0x20 : -0x20) + yOffset;
    }

    static int flyAwayDestinationXForTest(AbstractPlayableSprite owner) {
        return owner.getCentreX() - 0xC0;
    }

    private void tickAnimation(int bird) {
        int timer = animTimer(bird) - 1;
        if (timer < 0) {
            timer = 1;
            setAnim(bird, (anim(bird) + 1) & 1);
        }
        setAnimTimer(bird, timer);
    }

    private boolean decrementSearchDelay(int bird) {
        int delay = delay(bird);
        if (delay == 0) return true;
        setDelay(bird, delay - 1);
        return false;
    }

    private void releaseAllTargets() {
        for (int bird = 0; bird < COUNT; bird++) releaseTarget(bird);
    }

    private void releaseTarget(int bird) {
        setTarget(bird, null);
        setDelay(bird, SEARCH_DELAY);
    }

    private boolean isReserved(ObjectRefId id) {
        return id.equals(target0) || id.equals(target1) || id.equals(target2) || id.equals(target3);
    }

    private boolean birdIsOnScreen(int bird) {
        int cameraX = services().camera().getX();
        int cameraY = services().camera().getY();
        return x(bird) >= cameraX - OFFSCREEN_MARGIN
                && x(bird) <= cameraX + viewportWidth() + OFFSCREEN_MARGIN
                && y(bird) >= cameraY - OFFSCREEN_MARGIN
                && y(bird) <= cameraY + viewportHeight() + OFFSCREEN_MARGIN;
    }

    @Override public int getX() { return x(0); }
    @Override public int getY() { return y(0); }
    @Override public int getPriorityBucket() { return 1; }
    @Override public boolean isPersistent() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!visible) return;
        ensureRomArtLoaded();
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.SUPER_TAILS_BIRDS);
        if (renderer == null) return;
        boolean reverseGravity = services().gameState() != null
                && services().gameState().isReverseGravityActive();
        for (int bird = 0; bird < COUNT; bird++) {
            renderer.drawFrameIndex(anim(bird), x(bird), y(bird), xv(bird) < 0, reverseGravity);
        }
    }

    @Override public void destroy() { ObjectLifetimeOps.expireDynamic(this); }
    @Override public void setVisible(boolean visible) { this.visible = visible; }
    @Override public PlayableEntity boundPlayer() { return owner; }

    private void ensureRomArtLoaded() {
        if (getRenderManager() != null
                && getRenderManager().getArtProvider() instanceof Sonic3kObjectArtProvider provider) {
            provider.ensureStandaloneArtLoaded(Sonic3kObjectArtKeys.SUPER_TAILS_BIRDS);
        }
    }

    /** Package-visible deterministic state used by focused ROM-order tests. */
    record BirdRuntimeState(int x, int y, int xVelocity, int yVelocity,
            int angle, int searchDelay, ObjectRefId target) {}

    BirdRuntimeState birdRuntimeState(int bird) {
        return new BirdRuntimeState(x(bird), y(bird), xv(bird), yv(bird),
                angle(bird), delay(bird), target(bird));
    }

    private int x(int i) { return xFixed(i) >> SUBPIXEL_SHIFT; }
    private int y(int i) { return yFixed(i) >> SUBPIXEL_SHIFT; }
    private int xFixed(int i) { return switch (i) { case 0 -> x0; case 1 -> x1; case 2 -> x2; default -> x3; }; }
    private int yFixed(int i) { return switch (i) { case 0 -> y0; case 1 -> y1; case 2 -> y2; default -> y3; }; }
    private int xv(int i) { return switch (i) { case 0 -> xv0; case 1 -> xv1; case 2 -> xv2; default -> xv3; }; }
    private int yv(int i) { return switch (i) { case 0 -> yv0; case 1 -> yv1; case 2 -> yv2; default -> yv3; }; }
    private int angle(int i) { return switch (i) { case 0 -> angle0; case 1 -> angle1; case 2 -> angle2; default -> angle3; }; }
    private int delay(int i) { return switch (i) { case 0 -> delay0; case 1 -> delay1; case 2 -> delay2; default -> delay3; }; }
    private int anim(int i) { return switch (i) { case 0 -> anim0; case 1 -> anim1; case 2 -> anim2; default -> anim3; }; }
    private int animTimer(int i) { return switch (i) { case 0 -> animTimer0; case 1 -> animTimer1; case 2 -> animTimer2; default -> animTimer3; }; }
    private ObjectRefId target(int i) { return switch (i) { case 0 -> target0; case 1 -> target1; case 2 -> target2; default -> target3; }; }

    private void setXFixed(int i, int v) { switch (i) { case 0 -> x0=v; case 1 -> x1=v; case 2 -> x2=v; default -> x3=v; } }
    private void setYFixed(int i, int v) { switch (i) { case 0 -> y0=v; case 1 -> y1=v; case 2 -> y2=v; default -> y3=v; } }
    private void setXv(int i, int v) { switch (i) { case 0 -> xv0=v; case 1 -> xv1=v; case 2 -> xv2=v; default -> xv3=v; } }
    private void setYv(int i, int v) { switch (i) { case 0 -> yv0=v; case 1 -> yv1=v; case 2 -> yv2=v; default -> yv3=v; } }
    private void setAngle(int i, int v) { switch (i) { case 0 -> angle0=v; case 1 -> angle1=v; case 2 -> angle2=v; default -> angle3=v; } }
    private void setDelay(int i, int v) { switch (i) { case 0 -> delay0=v; case 1 -> delay1=v; case 2 -> delay2=v; default -> delay3=v; } }
    private void setAnim(int i, int v) { switch (i) { case 0 -> anim0=v; case 1 -> anim1=v; case 2 -> anim2=v; default -> anim3=v; } }
    private void setAnimTimer(int i, int v) { switch (i) { case 0 -> animTimer0=v; case 1 -> animTimer1=v; case 2 -> animTimer2=v; default -> animTimer3=v; } }
    private void setTarget(int i, ObjectRefId v) { switch (i) { case 0 -> target0=v; case 1 -> target1=v; case 2 -> target2=v; default -> target3=v; } }
}
